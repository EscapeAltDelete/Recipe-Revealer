package escapealtdelete.mixin;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

@Mixin(ClientRecipeBook.class)
abstract class EscapeAltDeleteBookMixin {
    @Shadow @Final private Map<RecipeDisplayId, RecipeDisplayEntry> known;
    @Unique private List<RecipeDisplayEntry> escapealtdelete$local;

    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void escapealtdelete$reveal(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        if (escapealtdelete$local == null) {
            escapealtdelete$local = new ArrayList<>();
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var files = new TreeMap<Identifier, IoSupplier<InputStream>>();
            client.getVanillaPackResources().fullResources()
                    .listResources(PackType.SERVER_DATA, "minecraft", "recipe", files::put);
            var groups = new HashMap<String, Integer>();
            for (var file : files.entrySet()) {
                if (!file.getKey().getPath().endsWith(".json")) continue;
                try (var reader = new InputStreamReader(file.getValue().get(), StandardCharsets.UTF_8)) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    switch (json.get("type").getAsString()) {
                        case "minecraft:crafting_shaped", "minecraft:crafting_shapeless",
                             "minecraft:smelting", "minecraft:blasting", "minecraft:smoking" -> { }
                        default -> { continue; }
                    }
                    var recipe = Recipe.DIRECT_CODEC.parse(ops, json).getOrThrow();
                    var group = recipe.group().isEmpty() ? OptionalInt.empty()
                            : OptionalInt.of(groups.computeIfAbsent(recipe.group(), key -> -1 - groups.size()));
                    for (var display : recipe.display()) {
                        if (!display.isEnabled(client.level.enabledFeatures())) continue;
                        var id = new RecipeDisplayId(-1 - escapealtdelete$local.size());
                        escapealtdelete$local.add(new RecipeDisplayEntry(id, display, group, recipe.recipeBookCategory(),
                                Optional.of(recipe.placementInfo().ingredients())));
                    }
                } catch (Exception error) {
                    LoggerFactory.getLogger("EscapeAltDelete").warn("Cannot display {}", file.getKey(), error);
                }
            }
        }
        // Local IDs are previews, never valid server crafting requests.
        known.clear();
        for (var entry : escapealtdelete$local) known.put(entry.id(), entry);
    }
}
