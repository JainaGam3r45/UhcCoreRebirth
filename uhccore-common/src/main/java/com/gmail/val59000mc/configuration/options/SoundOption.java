package com.gmail.val59000mc.configuration.options;

import org.bukkit.configuration.file.YamlConfiguration;

import com.gmail.val59000mc.utils.ConfigSound;
import com.gmail.val59000mc.utils.UniversalSound;

/**
 * An option that holds a sound, see {@link ConfigSound} for the shapes it accepts.
 */
public class SoundOption implements Option<ConfigSound> {

	private final String path;
	private final UniversalSound defaultSound;

	/**
	 * @param path config path of the sound section
	 * @param defaultSound the sound played when the config says {@code default} or holds an unknown name
	 */
	public SoundOption(String path, UniversalSound defaultSound) {
		this.path = path;
		this.defaultSound = defaultSound;
	}

	@Override
	public ConfigSound getValue(YamlConfiguration config) {
		// get(path) with no default never writes anything, and an old plain 'sound: true' is returned as it is.
		// Asking for a sub key with a default instead would turn that boolean into a section in memory.
		final Object raw = config.get(path);
		if (raw == null) {
			// Registers the defaults so the config notices the section is missing and adds all of it
			config.get(path + ".enabled", true);
			config.get(path + ".name", defaultSound.name());
			config.get(path + ".volume", 1.0);
			config.get(path + ".pitch", 1.0);
		}
		return ConfigSound.parse(raw, path, defaultSound, true);
	}

}
