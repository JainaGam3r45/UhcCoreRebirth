package com.gmail.val59000mc.commands;

import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.GameState;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.players.PlayerState;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.players.UhcTeam;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamCoordsCommandExecutor implements CommandExecutor{

	private static final long COOLDOWN_MILLIS = 3000L;

	private final GameManager gameManager;
	private final Map<UUID, Long> lastShared;

	public TeamCoordsCommandExecutor(GameManager gameManager){
		this.gameManager = gameManager;
		this.lastShared = new HashMap<>();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if (!(sender instanceof Player)){
			sender.sendMessage("Only players can use this command!");
			return true;
		}

		Player player = (Player) sender;
		GameState gameState = gameManager.getGameState();

		if (gameState != GameState.PLAYING && gameState != GameState.DEATHMATCH){
			player.sendMessage(Lang.COMMAND_TEAMCOORDS_ERROR_GAME);
			return true;
		}

		UhcPlayer uhcPlayer = gameManager.getPlayerManager().getUhcPlayer(player);

		if (uhcPlayer.getState() != PlayerState.PLAYING){
			player.sendMessage(Lang.COMMAND_TEAMCOORDS_ERROR_PLAYING);
			return true;
		}

		UhcTeam team = uhcPlayer.getTeam();

		if (team.isSolo()){
			player.sendMessage(Lang.COMMAND_TEAMCOORDS_ERROR_SOLO);
			return true;
		}

		long now = System.currentTimeMillis();
		Long last = lastShared.get(player.getUniqueId());

		if (last != null && now - last < COOLDOWN_MILLIS){
			long secondsLeft = (COOLDOWN_MILLIS - (now - last) + 999L) / 1000L;
			player.sendMessage(Lang.COMMAND_TEAMCOORDS_COOLDOWN.replace("%seconds%", Long.toString(secondsLeft)));
			return true;
		}

		// Expired entries are useless, drop them here so the map never outgrows the online player count.
		lastShared.values().removeIf(time -> now - time >= COOLDOWN_MILLIS);
		lastShared.put(player.getUniqueId(), now);

		Location location = player.getLocation();
		team.sendMessage(Lang.COMMAND_TEAMCOORDS_MESSAGE
				.replace("%player%", uhcPlayer.getRealName())
				.replace("%x%", Integer.toString(location.getBlockX()))
				.replace("%y%", Integer.toString(location.getBlockY()))
				.replace("%z%", Integer.toString(location.getBlockZ()))
				.replace("%world%", getWorldLabel(location.getWorld())));
		return true;
	}

	private String getWorldLabel(World world){
		switch (world.getEnvironment()){
			case NORMAL:
				return Lang.COMMAND_TEAMCOORDS_WORLD_OVERWORLD;
			case NETHER:
				return Lang.COMMAND_TEAMCOORDS_WORLD_NETHER;
			case THE_END:
				return Lang.COMMAND_TEAMCOORDS_WORLD_END;
			default:
				// CUSTOM only exists on newer APIs; datapack worlds still deserve a readable name.
				return world.getName();
		}
	}

}
