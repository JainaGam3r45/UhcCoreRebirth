package com.gmail.val59000mc.utils;

import java.util.Arrays;
import java.util.Optional;

import org.bukkit.Material;

public enum OreType {
	COAL(UniversalMaterial.COAL, UniversalMaterial.COAL_ORE, UniversalMaterial.DEEPSLATE_COAL_ORE, false),
	COPPER(UniversalMaterial.COPPER_INGOT, UniversalMaterial.COPPER_ORE, UniversalMaterial.DEEPSLATE_COPPER_ORE, true),
	LAPIS_LAZULI(UniversalMaterial.LAPIS_LAZULI, UniversalMaterial.LAPIS_ORE, UniversalMaterial.DEEPSLATE_LAPIS_ORE, false),
	IRON(UniversalMaterial.IRON_INGOT, UniversalMaterial.IRON_ORE, UniversalMaterial.DEEPSLATE_IRON_ORE, true),
	GOLD(UniversalMaterial.GOLD_INGOT, UniversalMaterial.GOLD_ORE, UniversalMaterial.DEEPSLATE_GOLD_ORE, true),
	REDSTONE(UniversalMaterial.REDSTONE, UniversalMaterial.REDSTONE_ORE, UniversalMaterial.DEEPSLATE_REDSTONE_ORE, false),
	DIAMOND(UniversalMaterial.DIAMOND, UniversalMaterial.DIAMOND_ORE, UniversalMaterial.DEEPSLATE_DIAMOND_ORE, false),
	EMERALD(UniversalMaterial.EMERALD, UniversalMaterial.EMERALD_ORE, UniversalMaterial.DEEPSLATE_EMERALD_ORE, false),
	NETHER_QUARTZ(UniversalMaterial.QUARTZ, UniversalMaterial.NETHER_QUARTZ_ORE, null, false),
	NETHER_GOLD(UniversalMaterial.GOLD_INGOT, UniversalMaterial.NETHER_GOLD_ORE, null, true),
	ANCIENT_DEBRIS(UniversalMaterial.NETHERITE_SCRAP, UniversalMaterial.ANCIENT_DEBRIS, null, true),
	RAW_IRON_BLOCK(UniversalMaterial.IRON_BLOCK, UniversalMaterial.RAW_IRON_BLOCK, null, true),
	RAW_COPPER_BLOCK(UniversalMaterial.COPPER_BLOCK, UniversalMaterial.RAW_COPPER_BLOCK, null, true),
	RAW_GOLD_BLOCK(UniversalMaterial.GOLD_BLOCK, UniversalMaterial.RAW_GOLD_BLOCK, null, true);

	private final UniversalMaterial drop;
	private final Material normal;
	private final Material deepslate;
	private final boolean needsSmelting;

	OreType(UniversalMaterial drop, Material normal, UniversalMaterial deepslate, boolean needsSmelting) {
		this.drop = drop;
		this.normal = normal;
		this.deepslate = deepslate == null ? null : deepslate.getType();
		this.needsSmelting = needsSmelting;
	}

	OreType(UniversalMaterial drop, UniversalMaterial normal, UniversalMaterial deepslate, boolean needsSmelting) {
		this(drop, normal == null ? null : normal.getType(), deepslate, needsSmelting);
	}

	public static Optional<OreType> valueOf(Material material) {
		if (material == null) {
			return Optional.empty();
		}
		return Arrays.stream(values())
				.filter(ore -> ore.equals(material))
				.findFirst();
	}

	public static boolean isGold(Material material) {
		if (material == null) {
			return false;
		}
		return GOLD.equals(material) || NETHER_GOLD.equals(material) || RAW_GOLD_BLOCK.equals(material);
	}

	public boolean isCorrectTool(Material tool) {
		if (tool == null) {
			return false;
		}

		switch (this) {
			case COAL:
			case NETHER_QUARTZ:
			case NETHER_GOLD:
				return tool == UniversalMaterial.NETHERITE_PICKAXE.getType() ||
						tool == Material.DIAMOND_PICKAXE ||
						tool == UniversalMaterial.GOLDEN_PICKAXE.getType() ||
						tool == Material.IRON_PICKAXE ||
						tool == UniversalMaterial.COPPER_PICKAXE.getType() ||
						tool == Material.STONE_PICKAXE ||
						tool == UniversalMaterial.WOODEN_PICKAXE.getType();
			case COPPER:
			case LAPIS_LAZULI:
			case IRON:
			case RAW_IRON_BLOCK:
			case RAW_COPPER_BLOCK:
				return tool == UniversalMaterial.NETHERITE_PICKAXE.getType() ||
						tool == Material.DIAMOND_PICKAXE ||
						tool == UniversalMaterial.GOLDEN_PICKAXE.getType() ||
						tool == Material.IRON_PICKAXE ||
						tool == UniversalMaterial.COPPER_PICKAXE.getType() ||
						tool == Material.STONE_PICKAXE;
			case GOLD:
			case REDSTONE:
			case DIAMOND:
			case EMERALD:
			case RAW_GOLD_BLOCK:
				return tool == UniversalMaterial.NETHERITE_PICKAXE.getType() ||
						tool == Material.DIAMOND_PICKAXE ||
						tool == UniversalMaterial.GOLDEN_PICKAXE.getType() ||
						tool == Material.IRON_PICKAXE;
			case ANCIENT_DEBRIS:
				return tool == UniversalMaterial.NETHERITE_PICKAXE.getType() ||
						tool == Material.DIAMOND_PICKAXE;
			default:
				return false;
		}
	}

	public int getXpPerBlock() {
		switch (this) {
			case COAL:
			case REDSTONE:
				return 1;
			case COPPER:
			case IRON:
			case NETHER_GOLD:
				return 2;
			case LAPIS_LAZULI:
			case GOLD:
			case DIAMOND:
			case EMERALD:
			case NETHER_QUARTZ:
				return 3;
			case ANCIENT_DEBRIS:
				return 4;
			case RAW_IRON_BLOCK:
			case RAW_COPPER_BLOCK:
				return 5;
			case RAW_GOLD_BLOCK:
				return 9;
			default:
				return 0;
		}
	}

	public boolean equals(Material material) {
		if (material == null) {
			return false;
		}
		// Deepslate and the other modern ores are null on old versions
		return (normal != null && normal == material) || (deepslate != null && deepslate == material);
	}

	public UniversalMaterial getDrop() {
		return drop;
	}

	public Material getNormal() {
		return normal;
	}

	public Material getDeepslate() {
		return deepslate;
	}

	public boolean needsSmelting() {
		return needsSmelting;
	}
}
