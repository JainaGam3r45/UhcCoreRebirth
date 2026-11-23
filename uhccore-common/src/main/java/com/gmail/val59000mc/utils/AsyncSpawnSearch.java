package com.gmail.val59000mc.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;

import io.papermc.lib.PaperLib;

/**
 * Random spawn search that never reads chunks from disk on the main thread. Candidate chunks get
 * loaded through Paper's async chunk API and each column is checked on the main thread as soon as
 * its chunk arrives, which costs nothing once it's in memory.
 * <p>
 * Only worth it on Paper 1.13+: anywhere else PaperLib falls back to a sync load, and 1.8 - 1.12
 * also need the 5x5 populate dance from {@link LocationUtils}.
 * </p>
 */
public final class AsyncSpawnSearch {

	private static final int CANDIDATES_PER_BATCH = 8;
	private static final int MAX_BATCHES = 40;

	private AsyncSpawnSearch() {
	}

	/**
	 * Same rules as {@link LocationUtils#getRandomSpawnLocation(World, int, BiomeBlacklist, Collection, double)},
	 * and it falls back to that (sync) search when no candidate fits.
	 *
	 * @return a future completed on the main thread
	 */
	public static CompletableFuture<Location> find(World world, int maxDistance, BiomeBlacklist blacklist, Collection<Location> otherSpawns, double minDistance) {
		CompletableFuture<Location> result = new CompletableFuture<>();
		BiomeBlacklist biomes = world.getEnvironment() == Environment.NORMAL ? blacklist : BiomeBlacklist.empty();
		searchBatch(world, maxDistance, biomes, otherSpawns, minDistance, 0, result);
		return result;
	}

	private static void searchBatch(World world, int maxDistance, BiomeBlacklist biomes, Collection<Location> otherSpawns,
									double minDistance, int batch, CompletableFuture<Location> result) {
		// This runs from scheduler callbacks, an exception escaping here would leave the scatter waiting forever.
		try {
			trySearchBatch(world, maxDistance, biomes, otherSpawns, minDistance, batch, result);
		} catch (RuntimeException e) {
			result.completeExceptionally(e);
		}
	}

	private static void trySearchBatch(World world, int maxDistance, BiomeBlacklist biomes, Collection<Location> otherSpawns,
									   double minDistance, int batch, CompletableFuture<Location> result) {
		if (batch >= MAX_BATCHES) {
			// Nothing fit with the full rules, the sync search knows how to relax them.
			result.complete(LocationUtils.getRandomSpawnLocation(world, maxDistance, biomes, otherSpawns, minDistance));
			return;
		}

		List<CompletableFuture<Void>> evaluations = new ArrayList<>();
		for (int i = 0; i < CANDIDATES_PER_BATCH; i++) {
			final int x = ThreadLocalRandom.current().nextInt(-maxDistance, maxDistance);
			final int z = ThreadLocalRandom.current().nextInt(-maxDistance, maxDistance);
			if (LocationUtils.isTooClose(world, x, z, otherSpawns, minDistance)) {
				continue;
			}

			evaluations.add(PaperLib.getChunkAtAsync(world, x >> 4, z >> 4, true).thenAccept(chunk -> MainThread.execute(() -> {
				if (result.isDone()) {
					return;
				}
				try {
					Location spawn = LocationUtils.getSafeSpawnLocationAt(world, x, z, biomes);
					if (spawn != null) {
						result.complete(spawn);
					}
				} catch (RuntimeException e) {
					result.completeExceptionally(e);
				}
			})));
		}

		CompletableFuture.allOf(evaluations.toArray(new CompletableFuture[0])).whenComplete((ignored, error) -> MainThread.execute(() -> {
			if (!result.isDone()) {
				searchBatch(world, maxDistance, biomes, otherSpawns, minDistance, batch + 1, result);
			}
		}));
	}
}
