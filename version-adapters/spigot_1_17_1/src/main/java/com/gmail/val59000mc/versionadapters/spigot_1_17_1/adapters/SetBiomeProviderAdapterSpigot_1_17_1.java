package com.gmail.val59000mc.versionadapters.spigot_1_17_1.adapters;

import org.bukkit.WorldCreator;

import com.gmail.val59000mc.versionadapters.ServerVersion;
import com.gmail.val59000mc.versionadapters.adapters.SetBiomeProviderAdapter;
import com.google.auto.service.AutoService;

@AutoService(SetBiomeProviderAdapter.class)
public class SetBiomeProviderAdapterSpigot_1_17_1 implements SetBiomeProviderAdapter {

	@Override
	public boolean isCompatible() {
		return ServerVersion.isVersion(17, 1);
	}

	@Override
	public void setBiomeProvider(WorldCreator worldCreator, String biomeProvider) {
		worldCreator.biomeProvider(biomeProvider);
	}

}
