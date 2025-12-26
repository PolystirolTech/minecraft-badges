package com.example.polystirolutility.neoforge;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.polystirolutility.core.ResourceCollectionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

public class ResourceCollectionCommands {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCollectionCommands.class);
	private ResourceCollectionService collectionService;

	public ResourceCollectionCommands(MinecraftServer server, String serverUuid) {
		this.collectionService = null; // Будет инициализирован позже через setCollectionService
	}

	public void setCollectionService(ResourceCollectionService collectionService) {
		this.collectionService = collectionService;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("collect")
				.executes(this::openCollectGui)
		);
	}

	private int openCollectGui(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Эта команда доступна только игрокам"));
			return 0;
		}

		if (collectionService == null) {
			source.sendFailure(Component.literal("Функционал сбора ресурсов не настроен. Проверьте конфигурацию serverUuid."));
			return 0;
		}

		// Открываем GUI через MenuProvider
		net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(27);
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.literal("Сбор ресурсов");
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.world.entity.player.Player player) {
				return new ResourceCollectionMenu(containerId, playerInventory, container);
			}
		});

		return 1;
	}

	@SubscribeEvent
	public void onContainerClose(PlayerContainerEvent.Close event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		if (event.getContainer() instanceof ResourceCollectionMenu menu) {
			// Обрабатываем предметы из контейнера
			net.minecraft.world.SimpleContainer container = menu.getContainer();
			
			// Собираем все предметы из контейнера
			ItemStack[] items = new ItemStack[container.getContainerSize()];
			for (int i = 0; i < container.getContainerSize(); i++) {
				items[i] = container.getItem(i).copy(); // Копируем, чтобы не потерять данные при очистке (хотя мы не очищаем сразу теперь)
			}

			if (collectionService != null) {
				// Обрабатываем и отправляем на API
				Map<String, Integer> sentCounts = collectionService.processAndSendItems(items);
				
				if (!sentCounts.isEmpty()) {
					StringBuilder message = new StringBuilder("Отправлено: ");
					boolean first = true;
					for (Map.Entry<String, Integer> entry : sentCounts.entrySet()) {
						if (!first) {
							message.append(", ");
						}
						message.append(entry.getValue()).append(" ").append(entry.getKey());
						first = false;
					}
					player.sendSystemMessage(Component.literal(message.toString()));

					// Удаляем ТОЛЬКО успешно отправленные предметы
					for (int i = 0; i < container.getContainerSize(); i++) {
						ItemStack stack = container.getItem(i);
						if (!stack.isEmpty()) {
							String type = com.example.polystirolutility.core.ResourceTypeMapper.getResourceType(stack.getItem());
							if (type != null && sentCounts.containsKey(type)) {
								// Пытаемся удалить количество, которое было отправлено
								int sent = sentCounts.get(type);
								int countInStack = stack.getCount();
								
								// У нас простой случай: API подтверждает только целые типы.
								// Если в контейнере было 10 дерева в одном стаке и 5 в другом,
								// sentCounts скажет "15". Нам нужно удалить 15 дерева из контейнера.
								
								if (sent > 0) {
									int toRemove = Math.min(sent, countInStack);
									stack.shrink(toRemove);
									sentCounts.put(type, sent - toRemove); // Уменьшаем остаток для удаления
									container.setItem(i, stack); // Обновляем слот (если стало 0, станет EMPTY)
								}
							}
						}
					}
				} else {
					player.sendSystemMessage(Component.literal("Нет ресурсов для отправки (или нет активных целей)"));
				}

				// Оставшиеся предметы будут возвращены игроку автоматически, так как мы не удалили их из Container
				// Однако, ResourceCollectionMenu.removed НЕ делает этого по умолчанию для SimpleContainer.
				// Нам нужно вручную вернуть оставшиеся предметы игроку.
				
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack stack = container.getItem(i);
					if (!stack.isEmpty()) {
						if (player.getInventory().add(stack)) {
							// Успешно добавлено в инвентарь
							container.setItem(i, ItemStack.EMPTY);
						} else {
							// Инвентарь полон, выбрасываем рядом
							player.drop(stack, false);
							container.setItem(i, ItemStack.EMPTY);
						}
					}
				}
			} else {
				LOGGER.warn("ResourceCollectionService не инициализирован");
				player.sendSystemMessage(Component.literal("Ошибка: сервис не инициализирован"));
				
				// Возвращаем все предметы
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack stack = container.getItem(i);
					if (!stack.isEmpty()) {
						player.getInventory().add(stack); // Или drop
						container.setItem(i, ItemStack.EMPTY);
					}
				}
			}
		}
	}
}

