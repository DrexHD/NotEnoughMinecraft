package me.drex.nem.block.model;

import com.mojang.authlib.GameProfile;
import de.tomalbrc.cameraobscura.render.renderer.ImageRenderer;
import eu.pb4.factorytools.api.virtualentity.BlockModel;
import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import eu.pb4.polymer.virtualentity.api.tracker.EntityTrackedData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.drex.nem.NotEnoughMinecraft;
import me.drex.nem.block.ComputerBlock;
import me.drex.nem.item.ModItems;
import me.drex.nem.logic.ComputerFakePlayer;
import me.drex.nem.logic.FakeClientConnection;
import me.drex.nem.logic.PlayerAction;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ComputerModel extends BlockModel {

    private static final Item[] defaultHotbar = new Item[]{Items.GRASS_BLOCK, Items.DIRT, Items.STONE, Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_LOG, Items.GLASS, Items.OAK_DOOR, Items.OAK_STAIRS};

    private final ItemStack screen;
    private final ItemDisplayElement display;
    public final InteractionElement interaction;
    public final TextDisplayElement camera;
    private final SimpleEntityElement horse;
    private final ItemDisplayElement main;
    private final ItemDisplayElement[] hotbar = new ItemDisplayElement[9];

    public static final int DISPLAY_WIDTH = 160;
    public static final int DISPLAY_HEIGHT = 144;
    private final AtomicBoolean rendering = new AtomicBoolean(false);

    public static final Map<UUID, ComputerModel> controlledComputers = new HashMap<>();

    // If the controllerUUID != null, fakePlayer needs to be != null
    private UUID controllerUUID;
    private FakePlayer fakePlayer;

    public ComputerModel(BlockState blockState) {
        this.screen = new ItemStack(Items.TRIAL_KEY);
        this.screen.set(DataComponents.ITEM_MODEL, NotEnoughMinecraft.id("screen"));
        this.display = new ItemDisplayElement(this.screen);
        this.display.setBrightness(Brightness.FULL_BRIGHT);
        this.display.setViewRange(0.1f);

        for (int i = 0; i < 9; i++) {
            this.hotbar[i] = ItemDisplayElementUtil.createSimple(Items.STONE);
            this.hotbar[i].setItemDisplayContext(ItemDisplayContext.GUI);
            this.addElement(this.hotbar[i]);
        }

        this.clearScreen();

        this.main = ItemDisplayElementUtil.createSimple(ModItems.COMPUTER);

        this.camera = new TextDisplayElement();

        this.horse = new SimpleEntityElement(EntityType.HORSE);
        horse.setInvisible(true);
        horse.setOffset(new Vec3(0, 256, 0));
        this.addElement(horse);

        this.interaction = new InteractionElement(new VirtualElement.InteractionHandler() {
            @Override
            public void interact(ServerPlayer player, InteractionHand hand) {
                if (hand != InteractionHand.MAIN_HAND) return;

                if (player.getUUID() == ComputerModel.this.controllerUUID) {
                    PlayerAction.USE.perform(fakePlayer);
                }
            }

            @Override
            public void attack(ServerPlayer player) {
                if (player.getUUID() == ComputerModel.this.controllerUUID) {
                    PlayerAction.ATTACK.perform(fakePlayer);
                }
            }

            @Override
            public void pickItem(ServerPlayer player, boolean includeData) {
//                if (player.getUUID() == ComputerModel.this.controllerUUID) {
//                    fakePlayer.teleportTo(player.getX(), player.getY(), player.getZ());
//                }
            }
        });
        this.interaction.setSize(1.1f, 1.1f);
        this.interaction.setOffset(new Vec3(0, -0.5f, 0));

        this.addElement(this.main);
        this.addElement(this.display);
        this.addElement(this.camera);
        this.updateBlockState(blockState);
    }

    private void updateBlockState(BlockState blockState) {
        Direction dir = blockState.getValue(ComputerBlock.FACING);
        var yaw = 180 - Mth.wrapDegrees(dir.toYRot());

        Quaternionf rotation = new Quaternionf();
        rotation.rotateY((float) Math.toRadians(yaw));
        for (int i = 0; i < 9; i++) {
            Matrix4f hotbarTransformation = new Matrix4f();
            hotbarTransformation.rotate(rotation);
            hotbarTransformation.translate(i / 14f - 0.28f, -4 / 16f, 5.7f / 16);
            hotbarTransformation.scale(0.05f);
            this.hotbar[i].setTransformation(hotbarTransformation);
        }

        Matrix4f displayTransform = new Matrix4f();
        displayTransform.rotate(rotation);
        displayTransform.translate(0, -1 / 16f, 5.5f / 16);
        displayTransform.scale(.8f, .8f, 1f);
        this.display.setTransformation(displayTransform);

        Matrix4f mainTransform = new Matrix4f();
        mainTransform.rotate(rotation);
        this.main.setTransformation(mainTransform);

        Vector3f offset = new Vector3f(0, 0, 1).rotate(rotation);

        this.camera.setOffset(new Vec3(offset));
        this.camera.setRotation(0, 180 - yaw);
    }

    @Override
    public void notifyUpdate(HolderAttachment.UpdateType updateType) {
        if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE) {
            updateBlockState(this.blockState());
        }
    }

    public boolean interact(ServerPlayer player) {
        if (ComputerModel.this.controllerUUID == null) {
            setCamera(player);
            setController(player);
            return true;
        }
        return false;
    }

    private void setCamera(ServerPlayer controller) {
        controller.connection.send(VirtualEntityUtils.createSetCameraEntityPacket(camera.getEntityId()));
        controller.connection.send(VirtualEntityUtils.createRidePacket(horse.getEntityId(), new int[]{controller.getId()}));
        controller.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, GameType.SPECTATOR.getId()));
        controller.connection.send(new ClientboundMoveEntityPacket.Rot(controller.getId(), (byte) 0, (byte) 0, controller.onGround()));
        controller.connection.send(new ClientboundSetEntityDataPacket(controller.getId(), List.of(
            SynchedEntityData.DataValue.create(EntityTrackedData.POSE, Pose.STANDING)
        )));
        controller.connection.send(new ClientboundSetHeldSlotPacket(0));
    }

    private void removeCamera(ServerPlayer controller) {
        controller.connection.send(new ClientboundSetCameraPacket(controller));
        controller.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, controller.gameMode().getId()));
        controller.connection.send(new ClientboundPlayerPositionPacket(controller.getId(), PositionMoveRotation.of(controller), EnumSet.noneOf(Relative.class)));
        controller.connection.send(VirtualEntityUtils.createRidePacket(horse.getEntityId(), new int[]{}));
        controller.connection.send(new ClientboundPlayerRotationPacket(controller.getYRot(), controller.getXRot()));
        controller.connection.send(new ClientboundSetHeldSlotPacket(controller.getInventory().getSelectedSlot()));
    }

    @Override
    protected void onTick() {
        super.onTick();
        HolderAttachment attachment = getAttachment();
        if (this.controllerUUID == null || this.fakePlayer == null || attachment == null) return;

        ServerLevel level = attachment.getWorld();
        MinecraftServer server = level.getServer();
        ServerPlayer controller = server.getPlayerList().getPlayer(this.controllerUUID);
        if (controller == null || controller.level() != level) {
            removeController(fakePlayer);
            return;
        }

        if (controller.distanceToSqr(attachment.getPos()) >= 6 * 6) {
            removeCamera(controller);
            removeController(fakePlayer);
            return;
        }

        Input input = controller.getLastClientInput();
        if (input.shift()) {
            removeCamera(controller);
            removeController(fakePlayer);
            return;
        }

        renderImage(fakePlayer);
        renderHotbar(fakePlayer);
        setFakePlayerInput(fakePlayer, input);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (this.controllerUUID != null) {
            HolderAttachment attachment = getAttachment();
            if (attachment != null) {
                ServerPlayer controller = attachment.getWorld().getServer().getPlayerList().getPlayer(this.controllerUUID);
                if (controller != null) {
                    removeCamera(controller);
                }
            }
            removeController(fakePlayer);
        }
    }

    private void setController(ServerPlayer controller) {
        UUID uuid = controller.getUUID();
        UUID fakeUUID = new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits() + 1);
        var fakeProfile = new GameProfile(fakeUUID, "." + controller.getScoreboardName());
        this.fakePlayer = new ComputerFakePlayer(controller.level(), fakeProfile);
        controller.getServer().getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), fakePlayer, new CommonListenerCookie(fakePlayer.getGameProfile(), 0, fakePlayer.clientInformation(), false));
        fakePlayer.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        for (int i = 0; i < 9; i++) {
            fakePlayer.getInventory().setItem(i, new ItemStack(defaultHotbar[i]));
        }

        this.addElement(interaction);

        this.controllerUUID = controller.getUUID();
        controlledComputers.put(this.controllerUUID, this);
    }

    private void removeController(FakePlayer fakePlayer) {
        fakePlayer.connection.onDisconnect(new DisconnectionDetails(Component.literal("Remove Fake Player")));
        controlledComputers.remove(this.controllerUUID);
        this.fakePlayer = null;
        this.controllerUUID = null;
        this.removeElement(interaction);
        this.clearScreen();
    }

    public void onCameraMove(float yRot, float xRot) {
        float currentYRot = fakePlayer.getYHeadRot();
        float currentXRot = fakePlayer.getXRot();

        fakePlayer.setYHeadRot(yRot + currentYRot);
        fakePlayer.setXRot(xRot + currentXRot);
        fakePlayer.setYBodyRot(yRot + currentYRot);
        fakePlayer.setYRot(yRot + currentYRot);
    }

    public void setSelectedSlot(int selectedSlot) {
        fakePlayer.getInventory().setSelectedSlot(selectedSlot);
    }

    private void setFakePlayerInput(FakePlayer fakePlayer, Input input) {
        fakePlayer.setSprinting(input.sprint());
        if (input.jump()) {
            PlayerAction.JUMP.perform(fakePlayer);
        }

        float velocity = input.shift() ? 0.3F : 1.0F;
        fakePlayer.xxa = 0;
        fakePlayer.zza = 0;
        if (input.forward()) fakePlayer.zza += velocity;
        if (input.backward()) fakePlayer.zza -= velocity;
        if (input.left()) fakePlayer.xxa += velocity;
        if (input.right()) fakePlayer.xxa -= velocity;
    }

    private void renderImage(FakePlayer fakePlayer) {
        if (!rendering.compareAndSet(false, true)) return;
        var renderer = new ImageRenderer(fakePlayer, DISPLAY_WIDTH, DISPLAY_HEIGHT, 64);
        CompletableFuture.supplyAsync(renderer::render).thenAcceptAsync(pixels -> {
            try {
                if (controllerUUID == null) return;
                CustomModelData customModelData = new CustomModelData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), IntArrayList.wrap(pixels));
                this.screen.set(DataComponents.CUSTOM_MODEL_DATA, customModelData);
                this.display.setItem(this.screen);
                this.display.getDataTracker().setDirty(DisplayTrackedData.Item.ITEM, true);
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                rendering.set(false);
            }
        });
    }

    private void renderHotbar(FakePlayer fakePlayer) {
        Inventory inventory = fakePlayer.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) item = new ItemStack(Items.BARRIER);
            this.hotbar[i].setItem(item);
            this.hotbar[i].setGlowing(selectedSlot == i);
        }
    }

    private void clearScreen() {
        CustomModelData customModelData = new CustomModelData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), IntArrayList.wrap(new int[DISPLAY_WIDTH * DISPLAY_HEIGHT]));
        this.screen.set(DataComponents.CUSTOM_MODEL_DATA, customModelData);
        this.display.setItem(this.screen);
        this.display.getDataTracker().setDirty(DisplayTrackedData.Item.ITEM, true);
        for (int i = 0; i < 9; i++) {
            this.hotbar[i].setItem(ItemStack.EMPTY);
        }
    }
}
