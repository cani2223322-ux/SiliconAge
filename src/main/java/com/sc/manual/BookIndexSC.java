package com.sc.manual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;

/**
 * МК-2: one pass over every machine, crafting and furnace recipe while the book is being built
 * (BookContent.all), keyed by Item: the recipes that make it and the ones that use it. The
 * look-ups (BookMaterialsSC, BookContent.craftingFor) then walk only these candidates - in the
 * registries' own order, with the same item/meta checks as before, so the pages come out the
 * same - instead of every recipe of a large modpack per item. Outside a build (no index) the
 * look-ups return whole registries, as they always did.
 */
final class BookIndexSC {

    /** A machine recipe with the machine whose list it is in. */
    static final class MR {
        final MachineType type;
        final MachineRecipe r;

        MR(MachineType type, MachineRecipe r) {
            this.type = type;
            this.r = r;
        }
    }

    /** The index of the current book build (null outside one). */
    private static volatile BookIndexSC current;

    private final Map<Item, List<MR>> machineMakes = new HashMap<Item, List<MR>>();     // outputs + by-products
    private final Map<Item, List<MR>> machineUses = new HashMap<Item, List<MR>>();      // inputs
    private final Map<Item, List<IRecipe>> craftMakes = new HashMap<Item, List<IRecipe>>();
    private final Map<Item, List<IRecipe>> craftUses = new HashMap<Item, List<IRecipe>>();
    private final Map<Item, List<Map.Entry<?, ?>>> smeltMakes = new HashMap<Item, List<Map.Entry<?, ?>>>();
    private final Map<Item, List<Map.Entry<?, ?>>> smeltUses = new HashMap<Item, List<Map.Entry<?, ?>>>();

    private BookIndexSC() {
    }

    /** Builds the index for one book build. */
    static void begin() {
        current = build();
    }

    static void end() {
        current = null;
    }

    static boolean active() {
        return current != null;
    }

    @SuppressWarnings("rawtypes")
    private static BookIndexSC build() {
        BookIndexSC x = new BookIndexSC();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                MR m = new MR(type, r);
                for (ItemStack o : r.outputs) {
                    add(x.machineMakes, o, m);
                }
                for (ItemStack o : r.byproducts) {
                    add(x.machineMakes, o, m);
                }
                for (ItemStack in : r.inputs) {
                    add(x.machineUses, in, m);
                }
            }
        }
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            add(x.craftMakes, r.getRecipeOutput(), r);
            for (Object in : BookMaterialsSC.ingredients(r)) {
                if (in instanceof ItemStack) {
                    add(x.craftUses, (ItemStack) in, r);
                } else if (in instanceof List) {
                    for (Object k : (List) in) {
                        if (k instanceof ItemStack) {
                            add(x.craftUses, (ItemStack) k, r);
                        }
                    }
                }
            }
        }
        for (Object o : FurnaceRecipes.smelting().getSmeltingList().entrySet()) {
            Map.Entry<?, ?> en = (Map.Entry<?, ?>) o;
            if (en.getValue() instanceof ItemStack) {
                add(x.smeltMakes, (ItemStack) en.getValue(), en);
            }
            if (en.getKey() instanceof ItemStack) {
                add(x.smeltUses, (ItemStack) en.getKey(), en);
            }
        }
        return x;
    }

    /** Adds v under s's item, once in a row (a recipe naming the item twice is listed once). */
    private static <V> void add(Map<Item, List<V>> map, ItemStack s, V v) {
        if (s == null || s.getItem() == null) {
            return;
        }
        List<V> l = map.get(s.getItem());
        if (l == null) {
            l = new ArrayList<V>(2);
            map.put(s.getItem(), l);
        }
        if (l.isEmpty() || l.get(l.size() - 1) != v) {
            l.add(v);
        }
    }

    private static <V> List<V> of(Map<Item, List<V>> map, ItemStack s) {
        List<V> l = s == null ? null : map.get(s.getItem());
        return l == null ? Collections.<V>emptyList() : l;
    }

    private static List<MR> allMachine() {
        List<MR> all = new ArrayList<MR>();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                all.add(new MR(type, r));
            }
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static List<IRecipe> allCraft() {
        return (List<IRecipe>) CraftingManager.getInstance().getRecipeList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map.Entry<?, ?>> allSmelt() {
        return new ArrayList<Map.Entry<?, ?>>((java.util.Set<Map.Entry<?, ?>>) (java.util.Set<?>) FurnaceRecipes.smelting().getSmeltingList().entrySet());
    }

    // ---- candidates (the caller still checks item and meta)

    static List<MR> machineMaking(ItemStack s) {
        return current != null ? of(current.machineMakes, s) : allMachine();
    }

    static List<MR> machineUsing(ItemStack s) {
        return current != null ? of(current.machineUses, s) : allMachine();
    }

    static List<IRecipe> craftMaking(ItemStack s) {
        return current != null ? of(current.craftMakes, s) : allCraft();
    }

    static List<IRecipe> craftUsing(ItemStack s) {
        return current != null ? of(current.craftUses, s) : allCraft();
    }

    static List<Map.Entry<?, ?>> smeltMaking(ItemStack s) {
        return current != null ? of(current.smeltMakes, s) : allSmelt();
    }

    static List<Map.Entry<?, ?>> smeltUsing(ItemStack s) {
        return current != null ? of(current.smeltUses, s) : allSmelt();
    }
}
