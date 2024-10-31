/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

// PillowMC: Changed package, renamed.
package net.pillowmc.pillow.launch.copied;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * Locates the Minecraft client files in a production environment.
 * <p>
 * It assumes that the installer produced two artifacts, one containing the
 * Minecraft classes ("srg") which have been renamed to Mojangs official names
 * using their mappings, and another containing only the Minecraft resource
 * files ("extra"), and searches for these artifacts in the library directory.
 */
public class PillowClientProvider implements IModFileCandidateLocator {
	private final List<MavenCoordinate> additionalContent;
	// PillowMC: Added additionalFiles
	private final List<Path> additionalFiles;

	// PillowMC: Added additionalFiles
	public PillowClientProvider(List<MavenCoordinate> additionalContent, List<Path> additionalFiles) {
		this.additionalContent = additionalContent;
		this.additionalFiles = additionalFiles;
	}

	@Override
	public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
		var vers = FMLLoader.versionInfo();

		var content = new ArrayList<Path>();
		addRequiredLibrary(new MavenCoordinate("net.minecraft", "client", "", "srg", vers.mcAndNeoFormVersion()),
				content);
		addRequiredLibrary(new MavenCoordinate("net.minecraft", "client", "", "extra", vers.mcAndNeoFormVersion()),
				content);
		for (var artifact : additionalContent) {
			addRequiredLibrary(artifact, content);
		}

		// PillowMC: Added additionalFiles (3 lines)
		content.addAll(additionalFiles);

		try {
			var mcJarContents = JarContents.of(content);

			var mcJarMetadata = new ModJarMetadata(mcJarContents);
			var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
			var mcjar = IModFile.create(mcSecureJar, MinecraftModInfo::buildMinecraftModInfo);
			mcJarMetadata.setModFile(mcjar);

			pipeline.addModFile(mcjar);
		} catch (Exception e) {
			pipeline.addIssue(ModLoadingIssue.error("fml.modloading.corrupted_installation").withCause(e));
		}
	}

	private static void addRequiredLibrary(MavenCoordinate coordinate, List<Path> content) {
		var path = LibraryFinder.findPathForMaven(coordinate);
		if (!Files.exists(path)) {
			throw new ModLoadingException(
					ModLoadingIssue.error("fml.modloading.corrupted_installation").withAffectedPath(path));
		} else {
			content.add(path);
		}
	}

	@Override
	public String toString() {
		var result = new StringBuilder("production client provider");
		for (var mavenCoordinate : additionalContent) {
			result.append(" +").append(mavenCoordinate);
		}
		return result.toString();
	}
}
