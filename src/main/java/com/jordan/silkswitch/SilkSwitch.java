package com.jordan.silkswitch;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilkSwitch implements ModInitializer {
	public static final String MOD_ID = "silkswitch";
	public static final Logger LOGGER = LoggerFactory.getLogger("Silk Switch");
	/** Highest enchantment level an item can hold. */
	private static final int MAX_STORED_LEVEL = 255;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * One enchantment on one side of a swap. While the other side is active its level is kept in vanilla CUSTOM_DATA under dataKey,
	 * so the item still loads if the mod is removed. Only levels that differ from defaultLevel are stored; a missing key restores the default.
	 * defaultLevel 0 means the enchantment only comes back if the item really had it. If free is set and the player turned on
	 * free Fortune, a missing key gives the player's chosen level instead.
	 */
	public record Part(ResourceKey<Enchantment> enchantment, int defaultLevel, boolean free, String dataKey) {
		Part(ResourceKey<Enchantment> enchantment, int defaultLevel) {
			this(enchantment, defaultLevel, false, "silkswitch_" + enchantment.identifier().getPath());
		}
	}

	/** One state of a swap: the enchantments it puts on the item, plus its message color, effect color and chime pitch. */
	public record Side(List<Part> parts, ChatFormatting textColor, int color, float pitch) {}

	/** Swaps for the main-hand item. The ordinal is the bit in SwapRequest.enabled, so only append new entries. */
	public enum Swap {
		// Fortune defaults to 0: a tool that only ever had Silk Touch can't swap to Fortune, unless free Fortune is on.
		// Data key keeps the Silk Swap name so tools already on Silk Touch get their Fortune level back.
		SILK_TOUCH(
				new Side(List.of(new Part(Enchantments.FORTUNE, 0, true, "silkswap_fortune")), ChatFormatting.GOLD, 0xFFAA00, 1.0F),
				new Side(List.of(new Part(Enchantments.SILK_TOUCH, 1)), ChatFormatting.AQUA, 0x55FFFF, 1.6F));

		public final Side first, second;

		Swap(Side first, Side second) {
			this.first = first;
			this.second = second;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public int bit() {
			return 1 << ordinal();
		}
	}

	/**
	 * Client → server: swap the main-hand item. enabled: bitmask of Swap.bit() the player has turned on.
	 * freeFortuneLevel: Fortune level for tools that never had Fortune; 0 = refuse them. The server caps it at MAX_STORED_LEVEL.
	 */
	public record SwapRequest(int enabled, int freeFortuneLevel) implements CustomPacketPayload {
		public static final Type<SwapRequest> TYPE = new Type<>(id("swap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SwapRequest> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, SwapRequest::enabled, ByteBufCodecs.VAR_INT, SwapRequest::freeFortuneLevel, SwapRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: action-bar message, and the hotbar slot flash color (0 = no flash).
	 * previous / previousColor: what the tool had before the swap, for client effects that show the old side giving way to the new one.
	 * On success, also the swapped tool and its hotbar slot, so the client can update its copy in place before the inventory syncs;
	 * that keeps the first-person tool from playing the re-equip animation. Failures send empty text, 0, ItemStack.EMPTY and slot -1.
	 */
	public record SwapResult(Component message, int flashColor, Component previous, int previousColor, ItemStack stack, int slot)
			implements CustomPacketPayload {
		public static final Type<SwapResult> TYPE = new Type<>(id("swap_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SwapResult> CODEC = StreamCodec.composite(
				ComponentSerialization.STREAM_CODEC, SwapResult::message, ByteBufCodecs.INT, SwapResult::flashColor,
				ComponentSerialization.STREAM_CODEC, SwapResult::previous, ByteBufCodecs.INT, SwapResult::previousColor,
				ItemStack.OPTIONAL_STREAM_CODEC, SwapResult::stack, ByteBufCodecs.VAR_INT, SwapResult::slot, SwapResult::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(SwapRequest.TYPE, SwapRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SwapResult.TYPE, SwapResult.CODEC);
		// Fabric runs play receivers on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(SwapRequest.TYPE, (payload, context) -> swap(context.player(), payload));
	}

	private static void swap(ServerPlayer player, SwapRequest request) {
		ServerLevel level = (ServerLevel) player.level();
		Registry<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

		// Work on a copy and set it back so the inventory change always syncs to the client.
		ItemStack stack = player.getMainHandItem().copy();
		ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

		Swap swap = null;
		Side from = null, to = null;
		for (Swap candidate : Swap.values()) {
			if (hasAny(current, candidate.first, enchantments)) {
				from = candidate.first;
				to = candidate.second;
			} else if (hasAny(current, candidate.second, enchantments)) {
				from = candidate.second;
				to = candidate.first;
			} else continue;
			swap = candidate;
			break;
		}
		if (swap == null) {
			fail(player, Component.translatable("message.silkswitch.no_enchant"));
			return;
		}
		if ((request.enabled() & swap.bit()) == 0) {
			fail(player, Component.translatable("message.silkswitch.disabled", Component.translatable("option.silkswitch." + swap.key())));
			return;
		}

		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		int[] restored = new int[to.parts().size()];
		boolean anyRestored = false;
		for (int i = 0; i < restored.length; i++) {
			Part part = to.parts().get(i);
			int fallback = part.defaultLevel();
			if (part.free() && request.freeFortuneLevel() > 0) {
				// Not capped at the enchantment's max level: mods like Unlimited Enchantments allow higher levels without changing it.
				fallback = Mth.clamp(request.freeFortuneLevel(), 1, MAX_STORED_LEVEL);
			}
			restored[i] = data.getIntOr(part.dataKey(), fallback);
			anyRestored |= restored[i] > 0;
		}
		if (!anyRestored) {
			// Nothing stored and no default, e.g. a Silk Touch tool that never had Fortune.
			Holder<Enchantment> missing = enchantments.getOrThrow(to.parts().getFirst().enchantment());
			fail(player, Component.translatable("message.silkswitch.never_had", missing.value().description()));
			return;
		}

		for (Part part : to.parts()) data.remove(part.dataKey());
		for (Part part : from.parts()) {
			int stored = current.getLevel(enchantments.getOrThrow(part.enchantment()));
			if (stored != part.defaultLevel()) data.putInt(part.dataKey(), stored);
			else data.remove(part.dataKey());
		}
		Side removing = from, adding = to;
		EnchantmentHelper.updateEnchantments(stack, e -> {
			for (Part part : removing.parts()) e.set(enchantments.getOrThrow(part.enchantment()), 0); // level 0 removes the enchantment
			for (int i = 0; i < restored.length; i++) e.set(enchantments.getOrThrow(adding.parts().get(i).enchantment()), restored[i]);
		});

		if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
		else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);

		int[] previous = new int[from.parts().size()];
		for (int i = 0; i < previous.length; i++) previous[i] = current.getLevel(enchantments.getOrThrow(from.parts().get(i).enchantment()));

		playChime(player, level, to);
		ServerPlayNetworking.send(player, new SwapResult(describe(to, restored, enchantments), to.color(),
				describe(from, previous, enchantments), from.color(), stack.copy(), player.getInventory().getSelectedSlot()));
	}

	/** The side's enchantments at these levels in its text color, e.g. "Fortune III". */
	private static MutableComponent describe(Side side, int[] levels, Registry<Enchantment> enchantments) {
		MutableComponent message = Component.empty();
		for (int i = 0; i < levels.length; i++) {
			if (levels[i] <= 0) continue;
			if (!message.getSiblings().isEmpty()) message.append(" + ");
			Holder<Enchantment> enchantment = enchantments.getOrThrow(side.parts().get(i).enchantment());
			message.append(enchantment.value().description());
			if (levels[i] > 1 || enchantment.value().getMaxLevel() > 1) {
				// Vanilla only has numerals up to X; higher levels from other mods fall back to plain digits.
				message.append(" ").append(Component.translatableWithFallback("enchantment.level." + levels[i], String.valueOf(levels[i])));
			}
		}
		return message.withStyle(side.textColor());
	}

	private static boolean hasAny(ItemEnchantments current, Side side, Registry<Enchantment> enchantments) {
		for (Part part : side.parts()) {
			if (current.getLevel(enchantments.getOrThrow(part.enchantment())) > 0) return true;
		}
		return false;
	}

	private static void fail(ServerPlayer player, MutableComponent message) {
		ServerPlayNetworking.send(player, new SwapResult(message.withStyle(ChatFormatting.RED), 0, Component.empty(), 0, ItemStack.EMPTY, -1));
	}

	private static void playChime(ServerPlayer player, ServerLevel level, Side to) {
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 1.5F, to.pitch());
	}
}
