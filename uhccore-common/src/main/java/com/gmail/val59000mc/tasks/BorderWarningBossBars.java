package com.gmail.val59000mc.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

	private final Map<UUID, BossBar> bars = new HashMap<>();

	void show(Player player, String title, double progress, boolean danger) {
		BarColor color = danger ? BarColor.RED : BarColor.YELLOW;
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
