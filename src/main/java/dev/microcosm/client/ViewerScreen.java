package dev.microcosm.client;

import dev.microcosm.block.CellBlock;
import dev.microcosm.device.DeviceAction;
import dev.microcosm.network.ViewPayload;
import dev.microcosm.network.ViewerRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Looks into a room one 16x16 layer at a time (north is up). Left click runs the selected action on a block, right
 * click on a cell zooms into its room, the mouse wheel changes the layer.
 *
 * <p>Tier 1 shows live colour icons, tier 2 a grayscale image refreshed every few seconds, tier 3 only the last
 * scan.</p>
 */
public class ViewerScreen extends Screen {
    private static final int CELL = 12;
    private static final int GRID = CELL * 16;
    private static final int PANEL = 140;
    private static final int SLOT = 18;

    private ViewPayload view;
    private int layer;
    private DeviceAction.Type mode = DeviceAction.Type.PLACE;
    private int slot;
    private int left;
    private int top;
    private final List<Button> modeButtons = new ArrayList<>();
    private @Nullable Button backButton;
    private @Nullable Button scanButton;

    public ViewerScreen(ViewPayload view) {
        super(Component.translatable("screen.microcosm.viewer"));
        this.view = view;
    }

    /** Called for every view update from the server. */
    public static void receive(ViewPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ViewerScreen screen && screen.view.viewerPos().equals(payload.viewerPos())) {
            boolean pathChanged = screen.view.pathLength() != payload.pathLength() || screen.view.depth() != payload.depth();
            screen.view = payload;
            if (pathChanged) {
                screen.rebuildWidgets();
            }
        } else {
            minecraft.setScreen(new ViewerScreen(payload));
        }
    }

    private static void send(ViewerRequestPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    protected void init() {
        left = (width - GRID - PANEL - 8) / 2;
        top = (height - GRID) / 2 + 6;
        int px = left + GRID + 8;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> setLayer(layer + 1))
                .bounds(px, top, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> setLayer(layer - 1))
                .bounds(px + 22, top, 20, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.translatable("screen.microcosm.zoom_out"),
                b -> send(ViewerRequestPayload.simple(ViewerRequestPayload.Op.ZOOM_OUT))).bounds(px + 44, top, PANEL - 44, 20).build());
        modeButtons.clear();
        int y = top + 24;
        for (DeviceAction.Type type : DeviceAction.Type.values()) {
            int i = type.ordinal();
            modeButtons.add(addRenderableWidget(Button.builder(modeLabel(type), b -> {
                mode = type;
                updateModeButtons();
            }).bounds(px + i * 47, y, 45, 20).build()));
        }
        scanButton = addRenderableWidget(Button.builder(Component.translatable("screen.microcosm.scan"),
                b -> send(ViewerRequestPayload.simple(ViewerRequestPayload.Op.SCAN))).bounds(px, top + GRID - 20, PANEL, 20).build());
        updateModeButtons();
    }

    private void updateModeButtons() {
        for (DeviceAction.Type type : DeviceAction.Type.values()) {
            Button button = modeButtons.get(type.ordinal());
            button.active = type != mode;
        }
        if (backButton != null) {
            backButton.active = view.pathLength() > 1;
        }
        if (scanButton != null) {
            scanButton.visible = view.viewerTier() >= 3;
        }
    }

    private static Component modeLabel(DeviceAction.Type type) {
        return Component.translatable("screen.microcosm.mode." + type.name().toLowerCase());
    }

    private void setLayer(int value) {
        layer = Math.max(0, Math.min(15, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        send(ViewerRequestPayload.simple(ViewerRequestPayload.Op.CLOSE));
        super.removed();
    }

    // ---------------------------------------------------------------- input

    private @Nullable BlockPos cellAt(double mouseX, double mouseY) {
        int x = (int) Math.floor((mouseX - left) / CELL);
        int z = (int) Math.floor((mouseY - top) / CELL);
        return x >= 0 && x < 16 && z >= 0 && z < 16 ? new BlockPos(x, layer, z) : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        BlockPos local = cellAt(event.x(), event.y());
        if (local != null) {
            if (event.button() == 1) {
                send(new ViewerRequestPayload(ViewerRequestPayload.Op.ZOOM_IN, local, 0, 0));
            } else if (event.button() == 0) {
                send(new ViewerRequestPayload(ViewerRequestPayload.Op.ACTION, local, mode.ordinal(), slot));
            }
            return true;
        }
        int slotIndex = slotAt(event.x(), event.y());
        if (slotIndex >= 0) {
            slot = slotIndex;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            setLayer(layer + (scrollY > 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private int slotsTop() {
        return top + 50;
    }

    private int slotAt(double mouseX, double mouseY) {
        int px = left + GRID + 8;
        int col = (int) Math.floor((mouseX - px) / SLOT);
        int row = (int) Math.floor((mouseY - slotsTop()) / SLOT);
        int index = row * 3 + col;
        return col >= 0 && col < 3 && row >= 0 && row < 3 && index < view.items().size() ? index : -1;
    }

    // ---------------------------------------------------------------- rendering

    private BlockState state(int x, int y, int z) {
        return Block.stateById(view.states()[(y * 16 + z) * 16 + x]);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        updateModeButtons();
        int titleColor = 0xFFFFFFFF;
        graphics.text(font, Component.translatable("screen.microcosm.header",
                Component.translatable("screen.microcosm.tier." + view.viewerTier()), view.depth(), layer), left, top - 12, titleColor);
        graphics.fill(left - 1, top - 1, left + GRID + 1, top + GRID + 1, 0xFF4A6070);
        graphics.fill(left, top, left + GRID, top + GRID, 0xFF0B1418);
        if (view.hasRoom()) {
            drawLayer(graphics);
        } else {
            Component message = Component.translatable(view.viewerTier() >= 3
                    ? (view.scanning() ? "screen.microcosm.scanning" : "screen.microcosm.no_scan")
                    : "screen.microcosm.no_image");
            graphics.centeredText(font, message, left + GRID / 2, top + GRID / 2 - 4, 0xFF9FB4C0);
        }
        drawPanel(graphics);
        BlockPos hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.outline(left + hovered.getX() * CELL, top + hovered.getZ() * CELL, CELL, CELL, 0xFFFFFFFF);
            if (view.hasRoom()) {
                List<Component> lines = new ArrayList<>();
                BlockState state = state(hovered.getX(), hovered.getY(), hovered.getZ());
                lines.add(state.isAir() ? Component.translatable("screen.microcosm.air") : state.getBlock().getName());
                lines.add(Component.literal(hovered.getX() + " " + hovered.getY() + " " + hovered.getZ()).withColor(0xFF808080));
                if (state.getBlock() instanceof CellBlock) {
                    lines.add(Component.translatable("screen.microcosm.zoom_hint").withColor(0xFF70D0E0));
                }
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    private void drawLayer(GuiGraphicsExtractor graphics) {
        boolean color = view.viewerTier() == 1;
        boolean blind = view.viewerTier() >= 3;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int sx = left + x * CELL;
                int sy = top + z * CELL;
                BlockState state = state(x, layer, z);
                if (state.isAir()) {
                    if (layer > 0) {
                        BlockState below = state(x, layer - 1, z);
                        if (!below.isAir()) {
                            graphics.fill(sx, sy, sx + CELL, sy + CELL, 0x70000000 | tint(below, color, blind));
                        }
                    }
                    continue;
                }
                ItemStack icon = new ItemStack(state.getBlock().asItem());
                if (color && !icon.isEmpty()) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(sx, sy);
                    graphics.pose().scale(CELL / 16f, CELL / 16f);
                    graphics.fakeItem(icon, 0, 0);
                    graphics.pose().popMatrix();
                } else {
                    graphics.fill(sx, sy, sx + CELL, sy + CELL, 0xFF000000 | tint(state, color, blind));
                    graphics.fill(sx, sy + CELL - 1, sx + CELL, sy + CELL, 0x30000000);
                }
            }
        }
    }

    /** Map colour, grayscale for tier 2, cyan-tinted grayscale for tier 3 scans. */
    private static int tint(BlockState state, boolean color, boolean blind) {
        MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        int rgb = mapColor == MapColor.NONE ? 0x808080 : mapColor.col;
        if (color) {
            return rgb;
        }
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        int l = (r * 30 + g * 59 + b * 11) / 100;
        l = 60 + l * 195 / 255;
        return blind ? (l * 6 / 10) << 16 | (l * 9 / 10) << 8 | l : l << 16 | l << 8 | l;
    }

    private void drawPanel(GuiGraphicsExtractor graphics) {
        int px = left + GRID + 8;
        int y = slotsTop();
        if (view.actuator() == 0) {
            graphics.text(font, Component.translatable("screen.microcosm.no_actuator"), px, y, 0xFFE0A050);
            y += 12;
        } else {
            for (int i = 0; i < view.items().size(); i++) {
                int sx = px + (i % 3) * SLOT;
                int sy = y + (i / 3) * SLOT;
                graphics.fill(sx, sy, sx + SLOT - 2, sy + SLOT - 2, 0xFF2A3640);
                graphics.item(view.items().get(i), sx, sy);
                graphics.itemDecorations(font, view.items().get(i), sx, sy);
                if (i == slot) {
                    graphics.outline(sx - 1, sy - 1, SLOT, SLOT, 0xFFFFFFFF);
                }
            }
            int tx = px + 3 * SLOT + 4;
            graphics.text(font, Component.translatable("screen.microcosm.tier." + view.actuator() + ".actuator"), tx, y, 0xFFB0C8D0);
            graphics.text(font, Component.translatable("screen.microcosm.queue", view.queue()), tx, y + 12, 0xFFB0C8D0);
            if (!view.lastResult().isEmpty()) {
                graphics.text(font, Component.translatable("message.microcosm.result." + view.lastResult()), tx, y + 24, 0xFF90A0A8);
            }
            y += 3 * SLOT + 4;
        }
        if (view.capacity() > 0) {
            graphics.text(font, Component.translatable("screen.microcosm.energy", view.energy(), view.capacity()), px, y, 0xFFE0D060);
            y += 12;
        }
        if (view.actuatorCapacity() > 0 && view.viewerTier() < 3) {
            graphics.text(font, Component.translatable("screen.microcosm.actuator_energy", view.actuatorEnergy(), view.actuatorCapacity()), px, y, 0xFFE0D060);
            y += 12;
        }
        if (view.viewerTier() >= 3 && view.hasRoom()) {
            graphics.text(font, Component.translatable("screen.microcosm.scan_age", view.dataAge() / 20), px, y, 0xFF90A0A8);
            y += 12;
        }
        if (!view.status().isEmpty()) {
            graphics.text(font, Component.translatable("message.microcosm.status." + view.status()), px, y, 0xFFFF9070);
        }
        graphics.text(font, Component.translatable("screen.microcosm.help"), left, top + GRID + 4, 0xFF708890);
    }
}
