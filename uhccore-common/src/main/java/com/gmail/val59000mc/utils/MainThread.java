package com.gmail.val59000mc.utils;

import org.bukkit.Bukkit;

import com.gmail.val59000mc.UhcCore;

public final class MainThread {

	private MainThread() {
	}

	/**
	 * Runs the task right away on the main thread, or schedules it there from any other thread.
	 * Dropped once the plugin is disabled, the scheduler throws for disabled plugins.
	 */
	public static void execute(Runnable task) {
		if (Bukkit.isPrimaryThread()) {
			task.run();
		} else if (UhcCore.getPlugin().isEnabled()) {
			Bukkit.getScheduler().runTask(UhcCore.getPlugin(), task);
		}
	}
}
