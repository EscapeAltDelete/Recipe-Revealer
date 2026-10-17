package recipe_revealer.mixin;

import recipe_revealer.RecipeFill;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
abstract class RecipeBookScreenMixin {
    @Shadow @Final private GhostSlots ghostSlots;
    @Shadow public abstract void fillGhostRecipe(RecipeDisplay display);

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void recipe_revealer$place(RecipeCollection collection, RecipeDisplayId id, boolean maxItems,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (id.index() >= 0) return;
        ghostSlots.clear();
        collection.getRecipes().stream().filter(entry -> entry.id().equals(id)).findFirst()
                .ifPresent(entry -> {
                    if (!RecipeFill.place(entry, maxItems)) fillGhostRecipe(entry.display());
                });
        cir.setReturnValue(true);
    }
}
