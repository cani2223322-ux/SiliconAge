package com.sc.tileentity;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.machine.UpgradeType;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * One class drives every generator, parameterized by GeneratorType from block metadata - same
 * pattern as the machine/cable/pipe TileEntities. A generator is an energy source; the ones
 * that must be lit first (Fusion Reactor, Tokamak, Exo Reactor) are sinks until their ignition
 * charge is in, then sources.
 *
 * Kinds (GeneratorType.Kind):
 * - PASSIVE (solar): open sky only; full by day, half at night, less in the rain.
 * - FLUID_FUEL: one fuel tank (the Combustion Generator takes several fuels, and coal as a
 *   fallback; the Geothermal Generator also takes lava buckets).
 * - DUAL_FLUID: two fuel tanks (Fuel Cell: hydrogen + oxygen, its water pumped out of a third
 *   tank; Plasma Reactor: argon + deuterium).
 * - SOLID: furnace fuels. WIND: height, open air, weather, a rotor that wears. WATER: flowing
 *   water round it. THERMO: hot and cold blocks next to it. RTG: radioisotope capsules.
 * - FUSION (Fusion Reactor, Tokamak): ignition, deuterium cells, a Li-blanket module; the output
 *   ramps up after ignition and the plasma heats up while the buffer is full - at the limit the
 *   reactor shuts down (no blast) and has to be lit again. The Tokamak needs its ring of coils.
 * - EXO: ignition, then liquid helium cooling only; no coolant -> it heats up and shuts down.
 * - CREATIVE: endless energy at the tier chosen on its screen.
 *
 * Four upgrade slots: Overdrive / Economizer (fuel-burning kinds), Transformer (a higher output
 * tier), Energy Storage (+10 000 EU of buffer).
 */
public class TileEntityGeneratorSC extends TileEntityEnergyBase implements ISidedInventory, IFluidHandler {

    public static final int MODULE_LIFE_TICKS = 1000000;
    /**
     * TODO(design doc §18.2 says "Deuterium Cell x1/тик"): literally that burns a stack of 64
     * cells in 3 seconds, while one cell takes hundreds of halite ores through the electrolyzer -
     * the reactor could never run. One cell now fuels 5 minutes (12.3M EU at 2048 EU/t).
     */
    public static final int CELL_BURN_TICKS = 6000;
    public static final int TANK_CAPACITY = 4000;
    /** Combustion Generator's coal fallback: vanilla burn time / 12 ticks of output. */
    public static final int SOLID_FUEL_DIVISOR = 12;
    /** Solid Fuel Generator: vanilla burn time / 4 ticks at 16 EU/t (coal: 6 400 EU). */
    public static final int SOLID_GEN_DIVISOR = 4;
    public static final int CAPSULE_EU = 32;
    /** Heat (plasma temperature, 0-1000 = 0-150 million K) at which a reactor shuts itself down. */
    public static final int HEAT_LIMIT = 1000;
    /** Ramp after ignition: 0-1000, output = rated x ramp / 1000; the first tick gives a tenth. */
    public static final int RAMP_FULL = 1000;

    public static final int SLOT_FUEL = 0, SLOT_BLANKET = 1, FIRST_UPGRADE_SLOT = 2, UPGRADE_SLOTS = 4;
    public static final int SLOT_COUNT = FIRST_UPGRADE_SLOT + UPGRADE_SLOTS;

    private GeneratorType generatorType = GeneratorType.COMBUSTION;
    private final FluidTank fuelTank = new FluidTank(TANK_CAPACITY);
    private final FluidTank fuelTank2 = new FluidTank(TANK_CAPACITY);
    private final FluidTank outTank = new FluidTank(TANK_CAPACITY);
    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];

    private long ignitionEU;
    private boolean ignited;
    private int moduleLifeRemaining;
    /** Ticks of fusion left from the deuterium cell already fed in (fractional under Overdrive / Economizer). */
    private double cellBurnRemaining;
    /** Ticks of burning left on the solid fuel item (fractional for the same reason). */
    private double solidBurnTicks;
    private double fuelDebt, fuel2Debt;
    private int heat, ramp;
    /** Wind / thermo output worked out once a second; wind height and freedom, thermo pairs and dT - for the screen. */
    private int cachedOutput, infoA, infoB;
    private boolean structureOk;
    private int creativeTier = Tier.values().length - 1;
    /** EU made last tick - for the screen and WAILA. */
    private int lastOutput;
    private GeneratorStatus status = GeneratorStatus.IDLE;
    /** The face with the front texture, turned to the player on placement - cosmetic, energy leaves every face. */
    private ForgeDirection facing = ForgeDirection.SOUTH;

    public ForgeDirection getFacing() {
        return facing;
    }

    /** Horizontal faces only (like a furnace); anything else is the old default, south. */
    public void setFacing(ForgeDirection facing) {
        this.facing = facing != null && facing != ForgeDirection.UNKNOWN && facing.offsetY == 0 ? facing : ForgeDirection.SOUTH;
    }

    public void setGeneratorType(GeneratorType type) {
        this.generatorType = type;
        setTier(type.tier);
    }

    public GeneratorType getGeneratorType() {
        return generatorType;
    }

    public GeneratorStatus getStatus() {
        return status;
    }

    public long getIgnitionEU() {
        return ignitionEU;
    }

    public boolean isIgnited() {
        return ignited;
    }

    public FluidTank getFuelTank() {
        return fuelTank;
    }

    public FluidTank getFuelTank2() {
        return fuelTank2;
    }

    public FluidTank getOutTank() {
        return outTank;
    }

    public int getLastOutput() {
        return lastOutput;
    }

    public int getHeat() {
        return heat;
    }

    public int getRamp() {
        return ramp;
    }

    public int getInfoA() {
        return infoA;
    }

    public int getInfoB() {
        return infoB;
    }

    public Tier getCreativeTier() {
        return Tier.values()[Math.max(0, Math.min(Tier.values().length - 1, creativeTier))];
    }

    /** The Creative Generator's screen button: next tier. */
    public void cycleCreativeTier() {
        creativeTier = (creativeTier + 1) % Tier.values().length;
        markDirty();
        refreshEnergyNet();
    }

    /** EU the Fusion Reactor needs pumped in before it lights (§18.2) - drives the screen's ignition bar. */
    public static long getIgnitionThreshold() {
        return GeneratorType.FUSION_REACTOR.ignitionThreshold();
    }

    // ---- client-side sync only, written by ContainerGeneratorSC.updateProgressBar() ----

    public static void setTankClient(FluidTank tank, int fluidId, int amount) {
        Fluid fluid = amount <= 0 ? null : FluidRegistry.getFluid(fluidId);
        tank.setFluid(fluid == null ? null : new FluidStack(fluid, amount));
    }

    public void setFuelFluidClient(int fluidId, int amount) {
        setTankClient(fuelTank, fluidId, amount);
    }

    public void setIgnitionClient(long eu, boolean isIgnited) {
        this.ignitionEU = eu;
        this.ignited = isIgnited;
    }

    public void setStatusClient(GeneratorStatus value) {
        this.status = value;
    }

    public void setLiveClient(int output, int heatValue, int rampValue, int a, int b, int tierOrdinal) {
        lastOutput = output;
        heat = heatValue;
        ramp = rampValue;
        infoA = a;
        infoB = b;
        creativeTier = tierOrdinal;
    }

    // ---- upgrades ----

    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (int i = FIRST_UPGRADE_SLOT; i < SLOT_COUNT; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        int cap = type.generatorOnly() ? UpgradeType.MAX_GENERATOR_EFFECTIVE : UpgradeType.MAX_EFFECTIVE;
        return Math.min(n, cap);
    }

    /** Output multiplier from Overdrive / Economizer (fuel-burning generators only). */
    public double outputMultiplier() {
        if (!generatorType.burnsFuel()) {
            return 1;
        }
        return Math.pow(1.5, upgradeCount(UpgradeType.OVERDRIVE)) * Math.pow(0.9, upgradeCount(UpgradeType.ECONOMIZER));
    }

    public double fuelMultiplier() {
        if (!generatorType.burnsFuel()) {
            return 1;
        }
        return Math.pow(1.75, upgradeCount(UpgradeType.OVERDRIVE)) * Math.pow(0.7, upgradeCount(UpgradeType.ECONOMIZER));
    }

    /** Rated output with the upgrades - what a fuel generator makes each tick while it runs. */
    public int ratedOutput() {
        return (int) Math.round(generatorType.euPerTick * outputMultiplier());
    }

    /** Transformer upgrades raise the output voltage a tier each; the Creative Generator's is chosen on its screen. */
    @Override
    public Tier outputTier() {
        if (generatorType == GeneratorType.CREATIVE) {
            return getCreativeTier();
        }
        Tier[] tiers = Tier.values();
        return tiers[Math.min(tiers.length - 1, getTier().ordinal() + upgradeCount(UpgradeType.TRANSFORMER))];
    }

    /** Tier buffer + 10 000 EU per storage upgrade, never less than two ticks of output. */
    @Override
    public int getMaxEnergyStored() {
        if (generatorType == GeneratorType.CREATIVE) {
            return getCreativeTier().getBuffer();
        }
        long buffer = (long) super.getMaxEnergyStored() + (long) upgradeCount(UpgradeType.ENERGY_STORAGE) * UpgradeType.STORAGE_PER_UPGRADE;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(buffer, 2L * ratedOutput()));
    }

    // ---- energy in (ignition) and out ----

    @Override
    public boolean isEnergySource() {
        return !generatorType.needsIgnition() || ignited;
    }

    @Override
    public boolean isEnergySink() {
        return generatorType.needsIgnition() && !ignited;
    }

    @Override
    public int demandedEnergy() {
        if (isEnergySink()) {
            return (int) Math.min(Integer.MAX_VALUE, generatorType.ignitionThreshold() - ignitionEU);
        }
        return 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (!isEnergySink()) {
            return 0; // every other generator, and an already-lit reactor, never accepts energy
        }
        int room = (int) Math.min(Integer.MAX_VALUE, generatorType.ignitionThreshold() - ignitionEU);
        int accepted = Math.max(0, Math.min(room, amount));
        if (!simulate) {
            ignitionEU += accepted;
        }
        return accepted;
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
        int before = getEnergyStored();
        switch (generatorType.kind) {
            case PASSIVE: updateSolar(); break;
            case FLUID_FUEL: updateFluidFuel(); break;
            case DUAL_FLUID: updateDualFluid(); break;
            case SOLID: updateSolid(); break;
            case WIND: updateWind(); break;
            case WATER: updateWater(); break;
            case THERMO: updateThermo(); break;
            case RTG: updateRtg(); break;
            case FUSION: updateFusion(); break;
            case EXO: updateExo(); break;
            case CREATIVE: updateCreative(); break;
            default:
        }
        if (generatorType != GeneratorType.CREATIVE) {
            lastOutput = Math.max(0, getEnergyStored() - before);
        }
    }

    private boolean bufferFull() {
        if (getEnergyStored() >= getMaxEnergyStored()) {
            status = GeneratorStatus.BUFFER_FULL;
            return true;
        }
        return false;
    }

    /** Free sources (sun, wind, water, heat, isotopes): make `eu` if there's room. */
    private void give(int eu, GeneratorStatus none) {
        if (eu <= 0) {
            status = none;
        } else if (!bufferFull()) {
            addEnergy(eu);
            status = GeneratorStatus.GENERATING;
        }
    }

    /** Open sky only: full by day, half at night, 60% of that in the rain. */
    private void updateSolar() {
        if (!worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord)) {
            status = GeneratorStatus.NO_SUNLIGHT;
            return;
        }
        double f = worldObj.isDaytime() ? 1.0 : 0.5;
        if (worldObj.canLightningStrikeAt(xCoord, yCoord + 1, zCoord)) {
            f *= 0.6;
        }
        give(Math.max(1, (int) Math.round(generatorType.euPerTick * f)), GeneratorStatus.NO_SUNLIGHT);
    }

    private void updateFluidFuel() {
        if (bufferFull()) {
            return;
        }
        if (generatorType == GeneratorType.GEOTHERMAL) {
            takeLavaBucket();
        }
        FluidStack held = fuelTank.getFluid();
        double euPerMb = held == null ? 0 : generatorType.euPerMb(held.getFluid().getName());
        if (euPerMb > 0 && held.amount > 0) {
            double need = fuelDebt + generatorType.euPerTick / euPerMb * fuelMultiplier();
            int mb = (int) need;
            if (held.amount >= mb) {
                if (mb > 0) {
                    fuelTank.drain(mb, true);
                }
                fuelDebt = need - mb;
                addEnergy(ratedOutput());
                status = GeneratorStatus.GENERATING;
                return;
            }
        }
        if (generatorType == GeneratorType.COMBUSTION && burnSolid(SOLID_FUEL_DIVISOR)) {
            addEnergy(ratedOutput());
            status = GeneratorStatus.GENERATING;
            return;
        }
        status = GeneratorStatus.NO_FUEL;
    }

    /** Geothermal: a lava bucket in the slot goes into the tank, the empty bucket stays for a hopper. */
    private void takeLavaBucket() {
        ItemStack s = slots[SLOT_FUEL];
        if (s != null && s.getItem() == Items.lava_bucket && fuelTank.getCapacity() - fuelTank.getFluidAmount() >= 1000) {
            if (fuelTank.fill(new FluidStack(FluidRegistry.LAVA, 1000), true) == 1000) {
                slots[SLOT_FUEL] = new ItemStack(Items.bucket);
                markDirty();
            }
        }
    }

    private void updateDualFluid() {
        if (bufferFull()) {
            return;
        }
        double fm = fuelMultiplier();
        double need1 = fuelDebt + generatorType.fuelRatePerTick * fm;
        double need2 = fuel2Debt + generatorType.fuel2RatePerTick * fm;
        int mb1 = (int) need1, mb2 = (int) need2;
        if (fuelTank.getFluidAmount() < Math.max(1, mb1) || fuelTank2.getFluidAmount() < Math.max(1, mb2)) {
            status = GeneratorStatus.NO_FUEL;
            return;
        }
        if (generatorType == GeneratorType.FUEL_CELL && mb2 > 0) {
            Fluid water = FluidRegistry.WATER;
            if (outTank.fill(new FluidStack(water, mb2), false) < mb2) {
                status = GeneratorStatus.WATER_FULL;
                return;
            }
            outTank.fill(new FluidStack(water, mb2), true);
        }
        if (mb1 > 0) {
            fuelTank.drain(mb1, true);
        }
        if (mb2 > 0) {
            fuelTank2.drain(mb2, true);
        }
        fuelDebt = need1 - mb1;
        fuel2Debt = need2 - mb2;
        addEnergy(ratedOutput());
        status = GeneratorStatus.GENERATING;
    }

    /**
     * Burns furnace fuel from the fuel slot: vanilla burn time / divisor ticks of output, used up
     * faster under Overdrive and slower under Economizer.
     */
    private boolean burnSolid(int divisor) {
        if (solidBurnTicks <= 0) {
            ItemStack fuel = slots[SLOT_FUEL];
            if (fuel == null || !TileEntityFurnace.isItemFuel(fuel)) {
                return false;
            }
            solidBurnTicks += Math.max(1, TileEntityFurnace.getItemBurnTime(fuel) / divisor);
            ItemStack container = fuel.getItem().getContainerItem(fuel); // lava bucket -> bucket
            fuel.stackSize--;
            if (fuel.stackSize <= 0) {
                slots[SLOT_FUEL] = container;
            }
            markDirty();
        }
        solidBurnTicks -= fuelMultiplier();
        return true;
    }

    private void updateSolid() {
        if (bufferFull()) {
            return;
        }
        if (burnSolid(SOLID_GEN_DIVISOR)) {
            addEnergy(ratedOutput());
            status = GeneratorStatus.GENERATING;
        } else {
            status = GeneratorStatus.NO_FUEL;
        }
    }

    /**
     * Wind: full output 96 blocks above sea level, nothing at or below it; every solid block in
     * the 5x5x5 round it takes 4%; rain x1.25, a storm x1.5; gusts of +-10%. Worked out once a
     * second. The rotor wears one step a second while it turns.
     */
    private void updateWind() {
        ItemStack rotor = slots[SLOT_FUEL];
        if (rotor == null || rotor.getItem() != com.sc.init.ModItems.windRotor) {
            status = GeneratorStatus.NO_ROTOR;
            return;
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            int height = yCoord - 64;
            int blocked = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if ((dx != 0 || dy != 0 || dz != 0) && !worldObj.isAirBlock(xCoord + dx, yCoord + dy, zCoord + dz)) {
                            blocked++;
                        }
                    }
                }
            }
            double f = Math.max(0, Math.min(1, height / 96.0)) * Math.max(0, 1 - blocked * 0.04);
            if (worldObj.isThundering()) {
                f *= 1.5;
            } else if (worldObj.isRaining()) {
                f *= 1.25;
            }
            f *= 0.9 + worldObj.rand.nextDouble() * 0.2;
            cachedOutput = Math.min(Tier.MV.getVoltage(), (int) Math.round(generatorType.euPerTick * f));
            infoA = Math.max(0, height);
            infoB = Math.max(0, 100 - blocked * 4);
        }
        if (cachedOutput <= 0) {
            status = GeneratorStatus.NO_WIND;
            return;
        }
        if (bufferFull()) {
            return;
        }
        addEnergy(cachedOutput);
        status = GeneratorStatus.GENERATING;
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            rotor.setItemDamage(rotor.getItemDamage() + 1);
            if (rotor.getItemDamage() >= rotor.getMaxDamage()) {
                slots[SLOT_FUEL] = null;
                worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.break", 1F, 0.8F);
            }
            markDirty();
        }
    }

    /** 4 EU/t for each side (north, south, east, west) with flowing water against it. */
    private void updateWater() {
        int flowing = 0;
        for (ForgeDirection d : new ForgeDirection[]{ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST}) {
            int x = xCoord + d.offsetX, z = zCoord + d.offsetZ;
            Block b = worldObj.getBlock(x, yCoord, z);
            if (b.getMaterial() == Material.water && (b == Blocks.flowing_water || worldObj.getBlockMetadata(x, yCoord, z) != 0)) {
                flowing++;
            }
        }
        infoA = flowing;
        give(flowing * 4, GeneratorStatus.NO_WATER);
    }

    /**
     * Thermoelectric: each hot side (lava, fire) paired with a cold one (water, ice, snow) is a
     * Peltier pair; output = pairs x (mean hot - mean cold) / 8, at most the rated 384 EU/t.
     */
    private void updateThermo() {
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            int hot = 0, cold = 0, hotSum = 0, coldSum = 0;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int t = temperatureAt(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
                if (t >= 700) {
                    hot++;
                    hotSum += t;
                } else if (t > 0 && t <= 310) {
                    cold++;
                    coldSum += t;
                }
            }
            int pairs = Math.min(hot, cold);
            int dT = pairs == 0 ? 0 : hotSum / hot - coldSum / cold;
            infoA = pairs;
            infoB = dT;
            cachedOutput = Math.min(generatorType.euPerTick, pairs * dT / 8);
        }
        give(cachedOutput, GeneratorStatus.NO_HEAT);
    }

    /** Kelvin of a block next to the generator, 0 if it neither heats nor cools. */
    private int temperatureAt(int x, int y, int z) {
        Block b = worldObj.getBlock(x, y, z);
        if (b.getMaterial() == Material.lava) {
            return 1300;
        }
        if (b == Blocks.fire) {
            return 1000;
        }
        if (b.getMaterial() == Material.water) {
            return 300;
        }
        if (b == Blocks.packed_ice) {
            return 250;
        }
        if (b == Blocks.ice || b == Blocks.snow) {
            return 270;
        }
        return 0;
    }

    /** Each capsule gives 32 EU/t and decays one step a second whether the energy is used or not. */
    private void updateRtg() {
        int capsules = 0;
        boolean tick = worldObj.getTotalWorldTime() % 20 == 0;
        for (int i = SLOT_FUEL; i <= SLOT_BLANKET; i++) {
            ItemStack s = slots[i];
            if (s == null || s.getItem() != com.sc.init.ModItems.isotopeCapsule) {
                continue;
            }
            capsules++;
            if (tick) {
                s.setItemDamage(s.getItemDamage() + 1);
                if (s.getItemDamage() >= s.getMaxDamage()) {
                    slots[i] = null;
                }
                markDirty();
            }
        }
        infoA = capsules;
        give(capsules * CAPSULE_EU, GeneratorStatus.NO_CAPSULE);
    }

    // ---- reactors ----

    /** Lights a FUSION / EXO generator once its charge is in (and, for FUSION, a blanket module is there). */
    private boolean ignite() {
        if (ignitionEU < generatorType.ignitionThreshold()) {
            status = GeneratorStatus.IGNITING;
            return false;
        }
        if (generatorType.kind == GeneratorType.Kind.FUSION) {
            ItemStack blanket = slots[SLOT_BLANKET];
            if (blanket == null || blanket.getItem() != com.sc.init.ModItems.component("liBlanketModule")) {
                status = GeneratorStatus.NO_BLANKET;
                return false;
            }
            // §18.2: ignition is a one-off expensive event, and the blanket module is what the
            // reaction breeds tritium in - both are spent here.
            blanket.stackSize--;
            if (blanket.stackSize <= 0) {
                slots[SLOT_BLANKET] = null;
            }
            moduleLifeRemaining = MODULE_LIFE_TICKS;
        }
        ignitionEU = 0;
        ignited = true;
        ramp = RAMP_FULL / 10;
        status = GeneratorStatus.GENERATING;
        markDirty();
        refreshEnergyNet();      // sink -> source: IC2 caches that on load
        return true;
    }

    /** At the heat limit the reactor goes out - no blast; it has to be lit again. */
    private void shutDown(GeneratorStatus why) {
        ignited = false;
        ramp = 0;
        status = why;
        markDirty();
        refreshEnergyNet();      // source -> sink again
    }

    /**
     * Running heat of a lit reactor at full power: its base plus 100 per Overdrive upgrade - an
     * overdriven reactor runs hot, and a full buffer can then tip it over the limit.
     */
    private int runningHeat(int base) {
        return base + 100 * upgradeCount(UpgradeType.OVERDRIVE);
    }

    /** One tick of a lit reactor making energy: ramps up, heat follows the power. */
    private void burnPlasma(int base) {
        ramp = Math.min(RAMP_FULL, ramp + 1);
        int target = runningHeat(base) * ramp / RAMP_FULL;
        heat += heat < target ? 2 : heat > target ? -1 : 0;
        addEnergy((int) Math.round(ratedOutput() * (double) ramp / RAMP_FULL));
        status = GeneratorStatus.GENERATING;
    }

    /**
     * The buffer is full and the plasma has nowhere to put its energy: the reactor throttles
     * down (power -0.3% a tick), and while it is still above 30% the plasma heats up. At the
     * base heat that settles well below the limit; with two or more Overdrive upgrades it can
     * reach it - the reactor then shuts down (no blast) and has to be lit again.
     */
    private boolean overheating() {
        if (getEnergyStored() < getMaxEnergyStored()) {
            return false;
        }
        ramp = Math.max(RAMP_FULL / 10, ramp - 3);
        heat += ramp > RAMP_FULL * 3 / 10 ? 1 : -1;
        heat = Math.max(0, heat);
        status = GeneratorStatus.BUFFER_FULL;
        if (heat >= HEAT_LIMIT) {
            shutDown(GeneratorStatus.OVERHEATED);
        }
        return true;
    }

    private void updateFusion() {
        if (generatorType == GeneratorType.TOKAMAK && worldObj.getTotalWorldTime() % 20 == 0) {
            structureOk = tokamakFormed();
        }
        if (!ignited) {
            heat = Math.max(0, heat - 5);
            if (generatorType == GeneratorType.TOKAMAK && !structureOk) {
                status = GeneratorStatus.NO_STRUCTURE;
                return;
            }
            if (status == GeneratorStatus.OVERHEATED && heat > 0) {
                return;                 // cools down before it can be lit again
            }
            ignite();
            return;
        }
        if (generatorType == GeneratorType.TOKAMAK && !structureOk) {
            heat = Math.max(0, heat - 2);
            status = GeneratorStatus.NO_STRUCTURE;
            return;
        }
        if (moduleLifeRemaining <= 0) {
            shutDown(GeneratorStatus.BLANKET_DEPLETED); // Li-Blanket Module spent (§18.2) - needs replacement, re-ignition required
            return;
        }
        if (overheating()) {
            return;
        }
        if (cellBurnRemaining <= 0) {
            ItemStack cell = slots[SLOT_FUEL];
            if (cell == null || cell.getItem() != com.sc.init.ModItems.deuteriumCell) {
                heat = Math.max(0, heat - 2);
                ramp = Math.max(0, ramp - 2);
                status = GeneratorStatus.NO_DEUTERIUM;
                return;
            }
            cell.stackSize--;
            if (cell.stackSize <= 0) {
                slots[SLOT_FUEL] = null;
            }
            cellBurnRemaining += CELL_BURN_TICKS;
            markDirty();
        }
        cellBurnRemaining -= fuelMultiplier();
        moduleLifeRemaining--;
        burnPlasma(600);
    }

    /** The Tokamak's ring: all eight blocks round it on its level are Tokamak Coils. */
    public boolean tokamakFormed() {
        if (worldObj == null) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && worldObj.getBlock(xCoord + dx, yCoord, zCoord + dz) != com.sc.init.ModBlocks.tokamakCoil) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Exo Reactor: lit once, then liquid helium only (1 mB/t); without it the core heats up and shuts down. */
    private void updateExo() {
        if (!ignited) {
            heat = Math.max(0, heat - 5);
            if (status == GeneratorStatus.OVERHEATED && heat > 0) {
                return;
            }
            ignite();
            return;
        }
        if (overheating()) {
            return;
        }
        double need = fuelDebt + generatorType.fuelRatePerTick * fuelMultiplier();
        int mb = (int) need;
        if (fuelTank.getFluidAmount() < Math.max(1, mb)) {
            heat += 2;                  // no coolant: it still burns, and heats up
            if (heat >= HEAT_LIMIT) {
                shutDown(GeneratorStatus.OVERHEATED);
                return;
            }
            addEnergy((int) Math.round(ratedOutput() * (double) ramp / RAMP_FULL));
            status = GeneratorStatus.NO_COOLANT;
            return;
        }
        if (mb > 0) {
            fuelTank.drain(mb, true);
        }
        fuelDebt = need - mb;
        burnPlasma(500);
    }

    private void updateCreative() {
        addEnergy(getMaxEnergyStored());
        lastOutput = getCreativeTier().getVoltage();
        status = GeneratorStatus.GENERATING;
    }

    // ---- IInventory: fuel slot, second slot (blanket / capsule), four upgrade slots ----

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? slots[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
        }
        ItemStack result = stack.splitStack(Math.min(amount, stack.stackSize));
        if (stack.stackSize <= 0) {
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
        if (slot >= 0 && slot < SLOT_COUNT) {
            slots[slot] = stack;
            markDirty();
        }
    }

    @Override
    public String getInventoryName() {
        return "container." + generatorType.displayName;
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

    /** Which of the two item slots this generator uses (the screen places only these). */
    public static boolean usesSlot(GeneratorType type, int slot) {
        switch (type) {
            case COMBUSTION:
            case SOLID_FUEL:
            case GEOTHERMAL:
            case WIND_TURBINE:
                return slot == SLOT_FUEL;
            case RTG:
            case FUSION_REACTOR:
            case TOKAMAK:
                return slot == SLOT_FUEL || slot == SLOT_BLANKET;
            default:
                return false;
        }
    }

    public static boolean hasUpgradeSlots(GeneratorType type) {
        return type != GeneratorType.CREATIVE;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (slot >= FIRST_UPGRADE_SLOT) {
            return hasUpgradeSlots(generatorType) && stack.getItem() instanceof com.sc.item.ItemUpgradeSC
                    && com.sc.item.ItemUpgradeSC.typeOf(stack).forGenerators();
        }
        if (!usesSlot(generatorType, slot)) {
            return false;
        }
        Item item = stack.getItem();
        switch (generatorType) {
            case COMBUSTION:
            case SOLID_FUEL:
                return TileEntityFurnace.isItemFuel(stack);
            case GEOTHERMAL:
                return item == Items.lava_bucket;
            case WIND_TURBINE:
                return item == com.sc.init.ModItems.windRotor;
            case RTG:
                return item == com.sc.init.ModItems.isotopeCapsule;
            default:                    // FUSION_REACTOR, TOKAMAK
                return slot == SLOT_BLANKET ? item == com.sc.init.ModItems.component("liBlanketModule")
                        : item == com.sc.init.ModItems.deuteriumCell;
        }
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        boolean a = usesSlot(generatorType, SLOT_FUEL), b = usesSlot(generatorType, SLOT_BLANKET);
        return a && b ? new int[]{SLOT_FUEL, SLOT_BLANKET} : a ? new int[]{SLOT_FUEL} : new int[0];
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < FIRST_UPGRADE_SLOT && isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        // Only what's left behind: the empty bucket from lava, a furnace fuel's container.
        return slot == SLOT_FUEL && stack != null && !isItemValidForSlot(slot, stack)
                && (generatorType == GeneratorType.COMBUSTION || generatorType == GeneratorType.SOLID_FUEL
                || generatorType == GeneratorType.GEOTHERMAL);
    }

    // ---- IFluidHandler: fuel in, the Fuel Cell's water out ----

    private boolean fitsTank1(Fluid fluid) {
        if (fluid == null) {
            return false;
        }
        switch (generatorType.kind) {
            case FLUID_FUEL: return generatorType.euPerMb(fluid.getName()) > 0;
            case DUAL_FLUID:
            case EXO: return fluid.getName().equals(generatorType.fuelFluidName);
            default: return false;
        }
    }

    private boolean fitsTank2(Fluid fluid) {
        return fluid != null && generatorType.kind == GeneratorType.Kind.DUAL_FLUID && fluid.getName().equals(generatorType.fuel2FluidName);
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null) {
            return 0;
        }
        if (fitsTank1(resource.getFluid())) {
            return fuelTank.fill(resource, doFill);
        }
        if (fitsTank2(resource.getFluid())) {
            return fuelTank2.fill(resource, doFill);
        }
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || outTank.getFluid() == null || !resource.isFluidEqual(outTank.getFluid())) {
            return null;
        }
        return outTank.drain(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return generatorType == GeneratorType.FUEL_CELL ? outTank.drain(maxDrain, doDrain) : null;
    }

    /**
     * Only this generator's own fuels get into its tanks. It used to take any fluid: water piped
     * into a Steam Turbine filled the tank for good, bricking the generator until it was broken.
     */
    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fitsTank1(fluid) || fitsTank2(fluid);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return generatorType == GeneratorType.FUEL_CELL;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        switch (generatorType.kind) {
            case DUAL_FLUID:
                return generatorType == GeneratorType.FUEL_CELL
                        ? new FluidTankInfo[]{fuelTank.getInfo(), fuelTank2.getInfo(), outTank.getInfo()}
                        : new FluidTankInfo[]{fuelTank.getInfo(), fuelTank2.getInfo()};
            default:
                return new FluidTankInfo[]{fuelTank.getInfo()};
        }
    }

    // ---- the dropped item keeps the buffer, the fuel and the reactor's ignition (BlockGeneratorSC) ----

    /** @return what the dropped item carries, or null when there's nothing worth keeping */
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (getEnergyStored() > 0 && generatorType != GeneratorType.CREATIVE) {
            nbt.setInteger("EnergySC", getEnergyStored());
        }
        if (fuelTank.getFluidAmount() > 0) {
            nbt.setTag("FuelTank", fuelTank.writeToNBT(new NBTTagCompound()));
        }
        if (fuelTank2.getFluidAmount() > 0) {
            nbt.setTag("FuelTank2", fuelTank2.writeToNBT(new NBTTagCompound()));
        }
        if (outTank.getFluidAmount() > 0) {
            nbt.setTag("OutTank", outTank.writeToNBT(new NBTTagCompound()));
        }
        if (generatorType.needsIgnition()) {
            if (ignitionEU > 0) {
                nbt.setLong("IgnitionEU", ignitionEU);
            }
            if (ignited) {
                nbt.setBoolean("Ignited", true);
                nbt.setInteger("ModuleLife", moduleLifeRemaining);
                nbt.setInteger("CellBurn", (int) cellBurnRemaining);
                nbt.setInteger("Ramp", ramp);
            }
        }
        return nbt.hasNoTags() ? null : nbt;
    }

    /** Placed from an item that carries writeToItem()'s data. */
    public void readFromItem(NBTTagCompound nbt) {
        restoreEnergy(nbt.getInteger("EnergySC"));
        if (nbt.hasKey("OutTank")) {
            outTank.readFromNBT(nbt.getCompoundTag("OutTank"));
        }
        if (nbt.hasKey("FuelTank")) {
            fuelTank.readFromNBT(nbt.getCompoundTag("FuelTank"));
        }
        if (nbt.hasKey("FuelTank2")) {
            fuelTank2.readFromNBT(nbt.getCompoundTag("FuelTank2"));
        }
        if (generatorType.needsIgnition()) {
            ignitionEU = Math.max(0L, Math.min(generatorType.ignitionThreshold(), nbt.getLong("IgnitionEU")));
            boolean was = ignited;
            ignited = nbt.getBoolean("Ignited");
            if (ignited) {
                moduleLifeRemaining = Math.max(0, Math.min(MODULE_LIFE_TICKS, nbt.getInteger("ModuleLife")));
                cellBurnRemaining = Math.max(0, Math.min(CELL_BURN_TICKS, nbt.getInteger("CellBurn")));
                ramp = Math.max(0, Math.min(RAMP_FULL, nbt.getInteger("Ramp")));
            }
            if (ignited != was) {
                refreshEnergyNet();
            }
        }
        markDirty();
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setFacing(nbt.hasKey("Facing") ? ForgeDirection.getOrientation(nbt.getInteger("Facing")) : ForgeDirection.SOUTH);
        GeneratorType[] types = GeneratorType.values();
        int ordinal = nbt.getInteger("GeneratorType");
        generatorType = types[ordinal >= 0 && ordinal < types.length ? ordinal : 0];
        fuelTank.readFromNBT(nbt.getCompoundTag("FuelTank"));
        fuelTank2.readFromNBT(nbt.getCompoundTag("FuelTank2"));
        outTank.readFromNBT(nbt.getCompoundTag("OutTank"));
        ignitionEU = nbt.getLong("IgnitionEU");
        ignited = nbt.getBoolean("Ignited");
        moduleLifeRemaining = nbt.getInteger("ModuleLife");
        cellBurnRemaining = nbt.hasKey("CellBurnD") ? nbt.getDouble("CellBurnD") : nbt.getInteger("CellBurn");
        solidBurnTicks = nbt.hasKey("SolidBurnD") ? nbt.getDouble("SolidBurnD") : nbt.getInteger("SolidBurn");
        fuelDebt = nbt.getDouble("FuelDebt");
        fuel2Debt = nbt.getDouble("FuelDebt2");
        heat = nbt.getInteger("Heat");
        ramp = nbt.hasKey("Ramp") ? nbt.getInteger("Ramp") : ignited ? RAMP_FULL : 0;
        creativeTier = nbt.hasKey("CreativeTier") ? nbt.getInteger("CreativeTier") : Tier.values().length - 1;
        status = GeneratorStatus.byOrdinal(nbt.getInteger("Status"));
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = null;
        }
        if (nbt.hasKey("Slots")) {
            NBTTagList list = nbt.getTagList("Slots", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound s = list.getCompoundTagAt(i);
                int slot = s.getByte("Slot");
                if (slot >= 0 && slot < SLOT_COUNT) {
                    slots[slot] = ItemStack.loadItemStackFromNBT(s);
                }
            }
        } else {                        // before the upgrade slots: the two named slots
            slots[SLOT_FUEL] = nbt.hasKey("FuelSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("FuelSlot")) : null;
            slots[SLOT_BLANKET] = nbt.hasKey("BlanketSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("BlanketSlot")) : null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("GeneratorType", generatorType.ordinal());
        nbt.setTag("FuelTank", fuelTank.writeToNBT(new NBTTagCompound()));
        nbt.setTag("FuelTank2", fuelTank2.writeToNBT(new NBTTagCompound()));
        nbt.setTag("OutTank", outTank.writeToNBT(new NBTTagCompound()));
        nbt.setLong("IgnitionEU", ignitionEU);
        nbt.setBoolean("Ignited", ignited);
        nbt.setInteger("ModuleLife", moduleLifeRemaining);
        nbt.setInteger("CellBurn", (int) cellBurnRemaining);
        nbt.setDouble("CellBurnD", cellBurnRemaining);
        nbt.setDouble("SolidBurnD", solidBurnTicks);
        nbt.setDouble("FuelDebt", fuelDebt);
        nbt.setDouble("FuelDebt2", fuel2Debt);
        nbt.setInteger("Heat", heat);
        nbt.setInteger("Ramp", ramp);
        nbt.setInteger("CreativeTier", creativeTier);
        nbt.setInteger("Status", status.ordinal());
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i] != null) {
                NBTTagCompound s = new NBTTagCompound();
                s.setByte("Slot", (byte) i);
                slots[i].writeToNBT(s);
                list.appendTag(s);
            }
        }
        nbt.setTag("Slots", list);
        nbt.setInteger("Facing", facing.ordinal());
    }

    /** Facing reaches the client with the chunk, so the front renders on the right side. */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
}
