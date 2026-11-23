package com.gmail.val59000mc.versionadapters;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World.Environment;

/**
 * Pre-generation progress for the loading MOTD and kick message.
 * <p>
 * Chunky's worker thread or the async chunk loader write it, and server list pings read it from
 * Netty threads, hence the locking. Chunky generates the overworld and nether at the same time,
 * so the "current" world is the first one in registration order that hasn't finished yet.
 * </p>
 */
public final class PreGenerationProgress {

	private final List<WorldProgress> worlds = new ArrayList<>();

	/** Resets the entry if the world was already tracked. */
	public synchronized void track(String worldName, Environment environment, long totalChunks) {
		WorldProgress progress = find(worldName);
		if (progress == null) {
			worlds.add(new WorldProgress(worldName, environment, 0, totalChunks, 0, false));
		} else {
			worlds.set(worlds.indexOf(progress), new WorldProgress(worldName, environment, 0, totalChunks, 0, false));
		}
	}

	/**
	 * @param percent completion between 0 and 100, or a negative value to derive it from the chunk counts
	 */
	public synchronized void update(String worldName, long chunks, double percent) {
		WorldProgress progress = find(worldName);
		if (progress == null || progress.isComplete()) {
			return;
		}
		long total = Math.max(progress.getTotalChunks(), chunks);
		double newPercent = percent >= 0 ? percent : (total == 0 ? 0 : 100.0 * chunks / total);
		worlds.set(worlds.indexOf(progress), new WorldProgress(worldName, progress.getEnvironment(), chunks, total, Math.min(100, newPercent), false));
	}

	public synchronized void complete(String worldName) {
		WorldProgress progress = find(worldName);
		if (progress != null) {
			worlds.set(worlds.indexOf(progress), new WorldProgress(worldName, progress.getEnvironment(),
				progress.getTotalChunks(), progress.getTotalChunks(), 100, true));
		}
	}

	/**
	 * @return the first unfinished world, the last world if everything finished, or null when nothing is tracked
	 */
	public synchronized WorldProgress getCurrent() {
		for (WorldProgress progress : worlds) {
			if (!progress.isComplete()) {
				return progress;
			}
		}
		return worlds.isEmpty() ? null : worlds.get(worlds.size() - 1);
	}

	public synchronized void clear() {
		worlds.clear();
	}

	private WorldProgress find(String worldName) {
		for (WorldProgress progress : worlds) {
			if (progress.getWorldName().equals(worldName)) {
				return progress;
			}
		}
		return null;
	}

	/**
	 * Chunks Chunky goes through for a square of the given apothem around 0,0. Its progress
	 * event only reports a percentage and a running count, not the total.
	 */
	public static long countChunksInSquare(int apothem) {
		long chunksPerSide = Math.floorDiv(apothem, 16) - Math.floorDiv(-apothem, 16) + 1;
		return chunksPerSide * chunksPerSide;
	}

	/** Immutable view of one world's progress. */
	public static final class WorldProgress {

		private final String worldName;
		private final Environment environment;
		private final long chunks;
		private final long totalChunks;
		private final double percent;
		private final boolean complete;

		private WorldProgress(String worldName, Environment environment, long chunks, long totalChunks, double percent, boolean complete) {
			this.worldName = worldName;
			this.environment = environment;
			this.chunks = chunks;
			this.totalChunks = totalChunks;
			this.percent = percent;
			this.complete = complete;
		}

		public String getWorldName() {
			return worldName;
		}

		public Environment getEnvironment() {
			return environment;
		}

		public long getChunks() {
			return chunks;
		}

		public long getTotalChunks() {
			return totalChunks;
		}

		public double getPercent() {
			return percent;
		}

		public boolean isComplete() {
			return complete;
		}
	}
}
