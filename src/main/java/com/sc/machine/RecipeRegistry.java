package com.sc.machine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Public API for registering/looking up machine recipes (03_claude_code_prompt.md: "собственный
 * RecipeRegistry с публичным API для аддонов"). Silicon-chain recipes (§3/§18.1) are
 * registered directly against this in step 5, since a machine's own process recipe is part of
 * its "Логика" (per that step's requirement) - generic ore-processing recipes shared across
 * many metals (Ore Washer/Centrifuge, §4/§11) are step 11's job.
 */
public final class RecipeRegistry {

    private static final Map<MachineType, List<MachineRecipe>> RECIPES = new EnumMap<MachineType, List<MachineRecipe>>(MachineType.class);

    private RecipeRegistry() {
    }

    public static void register(MachineRecipe recipe) {
        List<MachineRecipe> list = RECIPES.get(recipe.type);
        if (list == null) {
            list = new ArrayList<MachineRecipe>();
            RECIPES.put(recipe.type, list);
        }
        list.add(recipe);
    }

    /**
     * Picks the MOST SPECIFIC match, not the first one registered - several MachineType families
     * register multiple recipes that only differ by how much of one ingredient they need (e.g.
     * Rolling Machine's Lead Frame x3/x16/x40, all just N Copper Ingots). MachineRecipe.matches()
     * accepts a slot stack that's >= the required count (so a player can leave a full stack
     * sitting in the input, same as every other machine), which means every smaller-N recipe
     * also "matches" whenever a bigger-N recipe's input is present. Returning the first hit in
     * registration order silently made every larger recipe in such a family unreachable; picking
     * the highest total required-input count among all matches resolves it correctly instead.
     */
    public static MachineRecipe findMatch(MachineType type, ItemStack[] providedInputs, FluidStack providedFluidA, FluidStack providedFluidB) {
        List<MachineRecipe> list = RECIPES.get(type);
        if (list == null) {
            return null;
        }
        MachineRecipe best = null;
        int bestSpecificity = -1;
        for (MachineRecipe recipe : list) {
            if (recipe.matches(providedInputs, providedFluidA, providedFluidB)) {
                // More distinct ingredients wins first, then the bigger total: with rubber + 64
                // alumina in a Kiln, "rubber + 1 dust" (heat-resistant rubber) has to beat
                // "4 dust" (ceramic package) - on total count alone it never ran again once
                // automation had stocked 4+ dust.
                int specificity = ingredientKinds(recipe) * 1000000 + totalRequiredCount(recipe);
                if (specificity > bestSpecificity) {
                    bestSpecificity = specificity;
                    best = recipe;
                }
            }
        }
        return best;
    }

    private static int ingredientKinds(MachineRecipe recipe) {
        return recipe.inputs.length + (recipe.fluidInputA != null ? 1 : 0) + (recipe.fluidInputB != null ? 1 : 0);
    }

    private static int totalRequiredCount(MachineRecipe recipe) {
        int total = 0;
        for (ItemStack stack : recipe.inputs) {
            total += stack.stackSize;
        }
        if (recipe.fluidInputA != null) {
            total += recipe.fluidInputA.amount;
        }
        if (recipe.fluidInputB != null) {
            total += recipe.fluidInputB.amount;
        }
        return total;
    }

    /**
     * Whether `stack` is a solid ingredient of ANY recipe this machine knows - used by
     * TileEntityMachineSC.isItemValidForSlot/canInsertItem so the input slots reject junk
     * instead of letting a player or a hopper fill them with anything at all.
     */
    public static boolean isValidInput(MachineType type, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (type.isSmelter()) {
            return com.sc.tileentity.TileEntityMachineSC.smeltResult(stack) != null;
        }
        List<MachineRecipe> list = RECIPES.get(type);
        if (list == null) {
            return false;
        }
        for (MachineRecipe recipe : list) {
            for (ItemStack required : recipe.inputs) {
                if (MachineRecipe.isSameIngredient(stack, required)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether any recipe of this type ever touches machine tank `tank` (0/1 = fluid inputs A/B,
     * 2/3 = fluid outputs A/B) - the GUI hatches out the ones a machine never uses, so a Crusher
     * doesn't show four empty tanks that look like it's missing a fluid.
     */
    public static boolean usesTank(MachineType type, int tank) {
        if (tank < 2) {
            // Input tanks are filled first-free, not by recipe slot (see MachineRecipe.matches):
            // the second one is needed as soon as the machine takes two different fluids at all.
            return inputFluids(type).size() > tank;
        }
        for (MachineRecipe recipe : recipesFor(type)) {
            if ((tank == 2 ? recipe.fluidOutputA : recipe.fluidOutputB) != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether any recipe of this machine type consumes `fluid` - TileEntityMachineSC.fill() refuses the rest. */
    public static boolean isValidFluidInput(MachineType type, net.minecraftforge.fluids.Fluid fluid) {
        return fluid != null && inputFluids(type).contains(fluid.getName());
    }

    private static java.util.Set<String> inputFluids(MachineType type) {
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (MachineRecipe recipe : recipesFor(type)) {
            if (recipe.fluidInputA != null && recipe.fluidInputA.getFluid() != null) {
                names.add(recipe.fluidInputA.getFluid().getName());
            }
            if (recipe.fluidInputB != null && recipe.fluidInputB.getFluid() != null) {
                names.add(recipe.fluidInputB.getFluid().getName());
            }
        }
        return names;
    }

    public static List<MachineRecipe> recipesFor(MachineType type) {
        List<MachineRecipe> list = RECIPES.get(type);
        return list == null ? java.util.Collections.<MachineRecipe>emptyList() : list;
    }
}
