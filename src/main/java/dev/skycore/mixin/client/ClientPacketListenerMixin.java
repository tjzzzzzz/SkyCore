package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.skycore.core.dungeon.DungeonEvents;
import dev.skycore.core.module.dungeon.CroesusSolver;
import dev.skycore.core.module.dungeon.DungeonChestValue;
import dev.skycore.core.module.dungeon.MelodyMessage;
import dev.skycore.core.module.dungeon.TerminalSolvers;
import dev.skycore.core.module.mining.BreakResetFix;
import dev.skycore.core.module.mining.EndNodeHighlight;
import dev.skycore.core.module.mining.GhostVision;
import dev.skycore.core.module.mining.ScathaMining;
import dev.skycore.core.skyblock.ItemData;
import dev.skycore.core.skyblock.TabListCache;
import dev.skycore.core.stats.ServerStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

	@Unique
	private long skycore$lastGameTime = Long.MIN_VALUE;

	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void skycore$trackTps(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerStats.INSTANCE.onTimeSync(packet.gameTime());
		long time = packet.gameTime();
		if (skycore$lastGameTime != Long.MIN_VALUE && time > skycore$lastGameTime) {
			long delta = Math.min(time - skycore$lastGameTime, 20L);
			for (long i = 0; i < delta; i++) {
				DungeonEvents.INSTANCE.fireServerTick();
			}
		}
		skycore$lastGameTime = time;
	}

	@Inject(method = "handlePongResponse", at = @At("TAIL"))
	private void skycore$trackPing(ClientboundPongResponsePacket packet, CallbackInfo ci) {
		ServerStats.INSTANCE.onPong(packet.time());
	}

	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void skycore$inventory(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		var screen = Minecraft.getInstance().gui.screen();
		if (screen == null) {
			BreakResetFix.INSTANCE.onInventory(packet.getSlot(), packet.getItem());
			return;
		}
		dev.skycore.core.module.general.ItemProtection.INSTANCE.onSlotUpdate(packet.getItem(), false);
		if (!(screen instanceof AbstractContainerScreen<?> container)) return;
		String title = screen.getTitle().getString();
		AbstractContainerMenu menu = container.getMenu();
		int slotId = packet.getSlot();
		int containerSize = menu.slots.size() - 36;
		if (containerSize < 0) containerSize = menu.slots.size();
		boolean isContainerSlot = slotId >= 0 && slotId < containerSize;
		Slot slot = (slotId >= 0 && slotId < menu.slots.size()) ? menu.slots.get(slotId) : null;
		TerminalSolvers.INSTANCE.onSlotUpdate(title, slotId, packet.getItem(), slot, containerSize, isContainerSlot);
		DungeonChestValue.INSTANCE.onSlotUpdate(title, slotId, packet.getItem(), isContainerSlot);
		CroesusSolver.INSTANCE.onSlotUpdate(title, slotId, packet.getItem(), slot, isContainerSlot);
		MelodyMessage.INSTANCE.onSlotUpdate(title, menu);
	}

	@Inject(
		method = "handleParticleEvent",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void skycore$particle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (dev.skycore.core.module.general.NoRender.INSTANCE.shouldCancelParticle(packet)) {
			ci.cancel();
			return;
		}
		EndNodeHighlight.INSTANCE.onParticle(packet);
		DungeonEvents.INSTANCE.fireParticle(packet);
	}

	@SuppressWarnings("unchecked")
	@Inject(
		method = "handleSetEntityData",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V",
			shift = At.Shift.AFTER
		)
	)
	private void skycore$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci, @Local Entity ent) {
		if (ent instanceof ArmorStand) {
			for (SynchedEntityData.DataValue<?> entry : packet.packedItems()) {
				if (entry.serializer().equals(EntityDataSerializers.OPTIONAL_COMPONENT) && entry.value() != null) {
					((Optional<Component>) entry.value()).ifPresent(value -> {
						String plain = ItemData.INSTANCE.plain(value);
						ScathaMining.INSTANCE.onNamed(ent, value);
						DungeonEvents.INSTANCE.fireNamed(ent, value, plain);
					});
					break;
				}
			}
		}
		if (ent instanceof LivingEntity || ent instanceof ItemEntity) {
			GhostVision.INSTANCE.onEntity(ent);
			DungeonEvents.INSTANCE.fireUpdated(ent);
		}
	}

	@Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
	private void skycore$tabList(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
		TabListCache.INSTANCE.markDirty();
	}
}
