package com.example.polystirolbagdes.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
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
			// Создаем пакет для отправки resource pack
			// required = true означает принудительную загрузку (игрок не может отказаться)
			// В Minecraft 1.21.1 конструктор: (String url, Optional<byte[]> hash, boolean required, Component prompt)
			java.util.Optional<byte[]> hashOptional = hashBytes != null ? java.util.Optional.of(hashBytes) : java.util.Optional.empty();
			ClientboundResourcePackPacket packet = new ClientboundResourcePackPacket(
					resourcePackUrl,
					hashOptional,
					true, // required - принудительная загрузка
					Component.literal("Badges Resource Pack") // prompt message
			);

			player.connection.send(packet);
			LOGGER.debug("Resource pack отправлен игроку {}", player.getName().getString());
		} catch (Exception e) {
			LOGGER.error("Ошибка при отправке resource pack игроку {}: {}", player.getName().getString(), e.getMessage());
		}
	}

	/**
	 * Отправляет resource pack новому игроку при входе
	 */
	public void onPlayerJoin(ServerPlayer player) {
		if (currentResourcePackUrl != null && !currentResourcePackUrl.isEmpty()) {
			sendResourcePackToPlayer(player, currentResourcePackUrl, currentResourcePackHash);
		}
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

