package dev.microcosm.fabrication;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * A 3D fabrication recipe, loaded from {@code data/<namespace>/microcosm/fabrication/<name>.json}.
 *
 * <p>Pattern characters: {@code ' '} = must be air, {@code '.'} = anything, any other character is looked up in
 * {@code key} (a block id, a block tag like {@code #c:storage_blocks/iron}, or a list of block ids).</p>
 *
 * @param mode       {@code local}: a structure anywhere, finished by using {@code tool} on it; the result drops in
 *                   place. {@code cube}: the whole 16x16x16 room of a cell is the pattern; a redstone pulse on the
 *                   cell checks it and the cell turns into the result one level up.
 * @param scale      scale levels the recipe works at (0 = normal world, 1-3 = microcosm levels). Empty = all.
 * @param pattern    layers bottom to top; each layer is a list of rows (north to south) of characters (west to east).
 * @param key        what each pattern character stands for.
 * @param defaultCell cube recipes only: character for cells that no pattern, fill or structure covers.
 * @param fill       cube recipes only: boxes filled with one character, applied after the pattern.
 * @param structure  cube recipes only: a structure template ({@code .nbt}); its blocks must match exactly, air must
 *                   be air, structure voids are "anything". Applied last.
 * @param tool       local recipes: the item that has to be used on the structure.
 * @param toolDamage durability the tool loses per craft.
 * @param result     the produced item. For cube recipes a block item becomes the block that replaces the cell.
 */
public record FabricationRecipe(
        Mode mode,
        List<Integer> scale,
        Optional<List<List<String>>> pattern,
        Map<String, HolderSet<Block>> key,
        String defaultCell,
        List<Fill> fill,
        Optional<Identifier> structure,
        Optional<Ingredient> tool,
        int toolDamage,
        ItemStackTemplate result
) {
    public enum Mode implements StringRepresentable {
        LOCAL("local"),
        CUBE("cube");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Fill(BlockPos from, BlockPos to, String key) {
        public static final Codec<Fill> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("from").forGetter(Fill::from),
                BlockPos.CODEC.fieldOf("to").forGetter(Fill::to),
                Codec.STRING.fieldOf("key").forGetter(Fill::key)
        ).apply(i, Fill::new));
    }

    private static final Codec<List<Integer>> SCALE_CODEC = Codec.either(Codec.INT, Codec.INT.listOf()).xmap(
            e -> e.map(List::of, l -> l),
            l -> l.size() == 1 ? Either.left(l.getFirst()) : Either.right(l));

    public static final Codec<FabricationRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mode.CODEC.fieldOf("mode").forGetter(FabricationRecipe::mode),
            SCALE_CODEC.optionalFieldOf("scale", List.of()).forGetter(FabricationRecipe::scale),
            Codec.STRING.listOf().listOf().optionalFieldOf("pattern").forGetter(FabricationRecipe::pattern),
            Codec.unboundedMap(Codec.STRING, RegistryCodecs.homogeneousList(Registries.BLOCK))
                    .optionalFieldOf("key", Map.of()).forGetter(FabricationRecipe::key),
            Codec.STRING.optionalFieldOf("default", ".").forGetter(FabricationRecipe::defaultCell),
            Fill.CODEC.listOf().optionalFieldOf("fill", List.of()).forGetter(FabricationRecipe::fill),
            Identifier.CODEC.optionalFieldOf("structure").forGetter(FabricationRecipe::structure),
            Ingredient.CODEC.optionalFieldOf("tool").forGetter(FabricationRecipe::tool),
            Codec.INT.optionalFieldOf("tool_damage", 1).forGetter(FabricationRecipe::toolDamage),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(FabricationRecipe::result)
    ).apply(i, FabricationRecipe::new));

    public boolean allowsScale(int depth) {
        return scale.isEmpty() || scale.contains(depth);
    }
}
