package com.jordan.silkswitch.client;

import com.jordan.silkswitch.SilkSwitch.SwapResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side swap feedback, played on a successful swap next to the server's chime and the slot flash:
 * a two-color dust helix rising around the player, the enchantment name sliding in over the old one, and a spark around the hotbar slot.
 */
public final class SwapEffects {
	private static final int HELIX_TICKS = 12;
	private static final long NAME_SLIDE_MS = 260, NAME_HOLD_MS = 2200, NAME_FADE_MS = 500;
	private static final long RING_MS = 380, RING_FADE_MS = 220;
	/** Spark tail length in pixels along the slot border. */
	private static final int RING_TAIL = 26;

	private static int color, previousColor;
	private static Component name = Component.empty(), previousName = Component.empty();
	private static long start = Long.MIN_VALUE / 2;
	private static int helixTick = -1;

	private SwapEffects() {}

	/** Called on a successful SwapResult. Draws its own action-bar text, so the caller skips setOverlayMessage. */
	public static void onSwap(Minecraft client, SwapResult result) {
		color = result.flashColor();
		previousColor = result.previousColor();
		name = result.message();
		previousName = result.previous();
		start = System.currentTimeMillis();
		helixTick = 0;
		client.gui.hud.setOverlayMessage(Component.empty(), false); // clear any older message under the slide
	}

	public static void tick(Minecraft client) {
		if (helixTick < 0) return;
		if (client.player == null || client.level == null || helixTick >= HELIX_TICKS) {
			helixTick = -1;
			return;
		}
		spawnHelix(client, helixTick++);
	}

	/**
	 * Three strands, a third of a turn apart, rise around the player from the feet to just above the eyes, two turns: the old color
	 * winding outward, two of the new color winding in. Not at the tool: in first person the hand always draws over world particles, so a ring there
	 * looked like it floated in front of the tool.
	 */
	private static void spawnHelix(Minecraft client, int tick) {
		Player player = client.player;
		Vec3 feet = player.position();
		double top = player.getEyeHeight() + 0.3;
		// Three points per strand per tick so the spiral reads as a line instead of dots.
		for (int sub = 0; sub < 3; sub++) {
			float t = (tick + sub / 3F) / HELIX_TICKS;
			double angle = t * Math.PI * 4; // two turns
			double height = 0.1 + t * top;
			double out = 0.55 + 0.35 * t, in = 0.9 - 0.35 * t;
			Vec3 center = feet.add(0, height, 0);
			spawnDust(client, center, angle, out, previousColor, 0.55F);
			spawnDust(client, center, angle + Math.PI * 2 / 3, in, color, 0.75F);
			spawnDust(client, center, angle + Math.PI * 4 / 3, in, color, 0.75F);
		}
	}

	/** One dust point on a horizontal circle around center. */
	private static void spawnDust(Minecraft client, Vec3 center, double angle, double radius, int rgb, float size) {
		Vec3 pos = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
		client.level.addParticle(new DustParticleOptions(rgb, size), pos.x, pos.y, pos.z, 0, 0, 0);
	}

	/** HUD part, drawn after the hotbar. (x, y) is the selected hotbar icon's top-left corner. */
	public static void render(GuiGraphicsExtractor graphics, int x, int y) {
		long elapsed = System.currentTimeMillis() - start;
		if (elapsed < 0) return;
		renderRing(graphics, x, y, elapsed);
		renderNames(graphics, elapsed);
	}

	private static float easeOut(float t) {
		float u = 1 - Mth.clamp(t, 0, 1);
		return 1 - u * u * u;
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp((int) (alpha * 255), 0, 255) << 24 | rgb;
	}

	/** A spark with a fading tail runs clockwise around the 20x20 slot border, then the whole border glows and fades. */
	private static void renderRing(GuiGraphicsExtractor graphics, int x, int y, long elapsed) {
		if (elapsed >= RING_MS + RING_FADE_MS) return;
		int left = x - 2, top = y - 2, size = 20, perimeter = 4 * (size - 1);
		if (elapsed >= RING_MS) {
			float a = 1 - (elapsed - RING_MS) / (float) RING_FADE_MS;
			for (int i = 0; i < perimeter; i++) ringPixel(graphics, left, top, size, i, argb(0.8F * a, color));
			return;
		}
		int head = Math.round(perimeter * easeOut(elapsed / (float) RING_MS) * 1.05F);
		for (int i = 0; i < RING_TAIL; i++) {
			int pos = head - i;
			if (pos < 0 || pos >= perimeter) continue;
			float a = 1 - i / (float) RING_TAIL;
			ringPixel(graphics, left, top, size, pos, i < 2 ? argb(a, 0xFFFFFF) : argb(a * 0.9F, color));
		}
	}

	/** Pixel number i clockwise around a size-by-size square border starting at its top-left corner. */
	private static void ringPixel(GuiGraphicsExtractor graphics, int left, int top, int size, int i, int argb) {
		int side = size - 1, px, py;
		if (i < side) { px = left + i; py = top; }
		else if (i < 2 * side) { px = left + side; py = top + i - side; }
		else if (i < 3 * side) { px = left + side - (i - 2 * side); py = top + side; }
		else { px = left; py = top + side - (i - 3 * side); }
		graphics.fill(px, py, px + 1, py + 1, argb);
	}

	/** The old name slides up and fades while the new one slides in, where vanilla puts action-bar text (centered, 68 px up). */
	private static void renderNames(GuiGraphicsExtractor graphics, long elapsed) {
		if (elapsed >= NAME_HOLD_MS + NAME_FADE_MS) return;
		Font font = Minecraft.getInstance().font;
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() - 68 - 4;
		float slide = easeOut(elapsed / (float) NAME_SLIDE_MS);
		float hold = elapsed < NAME_HOLD_MS ? 1 : 1 - (elapsed - NAME_HOLD_MS) / (float) NAME_FADE_MS;

		if (slide < 1 && !previousName.getString().isEmpty()) drawName(graphics, font, previousName, cx, cy - 10 * slide, 1 - slide);
		drawName(graphics, font, name, cx, cy + 10 * (1 - slide), slide * hold);
	}

	private static void drawName(GuiGraphicsExtractor graphics, Font font, Component text, int cx, float y, float alpha) {
		if (alpha <= 0.02F) return;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx - font.width(text) / 2F, y);
		// The component carries its own color; only the alpha comes from here.
		graphics.text(font, text, 0, 0, argb(alpha, 0xFFFFFF), true);
		graphics.pose().popMatrix();
	}
}
