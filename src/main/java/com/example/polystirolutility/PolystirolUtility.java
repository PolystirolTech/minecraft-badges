package com.example.polystirolutility;

import java.util.UUID;

import org.slf4j.Logger;

import com.example.polystirolutility.core.BadgeApiClient;
import com.example.polystirolutility.core.BadgeCache;
import com.example.polystirolutility.core.BadgeService;
import com.example.polystirolutility.core.ResourceCollectionApiClient;
import com.example.polystirolutility.core.ResourceCollectionService;
import com.example.polystirolutility.core.ResourcePackManager;
import com.example.polystirolutility.neoforge.BadgeEventHandler;
import com.example.polystirolutility.neoforge.ResourceCollectionCommands;
import com.example.polystirolutility.neoforge.TabIntegration;
import com.example.polystirolutility.neoforge.AFKManager;
import com.example.polystirolutility.neoforge.AFKCommand;
import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(PolystirolUtility.MODID)
public class PolystirolUtility {
	public static final String MODID = "polystirolutility";
	public static final Logger LOGGER = LogUtils.getLogger();

	private BadgeService badgeService;
	private ResourcePackManager resourcePackManager;
	private ResourceCollectionApiClient resourceCollectionApiClient;
	private ResourceCollectionService resourceCollectionService;
	private ResourceCollectionCommands resourceCollectionCommands;
	private AFKManager afkManager;


	public PolystirolUtility(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::commonSetup);

		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

		NeoForge.EVENT_BUS.register(this);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			LOGGER.info("Инициализация Polystirol Utility Mod...");

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
				
				resourcePackManager.startPeriodicCheck(() -> {
					// Resource pack функционал отключен - управляется вручную
				});
			} else {
				LOGGER.warn("serverId не настроен в конфиге, Resource Pack Manager не будет работать");
			}

			// Регистрируем обработчик событий (TAB интеграция будет инициализирована при первом использовании)
			BadgeEventHandler eventHandler = new BadgeEventHandler(badgeService);
			NeoForge.EVENT_BUS.register(eventHandler);
			
			// Пробуем зарегистрировать TAB EventBus обработчик (может не получиться, если TAB еще не загружен)
			TabIntegration.registerTabEventListener();

			// Инициализируем API клиент для сбора ресурсов
			resourceCollectionApiClient = new ResourceCollectionApiClient(apiBaseUrl);

			// Инициализируем сервис сбора ресурсов
			String serverUuid = Config.getServerUuid();
			if (serverUuid != null) {
				resourceCollectionService = new ResourceCollectionService(resourceCollectionApiClient, serverUuid);
				LOGGER.info("ResourceCollectionService инициализирован для serverUuid: {}", serverUuid);
			} else {
				LOGGER.warn("serverUuid не настроен в конфиге, функционал сбора ресурсов не будет работать");
			}

			LOGGER.info("Polystirol Utility Mod успешно инициализирован!");
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
		// Сохраняем ссылку на сервер для выполнения команд TAB
		MinecraftServer server = event.getServer();
		TabIntegration.setServer(server);
		
		// Пробуем зарегистрировать TAB EventBus обработчик при старте сервера
		// (TAB должен быть загружен к этому моменту)
		TabIntegration.registerTabEventListener();

		// Регистрируем команды для сбора ресурсов (всегда регистрируем, проверка будет при выполнении)
		resourceCollectionCommands = new ResourceCollectionCommands(server, Config.getServerUuid());
		if (resourceCollectionService != null) {
			resourceCollectionCommands.setCollectionService(resourceCollectionService);
		} else {
			LOGGER.warn("serverUuid не настроен в конфиге, команда /collect будет доступна, но функционал не будет работать");
		}
		// NOTE: команды регистрируются в onRegisterCommands для поддержки /reload
		// Регистрируем обработчик событий для закрытия GUI
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(resourceCollectionCommands);
		LOGGER.info("Команда /collect зарегистрирована");
		
		// Инициализация AFK модуля
		afkManager = new AFKManager(server);
		// Регистрируем обработчик событий AFK (тиков и игроков)
		afkManager.register(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
		
		// NOTE: команды регистрируются в onRegisterCommands для поддержки /reload
		LOGGER.info("AFK модуль инициализирован");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		// Регистрируем команды при старте сервера и после /reload
		if (resourceCollectionCommands != null) {
			resourceCollectionCommands.register(event.getDispatcher());
		}
		if (afkManager != null) {
			new AFKCommand(afkManager).register(event.getDispatcher());
		}
	}
}

