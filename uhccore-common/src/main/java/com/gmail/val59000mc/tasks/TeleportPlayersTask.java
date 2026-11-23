package com.gmail.val59000mc.tasks;

import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.players.UhcTeam;
import com.gmail.val59000mc.utils.LocationUtils;
import com.gmail.val59000mc.utils.MainThread;
import io.papermc.lib.PaperLib;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class TeleportPlayersTask implements Runnable{

	private static final Logger LOGGER = Logger.getLogger(TeleportPlayersTask.class.getCanonicalName());

	private final GameManager gameManager;
	private final UhcTeam team;
	private final Runnable onTeleported;

	public TeleportPlayersTask(GameManager gameManager, UhcTeam team) {
		this(gameManager, team, null);
	}

	/**
	 * @param onTeleported runs on the main thread once the team has been teleported, even if nobody was online
	 */
	public TeleportPlayersTask(GameManager gameManager, UhcTeam team, @Nullable Runnable onTeleported) {
		this.gameManager = gameManager;
		this.team = team;
		this.onTeleported = onTeleported;
	}

	@Override
	public void run() {
		Location destination = team.getStartingLocation();
		if (destination == null || !ChunkLoaderTask.canRunOffMainThread()) {
			teleportMembers();
			return;
		}

		// Teleporting into an unloaded chunk reads it from disk right there on the main thread.
		// Loading it first through Paper keeps a big scatter from turning into one long lag spike.
		PaperLib.getChunkAtAsync(destination).whenComplete((chunk, error) -> {
			if (error != null) {
				LOGGER.log(Level.WARNING, "Unable to preload the spawn chunk of team #" + team.getTeamNumber(), error);
			}
			MainThread.execute(this::teleportMembers);
		});
	}

	private void teleportMembers() {
		try {
			for(UhcPlayer uhcPlayer : team.getMembers()){
				Player player;
				try {
					player = uhcPlayer.getPlayer();
				} catch (UhcPlayerNotOnlineException ignored) {
					continue;
				}

				LOGGER.info("Teleporting " + player.getName() + " to " + team.getStartingLocation());

				uhcPlayer.freezePlayer(team.getStartingLocation());

				UhcPlayer.teleport(player, LocationUtils.withSameDirection(team.getStartingLocation(), player));

				gameManager.getPlayerManager().setPlayerStartPlaying(uhcPlayer);

				uhcPlayer.setHasBeenTeleportedToLocation(true);
			}
		} finally {
			if (onTeleported != null) {
				onTeleported.run();
			}
		}
	}

}
