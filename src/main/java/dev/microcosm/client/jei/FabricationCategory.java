package dev.microcosm.client.jei;

import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import dev.microcosm.fabrication.RecipeDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Shows a fabrication recipe: the pattern layer by layer (cycling once per second), the materials with their counts,
 * the tool and the result.
 */
public class FabricationCategory implements IRecipeCategory<RecipeDisplay> {
    public static final IRecipeType<RecipeDisplay> TYPE =
            IRecipeType.create(Microcosm.MODID, "fabrication", RecipeDisplay.class);

    private static final int WIDTH = 180;
    private static final int HEIGHT = 124;
    private static final int GRID_X = 0;
    private static final int GRID_Y = 12;
    private static final int GRID_SIZE = 96;
    private static final int PANEL_X = 104;
    private static final int MATERIAL_SLOTS = 9;
    private static final long LAYER_MILLIS = 1200;

    private final IDrawable icon;
    private final IDrawable arrow;

    public FabricationCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModRegistry.CELL_ITEM.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public IRecipeType<RecipeDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.microcosm.fabrication");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public @Nullable Identifier getIdentifier(RecipeDisplay recipe) {
        return recipe.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeDisplay recipe, IFocusGroup focuses) {
        int[] counts = new int[recipe.palette().size()];
        for (int cell : recipe.cells()) {
            if (cell > 0) {
                counts[cell - 1]++;
            }
        }
        for (int i = 0; i < Math.min(MATERIAL_SLOTS, counts.length); i++) {
            List<ItemStack> stacks = new ArrayList<>();
            for (int id : recipe.palette().get(i)) {
                Block block = BuiltInRegistries.BLOCK.byId(id);
                ItemStack stack = new ItemStack(block.asItem(), counts[i]);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            builder.addInputSlot(PANEL_X + (i % 3) * 18, GRID_Y + (i / 3) * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks);
        }
        int bottom = GRID_Y + 3 * 18 + 10;
        if (!recipe.tools().isEmpty()) {
            builder.addInputSlot(PANEL_X, bottom).setStandardSlotBackground().addItemStacks(recipe.tools());
        } else {
            builder.addInputSlot(PANEL_X, bottom).setStandardSlotBackground().add(new ItemStack(ModRegistry.CELL_ITEM.get()));
        }
        builder.addOutputSlot(PANEL_X + 54, bottom).setOutputSlotBackground().add(recipe.result());
    }

    private static int layer(RecipeDisplay recipe) {
        return (int) ((System.currentTimeMillis() / LAYER_MILLIS) % recipe.sizeY());
    }

    private static int cellSize(RecipeDisplay recipe) {
        return Math.max(3, Math.min(16, GRID_SIZE / Math.max(recipe.sizeX(), recipe.sizeZ())));
    }

    /** Which accepted block to show for a palette entry; tags cycle through their blocks. */
    private static Block block(RecipeDisplay recipe, int cell) {
        List<Integer> ids = recipe.palette().get(cell - 1);
        int index = (int) ((System.currentTimeMillis() / 1000) % ids.size());
        return BuiltInRegistries.BLOCK.byId(ids.get(index));
    }

    @Override
    public void draw(RecipeDisplay recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.text(font, header(recipe), 0, 0, 0xFF404040, false);
        int size = cellSize(recipe);
        int layer = layer(recipe);
        int width = size * recipe.sizeX();
        int height = size * recipe.sizeZ();
        graphics.fill(GRID_X - 1, GRID_Y - 1, GRID_X + width + 1, GRID_Y + height + 1, 0xFF4A6070);
        graphics.fill(GRID_X, GRID_Y, GRID_X + width, GRID_Y + height, 0xFF1A242A);
        for (int z = 0; z < recipe.sizeZ(); z++) {
            for (int x = 0; x < recipe.sizeX(); x++) {
                int cell = recipe.cell(x, layer, z);
                int sx = GRID_X + x * size;
                int sy = GRID_Y + z * size;
                if (cell < 0) {
                    graphics.fill(sx, sy, sx + size, sy + size, 0xFF2C3A42);
                    if (size >= 6) {
                        graphics.fill(sx + size / 2, sy + size / 2, sx + size / 2 + 1, sy + size / 2 + 1, 0xFF56707C);
                    }
                } else if (cell > 0) {
                    Block block = block(recipe, cell);
                    ItemStack stack = new ItemStack(block.asItem());
                    if (size >= 12 && !stack.isEmpty()) {
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(sx, sy);
                        graphics.pose().scale(size / 16f, size / 16f);
                        graphics.fakeItem(stack, 0, 0);
                        graphics.pose().popMatrix();
                    } else {
                        MapColor color = block.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                        graphics.fill(sx, sy, sx + size, sy + size, 0xFF000000 | (color == MapColor.NONE ? 0x909090 : color.col));
                    }
                }
            }
        }
        graphics.text(font, Component.translatable("jei.microcosm.layer", layer + 1, recipe.sizeY()),
                GRID_X, GRID_Y + GRID_SIZE + 4, 0xFF404040, false);
        int bottom = GRID_Y + 3 * 18 + 10;
        arrow.draw(graphics, PANEL_X + 24, bottom + 1);
        if (recipe.palette().size() > MATERIAL_SLOTS) {
            graphics.text(font, "+" + (recipe.palette().size() - MATERIAL_SLOTS), PANEL_X + 56, GRID_Y + 40, 0xFF404040, false);
        }
    }

    private static Component header(RecipeDisplay recipe) {
        String scales = recipe.scales().isEmpty() ? "*"
                : recipe.scales().stream().map(String::valueOf).collect(Collectors.joining(", "));
        return Component.translatable(recipe.cube() ? "jei.microcosm.cube" : "jei.microcosm.local", scales,
                recipe.sizeX(), recipe.sizeY(), recipe.sizeZ());
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, RecipeDisplay recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        int size = cellSize(recipe);
        int x = (int) Math.floor((mouseX - GRID_X) / size);
        int z = (int) Math.floor((mouseY - GRID_Y) / size);
        if (x < 0 || z < 0 || x >= recipe.sizeX() || z >= recipe.sizeZ()) {
            return;
        }
        int cell = recipe.cell(x, layer(recipe), z);
        if (cell < 0) {
            tooltip.add(Component.translatable("jei.microcosm.any"));
        } else if (cell == 0) {
            tooltip.add(Component.translatable("jei.microcosm.air"));
        } else {
            for (int id : recipe.palette().get(cell - 1)) {
                tooltip.add(BuiltInRegistries.BLOCK.byId(id).getName());
            }
        }
        tooltip.add(Component.literal(x + " " + layer(recipe) + " " + z).withColor(0xFF808080));
    }
}
