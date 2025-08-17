package me.drex.nem.logic;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;


public class ComputerFakePlayer extends FakePlayer {
    public ComputerFakePlayer(ServerLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Override
    public void tick() {
        doTick();
    }
}
