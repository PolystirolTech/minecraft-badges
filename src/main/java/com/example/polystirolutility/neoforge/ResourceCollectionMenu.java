package com.example.polystirolutility.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ResourceCollectionMenu extends AbstractContainerMenu {
	// Используем стандартный MenuType, который клиент знает без установки мода
	@SuppressWarnings("unchecked")
	private static final MenuType<ResourceCollectionMenu> TYPE = (MenuType<ResourceCollectionMenu>) (Object) MenuType.GENERIC_9x3;

	private static final int CONTAINER_SIZE = 27; // 3x9 слоты для ресурсов

	private final net.minecraft.world.SimpleContainer container;
	private boolean removed = false;

	public ResourceCollectionMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new net.minecraft.world.SimpleContainer(CONTAINER_SIZE));
	}

	public ResourceCollectionMenu(int containerId, Inventory playerInventory, net.minecraft.world.SimpleContainer container) {
		// Используем стандартный MenuType для совместимости с клиентом без мода
		super(MenuType.GENERIC_9x3, containerId);
		this.container = container;

		// Слоты контейнера для ресурсов (3x9 = 27 слотов)
		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 9; ++j) {
				this.addSlot(new Slot(container, j + i * 9, 8 + j * 18, 18 + i * 18));
			}
		}

		// Слоты инвентаря игрока (3x9 = 27)
		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 9; ++j) {
				this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
			}
		}

		// Хотбар игрока (1x9 = 9)
		for (int i = 0; i < 9; ++i) {
			this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
		}
	}


	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			if (index < CONTAINER_SIZE) {
				// Из контейнера в инвентарь игрока
				if (!this.moveItemStackTo(itemstack1, CONTAINER_SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// Из инвентаря игрока в контейнер (только если это ресурс для сбора)
				if (com.example.polystirolutility.core.ResourceTypeMapper.isCollectibleResource(itemstack1.getItem())) {
					if (!this.moveItemStackTo(itemstack1, 0, CONTAINER_SIZE, false)) {
						return ItemStack.EMPTY;
					}
				} else {
					return ItemStack.EMPTY;
				}
			}

			if (itemstack1.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return itemstack;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!removed && player instanceof ServerPlayer serverPlayer) {
			removed = true;
			// Извлекаем все предметы из контейнера и обрабатываем их
			com.example.polystirolutility.PolystirolUtility.LOGGER.info("Игрок {} закрыл GUI сбора ресурсов", serverPlayer.getName().getString());
			// Обработка будет в ResourceCollectionCommands при закрытии меню
		}
		this.container.stopOpen(player);
	}

	public net.minecraft.world.SimpleContainer getContainer() {
		return container;
	}
}

