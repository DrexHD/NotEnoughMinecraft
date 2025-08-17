package me.drex.nem.duck;

import io.netty.channel.Channel;

public interface IConnection {
    void setChannel(Channel channel);
}
