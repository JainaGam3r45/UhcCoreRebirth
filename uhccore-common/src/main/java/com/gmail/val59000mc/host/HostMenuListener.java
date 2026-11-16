package com.gmail.val59000mc.host;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class HostMenuListener implements Listener {

	// Leave ignoreCancelled off: Spigot pre-cancels every click made in spectator mode
	// (same story as in ItemsListener).
	@EventHandler
	public void onClick(InventoryClickEvent event) {
		// event.getInventory() is the top inventory, and going through it instead of the
		// InventoryView dodges the class-to-interface switch that view got in 1.21.
		Inventory top = event.getInventory();
		if (!(top.getHolder() instanceof HostMenu)) {
			return;
		}

		// Cancel everything, including shift clicks from the player's own inventory,
		// or people can stuff random items into the menu.
		event.setCancelled(true);

		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}
		int slot = event.getRawSlot();
		if (slot < 0 || slot >= top.getSize()) {
			return;
		}

		((HostMenu) top.getHolder()).handleClick((Player) event.getWhoClicked(), slot, event.getClick());
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getInventory().getHolder() instanceof HostMenu) {
			event.setCancelled(true);
		}
	}

}
