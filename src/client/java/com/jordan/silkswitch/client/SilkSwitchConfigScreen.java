package com.jordan.silkswitch.client;

import com.jordan.silkswitch.SilkSwitch.Swap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Settings in the vanilla options-screen style: on/off per swap, free Fortune, and its level slider (only shown while free Fortune is on).
 * Opened from Mod Menu or the "Open settings" key.
 */
public class SilkSwitchConfigScreen extends OptionsSubScreen {
	/**
	 * Slider top end. Mods like Unlimited Enchantments allow Fortune above III through anvils without raising Fortune's max level,
	 * so the max can't be read from the game. X is the highest level vanilla has a numeral for.
	 */
	private static final int SLIDER_MAX = 10;

	public SilkSwitchConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("options.silkswitch.title"));
	}

	@Override
	protected void addOptions() {
		for (Swap swap : Swap.values()) {
			String key = "option.silkswitch." + swap.key();
			Tooltip tooltip = Tooltip.create(Component.translatable(key + ".tooltip"));
			list.addBig(CycleButton.onOffBuilder(SilkSwitchConfig.isEnabled(swap))
					.withTooltip(on -> tooltip)
					.create(Component.translatable(key), (button, on) -> SilkSwitchConfig.setEnabled(swap, on)));
		}
		// The slider is always in the list and only hidden: rebuildWidgets() would stack a second list on top, because
		// OptionsSubScreen keeps adding to its final layout. It's the last row, so hiding it leaves no gap.
		FortuneLevelSlider slider = new FortuneLevelSlider(Math.max(maxFortune(), SilkSwitchConfig.freeFortuneLevel()));
		slider.setTooltip(Tooltip.create(Component.translatable("option.silkswitch.free_fortune_level.tooltip")));
		slider.visible = SilkSwitchConfig.freeFortune();
		Tooltip freeFortuneTooltip = Tooltip.create(Component.translatable("option.silkswitch.free_fortune.tooltip"));
		list.addBig(CycleButton.onOffBuilder(SilkSwitchConfig.freeFortune())
				.withTooltip(on -> freeFortuneTooltip)
				.create(Component.translatable("option.silkswitch.free_fortune"), (button, on) -> {
					SilkSwitchConfig.setFreeFortune(on);
					slider.visible = on;
				}));
		list.addBig(slider);
	}

	/** SLIDER_MAX, or Fortune's max level in the current world if a data pack raised it higher. */
	private static int maxFortune() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return SLIDER_MAX;
		return client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE)
				.map(fortune -> Math.max(SLIDER_MAX, fortune.value().getMaxLevel())).orElse(SLIDER_MAX);
	}

	@Override
	public void removed() {
		super.removed();
		SilkSwitchConfig.save();
	}

	/** Picks the Fortune level, 1 to max, snapping to whole levels. */
	private static class FortuneLevelSlider extends AbstractSliderButton {
		private final int max;

		FortuneLevelSlider(int max) {
			super(0, 0, 310, 20, Component.empty(), toValue(SilkSwitchConfig.freeFortuneLevel(), max));
			this.max = max;
			updateMessage();
		}

		private static double toValue(int level, int max) {
			return max <= 1 ? 0 : (level - 1) / (double) (max - 1);
		}

		private int level() {
			return 1 + (int) Math.round(value * (max - 1));
		}

		@Override
		protected void updateMessage() {
			// Vanilla only has numerals up to X; higher levels from data packs fall back to plain digits.
			int level = level();
			setMessage(Component.translatable("option.silkswitch.free_fortune_level",
					Component.translatableWithFallback("enchantment.level." + level, String.valueOf(level))));
		}

		@Override
		protected void applyValue() {
			int level = Mth.clamp(level(), 1, max);
			value = toValue(level, max);
			SilkSwitchConfig.setFreeFortuneLevel(level);
		}
	}
}
