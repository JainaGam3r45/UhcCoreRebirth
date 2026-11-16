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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class ModernVersionUtils extends VersionUtils {

	private static final Logger LOGGER = Logger.getLogger(ModernVersionUtils.class.getCanonicalName());

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
		AttributeInstance attr = null;
		try {
			Attribute legacyAttr = Attribute.valueOf("GENERIC_MAX_HEALTH");
			attr = player.getAttribute(legacyAttr);
		} catch (IllegalArgumentException ignored) {
		}
		if (attr == null) {
			try {
				Attribute modernAttr = Attribute.valueOf("MAX_HEALTH");
				attr = player.getAttribute(modernAttr);
			} catch (IllegalArgumentException ignored) {
			}
		}
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
		PotionData potionData = potionMeta.getBasePotionData();
		if (potionData == null) {
			return null;
		}
		JsonObject baseEffect = new JsonObject();
		baseEffect.addProperty("type", potionData.getType().name());
		if (potionData.isUpgraded()) {
			baseEffect.addProperty("upgraded", true);
		}
		if (potionData.isExtended()) {
			baseEffect.addProperty("extended", true);
		}
		return baseEffect;
	}

	@Override
	public PotionMeta setBasePotionEffect(PotionMeta potionMeta, PotionData potionData) {
		potionMeta.setBasePotionData(potionData);
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
			attributesJson.add(attribute.name(), modifiersJson);
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
			Attribute attribute = Attribute.valueOf(attributeEntry.getKey());
			for (JsonElement jsonElement : attributeEntry.getValue().getAsJsonArray()) {
				JsonObject modifier = jsonElement.getAsJsonObject();
				String name = modifier.get("name").getAsString();
				double amount = modifier.get("amount").getAsDouble();
				String operation = modifier.get("operation").getAsString();
				EquipmentSlot slot = null;
				if (modifier.has("slot")) {
					slot = EquipmentSlot.valueOf(modifier.get("slot").getAsString());
				}
				UUID uuid = modifier.has("uuid") ? UUID.fromString(modifier.get("uuid").getAsString()) : UUID.randomUUID();
				meta.addAttributeModifier(attribute, new AttributeModifier(
						uuid,
						name,
						amount,
						Operation.valueOf(operation),
						slot
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
