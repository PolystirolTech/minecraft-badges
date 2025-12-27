package com.example.polystirolutility.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class AFKCommand {
	private final AFKManager afkManager;

	public AFKCommand(AFKManager afkManager) {
		this.afkManager = afkManager;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("afk")
			.executes(this::execute));
	}

	private int execute(CommandContext<CommandSourceStack> context) {
		try {
			ServerPlayer player = context.getSource().getPlayerOrException();
			afkManager.toggleAfk(player);
			return 1;
		} catch (Exception e) {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Could not toggle AFK: " + e.getMessage()));
			return 0;
		}
	}
}
