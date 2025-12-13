package com.example.polystirolbagdes;

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
			polystirolbagdes.LOGGER.error("Неверный формат serverId в конфиге: {}", serverIdStr);
			return null;
		}
	}
}
