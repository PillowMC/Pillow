/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.hacks;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.pillowmc.pillow.Utils;

public class SuperHackyClassLoader extends ClassLoader {
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		if (name.equals("fabric.mod.json")) {
			var urls = FabricLauncherBase.getLauncher().getTargetClassLoader().getResources(name);
			return new Enumeration<>() {
				@Override
				public boolean hasMoreElements() {
					return urls.hasMoreElements();
				}

				@Override
				public URL nextElement() {
					var url = urls.nextElement();
					try {
						return Utils.getUnionPathRealPath(Path.of(url.toURI())).toUri().toURL();
					} catch (URISyntaxException | MalformedURLException e) {
						throw new RuntimeException("Can't get path for " + url, e);
					}
				}
			};
		}
		return Collections.emptyEnumeration();
	}
}
