package com.sc.manual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.SCToolType;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * К7: the "Components and materials" chapter - every plain component, intermediate material,
 * machine consumable and cell of the mod, grouped, each with its icon, a line of what it is, and
 * where it is made and what it goes into - read from the recipe registries when the book is
 * built (machines, the crafting table, the furnace), so the lines follow the recipes. A crafting
 * grid (or the machine recipe that makes it) under each.
 */
final class BookMaterialsSC {

    /** The groups: an article id and its items (ModItems.component names; others resolved in stackOf). */
    static final String[][] GROUPS = {
            {"mat.electronics", "resistor", "capacitor", "tantalumCapacitor", "dielectric", "ceramicPackage", "leadFrame3", "leadFrame16",
                    "leadFrame40", "compound", "hfo2Die", "sensor", "lens", "quartzEmitter", "copperCoil", "energyCellLV", "energyCellMV", "energyCellHV"},
            {"mat.structure", "alFoil", "alFrame", "tiFrame", "tiPlate", "wTiPlate", "tiCasing", "steelCasing", "polymerPlate", "polymerHandle",
                    "ptfeSheet", "ceramicRod", "combustionChamber", "quartzChamber", "wBarrel", "focusLens", "sputterBacking"},
            {"mat.power", "nb3SnIngot", "nb3SnPlate", "nb3SnCoil", "heLoopModule", "liBlanketModule", "fusionCore", "powerCoreFrame",
                    "turbineBladeTitanium", "turbineBladeTungsten"},
            {"mat.ore", "crushedOreSC", "purifiedCrushedOreSC", "dustSC", "dustTinySC", "ingotSC", "coke", "limestoneSC", "rubber", "rubberBlue",
                    "rubberHeatResist"},
            {"mat.tools", "toolDIAMOND_WIRE", "toolDIAMOND_BLADE", "toolSEED_CRYSTAL", "toolPHOTOMASK", "toolSPUTTER_TARGET_COPPER",
                    "toolSPUTTER_TARGET_ALUMINIUM", "toolSPUTTER_TARGET_TUNGSTEN", "toolMOLD_PLATE", "toolMOLD_BLADE", "toolMOLD_COIL", "toolMOLD_TARGET",
                    "toolMOLD_LEAD_FRAME_3", "toolMOLD_LEAD_FRAME_16", "toolMOLD_LEAD_FRAME_40", "drillHeadSteel", "drillHeadTungsten", "drillHeadDiamond",
                    "drillHeadExo", "oreScanner"},
            {"mat.cells", "liquidHeCell", "deuteriumCell", "isotopeCapsule", "windRotor"}};

    /** Items with many kinds (metadata): shown as a row of icons, their G key on any kind. */
    private static final Set<String> KINDS = new LinkedHashSet<String>(Arrays.asList("crushedOreSC", "purifiedCrushedOreSC", "dustSC", "dustTinySC",
            "ingotSC"));

    private static final int SHOWN = 6;

    private BookMaterialsSC() {
    }

    /** An item of the mod by its registry name (meta 0), or null. */
    static ItemStack stackOf(String name) {
        Item item = (Item) Item.itemRegistry.getObject(com.sc.Reference.MODID + ":" + name);
        return item == null ? null : new ItemStack(item, 1, 0);
    }

    static void materials(List<BookEntry> list) {
        BookChapter c = BookChapter.MATERIALS;
        BookEntry all = new BookEntry("materials", c, new ItemStack(ModItems.component("capacitor")), Lang.tr("sc.book.mat.title"));
        all.add(BookEl.title(Lang.tr("sc.book.mat.title"))).add(BookEl.para(Lang.tr("sc.book.mat.intro")));
        List<ItemStack> icons = new ArrayList<ItemStack>();
        for (String[] g : GROUPS) {
            for (int i = 1; i < g.length; i++) {
                ItemStack s = stackOf(g[i]);
                if (s != null) {
                    icons.add(s);
                }
            }
        }
        all.add(BookEl.items(icons));
        for (String[] g : GROUPS) {
            all.add(BookEl.link(g[0], Lang.tr("sc.book." + g[0].replace('.', '_') + ".title")));
        }
        all.add(BookEl.dim(Lang.tr("sc.book.mat.hint")));
        list.add(all);
        for (String[] g : GROUPS) {
            ItemStack first = stackOf(g[1]);
            BookEntry e = new BookEntry(g[0], c, first, Lang.tr("sc.book." + g[0].replace('.', '_') + ".title"));
            e.add(BookEl.title(Lang.tr("sc.book." + g[0].replace('.', '_') + ".title")))
                    .add(BookEl.para(Lang.tr("sc.book." + g[0].replace('.', '_') + ".intro")));
            for (int i = 1; i < g.length; i++) {
                ItemStack s = stackOf(g[i]);
                if (s != null) {
                    entry(e, g[i], s);
                }
            }
            e.add(BookEl.link("materials", Lang.tr("sc.book.mat.title")));
            list.add(e);
        }
    }

    /** One item: icon, name and what it is; where it is made and what it goes into; a recipe card. */
    private static void entry(BookEntry e, String name, ItemStack s) {
        boolean kinds = KINDS.contains(name);
        boolean anyMeta = kinds || s.getItem().isDamageable();
        ItemStack any = new ItemStack(s.getItem(), 1, OreDictionary.WILDCARD_VALUE);
        e.add(BookEl.item(s, kinds ? Lang.tr("sc.book.mat.kind." + name) : s.getDisplayName(), describe(name, s)));
        if (kinds) {
            List<ItemStack> metas = new ArrayList<ItemStack>();
            for (com.sc.util.Material m : com.sc.util.Material.values()) {
                ItemStack k = stackFor(s.getItem(), m);
                if (k != null) {
                    metas.add(k);
                }
            }
            e.add(BookEl.items(metas));
        }
        String made = madeIn(s, anyMeta), used = kinds ? usedBy(s) : usedIn(s, anyMeta);
        if (made != null) {
            e.add(BookEl.dim(Lang.tr("sc.book.mat.made", made)));
        }
        if (used != null) {
            e.add(BookEl.dim(Lang.tr(kinds ? "sc.book.mat.usedby" : "sc.book.mat.used", used)));
        }
        if (!kinds) {
            List<IRecipe> crafts = BookContent.craftingFor(s);
            if (!crafts.isEmpty()) {
                e.add(BookEl.craft(crafts.get(0)));
            } else {
                MachineRecipe r = firstMaker(s, anyMeta);
                if (r != null) {
                    e.add(BookEl.machine(r));
                }
            }
        }
        e.about(anyMeta || kinds ? any : s);
        if (anyMeta && !kinds) {
            e.about(s);
        }
    }

    /** A material item of this kind for m, if the mod registered one (ItemMaterialSC.stackOf). */
    private static ItemStack stackFor(Item item, com.sc.util.Material m) {
        if (!(item instanceof com.sc.item.ItemMaterialSC)) {
            return null;
        }
        return ((com.sc.item.ItemMaterialSC) item).stackOf(m);
    }

    /** What the item is: its own line, a drill head's numbers, a tool's uses. */
    private static String describe(String name, ItemStack s) {
        if (s.getItem() instanceof com.sc.item.ItemDrillHeadSC) {
            com.sc.item.ItemDrillHeadSC.Kind k = ((com.sc.item.ItemDrillHeadSC) s.getItem()).kind;
            String hard = Lang.tr(k.maxHardness < 50 ? "sc.drillhead.noobsidian" : "sc.drillhead.all");
            return k.life > 0 ? Lang.tr("sc.book.mat.head", k.blocksPerSecond, BookContent.num(k.life), hard)
                    : Lang.tr("sc.book.mat.headexo", k.blocksPerSecond, hard);
        }
        if (s.getItem() instanceof com.sc.item.ItemToolSC) {
            SCToolType t = SCToolType.valueOf(name.substring(4));
            return Lang.tr("sc.book.mat.d." + name) + " " + Lang.tr("sc.book.mat.tool", t.durability);
        }
        return Lang.tr("sc.book.mat.d." + name);
    }

    // ------------------------------------------------------------------ where it is made, what it goes into

    static boolean same(ItemStack want, ItemStack have, boolean anyMeta) {
        return have != null && want != null && have.getItem() == want.getItem()
                && (anyMeta || have.getItemDamage() == want.getItemDamage() || have.getItemDamage() == OreDictionary.WILDCARD_VALUE);
    }

    /** A crafting recipe's ingredients: ItemStacks, or lists of them (the ore dictionary); empty for special recipes. */
    @SuppressWarnings("unchecked")
    static List<Object> ingredients(IRecipe r) {
        if (r instanceof ShapedRecipes) {
            return Arrays.asList((Object[]) ((ShapedRecipes) r).recipeItems);
        }
        if (r instanceof ShapelessRecipes) {
            return new ArrayList<Object>(((ShapelessRecipes) r).recipeItems);
        }
        if (r instanceof ShapedOreRecipe) {
            return Arrays.asList(((ShapedOreRecipe) r).getInput());
        }
        if (r instanceof ShapelessOreRecipe) {
            return new ArrayList<Object>(((ShapelessOreRecipe) r).getInput());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("rawtypes")
    static boolean ingredientIs(Object in, ItemStack want, boolean anyMeta) {
        if (in instanceof ItemStack) {
            return same(want, (ItemStack) in, anyMeta);
        }
        if (in instanceof List) {
            for (Object o : (List) in) {
                if (o instanceof ItemStack && same(want, (ItemStack) o, anyMeta)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MachineRecipe firstMaker(ItemStack s, boolean anyMeta) {
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                for (ItemStack o : r.outputs) {
                    if (same(s, o, anyMeta)) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    /** "Crusher, Ore Washer; Crafting table; Furnace" - who makes it (null: nothing). */
    static String madeIn(ItemStack s, boolean anyMeta) {
        Set<String> by = new LinkedHashSet<String>();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                boolean makes = false;
                for (ItemStack o : r.outputs) {
                    makes |= same(s, o, anyMeta);
                }
                for (ItemStack o : r.byproducts) {
                    makes |= same(s, o, anyMeta);
                }
                if (makes) {
                    by.add(type.localizedName());
                }
            }
        }
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            if (same(s, ((IRecipe) o).getRecipeOutput(), anyMeta)) {
                by.add(Lang.tr("sc.book.crafting"));
                break;
            }
        }
        for (Object o : FurnaceRecipes.smelting().getSmeltingList().values()) {
            if (o instanceof ItemStack && same(s, (ItemStack) o, anyMeta)) {
                by.add(new ItemStack(Blocks.furnace).getDisplayName());
                break;
            }
        }
        return join(by);
    }

    /** The products it goes into (machine recipes, crafting, the furnace), at most SHOWN named. */
    static String usedIn(ItemStack s, boolean anyMeta) {
        Set<String> into = new LinkedHashSet<String>();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                boolean uses = false;
                for (ItemStack in : r.inputs) {
                    uses |= same(s, in, anyMeta);
                }
                if (uses) {
                    into.add(product(r));
                }
            }
        }
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            if (r.getRecipeOutput() == null) {
                continue;
            }
            for (Object in : ingredients(r)) {
                if (ingredientIs(in, s, anyMeta)) {
                    into.add(name(r.getRecipeOutput()));
                    break;
                }
            }
        }
        for (Object o : FurnaceRecipes.smelting().getSmeltingList().entrySet()) {
            Map.Entry<?, ?> en = (Map.Entry<?, ?>) o;
            if (en.getKey() instanceof ItemStack && same(s, (ItemStack) en.getKey(), anyMeta) && en.getValue() instanceof ItemStack) {
                into.add(name((ItemStack) en.getValue()));
            }
        }
        into.remove(null);
        into.remove("");
        return join(into);
    }

    /** For a kind with many materials: the machines (and the crafting table / furnace) that take it. */
    static String usedBy(ItemStack s) {
        Set<String> by = new LinkedHashSet<String>();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                for (ItemStack in : r.inputs) {
                    if (same(s, in, true)) {
                        by.add(type.localizedName());
                    }
                }
            }
        }
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            boolean found = false;
            for (Object in : ingredients(r)) {
                found |= ingredientIs(in, s, true);
            }
            if (found) {
                by.add(Lang.tr("sc.book.crafting"));
                break;
            }
        }
        for (Object o : FurnaceRecipes.smelting().getSmeltingList().keySet()) {
            if (o instanceof ItemStack && same(s, (ItemStack) o, true)) {
                by.add(new ItemStack(Blocks.furnace).getDisplayName());
                break;
            }
        }
        return join(by);
    }

    /** A machine recipe's product: its first item, else its first fluid. */
    static String product(MachineRecipe r) {
        if (r.outputs.length > 0) {
            return name(r.outputs[0]);
        }
        FluidStack f = r.fluidOutputA != null ? r.fluidOutputA : r.fluidOutputB;
        return f != null && f.getFluid() != null ? f.getFluid().getLocalizedName(f) : null;
    }

    static String name(ItemStack s) {
        try {
            return s == null || s.getItem() == null ? null : s.getDisplayName();
        } catch (Throwable t) {
            return null;
        }
    }

    /** "a, b, c" - at most SHOWN, then "and N more"; null for none. */
    static String join(Set<String> names) {
        names.remove(null);
        if (names.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String s : names) {
            if (n == SHOWN) {
                sb.append(' ').append(Lang.tr("sc.book.mat.more", names.size() - SHOWN));
                break;
            }
            sb.append(n > 0 ? ", " : "").append(s);
            n++;
        }
        return sb.toString();
    }

    /** Every item the chapter covers (for the self-test). */
    static List<String> names() {
        List<String> out = new ArrayList<String>();
        for (String[] g : GROUPS) {
            out.addAll(Arrays.asList(g).subList(1, g.length));
        }
        return out;
    }
}
