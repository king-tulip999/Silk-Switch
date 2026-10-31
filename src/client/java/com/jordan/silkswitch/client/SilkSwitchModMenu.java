package com.jordan.silkswitch.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the settings button to Silk Switch's entry in Mod Menu. Only loaded when Mod Menu is installed. */
public class SilkSwitchModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SilkSwitchConfigScreen::new;
	}
}
