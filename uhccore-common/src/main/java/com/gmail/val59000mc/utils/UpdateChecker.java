package com.gmail.val59000mc.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class UpdateChecker {

	private static final Logger LOGGER = Logger.getLogger(UpdateChecker.class.getCanonicalName());
	private static final String GITHUB_REPO = "JainaGam3r45/UhcCoreRebirth";
	private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
	private static final String RELEASES_URL = "https://github.com/" + GITHUB_REPO + "/releases/latest";
	private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

	private static boolean updateAvailable = false;
	private static String latestVersion = null;

	public static void checkForUpdates(Plugin plugin) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				URL url = new URL(API_URL);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				// GitHub API strictly requires a User-Agent header; drops 403 otherwise
				conn.setRequestProperty("User-Agent", "UhcCoreRebirth-UpdateChecker");
				conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
				conn.setConnectTimeout(5000);
				conn.setReadTimeout(5000);

				int responseCode = conn.getResponseCode();
				if (responseCode == 200) {
					StringBuilder response = new StringBuilder();
					try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
						String line;
						while ((line = in.readLine()) != null) {
							response.append(line);
						}
					}

					Matcher matcher = TAG_PATTERN.matcher(response.toString());
					if (matcher.find()) {
						String tag = matcher.group(1).trim();
						String currentVersion = plugin.getDescription().getVersion();

						if (isNewerVersion(tag, currentVersion)) {
							updateAvailable = true;
							latestVersion = tag;
							LOGGER.info("[UhcCore] A new version is available: " + tag + " (Running: " + currentVersion + ")");
							LOGGER.info("[UhcCore] Download the updated jar at: " + RELEASES_URL);
						} else {
							LOGGER.info("[UhcCore] Plugin is up to date (" + currentVersion + ").");
						}
					}
				} else if (responseCode == 404) {
					// No release published yet on this repository fork
					LOGGER.fine("[UhcCore] No published releases found on GitHub repo.");
				}
			} catch (Exception e) {
				// Don't spam stacktraces if GitHub API is unreachable or rate-limited
				LOGGER.fine("[UhcCore] Could not query GitHub releases: " + e.getMessage());
			}
		});
	}

	public static boolean isUpdateAvailable() {
		return updateAvailable;
	}

	public static String getLatestVersion() {
		return latestVersion;
	}

	public static String getReleasesUrl() {
		return RELEASES_URL;
	}

	public static void notifyPlayerIfAdmin(Player player) {
		if (updateAvailable && (player.isOp() || player.hasPermission("uhc-core.admin"))) {
			player.sendMessage(ChatColor.GOLD + "[UhcCore] " + ChatColor.YELLOW + "A new update is available: "
				+ ChatColor.GREEN + latestVersion + ChatColor.YELLOW + "!");
			player.sendMessage(ChatColor.GOLD + "[UhcCore] " + ChatColor.AQUA + "Download: " + RELEASES_URL);
		}
	}

	public static boolean isNewerVersion(String remoteTag, String currentVersion) {
		if (remoteTag == null || currentVersion == null) {
			return false;
		}

		String cleanRemote = remoteTag.startsWith("v") || remoteTag.startsWith("V") ? remoteTag.substring(1) : remoteTag;
		String cleanCurrent = currentVersion.startsWith("v") || currentVersion.startsWith("V") ? currentVersion.substring(1) : currentVersion;

		// Strip commit hashes or SNAPSHOT tags for base semantic version check
		String cleanCurrentBase = cleanCurrent.split("-")[0];
		String cleanRemoteBase = cleanRemote.split("-")[0];

		String[] remoteParts = cleanRemoteBase.split("\\.");
		String[] currentParts = cleanCurrentBase.split("\\.");

		int length = Math.max(remoteParts.length, currentParts.length);
		for (int i = 0; i < length; i++) {
			int r = i < remoteParts.length ? parseSafeInt(remoteParts[i]) : 0;
			int c = i < currentParts.length ? parseSafeInt(currentParts[i]) : 0;
			if (r > c) return true;
			if (r < c) return false;
		}

		// If numerical version is identical, a release tag is newer than a dev snapshot
		return currentVersion.contains("SNAPSHOT") && !remoteTag.contains("SNAPSHOT");
	}

	private static int parseSafeInt(String s) {
		try {
			return Integer.parseInt(s.replaceAll("[^0-9]", ""));
		} catch (Exception ignored) {
			return 0;
		}
	}

}
