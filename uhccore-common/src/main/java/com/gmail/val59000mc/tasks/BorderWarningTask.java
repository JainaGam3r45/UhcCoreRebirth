package com.gmail.val59000mc.tasks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.bridge.VersionBridge;
import com.gmail.val59000mc.bridge.VersionBridgeProvider;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.GameState;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.utils.LocationUtils;
import com.gmail.val59000mc.utils.UniversalSound;
import com.gmail.val59000mc.utils.VersionUtils;

/**
 * Warns players who wander close to the world border of any UHC world, for the whole game.
 * Inside the danger distance it also beeps, faster and higher pitched the closer they get,
 * because nobody reads the scoreboard in the middle of a fight.
 */
public class BorderWarningTask implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(BorderWarningTask.class.getCanonicalName());

	// Short period so the beeps can actually speed up, the distance check is cheap.
	private static final long PERIOD_TICKS = 2;
	// Action bars fade after ~2 seconds, this keeps them (and titles) up without a packet every run.
	private static final long MESSAGE_INTERVAL_TICKS = 10;
	private static final int SLOWEST_BEEP_TICKS = 20;
	private static final int FASTEST_BEEP_TICKS = 3;
	private static final float LOWEST_PITCH = 0.7f;
	private static final float HIGHEST_PITCH = 2.0f;
	private static final int TITLE_STAY_TICKS = 25;

	public enum Display {
		ACTIONBAR, BOSSBAR, CHAT, TITLE
	}

	private enum Level {
		WARNING, DANGER
	}

	private static final class PlayerAlert {
		private Level level;
		private long nextBeepTick;
	}

	private final GameManager gameManager;
	private final VersionBridge bridge;
	private final int warningDistance;
	private final int dangerDistance;
	private final Display display;
	private final boolean sound;
	private final BorderWarningBossBars bossBars;
	private final Map<UUID, PlayerAlert> alerts = new HashMap<>();

	private BukkitTask task;
	private long ticks;

	public BorderWarningTask(GameManager gameManager) {
		this.gameManager = gameManager;
		this.bridge = VersionBridgeProvider.getBridge();

		MainConfig config = gameManager.getConfig();
		this.warningDistance = Math.max(0, config.get(MainConfig.BORDER_WARNING_DISTANCE));
		this.dangerDistance = Math.max(0, Math.min(warningDistance, config.get(MainConfig.BORDER_WARNING_DANGER_DISTANCE)));
		this.sound = config.get(MainConfig.BORDER_WARNING_SOUND);

		Display configured = parseDisplay(config.get(MainConfig.BORDER_WARNING_DISPLAY));
		// 1.8 has no boss bar API, and 1.8.8 is missing the action bar overload too.
		if (!VersionUtils.isAtLeast(9) && (configured == Display.BOSSBAR || configured == Display.ACTIONBAR)) {
			configured = Display.TITLE;
		}
		this.display = configured;
		this.bossBars = display == Display.BOSSBAR ? new BorderWarningBossBars() : null;
	}

	public void start() {
		task = Bukkit.getScheduler().runTaskTimer(UhcCore.getPlugin(), this, PERIOD_TICKS, PERIOD_TICKS);
	}

	@Override
	public void run() {
		GameState state = gameManager.getGameState();
		if (state != GameState.PLAYING && state != GameState.DEATHMATCH) {
			stop();
			return;
		}

		ticks += PERIOD_TICKS;
		boolean refreshMessages = ticks % MESSAGE_INTERVAL_TICKS == 0;
		Set<UUID> alerted = new HashSet<>();

		for (UhcPlayer uhcPlayer : gameManager.getPlayerManager().getOnlinePlayingPlayers()) {
			Player player;
			try {
				player = uhcPlayer.getPlayer();
			} catch (UhcPlayerNotOnlineException ignored) {
				continue;
			}

			if (player.getGameMode() == GameMode.SPECTATOR || !gameManager.getMapLoader().isUhcWorld(player.getWorld())) {
				continue;
			}

			double distance = LocationUtils.getDistanceToBorder(player.getLocation());
			if (updatePlayer(player, distance, refreshMessages)) {
				alerted.add(player.getUniqueId());
			}
		}

		// Left the danger zone, logged out, died or went to another world.
		Iterator<UUID> it = alerts.keySet().iterator();
		while (it.hasNext()) {
			UUID playerId = it.next();
			if (!alerted.contains(playerId)) {
				it.remove();
				if (bossBars != null) {
					bossBars.hide(playerId);
				}
			}
		}
	}

	/**
	 * @return true while the player is inside the warning distance
	 */
	private boolean updatePlayer(Player player, double distance, boolean refreshMessages) {
		Level level;
		if (distance <= dangerDistance) {
			level = Level.DANGER;
		} else if (distance <= warningDistance) {
			level = Level.WARNING;
		} else {
			return false;
		}

		PlayerAlert alert = alerts.get(player.getUniqueId());
		if (alert == null) {
			alert = new PlayerAlert();
			alerts.put(player.getUniqueId(), alert);
		}
		boolean levelChanged = alert.level != level;
		alert.level = level;

		if (levelChanged || refreshMessages) {
			showMessage(player, level, distance, levelChanged);
		}

		if (sound && level == Level.DANGER && ticks >= alert.nextBeepTick) {
			float closeness = getCloseness(distance);
			float pitch = LOWEST_PITCH + (HIGHEST_PITCH - LOWEST_PITCH) * closeness;
			player.playSound(player.getLocation(), UniversalSound.NOTE_PLING.getSound(), 1, pitch);
			alert.nextBeepTick = ticks + Math.round(SLOWEST_BEEP_TICKS - (SLOWEST_BEEP_TICKS - FASTEST_BEEP_TICKS) * closeness);
		}
		return true;
	}

	/**
	 * 0 right at the danger distance, 1 at (or past) the border itself.
	 */
	private float getCloseness(double distance) {
		if (dangerDistance <= 0) {
			return 1;
		}
		return (float) Math.max(0, Math.min(1, 1 - distance / dangerDistance));
	}

	private void showMessage(Player player, Level level, double distance, boolean levelChanged) {
		String template = level == Level.DANGER ? Lang.BORDER_WARNING_DANGER : Lang.BORDER_WARNING_NEAR;
		String message = template.replace("%distance%", String.valueOf((int) Math.max(0, Math.floor(distance))));

		switch (display) {
			case ACTIONBAR:
				bridge.sendActionBar(player, message);
				break;
			case BOSSBAR:
				double progress = warningDistance == 0 ? 0 : distance / warningDistance;
				bossBars.show(player, message, progress, level == Level.DANGER);
				break;
			case TITLE:
				if (!bridge.sendTitle(player, "", message, 0, TITLE_STAY_TICKS, 5) && levelChanged) {
					player.sendMessage(message);
				}
				break;
			case CHAT:
			default:
				// Chat only on zone changes, anything more frequent buries the actual chat.
				if (levelChanged) {
					player.sendMessage(message);
				}
				break;
		}
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		if (bossBars != null) {
			bossBars.hideAll();
		}
		alerts.clear();
	}

	private static Display parseDisplay(String value) {
		try {
			return Display.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException ex) {
			LOGGER.warning("Invalid border-warning.display '" + value + "', using ACTIONBAR. Pick one of ACTIONBAR, BOSSBAR, CHAT or TITLE");
			return Display.ACTIONBAR;
		}
	}
}
