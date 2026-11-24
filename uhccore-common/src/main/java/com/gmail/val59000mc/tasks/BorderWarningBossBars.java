package com.gmail.val59000mc.tasks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Boss bars for {@link BorderWarningTask}. Lives in its own class because the boss bar API is
 * 1.9+, and nothing here may get loaded on a 1.8 server.
 */
final class BorderWarningBossBars {

	private static final Logger LOGGER = Logger.getLogger(BorderWarningBossBars.class.getCanonicalName());

	private final Map<UUID, BossBar> bars = new HashMap<>();
	private final BarColor warningColor;
	private final BarColor dangerColor;

	BorderWarningBossBars(String warningColor, String dangerColor) {
		this.warningColor = parseColor(warningColor, BarColor.YELLOW, "border-warning.bossbar-warning-color");
		this.dangerColor = parseColor(dangerColor, BarColor.RED, "border-warning.bossbar-danger-color");
	}

	private static BarColor parseColor(String value, BarColor fallback, String path) {
		if (value == null) {
			return fallback;
		}
		try {
			return BarColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			LOGGER.warning("Invalid " + path + " '" + value + "', using " + fallback + ". Pick one of PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE");
			return fallback;
		}
	}

	void show(Player player, String title, double progress, boolean danger) {
		BarColor color = danger ? dangerColor : warningColor;
		BossBar bar = bars.get(player.getUniqueId());
		if (bar == null) {
			bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
			bars.put(player.getUniqueId(), bar);
		}
		bar.setTitle(title);
		bar.setColor(color);
		bar.setProgress(Math.max(0, Math.min(1, progress)));
		if (!bar.getPlayers().contains(player)) {
			bar.addPlayer(player);
		}
	}

	void hide(UUID playerId) {
		BossBar bar = bars.remove(playerId);
		if (bar != null) {
			bar.removeAll();
		}
	}

	void hideAll() {
		for (BossBar bar : bars.values()) {
			bar.removeAll();
		}
		bars.clear();
	}
}
