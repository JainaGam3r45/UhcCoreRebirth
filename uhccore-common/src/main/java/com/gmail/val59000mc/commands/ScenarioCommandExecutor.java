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
		if (args.length >= 2 && args[0].equalsIgnoreCase("revive")){
			PluginCommand reviveCmd = Bukkit.getPluginCommand("revive");
			if (reviveCmd != null) {
				String[] reviveArgs = new String[args.length - 1];
				System.arraycopy(args, 1, reviveArgs, 0, reviveArgs.length);
				return reviveCmd.execute(sender, "revive", reviveArgs);
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
