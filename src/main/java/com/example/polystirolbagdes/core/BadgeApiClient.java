package com.example.polystirolbagdes.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class BadgeApiClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(BadgeApiClient.class);
	private static final int MAX_RETRIES = 3;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final String baseUrl;
	private final HttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService scheduler;

	public BadgeApiClient(String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		this.gson = new GsonBuilder()
				.registerTypeAdapter(Instant.class, new InstantDeserializer())
				.registerTypeAdapter(Badge.BadgeType.class, new BadgeTypeDeserializer())
				.create();
		this.scheduler = Executors.newScheduledThreadPool(1, r -> {
			Thread t = new Thread(r, "BadgeApiClient-Scheduler");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Получает выбранный бэйджик игрока
	 * @param playerUuid UUID игрока
	 * @return Badge или null, если игрок не имеет бэйджика
	 */
	public CompletableFuture<Badge> getPlayerBadge(UUID playerUuid) {
		String url = baseUrl + "/badges/minecraft/" + playerUuid.toString();
		return sendRequest(url, Badge.class)
				.exceptionally(throwable -> {
					Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
					if (cause instanceof ApiException apiException && apiException.getStatusCode() == 404) {
						// 404 - нормальная ситуация, игрок не имеет бэйджика
						LOGGER.debug("Игрок {} не имеет бэйджика (404)", playerUuid);
						return null;
					}
					LOGGER.error("Ошибка при получении бэйджика игрока {}: {}", playerUuid, cause.getMessage());
					return null;
				});
	}

	/**
	 * Получает информацию о сервере
	 * @param serverId UUID сервера
	 * @return GameServerInfo
	 */
	public CompletableFuture<GameServerInfo> getServerInfo(UUID serverId) {
		String url = baseUrl + "/game-servers/" + serverId.toString();
		return sendRequest(url, GameServerInfo.class)
				.exceptionally(throwable -> {
					LOGGER.error("Ошибка при получении информации о сервере {}: {}", serverId, throwable.getMessage());
					return null;
				});
	}

	private <T> CompletableFuture<T> sendRequest(String url, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenCompose(response -> {
					if (response.statusCode() == 200) {
						try {
							T result = gson.fromJson(response.body(), responseType);
							return CompletableFuture.completedFuture(result);
						} catch (JsonParseException e) {
							return CompletableFuture.failedFuture(new ApiException("Ошибка парсинга JSON", response.statusCode(), e));
						}
					} else if (response.statusCode() == 404) {
						return CompletableFuture.failedFuture(new ApiException("Ресурс не найден", 404));
					} else if (response.statusCode() == 400) {
						return CompletableFuture.failedFuture(new ApiException("Неверный запрос", 400));
					} else {
						return retryRequest(request, responseType, 0);
					}
				})
				.exceptionally(throwable -> {
					Throwable cause = throwable.getCause();
					if (cause instanceof IOException || cause instanceof java.net.http.HttpTimeoutException) {
						// Сетевая ошибка - повторяем запрос
						return retryRequest(request, responseType, 0).join();
					}
					// Другие ошибки пробрасываем дальше
					if (throwable instanceof RuntimeException) {
						throw (RuntimeException) throwable;
					}
					throw new RuntimeException(throwable);
				});
	}

	private <T> CompletableFuture<T> retryRequest(HttpRequest request, Class<T> responseType, int attempt) {
		if (attempt >= MAX_RETRIES) {
			return CompletableFuture.failedFuture(new ApiException("Превышено количество попыток", 0));
		}

		long delay = (long) Math.pow(2, attempt); // Экспоненциальная задержка: 1, 2, 4 секунды
		LOGGER.debug("Повторная попытка запроса через {} секунд (попытка {}/{})", delay, attempt + 1, MAX_RETRIES);

		CompletableFuture<Void> delayFuture = new CompletableFuture<>();
		scheduler.schedule(() -> delayFuture.complete(null), delay, TimeUnit.SECONDS);

		return delayFuture.thenCompose(v -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
				.thenCompose(response -> {
					if (response.statusCode() == 200) {
						try {
							T result = gson.fromJson(response.body(), responseType);
							return CompletableFuture.completedFuture(result);
						} catch (JsonParseException e) {
							return CompletableFuture.failedFuture(new ApiException("Ошибка парсинга JSON", response.statusCode(), e));
						}
					} else if (response.statusCode() == 404) {
						return CompletableFuture.failedFuture(new ApiException("Ресурс не найден", 404));
					} else {
						return retryRequest(request, responseType, attempt + 1);
					}
				});
	}

	private static class ApiException extends RuntimeException {
		private final int statusCode;

		public ApiException(String message, int statusCode) {
			super(message);
			this.statusCode = statusCode;
		}

		public ApiException(String message, int statusCode, Throwable cause) {
			super(message, cause);
			this.statusCode = statusCode;
		}

		public int getStatusCode() {
			return statusCode;
		}
	}

	private static class InstantDeserializer implements JsonDeserializer<Instant> {
		@Override
		public Instant deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return Instant.parse(json.getAsString());
		}
	}

	private static class BadgeTypeDeserializer implements JsonDeserializer<Badge.BadgeType> {
		@Override
		public Badge.BadgeType deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			String value = json.getAsString().toUpperCase();
			try {
				return Badge.BadgeType.valueOf(value);
			} catch (IllegalArgumentException e) {
				return Badge.BadgeType.PERMANENT; // По умолчанию
			}
		}
	}
}

