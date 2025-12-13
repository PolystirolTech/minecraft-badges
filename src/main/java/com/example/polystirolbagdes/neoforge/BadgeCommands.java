package com.example.polystirolbagdes.neoforge;

import com.example.polystirolbagdes.core.ResourcePackManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgeCommands {
	private static final Logger LOGGER = LoggerFactory.getLogger(BadgeCommands.class);
	private final ResourcePackManager resourcePackManager;
	private final ResourcePackHandler resourcePackHandler;

	public BadgeCommands(ResourcePackManager resourcePackManager, ResourcePackHandler resourcePackHandler) {
		this.resourcePackManager = resourcePackManager;
		this.resourcePackHandler = resourcePackHandler;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("badges")
				.requires(source -> source.hasPermission(2)) // Требует OP уровень 2
				.then(Commands.literal("reloadpack")
						.executes(this::reloadResourcePack)
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reloadResourcePackForPlayer(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("refresh")
						.executes(this::refreshBadges))
		);
	}

	private int reloadResourcePack(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		
		if (resourcePackManager == null) {
			source.sendFailure(Component.literal("Resource Pack Manager не инициализирован. Проверьте конфигурацию serverId."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Проверка обновлений resource pack..."), true);
		
		resourcePackManager.checkResourcePackHash(() -> {
			resourcePackManager.getResourcePackUrl()
					.thenAccept(url -> {
						if (url != null && !url.isEmpty()) {
							// Получаем hash из последней проверки
							resourcePackManager.getServerInfo()
									.thenAccept(serverInfo -> {
										if (serverInfo != null) {
											String hash = serverInfo.getResourcePackHash();
											resourcePackHandler.sendResourcePackToAllPlayers(url, hash);
											source.sendSuccess(() -> Component.literal("Resource pack отправлен всем игрокам"), true);
										} else {
											source.sendFailure(Component.literal("Не удалось получить информацию о сервере"));
										}
									});
						} else {
							source.sendFailure(Component.literal("Resource pack URL не найден"));
						}
					})
					.exceptionally(throwable -> {
						LOGGER.error("Ошибка при обновлении resource pack: {}", throwable.getMessage());
						source.sendFailure(Component.literal("Ошибка при обновлении resource pack: " + throwable.getMessage()));
						return null;
					});
		});

		return 1;
	}

	private int reloadResourcePackForPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		
		if (resourcePackManager == null) {
			source.sendFailure(Component.literal("Resource Pack Manager не инициализирован. Проверьте конфигурацию serverId."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Отправка resource pack игроку " + player.getName().getString() + "..."), true);
		
		resourcePackManager.getServerInfo()
				.thenAccept(serverInfo -> {
					if (serverInfo == null) {
						source.sendFailure(Component.literal("Не удалось получить информацию о сервере"));
						return;
					}
					
					String url = serverInfo.getResourcePackUrl();
					String hash = serverInfo.getResourcePackHash();
					
					if (url != null && !url.isEmpty()) {
						resourcePackHandler.sendResourcePackToPlayer(player, url, hash);
						source.sendSuccess(() -> Component.literal("Resource pack отправлен игроку " + player.getName().getString()), true);
					} else {
						source.sendFailure(Component.literal("Resource pack URL не найден"));
					}
				})
				.exceptionally(throwable -> {
					LOGGER.error("Ошибка при отправке resource pack игроку: {}", throwable.getMessage());
					source.sendFailure(Component.literal("Ошибка при отправке resource pack: " + throwable.getMessage()));
					return null;
				});

		return 1;
	}

	private int refreshBadges(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("Обновление бэйджиков..."), true);
		// TODO: Реализовать обновление бэйджиков для всех игроков
		source.sendSuccess(() -> Component.literal("Бэйджики обновлены"), true);
		return 1;
	}
}

