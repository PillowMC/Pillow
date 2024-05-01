/*
 * MIT License
 *
 * Copyright (c) 2024 PillowMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.pillowmc.pillow;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.neoforged.fml.loading.FMLLoader;
import sun.misc.Unsafe;

public class Utils {
	private static EnvType side;
	private static Unsafe unsafe;
	private static long offset;
	
	static {
		Field theUnsafe;
		try {
			theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			unsafe = (Unsafe) theUnsafe.get(null);
			Field module = Class.class.getDeclaredField("module");
			offset = unsafe.objectFieldOffset(module);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
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
					return ret.get();
				} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
						| NoSuchMethodException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
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

	@FunctionalInterface
	public static interface PredicateThrowable<T, E extends Throwable> {
		/**
		 * Evaluates this predicate on the given argument.
		 *
		 * @param t
		 *            the input argument
		 * @return {@code true} if the input argument matches the predicate, otherwise
		 *         {@code false}
		 */
		boolean test(T t) throws E;
	}

	public static <T, E extends Throwable> Predicate<T> rethrowPredicate(PredicateThrowable<T, E> perdicateThrowable) {
		return (v) -> {
			try {
				return perdicateThrowable.test(v);
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		};
	}
}
