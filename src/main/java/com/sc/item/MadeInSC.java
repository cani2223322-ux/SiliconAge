package com.sc.item;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.manual.Lang;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/**
 * СТ-1 (audit 2026-10-20): the tooltips' "made in" and "works in" lines of the silicon chain's materials and the production tools,
 * worked out from the recipes themselves (the machines' RecipeRegistry and the crafting table) - never out of step with them. The
 * names in the language of the moment; cached by item, damage and language (the recipes don't change after the load).
 */
public final class MadeInSC {

    private static final Map<String, String> MADE = new HashMap<String, String>(), WORKS = new HashMap<String, String>();

    private MadeInSC() {
    }

    /** The machines (and the crafting table) whose recipes make `s`, joined by ", "; "" when none. */
    public static String madeIn(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return "";
        }
        String key = key(s);
        String v = MADE.get(key);
        if (v == null) {
            Set<String> names = new LinkedHashSet<String>();
            for (MachineType t : MachineType.values()) {
                for (MachineRecipe r : RecipeRegistry.recipesFor(t)) {
                    if (outputs(r, s)) {
                        names.add(t.localizedName());
                        break;
                    }
                }
            }
            for (Object o : CraftingManager.getInstance().getRecipeList()) {
                ItemStack out = o instanceof IRecipe ? ((IRecipe) o).getRecipeOutput() : null;
                if (out != null && out.getItem() == s.getItem() && (s.getItem().isDamageable() || out.getItemDamage() == s.getItemDamage())) {
                    names.add(Lang.tr("tile.workbench.name"));
                    break;
                }
            }
            v = join(names);
            MADE.put(key, v);
        }
        return v;
    }

    /** The machines whose recipes take `tool` in (worn there), joined by ", "; "" when none. */
    public static String worksIn(ItemStack tool) {
        if (tool == null || tool.getItem() == null) {
            return "";
        }
        String key = key(tool);
        String v = WORKS.get(key);
        if (v == null) {
            Set<String> names = new LinkedHashSet<String>();
            for (MachineType t : MachineType.values()) {
                for (MachineRecipe r : RecipeRegistry.recipesFor(t)) {
                    boolean uses = false;
                    for (ItemStack in : r.inputs) {
                        uses |= in != null && MachineRecipe.isSameIngredient(tool, in);
                    }
                    if (uses) {
                        names.add(t.localizedName());
                        break;
                    }
                }
            }
            v = join(names);
            WORKS.put(key, v);
        }
        return v;
    }

    private static boolean outputs(MachineRecipe r, ItemStack s) {
        for (ItemStack out : r.outputs) {
            if (out != null && out.getItem() == s.getItem() && (s.getItem().isDamageable() || out.getItemDamage() == s.getItemDamage())) {
                return true;
            }
        }
        return false;
    }

    private static String key(ItemStack s) {
        return net.minecraft.item.Item.getIdFromItem(s.getItem()) + ":" + (s.getItem().isDamageable() ? 0 : s.getItemDamage()) + ":"
                + Lang.tr("sc.levelgui.mb");                // the language: "мБ" / "mB" tells them apart
    }

    private static String join(Set<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            sb.append(sb.length() > 0 ? ", " : "").append(n);
        }
        return sb.toString();
    }
}
