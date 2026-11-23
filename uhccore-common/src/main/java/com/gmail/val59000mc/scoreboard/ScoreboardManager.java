package com.gmail.val59000mc.scoreboard;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.configuration.VaultManager;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.handlers.ScoreboardHandler;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.players.*;
import com.gmail.val59000mc.scenarios.Scenario;
import com.gmail.val59000mc.scenarios.scenariolisteners.SilentNightListener;
import com.gmail.val59000mc.scoreboard.placeholders.BlocksToTeamLeader;
import com.gmail.val59000mc.scoreboard.placeholders.ScenariosPlaceholder;
import com.gmail.val59000mc.scoreboard.placeholders.TeamMembersPlaceholder;
import com.gmail.val59000mc.scoreboard.placeholders.TimersPlaceholder;
import com.gmail.val59000mc.utils.LocationUtils;
import com.gmail.val59000mc.utils.TimeUtils;
import com.gmail.val59000mc.utils.VersionUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ScoreboardManager {

	private static final Logger LOGGER = Logger.getLogger(ScoreboardManager.class.getCanonicalName());

	// Anything bigger is the vanilla ~30M default (or a world nobody configured), not a UHC border.
	private static final int MAX_UHC_BORDER_APOTHEM = 50000;
	// Prefix + suffix: 16 + 16 before the 1.13 scoreboard rewrite, 64 + 64 after it.
	private static final int MAX_LEGACY_LINE_LENGTH = 32;
	private static final int MAX_MODERN_LINE_LENGTH = 128;

	private final ScoreboardHandler scoreboardHandler;
	private final ScoreboardLayout scoreboardLayout;
	private final List<Placeholder> placeholders;

	public ScoreboardManager(ScoreboardHandler scoreboardHandler, ScoreboardLayout scoreboardLayout) {
		this.scoreboardHandler = scoreboardHandler;
		this.scoreboardLayout = scoreboardLayout;

		scoreboardLayout.loadFile();
		placeholders = new ArrayList<>();
		placeholders.add(new BlocksToTeamLeader());
		placeholders.add(new TeamMembersPlaceholder());
		placeholders.add(new ScenariosPlaceholder());
		placeholders.add(new TimersPlaceholder());
	}

	public ScoreboardLayout getScoreboardLayout() {
		return scoreboardLayout;
	}

	public String translatePlaceholders(String s, UhcPlayer uhcPlayer, Player bukkitPlayer){

		String returnString = s;
		GameManager gm = GameManager.getGameManager();
		MainConfig cfg = gm.getConfig();

		returnString = returnString.replace("%online%",Bukkit.getOnlinePlayers().size() + "").replace("%needed%",cfg.get(MainConfig.MIN_PLAYERS_TO_START) + "");

		if (returnString.contains("%kit%")){
			if (uhcPlayer.hasKitSelected()){
				returnString = returnString.replace("%kit%", uhcPlayer.getKit().getName());
			}else{
				returnString = returnString.replace("%kit%", Lang.ITEMS_KIT_SCOREBOARD_NO_KIT);
			}
		}

		if (returnString.contains("%kills%")){
			returnString = returnString.replace("%kills%",uhcPlayer.getKills() + "");
		}

		if (returnString.contains("%teamKills%")){
			returnString = returnString.replace("%teamKills%",uhcPlayer.getTeam().getKills() + "");
		}

		if (returnString.contains("%teamColor%")){
			returnString = returnString.replace("%teamColor%", uhcPlayer.getTeam().getColor() + "\u25A0");
		}

		if (returnString.contains("%teamNumber%")){
			returnString = returnString.replace("%teamNumber%",uhcPlayer.getTeam().getTeamNumber() + "");
		}

		if (returnString.contains("%border%")){
			returnString = returnString.replace("%border%", getBorderString(gm, cfg, bukkitPlayer));
		}

		if (returnString.contains("%ylayer%")){
			returnString = returnString.replace("%ylayer%",(int) bukkitPlayer.getLocation().getY() + "");
		}

		if (returnString.contains("%xCoordinate%")){
			returnString = returnString.replace("%xCoordinate%",(int) bukkitPlayer.getLocation().getX() + "");
		}

		if (returnString.contains("%zCoordinate%")){
			returnString = returnString.replace("%zCoordinate%",(int) bukkitPlayer.getLocation().getZ() + "");
		}

		if (returnString.contains("%deathmatch%")){
			returnString = returnString.replace("%deathmatch%", gm.getFormattedRemainingTime());
		}

		if (returnString.contains("%time%")){
			returnString = returnString.replace("%time%", TimeUtils.getFormattedTime(gm.getElapsedTime()));
		}

		if (returnString.contains("%pvp%")){
			long pvp = cfg.get(MainConfig.TIME_BEFORE_PVP) - gm.getElapsedTime();

			if (pvp < 0){
				returnString = returnString.replace("%pvp%", "-");
			}else {
				returnString = returnString.replace("%pvp%", TimeUtils.getFormattedTime(pvp));
			}
		}

		if (returnString.contains("%alive%")){
			if (
					gm.getScenarioManager().isEnabled(Scenario.SILENT_NIGHT) &&
							((SilentNightListener) gm.getScenarioManager().getScenarioListener(Scenario.SILENT_NIGHT)).isNightMode()
			){
				returnString = returnString.replace("%alive%","?");
			}else{
				returnString = returnString.replace("%alive%",gm.getPlayerManager().getOnlinePlayingPlayers().size() + "");
			}
		}

		if (returnString.contains("%episode%")){
			returnString = returnString.replace("%episode%",gm.getEpisodeNumber() + "");
		}

		if (returnString.contains("%nextEpisode%")){
			returnString = returnString.replace("%nextEpisode%", TimeUtils.getFormattedTime(gm.getTimeUntilNextEpisode()) + "");
		}

		if (returnString.contains("%teamAlive%")){
			returnString = returnString.replace("%teamAlive%", String.valueOf(gm.getTeamManager().getPlayingUhcTeams().size()));
		}

		if (returnString.contains("%playerAlive%")){
			returnString = returnString.replace("%playerAlive%", String.valueOf(gm.getPlayerManager().getAllPlayingPlayers().size()));
		}

		if (returnString.contains("%playerSpectator%")){
			long count = gm.getPlayerManager().getPlayersList()
					.stream()
					.filter(UhcPlayer::isDead)
					.filter(UhcPlayer::isOnline)
					.count();
			returnString = returnString.replace("%playerSpectator%", String.valueOf(count));
		}

		if (returnString.contains("%money%")){
			returnString = returnString.replace("%money%", String.format("%.2f", VaultManager.getPlayerMoney(bukkitPlayer)));
		}

		for (Placeholder placeholder : placeholders){
			returnString = placeholder.parseString(returnString, uhcPlayer, bukkitPlayer);
		}

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			returnString = PlaceholderAPI.setPlaceholders(bukkitPlayer, returnString);
		}

		if (!VersionUtils.isAtLeast(13)) {
			if (returnString.length() > MAX_LEGACY_LINE_LENGTH){
				LOGGER.warning("Scoreboard line is too long: '" + returnString + "'!");
				returnString = "";
			}
		} else if (returnString.length() > MAX_MODERN_LINE_LENGTH) {
			returnString = returnString.substring(0, MAX_MODERN_LINE_LENGTH);
		}
		return returnString;
	}

	/**
	 * Always reads the main UHC world: players in the lobby world (or anywhere without a border)
	 * used to get the vanilla ~30M border, which blew the 16 char limit on 1.8 every second.
	 */
	private String getBorderString(GameManager gm, MainConfig cfg, Player bukkitPlayer) {
		World uhcWorld = gm.getMapLoader() == null ? null : gm.getMapLoader().getUhcWorld(Environment.NORMAL);
		int apothem = uhcWorld == null ? -1 : (int) (uhcWorld.getWorldBorder().getSize() / 2);

		if (apothem <= 0 || apothem > MAX_UHC_BORDER_APOTHEM) {
			int configuredApothem = cfg.get(MainConfig.BORDER_START_SIZE);
			return configuredApothem > 0 ? ChatColor.GREEN + "+" + configuredApothem + " -" + configuredApothem : "-";
		}

		String borderString = "+" + apothem + " -" + apothem;

		// Colored by the border the player is actually standing in, the nether one is scaled down.
		World playerWorld = bukkitPlayer.getWorld();
		if (!gm.getMapLoader().isUhcWorld(playerWorld)) {
			return ChatColor.GREEN + borderString;
		}
		WorldBorder playerBorder = playerWorld.getWorldBorder();
		double distance = LocationUtils.getDistanceToBorder(playerBorder, bukkitPlayer.getLocation().getX(), bukkitPlayer.getLocation().getZ());

		if (distance <= 5){
			return ChatColor.RED + borderString;
		}else if (distance <= 50){
			return ChatColor.YELLOW + borderString;
		}
		return ChatColor.GREEN + borderString;
	}

	/**
	 * Used to register custom placeholders.
	 * @param placeholder The placeholder you want to register.
	 */
	public void registerPlaceholder(Placeholder placeholder){
		placeholders.add(placeholder);
	}

	public void updatePlayerOnTab(UhcPlayer uhcPlayer) {
		scoreboardHandler.updatePlayerOnTab(uhcPlayer);
	}

	public void updateTeamOnTab(UhcTeam uhcTeam) {
		scoreboardHandler.updateTeamOnTab(uhcTeam);
	}

}
