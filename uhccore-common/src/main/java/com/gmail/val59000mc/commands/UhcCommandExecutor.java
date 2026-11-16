package com.gmail.val59000mc.commands;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.exceptions.UhcTeamException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.GameState;
import com.gmail.val59000mc.host.HostMenu;
import com.gmail.val59000mc.players.PlayerState;
import com.gmail.val59000mc.players.PlayerManager;
import com.gmail.val59000mc.players.TeamManager;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.players.UhcTeam;
import com.gmail.val59000mc.tasks.PreStartTask;
import com.gmail.val59000mc.utils.TimeUtils;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UhcCommandExecutor implements CommandExecutor{

	private static final Logger LOGGER = Logger.getLogger(UhcCommandExecutor.class.getCanonicalName());

	private final GameManager gameManager;

	public UhcCommandExecutor(GameManager gameManager){
		this.gameManager = gameManager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){

		if (args.length == 1 && args[0].equalsIgnoreCase("version")){
			sender.sendMessage(ChatColor.GREEN + "UhcCore version: " + UhcCore.getPlugin().getDescription().getVersion());
			return true;
		}

		if (args.length == 1 && args[0].equalsIgnoreCase("reload")){
			if (!sender.hasPermission("uhc-core.commands.reload")){
				sender.sendMessage(ChatColor.RED + "You don't have the permission to use this command");
				return true;
			}

			gameManager.getScoreboardManager().getScoreboardLayout().loadFile();
			Bukkit.getServer().resetRecipes();
			gameManager.loadConfig();
			sender.sendMessage(ChatColor.GREEN + "config.yml, lang.yml and scoreboard.yml have been reloaded");
			return true;
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("revive")){
			if (!sender.hasPermission("uhc-core.commands.revive")){
				sender.sendMessage(ChatColor.RED + "You don't have permission to revive players.");
				return true;
			}
			return handleReviveSubCommand(sender, args);
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("latescatter")){
			if (!sender.hasPermission("uhc-core.commands.latescatter")){
				sender.sendMessage(ChatColor.RED + "You don't have permission to late scatter players.");
				return true;
			}
			return handleLateScatterSubCommand(sender, args);
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("timer")){
			if (!sender.hasPermission("uhc-core.commands.timer")){
				sender.sendMessage(ChatColor.RED + "You don't have permission to modify the game timer.");
				return true;
			}
			return handleTimerSubCommand(sender, args);
		}

		if (args.length == 1 && args[0].equalsIgnoreCase("host")){
			if (!sender.hasPermission(HostMenu.PERMISSION)){
				sender.sendMessage(ChatColor.RED + "You don't have permission to change the host settings.");
				return true;
			}
			return handleHostSubCommand(sender);
		}

		// debug commands
		if (!sender.hasPermission("uhc-core.commands.debug")){
			sender.sendMessage(ChatColor.RED + "You don't have the permission to use UhcCore debug commands");
			return true;
		}

		if (args.length == 0){
			sender.sendMessage("Invalid command");
			return true;
		}

		PlayerManager pm = gameManager.getPlayerManager();

		switch(args[0]){
			case "gamestate":
				if(args.length == 2){
					try{
						GameState gameState = GameState.valueOf(args[1].toUpperCase());
						gameManager.setGameState(gameState);
						sender.sendMessage("Changed gamestate to: " + gameState.toString());
						return true;
					}catch(IllegalArgumentException e){
						sender.sendMessage(args[1]+" is not a valid game state");
						return true;
					}
				}else {
					sender.sendMessage("Current gamestate: " + gameManager.getGameState());
					return true;
				}
			case "playerstate":
				if(args.length == 3){
					try{
						Player player = Bukkit.getPlayer(args[1]);
						if(player == null){
							sender.sendMessage("Player "+args[1]+" is not online");
							return true;
						}
						PlayerState playerState = PlayerState.valueOf(args[2].toUpperCase());
						pm.getUhcPlayer(player).setState(playerState);
						sender.sendMessage("Changed " + player.getName() + "'s playerstate to " + playerState);
						return true;
					}catch(IllegalArgumentException e){
						sender.sendMessage(args[2]+" is not a valid player state");
						return true;
					}
				}else {
					sender.sendMessage("Invalid playerstate command");
					return true;
				}

			case "pvp":
				if(args.length == 2){
					boolean state = Boolean.parseBoolean(args[1]);
					gameManager.setPvp(state);
					sender.sendMessage("Changed PvP to " + state);
				}else {
					sender.sendMessage("Invalid pvp command");
				}
				return true;

			case "listplayers":
				listUhcPlayers(sender);
				return true;

			case "listteams":
				listUhcTeams(sender);
				return true;

			case "pause":
				String pauseState = PreStartTask.togglePause();
				sender.sendMessage("The starting task state is now : "+pauseState);
				return true;

			case "force":
				String forceState = PreStartTask.toggleForce();
				sender.sendMessage("The starting task state is now : "+forceState);
				return true;

			case "location":
				if (sender instanceof Player){
					sender.sendMessage(((Player) sender).getLocation().toString());
				}else{
					sender.sendMessage("Only players can use this sub-command.");
				}
				return true;
		}

		sender.sendMessage("Unknown sub command " + args[0]);
		return true;
	}


	private void listUhcPlayers(CommandSender sender) {
		StringBuilder str = new StringBuilder();
		str.append("Current UhcPlayers : ");
		for(UhcPlayer player : gameManager.getPlayerManager().getPlayersList()){
			str.append(player.getName());
			str.append(" ");
		}
		sender.sendMessage(str.toString());
	}

	private void listUhcTeams(CommandSender sender) {
		StringBuilder str;
		LOGGER.info("Current UhcTeams : ");

		for(UhcTeam team : gameManager.getPlayerManager().listUhcTeams()){
			str = new StringBuilder();
			str.append("Team ").append(team.getLeader().getName()).append(" : ");
			for(UhcPlayer player : team.getMembers()){
				str.append(player.getName()).append(" ");
			}
			sender.sendMessage(str.toString());
		}
	}

	private boolean handleReviveSubCommand(CommandSender sender, String[] args) {
		if (gameManager.getGameState() != GameState.PLAYING && gameManager.getGameState() != GameState.DEATHMATCH) {
			sender.sendMessage(ChatColor.RED + "You can only use this command while playing!");
			return true;
		}

		if (args.length < 2) {
			sender.sendMessage(ChatColor.RED + "Usage: /uhc revive <player> [clear]");
			return true;
		}

		String targetName = args[1];
		boolean spawnWithItems = args.length < 3 || !args[2].equalsIgnoreCase("clear");

		PlayerManager pm = gameManager.getPlayerManager();
		UhcPlayer targetUhcPlayer = null;
		for (UhcPlayer up : pm.getPlayersList()) {
			if (up.getName().equalsIgnoreCase(targetName) || up.getRealName().equalsIgnoreCase(targetName)) {
				targetUhcPlayer = up;
				break;
			}
		}

		if (targetUhcPlayer == null) {
			sender.sendMessage(ChatColor.RED + "Player " + targetName + " not found!");
			return true;
		}

		if (!targetUhcPlayer.isDead()) {
			sender.sendMessage(ChatColor.RED + "Player " + targetName + " is not dead!");
			return true;
		}

		pm.revivePlayer(targetUhcPlayer, spawnWithItems);

		try {
			Player onlinePlayer = targetUhcPlayer.getPlayer();
			onlinePlayer.sendMessage(ChatColor.GREEN + "You have been revived by an administrator!");
			sender.sendMessage(ChatColor.GREEN + targetUhcPlayer.getName() + " has been revived!");
		} catch (UhcPlayerNotOnlineException e) {
			sender.sendMessage(ChatColor.GREEN + targetUhcPlayer.getName() + " can now join the game!");
		}
		return true;
	}

	private boolean handleLateScatterSubCommand(CommandSender sender, String[] args) {
		if (gameManager.getGameState() != GameState.PLAYING && gameManager.getGameState() != GameState.DEATHMATCH) {
			sender.sendMessage(ChatColor.RED + "You can only use this command while playing!");
			return true;
		}

		if (args.length < 2) {
			sender.sendMessage(ChatColor.RED + "Usage: /uhc latescatter <player> [team]");
			return true;
		}

		String targetName = args[1];
		PlayerManager pm = gameManager.getPlayerManager();
		TeamManager tm = gameManager.getTeamManager();
		UhcPlayer targetUhcPlayer = null;
		for (UhcPlayer up : pm.getPlayersList()) {
			if (up.getName().equalsIgnoreCase(targetName) || up.getRealName().equalsIgnoreCase(targetName)) {
				targetUhcPlayer = up;
				break;
			}
		}

		if (targetUhcPlayer == null) {
			sender.sendMessage(ChatColor.RED + "Player " + targetName + " not found!");
			return true;
		}

		Player onlinePlayer;
		try {
			onlinePlayer = targetUhcPlayer.getPlayer();
		} catch (UhcPlayerNotOnlineException e) {
			sender.sendMessage(ChatColor.RED + "Player " + targetUhcPlayer.getName() + " must be online to be late-scattered!");
			return true;
		}

		if (targetUhcPlayer.getState() == PlayerState.PLAYING) {
			sender.sendMessage(ChatColor.RED + "Player " + targetUhcPlayer.getName() + " is already playing!");
			return true;
		}

		// Mid-game joiners show up as DEAD spectators, and those are exactly who latescatter is for.
		// Anyone who was actually in the game and died needs the revive cleanup (zombie, timebomb, snapshot).
		if (targetUhcPlayer.getState() == PlayerState.DEAD && targetUhcPlayer.getHasBeenTeleportedToLocation()) {
			sender.sendMessage(ChatColor.RED + "Player " + targetUhcPlayer.getName() + " is dead, use /uhc revive instead.");
			return true;
		}

		if (args.length >= 3) {
			String teamQuery = args[2];
			UhcTeam targetTeam = tm.getTeamByName(teamQuery);
			if (targetTeam == null) {
				for (UhcTeam team : tm.getUhcTeams()) {
					if (String.valueOf(team.getTeamNumber()).equalsIgnoreCase(teamQuery) || team.getTeamName().equalsIgnoreCase(teamQuery)) {
						targetTeam = team;
						break;
					}
				}
			}

			if (targetTeam != null) {
				try {
					targetTeam.join(targetUhcPlayer);
				} catch (UhcTeamException e) {
					sender.sendMessage(ChatColor.RED + "Failed to join team: " + e.getMessage());
					return true;
				}
			} else {
				sender.sendMessage(ChatColor.RED + "Team " + teamQuery + " not found!");
				return true;
			}
		} else {
			// Every UhcPlayer starts in their own solo team, so getTeam() is never null here.
			if (targetUhcPlayer.isSolo() && gameManager.getConfig().get(MainConfig.AUTO_ASSIGN_PLAYER_TO_TEAM)) {
				pm.autoAssignPlayerToTeam(targetUhcPlayer);
			}
		}

		pm.lateScatterPlayer(targetUhcPlayer);
		sender.sendMessage(ChatColor.GREEN + targetUhcPlayer.getName() + " has been late scattered into the game!");
		onlinePlayer.sendMessage(ChatColor.GREEN + "You have been scattered into the game!");
		return true;
	}

	private boolean handleHostSubCommand(CommandSender sender) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "Only players can open the host menu.");
			return true;
		}
		if (gameManager.getGameState() != GameState.WAITING) {
			sender.sendMessage(ChatColor.RED + "Host settings can only be changed while players wait in the lobby.");
			return true;
		}

		new HostMenu(gameManager).open((Player) sender);
		return true;
	}

	private boolean handleTimerSubCommand(CommandSender sender, String[] args) {
		if (gameManager.getGameState() != GameState.PLAYING && gameManager.getGameState() != GameState.DEATHMATCH) {
			sender.sendMessage(ChatColor.RED + "You can only use this command while playing!");
			return true;
		}

		if (args.length < 2) {
			sender.sendMessage(ChatColor.RED + "Usage: /uhc timer <set|add|pause> [seconds]");
			return true;
		}

		String sub = args[1].toLowerCase();
		switch (sub) {
			case "pause": {
				boolean paused = gameManager.toggleTimerPause();
				if (paused) {
					gameManager.broadcastInfoMessage(ChatColor.GOLD + "The game timer has been paused by an administrator.");
					sender.sendMessage(ChatColor.GREEN + "Timer paused.");
				} else {
					gameManager.broadcastInfoMessage(ChatColor.GOLD + "The game timer has been resumed.");
					sender.sendMessage(ChatColor.GREEN + "Timer resumed.");
				}
				return true;
			}
			case "set": {
				if (args.length < 3) {
					sender.sendMessage(ChatColor.RED + "Usage: /uhc timer set <seconds>");
					return true;
				}
				Long parsed = parseTimerSeconds(sender, args[2], false);
				if (parsed == null) {
					return true;
				}
				long seconds = parsed;
				gameManager.setTimer(seconds);
				gameManager.broadcastInfoMessage(ChatColor.GOLD + "Game timer set to " + TimeUtils.getFormattedTime(seconds) + " by an administrator.");
				sender.sendMessage(ChatColor.GREEN + "Timer set to " + seconds + "s (" + TimeUtils.getFormattedTime(seconds) + ").");
				return true;
			}
			case "add": {
				if (args.length < 3) {
					sender.sendMessage(ChatColor.RED + "Usage: /uhc timer add <seconds>");
					return true;
				}
				Long parsed = parseTimerSeconds(sender, args[2], true);
				if (parsed == null) {
					return true;
				}
				long seconds = parsed;
				gameManager.adjustTimer(seconds);
				long current = gameManager.getElapsedTime();
				gameManager.broadcastInfoMessage(ChatColor.GOLD + "Game timer adjusted by " + (seconds >= 0 ? "+" : "") + seconds + "s by an administrator.");
				sender.sendMessage(ChatColor.GREEN + "Timer adjusted by " + seconds + "s. Current time: " + TimeUtils.getFormattedTime(current));
				return true;
			}
			default:
				sender.sendMessage(ChatColor.RED + "Usage: /uhc timer <set|add|pause> [seconds]");
				return true;
		}
	}

	// Capped at int range so the PvP countdown (an int) and elapsed + diff can't overflow.
	private Long parseTimerSeconds(CommandSender sender, String input, boolean allowNegative) {
		long seconds;
		try {
			seconds = Long.parseLong(input);
		} catch (NumberFormatException e) {
			sender.sendMessage(ChatColor.RED + "Invalid number of seconds: " + input);
			return null;
		}

		long min = allowNegative ? -Integer.MAX_VALUE : 0;
		if (seconds < min || seconds > Integer.MAX_VALUE) {
			sender.sendMessage(ChatColor.RED + "Seconds must be between " + min + " and " + Integer.MAX_VALUE + ".");
			return null;
		}
		return seconds;
	}

}
