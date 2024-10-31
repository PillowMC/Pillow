/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.lib.accesswidener.AccessWidener;
import net.fabricmc.loader.impl.lib.accesswidener.AccessWidenerClassVisitor;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

public class AWTransformer implements ITransformer<ClassNode> {
	private final AccessWidener aw;
	public AWTransformer() {
		aw = FabricLoaderImpl.INSTANCE.getAccessWidener();
	}

	@Override
	public @NotNull ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		ClassNode output = new ClassNode(FabricLoaderImpl.ASM_VERSION);
		ClassVisitor visitor = AccessWidenerClassVisitor.createClassVisitor(FabricLoaderImpl.ASM_VERSION, output,
				FabricLoaderImpl.INSTANCE.getAccessWidener());
		input.accept(visitor);
		return output;
	}

	@Override
	public @NotNull TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public @NotNull Set<Target<ClassNode>> targets() {
		return aw.getTargets().stream().map(i -> i.replace(".", "/"))
				.map(name -> ITransformer.Target.targetPreClass(name.replace('/', '.'))).collect(Collectors.toSet());
	}

	@Override
	public @NotNull TargetType<ClassNode> getTargetType() {
		return TargetType.PRE_CLASS;
	}
}
