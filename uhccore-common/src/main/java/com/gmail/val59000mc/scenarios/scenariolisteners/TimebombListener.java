package com.gmail.val59000mc.scenarios.scenariolisteners;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.scenarios.Option;
import com.gmail.val59000mc.scenarios.ScenarioListener;
import com.gmail.val59000mc.utils.VersionUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TimebombListener extends ScenarioListener{

	private static final Map<String, TimebombTask> ACTIVE_TIMEBOMBS = new ConcurrentHashMap<>();

	@Option
	private long delay = 30;

	@Option(key = "explosion-power")
	private double explosionPower = 10;

	public static void cancelForPlayer(String playerName) {
		TimebombTask task = ACTIVE_TIMEBOMBS.remove(playerName.toLowerCase());
		if (task != null) {
			task.cancelAndClean();
		}
	}

	public static void cleanAll() {
		for (TimebombTask task : ACTIVE_TIMEBOMBS.values()) {
			task.cancelAndClean();
		}
		ACTIVE_TIMEBOMBS.clear();
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onPlayerDeath(PlayerDeathEvent e) {
		Player p = e.getEntity().getPlayer();
		List<ItemStack> drops = new ArrayList<>(e.getDrops());
		e.getDrops().removeAll(e.getDrops());

		TimebombTask timebombTask = new TimebombTask(drops, p.getLocation().getBlock().getLocation(), p.getName(), delay, (float) explosionPower);
		ACTIVE_TIMEBOMBS.put(p.getName().toLowerCase(), timebombTask);
		Bukkit.getScheduler().scheduleSyncDelayedTask(UhcCore.getPlugin(), timebombTask, 1L);
	}

	public static class TimebombTask implements Runnable{

		private ArmorStand armorStand;
		private Block block1, block2;
		private long timeLeft;
		private Location loc;
		private List<ItemStack> drops;
		private String name;
		private boolean spawned;
		private float explosionPower;
		private volatile boolean cancelled = false;

		public TimebombTask(List<ItemStack> drops, Location loc, String name, long delay, float explosionPower) {
			this.drops = drops;
			this.loc = loc;
			this.name = name;
			timeLeft = delay;
			spawned = false;
			this.explosionPower = explosionPower;
		}

		public void cancelAndClean() {
			this.cancelled = true;
			if (armorStand != null && !armorStand.isDead()) {
				armorStand.remove();
			}
			if (block1 != null && block1.getType() == Material.CHEST) {
				if (block1.getState() instanceof Chest) {
					((Chest) block1.getState()).getInventory().clear();
				}
				block1.setType(Material.AIR);
			}
			if (block2 != null && block2.getType() == Material.CHEST) {
				if (block2.getState() instanceof Chest) {
					((Chest) block2.getState()).getInventory().clear();
				}
				block2.setType(Material.AIR);
			}
		}

		@Override
		public void run() {
			if (cancelled) {
				return;
			}

			if (!spawned) {
				spawnChest();
			}

			if (timeLeft > 0) {
				String timeStr;
				if (timeLeft > 10) {
					timeStr = ChatColor.YELLOW + "" + timeLeft + "s";
				} else if (timeLeft > 3) {
					timeStr = ChatColor.GOLD + "" + timeLeft + "s";
				} else {
					timeStr = ChatColor.RED + "" + timeLeft + "s";
				}
				armorStand.setCustomName(timeStr);
				timeLeft--;
				Bukkit.getScheduler().scheduleSyncDelayedTask(UhcCore.getPlugin(), this, 20L);
			} else {
				ACTIVE_TIMEBOMBS.remove(name.toLowerCase());
				if (armorStand != null && !armorStand.isDead()) {
					armorStand.remove();
				}
				block1.setType(Material.AIR);
				block2.setType(Material.AIR);
				block1.getWorld().createExplosion(block1.getLocation(), explosionPower, false);
			}
		}

		private void spawnChest() {
			spawned = true;

			block1 = loc.getBlock();
			loc.add(-1, 0, 0);
			block2 = loc.getBlock();

			block1.setType(Material.CHEST);
			block2.setType(Material.CHEST);

			Chest chest1 = (Chest) block1.getState();
			Chest chest2 = (Chest) block2.getState();

			String chestName = Lang.SCENARIO_TIMEBOMB_CHEST.replace("%player%", name);
			VersionUtils.getVersionUtils().setChestName(chest1, chestName);
			VersionUtils.getVersionUtils().setChestName(chest2, chestName);

			// Make double chest for 1.13 and up
			VersionUtils.getVersionUtils().setChestSide(chest1, false);
			VersionUtils.getVersionUtils().setChestSide(chest2, true);

			for (int i = 0; i < drops.size(); i++) {
				ItemStack drop = drops.get(i);
				if (i < chest1.getInventory().getSize()) {
					chest1.getInventory().addItem(drop);
				} else {
					chest2.getInventory().addItem(drop);
				}
			}

			Location standLoc = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY() - 0.8, loc.getBlockZ() + 0.5);
			armorStand = (ArmorStand) loc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
			armorStand.setCustomNameVisible(true);
			armorStand.setGravity(false);
			armorStand.setVisible(false);
			try {
				armorStand.setMarker(true);
			} catch (NoSuchMethodError ignored) {
			}
			String initialTime = (timeLeft > 10 ? ChatColor.YELLOW : (timeLeft > 3 ? ChatColor.GOLD : ChatColor.RED)) + "" + timeLeft + "s";
			armorStand.setCustomName(initialTime);
		}
	}

}
