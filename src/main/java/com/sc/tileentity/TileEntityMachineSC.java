package com.sc.tileentity;

import java.util.Random;

import com.sc.energy.ExplosionLogic;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineStatus;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.machine.UpgradeType;
import com.sc.util.InvUtilSC;
import com.sc.util.Material;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * One class drives every silicon-chain machine (§13), parameterized by MachineType from block
 * metadata - same pattern as TileEntityCableSC/TileEntityPipeSC. Slots 0-2 are inputs, 3-5 are
 * outputs (unused slots simply stay empty for recipes that need fewer - §13's machines range
 * from 1 to 3 solid inputs). Two input FluidTanks cover the two machines that need simultaneous
 * different fluids (CVD Chamber: SiHCl3+H2; Etching Bath: Developer+HF); one output FluidTank
 * covers the chain's only fluid-producing step (Chem Reactor's SiHCl3, §3 step 3).
 *
 * A tool ingredient (Diamond Wire, Seed Crystal, Photomask, Sputter Target - §13.5) needs no
 * special recipe-side marker: consumeInputs() tells it apart from a plain consumable via
 * ItemToolSC and damages it by 1 instead of shrinking the stack.
 *
 * Two output FluidTanks (§14, added in step 6) cover Air Separator's simultaneous O2+Ar output -
 * everything else in the mod only ever produces at most one fluid at a time.
 *
 * Every side does everything (Ender IO style - the conduits decide what goes where): items and
 * fluids go into the inputs from any face and the products come out of any face, energy comes in
 * on any face. The machine itself pushes nothing out; a pipe / tube connector set to extract
 * takes the products, or an ejector upgrade pushes them into whatever is next to it.
 *
 * Four upgrade slots (IC2 style, UpgradeType): overclocker, transformer, energy storage, ejector,
 * puller, heat sink, quality control. Not reachable by automation.
 */
public class TileEntityMachineSC extends TileEntityEnergyBase implements ISidedInventory, IFluidHandler {

    public static final int INPUT_SLOTS = 3;
    public static final int OUTPUT_SLOTS = 3;
    public static final int UPGRADE_SLOTS = 4;
    public static final int FIRST_UPGRADE_SLOT = INPUT_SLOTS + OUTPUT_SLOTS;
    /** The battery slot under the energy gauge (a portable battery tops up the buffer). */
    public static final int SLOT_BATTERY = FIRST_UPGRADE_SLOT + UPGRADE_SLOTS;
    private static final int SLOT_COUNT = SLOT_BATTERY + 1;
    /** Inputs, outputs and the battery - what automation sees from every side. */
    private static final int[] IO_SLOTS = {0, 1, 2, 3, 4, 5, SLOT_BATTERY};
    /** mB an ejector / puller moves per tank per tick. */
    private static final int FLUID_MOVE = 1000;
    /** Items a puller takes per pull (every PULL_INTERVAL ticks). */
    private static final int PULL_ITEMS = 16;
    private static final int PULL_INTERVAL = 4;
    public static final int TANK_CAPACITY = 4000; // TODO(design doc): no machine tank capacity is specified anywhere; defaulted.
    /** With four Tank Extension upgrades. */
    public static final int MAX_TANK_CAPACITY = TANK_CAPACITY + UpgradeType.MAX_TANK_UPGRADES * UpgradeType.TANK_PER_UPGRADE;

    private static final int HEAT_CAPACITY = 100;
    private static final int HEAT_OVERFLOW = 150;
    private static final int HEAT_GAIN_PER_TICK = 1;
    private static final int HEAT_DISSIPATION_PER_TICK = 2;
    /** Once overheated, the operation stays paused until the machine has cooled back to this. */
    public static final int HEAT_RESUME = 70;
    /**
     * Heat sinks also carry heat away while the machine works: -1 heat per sink every this many
     * working ticks (on top of the slower heating, 1 / (n+1)). Net per tick: 1 sink +1/3 (a stall
     * after ~300 ticks instead of 100), 2 sinks 0, 3+ below 0 - a 1200-tick Czochralski run needs 2.
     */
    public static final int HEAT_SINK_INTERVAL = 6;

    private static final Random RANDOM = new Random();

    private MachineType machineType = MachineType.CRUSHER;
    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private final FluidTank tankA = new FluidTank(TANK_CAPACITY);
    private final FluidTank tankB = new FluidTank(TANK_CAPACITY);
    private final FluidTank outputTankA = new FluidTank(TANK_CAPACITY);
    private final FluidTank outputTankB = new FluidTank(TANK_CAPACITY);
    private int progressTicks;
    private int currentRecipeTicks;
    private MachineRecipe activeRecipe;
    private int heat;
    /** Paused for heat and still cooling toward HEAT_RESUME. */
    private boolean coolingDown;
    /** The running operation had to pause for heat at least once - its defect chance doubles (13.3). */
    private boolean overheatedThisRun;
    private MachineStatus status = MachineStatus.IDLE;

    public void setMachineType(MachineType machineType) {
        this.machineType = machineType;
        setTier(machineType.tier);
    }

    public MachineType getMachineType() {
        return machineType;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getCurrentRecipeTicks() {
        return currentRecipeTicks;
    }

    public int getHeat() {
        return heat;
    }

    public MachineStatus getStatus() {
        return status;
    }

    // ---- upgrades ----

    /** How many upgrades of a kind sit in the upgrade slots (effects stop growing at MAX_EFFECTIVE). */
    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (int i = FIRST_UPGRADE_SLOT; i < FIRST_UPGRADE_SLOT + UPGRADE_SLOTS; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return Math.min(n, UpgradeType.MAX_EFFECTIVE);
    }

    /** Recipe time with overclockers: x0.7 each, at least one tick. */
    public int effectiveTicks(MachineRecipe recipe) {
        return Math.max(1, (int) Math.round(recipe.ticks * Math.pow(0.7, upgradeCount(UpgradeType.OVERCLOCKER))
                / com.sc.util.ConfigSC.machineSpeed));
    }

    /** Energy per working tick: x1.6 per overclocker, x1.25 per quality control. */
    public int effectiveEuPerTick() {
        double eu = machineType.euPerTick * Math.pow(1.6, upgradeCount(UpgradeType.OVERCLOCKER))
                * Math.pow(1.25, upgradeCount(UpgradeType.QUALITY)) * com.sc.util.ConfigSC.machineEnergy
                * com.sc.util.ConfigSC.machineSpeed;                     // faster, not cheaper: EU an operation stays
        return (int) Math.min(Integer.MAX_VALUE / 4, Math.ceil(eu));
    }

    /** A type's EU/t with no upgrades, after the config's machineEnergy / machineSpeed (for NEI - as effectiveEuPerTick). */
    public static int configEuPerTick(MachineType type) {
        double eu = type.euPerTick * (double) com.sc.util.ConfigSC.machineEnergy * com.sc.util.ConfigSC.machineSpeed;
        return (int) Math.min(Integer.MAX_VALUE / 4, Math.ceil(eu));
    }

    /** Ticks with no upgrades, after the config's machineSpeed (for NEI - as effectiveTicks / smeltTicks). */
    public static int configTicks(int ticks) {
        return Math.max(1, (int) Math.round(ticks / (double) com.sc.util.ConfigSC.machineSpeed));
    }

    /**
     * Each transformer upgrade takes one tier higher voltage without exploding (IC2 style); a
     * universal transformer upgrade takes any voltage at all.
     */
    @Override
    public boolean acceptsAnyVoltage() {
        return upgradeCount(UpgradeType.UNIVERSAL_TRANSFORMER) > 0;
    }

    @Override
    public com.sc.energy.Tier inputTier() {
        com.sc.energy.Tier[] tiers = com.sc.energy.Tier.values();
        if (upgradeCount(UpgradeType.UNIVERSAL_TRANSFORMER) > 0) {
            return tiers[tiers.length - 1];
        }
        return tiers[Math.min(tiers.length - 1, getTier().ordinal() + upgradeCount(UpgradeType.TRANSFORMER))];
    }

    /**
     * Tier buffer + 10 000 EU per storage upgrade - and never less than two working ticks, or a
     * machine with enough overclockers would need more per tick than it can ever hold and sit
     * at "no power" for good.
     */
    @Override
    public int getMaxEnergyStored() {
        int buffer = super.getMaxEnergyStored() + upgradeCount(UpgradeType.ENERGY_STORAGE) * UpgradeType.STORAGE_PER_UPGRADE;
        return Math.max(buffer, 2 * effectiveEuPerTick());
    }

    /** Client-side sync only. */
    public void setStatusClient(MachineStatus value) {
        this.status = value;
    }

    /** Client-side sync only - see TileEntityEnergyBase.setEnergyStoredClient(). */
    public void setProgressTicksClient(int value) {
        this.progressTicks = value;
    }

    public void setCurrentRecipeTicksClient(int value) {
        this.currentRecipeTicks = value;
    }

    public void setHeatClient(int value) {
        this.heat = value;
    }

    public FluidTank getOutputTankA() {
        return outputTankA;
    }

    public FluidTank getOutputTankB() {
        return outputTankB;
    }

    /** Every tank's size: 4000 mB (the Matter Compressor: SM_TANK) + 8000 per Tank Extension upgrade (up to 4). */
    public int tankCapacity() {
        return (machineType.isCompressor() ? SM_TANK : TANK_CAPACITY)
                + Math.min(UpgradeType.MAX_TANK_UPGRADES, upgradeCount(UpgradeType.TANK_EXTENSION)) * UpgradeType.TANK_PER_UPGRADE;
    }

    /**
     * The tanks follow the upgrades. Taking upgrades out doesn't pour anything away: a tank then
     * holding more than it can takes nothing in until it has been used down.
     */
    private void syncTankCapacity() {
        int c = tankCapacity();
        if (tankA.getCapacity() != c) {
            for (FluidTank t : new FluidTank[]{tankA, tankB, outputTankA, outputTankB}) {
                t.setCapacity(c);
            }
        }
    }

    /** EU to pour out a tank (1 per 10 mB). */
    public int clearCost(int index) {
        return (getTank(index).getFluidAmount() + UpgradeType.CLEAR_MB_PER_EU - 1) / UpgradeType.CLEAR_MB_PER_EU;
    }

    /** The screen's Clear button: the tank is emptied for EU from the buffer - only if it can pay it all. */
    public boolean clearTank(int index) {
        FluidTank t = getTank(index);
        int cost = clearCost(index);
        if (t.getFluidAmount() <= 0 || getEnergyStored() < cost) {
            return false;
        }
        removeEnergy(cost);
        t.setFluid(null);
        markDirty();
        return true;
    }

    /** Indexed view of the four tanks (0/1 = fluid inputs, 2/3 = fluid outputs) for the GUI and its sync. */
    public FluidTank getTank(int index) {
        syncTankCapacity();
        switch (index) {
            case 0: return tankA;
            case 1: return tankB;
            case 2: return outputTankA;
            default: return outputTankB;
        }
    }

    /** NBT key the machine's item carries its tank contents under (BlockMachineSC drops/placement). */
    public static final String ITEM_TANKS_KEY = "TanksSC";
    private static final String[] ITEM_TANK_NAMES = {"TankA", "TankB", "TankOutA", "TankOutB"};

    /**
     * The four tanks as an item NBT compound, or null when all are empty - a broken machine used
     * to drop its inventory but silently lose up to 4 x 4000 mB of fluid.
     */
    public NBTTagCompound tanksForItem() {
        NBTTagCompound tag = null;
        for (int i = 0; i < ITEM_TANK_NAMES.length; i++) {
            FluidStack fluid = getTank(i).getFluid();
            if (fluid != null && fluid.amount > 0) {
                if (tag == null) {
                    tag = new NBTTagCompound();
                }
                tag.setTag(ITEM_TANK_NAMES[i], fluid.writeToNBT(new NBTTagCompound()));
            }
        }
        return tag;
    }

    /** NBT key the machine's item carries its buffer's charge under (as an energy storage's item). */
    public static final String ITEM_ENERGY_KEY = "EnergySC";

    /**
     * The charge a placed item brings back (BlockMachineSC, after the upgrades: a storage upgrade
     * makes the room for it) - capped at the buffer as it is then.
     */
    public void loadEnergyFromItem(int eu) {
        addEnergy(Math.max(0, eu));
        markDirty();
    }

    /** A placed machine starts switched off (BlockMachineSC) - upgrades in first, then on. */
    public static final String ITEM_REDSTONE_KEY = "RedstoneSC";

    @Override
    public int demandedEnergy() {
        return powerOn ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }

    /** NBT key the machine's item carries its upgrades under - they come back with it on placement. */
    public static final String ITEM_UPGRADES_KEY = "UpgradesSC";
    /** Set once the upgrades went into the dropped item, so breakBlock doesn't drop them loose too. */
    private boolean upgradesInItem;
    /** World tick upgradesForItem() ran in: a getDrops() from another tick (another mod asking) mustn't stick. */
    private long upgradesInItemTick = -1;

    /**
     * The upgrade slots as an item NBT compound (null when empty): a machine placed on a live line
     * used to come back bare and blow up in its first tick, before a transformer could go in.
     */
    public NBTTagCompound upgradesForItem() {
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack s = slots[FIRST_UPGRADE_SLOT + i];
            if (s != null) {
                NBTTagCompound t = s.writeToNBT(new NBTTagCompound());
                t.setByte("Slot", (byte) i);
                list.appendTag(t);
            }
        }
        upgradesInItem = list.tagCount() > 0;
        upgradesInItemTick = worldObj != null ? worldObj.getTotalWorldTime() : -1;
        if (!upgradesInItem) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Items", list);
        return tag;
    }

    /** Whether breakBlock should leave the upgrade slots alone (they're in the dropped item). */
    public boolean upgradesInItem() {
        return upgradesInItem && (worldObj == null || upgradesInItemTick == worldObj.getTotalWorldTime());
    }

    /** Puts back what upgradesForItem() saved (on placement, before the first energy tick). */
    public void loadUpgradesFromItem(NBTTagCompound tag) {
        ItemStack[] ups = upgradesOf(tag);
        for (int i = 0; i < ups.length; i++) {
            if (ups[i] != null) {
                slots[FIRST_UPGRADE_SLOT + i] = ups[i];
            }
        }
        markDirty();
    }

    /** The saved upgrades by slot (null where empty) - also used by the item's tooltip. */
    public static ItemStack[] upgradesOf(NBTTagCompound tag) {
        ItemStack[] ups = new ItemStack[UPGRADE_SLOTS];
        net.minecraft.nbt.NBTTagList list = tag == null ? null : tag.getTagList("Items", 10);
        for (int k = 0; list != null && k < list.tagCount(); k++) {
            NBTTagCompound t = list.getCompoundTagAt(k);
            int i = t.getByte("Slot");
            if (i >= 0 && i < UPGRADE_SLOTS) {
                ups[i] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        return ups;
    }

    /** Restores what tanksForItem() saved (on placement of the machine item). */
    public void loadTanksFromItem(NBTTagCompound tag) {
        FluidStack[] fluids = tankFluidsOf(tag);
        for (int i = 0; i < fluids.length; i++) {
            if (fluids[i] != null) {
                getTank(i).setFluid(fluids[i]);
            }
        }
        markDirty();
    }

    /** The saved tank contents (entries null where empty) - also used by the item's tooltip. */
    public static FluidStack[] tankFluidsOf(NBTTagCompound tag) {
        FluidStack[] fluids = new FluidStack[ITEM_TANK_NAMES.length];
        for (int i = 0; tag != null && i < ITEM_TANK_NAMES.length; i++) {
            if (tag.hasKey(ITEM_TANK_NAMES[i])) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(ITEM_TANK_NAMES[i]));
                if (fluid != null && fluid.amount > 0) {
                    // the compressor's tanks are bigger (SM_TANK base): its full extended tank mustn't lose 4000 mB
                    fluids[i] = new FluidStack(fluid.getFluid(), Math.min(Math.max(MAX_TANK_CAPACITY,
                            SM_TANK + UpgradeType.MAX_TANK_UPGRADES * UpgradeType.TANK_PER_UPGRADE), fluid.amount));
                }
            }
        }
        return fluids;
    }

    /** Client-side sync only - a fluid id of 0/unknown or an empty amount clears the tank. */
    public void setTankFluidClient(int index, int fluidId, int amount) {
        Fluid fluid = amount <= 0 ? null : FluidRegistry.getFluid(fluidId);
        getTank(index).setFluid(fluid == null ? null : new FluidStack(fluid, amount));
    }

    /** Heat at which a heat-capable machine stalls (§13.3); it detonates at HEAT_OVERFLOW above that. */
    public static int getHeatCapacity() {
        return HEAT_CAPACITY;
    }

    @Override
    public boolean isEnergySink() {
        return true;
    }

    @Override
    public boolean isEnergySource() {
        return false;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        syncTankCapacity();

        if (upgradeCount(UpgradeType.EJECTOR) > 0) {
            eject();
        }
        if (upgradeCount(UpgradeType.PULLER) > 0 && worldObj.getTotalWorldTime() % PULL_INTERVAL == 0) {
            pull();
        }
        if (feedFromBattery(slots[SLOT_BATTERY]) > 0) {
            markDirty();
        }
        if (machineType.isSmelter()) {
            updateSmelter();
            if (status == MachineStatus.PROCESSING) {
                com.sc.util.SoundsSC.loop(this, com.sc.util.SoundsSC.of(machineType));
            }
            return;
        }
        if (machineType.isCompressor()) {
            updateCompressor();
            if (status == MachineStatus.PROCESSING) {
                com.sc.util.SoundsSC.loop(this, com.sc.util.SoundsSC.of(machineType));
            }
            return;
        }
        if (!powerOn || !redstoneAllows()) {
            status = powerOn ? MachineStatus.REDSTONE : MachineStatus.DISABLED;
            dissipateHeat();
            return;
        }
        if (status == MachineStatus.PROCESSING) {
            com.sc.util.SoundsSC.loop(this, com.sc.util.SoundsSC.of(machineType));
        }

        ItemStack[] inputs = new ItemStack[INPUT_SLOTS];
        System.arraycopy(slots, 0, inputs, 0, INPUT_SLOTS);
        MachineRecipe recipe = RecipeRegistry.findMatch(machineType, inputs, tankA.getFluid(), tankB.getFluid());

        if (recipe == null) {
            progressTicks = 0;
            currentRecipeTicks = 0;
            activeRecipe = null;
            overheatedThisRun = false;
            status = MachineStatus.IDLE;
            dissipateHeat();
            return;
        }
        if (activeRecipe != null && recipe != activeRecipe) {
            // Inputs changed to match a *different* recipe mid-process (e.g. swapping ingredients
            // between two recipes with different tick counts) - carrying progress over would let
            // a short recipe "inherit" ticks banked under a longer one and finish instantly.
            // (activeRecipe == null only right after load/placement, where progressTicks was just
            // restored from NBT for whatever recipe was already running - don't discard that.)
            progressTicks = 0;
            overheatedThisRun = false;
        }
        activeRecipe = recipe;

        if (!hasRoomForOutput(recipe)) {
            status = tankHoldsOther(outputTankA, recipe.fluidOutputA) || tankHoldsOther(outputTankB, recipe.fluidOutputB)
                    ? MachineStatus.OUTPUT_TANK_BUSY : MachineStatus.OUTPUT_FULL;
            dissipateHeat();
            return;
        }

        int cost = effectiveEuPerTick();
        if (getEnergyStored() < cost) {
            status = MachineStatus.NO_POWER;
            dissipateHeat();
            return;
        }
        currentRecipeTicks = effectiveTicks(recipe);

        // §13.3: +1 heat per working tick, -2 per idle tick; at capacity the operation pauses
        // (and its defect chance doubles). The pause itself is idle time: no energy drawn, no
        // heat gained, the machine cools to HEAT_RESUME and carries on. It used to keep gaining
        // heat while paused, so every heat-capable machine climbed from 100 to the 150 overflow
        // and exploded ~150 ticks into its first operation - none of them (all 200+ tick
        // recipes) could ever finish one.
        if (machineType.heatCapable && (heat >= HEAT_CAPACITY || (coolingDown && heat > HEAT_RESUME))) {
            coolingDown = true;
            overheatedThisRun = true;
            status = MachineStatus.OVERHEATED;
            dissipateHeat();
            return;
        }
        coolingDown = false;

        removeEnergy(cost);
        int heatSinks = upgradeCount(UpgradeType.HEAT_SINK);
        if (machineType.heatCapable && heatSinks > 0 && heat > 0 && worldObj.getTotalWorldTime() % HEAT_SINK_INTERVAL == 0) {
            heat = Math.max(0, heat - heatSinks);           // the sinks carry heat away while it works, too
        }
        if (machineType.heatCapable && (heatSinks == 0 || worldObj.getTotalWorldTime() % (heatSinks + 1) == 0)) {
            heat += HEAT_GAIN_PER_TICK;
            if (heat >= HEAT_OVERFLOW) {                 // safety net - unreachable through the pause above
                ExplosionLogic.explodeFromOverheat(this);
                return;
            }
        }

        status = MachineStatus.PROCESSING;
        progressTicks++;
        if (progressTicks >= currentRecipeTicks) {
            finishRecipe(recipe, overheatedThisRun);
            overheatedThisRun = false;
        }
    }

    // ------------------------------------------------------------------ the electric / induction furnace

    /** Base ticks a piece smelts in (a furnace: 200). */
    public static final int SMELT_TICKS = 100;
    /** Induction heat: 0..max, speed 1 + 2 x heat / max; +1 a working (or keeping-warm) tick, -2 an idle one. */
    public static final int INDUCTION_HEAT_MAX = 1000, KEEP_WARM_EU = 8;
    /** Experience a smelter holds at most (points). */
    public static final int MAX_XP = 100000;
    private final int[] smeltProgress = new int[2];
    private double storedXp;
    private boolean keepWarm;
    /** What each stream was smelting last tick (-1: not seen since the load). */
    private final long[] smeltKey = {-1, -1};

    /** What a furnace makes of this, or null. */
    public static ItemStack smeltResult(ItemStack in) {
        if (in == null || in.getItem() == null) {
            return null;
        }
        // FurnaceRecipes walks its whole list on every call; a smelter asks every tick for every
        // stream, a puller for every neighbouring slot - so the answers are kept by item and damage
        long key = ((long) net.minecraft.item.Item.getIdFromItem(in.getItem()) << 32) | (in.getItemDamage() & 0xFFFFFFFFL);
        ItemStack[] known = SMELT_CACHE.get(key);
        if (known == null) {
            known = new ItemStack[]{net.minecraft.item.crafting.FurnaceRecipes.smelting().getSmeltingResult(in)};
            SMELT_CACHE.put(key, known);
        }
        return known[0];
    }

    private static final java.util.Map<Long, ItemStack[]> SMELT_CACHE = new java.util.concurrent.ConcurrentHashMap<Long, ItemStack[]>();

    /** The induction furnace's speed from its heat (x1 cold .. x3 hot); 1 for the electric one. */
    public double smeltSpeed() {
        return machineType == MachineType.INDUCTION_FURNACE ? 1 + 2.0 * heat / INDUCTION_HEAT_MAX : 1;
    }

    /** Ticks a piece takes now: overclockers, the config's machine speed, the induction heat. */
    public int smeltTicks() {
        return Math.max(1, (int) Math.round(SMELT_TICKS * Math.pow(0.7, upgradeCount(UpgradeType.OVERCLOCKER))
                / com.sc.util.ConfigSC.machineSpeed / smeltSpeed()));
    }

    public int getSmeltProgress(int stream) {
        return smeltProgress[stream];
    }

    public void setSmeltProgressClient(int stream, int value) {
        smeltProgress[stream] = value;
    }

    public float getStoredXp() {
        return (float) storedXp;
    }

    public void setStoredXpClient(float xp) {
        storedXp = xp;
    }

    public boolean isKeepWarm() {
        return keepWarm;
    }

    public void setKeepWarmClient(boolean on) {
        keepWarm = on;
    }

    public void toggleKeepWarm() {
        keepWarm = !keepWarm;
        markDirty();
    }

    private static boolean fitsOutput(ItemStack there, ItemStack out, int limit) {
        return there == null || there.isItemEqual(out) && ItemStack.areItemStackTagsEqual(there, out)
                && there.stackSize + out.stackSize <= Math.min(limit, there.getMaxStackSize());
    }

    private void coolInduction() {
        if (machineType == MachineType.INDUCTION_FURNACE && heat > 0) {
            heat = Math.max(0, heat - 2);
        }
    }

    /**
     * One tick of a smelter: each stream (input slot i -> output slot i) with something to smelt and
     * room for it works; the EU/t is shared by the streams at work. The induction furnace heats up
     * while it works (or keeps warm with nothing in, when switched to), and cools when idle.
     */
    private void updateSmelter() {
        boolean induction = machineType == MachineType.INDUCTION_FURNACE;
        int streams = machineType.smeltStreams();
        if (!powerOn || !redstoneAllows()) {
            status = powerOn ? MachineStatus.REDSTONE : MachineStatus.DISABLED;
            coolInduction();
            return;
        }
        currentRecipeTicks = smeltTicks();
        boolean[] run = new boolean[streams];
        int active = 0;
        boolean full = false;
        for (int i = 0; i < streams; i++) {
            ItemStack out = smeltResult(slots[i]);
            long key = slots[i] == null ? 0 : ((long) net.minecraft.item.Item.getIdFromItem(slots[i].getItem()) << 32) | (slots[i].getItemDamage() & 0xFFFFFFFFL);
            if (smeltKey[i] != -1 && smeltKey[i] != key) {
                smeltProgress[i] = 0;                         // another item swapped in: it starts over
            }
            smeltKey[i] = key;
            if (out == null) {
                smeltProgress[i] = 0;
                continue;
            }
            if (!fitsOutput(slots[INPUT_SLOTS + i], out, getInventoryStackLimit())) {
                full = true;
                continue;
            }
            run[i] = true;
            active++;
        }
        progressTicks = Math.max(smeltProgress[0], smeltProgress[1]);
        if (active == 0) {
            int warm = com.sc.util.ConfigSC.scale(KEEP_WARM_EU, com.sc.util.ConfigSC.machineEnergy, 1);
            if (full) {
                status = MachineStatus.OUTPUT_FULL;              // said first: warm or not, the output wants emptying
                coolInduction();
            } else if (induction && keepWarm && getEnergyStored() >= warm) {
                removeEnergy(warm);
                heat = Math.min(INDUCTION_HEAT_MAX, heat + 1);
                status = MachineStatus.HEATING;
            } else {
                status = induction && keepWarm ? MachineStatus.NO_POWER : MachineStatus.IDLE;
                coolInduction();
            }
            return;
        }
        int perStream = (int) Math.ceil(effectiveEuPerTick() / (double) streams);
        while (active > 0 && getEnergyStored() < perStream * active) {   // short of energy: fewer streams, the last first
            for (int i = streams - 1; i >= 0; i--) {
                if (run[i]) {
                    run[i] = false;
                    break;
                }
            }
            active--;
        }
        if (active == 0) {
            status = MachineStatus.NO_POWER;
            coolInduction();
            return;
        }
        int cost = perStream * active;
        removeEnergy(cost);
        if (induction) {
            heat = Math.min(INDUCTION_HEAT_MAX, heat + 1);
        }
        status = MachineStatus.PROCESSING;
        for (int i = 0; i < streams; i++) {
            if (run[i] && ++smeltProgress[i] >= currentRecipeTicks) {
                finishSmelt(i);
            }
        }
        progressTicks = Math.max(smeltProgress[0], smeltProgress[1]);
    }

    private void finishSmelt(int i) {
        ItemStack out = smeltResult(slots[i]).copy();
        slots[i].stackSize--;
        if (slots[i].stackSize <= 0) {
            slots[i] = null;
        }
        int o = INPUT_SLOTS + i;
        if (slots[o] == null) {
            slots[o] = out;
        } else {
            slots[o].stackSize += out.stackSize;
        }
        storedXp = Math.min(MAX_XP, storedXp + net.minecraft.item.crafting.FurnaceRecipes.smelting().func_151398_b(out) * out.stackSize);
        smeltProgress[i] = 0;
        markDirty();
    }

    /** The screen's button: the whole points of experience to the player (the fraction stays). */
    public void takeXp(EntityPlayer player) {
        int whole = (int) storedXp;
        if (whole <= 0) {
            return;
        }
        storedXp -= whole;
        player.addExperience(whole);
        worldObj.playSoundAtEntity(player, "random.orb", 0.3F, 0.5F * ((worldObj.rand.nextFloat() - worldObj.rand.nextFloat()) * 0.7F + 1.8F));
        markDirty();
    }

    /** The block goes: its experience comes out as orbs. */
    public void dropXp() {
        int whole = (int) storedXp;
        storedXp = 0;
        while (whole > 0) {
            int part = net.minecraft.entity.item.EntityXPOrb.getXPSplit(whole);
            whole -= part;
            worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityXPOrb(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, part));
        }
    }

    // ------------------------------------------------------------------ the matter compressor

    /** Mass one Compressed Matter Capsule takes: 64 stone blocks, or 16 lead blocks. */
    public static final int MATTER_PER_CAPSULE = 576;
    /** The counter takes in items up to this (two capsules), so the next capsule is ready as one comes out. */
    public static final int MATTER_MAX = 2 * MATTER_PER_CAPSULE;
    /** Ticks a capsule takes with no overclockers (10 s). */
    public static final int COMPRESS_TICKS = 200;
    /** Items the compressor swallows a tick (from its three input slots together). */
    public static final int MATTER_ABSORB_PER_TICK = 8;
    /** Base mass: a block 9, anything else 1; heavy metals x4. */
    public static final int MASS_BLOCK = 9, MASS_ITEM = 1, MASS_HEAVY = 4;
    /** OreDictionary forms and metals that count as heavy (exact names, e.g. "blockLead", "ingotGold"). */
    private static final String[] HEAVY_FORMS = {"ingot", "block", "dust", "plate", "crushed", "crushedPurified"};
    private static final String[] HEAVY_METALS = {"Lead", "Tungsten", "Hafnium", "Tantalum", "Iron", "Gold"};
    /** NBT key the compressor's item carries its mass under (BlockMachineSC drops / placement). */
    public static final String ITEM_MATTER_KEY = "MatterSC";
    /** СМ1 «жидкая материя»: singular matter per capsule's worth of mass, mB; the tank (output tank 1) without Tank Extensions. */
    public static final int SM_PER_CAPSULE = 100, SM_TANK = 8000;
    /** NBT key of the liquid mode in the compressor's item. */
    public static final String ITEM_LIQUID_KEY = "MatterLiquidSC";
    /** The mass in the counter (Matter Compressor only). */
    private int matter;
    /** СМ1: the compressor pours singular matter into its tank instead of pressing capsules (old compressors: capsules). */
    private boolean matterLiquid;

    public boolean isMatterLiquid() {
        return matterLiquid;
    }

    /** The screen's mode button. A capsule half-pressed is kept: the progress goes on in the new mode. */
    public void toggleMatterLiquid() {
        matterLiquid = !matterLiquid;
        markDirty();
    }

    /** Client-side sync, the item on placement, tests. */
    public void setMatterLiquid(boolean on) {
        matterLiquid = on;
    }

    /** mB of singular matter `mass` of the counter makes in the liquid mode (whole capsules' worth only). */
    public static int liquidFor(int mass) {
        return Math.max(0, mass) / MATTER_PER_CAPSULE * SM_PER_CAPSULE;
    }

    public int getMatter() {
        return matter;
    }

    /** Client-side sync only. */
    public void setMatterClient(int value) {
        matter = value;
    }

    /** On placement of the compressor's item (BlockMachineSC). */
    public void loadMatterFromItem(int value) {
        matter = Math.max(0, Math.min(MATTER_MAX + MASS_BLOCK * MASS_HEAVY, value));
        markDirty();
    }

    /** Self-test: set the counter directly. */
    public void setMatterForTest(int value) {
        matter = Math.max(0, value);
    }

    /**
     * The mass one piece of `stack` gives the compressor, or 0 when it doesn't take it at all: nothing
     * with NBT (a charged battery, a configured tool, a machine with upgrades inside - it would be
     * destroyed with everything on it), and never the capsule itself. Nor what would be a pity to burn
     * by accident: anything with a container (fluid buckets, cells), the mod's own machines, generators,
     * storages and tanks (its blocks with a tile entity), anything not of common rarity, and the
     * valuables of VALUABLES. A block 9, any other item 1; lead, tungsten, hafnium, tantalum, iron and
     * gold (ingot, dust, plate, crushed ore, block) x4.
     */
    public static int matterMass(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.hasTagCompound()) {
            return 0;
        }
        net.minecraft.item.Item capsule = ModItems.component("matterCapsule");
        if (capsule != null && stack.getItem() == capsule
                // machine upgrades and batteries: shift-click would feed them in instead of their own slots
                || stack.getItem() instanceof com.sc.item.ItemUpgradeSC || stack.getItem() instanceof com.sc.item.ItemBatterySC) {
            return 0;
        }
        if (stack.getItem().hasContainerItem(stack) || stack.getRarity() != net.minecraft.item.EnumRarity.common
                || isValuable(stack) || isModTileBlock(stack)) {
            return 0;
        }
        int mass = stack.getItem() instanceof net.minecraft.item.ItemBlock ? MASS_BLOCK : MASS_ITEM;
        return isHeavyMetal(stack) ? mass * MASS_HEAVY : mass;
    }

    /** What the compressor never takes, whatever its rarity says: nether stars, diamonds, emeralds, ender pearls and eyes, beacons. */
    private static boolean isValuable(ItemStack stack) {
        net.minecraft.item.Item i = stack.getItem();
        if (i == com.sc.init.ModItems.dust && com.sc.init.ModItems.dust.materialAt(stack.getItemDamage()) == com.sc.util.Material.DIAMOND) {
            return true;                                     // Р-1: a crushed diamond is still a diamond
        }
        return i == net.minecraft.init.Items.nether_star || i == net.minecraft.init.Items.diamond || i == net.minecraft.init.Items.emerald
                || i == net.minecraft.init.Items.ender_pearl || i == net.minecraft.init.Items.ender_eye
                || i == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.diamond_block)
                || i == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.emerald_block)
                || i == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.beacon)
                || i == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.dragon_egg);
    }

    /** The mod's machines, generators, storages, tanks...: its blocks with a tile entity (lead blocks and the like still go in). */
    private static boolean isModTileBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.item.ItemBlock)) {
            return false;
        }
        net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
        return b != null && b.getClass().getName().startsWith("com.sc.") && b.hasTileEntity(stack.getItem().getMetadata(stack.getItemDamage()));
    }

    /** The mod's own tile entities (machines, generators, storages, the quarry, tanks...): the compressor's puller leaves them alone. */
    private static boolean isModTile(net.minecraft.tileentity.TileEntity te) {
        return te != null && te.getClass().getName().startsWith("com.sc.");
    }

    private static boolean isHeavyMetal(ItemStack stack) {
        int[] ids;
        try {
            ids = net.minecraftforge.oredict.OreDictionary.getOreIDs(stack);
        } catch (RuntimeException e) {                       // a wildcard / broken stack: just not heavy
            return false;
        }
        for (int id : ids) {
            String name = net.minecraftforge.oredict.OreDictionary.getOreName(id);
            for (String form : HEAVY_FORMS) {
                if (!name.startsWith(form)) {
                    continue;
                }
                for (String metal : HEAVY_METALS) {
                    if (name.length() == form.length() + metal.length() && name.endsWith(metal)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ticks a capsule takes now: overclockers and the config's machine speed. */
    public int compressTicks() {
        return Math.max(1, (int) Math.round(COMPRESS_TICKS * Math.pow(0.7, upgradeCount(UpgradeType.OVERCLOCKER))
                / com.sc.util.ConfigSC.machineSpeed));
    }

    /** What the compressor makes (null if the item isn't registered). */
    public static ItemStack capsuleStack() {
        net.minecraft.item.Item capsule = ModItems.component("matterCapsule");
        return capsule == null ? null : new ItemStack(capsule);
    }

    /**
     * One tick of the compressor: it swallows up to MATTER_ABSORB_PER_TICK items from its inputs
     * into the counter (free, while it's switched on and has a tick's energy), and with MATTER_PER_CAPSULE of mass, room in
     * the outputs and the energy, it presses a capsule over compressTicks() ticks.
     */
    private void updateCompressor() {
        if (!powerOn || !redstoneAllows()) {
            status = powerOn ? MachineStatus.REDSTONE : MachineStatus.DISABLED;
            return;
        }
        if (getEnergyStored() >= effectiveEuPerTick()) {
            absorbMatter();                                  // only while it can work: nothing vanishes into an unpowered one
        }
        currentRecipeTicks = compressTicks();
        ItemStack capsule = capsuleStack();
        boolean liquid = matterLiquid && com.sc.init.ModFluids.singularMatter != null;
        if (matter < MATTER_PER_CAPSULE || capsule == null && !liquid) {
            progressTicks = 0;
            status = MachineStatus.IDLE;
            return;
        }
        FluidStack sm = liquid ? new FluidStack(com.sc.init.ModFluids.singularMatter, SM_PER_CAPSULE) : null;
        if (liquid ? safeFill(getTank(2), sm, false) < SM_PER_CAPSULE : !canInsertAll(new ItemStack[]{capsule})) {
            status = MachineStatus.OUTPUT_FULL;
            return;
        }
        int cost = effectiveEuPerTick();
        if (getEnergyStored() < cost) {
            status = MachineStatus.NO_POWER;
            return;
        }
        removeEnergy(cost);
        status = MachineStatus.PROCESSING;
        if (++progressTicks >= currentRecipeTicks) {
            progressTicks = 0;
            matter -= MATTER_PER_CAPSULE;
            if (liquid) {
                getTank(2).fill(sm, true);                     // СМ1: a capsule's worth as singular matter
            } else {
                insertOutput(capsule);
            }
            markDirty();
        }
    }

    /**
     * Moves items from the input slots into the counter, up to MATTER_MAX. A singularity clot (liquid mode
     * only) skips the counter: ItemSingularClotSC.SM_PER_CLOT mB straight into the tank while it has room.
     */
    private void absorbMatter() {
        int budget = MATTER_ABSORB_PER_TICK;
        boolean changed = false;
        for (int i = 0; i < INPUT_SLOTS && budget > 0; i++) {
            ItemStack s = slots[i];
            if (com.sc.item.ItemSingularClotSC.isClot(s)) {
                if (!matterLiquid || com.sc.init.ModFluids.singularMatter == null) {
                    continue;                                // the capsule mode: it waits for the liquid one
                }
                FluidStack sm = new FluidStack(com.sc.init.ModFluids.singularMatter, com.sc.item.ItemSingularClotSC.SM_PER_CLOT);
                while (s != null && budget > 0 && safeFill(getTank(2), sm, false) >= sm.amount) {
                    getTank(2).fill(sm.copy(), true);
                    budget--;
                    changed = true;
                    if (--s.stackSize <= 0) {
                        slots[i] = null;
                        s = null;
                    }
                }
                continue;
            }
            int mass = matterMass(s);
            while (s != null && mass > 0 && budget > 0 && matter < MATTER_MAX) {
                matter += mass;
                budget--;
                changed = true;
                if (--s.stackSize <= 0) {
                    slots[i] = null;
                    s = null;
                }
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /** Self-test: one compressor tick, power and switches as they are. */
    public void compressorTickForTest() {
        updateCompressor();
    }

    private net.minecraft.tileentity.TileEntity neighbour(ForgeDirection dir) {
        int x = xCoord + dir.offsetX, y = yCoord + dir.offsetY, z = zCoord + dir.offsetZ;
        return worldObj.blockExists(x, y, z) ? worldObj.getTileEntity(x, y, z) : null;
    }

    /** Ejector upgrade: products into every neighbour that takes them - items and fluids. */
    private void eject() {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            net.minecraft.tileentity.TileEntity te = neighbour(dir);
            if (te instanceof IInventory) {
                for (int slot = INPUT_SLOTS; slot < FIRST_UPGRADE_SLOT; slot++) {
                    if (slots[slot] == null) {
                        continue;
                    }
                    int left = InvUtilSC.insert((IInventory) te, dir, slots[slot]);
                    if (left != slots[slot].stackSize) {
                        slots[slot].stackSize = left;
                        if (left <= 0) {
                            slots[slot] = null;
                        }
                        markDirty();
                    }
                }
            }
            if (te instanceof IFluidHandler) {
                IFluidHandler handler = (IFluidHandler) te;
                for (FluidTank tank : new FluidTank[]{outputTankA, outputTankB}) {
                    FluidStack held = tank.getFluid();
                    if (held == null || held.amount <= 0 || !handler.canFill(dir.getOpposite(), held.getFluid())) {
                        continue;
                    }
                    FluidStack offer = held.copy();
                    offer.amount = Math.min(FLUID_MOVE, held.amount);
                    int accepted = handler.fill(dir.getOpposite(), offer, true);
                    if (accepted > 0) {
                        tank.drain(accepted, true);
                    }
                }
            }
        }
    }

    /** Puller upgrade: ingredients this machine has a recipe for, out of every neighbour that gives them (PULL_ITEMS per pull in all). */
    private void pull() {
        int budget = PULL_ITEMS;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            net.minecraft.tileentity.TileEntity te = neighbour(dir);
            if (te instanceof IInventory && budget > 0 && !(machineType.isCompressor() && isModTile(te))) {
                IInventory source = (IInventory) te;      // the compressor: only from other inventories (chests...), never the mod's own
                for (int slot : InvUtilSC.slots(source, dir)) {
                    ItemStack stack = source.getStackInSlot(slot);
                    if (budget <= 0) {
                        break;
                    }
                    if (stack == null || !RecipeRegistry.isValidInput(machineType, stack) || !InvUtilSC.canTake(source, slot, stack, dir)) {
                        continue;
                    }
                    // never trust another mod's inventory: take only what fits, move what it really gave
                    int took = InvUtilSC.move(source, slot, dir, this, ForgeDirection.UP, Math.min(budget, stack.stackSize),
                            worldObj, xCoord + 0.5, yCoord + 1.2, zCoord + 0.5);
                    if (took > 0) {
                        budget -= took;
                    }
                }
            }
            if (te instanceof IFluidHandler) {
                IFluidHandler source = (IFluidHandler) te;
                FluidTankInfo[] infos = source.getTankInfo(dir.getOpposite());
                for (int i = 0; infos != null && i < infos.length; i++) {
                    FluidStack there = infos[i] == null ? null : infos[i].fluid;
                    if (there == null || there.amount <= 0 || !RecipeRegistry.isValidFluidInput(machineType, there.getFluid())) {
                        continue;
                    }
                    FluidStack want = new FluidStack(there.getFluid(), FLUID_MOVE);
                    FluidStack can = source.drain(dir.getOpposite(), want, false);
                    int room = can == null ? 0 : fill(ForgeDirection.UNKNOWN, can, false);
                    if (room > 0) {
                        FluidStack got = source.drain(dir.getOpposite(), new FluidStack(there.getFluid(), room), true);
                        if (got != null) {
                            fill(ForgeDirection.UNKNOWN, got, true);
                        }
                    }
                }
            }
        }
    }

    private void dissipateHeat() {
        if (machineType.heatCapable && heat > 0) {
            heat = Math.max(0, heat - HEAT_DISSIPATION_PER_TICK);
        }
    }

    private void finishRecipe(MachineRecipe recipe, boolean doubledDefect) {
        progressTicks = 0;
        float defectChance = doubledDefect ? Math.min(1.0F, recipe.defectChance * 2) : recipe.defectChance;
        defectChance *= (float) Math.pow(0.5, upgradeCount(UpgradeType.QUALITY));
        boolean defect = RANDOM.nextFloat() < defectChance;

        // A suit piece / energy blade going through the Upgrade Station keeps its whole NBT (§16):
        // chips, PowerMode, FnToggled, ChipsOffSC, HeatSC... - the output is a fresh stack, which
        // used to carry only chips + charge and silently wiped the rest. The EU charge is then
        // re-capped at the new item's capacity.
        NBTTagCompound carriedArmorTag = null;
        NBTTagCompound carriedBladeTag = null;
        NBTTagCompound carriedDrillTag = null;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (slots[i] != null && slots[i].getItem() instanceof com.sc.item.ItemArmorSC && slots[i].hasTagCompound()) {
                carriedArmorTag = (NBTTagCompound) slots[i].getTagCompound().copy();
            }
            if (slots[i] != null && slots[i].getItem() instanceof com.sc.item.ItemBladeSC && slots[i].hasTagCompound()) {
                carriedBladeTag = (NBTTagCompound) slots[i].getTagCompound().copy();
            }
            if (slots[i] != null && slots[i].getItem() instanceof com.sc.item.ItemDrillSC && slots[i].hasTagCompound()) {
                carriedDrillTag = (NBTTagCompound) slots[i].getTagCompound().copy();
            }
        }
        consumeInputs(recipe);
        drainInput(recipe.fluidInputA);
        drainInput(recipe.fluidInputB);

        if (defect) {
            insertOutput(ModItems.dust.stackOf(Material.SCRAP));
        } else {
            for (ItemStack output : recipe.outputs) {
                ItemStack made = output.copy();
                if (carriedArmorTag != null && made.getItem() instanceof com.sc.item.ItemArmorSC) {
                    made.setTagCompound((NBTTagCompound) carriedArmorTag.copy());
                    com.sc.item.ItemArmorSC.setCharge(made, com.sc.item.ItemArmorSC.chargeOf(made));
                }
                if (carriedBladeTag != null && made.getItem() instanceof com.sc.item.ItemBladeSC) {
                    made.setTagCompound((NBTTagCompound) carriedBladeTag.copy());
                    com.sc.item.ItemBladeSC.setCharge(made, com.sc.item.ItemBladeSC.chargeOf(made));
                }
                if (carriedDrillTag != null && made.getItem() instanceof com.sc.item.ItemDrillSC) {
                    made.setTagCompound((NBTTagCompound) carriedDrillTag.copy());
                    com.sc.item.ItemDrillSC.setCharge(made, com.sc.item.ItemDrillSC.chargeOf(made));
                }
                insertOutput(made);
            }
            // §11.2's chanced Centrifuge by-products, rolled one at a time so an ore can carry
            // several independent traces (Sphalerite yields Ga at 6% AND In at 4%).
            for (int i = 0; i < recipe.byproducts.length; i++) {
                if (RANDOM.nextFloat() < recipe.byproductChances[i]) {
                    insertOutput(recipe.byproducts[i].copy());
                }
            }
            if (recipe.fluidOutputA != null) {
                outputTankA.fill(recipe.fluidOutputA.copy(), true);
            }
            if (recipe.fluidOutputB != null) {
                outputTankB.fill(recipe.fluidOutputB.copy(), true);
            }
        }
        markDirty();
    }

    /**
     * Sneak + empty-hand right-click (BlockMachineSC): dumps both input tanks. Nothing else can
     * drain them, so a leftover fluid in a multi-fluid machine (300 mB HCl in a Chem Reactor that
     * now needs Cl2 + H2) otherwise blocked a tank until the block was broken.
     * It costs what the screen's Clear buttons cost (clearCost per tank: 1 EU / 10 mB), and only
     * if the buffer can pay it all.
     * @return mB vented, or VENT_NO_POWER (nothing poured out) when the buffer can't pay
     */
    public int ventInputTanks() {
        boolean inputs = tankA.getFluidAmount() + tankB.getFluidAmount() > 0;
        // Inputs already empty: the second click clears the output tanks, so a product nothing
        // takes away (no pipe on that face) can't block the machine for good.
        int first = inputs ? 0 : 2;
        int vented = getTank(first).getFluidAmount() + getTank(first + 1).getFluidAmount();
        int cost = clearCost(first) + clearCost(first + 1);
        if (vented <= 0) {
            return 0;
        }
        if (getEnergyStored() < cost) {
            return VENT_NO_POWER;
        }
        removeEnergy(cost);
        getTank(first).setFluid(null);
        getTank(first + 1).setFluid(null);
        markDirty();
        return vented;
    }

    /** ventInputTanks(): the buffer couldn't pay for it, nothing was poured out. */
    public static final int VENT_NO_POWER = -1;
    /** Shift + right-click vents only when repeated within this many ticks (the first one warns). */
    public static final int VENT_CONFIRM_TICKS = 20;
    /** A held right button repeats every 4 ticks: clicks that close together only re-arm, so holding never vents. */
    public static final int VENT_HOLD_TICKS = 8;
    /** Server only, not saved: when and by whom the last venting click armed it. */
    private long ventArmedAt = Long.MIN_VALUE;
    private String ventArmedBy;

    /**
     * Shift + right-click's confirmation, as the power switch's double click: the first click only
     * arms it (false - "click again"), a second one by the same player within VENT_CONFIRM_TICKS
     * goes through (true) and disarms it.
     */
    public boolean confirmVent(EntityPlayer player) {
        long now = worldObj != null ? worldObj.getTotalWorldTime() : 0;
        String who = player == null ? "" : player.getCommandSenderName();
        if (who.equals(ventArmedBy) && now - ventArmedAt > VENT_HOLD_TICKS && now - ventArmedAt <= VENT_CONFIRM_TICKS) {
            ventArmedAt = Long.MIN_VALUE;
            ventArmedBy = null;
            return true;
        }
        ventArmedAt = now;
        ventArmedBy = who;
        return false;
    }

    /** Drains a recipe's fluid from whichever input tank holds it (see MachineRecipe.matches). */
    private void drainInput(FluidStack required) {
        if (required == null) {
            return;
        }
        FluidTank source = tankA.getFluid() != null && tankA.getFluid().isFluidEqual(required)
                && tankA.getFluidAmount() >= required.amount ? tankA : tankB;
        source.drain(required.amount, true);
    }

    private void consumeInputs(MachineRecipe recipe) {
        for (ItemStack required : recipe.inputs) {
            for (int i = 0; i < INPUT_SLOTS; i++) {
                ItemStack stack = slots[i];
                if (!MachineRecipe.ingredientMatches(stack, required)) {
                    continue;
                }
                // Only the mod's tools (moulds, blades, photomask...) wear instead of being used up.
                // Checking isDamageable() also caught suit pieces: the Upgrade Station only nicked
                // the input armour and ran again - endless Quantum/Exo pieces (chips copied too).
                if (stack.getItem() instanceof com.sc.item.ItemToolSC) {
                    // A tool (§13.5) - damage it by 1 use instead of consuming the stack.
                    int newDamage = stack.getItemDamage() + 1;
                    if (newDamage >= stack.getItem().getMaxDamage(stack)) {
                        slots[i] = null;
                    } else {
                        stack.setItemDamage(newDamage);
                    }
                } else {
                    stack.stackSize -= required.stackSize;
                    if (stack.stackSize <= 0) {
                        slots[i] = null;
                    }
                }
                break;
            }
        }
    }

    private boolean hasRoomForOutput(MachineRecipe recipe) {
        ItemStack[] scrapOnly = new ItemStack[]{ModItems.dust.stackOf(Material.SCRAP)};
        // Scrap only needs room when a defect can actually roll - Centrifuge/Rolling Machine
        // recipes are 0% and used to stall on OUTPUT_FULL once their 3 output slots held 3
        // different (non-scrap) items, with plenty of room left in each stack.
        boolean scrapFits = recipe.defectChance <= 0 || canInsertAll(scrapOnly);
        // Either the defect item or the real outputs might be produced - both must fit, since
        // the defect roll happens only once the recipe is already committed to finishing.
        // By-products are counted as if they all rolled: starting an operation that can't store
        // its own trace output would drop it on the floor silently.
        ItemStack[] withByproducts = new ItemStack[recipe.outputs.length + recipe.byproducts.length];
        System.arraycopy(recipe.outputs, 0, withByproducts, 0, recipe.outputs.length);
        System.arraycopy(recipe.byproducts, 0, withByproducts, recipe.outputs.length, recipe.byproducts.length);
        boolean itemsFit = canInsertAll(withByproducts) && scrapFits;
        return itemsFit && tankHasRoom(outputTankA, recipe.fluidOutputA) && tankHasRoom(outputTankB, recipe.fluidOutputB);
    }

    /** The tank is taken by a different fluid than the recipe would put in it. */
    private static boolean tankHoldsOther(FluidTank tank, FluidStack output) {
        return output != null && tank.getFluid() != null && !tank.getFluid().isFluidEqual(output);
    }

    private static boolean tankHasRoom(FluidTank tank, FluidStack output) {
        if (output == null) {
            return true;
        }
        return tank.getFluid() == null
                ? output.amount <= tank.getCapacity()
                : tank.getFluid().isFluidEqual(output) && tank.getFluidAmount() + output.amount <= tank.getCapacity();
    }

    private boolean canInsertAll(ItemStack[] toPlace) {
        int[] remaining = new int[OUTPUT_SLOTS];
        ItemStack[] simSlots = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack existing = slots[INPUT_SLOTS + i];
            simSlots[i] = existing;
            remaining[i] = existing == null ? 64 : existing.getMaxStackSize() - existing.stackSize;
        }
        for (ItemStack stack : toPlace) {
            int left = stack.stackSize;
            for (int i = 0; i < OUTPUT_SLOTS && left > 0; i++) {
                if (simSlots[i] == null) {
                    // Claim the slot for this item: without recording it, every later stack in
                    // the batch saw the same slot as still empty, so two outputs "fit" into one
                    // free slot and insertOutput() silently dropped the second.
                    int place = Math.min(left, stack.getMaxStackSize());
                    simSlots[i] = stack;
                    remaining[i] = stack.getMaxStackSize() - place;
                    left -= place;
                } else if (simSlots[i].getItem() == stack.getItem() && simSlots[i].getItemDamage() == stack.getItemDamage()
                        && ItemStack.areItemStackTagsEqual(simSlots[i], stack) && remaining[i] > 0) {
                    int place = Math.min(left, remaining[i]);
                    left -= place;
                    remaining[i] -= place;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private void insertOutput(ItemStack stack) {
        for (int i = 0; i < OUTPUT_SLOTS && stack.stackSize > 0; i++) {
            int slot = INPUT_SLOTS + i;
            if (slots[slot] == null) {
                int place = Math.min(stack.stackSize, stack.getMaxStackSize());
                slots[slot] = stack.splitStack(place);
            } else if (slots[slot].getItem() == stack.getItem() && slots[slot].getItemDamage() == stack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(slots[slot], stack)) {   // a suit piece's chips / charge mustn't merge away
                int room = slots[slot].getMaxStackSize() - slots[slot].stackSize;
                int place = Math.min(stack.stackSize, room);
                slots[slot].stackSize += place;
                stack.stackSize -= place;
            }
        }
    }

    /** Self-test: one tick of the battery slot. */
    public int batteryRoundForTest() {
        return feedFromBattery(slots[SLOT_BATTERY]);
    }

    // ---- IInventory / ISidedInventory (§13.3 side-config: front=output, others=input) ----

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slots[slot] == null) {
            return null;
        }
        ItemStack result = slots[slot].splitStack(Math.min(amount, slots[slot].stackSize));
        if (slots[slot].stackSize <= 0) {
            slots[slot] = null;
        }
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        slots[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container." + machineType.displayName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        // Only things this machine actually has a recipe for - otherwise any item (or any
        // hopper) could fill the input slots with junk that can never be processed, and the
        // player has to dig it back out by hand.
        if (slot == SLOT_BATTERY) {
            return com.sc.item.BatteryFeedSC.accepts(stack);
        }
        if (slot >= FIRST_UPGRADE_SLOT) {
            return stack != null && stack.getItem() instanceof com.sc.item.ItemUpgradeSC
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).generatorOnly()
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).fieldOnly()
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).storageOnly()
                    && !((machineType.isSmelter() || machineType.isCompressor()) && (com.sc.item.ItemUpgradeSC.typeOf(stack) == UpgradeType.QUALITY
                            || com.sc.item.ItemUpgradeSC.typeOf(stack) == UpgradeType.HEAT_SINK))    // nothing to improve there
                    // a heat sink only where there's heat, a tank extension only where there are tanks
                    // (only new ones are refused - what an older world already has in the slots stays)
                    && !(com.sc.item.ItemUpgradeSC.typeOf(stack) == UpgradeType.HEAT_SINK && !machineType.heatCapable)
                    // (the Matter Compressor has no recipes, but its singular-matter tank takes extensions - tankCapacity)
                    && !(com.sc.item.ItemUpgradeSC.typeOf(stack) == UpgradeType.TANK_EXTENSION && !usesAnyTank(machineType)
                            && !machineType.isCompressor());
        }
        if (machineType.isSmelter()) {
            return slot < machineType.smeltStreams() && smeltResult(stack) != null;
        }
        if (machineType.isCompressor()) {
            return slot < INPUT_SLOTS && (matterMass(stack) > 0 || com.sc.item.ItemSingularClotSC.isClot(stack));
        }
        return slot < INPUT_SLOTS && RecipeRegistry.isValidInput(machineType, stack);
    }

    /** Whether any recipe of this type uses any of the four tanks (as ContainerMachineSC.usesTanks). */
    private static boolean usesAnyTank(MachineType type) {
        for (int i = 0; i < 4; i++) {
            if (RecipeRegistry.usesTank(type, i)) {
                return true;
            }
        }
        return false;
    }

    /** Every side: inputs go in, products come out (the upgrade slots are the player's only). */
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return IO_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        if (slot == SLOT_BATTERY) {
            return slots[SLOT_BATTERY] == null && com.sc.item.BatteryFeedSC.accepts(stack);   // a full one in
        }
        if (machineType.isSmelter() || machineType.isCompressor()) {
            return isItemValidForSlot(slot, stack) && (slots[slot] == null || slots[slot].isItemEqual(stack));
        }
        if (machineType == MachineType.CRUSHER && handOnly(stack)) {
            return false;
        }
        return slot < INPUT_SLOTS && isItemValidForSlot(slot, stack) && fitsSomeRecipe(slot, stack);
    }

    /**
     * Р-2: what the Crusher takes from a player's hand only - diamonds (to dust) and nether quartz (to silica sand).
     * A Crusher fed by a quarry or a chest would otherwise grind every diamond and quartz that passes through.
     */
    public static boolean handOnly(ItemStack stack) {
        return stack != null && (stack.getItem() == net.minecraft.init.Items.diamond || stack.getItem() == net.minecraft.init.Items.quartz);
    }

    /**
     * Automation (hoppers, tubes) may only add an ingredient that, together with everything
     * already sitting in the OTHER input slots, is still part of one single recipe of this
     * machine - each stack claiming its own recipe ingredient. Otherwise a Blast Furnace or Kiln
     * ended up with e.g. iron + sand + coke (each valid for SOME recipe, but no recipe takes all
     * three), and since automation can't pull inputs back out, the machine was jammed for good.
     * This also keeps one ingredient to one slot (iron in all three slots fits no recipe).
     * The GUI is unaffected (players arrange by hand).
     */
    private boolean fitsSomeRecipe(int slot, ItemStack stack) {
        java.util.List<ItemStack> present = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (i != slot && slots[i] != null) {
                present.add(slots[i]);
            }
        }
        present.add(stack);
        for (MachineRecipe recipe : RecipeRegistry.recipesFor(machineType)) {
            if (present.size() <= recipe.inputs.length
                    && assignIngredients(present, 0, recipe.inputs, new boolean[recipe.inputs.length])) {
                return true;
            }
        }
        return false;
    }

    /** Backtracking: gives every present stack its own (distinct) ingredient of the recipe. */
    private static boolean assignIngredients(java.util.List<ItemStack> present, int index, ItemStack[] required, boolean[] taken) {
        if (index >= present.size()) {
            return true;
        }
        for (int r = 0; r < required.length; r++) {
            // Same ingredient as the machine sees it (tool wear ignored): isItemEqual compared
            // damage, so a fresh spare seed crystal took the gallium's slot and jammed the puller.
            if (!taken[r] && MachineRecipe.isSameIngredient(present.get(index), required[r])) {
                taken[r] = true;
                if (assignIngredients(present, index + 1, required, taken)) {
                    return true;
                }
                taken[r] = false;
            }
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        if (slot == SLOT_BATTERY) {
            return com.sc.item.BatteryFeedSC.chargeOf(stack) <= 0;                             // an empty one out
        }
        return slot >= INPUT_SLOTS && slot < FIRST_UPGRADE_SLOT;
    }

    // ---- IFluidHandler, every side: fill goes to whichever input tank already holds that fluid
    // (or the first empty one), drain takes from the output tanks. Only fluids some recipe of this
    // machine consumes get in at all: input tanks can't be drained, so anything else (lava in a
    // Crusher, water in the second CVD tank) would sit there forever and block the tank. ----

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || !RecipeRegistry.isValidFluidInput(machineType, resource.getFluid())) {
            return 0;
        }
        if (tankA.getFluid() != null && tankA.getFluid().isFluidEqual(resource)) {
            return safeFill(tankA, resource, doFill);
        }
        if (tankB.getFluid() != null && tankB.getFluid().isFluidEqual(resource)) {
            return safeFill(tankB, resource, doFill);
        }
        if (tankA.getFluid() == null && goesWith(resource, tankB.getFluid())) {
            return safeFill(tankA, resource, doFill);
        }
        if (tankB.getFluid() == null && goesWith(resource, tankA.getFluid())) {
            return safeFill(tankB, resource, doFill);
        }
        return 0;
    }

    /**
     * FluidTank.fill on a tank holding more than its capacity (Tank Extensions taken out) sets it
     * down to the capacity - the rest just vanished. Such a tank takes nothing until used down.
     */
    public static int safeFill(FluidTank tank, FluidStack resource, boolean doFill) {
        return tank.getFluidAmount() >= tank.getCapacity() ? 0 : tank.fill(resource, doFill);
    }

    /**
     * A fluid for the empty input tank: only if some recipe takes it together with what the other
     * tank holds - otherwise a pipe could park HCl beside Cl2 and jam the machine for good (the
     * items have the same check, fitsSomeRecipe).
     */
    private boolean goesWith(FluidStack incoming, FluidStack other) {
        if (other == null || other.getFluid() == null) {
            return true;
        }
        for (MachineRecipe r : RecipeRegistry.recipesFor(machineType)) {
            if (r.fluidInputA != null && r.fluidInputB != null
                    && (r.fluidInputA.isFluidEqual(incoming) && r.fluidInputB.isFluidEqual(other)
                    || r.fluidInputB.isFluidEqual(incoming) && r.fluidInputA.isFluidEqual(other))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null) {
            return null;
        }
        for (FluidTank tank : new FluidTank[]{outputTankA, outputTankB}) {
            if (tank.getFluid() != null && resource.isFluidEqual(tank.getFluid())) {
                return tank.drain(resource.amount, doDrain);
            }
        }
        return null;
    }

    /**
     * Any fluid: the fuller output tank. With two output fluids (Air Separator, Chlor-Alkali) and
     * two pipes, the second pipe then takes the second fluid instead of the one the first pipe is
     * already emptying - otherwise that tank filled up and stalled the machine.
     */
    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        FluidTank fuller = outputTankB.getFluidAmount() > outputTankA.getFluidAmount() ? outputTankB : outputTankA;
        return fuller.getFluidAmount() > 0 ? fuller.drain(maxDrain, doDrain) : null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return RecipeRegistry.isValidFluidInput(machineType, fluid);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{tankA.getInfo(), tankB.getInfo(), outputTankA.getInfo(), outputTankB.getInfo()};
    }

    // ---- NBT ----

    // ---- facing: the side the front texture shows on (only a look - every side works the same) ----

    /** South for machines placed before machines turned (their front was always drawn there). */
    private net.minecraftforge.common.util.ForgeDirection facing = net.minecraftforge.common.util.ForgeDirection.SOUTH;

    public net.minecraftforge.common.util.ForgeDirection getFacing() {
        return facing;
    }

    /** Horizontal sides only; anything else keeps the current facing. */
    public void setFacing(net.minecraftforge.common.util.ForgeDirection side) {
        if (side != null && side != net.minecraftforge.common.util.ForgeDirection.UNKNOWN && side.offsetY == 0) {
            facing = side;
            markDirty();
            if (worldObj != null && !worldObj.isRemote) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    /** The client only needs the facing to draw the block - not the slots and tanks. */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("Facing", facing.ordinal());
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        NBTTagCompound nbt = pkt.func_148857_g();
        if (nbt != null && nbt.hasKey("Facing")) {
            facing = net.minecraftforge.common.util.ForgeDirection.getOrientation(nbt.getInteger("Facing"));
            if (worldObj != null) {
                worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        net.minecraftforge.common.util.ForgeDirection f = nbt.hasKey("Facing")
                ? net.minecraftforge.common.util.ForgeDirection.getOrientation(nbt.getInteger("Facing"))
                : net.minecraftforge.common.util.ForgeDirection.SOUTH;
        facing = f.offsetY == 0 && f != net.minecraftforge.common.util.ForgeDirection.UNKNOWN ? f : net.minecraftforge.common.util.ForgeDirection.SOUTH;
        MachineType[] types = MachineType.values();
        int typeOrdinal = nbt.getInteger("MachineType");
        machineType = types[typeOrdinal >= 0 && typeOrdinal < types.length ? typeOrdinal : 0];
        // The tier always follows the type (as in setMachineType): super read "TierSC", which a
        // machine saved without it (older worlds) had as 0 - an HV/EV machine came back LV.
        setTier(machineType.tier);
        progressTicks = nbt.getInteger("Progress");
        heat = nbt.getInteger("Heat");
        smeltProgress[0] = nbt.getInteger("SmeltP0");
        smeltProgress[1] = nbt.getInteger("SmeltP1");
        storedXp = nbt.getDouble("StoredXp");                 // (an older float tag reads as well)
        keepWarm = nbt.getBoolean("KeepWarm");
        matter = nbt.getInteger("Matter");
        matterLiquid = nbt.getBoolean("MatterLiquid");
        coolingDown = nbt.getBoolean("CoolingDown");
        overheatedThisRun = nbt.getBoolean("OverheatedRun");
        tankA.readFromNBT(nbt.getCompoundTag("TankA"));
        tankB.readFromNBT(nbt.getCompoundTag("TankB"));
        outputTankA.readFromNBT(nbt.getCompoundTag("TankOutA"));
        outputTankB.readFromNBT(nbt.getCompoundTag("TankOutB"));

        NBTTagList list = nbt.getTagList("Slots", 10);
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = null;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound slotNbt = list.getCompoundTagAt(i);
            int slot = slotNbt.getInteger("Slot");
            if (slot >= 0 && slot < SLOT_COUNT) {
                slots[slot] = ItemStack.loadItemStackFromNBT(slotNbt);
            }
        }
        syncTankCapacity();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("MachineType", machineType.ordinal());
        nbt.setInteger("Facing", facing.ordinal());
        nbt.setInteger("Progress", progressTicks);
        nbt.setInteger("Heat", heat);
        nbt.setInteger("SmeltP0", smeltProgress[0]);
        nbt.setInteger("SmeltP1", smeltProgress[1]);
        nbt.setDouble("StoredXp", storedXp);
        nbt.setBoolean("KeepWarm", keepWarm);
        if (matter > 0) {
            nbt.setInteger("Matter", matter);
        }
        if (matterLiquid) {
            nbt.setBoolean("MatterLiquid", true);
        }
        nbt.setBoolean("CoolingDown", coolingDown);
        nbt.setBoolean("OverheatedRun", overheatedThisRun);
        nbt.setTag("TankA", tankA.writeToNBT(new NBTTagCompound()));
        nbt.setTag("TankB", tankB.writeToNBT(new NBTTagCompound()));
        nbt.setTag("TankOutA", outputTankA.writeToNBT(new NBTTagCompound()));
        nbt.setTag("TankOutB", outputTankB.writeToNBT(new NBTTagCompound()));

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i] != null) {
                NBTTagCompound slotNbt = new NBTTagCompound();
                slotNbt.setInteger("Slot", i);
                slots[i].writeToNBT(slotNbt);
                list.appendTag(slotNbt);
            }
        }
        nbt.setTag("Slots", list);
    }
}
