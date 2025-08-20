package me.drex.nem.block;

import me.drex.nem.NotEnoughMinecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;


public class ModBlocks {

    public static final ComputerBlock COMPUTER = register("computer", ComputerBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .noCollission()
            .pushReaction(PushReaction.BLOCK)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F)
    );

    public static void init() {
    }

    private static <T extends Block> T register(ResourceKey<Block> resourceKey, Function<BlockBehaviour.Properties, T> function, BlockBehaviour.Properties properties) {
        T block = function.apply(properties.setId(resourceKey));
        return Registry.register(BuiltInRegistries.BLOCK, resourceKey, block);
    }

    private static <T extends Block> T register(String string, Function<BlockBehaviour.Properties, T> function, BlockBehaviour.Properties properties) {
        return register(blockId(string), function, properties);
    }

    private static ResourceKey<Block> blockId(String string) {
        return ResourceKey.create(Registries.BLOCK, NotEnoughMinecraft.id(string));
    }
}
