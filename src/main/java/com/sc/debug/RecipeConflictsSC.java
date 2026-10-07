package com.sc.debug;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Developer check (PackCheckSC, -Dsc.packcheck): crafting-table recipes of this mod that another mod's recipe
 * also matches - the same grid gives the other mod's result instead (a copper ingot alone: our bare cable or
 * another mod's nuggets). Each recipe's grid is laid out from its own ingredients (every candidate of an ore
 * dictionary entry in turn) and every recipe of the other side is asked whether it matches it - both ways.
 */
public final class RecipeConflictsSC {

    private static final int MAX_VARIANTS = 12;

    private RecipeConflictsSC() {
    }

    private static final class Dummy extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer p) {
            return true;
        }
    }

    public static List<String> run(World w) {
        @SuppressWarnings("unchecked")
        List<IRecipe> all = new ArrayList<IRecipe>(CraftingManager.getInstance().getRecipeList());
        List<IRecipe> ours = new ArrayList<IRecipe>(), others = new ArrayList<IRecipe>();
        for (IRecipe r : all) {
            (isOurs(r) ? ours : others).add(r);
        }
        Set<String> found = new LinkedHashSet<String>();
        InventoryCrafting inv = new InventoryCrafting(new Dummy(), 3, 3);
        check(ours, others, inv, w, found, true);
        check(others, ours, inv, w, found, false);
        List<String> out = new ArrayList<String>();
        out.add("# recipes: ours " + ours.size() + ", others " + others.size() + ", conflicts " + found.size());
        out.addAll(found);
        return out;
    }

    private static void check(List<IRecipe> from, List<IRecipe> against, InventoryCrafting inv, World w, Set<String> found, boolean oursFirst) {
        for (IRecipe r : from) {
            List<ItemStack[]> grids;
            try {
                grids = grids(r);
            } catch (Throwable t) {
                continue;
            }
            for (ItemStack[] g : grids) {
                for (int i = 0; i < 9; i++) {
                    inv.setInventorySlotContents(i, g[i]);
                }
                for (IRecipe o : against) {
                    boolean m;
                    try {
                        m = o.matches(inv, w);
                    } catch (Throwable t) {
                        m = false;
                    }
                    if (!m || sameOutput(r, o)) {
                        continue;
                    }
                    IRecipe our = oursFirst ? r : o, their = oursFirst ? o : r;
                    found.add(describe(our) + "  <->  " + describe(their) + "  | grid: " + gridText(g));
                }
            }
        }
    }

    private static boolean isOurs(IRecipe r) {
        if (r.getClass().getName().startsWith("com.sc.")) {
            return true;
        }
        ItemStack out = r.getRecipeOutput();
        return out != null && out.getItem() != null && "SiliconAge".equals(modOf(out.getItem()));
    }

    private static String modOf(Item item) {
        try {
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
            return id == null ? "?" : id.modId;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static boolean sameOutput(IRecipe a, IRecipe b) {
        ItemStack x = a.getRecipeOutput(), y = b.getRecipeOutput();
        return x != null && y != null && x.getItem() == y.getItem() && x.getItemDamage() == y.getItemDamage();
    }

    private static String describe(IRecipe r) {
        ItemStack out = r.getRecipeOutput();
        String name = out == null || out.getItem() == null ? "?" : safeName(out) + " x" + out.stackSize;
        String mod = out == null || out.getItem() == null ? "?" : modOf(out.getItem());
        return "[" + mod + "] " + name + " (" + r.getClass().getSimpleName() + ")";
    }

    private static String safeName(ItemStack s) {
        try {
            return s.getDisplayName() + " {" + Item.itemRegistry.getNameForObject(s.getItem()) + ":" + s.getItemDamage() + "}";
        } catch (Throwable t) {
            return String.valueOf(Item.itemRegistry.getNameForObject(s.getItem()));
        }
    }

    private static String gridText(ItemStack[] g) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            b.append(i % 3 == 0 && i > 0 ? " / " : i > 0 ? ", " : "");
            b.append(g[i] == null ? "-" : String.valueOf(Item.itemRegistry.getNameForObject(g[i].getItem())) + ":" + g[i].getItemDamage());
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ grids from a recipe's own ingredients

    /**
     * A recipe of this mod whose grid holds one single ingredient that isn't this mod's own item (an ore dictionary
     * entry or another mod's / vanilla item): such a grid is someone else's recipe too, sooner or later. Null if fine.
     */
    public static String loneShared(IRecipe r) {
        Object[] in;
        try {
            in = r instanceof ShapedRecipes ? ((ShapedRecipes) r).recipeItems : r instanceof ShapedOreRecipe ? ((ShapedOreRecipe) r).getInput()
                    : r instanceof ShapelessRecipes ? ((ShapelessRecipes) r).recipeItems.toArray()
                    : r instanceof ShapelessOreRecipe ? ((ShapelessOreRecipe) r).getInput().toArray() : null;
        } catch (Throwable t) {
            return null;
        }
        if (in == null || !isOurs(r)) {
            return null;
        }
        Object only = null;
        int n = 0;
        for (Object o : in) {
            if (o != null) {
                n++;
                only = o;
            }
        }
        if (n != 1) {
            return null;
        }
        boolean shared = only instanceof List || only instanceof ItemStack && ((ItemStack) only).getItem() != null
                && !"SiliconAge".equals(modOf(((ItemStack) only).getItem()));
        return shared ? describe(r) : null;
    }

    static List<ItemStack[]> grids(IRecipe r) throws Exception {
        int w, h;
        Object[] in;
        boolean shaped;
        if (r instanceof ShapedRecipes) {
            ShapedRecipes s = (ShapedRecipes) r;
            w = s.recipeWidth;
            h = s.recipeHeight;
            in = s.recipeItems;
            shaped = true;
        } else if (r instanceof ShapedOreRecipe) {
            in = ((ShapedOreRecipe) r).getInput();
            w = intField(ShapedOreRecipe.class, r, "width");
            h = intField(ShapedOreRecipe.class, r, "height");
            shaped = true;
        } else if (r instanceof ShapelessRecipes) {
            in = ((ShapelessRecipes) r).recipeItems.toArray();
            w = 3;
            h = 3;
            shaped = false;
        } else if (r instanceof ShapelessOreRecipe) {
            in = ((ShapelessOreRecipe) r).getInput().toArray();
            w = 3;
            h = 3;
            shaped = false;
        } else {
            return new ArrayList<ItemStack[]>();
        }
        int variants = 1;
        for (Object o : in) {
            if (o instanceof List) {
                variants = Math.max(variants, Math.min(MAX_VARIANTS, ((List<?>) o).size()));
            }
        }
        List<ItemStack[]> out = new ArrayList<ItemStack[]>();
        for (int v = 0; v < variants; v++) {
            ItemStack[] g = new ItemStack[9];
            boolean ok = true;
            for (int i = 0; i < in.length && ok; i++) {
                ItemStack s = pick(in[i], v);
                if (in[i] != null && s == null) {
                    ok = false;                               // an empty ore list: no grid
                }
                int slot = shaped ? (i / w) * 3 + i % w : i;
                if (slot < 9) {
                    g[slot] = s;
                }
            }
            if (ok) {
                out.add(g);
            }
        }
        return out;
    }

    private static ItemStack pick(Object o, int v) {
        ItemStack s = null;
        if (o instanceof ItemStack) {
            s = (ItemStack) o;
        } else if (o instanceof List && !((List<?>) o).isEmpty()) {
            List<?> l = (List<?>) o;
            Object e = l.get(v % l.size());
            s = e instanceof ItemStack ? (ItemStack) e : null;
        } else if (o instanceof Item) {
            s = new ItemStack((Item) o);
        }
        if (s == null || s.getItem() == null) {
            return null;
        }
        ItemStack c = s.copy();
        c.stackSize = 1;
        if (c.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
            c.setItemDamage(0);
        }
        return c;
    }

    private static int intField(Class<?> c, Object o, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(o);
    }
}
