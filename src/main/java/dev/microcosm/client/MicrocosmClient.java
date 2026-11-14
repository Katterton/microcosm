package dev.microcosm.client;

import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import dev.microcosm.world.MicrocosmLevels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import dev.microcosm.network.OutsideViewPayload;
import dev.microcosm.network.RecipeSyncPayload;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import dev.microcosm.network.ViewPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only setup. */
@Mod(value = Microcosm.MODID, dist = Dist.CLIENT)
public class MicrocosmClient {
    /** Items of another scale say so in their tooltip. */
    private static void addScaleTooltip(ItemTooltipEvent event) {
        Integer scale = event.getItemStack().get(ModRegistry.SCALE.get());
        if (scale != null) {
            event.getToolTip().add(1, Component.translatable("tooltip.microcosm.scale", MicrocosmLevels.scaleName(scale))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** A small unit prefix (m, \u00b5, n or B) in the corner of items of another scale. */
    private static boolean drawScaleBadge(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        Integer scale = stack.get(ModRegistry.SCALE.get());
        if (scale == null) {
            return false;
        }
        String badge = switch (scale) {
            case 1 -> "m";
            case 2 -> "\u00b5";
            case 3 -> "n";
            default -> "B";
        };
        graphics.text(font, badge, x, y, 0xFF5FE6FF, true);
        return true;
    }

    public MicrocosmClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, OutsideViewRenderer::onSubmit);
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, MicrocosmClient::addScaleTooltip);
        modBus.addListener(RegisterItemDecorationsEvent.class, e -> {
            for (Item item : BuiltInRegistries.ITEM) {
                e.register(item, MicrocosmClient::drawScaleBadge);
            }
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerBlockEntityRenderer(ModRegistry.CELL_BLOCK_ENTITY.get(), context -> new CellRenderer()));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(ViewPayload.TYPE, (payload, context) -> ViewerScreen.receive(payload));
            e.register(RecipeSyncPayload.TYPE, (payload, context) -> ClientRecipes.set(payload.recipes()));
            e.register(OutsideViewPayload.TYPE, (payload, context) -> OutsideViewRenderer.receive(payload));
        });
    }
}
