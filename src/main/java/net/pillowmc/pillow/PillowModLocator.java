/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow;

import com.electronwill.nightconfig.core.Config;
import cpw.mods.jarhandling.VirtualJar;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.QuiltLoader;

public class PillowModLocator implements IModFileCandidateLocator {

	private record ProvidedModInfo(String id, String version) {
	}

	@Override
	public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
		var providedMods = new HashMap<org.quiltmc.loader.api.ModContainer, ArrayList<ProvidedModInfo>>();

		QuiltLoader.getAllMods().forEach((mod) -> {
			mod.getSourcePaths().stream().filter((p) -> p.size() == 1) // Exclude JiJ mods.
					.map(List::getFirst).forEach(context::addLocated);

			for (var e : mod.metadata().value("pillow-provided").asObject().entrySet()) {
				providedMods.computeIfAbsent(mod, (v) -> new ArrayList<>())
						.add(new ProvidedModInfo(e.getKey(), e.getValue().asString()));
			}
		});

		for (var e : providedMods.entrySet()) {
			pipeline.addModFile(IModFile.create(
					new VirtualJar(e.getKey().metadata().id().replace('-', '_'), getRefPath(e.getKey())), (mf) -> {
						var config = genModFileInfoConf(e.getKey(), e.getValue());
						return new ModFileInfo((ModFile) mf, config, config::setFile, List.of());
					}, IModFile.Type.MOD, ModFileDiscoveryAttributes.DEFAULT));
		}
	}

	private static NightConfigWrapper genModFileInfoConf(org.quiltmc.loader.api.ModContainer mod,
			ArrayList<ProvidedModInfo> providedMods) {
		final var conf = Config.inMemory();
		conf.set("modLoader", "lowcode");
		conf.set("loaderVersion", "[0,)");
		conf.set("license", joinLicenses(mod.metadata().licenses()));
		var mods = new ArrayList<Config>();
		for (var providedMod : providedMods) {
			final var modConf = Config.inMemory();
			modConf.set("modId", providedMod.id);
			modConf.set("version", providedMod.version);
			modConf.set("displayName", providedMod.id);
			modConf.set("description", providedMod.id);
			mods.add(modConf);
		}
		conf.set("mods", mods);

		return new NightConfigWrapper(conf);
	}

	private static String joinLicenses(Collection<ModLicense> c) {
		if (c.isEmpty()) {
			return "All Rights Reserved";
		}
		return c.stream().map(ModLicense::name).collect(Collectors.joining(", "));
	}

	private static Path getRefPath(ModContainer mod) {
		var src = mod.getSourcePaths().getFirst();
		return src.getLast();
	}

}
