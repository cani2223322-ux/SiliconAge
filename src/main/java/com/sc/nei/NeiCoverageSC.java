package com.sc.nei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/**
 * Developer check (PackCheckSC, -Dsc.packcheck): every item of this mod as the creative tabs list it, and how many
 * recipes NEI's own handlers show for it ("item" lookups, as the R key asks). Items with none are listed, with any
 * crafting-table recipe NEI doesn't draw (a custom IRecipe class). Client only, NEI loaded.
 */
public final class NeiCoverageSC {

    private NeiCoverageSC() {
    }

    public static int handlers() {
        return GuiCraftingRecipe.craftinghandlers.size();
    }

    public static List<String> run() {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        Set<String> seen = new HashSet<String>();
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
            if (id == null || !"SiliconAge".equals(id.modId)) {
                continue;
            }
            List<ItemStack> subs = new ArrayList<ItemStack>();
            CreativeTabs[] tabs = item.getCreativeTabs();
            for (CreativeTabs t : tabs == null ? new CreativeTabs[]{null} : tabs) {
                try {
                    item.getSubItems(item, t, subs);
                } catch (Throwable e) {
                    // listed below as it is
                }
            }
            if (subs.isEmpty()) {
                subs.add(new ItemStack(item));
            }
            for (ItemStack s : subs) {
                if (s != null && s.getItem() != null && seen.add(Item.itemRegistry.getNameForObject(s.getItem()) + ":" + s.getItemDamage())) {
                    stacks.add(s);
                }
            }
        }
        List<String> out = new ArrayList<String>();
        List<String> missing = new ArrayList<String>();
        for (ItemStack s : stacks) {
            int n = 0;
            for (ICraftingHandler h : new ArrayList<ICraftingHandler>(GuiCraftingRecipe.craftinghandlers)) {
                try {
                    ICraftingHandler hh = h.getRecipeHandler("item", s);
                    n += hh == null ? 0 : hh.numRecipes();
                } catch (Throwable e) {
                    // a handler that can't take it
                }
            }
            if (n == 0) {
                missing.add(line(s) + hiddenRecipes(s));
            }
        }
        out.add("# items " + stacks.size() + ", NEI handlers " + GuiCraftingRecipe.craftinghandlers.size() + ", without a recipe in NEI " + missing.size());
        out.addAll(missing);
        return out;
    }

    private static String line(ItemStack s) {
        String name;
        try {
            name = s.getDisplayName();
        } catch (Throwable e) {
            name = "?";
        }
        return name + " {" + Item.itemRegistry.getNameForObject(s.getItem()) + ":" + s.getItemDamage() + "}";
    }

    /** Crafting-table recipes for it that NEI's handlers don't show (their classes). */
    private static String hiddenRecipes(ItemStack s) {
        Set<String> classes = new HashSet<String>();
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            ItemStack out = r.getRecipeOutput();
            if (out != null && out.getItem() == s.getItem() && (out.getItemDamage() == s.getItemDamage() || !s.getHasSubtypes())) {
                classes.add(r.getClass().getName());
            }
        }
        return classes.isEmpty() ? "" : "  - crafting recipe(s) NEI doesn't draw: " + classes;
    }
}
