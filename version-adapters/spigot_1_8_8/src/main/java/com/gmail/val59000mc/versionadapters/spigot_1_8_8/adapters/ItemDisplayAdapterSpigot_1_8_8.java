package com.gmail.val59000mc.versionadapters.spigot_1_8_8.adapters;

import java.lang.reflect.Method;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import com.gmail.val59000mc.versionadapters.ServerVersion;
import com.gmail.val59000mc.versionadapters.adapters.ItemDisplayAdapter;
import com.google.auto.service.AutoService;

/**
 * Covers 1.8.8 up to 1.20.4. Same range as the other 1_8_8 adapters, the 1.20.5 item
 * component rewrite is where {@link ItemDisplayAdapterSpigot_1_20_5} takes over.
 */
@AutoService(ItemDisplayAdapter.class)
public class ItemDisplayAdapterSpigot_1_8_8 implements ItemDisplayAdapter {

	// This module is compiled against 1.8.8, which has no custom model data. The method
	// shows up in 1.14 and stays put until 1.20.4, so we look it up once and call it by reflection.
	private static final Method SET_CUSTOM_MODEL_DATA = findSetCustomModelData();

	private static Method findSetCustomModelData() {
		try {
			return ItemMeta.class.getMethod("setCustomModelData", Integer.class);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	@Override
	public boolean isCompatible() {
		return ServerVersion.isVersion(8, 8) && !ServerVersion.isVersion(20, 5);
	}

	@Override
	public void hideAttributes(ItemMeta meta) {
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
	}

	@Override
	public void setGlow(ItemMeta meta) {
		// No glint override before 1.20.5, so the old trick: a throwaway enchantment with the tooltip line hidden.
		// DURABILITY (Unbreaking) exists on every version this adapter covers, it only got renamed in 1.20.5.
		meta.addEnchant(Enchantment.DURABILITY, 1, true);
		meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
	}

	@Override
	public void setCustomModelData(ItemMeta meta, int customModelData) {
		if (SET_CUSTOM_MODEL_DATA == null) {
			return;
		}
		try {
			SET_CUSTOM_MODEL_DATA.invoke(meta, customModelData);
		} catch (ReflectiveOperationException e) {
			// Not worth failing the whole kit over a cosmetic model, the icon just keeps its vanilla look.
		}
	}

}
