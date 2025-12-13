package com.example.polystirolbagdes.neoforge;

import java.lang.reflect.Constructor;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

public class ResourcePackHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackHandler.class);
	private final MinecraftServer server;
	private String currentResourcePackUrl;
	private String currentResourcePackHash;

	public ResourcePackHandler(MinecraftServer server) {
		this.server = server;
	}

	/**
	 * Принудительно отправляет resource pack всем игрокам на сервере
	 * @param resourcePackUrl URL resource pack
	 * @param resourcePackHash SHA256 hash resource pack (может быть null)
	 */
	public void sendResourcePackToAllPlayers(String resourcePackUrl, String resourcePackHash) {
		if (resourcePackUrl == null || resourcePackUrl.isEmpty()) {
			LOGGER.warn("Resource pack URL пуст, не отправляем игрокам");
			return;
		}

		currentResourcePackUrl = resourcePackUrl;
		currentResourcePackHash = resourcePackHash;

		PlayerList playerList = server.getPlayerList();
		byte[] hashBytes = resourcePackHash != null ? hexStringToByteArray(resourcePackHash) : null;

		LOGGER.info("Отправка resource pack всем игрокам: {}", resourcePackUrl);

		for (ServerPlayer player : playerList.getPlayers()) {
			sendResourcePackToPlayer(player, resourcePackUrl, hashBytes);
		}
	}

	/**
	 * Принудительно отправляет resource pack конкретному игроку
	 * @param player игрок
	 * @param resourcePackUrl URL resource pack
	 * @param resourcePackHash SHA256 hash resource pack (может быть null)
	 */
	public void sendResourcePackToPlayer(ServerPlayer player, String resourcePackUrl, String resourcePackHash) {
		byte[] hashBytes = resourcePackHash != null ? hexStringToByteArray(resourcePackHash) : null;
		sendResourcePackToPlayer(player, resourcePackUrl, hashBytes);
	}

	private void sendResourcePackToPlayer(ServerPlayer player, String resourcePackUrl, byte[] hashBytes) {
		try {
			// В Minecraft 1.21.1 NeoForge используем пакет напрямую через connection
			// Пробуем найти класс пакета через разные варианты имен
			Class<?> packetClass = null;
			String[] possibleClassNames = {
					"net.minecraft.network.protocol.game.ClientboundResourcePackPacket",
					"net.minecraft.network.protocol.common.ClientboundResourcePackPacket",
					"net.minecraft.network.protocol.game.ClientboundResourcePackPopPacket"
			};
			
			for (String className : possibleClassNames) {
				try {
					packetClass = Class.forName(className);
					break;
				} catch (ClassNotFoundException e) {
					continue;
				}
			}
			
			if (packetClass == null) {
				LOGGER.error("Не удалось найти класс пакета resource pack. Пробуем альтернативный способ.");
				LOGGER.error("Не удалось найти класс пакета resource pack. Возможно, изменился API.");
				return;
			}
			
			Optional<byte[]> hashOptional = hashBytes != null ? Optional.of(hashBytes) : Optional.empty();
			Component prompt = Component.literal("Badges Resource Pack");
			
			// Пробуем разные варианты конструктора
			Constructor<?> constructor = null;
			Object[] constructorArgs = null;
			
			try {
				// Вариант 1: (String, Optional<byte[]>, boolean, Component)
				constructor = packetClass.getConstructor(String.class, Optional.class, boolean.class, Component.class);
				constructorArgs = new Object[]{resourcePackUrl, hashOptional, true, prompt};
			} catch (NoSuchMethodException e) {
				try {
					// Вариант 2: (String, byte[], boolean, Component)
					constructor = packetClass.getConstructor(String.class, byte[].class, boolean.class, Component.class);
					constructorArgs = new Object[]{resourcePackUrl, hashBytes, true, prompt};
				} catch (NoSuchMethodException e2) {
					try {
						// Вариант 3: (String, Optional<String>, boolean, Component) - hash как строка
						if (hashBytes != null) {
							String hashString = bytesToHex(hashBytes);
							Optional<String> hashStringOptional = Optional.of(hashString);
							constructor = packetClass.getConstructor(String.class, Optional.class, boolean.class, Component.class);
							constructorArgs = new Object[]{resourcePackUrl, hashStringOptional, true, prompt};
						}
					} catch (NoSuchMethodException e3) {
						LOGGER.error("Не удалось найти подходящий конструктор для пакета resource pack");
						return;
					}
				}
			}
			
			Object packet = constructor.newInstance(constructorArgs);
			
			// Отправляем пакет через connection
			Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
			java.lang.reflect.Method sendMethod = player.connection.getClass().getMethod("send", packetInterface);
			sendMethod.invoke(player.connection, packet);
		} catch (Exception e) {
			LOGGER.error("Ошибка при отправке resource pack игроку {}: {}", player.getName().getString(), e.getMessage(), e);
		}
	}
	
	/**
	 * Конвертирует byte array в hex строку
	 */
	private String bytesToHex(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		StringBuilder result = new StringBuilder();
		for (byte b : bytes) {
			result.append(String.format("%02x", b));
		}
		return result.toString();
	}

	/**
	 * Отправляет resource pack новому игроку при входе
	 */
	public void onPlayerJoin(ServerPlayer player) {
		// Если resource pack уже загружен, отправляем его сразу
		if (currentResourcePackUrl != null && !currentResourcePackUrl.isEmpty()) {
			sendResourcePackToPlayer(player, currentResourcePackUrl, currentResourcePackHash);
		}
		// Если resource pack еще не загружен, загружаем его из API
		// Это будет обработано через ResourcePackManager при первой проверке
	}

	/**
	 * Конвертирует hex строку в byte array
	 */
	private byte[] hexStringToByteArray(String hex) {
		if (hex == null || hex.isEmpty()) {
			return null;
		}
		int len = hex.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
					+ Character.digit(hex.charAt(i + 1), 16));
		}
		return data;
	}
}

