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

    /**
     * МШ-8 (audit 2026-10-20): the inputs' shared material names (OreRecipes.sharedName), worked out on the first comparison and
     * kept - findMatch walks every recipe every tick a machine waits. forgetNames() (a server start: MineTweaker's /mt reload and
     * the like may change the dictionary) makes them be worked out again. One holder, swapped whole: a client's NEI and the
     * integrated server's thread may both ask.
     */
    private volatile Names names;
    private static volatile int GEN;

    private static final class Names {
        final int gen;
        final String[] of;

        Names(int gen, String[] of) {
            this.gen = gen;
            this.of = of;
        }
    }

    /** МШ-8: the cached input names are worked out afresh on their next use (SCMod.serverStarting). */
    public static void forgetNames() {
        GEN++;
    }

    /** МШ-8: input `i`'s shared material name, null - none (cached, see forgetNames). */
    String inputName(int i) {
        int g = GEN;
        Names n = names;
        if (n == null || n.gen != g) {
            String[] of = new String[inputs.length];
            for (int k = 0; k < inputs.length; k++) {
                of[k] = com.sc.init.OreRecipes.sharedName(inputs[k]);
            }
            n = new Names(g, of);
            names = n;
        }
        return n.of[i];
    }

    /**
     * МШ-8: the input slots' shared names for one findMatch - each slot's looked up at most once, and only when a recipe gets as
     * far as asking (its item didn't match by itself).
     */
    public static final class SlotNames {
        private final ItemStack[] stacks;
        private final String[] names;
        private final boolean[] done;

        public SlotNames(ItemStack[] stacks) {
            this.stacks = stacks;
            this.names = new String[stacks.length];
            this.done = new boolean[stacks.length];
        }

        String name(int i) {
            if (!done[i]) {
                names[i] = com.sc.init.OreRecipes.sharedName(stacks[i]);
                done[i] = true;
            }
            return names[i];
        }
    }

    /** @param providedInputs the machine's input slots (may contain nulls/other items) */
    public boolean matches(ItemStack[] providedInputs, FluidStack providedFluidA, FluidStack providedFluidB) {
        return matches(providedInputs, providedFluidA, providedFluidB, new SlotNames(providedInputs));
    }

    /** МШ-8: matches() with the slots' names shared between the recipes of one search (RecipeRegistry.findMatch). */
    public boolean matches(ItemStack[] providedInputs, FluidStack providedFluidA, FluidStack providedFluidB, SlotNames slotNames) {
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
        for (int r = 0; r < inputs.length; r++) {
            if (!consumeOneMatch(providedInputs, used, r, slotNames)) {
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

    private boolean consumeOneMatch(ItemStack[] providedInputs, boolean[] used, int r, SlotNames slotNames) {
        ItemStack required = inputs[r];
        for (int i = 0; i < providedInputs.length; i++) {
            if (used[i]) {
                continue;
            }
            ItemStack provided = providedInputs[i];
            if (provided == null || provided.stackSize < required.stackSize) {
                continue;
            }
            if (sameItem(provided, required) || inputName(r) != null && inputName(r).equals(slotNames.name(i))) {
                used[i] = true;
                return true;
            }
        }
        return false;
    }

    /**
     * МШ-8: ingredientMatches() for this recipe's input `r`, with its cached name - TileEntityMachineSC.consumeInputs picks the
     * slot to spend with it, so it compares exactly as matches() does.
     */
    public boolean inputMatches(int r, ItemStack provided) {
        ItemStack required = inputs[r];
        return provided != null && provided.stackSize >= required.stackSize
                && isSameIngredient(provided, sameItem(provided, required) ? null : com.sc.init.OreRecipes.sharedName(provided), required,
                inputName(r));
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
        if (sameItem(provided, required)) {
            return true;
        }
        // Another mod's copper ingot, crushed ore or rubber is the same ingredient as ours when
        // both are registered under the same generic material name - see OreRecipes.
        String requiredName = com.sc.init.OreRecipes.sharedName(required);
        return requiredName != null && isSameIngredient(provided, com.sc.init.OreRecipes.sharedName(provided), required, requiredName);
    }

    /**
     * МШ-8: isSameIngredient with the shared names already looked up (null - none): the item and its damage, else the same
     * generic material name.
     */
    public static boolean isSameIngredient(ItemStack provided, String providedName, ItemStack required, String requiredName) {
        if (provided == null) {
            return false;
        }
        return sameItem(provided, required) || requiredName != null && requiredName.equals(providedName);
    }

    /**
     * The same item: the damage counts for a non-damageable item only (the subtype); МШ-7: a required damage of
     * OreDictionary.WILDCARD_VALUE (another mod's ore registered for every meta) matches any.
     */
    private static boolean sameItem(ItemStack provided, ItemStack required) {
        return provided.getItem() == required.getItem() && (provided.getItem().isDamageable()
                || required.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                || provided.getItemDamage() == required.getItemDamage());
    }
}
