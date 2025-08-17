package me.drex.nem.mixin;

import io.netty.channel.Channel;
import me.drex.nem.duck.IConnection;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements IConnection {
    @Override
    @Accessor
    public abstract void setChannel(Channel channel);
}
