/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import net.pillowmc.pillow.Utils;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.launch.common.QuiltMixinBootstrap;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

@SuppressWarnings("unused")
public class PillowConnector implements IMixinConnector {
	@Override
	public void connect() {
		var manager = Launcher.INSTANCE.findLayerManager().orElseThrow();
		var languageMods = manager.getLayer(Layer.GAME).orElseThrow().findModule("quiltLanguageMods").orElse(null);
		var mods = manager.getLayer(Layer.GAME).orElseThrow().findModule("quiltMods").orElse(null);
		if (mods == null && languageMods == null)
			return; // No Quilt Mod installed.
		var bootLayer = manager.getLayer(Layer.BOOT).orElseThrow();
		var loader = bootLayer
				.findModule(PillowNamingContext.isUserDev ? "org.quiltmc.loader.beta._2" : "org.quiltmc.loader")
				.orElseThrow();
		var selfModule = getClass().getModule();
		if (mods != null) {
			Utils.setModule(mods, getClass());
			mods.addReads(loader);
			if (languageMods != null) mods.addReads(languageMods);
		}
		Utils.setModule(loader, getClass());
		if (mods != null) loader.addReads(mods);
		if (languageMods != null) loader.addReads(languageMods);
		if (languageMods != null) {
			Utils.setModule(languageMods, getClass());
			languageMods.addReads(loader);
			if (mods != null) languageMods.addReads(mods);
		}
		Utils.setModule(selfModule, getClass());
		var mappings = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
		// QuiltMixinBootstrap.init
		System.setProperty("mixin.env.remapRefMap", "true");
		try {
			MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings,
					PillowNamingContext.fromName, PillowNamingContext.toName);
			MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
			Log.info(LogCategory.MIXIN, "Loaded Pillow Loader mappings for mixin remapper!");
		} catch (Exception e) {
			Log.error(LogCategory.MIXIN, "Pillow Loader environment setup error - the game will probably crash soon!");
			var byos = new ByteArrayOutputStream();
			e.printStackTrace(new PrintStream(byos));
			Log.error(LogCategory.MIXIN, byos.toString());
		}
		QuiltMixinBootstrap.init(QuiltLauncherBase.getLauncher().getEnvironmentType(), QuiltLoaderImpl.INSTANCE);
	}
}
