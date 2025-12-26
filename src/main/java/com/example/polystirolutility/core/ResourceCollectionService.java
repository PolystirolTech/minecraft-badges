package com.example.polystirolutility.core;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.item.ItemStack;

public class ResourceCollectionService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCollectionService.class);

	private final ResourceCollectionApiClient apiClient;
	private final String serverUuid;
	
	// Кеш разрешенных ресурсов (те, по которым есть активные цели)
	private final java.util.Set<String> allowedResources = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
	private long lastGoalsUpdateTime = 0;
	private static final long GOALS_UPDATE_INTERVAL_MS = 60_000; // Обновляем раз в минуту

	public ResourceCollectionService(ResourceCollectionApiClient apiClient, String serverUuid) {
		this.apiClient = apiClient;
		this.serverUuid = serverUuid;
		updateAllowedResources(); // Инициализируем при создании
	}
	
	private void updateAllowedResources() {
		long now = System.currentTimeMillis();
		if (now - lastGoalsUpdateTime < GOALS_UPDATE_INTERVAL_MS && !allowedResources.isEmpty()) {
			return; // Кеш свежий
		}
		
		apiClient.getResourceGoals(serverUuid).thenAccept(response -> {
			if (response != null && response.getResources() != null) {
				java.util.Set<String> newAllowed = new java.util.HashSet<>();
				for (ResourceGoal goal : response.getResources()) {
					if (goal.isActive()) {
						newAllowed.add(goal.getResourceType());
					}
				}
				allowedResources.clear();
				allowedResources.addAll(newAllowed);
				lastGoalsUpdateTime = System.currentTimeMillis();
				LOGGER.debug("Обновлен список целей. Доступно ресурсов: {}", allowedResources.size());
			}
		});
	}

	/**
	 * Обрабатывает предметы и отправляет данные на API
	 * @param items массив предметов для обработки
	 * @return Map с результатами отправки по типам ресурсов (resourceType -> количество успешно отправлено)
	 */
	public Map<String, Integer> processAndSendItems(ItemStack[] items) {
	    // Пробуем обновить список целей перед обработкой (асинхронно, если старый)
		updateAllowedResources();

		// Группируем предметы по типам ресурсов
		Map<String, Integer> resourceCounts = new HashMap<>();

		for (ItemStack stack : items) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}

			String resourceType = ResourceTypeMapper.getResourceType(stack.getItem());
			if (resourceType != null) {
				// Проверяем, есть ли активная цель для этого ресурса
				if (allowedResources.contains(resourceType)) {
					int count = stack.getCount();
					resourceCounts.merge(resourceType, count, Integer::sum);
				} else {
					LOGGER.debug("Пропущен ресурс {}, так как нет активной цели", resourceType);
				}
			}
		}

		// Отправляем данные на API по одному типу за раз
		Map<String, Integer> sentCounts = new HashMap<>();
		for (Map.Entry<String, Integer> entry : resourceCounts.entrySet()) {
			String resourceType = entry.getKey();
			int amount = entry.getValue();

			try {
				// Ждем завершения (блокируем для синхронной обработки)
				ResourceCollectionResponse response = apiClient.collectResource(serverUuid, resourceType, amount).join();
				if (response != null && response.isSuccess()) {
					LOGGER.info("Отправлено {} единиц {} (всего: {})", amount, resourceType, response.getCurrentAmount());
					sentCounts.put(resourceType, amount);
				} else {
					LOGGER.warn("Не удалось отправить {} единиц {}", amount, resourceType);
				}
			} catch (Exception e) {
				LOGGER.error("Ошибка при обработке ресурса {}: {}", resourceType, e.getMessage());
			}
		}

		return sentCounts;
	}
}

