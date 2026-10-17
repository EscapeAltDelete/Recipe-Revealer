package escapealtdelete.mixin;

import escapealtdelete.EscapeAltDeleteFill;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
abstract class EscapeAltDeleteScreenMixin {
    @Shadow public abstract void fillGhostRecipe(RecipeDisplay display);

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void escapealtdelete$preview(RecipeCollection collection, RecipeDisplayId id, boolean maxItems,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (id.index() >= 0) return;
        collection.getRecipes().stream().filter(entry -> entry.id().equals(id)).findFirst()
                .ifPresent(entry -> {
                    if (!EscapeAltDeleteFill.place(entry, maxItems)) fillGhostRecipe(entry.display());
                });
        cir.setReturnValue(true);
    }
}
