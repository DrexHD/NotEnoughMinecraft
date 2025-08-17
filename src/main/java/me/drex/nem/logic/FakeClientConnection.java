package me.drex.nem.logic;

import io.netty.channel.embedded.EmbeddedChannel;
import me.drex.nem.duck.IConnection;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

public class FakeClientConnection extends Connection {
    public FakeClientConnection(PacketFlow packetFlow) {
        super(packetFlow);
        ((IConnection) this).setChannel(new EmbeddedChannel());
    }
}
