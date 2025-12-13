package com.example.polystirolbagdes.neoforge;

import java.lang.reflect.Method;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.polystirolbagdes.core.Badge;

public class TabIntegration {
	private static final Logger LOGGER = LoggerFactory.getLogger(TabIntegration.class);
	private static Object tabApi;
	private static Object nameTagManager;
	private static boolean initialized = false;
	private static boolean tabAvailable = false;

	/**
	 * Инициализирует интеграцию с TAB модом через рефлексию (ленивая инициализация)
	 * @return true, если TAB мод найден и инициализирован
	 */
	private static boolean ensureInitialized() {
		if (initialized) {
			return tabAvailable;
		}
		initialized = true;

		try {
			// Проверяем наличие TAB мода через рефлексию
			Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
			Method getInstanceMethod = tabApiClass.getMethod("getInstance");
			tabApi = getInstanceMethod.invoke(null);
			
			if (tabApi == null) {
				LOGGER.error("TAB API не доступен");
				return false;
			}
			
			Method getNameTagManagerMethod = tabApiClass.getMethod("getNameTagManager");
			nameTagManager = getNameTagManagerMethod.invoke(tabApi);
			
			if (nameTagManager == null) {
				LOGGER.error("NameTagManager не доступен");
				return false;
			}
			
			tabAvailable = true;
			LOGGER.info("TAB интеграция успешно инициализирована");
			return true;
		} catch (ClassNotFoundException e) {
			LOGGER.warn("TAB мод не найден. Бэйджики не будут отображаться в префиксах.");
			tabAvailable = false;
			return false;
		} catch (Exception e) {
			LOGGER.error("Ошибка при инициализации TAB интеграции: {}", e.getMessage());
			tabAvailable = false;
			return false;
		}
	}

	/**
	 * Устанавливает префикс для игрока на основе бэйджика
	 * @param playerUuid UUID игрока
	 * @param badge бэйджик или null для удаления префикса
	 */
	public static void setPlayerPrefix(UUID playerUuid, Badge badge) {
		if (!ensureInitialized()) {
			return;
		}

		try {
			// Получаем игрока через рефлексию
			Method getPlayerMethod = tabApi.getClass().getMethod("getPlayer", UUID.class);
			Object tabPlayer = getPlayerMethod.invoke(tabApi, playerUuid);
			
			if (tabPlayer == null) {
				LOGGER.debug("Игрок {} не найден в TAB", playerUuid);
				return;
			}

			// Устанавливаем префикс через рефлексию
			Method setPrefixMethod = nameTagManager.getClass().getMethod("setPrefix", tabPlayer.getClass(), String.class);
			
			if (badge == null) {
				// Удаляем префикс
				setPrefixMethod.invoke(nameTagManager, tabPlayer, "");
				LOGGER.debug("Префикс удален для игрока {}", playerUuid);
			} else {
				// Устанавливаем префикс с Unicode символом бэйджика
				String unicodeString = badge.getUnicodeString();
				if (unicodeString.isEmpty()) {
					LOGGER.warn("Неверный Unicode символ для бэйджика {}: {}", badge.getId(), badge.getUnicodeChar());
					return;
				}
				setPrefixMethod.invoke(nameTagManager, tabPlayer, unicodeString);
				LOGGER.debug("Префикс установлен для игрока {}: {}", playerUuid, badge.getName());
			}
		} catch (Exception e) {
			LOGGER.error("Ошибка при установке префикса для игрока {}: {}", playerUuid, e.getMessage());
		}
	}

	/**
	 * Проверяет, доступна ли TAB интеграция
	 */
	public static boolean isAvailable() {
		return ensureInitialized();
	}
}

