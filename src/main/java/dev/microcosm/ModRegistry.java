package dev.microcosm;

import dev.microcosm.block.CellBlock;
import dev.microcosm.block.CellBlockEntity;
import dev.microcosm.block.CellWallBlock;
import dev.microcosm.block.PortBlock;
import dev.microcosm.block.ScaleConverterBlock;
import dev.microcosm.block.ScaleConverterBlockEntity;
import dev.microcosm.device.DeviceBlock;
import dev.microcosm.device.DeviceBlockEntity;
import dev.microcosm.device.ActuatorDeviceBlock;
import dev.microcosm.device.DeviceKind;
import dev.microcosm.device.ViewerDeviceBlock;
import dev.microcosm.item.SizeShifterItem;
import net.minecraft.world.item.Rarity;
import java.util.EnumMap;
import java.util.Map;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
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
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Microcosm.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Microcosm.MODID);

    /** Absolute scale of an item that is not in a place of its own scale (see {@link dev.microcosm.world.ItemScales}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SCALE = COMPONENTS.registerComponentType(
            "scale", b -> b.persistent(Codec.intRange(0, 3)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * A block divided into microblocks: it holds a 16x16x16 room one scale level down. Not obtainable; ordinary
     * blocks become one when their interior is changed, and turn back when it is uniform again.
     */
    public static final DeferredBlock<CellBlock> CELL = BLOCKS.registerBlock("cell", CellBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(3.5f, 1200f).pushReaction(PushReaction.BLOCK)
                    .noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false));

    /** Unbreakable boundary between a room and empty space. Not obtainable. */
    public static final DeferredBlock<CellWallBlock> CELL_WALL = BLOCKS.registerBlock("cell_wall", CellWallBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).noLootTable()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, pos, type) -> false)
                    .noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false));

    /** Connects a room side to the matching side of its cell (capabilities and redstone). */
    public static final DeferredBlock<PortBlock> PORT = BLOCKS.registerBlock("port", PortBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6f).pushReaction(PushReaction.BLOCK));

    public static final DeferredItem<BlockItem> PORT_ITEM = ITEMS.registerSimpleBlockItem("port", PORT);

    /** The room access devices, one block and item per kind. */
    public static final Map<DeviceKind, DeferredBlock<DeviceBlock>> DEVICES = new EnumMap<>(DeviceKind.class);
    public static final Map<DeviceKind, DeferredItem<BlockItem>> DEVICE_ITEMS = new EnumMap<>(DeviceKind.class);

    static {
        for (DeviceKind kind : DeviceKind.values()) {
            DeferredBlock<DeviceBlock> block = kind.isViewer()
                    ? BLOCKS.registerBlock(kind.id, p -> new ViewerDeviceBlock(p, kind),
                            p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops().noOcclusion())
                    : BLOCKS.registerBlock(kind.id, p -> new ActuatorDeviceBlock(p, kind),
                            p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops().noOcclusion());
            DEVICES.put(kind, block);
            DEVICE_ITEMS.put(kind, ITEMS.registerSimpleBlockItem(kind.id, block));
        }
    }

    /** Converts material between scales (1 block = 4096 blocks of the next smaller scale). */
    public static final DeferredBlock<ScaleConverterBlock> SCALE_CONVERTER = BLOCKS.registerBlock("scale_converter",
            ScaleConverterBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> SCALE_CONVERTER_ITEM = ITEMS.registerSimpleBlockItem("scale_converter", SCALE_CONVERTER);

    /** Example fabrication tool used by local recipes. */
    public static final DeferredItem<Item> MICRO_SOLDERING_IRON = ITEMS.registerItem("micro_soldering_iron", Item::new,
            p -> p.durability(128));

    /** Late-game item to enter rooms in person. */
    public static final DeferredItem<SizeShifterItem> SIZE_SHIFTER = ITEMS.registerItem("size_shifter", SizeShifterItem::new,
            p -> p.durability(256).rarity(Rarity.EPIC));

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

    public static final Supplier<BlockEntityType<ScaleConverterBlockEntity>> SCALE_CONVERTER_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "scale_converter", () -> new BlockEntityType<>(ScaleConverterBlockEntity::new, SCALE_CONVERTER.get()));

    private ModRegistry() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
    }
}
