package com.example.polystirolbagdes;

import java.util.UUID;

import org.slf4j.Logger;

import com.example.polystirolbagdes.core.BadgeApiClient;
import com.example.polystirolbagdes.core.BadgeCache;
import com.example.polystirolbagdes.core.BadgeService;
import com.example.polystirolbagdes.core.ResourcePackManager;
import com.example.polystirolbagdes.neoforge.BadgeCommands;
import com.example.polystirolbagdes.neoforge.BadgeEventHandler;
import com.example.polystirolbagdes.neoforge.ResourcePackHandler;
import com.example.polystirolbagdes.neoforge.TabIntegration;
import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(polystirolbagdes.MODID)
public class polystirolbagdes {
	public static final String MODID = "polystirolbagdes";
	public static final Logger LOGGER = LogUtils.getLogger();

	private BadgeService badgeService;
	private ResourcePackManager resourcePackManager;
	private ResourcePackHandler resourcePackHandler;
	private BadgeCommands badgeCommands;

	public polystirolbagdes(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::commonSetup);

		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

		NeoForge.EVENT_BUS.register(this);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			LOGGER.info("Инициализация Minecraft Badges Mod...");

			// Инициализируем API клиент
			String apiBaseUrl = Config.API_BASE_URL.get();
			BadgeApiClient apiClient = new BadgeApiClient(apiBaseUrl);
			LOGGER.info("API клиент инициализирован с URL: {}", apiBaseUrl);

			// Инициализируем кэш
			long cacheTtl = Config.CACHE_TTL_SECONDS.get();
			BadgeCache cache = new BadgeCache(cacheTtl);
			LOGGER.info("Кэш инициализирован с TTL: {} секунд", cacheTtl);

			// Инициализируем сервис
			badgeService = new BadgeService(apiClient, cache);

			// Инициализируем Resource Pack Manager
			UUID serverId = Config.getServerId();
			if (serverId != null) {
				long checkInterval = Config.RESOURCE_PACK_CHECK_INTERVAL.get();
				resourcePackManager = new ResourcePackManager(apiClient, serverId, checkInterval);
				
				// ResourcePackHandler будет инициализирован при старте сервера
				// так как нужен доступ к MinecraftServer
				
				resourcePackManager.startPeriodicCheck(() -> {
					LOGGER.info("Resource pack обновлен, отправка всем игрокам...");
					if (resourcePackHandler != null) {
						resourcePackManager.getServerInfo()
								.thenAccept(serverInfo -> {
									if (serverInfo != null) {
										String url = serverInfo.getResourcePackUrl();
										String hash = serverInfo.getResourcePackHash();
										if (url != null && !url.isEmpty()) {
											resourcePackHandler.sendResourcePackToAllPlayers(url, hash);
										}
									}
								});
					}
				});
				LOGGER.info("Resource Pack Manager инициализирован для сервера: {}", serverId);
			} else {
				LOGGER.warn("serverId не настроен в конфиге, Resource Pack Manager не будет работать");
			}

			// Регистрируем обработчик событий (TAB интеграция будет инициализирована при первом использовании)
			// resourcePackHandler будет установлен при старте сервера
			NeoForge.EVENT_BUS.register(new BadgeEventHandler(badgeService, null));

			LOGGER.info("Minecraft Badges Mod успешно инициализирован!");
		});
	}

	public BadgeService getBadgeService() {
		return badgeService;
	}

	public ResourcePackManager getResourcePackManager() {
		return resourcePackManager;
	}

	@SubscribeEvent
	public void onServerStarting(ServerStartingEvent event) {
		MinecraftServer server = event.getServer();
		
		// Инициализируем ResourcePackHandler при старте сервера
		resourcePackHandler = new ResourcePackHandler(server);
		
		// Загружаем resource pack при старте сервера
		if (resourcePackManager != null) {
			resourcePackManager.getServerInfo()
					.thenAccept(serverInfo -> {
						if (serverInfo != null) {
							String url = serverInfo.getResourcePackUrl();
							String hash = serverInfo.getResourcePackHash();
							if (url != null && !url.isEmpty()) {
								LOGGER.info("Загрузка resource pack при старте сервера: {}", url);
								resourcePackHandler.sendResourcePackToAllPlayers(url, hash);
							}
						}
					});
		}
		
		// Регистрируем команды
		if (badgeCommands == null && resourcePackManager != null) {
			badgeCommands = new BadgeCommands(resourcePackManager, resourcePackHandler);
		}
		if (badgeCommands != null) {
			badgeCommands.register(event.getServer().getCommands().getDispatcher());
			LOGGER.info("Команды badges зарегистрированы");
		}
	}
}
