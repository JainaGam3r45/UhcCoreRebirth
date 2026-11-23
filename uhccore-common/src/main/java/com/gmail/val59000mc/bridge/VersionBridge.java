package com.gmail.val59000mc.bridge;

import java.util.UUID;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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

public interface VersionBridge {

	/** Namespace UHC worlds get registered under where the server supports world keys. */
	String UHC_WORLD_NAMESPACE = "uhccore";

	// World operations
	/**
	 * Creator for a UHC world. Servers with namespaced world keys (Paper 1.16+) register it as
	 * {@code uhccore:<worldName>}, so Paper builds that store worlds under
	 * {@code world/dimensions/<namespace>/} keep ours apart from everything else.
	 * The world name stays {@code worldName} either way.
	 */
	default WorldCreator createUhcWorldCreator(String worldName) {
		return new WorldCreator(worldName);
	}

	void setWorldBorder(World world, double centerX, double centerZ, double sideLength);

	void setGameRule(World world, String gameRule, Object value);

	boolean setLocatorBar(World world, boolean enabled);

	void setSpawnChunkRadius(World world, int radius);

	// Scoreboards
	void setReadOnlyPlayerScore(Score score, int value);

	Objective registerObjective(Scoreboard scoreboard, String name, String criteria, String displayName, String renderType);

	void setTeamNameTagVisibility(Team team, boolean visible);

	/**
	 * Hides the red score numbers on the right of a sidebar objective, on servers and clients that support it (1.20.3+).
	 * The scores still decide the line order, they just stop being drawn. Does nothing elsewhere.
	 */
	default void hideScoreNumbers(Objective objective) {
	}

	// Entity attributes
	void setPlayerMaxHealth(Player player, double maxHealth);

	void setEntityAI(LivingEntity entity, boolean ai);

	// Items and recipes
	ShapedRecipe createShapedRecipe(ItemStack result, String key);

	ItemStack createPlayerSkull(String name, UUID uuid);

	// Chat and Action bar
	void sendActionBar(Player player, String message);

	// Block manipulation
	boolean hasEndPortalEye(Block block);

	void setEndPortalEye(Block block, boolean eye);

	void setEndPortalFrameFacing(Block block, BlockFace facing);
}
