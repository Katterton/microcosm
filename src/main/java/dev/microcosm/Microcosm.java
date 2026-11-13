package dev.microcosm;

import dev.microcosm.block.CellBlock;
import dev.microcosm.command.MicrocosmCommands;
import dev.microcosm.fabrication.FabricationHandler;
import dev.microcosm.fabrication.FabricationRecipes;
import dev.microcosm.device.ViewerSessions;
import dev.microcosm.fabrication.RecipeDisplay;
import dev.microcosm.network.RecipeSyncPayload;
import dev.microcosm.network.ViewPayload;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.microcosm.network.ViewerRequestPayload;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.PortLinks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import dev.microcosm.world.RoomManager;
import dev.microcosm.world.ShiftManager;
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

    public Microcosm(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, MicrocosmConfig.SPEC);
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1")
                .playToClient(ViewPayload.TYPE, ViewPayload.STREAM_CODEC)
                .playToClient(RecipeSyncPayload.TYPE, RecipeSyncPayload.STREAM_CODEC)
                .playToServer(ViewerRequestPayload.TYPE, ViewerRequestPayload.STREAM_CODEC,
                        (payload, context) -> ViewerSessions.handle((ServerPlayer) context.player(), payload)));
        modBus.addListener(RegisterTicketControllersEvent.class, e -> e.register(RoomManager.TICKETS));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Microcosm::addToTabs);
        modBus.addListener(RegisterCapabilitiesEvent.class, Microcosm::registerCapabilities);

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> MicrocosmCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(AddServerReloadListenersEvent.class,
                e -> e.addListener(id("fabrication_recipes"), new FabricationRecipes()));
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, FabricationHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, Microcosm::onBreak);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, Microcosm::onPlace);
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, e -> {
            RecipeSyncPayload payload = new RecipeSyncPayload(RecipeDisplay.all(e.getPlayerList().getServer()));
            e.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            ViewerSessions.onServerTick(e.getServer());
            ShiftManager.onServerTick(e.getServer());
        });
    }

    /** Ports tunnel these capabilities; add more here to tunnel them too. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        tunnel(event, Capabilities.Item.BLOCK);
        tunnel(event, Capabilities.Fluid.BLOCK);
        tunnel(event, Capabilities.Energy.BLOCK);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRegistry.DEVICE_BLOCK_ENTITY.get(),
                (device, side) -> device.kind().hasInventory ? VanillaContainerWrapper.of(device) : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModRegistry.DEVICE_BLOCK_ENTITY.get(),
                (device, side) -> device.energy());
    }

    private static <T> void tunnel(RegisterCapabilitiesEvent event, BlockCapability<T, @Nullable Direction> capability) {
        event.registerBlock(capability, PortLinks.cellProvider(capability), ModRegistry.CELL.get());
        event.registerBlock(capability, PortLinks.portProvider(capability), ModRegistry.PORT.get());
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.CELL_ITEM.get());
            event.accept(ModRegistry.PORT_ITEM.get());
            ModRegistry.DEVICE_ITEMS.values().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.MICRO_SOLDERING_IRON.get());
            event.accept(ModRegistry.SIZE_SHIFTER.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModRegistry.LOGIC_CHIP.get());
            event.accept(ModRegistry.NANO_LENS.get());
            event.accept(ModRegistry.QUANTUM_LATTICE.get());
        }
    }

    /** Pack makers can forbid building rooms by hand; machines (fake players) are not affected. */
    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !(player instanceof FakePlayer)
                && event.getLevel() instanceof ServerLevel level && MicrocosmLevels.depth(level) > 0
                && !MicrocosmConfig.ALLOW_PLAYER_PLACEMENT_IN_ROOMS.getAsBoolean()) {
            event.setCanceled(true);
            player.sendOverlayMessage(Component.translatable("message.microcosm.no_hand_placement"));
        }
    }

    /** A cell whose room still contains blocks cannot be broken by players, so nothing inside is lost. */
    private static void onBreak(BreakBlockEvent event) {
        if (event.getState().getBlock() instanceof CellBlock && event.getLevel() instanceof ServerLevel level
                && (!RoomManager.isRoomEmpty(level, event.getPos()) || ShiftManager.hasPlayersInside(level, event.getPos()))) {
            event.setCanceled(true);
            event.getPlayer().sendOverlayMessage(Component.translatable("message.microcosm.room_not_empty"));
        }
    }
}
