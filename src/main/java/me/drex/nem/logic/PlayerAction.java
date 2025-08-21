package me.drex.nem.logic;

import me.drex.nem.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
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
                    if (!ModConfig.getInstance().allowBreaking) return;
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
                    if (!ModConfig.getInstance().allowPlacing) return;
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
    PICK_ITEM {
        @Override
        public void perform(ServerPlayer player) {
            HitResult pick = GameRenderer.pick(player);
            switch (pick.getType()) {
                case BLOCK -> {
                    if (!ModConfig.getInstance().allowPicking) return;
                    BlockHitResult blockHit = (BlockHitResult) pick;
                    BlockPos blockPos = blockHit.getBlockPos();
                    ServerLevel serverLevel = player.level();
                    if (player.canInteractWithBlock(blockPos, 1.0) && serverLevel.isLoaded(blockPos)) {
                        BlockState blockState = serverLevel.getBlockState(blockPos);
                        ItemStack itemStack = blockState.getCloneItemStack(serverLevel, blockPos, false);
                        if (!itemStack.isEmpty()) {
                            tryPickItem(itemStack, player);
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
    },
    DROP {
        @Override
        public void perform(ServerPlayer player) {
            if (!ModConfig.getInstance().allowDropping) return;
            player.drop(false);
        }
    },
    DROP_ALL {
        @Override
        public void perform(ServerPlayer player) {
            if (!ModConfig.getInstance().allowDropping) return;
            player.drop(true);
        }
    };

    PlayerAction() {

    }

    private static void tryPickItem(ItemStack itemStack, ServerPlayer player) {
        if (itemStack.isItemEnabled(player.level().enabledFeatures())) {
            Inventory inventory = player.getInventory();
            int i = inventory.findSlotMatchingItem(itemStack);
            if (i != -1) {
                if (Inventory.isHotbarSlot(i)) {
                    inventory.setSelectedSlot(i);
                } else {
                    inventory.pickSlot(i);
                }
            } else if (player.hasInfiniteMaterials()) {
                inventory.addAndPickItem(itemStack);
            }
        }
    }

    public abstract void perform(ServerPlayer player);
}
