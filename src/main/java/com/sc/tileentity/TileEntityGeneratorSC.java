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
@cpw.mods.fml.common.Optional.Interface(iface = "ic2.api.energy.tile.IMultiEnergySource", modid = "industrialupgrade")
public class TileEntityGeneratorSC extends TileEntityEnergyBase implements ISidedInventory, IFluidHandler,
        ic2.api.energy.tile.IMultiEnergySource {

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
    /** The battery slot under the gauge: the output charges a portable battery. */
    public static final int SLOT_BATTERY = FIRST_UPGRADE_SLOT + UPGRADE_SLOTS;
    public static final int SLOT_COUNT = SLOT_BATTERY + 1;

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
    /** The piece burning now: the ticks it gave, and what it was (item id << 16 | damage) - for the screen. */
    private int solidBurnTotal, solidBurnItem;
    /** Went out overheated: not lit again before the heat is gone (saved - switching off mustn't skip it). */
    private boolean coolingDown;
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
        syncTankCapacity();
        return fuelTank;
    }

    public FluidTank getFuelTank2() {
        syncTankCapacity();
        return fuelTank2;
    }

    public FluidTank getOutTank() {
        syncTankCapacity();
        return outTank;
    }

    /** Every tank's size: 4000 mB + 8000 per Tank Extension upgrade (up to 4). */
    public int tankCapacity() {
        return TANK_CAPACITY + Math.min(UpgradeType.MAX_TANK_UPGRADES, upgradeCount(UpgradeType.TANK_EXTENSION)) * UpgradeType.TANK_PER_UPGRADE;
    }

    /** The tanks follow the upgrades; taking them out pours nothing away (a fuller tank just takes nothing in). */
    private void syncTankCapacity() {
        int c = tankCapacity();
        if (fuelTank.getCapacity() != c) {
            for (FluidTank t : new FluidTank[]{fuelTank, fuelTank2, outTank}) {
                t.setCapacity(c);
            }
        }
    }

    /** 0 fuel, 1 second fuel, 2 the Fuel Cell's water. */
    private FluidTank tankAt(int i) {
        return i == 0 ? fuelTank : i == 1 ? fuelTank2 : outTank;
    }

    /** EU to pour out a tank (1 per 10 mB). */
    public int clearCost(int i) {
        return (tankAt(i).getFluidAmount() + UpgradeType.CLEAR_MB_PER_EU - 1) / UpgradeType.CLEAR_MB_PER_EU;
    }

    /** The screen's Clear button: the tank is emptied for EU from the buffer - only if it can pay it all. */
    public boolean clearTank(int i) {
        FluidTank t = tankAt(i);
        int cost = clearCost(i);
        if (t.getFluidAmount() <= 0 || getEnergyStored() < cost) {
            return false;
        }
        removeEnergy(cost);
        t.setFluid(null);
        markDirty();
        return true;
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

    /** mB flowed into the fuel tank this second so far, and the last second's rate in tenths of a mB a tick. */
    private int inflowWindow, inflowTenths;

    public int getInflowTenths() {
        return inflowTenths;
    }

    /** Fusion: ticks the deuterium cell burning now has left (at one a tick; Overdrive / Economizer change the pace). */
    public double getCellBurnRemaining() {
        return cellBurnRemaining;
    }

    /** Fusion: ticks the Li-blanket module lit into the reactor has left. */
    public int getModuleLife() {
        return moduleLifeRemaining;
    }

    public void setFusionClient(int cellTicks, int moduleLife) {
        cellBurnRemaining = cellTicks;
        moduleLifeRemaining = moduleLife;
    }

    /** Solid fuel: ticks the piece burning now has left (at one a tick), and how many it gave. */
    public double getSolidBurn() {
        return solidBurnTicks;
    }

    public int getSolidBurnTotal() {
        return solidBurnTotal;
    }

    /** The piece burning now as a stack, or null. */
    public ItemStack getSolidBurnStack() {
        Item item = solidBurnItem == 0 ? null : Item.getItemById(solidBurnItem >>> 16);
        return item == null ? null : new ItemStack(item, 1, solidBurnItem & 0xFFFF);
    }

    public int getSolidBurnItem() {
        return solidBurnItem;
    }

    public void setSolidClient(int burn, int total, int item) {
        solidBurnTicks = burn;
        solidBurnTotal = total;
        solidBurnItem = item;
    }

    /** What's on each side for the screen: 3 bits per ForgeDirection ordinal (Thermoelectric: thermoKind()). */
    private int sideInfo;

    public int getSideInfo() {
        return sideInfo;
    }

    public void setSideInfoClient(int v) {
        sideInfo = v;
    }

    public void setInflowClient(int tenths) {
        inflowTenths = tenths;
    }

    public void setLiveClient(int output, int heatValue, int rampValue, int a, int b, int tierOrdinal) {
        lastOutput = output;
        heat = heatValue;
        ramp = rampValue;
        infoA = a;
        infoB = b;
        creativeTier = tierOrdinal;
    }

    /** Self-test: one tick of the battery slot. */
    public int batteryRoundForTest() {
        return chargeBattery(slots[SLOT_BATTERY]);
    }

    // ---- upgrades ----

    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (int i = FIRST_UPGRADE_SLOT; i < FIRST_UPGRADE_SLOT + UPGRADE_SLOTS; i++) {
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
        return (int) Math.round(generatorType.euPerTick * outputMultiplier() * shieldingMultiplier() * bigOutputMultiplier());
    }

    // ---- the Tokamak XV: a tokamak inside its 7x7x3 build ----

    /** This is the Tokamak XV. */
    private boolean xv() {
        return generatorType == GeneratorType.TOKAMAK_XV;
    }

    /**
     * The Tokamak XV: output x0.7 without hydrogen heating; deuterium from a port tank
     * (mB a tick) or cells burnt this many times faster; helium a tick; hydrogen a tick for the
     * heating and at the start; the blanket's wear; argon for a soft stop; the radiation and its
     * reach; a breakdown's burst and its length; how many coils it throws.
     */
    public static final double BIG_D_PER_TICK = 0.5, BIG_HE_PER_TICK = 3, BIG_H2_PER_TICK = 1;
    public static final int BIG_CELL_MUL = 12, BIG_H2_START = 50, BIG_BLANKET_WEAR = 2, BIG_ARGON_STOP = 1000;
    public static final float BIG_NO_H2_OUTPUT = 0.7F;
    public static final float BIG_RADIATION = 12F, BURST_RADIATION = 25F;
    public static final int BIG_RADIUS = 20, BURST_RADIUS = 24, BURST_TICKS = 1200;
    /** Stability a second: helium short, the build broken, an overdrive, too hot; the recovery. */
    public static final float STAB_NO_HE = 5F, STAB_BROKEN = 10F, STAB_OVERDRIVE = 0.5F, STAB_HOT = 2F, STAB_RECOVER = 1F;
    public static final float STAB_ARGON = 10F;
    public static final int PORT_TANKS_MAX = 4, PORT_STORES_MAX = 2;
    /** Liquid helium the XV must have in its port tanks to light (a few seconds of cooling). */
    public static final int BIG_HE_START = 1000;
    /** A wall cell's label for the screen: nothing / the gas of its tank (1-4) / a storage / a tank never filled / another fluid. */
    public static final int LABEL_NONE = 0, LABEL_STORE = 5, LABEL_FREE = 6, LABEL_OTHER = 7;

    /** Per wall cell: the gas its port tank last held - kept while the tank is empty (the gauge stays that gas's). */
    private final String[] portMem = new String[24];
    /** Per gas: the port tanks' total capacity; tanks per gas (3 bits each) and never-filled ones (<< 12); the wall labels (3 bits a cell). */
    private final int[] portCap = new int[4];
    private int portCounts;
    private final int[] portLabels = new int[3];
    /** The port storages: charge % and tier of the first two, the total (per mille). */
    private int storeInfo;
    /** The port storages' charge and room, thousands of EU. */
    private int storeHaveK, storeRoomK;
    /** Cap and floor cells present (7x7, bit (dz + 3) * 7 + dx + 3). */
    private long capTop, capBottom;
    /** Stability a second over the last minute (255 - not lit), a ring. */
    private final byte[] stabHist = new byte[60];
    private int stabHead;

    {
        java.util.Arrays.fill(stabHist, (byte) 255);
    }

    private boolean bigReady, bigRunning, heShort, h2Short;
    /** Not saved: the build scanned since the load; part of the build in an unloaded chunk (the big mode waits). */
    private boolean scannedOnce, bigFrozen;
    /** What ended the last run, kept until the next lighting (the screen): 0 nothing, 1 put out safely, 2 broke down. */
    private int bigEvent;
    public static final int EVENT_SOFT = 1, EVENT_BROKE = 2;

    public int getBigEvent() {
        return bigEvent;
    }
    private float stability = 100F;
    private int burstTicks, warnedAt = 100;
    private double heDebt, h2Debt, dDebt;
    /** The last scan: coils (24 bits), walls fine (24), walls that are ports (24); caps missing, tanks, storages, weak storages. */
    private int coilMask, wallMask, portMask, capMissing, portTanks, portStores, weakStores;
    /** What the port tanks hold, mB: helium, hydrogen, argon, deuterium (the screen's gauges). */
    private final int[] portFluid = new int[4];
    private static final String[] PORT_FLUIDS = {"liquidhelium", "hydrogen", "argon", "deuterium"};
    private final java.util.List<int[]> tankPorts = new java.util.ArrayList<int[]>(), storePorts = new java.util.ArrayList<int[]>();

    public boolean isBigReady() {
        return bigReady;
    }

    public boolean isBigRunning() {
        return bigRunning;
    }

    public float getStability() {
        return stability;
    }

    public int getBurstTicks() {
        return burstTicks;
    }

    public boolean isHeliumShort() {
        return heShort;
    }

    public boolean isHydrogenShort() {
        return h2Short;
    }

    public int getPortFluid(int i) {
        return portFluid[i];
    }

    public int[] bigScan() {
        return new int[]{coilMask, wallMask, portMask, capMissing, portTanks, portStores, weakStores};
    }

    /** The screen's numbers: BIG_SYNC ints - see setBigClient for the layout. */
    public static final int BIG_SYNC = 40;

    public int[] bigSync() {
        int[] v = new int[BIG_SYNC];
        int[] head = bigSyncHead();
        System.arraycopy(head, 0, v, 0, 8);
        for (int i = 0; i < 4; i++) {
            v[8 + i] = portCap[i];
        }
        v[12] = portLabels[0];
        v[13] = portLabels[1];
        v[14] = portLabels[2];
        v[15] = portCounts;
        v[16] = storeInfo;
        for (int i = 0; i < 15; i++) {
            v[17 + i] = (stabHist[i * 4] & 255) | (stabHist[i * 4 + 1] & 255) << 8 | (stabHist[i * 4 + 2] & 255) << 16
                    | (stabHist[i * 4 + 3] & 255) << 24;
        }
        v[32] = stabHead;
        v[33] = (int) (capTop & 0x1FFFFFF);
        v[34] = (int) (capTop >>> 25);
        v[35] = (int) (capBottom & 0x1FFFFFF);
        v[36] = (int) (capBottom >>> 25);
        v[37] = storeHaveK;
        v[38] = storeRoomK;
        v[39] = burstTicks;
        return v;
    }

    private int[] bigSyncHead() {
        int flags = (bigReady ? 1 : 0) | (bigRunning ? 2 : 0) | (heShort ? 8 : 0) | (h2Short ? 16 : 0)
                | bigEvent << 5;
        return new int[]{coilMask | flags << 24, wallMask, portMask | portTanks << 24 | portStores << 27 | weakStores << 29,
                capMissing | Math.round(stability * 10) << 8, portFluid[0], portFluid[1], portFluid[2], portFluid[3]};
    }

    public void setBigClient(int[] v) {
        coilMask = v[0] & 0xFFFFFF;
        int flags = v[0] >>> 24;
        bigReady = (flags & 1) != 0;
        bigRunning = (flags & 2) != 0;
        heShort = (flags & 8) != 0;
        h2Short = (flags & 16) != 0;
        bigEvent = flags >> 5 & 3;
        wallMask = v[1];
        portMask = v[2] & 0xFFFFFF;
        portTanks = v[2] >>> 24 & 7;
        portStores = v[2] >>> 27 & 3;
        weakStores = v[2] >>> 29 & 3;
        capMissing = v[3] & 0xFF;
        stability = (v[3] >>> 8) / 10F;
        for (int i = 0; i < 4; i++) {
            portFluid[i] = v[4 + i];
        }
        if (v.length < BIG_SYNC) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            portCap[i] = v[8 + i];
        }
        portLabels[0] = v[12];
        portLabels[1] = v[13];
        portLabels[2] = v[14];
        portCounts = v[15];
        storeInfo = v[16];
        for (int i = 0; i < 15; i++) {
            for (int k = 0; k < 4; k++) {
                stabHist[i * 4 + k] = (byte) (v[17 + i] >>> (k * 8));
            }
        }
        stabHead = v[32];
        capTop = (v[33] & 0x1FFFFFFL) | (long) v[34] << 25;
        capBottom = (v[35] & 0x1FFFFFFL) | (long) v[36] << 25;
        storeHaveK = v[37];
        storeRoomK = v[38];
        burstTicks = v[39];
    }

    /** The port storages' charge and room, EU. */
    public long getStoresHave() {
        return storeHaveK * 1000L;
    }

    public long getStoresRoom() {
        return storeRoomK * 1000L;
    }

    /** Port tanks' capacity for gas i (0 He, 1 H2, 2 Ar, 3 D) and how many; tanks never filled. */
    public int getPortCap(int i) {
        return portCap[i];
    }

    public int getPortTankCount(int i) {
        return portCounts >> (i * 3) & 7;
    }

    public int getFreeTanks() {
        return portCounts >> 12 & 7;
    }

    /** Wall cell w's label (LABEL_*, 1-4 a gas). */
    public int getPortLabel(int w) {
        return portLabels[w / 8] >> ((w % 8) * 3) & 7;
    }

    /** Port storage i (0, 1): charge % and tier ordinal; the storages' total charge, 0..1. */
    public int getStorePct(int i) {
        return storeInfo >> (i * 11) & 127;
    }

    public int getStoreTier(int i) {
        return storeInfo >> (i * 11 + 7) & 15;
    }

    public float getStoresLevel() {
        return (storeInfo >>> 22) / 1000F;
    }

    /** The cap (top) or the floor: a lead block at (dx, dz), -3..3. */
    public boolean capAt(boolean topLayer, int dx, int dz) {
        return ((topLayer ? capTop : capBottom) >> ((dz + 3) * 7 + dx + 3) & 1) != 0;
    }

    /** Stability k seconds ago (0 - the latest), -1 not lit then. */
    public int stabilityAgo(int k) {
        int v = stabHist[((stabHead - 1 - k) % 60 + 60) % 60] & 255;
        return v == 255 ? -1 : v;
    }

    /** The screen's button: the plasma put out safely. */
    public void softStop() {
        if (!ignited && bigEvent != 0) {
            bigEvent = 0;                                              // the same button, now "Allow lighting"
            markDirty();
            return;
        }
        if (ignited) {
            bigEvent = bigRunning ? EVENT_SOFT : bigEvent;
            shutDown(GeneratorStatus.SOFT_STOP);
        }
    }

    /** The charge a reactor needs to light: the Tokamak XV half with 50 mB of hydrogen in a port tank for the breakdown. */
    public long ignitionNeed() {
        long base = generatorType.ignitionThreshold();
        return xv() && bigReady && portFluid[1] >= BIG_H2_START ? base / 2 : base;
    }

    private double bigOutputMultiplier() {
        return bigRunning && h2Short ? BIG_NO_H2_OUTPUT : 1.0;
    }

    public int radiationRadiusNow() {
        if (xv() && burstTicks > 0) {
            return BURST_RADIUS;
        }
        return radiationRadius(generatorType);
    }

    /** Index of a wall cell (|dx| or |dz| = 3), or -1. */
    private static int wallIndex(int dx, int dz) {
        int k = 0;
        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    if (x == dx && z == dz) {
                        return k;
                    }
                    k++;
                }
            }
        }
        return -1;
    }

    private static boolean isShell(net.minecraft.block.Block b) {
        return b == com.sc.init.ModBlocks.leadBlock || b == com.sc.init.ModBlocks.leadGlass;
    }

    /** Once a second: the 7x7x3 round the tokamak - coils, walls (lead, lead glass, port tanks and storages), cap and floor. */
    private void scanBig() {
        int x0 = xCoord, y0 = yCoord, z0 = zCoord;
        if (!worldObj.checkChunksExist(x0 - 3, y0 - 1, z0 - 3, x0 + 3, y0 + 1, z0 + 3)) {
            bigFrozen = true;                                 // part of it isn't loaded: the big mode waits, no penalty
            return;
        }
        bigFrozen = false;
        int coils = 0, walls = 0, ports = 0, caps = 0, tanks = 0, stores = 0, weak = 0, ci = 0;
        int[] labels = new int[24], caps4 = new int[4], counts = new int[4];
        int free = 0;
        long top = 0, bottomMask = 0;
        tankPorts.clear();
        storePorts.clear();
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 1; dy += 2) {                 // cap and floor
                    if (!isShell(worldObj.getBlock(x0 + dx, y0 + dy, z0 + dz))) {
                        caps++;
                    } else if (dy > 0) {
                        top |= 1L << ((dz + 3) * 7 + dx + 3);
                    } else {
                        bottomMask |= 1L << ((dz + 3) * 7 + dx + 3);
                    }
                }
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = x0 + dx, z = z0 + dz;
                if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
                    if (worldObj.getBlock(x, y0, z) == com.sc.init.ModBlocks.tokamakCoil) {
                        coils |= 1 << ci;
                    }
                    ci++;
                    continue;
                }
                int w = wallIndex(dx, dz);
                net.minecraft.tileentity.TileEntity te = worldObj.getTileEntity(x, y0, z);
                if (isShell(worldObj.getBlock(x, y0, z))) {
                    walls |= 1 << w;
                    portMem[w] = null;
                } else if (te instanceof TileEntityTankSC && tanks < PORT_TANKS_MAX) {
                    walls |= 1 << w;
                    ports |= 1 << w;
                    tanks++;
                    tankPorts.add(new int[]{x, y0, z});
                    FluidTank t = ((TileEntityTankSC) te).getTank();
                    FluidStack f = t.getFluid();
                    if (f != null && f.amount > 0 && f.getFluid() != null) {
                        portMem[w] = f.getFluid().getName();       // remembered: the gauge stays this gas's when it empties
                    }
                    int g = portMem[w] == null ? -1 : java.util.Arrays.asList(PORT_FLUIDS).indexOf(portMem[w]);
                    if (g >= 0) {
                        caps4[g] += t.getCapacity();
                        counts[g]++;
                        labels[w] = g + 1;
                    } else if (portMem[w] == null) {
                        free++;
                        labels[w] = LABEL_FREE;
                    } else {
                        labels[w] = LABEL_OTHER;
                    }
                } else if (te instanceof TileEntityEnergyStorageSC && !(te instanceof TileEntityChargePadSC) && stores < PORT_STORES_MAX) {
                    walls |= 1 << w;
                    ports |= 1 << w;
                    portMem[w] = null;
                    labels[w] = LABEL_STORE;
                    stores++;
                    if (((TileEntityEnergyStorageSC) te).getTier().ordinal() < Tier.IV.ordinal()) {
                        weak++;
                    }
                    storePorts.add(new int[]{x, y0, z});
                } else {
                    portMem[w] = null;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            portCap[i] = caps4[i];
        }
        portCounts = Math.min(7, counts[0]) | Math.min(7, counts[1]) << 3 | Math.min(7, counts[2]) << 6 | Math.min(7, counts[3]) << 9
                | Math.min(7, free) << 12;
        for (int i = 0; i < 3; i++) {
            int v = 0;
            for (int k = 0; k < 8; k++) {
                v |= labels[i * 8 + k] << (k * 3);
            }
            portLabels[i] = v;
        }
        capTop = top;
        capBottom = bottomMask;
        long have = 0, room = 0;
        int info = 0;
        for (int i = 0; i < storePorts.size(); i++) {
            int[] p = storePorts.get(i);
            net.minecraft.tileentity.TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityEnergyStorageSC) {
                TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
                have += s.getEnergyStored();
                room += s.getMaxEnergyStored();
                int pct = (int) Math.min(100, (long) s.getEnergyStored() * 100 / Math.max(1, s.getMaxEnergyStored()));
                info |= (pct | s.getTier().ordinal() << 7) << (i * 11);
            }
        }
        storeInfo = info | (int) (room <= 0 ? 0 : Math.min(1000, have * 1000 / room)) << 22;
        storeHaveK = (int) Math.min(Integer.MAX_VALUE, have / 1000);
        storeRoomK = (int) Math.min(Integer.MAX_VALUE, room / 1000);
        coilMask = coils;
        wallMask = walls;
        portMask = ports;
        capMissing = caps;
        portTanks = tanks;
        portStores = stores;
        weakStores = weak;
        bigReady = coils == 0xFFFFFF && walls == 0xFFFFFF && caps == 0 && stores > 0 && weak == 0;
        for (int i = 0; i < 4; i++) {
            portFluid[i] = portAmount(PORT_FLUIDS[i]);
        }
    }

    private int portAmount(String fluid) {
        int sum = 0;
        for (int[] p : tankPorts) {
            net.minecraft.tileentity.TileEntity te = worldObj.blockExists(p[0], p[1], p[2]) ? worldObj.getTileEntity(p[0], p[1], p[2]) : null;
            if (te instanceof TileEntityTankSC) {
                FluidStack f = ((TileEntityTankSC) te).getTank().getFluid();
                if (f != null && f.getFluid() != null && f.getFluid().getName().equals(fluid)) {
                    sum += f.amount;
                }
            }
        }
        return sum;
    }

    /** Takes `mb` of a fluid out of the port tanks - all of it or (allOrNothing) nothing. @return mB taken */
    private int drainPorts(String fluid, int mb, boolean allOrNothing) {
        if (mb <= 0) {
            return 0;
        }
        if (allOrNothing && portAmount(fluid) < mb) {
            return 0;
        }
        int left = mb;
        for (int[] p : tankPorts) {
            net.minecraft.tileentity.TileEntity te = worldObj.blockExists(p[0], p[1], p[2]) ? worldObj.getTileEntity(p[0], p[1], p[2]) : null;
            if (left > 0 && te instanceof TileEntityTankSC) {
                FluidTank t = ((TileEntityTankSC) te).getTank();
                FluidStack f = t.getFluid();
                if (f != null && f.getFluid() != null && f.getFluid().getName().equals(fluid)) {
                    FluidStack got = t.drain(left, true);
                    if (got != null) {
                        left -= got.amount;
                        te.markDirty();
                    }
                }
            }
        }
        return mb - left;
    }

    /** A tick of the big mode's supplies and, once a second, its stability. @return false: it went out */
    private boolean bigTick() {
        double fm = Math.max(0.01, fuelMultiplier());
        boolean fluidD = portFluid[3] > 0;                     // deuterium in a port tank: that first
        if (fluidD) {
            dDebt += BIG_D_PER_TICK * fm;
            int dmb = (int) dDebt;
            if (dmb > 0) {
                int got = drainPorts("deuterium", dmb, false);
                dDebt -= got;
                if (got < dmb) {
                    dDebt = 0;
                    fluidD = false;
                }
            }
        }
        if (!fluidD) {                                          // otherwise the cells, burnt faster
            if (cellBurnRemaining <= 0) {
                ItemStack cell = slots[SLOT_FUEL];
                if (cell == null || cell.getItem() != com.sc.init.ModItems.deuteriumCell) {
                    heat = Math.max(0, heat - 2);
                    ramp = Math.max(0, ramp - 2);
                    status = GeneratorStatus.NO_DEUTERIUM;
                    return false;
                }
                cell.stackSize--;
                if (cell.stackSize <= 0) {
                    slots[SLOT_FUEL] = null;
                }
                cellBurnRemaining += CELL_BURN_TICKS;
                markDirty();
            }
            cellBurnRemaining -= fm * BIG_CELL_MUL;
        }
        heDebt += BIG_HE_PER_TICK;
        int he = (int) heDebt;
        if (he > 0) {
            int got = drainPorts("liquidhelium", he, false);
            heDebt -= got;
            heShort = got < he;
            if (heShort) {
                heDebt = 0;
            }
        }
        h2Debt += BIG_H2_PER_TICK;
        int h2 = (int) h2Debt;
        if (h2 > 0) {
            int got = drainPorts("hydrogen", h2, false);
            h2Debt -= got;
            h2Short = got < h2;
            if (h2Short) {
                h2Debt = 0;
            }
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            float delta = 0F;
            if (heShort) {
                delta -= STAB_NO_HE;
            }
            if (!bigReady) {
                delta -= STAB_BROKEN;
            }
            delta -= STAB_OVERDRIVE * upgradeCount(UpgradeType.OVERDRIVE);
            if (heat > HEAT_LIMIT * 9 / 10) {
                delta -= STAB_HOT;
            }
            stability = Math.max(0F, Math.min(100F, stability + (delta == 0F ? STAB_RECOVER : delta)));
            warnAt(40, "sc.chat.tok.warn40");
            warnAt(20, "sc.chat.tok.warn20");
            if (stability > 45) {
                warnedAt = 100;
            }
            if (stability < STAB_ARGON && drainPorts("argon", BIG_ARGON_STOP, true) == BIG_ARGON_STOP) {
                tellNear("sc.chat.tok.argon");
                bigEvent = EVENT_SOFT;
                shutDown(GeneratorStatus.SOFT_STOP);
                return false;
            }
            if (stability <= 0F) {
                disrupt();
                return false;
            }
            markDirty();
        }
        return true;
    }

    private void warnAt(int level, String key) {
        if (stability < level && warnedAt > level) {
            warnedAt = level;
            tellNear(key);
        }
    }

    private void tellNear(String key) {
        for (Object o : worldObj.playerEntities) {
            net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) o;
            if (p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 48 * 48) {
                p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(key, xCoord, yCoord, zCoord));
            }
        }
    }

    /** The plasma breaks down: a burst of radiation, 3-5 coils thrown out as items, the reactor out. */
    private void disrupt() {
        tellNear("sc.chat.tok.disrupt");
        burstTicks = BURST_TICKS;
        java.util.List<int[]> coils = new java.util.ArrayList<int[]>();
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if ((dx != 0 || dz != 0) && worldObj.getBlock(xCoord + dx, yCoord, zCoord + dz) == com.sc.init.ModBlocks.tokamakCoil) {
                    coils.add(new int[]{xCoord + dx, yCoord, zCoord + dz});
                }
            }
        }
        java.util.Collections.shuffle(coils, worldObj.rand);
        int n = 3 + worldObj.rand.nextInt(3);
        for (int i = 0; i < n && i < coils.size(); i++) {
            int[] c = coils.get(i);
            worldObj.func_147480_a(c[0], c[1], c[2], true);         // broken, dropped as an item
        }
        worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.explode", 1.5F, 0.6F);
        stability = 100F;
        bigEvent = EVENT_BROKE;
        shutDown(GeneratorStatus.DISRUPTED);
    }

    /** EU a tick the ignition charge draws from the port storages (5 million in about 40 s). */
    public static final int PORT_CHARGE_PER_TICK = 131072;

    /** The ignition charge taken out of the port storages (the tokamak can't be reached by a cable any more). */
    private void chargeFromPorts() {
        long need = ignitionNeed() - ignitionEU;
        for (int[] p : storePorts) {
            if (need <= 0) {
                return;
            }
            if (!worldObj.blockExists(p[0], p[1], p[2])) {
                continue;                                     // unloaded since the last scan: not loaded for this
            }
            net.minecraft.tileentity.TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityEnergyStorageSC && ((TileEntityEnergyStorageSC) te).isPowerOn()) {
                int took = ((TileEntityEnergyStorageSC) te).extractForItemCharging((int) Math.min(need, PORT_CHARGE_PER_TICK));
                if (took > 0) {
                    ignitionEU += took;
                    need -= took;
                    te.markDirty();
                }
            }
        }
    }

    /** The tokamak whose 7x7x3 this block is part of, or null (a click on the wall opens its screen). */
    public static TileEntityGeneratorSC bigTokamakAround(net.minecraft.world.World w, int x, int y, int z) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    net.minecraft.tileentity.TileEntity te = w.getTileEntity(x + dx, y + dy, z + dz);
                    if (te instanceof TileEntityGeneratorSC && ((TileEntityGeneratorSC) te).generatorType == GeneratorType.TOKAMAK_XV) {
                        return (TileEntityGeneratorSC) te;
                    }
                }
            }
        }
        return null;
    }

    /** A click with an empty hand on the build's lead, glass or coils: the tokamak's screen. */
    public static boolean openFromBuild(net.minecraft.world.World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p) {
        if (p.getCurrentEquippedItem() != null || p.isSneaking()) {
            return false;                                     // building: blocks go on the wall as usual
        }
        TileEntityGeneratorSC t = bigTokamakAround(w, x, y, z);
        if (t == null) {
            return false;
        }
        if (!w.isRemote) {
            p.openGui(com.sc.SCMod.instance, com.sc.handler.GuiHandlerSC.GENERATOR_GUI_ID, w, t.xCoord, t.yCoord, t.zCoord);
        }
        return true;
    }

    /** The big mode's output goes into the port storages (the tokamak itself is walled in). */
    private void pushToPorts() {
        for (int[] p : storePorts) {
            if (getEnergyStored() <= 0) {
                return;
            }
            if (!worldObj.blockExists(p[0], p[1], p[2])) {
                continue;                                     // unloaded since the last scan: not loaded for this
            }
            net.minecraft.tileentity.TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityEnergyStorageSC) {
                TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
                int took = s.receiveEnergy(ForgeDirection.UNKNOWN, s.inputTier().getVoltage(), getEnergyStored(), false);
                if (took > 0) {
                    removeEnergy(took);
                    s.markDirty();
                }
            }
        }
    }

    /** Tests: the big mode's state straight. */
    public void setStabilityForTest(float s) {
        stability = s;
    }

    public void setIgnitionForTest() {
        ignitionEU = ignitionNeed();
    }

    // ---- radiation ----

    /** Output with a lead casing in (x0.9), 1 without. */
    public double shieldingMultiplier() {
        return isShielded() ? SHIELDED_OUTPUT : 1.0;
    }

    public static final double SHIELDED_OUTPUT = 0.9;
    /** A lead casing's extra running heat in a fusion / exo reactor. */
    public static final int SHIELDED_HEAT = 150;

    public boolean isShielded() {
        return radiationBase(generatorType) > 0 && upgradeCount(UpgradeType.RAD_SHIELDING) > 0;
    }

    /** Radiation of a type at full work (an RTG: a capsule's worth, 1 each), 0 for the clean ones. */
    public static float radiationBase(GeneratorType type) {
        switch (type) {
            case RTG: return 1F;
            case FUSION_REACTOR: return 4F;
            case TOKAMAK: return 6F;
            case TOKAMAK_XV: return BIG_RADIATION;
            case PLASMA_REACTOR: return 8F;
            case EXO_REACTOR: return 10F;
            default: return 0F;
        }
    }

    /** How far a type's radiation reaches, blocks. */
    public static int radiationRadius(GeneratorType type) {
        switch (type) {
            case RTG: return 4;
            case FUSION_REACTOR: return 8;
            case TOKAMAK: return 12;
            case TOKAMAK_XV: return BIG_RADIUS;
            case PLASMA_REACTOR: return 14;
            case EXO_REACTOR: return 16;
            default: return 0;
        }
    }

    /**
     * What it gives off now: an RTG as long as it holds capsules (they decay, running or not); a
     * reactor while it's lit and working; nothing with a lead casing.
     */
    public float radiationLevel() {
        float base = radiationBase(generatorType);
        if (base <= 0 || isShielded()) {
            return 0F;
        }
        if (xv() && burstTicks > 0) {            // a breakdown's burst, fading
            return Math.max(BIG_RADIATION, BURST_RADIATION * burstTicks / (float) BURST_TICKS);
        }
        if (generatorType == GeneratorType.RTG) {
            int capsules = 0;
            for (int i = SLOT_FUEL; i <= SLOT_BLANKET; i++) {
                capsules += slots[i] != null && slots[i].getItem() == com.sc.init.ModItems.isotopeCapsule ? 1 : 0;
            }
            return base * capsules;
        }
        boolean working = status == GeneratorStatus.GENERATING || status == GeneratorStatus.BUFFER_FULL
                || status == GeneratorStatus.NO_COOLANT;
        if (generatorType.needsIgnition()) {
            working &= ignited;
        }
        return working ? base : 0F;
    }

    /**
     * Overdrive can make more than one packet of the output voltage holds: the generator then sends
     * as many packets a tick as its output needs (48 EU/t at LV = 2 x 32), not one packet and a buffer
     * that fills and stalls it. The voltage stays the same, so nothing on the line blows.
     */
    @Override
    public int packetsPerTick() {
        if (generatorType == GeneratorType.CREATIVE) {
            return 1;                    // one packet of the chosen tier a tick (its screen and lastOutput say so)
        }
        int v = outputTier().getVoltage();
        return Math.max(1, Math.min(64, (ratedOutput() + v - 1) / v));
    }

    /** Industrial Upgrade's energy net: several packets a tick (see packetsPerTick()). */
    @Override
    public boolean sendMultibleEnergyPackets() {
        return packetsPerTick() > 1;
    }

    @Override
    public double getMultibleEnergyPacketAmount() {
        return packetsPerTick();
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
    public int offerableEnergy() {
        return switchedOn() ? super.offerableEnergy() : 0;
    }

    @Override
    public int demandedEnergy() {
        if (isEnergySink() && switchedOn()) {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, ignitionNeed() - ignitionEU));
        }
        return 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (!isEnergySink() || !switchedOn()) {
            return 0; // every other generator, and an already-lit reactor, never accepts energy
        }
        int room = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, ignitionNeed() - ignitionEU));
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
        syncTankCapacity();
        if (xv()) {
            if (burstTicks > 0) {
                burstTicks--;
            }
            if (worldObj.getTotalWorldTime() % 20 == 5) {
                stabHist[stabHead] = (byte) (ignited ? Math.round(stability) : 255);
                stabHead = (stabHead + 1) % 60;
            }
            if (!scannedOnce || worldObj.getTotalWorldTime() % 20 == 3) {
                scannedOnce = true;
                scanBig();
            }
        }
        if (generatorType == GeneratorType.TOKAMAK && (!scannedOnce || worldObj.getTotalWorldTime() % 20 == 0)) {
            scannedOnce = true;
            sideInfo = tokamakMask();
            structureOk = sideInfo == 0xFF;
        }
        if (bigRunning != (xv() && ignited)) {
            bigRunning = xv() && ignited;                      // (a tokamak saved by an older version in its old big mode)
            markDirty();
        }
        if (worldObj.getTotalWorldTime() % 20 == 7) {
            float rad = radiationLevel();
            if (rad > 0) {
                com.sc.radiation.RadiationSC.report(worldObj, xCoord, yCoord, zCoord, rad, radiationRadiusNow());
            }
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {                  // the inflow over the last second
            inflowTenths = inflowWindow / 2;
            inflowWindow = 0;
        }
        if (!switchedOn()) {
            status = powerOn ? GeneratorStatus.REDSTONE : GeneratorStatus.DISABLED;
            lastOutput = 0;
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
        if (xv() && !storePorts.isEmpty() && !bigFrozen) {
            pushToPorts();                                   // after the count: the screen shows what it made
        }
        if (status == GeneratorStatus.GENERATING) {
            com.sc.util.SoundsSC.loop(this, com.sc.util.SoundsSC.of(generatorType));
        }
        if (chargeBattery(slots[SLOT_BATTERY]) > 0) {
            markDirty();
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
        // The screen shows day / night and rain from these (infoA: 1 by day, infoB: 1 in the rain) -
        // the client's own world can't tell: its isDaytime() is always true (skylight isn't
        // computed client-side in 1.7.10), which made the screen say "Day" all night.
        boolean day = worldObj.isDaytime(), rain = worldObj.canLightningStrikeAt(xCoord, yCoord + 1, zCoord);
        infoA = day ? 1 : 0;
        infoB = rain ? 1 : 0;
        if (!worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord)) {
            status = GeneratorStatus.NO_SUNLIGHT;
            return;
        }
        double f = day ? 1.0 : 0.5;
        if (rain) {
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
                inflowWindow += 1000;           // counts toward the screen's supply bar like piped-in lava
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
            solidBurnTotal = (int) Math.ceil(solidBurnTicks);
            solidBurnItem = Item.getIdFromItem(fuel.getItem()) << 16 | (fuel.getItemDamage() & 0xFFFF);
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

    /**
     * 4 EU/t for each side (north, south, east, west) with flowing water against it. infoB tells the
     * screen each side, 2 bits apiece in that order: 0 no water, 1 flowing, 2 standing (a source).
     */
    private void updateWater() {
        int flowing = 0, sides = 0, i = 0;
        for (ForgeDirection d : new ForgeDirection[]{ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST}) {
            int x = xCoord + d.offsetX, z = zCoord + d.offsetZ;
            Block b = worldObj.getBlock(x, yCoord, z);
            int state = 0;
            if (b.getMaterial() == Material.water) {
                state = b == Blocks.flowing_water || worldObj.getBlockMetadata(x, yCoord, z) != 0 ? 1 : 2;
            }
            if (state == 1) {
                flowing++;
            }
            sides |= state << (i++ * 2);
        }
        infoA = flowing;
        infoB = sides;
        give(flowing * 4, GeneratorStatus.NO_WATER);
    }

    /**
     * Thermoelectric: each hot side (lava, fire) paired with a cold one (water, ice, snow) is a
     * Peltier pair; output = pairs x (mean hot - mean cold) / 8, at most the rated 384 EU/t.
     */
    private void updateThermo() {
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            int hot = 0, cold = 0, hotSum = 0, coldSum = 0, sides = 0;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int t = temperatureAt(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
                sides |= thermoKind(t) << (d.ordinal() * 3);
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
            sideInfo = sides;
            cachedOutput = Math.min(generatorType.euPerTick, pairs * dT / 8);
        }
        give(cachedOutput, GeneratorStatus.NO_HEAT);
    }

    /** For the screen: 0 nothing, 1 lava, 2 fire, 3 water, 4 ice / snow, 5 packed ice - from its Kelvin. */
    public static int thermoKind(int kelvin) {
        switch (kelvin) {
            case 1300: return 1;
            case 1000: return 2;
            case 300: return 3;
            case 270: return 4;
            case 250: return 5;
            default: return 0;
        }
    }

    /** Kelvin of each thermoKind(). */
    public static final int[] THERMO_KELVIN = {0, 1300, 1000, 300, 270, 250};

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
        give((int) Math.round(capsules * CAPSULE_EU * shieldingMultiplier()), GeneratorStatus.NO_CAPSULE);
    }

    // ---- reactors ----

    /** Lights a FUSION / EXO generator once its charge is in (and, for FUSION, a blanket module is there). */
    private boolean ignite() {
        if (xv() && bigEvent != 0) {
            return false;               // put out / broken down: lit again only once the player allows it (the screen's button)
        }
        if (xv() && portFluid[0] < BIG_HE_START) {
            status = GeneratorStatus.NO_COOLANT;                  // the coils can't be cooled: not lit
            return false;
        }
        if (xv() && portFluid[3] <= 0 && (slots[SLOT_FUEL] == null || slots[SLOT_FUEL].getItem() != com.sc.init.ModItems.deuteriumCell)) {
            status = GeneratorStatus.NO_DEUTERIUM;
            return false;
        }
        if (ignitionEU < ignitionNeed()) {
            status = GeneratorStatus.IGNITING;
            return false;
        }
        if (xv() && ignitionNeed() < generatorType.ignitionThreshold()
                && portAmount("hydrogen") < BIG_H2_START) {
            status = GeneratorStatus.IGNITING;                          // the hydrogen went: the full charge then
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
        long ignitionNeedAtLighting = ignitionNeed();       // before the hydrogen that halved it is used up
        if (xv()) {                                     // lit inside its build (never without it)
            if (ignitionNeed() < generatorType.ignitionThreshold()) {
                drainPorts("hydrogen", BIG_H2_START, true);                     // the hydrogen breakdown that halved the charge
            }
            bigRunning = true;
            stability = 100F;
            warnedAt = 100;
            bigEvent = 0;
        }
        ignitionEU = Math.max(0L, ignitionEU - ignitionNeedAtLighting);
        ignited = true;
        ramp = RAMP_FULL / 10;
        status = GeneratorStatus.GENERATING;
        markDirty();
        refreshEnergyNet();      // sink -> source: IC2 caches that on load
        return true;
    }

    /** At the heat limit the reactor goes out - no blast; it has to be lit again. */
    private void shutDown(GeneratorStatus why) {
        coolingDown |= why == GeneratorStatus.OVERHEATED;
        bigRunning = false;
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
        return base + 100 * upgradeCount(UpgradeType.OVERDRIVE) + (isShielded() ? SHIELDED_HEAT : 0);
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
        if (!ignited) {
            heat = Math.max(0, heat - 5);
            if (xv() && bigFrozen) {
                return;                                        // part of the build unloaded: wait
            }
            if (generatorType == GeneratorType.TOKAMAK && !structureOk || xv() && !bigReady) {
                status = GeneratorStatus.NO_STRUCTURE;          // the XV: the screen says what's missing
                return;
            }
            if (coolingDown && heat > 0) {
                return;                 // cools down before it can be lit again (switching off and on doesn't skip it)
            }
            coolingDown = false;
            if (xv() && bigEvent == 0) {
                chargeFromPorts();      // walled in: the charge comes out of the port storages
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
        if (bigRunning) {
            if (bigFrozen) {
                return;                                        // part of the build unloaded: held as it is
            }
            if (!bigTick()) {                                  // helium, hydrogen, stability - full buffer or not
                return;
            }
            moduleLifeRemaining -= BIG_BLANKET_WEAR - 1;           // the big mode wears the blanket twice as fast
            if (moduleLifeRemaining <= 0) {
                shutDown(GeneratorStatus.BLANKET_DEPLETED);
                return;
            }
            moduleLifeRemaining--;
            if (!overheating()) {
                burnPlasma(600);
            }
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

    /**
     * The Tokamak's ring for the screen: bit k set where the k-th of the eight places round it (row by
     * row from the north-west: NW, N, NE, W, E, SW, S, SE) holds a Tokamak Coil.
     */
    public int tokamakMask() {
        if (worldObj == null) {
            return 0;
        }
        int mask = 0, k = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (worldObj.getBlock(xCoord + dx, yCoord, zCoord + dz) == com.sc.init.ModBlocks.tokamakCoil) {
                    mask |= 1 << k;
                }
                k++;
            }
        }
        return mask;
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
            if (coolingDown && heat > 0) {
                return;
            }
            coolingDown = false;
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
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= (xv() ? 144 : 64);
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
            case TOKAMAK_XV:
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
        if (slot == SLOT_BATTERY) {
            return com.sc.item.BatteryFeedSC.accepts(stack);
        }
        if (slot >= FIRST_UPGRADE_SLOT) {
            return hasUpgradeSlots(generatorType) && stack.getItem() instanceof com.sc.item.ItemUpgradeSC
                    && com.sc.item.ItemUpgradeSC.typeOf(stack).forGenerators()
                    && (com.sc.item.ItemUpgradeSC.typeOf(stack) != UpgradeType.RAD_SHIELDING || radiationBase(generatorType) > 0);
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
            default:                    // FUSION_REACTOR, TOKAMAK, TOKAMAK_XV
                return slot == SLOT_BLANKET ? item == com.sc.init.ModItems.component("liBlanketModule")
                        : item == com.sc.init.ModItems.deuteriumCell;
        }
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        boolean a = usesSlot(generatorType, SLOT_FUEL), b = usesSlot(generatorType, SLOT_BLANKET);
        return a && b ? new int[]{SLOT_FUEL, SLOT_BLANKET, SLOT_BATTERY} : a ? new int[]{SLOT_FUEL, SLOT_BATTERY} : new int[]{SLOT_BATTERY};
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        if (slot == SLOT_BATTERY) {
            return slots[SLOT_BATTERY] == null && com.sc.item.BatteryFeedSC.accepts(stack);            // an empty one in
        }
        return slot < FIRST_UPGRADE_SLOT && isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        if (slot == SLOT_BATTERY) {                                  // a full one out
            return stack != null && com.sc.item.BatteryFeedSC.chargeOf(stack) >= com.sc.item.BatteryFeedSC.capacityOf(stack);
        }
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
            int n = fuelTank.fill(resource, doFill);
            if (doFill) {
                inflowWindow += n;
            }
            return n;
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
            if (heat > 0) {
                nbt.setInteger("Heat", heat);
            }
            if (coolingDown) {
                nbt.setBoolean("CoolingDown", true);
            }
            if (xv()) {
                if (stability < 100F) {
                    nbt.setFloat("Stability", stability);
                }
                if (bigEvent != 0) {
                    nbt.setInteger("BigEvent", bigEvent);
                }
                if (burstTicks > 0) {
                    nbt.setInteger("Burst", burstTicks);
                }
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
            heat = Math.max(0, Math.min(HEAT_LIMIT, nbt.getInteger("Heat")));
            coolingDown = nbt.getBoolean("CoolingDown");
            if (xv()) {
                stability = nbt.hasKey("Stability") ? Math.max(0F, Math.min(100F, nbt.getFloat("Stability"))) : 100F;
                bigEvent = nbt.getInteger("BigEvent");
                burstTicks = Math.max(0, Math.min(BURST_TICKS, nbt.getInteger("Burst")));
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
        setTier(generatorType.tier);         // follows the type (as in setGeneratorType), not a missing "TierSC" (LV)
        fuelTank.readFromNBT(nbt.getCompoundTag("FuelTank"));
        fuelTank2.readFromNBT(nbt.getCompoundTag("FuelTank2"));
        outTank.readFromNBT(nbt.getCompoundTag("OutTank"));
        ignitionEU = nbt.getLong("IgnitionEU");
        ignited = nbt.getBoolean("Ignited");
        bigRunning = nbt.getBoolean("BigRunning");
        stability = nbt.hasKey("Stability") ? nbt.getFloat("Stability") : 100F;
        burstTicks = nbt.getInteger("Burst");
        bigEvent = nbt.getInteger("BigEvent");
        NBTTagCompound mem = nbt.getCompoundTag("PortMem");
        for (int i = 0; i < portMem.length; i++) {
            portMem[i] = mem.hasKey("w" + i) ? mem.getString("w" + i) : null;
        }
        moduleLifeRemaining = nbt.getInteger("ModuleLife");
        cellBurnRemaining = nbt.hasKey("CellBurnD") ? nbt.getDouble("CellBurnD") : nbt.getInteger("CellBurn");
        solidBurnTicks = nbt.hasKey("SolidBurnD") ? nbt.getDouble("SolidBurnD") : nbt.getInteger("SolidBurn");
        fuelDebt = nbt.getDouble("FuelDebt");
        fuel2Debt = nbt.getDouble("FuelDebt2");
        heat = nbt.getInteger("Heat");
        solidBurnTotal = nbt.getInteger("SolidBurnTotal");
        solidBurnItem = nbt.getInteger("SolidBurnItem");
        ramp = nbt.hasKey("Ramp") ? nbt.getInteger("Ramp") : ignited ? RAMP_FULL : 0;
        creativeTier = nbt.hasKey("CreativeTier") ? nbt.getInteger("CreativeTier") : Tier.values().length - 1;
        status = GeneratorStatus.byOrdinal(nbt.getInteger("Status"));
        coolingDown = nbt.hasKey("CoolingDown") ? nbt.getBoolean("CoolingDown") : status == GeneratorStatus.OVERHEATED;
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
        nbt.setBoolean("BigRunning", bigRunning);
        nbt.setFloat("Stability", stability);
        nbt.setInteger("Burst", burstTicks);
        nbt.setInteger("BigEvent", bigEvent);
        NBTTagCompound mem = new NBTTagCompound();
        for (int i = 0; i < portMem.length; i++) {
            if (portMem[i] != null) {
                mem.setString("w" + i, portMem[i]);
            }
        }
        nbt.setTag("PortMem", mem);
        nbt.setInteger("ModuleLife", moduleLifeRemaining);
        nbt.setInteger("CellBurn", (int) cellBurnRemaining);
        nbt.setDouble("CellBurnD", cellBurnRemaining);
        nbt.setDouble("SolidBurnD", solidBurnTicks);
        nbt.setDouble("FuelDebt", fuelDebt);
        nbt.setDouble("FuelDebt2", fuel2Debt);
        nbt.setInteger("Heat", heat);
        nbt.setBoolean("CoolingDown", coolingDown);
        nbt.setInteger("SolidBurnTotal", solidBurnTotal);
        nbt.setInteger("SolidBurnItem", solidBurnItem);
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
