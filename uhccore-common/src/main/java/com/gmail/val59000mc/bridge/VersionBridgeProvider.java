package com.gmail.val59000mc.bridge;

import java.util.logging.Level;
import java.util.logging.Logger;

public class VersionBridgeProvider {

	private static final Logger LOGGER = Logger.getLogger(VersionBridgeProvider.class.getCanonicalName());

	private static VersionBridge bridge = null;

	public static VersionBridge getBridge() {
		if (bridge == null) {
			bridge = detectAndCreateBridge();
		}
		return bridge;
	}

	private static VersionBridge detectAndCreateBridge() {
		try {
			Class<?> modernClass = Class.forName("com.gmail.val59000mc.bridge.ModernVersionBridge");
			return (VersionBridge) modernClass.getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			// Expected on the Legacy jar, so keep it out of the console unless someone is debugging
			LOGGER.log(Level.FINE, "Modern version bridge unavailable", t);
		}

		try {
			Class<?> legacyClass = Class.forName("com.gmail.val59000mc.bridge.LegacyVersionBridge");
			return (VersionBridge) legacyClass.getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			LOGGER.log(Level.FINE, "Legacy version bridge unavailable", t);
		}

		return new DefaultVersionBridge();
	}

	public static void setBridge(VersionBridge customBridge) {
		bridge = customBridge;
	}
}
