package com.gmail.val59000mc.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

/**
 * A sound as the config files describe it:
 * <pre>
 * sound:
 *   enabled: true
 *   name: NOTE_PLING
 *   volume: 1.0
 *   pitch: 1.0
 * </pre>
 * Every option that plays a sound reads this same section.
 * <p>
 * Sound names change between Minecraft versions and a typo must never break a game, so a name that can't be
 * resolved logs one warning for its config path and falls back to the sound the option always used.
 * The older shapes still work: a plain {@code true}/{@code false}, and the strings {@code default}, {@code none},
 * {@code org.bukkit.Sound#NAME} and a bare name.
 */
public final class ConfigSound {

	private static final Logger LOGGER = Logger.getLogger(ConfigSound.class.getCanonicalName());

	// Minecraft clamps the pitch to this range, anything outside would just silently be cut
	private static final float MIN_PITCH = 0.5f;
	private static final float MAX_PITCH = 2.0f;
	// A volume above 1 only widens the area where it is heard, nobody needs it past this
	private static final float MAX_VOLUME = 100f;

	// One warning per config path, the sounds are read again every time the option is
	private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<String>());

	private final boolean enabled;
	private final Sound sound;
	private final float volume;
	private final float pitch;

	private ConfigSound(boolean enabled, @Nullable Sound sound, float volume, float pitch) {
		this.enabled = enabled && sound != null;
		this.sound = sound;
		this.volume = volume;
		this.pitch = pitch;
	}

	/**
	 * @param raw what the config holds at {@code path}: a section, a boolean, a string, or null if it is missing
	 * @param path config path, only used in warnings
	 * @param defaultSound the sound for {@code default}, and for names that can't be resolved
	 * @param enabledIfMissing whether the sound plays when the config says nothing about it. A section without an
	 * {@code enabled} key counts as enabled, writing one means you want it.
	 */
	public static ConfigSound parse(@Nullable Object raw, String path, UniversalSound defaultSound, boolean enabledIfMissing) {
		if (raw == null) {
			return new ConfigSound(enabledIfMissing, resolve(UniversalSound.CONFIG_VALUE_DEFAULT, path, defaultSound), 1f, 1f);
		}
		if (raw instanceof Boolean) {
			return new ConfigSound((Boolean) raw, resolve(UniversalSound.CONFIG_VALUE_DEFAULT, path, defaultSound), 1f, 1f);
		}
		if (raw instanceof String) {
			return fromName((String) raw, path, defaultSound, 1f, 1f);
		}
		if (raw instanceof ConfigurationSection) {
			return fromSection((ConfigurationSection) raw, path, defaultSound);
		}

		warnOnce(path, "Invalid sound in " + path + ", expected a section with enabled, name, volume and pitch. Using the default sound");
		return new ConfigSound(enabledIfMissing, resolve(UniversalSound.CONFIG_VALUE_DEFAULT, path, defaultSound), 1f, 1f);
	}

	private static ConfigSound fromSection(ConfigurationSection section, String path, UniversalSound defaultSound) {
		final boolean enabled = !section.isBoolean("enabled") || section.getBoolean("enabled");
		final String name = section.isString("name") ? section.getString("name") : UniversalSound.CONFIG_VALUE_DEFAULT;
		final float volume = readFloat(section, "volume", path, 1f, 0f, MAX_VOLUME);
		final float pitch = readFloat(section, "pitch", path, 1f, MIN_PITCH, MAX_PITCH);

		final ConfigSound named = fromName(name, path, defaultSound, volume, pitch);
		return new ConfigSound(enabled && named.sound != null, named.sound, volume, pitch);
	}

	private static ConfigSound fromName(String name, String path, UniversalSound defaultSound, float volume, float pitch) {
		final String trimmed = name.trim();
		if (trimmed.equalsIgnoreCase(UniversalSound.CONFIG_VALUE_NONE)) {
			return new ConfigSound(false, null, volume, pitch);
		}
		return new ConfigSound(true, resolve(trimmed, path, defaultSound), volume, pitch);
	}

	/**
	 * @return the sound for the name on this server, the default sound if there is none, or null if even the default
	 * doesn't exist here
	 */
	@Nullable
	private static Sound resolve(String name, String path, UniversalSound defaultSound) {
		String key = name;
		if (key.startsWith(UniversalSound.CONFIG_BUKKIT_SOUND_PREFIX)) {
			key = key.substring(UniversalSound.CONFIG_BUKKIT_SOUND_PREFIX.length());
		}
		key = key.trim().toUpperCase(Locale.ROOT);
		if (key.startsWith("MINECRAFT:")) {
			key = key.substring("MINECRAFT:".length());
		}
		// entity.player.levelup and ENTITY_PLAYER_LEVELUP are the same thing to us
		key = key.replace('.', '_').replace('-', '_').replace(' ', '_');

		if (!key.equals("DEFAULT") && !key.isEmpty()) {
			final Sound found = find(key);
			if (found != null) {
				return found;
			}
			warnOnce(path, "Unknown sound '" + name + "' in " + path + ", it doesn't exist on this Minecraft version. "
					+ "Using " + defaultSound.name() + " instead");
		}

		try {
			return defaultSound.getSound();
		} catch (IllegalArgumentException e) {
			// The default itself is missing on this version, so this sound stays silent
			return null;
		}
	}

	@Nullable
	private static Sound find(String key) {
		final UniversalSound universal = UniversalSound.findByName(key);
		try {
			return universal != null ? universal.getSound() : Sound.valueOf(key);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static float readFloat(ConfigurationSection section, String key, String path, float def, float min, float max) {
		if (!section.contains(key)) {
			return def;
		}
		if (!section.isDouble(key) && !section.isInt(key)) {
			warnOnce(path + "." + key, "The " + key + " of " + path + " has to be a number, found: " + section.get(key) + ". Using " + def);
			return def;
		}

		final double value = section.getDouble(key);
		if (Double.isNaN(value) || value < min || value > max) {
			final float clamped = (float) Math.max(min, Math.min(max, Double.isNaN(value) ? def : value));
			warnOnce(path + "." + key, "The " + key + " of " + path + " has to be between " + min + " and " + max + ", found: " + value
					+ ". Using " + clamped);
			return clamped;
		}
		return (float) value;
	}

	private static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			LOGGER.warning(message);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void play(Player player) {
		play(player, 1f);
	}

	/**
	 * @param pitchFactor multiplies the configured pitch, for sounds whose pitch changes while playing
	 */
	public void play(Player player, float pitchFactor) {
		if (enabled) {
			player.playSound(player.getLocation(), sound, volume, Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch * pitchFactor)));
		}
	}

	/**
	 * @return the sound to hand to code that plays it on its own, or null when it is disabled
	 */
	@Nullable
	public Sound getSound() {
		return enabled ? sound : null;
	}

	public float getVolume() {
		return volume;
	}

	public float getPitch() {
		return pitch;
	}

}
