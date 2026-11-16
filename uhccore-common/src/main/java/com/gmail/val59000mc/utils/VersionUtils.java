package com.gmail.val59000mc.utils;

import com.gmail.val59000mc.exceptions.ParseException;
import com.gmail.val59000mc.players.UhcPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.papermc.lib.PaperLib;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Skull;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class VersionUtils{

	private static VersionUtils versionUtils = null;
	private static MinecraftVersion serverVersion = null;

	public static MinecraftVersion getServerVersion(){
		if (serverVersion == null){
			String bukkitVersion = Bukkit.getBukkitVersion();
			String version = Bukkit.getVersion();
			serverVersion = parseVersion(bukkitVersion, version);
		}
		return serverVersion;
	}

	public static MinecraftVersion parseVersion(String bukkitVersion, String version){
		Pattern pattern = Pattern.compile("(?i)(?:\\(MC:\\s*|v)?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
		if (bukkitVersion != null && !bukkitVersion.isEmpty()){
			Matcher matcher = pattern.matcher(bukkitVersion);
			if (matcher.find()){
				int major = Integer.parseInt(matcher.group(1));
				int minor = Integer.parseInt(matcher.group(2));
				int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
				return new MinecraftVersion(major, minor, patch);
			}
		}
		if (version != null && !version.isEmpty()){
			Matcher matcher = pattern.matcher(version);
			if (matcher.find()){
				int major = Integer.parseInt(matcher.group(1));
				int minor = Integer.parseInt(matcher.group(2));
				int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
				return new MinecraftVersion(major, minor, patch);
			}
		}
		int fallbackMinor = PaperLib.getMinecraftVersion();
		return new MinecraftVersion(1, fallbackMinor, 0);
	}

	public static boolean isAtLeast(int legacyMinor){
		return getServerVersion().isAtLeastLegacy(legacyMinor);
	}

	public static boolean isAtLeast(int legacyMinor, int legacyPatch){
		return getServerVersion().isAtLeastLegacy(legacyMinor, legacyPatch);
	}

	public static boolean isAtLeast(int major, int minor, int patch){
		return getServerVersion().isAtLeast(major, minor, patch);
	}

	public static boolean isVersion(int legacyMinor){
		return isAtLeast(legacyMinor);
	}

	public static boolean isVersion(int legacyMinor, int legacyPatch){
		return isAtLeast(legacyMinor, legacyPatch);
	}

	public static void setVersionUtils(VersionUtils utils){
		versionUtils = utils;
	}

	public static VersionUtils getVersionUtils(){
		if (versionUtils == null){
			try {
				if (getServerVersion().getMajor() >= 26 || isAtLeast(1, 21, 0)) {
					try {
						Class<?> modernClass = Class.forName("com.gmail.val59000mc.utils.ModernVersionUtils");
						versionUtils = (VersionUtils) modernClass.getDeclaredConstructor().newInstance();
					} catch (Throwable ignored) {
					}
				}
				if (versionUtils == null) {
					String className;
					if (isAtLeast(15)){
						className = "com.gmail.val59000mc.utils.VersionUtils_1_15";
					}else if (isAtLeast(14)){
						className = "com.gmail.val59000mc.utils.VersionUtils_1_14";
					}else if (isAtLeast(13)){
						className = "com.gmail.val59000mc.utils.VersionUtils_1_13";
					}else if (isAtLeast(12)){
						className = "com.gmail.val59000mc.utils.VersionUtils_1_12";
					}else {
						className = "com.gmail.val59000mc.utils.VersionUtils_1_8";
					}
					try {
						Class<?> legacyClass = Class.forName(className);
						versionUtils = (VersionUtils) legacyClass.getDeclaredConstructor().newInstance();
					} catch (Throwable t) {
						Class<?> modernClass = Class.forName("com.gmail.val59000mc.utils.ModernVersionUtils");
						versionUtils = (VersionUtils) modernClass.getDeclaredConstructor().newInstance();
					}
				}
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to initialize VersionUtils", ex);
			}
		}
		return versionUtils;
	}

	public static class MinecraftVersion implements Comparable<MinecraftVersion>{
		private final int major;
		private final int minor;
		private final int patch;

		public MinecraftVersion(int major, int minor, int patch){
			this.major = major;
			this.minor = minor;
			this.patch = patch;
		}

		public int getMajor(){
			return major;
		}

		public int getMinor(){
			return minor;
		}

		public int getPatch(){
			return patch;
		}

		public boolean isAtLeast(int targetMajor, int targetMinor, int targetPatch){
			return compareTo(new MinecraftVersion(targetMajor, targetMinor, targetPatch)) >= 0;
		}

		public boolean isAtLeast(int targetMajor, int targetMinor){
			return isAtLeast(targetMajor, targetMinor, 0);
		}

		public boolean isAtLeastLegacy(int legacyMinor){
			return isAtLeastLegacy(legacyMinor, 0);
		}

		public boolean isAtLeastLegacy(int legacyMinor, int legacyPatch){
			if (this.major >= 26){
				return true;
			}
			if (this.major == 1){
				if (this.minor != legacyMinor){
					return this.minor > legacyMinor;
				}
				return this.patch >= legacyPatch;
			}
			return this.major > 1;
		}

		@Override
		public int compareTo(MinecraftVersion o){
			if (o == null){
				return 1;
			}
			if (this.major >= 26 && o.major == 1){
				return 1;
			}
			if (this.major == 1 && o.major >= 26){
				return -1;
			}
			if (this.major != o.major){
				return Integer.compare(this.major, o.major);
			}
			if (this.minor != o.minor){
				return Integer.compare(this.minor, o.minor);
			}
			return Integer.compare(this.patch, o.patch);
		}

		@Override
		public String toString(){
			return major + "." + minor + (patch > 0 ? "." + patch : "");
		}
	}

	public abstract ShapedRecipe createShapedRecipe(ItemStack craft, String craftKey);

	public abstract ItemStack createPlayerSkull(String name, UUID uuid);

	public abstract void setSkullOwner(Skull skull, UhcPlayer player);

	// RenderType exists in API since 1.13, internally since 1.8. RenderType.HEARTS is the default for the "health" criteria if left unspecified.
	public abstract Objective registerNewObjective(Scoreboard scoreboard, String name, String criteria, String displayName, String renderType);

	public Objective registerNewObjective(Scoreboard scoreboard, String name, String criteria, String renderType) {
		return registerNewObjective(scoreboard, name, criteria, name, renderType);
	}

	public Objective registerNewObjective(Scoreboard scoreboard, String name, String criteria) {
		return registerNewObjective(scoreboard, name, criteria, name, null);
	}

	public abstract void setPlayerMaxHealth(Player player, double maxHealth);

	public abstract void setGameRuleValue(World world, String gameRule, Object value);

	public abstract boolean hasEye(Block block);

	public abstract void setEye(Block block, boolean eye);

	public abstract void setEndPortalFrameOrientation(Block block, BlockFace blockFace);

	public abstract void setTeamNameTagVisibility(Team team, boolean value);

	public abstract void setChestName(Chest chest, String name);

	@Nullable
	public abstract JsonObject getBasePotionEffect(PotionMeta potionMeta);

	public abstract PotionMeta setBasePotionEffect(PotionMeta potionMeta, PotionData potionData);

	@Nullable
	public abstract Color getPotionColor(PotionMeta potionMeta);

	public abstract PotionMeta setPotionColor(PotionMeta potionMeta, Color color);

	/**
	 * Sets the side of a chest. (Used for double chests)
	 * @param chest The chest you want to change the side for.
	 * @param left If true it's changed to a left chest, false means it will be changed to a right chest.
	 */
	public abstract void setChestSide(Chest chest, boolean left);

	/**
	 * Removes item recipe
	 * @param item The item you want to remove the recipe for.
	 * @param recipe The recipe you want to remove. (Can be null when using 'item')
	 */
	public abstract void removeRecipe(ItemStack item, Recipe recipe);

	public abstract void handleNetherPortalEvent(PlayerPortalEvent event);

	@Nullable
	public abstract JsonObject getItemAttributes(ItemStack itemStack);

	public abstract JsonItemStack applyItemAttributes(JsonItemStack itemStack, JsonObject attributes);

	public abstract String getEnchantmentKey(Enchantment enchantment);

	@Nullable
	public abstract Enchantment getEnchantmentFromKey(String key);

	public abstract void setEntityAI(LivingEntity entity, boolean b);

	public abstract List<Material> getItemList();

	@Nullable
	public abstract JsonArray getSuspiciousStewEffects(ItemMeta meta);

	public abstract ItemMeta applySuspiciousStewEffects(ItemMeta meta, JsonArray effects) throws ParseException;

	public abstract void setItemUnbreakable(ItemMeta meta, boolean b);

	public abstract boolean getItemUnbreakable(ItemMeta meta);

	public abstract boolean isAir(Material material);

	public abstract boolean leavesIsPersistent(Block leaves);

}
