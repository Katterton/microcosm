package dev.microcosm.network;

import dev.microcosm.Microcosm;
import dev.microcosm.fabrication.RecipeDisplay;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: all fabrication recipes in display form, sent on login and after {@code /reload}. */
public record RecipeSyncPayload(List<RecipeDisplay> recipes) implements CustomPacketPayload {
    public static final Type<RecipeSyncPayload> TYPE = new Type<>(Microcosm.id("recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSyncPayload> STREAM_CODEC =
            RecipeDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).map(RecipeSyncPayload::new, RecipeSyncPayload::recipes);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
