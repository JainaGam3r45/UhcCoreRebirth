package com.gmail.val59000mc.versionadapters.adapters;

import java.util.concurrent.CompletableFuture;

import com.gmail.val59000mc.versionadapters.PreGenerationProgress;
import com.gmail.val59000mc.versionadapters.VersionAdapter;

public interface ChunkyPreGenerator extends VersionAdapter {

	/**
	 * Pre-generates an area of chunks within the given world.
	 *
	 * @param world the name of the world to pre-generate in
	 * @param size the size (apothem) of the pre-generation area
	 * @return a future which completes when all the chunks within the area have been generated
	 */
	CompletableFuture<Void> preGenerateChunks(String world, int size);

	/**
	 * Same as {@link #preGenerateChunks(String, int)}, also reporting progress for the given world name.
	 * The world must already be tracked by {@code progress}.
	 */
	default CompletableFuture<Void> preGenerateChunks(String world, int size, PreGenerationProgress progress) {
		return preGenerateChunks(world, size);
	}

}
