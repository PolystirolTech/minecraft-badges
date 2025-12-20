package com.example.polystirolutility.core;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ResourceTypeMapper {
	private static final Map<Item, String> ITEM_TO_RESOURCE_TYPE = new HashMap<>();

	static {
		// Дерево
		ITEM_TO_RESOURCE_TYPE.put(Items.OAK_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.BIRCH_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.SPRUCE_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.JUNGLE_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.ACACIA_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.DARK_OAK_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.MANGROVE_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.CHERRY_LOG, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.OAK_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.BIRCH_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.SPRUCE_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.JUNGLE_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.ACACIA_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.DARK_OAK_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.MANGROVE_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.CHERRY_PLANKS, "wood");
		ITEM_TO_RESOURCE_TYPE.put(Items.STICK, "wood");

		// Камень
		ITEM_TO_RESOURCE_TYPE.put(Items.STONE, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.COBBLESTONE, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.COBBLED_DEEPSLATE, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.SAND, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.RED_SAND, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.GRAVEL, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.ANDESITE, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.DIORITE, "stone");
		ITEM_TO_RESOURCE_TYPE.put(Items.GRANITE, "stone");

		// Железо
		ITEM_TO_RESOURCE_TYPE.put(Items.IRON_INGOT, "iron");
		ITEM_TO_RESOURCE_TYPE.put(Items.RAW_IRON, "iron");
		ITEM_TO_RESOURCE_TYPE.put(Items.IRON_ORE, "iron");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_IRON_ORE, "iron");

		// Золото
		ITEM_TO_RESOURCE_TYPE.put(Items.GOLD_INGOT, "gold");
		ITEM_TO_RESOURCE_TYPE.put(Items.RAW_GOLD, "gold");
		ITEM_TO_RESOURCE_TYPE.put(Items.GOLD_ORE, "gold");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_GOLD_ORE, "gold");

		// Медь
		ITEM_TO_RESOURCE_TYPE.put(Items.COPPER_INGOT, "copper");
		ITEM_TO_RESOURCE_TYPE.put(Items.RAW_COPPER, "copper");
		ITEM_TO_RESOURCE_TYPE.put(Items.COPPER_ORE, "copper");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_COPPER_ORE, "copper");

		// Алмазы
		ITEM_TO_RESOURCE_TYPE.put(Items.DIAMOND, "diamond");
		ITEM_TO_RESOURCE_TYPE.put(Items.DIAMOND_ORE, "diamond");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_DIAMOND_ORE, "diamond");

		// Изумруды
		ITEM_TO_RESOURCE_TYPE.put(Items.EMERALD, "emerald");
		ITEM_TO_RESOURCE_TYPE.put(Items.EMERALD_ORE, "emerald");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_EMERALD_ORE, "emerald");

		// Уголь
		ITEM_TO_RESOURCE_TYPE.put(Items.COAL, "coal");
		ITEM_TO_RESOURCE_TYPE.put(Items.COAL_ORE, "coal");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_COAL_ORE, "coal");

		// Лазурит
		ITEM_TO_RESOURCE_TYPE.put(Items.LAPIS_LAZULI, "lapis");
		ITEM_TO_RESOURCE_TYPE.put(Items.LAPIS_ORE, "lapis");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_LAPIS_ORE, "lapis");

		// Красный камень
		ITEM_TO_RESOURCE_TYPE.put(Items.REDSTONE, "redstone");
		ITEM_TO_RESOURCE_TYPE.put(Items.REDSTONE_ORE, "redstone");
		ITEM_TO_RESOURCE_TYPE.put(Items.DEEPSLATE_REDSTONE_ORE, "redstone");
	}

	/**
	 * Получает тип ресурса для предмета
	 * @param item предмет
	 * @return тип ресурса или null, если предмет не является ресурсом для сбора
	 */
	public static String getResourceType(Item item) {
		return ITEM_TO_RESOURCE_TYPE.get(item);
	}

	/**
	 * Проверяет, является ли предмет ресурсом для сбора
	 * @param item предмет
	 * @return true, если предмет является ресурсом для сбора
	 */
	public static boolean isCollectibleResource(Item item) {
		return ITEM_TO_RESOURCE_TYPE.containsKey(item);
	}
}

