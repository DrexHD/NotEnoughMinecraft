package de.tomalbrc.cameraobscura.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.cameraobscura.json.*;
import de.tomalbrc.cameraobscura.render.model.resource.RPBlockState;
import de.tomalbrc.cameraobscura.render.model.resource.RPElement;
import de.tomalbrc.cameraobscura.render.model.resource.RPModel;
import de.tomalbrc.cameraobscura.render.model.resource.state.MultipartDefinition;
import de.tomalbrc.cameraobscura.render.model.resource.state.Variant;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.drex.nem.block.model.ComputerModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import nl.theepicblock.resourcelocatorapi.ResourceLocatorApi;
import nl.theepicblock.resourcelocatorapi.api.AssetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import xyz.nucleoid.packettweaker.PacketContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RPHelper {
    private static final AssetContainer GLOBAL_ASSETS = ResourceLocatorApi.createGlobalAssetContainer();

    // Cache resourcepack models
    private static final Map<ResourceLocation, RPModel> modelResources = new ConcurrentHashMap<>();
    private static final Map<BlockState, RPBlockState> blockStateResources = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, BufferedImage> textureCache = new ConcurrentHashMap<>();

    private static FileSystem vanillaFilesystem;

    final public static Gson gson = new GsonBuilder()
        .registerTypeAdapter(ResourceLocation.class, new CachedResourceLocationDeserializer())
        .registerTypeAdapter(Variant.class, new VariantDeserializer())
        .registerTypeAdapter(MultipartDefinition.class, new MultipartDefinitionDeserializer())
        .registerTypeAdapter(MultipartDefinition.Condition.class, new ConditionDeserializer())
        .registerTypeAdapter(Vector3f.class, new Vector3fDeserializer())
        .registerTypeAdapter(Vector4f.class, new Vector4fDeserializer())
        .create();

    public static void clearCache() {
        modelResources.clear();
        blockStateResources.clear();
        textureCache.clear();
    }

    public static void init() {
        try {
            Path clientJar = PolymerCommonUtils.getClientJar();
            if (clientJar == null) {
                throw new RuntimeException("Could not get client jar");
            }
            vanillaFilesystem = FileSystems.newFileSystem(clientJar);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream getAsset(ResourceLocation location, String type, String extension) {
        IoSupplier<InputStream> supplier = GLOBAL_ASSETS.getAsset(location.getNamespace(), type + "/" + location.getPath() + extension);
        if (supplier == null) {
            try {
                Path path = vanillaFilesystem.getPath("/assets/" + location.getNamespace() + "/" + type + "/" + location.getPath() + extension);
                return Files.newInputStream(path);
            } catch (IOException e) {
                return null;
            }
        }
        try {
            return supplier.get();
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    public static RPBlockState loadBlockState(BlockState blockState) {
        if (blockStateResources.containsKey(blockState)) {
            return blockStateResources.get(blockState);
        }

        ResourceLocation location = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        InputStream inputStream = getAsset(location, "blockstates", ".json");

        if (inputStream != null) {
            var resource = gson.fromJson(new InputStreamReader(inputStream), RPBlockState.class);
            blockStateResources.put(blockState, resource);
            return resource;
        }
        return null;
    }

    public static RPModel.View loadModelView(ResourceLocation resourceLocation, Vector3fc blockRotation, boolean uvlock) {
        return new RPModel.View(loadModel(resourceLocation), blockRotation, uvlock);
    }

    public static RPModel loadModel(ResourceLocation resourceLocation) {
        if (modelResources.containsKey(resourceLocation)) {
            return modelResources.get(resourceLocation);
        }

        InputStream inputStream = getAsset(resourceLocation, "models", ".json");
        if (inputStream != null) {
            RPModel model = loadModel(inputStream);
            modelResources.put(resourceLocation, model);
            return model;
        }
        return null;
    }

    public static RPModel loadModel(InputStream inputStream) {
        RPModel model = gson.fromJson(new InputStreamReader(inputStream), RPModel.class);
        if (model.elements != null) {
            for (int i = 0; i < model.elements.size(); i++) {
                RPElement element = model.elements.get(i);
                for (Map.Entry<String, RPElement.TextureInfo> stringTextureInfoEntry : element.faces.entrySet()) {
                    if (stringTextureInfoEntry.getValue().uv == null) {
                        stringTextureInfoEntry.getValue().uv = new Vector4f(
                            element.from.x(),
                            element.from.y(),
                            element.to.x(),
                            element.to.y());
                    }
                }
            }
        }
        return model;
    }

    public static InputStream getTexture(ResourceLocation location) {
        return getAsset(location, "textures", ".png");
    }

    public static BufferedImage loadTextureImage(ResourceLocation path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }

        if (path.getNamespace().equals(Constants.DYNAMIC_PLAYER_TEXTURE)) {
            var img = imageFromBytes(getPlayerTexture(path.getPath()));
            textureCache.put(path, img);
            return img;
        } else if (path.getNamespace().equals(Constants.DYNAMIC_DISPLAY_TEXTURE)) {
            return getDisplayTexture(path.getPath());
        }

        try {
            InputStream inputStream = getTexture(path);
            BufferedImage img = imageFromBytes(inputStream);
            textureCache.put(path, img);
            return img;
        } catch (Exception e) {
            // TODO figure out why this NPEs
            return null;
        }


    }

    private static BufferedImage imageFromBytes(InputStream inputStream) {
        BufferedImage img = null;
        try {
            img = ImageIO.read(inputStream);
            if (img.getType() == 10) {
                img = TextureHelper.darkenGrayscale(img);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return img;
    }

    @NotNull
    private static InputStream getPlayerTexture(String uuid) {
        InputStreamReader inputStreamReader = null;
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            inputStreamReader = new InputStreamReader(url.openStream());

            JsonObject textureProperty = new JsonParser().parse(inputStreamReader).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            String texture = textureProperty.get("value").getAsString();
            var newJson = new String(Base64.getDecoder().decode(texture));

            var str = new JsonParser().parse(newJson).getAsJsonObject().get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString();

            URL textureUrl = new URL(str);

            return textureUrl.openStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage getDisplayTexture(String posString) {
        String[] split = posString.split("/");
        BlockPos blockPos = new BlockPos(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
        return ComputerModel.renderedFrames.get(blockPos);
    }

    public static List<RPModel.View> loadModel(RPBlockState rpBlockState, BlockState blockState) {
        if (rpBlockState != null && rpBlockState.variants != null) {
            for (Map.Entry<String, Variant> entry : rpBlockState.variants.entrySet()) {
                boolean matches = true;
                if (!entry.getKey().isEmpty()) {
                    try {
                        String str = String.format("%s[%s]", BuiltInRegistries.BLOCK.getKey(blockState.getBlock()), entry.getKey());
                        BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, str, false);

                        for (Map.Entry<Property<?>, Comparable<?>> propertyComparableEntry : blockResult.properties().entrySet()) {
                            if (!blockState.getValue(propertyComparableEntry.getKey()).equals(propertyComparableEntry.getValue())) {
                                matches = false;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }

                if (entry.getKey().isEmpty() || matches) {
                    var model = RPHelper.loadModelView(entry.getValue().model, new Vector3f(entry.getValue().x, entry.getValue().y, entry.getValue().z), entry.getValue().uvlock);
                    return ObjectArrayList.of(model);
                }
            }
        } else if (rpBlockState != null && rpBlockState.multipart != null) {
            ObjectArrayList<RPModel.View> list = new ObjectArrayList<>();

            int num = rpBlockState.multipart.size();
            for (int i = 0; i < num; i++) {
                MultipartDefinition mp = rpBlockState.multipart.get(i);

                if (mp.when == null || mp.when.canApply(blockState)) {
                    for (int applyIndex = 0; applyIndex < mp.apply.size(); applyIndex++) {
                        var apply = mp.apply.get(applyIndex);
                        var model = RPHelper.loadModelView(apply.model, new Vector3f(apply.x, apply.y, apply.z), apply.uvlock);
                        list.add(model);
                    }
                }
            }
            return list;
        }

        return null;
    }

    private static final Map<BlockState, List<RPModel.View>> blockModelCache = new ConcurrentHashMap<>();

    // returning a list for multipart blocks like multiple vines/lichen in a block
    public static List<RPModel.View> loadBlockModelViews(BlockState blockState) {
        if (blockModelCache.containsKey(blockState)) {
            return blockModelCache.get(blockState);
        }
        RPBlockState rpBlockState = RPHelper.loadBlockState(blockState);
        if (rpBlockState != null) {
            List<RPModel.View> views = loadModel(rpBlockState, blockState);
            blockModelCache.put(blockState, views);
            return views;
        }

        return null;
    }

    public static RPModel loadItemModel(ItemStack itemStack) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return loadModel(resourceLocation.withPath("item/" + resourceLocation.getPath()));
    }

    private static BlockState safePolymerBlockState(BlockState blockState) {
        if (blockState.getBlock() instanceof PolymerBlock polymerBlock) {
            blockState = polymerBlock.getPolymerBlockState(blockState, PacketContext.get());
        }
        return blockState;
    }
}
