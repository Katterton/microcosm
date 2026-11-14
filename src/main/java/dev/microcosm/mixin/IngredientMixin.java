package dev.microcosm.mixin;

import dev.microcosm.world.ItemScales;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Material of a foreign scale is no ingredient for anything: nine Milliblock gold ingots must not become a normal
 * gold block. Inside a room of their own scale items carry no scale and craft normally.
 */
@Mixin(Ingredient.class)
public abstract class IngredientMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void microcosm$rejectForeignScale(ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScales.isForeign(input)) {
            cir.setReturnValue(false);
        }
    }
}
