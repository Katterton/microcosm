package dev.microcosm;

import dev.microcosm.block.CellBlock;
import dev.microcosm.block.CellBlockEntity;
import dev.microcosm.block.PortBlock;
import dev.microcosm.device.DeviceBlock;
import dev.microcosm.device.DeviceBlockEntity;
import dev.microcosm.device.DeviceKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Microcosm.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Microcosm.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Microcosm.MODID);

    /** A block that holds a 16x16x16 room one scale level down. */
    public static final DeferredBlock<CellBlock> CELL = BLOCKS.registerBlock("cell", CellBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(3.5f, 1200f).pushReaction(PushReaction.BLOCK));

    /** Unbreakable boundary between a room and empty space. Not obtainable. */
    public static final DeferredBlock<Block> CELL_WALL = BLOCKS.registerSimpleBlock("cell_wall",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).noLootTable()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, pos, type) -> false));

    /** Connects a room side to the matching side of its cell (capabilities and redstone). */
    public static final DeferredBlock<PortBlock> PORT = BLOCKS.registerBlock("port", PortBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6f).pushReaction(PushReaction.BLOCK));

    public static final DeferredItem<BlockItem> CELL_ITEM = ITEMS.registerSimpleBlockItem("cell", CELL);
    public static final DeferredItem<BlockItem> PORT_ITEM = ITEMS.registerSimpleBlockItem("port", PORT);

    /** The room access devices, one block and item per kind. */
    public static final Map<DeviceKind, DeferredBlock<DeviceBlock>> DEVICES = new EnumMap<>(DeviceKind.class);
    public static final Map<DeviceKind, DeferredItem<BlockItem>> DEVICE_ITEMS = new EnumMap<>(DeviceKind.class);

    static {
        for (DeviceKind kind : DeviceKind.values()) {
            DeferredBlock<DeviceBlock> block = BLOCKS.registerBlock(kind.id, p -> new DeviceBlock(p, kind),
                    p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops());
            DEVICES.put(kind, block);
            DEVICE_ITEMS.put(kind, ITEMS.registerSimpleBlockItem(kind.id, block));
        }
    }

    /** Example fabrication tool used by local recipes. */
    public static final DeferredItem<Item> MICRO_SOLDERING_IRON = ITEMS.registerItem("micro_soldering_iron", Item::new,
            p -> p.durability(128));

    /** Example fabrication product. */
    public static final DeferredItem<Item> LOGIC_CHIP = ITEMS.registerSimpleItem("logic_chip");
    /** Fabricated on scale level 1; needed for tier 2 devices. */
    public static final DeferredItem<Item> NANO_LENS = ITEMS.registerSimpleItem("nano_lens");
    /** Fabricated on scale level 2; needed for the tier 3 device. */
    public static final DeferredItem<Item> QUANTUM_LATTICE = ITEMS.registerSimpleItem("quantum_lattice");

    public static final Supplier<BlockEntityType<CellBlockEntity>> CELL_BLOCK_ENTITY = BLOCK_ENTITIES.register("cell",
            () -> new BlockEntityType<>(CellBlockEntity::new, CELL.get()));

    public static final Supplier<BlockEntityType<DeviceBlockEntity>> DEVICE_BLOCK_ENTITY = BLOCK_ENTITIES.register("device",
            () -> new BlockEntityType<>(DeviceBlockEntity::new,
                    DEVICES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    private ModRegistry() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
