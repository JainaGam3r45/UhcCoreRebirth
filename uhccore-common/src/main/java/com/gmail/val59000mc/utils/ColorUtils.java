package com.gmail.val59000mc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

import javax.annotation.Nullable;

/**
 * Color code translation for config text: legacy {@code &} codes plus {@code &#RRGGBB} hex.
 */
public final class ColorUtils {

	private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

	// Vanilla chat colors 0 to f, same order in both arrays.
	private static final char[] LEGACY_CODES = {
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};
	private static final int[] LEGACY_RGB = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
	};

	private ColorUtils() {
	}

	/**
	 * Translates {@code &} color codes and {@code &#RRGGBB} hex colors.
	 * Before 1.16 there is no hex in chat, so each hex color becomes the closest of the 16 classic colors.
	 *
	 * @param text the raw config text, may be null
	 * @return the colored text, or null if the input was null
	 */
	@Nullable
	public static String colorize(@Nullable String text) {
		if (text == null) {
			return null;
		}

		// Hex goes first and is written straight as section signs: translateAlternateColorCodes
		// knows nothing about '#' (and on 1.8 not even about 'x').
		final boolean hexSupported = VersionUtils.isAtLeast(16);
		final Matcher matcher = HEX_PATTERN.matcher(text);
		final StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			final String hex = matcher.group(1);
			matcher.appendReplacement(result, Matcher.quoteReplacement(hexSupported ? toSpigotHex(hex) : toNearestLegacy(hex)));
		}
		matcher.appendTail(result);

		return ChatColor.translateAlternateColorCodes('&', result.toString());
	}

	public static List<String> colorize(List<String> lines) {
		final List<String> colored = new ArrayList<>(lines.size());
		for (String line : lines) {
			colored.add(colorize(line));
		}
		return colored;
	}

	// The 1.16+ client reads the legacy format "§x§R§R§G§G§B§B" as a hex color.
	private static String toSpigotHex(String hex) {
		final StringBuilder builder = new StringBuilder("§x");
		for (char c : hex.toCharArray()) {
			builder.append('§').append(Character.toLowerCase(c));
		}
		return builder.toString();
	}

	private static String toNearestLegacy(String hex) {
		final int rgb = Integer.parseInt(hex, 16);
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int i = 0; i < LEGACY_RGB.length; i++) {
			final long distance = distanceSquared(rgb, LEGACY_RGB[i]);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return "§" + LEGACY_CODES[best];
	}

	private static long distanceSquared(int a, int b) {
		final long red = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		final long green = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		final long blue = (a & 0xFF) - (b & 0xFF);
		return red * red + green * green + blue * blue;
	}

}
