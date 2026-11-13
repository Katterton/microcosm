package dev.microcosm.fabrication;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A fabrication recipe in the form clients need to show it (recipe viewers): the compiled pattern with a palette of
 * blocks per cell, so clients do not need structure files or tags.
 *
 * @param cells   index {@code (y * sizeZ + z) * sizeX + x}: -1 anything, 0 air, n > 0 palette entry n - 1
 * @param palette for each palette entry, the block registry ids that are accepted there
 */
public record RecipeDisplay(
        Identifier id,
        boolean cube,
        List<Integer> scales,
        int sizeX,
        int sizeY,
        int sizeZ,
        int[] cells,
        List<List<Integer>> palette,
        List<ItemStack> tools,
        int toolDamage,
        ItemStack result
) {
    private static final int MAX_BLOCKS_PER_ENTRY = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeDisplay> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeIdentifier(d.id);
                buf.writeBoolean(d.cube);
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, d.scales);
                buf.writeVarInt(d.sizeX);
                buf.writeVarInt(d.sizeY);
                buf.writeVarInt(d.sizeZ);
                buf.writeVarIntArray(d.cells);
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()).encode(buf, d.palette);
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, d.tools);
                buf.writeVarInt(d.toolDamage);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, d.result);
            },
            buf -> new RecipeDisplay(buf.readIdentifier(), buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(),
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()).decode(buf),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf), buf.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    public int cell(int x, int y, int z) {
        return cells[(y * sizeZ + z) * sizeX + x];
    }

    /** Builds the display form of all loaded recipes that compile. */
    public static List<RecipeDisplay> all(MinecraftServer server) {
        List<RecipeDisplay> out = new ArrayList<>();
        for (FabricationRecipes.Entry entry : FabricationRecipes.all()) {
            RecipeDisplay display = of(entry, server);
            if (display != null) {
                out.add(display);
            }
        }
        return out;
    }

    private static @Nullable RecipeDisplay of(FabricationRecipes.Entry entry, MinecraftServer server) {
        Pattern[] rotations = entry.rotations(server);
        if (rotations == null) {
            return null;
        }
        Pattern pattern = rotations[0];
        Map<HolderSet<Block>, Integer> index = new IdentityHashMap<>();
        List<List<Integer>> palette = new ArrayList<>();
        int[] cells = new int[pattern.sizeX * pattern.sizeY * pattern.sizeZ];
        for (int y = 0; y < pattern.sizeY; y++) {
            for (int z = 0; z < pattern.sizeZ; z++) {
                for (int x = 0; x < pattern.sizeX; x++) {
                    Pattern.Cell cell = pattern.get(x, y, z);
                    int value;
                    if (cell == null) {
                        value = -1;
                    } else if (cell instanceof Pattern.Blocks blocks) {
                        value = index.computeIfAbsent(blocks.blocks(), set -> {
                            List<Integer> ids = new ArrayList<>();
                            for (Holder<Block> holder : set) {
                                if (ids.size() < MAX_BLOCKS_PER_ENTRY) {
                                    ids.add(BuiltInRegistries.BLOCK.getId(holder.value()));
                                }
                            }
                            palette.add(ids);
                            return palette.size();
                        });
                    } else {
                        value = 0;
                    }
                    cells[(y * pattern.sizeZ + z) * pattern.sizeX + x] = value;
                }
            }
        }
        FabricationRecipe recipe = entry.recipe;
        List<ItemStack> tools = recipe.tool().map(t -> t.getValues().stream().map(ItemStack::new).toList()).orElse(List.of());
        return new RecipeDisplay(entry.id, recipe.mode() == FabricationRecipe.Mode.CUBE, recipe.scale(),
                pattern.sizeX, pattern.sizeY, pattern.sizeZ, cells, palette, tools, recipe.toolDamage(),
                recipe.result().create());
    }
}
