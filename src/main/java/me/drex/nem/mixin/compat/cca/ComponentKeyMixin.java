package me.drex.nem.mixin.compat.cca;

import me.drex.nem.logic.ComputerFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.sync.ComponentPacketWriter;
import org.ladysnake.cca.api.v3.component.sync.PlayerSyncPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ComponentKey.class)
public abstract class ComponentKeyMixin {
    @Inject(
        method = "syncWith(Lnet/minecraft/server/level/ServerPlayer;Lorg/ladysnake/cca/api/v3/component/ComponentProvider;Lorg/ladysnake/cca/api/v3/component/sync/ComponentPacketWriter;Lorg/ladysnake/cca/api/v3/component/sync/PlayerSyncPredicate;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    public void preventFakePlayerSync(ServerPlayer player, ComponentProvider provider, ComponentPacketWriter writer, PlayerSyncPredicate predicate, CallbackInfo ci) {
        if (player instanceof ComputerFakePlayer) {
            ci.cancel();
        }
    }
}
