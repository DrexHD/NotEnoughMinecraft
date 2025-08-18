package me.drex.nem.item;

import eu.pb4.factorytools.api.item.FactoryBlockItem;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import me.drex.nem.NotEnoughMinecraft;
import me.drex.nem.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

public class ModItems {

    public static final FactoryBlockItem COMPUTER = register(ModBlocks.COMPUTER);
    public static final ResourceKey<CreativeModeTab> ITEM_GROUP_KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), ResourceLocation.fromNamespaceAndPath(NotEnoughMinecraft.MOD_ID, "item_group"));

    public static final CreativeModeTab CUSTOM_ITEM_GROUP = FabricItemGroup.builder()
        .icon(() -> new ItemStack(COMPUTER))
        .title(Component.translatable("itemGroup.not-enough-minecraft"))
        .build();


    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ITEM_GROUP_KEY, CUSTOM_ITEM_GROUP);
        ItemGroupEvents.modifyEntriesEvent(ITEM_GROUP_KEY).register(entries -> {
            entries.accept(COMPUTER);
        });

    }

    public static <E extends Block & PolymerBlock> FactoryBlockItem register(E block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return register(ResourceKey.create(Registries.ITEM, id), properties -> new FactoryBlockItem(block, properties), new Item.Properties().useBlockDescriptionPrefix());
    }

    private static <T extends Item> T register(ResourceKey<Item> resourceKey, Function<Item.Properties, T> function, Item.Properties properties) {
        T item = function.apply(properties.setId(resourceKey));
        return Registry.register(BuiltInRegistries.ITEM, resourceKey, item);
    }

    private static <T extends Item> T register(String string, Function<Item.Properties, T> function, Item.Properties properties) {
        return register(itemId(string), function, properties);
    }

    private static ResourceKey<Item> itemId(String string) {
        return ResourceKey.create(Registries.ITEM, NotEnoughMinecraft.id(string));
    }
}
