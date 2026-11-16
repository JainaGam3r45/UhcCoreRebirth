package com.gmail.val59000mc.versionadapters.spigot_1_12.adapters;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Team;

import com.gmail.val59000mc.versionadapters.ServerVersion;
import com.gmail.val59000mc.versionadapters.adapters.SetTeamColorAdapter;
import com.google.auto.service.AutoService;

@AutoService(SetTeamColorAdapter.class)
public class SetTeamColorAdapterSpigot_1_12 implements SetTeamColorAdapter {

	@Override
	public boolean isCompatible() {
		return ServerVersion.isVersion(12);
	}

	@Override
	public void setTeamColor(Team team, ChatColor color) {
		team.setColor(color);
	}

}
