package dev.microcosm.client;

import dev.microcosm.fabrication.RecipeDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Fabrication recipes as last received from the server, for recipe viewer integrations. */
public final class ClientRecipes {
    private static List<RecipeDisplay> recipes = List.of();
    private static final List<Consumer<List<RecipeDisplay>>> LISTENERS = new ArrayList<>();

    private ClientRecipes() {}

    public static List<RecipeDisplay> get() {
        return recipes;
    }

    public static void addListener(Consumer<List<RecipeDisplay>> listener) {
        LISTENERS.add(listener);
    }

    static void set(List<RecipeDisplay> received) {
        recipes = List.copyOf(received);
        LISTENERS.forEach(l -> l.accept(recipes));
    }
}
