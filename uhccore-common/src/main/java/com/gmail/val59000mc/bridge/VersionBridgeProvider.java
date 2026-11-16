package com.gmail.val59000mc.bridge;

public class VersionBridgeProvider {

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
		} catch (Throwable ignored) {
		}

		try {
			Class<?> legacyClass = Class.forName("com.gmail.val59000mc.bridge.LegacyVersionBridge");
			return (VersionBridge) legacyClass.getDeclaredConstructor().newInstance();
		} catch (Throwable ignored) {
		}

		return new DefaultVersionBridge();
	}

	public static void setBridge(VersionBridge customBridge) {
		bridge = customBridge;
	}
}
