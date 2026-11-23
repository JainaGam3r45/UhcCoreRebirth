package com.gmail.val59000mc.tasks;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.versionadapters.PreGenerationProgress;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ChunkLoaderTask implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(ChunkLoaderTask.class.getCanonicalName());

	private final World world;
	private final int restEveryNumOfChunks, restDuration;

	private final int maxChunk;
	private int x, z;
	private final int totalChunksToLoad;
	private int chunksLoaded;
	private final PreGenerationProgress progress;

	public ChunkLoaderTask(World world, int size, int restEveryNumOfChunks, int restDuration) {
		this(world, size, restEveryNumOfChunks, restDuration, null);
	}

	public ChunkLoaderTask(World world, int size, int restEveryNumOfChunks, int restDuration, PreGenerationProgress progress) {
		this.world = world;
		this.progress = progress;
		this.restEveryNumOfChunks = restEveryNumOfChunks;
		this.restDuration = restDuration;

		maxChunk = Math.round(size/16f) + 1;

		totalChunksToLoad = (2*maxChunk+1)*(2*maxChunk+1);

		x = -maxChunk;
		z = -maxChunk;

		if (progress != null) {
			progress.track(world.getName(), world.getEnvironment(), totalChunksToLoad);
		}
	}

	/**
	 * Deliberately asks PaperLib instead of VersionUtils: getChunkAtAsync only goes async when
	 * PaperLib picked its Paper handler. PaperLib 1.0.8 can't parse 26.x, falls back to plain
	 * world.getChunkAt there, and Paper kills sync chunk loads coming from another thread.
	 */
	public static boolean canRunOffMainThread() {
		return PaperLib.isPaper() && PaperLib.isVersion(13);
	}

	public abstract void onDoneLoadingWorld();
	public abstract void onDoneLoadingChunk(Chunk chunk);

	@Override
	public void run() {
		int loaded = 0;
		while(x <= maxChunk && loaded < restEveryNumOfChunks){
			try {
				Chunk chunk = PaperLib.getChunkAtAsync(world, x, z, true).get();

				if (Bukkit.isPrimaryThread()){
					onDoneLoadingChunk(chunk);
				}else {
					Bukkit.getScheduler().runTask(UhcCore.getPlugin(), () -> onDoneLoadingChunk(chunk));
				}
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, "Chunk loader interrupted", e);
				return;
			} catch (ExecutionException e) {
				LOGGER.log(Level.WARNING, "Unable to load chunk", e);
			}

			loaded++;
			z++;

			if (z > maxChunk){
				z = -maxChunk;
				x++;
			}
		}

		chunksLoaded += loaded;
		if (progress != null) {
			progress.update(world.getName(), chunksLoaded, -1);
		}

		// Cancel world generation if the plugin has been disabled.
		if (!UhcCore.getPlugin().isEnabled()) {
			LOGGER.info("Plugin is disabled, stopping world generation!");
			return;
		}

		// Not yet done loading all chunks
		if(x <= maxChunk){
			LOGGER.info("Loading map "+getLoadingState()+"% - "+chunksLoaded+"/"+totalChunksToLoad+" chunks loaded");

			if (canRunOffMainThread()){
				Bukkit.getScheduler().scheduleAsyncDelayedTask(UhcCore.getPlugin(), this, restDuration);
			}else {
				Bukkit.getScheduler().scheduleSyncDelayedTask(UhcCore.getPlugin(), this, restDuration);
			}
		}
		// Done loading all chunks
		else{
			if (progress != null) {
				progress.complete(world.getName());
			}
			Bukkit.getScheduler().runTask(UhcCore.getPlugin(), this::onDoneLoadingWorld);
		}
	}

	public void printSettings(){
		LOGGER.info("Generating environment "+world.getEnvironment().toString());
		LOGGER.info("Loading a total "+Math.floor(totalChunksToLoad)+" chunks, up to chunk ( "+maxChunk+" , "+maxChunk+" )");
		LOGGER.info("Resting "+restDuration+" ticks every "+restEveryNumOfChunks+" chunks");
		LOGGER.info("Loading map "+getLoadingState()+"%");
	}

	private String getLoadingState(){
		double percentage = 100*(double)chunksLoaded/totalChunksToLoad;
		return world.getEnvironment()+" "+(Math.floor(10*percentage)/10);
	}

}
