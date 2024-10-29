/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.launch;

import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.util.log.Log;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.targets.CommonServerLaunchHandler;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.pillowmc.pillow.Utils;
import net.pillowmc.pillow.launch.copied.PillowServerProvider;

public class PillowServerLaunchHandler extends CommonServerLaunchHandler {
	@Override
	public String name() {
		return "pillowserver";
	}

	@Override
	protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
		try {
			// If the very first class transformed by mixin is also referenced by a mixin
			// config
			// then we'll crash due to an "attempted duplicate class definition"
			// Since this target class is *very unlikely* to be referenced by mixin we
			// forcibly load it.
			Thread.currentThread().getContextClassLoader().loadClass("net.minecraft.server.Main");
		} catch (ClassNotFoundException cnfe) {
			Log.warn(Utils.PILLOW_LOG_CATEGORY, "Early non-mixin-config related class failed to load!");
			Log.warn(Utils.PILLOW_LOG_CATEGORY,
					"If you get a 'LinkageError' of 'attempted duplicated * definition' after this then this error is the cause!",
					cnfe);
		}
		try {
			FabricLoader.getInstance().invokeEntrypoints("preLaunch", PreLaunchEntrypoint.class,
					PreLaunchEntrypoint::onPreLaunch);
		} catch (RuntimeException e) {
			throw FormattedException.ofLocalized("exception.initializerFailure", e);
		}
		return super.preLaunch(arguments, layer);
	}

	@Override
	public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
		var additionalContent = getAdditionalMinecraftJarContent(versionInfo);
		output.accept(new PillowServerProvider(additionalContent,
				((ModContainerImpl) FabricLoaderImpl.INSTANCE.getModContainer("minecraft").orElseThrow())
						.getCodeSourcePaths()));
	}
}
