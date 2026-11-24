package com.gmail.val59000mc.versionadapters.spigot_1_20_5.adapters;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import com.gmail.val59000mc.versionadapters.ServerVersion;
import com.gmail.val59000mc.versionadapters.adapters.ItemDisplayAdapter;
import com.google.auto.service.AutoService;
import com.google.common.collect.MultimapBuilder;

@AutoService(ItemDisplayAdapter.class)
public class ItemDisplayAdapterSpigot_1_20_5 implements ItemDisplayAdapter {

	@Override
	public boolean isCompatible() {
		return ServerVersion.isVersion(20, 5);
	}

	@Override
	public void hideAttributes(ItemMeta meta) {
		// Since 1.20.5 the default attribute lines come from the item prototype, and HIDE_ATTRIBUTES alone
		// does nothing for them (https://github.com/PaperMC/Paper/issues/10693). Setting an empty modifier map
		// makes the attribute_modifiers component exist, and that is what the hide flag gets applied to.
		meta.setAttributeModifiers(MultimapBuilder.hashKeys().arrayListValues().build());
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
	}

	@Override
	public void setGlow(ItemMeta meta) {
		meta.setEnchantmentGlintOverride(true);
	}

	@Override
	public void setCustomModelData(ItemMeta meta, int customModelData) {
		try {
			meta.setCustomModelData(customModelData);
		} catch (LinkageError e) {
			// Mojang keeps reshaping this one (it is a component with lists now) and the old Integer
			// setter is deprecated, so a future server may drop it. Skip the model instead of breaking the kit.
		}
	}

}
