package com.gmail.val59000mc.bridge;

import com.gmail.val59000mc.utils.VersionUtils;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class DefaultVersionBridge implements VersionBridge {

	private static final Logger LOGGER = Logger.getLogger(DefaultVersionBridge.class.getCanonicalName());
	private static volatile boolean actionBarUnsupported = false;

	@Override
	public void setWorldBorder(World world, double centerX, double centerZ, double sideLength) {
		world.getWorldBorder().setCenter(centerX, centerZ);
		world.getWorldBorder().setSize(sideLength);
	}

	@Override
	public void setGameRule(World world, String gameRule, Object value) {
		VersionUtils.getVersionUtils().setGameRuleValue(world, gameRule, value);
	}

	@Override
	public boolean setLocatorBar(World world, boolean enabled) {
		try {
			GameRule<?> rule = GameRule.getByName("locatorBar");
			if (rule != null) {
				VersionUtils.getVersionUtils().setGameRuleValue(world, "locatorBar", enabled);
				return true;
			}
		} catch (Throwable ignored) {
			// Not supported on older versions
		}
		return false;
	}

	@Override
	public void setSpawnChunkRadius(World world, int radius) {
		if (VersionUtils.isAtLeast(20, 5)) {
			try {
				GameRule<?> rule = GameRule.getByName("spawnChunkRadius");
				if (rule != null) {
					VersionUtils.getVersionUtils().setGameRuleValue(world, "spawnChunkRadius", radius);
				}
			} catch (Throwable ignored) {
				// Not supported on older versions
			}
		}
	}

	@Override
	public void setReadOnlyPlayerScore(Score score, int value) {
		ReadOnlyScoreWriter.set(score, value);
	}

	@Override
	public Objective registerObjective(Scoreboard scoreboard, String name, String criteria, String displayName, String renderType) {
		return VersionUtils.getVersionUtils().registerNewObjective(scoreboard, name, criteria, displayName, renderType);
	}

	@Override
	public void setTeamNameTagVisibility(Team team, boolean visible) {
		VersionUtils.getVersionUtils().setTeamNameTagVisibility(team, visible);
	}

	@Override
	public void setPlayerMaxHealth(Player player, double maxHealth) {
		VersionUtils.getVersionUtils().setPlayerMaxHealth(player, maxHealth);
	}

	@Override
	public void setEntityAI(LivingEntity entity, boolean ai) {
		VersionUtils.getVersionUtils().setEntityAI(entity, ai);
	}

	@Override
	public ShapedRecipe createShapedRecipe(ItemStack result, String key) {
		return VersionUtils.getVersionUtils().createShapedRecipe(result, key);
	}

	@Override
	public ItemStack createPlayerSkull(String name, UUID uuid) {
		return VersionUtils.getVersionUtils().createPlayerSkull(name, uuid);
	}

	@Override
	public void sendActionBar(Player player, String message) {
		if (actionBarUnsupported) {
			return;
		}
		try {
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
		} catch (LinkageError e) {
			// Old Spigot (1.8.8) has no ChatMessageType overload. Falling back to chat here would
			// flood it, NoClean pushes a countdown every second, so just go quiet from now on.
			actionBarUnsupported = true;
			LOGGER.log(Level.FINE, "Action bar not supported on this server, skipping it", e);
		} catch (Throwable t) {
			player.sendMessage(message);
		}
	}

	@Override
	public boolean hasEndPortalEye(Block block) {
		return VersionUtils.getVersionUtils().hasEye(block);
	}

	@Override
	public void setEndPortalEye(Block block, boolean eye) {
		VersionUtils.getVersionUtils().setEye(block, eye);
	}

	@Override
	public void setEndPortalFrameFacing(Block block, BlockFace facing) {
		VersionUtils.getVersionUtils().setEndPortalFrameOrientation(block, facing);
	}
}
