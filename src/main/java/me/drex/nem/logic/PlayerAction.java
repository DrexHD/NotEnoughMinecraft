package me.drex.nem.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public enum PlayerAction {
    ATTACK {
        @Override
        public void perform(ServerPlayer player) {
            HitResult pick = GameRenderer.pick(player);
            switch (pick.getType()) {
                case ENTITY -> {
                    // TODO
                }
                case BLOCK -> {
                    ServerLevel level = player.level();
                    BlockHitResult blockHitResult = (BlockHitResult) pick;
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    if (!level.getBlockState(blockPos).isAir()) {
                        player.gameMode.destroyBlock(blockPos);
                    }
                }
            }
        }
    },
    USE {
        @Override
        public void perform(ServerPlayer player) {
            HitResult pick = GameRenderer.pick(player);
            switch (pick.getType()) {
                case BLOCK -> {
                    player.resetLastActionTime();
                    ServerLevel level = player.level();
                    BlockHitResult blockHit = (BlockHitResult) pick;
                    BlockPos hitPos = blockHit.getBlockPos();
                    Direction direction = blockHit.getDirection();
                    if (hitPos.getY() < player.level().getMaxY() - (direction == Direction.UP ? 1 : 0) && level.mayInteract(player, hitPos)) {
                        InteractionResult result = player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, blockHit);
                        if (result instanceof InteractionResult.Success success) {
                            if (success.swingSource() == InteractionResult.SwingSource.SERVER) {
                                player.swing(InteractionHand.MAIN_HAND);
                            }
                        }
                    }
                }
            }
        }
    },
    JUMP {
        @Override
        public void perform(ServerPlayer player) {
            if (player.onGround()) player.jumpFromGround();
        }
    };

    PlayerAction() {

    }

    public abstract void perform(ServerPlayer player);
}
