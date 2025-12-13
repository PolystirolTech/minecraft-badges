package com.example.polystirolbagdes.core;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourcePackManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackManager.class);

	private final BadgeApiClient apiClient;
	private final UUID serverId;
	private final long checkIntervalSeconds;
	private String lastKnownHash;
	private ScheduledExecutorService scheduler;

	public ResourcePackManager(BadgeApiClient apiClient, UUID serverId, long checkIntervalSeconds) {
		this.apiClient = apiClient;
		this.serverId = serverId;
		this.checkIntervalSeconds = checkIntervalSeconds;
	}

	/**
	 * Запускает периодическую проверку resource pack
	 * @param onHashChanged колбэк, вызываемый при изменении hash
	 */
	public void startPeriodicCheck(Runnable onHashChanged) {
		if (scheduler != null) {
			return; // Уже запущен
		}

		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ResourcePackManager-Checker");
			t.setDaemon(true);
			return t;
		});

		scheduler.scheduleAtFixedRate(() -> {
			checkResourcePackHash(onHashChanged);
		}, 0, checkIntervalSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Проверяет hash resource pack и вызывает колбэк при изменении
	 */
	public CompletableFuture<Void> checkResourcePackHash(Runnable onHashChanged) {
		return apiClient.getServerInfo(serverId)
				.thenAccept(serverInfo -> {
					if (serverInfo == null) {
						LOGGER.warn("Не удалось получить информацию о сервере {}", serverId);
						return;
					}

					String currentHash = serverInfo.getResourcePackHash();
					if (currentHash == null || currentHash.isEmpty()) {
						return;
					}

					if (lastKnownHash == null) {
						// Первая проверка - сохраняем hash
						lastKnownHash = currentHash;
						if (onHashChanged != null) {
							onHashChanged.run();
						}
					} else if (!lastKnownHash.equals(currentHash)) {
						// Hash изменился
						lastKnownHash = currentHash;
						if (onHashChanged != null) {
							onHashChanged.run();
						}
					}
				})
				.exceptionally(throwable -> {
					LOGGER.error("Ошибка при проверке resource pack hash: {}", throwable.getMessage());
					return null;
				});
	}

	/**
	 * Получает URL resource pack
	 */
	public CompletableFuture<String> getResourcePackUrl() {
		return apiClient.getServerInfo(serverId)
				.thenApply(serverInfo -> {
					if (serverInfo == null) {
						return null;
					}
					return serverInfo.getResourcePackUrl();
				});
	}

	/**
	 * Получает информацию о сервере
	 */
	public CompletableFuture<GameServerInfo> getServerInfo() {
		return apiClient.getServerInfo(serverId);
	}

	public void shutdown() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
	}
}

