package com.gmail.val59000mc;

import java.util.logging.Logger;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.utils.PluginForwardingHandler;
import com.gmail.val59000mc.versionadapters.VersionAdapterLoader;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class UhcCore extends JavaPlugin{

	private static final Logger LOGGER = Logger.getLogger(UhcCore.class.getCanonicalName());

	private static UhcCore pl;
	private static VersionAdapterLoader versionAdapterLoader;
	private Logger forwardingLogger;
	private GameManager gameManager;

	@Override
	public void onEnable(){
		pl = this;
		forwardingLogger = PluginForwardingHandler.createForwardingLogger(this);
		gameManager = new GameManager();

		gameManager.loadConfig();
		versionAdapterLoader = VersionAdapterLoader.loadAll(getClassLoader());
		if (gameManager.getConfig().get(MainConfig.ENABLE_UHC)) {
			Bukkit.getScheduler().runTaskLater(this, () -> gameManager.loadNewGame(), 1);
			if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
				new UhcCorePlaceholderExpansion(pl).register();
			}
		} else {
			LOGGER.warning("NOTE: UHC is disabled, by the enable-uhc option in plugins/UhcCore/config.yml");
		}
	}

	public static UhcCore getPlugin(){
		return pl;
	}

	public Logger getForwardingLogger() {
		return forwardingLogger;
	}

	public static VersionAdapterLoader getVersionAdapterLoader() {
		return versionAdapterLoader;
	}

}
