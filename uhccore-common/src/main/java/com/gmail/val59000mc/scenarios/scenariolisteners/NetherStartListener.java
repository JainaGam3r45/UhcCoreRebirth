package com.gmail.val59000mc.scenarios.scenariolisteners;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.events.UhcPreTeleportEvent;
import com.gmail.val59000mc.players.UhcTeam;
import com.gmail.val59000mc.scenarios.Scenario;
import com.gmail.val59000mc.scenarios.ScenarioListener;
import com.gmail.val59000mc.utils.BiomeBlacklist;
import com.gmail.val59000mc.utils.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;

import java.util.ArrayList;
import java.util.List;

public class NetherStartListener extends ScenarioListener{

	@Override
	public void onEnable(){
		if (!getConfiguration().get(MainConfig.ENABLE_NETHER)){
			Bukkit.broadcastMessage(ChatColor.RED + "[UhcCore] For NetherStart the nether needs to be enabled first!");
			getScenarioManager().disableScenario(Scenario.NETHER_START);
		}
	}

	@EventHandler
	public void onPreTeleport(UhcPreTeleportEvent e){
		World nether = getGameManager().getMapLoader().getUhcWorld(World.Environment.NETHER);
		if (nether == null) {
			return;
		}
		int maxDistance = (int) (nether.getWorldBorder().getSize() / 2);
		double minDistance = getGameManager().getConfig().get(MainConfig.SCATTER_MIN_DISTANCE_BETWEEN_TEAMS);
		List<Location> takenSpawns = new ArrayList<>();

		for(UhcTeam team : getPlayerManager().listUhcTeams()){
			Location newLoc = LocationUtils.getRandomSpawnLocation(nether, maxDistance, BiomeBlacklist.empty(), takenSpawns, minDistance);
			team.setStartingLocation(newLoc);
			takenSpawns.add(newLoc);
		}
	}

}
