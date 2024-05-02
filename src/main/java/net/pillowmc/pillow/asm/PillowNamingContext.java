/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;

public final class PillowNamingContext {
	public static boolean isUserDev = false;
	public static String fromName = "intermediary";
	public static String toName = "srg";

	private PillowNamingContext() {
	}

	static {
		var environment = Launcher.INSTANCE.environment();
		environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get())
				.ifPresent((v) -> isUserDev = v.contains("userdev"));
		if (isUserDev) {
			fromName = "left";
			toName = "right";
		}
	}
}
