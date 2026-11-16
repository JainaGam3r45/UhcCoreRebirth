package com.gmail.val59000mc.bridge;

import com.gmail.val59000mc.utils.ModernVersionUtils;
import com.gmail.val59000mc.utils.VersionUtils;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class ModernVersionBridge implements VersionBridge {

	public ModernVersionBridge() {
		VersionUtils.setVersionUtils(new ModernVersionUtils());
	}

	@Override
	public void setWorldBorder(World world, double centerX, double centerZ, double sideLength) {
		world.getWorldBorder().setCenter(centerX, centerZ);
		world.getWorldBorder().setSize(sideLength);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setGameRule(World world, String gameRule, Object value) {
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
	@SuppressWarnings("unchecked")
	public boolean setLocatorBar(World world, boolean enabled) {
		try {
			GameRule<?> rule = GameRule.getByName("locatorBar");
			if (rule != null) {
				world.setGameRule((GameRule<Boolean>) rule, enabled);
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setSpawnChunkRadius(World world, int radius) {
		try {
			GameRule<?> rule = GameRule.getByName("spawnChunkRadius");
			if (rule != null) {
				world.setGameRule((GameRule<Integer>) rule, radius);
			}
		} catch (Throwable ignored) {
		}
	}

	@Override
	public void setReadOnlyPlayerScore(Score score, int value) {
		try {
			score.setScore(value);
		} catch (Throwable ignored) {
		}
	}

	@Override
	public Objective registerObjective(Scoreboard scoreboard, String name, String criteria, String displayName, String renderType) {
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
	public void setTeamNameTagVisibility(Team team, boolean visible) {
		team.setOption(Option.NAME_TAG_VISIBILITY, visible ? OptionStatus.ALWAYS : OptionStatus.NEVER);
	}

	@Override
	public void setPlayerMaxHealth(Player player, double maxHealth) {
		Attribute maxHealthAttribute = ModernVersionUtils.findMaxHealthAttribute();
		AttributeInstance attr = maxHealthAttribute == null ? null : player.getAttribute(maxHealthAttribute);
		if (attr != null) {
			attr.setBaseValue(maxHealth);
		}
	}

	@Override
	public void setEntityAI(LivingEntity entity, boolean ai) {
		entity.setAI(ai);
	}

	@Override
	public ShapedRecipe createShapedRecipe(ItemStack result, String key) {
		NamespacedKey namespacedKey = new NamespacedKey("uhccore", key.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
		return new ShapedRecipe(namespacedKey, result);
	}

	@Override
	public ItemStack createPlayerSkull(String name, UUID uuid) {
		ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) skull.getItemMeta();
		if (meta != null) {
			if (uuid != null) {
				meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
			} else {
				meta.setOwner(name);
			}
			skull.setItemMeta(meta);
		}
		return skull;
	}

	@Override
	public void sendActionBar(Player player, String message) {
		player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
	}

	@Override
	public boolean hasEndPortalEye(Block block) {
		if (block.getBlockData() instanceof EndPortalFrame) {
			EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
			return frame.hasEye();
		}
		return false;
	}

	@Override
	public void setEndPortalEye(Block block, boolean eye) {
		if (block.getBlockData() instanceof EndPortalFrame) {
			EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
			frame.setEye(eye);
			block.setBlockData(frame);
		}
	}

	@Override
	public void setEndPortalFrameFacing(Block block, BlockFace facing) {
		if (block.getBlockData() instanceof EndPortalFrame) {
			EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
			frame.setFacing(facing);
			block.setBlockData(frame);
		}
	}
}
