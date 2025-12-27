package com.example.polystirolutility.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class AFKManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(AFKManager.class);
	private static final long AFK_TIMEOUT_MS = 5 * 60 * 1000; // 5 минут
	private static final long AFK_CHECK_INTERVAL_MS = 1000; // Проверка каждую секунду

	// Хранение времени последней активности
	private final Map<UUID, Long> lastActivityTimes = new ConcurrentHashMap<>();
	// Хранение последней позиции для проверки движения
	private final Map<UUID, PlayerLocation> lastLocations = new HashMap<>();
	// Хранение AFK игроков
	private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
	
	private final MinecraftServer server;
	private long lastCheckTime = 0;
	private int baseSleepPercentage = -1; // -1 значит еще не инициализировано

	public AFKManager(MinecraftServer server) {
		this.server = server;
	}

	public void register(net.neoforged.bus.api.IEventBus eventBus) {
		eventBus.register(this);
	}

	@SubscribeEvent
	public void onServerTick(ServerTickEvent.Post event) {
		long now = System.currentTimeMillis();
		if (now - lastCheckTime < AFK_CHECK_INTERVAL_MS) {
			return;
		}
		lastCheckTime = now;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			checkPlayerActivity(player, now);
		}
		
		updateSleepGamerule();
	}
	
	@SubscribeEvent
	public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			updateActivity(player);
		}
	}
	
	@SubscribeEvent
	public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID uuid = event.getEntity().getUUID();
		lastActivityTimes.remove(uuid);
		lastLocations.remove(uuid);
		if (afkPlayers.contains(uuid)) {
			afkPlayers.remove(uuid);
			TabIntegration.setAfkStatus(uuid, false);
			// Пересчет сна будет на следующем тике
		}
	}

	private void checkPlayerActivity(ServerPlayer player, long now) {
		UUID uuid = player.getUUID();
		
		// Проверка движения/вращения
		PlayerLocation currentLocation = new PlayerLocation(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		PlayerLocation lastLocation = lastLocations.get(uuid);
		
		if (lastLocation == null || !currentLocation.equals(lastLocation)) {
			// Игрок двигался
			updateActivity(player);
			lastLocations.put(uuid, currentLocation);
			
			// Если был AFK, снимаем статус (если это не проверка сразу после входа)
			if (afkPlayers.contains(uuid) && lastLocation != null) {
				setAfk(player, false);
				player.sendSystemMessage(Component.literal("§7Вы вернулись. Статус AFK снят."));
			}
		} else {
			// Игрок стоит на месте
			Long lastActive = lastActivityTimes.get(uuid);
			if (lastActive != null && !afkPlayers.contains(uuid)) {
				if (now - lastActive > AFK_TIMEOUT_MS) {
					setAfk(player, true);
					player.sendSystemMessage(Component.literal("§7Вы теперь §8AFK §7(5 минут бездействия)."));
					broadcastAfkMessage(player, true);
				}
			}
		}
	}

	public void updateActivity(ServerPlayer player) {
		lastActivityTimes.put(player.getUUID(), System.currentTimeMillis());
	}

	public void toggleAfk(ServerPlayer player) {
		boolean isAfk = afkPlayers.contains(player.getUUID());
		setAfk(player, !isAfk);
		
		if (!isAfk) {
			player.sendSystemMessage(Component.literal("§7Вы теперь §8AFK§7."));
			broadcastAfkMessage(player, true);
		} else {
			player.sendSystemMessage(Component.literal("§7Вы больше не §8AFK§7."));
			broadcastAfkMessage(player, false);
			// Обновляем время активности, чтобы не выкинуло обратно в AFK сразу
			updateActivity(player);
		}
	}

	public void setAfk(ServerPlayer player, boolean afk) {
		UUID uuid = player.getUUID();
		if (afk) {
			afkPlayers.add(uuid);
		} else {
			afkPlayers.remove(uuid);
			updateActivity(player);
		}
		
		TabIntegration.setAfkStatus(uuid, afk);
		// Обновление gamerule происходит в тике
	}

	private void broadcastAfkMessage(ServerPlayer player, boolean isAfk) {
		String message = isAfk 
				? "§f" + player.getName().getString() + " §7ушел в AFK" 
				: "§f" + player.getName().getString() + " §7вернулся";
		server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	}

	private void updateSleepGamerule() {
		GameRules gameRules = server.getGameRules();
		GameRules.IntegerValue sleepPercentageRule = gameRules.getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
		
		// Инициализация базового значения
		if (baseSleepPercentage == -1) {
			if (afkPlayers.isEmpty()) {
				baseSleepPercentage = sleepPercentageRule.get();
				// LOGGER.info("Base sleep percentage captured: {}", baseSleepPercentage);
			} else {
				// Если модуль загрузился, а уже есть AFK (перезагрузка?), берем 50 как safe default или 100
				baseSleepPercentage = 50; 
			}
		}
		
		int totalPlayers = server.getPlayerList().getPlayerCount();
		if (totalPlayers == 0) return;
		
		int afkCount = afkPlayers.size();
		int activePlayers = totalPlayers - afkCount;
		
		int targetPercentage;
		
		if (afkCount == 0) {
			// Восстанавливаем оригинальное значение
			targetPercentage = baseSleepPercentage;
		} else if (activePlayers == 0) {
			// Все AFK. Пусть будет 100 (или 0), но чтобы скипнуть, все равно нужен кто-то в кровати.
			// Если 100%, то никто не скипнет. Если 0%, то первый спать скипнет?
			// Логично оставить basePercentage, так как спать некому.
			targetPercentage = baseSleepPercentage;
		} else {
			// Формула: (base * active) / total
			// Пример: base=50, total=10, afk=5, active=5. target = (50 * 5) / 10 = 25.
			// Проверка: 25% от 10 = 3 чел. 50% от 5 = 2.5 -> 3 чел. Совпадает.
			targetPercentage = (baseSleepPercentage * activePlayers) / totalPlayers;
			
			// Защита от 0, если 1 активный из 100 и база 10%. (10 * 1) / 100 = 0.
			// Minecraft gamerule 0% обычно работает как "1 active sleeper".
			if (targetPercentage < 1 && baseSleepPercentage > 0) {
				targetPercentage = 1; 
			}
		}
		
		if (sleepPercentageRule.get() != targetPercentage) {
			sleepPercentageRule.set(targetPercentage, server);
			// LOGGER.debug("Updated sleep percentage to {} (Active: {}/{})", targetPercentage, activePlayers, totalPlayers);
		}
	}

	private static class PlayerLocation {
		final double x, y, z;
		final float yRot, xRot;

		PlayerLocation(double x, double y, double z, float yRot, float xRot) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.yRot = yRot;
			this.xRot = xRot;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			PlayerLocation that = (PlayerLocation) o;
			return Double.compare(that.x, x) == 0 &&
					Double.compare(that.y, y) == 0 &&
					Double.compare(that.z, z) == 0 &&
					Float.compare(that.yRot, yRot) == 0 &&
					Float.compare(that.xRot, xRot) == 0;
		}
	}
}
