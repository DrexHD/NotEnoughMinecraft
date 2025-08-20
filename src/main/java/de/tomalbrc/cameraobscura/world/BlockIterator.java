package de.tomalbrc.cameraobscura.world;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BlockIterator {
    protected final ServerLevel level;
    private final LevelChunk[][] loadedChunks;
    private final LevelChunk fallbackChunk;
    private final int centerX;
    private final int centerZ;
    private final int chunkRadius;

    public record WorldHit(BlockPos blockPos, BlockState blockState, FluidState fluidState, FluidState fluidStateAbove) {
        public boolean isWaterOrWaterlogged() {
            if (fluidState != null && !fluidState.isEmpty()) {
                return fluidState.is(FluidTags.WATER);
            }
            return false;
        }
    }

    public BlockIterator(ServerLevel level, LivingEntity entity, int distance) {
        this.level = level;
        this.fallbackChunk = new EmptyLevelChunk(this.level, ChunkPos.ZERO, level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        BlockPos center = entity.getOnPos();
        centerX = SectionPos.blockToSectionCoord(center.getX());
        centerZ = SectionPos.blockToSectionCoord(center.getZ());

        // load chunks
        chunkRadius = distance / 16;
        this.loadedChunks = new LevelChunk[chunkRadius * 2 + 1][chunkRadius * 2 + 1];
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                this.loadedChunks[dx + chunkRadius][dz + chunkRadius] = level.getChunk(centerX + dx, centerZ + dz);
            }
        }
    }

    protected LevelChunk getChunkAt(ChunkPos pos) {
        int x = pos.x;
        int z = pos.z;
        int localX = x + chunkRadius - centerX;
        int localZ = z + chunkRadius - centerZ;
        if (localX < 0 || localX >= this.loadedChunks.length || localZ < 0 || localZ >= this.loadedChunks[0].length) {
            return this.fallbackChunk;
        }
        return loadedChunks[localX][localZ];
    }

    public LevelChunk getChunkAt(BlockPos blockPos) {
        return getChunkAt(new ChunkPos(blockPos));
    }

    public List<WorldHit> raycast(ClipContext clipContext) {
        List<WorldHit> list = new ObjectArrayList<>();

        LevelChunk cachedChunk = null;
        long cachedChunkKey = 0;
        boolean cached = false;

        Vec3 from = clipContext.getFrom();
        Vec3 to = clipContext.getTo();
        var blockIterator = new CustomBlockIterator(from.x, from.y, from.z, to.x, to.y, to.z);
        while (blockIterator.hasNext()) {
            int[] pos = blockIterator.next();
            int x = pos[0];
            int y = pos[1];
            int z = pos[2];
            BlockPos blockPos = new BlockPos(x, y, z);
            ChunkPos chunkPos = new ChunkPos(blockPos);
            long key = chunkPos.toLong();
            if (key != cachedChunkKey || !cached) {
                cachedChunkKey = key;

                cachedChunk = this.getChunkAt(chunkPos);
                cached = true;
            }

            BlockState blockState;
            FluidState fluidState;
            FluidState fluidStateAbove;
            if (cachedChunk != null) {
                blockState = cachedChunk.getBlockState(blockPos);
                fluidState = blockState.getFluidState();
                fluidStateAbove = null;
                if (!fluidState.isEmpty()) {
                    fluidStateAbove = cachedChunk.getFluidState(blockPos.above());
                }
            } else {
                blockState = Blocks.AIR.defaultBlockState();
                fluidState = Fluids.EMPTY.defaultFluidState();
                fluidStateAbove = Fluids.EMPTY.defaultFluidState();
            }


            if (!blockState.isSolidRender()) {
                if (!blockState.isAir()) {
                    // keep searching
                    list.add(new WorldHit(new BlockPos(blockPos), blockState, fluidState, fluidStateAbove));
                }

            } else if (!blockState.isAir()) {
                list.add(new WorldHit(new BlockPos(blockPos), blockState, fluidState, fluidStateAbove));
                break;
            }
        }

        if (list.isEmpty()) {
            BlockState blockState = Blocks.AIR.defaultBlockState();
            list.add(new WorldHit(BlockPos.containing(to), blockState, blockState.getFluidState(), blockState.getFluidState()));
        }
        return list;
    }

}
