package me.drex.nem.mixin;

import me.drex.nem.block.model.ComputerModel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin extends ServerCommonPacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    public ServerGamePacketListenerImplMixin(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraftServer, connection, commonListenerCookie);
    }

    @Inject(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    public void detectPlayerRotation(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ComputerModel computerModel = ComputerModel.controlledComputers.get(player.getUUID());
        if (computerModel != null) {
            float yRot = packet.getYRot(0);
            float xRot = packet.getXRot(0);

            if (xRot != 0 || yRot != 0) {
                this.send(new ClientboundPlayerRotationPacket(0, 0));
                computerModel.onCameraMove(yRot, xRot);
            }
            ci.cancel();
        }
    }

    @Inject(
        method = "handleSetCarriedItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"
        ),
        cancellable = true
    )
    public void detectHotbarScrolling(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        ComputerModel computerModel = ComputerModel.controlledComputers.get(player.getUUID());
        if (computerModel != null) {
            computerModel.setSelectedSlot(packet.getSlot());
            ci.cancel();
        }
    }

    @Inject(
        method = "handlePlayerCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getVehicle()Lnet/minecraft/world/entity/Entity;"
        ),
        cancellable = true
    )
    public void detectInventoryOpen(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        ComputerModel computerModel = ComputerModel.controlledComputers.get(player.getUUID());
        if (computerModel != null) {
            computerModel.onInventoryOpen();
            ci.cancel();
        }
    }

    @Inject(
        method = "handlePlayerAction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"
        ),
        cancellable = true
    )
    public void detectPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ComputerModel computerModel = ComputerModel.controlledComputers.get(player.getUUID());
        if (computerModel != null) {
            computerModel.onPlayerAction(packet.getAction());
            ci.cancel();
        }
    }
}