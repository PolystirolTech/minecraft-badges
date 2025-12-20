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

	public ResourceCollectionService(ResourceCollectionApiClient apiClient, String serverUuid) {
		this.apiClient = apiClient;
		this.serverUuid = serverUuid;
	}

	/**
	 * Обрабатывает предметы и отправляет данные на API
	 * @param items массив предметов для обработки
	 * @return Map с результатами отправки по типам ресурсов (resourceType -> количество успешно отправлено)
	 */
	public Map<String, Integer> processAndSendItems(ItemStack[] items) {
		// Группируем предметы по типам ресурсов
		Map<String, Integer> resourceCounts = new HashMap<>();

		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				continue;
			}

			String resourceType = ResourceTypeMapper.getResourceType(stack.getItem());
			if (resourceType != null) {
				int count = stack.getCount();
				resourceCounts.merge(resourceType, count, Integer::sum);
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

