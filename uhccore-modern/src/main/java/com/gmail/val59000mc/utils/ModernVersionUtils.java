package com.gmail.val59000mc.utils;

import com.gmail.val59000mc.exceptions.ParseException;
import com.gmail.val59000mc.players.UhcPlayer;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameRule;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.block.Skull;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class ModernVersionUtils extends VersionUtils {

	private static final Logger LOGGER = Logger.getLogger(ModernVersionUtils.class.getCanonicalName());

	private static final String STRONG_POTION_PREFIX = "STRONG_";
	private static final String LONG_POTION_PREFIX = "LONG_";

	// Enum-style names the legacy jar and upstream kits store, e.g. GENERIC_MAX_HEALTH.
	private static final String[] LEGACY_ATTRIBUTE_PREFIXES = {"generic_", "player_", "zombie_", "horse_"};

	/**
	 * Registry lookup that survives the 1.21.2 attribute rename, where Mojang dropped the
	 * "generic." / "player." style prefixes. Tries the key as-is, then without the prefix,
	 * so "generic.max_health" resolves on 1.21.0/1.21.1 and on 1.21.2+ alike.
	 */
	@Nullable
	public static Attribute findAttribute(NamespacedKey key) {
		Attribute attribute = Registry.ATTRIBUTE.get(key);
		int prefixEnd = key.getKey().lastIndexOf('.');
		if (attribute == null && prefixEnd >= 0) {
			attribute = Registry.ATTRIBUTE.get(new NamespacedKey(key.getNamespace(), key.getKey().substring(prefixEnd + 1)));
		}
		return attribute;
	}

	/**
	 * Max health lives at "max_health" on 1.21.2+ and at "generic.max_health" on 1.21.0/1.21.1.
	 */
	@Nullable
	public static Attribute findMaxHealthAttribute() {
		Attribute attribute = findAttribute(NamespacedKey.minecraft("max_health"));
		if (attribute == null) {
			attribute = findAttribute(NamespacedKey.minecraft("generic.max_health"));
		}
		return attribute;
	}

	// Mirrors what OldEnum#name() hands out, so kit files written before this change still parse.
	private static String getAttributeName(Attribute attribute) {
		NamespacedKey key = attribute.getKey();
		if (NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
			return key.getKey().toUpperCase(Locale.ROOT);
		}
		return key.toString();
	}

	// Old enum names like GENERIC_ATTACK_DAMAGE only ever resolved through valueOf because
	// Paper's Commodore rewrote them for us. The registry knows nothing about them, so we
	// rebuild the real key: generic_attack_damage -> generic.attack_damage, and findAttribute
	// drops the prefix again on 1.21.2+.
	private static Attribute parseAttributeName(String name) {
		String lowerName = name.toLowerCase(Locale.ROOT);
		// Horse jump strength became generic.jump_strength back in 1.20.5.
		if ("horse_jump_strength".equals(lowerName)) {
			lowerName = "generic_jump_strength";
		}
		NamespacedKey key = NamespacedKey.fromString(lowerName);
		Attribute attribute = key == null ? null : findAttribute(key);
		if (attribute == null && key != null && NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
			attribute = findLegacyAttribute(key.getKey());
		}
		if (attribute == null) {
			throw new IllegalArgumentException("No attribute found with the name " + name);
		}
		return attribute;
	}

	@Nullable
	private static Attribute findLegacyAttribute(String legacyName) {
		for (String prefix : LEGACY_ATTRIBUTE_PREFIXES) {
			if (legacyName.startsWith(prefix) && legacyName.length() > prefix.length()) {
				String dotted = prefix.substring(0, prefix.length() - 1) + "." + legacyName.substring(prefix.length());
				return findAttribute(NamespacedKey.minecraft(dotted));
			}
		}
		return null;
	}

	@Override
	public ShapedRecipe createShapedRecipe(ItemStack craft, String craftKey) {
		NamespacedKey key = new NamespacedKey("uhccore", craftKey.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
		return new ShapedRecipe(key, craft);
	}

	@Override
	public ItemStack createPlayerSkull(String name, UUID uuid) {
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) item.getItemMeta();
		if (meta != null) {
			if (uuid != null) {
				meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
			} else {
				meta.setOwner(name);
			}
			item.setItemMeta(meta);
		}
		return item;
	}

	@Override
	public void setSkullOwner(Skull skull, UhcPlayer player) {
		skull.setOwningPlayer(Bukkit.getOfflinePlayer(player.getUuid()));
	}

	@Override
	public Objective registerNewObjective(Scoreboard scoreboard, String name, String criteria, String displayName, String renderType) {
		RenderType type = RenderType.INTEGER;
		if (renderType != null) {
			try {
				type = RenderType.valueOf(renderType.toUpperCase());
			} catch (IllegalArgumentException ignored) {
			}
		}
		return scoreboard.registerNewObjective(name, criteria, displayName, type);
	}

	@Override
	public void setPlayerMaxHealth(Player player, double maxHealth) {
		Attribute maxHealthAttribute = findMaxHealthAttribute();
		AttributeInstance attr = maxHealthAttribute == null ? null : player.getAttribute(maxHealthAttribute);
		if (attr != null) {
			attr.setBaseValue(maxHealth);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setGameRuleValue(World world, String gameRule, Object value) {
		GameRule<?> rule = GameRule.getByName(gameRule);
		if (rule != null) {
			if (value instanceof Boolean) {
				world.setGameRule((GameRule<Boolean>) rule, (Boolean) value);
			} else if (value instanceof Integer) {
				world.setGameRule((GameRule<Integer>) rule, (Integer) value);
			}
		}
	}

	@Override
	public boolean hasEye(Block block) {
		if (block.getBlockData() instanceof EndPortalFrame) {
			EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
			return frame.hasEye();
		}
		return false;
	}

	@Override
	public void setEye(Block block, boolean eye) {
		if (block.getBlockData() instanceof EndPortalFrame) {
			EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
			frame.setEye(eye);
			block.setBlockData(frame);
		}
	}

	@Override
	public void setEndPortalFrameOrientation(Block block, BlockFace blockFace) {
		if (block.getBlockData() instanceof EndPortalFrame) {
			EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
			frame.setFacing(blockFace);
			block.setBlockData(frame);
		}
	}

	@Override
	public void setTeamNameTagVisibility(Team team, boolean value) {
		team.setOption(Option.NAME_TAG_VISIBILITY, value ? OptionStatus.ALWAYS : OptionStatus.NEVER);
	}

	@Override
	public void setChestName(Chest chest, String name) {
		chest.setCustomName(name);
		chest.update();
	}

	@Nullable
	@Override
	public JsonObject getBasePotionEffect(PotionMeta potionMeta) {
		PotionType potionType = potionMeta.getBasePotionType();
		if (potionType == null) {
			return null;
		}
		// Since 1.20.5 long/strong potions are their own PotionType. Kit JSON still stores
		// the base type plus flags, because common rebuilds it through PotionData, which
		// rejects LONG_/STRONG_ types outright.
		String typeName = potionType.name();
		boolean upgraded = typeName.startsWith(STRONG_POTION_PREFIX);
		boolean extended = typeName.startsWith(LONG_POTION_PREFIX);
		if (upgraded) {
			typeName = typeName.substring(STRONG_POTION_PREFIX.length());
		} else if (extended) {
			typeName = typeName.substring(LONG_POTION_PREFIX.length());
		}
		JsonObject baseEffect = new JsonObject();
		baseEffect.addProperty("type", typeName);
		if (upgraded) {
			baseEffect.addProperty("upgraded", true);
		}
		if (extended) {
			baseEffect.addProperty("extended", true);
		}
		return baseEffect;
	}

	// PotionData is pinned here by the abstract signature in common, which still has to
	// compile for 1.8.8. We only read it and hand the real PotionType to the meta.
	@Override
	@SuppressWarnings("removal")
	public PotionMeta setBasePotionEffect(PotionMeta potionMeta, PotionData potionData) {
		PotionType potionType = potionData.getType();
		if (potionData.isUpgraded()) {
			potionType = PotionType.valueOf(STRONG_POTION_PREFIX + potionType.name());
		} else if (potionData.isExtended()) {
			potionType = PotionType.valueOf(LONG_POTION_PREFIX + potionType.name());
		}
		potionMeta.setBasePotionType(potionType);
		return potionMeta;
	}

	@Nullable
	@Override
	public Color getPotionColor(PotionMeta potionMeta) {
		if (potionMeta.hasColor()) {
			return potionMeta.getColor();
		}
		return null;
	}

	@Override
	public PotionMeta setPotionColor(PotionMeta potionMeta, Color color) {
		potionMeta.setColor(color);
		return potionMeta;
	}

	@Override
	public void setChestSide(Chest chest, boolean left) {
		if (chest.getBlockData() instanceof org.bukkit.block.data.type.Chest) {
			org.bukkit.block.data.type.Chest data = (org.bukkit.block.data.type.Chest) chest.getBlockData();
			data.setType(left ? Type.LEFT : Type.RIGHT);
			chest.getBlock().setBlockData(data, true);
		}
	}

	@Override
	public void removeRecipe(ItemStack item, Recipe recipe) {
		if (recipe instanceof Keyed) {
			Bukkit.removeRecipe(((Keyed) recipe).getKey());
		} else {
			Iterator<Recipe> it = Bukkit.recipeIterator();
			while (it.hasNext()) {
				Recipe r = it.next();
				if (r.getResult().isSimilar(item)) {
					it.remove();
				}
			}
		}
	}

	@Override
	public void handleNetherPortalEvent(PlayerPortalEvent event) {
		event.setCanCreatePortal(true);
	}

	@Nullable
	@Override
	public JsonObject getItemAttributes(ItemStack itemStack) {
		ItemMeta meta = itemStack.getItemMeta();
		if (meta == null || !meta.hasAttributeModifiers()) {
			return null;
		}
		JsonObject attributesJson = new JsonObject();
		Multimap<Attribute, AttributeModifier> attributeModifiers = meta.getAttributeModifiers();
		if (attributeModifiers == null) {
			return null;
		}
		for (Attribute attribute : attributeModifiers.keySet()) {
			JsonArray modifiersJson = new JsonArray();
			Collection<AttributeModifier> modifiers = attributeModifiers.get(attribute);
			for (AttributeModifier modifier : modifiers) {
				JsonObject modifierObject = new JsonObject();
				modifierObject.addProperty("name", modifier.getName());
				modifierObject.addProperty("amount", modifier.getAmount());
				modifierObject.addProperty("operation", modifier.getOperation().name());
				if (modifier.getSlot() != null) {
					modifierObject.addProperty("slot", modifier.getSlot().name());
				}
				modifiersJson.add(modifierObject);
			}
			attributesJson.add(getAttributeName(attribute), modifiersJson);
		}
		return attributesJson;
	}

	@Override
	public JsonItemStack applyItemAttributes(JsonItemStack itemStack, JsonObject attributes) {
		ItemMeta meta = itemStack.getItemMeta();
		if (meta == null) {
			return itemStack;
		}
		Set<Map.Entry<String, JsonElement>> entries = attributes.entrySet();
		for (Map.Entry<String, JsonElement> attributeEntry : entries) {
			Attribute attribute = parseAttributeName(attributeEntry.getKey());
			for (JsonElement jsonElement : attributeEntry.getValue().getAsJsonArray()) {
				JsonObject modifier = jsonElement.getAsJsonObject();
				double amount = modifier.get("amount").getAsDouble();
				String operation = modifier.get("operation").getAsString();
				EquipmentSlot slot = null;
				if (modifier.has("slot")) {
					slot = EquipmentSlot.valueOf(modifier.get("slot").getAsString());
				}
				UUID uuid = modifier.has("uuid") ? UUID.fromString(modifier.get("uuid").getAsString()) : UUID.randomUUID();
				// 1.21 keys modifiers by NamespacedKey and the old UUID constructor already threw
				// the "name" field away, so we just do the same conversion it did internally.
				meta.addAttributeModifier(attribute, new AttributeModifier(
						NamespacedKey.fromString(uuid.toString()),
						amount,
						Operation.valueOf(operation),
						slot == null ? EquipmentSlotGroup.ANY : slot.getGroup()
				));
			}
		}
		itemStack.setItemMeta(meta);
		return itemStack;
	}

	@Override
	public String getEnchantmentKey(Enchantment enchantment) {
		return enchantment.getKey().toString();
	}

	@Nullable
	@Override
	public Enchantment getEnchantmentFromKey(String key) {
		Enchantment enchantment = Enchantment.getByName(key);
		if (enchantment != null) {
			return enchantment;
		}
		String[] parts = key.split(":", 2);
		NamespacedKey namespacedKey = parts.length == 2 ? new NamespacedKey(parts[0], parts[1]) : NamespacedKey.minecraft(parts[0]);
		return Enchantment.getByKey(namespacedKey);
	}

	@Override
	public void setEntityAI(LivingEntity entity, boolean b) {
		entity.setAI(b);
	}

	@Override
	public List<Material> getItemList() {
		return Arrays.stream(Material.values())
				.filter(Material::isItem)
				.filter(m -> !m.isAir())
				.collect(Collectors.toList());
	}

	@Nullable
	@Override
	public JsonArray getSuspiciousStewEffects(ItemMeta meta) {
		if (!(meta instanceof SuspiciousStewMeta)) {
			return null;
		}
		SuspiciousStewMeta stewMeta = (SuspiciousStewMeta) meta;
		JsonArray customEffects = new JsonArray();
		for (PotionEffect effect : stewMeta.getCustomEffects()) {
			customEffects.add(JsonItemUtils.getPotionEffectJson(effect));
		}
		return customEffects;
	}

	@Override
	public ItemMeta applySuspiciousStewEffects(ItemMeta meta, JsonArray effects) throws ParseException {
		if (!(meta instanceof SuspiciousStewMeta)) {
			return meta;
		}
		SuspiciousStewMeta stewMeta = (SuspiciousStewMeta) meta;
		for (JsonElement effectElement : effects) {
			PotionEffect effect = JsonItemUtils.parsePotionEffect(effectElement.getAsJsonObject());
			stewMeta.addCustomEffect(effect, true);
		}
		return stewMeta;
	}

	@Override
	public void setItemUnbreakable(ItemMeta meta, boolean b) {
		meta.setUnbreakable(b);
	}

	@Override
	public boolean getItemUnbreakable(ItemMeta meta) {
		return meta.isUnbreakable();
	}

	@Override
	public boolean isAir(Material material) {
		return material.isAir();
	}

	@Override
	public boolean leavesIsPersistent(Block leaves) {
		if (leaves.getBlockData() instanceof Leaves) {
			Leaves data = (Leaves) leaves.getBlockData();
			return data.isPersistent();
		}
		return false;
	}
}
