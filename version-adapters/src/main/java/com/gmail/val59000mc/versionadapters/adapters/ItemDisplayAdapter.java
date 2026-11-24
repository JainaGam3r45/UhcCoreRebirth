package com.gmail.val59000mc.versionadapters.adapters;

import org.bukkit.inventory.meta.ItemMeta;

import com.gmail.val59000mc.versionadapters.VersionAdapter;

/**
 * Cosmetic item meta tweaks used by menu icons. Every one of them is done differently
 * depending on the Minecraft version, so the core never touches the raw API for these.
 */
public interface ItemDisplayAdapter extends VersionAdapter {

	/**
	 * Hides the attack damage / attack speed / armor lines from the item tooltip.
	 * On 1.20.5+ the item flag alone is not enough, see the implementation.
	 */
	void hideAttributes(ItemMeta meta);

	/**
	 * Makes the item shine like an enchanted one without showing any enchantment in the tooltip.
	 */
	void setGlow(ItemMeta meta);

	/**
	 * Sets the custom model data of the item. Does nothing on versions that don't have it (before 1.14).
	 */
	void setCustomModelData(ItemMeta meta, int customModelData);

}
