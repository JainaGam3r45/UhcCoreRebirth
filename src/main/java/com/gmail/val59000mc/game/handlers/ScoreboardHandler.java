package com.gmail.val59000mc.game.handlers;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.players.*;
import com.gmail.val59000mc.scoreboard.ScoreboardLayout;
import com.gmail.val59000mc.scoreboard.ScoreboardManager;
import com.gmail.val59000mc.scoreboard.ScoreboardType;
import com.gmail.val59000mc.tasks.UpdateScoreboardTask;
import com.gmail.val59000mc.utils.VersionUtils;
import com.gmail.val59000mc.versionadapters.adapters.SetTeamColorAdapter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class ScoreboardHandler {

	private static final Logger LOGGER = Logger.getLogger(ScoreboardHandler.class.getCanonicalName());

	private static final String COLOR_CHAR = String.valueOf(ChatColor.COLOR_CHAR);
	public static final String[] SCOREBOARD_LINES = new String[] {
			ChatColor.UNDERLINE + "" + ChatColor.RESET,
			ChatColor.ITALIC + "" + ChatColor.RESET,
			ChatColor.BOLD + "" + ChatColor.RESET,
			ChatColor.RESET + "" + ChatColor.RESET,
			ChatColor.GREEN + "" + ChatColor.RESET,
			ChatColor.DARK_GRAY + "" + ChatColor.RESET,
			ChatColor.GOLD + "" + ChatColor.RESET,
			ChatColor.RED + "" + ChatColor.RESET,
			ChatColor.YELLOW + "" + ChatColor.RESET,
			ChatColor.WHITE + "" + ChatColor.RESET,
			ChatColor.DARK_GREEN + "" + ChatColor.RESET,
			ChatColor.BLUE + "" + ChatColor.RESET,
			ChatColor.STRIKETHROUGH + "" + ChatColor.RESET,
			ChatColor.MAGIC + "" + ChatColor.RESET,
			ChatColor.DARK_RED + "" + ChatColor.RESET
	};

	private final GameManager gameManager;
	private final MainConfig config;
	private final ScoreboardLayout scoreboardLayout;

	public ScoreboardHandler(GameManager gameManager, MainConfig config, ScoreboardLayout scoreboardLayout) {
		this.gameManager = gameManager;
		this.config = config;
		this.scoreboardLayout = scoreboardLayout;
	}

	public void setUpPlayerScoreboard(UhcPlayer uhcPlayer, Player bukkitPlayer) {

		Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
		uhcPlayer.setScoreboard(scoreboard);
		bukkitPlayer.setScoreboard(scoreboard);

		Objective healthTab = null;
		Objective healthBelowName = null;

		if (config.get(MainConfig.HEARTS_ON_TAB)) {
			healthTab = VersionUtils.getVersionUtils().registerNewObjective(scoreboard, "health_tab", "health", config.get(MainConfig.HEARTS_ON_TAB_RENDER_TYPE));
			healthTab.setDisplaySlot(DisplaySlot.PLAYER_LIST);
		}

		if (config.get(MainConfig.HEARTS_BELOW_NAME)) {
			// Note: renderType option has no effect below name, so no point in specifying it
			healthBelowName = VersionUtils.getVersionUtils().registerNewObjective(scoreboard, ChatColor.RED + "\u2764", "health");
			healthBelowName.setDisplaySlot(DisplaySlot.BELOW_NAME);
		}

		// add teams for no flicker scoreboard
		for (int i = 0; i < 15; i++){
			Team team = scoreboard.registerNewTeam(SCOREBOARD_LINES[i]);
			team.addEntry(SCOREBOARD_LINES[i]);
		}

		boolean disableEnemyNameTags = config.get(MainConfig.DISABLE_ENEMY_NAMETAGS);
		PlayerManager playerManager = gameManager.getPlayerManager();

		// setup teams
		if (config.get(MainConfig.TEAM_COLORS)){
			setColoredTeams(gameManager.getTeamManager(), uhcPlayer, scoreboard, disableEnemyNameTags, healthTab, healthBelowName);
		}else {
			setFriendEnemyTeams(uhcPlayer, playerManager, scoreboard, disableEnemyNameTags, healthTab, healthBelowName);
		}

		updatePlayerOnTab(uhcPlayer);

		Bukkit.getScheduler().scheduleSyncDelayedTask(UhcCore.getPlugin(),new UpdateScoreboardTask(this, uhcPlayer),1L);
	}

	private void setFriendEnemyTeams(UhcPlayer scoreboardPlayer, PlayerManager pm, Scoreboard scoreboard, boolean disableEnemyNameTags, Objective healthTab, Objective healthBelowName) {
		Team friends = scoreboard.registerNewTeam("friends");
		Team enemies = scoreboard.registerNewTeam("enemies");
		friends.setPrefix(ChatColor.GREEN + "");
		enemies.setPrefix(ChatColor.RED + "");
		friends.setSuffix(ChatColor.RESET + "");
		enemies.setSuffix(ChatColor.RESET + "");

		if (disableEnemyNameTags){
			VersionUtils.getVersionUtils().setTeamNameTagVisibility(enemies, false);
		}

		Team spectators = scoreboard.registerNewTeam("spectators");
		spectators.setPrefix(ChatColor.GRAY + "");
		spectators.setSuffix(ChatColor.RESET + "");

		// Putting players in colored teams
		for (UhcPlayer uhcPlayer : pm.getPlayersList()) {
			updatePlayerHealth(uhcPlayer, healthTab, healthBelowName);

			if (uhcPlayer.getState().equals(PlayerState.DEAD) || uhcPlayer.getState().equals(PlayerState.WAITING)){
				spectators.addEntry(uhcPlayer.getName());
			}else if (uhcPlayer.isInTeamWith(scoreboardPlayer)) {
				friends.addEntry(uhcPlayer.getName());
			}else {
				enemies.addEntry(uhcPlayer.getName());
			}

		}
	}

	private void setColoredTeams(TeamManager teamManager, UhcPlayer scoreboardPlayer, Scoreboard scoreboard, boolean disableEnemyNameTags, Objective healthTab, Objective healthBelowName) {
		Team spectators = scoreboard.registerNewTeam("spectators");
		spectators.setPrefix(ChatColor.GRAY + "");
		spectators.setSuffix(ChatColor.RESET + "");

		for (UhcTeam uhcTeam : teamManager.getUhcTeams()) {

			Team team;

			if (uhcTeam.contains(scoreboardPlayer)) {
				// Add 0 to be at the top of the tab list
				team = scoreboard.registerNewTeam("0" + uhcTeam.getTeamNumber());
			} else {
				team = scoreboard.registerNewTeam("" + uhcTeam.getTeamNumber());

				if (disableEnemyNameTags){
					VersionUtils.getVersionUtils().setTeamNameTagVisibility(team, false);
				}
			}

			team.setPrefix(uhcTeam.getPrefix());
			team.setSuffix(ChatColor.RESET + "");
			if (config.get(MainConfig.SET_SCOREBOARD_TEAM_COLORS)) {
				UhcCore.getVersionAdapterLoader().getVersionAdapter(SetTeamColorAdapter.class).setTeamColor(team, uhcTeam.getColor());
			}

			for (UhcPlayer member : uhcTeam.getMembers()) {

				updatePlayerHealth(member, healthTab, healthBelowName);

				if (member.getState().equals(PlayerState.DEAD)) {
					// spec team
					spectators.addEntry(member.getName());
				} else {
					team.addEntry(member.getName());
				}
			}
		}
	}

	private void updatePlayerHealth(UhcPlayer uhcPlayer, Objective healthTab, Objective healthBelowName) {
		Player player;

		try {
			player = uhcPlayer.getPlayer();
		} catch (UhcPlayerNotOnlineException ex) {
			// No health display for offline players.
			return;
		}

		// Note: Player#getHealth() is used for the initial value, but technically it should be
		// Player#getHealth() + Player#getAbsorptionAmount() to match vanilla behavior.
		// The problem is that the getAbsorptionAmount API doesn't exist on older Minecraft versions (e.g. 1.8.8).
		// We could get the absorption amount using NMS, but it should be fine to assume that they have no absorption here.
		if (healthTab != null) {
			setReadOnlyPlayerScore(healthTab.getScore(player.getName()), (int) player.getHealth());
		}
		if (healthBelowName != null) {
			setReadOnlyPlayerScore(healthBelowName.getScore(player.getName()), (int) player.getHealth());
		}
	}

	private static boolean loggedReadOnlyScoreWarning = false;

	// Workaround for https://bugs.mojang.com/browse/MC-111729
	// On 1.20.3+, vanilla made HEALTH criteria strictly read-only (MC-163943), causing score.setScore()
	// to throw an IllegalStateException. We punch through to NMS Scoreboard via reflection to force the initial value.
	private void setReadOnlyPlayerScore(Score score, int value) {
		try {
			score.setScore(value);
		} catch (IllegalStateException e) {
			try {
				Object craftScoreboard = score.getScoreboard();
				Method getHandleScoreboard = craftScoreboard.getClass().getMethod("getHandle");
				Object nmsScoreboard = getHandleScoreboard.invoke(craftScoreboard);

				Object craftObjective = score.getObjective();
				Method getHandleObjective = craftObjective.getClass().getMethod("getHandle");
				Object nmsObjective = getHandleObjective.invoke(craftObjective);

				Method getOrCreateScore = null;
				// Check for 3-parameter (ScoreHolder/String, Objective, boolean) or 2-parameter (ScoreHolder/String, Objective) methods
				for (Method m : nmsScoreboard.getClass().getMethods()) {
					Class<?>[] params = m.getParameterTypes();
					if (params.length == 3 && params[2] == boolean.class && params[1].isAssignableFrom(nmsObjective.getClass())) {
						getOrCreateScore = m;
						break;
					}
					if (params.length == 2 && params[1].isAssignableFrom(nmsObjective.getClass())) {
						getOrCreateScore = m;
						break;
					}
				}
				if (getOrCreateScore == null) {
					for (Method m : nmsScoreboard.getClass().getDeclaredMethods()) {
						Class<?>[] params = m.getParameterTypes();
						if (params.length == 3 && params[2] == boolean.class && params[1].isAssignableFrom(nmsObjective.getClass())) {
							m.setAccessible(true);
							getOrCreateScore = m;
							break;
						}
						if (params.length == 2 && params[1].isAssignableFrom(nmsObjective.getClass())) {
							m.setAccessible(true);
							getOrCreateScore = m;
							break;
						}
					}
				}

				if (getOrCreateScore != null) {
					Class<?> holderParam = getOrCreateScore.getParameterTypes()[0];
					Object holder;
					if (holderParam == String.class) {
						holder = score.getEntry();
					} else {
						holder = Proxy.newProxyInstance(
								holderParam.getClassLoader(),
								new Class<?>[]{holderParam},
								(proxy, method, args) -> {
									if (method.getReturnType() == String.class) {
										return score.getEntry();
									}
									if ("equals".equals(method.getName()) && args != null && args.length == 1) {
										return proxy == args[0];
									}
									if ("hashCode".equals(method.getName())) {
										return score.getEntry().hashCode();
									}
									if ("toString".equals(method.getName())) {
										return score.getEntry();
									}
									return null;
								}
						);
					}

					Object scoreAccess;
					if (getOrCreateScore.getParameterCount() == 3) {
						scoreAccess = getOrCreateScore.invoke(nmsScoreboard, holder, nmsObjective, true);
					} else {
						scoreAccess = getOrCreateScore.invoke(nmsScoreboard, holder, nmsObjective);
					}

					boolean scoreSet = false;
					for (Method m : scoreAccess.getClass().getMethods()) {
						if ((m.getReturnType() == void.class || m.getReturnType() == int.class)
								&& m.getParameterCount() == 1
								&& m.getParameterTypes()[0] == int.class) {
							m.invoke(scoreAccess, value);
							scoreSet = true;
							break;
						}
					}
					if (!scoreSet) {
						for (Method m : scoreAccess.getClass().getDeclaredMethods()) {
							if ((m.getReturnType() == void.class || m.getReturnType() == int.class)
									&& m.getParameterCount() == 1
									&& m.getParameterTypes()[0] == int.class) {
								m.setAccessible(true);
								m.invoke(scoreAccess, value);
								break;
							}
						}
					}
				}
			} catch (Exception ex) {
				if (!loggedReadOnlyScoreWarning) {
					loggedReadOnlyScoreWarning = true;
					LOGGER.log(Level.WARNING, "Failed to update read-only health score on scoreboard via reflection (suppressing further warnings): " + ex.getMessage());
				}
			}
		}
	}

	/**
	 * Updates the display of a player, on the tab list of each player on the server.
	 *
	 * @param uhcPlayer the player to update on the tab list
	 */
	public void updatePlayerOnTab(UhcPlayer uhcPlayer) {
		for (UhcPlayer scoreboardOwner : gameManager.getPlayerManager().getPlayersList()) {
			if (config.get(MainConfig.TEAM_COLORS)) {
				updatePlayerOnColoredTab(uhcPlayer, scoreboardOwner);
			}else {
				updatePlayerOnFriendEnemyTab(uhcPlayer, scoreboardOwner);
			}
		}

		if (config.get(MainConfig.CHANGE_DISPLAY_NAMES)) {
			try {
				uhcPlayer.getPlayer().setDisplayName(uhcPlayer.getDisplayName());
			}catch (UhcPlayerNotOnlineException ex){
				// Player left while updating tab.
			}
		}
	}

	/**
	 * Updates the display of all players in a team, on the tab list of each player on the server.
	 *
	 * @param uhcTeam the team to update on the tab list
	 */
	public void updateTeamOnTab(UhcTeam uhcTeam) {
		for (UhcPlayer teamMember : uhcTeam.getMembers()) {
			updatePlayerOnTab(teamMember);
		}
	}

	private void updatePlayerOnFriendEnemyTab(UhcPlayer uhcPlayer, UhcPlayer scoreboardOwner) {
		Scoreboard scoreboard = scoreboardOwner.getScoreboard();
		if (scoreboard == null) {
			return;
		}

		if (uhcPlayer.getState().equals(PlayerState.PLAYING)) {
			if (scoreboardOwner.isInTeamWith(uhcPlayer)) {
				// add to there friend team
				scoreboard.getTeam("friends").addEntry(uhcPlayer.getName());
			} else {
				// add to enemies team
				scoreboard.getTeam("enemies").addEntry(uhcPlayer.getName());
			}
		} else {
			// add to spectators team
			scoreboard.getTeam("spectators").addEntry(uhcPlayer.getName());
		}
	}

	private void updatePlayerOnColoredTab(UhcPlayer uhcPlayer, UhcPlayer scoreboardOwner) {
		Scoreboard scoreboard = scoreboardOwner.getScoreboard();
		if (scoreboard == null) {
			return;
		}

		// Add to no-team team
		if (uhcPlayer.getState() == PlayerState.DEAD) {
			Team team = scoreboard.getTeam("spectators");
			if (team != null) {
				team.addEntry(uhcPlayer.getName());
			}

			return;
		}

		Team team;
		if (scoreboardOwner.isInTeamWith(uhcPlayer)) {
			// Add to their team with 0 in front
			team = scoreboard.getTeam("0" + uhcPlayer.getTeam().getTeamNumber());
			if (team == null){
				team = scoreboard.registerNewTeam("0" + uhcPlayer.getTeam().getTeamNumber());
			}
		} else {
			// Add to normal team
			team = scoreboard.getTeam("" + uhcPlayer.getTeam().getTeamNumber());
			if (team == null){
				team = scoreboard.registerNewTeam("" + uhcPlayer.getTeam().getTeamNumber());

				if (config.get(MainConfig.DISABLE_ENEMY_NAMETAGS)){
					VersionUtils.getVersionUtils().setTeamNameTagVisibility(team, false);
				}
			}
		}

		team.setPrefix(uhcPlayer.getTeam().getPrefix());
		team.setSuffix(ChatColor.RESET + "");
		team.addEntry(uhcPlayer.getName());
	}

	public void resetObjective(Scoreboard scoreboard, ScoreboardType scoreboardType) {
		Objective objective = scoreboard.getObjective("informations");

		if (objective != null) {
			objective.unregister();
		}

		objective = VersionUtils.getVersionUtils().registerNewObjective(scoreboard, "informations", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		objective.setDisplayName(scoreboardLayout.getTitle());

		int lines = scoreboardLayout.getLines(scoreboardType).size();

		for (int i = 0; i < lines; i++){
			Score score = objective.getScore(SCOREBOARD_LINES[i]);
			score.setScore(i);
		}
	}

	public void updatePlayerSidebar(UhcPlayer uhcPlayer, ScoreboardType scoreboardType) {
		ScoreboardManager scoreboardManager = gameManager.getScoreboardManager();

		Player player;

		try {
			player = uhcPlayer.getPlayer();
		}catch (UhcPlayerNotOnlineException ignored) {
			// Player left while updating sidebar
			return;
		}

		int i = 0;
		for (String line : scoreboardLayout.getLines(scoreboardType)){

			String first = "";
			String second = "";

			if (!line.isEmpty()) {

				String translatedLine = scoreboardManager.translatePlaceholders(line, uhcPlayer, player);

				if (translatedLine.length() <= 16){
					first = translatedLine;
				}else {

					int split = 16;

					first = translatedLine.substring(0, split);
					boolean copyColor = true;

					if (first.endsWith(COLOR_CHAR)){
						copyColor = false;
						split = 15;
						first = translatedLine.substring(0, split);

						if (first.substring(0, 14).endsWith(COLOR_CHAR)){
							split = 13;
							first = translatedLine.substring(0, split);
						}
					}

					if (copyColor) {
						second = ChatColor.getLastColors(first);
					}

					second += translatedLine.substring(split);

					if (second.length() > 16){
						LOGGER.warning("Scoreboard line is too long: '" + translatedLine + "'!");
						second = "";
					}
				}
			}

			Team lineTeam = uhcPlayer.getScoreboard().getTeam(SCOREBOARD_LINES[i]);

			if (!lineTeam.getPrefix().equals(first)){
				lineTeam.setPrefix(first);
			}
			if (!lineTeam.getSuffix().equals(second)){
				lineTeam.setSuffix(second);
			}

			i++;
		}
	}

	public ScoreboardType getPlayerScoreboardType(UhcPlayer uhcPlayer) {
		if (uhcPlayer.getState().equals(PlayerState.DEAD)) {
			return ScoreboardType.SPECTATING;
		}
		switch (gameManager.getGameState()) {
			case LOADING:
			case WAITING:
			case STARTING: {
				return ScoreboardType.WAITING;
			}
			case PLAYING:
			case ENDED: {
				return ScoreboardType.PLAYING;
			}
			case DEATHMATCH: {
				return ScoreboardType.DEATHMATCH;
			}
		}
		return ScoreboardType.WAITING;
	}

}
