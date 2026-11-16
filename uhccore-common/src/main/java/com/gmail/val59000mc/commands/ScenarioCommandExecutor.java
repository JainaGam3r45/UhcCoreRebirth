package com.gmail.val59000mc.commands;

import com.gmail.val59000mc.scenarios.ScenarioManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public class ScenarioCommandExecutor implements CommandExecutor{

	private final ScenarioManager scenarioManager;

	public ScenarioCommandExecutor(ScenarioManager scenarioManager){
		this.scenarioManager = scenarioManager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
		if (args.length >= 1 && (args[0].equalsIgnoreCase("revive") || args[0].equalsIgnoreCase("latescatter") || args[0].equalsIgnoreCase("timer"))){
			PluginCommand uhcCmd = Bukkit.getPluginCommand("uhccore");
			if (uhcCmd != null) {
				return uhcCmd.execute(sender, "uhccore", args);
			}
		}

		if (sender instanceof Player){
			Player p = (Player) sender;
			// get inventory
			p.openInventory(scenarioManager.getScenarioMainInventory(p.hasPermission("uhc-core.scenarios.edit")));
		}else {
			sender.sendMessage("Only players can use this command.");
		}
		return true;
	}

}
