package com.sc.debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sc.Reference;
import com.sc.energy.GeneratorType;
import com.sc.init.ModBlocks;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Developer tool: with -Dsc.dumpRecipes=<file> (see SCMod.preInit) writes every registered mod
 * item variant, every crafting/smelting/machine recipe and the machine/generator blocks to a
 * TSV that an offline reachability check reads ("can every item actually be obtained?").
 */
public final class RecipeDumpSC {

    private RecipeDumpSC() {
    }

    public static void dump(String path) {
        try {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(path)), "UTF-8"));
            dumpItems(out);
            for (MachineType type : MachineType.values()) {
                int ord = type.ordinal();
                out.println("B\tmachine\t" + type.name() + "\t" + name(new ItemStack(ord < 16 ? ModBlocks.machineSC : ModBlocks.machineSC2, 1, ord % 16)));
                for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                    out.println("M\t" + type.name() + "\t" + stacks(r.inputs) + "\t" + fluid(r.fluidInputA) + "\t" + fluid(r.fluidInputB)
                            + "\t" + stacks(r.outputs) + "\t" + fluid(r.fluidOutputA) + "\t" + fluid(r.fluidOutputB) + "\t" + stacks(r.byproducts));
                }
            }
            for (GeneratorType type : GeneratorType.values()) {
                out.println("B\tgenerator\t" + type.name() + "\t" + name(new ItemStack(ModBlocks.generatorSC, 1, type.ordinal()))
                        + "\t" + type.kind + "\t" + type.fuelFluidName);
            }
            for (Object o : CraftingManager.getInstance().getRecipeList()) {
                IRecipe recipe = (IRecipe) o;
                ItemStack output = recipe.getRecipeOutput();
                if (output == null || output.getItem() == null) {
                    continue;
                }
                StringBuilder in = new StringBuilder();
                if (recipe instanceof ShapedRecipes) {
                    for (ItemStack s : ((ShapedRecipes) recipe).recipeItems) {
                        appendInput(in, s);
                    }
                } else if (recipe instanceof ShapelessRecipes) {
                    for (Object s : ((ShapelessRecipes) recipe).recipeItems) {
                        appendInput(in, s);
                    }
                } else if (recipe instanceof ShapedOreRecipe) {
                    for (Object s : ((ShapedOreRecipe) recipe).getInput()) {
                        appendInput(in, s);
                    }
                } else if (recipe instanceof ShapelessOreRecipe) {
                    for (Object s : ((ShapelessOreRecipe) recipe).getInput()) {
                        appendInput(in, s);
                    }
                } else {
                    in.append("?").append(recipe.getClass().getName());
                }
                out.println("C\t" + name(output) + "\t" + in);
            }
            for (Object e : FurnaceRecipes.smelting().getSmeltingList().entrySet()) {
                Map.Entry entry = (Map.Entry) e;
                out.println("F\t" + name((ItemStack) entry.getKey()) + "\t" + name((ItemStack) entry.getValue()));
            }
            out.close();
            System.out.println("[SC-DUMP] written to " + path);
        } catch (Throwable t) {
            System.out.println("[SC-DUMP] FAILED " + t);
            t.printStackTrace(System.out);
        }
    }

    /**
     * Every distinct variant of every mod item: metadata 0..255 deduplicated by unlocalized
     * name (getSubItems is client-only, so it can't be used on a dedicated server).
     */
    private static void dumpItems(PrintWriter out) {
        for (Object key : Item.itemRegistry.getKeys()) {
            String id = key.toString();
            if (!id.startsWith(Reference.MODID + ":")) {
                continue;
            }
            Item item = (Item) Item.itemRegistry.getObject(id);
            int maxMeta = item.getHasSubtypes() ? 255 : 0;
            Set<String> seen = new HashSet<String>();
            for (int meta = 0; meta <= maxMeta; meta++) {
                String unloc;
                try {
                    unloc = item.getUnlocalizedName(new ItemStack(item, 1, meta));
                } catch (Throwable t) {
                    continue;
                }
                if (unloc != null && seen.add(unloc)) {
                    out.println("I\t" + id + "@" + meta + "\t" + unloc);
                }
            }
        }
    }

    private static void appendInput(StringBuilder sb, Object s) {
        if (s == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(",");
        }
        if (s instanceof ItemStack) {
            sb.append(name((ItemStack) s));
        } else if (s instanceof List) {
            StringBuilder alt = new StringBuilder();
            for (Object a : (List) s) {
                if (alt.length() > 0) {
                    alt.append("|");
                }
                alt.append(name((ItemStack) a));
            }
            sb.append(alt.length() == 0 ? "EMPTY_OREDICT" : alt.toString());
        } else {
            sb.append("?").append(s.getClass().getSimpleName());
        }
    }

    private static String stacks(ItemStack[] stacks) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : stacks) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(name(s));
        }
        return sb.toString();
    }

    private static String fluid(FluidStack f) {
        return f == null || f.getFluid() == null ? "" : "fluid:" + f.getFluid().getName() + "*" + f.amount;
    }

    private static String name(ItemStack s) {
        if (s == null) {
            return "NULLSTACK";
        }
        if (s.getItem() == null) {
            return "NULLITEM";
        }
        Object n = Item.itemRegistry.getNameForObject(s.getItem());
        return (n == null ? "UNREGISTERED" : n.toString()) + "@" + s.getItemDamage() + "*" + s.stackSize;
    }
}
