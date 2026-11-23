package com.gmail.val59000mc.bridge;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
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

	String UHC_WORLD_NAMESPACE = "uhccore";

	/**
	 * Creator for a UHC world, keyed {@code uhccore:<path>} where the server has world keys (Paper 1.16.5+),
	 * a plain one otherwise. Ask {@code creator.name()} for the world name instead of assuming it equals
	 * the path: Paper 26.x registers a namespaced world as {@code <namespace>_<path>}.
	 */
	default WorldCreator createUhcWorldCreator(String path) {
		return new WorldCreator(path);
	}

	/**
	 * @return the key of a creator made by {@link #createUhcWorldCreator(String)} (e.g. {@code uhccore:<path>}),
	 *         or null when it ended up being a plain one
	 */
	default String getWorldKey(WorldCreator creator) {
		return null;
	}

	// World operations
	void setWorldBorder(World world, double centerX, double centerZ, double sideLength);

	void setGameRule(World world, String gameRule, Object value);

	boolean setLocatorBar(World world, boolean enabled);

	void setSpawnChunkRadius(World world, int radius);

	// Chunks
	/**
	 * True when {@link #getChunkAtAsync(World, int, int)} really loads off the main thread.
	 * PaperLib only knows that when it picked its Paper handler, which needs 1.13+.
	 */
	default boolean supportsAsyncChunkLoading() {
		return PaperLib.isPaper() && PaperLib.isVersion(13);
	}

	/**
	 * Loads (and generates) a chunk. Off the main thread where {@link #supportsAsyncChunkLoading()} says so,
	 * otherwise it's a plain sync load wrapped in a completed future.
	 */
	default CompletableFuture<Chunk> getChunkAtAsync(World world, int chunkX, int chunkZ) {
		return PaperLib.getChunkAtAsync(world, chunkX, chunkZ, true);
	}

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

	/**
	 * @return false when the server has no title API at all, so the caller can fall back to chat
	 */
	@SuppressWarnings("deprecation")
	default boolean sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		try {
			player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
			return true;
		} catch (NoSuchMethodError e) {
			// 1.8.8 - 1.10 only have the two argument version with fixed timings
			try {
				player.sendTitle(title, subtitle);
				return true;
			} catch (NoSuchMethodError unsupported) {
				return false;
			}
		}
	}

	/**
	 * Upper case biome name of the block, as used by the scatter biome blacklist ("DESERT", "SNOWY_PLAINS", ...).
	 */
	default String getBiomeName(Block block) {
		return block.getBiome().name();
	}

	// Block manipulation
	boolean hasEndPortalEye(Block block);

	void setEndPortalEye(Block block, boolean eye);

	void setEndPortalFrameFacing(Block block, BlockFace facing);
}
