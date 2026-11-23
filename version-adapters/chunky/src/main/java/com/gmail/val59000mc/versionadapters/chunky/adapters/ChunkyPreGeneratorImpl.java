package com.gmail.val59000mc.versionadapters.chunky.adapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.popcraft.chunky.api.ChunkyAPI;

import com.gmail.val59000mc.versionadapters.PreGenerationProgress;
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
		return preGenerateChunks(world, Collections.<String>emptyList(), size, null);
	}

	@Override
	public CompletableFuture<Void> preGenerateChunks(String world, List<String> alternateIds, int size, PreGenerationProgress progress) {
		final CompletableFuture<Void> generationComplete = new CompletableFuture<>();
		final ChunkyAPI chunky = Bukkit.getServer().getServicesManager().load(ChunkyAPI.class);

		if (progress != null) {
			chunky.onGenerationProgress(e -> {
				if (isSameWorld(e.world(), world)) {
					progress.update(world, e.chunks(), e.progress());
				}
			});
		}
		chunky.onGenerationComplete(e -> {
			if (isSameWorld(e.world(), world)) {
				if (progress != null) {
					progress.complete(world);
				}
				generationComplete.complete(null);
			} else {
				LOGGER.fine("Ignoring Chunky completion for " + e.world() + ", waiting for " + world);
			}
		});

		// startTask returns false when Chunky can't resolve the world. Ignoring that left the
		// server kicking everyone with kick.loading forever, since completion never fires.
		List<String> ids = new ArrayList<>();
		ids.add(world);
		ids.addAll(alternateIds);
		for (String id : ids) {
			if (chunky.startTask(id, "square", 0, 0, size, size, "region")) {
				return generationComplete;
			}
		}

		generationComplete.completeExceptionally(new IllegalStateException("Chunky could not start a task for any of " + ids));
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
