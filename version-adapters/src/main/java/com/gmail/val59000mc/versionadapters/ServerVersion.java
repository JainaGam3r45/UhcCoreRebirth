package com.gmail.val59000mc.versionadapters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;

/**
 * Minecraft version of the running server, parsed once.
 * <p>
 * PaperLib's parser only accepts a single digit before the first dot, so on
 * year-versioned releases (26.1 and up) it reports version 0 and every check fails.
 * This one keeps PaperLib's {@code isVersion} semantics but understands any major.
 */
public final class ServerVersion {

	// Bukkit version always leads with the game version: 1.8.8-R0.1-SNAPSHOT, 26.1.2.build.5-alpha...
	private static final Pattern BUKKIT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
	private static final Pattern MC_VERSION = Pattern.compile("\\(MC: (\\d+)\\.(\\d+)(?:\\.(\\d+))?");

	private static final ServerVersion UNKNOWN = new ServerVersion(0, 0, 0);

	private final int major;
	private final int minor;
	private final int patch;

	private ServerVersion(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	// Holder idiom so parse() can be used without a running server
	private static final class Current {
		private static final ServerVersion VERSION = parse(Bukkit.getBukkitVersion(), Bukkit.getVersion());
	}

	public static ServerVersion current() {
		return Current.VERSION;
	}

	/**
	 * Same contract as PaperLib: {@code true} if the server runs 1.{@code minor} or newer.
	 * Year-versioned releases (26.x and up) count as newer than any 1.x.
	 */
	public static boolean isVersion(int minor) {
		return current().isAtLeast(minor, 0);
	}

	/**
	 * Same contract as PaperLib: {@code true} if the server runs 1.{@code minor}.{@code patch} or newer.
	 */
	public static boolean isVersion(int minor, int patch) {
		return current().isAtLeast(minor, patch);
	}

	/**
	 * @param bukkitVersion {@code Bukkit.getBukkitVersion()}, checked first
	 * @param version {@code Bukkit.getVersion()}, used if the first one doesn't parse
	 * @return the parsed version, or an unknown version if neither string makes sense
	 */
	public static ServerVersion parse(String bukkitVersion, String version) {
		ServerVersion parsed = match(BUKKIT_VERSION, bukkitVersion);
		if (parsed == null) {
			parsed = match(MC_VERSION, version);
		}
		return parsed == null ? UNKNOWN : parsed;
	}

	private static ServerVersion match(Pattern pattern, String input) {
		if (input == null) {
			return null;
		}
		final Matcher matcher = pattern.matcher(input.trim());
		if (!matcher.find()) {
			return null;
		}
		try {
			final int major = Integer.parseInt(matcher.group(1));
			final int minor = Integer.parseInt(matcher.group(2));
			final int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
			return major == 0 ? null : new ServerVersion(major, minor, patch);
		} catch (NumberFormatException e) {
			// Absurdly long digit runs, not a version we can do anything with
			return null;
		}
	}

	/**
	 * @return {@code true} if this version is 1.{@code minor}.{@code patch} or newer
	 */
	public boolean isAtLeast(int minor, int patch) {
		if (major != 1) {
			return major > 1;
		}
		if (this.minor != minor) {
			return this.minor > minor;
		}
		return this.patch >= patch;
	}

	public boolean isKnown() {
		return major > 0;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	public int getPatch() {
		return patch;
	}

	@Override
	public String toString() {
		return isKnown() ? major + "." + minor + "." + patch : "unknown";
	}

}
