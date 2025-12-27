package com.example.polystirolutility.neoforge;

import java.lang.reflect.Method;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.polystirolutility.core.Badge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public class TabIntegration {
	private static final Logger LOGGER = LoggerFactory.getLogger(TabIntegration.class);
	private static Object tabApi;
	private static Object nameTagManager;
	private static boolean initialized = false;
	private static boolean tabAvailable = false;
	private static MinecraftServer server;

	/**
	 * Инициализирует интеграцию с TAB модом через рефлексию (ленивая инициализация)
	 * @return true, если TAB мод найден и инициализирован
	 */
	private static boolean ensureInitialized() {
		// Если уже успешно инициализирован, возвращаем true
		if (initialized && tabAvailable) {
			return true;
		}
		
		// Если предыдущая попытка не удалась, пробуем снова
		if (initialized && !tabAvailable) {
			initialized = false; // Сбрасываем флаг для повторной попытки
		}
		
		initialized = true;

		try {
			// Проверяем наличие TAB мода через рефлексию
			Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
			Method getInstanceMethod = tabApiClass.getMethod("getInstance");
			tabApi = getInstanceMethod.invoke(null);
			
			if (tabApi == null) {
				LOGGER.error("TAB API не доступен (getInstance вернул null)");
				tabAvailable = false;
				return false;
			}
			
			Method getNameTagManagerMethod = tabApiClass.getMethod("getNameTagManager");
			nameTagManager = getNameTagManagerMethod.invoke(tabApi);
			
			if (nameTagManager == null) {
				LOGGER.error("NameTagManager не доступен (getNameTagManager вернул null)");
				tabAvailable = false;
				return false;
			}
			
			tabAvailable = true;
			return true;
		} catch (ClassNotFoundException e) {
			LOGGER.error("TAB мод не найден (ClassNotFoundException): {}. Бэйджики не будут отображаться в префиксах.", e.getMessage(), e);
			// Сбрасываем флаг, чтобы можно было повторить попытку позже
			initialized = false;
			tabAvailable = false;
			return false;
		} catch (NoSuchMethodException e) {
			LOGGER.error("Метод не найден в TAB API: {}", e.getMessage(), e);
			initialized = false;
			tabAvailable = false;
			return false;
		} catch (Exception e) {
			LOGGER.error("Ошибка при инициализации TAB интеграции: {}", e.getMessage(), e);
			initialized = false;
			tabAvailable = false;
			return false;
		}
	}

	// Кэш для хранения бэйджиков игроков до их загрузки в TAB
	private static final java.util.Map<UUID, Badge> pendingBadges = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Сохраняет бэйджик для установки после загрузки игрока в TAB
	 */
	public static void setPendingBadge(UUID playerUuid, Badge badge) {
		if (badge != null) {
			pendingBadges.put(playerUuid, badge);
		} else {
			pendingBadges.remove(playerUuid);
		}
		// Обновляем префикс, если игрок в сети и TAB доступен
		refreshPlayerPrefix(playerUuid);
	}

	// Хранение AFK статуса игроков
	private static final java.util.Set<UUID> afkPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Устанавливает AFK статус для игрока
	 */
	public static void setAfkStatus(UUID playerUuid, boolean isAfk) {
		if (isAfk) {
			afkPlayers.add(playerUuid);
		} else {
			afkPlayers.remove(playerUuid);
		}
		refreshPlayerPrefix(playerUuid);
	}

	private static boolean eventBusRegistered = false;

	/**
	 * Регистрирует обработчик событий TAB для установки префикса после загрузки игрока
	 */
	public static void registerTabEventListener() {
		if (eventBusRegistered) {
			return;
		}
		
		// Пробуем инициализировать TAB
		if (!ensureInitialized()) {
			LOGGER.warn("TAB не инициализирован, откладываем регистрацию EventBus");
			return;
		}

		try {
			// Получаем EventBus из TabAPI
			Method getEventBusMethod = tabApi.getClass().getMethod("getEventBus");
			Object eventBus = getEventBusMethod.invoke(tabApi);
			
			if (eventBus == null) {
				LOGGER.error("TAB EventBus не доступен (вернул null)");
				return;
			}
			
			// Получаем класс PlayerLoadEvent
			Class<?> playerLoadEventClass = Class.forName("me.neznamy.tab.api.event.player.PlayerLoadEvent");
			
			// Пробуем найти метод register с разными сигнатурами
			Method registerMethod = null;
			try {
				// Вариант 1: register(Class, Consumer)
				registerMethod = eventBus.getClass().getMethod("register", Class.class, java.util.function.Consumer.class);
			} catch (NoSuchMethodException e) {
				try {
					// Вариант 2: register(Class, Object) - может быть функциональный интерфейс
					registerMethod = eventBus.getClass().getMethod("register", Class.class, Object.class);
				} catch (NoSuchMethodException e2) {
					// Пробуем найти любой метод register
					Method[] methods = eventBus.getClass().getMethods();
					for (Method method : methods) {
						if (method.getName().equals("register") && method.getParameterCount() == 2) {
							registerMethod = method;
							break;
						}
					}
				}
			}
			
			if (registerMethod == null) {
				LOGGER.error("Не удалось найти метод register в EventBus");
				return;
			}
			
			// Получаем интерфейс EventHandler
			Class<?> eventHandlerInterface = Class.forName("me.neznamy.tab.api.event.EventHandler");
			
			// Создаем обработчик события через динамический прокси
			Object eventHandler = java.lang.reflect.Proxy.newProxyInstance(
					eventHandlerInterface.getClassLoader(),
					new Class<?>[]{eventHandlerInterface},
					(java.lang.reflect.InvocationHandler) (proxy, method, args) -> {
						// Если метод принимает параметры, это скорее всего обработчик события
						if (args != null && args.length > 0) {
							Object event = args[0];
							// Проверяем, что это PlayerLoadEvent
							if (playerLoadEventClass.isInstance(event)) {
								try {
									// Получаем TabPlayer из события
									Method getPlayerMethod = playerLoadEventClass.getMethod("getPlayer");
									Object tabPlayer = getPlayerMethod.invoke(event);
									
									if (tabPlayer == null) {
										return null;
									}
									
									// Получаем UUID игрока из TabPlayer
									Method getUniqueIdMethod = tabPlayer.getClass().getMethod("getUniqueId");
									UUID playerUuid = (UUID) getUniqueIdMethod.invoke(tabPlayer);
									
									// Проверяем, есть ли ожидающий бэйджик или AFK статус
									Badge pendingBadge = pendingBadges.get(playerUuid);
									boolean isAfk = afkPlayers.contains(playerUuid);
									
									if (pendingBadge != null || isAfk) {
										setPlayerPrefixDirectly(tabPlayer, pendingBadge, isAfk);
									} else {
										// Планируем повторную проверку через 1 секунду
										java.util.concurrent.ScheduledExecutorService scheduler = 
												java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
													Thread t = new Thread(r, "TabIntegration-DelayedCheck");
													t.setDaemon(true);
													return t;
												});
										
										scheduler.schedule(() -> {
											try {
												Badge delayedBadge = pendingBadges.get(playerUuid);
												boolean nowAfk = afkPlayers.contains(playerUuid);
												setPlayerPrefixDirectly(tabPlayer, delayedBadge, nowAfk);
											} finally {
												scheduler.shutdown();
											}
										}, 1, java.util.concurrent.TimeUnit.SECONDS);
									}
								} catch (Exception e) {
									LOGGER.error("Ошибка в обработчике PlayerLoadEvent: {}", e.getMessage(), e);
								}
							}
						}
						
						// Возвращаем null для void методов или методов, которые не обрабатывают события
						if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
							return null;
						}
						return null;
					}
			);
			
			// Вызываем метод register
			registerMethod.invoke(eventBus, playerLoadEventClass, eventHandler);
			
			eventBusRegistered = true;
		} catch (Exception e) {
			LOGGER.error("Не удалось зарегистрировать TAB EventBus обработчик: {}", e.getMessage(), e);
		}
	}

	/**
	 * Устанавливает префикс напрямую для TabPlayer (используется из PlayerLoadEvent)
	 * @param tabPlayer TabPlayer объект
	 * @param badge бэйджик или null
	 * @param isAfk находится ли игрок в AFK
	 */
	private static void setPlayerPrefixDirectly(Object tabPlayer, Badge badge, boolean isAfk) {
		if (!ensureInitialized()) {
			LOGGER.error("TAB не инициализирован при попытке установить префикс");
			return;
		}

		try {
			Method getNameMethod = tabPlayer.getClass().getMethod("getName");
			String playerName = (String) getNameMethod.invoke(tabPlayer);
			
			String unicodePrefix = null;
			if (badge != null) {
				String unicodeString = badge.getUnicodeString();
				if (!unicodeString.isEmpty()) {
					unicodePrefix = unicodeString;
				} else {
					LOGGER.warn("Неверный Unicode символ для бэйджика {}: {}", badge.getId(), badge.getUnicodeChar());
				}
			}
			
			// Формируем полный префикс: [AFK] + Badge
			StringBuilder finalPrefix = new StringBuilder();
			if (isAfk) {
				finalPrefix.append("&7[AFK]&r ");
			}
			if (unicodePrefix != null) {
				finalPrefix.append(unicodePrefix);
			}
			
			String resultPrefix = finalPrefix.length() > 0 ? finalPrefix.toString() : null;
			
			executeTabPrefixCommand(playerName, resultPrefix);
		} catch (Exception e) {
			LOGGER.error("Ошибка при установке префикса: {}", e.getMessage(), e);
		}
	}

	/**
	 * Устанавливает ссылку на MinecraftServer для выполнения команд
	 */
	public static void setServer(MinecraftServer server) {
		TabIntegration.server = server;
	}
	
	/**
	 * Устанавливает префикс для игрока через выполнение команды TAB
	 * @param playerName имя игрока
	 * @param unicodePrefix Unicode символ префикса или null для удаления
	 * @return true, если команда выполнена успешно
	 */
	private static boolean executeTabPrefixCommand(String playerName, String unicodePrefix) {
		if (server == null) {
			LOGGER.warn("MinecraftServer не установлен, не могу выполнить команду TAB");
			return false;
		}
		
		try {
			CommandSourceStack source = server.createCommandSourceStack().withPermission(4);
			String command = unicodePrefix != null 
				? String.format("tab player %s tabprefix %s", playerName, unicodePrefix)
				: String.format("tab player %s tabprefix", playerName);
			
			server.getCommands().performPrefixedCommand(source, command);
			return true;
		} catch (Exception e) {
			LOGGER.error("Ошибка при выполнении команды TAB: {}", e.getMessage(), e);
			return false;
		}
	}
	
	/**
	 * Устанавливает префикс для игрока на основе бэйджика (сохраняет в кэш для установки через PlayerLoadEvent)
	 * @param playerUuid UUID игрока
	 * @param badge бэйджик или null для удаления префикса
	 */
	public static void setPlayerPrefix(UUID playerUuid, Badge badge) {
		// Пробуем зарегистрировать EventBus, если еще не зарегистрирован
		if (!eventBusRegistered) {
			registerTabEventListener();
		}
		
		// Всегда сохраняем бэйджик в кэш - установка будет через PlayerLoadEvent
		if (badge != null) {
			pendingBadges.put(playerUuid, badge);
		} else {
			pendingBadges.remove(playerUuid);
		}
		
		refreshPlayerPrefix(playerUuid);
	}

	/**
	 * Обновляет префикс игрока на основе его Badge и AFK статуса
	 */
	private static void refreshPlayerPrefix(UUID playerUuid) {
		// Пробуем установить сразу, если игрок уже загружен в TAB
		if (ensureInitialized()) {
			try {
				Method getPlayerMethod = tabApi.getClass().getMethod("getPlayer", UUID.class);
				Object tabPlayer = getPlayerMethod.invoke(tabApi, playerUuid);
				if (tabPlayer != null) {
					Badge badge = pendingBadges.get(playerUuid);
					boolean isAfk = afkPlayers.contains(playerUuid);
					setPlayerPrefixDirectly(tabPlayer, badge, isAfk);
				}
			} catch (Exception e) {
				// Игнорируем ошибки получения игрока из TAB
			}
		}
	}

	/**
	 * Проверяет, доступна ли TAB интеграция
	 */
	public static boolean isAvailable() {
		return ensureInitialized();
	}
}

