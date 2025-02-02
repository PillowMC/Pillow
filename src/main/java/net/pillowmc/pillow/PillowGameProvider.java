/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LibraryFinder;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.game.GameProviderHelper;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.metadata.qmj.V1ModMetadataBuilder;
import org.quiltmc.loader.impl.util.Arguments;
import org.quiltmc.loader.impl.util.SystemProperties;

public class PillowGameProvider implements GameProvider {
	private String[] args;

	@Override
	public String getGameId() {
		return "minecraft";
	}

	@Override
	public String getGameName() {
		return "Minecraft";
	}

	@Override
	public String getRawGameVersion() {
		return FMLLoader.versionInfo().mcVersion();
	}

	@Override
	public String getNormalizedGameVersion() {
		return getRawGameVersion();
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		V1ModMetadataBuilder minecraftMetadata = new V1ModMetadataBuilder();
		minecraftMetadata.setId(getGameId());
		minecraftMetadata.setVersion(Version.of(getRawGameVersion()));
		minecraftMetadata.setName(getGameName());
		minecraftMetadata.setGroup("builtin");
		minecraftMetadata.setDescription(
				"Deobfuscated, NeoForm version = %s".formatted(FMLLoader.versionInfo().neoFormVersion()));
		Path path;
		var vers = FMLLoader.versionInfo();
		if (Utils.getSide() == EnvType.SERVER) {
			path = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "srg", vers.mcAndNeoFormVersion());
		} else {
			path = LibraryFinder.findPathForMaven("net.minecraft", "client", "", "srg", vers.mcAndNeoFormVersion());
		}
		return List.of(new BuiltinMod(List.of(path), minecraftMetadata.build()));
	}

	@Override
	public String getEntrypoint() {
		if (Utils.getSide() == EnvType.CLIENT) {
			return "net.minecraft.client.main.Main";
		} else {
			return "net.minecraft.server.Main";
		}
	}

	@Override
	public Path getLaunchDirectory() {
		return FMLLoader.getGamePath();
	}

	@Override
	public boolean isObfuscated() {
		return true;
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return false;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean locateGame(QuiltLauncher launcher, String[] args) {
		this.args = args;
		return true;
	}

	@Override
	public void initialize(QuiltLauncher launcher) {
		var side = Utils.getSide().name().toLowerCase();
		var mc = LibraryFinder.findPathForMaven("net.minecraft", side, "", "slim",
				FMLLoader.versionInfo().mcAndNeoFormVersion());
		var deobf = GameProviderHelper
				.deobfuscate(Map.of(side, mc), getGameId(), getNormalizedGameVersion(), getLaunchDirectory(), launcher)
				.get(side);
		try {
			var pillowdir = FMLPaths.GAMEDIR.get().resolve(".pillow");
			if (!Files.isDirectory(pillowdir)) {
				Files.createDirectory(pillowdir);
			}
			var rcp = pillowdir.resolve("remapClasspath.txt");
			Files.writeString(rcp, deobf.toString());
			System.setProperty(SystemProperties.REMAP_CLASSPATH_FILE, rcp.toString());
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot generate remapClasspath!", e);
		}
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return QuiltLauncherBase.getLauncher().getEntrypointTransformer();
	}

	@Override
	public void unlockClassPath(QuiltLauncher launcher) {
	}

	@Override
	public void launch(ClassLoader loader) {
	}

	@Override
	public Arguments getArguments() {
		Arguments arguments = new Arguments();
		arguments.parse(args);
		return arguments;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		return new String[0];
	}
}
