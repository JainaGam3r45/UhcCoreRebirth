package com.gmail.val59000mc.host;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.customitems.UhcItems;
import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.game.GameState;
import com.gmail.val59000mc.players.PlayerState;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.players.UhcTeam;
import com.gmail.val59000mc.scenarios.ScenarioManager;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Pre-game settings GUI for hosts. Changes live in memory only: a /uhccore reload or the
 * post-game restart puts config.yml back in charge.
 *
 * The menu is recognised by its holder instead of its title, which keeps it working no matter
 * what lang.yml or a resource pack does to inventory names.
 */
public class HostMenu implements InventoryHolder {

	private static final Logger LOGGER = Logger.getLogger(HostMenu.class.getCanonicalName());

	public static final String PERMISSION = "uhc-core.commands.host";

	private static final String TITLE = ChatColor.DARK_GRAY + "UHC Host Settings";
	private static final int SIZE = 5 * 9;
	private static final int SCENARIOS_SLOT = 39;
	private static final int CLOSE_SLOT = 41;

	private final GameManager gameManager;
	private final MainConfig config;
	private final Map<Integer, HostSetting> settings;
	private final Inventory inventory;

	public HostMenu(GameManager gameManager) {
		this.gameManager = gameManager;
		this.config = gameManager.getConfig();
		this.settings = createSettings(config);
		this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
		render();
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	private static Map<Integer, HostSetting> createSettings(MainConfig config) {
		Map<Integer, HostSetting> settings = new LinkedHashMap<>();

		settings.put(10, HostSetting.integer("team-size", Material.NAME_TAG, "Team Size", MainConfig.MAX_PLAYERS_PER_TEAM,
				1, 4, 1, 32, size -> size + (size == 1 ? " player" : " players"),
				"Max players per team.", "Set it to 1 for a solo game."));
		settings.put(11, HostSetting.seconds("pvp-time", Material.DIAMOND_SWORD, "PvP Time", MainConfig.TIME_BEFORE_PVP,
				60, 300, 0, 7200,
				"Grace period before PvP turns on."));
		settings.put(12, HostSetting.toggle("friendly-fire", Material.FLINT_AND_STEEL, "Friendly Fire", MainConfig.ENABLE_FRIENDLY_FIRE,
				"Lets teammates hurt each other."));
		settings.put(13, HostSetting.toggle("spectate-after-death", Material.ENDER_PEARL, "Spectate After Death", MainConfig.CAN_SPECTATE_AFTER_DEATH,
				"Dead players stay around as spectators."));
		settings.put(14, HostSetting.percentage("apple-rate", Material.APPLE, "Apple Rate", MainConfig.APPLE_DROP_PERCENTAGE,
				0.5, 2.5,
				"Extra apple chance per leaf block,", "on top of vanilla drops (vanilla is 0.5%)."));
		settings.put(15, HostSetting.toggle("day-night-cycle", Material.GLOWSTONE_DUST, "Day/Night Cycle", MainConfig.ENABLE_DAY_NIGHT_CYCLE,
				"Disabled means eternal noon."));

		// Start size is missing on purpose: the border and pregen already happened when the world loaded.
		int borderStartSize = config.get(MainConfig.BORDER_START_SIZE);
		settings.put(19, HostSetting.toggle("border-moving", Material.BEDROCK, "Moving Border", MainConfig.BORDER_IS_MOVING,
				"Shrinks the border during the game."));
		settings.put(20, HostSetting.integer("border-end-size", Material.COMPASS, "Border End Size", MainConfig.BORDER_END_SIZE,
				25, 100, 0, borderStartSize, size -> size + " blocks",
				"Radius the border shrinks down to.", "Capped at the start size (+/-" + borderStartSize + ")."));
		settings.put(21, HostSetting.seconds("border-time-before-shrink", Material.FEATHER, "Border Shrink Delay", MainConfig.BORDER_TIME_BEFORE_SHRINK,
				60, 600, 0, 14400,
				"Time before the border starts moving."));
		settings.put(22, HostSetting.seconds("border-time-to-shrink", Material.FISHING_ROD, "Border Shrink Duration", MainConfig.BORDER_TIME_TO_SHRINK,
				60, 600, 60, 14400,
				"How long the border takes", "to reach its end size."));

		settings.put(28, HostSetting.toggle("deathmatch", Material.BOW, "Deathmatch", MainConfig.ENABLE_DEATHMATCH,
				"Teleports everyone to the arena", "once the deathmatch timer runs out."));
		settings.put(29, HostSetting.seconds("deathmatch-delay", Material.ARROW, "Deathmatch Time", MainConfig.DEATHMATCH_DELAY,
				60, 600, 60, 14400,
				"Time before the deathmatch starts."));
		settings.put(30, HostSetting.toggle("final-heal", Material.GOLDEN_APPLE, "Final Heal", MainConfig.ENABLE_FINAL_HEAL,
				"Heals every player once, mid game."));
		settings.put(31, HostSetting.seconds("final-heal-delay", Material.SUGAR, "Final Heal Time", MainConfig.FINAL_HEAL_DELAY,
				60, 300, 0, 14400,
				"When the final heal kicks in."));
		// Read when deathmatch PvP turns on, so flipping it in the lobby is enough. Arena deathmatches
		// never start the surface task, hence the warning in the lore.
		settings.put(32, HostSetting.toggle("meetup-surface", Material.LADDER, "MeetUp Surface Rule", MainConfig.DEATHMATCH_SURFACE_ENABLE,
				"Pulls players hiding underground or", "outside the border back to the surface.",
				"Center deathmatch only, ignored", "when an arena is loaded."));
		// MeetupSurfaceTask copies both values in its constructor, same timing as the toggle above.
		// Depth starts at 1: the head block is always air, so 0 and 1 both trip on the block right above it.
		settings.put(33, HostSetting.integer("meetup-surface-max-depth", Material.IRON_PICKAXE, "MeetUp Max Depth", MainConfig.DEATHMATCH_SURFACE_MAX_DEPTH,
				1, 5, 1, 64, depth -> depth + (depth == 1 ? " block" : " blocks"),
				"Solid roof a player can have above", "them before getting pulled up.",
				"Only used when the surface rule is on."));
		settings.put(34, HostSetting.seconds("meetup-surface-check-interval", Material.REDSTONE, "MeetUp Check Interval", MainConfig.DEATHMATCH_SURFACE_CHECK_INTERVAL,
				1, 5, 1, 60,
				"How often players get checked.", "Lower catches diggers sooner.",
				"Only used when the surface rule is on."));

		return settings;
	}

	private void render() {
		inventory.clear();
		for (Map.Entry<Integer, HostSetting> entry : settings.entrySet()) {
			inventory.setItem(entry.getKey(), entry.getValue().render(config));
		}
		inventory.setItem(SCENARIOS_SLOT, namedItem(Material.BOOK, ChatColor.GOLD + "Scenarios", ChatColor.GRAY + "Open the scenario editor."));
		inventory.setItem(CLOSE_SLOT, namedItem(Material.BARRIER, ChatColor.RED + "Close"));
	}

	void handleClick(Player player, int slot, ClickType click) {
		// The menu can stay open while the countdown fires and the scatter begins.
		if (gameManager.getGameState() != GameState.WAITING) {
			player.closeInventory();
			player.sendMessage(ChatColor.RED + "The game is already starting, host settings are locked.");
			return;
		}
		if (!player.hasPermission(PERMISSION)) {
			player.closeInventory();
			return;
		}

		if (slot == CLOSE_SLOT) {
			player.closeInventory();
			return;
		}
		if (slot == SCENARIOS_SLOT) {
			openScenarioEditor(player);
			return;
		}

		HostSetting setting = settings.get(slot);
		if (setting == null) {
			return;
		}

		int oldTeamSize = config.get(MainConfig.MAX_PLAYERS_PER_TEAM);
		if (!setting.handleClick(config, click)) {
			return;
		}

		inventory.setItem(slot, setting.render(config));
		applySideEffects(player, setting, oldTeamSize);
		LOGGER.info(player.getName() + " changed host setting " + setting.getKey() + " to " + ChatColor.stripColor(setting.formatValue(config)));
	}

	private void applySideEffects(Player host, HostSetting setting, int oldTeamSize) {
		switch (setting.getKey()) {
			case "deathmatch":
			case "deathmatch-delay":
				// remainingTime is only computed in loadConfig, so the scoreboard countdown
				// would keep the old value without this.
				gameManager.setRemainingTime(config.get(MainConfig.ENABLE_DEATHMATCH) ? config.get(MainConfig.DEATHMATCH_DELAY) : 0);
				break;
			case "team-size":
				int newTeamSize = config.get(MainConfig.MAX_PLAYERS_PER_TEAM);
				warnAboutOversizedTeams(host, newTeamSize);

				boolean wasSolo = oldTeamSize <= 1;
				boolean isSolo = newTeamSize <= 1;
				if (wasSolo != isSolo) {
					// Team menu items only get handed out on join, so swap them for everyone in the lobby.
					refreshLobbyItems();
				}
				break;
			default:
				break;
		}
	}

	// Nobody gets kicked out of an existing team, so the host has to sort out the leftovers.
	private void warnAboutOversizedTeams(Player host, int teamSize) {
		int oversized = 0;
		for (UhcTeam team : gameManager.getTeamManager().getUhcTeams()) {
			if (team.getMembers().size() > teamSize) {
				oversized++;
			}
		}
		if (oversized > 0) {
			host.sendMessage(ChatColor.YELLOW + "Heads up: " + oversized + " team(s) already have more than "
					+ teamSize + " player(s). They stay as they are until someone leaves.");
		}
	}

	private void refreshLobbyItems() {
		for (UhcPlayer uhcPlayer : gameManager.getPlayerManager().getPlayersList()) {
			if (uhcPlayer.getState() != PlayerState.WAITING) {
				continue;
			}
			try {
				Player player = uhcPlayer.getPlayer();
				player.getInventory().clear();
				UhcItems.giveLobbyItemsTo(player);
			} catch (UhcPlayerNotOnlineException ignored) {
				// Offline players get a fresh set when they come back.
			}
		}
	}

	private void openScenarioEditor(Player player) {
		ScenarioManager scenarioManager = gameManager.getScenarioManager();
		if (player.hasPermission("uhc-core.scenarios.edit")) {
			gameManager.getPlayerManager().getUhcPlayer(player).setBrowsingPage(0);
			player.openInventory(scenarioManager.getScenarioEditInventory(0));
		} else {
			player.openInventory(scenarioManager.getScenarioMainInventory(false));
		}
	}

	private static ItemStack namedItem(Material material, String name, String... lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		if (lore.length > 0) {
			meta.setLore(Arrays.asList(lore));
		}
		item.setItemMeta(meta);
		return item;
	}

}
