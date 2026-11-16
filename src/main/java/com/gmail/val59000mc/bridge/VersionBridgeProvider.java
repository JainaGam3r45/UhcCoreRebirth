package com.gmail.val59000mc.bridge;

public class VersionBridgeProvider {

	private static VersionBridge bridge = null;

	public static VersionBridge getBridge() {
		if (bridge == null) {
			bridge = new DefaultVersionBridge();
		}
		return bridge;
	}

	public static void setBridge(VersionBridge customBridge) {
		bridge = customBridge;
	}
}
