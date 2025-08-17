package de.tomalbrc.cameraobscura.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Map;

abstract public class AbstractWorldIterator<T> {
    protected final ServerLevel level;

    private final Map<Long, LevelChunk> cachedChunks;

    public AbstractWorldIterator(ServerLevel level, Map<Long, LevelChunk> cachedChunks) {
        this.level = level;
        this.cachedChunks = cachedChunks;
    }

    public void preloadChunks(BlockPos center, int distance) {
        int xc = SectionPos.blockToSectionCoord(center.getX());
        int zc = SectionPos.blockToSectionCoord(center.getZ());

        int radius = distance / 16 + 1;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                this.cachedChunks.put(ChunkPos.asLong(x + xc, z + zc), this.level.getChunk(x + xc, z + zc));
            }
        }
    }

    protected LevelChunk getChunkAt(ChunkPos pos) {
        return this.cachedChunks.computeIfAbsent(pos.toLong(), k -> new EmptyLevelChunk(this.level, new ChunkPos(pos.x, pos.z), level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)));
    }

    public LevelChunk getChunkAt(BlockPos blockPos) {
        return this.getChunkAt(new ChunkPos(blockPos));
    }

    abstract public List<T> raycast(ClipContext clipContext);
}
