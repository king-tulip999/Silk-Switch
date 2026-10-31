package com.jordan.silkswitch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.jordan.silkswitch.SilkSwitch;
import com.jordan.silkswitch.SilkSwitch.Swap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * This player's settings, saved to config/silkswitch.json as {"silk_touch": true, "free_fortune": false, "free_fortune_level": 1}.
 * Sent with every SwapRequest.
 */
public final class SilkSwitchConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("silkswitch.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FREE_FORTUNE = "free_fortune";
	private static final String FREE_FORTUNE_LEVEL = "free_fortune_level";
	private static final Set<Swap> enabled = EnumSet.allOf(Swap.class);
	/** Lets tools that never had Fortune swap to Fortune at freeFortuneLevel. */
	private static boolean freeFortune = false;
	/** Kept while freeFortune is off so turning it back on remembers the level. */
	private static int freeFortuneLevel = 1;

	private SilkSwitchConfig() {}

	public static boolean freeFortune() {
		return freeFortune;
	}

	public static void setFreeFortune(boolean on) {
		freeFortune = on;
	}

	public static int freeFortuneLevel() {
		return freeFortuneLevel;
	}

	public static void setFreeFortuneLevel(int level) {
		freeFortuneLevel = Math.max(1, level);
	}

	/** Level sent in SwapRequest: 0 when free Fortune is off. */
	public static int requestedFreeFortuneLevel() {
		return freeFortune ? freeFortuneLevel : 0;
	}

	public static boolean isEnabled(Swap swap) {
		return enabled.contains(swap);
	}

	public static void setEnabled(Swap swap, boolean on) {
		if (on) enabled.add(swap);
		else enabled.remove(swap);
	}

	public static int mask() {
		int mask = 0;
		for (Swap swap : enabled) mask |= swap.bit();
		return mask;
	}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try {
			JsonObject json = GSON.fromJson(Files.readString(FILE), JsonObject.class);
			// Missing keys (swaps added in a later version) stay on.
			for (Swap swap : Swap.values()) {
				if (json != null && json.has(swap.key())) setEnabled(swap, json.get(swap.key()).getAsBoolean());
			}
			if (json != null && json.has(FREE_FORTUNE)) freeFortune = json.get(FREE_FORTUNE).getAsBoolean();
			if (json != null && json.has(FREE_FORTUNE_LEVEL)) setFreeFortuneLevel(json.get(FREE_FORTUNE_LEVEL).getAsInt());
		} catch (Exception e) {
			SilkSwitch.LOGGER.warn("Could not read {}, using defaults", FILE, e);
		}
	}

	public static void save() {
		JsonObject json = new JsonObject();
		for (Swap swap : Swap.values()) json.addProperty(swap.key(), isEnabled(swap));
		json.addProperty(FREE_FORTUNE, freeFortune);
		json.addProperty(FREE_FORTUNE_LEVEL, freeFortuneLevel);
		try {
			Files.writeString(FILE, GSON.toJson(json));
		} catch (IOException e) {
			SilkSwitch.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
