package dev.microcosm.client.jei;

import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import dev.microcosm.client.ClientRecipes;
import dev.microcosm.device.DeviceKind;
import dev.microcosm.fabrication.RecipeDisplay;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** JEI integration: one category for all fabrication recipes. Only loaded when JEI is installed. */
@JeiPlugin
public class MicrocosmJeiPlugin implements IModPlugin {
    private static @Nullable IJeiRuntime runtime;
    private static List<RecipeDisplay> shown = List.of();
    private static boolean listening;

    @Override
    public Identifier getPluginUid() {
        return Microcosm.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FabricationCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        shown = ClientRecipes.get();
        registration.addRecipes(FabricationCategory.TYPE, shown);
        if (!listening) {
            listening = true;
            // Our recipes arrive in their own packet, possibly after JEI has started.
            ClientRecipes.addListener(MicrocosmJeiPlugin::update);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(FabricationCategory.TYPE, ModRegistry.CELL.get(), ModRegistry.MICRO_SOLDERING_IRON.get());
        for (DeviceKind kind : DeviceKind.values()) {
            if (kind.isActuator()) {
                registration.addCraftingStation(FabricationCategory.TYPE, ModRegistry.DEVICES.get(kind).get());
            }
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void update(List<RecipeDisplay> recipes) {
        IJeiRuntime current = runtime;
        if (current == null) {
            return;
        }
        if (!shown.isEmpty()) {
            current.getRecipeManager().hideRecipes(FabricationCategory.TYPE, shown);
        }
        shown = recipes;
        current.getRecipeManager().addRecipes(FabricationCategory.TYPE, recipes);
    }
}
