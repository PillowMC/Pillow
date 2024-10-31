/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.launch;

import java.util.List;
import java.util.function.Consumer;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.targets.CommonClientLaunchHandler;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.pillowmc.pillow.Utils;
import net.pillowmc.pillow.launch.copied.PillowClientProvider;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.log.Log;

public class PillowClientLaunchHandler extends CommonClientLaunchHandler {
	@Override
	public String name() {
		return "pillowclient";
	}

	@Override
	protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
		try {
			// If the very first class transformed by mixin is also referenced by a mixin
			// config
			// then we'll crash due to an "attempted duplicate class definition"
			// Since this target class is *very unlikely* to be referenced by mixin we
			// forcibly load it.
			Thread.currentThread().getContextClassLoader().loadClass("net.minecraft.client.main.Main");
		} catch (ClassNotFoundException cnfe) {
			Log.warn(Utils.PILLOW_LOG_CATEGORY, "Early non-mixin-config related class failed to load!");
			Log.warn(Utils.PILLOW_LOG_CATEGORY,
					"If you get a 'LinkageError' of 'attempted duplicated * definition' after this then this error is the cause!",
					cnfe);
		}
		QuiltLoaderImpl.INSTANCE.invokePreLaunch();
		return super.preLaunch(arguments, layer);
	}

	@Override
	public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
		var additionalContent = getAdditionalMinecraftJarContent(versionInfo);
		output.accept(new PillowClientProvider(additionalContent,
				List.of(QuiltLoader.getModContainer("minecraft").orElseThrow().rootPath())));
	}
}
