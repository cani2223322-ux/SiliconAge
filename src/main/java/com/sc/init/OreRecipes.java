package com.sc.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Crafting-table registration that accepts equivalent materials from other mods. Every recipe
 * used to name this mod's own ingot/dust/rubber items exactly, so with IC2 installed its copper
 * ingot or rubber would not fit a single SC recipe even though both mods register the same
 * OreDict names ("ingotCopper", "itemRubber") - the OreDict entries ModItems publishes were
 * never used by the mod itself.
 *
 * Recipes keep passing plain ItemStacks; any stack that carries one of the OreDict names below
 * is swapped for that name before the recipe is built. Only generic material names qualify -
 * a component like tiCasing stays specific to this mod.
 */
public final class OreRecipes {

    /** Checked in order; the first name a stack carries wins ("crushedPurified" before "crushed"). */
    private static final String[] PREFIXES = {"itemRubber", "ingot", "dust", "crushedPurified", "crushed"};

    /**
     * This mod's own spellings, each always registered next to the common one ModItems also
     * publishes (crushedCopper, crushedPurifiedCopper, ingotAluminum). A stack must resolve to the
     * SAME name as the other mod's item for the two to match, so these are passed over.
     */
    private static final String[] OWN_SPELLINGS = {"crushedOre", "purifiedCrushedOre", "ingotAluminium"};

    private OreRecipes() {
    }

    public static void shapeless(ItemStack output, Object... inputs) {
        GameRegistry.addRecipe(new ShapelessOreRecipe(output, convert(inputs)));
    }

    /** Same arguments as GameRegistry.addRecipe: pattern rows, then char / ingredient pairs. */
    public static void shaped(ItemStack output, Object... recipe) {
        GameRegistry.addRecipe(new ShapedOreRecipe(output, convert(recipe)));
    }

    private static Object[] convert(Object[] in) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] instanceof ItemStack ? oreName((ItemStack) in[i]) : in[i];
        }
        return out;
    }

    /** The shared material name to use in place of this exact stack, or the stack itself. */
    public static Object oreName(ItemStack stack) {
        String name = sharedName(stack);
        return name != null ? name : stack;
    }

    /** First generic material OreDict name `stack` is registered under, or null. */
    public static String sharedName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        int[] ids = OreDictionary.getOreIDs(stack);
        for (String prefix : PREFIXES) {
            for (int id : ids) {
                String name = OreDictionary.getOreName(id);
                if (name.startsWith(prefix) && !isOwnSpelling(name)) {
                    return name;
                }
            }
        }
        return null;
    }

    private static boolean isOwnSpelling(String name) {
        for (String own : OWN_SPELLINGS) {
            if (name.startsWith(own)) {
                return true;
            }
        }
        return false;
    }
}
