package com.gmail.val59000mc;

import java.util.logging.Logger;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.players.DeathSnapshot;
import com.gmail.val59000mc.scenarios.scenariolisteners.TimebombListener;
import com.gmail.val59000mc.utils.PluginForwardingHandler;
import com.gmail.val59000mc.utils.UpdateChecker;
import com.gmail.val59000mc.versionadapters.ServerVersion;
import com.gmail.val59000mc.versionadapters.VersionAdapterLoader;

import io.papermc.lib.PaperLib;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class UhcCore extends JavaPlugin{

	private static final Logger LOGGER = Logger.getLogger(UhcCore.class.getCanonicalName());
	// Only the Modern jar ships this. Looked up as a resource because loading the class
	// on an old Java runtime blows up with UnsupportedClassVersionError.
	private static final String MODERN_JAR_MARKER = "com/gmail/val59000mc/utils/ModernVersionUtils.class";

	private static UhcCore pl;
	private static VersionAdapterLoader versionAdapterLoader;
	private Logger forwardingLogger;
	private GameManager gameManager;

	@Override
	public void onEnable(){
		pl = this;
		if (!isRightJarForServer()) {
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		forwardingLogger = PluginForwardingHandler.createForwardingLogger(this);
		gameManager = new GameManager();

		gameManager.loadConfig();
		versionAdapterLoader = VersionAdapterLoader.loadAll(getClassLoader());
		if (gameManager.getConfig().get(MainConfig.CHECK_FOR_UPDATES)) {
			UpdateChecker.checkForUpdates(this);
		}

		if (gameManager.getConfig().get(MainConfig.ENABLE_UHC)) {
			Bukkit.getScheduler().runTaskLater(this, () -> gameManager.loadNewGame(), 1);
			if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
				new UhcCorePlaceholderExpansion(pl).register();
			}
		} else {
			LOGGER.warning("NOTE: UHC is disabled, by the enable-uhc option in plugins/UhcCore/config.yml");
		}
	}

	private boolean isRightJarForServer() {
		final ServerVersion server = ServerVersion.current();
		if (!server.isKnown()) {
			// Can't tell, so don't send anyone off to download the wrong jar
			return true;
		}
		final boolean modernJar = getClassLoader().getResource(MODERN_JAR_MARKER) != null;
		if (modernJar && !ServerVersion.isVersion(21)) {
			getLogger().severe("This is the Modern jar (1.21+) but the server runs " + server
					+ ". Use UhcCoreRebirth-Legacy for 1.8.8-1.20.6.");
			return false;
		}
		// The Modern bridge calls Paper-only API, and a failed bridge load only shows up at FINE.
		if (modernJar && !PaperLib.isPaper()) {
			getLogger().warning("The Modern jar is built for Paper. On plain Spigot some features may not work, run Paper or a fork.");
		}
		if (!modernJar && ServerVersion.isVersion(21)) {
			getLogger().warning("Running the Legacy jar on " + server
					+ ". Switch to UhcCoreRebirth-Modern, it's the one built for 1.21+.");
		}
		return true;
	}

	@Override
	public void onDisable() {
		TimebombListener.cleanAll();
		DeathSnapshot.clear();
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
