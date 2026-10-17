package recipe_revealer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecipeBookTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        for (int connection = 0; connection < 2; connection++) {
            try (var world = context.worldBuilder().adjustSettings(settings ->
                    settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
                sync(world);
                long expected = world.getServer().computeOnServer(server -> server.getRecipeManager().getRecipes().stream()
                        .filter(holder -> Set.of(RecipeType.CRAFTING, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING)
                                .contains(holder.value().getType()) && !holder.value().isSpecial())
                        .flatMap(holder -> holder.value().display().stream()).count());
                check(expected > 1000, "Real vanilla recipe catalog must be loaded");
                context.runOnClient(client -> {
                    var book = client.player.getRecipeBook();
                    check(entries(book).size() == expected, "Complete local catalog");
                    var sample = entries(book).getFirst();
                    var serverEntry = new RecipeDisplayEntry(new RecipeDisplayId(0), sample.display(), sample.group(),
                            sample.category(), sample.craftingRequirements());
                    client.getConnection().handleRecipeBookAdd(new ClientboundRecipeBookAddPacket(
                            List.of(new ClientboundRecipeBookAddPacket.Entry(serverEntry, false, false)), true));
                    client.getConnection().handleRecipeBookRemove(new ClientboundRecipeBookRemovePacket(List.of(serverEntry.id())));
                    client.getConnection().handleRecipeBookAdd(new ClientboundRecipeBookAddPacket(List.of(), true));
                    book.rebuildCollections();
                    check(entries(book).size() == expected, "Server updates and rebuilds cannot remove local recipes");
                    check(entries(book).stream().allMatch(entry -> entry.id().index() < 0), "No guessed server IDs");
                });
                if (connection == 0) checkMissingTags(context, expected);
                // A shaped 2x2 recipe with mixed interchangeable ingredient stacks.
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
                    player.getInventory().setItem(1, new ItemStack(Items.BIRCH_PLANKS, 16));
                    player.inventoryMenu.broadcastChanges();
                    player.getRecipeBook().removeRecipes(server.getRecipeManager().getRecipes(), player);
                });
                sync(world);
                context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
                click(context, Items.CRAFTING_TABLE, false);
                sync(world);
                checkGrid(world, 1);
                click(context, Items.CRAFTING_TABLE, false);
                sync(world);
                checkGrid(world, 2);
                click(context, Items.CRAFTING_TABLE, true);
                sync(world);
                checkGrid(world, 8);
                world.getServer().runOnServer(server -> check(world.getConnection().getServerPlayer()
                        .inventoryMenu.getSlot(0).getItem().is(Items.CRAFTING_TABLE), "Server recognizes the real crafting result"));
                context.runOnClient(client -> client.gameMode.handleContainerInput(0, 0, 0,
                        ContainerInput.QUICK_MOVE, client.player));
                sync(world);
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    check(player.getInventory().getNonEquipmentItems().stream()
                            .filter(stack -> stack.is(Items.CRAFTING_TABLE)).mapToInt(ItemStack::getCount).sum() == 8,
                            "Crafted items really exist on the server");
                });
                // Shapeless crafting, missing ingredients and a nonempty cursor.
                context.runOnClient(client -> {
                    var book = client.player.getRecipeBook();
                    var missing = find(book, Items.DIAMOND_BLOCK);
                    check(!RecipeFill.place(missing, false), "Missing ingredients must not fabricate items");
                    client.player.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
                    check(!RecipeFill.place(find(book, Items.OAK_PLANKS), false), "Do not touch a nonempty cursor");
                    client.player.containerMenu.setCarried(ItemStack.EMPTY);
                });
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 8));
                    player.inventoryMenu.broadcastChanges();
                });
                sync(world);
                click(context, Items.OAK_PLANKS, false);
                sync(world);
                world.getServer().runOnServer(server -> check(world.getConnection().getServerPlayer()
                        .inventoryMenu.getSlot(0).getItem().is(Items.OAK_PLANKS), "Shapeless placement works"));
                context.runOnClient(client -> client.player.closeContainer());
                sync(world);
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.getInventory().setItem(0, new ItemStack(Items.RAW_IRON, 7));
                    player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new FurnaceMenu(id, inventory),
                            Component.literal("Recipe Revealer")));
                });
                sync(world);
                click(context, Items.IRON_INGOT, true);
                sync(world);
                world.getServer().runOnServer(server -> {
                    var input = world.getConnection().getServerPlayer().containerMenu.getSlot(0).getItem();
                    check(input.is(Items.RAW_IRON) && input.getCount() == 7, "Furnace input reaches the server");
                });
            }
        }
    }

    private static void checkMissingTags(ClientGameTestContext context, long expected) {
        context.runOnClient(client -> {
            var items = client.level.registryAccess().lookupOrThrow(Registries.ITEM);
            var original = items.getTags().collect(Collectors.toMap(tag -> tag.key(), tag -> tag.stream().toList()));
            try {
                items.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, Map.of(
                        ItemTags.PLANKS, List.of(Items.DIRT.builtInRegistryHolder()), ItemTags.POPLAR_LOGS, List.of()))).apply();
                var book = new ClientRecipeBook();
                book.rebuildCollections();
                check(entries(book).size() == expected, "Missing and empty server tags cannot hide vanilla recipes");
                var mushrooms = find(book, Items.MUSHROOM_STEW).craftingRequirements().orElseThrow().getFirst();
                check(mushrooms.test(new ItemStack(Items.RED_MUSHROOM))
                        && mushrooms.test(new ItemStack(Items.BROWN_MUSHROOM)), "Bundled mushroom ingredients");
                check(find(book, Items.POPLAR_PLANKS).craftingRequirements().orElseThrow().getFirst()
                        .test(new ItemStack(Items.POPLAR_LOG)), "Empty server tag has local ingredients");
                var planks = find(book, Items.CRAFTING_TABLE).craftingRequirements().orElseThrow().getFirst();
                check(planks.test(new ItemStack(Items.OAK_PLANKS)) && !planks.test(new ItemStack(Items.DIRT)),
                        "Changed server tags cannot change vanilla ingredients");
                check(items.get(ItemTags.MUSHROOMS).isEmpty() && items.getOrThrow(ItemTags.POPLAR_LOGS).size() == 0
                        && items.getOrThrow(ItemTags.PLANKS).contains(Items.DIRT.builtInRegistryHolder()),
                        "Revealing recipes must not change server tags");
            } finally {
                items.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, original)).apply();
            }
        });
    }

    private static void click(ClientGameTestContext context, Item result, boolean maximum) {
        try {
            context.runOnClient(client -> {
                var entry = find(client.player.getRecipeBook(), result);
                var component = client.gui.screen().children().stream()
                        .filter(RecipeBookComponent.class::isInstance).findFirst().orElseThrow();
                var method = RecipeBookComponent.class.getDeclaredMethod("tryPlaceRecipe", RecipeCollection.class,
                        RecipeDisplayId.class, boolean.class);
                method.setAccessible(true);
                check((boolean) method.invoke(component, new RecipeCollection(List.of(entry)), entry.id(), maximum),
                        "Native click hook");
            });
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static RecipeDisplayEntry find(ClientRecipeBook book, Item result) {
        var client = net.minecraft.client.Minecraft.getInstance();
        return entries(book).stream()
                .filter(entry -> result != Items.IRON_INGOT || entry.display() instanceof
                        net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace
                        && furnace.ingredient().resolveForStacks(SlotDisplayContext.fromLevel(client.level)).stream()
                                .anyMatch(stack -> stack.is(Items.RAW_IRON)))
                .filter(entry -> entry.display().result()
                .resolveForStacks(SlotDisplayContext.fromLevel(client.level)).stream().anyMatch(stack -> stack.is(result)))
                .findFirst().orElseThrow();
    }

    private static List<RecipeDisplayEntry> entries(ClientRecipeBook book) {
        return book.getCollections().stream().flatMap(collection -> collection.getRecipes().stream()).toList();
    }

    private static void checkGrid(TestSingleplayerContext world, int count) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            for (var slot : ((AbstractCraftingMenu) player.containerMenu).getInputGridSlots()) {
                check(slot.getItem().getCount() == count, "Server grid count must be " + count);
            }
            check(player.containerMenu.getCarried().isEmpty(), "Cursor must be empty after filling");
        });
    }

    private static void sync(TestSingleplayerContext world) {
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
