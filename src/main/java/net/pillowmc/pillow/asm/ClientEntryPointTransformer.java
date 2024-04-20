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

package net.pillowmc.pillow.asm;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.ListIterator;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.quiltmc.loader.impl.game.minecraft.Hooks;

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
