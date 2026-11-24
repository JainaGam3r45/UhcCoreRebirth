package com.gmail.val59000mc.customitems;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.configuration.YamlFile;
import com.gmail.val59000mc.exceptions.ParseException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.utils.ColorUtils;
import com.gmail.val59000mc.utils.FileUtils;
import com.gmail.val59000mc.utils.JsonItemUtils;
import com.gmail.val59000mc.versionadapters.adapters.ItemDisplayAdapter;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KitsManager{

	private static final Logger LOGGER = Logger.getLogger(KitsManager.class.getCanonicalName());

	private final static List<Kit> kits;
	private static KitMenu menu;

	static{
		kits = new ArrayList<>();
	}

	public static boolean isAtLeastOneKit(){
		return (kits != null && kits.size() > 0);
	}

	public static Kit getFirstKitFor(Player player){
		for (Kit kit : kits){
			if (kit.canBeUsedBy(player, GameManager.getGameManager().getConfig())){
				return kit;
			}
		}
		return null;
	}

	public static void loadKits(){
		LOGGER.info("Start loading kits");

		YamlFile cfg;

		try{
			cfg = FileUtils.saveResourceIfNotAvailable(UhcCore.getPlugin(), "kits.yml");
		}catch (IOException | InvalidConfigurationException ex){
			LOGGER.log(Level.WARNING, "Unable to load kits.yml", ex);
			return;
		}

		kits.clear();

		ConfigurationSection kitsSection = cfg.getConfigurationSection("kits");

		if (kitsSection != null){
			Set<String> kitsKeys = kitsSection.getKeys(false);
			for(String kitKey : kitsKeys){
				ConfigurationSection kitSection = kitsSection.getConfigurationSection(kitKey);

				if (kitSection == null){
					LOGGER.warning("Unable to load kit " + kitKey + ", it is not a section");
					continue;
				}

				// Defaulting to true for enabled since that is the old behavior
				if (!kitSection.getBoolean("enabled", true)) {
					continue;
				}

				try{
					LOGGER.info("Loading kit " + kitKey);
					kits.add(loadKit(kitKey, kitSection));
					LOGGER.info("Added kit " + kitKey);

				// IllegalArgumentException is thrown by builder.build() when the kit has no items. Any other runtime
				// exception means a value of the wrong shape in the YAML, which shouldn't take the other kits down.
				}catch(ParseException | RuntimeException ex){
					LOGGER.log(Level.WARNING, "Unable to load kit "+kitKey+" because of a syntax error", ex);
				}
			}
		}

		menu = KitMenu.build(kits, KitMenuSettings.parse(cfg.getConfigurationSection("gui")));

		LOGGER.info("Loaded " + kits.size() + " kits");
	}

	/**
	 * Each kit and each item can use either syntax, so a file can be migrated bit by bit. The old one is
	 * {@code symbol.item} plus JSON strings, the new one {@code symbol.material} plus item sections.
	 */
	private static Kit loadKit(String kitKey, ConfigurationSection kitSection) throws ParseException{
		Kit.Builder builder = new Kit.Builder(kitKey);

		ConfigurationSection symbolSection = kitSection.getConfigurationSection("symbol");
		if (symbolSection == null){
			throw new ParseException("Missing symbol section");
		}

		List<ItemStack> items = parseItems(kitSection.getList("items"));
		List<String> lore = new ArrayList<>();
		for (ItemStack item : items){
			builder.addItem(item);
			lore.add(ChatColor.WHITE + "" + item.getAmount() + " x " + item.getType().toString().toLowerCase());
		}

		ItemDisplayAdapter display = UhcCore.getVersionAdapterLoader().getVersionAdapter(ItemDisplayAdapter.class);
		ItemStack symbol;
		ItemMeta im;

		if (!symbolSection.contains("material") && symbolSection.isString("item")){
			// Old JSON syntax. Same result as it always had, only the attribute lines are hidden now.
			String name = symbolSection.getString("name");
			symbol = JsonItemUtils.getItemFromJson(symbolSection.getString("item"));
			im = symbol.getItemMeta();

			if (!im.hasDisplayName()) {
				im.setDisplayName(ChatColor.GREEN + name);
			}

			if (!im.hasLore()) {
				im.setLore(lore);
			}

			display.hideAttributes(im);
			builder.setName(name);
		}else{
			if (!symbolSection.contains("material")){
				throw new ParseException("The kit symbol needs a material (or the old JSON 'item')");
			}

			symbol = KitItemParser.parseIcon(symbolSection, null, kitKey);
			im = symbol.getItemMeta();

			if (!im.hasLore()) {
				im.setLore(lore);
			}

			builder.setName(ColorUtils.colorize(symbolSection.getString("name", kitKey)));
			builder.setSlot(parseSlot(kitKey, symbolSection));
		}

		symbol.setItemMeta(im);
		builder.setSymbol(symbol);

		return builder.build();
	}

	private static List<ItemStack> parseItems(@Nullable List<?> rawItems) throws ParseException{
		List<ItemStack> items = new ArrayList<>();
		if (rawItems == null){
			return items;
		}

		for (Object rawItem : rawItems){
			if (rawItem instanceof String){
				items.add(JsonItemUtils.getItemFromJson((String) rawItem));
			}else if (rawItem instanceof Map){
				items.add(KitItemParser.parse((Map<?, ?>) rawItem));
			}else{
				throw new ParseException("A kit item has to be an item section or a JSON string, found: " + rawItem);
			}
		}
		return items;
	}

	@Nullable
	private static Integer parseSlot(String kitKey, ConfigurationSection symbolSection){
		if (!symbolSection.contains("slot")){
			return null;
		}
		if (!symbolSection.isInt("slot")){
			LOGGER.warning("The slot of kit " + kitKey + " must be a whole number, found: " + symbolSection.get("slot")
					+ ". It gets the first free slot instead");
			return null;
		}
		return symbolSection.getInt("slot");
	}

	public static void openKitSelectionInventory(Player player){
		if (menu == null){
			return;
		}

		GameManager gameManager = GameManager.getGameManager();
		UhcPlayer uhcPlayer = gameManager.getPlayerManager().getUhcPlayer(player);
		menu.open(player, uhcPlayer, gameManager.getConfig());
	}

	public static boolean isKitMenu(Inventory inventory){
		return KitMenu.isKitMenu(inventory);
	}

	/**
	 * Handles a click inside the kit selection menu, see {@link #isKitMenu(Inventory)}.
	 * The caller has to cancel the event.
	 */
	public static void handleKitMenuClick(InventoryClickEvent event, UhcPlayer uhcPlayer){
		KitMenu.handleClick(event, (Player) event.getWhoClicked(), uhcPlayer, GameManager.getGameManager().getConfig());
	}

	public static void giveKitTo(Player player){
		UhcPlayer uhcPlayer = GameManager.getGameManager().getPlayerManager().getUhcPlayer(player);
		if(!uhcPlayer.hasKitSelected()){
			uhcPlayer.setKit(KitsManager.getFirstKitFor(player));
		}

		if(uhcPlayer.hasKitSelected() && isAtLeastOneKit()){
			player.getInventory().addItem(uhcPlayer.getKit().getItems());
		}
	}

}
