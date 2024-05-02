/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.targets.CommonServerLaunchHandler;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

public class PillowServerLaunchHandler extends CommonServerLaunchHandler {
	@Override
	public String name() {
		return "pillowserver";
	}

	@Override
	protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
		QuiltLoaderImpl.INSTANCE.invokePreLaunch();
		return super.preLaunch(arguments, layer);
	}

	@Override
	protected void processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods) {
		mc.accept(QuiltLoader.getModContainer("minecraft").get().rootPath());
	}
}
