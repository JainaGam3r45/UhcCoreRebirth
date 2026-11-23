package com.gmail.val59000mc.tasks;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitTask;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.GameState;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.utils.UniversalSound;

/**
 * Battle royale style zone for the center deathmatch. Instead of one long shrink towards 0,0
 * the border closes in phases, and every phase drifts its center to a random spot inside the
 * current zone. Camping a sky tower in the middle stops being a strategy.
 * <p>
 * Each new zone fits entirely inside the previous one and the transition interpolates center and
 * size together, so anyone already standing in the announced zone is never caught by the wall.
 * </p>
 */
public class MovingZoneTask implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(MovingZoneTask.class.getCanonicalName());

	private static final int REMINDER_SECONDS = 10;

	private final GameManager gameManager;
	private final World world;
	private final int phaseInterval;
	private final int shrinkDuration;
	private final int shrinkPerPhase;
	private final int maxCenterShift;
	private final double endApothem;

	private BukkitTask task;
	private boolean shrinking;
	private int secondsLeft;
	private int phase;

	private double fromX, fromZ, fromApothem;
	private double toX, toZ, toApothem;

	public MovingZoneTask(GameManager gameManager, World world) {
		this.gameManager = gameManager;
		this.world = world;

		MainConfig config = gameManager.getConfig();
		this.phaseInterval = Math.max(1, config.get(MainConfig.DEATHMATCH_MOVING_ZONE_PHASE_INTERVAL));
		this.shrinkDuration = Math.max(1, config.get(MainConfig.DEATHMATCH_MOVING_ZONE_SHRINK_DURATION));
		this.shrinkPerPhase = Math.max(1, config.get(MainConfig.DEATHMATCH_MOVING_ZONE_SHRINK_PER_PHASE));
		this.maxCenterShift = Math.max(0, config.get(MainConfig.DEATHMATCH_MOVING_ZONE_MAX_CENTER_SHIFT));
		this.endApothem = Math.max(1, config.get(MainConfig.DEATHMATCH_END_SIZE));
	}

	public void start() {
		WorldBorder border = world.getWorldBorder();
		Location center = border.getCenter();
		toX = center.getX();
		toZ = center.getZ();
		toApothem = border.getSize() / 2;

		if (toApothem <= endApothem) {
			LOGGER.info("Deathmatch zone already at its end size, the moving zone has nothing to do");
			return;
		}

		planNextPhase();
		task = Bukkit.getScheduler().runTaskTimer(UhcCore.getPlugin(), this, 20, 20);
	}

	@Override
	public void run() {
		if (gameManager.getGameState() != GameState.DEATHMATCH) {
			task.cancel();
			return;
		}

		secondsLeft--;

		if (!shrinking) {
			if (secondsLeft == REMINDER_SECONDS && phaseInterval > REMINDER_SECONDS) {
				announceNextZone();
			}
			if (secondsLeft <= 0) {
				shrinking = true;
				secondsLeft = shrinkDuration;
				gameManager.broadcastInfoMessage(formatZoneMessage(Lang.DEATHMATCH_ZONE_CLOSING, 0));
				gameManager.getPlayerManager().playSoundToAll(UniversalSound.ENDERDRAGON_GROWL.getSound());
			}
			return;
		}

		double progress = 1 - Math.max(0, secondsLeft) / (double) shrinkDuration;
		moveBorder(lerp(fromX, toX, progress), lerp(fromZ, toZ, progress), lerp(fromApothem, toApothem, progress));

		if (secondsLeft <= 0) {
			if (toApothem <= endApothem) {
				gameManager.broadcastInfoMessage(Lang.DEATHMATCH_ZONE_FINAL);
				task.cancel();
				return;
			}
			planNextPhase();
		}
	}

	private void planNextPhase() {
		phase++;
		fromX = toX;
		fromZ = toZ;
		fromApothem = toApothem;

		toApothem = Math.max(endApothem, fromApothem - shrinkPerPhase);
		// Any further and part of the new zone would sit outside the current one.
		double maxShift = Math.min(maxCenterShift, fromApothem - toApothem);
		toX = fromX + randomOffset(maxShift);
		toZ = fromZ + randomOffset(maxShift);

		shrinking = false;
		secondsLeft = phaseInterval;
		LOGGER.fine("Deathmatch zone phase " + phase + ": center " + toX + ", " + toZ + " radius " + toApothem);

		announceNextZone();
	}

	private void announceNextZone() {
		gameManager.broadcastInfoMessage(formatZoneMessage(Lang.DEATHMATCH_ZONE_NEXT, secondsLeft));
		gameManager.getPlayerManager().playSoundToAll(UniversalSound.NOTE_PLING.getSound());
	}

	private String formatZoneMessage(String message, int seconds) {
		return message
			.replace("%x%", String.valueOf((int) Math.floor(toX)))
			.replace("%z%", String.valueOf((int) Math.floor(toZ)))
			.replace("%size%", String.valueOf((int) toApothem))
			.replace("%time%", String.valueOf(seconds))
			.replace("%phase%", String.valueOf(phase));
	}

	private void moveBorder(double centerX, double centerZ, double apothem) {
		WorldBorder border = world.getWorldBorder();
		// setCenter has no transition, so the center jumps a little every second while the
		// size eases over that same second. Small steps, nobody gets teleported by the wall.
		border.setCenter(centerX, centerZ);
		border.setSize(2 * apothem, 1);
	}

	private static double randomOffset(double maxShift) {
		if (maxShift <= 0) {
			return 0;
		}
		return Math.round(ThreadLocalRandom.current().nextDouble(-maxShift, maxShift));
	}

	private static double lerp(double from, double to, double progress) {
		return from + (to - from) * progress;
	}
}
