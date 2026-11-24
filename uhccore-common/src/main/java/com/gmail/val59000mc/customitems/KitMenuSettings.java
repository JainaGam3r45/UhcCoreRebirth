package com.gmail.val59000mc.customitems;

import com.gmail.val59000mc.exceptions.ParseException;
import com.gmail.val59000mc.utils.ColorUtils;
import com.gmail.val59000mc.utils.ConfigSound;
import com.gmail.val59000mc.utils.UniversalSound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code gui} section of kits.yml: how the kit menu looks and sounds.
 * <p>
 * It lives in kits.yml and not in its own guis/kits.yml so old installs keep working untouched and admins don't
 * get a second file to babysit. A bad value logs a warning and falls back to the default, a typo here must
 * never stop the kits from loading.
 */
final class KitMenuSettings {

	private static final Logger LOGGER = Logger.getLogger(KitMenuSettings.class.getCanonicalName());

	// 6 rows is what the menu always had before it became configurable.
	static final int DEFAULT_ROWS = 6;
	private static final int MIN_ROWS = 1;
	private static final int MAX_ROWS = 6;

	// A few digits at most: no inventory is bigger than 54 slots, and this keeps parseInt from overflowing.
	private static final Pattern SLOT_RANGE = Pattern.compile("(\\d{1,4})\\s*-\\s*(\\d{1,4})");
	private static final Pattern SLOT_NUMBER = Pattern.compile("\\d{1,4}");

	private final String title;
	private final int rows;
	private final ItemStack filler;
	private final Set<Integer> fillerSlots;
	private final ItemStack closeButton;
	private final int closeSlot;
	private final ConfigSound successSound;
	private final ConfigSound errorSound;
	private final ConfigSound clickSound;
	private final String statusSelected;
	private final String statusAvailable;
	private final String statusNoPermission;

	private KitMenuSettings(Builder builder) {
		title = builder.title;
		rows = builder.rows;
		filler = builder.filler;
		fillerSlots = builder.fillerSlots;
		closeButton = builder.closeButton;
		closeSlot = builder.closeSlot;
		successSound = builder.successSound;
		errorSound = builder.errorSound;
		clickSound = builder.clickSound;
		statusSelected = builder.statusSelected;
		statusAvailable = builder.statusAvailable;
		statusNoPermission = builder.statusNoPermission;
	}

	/**
	 * @param gui the {@code gui} section, null when kits.yml doesn't have one (the menu then looks like it always did)
	 */
	static KitMenuSettings parse(@Nullable ConfigurationSection gui) {
		final Builder builder = new Builder();
		if (gui == null) {
			return new KitMenuSettings(builder);
		}

		builder.title = ColorUtils.colorize(gui.getString("title"));

		if (gui.contains("rows")) {
			final int rows = gui.getInt("rows", -1);
			if (rows >= MIN_ROWS && rows <= MAX_ROWS) {
				builder.rows = rows;
			} else {
				LOGGER.warning("gui.rows in kits.yml must be a number from " + MIN_ROWS + " to " + MAX_ROWS
						+ ", found: " + gui.get("rows") + ". Using " + DEFAULT_ROWS + " rows");
			}
		}
		final int size = builder.rows * 9;

		parseFiller(gui.getConfigurationSection("filler"), size, builder);
		parseCloseButton(gui.getConfigurationSection("close_button"), size, builder);
		parseSounds(gui.getConfigurationSection("sounds"), builder);
		parseStatus(gui.getConfigurationSection("status"), builder);

		return new KitMenuSettings(builder);
	}

	private static void parseFiller(@Nullable ConfigurationSection section, int size, Builder builder) {
		if (section == null || !section.getBoolean("enabled", true)) {
			return;
		}

		try {
			builder.filler = KitItemParser.parseIcon(section, "GRAY_STAINED_GLASS_PANE", "&r");
		} catch (ParseException e) {
			LOGGER.warning("Ignoring gui.filler in kits.yml: " + e.getMessage());
			return;
		}

		// No slots listed means every slot a kit or button doesn't use.
		if (section.get("slots") != null) {
			builder.fillerSlots = parseSlots(section.get("slots"), size, "gui.filler.slots");
		}
	}

	private static void parseCloseButton(@Nullable ConfigurationSection section, int size, Builder builder) {
		if (section == null || !section.getBoolean("enabled", true)) {
			return;
		}

		try {
			builder.closeButton = KitItemParser.parseIcon(section, "BARRIER", "&cClose");
		} catch (ParseException e) {
			LOGGER.warning("Ignoring gui.close_button in kits.yml: " + e.getMessage());
			return;
		}

		// Middle of the bottom row by default.
		builder.closeSlot = size - 5;
		if (section.contains("slot")) {
			final int slot = section.getInt("slot", -1);
			if (slot >= 0 && slot < size) {
				builder.closeSlot = slot;
			} else {
				LOGGER.warning("gui.close_button.slot in kits.yml must be from 0 to " + (size - 1) + ", found: "
						+ section.get("slot") + ". Using slot " + builder.closeSlot);
			}
		}
	}

	private static void parseSounds(@Nullable ConfigurationSection section, Builder builder) {
		if (section == null) {
			return;
		}
		// A sound the file doesn't mention stays silent, so a kits.yml from before the menu was configurable sounds as it did.
		builder.successSound = ConfigSound.parse(section.get("success"), "gui.sounds.success", UniversalSound.NOTE_PLING, false);
		builder.errorSound = ConfigSound.parse(section.get("error"), "gui.sounds.error", UniversalSound.NOTE_BASS, false);
		builder.clickSound = ConfigSound.parse(section.get("click"), "gui.sounds.click", UniversalSound.CLICK, false);
	}

	private static void parseStatus(@Nullable ConfigurationSection section, Builder builder) {
		if (section == null) {
			return;
		}
		builder.statusSelected = ColorUtils.colorize(section.getString("selected", builder.statusSelected));
		builder.statusAvailable = ColorUtils.colorize(section.getString("available", builder.statusAvailable));
		builder.statusNoPermission = ColorUtils.colorize(section.getString("no-permission", builder.statusNoPermission));
	}

	/**
	 * Reads a list of slots where each entry is a number or a range, like {@code [0-8, 45, 46-53]}.
	 * YAML hands us integers for {@code 45} and strings for {@code 0-8}, so both are handled.
	 */
	private static Set<Integer> parseSlots(Object raw, int size, String path) {
		final Set<Integer> slots = new TreeSet<>();
		final Collection<?> entries = raw instanceof Collection ? (Collection<?>) raw : Collections.singletonList(raw);
		for (Object entry : entries) {
			for (String token : String.valueOf(entry).split("[,;]")) {
				final String slot = token.trim();
				if (slot.isEmpty()) {
					continue;
				}

				final Matcher range = SLOT_RANGE.matcher(slot);
				if (range.matches()) {
					final int first = Integer.parseInt(range.group(1));
					final int last = Integer.parseInt(range.group(2));
					for (int i = Math.min(first, last); i <= Math.max(first, last); i++) {
						addSlot(slots, i, size, path);
					}
				} else if (SLOT_NUMBER.matcher(slot).matches()) {
					addSlot(slots, Integer.parseInt(slot), size, path);
				} else {
					LOGGER.warning("Ignoring '" + slot + "' in " + path + " of kits.yml, use numbers or ranges like 0-8");
				}
			}
		}
		return Collections.unmodifiableSet(slots);
	}

	private static void addSlot(Set<Integer> slots, int slot, int size, String path) {
		if (slot < size) {
			slots.add(slot);
		} else {
			LOGGER.warning("Ignoring slot " + slot + " in " + path + " of kits.yml, the menu only has " + size + " slots (0-" + (size - 1) + ")");
		}
	}

	/**
	 * @return the configured title with colors, or null to use the language file's kit inventory title
	 */
	@Nullable
	String getTitle() {
		return title;
	}

	int getSize() {
		return rows * 9;
	}

	@Nullable
	ItemStack getFiller() {
		return filler;
	}

	/**
	 * @return true if the filler goes on this slot (when it has no explicit slot list it goes on every free one)
	 */
	boolean isFillerSlot(int slot) {
		return filler != null && (fillerSlots == null || fillerSlots.contains(slot));
	}

	/**
	 * @return true if the filler was given an explicit slot list. Those slots are kept free of automatically placed kits.
	 */
	boolean isReservedForFiller(int slot) {
		return filler != null && fillerSlots != null && fillerSlots.contains(slot);
	}

	@Nullable
	ItemStack getCloseButton() {
		return closeButton;
	}

	int getCloseSlot() {
		return closeSlot;
	}

	ConfigSound getSuccessSound() {
		return successSound;
	}

	ConfigSound getErrorSound() {
		return errorSound;
	}

	ConfigSound getClickSound() {
		return clickSound;
	}

	String getStatusSelected() {
		return statusSelected;
	}

	String getStatusAvailable() {
		return statusAvailable;
	}

	String getStatusNoPermission() {
		return statusNoPermission;
	}

	/**
	 * Holds the defaults, which are also what a kits.yml without a gui section gets.
	 */
	private static class Builder {
		private String title = null;
		private int rows = DEFAULT_ROWS;
		private ItemStack filler = null;
		private Set<Integer> fillerSlots = null;
		private ItemStack closeButton = null;
		private int closeSlot = -1;
		private ConfigSound successSound = ConfigSound.parse(null, "gui.sounds.success", UniversalSound.NOTE_PLING, false);
		private ConfigSound errorSound = ConfigSound.parse(null, "gui.sounds.error", UniversalSound.NOTE_BASS, false);
		private ConfigSound clickSound = ConfigSound.parse(null, "gui.sounds.click", UniversalSound.CLICK, false);
		private String statusSelected = ColorUtils.colorize("&aSelected");
		private String statusAvailable = ColorUtils.colorize("&eClick to select");
		private String statusNoPermission = ColorUtils.colorize("&cPermission required");
	}

}
