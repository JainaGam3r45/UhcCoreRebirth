package com.gmail.val59000mc.host;

import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.configuration.options.BasicOption;
import com.gmail.val59000mc.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * One editable entry of the host menu. Only wire options here that are read at game start
 * or on every use; anything consumed while the world loads would silently do nothing.
 */
public class HostSetting {

	@FunctionalInterface
	interface ClickHandler {
		/** @return true when the value actually changed */
		boolean handle(MainConfig config, ClickType click);
	}

	private static final String NUMBER_CONTROLS = ChatColor.GRAY + "Left/Right click: " + ChatColor.WHITE + "+/- %small%"
			+ "\n" + ChatColor.GRAY + "Shift click: " + ChatColor.WHITE + "+/- %big%";

	private final String key;
	private final Material icon;
	private final String name;
	private final List<String> description;
	private final Function<MainConfig, String> valueFormatter;
	private final ClickHandler clickHandler;
	private final String controls;

	private HostSetting(String key, Material icon, String name, List<String> description,
			Function<MainConfig, String> valueFormatter, ClickHandler clickHandler, String controls) {
		this.key = key;
		this.icon = icon;
		this.name = name;
		this.description = description;
		this.valueFormatter = valueFormatter;
		this.clickHandler = clickHandler;
		this.controls = controls;
	}

	public String getKey() {
		return key;
	}

	public String formatValue(MainConfig config) {
		return valueFormatter.apply(config);
	}

	public boolean handleClick(MainConfig config, ClickType click) {
		return clickHandler.handle(config, click);
	}

	public ItemStack render(MainConfig config) {
		ItemStack item = new ItemStack(icon);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(ChatColor.GOLD + name);

		List<String> lore = new ArrayList<>();
		for (String line : description) {
			lore.add(ChatColor.GRAY + line);
		}
		lore.add("");
		lore.add(ChatColor.GRAY + "Current: " + formatValue(config));
		lore.add("");
		lore.addAll(Arrays.asList(controls.split("\n")));
		meta.setLore(lore);

		// Swords and armor would otherwise print their attack/armor stats under our lore on 1.9+.
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		item.setItemMeta(meta);
		return item;
	}

	public static HostSetting toggle(String key, Material icon, String name, BasicOption<Boolean> option, String... description) {
		return new HostSetting(key, icon, name, Arrays.asList(description),
				config -> config.get(option) ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled",
				(config, click) -> {
					// A double click fires LEFT and then DOUBLE_CLICK, and isLeftClick() is true for both,
					// so only plain and shift clicks may flip it or the toggle undoes itself.
					if (delta(click, 1, 1) == 0) {
						return false;
					}
					config.set(option, !config.get(option));
					return true;
				},
				ChatColor.GRAY + "Click: " + ChatColor.WHITE + "toggle");
	}

	public static HostSetting seconds(String key, Material icon, String name, BasicOption<Integer> option,
			int step, int bigStep, int min, int max, String... description) {
		return integer(key, icon, name, option, step, bigStep, min, max,
				value -> TimeUtils.getFormattedTime(value),
				description);
	}

	public static HostSetting integer(String key, Material icon, String name, BasicOption<Integer> option,
			int step, int bigStep, int min, int max, IntFunction<String> formatter, String... description) {
		return new HostSetting(key, icon, name, Arrays.asList(description),
				config -> ChatColor.YELLOW + formatter.apply(config.get(option)),
				(config, click) -> {
					// Bail before clamping, or a number-key press would snap an out-of-range
					// config.yml value to the menu bounds without anyone asking for it.
					int delta = (int) delta(click, step, bigStep);
					if (delta == 0) {
						return false;
					}
					int current = config.get(option);
					int next = clamp(current + delta, min, max);
					if (next == current) {
						return false;
					}
					config.set(option, next);
					return true;
				},
				NUMBER_CONTROLS.replace("%small%", formatter.apply(step)).replace("%big%", formatter.apply(bigStep)));
	}

	public static HostSetting percentage(String key, Material icon, String name, BasicOption<Double> option,
			double step, double bigStep, String... description) {
		return new HostSetting(key, icon, name, Arrays.asList(description),
				config -> ChatColor.YELLOW + formatPercentage(config.get(option)),
				(config, click) -> {
					double delta = delta(click, step, bigStep);
					if (delta == 0) {
						return false;
					}
					double current = config.get(option);
					// Round to one decimal so 0.1 + 0.2 doesn't end up as 0.30000000000000004% in the lore.
					double next = Math.round(clamp(current + delta, 0, 100) * 10) / 10.0;
					if (next == current) {
						return false;
					}
					config.set(option, next);
					return true;
				},
				NUMBER_CONTROLS.replace("%small%", formatPercentage(step)).replace("%big%", formatPercentage(bigStep)));
	}

	private static double delta(ClickType click, double step, double bigStep) {
		switch (click) {
			case LEFT:
				return step;
			case RIGHT:
				return -step;
			case SHIFT_LEFT:
				return bigStep;
			case SHIFT_RIGHT:
				return -bigStep;
			default:
				return 0;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String formatPercentage(double value) {
		return (value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value)) + "%";
	}

}
