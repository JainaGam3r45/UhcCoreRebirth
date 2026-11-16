package com.gmail.val59000mc.scenarios.scenariolisteners;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.scenarios.Option;
import com.gmail.val59000mc.scenarios.ScenarioListener;
import com.gmail.val59000mc.utils.FloodFill;
import com.gmail.val59000mc.utils.UniversalMaterial;

import io.papermc.lib.PaperLib;

public class TimberListener extends ScenarioListener {

	@Option(key = "calculate-axe-damage")
	private boolean calculateAxeDamage = true;

	@Option(key = "require-axe")
	private boolean requireAxe = true;

	@Option(key = "drop-planks")
	private boolean dropPlanks = false;

	@Option(key = "log-break-limit")
	private int logBreakLimit = 1000;

	// High priority to allow custom block drop scenarios to override Timber
	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockBreak(BlockBreakEvent e) {
		if (e.isCancelled()) {
			return;
		}

		final ItemStack tool = e.getPlayer().getItemInHand();
		if (requireAxe && !UniversalMaterial.isAxe(tool.getType())) {
			return;
		}

		final Block block = e.getBlock();
		if (isWoodOrLog(block.getType())) {
			final Set<Vector> treeLogs = FloodFill.floodFind26(block, this::isTreeBlock, logBreakLimit);

			final World world = block.getWorld();
			for (Vector v : treeLogs) {
				breakLog(world.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ()));
			}

			if (calculateAxeDamage) {
				UhcPlayer.damageMiningTool(e.getPlayer(), treeLogs.size());
			}
		}
	}

	private boolean isWoodOrLog(Material material) {
		return UniversalMaterial.isLog(material) || UniversalMaterial.isWood(material);
	}

	private boolean isTreeBlock(Block block) {
		Material type = block.getType();
		if (isWoodOrLog(type)) {
			return true;
		}
		// In Pale Garden trees, creaking hearts generate inside the trunk
		return type == UniversalMaterial.CREAKING_HEART.getType();
	}

	private void breakLog(Block log) {
		Material type = log.getType();

		// Always break naturally to avoid orphan Creaking entities and never drop wood planks from hearts
		if (type == UniversalMaterial.CREAKING_HEART.getType()) {
			log.breakNaturally();
			return;
		}

		if (dropPlanks) {
			ItemStack planks = getPlanksForLog(log);
			log.setType(Material.AIR);
			final Location itemDropLocation = log.getLocation().add(0.5, 0.5, 0.5);
			log.getWorld().dropItem(itemDropLocation, planks);
		} else {
			log.breakNaturally();
		}
	}

	private ItemStack getPlanksForLog(Block log) {
		if (PaperLib.getMinecraftVersion() >= 13) {
			String name = log.getType().name();
			String woodType;
			if (name.contains("DARK_OAK")) woodType = "DARK_OAK";
			else if (name.contains("PALE_OAK")) woodType = "PALE_OAK";
			else if (name.contains("OAK")) woodType = "OAK";
			else if (name.contains("SPRUCE")) woodType = "SPRUCE";
			else if (name.contains("BIRCH")) woodType = "BIRCH";
			else if (name.contains("JUNGLE")) woodType = "JUNGLE";
			else if (name.contains("ACACIA")) woodType = "ACACIA";
			else if (name.contains("MANGROVE")) woodType = "MANGROVE";
			else if (name.contains("CHERRY")) woodType = "CHERRY";
			else if (name.contains("CRIMSON")) woodType = "CRIMSON";
			else if (name.contains("WARPED")) woodType = "WARPED";
			else if (name.contains("BAMBOO")) woodType = "BAMBOO";
			else woodType = "OAK";

			Material planksMat = Material.matchMaterial(woodType + "_PLANKS");
			if (planksMat != null) {
				return new ItemStack(planksMat, 4);
			}
		} else {
			if (UniversalMaterial.SPRUCE_LOG.matches(log)) return UniversalMaterial.SPRUCE_PLANKS.getStack(4);
			if (UniversalMaterial.BIRCH_LOG.matches(log)) return UniversalMaterial.BIRCH_PLANKS.getStack(4);
			if (UniversalMaterial.JUNGLE_LOG.matches(log)) return UniversalMaterial.JUNGLE_PLANKS.getStack(4);
			if (UniversalMaterial.ACACIA_LOG.matches(log)) return UniversalMaterial.ACACIA_PLANKS.getStack(4);
			if (UniversalMaterial.DARK_OAK_LOG.matches(log)) return UniversalMaterial.DARK_OAK_PLANKS.getStack(4);
			if (UniversalMaterial.OAK_LOG.matches(log)) return UniversalMaterial.OAK_PLANKS.getStack(4);
		}
		return UniversalMaterial.OAK_PLANKS.getStack(4);
	}

}
