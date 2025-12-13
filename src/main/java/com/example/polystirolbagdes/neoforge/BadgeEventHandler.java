package com.example.polystirolbagdes.neoforge;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.polystirolbagdes.core.Badge;
import com.example.polystirolbagdes.core.BadgeService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class BadgeEventHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(BadgeEventHandler.class);
	private final BadgeService badgeService;
	// Resource pack функционал отключен - будет управляться вручную через server.properties

	public BadgeEventHandler(BadgeService badgeService) {
		this.badgeService = badgeService;
		// Resource pack функционал отключен - будет управляться вручную через server.properties
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		UUID playerUuid = player.getUUID();

		// Resource pack функционал отключен - управляется вручную через server.properties

		// Запрашиваем бэйджик и сохраняем в кэш для установки через TAB PlayerLoadEvent
		badgeService.getPlayerBadge(playerUuid)
				.thenAccept(badge -> {
					if (badge != null) {
						// Сохраняем бэйджик в кэш - установка будет через TAB PlayerLoadEvent
						TabIntegration.setPlayerPrefix(playerUuid, badge);
					} else {
						TabIntegration.setPlayerPrefix(playerUuid, null);
					}
				})
				.exceptionally(throwable -> {
					LOGGER.error("Ошибка при получении бэйджика для игрока {}: {}", playerUuid, throwable.getMessage());
					return null;
				});
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		UUID playerUuid = player.getUUID();
		// Очищаем префикс при выходе
		TabIntegration.setPlayerPrefix(playerUuid, null);
		// Инвалидируем кэш для освобождения памяти
		badgeService.invalidateCache(playerUuid);
	}

	@SubscribeEvent
	public void onServerChat(ServerChatEvent event) {
		ServerPlayer player = event.getPlayer();
		UUID playerUuid = player.getUUID();
		
		// Получаем бэйджик из кэша
		Badge badge = badgeService.getCachedBadge(playerUuid);
		if (badge == null) {
			return;
		}
		
		String unicodeString = badge.getUnicodeString();
		if (unicodeString.isEmpty()) {
			return;
		}
		
		// Используем PlayerTeam из Scoreboard для установки префикса к имени в чате
		// Имя игрока НЕ является частью сообщения - оно берется из PlayerTeam при форматировании
		Scoreboard scoreboard = player.getServer().getScoreboard();
		Component prefix = Component.literal(unicodeString);
		
		// Создаем уникальное имя команды для этого префикса
		String teamName = "badge_" + badge.getId().toString().replace("-", "").substring(0, 16);
		
		PlayerTeam team = scoreboard.getPlayerTeam(teamName);
		if (team == null) {
			// Создаем новую команду, если ее нет
			team = scoreboard.addPlayerTeam(teamName);
		}
		
		// Устанавливаем префикс команды
		team.setPlayerPrefix(prefix);
		
		// Добавляем игрока в эту команду
		scoreboard.addPlayerToTeam(player.getName().getString(), team);
		
		// После отправки сообщения можно удалить игрока из команды, если нужно
		// Но для постоянного отображения префикса лучше оставить игрока в команде
	}
}

