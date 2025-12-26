package com.example.polystirolutility.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class ResourceCollectionApiClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCollectionApiClient.class);
	private static final int MAX_RETRIES = 3;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final String baseUrl;
	private final HttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService scheduler;

	public ResourceCollectionApiClient(String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		this.gson = new com.google.gson.GsonBuilder().create();
		this.scheduler = Executors.newScheduledThreadPool(1, r -> {
			Thread t = new Thread(r, "ResourceCollectionApiClient-Scheduler");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Отправляет данные о собранных ресурсах
	 * @param serverUuid UUID сервера (36 символов)
	 * @param resourceType тип ресурса (например, "wood", "stone")
	 * @param amount количество (инкремент, >= 0)
	 * @return CompletableFuture с ответом или null при ошибке
	 */
	public CompletableFuture<ResourceCollectionResponse> collectResource(String serverUuid, String resourceType, int amount) {
		if (amount < 0) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("amount должен быть >= 0"));
		}

		if (serverUuid == null || serverUuid.length() != 36) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("serverUuid должен быть 36 символов"));
		}

		String url = baseUrl + "/resource-collection/collect";
		ResourceCollectionRequest request = new ResourceCollectionRequest(serverUuid, resourceType, amount);
		String jsonBody = gson.toJson(request);

		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

		return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
				.thenCompose(response -> {
					if (response.statusCode() == 201) {
						try {
							ResourceCollectionResponse result = gson.fromJson(response.body(), ResourceCollectionResponse.class);
							return CompletableFuture.completedFuture(result);
						} catch (JsonParseException e) {
							LOGGER.error("Ошибка парсинга JSON ответа: {}", e.getMessage());
							return CompletableFuture.failedFuture(new ApiException("Ошибка парсинга JSON", response.statusCode(), e));
						}
					} else if (response.statusCode() == 400) {
						LOGGER.error("Неверный запрос (400): {}", response.body());
						return CompletableFuture.failedFuture(new ApiException("Неверный запрос: " + response.body(), 400));
					} else {
						return retryRequest(httpRequest, response.statusCode());
					}
				})
				.exceptionally(throwable -> {
					Throwable cause = throwable.getCause();
					if (cause instanceof IOException || cause instanceof java.net.http.HttpTimeoutException) {
						// Сетевая ошибка - повторяем запрос
						return retryRequest(httpRequest, 0).join();
					}
					LOGGER.error("Ошибка при отправке данных о сборе ресурсов: {}", throwable.getMessage());
					return null;
				});
	}

	/**
	 * Получает список активных целей для сервера
	 * @param serverUuid UUID сервера
	 * @return CompletableFuture с ответом или null при ошибке
	 */
	public CompletableFuture<ResourceProgressResponse> getResourceGoals(String serverUuid) {
		if (serverUuid == null || serverUuid.length() != 36) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("serverUuid должен быть 36 символов"));
		}

		String url = baseUrl + "/resource-collection/servers/" + serverUuid + "/progress";
		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();

		return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200) {
						try {
							return gson.fromJson(response.body(), ResourceProgressResponse.class);
						} catch (JsonParseException e) {
							LOGGER.error("Ошибка парсинга JSON ответа (цели): {}", e.getMessage());
							throw new ApiException("Ошибка парсинга JSON", response.statusCode(), e);
						}
					} else {
						LOGGER.error("Ошибка получения целей: код {}", response.statusCode());
						throw new ApiException("Ошибка получения целей", response.statusCode());
					}
				})
				.exceptionally(throwable -> {
					LOGGER.error("Ошибка при получении списка целей: {}", throwable.getMessage());
					return null;
				});
	}

	private CompletableFuture<ResourceCollectionResponse> retryRequest(HttpRequest request, int lastStatusCode) {
		CompletableFuture<ResourceCollectionResponse> retryFuture = new CompletableFuture<>();
		
		scheduler.execute(() -> {
			for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
				try {
					long delay = (long) Math.pow(2, attempt); // Экспоненциальная задержка: 1, 2, 4 секунды
					Thread.sleep(delay * 1000);

					HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					
					if (response.statusCode() == 201) {
						try {
							ResourceCollectionResponse result = gson.fromJson(response.body(), ResourceCollectionResponse.class);
							retryFuture.complete(result);
							return;
						} catch (JsonParseException e) {
							LOGGER.error("Ошибка парсинга JSON ответа при повторе: {}", e.getMessage());
						}
					} else if (response.statusCode() == 400) {
						retryFuture.completeExceptionally(new ApiException("Неверный запрос: " + response.body(), 400));
						return;
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					retryFuture.completeExceptionally(new ApiException("Прервано", 0, e));
					return;
				} catch (Exception e) {
					LOGGER.warn("Попытка {} не удалась: {}", attempt + 1, e.getMessage());
				}
			}
			
			retryFuture.completeExceptionally(new ApiException("Превышено количество попыток", lastStatusCode));
		});

		return retryFuture;
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

	}
}

