/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.neoforged.fml.loading.FMLLoader;
import sun.misc.Unsafe;

public class Utils {
	private static EnvType side;
	private static final Unsafe unsafe;
	private static final long offset;

	public static final LogCategory PILLOW_LOG_CATEGORY = LogCategory.createCustom("Pillow Loader");

	static {
		Field theUnsafe;
		try {
			theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			unsafe = (Unsafe) theUnsafe.get(null);
			Field module = Class.class.getDeclaredField("module");
			offset = unsafe.objectFieldOffset(module);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static EnvType getSide() {
		if (side != null)
			return side;
		return side = FMLLoader.getDist().isClient() ? EnvType.CLIENT : EnvType.SERVER;
	}

	@SuppressWarnings("unchecked")
	public static Path getUnionPathRealPath(Path path) {
		if (path.getClass().getName().contains("Union")) {
			var pc = path.getClass();
			var old = setModule(pc.getModule(), Utils.class);
			try {
				var fsf = pc.getDeclaredField("fileSystem");
				fsf.setAccessible(true);
				var fs = fsf.get(path);
				var fsc = fs.getClass();
				var findFirstFilteredMethod = fsc.getDeclaredMethod("findFirstFiltered", pc);
				findFirstFilteredMethod.setAccessible(true);
				var ret = (Optional<Path>) findFirstFilteredMethod.invoke(fs, path);
				setModule(old, Utils.class);
				return ret.orElseThrow();
			} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
					| NoSuchMethodException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
		return path;
	}

	@Deprecated
	public static Module setModule(Module new_, Class<?> class_) {
		var old = class_.getModule();
		unsafe.putObject(class_, offset, new_);
		return old;
	}
}
