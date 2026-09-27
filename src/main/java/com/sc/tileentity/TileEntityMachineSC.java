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
    private static final int SLOT_COUNT = INPUT_SLOTS + OUTPUT_SLOTS + UPGRADE_SLOTS;
    /** Inputs and outputs - what automation sees from every side. */
    private static final int[] IO_SLOTS = {0, 1, 2, 3, 4, 5};
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
        for (int i = FIRST_UPGRADE_SLOT; i < SLOT_COUNT; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return Math.min(n, UpgradeType.MAX_EFFECTIVE);
    }

    /** Recipe time with overclockers: x0.7 each, at least one tick. */
    public int effectiveTicks(MachineRecipe recipe) {
        return Math.max(1, (int) Math.round(recipe.ticks * Math.pow(0.7, upgradeCount(UpgradeType.OVERCLOCKER))));
    }

    /** Energy per working tick: x1.6 per overclocker, x1.25 per quality control. */
    public int effectiveEuPerTick() {
        double eu = machineType.euPerTick * Math.pow(1.6, upgradeCount(UpgradeType.OVERCLOCKER))
                * Math.pow(1.25, upgradeCount(UpgradeType.QUALITY));
        return (int) Math.min(Integer.MAX_VALUE / 4, Math.ceil(eu));
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

    /** Every tank's size: 4000 mB + 8000 per Tank Extension upgrade (up to 4). */
    public int tankCapacity() {
        return TANK_CAPACITY + Math.min(UpgradeType.MAX_TANK_UPGRADES, upgradeCount(UpgradeType.TANK_EXTENSION)) * UpgradeType.TANK_PER_UPGRADE;
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

    /**
     * The power switch: off, the machine takes no energy at all (so no line can overvolt it) and
     * does nothing. A placed machine starts off - upgrades in first, then switch on.
     */
    private boolean powerOn = true;
    /** 0 works always, 1 only with a redstone signal, 2 only without one. */
    private int redstoneMode;
    public static final String ITEM_REDSTONE_KEY = "RedstoneSC";

    public boolean isPowerOn() {
        return powerOn;
    }

    public void setPowerOn(boolean on) {
        powerOn = on;
        markDirty();
    }

    public int getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(int mode) {
        redstoneMode = Math.max(0, Math.min(2, mode));
        markDirty();
    }

    /** Switch and redstone mode as one int for the container sync. */
    public int powerFlags() {
        return (powerOn ? 1 : 0) | redstoneMode << 1;
    }

    public void setPowerFlagsClient(int flags) {
        powerOn = (flags & 1) != 0;
        redstoneMode = (flags >> 1) & 3;
    }

    @Override
    public int demandedEnergy() {
        return powerOn ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }

    private boolean redstoneAllows() {
        if (redstoneMode == 0) {
            return true;
        }
        boolean signal = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        return redstoneMode == 1 ? signal : !signal;
    }

    /**
     * The strongest voltage a neighbour can bring in: a cable beside the machine, or an energy
     * source touching it; null when there's none. Works on the client too (for the screen).
     */
    public com.sc.energy.Tier lineTier() {
        com.sc.energy.Tier best = null;
        for (net.minecraftforge.common.util.ForgeDirection dir : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
            net.minecraft.tileentity.TileEntity te = worldObj == null ? null : neighbour(dir);
            com.sc.energy.Tier t = null;
            if (te instanceof TileEntityConduitBundleSC && ((TileEntityConduitBundleSC) te).getCable() != null) {
                t = ((TileEntityConduitBundleSC) te).getCable().tier;
            } else if (te instanceof TileEntityCableSC && ((TileEntityCableSC) te).getCableType() != null) {
                t = ((TileEntityCableSC) te).getCableType().tier;
            } else if (te instanceof com.sc.energy.TileEntityEnergyBase && ((com.sc.energy.TileEntityEnergyBase) te).isEnergySource()) {
                t = ((com.sc.energy.TileEntityEnergyBase) te).outputTier();
            }
            if (t != null && (best == null || t.ordinal() > best.ordinal())) {
                best = t;
            }
        }
        return best;
    }

    /** A line beside it stronger than its input (and no universal transformer): switching on would blow it up. */
    public boolean lineTooStrong() {
        com.sc.energy.Tier t = lineTier();
        return t != null && !acceptsAnyVoltage() && inputTier().excessTiersOf(t) > 0;
    }

    /** NBT key the machine's item carries its upgrades under - they come back with it on placement. */
    public static final String ITEM_UPGRADES_KEY = "UpgradesSC";
    /** Set once the upgrades went into the dropped item, so breakBlock doesn't drop them loose too. */
    private boolean upgradesInItem;

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
        if (!upgradesInItem) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Items", list);
        return tag;
    }

    /** Whether breakBlock should leave the upgrade slots alone (they're in the dropped item). */
    public boolean upgradesInItem() {
        return upgradesInItem;
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
                    fluids[i] = new FluidStack(fluid.getFluid(), Math.min(MAX_TANK_CAPACITY, fluid.amount));
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
        if (!powerOn || !redstoneAllows()) {
            status = powerOn ? MachineStatus.REDSTONE : MachineStatus.DISABLED;
            dissipateHeat();
            return;
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
            status = MachineStatus.OUTPUT_FULL;
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
            if (te instanceof IInventory && budget > 0) {
                IInventory source = (IInventory) te;
                for (int slot : InvUtilSC.slots(source, dir)) {
                    ItemStack stack = source.getStackInSlot(slot);
                    if (budget <= 0) {
                        break;
                    }
                    if (stack == null || !RecipeRegistry.isValidInput(machineType, stack) || !InvUtilSC.canTake(source, slot, stack, dir)) {
                        continue;
                    }
                    ItemStack want = stack.copy();
                    want.stackSize = Math.min(budget, stack.stackSize);
                    int took = want.stackSize - InvUtilSC.insert(this, ForgeDirection.UP, want);
                    if (took > 0) {
                        source.decrStackSize(slot, took);
                        source.markDirty();
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
     * @return mB vented
     */
    public int ventInputTanks() {
        int vented = tankA.getFluidAmount() + tankB.getFluidAmount();
        if (vented > 0) {
            tankA.setFluid(null);
            tankB.setFluid(null);
        } else {
            // Inputs already empty: the second click clears the output tanks, so a product nothing
            // takes away (no pipe on that face) can't block the machine for good.
            vented = outputTankA.getFluidAmount() + outputTankB.getFluidAmount();
            outputTankA.setFluid(null);
            outputTankB.setFluid(null);
        }
        markDirty();
        return vented;
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
                } else if (simSlots[i].getItem() == stack.getItem() && simSlots[i].getItemDamage() == stack.getItemDamage() && remaining[i] > 0) {
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
            } else if (slots[slot].getItem() == stack.getItem() && slots[slot].getItemDamage() == stack.getItemDamage()) {
                int room = slots[slot].getMaxStackSize() - slots[slot].stackSize;
                int place = Math.min(stack.stackSize, room);
                slots[slot].stackSize += place;
                stack.stackSize -= place;
            }
        }
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
        if (slot >= FIRST_UPGRADE_SLOT) {
            return stack != null && stack.getItem() instanceof com.sc.item.ItemUpgradeSC
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).generatorOnly()
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).fieldOnly();
        }
        return slot < INPUT_SLOTS && RecipeRegistry.isValidInput(machineType, stack);
    }

    /** Every side: inputs go in, products come out (the upgrade slots are the player's only). */
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return IO_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < INPUT_SLOTS && isItemValidForSlot(slot, stack) && fitsSomeRecipe(slot, stack);
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
            return tankA.fill(resource, doFill);
        }
        if (tankB.getFluid() != null && tankB.getFluid().isFluidEqual(resource)) {
            return tankB.fill(resource, doFill);
        }
        if (tankA.getFluid() == null) {
            return tankA.fill(resource, doFill);
        }
        if (tankB.getFluid() == null) {
            return tankB.fill(resource, doFill);
        }
        return 0;
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
        progressTicks = nbt.getInteger("Progress");
        heat = nbt.getInteger("Heat");
        coolingDown = nbt.getBoolean("CoolingDown");
        overheatedThisRun = nbt.getBoolean("OverheatedRun");
        powerOn = !nbt.getBoolean("PowerOff");                  // machines saved before the switch: on
        redstoneMode = Math.max(0, Math.min(2, nbt.getInteger("RedstoneMode")));
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
        nbt.setBoolean("CoolingDown", coolingDown);
        nbt.setBoolean("PowerOff", !powerOn);
        nbt.setInteger("RedstoneMode", redstoneMode);
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
