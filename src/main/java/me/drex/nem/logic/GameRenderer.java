package me.drex.nem.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;

public class GameRenderer {
    public static HitResult pick(ServerPlayer player) {
        return pick(player, player.blockInteractionRange(), player.entityInteractionRange(), 1.0F);
    }

    private static HitResult pick(Entity entity, double blockInteractionRange, double entityInteractionRange, float tickDelta) {
        double maxRange = Math.max(blockInteractionRange, entityInteractionRange);
        double maxRangeSquare = Mth.square(maxRange);
        Vec3 eyePos = entity.getEyePosition(tickDelta);
        HitResult hitResult = entity.pick(maxRange, tickDelta, false);
        double distanceSquare = hitResult.getLocation().distanceToSqr(eyePos);
        if (hitResult.getType() != HitResult.Type.MISS) {
            maxRangeSquare = distanceSquare;
            maxRange = Math.sqrt(distanceSquare);
        }

        Vec3 viewVector = entity.getViewVector(tickDelta);
        Vec3 maxRangeVector = eyePos.add(viewVector.x * maxRange, viewVector.y * maxRange, viewVector.z * maxRange);
        AABB aABB = entity.getBoundingBox().expandTowards(viewVector.scale(maxRange)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, eyePos, maxRangeVector, aABB, EntitySelector.CAN_BE_PICKED, maxRangeSquare);
        return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(eyePos) < distanceSquare
            ? filterHitResult(entityHitResult, eyePos, entityInteractionRange)
            : filterHitResult(hitResult, eyePos, blockInteractionRange);
    }

    private static HitResult filterHitResult(HitResult hitResult, Vec3 eyePos, double interactionRange) {
        Vec3 location = hitResult.getLocation();
        if (!location.closerThan(eyePos, interactionRange)) {
            Direction direction = Direction.getApproximateNearest(location.x - eyePos.x, location.y - eyePos.y, location.z - eyePos.z);
            return BlockHitResult.miss(location, direction, BlockPos.containing(location));
        } else {
            return hitResult;
        }
    }
}
