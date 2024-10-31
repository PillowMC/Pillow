/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.spongepowered.asm.service.modlauncher.MixinServiceModLauncher;

public class MixinServicePillow extends MixinServiceModLauncher {
	public MixinServicePillow() throws URISyntaxException {
		super();
		this.getPrimaryContainer().addResource("pillow",
				Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()));
	}

	@Override
	public String getName() {
		return "ModLauncher/Pillow";
	}
}
