package com.gmail.val59000mc.versionadapters.chunky.adapters;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.popcraft.chunky.api.ChunkyAPI;

import com.gmail.val59000mc.versionadapters.adapters.ChunkyPreGenerator;
import com.google.auto.service.AutoService;

@AutoService(ChunkyPreGenerator.class)
public class ChunkyPreGeneratorImpl implements ChunkyPreGenerator {

	private static final Logger LOGGER = Logger.getLogger(ChunkyPreGeneratorImpl.class.getCanonicalName());

	@Override
	public boolean isCompatible() {
		return Bukkit.getPluginManager().isPluginEnabled("Chunky") && Bukkit.getServer().getServicesManager().load(ChunkyAPI.class) != null;
	}

	@Override
	public CompletableFuture<Void> preGenerateChunks(String world, int size) {
		final CompletableFuture<Void> generationComplete = new CompletableFuture<>();
		final ChunkyAPI chunky = Bukkit.getServer().getServicesManager().load(ChunkyAPI.class);

		chunky.onGenerationComplete(e -> {
			if (isSameWorld(e.world(), world)) {
				generationComplete.complete(null);
			} else {
				LOGGER.fine("Ignoring Chunky completion for " + e.world() + ", waiting for " + world);
			}
		});
		chunky.startTask(world, "square", 0, 0, size, size, "region");

		return generationComplete;
	}

	/**
	 * Chunky-Bukkit reports worlds by plain name ("uhc_nether"), while Chunky-Paper 1.5+
	 * reports the namespaced key ("minecraft:uhc_nether"). Paper lowercases key paths, so the
	 * comparison ignores case. We can't ask the world for its key here: this adapter also
	 * ships in the Legacy jar, where World#getKey doesn't exist.
	 */
	static boolean isSameWorld(String chunkyWorld, String worldName) {
		if (chunkyWorld == null || worldName == null) {
			return false;
		}
		if (chunkyWorld.equals(worldName)) {
			return true;
		}
		final String path = chunkyWorld.substring(chunkyWorld.lastIndexOf(':') + 1);
		return path.toLowerCase(Locale.ROOT).equals(worldName.toLowerCase(Locale.ROOT));
	}

}
