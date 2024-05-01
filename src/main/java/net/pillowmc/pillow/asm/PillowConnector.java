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

public class PillowConnector implements IMixinConnector {
	@Override
	public void connect() {
		var manager = Launcher.INSTANCE.findLayerManager().orElseThrow();
		var mods = manager.getLayer(Layer.GAME).orElseThrow().findModule("quiltMods").orElse(null);
		if (mods == null)
			return; // No Quilt Mod installed.
		var bootLayer = manager.getLayer(Layer.BOOT).orElseThrow();
		var loader = bootLayer
				.findModule(PillowNamingContext.isUserDev ? "org.quiltmc.loader.beta._2" : "org.quiltmc.loader")
				.orElseThrow();
		var selfModule = Utils.setModule(mods, getClass());
		mods.addReads(loader);
		Utils.setModule(loader, getClass());
		loader.addReads(mods);
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
			e.printStackTrace();
		}
		QuiltMixinBootstrap.init(QuiltLauncherBase.getLauncher().getEnvironmentType(), QuiltLoaderImpl.INSTANCE);
	}
}
