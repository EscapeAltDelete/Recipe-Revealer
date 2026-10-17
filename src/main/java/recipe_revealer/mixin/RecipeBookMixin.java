package recipe_revealer.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

@Mixin(ClientRecipeBook.class)
abstract class RecipeBookMixin {
    @Shadow @Final private Map<RecipeDisplayId, RecipeDisplayEntry> known;
    @Unique private List<RecipeDisplayEntry> recipe_revealer$local;

    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void recipe_revealer$reveal(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        if (recipe_revealer$local == null) {
            recipe_revealer$local = new ArrayList<>();
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var resources = new FallbackResourceManager(PackType.SERVER_DATA, "minecraft");
            resources.push(client.getVanillaPackResources().fullResources());
            var items = client.level.registryAccess().lookupOrThrow(Registries.ITEM);
            var loader = new TagLoader<Holder<Item>>((id, required) -> items.get(id), Registries.tagsDirPath(Registries.ITEM));
            var tags = loader.build(loader.load(resources));
            var groups = new HashMap<String, Integer>();
            for (var file : resources.listResources("recipe", id -> id.getPath().endsWith(".json")).entrySet()) {
                try (var reader = file.getValue().openAsReader()) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    var type = json.get("type").getAsString();
                    if (!type.startsWith("minecraft:crafting_") && !type.equals("minecraft:smelting")
                            && !type.equals("minecraft:blasting") && !type.equals("minecraft:smoking")) continue;
                    var recipe = Recipe.DIRECT_CODEC.parse(ops, recipe_revealer$resolveTags(json, tags)).getOrThrow();
                    if (recipe.isSpecial()) continue;
                    var group = recipe.group().isEmpty() ? OptionalInt.empty()
                            : OptionalInt.of(groups.computeIfAbsent(recipe.group(), key -> -1 - groups.size()));
                    for (var display : recipe.display()) {
                        if (!display.isEnabled(client.level.enabledFeatures())) continue;
                        var id = new RecipeDisplayId(-1 - recipe_revealer$local.size());
                        var ingredients = recipe.placementInfo().ingredients();
                        if (display instanceof ShapelessCraftingRecipeDisplay shapeless
                                && shapeless.ingredients().size() < ingredients.size()) {
                            ingredients = ingredients.subList(0, shapeless.ingredients().size());
                        }
                        recipe_revealer$local.add(new RecipeDisplayEntry(id, display, group, recipe.recipeBookCategory(),
                                Optional.of(ingredients)));
                    }
                } catch (Exception error) {
                    LoggerFactory.getLogger("Recipe Revealer").warn("Cannot display {}: {}", file.getKey(), error.toString());
                }
            }
        }
        // Local display IDs must never be sent as server recipe IDs.
        known.clear();
        for (var entry : recipe_revealer$local) known.put(entry.id(), entry);
    }

    // Resolve stock ingredients without changing the server's registry tags.
    @Unique
    private static JsonElement recipe_revealer$resolveTags(JsonElement json, Map<Identifier, List<Holder<Item>>> tags) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> entry.setValue(recipe_revealer$resolveTags(entry.getValue(), tags)));
        } else if (json.isJsonArray()) {
            var array = json.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) array.set(i, recipe_revealer$resolveTags(array.get(i), tags));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && json.getAsString().startsWith("#minecraft:")) {
            var values = new JsonArray();
            for (var item : tags.get(Identifier.parse(json.getAsString().substring(1)))) values.add(item.getRegisteredName());
            return values;
        }
        return json;
    }
}
