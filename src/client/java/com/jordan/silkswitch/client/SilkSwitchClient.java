package com.jordan.silkswitch.client;

import com.jordan.silkswitch.SilkSwitch;
import com.jordan.silkswitch.SilkSwitch.SwapRequest;
import com.jordan.silkswitch.SilkSwitch.SwapResult;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.sdl.SDLScancode;

public class SilkSwitchClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SilkSwitch.id("main"));
	private static final KeyMapping SWAP_KEY = new KeyMapping("key.silkswitch.swap", InputConstants.Type.KEYBOARD,
			SDLScancode.SDL_SCANCODE_G, CATEGORY);
	/** Unbound by default; lets players without Mod Menu reach the settings screen. */
	private static final KeyMapping SETTINGS_KEY = new KeyMapping("key.silkswitch.settings", InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(), CATEGORY);

	/** Presses within this long of the last switch are ignored. */
	private static final long COOLDOWN_MS = 500;
	private static final long FLASH_MS = 450;

	// 0, not Long.MIN_VALUE: now - Long.MIN_VALUE overflows to a negative number, which made every press look like it was inside the cooldown.
	private static long lastSwap = 0;
	private static long flashStart = 0;
	private static int flashColor;

	@Override
	public void onInitializeClient() {
		SilkSwitchConfig.load();
		KeyMappingHelper.registerKeyMapping(SWAP_KEY);
		KeyMappingHelper.registerKeyMapping(SETTINGS_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (SWAP_KEY.consumeClick()) requestSwap(client);
			while (SETTINGS_KEY.consumeClick()) client.gui.setScreen(new SilkSwitchConfigScreen(client.gui.screen()));
			SwapEffects.tick(client);
		});

		ClientPlayNetworking.registerGlobalReceiver(SwapResult.TYPE, (payload, context) -> onResult(context.client(), payload));

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SilkSwitch.id("swap_flash"), SilkSwitchClient::renderFlash);
	}

	private static void requestSwap(Minecraft client) {
		long now = System.currentTimeMillis();
		if (client.getConnection() == null || now - lastSwap < COOLDOWN_MS) return;
		lastSwap = now;
		if (!ClientPlayNetworking.canSend(SwapRequest.TYPE)) {
			SilkSwitch.LOGGER.warn("Server does not accept {}", SwapRequest.TYPE.id());
			client.gui.hud.setOverlayMessage(Component.translatable("message.silkswitch.not_on_server").withStyle(ChatFormatting.RED), false);
			return;
		}
		ClientPlayNetworking.send(new SwapRequest(SilkSwitchConfig.mask(), SilkSwitchConfig.requestedFreeFortuneLevel()));
	}

	private static void onResult(Minecraft client, SwapResult result) {
		if (result.flashColor() == 0) {
			client.gui.hud.setOverlayMessage(result.message(), false);
			return;
		}
		applyInPlace(client, result);
		startFlash(result.flashColor());
		SwapEffects.onSwap(client, result); // shows the message itself, sliding in over the old one
	}

	/**
	 * Copies the swapped enchantments onto the held stack before the server's slot update arrives. The first-person renderer then sees
	 * the new stack as the same item and swaps it in without the drop-and-rise animation it plays when components change.
	 * Only when the same slot is still selected with the same item, so no other stack is ever changed.
	 */
	private static void applyInPlace(Minecraft client, SwapResult result) {
		if (client.player == null || result.slot() != client.player.getInventory().getSelectedSlot()) return;
		ItemStack held = client.player.getMainHandItem();
		if (!ItemStack.isSameItem(held, result.stack())) return;
		held.copyFrom(DataComponents.ENCHANTMENTS, result.stack());
		held.copyFrom(DataComponents.CUSTOM_DATA, result.stack());
	}

	private static void startFlash(int color) {
		flashColor = color;
		flashStart = System.currentTimeMillis();
	}

	/** Slot ring and name slide, then the fading colored glow over the selected hotbar slot. Time-based, so no tick state to maintain. */
	private static void renderFlash(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		// Matches vanilla hotbar item layout: x = width/2 - 90 + slot*20 + 2, y = height - 19.
		int x = graphics.guiWidth() / 2 - 88 + client.player.getInventory().getSelectedSlot() * 20;
		int y = graphics.guiHeight() - 19;
		SwapEffects.render(graphics, x, y);
		long elapsed = System.currentTimeMillis() - flashStart;
		if (elapsed < 0 || elapsed >= FLASH_MS) return;

		float t = 1F - elapsed / (float) FLASH_MS;
		int alpha = (int) (t * t * 0xC0);
		graphics.fill(x - 2, y - 2, x + 18, y + 18, (alpha / 3) << 24 | flashColor);
		graphics.fill(x, y, x + 16, y + 16, alpha << 24 | flashColor);
	}
}
