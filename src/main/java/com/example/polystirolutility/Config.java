package com.example.polystirolutility;

import java.util.UUID;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	public static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
			.comment("Базовый URL API для получения бэйджиков")
			.define("apiBaseUrl", "https://api.dev.sluicee.ru/api/v1");

	public static final ModConfigSpec.ConfigValue<String> SERVER_ID = BUILDER
			.comment("UUID сервера для запроса информации о resource pack")
			.define("serverId", "");

	public static final ModConfigSpec.ConfigValue<String> SERVER_UUID = BUILDER
			.comment("UUID сервера для отправки данных о сборе ресурсов (формат: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)")
			.define("serverUuid", "");

	public static final ModConfigSpec.IntValue CACHE_TTL_SECONDS = BUILDER
			.comment("Время жизни кэша бэйджиков в секундах")
			.defineInRange("cacheTtlSeconds", 60, 10, 3600);

	public static final ModConfigSpec.IntValue RESOURCE_PACK_CHECK_INTERVAL = BUILDER
			.comment("Интервал проверки resource pack hash в секундах")
			.defineInRange("resourcePackCheckInterval", 300, 60, 3600);

	static final ModConfigSpec SPEC = BUILDER.build();

	/**
	 * Получает UUID сервера из конфига
	 * @return UUID или null, если неверный формат
	 */
	public static UUID getServerId() {
		String serverIdStr = SERVER_ID.get();
		if (serverIdStr == null || serverIdStr.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(serverIdStr);
		} catch (IllegalArgumentException e) {
			PolystirolUtility.LOGGER.error("Неверный формат serverId в конфиге: {}", serverIdStr);
			return null;
		}
	}

	/**
	 * Получает UUID сервера для сбора ресурсов из конфига
	 * @return UUID строка или null, если неверный формат
	 */
	public static String getServerUuid() {
		String serverUuidStr = SERVER_UUID.get();
		if (serverUuidStr == null || serverUuidStr.isEmpty()) {
			return null;
		}
		// Проверяем формат UUID (36 символов с дефисами)
		if (serverUuidStr.length() != 36) {
			PolystirolUtility.LOGGER.error("Неверный формат serverUuid в конфиге (должно быть 36 символов): {}", serverUuidStr);
			return null;
		}
		// Проверяем, что это валидный UUID
		try {
			UUID.fromString(serverUuidStr);
			return serverUuidStr;
		} catch (IllegalArgumentException e) {
			PolystirolUtility.LOGGER.error("Неверный формат serverUuid в конфиге: {}", serverUuidStr);
			return null;
		}
	}
}

