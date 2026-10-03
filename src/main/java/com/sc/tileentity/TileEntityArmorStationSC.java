package com.sc.tileentity;

import java.util.List;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemArmorSC;
import com.sc.machine.UpgradeType;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * The Armour Service Station (MV): four slots for the energy suit's pieces (helmet, chestplate,
 * leggings, boots) and an MV buffer. Every EVERY ticks it pulls the gases the pieces lack
 * (ArmorGasSC) out of the fluid containers beside it - the mod's tanks, pipes and conduit bundles,
 * other mods' tanks, any side - at GAS_PER_TICK mB a tick per gas, and charges the pieces like a
 * charge pad of its tier. A player standing on top gets the worn suit filled and charged the same
 * way. Pipes may also push gases straight in (fill). Gases cost energy (pumping): 1 EU per
 * MB_PER_EU mB. Per-gas switches and a master switch on the screen; power switch and redstone
 * like every machine.
 * Four module slots (UpgradeType, as a machine's): Overclocker (gas and charging x1/0.7 each, up to
 * MAX_OVERCLOCKERS; pumping EU x1.6 a tick, as a machine's EU/t), Transformer (input one tier up
 * each - and the charge rate follows the input voltage), Universal Transformer (any voltage),
 * Energy Storage (+10 000 EU of buffer), Tank Extension (+8 000 mB to every tank, up to 4). Nothing
 * else goes in. The modules ride in the item.
 * Eight inner tanks, one per gas - singular matter the 8th (TANK_CAPACITY each): pipes push into them (fill), the station
 * pulls into them from the containers beside it, and the armour is filled out of them. A tank
 * takes only its own gas; pipes can't drain the station. The screen's x pours a tank out for EU
 * (as a machine's). The tanks ride in the item; the sides of the block show their levels.
 */
public class TileEntityArmorStationSC extends TileEntityEnergyBase implements ISidedInventory, IFluidHandler {

    /** Ticks between two rounds. */
    public static final int EVERY = 5;
    /** mB of one gas a tick (a round moves EVERY x this of each gas). */
    public static final int GAS_PER_TICK = 100;
    /** Pumping: 1 EU per this many mB. */
    public static final int MB_PER_EU = 20;
    public static final int SLOTS = 4;
    /** Module slots after the armour slots. */
    public static final int UPGRADE_SLOTS = 4, FIRST_UPGRADE_SLOT = SLOTS, ALL_SLOTS = SLOTS + UPGRADE_SLOTS;
    /** Overclockers stop adding up past this many. */
    public static final int MAX_OVERCLOCKERS = 4;
    /** NBT key the station's item carries its modules under. */
    public static final String ITEM_UPGRADES_KEY = "UpgradesSC";
    public static final int ST_IDLE = 0, ST_WORKING = 1, ST_FULL = 2, ST_NO_GAS = 3, ST_NO_ENERGY = 4, ST_OFF = 5;
    /** All gases switched on. */
    public static final int ALL_GASES = (1 << Gas.values().length) - 1;
    private static final int GASES = Gas.values().length;
    /** NBT: how many gases there were when the mask was saved (none saved: the first 7). */
    private static final String MASK_GASES_KEY = "GasMaskN";
    private static final int OLD_GASES = 7;

    /**
     * Pure: a gas mask saved when there were `known` gases - the gases added since then come
     * switched on (an old station fills singular matter too), the rest as saved.
     */
    public static int migrateMask(int saved, int known) {
        int k = Math.max(0, Math.min(GASES, known));
        int added = ALL_GASES & ~((1 << k) - 1);
        return (saved & ALL_GASES) | added;
    }
    /** Each gas's inner tank, mB (+ UpgradeType.TANK_PER_UPGRADE per Tank Extension, up to MAX_TANK_UPGRADES). */
    public static final int TANK_CAPACITY = 16000;
    public static final int MAX_TANK_CAPACITY = TANK_CAPACITY + UpgradeType.MAX_TANK_UPGRADES * UpgradeType.TANK_PER_UPGRADE;
    /** NBT key of the tanks (gas key -> mB): in the world save, and in the station's item. */
    public static final String TANKS_KEY = "StationTanks", ITEM_TANKS_KEY = "StationTanksSC";
    /** The block's side windows: levels 0..WINDOW_LEVELS, sent to clients at most every WINDOW_EVERY ticks, only on change. */
    public static final int WINDOW_LEVELS = 15, WINDOW_EVERY = 20;

    /** Slot i holds the piece of ItemArmor.armorType i (0 helmet .. 3 boots); FIRST_UPGRADE_SLOT.. the modules. */
    private final ItemStack[] slots = new ItemStack[ALL_SLOTS];
    private ForgeDirection facing = ForgeDirection.NORTH;
    private boolean fillGases = true;
    private int gasMask = ALL_GASES;
    private int status, players;
    /** Lit top (clients): something was filled or charged in the last round. */
    private boolean active;
    /** Screen: per gas, in the slots and on the players on top: [gas] amount / capacity (client copy too). */
    private final int[] shownAmount = new int[Gas.values().length], shownCap = new int[Gas.values().length];
    /** The inner tanks, by Gas.ordinal(). */
    private final FluidTank[] tanks = new FluidTank[GASES];
    /** Clients: the window levels last received; the server: the levels last sent (null - nothing sent yet). */
    private byte[] windowLevels = new byte[GASES], sentLevels;

    public TileEntityArmorStationSC() {
        super(Tier.MV);
        for (int i = 0; i < GASES; i++) {
            tanks[i] = new FluidTank(TANK_CAPACITY);
        }
    }

    // ------------------------------------------------------------------ state

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection f) {
        if (f != null && f.offsetY == 0 && f != ForgeDirection.UNKNOWN) {
            facing = f;
            markDirty();
        }
    }

    public boolean isFillGases() {
        return fillGases;
    }

    public void toggleFillGases() {
        fillGases = !fillGases;
        markDirty();
    }

    public boolean gasEnabled(Gas g) {
        return (gasMask & (1 << g.ordinal())) != 0;
    }

    public void toggleGas(Gas g) {
        gasMask ^= 1 << g.ordinal();
        markDirty();
    }

    /** "Helium only": only helium on; pressed again - every gas back on. */
    public void toggleHeliumOnly() {
        int helium = 1 << Gas.HELIUM.ordinal();
        gasMask = gasMask == helium ? ALL_GASES : helium;
        markDirty();
    }

    public int getGasMask() {
        return gasMask;
    }

    public int getStatus() {
        return status;
    }

    public int getPlayers() {
        return players;
    }

    public boolean isActive() {
        return active;
    }

    public int shownAmount(Gas g) {
        return shownAmount[g.ordinal()];
    }

    public int shownCapacity(Gas g) {
        return shownCap[g.ordinal()];
    }

    /** Settings flags for the container's sync: bit 0 fillGases, bits 1.. the gas mask. */
    public int settingsFlags() {
        return (fillGases ? 1 : 0) | gasMask << 1;
    }

    public void setScreenClient(int st, int n, int settings) {
        status = st;
        players = n;
        fillGases = (settings & 1) != 0;
        gasMask = (settings >> 1) & ALL_GASES;
    }

    public void setShownClient(int gas, int amount, int cap) {
        shownAmount[gas] = amount;
        shownCap[gas] = cap;
    }

    // ------------------------------------------------------------------ modules

    /** What the module slots take: Overclocker, Transformer, Universal Transformer, Energy Storage, Tank Extension. */
    public static boolean acceptsModule(ItemStack s) {
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemUpgradeSC)) {
            return false;
        }
        UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
        return t == UpgradeType.OVERCLOCKER || t == UpgradeType.TRANSFORMER || t == UpgradeType.UNIVERSAL_TRANSFORMER
                || t == UpgradeType.ENERGY_STORAGE || t == UpgradeType.TANK_EXTENSION;
    }

    /** Modules of a kind in the slots (Overclockers count up to MAX_OVERCLOCKERS, the rest up to UpgradeType.MAX_EFFECTIVE). */
    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (int i = FIRST_UPGRADE_SLOT; i < ALL_SLOTS; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return Math.min(n, type == UpgradeType.OVERCLOCKER ? MAX_OVERCLOCKERS : UpgradeType.MAX_EFFECTIVE);
    }

    /** Filled module slots (the screen's "N of 4"). */
    public int modulesUsed() {
        int n = 0;
        for (int i = FIRST_UPGRADE_SLOT; i < ALL_SLOTS; i++) {
            n += slots[i] != null ? 1 : 0;
        }
        return n;
    }

    /** Speed with overclockers, as a machine's: 1 / 0.7 per overclocker. */
    public double speedFactor() {
        return 1.0 / Math.pow(0.7, upgradeCount(UpgradeType.OVERCLOCKER));
    }

    /** EU a tick with overclockers, as a machine's: x1.6 per overclocker (pumping a mB: x1.6 x 0.7 = x1.12 each). */
    public double energyFactor() {
        return Math.pow(1.6, upgradeCount(UpgradeType.OVERCLOCKER));
    }

    /** mB of one gas a tick: GAS_PER_TICK, faster with overclockers (100, 143, 204, 292, 416). */
    public int gasPerTick() {
        return (int) Math.round(GAS_PER_TICK * speedFactor());
    }

    /** EU one round may charge the armour with: the input voltage x EVERY, faster with overclockers. */
    public int chargePerRound() {
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.round((double) inputTier().getVoltage() * EVERY * speedFactor()));
    }

    /** EU the pumps take for `mb` mB: 1 per MB_PER_EU, x1.12 per overclocker (rounded up). */
    public int gasCost(int mb) {
        if (mb <= 0) {
            return 0;
        }
        if (upgradeCount(UpgradeType.OVERCLOCKER) == 0) {
            return (mb + MB_PER_EU - 1) / MB_PER_EU;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(mb * energyFactor() / speedFactor() / MB_PER_EU - 1e-9));
    }

    /** mB the pumps can move for `eu` EU (gasCost's inverse, rounded down). */
    public int affordableGas(int eu) {
        if (eu <= 0) {
            return 0;
        }
        if (upgradeCount(UpgradeType.OVERCLOCKER) == 0) {
            return (int) Math.min(Integer.MAX_VALUE, (long) eu * MB_PER_EU);
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(eu * (double) MB_PER_EU * speedFactor() / energyFactor() + 1e-9));
    }

    /** Each Transformer takes one tier higher voltage (not above the top tier); a Universal Transformer takes any. */
    @Override
    public boolean acceptsAnyVoltage() {
        return upgradeCount(UpgradeType.UNIVERSAL_TRANSFORMER) > 0;
    }

    @Override
    public Tier inputTier() {
        if (acceptsAnyVoltage()) {
            return Tier.max();
        }
        return Tier.byOrdinal(getTier().ordinal() + upgradeCount(UpgradeType.TRANSFORMER));
    }

    /** The MV buffer + 10 000 EU per Energy Storage module. */
    @Override
    public int getMaxEnergyStored() {
        return super.getMaxEnergyStored() + upgradeCount(UpgradeType.ENERGY_STORAGE) * UpgradeType.STORAGE_PER_UPGRADE;
    }

    /** The input tier IC2 / the mod's net saw last (-1: any voltage); a change re-announces the tile. */
    private int lastSinkKey = Tier.MV.ordinal();

    private int sinkKey() {
        return acceptsAnyVoltage() ? -1 : inputTier().ordinal();
    }

    /** The modules changed: IC2 caches a sink's tier - re-announce the station when it moved. */
    private void modulesChanged() {
        int key = sinkKey();
        if (key != lastSinkKey) {
            lastSinkKey = key;
            if (worldObj != null && !worldObj.isRemote) {
                refreshEnergyNet();
            }
        }
    }

    // ------------------------------------------------------------------ the inner tanks

    /** Every tank's size: TANK_CAPACITY + 8000 mB per Tank Extension (up to 4), as a machine's. */
    public int tankCapacity() {
        return TANK_CAPACITY + Math.min(UpgradeType.MAX_TANK_UPGRADES, upgradeCount(UpgradeType.TANK_EXTENSION)) * UpgradeType.TANK_PER_UPGRADE;
    }

    /**
     * The tanks follow the modules. Taking a Tank Extension out pours nothing away: a tank then
     * holding more than it can takes nothing in until it has been used down (as a machine's).
     */
    private void syncTankCapacity() {
        int c = tankCapacity();
        if (tanks[0].getCapacity() != c) {
            for (FluidTank t : tanks) {
                t.setCapacity(c);
            }
        }
    }

    public FluidTank getTank(Gas g) {
        syncTankCapacity();
        return tanks[g.ordinal()];
    }

    public int tankAmount(Gas g) {
        return tanks[g.ordinal()].getFluidAmount();
    }

    /** Puts up to `mb` of `g` into its own tank (nothing past the capacity). @return mB that went (or would go) in */
    public int fillTank(Gas g, int mb, boolean doFill) {
        Fluid f = g == null ? null : g.fluidOf();
        if (f == null || mb <= 0) {
            return 0;
        }
        int n = TileEntityMachineSC.safeFill(getTank(g), new FluidStack(f, mb), doFill);
        if (doFill && n > 0) {
            markDirty();
        }
        return n;
    }

    /** EU to pour a tank out: 1 per UpgradeType.CLEAR_MB_PER_EU mB (as a machine's). */
    public int clearCost(Gas g) {
        return (tankAmount(g) + UpgradeType.CLEAR_MB_PER_EU - 1) / UpgradeType.CLEAR_MB_PER_EU;
    }

    /** The screen's x: the tank is emptied for EU from the buffer - only if it can pay it all. */
    public boolean clearTank(Gas g) {
        int cost = clearCost(g);
        if (tankAmount(g) <= 0 || getEnergyStored() < cost) {
            return false;
        }
        removeEnergy(cost);
        tanks[g.ordinal()].setFluid(null);
        markDirty();
        return true;
    }

    /** Clients (the screen's sync): a tank's contents - the gas is the tank's own. */
    public void setTankClient(int gas, int amount) {
        Fluid f = Gas.values()[gas].fluidOf();
        tanks[gas].setFluid(amount > 0 && f != null ? new FluidStack(f, amount) : null);
    }

    /** The windows' levels now: 0 empty, 1..WINDOW_LEVELS (any gas at all shows at least 1). */
    public byte[] currentWindowLevels() {
        byte[] out = new byte[GASES];
        int cap = Math.max(1, tankCapacity());
        for (Gas g : Gas.values()) {
            int a = tankAmount(g);
            out[g.ordinal()] = (byte) (a <= 0 ? 0 : Math.max(1, Math.min(WINDOW_LEVELS, (int) (((long) a * WINDOW_LEVELS + cap - 1) / cap))));
        }
        return out;
    }

    /** Clients: a window's level (0..WINDOW_LEVELS), as last sent. */
    public int windowLevel(Gas g) {
        return windowLevels[g.ordinal()];
    }

    /** Server, every WINDOW_EVERY ticks: the window levels go to the clients only when they changed. */
    private void refreshWindows() {
        byte[] now = currentWindowLevels();
        if (sentLevels == null || !java.util.Arrays.equals(now, sentLevels)) {
            sentLevels = now;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** The tanks as NBT (gas key -> mB, only the gases there are). */
    private NBTTagCompound tanksTag() {
        NBTTagCompound t = new NBTTagCompound();
        for (Gas g : Gas.values()) {
            if (tankAmount(g) > 0) {
                t.setInteger(g.key(), tankAmount(g));
            }
        }
        return t;
    }

    /** The tank amounts saved in `tag` (by Gas.ordinal(), 0 where none) - also for the item's tooltip. */
    public static int[] tankAmountsOf(NBTTagCompound tag) {
        int[] out = new int[GASES];
        for (Gas g : Gas.values()) {
            if (tag != null && tag.hasKey(g.key())) {
                out[g.ordinal()] = Math.max(0, Math.min(MAX_TANK_CAPACITY, tag.getInteger(g.key())));
            }
        }
        return out;
    }

    /** Sets the tanks to what `tag` holds (an old station without the tag: empty tanks). */
    private void loadTanks(NBTTagCompound tag) {
        int[] a = tankAmountsOf(tag);
        for (Gas g : Gas.values()) {
            Fluid f = g.fluidOf();
            tanks[g.ordinal()].setFluid(a[g.ordinal()] > 0 && f != null ? new FluidStack(f, a[g.ordinal()]) : null);
        }
    }

    // ------------------------------------------------------------------ the work

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        modulesChanged();                                  // also after a hopper / another mod took a module out
        syncTankCapacity();
        if (worldObj.getTotalWorldTime() % WINDOW_EVERY == 0) {
            refreshWindows();
        }
        if (worldObj.getTotalWorldTime() % EVERY != 0) {
            return;
        }
        List<EntityPlayer> on = standing();
        players = on.size();
        if (!switchedOn()) {
            status = ST_OFF;
            setActive(false);
            refreshShown(on);
            return;
        }
        boolean did = false, needGas = false, gotGas = false, starved = false;
        boolean pulled = false;
        if (fillGases) {
            for (Gas g : Gas.values()) {
                if (!gasEnabled(g)) {
                    continue;                              // switched off: neither pulled in nor filled
                }
                pulled |= pullIntoTank(g, gasPerTick() * EVERY) > 0;
                int need = need(g, on);
                if (need <= 0) {
                    continue;
                }
                needGas = true;
                if (tankAmount(g) <= 0) {
                    continue;
                }
                if (affordableGas(getEnergyStored()) <= 0) {
                    starved = true;
                    continue;
                }
                if (fillFromTank(g, gasPerTick() * EVERY, on) > 0) {
                    gotGas = true;
                    did = true;
                }
            }
        }
        // energy, after the gases (their pumps cost little; a flat suit must not starve them): one budget
        // of the input voltage x EVERY (overclockers: more) for the slots first, then the players on top
        int budget = Math.min(getEnergyStored(), chargePerRound()), start = budget;
        for (int i = 0; i < SLOTS && budget > 0; i++) {
            budget -= chargePiece(slots[i], budget);
        }
        for (EntityPlayer p : on) {
            for (int t = 0; t < 4 && budget > 0; t++) {
                budget -= chargePiece(ArmorGasSC.worn(p, t), budget);
            }
        }
        boolean needEu = anyNeedsCharge(on);
        if (start - budget > 0) {
            removeEnergy(start - budget);
            did = true;
        }
        if (did || pulled) {
            markDirty();
        }
        status = did ? ST_WORKING : starved || (needEu && getEnergyStored() <= 0) ? ST_NO_ENERGY
                : needGas && !gotGas ? ST_NO_GAS : hasAnyArmor(on) ? ST_FULL : ST_IDLE;
        setActive(did);
        refreshShown(on);
    }

    /** Players standing on the station (feet in the block above it). */
    @SuppressWarnings("unchecked")
    public List<EntityPlayer> standing() {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord, yCoord + 1, zCoord, xCoord + 1, yCoord + 1.5, zCoord + 1);
        return worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
    }

    private void setActive(boolean on) {
        if (on != active) {
            active = on;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);       // the lit / dark top
        }
    }

    /** Charges one piece out of `max` EU (any of the mod's suits; the rate is the round's budget, chargePerRound). */
    public int chargePiece(ItemStack s, int max) {
        if (s == null || max <= 0 || !(s.getItem() instanceof ItemArmorSC) || !tierAllows(s)) {
            return 0;
        }
        return ItemArmorSC.charge(s, max);
    }

    /** Any of the mod's suits: a service station charges Nano, Quantum and Exo alike, at its input voltage's rate. */
    public boolean tierAllows(ItemStack s) {
        return s != null && s.getItem() instanceof ItemArmorSC;
    }

    private boolean anyNeedsCharge(List<EntityPlayer> on) {
        for (ItemStack s : slots) {
            if (tierAllows(s) && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s)) {
                return true;
            }
        }
        for (EntityPlayer p : on) {
            for (int t = 0; t < 4; t++) {
                ItemStack s = ArmorGasSC.worn(p, t);
                if (tierAllows(s) && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnyArmor(List<EntityPlayer> on) {
        for (ItemStack s : slots) {
            if (s != null) {
                return true;
            }
        }
        for (EntityPlayer p : on) {
            for (int t = 0; t < 4; t++) {
                if (ArmorGasSC.worn(p, t) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Room for `g` in the pieces in the slots, mB, in the fill order. */
    private int[] slotRoom(Gas g) {
        int[] room = new int[FILL_ORDER.length];
        for (int k = 0; k < FILL_ORDER.length; k++) {
            ItemStack s = slots[FILL_ORDER[k]];
            room[k] = Math.max(0, ArmorGasSC.capacity(s, g) - ArmorGasSC.amount(s, g));
        }
        return room;
    }

    /** Room for `g` in the slots and the suits of the players on top, mB. */
    public int need(Gas g, List<EntityPlayer> on) {
        long sum = 0;
        for (int r : slotRoom(g)) {
            sum += r;
        }
        for (EntityPlayer p : on == null ? java.util.Collections.<EntityPlayer>emptyList() : on) {
            sum += Math.max(0, ArmorGasSC.suitCapacity(p, g) - ArmorGasSC.suitAmount(p, g));
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /**
     * Pulls up to `want` mB of `g` out of the fluid handlers beside the station into its own tank
     * (up to the tank's room - not just what the armour lacks). @return mB moved
     */
    private int pullIntoTank(Gas g, int want) {
        Fluid fluid = g.fluidOf();
        if (fluid == null || want <= 0) {
            return 0;
        }
        int got = 0;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            int room = fillTank(g, want - got, false);    // never take more than fits
            if (room <= 0) {
                break;
            }
            int x = xCoord + dir.offsetX, y = yCoord + dir.offsetY, z = zCoord + dir.offsetZ;
            if (!worldObj.blockExists(x, y, z)) {
                continue;
            }
            TileEntity te = worldObj.getTileEntity(x, y, z);
            if (!(te instanceof IFluidHandler) || te instanceof TileEntityArmorStationSC
                    || te instanceof TileEntityMachineSC || te instanceof TileEntityGeneratorSC) {   // not a machine's or a reactor's own feed
                continue;
            }
            IFluidHandler h = (IFluidHandler) te;
            ForgeDirection from = dir.getOpposite();
            FluidStack test = h.drain(from, new FluidStack(fluid, room), false);
            if (test == null || test.amount <= 0 || test.getFluid() != fluid) {
                continue;
            }
            FluidStack real = h.drain(from, new FluidStack(fluid, Math.min(test.amount, room)), true);
            if (real == null || real.amount <= 0) {
                continue;
            }
            int put = fillTank(g, Math.min(real.amount, room), true);
            int rest = real.amount - put;
            if (rest > 0) {                                // a handler that gave more than asked: give it back
                int back = h.fill(from, new FluidStack(fluid, rest), true);
                if (back < rest) {
                    cpw.mods.fml.common.FMLLog.fine("[Silicon Age] armour station at %d,%d,%d: %d mB of %s neither fitted nor went back",
                            xCoord, yCoord, zCoord, rest - back, fluid.getName());
                }
            }
            got += put;
        }
        return got;
    }

    /**
     * Fills the armour in the slots and on the players on top with up to `max` mB of `g` out of its
     * tank, as far as the buffer pays the pumps (gasCost). @return mB moved (the tank lost as much)
     */
    public int fillFromTank(Gas g, int max, List<EntityPlayer> on) {
        int n = Math.min(Math.min(max, tankAmount(g)), Math.min(need(g, on), affordableGas(getEnergyStored())));
        if (n <= 0) {
            return 0;
        }
        int put = putGas(g, n, on);
        if (put > 0) {
            getTank(g).drain(put, true);
            removeEnergy(gasCost(put));
            markDirty();
        }
        return put;
    }

    /** Puts `mb` of `g` into the pieces in the slots (chestplate first), then the suits on top. @return mB put */
    public int putGas(Gas g, int mb, List<EntityPlayer> on) {
        int[] room = slotRoom(g);
        int[] split = split(mb, room);
        int put = 0;
        for (int k = 0; k < FILL_ORDER.length; k++) {
            if (split[k] > 0) {
                put += ArmorGasSC.fill(slots[FILL_ORDER[k]], g, split[k], false);
            }
        }
        if (on != null) {
            for (EntityPlayer p : on) {
                if (put >= mb) {
                    break;
                }
                put += ArmorGasSC.suitFill(p, g, mb - put, false);
            }
        }
        return put;
    }

    /** The slots in fill order: the chestplate's loop first, as ArmorGasSC.suitFill. */
    public static final int[] FILL_ORDER = {ArmorGasSC.CHEST, ArmorGasSC.HELMET, ArmorGasSC.LEGS, ArmorGasSC.BOOTS};

    /**
     * Pure: shares `mb` out over tanks with `room` free each, in their order (each takes what it
     * can before the next). @return how much each one gets; the sum is min(mb, total room).
     */
    public static int[] split(int mb, int[] room) {
        int[] out = new int[room.length];
        int left = Math.max(0, mb);
        for (int k = 0; k < room.length && left > 0; k++) {
            out[k] = Math.max(0, Math.min(room[k], left));
            left -= out[k];
        }
        return out;
    }

    /** Sums for the screen and WAILA: per gas, in the slots and on the players on top. */
    private void refreshShown(List<EntityPlayer> on) {
        for (Gas g : Gas.values()) {
            long a = 0, c = 0;
            for (ItemStack s : slots) {
                a += ArmorGasSC.amount(s, g);
                c += ArmorGasSC.capacity(s, g);
            }
            for (EntityPlayer p : on) {
                a += ArmorGasSC.suitAmount(p, g);
                c += ArmorGasSC.suitCapacity(p, g);
            }
            shownAmount[g.ordinal()] = (int) Math.min(Integer.MAX_VALUE, a);
            shownCap[g.ordinal()] = (int) Math.min(Integer.MAX_VALUE, c);
        }
    }

    // ------------------------------------------------------------------ inventory: the four pieces, then the modules

    /** What goes in slot `slot`: a piece of the mod's suits of that type (slots 0..3), a module the station takes (4..7). */
    public static boolean fits(int slot, ItemStack stack) {
        if (slot >= FIRST_UPGRADE_SLOT) {
            return slot < ALL_SLOTS && acceptsModule(stack);
        }
        return stack != null && stack.getItem() instanceof ItemArmorSC && ((ItemArmorSC) stack.getItem()).armorType == slot;
    }

    @Override
    public int getSizeInventory() {
        return ALL_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < ALL_SLOTS ? slots[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slots[slot] == null) {
            return null;
        }
        ItemStack out = slots[slot].splitStack(Math.min(amount, slots[slot].stackSize));
        if (slots[slot].stackSize <= 0) {
            slots[slot] = null;
        }
        if (slot >= FIRST_UPGRADE_SLOT) {
            modulesChanged();
        }
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        slots[slot] = stack;
        if (slot >= FIRST_UPGRADE_SLOT) {
            modulesChanged();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.siliconage.armorStation.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;                                         // modules stack; a suit piece is one anyway (SlotPiece: 1)
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
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
        return fits(slot, stack);
    }

    /** Hoppers and tubes reach the armour slots only - the modules are the player's. */
    private static final int[] PIECE_SLOTS = {0, 1, 2, 3};

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return PIECE_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < SLOTS && slots[slot] == null && fits(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot < SLOTS;
    }

    // ------------------------------------------------------------------ gases pushed in by pipes

    /** Pipes push gases into the tanks (each only its own gas; gasPerTick() a tick per gas, however often they call). */
    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        Gas g = resource == null ? null : Gas.of(resource.getFluid());
        if (g == null || resource.amount <= 0) {
            return 0;
        }
        int n = fillTank(g, resource.amount, false);
        long now = worldObj != null ? worldObj.getTotalWorldTime() : 0L;
        boolean byHand = from == ForgeDirection.UNKNOWN;      // a bucket / cell poured by a player (FluidHandSC): no pipe limit
        if (worldObj != null && !byHand) {
            n = Math.min(n, pipeLeft(g, now));
        }
        if (n <= 0 || !doFill) {
            return Math.max(0, n);
        }
        int put = fillTank(g, n, true);
        if (put > 0 && worldObj != null && !byHand) {
            pipeUsed(g, now, put);
        }
        return put;
    }

    /** What the pipes put in this tick, per gas (reset when the world time moves on; not saved). */
    private final int[] pipeIn = new int[Gas.values().length];
    private long pipeTick = Long.MIN_VALUE;

    /** mB of `g` the pipes may still push in at world time `now` (gasPerTick() a tick per gas). */
    public int pipeLeft(Gas g, long now) {
        if (now != pipeTick) {
            pipeTick = now;
            java.util.Arrays.fill(pipeIn, 0);
        }
        return Math.max(0, gasPerTick() - pipeIn[g.ordinal()]);
    }

    /** Counts `mb` of `g` pushed in by a pipe at world time `now`. */
    public void pipeUsed(Gas g, long now, int mb) {
        pipeLeft(g, now);
        pipeIn[g.ordinal()] += mb;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return Gas.of(fluid) != null;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    /** The tanks, one per gas (read-only for pipes: the station is a consumer, drain gives nothing). */
    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        FluidTankInfo[] out = new FluidTankInfo[GASES];
        for (Gas g : Gas.values()) {
            out[g.ordinal()] = getTank(g).getInfo();
        }
        return out;
    }

    // ------------------------------------------------------------------ the item keeps the energy, the settings and the modules

    /** Set once the modules went into the dropped item, so breakBlock doesn't drop them loose too. */
    private boolean upgradesInItem;
    /** World tick writeToItem() ran in: a getDrops() from another tick (another mod asking) mustn't stick. */
    private long upgradesInItemTick = -1;
    /** Set once the tanks went into the dropped item (same tick rule): breakBlock empties them here. */
    private boolean tanksInItem;

    /** Whether breakBlock should leave the module slots alone (they're in the dropped item). */
    public boolean upgradesInItem() {
        return upgradesInItem && sameTick();
    }

    /** Whether breakBlock should empty the tanks (they're in the dropped item). */
    public boolean tanksInItem() {
        return tanksInItem && sameTick();
    }

    private boolean sameTick() {
        return worldObj == null || upgradesInItemTick == worldObj.getTotalWorldTime();
    }

    /**
     * Breaking: what drops loose, taken out of the slots - the armour pieces always, the modules
     * only when they didn't go into the item (writeToItem in the same tick). The tanks that went
     * into the item are emptied here, so nothing is left behind to count twice.
     */
    public java.util.List<ItemStack> takeLooseContents() {
        java.util.List<ItemStack> out = new java.util.ArrayList<ItemStack>();
        boolean modulesKept = upgradesInItem();
        if (tanksInItem()) {
            for (FluidTank t : tanks) {
                t.setFluid(null);
            }
        }
        for (int i = 0; i < ALL_SLOTS; i++) {
            if (slots[i] == null) {
                continue;
            }
            if (!(modulesKept && i >= FIRST_UPGRADE_SLOT)) {
                out.add(slots[i]);
            }
            slots[i] = null;                              // no second copy for a screen still open (the item's modules too)
        }
        markDirty();
        return out;
    }

    /** The modules saved in an item's NBT, by slot (null where empty) - also for the item's tooltip. */
    public static ItemStack[] upgradesOf(NBTTagCompound tag) {
        ItemStack[] ups = new ItemStack[UPGRADE_SLOTS];
        NBTTagList list = tag == null ? null : tag.getTagList("Items", 10);
        for (int k = 0; list != null && k < list.tagCount(); k++) {
            NBTTagCompound t = list.getCompoundTagAt(k);
            int i = t.getByte("Slot");
            if (i >= 0 && i < UPGRADE_SLOTS) {
                ups[i] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        return ups;
    }

    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList ups = new NBTTagList();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack s = slots[FIRST_UPGRADE_SLOT + i];
            if (s != null) {
                NBTTagCompound t = s.writeToNBT(new NBTTagCompound());
                t.setByte("Slot", (byte) i);
                ups.appendTag(t);
            }
        }
        upgradesInItem = ups.tagCount() > 0;
        upgradesInItemTick = worldObj != null ? worldObj.getTotalWorldTime() : -1;
        if (upgradesInItem) {
            NBTTagCompound u = new NBTTagCompound();
            u.setTag("Items", ups);
            nbt.setTag(ITEM_UPGRADES_KEY, u);
        }
        if (getEnergyStored() > 0) {
            nbt.setInteger("EnergySC", getEnergyStored());
        }
        if (!fillGases || gasMask != ALL_GASES) {
            nbt.setBoolean("NoGases", !fillGases);
            nbt.setInteger("GasMask", gasMask);
            nbt.setInteger(MASK_GASES_KEY, GASES);
        }
        NBTTagCompound t = tanksTag();
        tanksInItem = !t.hasNoTags();
        if (tanksInItem) {
            nbt.setTag(ITEM_TANKS_KEY, t);
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        if (nbt.hasKey(ITEM_UPGRADES_KEY)) {               // the modules first: a transformer before the first energy tick
            ItemStack[] ups = upgradesOf(nbt.getCompoundTag(ITEM_UPGRADES_KEY));
            for (int i = 0; i < ups.length; i++) {
                if (ups[i] != null) {
                    slots[FIRST_UPGRADE_SLOT + i] = ups[i];
                }
            }
            modulesChanged();
        }
        if (nbt.hasKey("EnergySC")) {
            restoreEnergy(nbt.getInteger("EnergySC"));
        }
        if (nbt.hasKey("GasMask")) {
            fillGases = !nbt.getBoolean("NoGases");
            gasMask = migrateMask(nbt.getInteger("GasMask"), nbt.hasKey(MASK_GASES_KEY) ? nbt.getInteger(MASK_GASES_KEY) : OLD_GASES);
        }
        if (nbt.hasKey(ITEM_TANKS_KEY)) {                 // after the modules: a Tank Extension sizes the tanks first
            syncTankCapacity();
            loadTanks(nbt.getCompoundTag(ITEM_TANKS_KEY));
        }
        markDirty();
    }

    // ------------------------------------------------------------------ saving, and what clients see

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        facing = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (facing.offsetY != 0 || facing == ForgeDirection.UNKNOWN) {
            facing = ForgeDirection.NORTH;
        }
        fillGases = !nbt.getBoolean("NoGases");
        gasMask = nbt.hasKey("GasMask")
                ? migrateMask(nbt.getInteger("GasMask"), nbt.hasKey(MASK_GASES_KEY) ? nbt.getInteger(MASK_GASES_KEY) : OLD_GASES) : ALL_GASES;
        active = nbt.getBoolean("StationActive");
        for (int i = 0; i < ALL_SLOTS; i++) {
            slots[i] = null;
        }
        NBTTagList list = nbt.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot");
            if (slot >= 0 && slot < ALL_SLOTS) {
                slots[slot] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        loadTanks(nbt.hasKey(TANKS_KEY) ? nbt.getCompoundTag(TANKS_KEY) : null);     // an older station: empty tanks
        syncTankCapacity();
        lastSinkKey = sinkKey();                           // loaded before the tile joins the net: nothing to re-announce
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Facing", facing.ordinal());
        nbt.setBoolean("NoGases", !fillGases);
        nbt.setInteger("GasMask", gasMask);
        nbt.setInteger(MASK_GASES_KEY, GASES);
        nbt.setBoolean("StationActive", active);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < ALL_SLOTS; i++) {
            if (slots[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                slots[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        nbt.setTag("Items", list);
        nbt.setTag(TANKS_KEY, tanksTag());
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("StationActive", active);
        nbt.setInteger("Facing", facing.ordinal());
        nbt.setByteArray("Lv", currentWindowLevels());   // the side windows
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        NBTTagCompound nbt = pkt.func_148857_g();
        boolean wasActive = active;
        ForgeDirection wasFacing = facing;
        active = nbt.getBoolean("StationActive");
        ForgeDirection f = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (f.offsetY == 0 && f != ForgeDirection.UNKNOWN) {
            facing = f;
        }
        byte[] lv = nbt.getByteArray("Lv");
        if (lv.length == GASES) {
            windowLevels = lv;                            // the windows: ArmorStationRendererSC draws them, no chunk re-render
        }
        if (worldObj != null && (wasActive != active || wasFacing != facing)) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }

    /** Switched off (it's placed so): it takes no energy, so no line can overvolt it. */
    @Override
    public int demandedEnergy() {
        return powerOn ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }
}
