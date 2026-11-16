package com.gmail.val59000mc.scenarios.scenariolisteners;

import com.gmail.val59000mc.events.PlayerStartsPlayingEvent;
import com.gmail.val59000mc.exceptions.UhcPlayerNotOnlineException;
import com.gmail.val59000mc.players.UhcPlayer;
import com.gmail.val59000mc.scenarios.ScenarioListener;
import com.gmail.val59000mc.utils.RandomUtils;

import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SuperHeroesListener extends ScenarioListener{

	private static final Logger LOGGER = Logger.getLogger(SuperHeroesListener.class.getCanonicalName());

	@EventHandler
	public void onGameStart(PlayerStartsPlayingEvent e){
		addHeroesEffect(e.getUhcPlayer(), RandomUtils.randomInteger(0, 5));
	}

	private void addHeroesEffect(UhcPlayer uhcPlayer, int effect){

		Player player;

		try {
			player = uhcPlayer.getPlayer();
		} catch (UhcPlayerNotOnlineException ignored) {
			// No effect for offline player
			return;
		}

		PotionEffectType type;
		int amplifier;

		switch (effect) {
			case 0:
				type = getPotionEffectType("SPEED", "speed");
				amplifier = 0;
				break;
			case 1:
				// Paper 1.20.5+ standardized potion names, while 1.8-1.20.4 uses legacy Bukkit names
				type = getPotionEffectType("STRENGTH", "INCREASE_DAMAGE", "strength");
				amplifier = 0;
				break;
			case 2:
				type = getPotionEffectType("RESISTANCE", "DAMAGE_RESISTANCE", "resistance");
				amplifier = 1;
				break;
			case 3:
				type = getPotionEffectType("INVISIBILITY", "invisibility");
				amplifier = 0;
				break;
			case 4:
				type = getPotionEffectType("JUMP_BOOST", "JUMP", "jump_boost", "jump");
				amplifier = 3;
				break;
			case 5:
				type = getPotionEffectType("HEALTH_BOOST", "health_boost");
				amplifier = 4;
				break;
			default:
				LOGGER.warning("No effect for: " + effect);
				return;
		}

		if (type != null) {
			forceApplyPotionEffect(player, new PotionEffect(type, 999999, amplifier));
			if (effect == 5) {
				try {
					uhcPlayer.healFully(); // Fill the newly added hearts
				} catch (UhcPlayerNotOnlineException ignored) {
					// Player logged out
				}
			}
		} else {
			LOGGER.warning("Could not resolve PotionEffectType for effect index: " + effect);
		}
	}

	private PotionEffectType getPotionEffectType(String... names) {
		for (String name : names) {
			PotionEffectType type = PotionEffectType.getByName(name);
			if (type != null) {
				return type;
			}
		}
		return null;
	}

	// Solution for https://gitlab.com/uhccore/uhccore/-/issues/120
	private void forceApplyPotionEffect(Player player, PotionEffect effect) {
		player.removePotionEffect(effect.getType());
		player.addPotionEffect(effect);
	}

}
