package com.gmail.val59000mc.players;

import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.utils.LocationUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathSnapshot {

	private static final Map<UUID, DeathSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

	private final UUID playerUuid;
	private final String playerName;
	private final ItemStack[] inventoryContents;
	private final ItemStack[] armorContents;
	private final ItemStack offHandItem;
	private final int xpLevel;
	private final float xpProgress;
	private final int totalExperience;
	private final Location deathLocation;
	private final UhcTeam team;
	private final long timestamp;

	public DeathSnapshot(UUID playerUuid, String playerName, ItemStack[] inventoryContents, ItemStack[] armorContents,
						 @Nullable ItemStack offHandItem, int xpLevel, float xpProgress, int totalExperience,
						 Location deathLocation, @Nullable UhcTeam team) {
		this.playerUuid = playerUuid;
		this.playerName = playerName;
		this.inventoryContents = cloneItemArray(inventoryContents);
		this.armorContents = cloneItemArray(armorContents);
		this.offHandItem = offHandItem != null ? offHandItem.clone() : null;
		this.xpLevel = xpLevel;
		this.xpProgress = xpProgress;
		this.totalExperience = totalExperience;
		this.deathLocation = deathLocation != null ? deathLocation.clone() : null;
		this.team = team;
		this.timestamp = System.currentTimeMillis();
	}

	public static DeathSnapshot record(Player player, UhcPlayer uhcPlayer) {
		PlayerInventory inv = player.getInventory();
		ItemStack offHand = getOffHandSafely(inv);

		DeathSnapshot snapshot = new DeathSnapshot(
				player.getUniqueId(),
				player.getName(),
				inv.getContents(),
				inv.getArmorContents(),
				offHand,
				player.getLevel(),
				player.getExp(),
				player.getTotalExperience(),
				player.getLocation(),
				uhcPlayer.getTeam()
		);
		SNAPSHOTS.put(player.getUniqueId(), snapshot);
		return snapshot;
	}

	public static Optional<DeathSnapshot> get(UUID uuid) {
		return Optional.ofNullable(SNAPSHOTS.get(uuid));
	}

	public static Optional<DeathSnapshot> getByName(String name) {
		return SNAPSHOTS.values().stream()
				.filter(s -> s.getPlayerName().equalsIgnoreCase(name))
				.findFirst();
	}

	public static void remove(UUID uuid) {
		SNAPSHOTS.remove(uuid);
	}

	public static void clear() {
		SNAPSHOTS.clear();
	}

	public Location resolveSafeLocation(Player player, @Nullable UhcPlayer uhcPlayer) {
		if (deathLocation != null && deathLocation.getWorld() != null && LocationUtils.isWithinBorder(deathLocation)) {
			return deathLocation;
		}

		UhcTeam targetTeam = uhcPlayer != null ? uhcPlayer.getTeam() : this.team;
		if (targetTeam != null) {
			for (UhcPlayer member : targetTeam.getMembers()) {
				if ((uhcPlayer == null || !member.equals(uhcPlayer)) && member.getState() == PlayerState.PLAYING) {
					try {
						Player teammate = member.getPlayer();
						Location teammateLoc = teammate.getLocation();
						if (LocationUtils.isWithinBorder(teammateLoc)) {
							return teammateLoc;
						}
					} catch (UhcPlayerNotOnlineException ignored) {
					}
				}
			}
		}

		GameManager gm = GameManager.getGameManager();
		World world = deathLocation != null && deathLocation.getWorld() != null
				? deathLocation.getWorld()
				: gm.getMapLoader().getUhcWorld(World.Environment.NORMAL);

		double borderHalfSize = world.getWorldBorder().getSize() / 2.0;
		int safeRadius = Math.max(10, (int) borderHalfSize - 15);
		return LocationUtils.getRandomSpawnLocation(world, safeRadius);
	}

	public void restore(Player player) {
		restore(player, null);
	}

	public void restore(Player player, @Nullable UhcPlayer uhcPlayer) {
		PlayerInventory inv = player.getInventory();
		if (inventoryContents != null) {
			inv.setContents(cloneItemArray(inventoryContents));
		}
		if (armorContents != null) {
			inv.setArmorContents(cloneItemArray(armorContents));
		}
		if (offHandItem != null) {
			setOffHandSafely(inv, offHandItem.clone());
		}
		player.setLevel(xpLevel);
		player.setExp(xpProgress);
		player.setTotalExperience(totalExperience);

		Location safeLoc = resolveSafeLocation(player, uhcPlayer);
		if (safeLoc != null && safeLoc.getWorld() != null) {
			player.teleport(safeLoc);
		}
	}

	public UUID getPlayerUuid() {
		return playerUuid;
	}

	public String getPlayerName() {
		return playerName;
	}

	public ItemStack[] getInventoryContents() {
		return cloneItemArray(inventoryContents);
	}

	public ItemStack[] getArmorContents() {
		return cloneItemArray(armorContents);
	}

	@Nullable
	public ItemStack getOffHandItem() {
		return offHandItem != null ? offHandItem.clone() : null;
	}

	public int getXpLevel() {
		return xpLevel;
	}

	public float getXpProgress() {
		return xpProgress;
	}

	public int getTotalExperience() {
		return totalExperience;
	}

	public Location getDeathLocation() {
		return deathLocation != null ? deathLocation.clone() : null;
	}

	@Nullable
	public UhcTeam getTeam() {
		return team;
	}

	public long getTimestamp() {
		return timestamp;
	}

	private static ItemStack[] cloneItemArray(ItemStack[] items) {
		if (items == null) {
			return new ItemStack[0];
		}
		ItemStack[] clone = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			clone[i] = items[i] != null ? items[i].clone() : null;
		}
		return clone;
	}

	private static ItemStack getOffHandSafely(PlayerInventory inv) {
		try {
			Method m = inv.getClass().getMethod("getItemInOffHand");
			Object item = m.invoke(inv);
			return item instanceof ItemStack ? (ItemStack) item : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static void setOffHandSafely(PlayerInventory inv, ItemStack item) {
		try {
			Method m = inv.getClass().getMethod("setItemInOffHand", ItemStack.class);
			m.invoke(inv, item);
		} catch (Throwable ignored) {
		}
	}
}
