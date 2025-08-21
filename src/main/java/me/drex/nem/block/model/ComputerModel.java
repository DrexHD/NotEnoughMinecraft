package me.drex.nem.block.model;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.virtualentity.api.tracker.DataTrackerLike;
import eu.pb4.polymer.virtualentity.api.tracker.SimpleDataTracker;
import me.drex.nem.config.ModConfig;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
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
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
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

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ComputerModel extends BlockModel {

    public static final int DISPLAY_WIDTH = 160;
    public static final int DISPLAY_HEIGHT = 144;

    public static final Map<UUID, ComputerModel> controlledComputers = new HashMap<>();
    // super cursed (doesn't take world into account, but it works)
    public static final Map<BlockPos, BufferedImage> renderedFrames = new HashMap<>();

    private final ItemStack screen;
    private final ItemDisplayElement display;
    public final InteractionElement interaction;
    public final TextDisplayElement camera;
    public final TextDisplayElement debug;
    private final SimpleEntityElement horse;
    private final ItemDisplayElement main;
    private final ItemDisplayElement[] hotbar = new ItemDisplayElement[9];
    private final DataTrackerLike displayDataTracker = new SimpleDataTracker(EntityType.ITEM_DISPLAY);

    private final AtomicBoolean rendering = new AtomicBoolean(false);
    private final BlockPos blockPos;

    // If the controllerUUID != null, fakePlayer needs to be != null
    private UUID controllerUUID;
    private FakePlayer fakePlayer;

    // debug
    private boolean showDebug = false;
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();

    public ComputerModel(BlockPos blockPos, BlockState blockState) {
        this.blockPos = blockPos;
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

        this.main = ItemDisplayElementUtil.createSimple(ModItems.COMPUTER);

        this.camera = new TextDisplayElement();
        this.debug = new TextDisplayElement();
        this.debug.setBackground(0);
        this.debug.setTextAlignment(Display.TextDisplay.Align.LEFT);

        this.clearScreen();

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
                if (player.getUUID() == ComputerModel.this.controllerUUID) {
                    PlayerAction.PICK_ITEM.perform(fakePlayer);
                }
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

        Matrix4f debugTransformation = new Matrix4f();
        debugTransformation.rotate(rotation);
        debugTransformation.translate(-0.26f, 3f / 16f, 5.7f / 16);
        debugTransformation.scale(.2f);
        this.debug.setTransformation(debugTransformation);

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
        controller.connection.send(VirtualEntityUtils.createRidePacket(horse.getEntityId(), new int[]{}));
        controller.connection.teleport(PositionMoveRotation.of(controller), EnumSet.noneOf(Relative.class));
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
        updateFps();
        renderHotbar(fakePlayer);
        setFakePlayerInput(fakePlayer, input);

        updateBlockState(this.blockState());
    }

    @Override
    protected void onAttachmentRemoved(HolderAttachment oldAttachment) {
        super.onAttachmentRemoved(oldAttachment);
        if (this.controllerUUID != null) {
            ServerPlayer controller = fakePlayer.getServer().getPlayerList().getPlayer(this.controllerUUID);
            if (controller != null) {
                removeCamera(controller);
            }
            removeController(fakePlayer);
        }
    }

    private void setController(ServerPlayer controller) {
        UUID uuid = controller.getUUID();
        UUID fakeUUID = new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits() + 1);
        String fakeName = "." + controller.getScoreboardName();
        if (fakeName.length() > 16) fakeName = fakeName.substring(0, 16);
        var fakeProfile = new GameProfile(fakeUUID, fakeName);
        this.fakePlayer = new ComputerFakePlayer(controller.level(), fakeProfile);
        MinecraftServer server = controller.getServer();
        CommandSourceStack source = server.createCommandSourceStack()
            .withSuppressedOutput()
            .withEntity(fakePlayer);
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), fakePlayer, new CommonListenerCookie(fakePlayer.getGameProfile(), 0, fakePlayer.clientInformation(), false));
        fakePlayer.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        for (String spawnCommand : ModConfig.getInstance().spawnCommands) {
            server.getCommands().performPrefixedCommand(source, spawnCommand);
        }

        this.addElement(interaction);

        this.controllerUUID = controller.getUUID();
        controlledComputers.put(this.controllerUUID, this);
    }

    private void removeController(FakePlayer fakePlayer) {
        fakePlayer.connection.onDisconnect(new DisconnectionDetails(Component.literal("Remove Fake Player")));
        controlledComputers.remove(this.controllerUUID);
        renderedFrames.remove(blockPos);
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

    public void onInventoryOpen() {
        if (showDebug) {
            removeElement(debug);
        } else {
            addElement(debug);
        }
        showDebug = !showDebug;
    }

    public void onPlayerAction(ServerboundPlayerActionPacket.Action action) {
        switch (action) {
            case DROP_ITEM -> PlayerAction.DROP.perform(fakePlayer);
            case DROP_ALL_ITEMS -> PlayerAction.DROP_ALL.perform(fakePlayer);
        }
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
        var renderer = new ImageRenderer(fakePlayer, DISPLAY_WIDTH, DISPLAY_HEIGHT, ModConfig.getInstance().renderDistance);
        CompletableFuture.supplyAsync(renderer::render).thenAcceptAsync(pixels -> {
            try {
                if (controllerUUID == null) return;
                updateScreen(pixels, false);
                frameTimes.addLast(System.currentTimeMillis());
                saveFrame(pixels);
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                rendering.set(false);
            }
        }, fakePlayer.getServer());
    }

    private void saveFrame(int[] pixels) {
        BlockPos blockPos = this.blockPos();

        int width = ComputerModel.DISPLAY_WIDTH;
        int height = ComputerModel.DISPLAY_HEIGHT;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] texture = new int[width * height];
        System.arraycopy(pixels, 0, texture, 0, texture.length);

        // flip
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            int rowEnd = rowStart + width - 1;
            for (int x = 0; x < width; x++) {
                texture[rowStart + x] = pixels[rowEnd - x];
            }
        }

        image.setRGB(0, 0, width, height, texture, 0, width);
        renderedFrames.put(blockPos, image);
    }

    private void updateFps() {
        long now = System.currentTimeMillis();

        long cutoff = now - 1_000L;
        while (!frameTimes.isEmpty() && frameTimes.getFirst() < cutoff) {
            frameTimes.removeFirst();
        }
        debug.setText(Component.literal("FPS: " + frameTimes.size()));
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
        updateScreen(new int[DISPLAY_WIDTH * DISPLAY_HEIGHT], true);
        for (int i = 0; i < 9; i++) {
            this.hotbar[i].setItem(ItemStack.EMPTY);
        }
        this.debug.setText(Component.literal(""));
    }

    private void updateScreen(int[] pixels, boolean forceUpdate) {
        CustomModelData customModelData = new CustomModelData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), IntArrayList.wrap(pixels));
        this.screen.set(DataComponents.CUSTOM_MODEL_DATA, customModelData);
        displayDataTracker.set(DisplayTrackedData.Item.ITEM, this.screen, true);
        for (ServerGamePacketListenerImpl watchingPlayer : this.getWatchingPlayers()) {
            if (forceUpdate || watchingPlayer.player.distanceToSqr(blockPos.getBottomCenter()) <= 6 * 6) {
                watchingPlayer.send(new ClientboundSetEntityDataPacket(display.getEntityId(), displayDataTracker.getChangedEntries()));
            }
        }
    }
}
