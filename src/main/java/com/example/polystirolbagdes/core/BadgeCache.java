package com.example.polystirolbagdes.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BadgeCache {
	private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
	private final long ttlSeconds;
	private final ScheduledExecutorService cleanupExecutor;

	public BadgeCache(long ttlSeconds) {
		this.ttlSeconds = ttlSeconds;
		this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "BadgeCache-Cleanup");
			t.setDaemon(true);
			return t;
		});
		
		// Периодическая очистка устаревших записей
		this.cleanupExecutor.scheduleAtFixedRate(this::cleanup, ttlSeconds, ttlSeconds, TimeUnit.SECONDS);
	}

	public Badge get(UUID playerUuid) {
		CacheEntry entry = cache.get(playerUuid);
		if (entry == null || entry.isExpired()) {
			cache.remove(playerUuid);
			return null;
		}
		return entry.badge;
	}

	public void put(UUID playerUuid, Badge badge) {
		cache.put(playerUuid, new CacheEntry(badge, System.currentTimeMillis() + ttlSeconds * 1000));
	}

	public void invalidate(UUID playerUuid) {
		cache.remove(playerUuid);
	}

	public void clear() {
		cache.clear();
	}

	private void cleanup() {
		long now = System.currentTimeMillis();
		cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
	}

	public void shutdown() {
		cleanupExecutor.shutdown();
	}

	private static class CacheEntry {
		final Badge badge;
		final long expiresAt;

		CacheEntry(Badge badge, long expiresAt) {
			this.badge = badge;
			this.expiresAt = expiresAt;
		}

		boolean isExpired() {
			return isExpired(System.currentTimeMillis());
		}

		boolean isExpired(long now) {
			return now >= expiresAt;
		}
	}
}

