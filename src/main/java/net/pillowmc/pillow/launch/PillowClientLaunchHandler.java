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
import net.pillowmc.pillow.launch.copied.PillowClientProvider;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

public class PillowClientLaunchHandler extends CommonClientLaunchHandler {
	@Override
	public String name() {
		return "pillowclient";
	}

	@Override
	protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
		QuiltLoaderImpl.INSTANCE.invokePreLaunch();
		return super.preLaunch(arguments, layer);
	}

	@Override
	public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
		var additionalContent = getAdditionalMinecraftJarContent(versionInfo);
		output.accept(new PillowClientProvider(additionalContent,
				List.of(QuiltLoader.getModContainer("minecraft").get().rootPath())));
	}
}
