package com.gmail.val59000mc.bridge;

import com.gmail.val59000mc.utils.ModernVersionUtils;
import com.gmail.val59000mc.utils.VersionUtils;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Keyed;
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

	private static volatile boolean numberFormatUnsupported = false;
	// PaperLib 1.0.8 can't parse 26.x version strings and quietly goes sync there,
	// so ask Paper directly. Spigot doesn't have the method.
	private static final boolean NATIVE_ASYNC_CHUNKS = hasNativeAsyncChunks();

	public ModernVersionBridge() {
		VersionUtils.setVersionUtils(new ModernVersionUtils());
	}

	private static boolean hasNativeAsyncChunks() {
		try {
			World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	@Override
	public boolean supportsAsyncChunkLoading() {
		return NATIVE_ASYNC_CHUNKS;
	}

	@Override
	public CompletableFuture<Chunk> getChunkAtAsync(World world, int chunkX, int chunkZ) {
		if (NATIVE_ASYNC_CHUNKS) {
			return world.getChunkAtAsync(chunkX, chunkZ, true);
		}
		return CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ));
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
		ReadOnlyScoreWriter.set(score, value);
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
	public void hideScoreNumbers(Objective objective) {
		if (numberFormatUnsupported) {
			return;
		}
		try {
			objective.numberFormat(NumberFormat.blank());
		} catch (LinkageError e) {
			// Paper-only API, a Spigot server running the Modern jar doesn't have it
			numberFormatUnsupported = true;
		}
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
	public String getBiomeName(Block block) {
		// Biome went from enum to registry interface in 1.21.3. Calling through Keyed links
		// against both, a plain Biome#name() call breaks on one side of that change.
		Keyed biome = block.getBiome();
		try {
			return biome.getKey().getKey().toUpperCase(Locale.ROOT);
		} catch (RuntimeException e) {
			// Biome.CUSTOM on 1.21.0 - 1.21.2 has no key
			return "CUSTOM";
		}
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
