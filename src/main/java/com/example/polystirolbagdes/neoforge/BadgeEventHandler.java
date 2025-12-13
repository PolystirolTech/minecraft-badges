package com.example.polystirolbagdes.neoforge;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.polystirolbagdes.core.Badge;
import com.example.polystirolbagdes.core.BadgeService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class BadgeEventHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(BadgeEventHandler.class);
	private final BadgeService badgeService;
	private final ResourcePackHandler resourcePackHandler;

	public BadgeEventHandler(BadgeService badgeService, ResourcePackHandler resourcePackHandler) {
		this.badgeService = badgeService;
		this.resourcePackHandler = resourcePackHandler;
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		UUID playerUuid = player.getUUID();
		LOGGER.debug("Игрок {} зашел на сервер, запрашиваем бэйджик", playerUuid);

		// Отправляем resource pack при входе (если handler доступен)
		if (resourcePackHandler != null) {
			resourcePackHandler.onPlayerJoin(player);
		}

		badgeService.getPlayerBadge(playerUuid)
				.thenAccept(badge -> {
					if (badge != null) {
						LOGGER.info("Бэйджик для игрока {}: {}", player.getName().getString(), badge.getName());
						TabIntegration.setPlayerPrefix(playerUuid, badge);
					} else {
						LOGGER.debug("Игрок {} не имеет бэйджика", player.getName().getString());
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
}

