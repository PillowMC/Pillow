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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LibraryFinder;
import net.pillowmc.pillow.PillowGameProvider;
import net.pillowmc.pillow.Utils;
import net.pillowmc.pillow.hacks.SuperHackyClassLoader;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.config.QuiltConfigImpl;
import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystemProvider;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.game.minecraft.Log4jLogHandler;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.plugin.gui.I18n;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

public class PillowTransformationService extends QuiltLauncherBase implements ITransformationService {
	private static final String DFU_VERSION = "8.0.16";
	private static final String AUTHLIB_VERSION = "6.0.55";
	private boolean hasLanguageAdapter = false;
	@SuppressWarnings("unchecked")
	public PillowTransformationService() {
		var layer = Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(Layer.BOOT).orElseThrow();
		Utils.setModule(Thread.currentThread().getContextClassLoader().getUnnamedModule(), I18n.class);
		// Remove other mixin services. These services may not work well.
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
			map.get("org.spongepowered.asm.service.IMixinServiceBootstrap").removeIf(Utils.rethrowPredicate(
					v -> "org.quiltmc.loader".equals(((Module) v.getClass().getMethod("module").invoke(v)).getName())));
			map.get("org.spongepowered.asm.service.IGlobalPropertyService").removeIf(Utils.rethrowPredicate(
					v -> "org.quiltmc.loader".equals(((Module) v.getClass().getMethod("module").invoke(v)).getName())));
			Utils.setModule(old, PillowTransformationService.class);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public @NotNull String name() {
		return "pillow";
	}

	@Override
	// @SuppressWarnings("unchecked")
	public void initialize(IEnvironment environment) {
		setProperties(new HashMap<>());
		setupUncaughtExceptionHandler();
		setupURLHandlers();
		// Add Quilt's FileSystemProviders.
		try {
			var FSPC = FileSystemProvider.class;
			var old = Utils.setModule(FSPC.getModule(), getClass());
			var installedProviders = FSPC.getDeclaredField("installedProviders");
			installedProviders.setAccessible(true);
			@SuppressWarnings("unchecked")
			var val = (List<FileSystemProvider>) installedProviders.get(null);
			var newval = new ArrayList<>(val);
			newval.removeIf(i -> i.getClass().getName().contains("Quilt"));
			newval.add(new QuiltMemoryFileSystemProvider());
			newval.add(new QuiltJoinedFileSystemProvider());
			newval.add(new QuiltUnifiedFileSystemProvider());
			newval.add(new QuiltZipFileSystemProvider());
			installedProviders.set(null, Collections.unmodifiableList(newval));
			Utils.setModule(old, getClass());
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		provider = new PillowGameProvider();
		Log.init(new Log4jLogHandler(), true);
		// Maybe tomorrow's Pillow Loader uses this?
		provider.locateGame(this, new String[0]);
		Log.info(LogCategory.GAME_PROVIDER, "Loading %s %s with Quilt Loader %s", provider.getGameName(),
				provider.getRawGameVersion(), QuiltLoaderImpl.VERSION);
		provider.initialize(this);
		// Copy legacyClassPath to java.class.path for QuiltForkComms
		System.setProperty("java.class.path", System.getProperty("legacyClassPath"));
		// It's time for Quilt!
		QuiltLoaderImpl loader = QuiltLoaderImpl.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		try {
			loader.freeze();
		} catch (RuntimeException e) {
			if (e.getMessage().startsWith("Failed to instantiate language adapter: ")) {
				this.hasLanguageAdapter = true;
			} else {
				throw e;
			}
		}
		QuiltConfigImpl.init();
	}

	private static void setupURLHandlers() {
		System.setProperty(SystemProperties.DISABLE_URL_STREAM_FACTORY, "true");
	}

	@Override
	public List<Resource> beginScanning(IEnvironment environment) {
		if (plugincp.isEmpty())
			return List.of();
		var modContents = new JarContentsBuilder().paths(plugincp.toArray(new Path[0]))
				.pathFilter(this::filterPackagesPluginLayer).build();
		var modJar = SecureJar.from(modContents, createJarMetadata(modContents, "quiltLanguageMods"));
		var modResource = new Resource(Layer.PLUGIN, List.of(modJar));
		return List.of(modResource);
	}

	@Override
	public List<Resource> completeScan(IModuleLayerManager environment) {
		if (this.hasLanguageAdapter) {
			var clazz = QuiltLoaderImpl.class;
			var old = Utils.setModule(clazz.getModule(), getClass());
			try {
				var method = clazz.getDeclaredMethod("setupLanguageAdapters");
				method.setAccessible(true);
				method.invoke(QuiltLoaderImpl.INSTANCE);
				method = clazz.getDeclaredMethod("setupMods");
				method.setAccessible(true);
				method.invoke(QuiltLoaderImpl.INSTANCE);
			} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(e);
			}
			Utils.setModule(old, getClass());
		}
		if (cp.isEmpty())
			return List.of();
		// We merge all Quilt mods into one module.
		var modContents = new JarContentsBuilder().paths(cp.toArray(new Path[0])).pathFilter(this::filterPackages)
				.build();
		var modJar = SecureJar.from(modContents, createJarMetadata(modContents, "quiltMods"));
		var modResource = new Resource(Layer.GAME, List.of(modJar));
		var dfuJar = SecureJar
				.from(LibraryFinder.findPathForMaven("com.mojang", "datafixerupper", "", "", DFU_VERSION));
		var authlibJar = SecureJar
				.from(LibraryFinder.findPathForMaven("com.mojang", "authlib", "", "", AUTHLIB_VERSION));
		var depResource = new Resource(Layer.GAME, List.of(dfuJar, authlibJar));
		return List.of(modResource, depResource);
	}

	// NeoForge offers some libraries that Quilt doesn't offer.
	// Some of the mods includes these libraries.
	// So we remove these packages from quiltMods to make the module system happy.
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
				: new ServerEntryPointTransformer());
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

	// QuiltLauncher start

	@Override
	public void addToClassPath(Path path, String... allowedPrefixes) {
		cp.add(path);
	}

	@Override
	public void setAllowedPrefixes(Path path, String... prefixes) {
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
		// Let Quilt get the real location of itself, and Pillow.
		var trace = Thread.currentThread().getStackTrace();
		if (trace[2].getClassName().contains("ClasspathModCandidateFinder")
				&& trace[2].getMethodName().equals("findCandidates"))
			return new SuperHackyClassLoader();
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
				|| trace[4].getMethodName().equals("scan0");
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

	@Override
	public void addToClassPath(Path path, ModContainer mod, URL origin, String... allowedPrefixes) {
		if (NO_LOAD_MODS.contains(mod.metadata().id()))
			return;
		if (mod.metadata() instanceof ModMetadataExt ext && !ext.languageAdapters().isEmpty()) {
			plugincp.add(path);
		} else {
			cp.add(path);
		}
	}

	@Override
	public void setTransformCache(URL insideTransformCache) {
	}

	@Override
	public void hideParentUrl(URL hidden) {
	}

	@Override
	public void hideParentPath(Path obf) {
	}

	@Override
	public void validateGameClassLoader(Object gameInstance) {
	}

	@Override
	public URL getResourceURL(String name) {
		return getTargetClassLoader().getResource(name);
	}

	@Override
	public ClassLoader getClassLoader(ModContainer mod) {
		if (mod.metadata() instanceof ModMetadataExt ext && !ext.languageAdapters().isEmpty()) {
			return Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(Layer.PLUGIN).orElseThrow()
					.findLoader("quiltLanguageMods");
		}
		return getTargetClassLoader();
	}

	@Override
	public void setHiddenClasses(Set<String> classes) {
		// TODO Error when load these classes.
	}

	@Override
	public void setHiddenClasses(Map<String, String> classes) {
		// TODO Error when load these classes.
	}

	@Override
	public void setPluginPackages(Map<String, ClassLoader> map) {

	}

	private final GameTransformer gameTransformer = new GameTransformer() {
		public byte[] transform(String className) {
			return null;
		}
	};

	@Override
	public GameTransformer getEntrypointTransformer() {
		return this.gameTransformer;
	}
}
