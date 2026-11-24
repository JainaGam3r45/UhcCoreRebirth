package com.gmail.val59000mc.customitems;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.exceptions.ParseException;
import com.gmail.val59000mc.utils.ColorUtils;
import com.gmail.val59000mc.utils.JsonItemStack;
import com.gmail.val59000mc.utils.JsonItemUtils;
import com.gmail.val59000mc.utils.MaterialLookup;
import com.gmail.val59000mc.versionadapters.adapters.ItemDisplayAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the native YAML item syntax used by kits.yml.
 */
final class KitItemParser {

	private KitItemParser() {
	}

	/**
	 * Parses one entry of a kit's {@code items} list written as a YAML section.
	 * <p>
	 * The section is rewritten into the keys of the old JSON format and handed to {@link JsonItemUtils}, so
	 * enchantment lookup, flags, potions and the rest keep a single implementation (and a single set of version
	 * quirks) whichever syntax the admin picked.
	 */
	static JsonItemStack parse(Map<?, ?> section) throws ParseException {
		final JsonObject json = new JsonObject();
		ItemStack base = null;

		for (Map.Entry<?, ?> entry : section.entrySet()) {
			final String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
			final Object value = entry.getValue();

			switch (key) {
				case "material":
				case "type":
					base = MaterialLookup.find(String.valueOf(value));
					if (base == null) {
						throw new ParseException("Invalid item material: " + value + " does the item exist on this minecraft version?");
					}
					break;
				case "name":
				case "display-name":
					json.addProperty("display-name", ColorUtils.colorize(String.valueOf(value)));
					break;
				case "lore":
					json.add("lore", parseLore(value));
					break;
				case "enchantments":
					json.add("enchantments", parseEnchantments(value));
					break;
				default:
					// amount, durability, flags, unbreakable, custom-model-data, color, base-effect... same keys as the JSON format
					json.add(key, toJson(value));
			}
		}

		if (base == null) {
			throw new ParseException("Missing material");
		}

		json.addProperty("type", base.getType().name());
		// Before 1.13 colored blocks (GRAY_STAINED_GLASS_PANE...) are a base material plus a data value.
		if (base.getDurability() != 0 && !json.has("durability")) {
			json.addProperty("durability", base.getDurability());
		}

		return JsonItemUtils.getItemFromJson(json.toString());
	}

	/**
	 * Builds a menu icon from a section with the keys {@code material}, {@code name}, {@code lore}, {@code glow}
	 * and {@code custom-model-data}. Attribute lines are always hidden, an icon is never meant to be used.
	 *
	 * @param defaultMaterial material to use when the section has none, null to make it mandatory
	 * @param defaultName name to use when the section has none, null for the vanilla item name
	 */
	static ItemStack parseIcon(ConfigurationSection section, @Nullable String defaultMaterial, @Nullable String defaultName) throws ParseException {
		final String materialName = section.getString("material", defaultMaterial);
		final ItemStack icon = MaterialLookup.find(materialName);
		if (icon == null) {
			throw new ParseException("Invalid material: " + materialName + " does the item exist on this minecraft version?");
		}

		final ItemDisplayAdapter display = UhcCore.getVersionAdapterLoader().getVersionAdapter(ItemDisplayAdapter.class);
		final ItemMeta meta = icon.getItemMeta();

		final String name = section.getString("name", defaultName);
		if (name != null) {
			meta.setDisplayName(ColorUtils.colorize(name));
		}

		if (section.isList("lore")) {
			meta.setLore(ColorUtils.colorize(section.getStringList("lore")));
		} else if (section.isString("lore")) {
			meta.setLore(ColorUtils.colorize(Collections.singletonList(section.getString("lore"))));
		}

		if (section.getBoolean("glow", false)) {
			display.setGlow(meta);
		}

		if (section.contains("custom-model-data")) {
			if (!section.isInt("custom-model-data")) {
				throw new ParseException("custom-model-data must be a whole number");
			}
			display.setCustomModelData(meta, section.getInt("custom-model-data"));
		}

		display.hideAttributes(meta);
		icon.setItemMeta(meta);
		return icon;
	}

	private static JsonArray parseLore(Object value) {
		final JsonArray lore = new JsonArray();
		if (value instanceof Collection) {
			for (Object line : (Collection<?>) value) {
				lore.add(new JsonPrimitive(ColorUtils.colorize(String.valueOf(line))));
			}
		} else {
			lore.add(new JsonPrimitive(ColorUtils.colorize(String.valueOf(value))));
		}
		return lore;
	}

	/**
	 * Accepts a map ({@code sharpness: 2}), a list of {@code NAME;LEVEL} strings, or the JSON style list of
	 * {@code type}/{@code level} sections.
	 */
	private static JsonArray parseEnchantments(Object value) throws ParseException {
		final JsonArray enchantments = new JsonArray();

		if (value instanceof Map) {
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				enchantments.add(enchantment(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())));
			}
		} else if (value instanceof Collection) {
			for (Object element : (Collection<?>) value) {
				if (element instanceof String) {
					final String[] parts = ((String) element).split(";", 2);
					enchantments.add(enchantment(parts[0], parts.length == 2 ? parts[1] : "1"));
				} else {
					enchantments.add(toJson(element));
				}
			}
		} else {
			throw new ParseException("Enchantments must be a list or a section, found: " + value);
		}

		return enchantments;
	}

	private static JsonObject enchantment(String type, String level) throws ParseException {
		final JsonObject enchantment = new JsonObject();
		enchantment.addProperty("type", type.trim());
		try {
			enchantment.addProperty("level", Integer.parseInt(level.trim()));
		} catch (NumberFormatException e) {
			throw new ParseException("Invalid enchantment level for " + type.trim() + ": " + level);
		}
		return enchantment;
	}

	private static JsonElement toJson(@Nullable Object value) {
		if (value == null) {
			return JsonNull.INSTANCE;
		}
		if (value instanceof Map) {
			final JsonObject object = new JsonObject();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				object.add(String.valueOf(entry.getKey()), toJson(entry.getValue()));
			}
			return object;
		}
		if (value instanceof Collection) {
			final JsonArray array = new JsonArray();
			for (Object element : (Collection<?>) value) {
				array.add(toJson(element));
			}
			return array;
		}
		if (value instanceof Boolean) {
			return new JsonPrimitive((Boolean) value);
		}
		if (value instanceof Number) {
			return new JsonPrimitive((Number) value);
		}
		return new JsonPrimitive(String.valueOf(value));
	}

}
