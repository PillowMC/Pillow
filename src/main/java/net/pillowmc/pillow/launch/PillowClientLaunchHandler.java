/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.launch;

import net.neoforged.fml.loading.targets.CommonClientLaunchHandler;

public class PillowClientLaunchHandler extends CommonClientLaunchHandler {
	@Override
	public String name() {
		return "pillowclient";
	}

	// @Override
	// protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
	// try {
	// // If the very first class transformed by mixin is also referenced by a mixin
	// // config
	// // then we'll crash due to an "attempted duplicate class definition"
	// // Since this target class is *very unlikely* to be referenced by mixin we
	// // forcibly load it.
	// Thread.currentThread().getContextClassLoader().loadClass("net.minecraft.client.main.Main");
	// } catch (ClassNotFoundException cnfe) {
	// Log.warn(Utils.PILLOW_LOG_CATEGORY, "Early non-mixin-config related class
	// failed to load!");
	// Log.warn(Utils.PILLOW_LOG_CATEGORY,
	// "If you get a 'LinkageError' of 'attempted duplicated * definition' after
	// this then this error is the cause!",
	// cnfe);
	// }
	// try {
	// FabricLoader.getInstance().invokeEntrypoints("preLaunch",
	// PreLaunchEntrypoint.class,
	// PreLaunchEntrypoint::onPreLaunch);
	// } catch (RuntimeException e) {
	// throw FormattedException.ofLocalized("exception.initializerFailure", e);
	// }
	// return super.preLaunch(arguments, layer);
	// }

	// @Override
	// public void collectAdditionalModFileLocators(VersionInfo versionInfo,
	// Consumer<IModFileCandidateLocator> output) {
	// var additionalContent = getAdditionalMinecraftJarContent(versionInfo);
	// output.accept(new PillowClientProvider(additionalContent,
	// ((ModContainerImpl)
	// FabricLoaderImpl.INSTANCE.getModContainer("minecraft").orElseThrow())
	// .getCodeSourcePaths()));
	// }
}
