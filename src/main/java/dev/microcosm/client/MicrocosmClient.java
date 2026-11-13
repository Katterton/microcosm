package dev.microcosm.client;

import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import dev.microcosm.network.RecipeSyncPayload;
import dev.microcosm.network.ViewPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only setup. */
@Mod(value = Microcosm.MODID, dist = Dist.CLIENT)
public class MicrocosmClient {
    public MicrocosmClient(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerBlockEntityRenderer(ModRegistry.CELL_BLOCK_ENTITY.get(), context -> new CellRenderer()));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(ViewPayload.TYPE, (payload, context) -> ViewerScreen.receive(payload));
            e.register(RecipeSyncPayload.TYPE, (payload, context) -> ClientRecipes.set(payload.recipes()));
        });
    }
}
