package com.gmail.val59000mc.utils;

import org.bukkit.Sound;

public enum UniversalSound {
	CLICK("CLICK", "UI_BUTTON_CLICK", "UI_BUTTON_CLICK"),
	ENDERDRAGON_GROWL("ENDERDRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENTITY_ENDER_DRAGON_GROWL"),
	WITHER_SPAWN("WITHER_SPAWN", "ENTITY_WITHER_SPAWN", "ENTITY_WITHER_SPAWN"),
	FIREWORK_LAUNCH("FIREWORK_LAUNCH", "ENTITY_FIREWORK_LAUNCH", "ENTITY_FIREWORK_ROCKET_LAUNCH"),
	BLOCK_GRASS_BREAK("DIG_GRASS", "BLOCK_GRASS_BREAK", "BLOCK_GRASS_BREAK"),
	NOTE_PLING("NOTE_PLING", "BLOCK_NOTE_PLING", "BLOCK_NOTE_BLOCK_PLING"),
	NOTE_BASS("NOTE_BASS", "BLOCK_NOTE_BASS", "BLOCK_NOTE_BLOCK_BASS"),
	LEVEL_UP("LEVEL_UP", "ENTITY_PLAYER_LEVELUP", "ENTITY_PLAYER_LEVELUP"),
	VILLAGER_NO("VILLAGER_NO", "ENTITY_VILLAGER_NO", "ENTITY_VILLAGER_NO");

	private final String name8, name9, name13;
	private Sound sound;

	UniversalSound(String name8, String name9, String name13){
		this.name8 = name8;
		this.name9 = name9;
		this.name13 = name13;

		sound = null;
	}

	public Sound getSound(){
		if (sound == null){
			if (!VersionUtils.isAtLeast(9)){
				sound = Sound.valueOf(name8);
			}else if (!VersionUtils.isAtLeast(13)){
				sound = Sound.valueOf(name9);
			}else {
				sound = Sound.valueOf(name13);
			}
		}

		return sound;
	}

	/**
	 * Finds a sound by any name it has had across versions, so LEVEL_UP and ENTITY_PLAYER_LEVELUP resolve
	 * to the same sound.
	 *
	 * @param name upper case sound name
	 * @return the matching sound, or null if it isn't one of ours
	 */
	public static UniversalSound findByName(String name) {
		for (UniversalSound universal : values()) {
			if (universal.name().equals(name) || universal.name8.equals(name)
					|| universal.name9.equals(name) || universal.name13.equals(name)) {
				return universal;
			}
		}
		return null;
	}

	public static final String CONFIG_VALUE_NONE = "none";
	public static final String CONFIG_VALUE_DEFAULT = "default";
	public static final String CONFIG_BUKKIT_SOUND_PREFIX = "org.bukkit.Sound#";

}
