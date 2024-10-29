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
import org.objectweb.asm.tree.*;

public class ClientEntryPointTransformer implements ITransformer<MethodNode> {
	@Override
	public @NotNull MethodNode transform(MethodNode input, ITransformerVotingContext context) {
		// after initBackendSystem
		ListIterator<AbstractInsnNode> it = input.instructions.iterator();
		FieldInsnNode insn = null;
		while (it.hasNext()) {
			AbstractInsnNode ins = it.next();
			if (ins instanceof FieldInsnNode fin) {
				if ((fin.desc.equals("Ljava/io/File;") && fin.getOpcode() == Opcodes.PUTFIELD)) {
					insn = fin;
					break;
				}
			}
		}
		if (insn == null)
			throw new RuntimeException("net.minecraft.client.Minecraft.<init> doesn't set gameDirectory!");
		it.add(new VarInsnNode(Opcodes.ALOAD, 0));
		it.add(new FieldInsnNode(Opcodes.GETFIELD, insn.owner, insn.name, insn.desc));
		it.add(new VarInsnNode(Opcodes.ALOAD, 0));
		it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "startClient",
				"(Ljava/io/File;Ljava/lang/Object;)V"));
		return input;
	}

	@Override
	public @NotNull TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public @NotNull Set<Target<MethodNode>> targets() {
		return Set.of(Target.targetMethod("net.minecraft.client.Minecraft", "<init>",
				"(Lnet/minecraft/client/main/GameConfig;)V"));
	}

	@Override
	public @NotNull TargetType<MethodNode> getTargetType() {
		return TargetType.METHOD;
	}
}
