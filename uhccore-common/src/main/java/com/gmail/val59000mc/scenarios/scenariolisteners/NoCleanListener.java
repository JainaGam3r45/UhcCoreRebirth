package com.gmail.val59000mc.scenarios.scenariolisteners;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.bridge.VersionBridgeProvider;
import com.gmail.val59000mc.languages.Lang;
import com.gmail.val59000mc.scenarios.Option;
import com.gmail.val59000mc.scenarios.ScenarioListener;
import com.gmail.val59000mc.utils.TimeUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

public class NoCleanListener extends ScenarioListener {

	private final Map<UUID, Long> pvpCooldown;
	private BukkitTask countdownTask;

	@Option
	private long duration = 20;

	public NoCleanListener() {
		pvpCooldown = new HashMap<>();
	}

	// ScenarioManager builds a fresh listener on every enable, so the timer has to live and die
	// with onEnable/onDisable or each toggle leaks another repeating task.
	@Override
	public void onEnable() {
		startCountdownTask();
	}

	@Override
	public void onDisable() {
		if (countdownTask != null) {
			countdownTask.cancel();
			countdownTask = null;
		}
		pvpCooldown.clear();
	}

	private void startCountdownTask() {
		countdownTask = Bukkit.getScheduler().runTaskTimer(UhcCore.getPlugin(), () -> {
			long now = System.currentTimeMillis();
			pvpCooldown.entrySet().removeIf(entry -> {
				UUID uuid = entry.getKey();
				long expiresAt = entry.getValue();
				Player player = Bukkit.getPlayer(uuid);

				if (now >= expiresAt) {
					if (player != null && player.isOnline()) {
						player.sendMessage(Lang.SCENARIO_NOCLEAN_VULNERABLE);
						VersionBridgeProvider.getBridge().sendActionBar(player, ChatColor.RED + "NoClean protection expired!");
					}
					return true;
				}

				if (player != null && player.isOnline()) {
					long remainingSeconds = (expiresAt - now + 999) / 1000;
					String message = ChatColor.GOLD + "NoClean: " + ChatColor.YELLOW + remainingSeconds + "s remaining";
					VersionBridgeProvider.getBridge().sendActionBar(player, message);
				}
				return false;
			});
		}, 20L, 20L);
	}

	private boolean isProtected(UUID uuid) {
		Long expires = pvpCooldown.get(uuid);
		if (expires == null) {
			return false;
		}
		if (System.currentTimeMillis() >= expires) {
			pvpCooldown.remove(uuid);
			return false;
		}
		return true;
	}

	private void breakProtection(Player player, String reason) {
		if (pvpCooldown.remove(player.getUniqueId()) != null) {
			player.sendMessage(Lang.SCENARIO_NOCLEAN_VULNERABLE);
			if (reason != null && !reason.isEmpty()) {
				player.sendMessage(ChatColor.RED + reason);
			}
			VersionBridgeProvider.getBridge().sendActionBar(player, ChatColor.RED + "NoClean protection lost!");
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent e) {
		Player victim = e.getEntity();
		Player killer = victim.getKiller();
		if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
			pvpCooldown.put(killer.getUniqueId(), System.currentTimeMillis() + duration * TimeUtils.SECOND);
			String message = Lang.SCENARIO_NOCLEAN_INVULNERABLE.replace("%time%", String.valueOf(duration));
			killer.sendMessage(message);
			VersionBridgeProvider.getBridge().sendActionBar(killer, ChatColor.GREEN + "NoClean protection active (" + duration + "s)");
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
		Player damager = resolvePlayerAttack(e);
		if (damager == null) {
			return;
		}

		if (isProtected(e.getEntity().getUniqueId())) {
			e.setCancelled(true);
			damager.sendMessage(Lang.SCENARIO_NOCLEAN_ERROR);
		}
	}

	// MONITOR so hits that friendly fire or PvP-off cancel later don't cost the attacker their protection.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDamageByEntityMonitor(EntityDamageByEntityEvent e) {
		Player damager = resolvePlayerAttack(e);
		if (damager != null && isProtected(damager.getUniqueId())) {
			breakProtection(damager, "Protection broken because you attacked another player!");
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPlayerDamage(EntityDamageEvent e) {
		if (e.getEntityType() != EntityType.PLAYER) {
			return;
		}

		Player player = (Player) e.getEntity();
		if (!isProtected(player.getUniqueId())) {
			return;
		}

		EntityDamageEvent.DamageCause cause = e.getCause();
		if (cause == EntityDamageEvent.DamageCause.FIRE
				|| cause == EntityDamageEvent.DamageCause.LAVA
				|| cause == EntityDamageEvent.DamageCause.FIRE_TICK
				|| cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
				|| cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileLaunch(ProjectileLaunchEvent e) {
		Projectile projectile = e.getEntity();
		ProjectileSource shooter = projectile.getShooter();
		if (!(shooter instanceof Player)) {
			return;
		}

		Player player = (Player) shooter;
		if (!isProtected(player.getUniqueId())) {
			return;
		}

		if (projectile instanceof Arrow || projectile instanceof ThrownPotion || isOffensiveProjectile(projectile)) {
			breakProtection(player, "Protection broken because you fired an offensive projectile!");
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		Player player = e.getPlayer();
		if (!isProtected(player.getUniqueId())) {
			return;
		}

		Block block = e.getBlockPlaced();
		Material material = block.getType();
		String materialName = material.name();

		boolean isHazard = materialName.contains("FIRE")
				|| materialName.contains("TNT");

		if (isHazard && isNearOtherPlayers(player, block.getLocation(), 15.0)) {
			breakProtection(player, "Protection broken because you placed a hazard near another player!");
		}
	}

	// Lava buckets never fire BlockPlaceEvent, they come through here.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBucketEmpty(PlayerBucketEmptyEvent e) {
		Player player = e.getPlayer();
		if (e.getBucket() != Material.LAVA_BUCKET || !isProtected(player.getUniqueId())) {
			return;
		}

		// getBlock() doesn't exist on 1.8.8, so work out the target from the clicked face.
		Location target = e.getBlockClicked().getRelative(e.getBlockFace()).getLocation();
		if (isNearOtherPlayers(player, target, 15.0)) {
			breakProtection(player, "Protection broken because you placed a hazard near another player!");
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e) {
		pvpCooldown.remove(e.getPlayer().getUniqueId());
	}

	private Player resolvePlayerAttack(EntityDamageByEntityEvent e) {
		if (!(e.getEntity() instanceof Player)) {
			return null;
		}

		Player damager = resolvePlayerDamager(e.getDamager());
		if (damager == null || damager.getUniqueId().equals(e.getEntity().getUniqueId())) {
			return null;
		}
		return damager;
	}

	private Player resolvePlayerDamager(Entity damagerEntity) {
		if (damagerEntity instanceof Player) {
			return (Player) damagerEntity;
		}
		if (damagerEntity instanceof Projectile) {
			Projectile projectile = (Projectile) damagerEntity;
			if (projectile.getShooter() instanceof Player) {
				return (Player) projectile.getShooter();
			}
		}
		return null;
	}

	private boolean isOffensiveProjectile(Projectile projectile) {
		String typeName = projectile.getType().name();
		return typeName.contains("TRIDENT")
				|| typeName.contains("FIREBALL")
				|| typeName.contains("CHARGE")
				|| typeName.contains("ARROW");
	}

	private boolean isNearOtherPlayers(Player source, Location loc, double radius) {
		double radiusSq = radius * radius;
		for (Player other : loc.getWorld().getPlayers()) {
			if (!other.getUniqueId().equals(source.getUniqueId())) {
				if (other.getLocation().distanceSquared(loc) <= radiusSq) {
					return true;
				}
			}
		}
		return false;
	}
}
