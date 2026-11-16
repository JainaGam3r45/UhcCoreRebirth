package com.gmail.val59000mc.tasks;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.GameState;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.utils.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps the center deathmatch on the surface: anyone who digs in to wait out
 * the shrinking border, or drifts outside it, gets dragged back up inside it.
 */
public class MeetupSurfaceTask implements Runnable {

	// Targets stay this far inside the border, so one that keeps closing in doesn't
	// catch the player again a few seconds after we moved them.
	private static final int BORDER_MARGIN = 3;
	// Wide enough to get out from under a decent lake or lava pool; with a 2 block
	// radius hiding under one was a free pass. Rings go outward and stop at the first
	// hit, so the full 33x33 sweep only happens when everything around is liquid.
	private static final int SEARCH_RADIUS = 16;
	// Two blocks out reaches past the walls of a 1x1 or 2x2 shaft (and the middle of a 3x3).
	private static final int SHAFT_PROBE = 2;

	private final GameManager gameManager;
	private final World world;
	private final int maxDepth;
	private final int checkIntervalTicks;
	private final Deque<UhcPlayer> pending = new ArrayDeque<>();
	private BukkitTask task;
	private int ticks;

	public MeetupSurfaceTask(GameManager gameManager, World world) {
		this.gameManager = gameManager;
		this.world = world;
		MainConfig config = gameManager.getConfig();
		// Same floor as the host menu: at 0 one branch log overhead already counts as a roof.
		this.maxDepth = Math.max(1, config.get(MainConfig.DEATHMATCH_SURFACE_MAX_DEPTH));
		// Capped so a fat-fingered config value can't overflow into a zero modulus below.
		this.checkIntervalTicks = 20 * Math.max(1, Math.min(3600, config.get(MainConfig.DEATHMATCH_SURFACE_CHECK_INTERVAL)));
	}

	public void start() {
		task = Bukkit.getScheduler().runTaskTimer(UhcCore.getPlugin(), this, 1L, 1L);
	}

	@Override
	public void run() {
		// Game ended or got reset: nothing left to babysit.
		if (gameManager.getGameState() != GameState.DEATHMATCH) {
			task.cancel();
			pending.clear();
			return;
		}

		if (ticks++ % checkIntervalTicks == 0) {
			queueStrandedPlayers();
		}

		// One teleport per tick, so a meetup full of cave campers doesn't turn
		// into a single fat lag spike (1.8 also loads a 5x5 chunk area per spot).
		UhcPlayer next = pending.poll();
		if (next != null) {
			pullUp(next);
		}
	}

	private void queueStrandedPlayers() {
		for (UhcPlayer uhcPlayer : gameManager.getPlayerManager().getOnlinePlayingPlayers()) {
			if (uhcPlayer.isFrozen() || pending.contains(uhcPlayer)) {
				continue;
			}

			try {
				if (isStranded(uhcPlayer.getPlayer())) {
					pending.add(uhcPlayer);
				}
			} catch (UhcPlayerNotOnlineException ignored) {
				// Logged off between the online filter and here, the next check picks them up again.
			}
		}
	}

	private boolean isStranded(Player player) {
		Location location = player.getLocation();
		if (!world.equals(location.getWorld())) {
			return false;
		}
		return isOutsideBorder(location) || isUnderground(location);
	}

	private boolean isOutsideBorder(Location location) {
		WorldBorder border = world.getWorldBorder();
		double halfSize = border.getSize() / 2;
		Location center = border.getCenter();
		return Math.abs(location.getX() - center.getX()) > halfSize || Math.abs(location.getZ() - center.getZ()) > halfSize;
	}

	private boolean isUnderground(Location location) {
		int x = location.getBlockX();
		int feetY = location.getBlockY();
		int z = location.getBlockZ();

		// Solid roof over their head: classic cave camping. Counting blocks instead of
		// flagging the first one is what lets a stray branch log over someone slide.
		if (hasTooMuchCover(x, z, feetY + 2)) {
			return true;
		}

		// Digging straight down drags the heightmap of their own column with them,
		// so a shaft only shows up as tall walls all around. Walls count from the
		// player's own level, which makes it plain "how deep did they dig".
		// Requiring all four sides keeps people standing next to a cliff out of it.
		return hasTooMuchCover(x + SHAFT_PROBE, z, feetY)
			&& hasTooMuchCover(x - SHAFT_PROBE, z, feetY)
			&& hasTooMuchCover(x, z + SHAFT_PROBE, feetY)
			&& hasTooMuchCover(x, z - SHAFT_PROBE, feetY);
	}

	/**
	 * Counts the blocks that actually hide a player between {@code fromY} and the
	 * column's heightmap top, bailing out once there are more than max-depth.
	 * Leaves, glass and water don't occlude, so canopies and lakes never count.
	 */
	private boolean hasTooMuchCover(int x, int z, int fromY) {
		int surfaceY = LocationUtils.getSurfaceLevelAt(world, x, z);
		int cover = 0;
		for (int y = fromY; y <= surfaceY; y++) {
			if (world.getBlockAt(x, y, z).getType().isOccluding() && ++cover > maxDepth) {
				return true;
			}
		}
		return false;
	}

	private void pullUp(UhcPlayer uhcPlayer) {
		Player player;
		try {
			player = uhcPlayer.getPlayer();
		} catch (UhcPlayerNotOnlineException e) {
			return;
		}

		// They had up to a few ticks in the queue to climb out, die or log off.
		Location location = player.getLocation();
		if (!uhcPlayer.isPlaying() || uhcPlayer.isFrozen() || !world.equals(location.getWorld())) {
			return;
		}
		boolean outsideBorder = isOutsideBorder(location);
		if (!outsideBorder && !isUnderground(location)) {
			return;
		}

		// In an open shaft the player's own column tops out at the shaft floor, so the
		// nearest "surface" is the exact spot they're standing on. Anything at or below
		// the allowed depth would just get them flagged again. Someone caught outside the
		// border on a hill is fine landing lower, they're not hiding.
		int minY = outsideBorder ? Integer.MIN_VALUE : location.getBlockY() + maxDepth + 1;

		WorldBorder border = world.getWorldBorder();
		Location center = border.getCenter();
		double halfSize = border.getSize() / 2;
		// With a border thinner than the margin the limit hits 0 and no block center fits,
		// so tiny borders just keep the player within the middle half instead.
		double limit = halfSize - Math.min(BORDER_MARGIN, halfSize / 2);
		int x = (int) Math.floor(clamp(location.getX(), center.getX() - limit, center.getX() + limit));
		int z = (int) Math.floor(clamp(location.getZ(), center.getZ() - limit, center.getZ() + limit));

		Location target = LocationUtils.getSafeSurfaceLocationNear(world, x, z, SEARCH_RADIUS, minY, limit);
		if (target == null) {
			// Lava lake, cactus field or nothing tall enough nearby. Better to leave them
			// be until the next check than to drop them into lava ourselves.
			return;
		}
		player.setFallDistance(0);
		// Another plugin can cancel the teleport; no point bragging about a pull that never happened.
		if (UhcPlayer.teleport(player, LocationUtils.withSameDirection(target, player))) {
			uhcPlayer.sendMessage(Lang.GAME_MEETUP_PULLED_TO_SURFACE);
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

}
