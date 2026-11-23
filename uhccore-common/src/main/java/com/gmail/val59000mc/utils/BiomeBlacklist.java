package com.gmail.val59000mc.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.block.Block;

import com.gmail.val59000mc.bridge.VersionBridgeProvider;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.game.GameManager;

/**
 * Biomes teams may not spawn in. Matched by name, because the names moved around between versions
 * (ICE_PLAINS, then SNOWY_TUNDRA, then SNOWY_PLAINS) and Biome stopped being an enum in 1.21.3.
 * A leading or trailing {@code *} matches any suffix or prefix, so {@code *OCEAN} covers every ocean.
 */
public final class BiomeBlacklist {

	private static final BiomeBlacklist EMPTY = new BiomeBlacklist(Collections.<String>emptyList());

	private final List<String> exact = new ArrayList<>();
	private final List<String> prefixes = new ArrayList<>();
	private final List<String> suffixes = new ArrayList<>();
	private final List<String> fragments = new ArrayList<>();

	private BiomeBlacklist(Collection<String> patterns) {
		for (String rawPattern : patterns) {
			if (rawPattern == null) {
				continue;
			}
			String pattern = rawPattern.trim().toUpperCase(Locale.ROOT);
			if (pattern.startsWith("MINECRAFT:")) {
				pattern = pattern.substring("MINECRAFT:".length());
			}

			boolean leadingWildcard = pattern.startsWith("*");
			boolean trailingWildcard = pattern.length() > 1 && pattern.endsWith("*");
			String name = pattern.substring(leadingWildcard ? 1 : 0, pattern.length() - (trailingWildcard ? 1 : 0));
			if (name.isEmpty()) {
				continue;
			}

			if (leadingWildcard && trailingWildcard) {
				fragments.add(name);
			} else if (leadingWildcard) {
				suffixes.add(name);
			} else if (trailingWildcard) {
				prefixes.add(name);
			} else {
				exact.add(name);
			}
		}
	}

	public static BiomeBlacklist empty() {
		return EMPTY;
	}

	public static BiomeBlacklist of(Collection<String> patterns) {
		return patterns.isEmpty() ? EMPTY : new BiomeBlacklist(patterns);
	}

	/**
	 * @return the blacklist from config.yml, or an empty one before the game manager exists
	 */
	public static BiomeBlacklist fromConfig() {
		GameManager gameManager = GameManager.getGameManager();
		if (gameManager == null) {
			return EMPTY;
		}
		return of(gameManager.getConfig().get(MainConfig.SCATTER_BLACKLISTED_BIOMES));
	}

	public boolean isEmpty() {
		return exact.isEmpty() && prefixes.isEmpty() && suffixes.isEmpty() && fragments.isEmpty();
	}

	public boolean isBlacklisted(Block block) {
		return !isEmpty() && matches(VersionBridgeProvider.getBridge().getBiomeName(block));
	}

	public boolean matches(String biomeName) {
		String name = biomeName.toUpperCase(Locale.ROOT);
		if (exact.contains(name)) {
			return true;
		}
		for (String prefix : prefixes) {
			if (name.startsWith(prefix)) {
				return true;
			}
		}
		for (String suffix : suffixes) {
			if (name.endsWith(suffix)) {
				return true;
			}
		}
		for (String fragment : fragments) {
			if (name.contains(fragment)) {
				return true;
			}
		}
		return false;
	}
}
