package com.sc.machine;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * One registered recipe for a MachineType, per §3/§13/§18.1 - inputs/outputs/ticks/defect all
 * live here (not on MachineType) because the same machine can have multiple recipes with
 * different numbers (e.g. Oxidation Furnace's oxidize-step vs anneal-step, §3 steps 6 and 11).
 *
 * Two fluid inputs (CVD Chamber needs SiHCl3 *and* H2; Etching Bath needs Developer *and* HF)
 * and one fluid output (only Chem Reactor's SiHCl3, §3 step 3) cover everything in the chain -
 * see TileEntityMachineSC's two input tanks + one output tank.
 *
 * A tool ingredient (Diamond Wire, Seed Crystal, Photomask, Sputter Target - §13.5) is just a
 * normal entry in `inputs`; TileEntityMachineSC tells them apart from a consumable at
 * consumption time via Item.isDamageable(), not via anything on this class.
 */
public class MachineRecipe {

    public final MachineType type;
    public final ItemStack[] inputs;
    public final FluidStack fluidInputA;
    public final FluidStack fluidInputB;
    public final FluidStack fluidOutputA;
    public final FluidStack fluidOutputB;
    public final ItemStack[] outputs;
    public final int ticks;
    /** 0..1, §13.2: on a defect roll the recipe's normal outputs are replaced by dustScrapSC instead. */
    public final float defectChance;
    /**
     * §11.2's Centrifuge by-products: extra stacks rolled independently of the main output
     * ("dustTinySilver 12.5%", "dustTinyGallium 6%" and so on). Separate from `outputs` because
     * those are guaranteed and these are not, and separate from defectChance because a defect
     * REPLACES the output while a by-product accompanies it.
     */
    public final ItemStack[] byproducts;
    public final float[] byproductChances;

    private static final ItemStack[] NO_BYPRODUCTS = new ItemStack[0];
    private static final float[] NO_CHANCES = new float[0];

    public MachineRecipe(MachineType type, ItemStack[] inputs, FluidStack fluidInputA, FluidStack fluidInputB,
                          ItemStack[] outputs, FluidStack fluidOutputA, FluidStack fluidOutputB, int ticks, float defectChance) {
        this(type, inputs, fluidInputA, fluidInputB, outputs, fluidOutputA, fluidOutputB, ticks, defectChance,
                NO_BYPRODUCTS, NO_CHANCES);
    }

    public MachineRecipe(MachineType type, ItemStack[] inputs, FluidStack fluidInputA, FluidStack fluidInputB,
                          ItemStack[] outputs, FluidStack fluidOutputA, FluidStack fluidOutputB, int ticks, float defectChance,
                          ItemStack[] byproducts, float[] byproductChances) {
        if (byproducts.length != byproductChances.length) {
            throw new IllegalArgumentException("byproducts/chances length mismatch for " + type);
        }
        this.type = type;
        this.inputs = inputs;
        this.fluidInputA = fluidInputA;
        this.fluidInputB = fluidInputB;
        this.outputs = outputs;
        this.fluidOutputA = fluidOutputA;
        this.fluidOutputB = fluidOutputB;
        this.ticks = ticks;
        this.defectChance = defectChance;
        this.byproducts = byproducts;
        this.byproductChances = byproductChances;
    }

    /** @param providedInputs the machine's input slots (may contain nulls/other items) */
    public boolean matches(ItemStack[] providedInputs, FluidStack providedFluidA, FluidStack providedFluidB) {
        // Either tank may hold either fluid: TileEntityMachineSC.fill() puts a new fluid into the
        // first free input tank, so which tank it lands in depends only on what the player piped
        // in first. Positional matching made a CVD Chamber fed hydrogen before SiHCl3 (or a Boiler
        // fed diesel before water) wait forever, since input tanks can't be emptied again.
        boolean straight = fluidSatisfied(fluidInputA, providedFluidA) && fluidSatisfied(fluidInputB, providedFluidB);
        boolean swapped = fluidSatisfied(fluidInputA, providedFluidB) && fluidSatisfied(fluidInputB, providedFluidA);
        if (!straight && !swapped) {
            return false;
        }
        boolean[] used = new boolean[providedInputs.length];
        for (ItemStack required : inputs) {
            if (!consumeOneMatch(providedInputs, used, required)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fluidSatisfied(FluidStack required, FluidStack provided) {
        if (required == null) {
            return true;
        }
        return provided != null && provided.isFluidEqual(required) && provided.amount >= required.amount;
    }

    private boolean consumeOneMatch(ItemStack[] providedInputs, boolean[] used, ItemStack required) {
        for (int i = 0; i < providedInputs.length; i++) {
            if (used[i]) {
                continue;
            }
            if (ingredientMatches(providedInputs[i], required)) {
                used[i] = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a slot's stack can serve as `required`. The damage value only has to match for a
     * NON-damageable item, where damage is the subtype (which dust/ingot/wafer this is). On a
     * tool (§13.5) damage is the durability counter instead, so a partly-worn Diamond Wire still
     * satisfies a recipe that lists a fresh one - comparing it exactly meant every tool matched
     * on its first operation only and the machine then sat idle with a perfectly good tool in it.
     *
     * TileEntityMachineSC.consumeInputs() picks the slot to actually consume from with this same
     * method on purpose: if the two ever disagreed, a recipe could complete while consuming
     * nothing (free infinite tool) or match a slot it can't spend.
     */
    public static boolean ingredientMatches(ItemStack provided, ItemStack required) {
        return isSameIngredient(provided, required) && provided.stackSize >= required.stackSize;
    }

    /**
     * ingredientMatches() without the "enough of it" part - for deciding whether an item BELONGS
     * in an input slot at all (TileEntityMachineSC.isItemValidForSlot). A player feeding a
     * 4-ingot recipe one ingot at a time has to be allowed to put the first one in.
     */
    public static boolean isSameIngredient(ItemStack provided, ItemStack required) {
        if (provided == null) {
            return false;
        }
        if (provided.getItem() == required.getItem()
                && (provided.getItem().isDamageable() || provided.getItemDamage() == required.getItemDamage())) {
            return true;
        }
        // Another mod's copper ingot, crushed ore or rubber is the same ingredient as ours when
        // both are registered under the same generic material name - see OreRecipes.
        String requiredName = com.sc.init.OreRecipes.sharedName(required);
        return requiredName != null && requiredName.equals(com.sc.init.OreRecipes.sharedName(provided));
    }
}
