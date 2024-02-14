/*
 * MIT License
 *
 * Copyright (c) 2024 PillowMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.pillowmc.pillow.asm;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.VirtualJar;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.io.InputStream;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import net.fabricmc.api.EnvType;
import net.neoforged.fml.loading.LibraryFinder;
import net.pillowmc.pillow.PillowGameProvider;
import net.pillowmc.pillow.Utils;
import net.pillowmc.pillow.hacks.SuperHackyClassLoader;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.config.QuiltConfigImpl;
import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.filesystem.DelegatingUrlStreamHandlerFactory;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystemProvider;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.game.minecraft.Log4jLogHandler;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.plugin.gui.I18n;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.spongepowered.asm.util.Constants;

public class PillowTransformationService extends QuiltLauncherBase implements ITransformationService {
	@SuppressWarnings("unchecked")
	public PillowTransformationService() {
		var layer = Launcher.INSTANCE.findLayerManager().get().getLayer(Layer.BOOT).get();
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
	@SuppressWarnings("unchecked")
	public void initialize(IEnvironment environment) {
		setProperties(new HashMap<>());
		setupUncaughtExceptionHandler();
		// Don't touch here
		// Reload URLStreamHandlerFactory(s).
		try {
			var old = Utils.setModule(URL.class.getModule(), getClass());
			var field = URL.class.getDeclaredField("factory");
			field.setAccessible(true);
			var oldFactory = (URLStreamHandlerFactory) field.get(null);
			field.set(null, null);
			DelegatingUrlStreamHandlerFactory.appendFactory(oldFactory);
			field.set(null, DelegatingUrlStreamHandlerFactory.INSTANCE);
			Utils.setModule(old, getClass());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// End of don't touch here
		// Add Quilt's FileSystemProviders.
		try {
			var FSPC = FileSystemProvider.class;
			var old = Utils.setModule(FSPC.getModule(), getClass());
			var installedProviders = FSPC.getDeclaredField("installedProviders");
			installedProviders.setAccessible(true);
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
		loader.freeze();
		QuiltConfigImpl.init();
	}

	public List<Resource> beginScanning(IEnvironment environment) {
		return List.of(new Resource(Layer.GAME, List.of(new VirtualJar("mixin_generated",
				Path.of(uncheck(() -> Constants.class.getProtectionDomain().getCodeSource().getLocation().toURI())),
				Constants.SYNTHETIC_PACKAGE, Constants.SYNTHETIC_PACKAGE + ".args"))));
	}

	@Override
	public List<Resource> completeScan(IModuleLayerManager environment) {
		Log.debug(LogCategory.DISCOVERY, "Completing scan with classpath %s", cp);
		if (cp.isEmpty())
			return List.of();
		// We merge all Quilt mods into one module.
		var modContents = new JarContentsBuilder().paths(cp.toArray(new Path[0])).build();
		var modResource = new Resource(Layer.GAME,
				List.of(SecureJar.from(modContents, createJarMetadata(modContents, "quiltMods"))));
		var dfuJar = SecureJar.from(LibraryFinder.findPathForMaven("com.mojang", "datafixerupper", "", "", "6.0.8"));
		var depResource = new Resource(Layer.GAME, List.of(dfuJar));
		return List.of(modResource, depResource);
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) {
	}

	@Override
	@SuppressWarnings("rawtypes")
	public @NotNull List<ITransformer> transformers() {
		return List.of(Utils.getSide() == EnvType.CLIENT
				? new ClientEntryPointTransformer()
				: new ServerEntryPointTransformer());
	}

	@Override
	public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalClassesLocator() {
		return null;
	}

	public static JarMetadata createJarMetadata(JarContents contents, String name) {
		return new SimpleJarMetadata(name, "1.0.0", contents::getPackages, contents.getMetaInfServices());
	}

	private GameProvider provider;
	private final List<Path> cp = new ArrayList<>();
	public static List<String> NO_LOAD_MODS = List.of("pillow-loader", "forge", "minecraft", "java");

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
		return null;
	}

	@Override
	public void addToClassPath(Path path, ModContainer mod, URL origin, String... allowedPrefixes) {
		if (NO_LOAD_MODS.contains(mod.metadata().id()))
			return;
		addToClassPath(path, allowedPrefixes);
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
		return getTargetClassLoader();
	}

	@Override
	public void setHiddenClasses(Set<String> classes) {
		// TODO Error when load these classes.
	}

	private final GameTransformer gameTransformer = new GameTransformer() {
		public byte[] transform(String className) {
			return null;
		};
	};

	@Override
	public GameTransformer getEntrypointTransformer() {
		return this.gameTransformer;
	}
}
