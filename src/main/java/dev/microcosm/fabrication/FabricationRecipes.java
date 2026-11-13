package dev.microcosm.fabrication;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Loads fabrication recipes from data packs; reloads with {@code /reload}. */
public final class FabricationRecipes extends SimplePreparableReloadListener<Map<Identifier, FabricationRecipe>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FileToIdConverter LISTER = FileToIdConverter.json("microcosm/fabrication");

    private static volatile List<Entry> recipes = List.of();


    /** A loaded recipe plus its compiled pattern in all four rotations (compiled lazily for structure recipes). */
    public static final class Entry {
        public final Identifier id;
        public final FabricationRecipe recipe;
        private Pattern @Nullable [] rotations;
        private boolean failed;

        Entry(Identifier id, FabricationRecipe recipe) {
            this.id = id;
            this.recipe = recipe;
        }

        /** The pattern in all four rotations, or null if it could not be compiled. */
        public Pattern @Nullable [] rotations(MinecraftServer server) {
            if (rotations == null && !failed) {
                try {
                    rotations = compile(recipe, server).rotations();
                } catch (RuntimeException e) {
                    failed = true;
                    LOGGER.error("Invalid fabrication recipe {}: {}", id, e.getMessage());
                }
            }
            return rotations;
        }
    }

    public static List<Entry> all() {
        return recipes;
    }

    @Override
    protected Map<Identifier, FabricationRecipe> prepare(ResourceManager manager, ProfilerFiller profiler) {
        DynamicOps<JsonElement> ops = getRegistryLookup().createSerializationContext(JsonOps.INSTANCE);
        Map<Identifier, FabricationRecipe> result = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, LISTER, ops, FabricationRecipe.CODEC, result);
        return result;
    }

    @Override
    protected void apply(Map<Identifier, FabricationRecipe> loaded, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> entries = new ArrayList<>();
        loaded.forEach((id, recipe) -> {
            if (recipe.mode() == FabricationRecipe.Mode.LOCAL && (recipe.tool().isEmpty() || recipe.pattern().isEmpty())) {
                LOGGER.error("Local fabrication recipe {} needs both 'pattern' and 'tool'", id);
            } else {
                entries.add(new Entry(id, recipe));
            }
        });
        recipes = List.copyOf(entries);
        LOGGER.info("Loaded {} fabrication recipes", entries.size());
    }

    static Pattern compile(FabricationRecipe recipe, @Nullable MinecraftServer server) {
        if (recipe.mode() == FabricationRecipe.Mode.LOCAL) {
            return fromLayers(recipe, recipe.pattern().orElseThrow(), null);
        }
        int size = dev.microcosm.world.RoomGeometry.ROOM;
        Pattern cube = new Pattern(size, size, size);
        Pattern.Cell fallback = cell(recipe, recipe.defaultCell());
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    cube.set(x, y, z, fallback);
                }
            }
        }
        recipe.pattern().ifPresent(layers -> fromLayers(recipe, layers, cube));
        for (FabricationRecipe.Fill fill : recipe.fill()) {
            Pattern.Cell cell = cell(recipe, fill.key());
            for (int y = Math.min(fill.from().getY(), fill.to().getY()); y <= Math.max(fill.from().getY(), fill.to().getY()); y++) {
                for (int z = Math.min(fill.from().getZ(), fill.to().getZ()); z <= Math.max(fill.from().getZ(), fill.to().getZ()); z++) {
                    for (int x = Math.min(fill.from().getX(), fill.to().getX()); x <= Math.max(fill.from().getX(), fill.to().getX()); x++) {
                        checkBounds(x, y, z, size);
                        cube.set(x, y, z, cell);
                    }
                }
            }
        }
        if (recipe.structure().isPresent()) {
            if (server == null) {
                throw new IllegalStateException("structure recipes need a running server");
            }
            Identifier structureId = recipe.structure().get();
            StructureTemplate template = server.getStructureManager().get(structureId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown structure " + structureId));
            applyStructure(template, cube);
        }
        return cube;
    }

    private static Pattern fromLayers(FabricationRecipe recipe, List<List<String>> layers, @Nullable Pattern into) {
        int sizeY = layers.size();
        int sizeZ = layers.stream().mapToInt(List::size).max().orElse(0);
        int sizeX = layers.stream().flatMap(List::stream).mapToInt(String::length).max().orElse(0);
        if (sizeX == 0 || sizeY == 0 || sizeZ == 0) {
            throw new IllegalArgumentException("empty pattern");
        }
        int room = dev.microcosm.world.RoomGeometry.ROOM;
        if (sizeX > room || sizeY > room || sizeZ > room) {
            throw new IllegalArgumentException("pattern is larger than 16x16x16");
        }
        Pattern out = into != null ? into : new Pattern(sizeX, sizeY, sizeZ);
        for (int y = 0; y < sizeY; y++) {
            List<String> rows = layers.get(y);
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    checkBounds(x, y, z, out.sizeX, out.sizeY, out.sizeZ);
                    out.set(x, y, z, cell(recipe, String.valueOf(row.charAt(x))));
                }
            }
        }
        return out;
    }

    private static Pattern.@Nullable Cell cell(FabricationRecipe recipe, String symbol) {
        return switch (symbol) {
            case " " -> Pattern.AIR;
            case "." -> null;
            default -> {
                HolderSet<Block> blocks = recipe.key().get(symbol);
                if (blocks == null) {
                    throw new IllegalArgumentException("pattern symbol '" + symbol + "' is not defined in key");
                }
                yield new Pattern.Blocks(blocks);
            }
        };
    }

    /** Structure blocks must match exactly; air must stay air; structure voids (not stored) are left unchanged. */
    private static void applyStructure(StructureTemplate template, Pattern cube) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag palette = tag.getListOrEmpty("palette");
        List<Pattern.Cell> cells = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            Identifier blockId = Identifier.parse(palette.getCompoundOrEmpty(i).getStringOr("Name", "minecraft:air"));
            Block block = BuiltInRegistries.BLOCK.getValue(blockId);
            cells.add(block.defaultBlockState().isAir()
                    ? Pattern.AIR
                    : new Pattern.Blocks(HolderSet.direct(block.defaultBlockState().typeHolder())));
        }
        ListTag blocks = tag.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompoundOrEmpty(i);
            ListTag pos = entry.getListOrEmpty("pos");
            int x = pos.getIntOr(0, 0);
            int y = pos.getIntOr(1, 0);
            int z = pos.getIntOr(2, 0);
            checkBounds(x, y, z, cube.sizeX);
            cube.set(x, y, z, cells.get(entry.getIntOr("state", 0)));
        }
    }

    private static void checkBounds(int x, int y, int z, int size) {
        checkBounds(x, y, z, size, size, size);
    }

    private static void checkBounds(int x, int y, int z, int sx, int sy, int sz) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            throw new IllegalArgumentException("position " + x + "," + y + "," + z + " is outside the 16x16x16 room");
        }
    }
}
