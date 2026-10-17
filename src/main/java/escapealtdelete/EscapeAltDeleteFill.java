package escapealtdelete;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.List;

public final class EscapeAltDeleteFill {
    public static boolean place(RecipeDisplayEntry entry, boolean maximum) {
        var client = Minecraft.getInstance();
        var menu = client.player.containerMenu;
        if (!menu.getCarried().isEmpty() || entry.craftingRequirements().isEmpty()) return false;
        List<Slot> inputs;
        var targets = new ArrayList<Slot>();
        if (menu instanceof AbstractCraftingMenu crafting) {
            inputs = crafting.getInputGridSlots();
            if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
                if (shaped.width() > crafting.getGridWidth() || shaped.height() > crafting.getGridHeight()) return false;
                PlaceRecipeHelper.placeRecipe(crafting.getGridWidth(), crafting.getGridHeight(),
                        shaped.width(), shaped.height(), shaped.ingredients(), (ingredient, slot, x, y) -> {
                            if (ingredient != SlotDisplay.Empty.INSTANCE) targets.add(inputs.get(slot));
                        });
            } else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
                if (shapeless.ingredients().size() > inputs.size()) return false;
                targets.addAll(inputs.subList(0, shapeless.ingredients().size()));
            } else return false;
        } else if (menu instanceof AbstractFurnaceMenu && entry.display() instanceof FurnaceRecipeDisplay) {
            inputs = List.of(menu.getSlot(0));
            targets.addAll(inputs);
        } else return false;

        var requirements = entry.craftingRequirements().get();
        if (targets.size() != requirements.size() || targets.isEmpty()) return false;
        boolean matches = inputs.stream().filter(slot -> !targets.contains(slot)).noneMatch(Slot::hasItem);
        int previous = Integer.MAX_VALUE;
        for (int i = 0; i < targets.size(); i++) {
            var stack = targets.get(i).getItem();
            matches &= !stack.isEmpty() && requirements.get(i).test(stack);
            previous = Math.min(previous, stack.getCount());
        }
        int wanted = maximum ? 64 : matches ? Math.min(64, previous + 1) : 1;
        // Quick-move returns inputs through vanilla inventory rules; never drop items.
        for (var input : inputs) {
            if (input.hasItem()) click(client, input, 0, ContainerInput.QUICK_MOVE);
            if (input.hasItem()) return false;
        }
        var contents = new StackedContents<Holder<Item>>();
        var inventory = client.player.getInventory();
        for (var stack : inventory.getNonEquipmentItems()) {
            if (!stack.isEmpty() && Inventory.isUsableForCrafting(stack)) contents.account(stack.typeHolder(), stack.getCount());
        }
        var chosen = new ArrayList<Holder<Item>>();
        int amount = contents.tryPickAll(requirements, wanted, chosen::add);
        if (amount == 0) return false;
        for (var item : chosen) amount = Math.min(amount, item.value().getDefaultMaxStackSize());
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            int remaining = amount;
            while (remaining > 0) {
                int index = inventory.findSlotMatchingCraftingIngredient(chosen.get(i), target.getItem());
                if (index < 0) return false;
                var source = menu.slots.stream().filter(slot -> slot.container == inventory && slot.getContainerSlot() == index)
                        .findFirst().orElseThrow();
                int available = source.getItem().getCount();
                boolean half = (available + 1) / 2 == remaining && available > remaining;
                click(client, source, half ? 1 : 0, ContainerInput.PICKUP);
                var carried = menu.getCarried();
                int move = Math.min(remaining, Math.min(carried.getCount(), target.getMaxStackSize(carried) - target.getItem().getCount()));
                if (move == carried.getCount()) click(client, target, 0, ContainerInput.PICKUP);
                else for (int n = 0; n < move; n++) click(client, target, 1, ContainerInput.PICKUP);
                if (!menu.getCarried().isEmpty()) click(client, source, 0, ContainerInput.PICKUP);
                if (move == 0 || !menu.getCarried().isEmpty()) return false;
                remaining -= move;
            }
        }
        return true;
    }

    private static void click(Minecraft client, Slot slot, int button, ContainerInput action) {
        var menu = client.player.containerMenu;
        client.gameMode.handleContainerInput(menu.containerId, menu.slots.indexOf(slot), button, action, client.player);
    }
}
