/*
 * Copyright (c) PillowMC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.pillowmc.pillow.asm;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.ListIterator;
import java.util.Set;
import net.fabricmc.loader.impl.game.minecraft.Hooks;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ServerEntryPointTransformer implements ITransformer<MethodNode> {
	@Override
	public @NotNull MethodNode transform(MethodNode input, ITransformerVotingContext context) {
		// before server.properties
		ListIterator<AbstractInsnNode> it = input.instructions.iterator();
		while (it.hasNext()) {
			AbstractInsnNode ins = it.next();
			if (ins instanceof LdcInsnNode lin) {
				if ((lin.cst.equals("server.properties"))) {
					it.previous();
					it.add(new InsnNode(Opcodes.ACONST_NULL));
					it.add(new InsnNode(Opcodes.ACONST_NULL));
					it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "startServer",
							"(Ljava/io/File;Ljava/lang/Object;)V"));
					it.next();
				}
			} else if (ins instanceof MethodInsnNode min) {
				if (min.owner.equals("net/minecraft/server/dedicated/DedicatedServer") && min.name.equals("<init>")) {
					it.add(new InsnNode(Opcodes.DUP));
					it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "setGameInstance",
							"(Ljava/lang/Object;)V"));
				}
			}
		}
		return input;
	}

	@Override
	public @NotNull TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public @NotNull Set<Target<MethodNode>> targets() {
		return Set.of(Target.targetMethod("net.minecraft.server.Main", "main", "([Ljava/lang/String)V"));
	}

	@Override
	public @NotNull TargetType<MethodNode> getTargetType() {
		return TargetType.METHOD;
	}
}
