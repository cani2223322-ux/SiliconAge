package com.sc.nei;

import net.minecraft.item.ItemStack;

/** The handbook's NEI calls - only ever reached with NEI loaded (the callers check). */
public final class NeiBookSC {

    private NeiBookSC() {
    }

    /** NEI's recipes for an item (right-click on an icon in the handbook). */
    public static void recipes(ItemStack s) {
        try {
            codechicken.nei.recipe.GuiCraftingRecipe.openRecipeGui("item", s.copy());
        } catch (Throwable t) {
            // NEI couldn't open from this screen - nothing lost
        }
    }

    /** NEI's search box has the keyboard (the G key must not steal its letter). */
    public static boolean searchFocused() {
        try {
            return codechicken.nei.LayoutManager.searchField != null && codechicken.nei.LayoutManager.searchField.focused();
        } catch (Throwable t) {
            return false;
        }
    }
}
