package dev.microcosm;

import dev.microcosm.block.CellBlock;
import dev.microcosm.command.MicrocosmCommands;
import dev.microcosm.fabrication.FabricationHandler;
import dev.microcosm.fabrication.FabricationRecipes;
import dev.microcosm.world.PortLinks;
import dev.microcosm.world.RoomManager;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;

@Mod(Microcosm.MODID)
public class Microcosm {
    public static final String MODID = "microcosm";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public Microcosm(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(RegisterTicketControllersEvent.class, e -> e.register(RoomManager.TICKETS));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Microcosm::addToTabs);
        modBus.addListener(RegisterCapabilitiesEvent.class, Microcosm::registerCapabilities);

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> MicrocosmCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(AddServerReloadListenersEvent.class,
                e -> e.addListener(id("fabrication_recipes"), new FabricationRecipes()));
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, FabricationHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, Microcosm::onBreak);
    }

    /** Ports tunnel these capabilities; add more here to tunnel them too. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        tunnel(event, Capabilities.Item.BLOCK);
        tunnel(event, Capabilities.Fluid.BLOCK);
        tunnel(event, Capabilities.Energy.BLOCK);
    }

    private static <T> void tunnel(RegisterCapabilitiesEvent event, BlockCapability<T, @Nullable Direction> capability) {
        event.registerBlock(capability, PortLinks.cellProvider(capability), ModRegistry.CELL.get());
        event.registerBlock(capability, PortLinks.portProvider(capability), ModRegistry.PORT.get());
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.CELL_ITEM.get());
            event.accept(ModRegistry.PORT_ITEM.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.MICRO_SOLDERING_IRON.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModRegistry.LOGIC_CHIP.get());
        }
    }

    /** A cell whose room still contains blocks cannot be broken by players, so nothing inside is lost. */
    private static void onBreak(BreakBlockEvent event) {
        if (event.getState().getBlock() instanceof CellBlock && event.getLevel() instanceof ServerLevel level
                && !RoomManager.isRoomEmpty(level, event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().sendOverlayMessage(Component.translatable("message.microcosm.room_not_empty"));
        }
    }
}
