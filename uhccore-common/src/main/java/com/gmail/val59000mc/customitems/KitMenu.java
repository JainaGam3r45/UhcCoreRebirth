package com.gmail.val59000mc.customitems;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.utils.VersionUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * The kit selection menu. The slot of every kit is worked out once when kits.yml loads, each player
 * then gets their own copy with the %status% placeholder filled in.
 * <p>
 * Clicks are matched through the {@link Holder} and its slot map, never by comparing item names:
 * names carry colors (and hex), and the admin can change them any time.
 */
final class KitMenu {

	private static final Logger LOGGER = Logger.getLogger(KitMenu.class.getCanonicalName());

	static final String STATUS_PLACEHOLDER = "%status%";

	// Old servers throw when an inventory title is longer than this.
	private static final int LEGACY_MAX_TITLE_LENGTH = 32;

	private final KitMenuSettings settings;
	private final Map<Integer, Kit> kitSlots;
	private final boolean closeButtonShown;

	private KitMenu(KitMenuSettings settings, Map<Integer, Kit> kitSlots, boolean closeButtonShown) {
		this.settings = settings;
		this.kitSlots = kitSlots;
		this.closeButtonShown = closeButtonShown;
	}

	/**
	 * Decides where every kit goes. A kit with a valid free {@code slot} gets it, the rest take the first free
	 * slot from the top. Slots the filler was explicitly given, and the close button's slot, don't count as free
	 * for those: an admin who lists {@code 0-8} as filler wants a clean border, not kits in it.
	 */
	static KitMenu build(List<Kit> kits, KitMenuSettings settings) {
		final int size = settings.getSize();
		final Map<Integer, Kit> placed = new TreeMap<>();
		final List<Kit> unplaced = new ArrayList<>();

		for (Kit kit : kits) {
			final Integer wanted = kit.getSlot();
			if (wanted == null) {
				unplaced.add(kit);
			} else if (wanted < 0 || wanted >= size) {
				LOGGER.warning("Kit " + kit.getKey() + " asks for slot " + wanted + " but the menu only has " + size
						+ " slots (0-" + (size - 1) + "), placing it in the first free one");
				unplaced.add(kit);
			} else if (placed.containsKey(wanted)) {
				LOGGER.warning("Kit " + kit.getKey() + " asks for slot " + wanted + " which kit "
						+ placed.get(wanted).getKey() + " already uses, placing it in the first free one");
				unplaced.add(kit);
			} else {
				placed.put(wanted, kit);
			}
		}

		final boolean hasCloseButton = settings.getCloseButton() != null;
		int next = 0;
		for (Kit kit : unplaced) {
			while (next < size && (placed.containsKey(next) || settings.isReservedForFiller(next)
					|| (hasCloseButton && next == settings.getCloseSlot()))) {
				next++;
			}
			if (next >= size) {
				LOGGER.warning("No free slot left in the kit menu for kit " + kit.getKey()
						+ ", raise gui.rows or free some filler slots. The kit can't be picked");
				continue;
			}
			placed.put(next++, kit);
		}

		boolean closeButtonShown = hasCloseButton;
		if (hasCloseButton && placed.containsKey(settings.getCloseSlot())) {
			LOGGER.warning("gui.close_button.slot " + settings.getCloseSlot() + " is used by kit "
					+ placed.get(settings.getCloseSlot()).getKey() + ", the close button is hidden");
			closeButtonShown = false;
		}

		return new KitMenu(settings, Collections.unmodifiableMap(placed), closeButtonShown);
	}

	void open(Player player, UhcPlayer uhcPlayer, MainConfig config) {
		final Holder holder = new Holder(this);
		final Inventory inventory = Bukkit.createInventory(holder, settings.getSize(), resolveTitle());
		holder.inventory = inventory;

		for (int slot = 0; slot < settings.getSize(); slot++) {
			final Kit kit = kitSlots.get(slot);
			if (kit != null) {
				inventory.setItem(slot, renderSymbol(kit, player, uhcPlayer, config));
			} else if (closeButtonShown && slot == settings.getCloseSlot()) {
				inventory.setItem(slot, settings.getCloseButton().clone());
			} else if (settings.isFillerSlot(slot)) {
				inventory.setItem(slot, settings.getFiller().clone());
			}
		}

		player.openInventory(inventory);
	}

	static boolean isKitMenu(Inventory inventory) {
		return inventory.getHolder() instanceof Holder;
	}

	/**
	 * Handles a click in an inventory that {@link #isKitMenu} accepted. The caller cancels the event first,
	 * so nothing can be taken out of, or shift-clicked into, the menu.
	 */
	static void handleClick(InventoryClickEvent event, Player player, UhcPlayer uhcPlayer, MainConfig config) {
		final Inventory top = event.getInventory();
		final KitMenu menu = ((Holder) top.getHolder()).menu;

		final int slot = event.getRawSlot();
		// Clicks in the player's own inventory (or outside the window) have a raw slot past the menu.
		if (slot < 0 || slot >= top.getSize()) {
			return;
		}

		final Kit kit = menu.kitSlots.get(slot);
		if (kit != null) {
			if (kit.canBeUsedBy(player, config)) {
				uhcPlayer.setKit(kit);
				uhcPlayer.sendMessage(Lang.ITEMS_KIT_SELECTED.replace("%kit%", kit.getName()));
				menu.settings.getSuccessSound().play(player);
			} else {
				uhcPlayer.sendMessage(Lang.ITEMS_KIT_NO_PERMISSION);
				menu.settings.getErrorSound().play(player);
			}
			player.closeInventory();
			return;
		}

		// Filler and the close button. Empty slots stay quiet.
		if (top.getItem(slot) == null) {
			return;
		}
		menu.settings.getClickSound().play(player);
		if (menu.closeButtonShown && slot == menu.settings.getCloseSlot()) {
			player.closeInventory();
		}
	}

	private ItemStack renderSymbol(Kit kit, Player player, UhcPlayer uhcPlayer, MainConfig config) {
		final ItemStack symbol = kit.getSymbol().clone();
		final ItemMeta meta = symbol.getItemMeta();
		if (meta == null || !meta.hasLore()) {
			return symbol;
		}

		final String status = statusFor(kit, player, uhcPlayer, config);
		final List<String> lore = new ArrayList<>();
		for (String line : meta.getLore()) {
			lore.add(line.replace(STATUS_PLACEHOLDER, status));
		}
		meta.setLore(lore);
		symbol.setItemMeta(meta);
		return symbol;
	}

	private String statusFor(Kit kit, Player player, UhcPlayer uhcPlayer, MainConfig config) {
		// Compared by key, a reload creates new Kit objects for the same kit.
		if (uhcPlayer.hasKitSelected() && uhcPlayer.getKit().getKey().equals(kit.getKey())) {
			return settings.getStatusSelected();
		}
		return kit.canBeUsedBy(player, config) ? settings.getStatusAvailable() : settings.getStatusNoPermission();
	}

	private String resolveTitle() {
		String title = settings.getTitle() != null ? settings.getTitle() : Lang.ITEMS_KIT_INVENTORY;

		// Hex colors take 14 characters each, so long titles are normal on 1.16+. Only cut where the
		// limit may still bite: before 1.14, where hex doesn't exist anyway.
		if (!VersionUtils.isAtLeast(14) && title.length() > LEGACY_MAX_TITLE_LENGTH) {
			title = title.substring(0, LEGACY_MAX_TITLE_LENGTH);
			// Don't leave a dangling section sign, the client prints it as garbage.
			if (title.endsWith("§")) {
				title = title.substring(0, title.length() - 1);
			}
		}
		return title;
	}

	private static class Holder implements InventoryHolder {

		private final KitMenu menu;
		private Inventory inventory;

		private Holder(KitMenu menu) {
			this.menu = menu;
		}

		@Override
		public Inventory getInventory() {
			return inventory;
		}

	}

}
