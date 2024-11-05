/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.minecraft.Log4jLogHandler;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LibraryFinder;
import net.pillowmc.pillow.PillowGameProvider;
import net.pillowmc.pillow.Utils;
import net.pillowmc.pillow.hacks.SuperHackyClassLoader;
import org.jetbrains.annotations.NotNull;

public class PillowTransformationService extends FabricLauncherBase implements ITransformationService {
	private static final String DFU_VERSION = "8.0.16";
	private static final String AUTHLIB_VERSION = "6.0.55";
	private boolean hasLanguageAdapter = false;

	@Override
	public @NotNull String name() {
		return "pillow";
	}
	@SuppressWarnings("unchecked")
	public PillowTransformationService() {
		var layer = Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(Layer.BOOT).orElseThrow();
		// Remove Fabric's mixin service, which may not work well.
		try {
			var field = layer.getClass().getDeclaredField("servicesCatalog");
			var old = Utils.setModule(layer.getClass().getModule(), PillowTransformationService.class);
			field.setAccessible(true);
			var catalog = field.get(layer);
			var mapField = catalog.getClass().getDeclaredField("map");
			mapField.setAccessible(true);
			var map = (Map<String, List<Object>>) mapField.get(catalog);
			map.get("org.spongepowered.asm.service.IMixinService").removeIf(Utils.rethrowPredicate(
					v -> !"pillow".equals(((Module) v.getClass().getMethod("module").invoke(v)).getName())));
			map.get("org.spongepowered.asm.service.IMixinServiceBootstrap")
					.removeIf(Utils.rethrowPredicate(v -> "net.fabricmc.loader"
							.equals(((Module) v.getClass().getMethod("module").invoke(v)).getName())));
			map.get("org.spongepowered.asm.service.IGlobalPropertyService")
					.removeIf(Utils.rethrowPredicate(v -> "net.fabricmc.loader"
							.equals(((Module) v.getClass().getMethod("module").invoke(v)).getName())));
			Utils.setModule(old, PillowTransformationService.class);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	// @SuppressWarnings("unchecked")
	public void initialize(IEnvironment environment) {
		setProperties(new HashMap<>());
		setupUncaughtExceptionHandler();
		provider = new PillowGameProvider();
		Log.init(new Log4jLogHandler());
		// Maybe tomorrow's Pillow Loader uses this?
		provider.locateGame(this, new String[0]);
		Log.info(LogCategory.GAME_PROVIDER, "Loading %s %s with Fabric Loader %s", provider.getGameName(),
				provider.getRawGameVersion(), FabricLoaderImpl.VERSION);
		provider.initialize(this);
		// It's time for Fabric!
		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		try {
			doFreeze();
		} catch (RuntimeException e) {
			if (e.getMessage().startsWith("Failed to instantiate language adapter: ")) {
				this.hasLanguageAdapter = true;
			} else {
				throw e;
			}
		}
		FabricLoaderImpl.INSTANCE.loadAccessWideners();
	}

	private void doFreeze() {
		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
		try {
			var frozen = FabricLoaderImpl.class.getDeclaredField("frozen");
			var module = Utils.setModule(FabricLoaderImpl.class.getModule(), this.getClass());
			frozen.setAccessible(true);
			Utils.setModule(module, this.getClass());
			frozen.setBoolean(loader, true);

		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		for (ModContainerImpl mod : loader.getModsInternal()) {
			if (!mod.getMetadata().getId().equals(FabricLoaderImpl.MOD_ID)
					&& !mod.getMetadata().getType().equals("builtin")) {
				if (NO_LOAD_MODS.contains(mod.getMetadata().getId()))
					continue;
				boolean islang = !mod.getMetadata().getLanguageAdapterDefinitions().isEmpty();
				if (islang) {
					hasLanguageAdapter = true;
				}
				for (Path path : mod.getCodeSourcePaths()) {
					if (islang) {
						plugincp.add(path);
					} else {
						cp.add(path);
					}
				}
			}
		}
	}

	@Override
	public List<Resource> beginScanning(IEnvironment environment) {
		if (plugincp.isEmpty())
			return List.of();
		var modContents = new JarContentsBuilder().paths(plugincp.toArray(new Path[0]))
				.pathFilter(this::filterPackagesPluginLayer).build();
		var modJar = SecureJar.from(modContents, createJarMetadata(modContents, "fabricLanguageMods"));
		var modResource = new Resource(Layer.PLUGIN, List.of(modJar));
		return List.of(modResource);
	}

	@Override
	public List<Resource> completeScan(IModuleLayerManager environment) {
		var clazz = FabricLoaderImpl.class;
		var old = Utils.setModule(clazz.getModule(), getClass());
		try {
			var method = clazz.getDeclaredMethod("setupLanguageAdapters");
			method.setAccessible(true);
			method.invoke(FabricLoaderImpl.INSTANCE);
			method = clazz.getDeclaredMethod("setupMods");
			method.setAccessible(true);
			method.invoke(FabricLoaderImpl.INSTANCE);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
		Utils.setModule(old, getClass());
		if (cp.isEmpty())
			return List.of();
		// We merge all Fabric mods into one module.
		var modContents = new JarContentsBuilder().paths(cp.toArray(new Path[0])).pathFilter(this::filterPackages)
				.build();
		var modJar = SecureJar.from(modContents, createJarMetadata(modContents, "fabricMods"));
		var modResource = new Resource(Layer.GAME, List.of(modJar));
		var dfuJar = SecureJar
				.from(LibraryFinder.findPathForMaven("com.mojang", "datafixerupper", "", "", DFU_VERSION));
		var authlibJar = SecureJar
				.from(LibraryFinder.findPathForMaven("com.mojang", "authlib", "", "", AUTHLIB_VERSION));
		var depResource = new Resource(Layer.GAME, List.of(dfuJar, authlibJar));
		return List.of(modResource, depResource);
	}

	// NeoForge offers some libraries that Fabric doesn't offer.
	// Some of the mods includes these libraries.
	// So we remove these packages from fabricMods to make the module system happy.
	// But... Who includes LWJGL??? IDK but without this in NO_LOAD_PACKAGES,
	// Replay Mod will crash.
	// However, I didn't find org/lwjgl in Replay Mod.
	public static final Set<String> NO_LOAD_PACKAGES = loadNoLoads("packages", "javax/annotation",
			"com/electronwill/nightconfig", "org/openjdk/nashorn", "org/apache/maven/artifact",
			"org/apache/maven/repository", "org/lwjgl", "org/antlr");

	public static final Set<String> NO_LOAD_PACKAGES_PLUGIN_LAYER = loadNoLoads("packages-plugin-layer", "kotlin",
			"_COROUTINE");

	private boolean filterPackages(String entry, Path _basePath) {
		return NO_LOAD_PACKAGES.stream().noneMatch(entry::startsWith);
	}

	private boolean filterPackagesPluginLayer(String entry, Path _basePath) {
		return Stream.concat(NO_LOAD_PACKAGES.stream(), NO_LOAD_PACKAGES_PLUGIN_LAYER.stream())
				.noneMatch(entry::startsWith);
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) {
	}

	@Override
	public @NotNull List<ITransformer<?>> transformers() {
		return List.of(Utils.getSide() == EnvType.CLIENT
				? new ClientEntryPointTransformer()
				: new ServerEntryPointTransformer(), new AWTransformer());
	}

	public static JarMetadata createJarMetadata(JarContents contents, String name) {
		return new SimpleJarMetadata(name, "1.0.0", contents::getPackages, contents.getMetaInfServices());
	}

	private GameProvider provider;
	private final List<Path> cp = new ArrayList<>();
	private final List<Path> plugincp = new ArrayList<>();
	public static final Set<String> NO_LOAD_MODS = loadNoLoads("mods", "pillow-loader", "forge", "minecraft", "java",
			"night-config", "org_antlr_antlr4-runtime");

	private static Set<String> loadNoLoads(String name, String... defaults) {
		try {
			var file = FMLPaths.CONFIGDIR.get().resolve("pillow-loader-noload" + name + ".txt");
			if (file.toFile().exists()) {
				return new HashSet<>(Files.readAllLines(file));
			}
			Files.writeString(file, String.join("\n", defaults));
			return Set.of(defaults);
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read Pillow Loader noload files!", e);
		}
	}

	// FabricLauncher start

	@Override
	public void addToClassPath(Path path, String... allowedPrefixes) {
		cp.add(path);
	}

	@Override
	public void setAllowedPrefixes(Path path, String... prefixes) {
	}

	@Override
	public void setValidParentClassPath(Collection<Path> paths) {

	}

	@Override
	public EnvType getEnvironmentType() {
		return Utils.getSide();
	}

	@Override
	public boolean isClassLoaded(String name) {
		return false;
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		return getTargetClassLoader().loadClass(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return this.getTargetClassLoader().getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		// Let Fabric get the real location of itself, and Pillow.
		var trace = Thread.currentThread().getStackTrace();
		if (trace[2].getClassName().contains("ClasspathModCandidateFinder")
				&& trace[2].getMethodName().equals("findCandidates"))
			return new SuperHackyClassLoader();
		if (trace[2].getMethodName().equals("setupLanguageAdapters")) {
			return Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(Layer.PLUGIN).orElseThrow()
					.findLoader("fabricLanguageMods");
		}
		return Thread.currentThread().getContextClassLoader();
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) {
		return new byte[0];
	}

	@Override
	public Manifest getManifest(Path originPath) {
		return null;
	}

	@Override
	public boolean isDevelopment() {
		var trace = Thread.currentThread().getStackTrace();
		return trace[2].getClassName().contains("ClasspathModCandidateFinder")
				|| trace[4].getMethodName().equals("scan0") || trace[3].getMethodName().equals("setup");
	}

	@Override
	public String getEntrypoint() {
		return provider.getEntrypoint();
	}

	@Override
	public String getTargetNamespace() {
		return "srg";
	}

	@Override
	public List<Path> getClassPath() {
		return Stream.of(System.getProperty("legacyClassPath").split(File.pathSeparator)).map(Path::of).toList();
	}
}
