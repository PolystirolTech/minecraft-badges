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
	private BadgeEventHandler eventHandler;

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

			// Инициализируем кэш
			long cacheTtl = Config.CACHE_TTL_SECONDS.get();
			BadgeCache cache = new BadgeCache(cacheTtl);

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
			} else {
				LOGGER.warn("serverId не настроен в конфиге, Resource Pack Manager не будет работать");
			}

			// Регистрируем обработчик событий (TAB интеграция будет инициализирована при первом использовании)
			BadgeEventHandler eventHandler = new BadgeEventHandler(badgeService);
			NeoForge.EVENT_BUS.register(eventHandler);
			
			// Сохраняем ссылку
			this.eventHandler = eventHandler;
			
			// Пробуем зарегистрировать TAB EventBus обработчик (может не получиться, если TAB еще не загружен)
			TabIntegration.registerTabEventListener();

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
		// Resource pack функционал отключен - управляется вручную через server.properties
		// ResourcePackHandler и команды не инициализируются
		
		// Сохраняем ссылку на сервер для выполнения команд TAB
		MinecraftServer server = event.getServer();
		TabIntegration.setServer(server);
		
		// Пробуем зарегистрировать TAB EventBus обработчик при старте сервера
		// (TAB должен быть загружен к этому моменту)
		TabIntegration.registerTabEventListener();
	}
}
