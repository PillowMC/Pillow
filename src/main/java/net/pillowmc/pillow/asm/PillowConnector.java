/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.FabricMixinBootstrap;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import net.pillowmc.pillow.Utils;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

@SuppressWarnings("unused")
public class PillowConnector implements IMixinConnector {
	@Override
	public void connect() {
		var manager = Launcher.INSTANCE.findLayerManager().orElseThrow();
		var languageMods = manager.getLayer(Layer.GAME).orElseThrow().findModule("fabricLanguageMods").orElse(null);
		var mods = manager.getLayer(Layer.GAME).orElseThrow().findModule("fabricMods").orElse(null);
		if (mods == null && languageMods == null)
			return; // No Fabric Mod installed.
		var bootLayer = manager.getLayer(Layer.BOOT).orElseThrow();
		var loader = bootLayer.findModule("net.fabricmc.loader").orElseThrow();
		var selfModule = getClass().getModule();
		if (mods != null) {
			Utils.setModule(mods, getClass());
			mods.addReads(loader);
			if (languageMods != null)
				mods.addReads(languageMods);
		}
		Utils.setModule(loader, getClass());
		if (mods != null)
			loader.addReads(mods);
		if (languageMods != null)
			loader.addReads(languageMods);
		if (languageMods != null) {
			Utils.setModule(languageMods, getClass());
			languageMods.addReads(loader);
			if (mods != null)
				languageMods.addReads(mods);
		}
		Utils.setModule(selfModule, getClass());
		var mappings = FabricLauncherBase.getLauncher().getMappingConfiguration().getMappings();
		// FabricMixinBootstrap.init
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
		FabricMixinBootstrap.init(FabricLauncherBase.getLauncher().getEnvironmentType(), FabricLoaderImpl.INSTANCE);
	}
}
