package com.example.polystirolbagdes.core;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgeService {
	private static final Logger LOGGER = LoggerFactory.getLogger(BadgeService.class);

	private final BadgeApiClient apiClient;
	private final BadgeCache cache;

	public BadgeService(BadgeApiClient apiClient, BadgeCache cache) {
		this.apiClient = apiClient;
		this.cache = cache;
	}

	/**
	 * Получает бэйджик игрока (с проверкой кэша)
	 * @param playerUuid UUID игрока
	 * @return CompletableFuture с Badge или null
	 */
	public CompletableFuture<Badge> getPlayerBadge(UUID playerUuid) {
		// Проверяем кэш
		Badge cached = cache.get(playerUuid);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		// Запрашиваем из API
		return apiClient.getPlayerBadge(playerUuid)
				.thenApply(badge -> {
					if (badge != null) {
						cache.put(playerUuid, badge);
						LOGGER.debug("Бэйджик игрока {} закэширован: {}", playerUuid, badge.getName());
					} else {
						// Кэшируем null на короткое время, чтобы не спамить API
						// Но не сохраняем в кэш, чтобы при следующем запросе попробовать снова
						LOGGER.debug("Игрок {} не имеет бэйджика", playerUuid);
					}
					return badge;
				});
	}

	/**
	 * Инвалидирует кэш для игрока
	 * @param playerUuid UUID игрока
	 */
	public void invalidateCache(UUID playerUuid) {
		cache.invalidate(playerUuid);
	}

	/**
	 * Очищает весь кэш
	 */
	public void clearCache() {
		cache.clear();
	}
}

