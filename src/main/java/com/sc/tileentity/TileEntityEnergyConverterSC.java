package com.sc.tileentity;

import com.sc.energy.ForeignEnergySC;
import com.sc.energy.ForeignEnergySC.Kind;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemConverterModuleSC;
import com.sc.item.ItemUpgradeSC;
import com.sc.machine.UpgradeType;

import cpw.mods.fml.common.Optional;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The Energy Converter: two buffers - EU (1 000 000, x4 per Energy Storage module) and one of another
 * mod's energy, the pair chosen on the screen (EU-RF, EU-J with the Mekanism card, EU-gJ with the
 * Galacticraft card) - and a converter between them: EU -> X, X -> EU, or «Баланс» (both kept equally
 * full). Rates and the loss come from the config (ForeignEnergySC). Each face is set to Вход / Выход /
 * Выкл and to a buffer (EU / the other energy / Авто - what the neighbour understands); an input
 * filter per face, an output priority, a comparator on either buffer, a charge slot for items of any
 * of the energies, six expansion slots (Transformer, Universal Transformer, Energy Storage, Channel
 * Amplifier, Efficiency, the cards). Throughput = the working tier's voltage x packets a tick.
 *
 * Other mods' APIs: IC2 through TileEntityEnergyBase (@Optional), Industrial Upgrade's several
 * packets (@Optional), Mekanism's acceptor / outputter and Galacticraft's IElectrical (@Optional -
 * stripped when the mod is absent), RF through the subclass TileEntityEnergyConverterRfSC (only
 * registered when the RF API is on the class path - see create()); the calls into those APIs live in
 * com.sc.compat.RfOpsSC / MekOpsSC / GcOpsSC, touched only when ForeignEnergySC says the API is there.
 * The RF methods below take and give only primitives, so they are harmless without the API.
 */
@Optional.InterfaceList({
        @Optional.Interface(iface = "ic2.api.energy.tile.IMultiEnergySource", modid = "industrialupgrade"),
        @Optional.Interface(iface = "mekanism.api.energy.IStrictEnergyAcceptor", modid = ForeignEnergySC.MEKANISM_MODID),
        @Optional.Interface(iface = "mekanism.api.energy.ICableOutputter", modid = ForeignEnergySC.MEKANISM_MODID),
        @Optional.Interface(iface = "micdoodle8.mods.galacticraft.api.transmission.tile.IElectrical", modid = ForeignEnergySC.GC_MODID)
})
public class TileEntityEnergyConverterSC extends TileEntityEnergyBase implements net.minecraft.inventory.ISidedInventory,
        ic2.api.energy.tile.IMultiEnergySource, mekanism.api.energy.IStrictEnergyAcceptor, mekanism.api.energy.ICableOutputter,
        micdoodle8.mods.galacticraft.api.transmission.tile.IElectrical {

    /** Slots: 0 the charge slot, 1..6 the expansions. */
    public static final int SLOT_CHARGE = 0, FIRST_MODULE = 1, MODULE_SLOTS = 6, SLOT_COUNT = FIRST_MODULE + MODULE_SLOTS;
    /** A face: off, an input, an output. */
    public static final int MODE_OFF = 0, MODE_IN = 1, MODE_OUT = 2;
    /** A face's buffer: whatever the neighbour understands, EU, the other energy. */
    public static final int BUF_AUTO = 0, BUF_EU = 1, BUF_X = 2;
    /** What a face sends out now. */
    public static final int OUT_NONE = 0, OUT_EU = 1, OUT_X = 2;
    public static final int DIR_EU_TO_X = 0, DIR_X_TO_EU = 1, DIR_BALANCE = 2;
    /** Output priority: EU outputs first, or the other energy's. */
    public static final int PRIO_EU = 0, PRIO_X = 1;
    public static final int COMP_EU = 0, COMP_X = 1;
    /** The input filter's bits: EU, then one per Kind (2 << ordinal). */
    public static final int F_EU = 1, F_ALL = 15;
    /** switchPair's answers. */
    public static final int SW_OK = 0, SW_SAME = 1, SW_UNAVAILABLE = 2, SW_NO_ROOM = 3;
    /** Seconds of the flow graph. */
    public static final int GRAPH = 30;
    /** The NBT id of the tile (both classes go by it: a world keeps its converters with or without the RF API). */
    public static final String TILE_ID = "SiliconAge.energyConverter";

    private final ItemStack[] inv = new ItemStack[SLOT_COUNT];
    /** The other energy's buffer, in its own units. */
    private double foreign;
    /** The pair (Kind ordinal), -1: none (no other energy in this game). */
    private int pair = -1;
    private int direction = DIR_EU_TO_X, outPriority = PRIO_EU, comparatorOf = COMP_EU;
    private final byte[] mode = new byte[6], buf = new byte[6], filter = new byte[6];
    /** Only for the texture: the front with the two windows. */
    private ForgeDirection facing = ForgeDirection.NORTH;

    // ---- worked out from the modules (refreshModules) ----
    private Tier euTier = ForeignEnergySC.BASE_TIER, workTier = ForeignEnergySC.BASE_TIER;
    private int packets = 1, throughput = ForeignEnergySC.BASE_TIER.getVoltage(), lossPct = 5, euCap = ForeignEnergySC.BASE_EU_BUFFER;
    private boolean universal;

    // ---- the faces' output kinds (recomputeKinds) ----
    private final int[] outKind = new int[6];
    private ForgeDirection[] euOutFaces = new ForgeDirection[0];
    private int faceSignature = -1;
    private boolean kindsDirty = true, netRefreshDue, firstTick = true;
    private int rr;

    // ---- this tick (beginTick) ----
    private long tickStamp = Long.MIN_VALUE;
    private long euInTick, euOutTick, euOutPrevTick;
    private double xInTick, xOutTick;

    // ---- the second being measured, and what the screen shows (per tick, averaged over a second) ----
    private long wEuIn, wEuOut, wEuConvOut, wEuConvIn, wEuCharge;
    private double wXIn, wXOut, wXConvIn, wXConvOut, wXCharge;
    private final double[] wSide = new double[6];
    private int ticksInWindow;
    /** Synced: EU +/- a tick, the other energy +/- a tick, the conversion (EU, X; + EU -> X), per face. */
    private int statEuPlus, statEuMinus, statXPlus, statXMinus, statConvEu, statConvX;
    private final int[] statSide = new int[6];
    private final int[] graphEu = new int[GRAPH], graphX = new int[GRAPH];
    private int graphHead;
    private int lastComparator = -1;
    /** The modules went into the dropped item (breakBlock doesn't drop them loose): the world tick it happened. */
    private long modulesInItemTick = -1;

    /** Totals since load (the world test checks the rate with them). */
    public long totalEuToX, totalEuFromX;
    public double totalXMade, totalXUsed;

    public TileEntityEnergyConverterSC() {
        super(ForeignEnergySC.BASE_TIER);
        for (int i = 0; i < 6; i++) {
            mode[i] = MODE_IN;
            buf[i] = BUF_AUTO;
            filter[i] = F_ALL;
        }
    }

    // ------------------------------------------------------------------ the class with or without RF

    private static Class<? extends TileEntityEnergyConverterSC> tileClass;

    /** The tile class to register and create: with the RF interfaces when the RF API is there. */
    @SuppressWarnings("unchecked")
    public static Class<? extends TileEntityEnergyConverterSC> tileClass() {
        if (tileClass == null) {
            tileClass = TileEntityEnergyConverterSC.class;
            if (ForeignEnergySC.rfApi()) {
                try {
                    tileClass = (Class<? extends TileEntityEnergyConverterSC>) Class.forName("com.sc.tileentity.TileEntityEnergyConverterRfSC");
                } catch (Throwable t) {
                    tileClass = TileEntityEnergyConverterSC.class;      // the API is there but broken: EU and the others only
                }
            }
        }
        return tileClass;
    }

    /** A new converter tile (the right class for this game). */
    public static TileEntityEnergyConverterSC create() {
        try {
            return tileClass().newInstance();
        } catch (Throwable t) {
            return new TileEntityEnergyConverterSC();
        }
    }

    /** This tile speaks RF to its neighbours (the subclass). */
    public boolean speaksRf() {
        return false;
    }

    // ------------------------------------------------------------------ modules

    /** What the expansion slots take: Transformer, Universal Transformer, Energy Storage, the converter's own modules. */
    public static boolean isModule(ItemStack s) {
        if (s == null) {
            return false;
        }
        if (s.getItem() instanceof ItemConverterModuleSC) {
            return true;
        }
        if (s.getItem() instanceof ItemUpgradeSC) {
            UpgradeType t = ItemUpgradeSC.typeOf(s);
            return t == UpgradeType.TRANSFORMER || t == UpgradeType.UNIVERSAL_TRANSFORMER || t == UpgradeType.ENERGY_STORAGE;
        }
        return false;
    }

    /** How many of this module count (a slot holds no more). */
    public static int moduleLimit(ItemStack s) {
        if (s == null) {
            return 0;
        }
        if (s.getItem() instanceof ItemConverterModuleSC) {
            return ItemConverterModuleSC.kindOf(s).max;
        }
        UpgradeType t = ItemUpgradeSC.typeOf(s);
        return t == UpgradeType.TRANSFORMER ? ForeignEnergySC.MAX_TRANSFORMERS : t == UpgradeType.ENERGY_STORAGE ? ForeignEnergySC.MAX_STORAGE : 1;
    }

    private int count(UpgradeType type) {
        int n = 0;
        for (int i = FIRST_MODULE; i < SLOT_COUNT; i++) {
            ItemStack s = inv[i];
            if (s != null && s.getItem() instanceof ItemUpgradeSC && ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return n;
    }

    public int count(ItemConverterModuleSC.Kind kind) {
        int n = 0;
        for (int i = FIRST_MODULE; i < SLOT_COUNT; i++) {
            ItemStack s = inv[i];
            if (s != null && s.getItem() instanceof ItemConverterModuleSC && ItemConverterModuleSC.kindOf(s) == kind) {
                n += s.stackSize;
            }
        }
        return n;
    }

    public int transformers() {
        return Math.min(ForeignEnergySC.MAX_TRANSFORMERS, count(UpgradeType.TRANSFORMER));
    }

    public int storageModules() {
        return Math.min(ForeignEnergySC.MAX_STORAGE, count(UpgradeType.ENERGY_STORAGE));
    }

    public int amplifiers() {
        return Math.min(ForeignEnergySC.MAX_AMPLIFIERS, count(ItemConverterModuleSC.Kind.AMPLIFIER));
    }

    public int efficiencyModules() {
        return Math.min(ForeignEnergySC.MAX_EFFICIENCY, count(ItemConverterModuleSC.Kind.EFFICIENCY));
    }

    /** Works the tiers, throughput, loss and capacity out of the slots again. */
    private void refreshModules() {
        universal = count(UpgradeType.UNIVERSAL_TRANSFORMER) > 0;
        int tr = transformers();
        euTier = ForeignEnergySC.euTier(tr);
        workTier = ForeignEnergySC.workTier(tr, universal);
        packets = ForeignEnergySC.packets(amplifiers());
        throughput = ForeignEnergySC.throughput(tr, universal, amplifiers());
        lossPct = ForeignEnergySC.lossPercent(efficiencyModules());
        euCap = ForeignEnergySC.euCapacity(storageModules());
        setTier(euTier);
    }

    /** A module went in or out (both sides): numbers, the pair, the energy nets. */
    private void modulesChanged() {
        Tier before = euTier;
        boolean uniBefore = universal;
        refreshModules();
        if (worldObj != null && !worldObj.isRemote) {
            fixPair();
            kindsDirty = true;
            if (before != euTier || uniBefore != universal) {
                netRefreshDue = true;                   // IC2 caches the sink / source tier
            }
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ---- the numbers ----

    public Tier euTier() {
        return euTier;
    }

    public Tier workTier() {
        return workTier;
    }

    public int packets() {
        return packets;
    }

    /** EU (or their worth) a tick: converted, taken in, sent out. */
    public int throughput() {
        return throughput;
    }

    public int lossPercent() {
        return lossPct;
    }

    @Override
    public int getMaxEnergyStored() {
        return euCap;
    }

    @Override
    public boolean acceptsAnyVoltage() {
        return universal;
    }

    @Override
    public Tier inputTier() {
        return universal ? Tier.max() : euTier;
    }

    @Override
    public Tier outputTier() {
        return euTier;
    }

    // ------------------------------------------------------------------ the pair

    public Kind pairKind() {
        return Kind.byOrdinal(pair);
    }

    /** A card for this energy is in a slot. */
    public boolean hasCard(Kind k) {
        if (k == Kind.J) {
            return count(ItemConverterModuleSC.Kind.CARD_MEKANISM) > 0;
        }
        return k == Kind.GJ && count(ItemConverterModuleSC.Kind.CARD_GALACTICRAFT) > 0;
    }

    /** The energy can be chosen here: its mod is there, and (J, gJ) its card is in. */
    public boolean pairAvailable(Kind k) {
        return k != null && k.modPresent() && (k == Kind.RF || hasCard(k));
    }

    /** A pair is chosen and works (its mod and card are there). */
    public boolean pairActive() {
        return pairAvailable(pairKind());
    }

    /** Units of the other energy an EU is worth (1 without a pair). */
    public double rate() {
        Kind k = pairKind();
        return k == null ? 1 : k.perEu();
    }

    public double getForeign() {
        return foreign;
    }

    /** The other energy's buffer: the EU one's worth. */
    public double foreignCapacity() {
        return pairKind() == null ? 0 : euCap * rate();
    }

    /**
     * Keeps the pair sensible: one that works stays; one that doesn't with energy in it stays too
     * (blocked - its card or mod is gone; nothing lost); else the first energy that works, or none.
     */
    private void fixPair() {
        if (pairActive() || (pairKind() != null && foreign > 0)) {
            return;
        }
        int before = pair;
        pair = -1;
        foreign = 0;
        for (Kind k : Kind.values()) {
            if (pairAvailable(k)) {
                pair = k.ordinal();
                break;
            }
        }
        if (pair != before) {
            kindsDirty = true;
            markDirty();
        }
    }

    /**
     * Another pair (the screen's button): the old energy's buffer goes back into EU with the loss
     * - refused when the EU buffer hasn't the room for it. @return SW_*
     */
    public int switchPair(int kind) {
        Kind k = Kind.byOrdinal(kind);
        if (k == null || kind == pair) {
            return SW_SAME;
        }
        if (!pairAvailable(k)) {
            return SW_UNAVAILABLE;
        }
        if (pairKind() != null && foreign > 0) {
            long eu = ForeignEnergySC.refund(foreign, rate(), lossPct, (long) euCap - getEnergyStored());
            if (eu < 0) {
                return SW_NO_ROOM;
            }
            addEnergy((int) eu);
        }
        foreign = 0;
        pair = kind;
        kindsDirty = true;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);   // the front's window colour
        }
        return SW_OK;
    }

    /** EU the pair switch would give back now (the screen's hint). */
    public long refundPreview() {
        return pairKind() == null ? 0 : (long) Math.floor(ForeignEnergySC.xToEu(foreign, rate(), lossPct) + 1e-9);
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int d) {
        direction = Math.max(0, Math.min(2, d));
        markDirty();
    }

    public int getOutPriority() {
        return outPriority;
    }

    public void cyclePriority() {
        outPriority = 1 - outPriority;
        markDirty();
    }

    public int getComparatorOf() {
        return comparatorOf;
    }

    public void cycleComparator() {
        comparatorOf = 1 - comparatorOf;
        markDirty();
    }

    // ------------------------------------------------------------------ faces

    public int getMode(int side) {
        return mode[side];
    }

    public int getBuf(int side) {
        return buf[side];
    }

    public int getFilter(int side) {
        return filter[side];
    }

    public int getOutKind(int side) {
        return outKind[side];
    }

    public static int filterBit(Kind k) {
        return 2 << k.ordinal();
    }

    public void setMode(int side, int m) {
        mode[side] = (byte) Math.max(0, Math.min(2, m));
        facesChanged();
    }

    public void cycleMode(int side) {
        setMode(side, (mode[side] + 1) % 3);
    }

    public void setBuf(int side, int b) {
        buf[side] = (byte) Math.max(0, Math.min(2, b));
        facesChanged();
    }

    public void cycleBuf(int side) {
        setBuf(side, (buf[side] + 1) % 3);
    }

    public void setFilter(int side, int f) {
        filter[side] = (byte) (f & F_ALL);
        facesChanged();
    }

    /** The filter round: both (EU and the pair's energy) -> EU only -> the other only -> none -> both. */
    public void cycleFilter(int side) {
        Kind k = pairKind();
        int xb = k == null ? 0 : filterBit(k);
        int f = filter[side];
        boolean eu = (f & F_EU) != 0, x = xb != 0 && (f & xb) != 0;
        if (xb == 0) {
            f = eu ? f & ~F_EU : f | F_EU;
        } else if (eu && x) {
            f &= ~xb;
        } else if (eu) {
            f = (f & ~F_EU) | xb;
        } else if (x) {
            f &= ~xb;
        } else {
            f |= F_EU | xb;
        }
        setFilter(side, f);
    }

    private void facesChanged() {
        kindsDirty = true;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            recomputeKinds();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());   // cables look at their sides again
        }
    }

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection f) {
        if (f != null && f != ForgeDirection.UNKNOWN) {
            facing = f;
            markDirty();
        }
    }

    /** EU come in through this face (Вход, the buffer EU or Авто, the filter lets EU through). */
    @Override
    public boolean acceptsFrom(ForgeDirection side) {
        if (side == ForgeDirection.UNKNOWN) {
            return true;
        }
        int s = side.ordinal();
        return s < 6 && mode[s] == MODE_IN && buf[s] != BUF_X && (filter[s] & F_EU) != 0;
    }

    /** The other energy comes in through this face. */
    public boolean acceptsForeignFrom(ForgeDirection side) {
        Kind k = pairKind();
        if (k == null) {
            return false;
        }
        if (side == ForgeDirection.UNKNOWN) {
            return true;
        }
        int s = side.ordinal();
        return s < 6 && mode[s] == MODE_IN && buf[s] != BUF_EU && (filter[s] & filterBit(k)) != 0;
    }

    @Override
    public ForgeDirection[] outputFaces() {
        return euOutFaces;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection side) {
        return side != ForgeDirection.UNKNOWN && side.ordinal() < 6 && mode[side.ordinal()] != MODE_OFF;
    }

    @Override
    public boolean isEnergySink() {
        return true;
    }

    @Override
    public boolean isEnergySource() {
        return true;
    }

    /** The neighbour beside face `side`, or null (never loads a chunk). */
    private TileEntity neighbour(int side) {
        if (worldObj == null) {
            return null;
        }
        ForgeDirection d = ForgeDirection.getOrientation(side);
        int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
        return worldObj.blockExists(x, y, z) ? worldObj.getTileEntity(x, y, z) : null;
    }

    /** The neighbour beside `side` takes the pair's energy (asked of its API). */
    private boolean neighbourTakesForeign(int side) {
        Kind k = pairKind();
        TileEntity te = neighbour(side);
        if (k == null || te == null || te == this) {
            return false;
        }
        ForgeDirection face = ForgeDirection.getOrientation(side).getOpposite();
        try {
            switch (k) {
                case RF: return ForeignEnergySC.rfApi() && !ForeignEnergySC.testAllPresent && com.sc.compat.RfOpsSC.takes(te, face);
                case J: return ForeignEnergySC.mekanism() && !ForeignEnergySC.testAllPresent && com.sc.compat.MekOpsSC.takes(te, face);
                default: return ForeignEnergySC.galacticraft() && !ForeignEnergySC.testAllPresent && com.sc.compat.GcOpsSC.takes(te, face);
            }
        } catch (Throwable t) {
            return false;                              // another mod's tile misbehaving: not a receiver
        }
    }

    /** What face `side` sends out: nothing, EU, or the other energy (Авто: what the neighbour takes, EU else). */
    public int computeOutKind(int side) {
        if (mode[side] != MODE_OUT) {
            return OUT_NONE;
        }
        boolean x = pairActive();
        switch (buf[side]) {
            case BUF_EU: return OUT_EU;
            case BUF_X: return x ? OUT_X : OUT_NONE;
            default: return x && neighbourTakesForeign(side) ? OUT_X : OUT_EU;
        }
    }

    /** Every face's output kind again; IC2 / the mod's net are told when the EU faces changed. */
    public void recomputeKinds() {
        kindsDirty = false;
        int n = 0;
        for (int s = 0; s < 6; s++) {
            outKind[s] = computeOutKind(s);
            n += outKind[s] == OUT_EU ? 1 : 0;
        }
        ForgeDirection[] faces = new ForgeDirection[n];
        int sig = 0;
        for (int s = 0, k = 0; s < 6; s++) {
            if (outKind[s] == OUT_EU) {
                faces[k++] = ForgeDirection.getOrientation(s);
                sig |= 1 << s;
            }
            if (acceptsFrom(ForgeDirection.getOrientation(s))) {
                sig |= 1 << (s + 6);
            }
            if (mode[s] != MODE_OFF) {
                sig |= 1 << (s + 12);
            }
        }
        euOutFaces = faces;
        if (sig != faceSignature) {
            // IC2 caches the faces a tile takes / gives on. The first time too, when EU go out somewhere:
            // the tile joined the net (chunk load, placement with the item's settings) before its output
            // faces were worked out - euOutFaces was still empty, and IC2 would never take EU from it
            if (faceSignature != -1 || n > 0) {
                netRefreshDue = true;
            }
            faceSignature = sig;
        }
    }

    /** A neighbouring block changed (the block tells it). */
    public void neighbourChanged() {
        kindsDirty = true;
    }

    // ------------------------------------------------------------------ the tick

    /** A new world tick: this tick's counters start again. */
    private void beginTick() {
        long now = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        if (now != tickStamp) {
            tickStamp = now;
            euOutPrevTick = euOutTick;
            euInTick = 0;
            euOutTick = 0;
            xInTick = 0;
            xOutTick = 0;
        }
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
        beginTick();
        if (firstTick) {
            firstTick = false;
            refreshModules();
            fixPair();
            kindsDirty = true;
        }
        if (kindsDirty || worldObj.getTotalWorldTime() % 40 == 0) {
            recomputeKinds();
        }
        if (netRefreshDue) {
            netRefreshDue = false;
            refreshEnergyNet();
        }
        if (switchedOn()) {
            convert();
            pushForeign();
            chargeSlotItem();
        }
        window();
        if (worldObj.getTotalWorldTime() % 10 == 0) {
            int level = comparatorLevel();
            if (level != lastComparator) {
                lastComparator = level;
                worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());
            }
        }
    }

    /** One tick of conversion, at most the throughput (EU or their worth). */
    public void convert() {
        if (!pairActive()) {
            return;
        }
        double rate = rate(), xCap = foreignCapacity();
        double keep = (100 - lossPct) / 100.0;
        long eu = getEnergyStored();
        if (direction == DIR_BALANCE) {
            long step = ForeignEnergySC.balanceStep(eu, euCap, foreign, xCap, rate, lossPct, throughput);
            if (step > 0) {
                euToX((int) step, rate);
            } else if (step < 0) {
                xToEu((int) -step, rate, keep);
            }
        } else if (direction == DIR_EU_TO_X) {
            long room = keep <= 0 ? 0 : (long) Math.floor((xCap - foreign) / (rate * keep));
            long e = Math.min(Math.min(throughput, eu), room);
            if (e > 0) {
                euToX((int) e, rate);
            }
        } else {
            long fromX = (long) Math.floor(ForeignEnergySC.xToEu(foreign, rate, lossPct) + 1e-9);
            long make = Math.min(Math.min((long) Math.floor(throughput * keep), (long) euCap - eu), fromX);
            if (make > 0) {
                xToEu((int) make, rate, keep);
            }
        }
    }

    private void euToX(int e, double rate) {
        int taken = removeEnergy(e);
        double made = ForeignEnergySC.euToX(taken, rate, lossPct);
        foreign = Math.min(foreignCapacity(), foreign + made);
        wEuConvOut += taken;
        wXConvIn += made;
        totalEuToX += taken;
        totalXMade += made;
    }

    private void xToEu(int make, double rate, double keep) {
        double used = keep <= 0 ? 0 : make * rate / keep;
        if (used > foreign) {
            used = foreign;
            make = (int) Math.floor(used / rate * keep + 1e-9);
        }
        foreign = Math.max(0, foreign - used);
        addEnergy(make);
        wXConvOut += used;
        wEuConvIn += make;
        totalXUsed += used;
        totalEuFromX += make;
    }

    /** World tick the chunk was last told the other buffer changed. */
    private long foreignMarkTick = -1;

    /**
     * The other buffer changed without the EU one: tell the chunk it has something to save (once a
     * tick) - else RF / J / gJ moved in or out are not written on autosave / server stop and come back
     * as they were (RF sent out a second time, or RF taken in lost).
     */
    private void foreignChanged() {
        if (worldObj != null && !worldObj.isRemote) {
            long now = worldObj.getTotalWorldTime();
            if (now != foreignMarkTick) {
                foreignMarkTick = now;
                worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
            }
        }
    }

    /** The other energy's outputs: what this tick's output budget leaves (EU first: the EU sent last tick counts against it). */
    private void pushForeign() {
        Kind k = pairKind();
        if (!pairActive() || foreign <= 0) {
            return;
        }
        double rate = rate();
        double budget = foreignOutRoom();
        if (budget <= 0) {
            return;
        }
        for (int i = 0; i < 6 && budget > 0 && foreign > 0; i++) {
            int s = (rr + i) % 6;
            if (outKind[s] != OUT_X) {
                continue;
            }
            TileEntity te = neighbour(s);
            if (te == null || te == this) {
                continue;
            }
            ForgeDirection face = ForgeDirection.getOrientation(s).getOpposite();
            double offer = Math.min(budget, foreign), given = 0;
            try {
                switch (k) {
                    case RF:
                        if (ForeignEnergySC.rfApi()) {
                            given = com.sc.compat.RfOpsSC.give(te, face, (int) Math.min(Integer.MAX_VALUE, Math.floor(offer)));
                        }
                        break;
                    case J:
                        if (ForeignEnergySC.mekanism()) {
                            given = com.sc.compat.MekOpsSC.give(te, face, offer);
                        }
                        break;
                    default:
                        if (ForeignEnergySC.galacticraft()) {
                            given = com.sc.compat.GcOpsSC.give(te, face, offer, this);
                        }
                }
            } catch (Throwable t) {
                given = 0;                                     // another mod's tile misbehaving: skipped
            }
            given = Math.max(0, Math.min(given, offer));
            if (given > 0) {
                foreign -= given;
                foreignChanged();
                budget -= given;
                xOutTick += given;
                wXOut += given;
                wSide[s] -= given;
            }
        }
        rr = (rr + 1) % 6;
        if (rate <= 0) {
            foreign = Math.max(0, foreign);
        }
    }

    /** The other energy's output room left this tick, in its units. */
    private double foreignOutRoom() {
        double eu = throughput - xOutTick / rate() - (outPriority == PRIO_EU ? euOutPrevTick : 0);
        return Math.max(0, eu) * rate();
    }

    /** EU the outputs may still send this tick (the other energy first: what it sent counts against it). */
    private long euOutRoom() {
        long room = (long) euTier.getVoltage() * packets - euOutTick;
        if (outPriority == PRIO_X) {
            room = Math.min(room, (long) Math.floor(throughput - xOutTick / rate()) - euOutTick);
        }
        return Math.max(0, room);
    }

    // ------------------------------------------------------------------ EU in and out (the nets)

    @Override
    public int offerableEnergy() {
        if (!switchedOn() || euOutFaces.length == 0) {
            return 0;
        }
        beginTick();
        return (int) Math.min(Math.min(getEnergyStored(), euTier.getVoltage()), euOutRoom());
    }

    @Override
    public int packetsPerTick() {
        long room = euOutRoom();
        return (int) Math.max(1, Math.min(packets, room / Math.max(1, euTier.getVoltage())));
    }

    @Override
    public int demandedEnergy() {
        if (!switchedOn()) {
            return 0;
        }
        beginTick();
        long room = Math.min((long) euCap - getEnergyStored(), throughput - euInTick);
        return (int) Math.max(0, room);
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (!switchedOn()) {
            return 0;
        }
        beginTick();
        int budget = (int) Math.max(0, Math.min(Integer.MAX_VALUE, throughput - euInTick));
        int got = super.receiveEnergy(from, voltage, Math.min(amount, budget), simulate);
        if (isInvalid()) {
            return amount;                                  // overvolted - it blew up, the packet with it
        }
        if (!simulate && got > 0) {
            euInTick += got;
            wEuIn += got;
            side(from, got, true);
        }
        return got;
    }

    @Override
    protected void sentOut(ForgeDirection face, int eu) {
        beginTick();
        euOutTick += eu;
        wEuOut += eu;
        side(face, eu, false);
    }

    /** Books `amount` EU on face `face` (UNKNOWN - IC2 doesn't say: shared among the faces it could be). */
    private void side(ForgeDirection face, double amount, boolean in) {
        if (face != null && face != ForgeDirection.UNKNOWN && face.ordinal() < 6) {
            wSide[face.ordinal()] += in ? amount : -amount;
            return;
        }
        int n = 0;
        for (int s = 0; s < 6; s++) {
            n += in ? (acceptsFrom(ForgeDirection.getOrientation(s)) ? 1 : 0) : (outKind[s] == OUT_EU ? 1 : 0);
        }
        for (int s = 0; s < 6 && n > 0; s++) {
            if (in ? acceptsFrom(ForgeDirection.getOrientation(s)) : outKind[s] == OUT_EU) {
                wSide[s] += (in ? amount : -amount) / n;
            }
        }
    }

    // ---- Industrial Upgrade: several packets a tick ----

    @Override
    public boolean sendMultibleEnergyPackets() {
        return packetsPerTick() > 1;
    }

    @Override
    public double getMultibleEnergyPacketAmount() {
        return packetsPerTick();
    }

    // ------------------------------------------------------------------ the other energy in and out (any API)

    /**
     * Takes up to `amount` of energy `k` in through `side`: only the pair's energy, through an input
     * face that lets it in, switched on, within the buffer and this tick's throughput. @return taken
     */
    public double acceptForeign(Kind k, ForgeDirection side, double amount, boolean simulate) {
        if (k == null || k != pairKind() || !pairActive() || !switchedOn() || amount <= 0 || !acceptsForeignFrom(side)) {
            return 0;
        }
        beginTick();
        double budget = throughput * rate() - xInTick, room = foreignCapacity() - foreign;
        double got = Math.max(0, Math.min(amount, Math.min(budget, room)));
        if (!simulate && got > 0) {
            foreign += got;
            foreignChanged();
            xInTick += got;
            wXIn += got;
            if (side != ForgeDirection.UNKNOWN && side.ordinal() < 6) {
                wSide[side.ordinal()] += got;
            }
        }
        return got;
    }

    /** Gives up to `amount` of energy `k` out through `side` (pulled by a neighbour): an output face of that energy. */
    public double provideForeign(Kind k, ForgeDirection side, double amount, boolean simulate) {
        if (k == null || k != pairKind() || !pairActive() || !switchedOn() || amount <= 0 || side == ForgeDirection.UNKNOWN
                || side.ordinal() >= 6 || outKind[side.ordinal()] != OUT_X) {
            return 0;
        }
        beginTick();
        double give = Math.max(0, Math.min(amount, Math.min(foreign, foreignOutRoom())));
        if (!simulate && give > 0) {
            foreign -= give;
            foreignChanged();
            xOutTick += give;
            wXOut += give;
            wSide[side.ordinal()] -= give;
        }
        return give;
    }

    /** What could come in through `side` now (a neighbour asking). */
    private double foreignRoom(Kind k, ForgeDirection side) {
        if (k != pairKind() || !pairActive() || !switchedOn() || !acceptsForeignFrom(side)) {
            return 0;
        }
        beginTick();
        return Math.max(0, Math.min(throughput * rate() - xInTick, foreignCapacity() - foreign));
    }

    // ---- RF (the methods of CoFH's IEnergyHandler; the subclass declares the interface) ----

    /** RF is whole: only the whole RF that fit are taken (a fractional room taken in full and reported floored made RF). */
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        int can = (int) Math.floor(acceptForeign(Kind.RF, from, maxReceive, true) + 1e-9);
        if (simulate || can <= 0) {
            return Math.max(0, can);
        }
        return (int) Math.floor(acceptForeign(Kind.RF, from, can, false) + 1e-9);
    }

    /** Only whole RF leave (a fractional rest given out and reported floored was lost). */
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        int can = (int) Math.floor(provideForeign(Kind.RF, from, maxExtract, true) + 1e-9);
        if (simulate || can <= 0) {
            return Math.max(0, can);
        }
        return (int) Math.floor(provideForeign(Kind.RF, from, can, false) + 1e-9);
    }

    public int getEnergyStored(ForgeDirection from) {
        return pairKind() == Kind.RF ? (int) Math.min(Integer.MAX_VALUE, foreign) : 0;
    }

    public int getMaxEnergyStored(ForgeDirection from) {
        return pairKind() == Kind.RF ? (int) Math.min(Integer.MAX_VALUE, foreignCapacity()) : 0;
    }

    // ---- Mekanism (IStrictEnergyAcceptor, ICableOutputter) ----

    @Override
    public double getEnergy() {
        return pairKind() == Kind.J ? foreign : 0;
    }

    @Override
    public void setEnergy(double energy) {
        if (pairKind() == Kind.J) {
            foreign = Math.max(0, Math.min(foreignCapacity(), energy));
            foreignChanged();
        }
    }

    @Override
    public double getMaxEnergy() {
        return pairKind() == Kind.J ? foreignCapacity() : 0;
    }

    @Override
    public double transferEnergyToAcceptor(ForgeDirection side, double amount) {
        return acceptForeign(Kind.J, side, amount, false);
    }

    @Override
    public boolean canReceiveEnergy(ForgeDirection side) {
        return foreignRoom(Kind.J, side) > 0;
    }

    @Override
    public boolean canOutputTo(ForgeDirection side) {
        return pairKind() == Kind.J && pairActive() && side != ForgeDirection.UNKNOWN && side.ordinal() < 6 && outKind[side.ordinal()] == OUT_X;
    }

    // ---- Galacticraft (IElectrical) ----

    @Override
    @Optional.Method(modid = ForeignEnergySC.GC_MODID)
    public float receiveElectricity(ForgeDirection from, float receive, int tier, boolean doReceive) {
        return (float) acceptForeign(Kind.GJ, from, receive, !doReceive);
    }

    @Override
    @Optional.Method(modid = ForeignEnergySC.GC_MODID)
    public float provideElectricity(ForgeDirection from, float request, boolean doProvide) {
        return (float) provideForeign(Kind.GJ, from, request, !doProvide);
    }

    @Override
    @Optional.Method(modid = ForeignEnergySC.GC_MODID)
    public float getRequest(ForgeDirection direction) {
        return (float) Math.min(Float.MAX_VALUE, foreignRoom(Kind.GJ, direction));
    }

    @Override
    @Optional.Method(modid = ForeignEnergySC.GC_MODID)
    public float getProvide(ForgeDirection direction) {
        return 0F;                                                 // it pushes its gJ out itself (pushForeign)
    }

    @Override
    @Optional.Method(modid = ForeignEnergySC.GC_MODID)
    public int getTierGC() {
        return 1;
    }

    @Override
    @Optional.Method(modid = ForeignEnergySC.GC_MODID)
    public boolean canConnect(ForgeDirection direction, micdoodle8.mods.galacticraft.api.transmission.NetworkType type) {
        return type == micdoodle8.mods.galacticraft.api.transmission.NetworkType.POWER && canConnectEnergy(direction);
    }

    // ------------------------------------------------------------------ the charge slot

    /** Items the charge slot takes: the mod's (and IC2's) electric items, RF items when the RF API is there. */
    public static boolean isChargeable(ItemStack s) {
        if (s == null) {
            return false;
        }
        if (TileEntityEnergyStorageSC.isChargeable(s) || TileEntityEnergyStorageSC.ic2Chargeable(s)) {
            return true;
        }
        return ForeignEnergySC.rfApi() && !ForeignEnergySC.testAllPresent && com.sc.compat.RfOpsSC.isItem(s);
    }

    /** One tick of charging: EU items from the EU buffer; RF items from the RF buffer (pair RF) or from EU at the RF rate. */
    private void chargeSlotItem() {
        ItemStack s = inv[SLOT_CHARGE];
        if (s == null) {
            return;
        }
        int eu = getEnergyStored();
        int took = TileEntityEnergyStorageSC.chargeItemAt(s, Math.min(eu, throughput), workTier);
        if (took > 0) {
            removeEnergy(took);
            wEuCharge += took;
            return;
        }
        if (!ForeignEnergySC.rfApi() || !com.sc.compat.RfOpsSC.isItem(s)) {
            return;
        }
        if (pairKind() == Kind.RF && pairActive()) {
            int rf = (int) Math.min(Integer.MAX_VALUE, Math.floor(Math.min(foreign, throughput * rate())));
            int got = com.sc.compat.RfOpsSC.charge(s, rf, false);
            if (got > 0) {
                foreign = Math.max(0, foreign - got);
                foreignChanged();
                wXCharge += got;
            }
            return;
        }
        double rfRate = Kind.RF.perEu(), keep = (100 - lossPct) / 100.0;
        int maxEu = Math.min(eu, throughput);
        int rfMax = (int) Math.min(Integer.MAX_VALUE, Math.floor(ForeignEnergySC.euToX(maxEu, rfRate, lossPct)));
        int got = com.sc.compat.RfOpsSC.charge(s, rfMax, false);
        if (got > 0 && keep > 0) {
            int cost = (int) Math.min(eu, Math.ceil(got / (rfRate * keep)));
            removeEnergy(cost);
            wEuCharge += cost;
        }
    }

    // ------------------------------------------------------------------ the screen's numbers

    /** Closes a second: per-tick averages for the screen, the graph's next point. */
    private void window() {
        if (++ticksInWindow < 20) {
            return;
        }
        int n = ticksInWindow;
        ticksInWindow = 0;
        statEuPlus = (int) ((wEuIn + wEuConvIn) / n);
        statEuMinus = (int) ((wEuOut + wEuConvOut + wEuCharge) / n);
        statXPlus = clampInt((wXIn + wXConvIn) / n);
        statXMinus = clampInt((wXOut + wXConvOut + wXCharge) / n);
        statConvEu = (int) ((wEuConvOut - wEuConvIn) / n);
        statConvX = clampInt((wXConvIn - wXConvOut) / n);
        for (int s = 0; s < 6; s++) {
            statSide[s] = clampInt(wSide[s] / n);
            wSide[s] = 0;
        }
        double rate = rate();
        graphHead = (graphHead + 1) % GRAPH;
        graphEu[graphHead] = (int) ((wEuIn + wEuOut) / n);
        graphX[graphHead] = clampInt((wXIn + wXOut) / rate / n);
        wEuIn = wEuOut = wEuConvOut = wEuConvIn = wEuCharge = 0;
        wXIn = wXOut = wXConvIn = wXConvOut = wXCharge = 0;
    }

    private static int clampInt(double v) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    public int statEuPlus() {
        return statEuPlus;
    }

    public int statEuMinus() {
        return statEuMinus;
    }

    public int statXPlus() {
        return statXPlus;
    }

    public int statXMinus() {
        return statXMinus;
    }

    /** EU a tick the conversion took (+, EU -> X) or made (-). */
    public int statConvEu() {
        return statConvEu;
    }

    /** The other energy a tick the conversion made (+) or took (-). */
    public int statConvX() {
        return statConvX;
    }

    /** A face's flow a tick: + in, - out (EU or the other energy, as the face carries). */
    public int statSide(int s) {
        return statSide[s];
    }

    public int graphHead() {
        return graphHead;
    }

    public int graphEu(int i) {
        return graphEu[i];
    }

    public int graphX(int i) {
        return graphX[i];
    }

    // ---- the screen's sync (ContainerEnergyConverterSC) ----

    /** Pair, direction, priority, comparator as one int. */
    public int configFlags() {
        return (pair + 1) | direction << 3 | outPriority << 5 | comparatorOf << 6;
    }

    /** A face's mode, buffer, filter and output kind as one int. */
    public int faceFlags(int s) {
        return mode[s] | buf[s] << 2 | filter[s] << 4 | outKind[s] << 8;
    }

    /** The values the screen needs, in ContainerEnergyConverterSC's order. */
    public int[] syncValues() {
        int[] v = new int[SYNC_COUNT];
        long f = Math.round(foreign);
        v[0] = getEnergyStored();
        v[1] = (int) (f >>> 32);
        v[2] = (int) f;
        v[3] = powerFlags();
        v[4] = configFlags();
        for (int s = 0; s < 6; s++) {
            v[5 + s] = faceFlags(s);
            v[11 + s] = statSide[s];
        }
        v[17] = statEuPlus;
        v[18] = statEuMinus;
        v[19] = statXPlus;
        v[20] = statXMinus;
        v[21] = statConvEu;
        v[22] = statConvX;
        v[23] = graphHead;
        for (int i = 0; i < GRAPH; i++) {
            v[24 + i] = graphEu[i];
            v[24 + GRAPH + i] = graphX[i];
        }
        return v;
    }

    public static final int SYNC_COUNT = 24 + 2 * GRAPH;

    /** Client: one synced value arrived. */
    public void syncClient(int id, int value, int[] all) {
        switch (id) {
            case 0: setEnergyStoredClient(value); return;
            case 1:
            case 2: foreign = (double) (((long) all[1] << 32) | (all[2] & 0xFFFFFFFFL)); return;
            case 3: setPowerFlagsClient(value); return;
            case 4:
                pair = (value & 7) - 1;
                direction = (value >> 3) & 3;
                outPriority = (value >> 5) & 1;
                comparatorOf = (value >> 6) & 1;
                return;
            case 17: statEuPlus = value; return;
            case 18: statEuMinus = value; return;
            case 19: statXPlus = value; return;
            case 20: statXMinus = value; return;
            case 21: statConvEu = value; return;
            case 22: statConvX = value; return;
            case 23: graphHead = value; return;
            default:
        }
        if (id >= 5 && id < 11) {
            int s = id - 5;
            mode[s] = (byte) (value & 3);
            buf[s] = (byte) ((value >> 2) & 3);
            filter[s] = (byte) ((value >> 4) & F_ALL);
            outKind[s] = (value >> 8) & 3;
        } else if (id >= 11 && id < 17) {
            statSide[id - 11] = value;
        } else if (id >= 24 && id < 24 + GRAPH) {
            graphEu[id - 24] = value;
        } else if (id >= 24 + GRAPH && id < SYNC_COUNT) {
            graphX[id - 24 - GRAPH] = value;
        }
    }

    // ------------------------------------------------------------------ comparator

    public int comparatorLevel() {
        double v, cap;
        if (comparatorOf == COMP_X) {
            v = foreign;
            cap = foreignCapacity();
        } else {
            v = getEnergyStored();
            cap = euCap;
        }
        return v <= 0 || cap <= 0 ? 0 : Math.min(15, 1 + (int) (14 * v / cap));
    }

    // ------------------------------------------------------------------ inventory

    /** May the module in `slot` come out? Not an Energy Storage one while the buffers wouldn't fit; not the active pair's card with energy in it. */
    public int moduleRemoveBlock(int slot) {
        ItemStack s = getStackInSlot(slot);
        if (s == null || slot < FIRST_MODULE) {
            return 0;
        }
        if (s.getItem() instanceof ItemUpgradeSC && ItemUpgradeSC.typeOf(s) == UpgradeType.ENERGY_STORAGE) {
            int left = count(UpgradeType.ENERGY_STORAGE) - s.stackSize;
            long cap = ForeignEnergySC.euCapacity(Math.min(ForeignEnergySC.MAX_STORAGE, left));
            if (getEnergyStored() > cap || foreign > cap * rate() + 1e-6) {
                return 1;
            }
        }
        if (s.getItem() instanceof ItemConverterModuleSC) {
            Kind k = ItemConverterModuleSC.kindOf(s).energy();
            if (k != null && k == pairKind() && foreign > 0 && count(ItemConverterModuleSC.kindOf(s)) - s.stackSize <= 0) {
                return 2;
            }
        }
        return 0;
    }

    /** The EU buffer without the Energy Storage modules of `slot` (the refusal's message). */
    public int capacityWithout(int slot) {
        ItemStack s = getStackInSlot(slot);
        int left = count(UpgradeType.ENERGY_STORAGE) - (s == null ? 0 : s.stackSize);
        return ForeignEnergySC.euCapacity(Math.min(ForeignEnergySC.MAX_STORAGE, Math.max(0, left)));
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? inv[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack s = getStackInSlot(slot);
        if (s == null || moduleRemoveBlock(slot) != 0) {
            return null;
        }
        ItemStack out = s.splitStack(Math.min(amount, s.stackSize));
        if (s.stackSize <= 0) {
            setInventorySlotContents(slot, null);
        } else if (slot >= FIRST_MODULE) {
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
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        inv[slot] = stack;
        if (slot >= FIRST_MODULE) {
            modulesChanged();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.energyConverter";
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
        return slot == SLOT_CHARGE ? isChargeable(stack) : slot >= FIRST_MODULE && slot < SLOT_COUNT && isModule(stack);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[]{SLOT_CHARGE};
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot == SLOT_CHARGE && inv[SLOT_CHARGE] == null && isChargeable(stack);
    }

    /** Automation takes an item out of the charge slot once it's full (nothing more goes in). */
    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        if (slot != SLOT_CHARGE || stack == null) {
            return slot == SLOT_CHARGE;
        }
        ItemStack probe = stack.copy();
        if (TileEntityEnergyStorageSC.chargeItemAt(probe, 1000, Tier.max()) > 0) {
            return false;
        }
        return !(ForeignEnergySC.rfApi() && !ForeignEnergySC.testAllPresent && com.sc.compat.RfOpsSC.charge(stack, 1000, true) > 0);
    }

    // ------------------------------------------------------------------ the item: buffers, modules, settings

    /** Everything the dropped block keeps: both buffers, the pair and the settings, the modules. */
    public NBTTagCompound writeToItem() {
        NBTTagCompound t = new NBTTagCompound();
        if (getEnergyStored() > 0) {
            t.setInteger("EnergySC", getEnergyStored());
        }
        if (foreign > 0) {
            t.setDouble("ForeignSC", foreign);
        }
        t.setInteger("ConvPair", pair);
        writeSettings(t);
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = FIRST_MODULE; i < SLOT_COUNT; i++) {
            if (inv[i] != null) {
                NBTTagCompound s = inv[i].writeToNBT(new NBTTagCompound());
                s.setByte("Slot", (byte) i);
                list.appendTag(s);
            }
        }
        if (list.tagCount() > 0) {
            NBTTagCompound ups = new NBTTagCompound();
            ups.setTag("Items", list);
            t.setTag("UpgradesSC", ups);
        }
        modulesInItemTick = worldObj != null ? worldObj.getTotalWorldTime() : -1;
        return t;
    }

    /** The block is gone (its contents dropped or in the item): the slots emptied, no rules asked. */
    public void clearForBreak() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            inv[i] = null;
        }
    }

    /** The modules went into the item this tick (breakBlock doesn't drop them loose). */
    public boolean modulesInItem() {
        return modulesInItemTick != -1 && (worldObj == null || modulesInItemTick == worldObj.getTotalWorldTime());
    }

    /** Puts back what writeToItem saved (placement): the modules first - they make the room for the buffers. */
    public void readFromItem(NBTTagCompound t) {
        if (t == null) {
            return;
        }
        net.minecraft.nbt.NBTTagList list = t.getCompoundTag("UpgradesSC").getTagList("Items", 10);
        for (int k = 0; k < list.tagCount(); k++) {
            NBTTagCompound s = list.getCompoundTagAt(k);
            int i = s.getByte("Slot");
            if (i >= FIRST_MODULE && i < SLOT_COUNT) {
                inv[i] = ItemStack.loadItemStackFromNBT(s);
            }
        }
        refreshModules();
        if (t.hasKey("ConvPair")) {
            pair = Math.max(-1, Math.min(Kind.values().length - 1, t.getInteger("ConvPair")));
        }
        readSettings(t);
        restoreEnergy(Math.min(euCap, t.getInteger("EnergySC")));
        foreign = Math.max(0, t.getDouble("ForeignSC"));
        if (pairKind() == null) {
            foreign = 0;
        }
        kindsDirty = true;
        markDirty();
    }

    private void writeSettings(NBTTagCompound t) {
        t.setInteger("ConvDir", direction);
        t.setInteger("ConvPrio", outPriority);
        t.setInteger("ConvComp", comparatorOf);
        t.setByteArray("ConvMode", mode.clone());
        t.setByteArray("ConvBuf", buf.clone());
        t.setByteArray("ConvFilter", filter.clone());
    }

    private void readSettings(NBTTagCompound t) {
        direction = Math.max(0, Math.min(2, t.getInteger("ConvDir")));
        outPriority = Math.max(0, Math.min(1, t.getInteger("ConvPrio")));
        comparatorOf = Math.max(0, Math.min(1, t.getInteger("ConvComp")));
        byte[] m = t.getByteArray("ConvMode"), b = t.getByteArray("ConvBuf"), f = t.getByteArray("ConvFilter");
        for (int s = 0; s < 6; s++) {
            if (m.length == 6) {
                mode[s] = (byte) Math.max(0, Math.min(2, m[s]));
            }
            if (b.length == 6) {
                buf[s] = (byte) Math.max(0, Math.min(2, b[s]));
            }
            if (f.length == 6) {
                filter[s] = (byte) (f[s] & F_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String k = "Slot" + i;
            inv[i] = nbt.hasKey(k) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(k)) : null;
        }
        refreshModules();
        foreign = Math.max(0, nbt.getDouble("ForeignSC"));
        pair = nbt.hasKey("ConvPair") ? Math.max(-1, Math.min(Kind.values().length - 1, nbt.getInteger("ConvPair"))) : -1;
        readSettings(nbt);
        ForgeDirection f = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        facing = f == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : f;
        if (nbt.hasKey("ConvOutKinds")) {                         // the client: what the faces send (the screen's and the texture's)
            int ok = nbt.getInteger("ConvOutKinds");
            for (int s = 0; s < 6; s++) {
                outKind[s] = (ok >> 2 * s) & 3;
            }
        }
        kindsDirty = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (inv[i] != null) {
                nbt.setTag("Slot" + i, inv[i].writeToNBT(new NBTTagCompound()));
            }
        }
        nbt.setDouble("ForeignSC", foreign);
        nbt.setInteger("ConvPair", pair);
        writeSettings(nbt);
        nbt.setInteger("Facing", facing.ordinal());
        int ok = 0;
        for (int s = 0; s < 6; s++) {
            ok |= outKind[s] << 2 * s;
        }
        nbt.setInteger("ConvOutKinds", ok);
    }

    /** The NBT id goes by TILE_ID whichever class this is (with or without RF). */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ------------------------------------------------------------------ for the tests

    /** Self-test: the module numbers without a world. */
    public void refreshForTest() {
        refreshModules();
        fixPairForTest();
    }

    private void fixPairForTest() {
        fixPair();
    }

    /** Self-test / world test: the other buffer set directly. */
    public void setForeignForTest(double v) {
        foreign = Math.max(0, v);
    }

    public void setPairForTest(int kind) {
        pair = kind;
        kindsDirty = true;
    }

    /** Self-test: the energy nets are to be told about the faces again (next tick). */
    public boolean netRefreshDueForTest() {
        return netRefreshDue;
    }

    /** World test: an EU amount straight into the buffer. */
    public void addEnergyForTest(int eu) {
        addEnergy(eu);
    }
}
