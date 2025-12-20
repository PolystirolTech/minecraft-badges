package com.example.polystirolutility.core;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BadgeService {

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
					}
					return badge;
				});
	}

	/**
	 * Получает бэйджик из кэша без запроса к API
	 * @param playerUuid UUID игрока
	 * @return бэйджик или null, если не найден в кэше
	 */
	public Badge getCachedBadge(UUID playerUuid) {
		return cache.get(playerUuid);
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

