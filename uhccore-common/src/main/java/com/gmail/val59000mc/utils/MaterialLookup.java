package com.gmail.val59000mc.utils;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;

/**
 * Turns a material name from a config file into an item stack, without throwing when the name
 * doesn't exist on the running server version.
 */
public final class MaterialLookup {

	// Dye colors in 1.8-1.12 data value order. The 1.13+ name is LIGHT_GRAY, SILVER was the old DyeColor name.
	private static final String[] DYE_COLORS = {
		"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
		"LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
	};

	// Suffix of the flattened names -> the pre-1.13 material that stores the color as a data value.
	// CONCRETE_POWDER has to come before CONCRETE, both end the same way.
	private static final String[][] LEGACY_COLORED_FAMILIES = {
		{"_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"},
		{"_STAINED_GLASS", "STAINED_GLASS"},
		{"_WOOL", "WOOL"},
		{"_CARPET", "CARPET"},
		{"_TERRACOTTA", "STAINED_CLAY"},
		{"_CONCRETE_POWDER", "CONCRETE_POWDER"},
		{"_CONCRETE", "CONCRETE"}
	};

	private MaterialLookup() {
	}

	/**
	 * Looks a material up by name. Accepts the modern names on every version (GRAY_STAINED_GLASS_PANE on 1.8 comes back
	 * as STAINED_GLASS_PANE with the right data value), and raw Bukkit names on the versions that have them.
	 *
	 * @param name material name, case insensitive, an optional "minecraft:" prefix is ignored
	 * @return a one item stack, or null if the material doesn't exist here or is air
	 */
	@Nullable
	public static ItemStack find(@Nullable String name) {
		if (name == null) {
			return null;
		}

		String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
		if (key.startsWith("MINECRAFT:")) {
			key = key.substring("MINECRAFT:".length());
		}
		if (key.isEmpty()) {
			return null;
		}

		ItemStack stack = findUniversal(key);
		if (stack == null) {
			final Material material = Material.matchMaterial(key);
			if (material != null) {
				stack = new ItemStack(material);
			}
		}
		if (stack == null && !VersionUtils.isAtLeast(13)) {
			stack = findLegacyColored(key);
		}

		if (stack == null || VersionUtils.getVersionUtils().isAir(stack.getType())) {
			return null;
		}
		return stack;
	}

	@Nullable
	private static ItemStack findUniversal(String key) {
		try {
			final UniversalMaterial universal = UniversalMaterial.valueOf(key);
			// getType() is null for materials this server doesn't have, getStack() would blow up on those.
			return universal.getType() == null ? null : universal.getStack();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Nullable
	private static ItemStack findLegacyColored(String key) {
		for (String[] family : LEGACY_COLORED_FAMILIES) {
			if (!key.endsWith(family[0])) {
				continue;
			}

			String color = key.substring(0, key.length() - family[0].length());
			if (color.equals("SILVER")) {
				color = "LIGHT_GRAY";
			}

			final Material material = Material.matchMaterial(family[1]);
			for (int data = 0; material != null && data < DYE_COLORS.length; data++) {
				if (DYE_COLORS[data].equals(color)) {
					return new ItemStack(material, 1, (short) data);
				}
			}
		}
		return null;
	}

}
