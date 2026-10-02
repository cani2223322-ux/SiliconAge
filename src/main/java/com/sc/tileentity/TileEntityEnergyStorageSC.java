package com.sc.tileentity;

import com.sc.energy.Tier;
import com.sc.machine.UpgradeType;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemWeaponSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Energy storage (LV..EV): a big buffer that takes energy on five faces and sends it out of its
 * front face only (the face turned to the player when it was placed), at its tier's voltage -
 * same rules for the mod's own net and for IC2. A slot charges a weapon or an armor piece of the
 * block's tier or lower from the stored energy.
 * Broken and picked up, it keeps its charge in the item (BlockEnergyStorageSC).
 */
@cpw.mods.fml.common.Optional.Interface(iface = "ic2.api.energy.tile.IMultiEnergySource", modid = "industrialupgrade")
public class TileEntityEnergyStorageSC extends TileEntityEnergyBase implements net.minecraft.inventory.ISidedInventory, ic2.api.energy.tile.IMultiEnergySource {

    /** Slots: 0 charges an item, 1 takes the energy out of one, 2..5 the upgrades. */
    public static final int SLOT_CHARGE = 0, SLOT_DISCHARGE = 1, FIRST_UPGRADE_SLOT = 2, UPGRADE_SLOTS = 4,
            FIRST_EXTRA_CHARGE = FIRST_UPGRADE_SLOT + UPGRADE_SLOTS, MAX_CHARGE_SLOTS = 4,
            SLOT_COUNT = FIRST_EXTRA_CHARGE + MAX_CHARGE_SLOTS - 1;
    /** Capacity upgrade: +25% of the tier's capacity each. */
    public static final int CAPACITY_PERCENT_PER_UPGRADE = 25;
    /** Overdrive: one more packet of the output voltage a tick each, at most this many. */
    public static final int MAX_EXTRA_PACKETS = 4;

    /**
     * TODO(design doc has no storage blocks): capacities per tier, LV..SV (XV close to the int ceiling,
     * SV right on it - capacity upgrades can't raise it any more, getMaxEnergyStored clamps to int).
     */
    public static final int[] CAPACITY = {40000, 300000, 4000000, 40000000, 300000000, 1000000000, 2000000000, Integer.MAX_VALUE};

    private ForgeDirection facing = ForgeDirection.SOUTH;
    private ItemStack chargeSlot;
    /** Charge slots 2..4 (inventory slots 6..8) - how many are open goes by the tier (chargeSlots()). */
    private final ItemStack[] extraCharge = new ItemStack[MAX_CHARGE_SLOTS - 1];
    private ItemStack dischargeSlot;
    private final ItemStack[] upgradeSlots = new ItemStack[UPGRADE_SLOTS];
    /** Set once the upgrades went into the dropped item, so breakBlock doesn't drop them loose too. */
    private boolean upgradesInItem;
    /** World tick upgradesForItem() ran in: a getDrops() from another tick (another mod asking) mustn't stick. */
    private long upgradesInItemTick = -1;
    /** The comparator level last announced to the neighbours. */
    private int lastComparator = -1;
    /** EU gained (+) or lost (-) per tick, averaged over the last second - shown on the screen. */
    private int flowPerTick;
    private int energyAtWindowStart = -1;

    /**
     * Output Splitter: the extra output faces, in the order the wrench set them (never `facing`) -
     * as many count as there are modules, the last ones go when a module comes out. NBT "OutFaces"
     * (the mask with the front; worlds from before it: the front only) and "OutOrder".
     */
    private final java.util.List<ForgeDirection> extraOut = new java.util.ArrayList<ForgeDirection>();
    /** EU of upkeep the extra faces owe, not yet whole (sentOut). */
    private double upkeepDue;
    /** Upkeep of an extra output face: this percent of what goes out of it. */
    public static final int UPKEEP_PERCENT = 1;
    /** toggleExtraOutput's answers. */
    public static final int OUT_ADDED = 1, OUT_REMOVED = 2, OUT_MAIN = 3, OUT_NO_MODULE = 4, OUT_FULL = 5;

    /** Adaptive Transformer: the tier it gives now (null: none) and what holds it there (ADAPT_*). */
    private Tier adaptiveTier;
    private int adaptiveWhy;
    public static final int ADAPT_NONE = 0, ADAPT_CABLE = 1, ADAPT_CONSUMER = 2, ADAPT_CEILING = 3;
    /** Set when the adaptive tier changed: IC2 is told on the next tick, never in the middle of a net's own work. */
    private boolean ic2RefreshDue;
    /** False until the first tick worked the adaptive tier out (it isn't saved). */
    private boolean adaptiveChecked;

    /**
     * Cached (refreshModules): the Output Splitters in the slots, whether an Adaptive Transformer
     * is in, and the output faces (null: work them out again; kept with the tier they were for).
     */
    private int splitterModules;
    private boolean adaptiveModule;
    private ForgeDirection[] outFacesCache;
    private Tier outFacesTier;
    /** Inside upgradesChanged: its own markDirty() calls don't start it again. */
    private boolean modulesChanging;

    public TileEntityEnergyStorageSC() {
        super(Tier.LV);
    }

    public void setStorageTier(Tier tier) {
        setTier(tier);
    }

    /** Saved before the tier went into the NBT ("TierSC"): the tier comes from the block's metadata on the first tick. */
    private boolean tierFromMeta;

    private void fixTierFromMeta() {
        tierFromMeta = false;
        if (getBlockType() instanceof com.sc.block.BlockEnergyStorageSC) {
            setTier(com.sc.block.BlockEnergyStorageSC.tierFor(getBlockMetadata()));
            refreshEnergyNet();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** A storage item's capacity: its tier and the Energy Storage upgrades it carries (getDrops' "UpgradesSC"). */
    public static int capacityOf(Tier tier, net.minecraft.nbt.NBTTagCompound ups) {
        int n = 0;
        net.minecraft.nbt.NBTTagList list = ups == null ? null : ups.getTagList("Items", 10);
        for (int k = 0; list != null && k < list.tagCount(); k++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(k));
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == UpgradeType.ENERGY_STORAGE) {
                n += s.stackSize;
            }
        }
        long cap = capacityOf(tier) * (100L + (long) CAPACITY_PERCENT_PER_UPGRADE * Math.min(n, UpgradeType.MAX_EFFECTIVE)) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public static int capacityOf(Tier tier) {
        return com.sc.util.ConfigSC.scale(CAPACITY[tier.ordinal()], com.sc.util.ConfigSC.storageCapacity, 1);
    }

    @Override
    public int getMaxEnergyStored() {
        long cap = capacityOf(getTier()) * (100L + (long) CAPACITY_PERCENT_PER_UPGRADE * upgradeCount(UpgradeType.ENERGY_STORAGE)) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    // ---- upgrades: Transformer (output a tier up), Energy Storage (+25%), Overdrive (+1 packet a tick) ----

    public static boolean acceptsUpgrade(ItemStack s) {
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemUpgradeSC)) {
            return false;
        }
        UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
        return t == UpgradeType.TRANSFORMER || t == UpgradeType.ENERGY_STORAGE || (t == UpgradeType.OVERDRIVE && overdriveWorks())
                || (t == UpgradeType.OUTPUT_SPLITTER && overdriveWorks()) || t == UpgradeType.ADAPTIVE_TRANSFORMER;
    }

    /**
     * What this storage's upgrade slots take: acceptsUpgrade(), the Output Splitter from HV up, and
     * the two storage modules not in a charge pad (its top is where one stands).
     */
    public boolean acceptsUpgradeHere(ItemStack s) {
        if (!acceptsUpgrade(s)) {
            return false;
        }
        UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
        if (t.storageOnly() && this instanceof TileEntityChargePadSC) {
            return false;
        }
        if (t == UpgradeType.ENERGY_STORAGE && capacityOf(getTier()) == Integer.MAX_VALUE) {
            return false;                       // already at the ceiling (SV): the upgrade would be lost for nothing
        }
        return t != UpgradeType.OUTPUT_SPLITTER || getTier().ordinal() >= Tier.HV.ordinal();
    }

    /**
     * Overdrive's extra packets reach IC2's net only through Industrial Upgrade's IMultiEnergySource:
     * under IC2 without it the module would do nothing, so a storage doesn't take it (ones already
     * in stay put). Without IC2 the mod's own net honours it.
     */
    public static boolean overdriveWorks() {
        return !cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)
                || cpw.mods.fml.common.Loader.isModLoaded(IU_MODID);
    }

    /** Industrial Upgrade's modid (as in the @Optional.Interface above). */
    public static final String IU_MODID = "industrialupgrade";

    /** The capacity with the Energy Storage upgrades of upgrade slot `slot` taken out. */
    public int capacityWithout(int slot) {
        ItemStack out = getStackInSlot(slot);
        int n = 0;
        for (ItemStack s : upgradeSlots) {
            if (s != null && s != out && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == UpgradeType.ENERGY_STORAGE) {
                n += s.stackSize;
            }
        }
        long cap = capacityOf(getTier()) * (100L + (long) CAPACITY_PERCENT_PER_UPGRADE * Math.min(n, UpgradeType.MAX_EFFECTIVE)) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    /**
     * May the upgrade in `slot` come out? Not an Energy Storage one while the charge wouldn't fit
     * the capacity left without it (the energy would just vanish).
     */
    public boolean canRemoveUpgrade(int slot) {
        ItemStack s = getStackInSlot(slot);
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemUpgradeSC) || com.sc.item.ItemUpgradeSC.typeOf(s) != UpgradeType.ENERGY_STORAGE) {
            return true;
        }
        return getEnergyStored() <= capacityWithout(slot);
    }

    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (ItemStack s : upgradeSlots) {
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        int cap = type == UpgradeType.OVERDRIVE ? MAX_EXTRA_PACKETS
                : type == UpgradeType.OUTPUT_SPLITTER ? UpgradeType.MAX_OUTPUT_SPLITTERS
                : type == UpgradeType.ADAPTIVE_TRANSFORMER ? 1 : UpgradeType.MAX_EFFECTIVE;
        return Math.min(n, cap);
    }

    /** Transformer upgrades send the output out a tier higher each (up to Tier.outputRaiseCeiling()). */
    public Tier baseOutputTier() {
        return getTier().raisedOutput(upgradeCount(UpgradeType.TRANSFORMER));
    }

    /** The Transformers' tier, or the Adaptive Transformer's when that is higher (it never passes the neighbour). */
    @Override
    public Tier outputTier() {
        Tier base = baseOutputTier();
        Tier a = getAdaptiveTier();
        return a != null && a.ordinal() > base.ordinal() ? a : base;
    }

    /** One packet a tick on each output face, and one more per Overdrive upgrade on each. */
    @Override
    public int packetsPerTick() {
        return packetsPerFace() * outputFaces().length;
    }

    @Override
    public int packetsPerFace() {
        return 1 + upgradeCount(UpgradeType.OVERDRIVE);
    }

    // ---- Output Splitter: extra output faces (HV and up; not under IC2 without Industrial Upgrade) ----

    /** The splitters that work here: none below HV, in a charge pad, or under IC2 without IU (one packet a tile there). */
    public int outputSplitters() {
        if (!overdriveWorks() || getTier().ordinal() < Tier.HV.ordinal() || this instanceof TileEntityChargePadSC) {
            return 0;
        }
        return splitterModules;
    }

    /** Re-reads the module cache from the slots (the output faces worked out again on the next ask). */
    private void refreshModules() {
        splitterModules = upgradeCount(UpgradeType.OUTPUT_SPLITTER);
        adaptiveModule = upgradeCount(UpgradeType.ADAPTIVE_TRANSFORMER) > 0;
        outFacesCache = null;
    }

    /** The slots no longer match the module cache (a stack changed in place - a part taken out, merged in). */
    private boolean modulesStale() {
        return upgradeCount(UpgradeType.OUTPUT_SPLITTER) != splitterModules
                || (upgradeCount(UpgradeType.ADAPTIVE_TRANSFORMER) > 0) != adaptiveModule;
    }

    /** Any change to the inventory comes here (slots changed in place too): modules in or out - upgradesChanged(). */
    @Override
    public void markDirty() {
        super.markDirty();
        if (!modulesChanging && modulesStale()) {
            upgradesChanged();
        }
    }

    /** The extra output faces that work now (the first ones set, as many as there are splitters). */
    public int extraOutputCount() {
        return Math.min(extraOut.size(), outputSplitters());
    }

    /**
     * The wrench on face `face` (sneak + left-click): makes it an extra output, or an input again.
     * @return OUT_ADDED / OUT_REMOVED, OUT_MAIN (the front - always an output), OUT_NO_MODULE, OUT_FULL
     */
    public int toggleExtraOutput(ForgeDirection face) {
        if (face == null || face == ForgeDirection.UNKNOWN || face == facing) {
            return OUT_MAIN;
        }
        if (extraOut.remove(face)) {
            outputsChanged();
            return OUT_REMOVED;
        }
        int n = outputSplitters();
        if (n <= 0) {
            return OUT_NO_MODULE;
        }
        if (extraOut.size() >= n) {
            return OUT_FULL;
        }
        extraOut.add(face);
        outputsChanged();
        return OUT_ADDED;
    }

    /** A splitter came out: the extra faces set last go back to inputs. */
    private void trimExtraOutputs() {
        boolean changed = false;
        while (extraOut.size() > outputSplitters()) {
            extraOut.remove(extraOut.size() - 1);
            changed = true;
        }
        if (changed) {
            outputsChanged();
        }
    }

    private void outputsChanged() {
        outFacesCache = null;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            refreshEnergyNet();          // IC2 caches the faces a tile emits / accepts on - re-announce it
            recomputeAdaptive();         // the faces it looks at changed
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);   // the extra terminals render on the client
        }
    }

    /** Upkeep: UPKEEP_PERCENT of what leaves through an extra face (IC2 doesn't say which face: their share). */
    @Override
    protected void sentOut(ForgeDirection face, int eu) {
        int extras = extraOutputCount();
        if (extras <= 0 || eu <= 0) {
            return;
        }
        double share = face == ForgeDirection.UNKNOWN ? (double) eu * extras / (extras + 1) : face != facing ? eu : 0;
        upkeepDue += share * UPKEEP_PERCENT / 100.0;
        int fee = (int) upkeepDue;
        if (fee > 0) {
            upkeepDue -= fee;
            removeEnergy(fee);
        }
    }

    // ---- Adaptive Transformer: up to what the neighbour on the output face takes, at most 2 tiers over ----

    public boolean hasAdaptive() {
        return adaptiveModule && !(this instanceof TileEntityChargePadSC);
    }

    /** The highest tier the Adaptive Transformer gives: the storage's own + 2, at most Tier.max(). */
    public Tier adaptiveCeiling() {
        Tier[] tiers = Tier.values();
        return tiers[Math.min(tiers.length - 1, getTier().ordinal() + UpgradeType.ADAPTIVE_MAX_RAISE)];
    }

    /** The tier the Adaptive Transformer gives now (null: none, or nothing to go by). */
    public Tier getAdaptiveTier() {
        return hasAdaptive() ? adaptiveTier : null;
    }

    /** What holds the adaptive tier where it is: ADAPT_CABLE, ADAPT_CONSUMER, ADAPT_CEILING or ADAPT_NONE. */
    public int getAdaptiveWhy() {
        return adaptiveWhy;
    }

    /**
     * Sets the adaptive tier from the weakest output neighbour's limit (`why`: ADAPT_CABLE or
     * ADAPT_CONSUMER); null: no neighbour to go by - no raise. @return whether outputTier() changed
     */
    public boolean applyAdaptiveLimit(Tier limit, int why) {
        Tier before = outputTier();
        Tier ceiling = adaptiveCeiling();
        if (limit == null) {
            adaptiveTier = null;
            adaptiveWhy = ADAPT_NONE;
        } else if (limit.ordinal() > ceiling.ordinal()) {
            adaptiveTier = ceiling;
            adaptiveWhy = ADAPT_CEILING;
        } else {
            adaptiveTier = limit;
            adaptiveWhy = why;
        }
        return outputTier() != before;
    }

    /** A neighbour's limit for the Adaptive Transformer: the tier it takes (null: nothing safe - no raise) and what it is. */
    public static final class Limit {
        public final Tier tier;
        public final int why;

        Limit(Tier tier, int why) {
            this.tier = tier;
            this.why = why;
        }
    }

    /**
     * What `te`, beside this storage's face `face`, takes at most - never more than the weakest
     * thing behind it: the mod's cable (bundle) its tier, but no more than the weakest consumer on
     * the mod's network behind it (cableLimit; no network known - nothing safe); the mod's consumer
     * its input tier (any voltage: XV); an IC2 cable nothing safe (what hangs on it can't be seen);
     * an IC2 sink its sink tier (none given - nothing safe); another IC2 energy acceptor nothing safe.
     * `self`: this storage, left out of its own cable's network.
     * @return null when it takes no energy from there (it doesn't count)
     */
    public static Limit neighbourLimit(net.minecraft.tileentity.TileEntity te, ForgeDirection face, TileEntityEnergyBase self) {
        if (te == null) {
            return null;
        }
        if (te instanceof TileEntityConduitBundleSC) {
            com.sc.energy.CableType c = ((TileEntityConduitBundleSC) te).getCable();
            return c == null ? null
                    : cableLimit(c.tier, com.sc.energy.EnergyNetSC.instance().weakestSinkOn((TileEntityConduitBundleSC) te, self));
        }
        if (te instanceof TileEntityCableSC) {
            com.sc.energy.CableType c = ((TileEntityCableSC) te).getCableType();
            return c == null ? null : new Limit(null, ADAPT_CABLE);     // an old cable, about to turn into a bundle: no network yet
        }
        if (te instanceof TileEntityEnergyBase) {
            TileEntityEnergyBase e = (TileEntityEnergyBase) te;
            if (!e.isEnergySink() || !e.acceptsFrom(face.getOpposite())) {
                return null;
            }
            return new Limit(e.acceptsAnyVoltage() ? Tier.max() : e.inputTier(), ADAPT_CONSUMER);
        }
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            return Ic2Limit.of(te);
        }
        return null;
    }

    /**
     * The mod's cable of tier `cable` with `weakest` the lowest input tier on its network
     * (EnergyNetSC.weakestSinkOn; null: no network known or nobody on it - nothing to go by, no raise).
     */
    public static Limit cableLimit(Tier cable, Tier weakest) {
        if (weakest == null) {
            return new Limit(null, ADAPT_NONE);
        }
        return weakest.ordinal() < cable.ordinal() ? new Limit(weakest, ADAPT_CONSUMER) : new Limit(cable, ADAPT_CABLE);
    }

    /**
     * An IC2 neighbour: a conductor - nothing safe (the machines behind it can't be seen); a sink -
     * its sink tier (0 or less: none given - nothing safe); another acceptor - nothing safe.
     * @return null when it is none of them (it doesn't count)
     */
    public static Limit ic2Limit(boolean conductor, boolean sink, int sinkTier, boolean acceptor) {
        if (conductor) {
            return new Limit(null, ADAPT_NONE);
        }
        if (sink) {
            return sinkTier <= 0 ? new Limit(null, ADAPT_NONE) : new Limit(Tier.fromIc2Tier(sinkTier), ADAPT_CONSUMER);
        }
        return acceptor ? new Limit(null, ADAPT_NONE) : null;
    }

    /** Kept apart so IC2's API is only loaded when IC2 is. */
    private static final class Ic2Limit {
        static Limit of(net.minecraft.tileentity.TileEntity te) {
            boolean conductor = te instanceof ic2.api.energy.tile.IEnergyConductor;
            boolean sink = !conductor && te instanceof ic2.api.energy.tile.IEnergySink;
            return ic2Limit(conductor, sink, sink ? ((ic2.api.energy.tile.IEnergySink) te).getSinkTier() : 0,
                    te instanceof ic2.api.energy.tile.IEnergyAcceptor);
        }
    }

    /**
     * Looks at the neighbours on the output faces (server: every tick with the module, when one
     * changes, when the faces or upgrades do, when the mod's networks are rebuilt): the weakest one
     * sets the tier - one tier a tile, as IC2 and the mod's net both want.
     */
    public void recomputeAdaptive() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        Tier limit = null;
        int why = ADAPT_NONE;
        boolean blocked = false;
        if (hasAdaptive()) {
            for (ForgeDirection d : outputFaces()) {
                int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
                if (!worldObj.blockExists(x, y, z)) {
                    blocked = true;              // not loaded: can't see what's there - no raise
                    break;
                }
                Limit l = neighbourLimit(worldObj.getTileEntity(x, y, z), d, this);
                if (l == null) {
                    continue;
                }
                if (l.tier == null) {
                    blocked = true;
                    break;
                }
                if (limit == null || l.tier.ordinal() < limit.ordinal()) {
                    limit = l.tier;
                    why = l.why;
                }
            }
        }
        if (applyAdaptiveLimit(blocked ? null : limit, why)) {
            // re-announced on the next tick (IC2 caches the source tier; the mod's net reads it live)
            ic2RefreshDue = cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID);
            markDirty();
        }
    }

    /** The mod's networks were rebuilt: a consumer may have joined one behind a cable - look again before it gets energy. */
    @Override
    public void energyNetRebuilt() {
        if (hasAdaptive()) {
            recomputeAdaptive();
        }
    }

    /** A neighbouring block changed (BlockEnergyStorageSC). */
    public void neighbourChanged() {
        recomputeAdaptive();
    }

    /** The adaptive tier and its reason as one int for the screen's sync. */
    public int adaptiveSync() {
        return (adaptiveTier == null ? 0 : adaptiveTier.ordinal() + 1) | adaptiveWhy << 4;
    }

    public void setAdaptiveClient(int v) {
        int t = v & 15;
        adaptiveTier = t == 0 || t > Tier.values().length ? null : Tier.values()[t - 1];
        adaptiveWhy = (v >> 4) & 15;
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

    /** The upgrades as an item NBT compound for the dropped block (null when empty) - see TileEntityMachineSC. */
    public NBTTagCompound upgradesForItem() {
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgradeSlots[i] != null) {
                NBTTagCompound t = upgradeSlots[i].writeToNBT(new NBTTagCompound());
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

    public boolean upgradesInItem() {
        return upgradesInItem && (worldObj == null || upgradesInItemTick == worldObj.getTotalWorldTime());
    }

    /** Puts back what upgradesForItem() saved (on placement, before the charge is loaded). */
    public void loadUpgradesFromItem(NBTTagCompound tag) {
        net.minecraft.nbt.NBTTagList list = tag == null ? null : tag.getTagList("Items", 10);
        for (int k = 0; list != null && k < list.tagCount(); k++) {
            NBTTagCompound t = list.getCompoundTagAt(k);
            int i = t.getByte("Slot");
            if (i >= 0 && i < UPGRADE_SLOTS) {
                upgradeSlots[i] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        markDirty();
    }

    // ---- the comparator: 0 empty, 1..15 by how full ----

    public int comparatorLevel() {
        int e = getEnergyStored();
        return e <= 0 ? 0 : Math.min(15, 1 + (int) (14L * e / Math.max(1, getMaxEnergyStored())));
    }

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection facing) {
        boolean changed = this.facing != facing;
        this.facing = facing;
        extraOut.remove(facing);      // the front is an output anyway
        outFacesCache = null;
        if (changed) {
            refreshEnergyNet();       // IC2 caches the faces a tile emits / accepts on - re-announce it
            recomputeAdaptive();
        }
    }

    // ---- faces: in on five, out on the front ----

    @Override
    public boolean isEnergySink() {
        return true;
    }

    @Override
    public boolean isEnergySource() {
        return true;
    }

    @Override
    public boolean acceptsFrom(ForgeDirection side) {
        return !isOutputFace(side);
    }

    /** The front, and the extra faces of the Output Splitters. */
    @Override
    public ForgeDirection[] outputFaces() {
        if (outFacesCache != null && outFacesTier == getTier()) {
            return outFacesCache;                    // asked many times a tick (isOutputFace) - shared, never changed
        }
        int extras = extraOutputCount();
        ForgeDirection[] out = new ForgeDirection[1 + extras];
        out[0] = facing;
        for (int i = 0; i < extras; i++) {
            out[i + 1] = extraOut.get(i);
        }
        outFacesTier = getTier();                    // below HV the splitters don't work
        outFacesCache = out;
        return out;
    }

    /** Loaded charge from a placed item (BlockEnergyStorageSC.onBlockPlacedBy). */
    public void setStoredFromItem(int eu) {
        addEnergy(Math.max(0, eu));
    }

    public int getFlowPerTick() {
        return flowPerTick;
    }

    public void setFlowClient(int flow) {
        flowPerTick = flow;
    }

    @Override
    public int offerableEnergy() {
        return switchedOn() ? super.offerableEnergy() : 0;
    }

    @Override
    public int demandedEnergy() {
        return switchedOn() ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }

    /** Switched off, it gives nothing to sneak-click charging either. */
    @Override
    public int extractForItemCharging(int max) {
        return switchedOn() ? super.extractForItemCharging(max) : 0;
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
        if (tierFromMeta) {
            fixTierFromMeta();
        }
        if (!adaptiveChecked || hasAdaptive()) {        // every tick with the module (a few lookups): a weaker consumer counts at once
            adaptiveChecked = true;
            recomputeAdaptive();
        }
        if (ic2RefreshDue) {
            ic2RefreshDue = false;
            refreshEnergyNet();       // the adaptive tier changed: IC2 caches the source tier
        }
        if (switchedOn()) {                                          // off: no charging or emptying items either
            chargeRound();
            dischargeRound();
        } else {
            switchedOff();
        }
        if (worldObj.getTotalWorldTime() % 10 == 0) {
            int level = comparatorLevel();
            if (level != lastComparator) {
                lastComparator = level;
                worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());   // tell the comparators round it
            }
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            if (energyAtWindowStart >= 0) {
                flowPerTick = (getEnergyStored() - energyAtWindowStart) / 20;
            }
            energyAtWindowStart = getEnergyStored();
        }
    }

    /**
     * Charge slots by the tier: LV and MV one, HV and EV two, IV three, QV and XV four - each
     * charging on its own at the tier's voltage, so a bigger storage charges more at once.
     */
    public int chargeSlots() {
        return chargeSlotsFor(getTier());
    }

    /** Charge slots by tier (the handbook lists them): LV / MV 1, HV / EV 2, IV 3, QV / XV 4. */
    public static int chargeSlotsFor(Tier tier) {
        switch (tier) {
            case LV:
            case MV: return 1;
            case HV:
            case EV: return 2;
            case IV: return 3;
            default: return 4;
        }
    }

    /** The self-test's handle on an extra face's upkeep (sentOut is protected). */
    public void sentOutForTest(ForgeDirection face, int eu) {
        sentOut(face, eu);
    }

    /** The self-test's handle on one charging round (chargeRound is protected). */
    public void chargeRoundForTest() {
        chargeRound();
    }

    /** The inventory slot of charge slot k (0 .. chargeSlots()-1). */
    public static int chargeSlotIndex(int k) {
        return k == 0 ? SLOT_CHARGE : FIRST_EXTRA_CHARGE + k - 1;
    }

    /** One tick's charging: each open slot's item, at most one packet of the tier's voltage each. */
    /** Called each tick while switched off (the charge pad puts its lit top out). */
    protected void switchedOff() {
    }

    protected void chargeRound() {
        boolean any = false;
        for (int k = 0; k < chargeSlots(); k++) {
            int taken = chargeItem(getStackInSlot(chargeSlotIndex(k)), Math.min(getEnergyStored(), getTier().getVoltage()));
            if (taken > 0) {
                removeEnergy(taken);
                any = true;
            }
        }
        if (any) {
            markDirty();
        }
    }

    /**
     * One tick's discharging: energy out of the discharge slot's item into the buffer, at most one
     * packet of the tier's voltage and what room there is.
     */
    protected void dischargeRound() {
        int room = getMaxEnergyStored() - getEnergyStored();
        if (dischargeSlot == null || room <= 0) {
            return;
        }
        int got = dischargeItem(dischargeSlot, Math.min(room, getTier().getVoltage()));
        if (got > 0) {
            addEnergy(got);
            markDirty();
        }
    }

    /** Takes up to `max` EU out of an item: the mod's suits, blades and drills, and with IC2 any battery. @return EU got */
    public int dischargeItem(ItemStack s, int max) {
        if (s == null || max <= 0) {
            return 0;
        }
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            return com.sc.item.ItemArmorSC.discharge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            return com.sc.item.ItemBladeSC.discharge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            return com.sc.item.ItemDrillSC.discharge(s, max);
        }
        if (com.sc.item.ItemBatterySC.isBattery(s)) {
            return com.sc.item.ItemBatterySC.discharge(s, max);
        }
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            return Ic2Charge.discharge(s, max, getTier().toIc2Tier());
        }
        return 0;
    }

    /** What goes in the discharge slot: something the storage can take energy out of. */
    public static boolean isDischargeable(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.getItem() instanceof com.sc.item.ItemArmorSC || stack.getItem() instanceof com.sc.item.ItemBladeSC
                || stack.getItem() instanceof com.sc.item.ItemDrillSC || com.sc.item.ItemBatterySC.isBattery(stack)) {
            return true;
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) && Ic2Charge.providesEnergy(stack);
    }

    /** Charges the slot's item up to `max` EU (the caller takes them out of the buffer). @return EU taken */
    protected int chargeSlotItem(int max) {
        int start = max;
        for (int k = 0; k < chargeSlots() && max > 0; k++) {       // the charge pad: one budget across its slots
            max -= chargeItem(getStackInSlot(chargeSlotIndex(k)), max);
        }
        return start - max;
    }

    /**
     * Charges one item up to `max` EU if it is chargeable here: only items of this block's tier
     * or lower (as IC2 chargers) - the mod's suits, blades and weapons, and with IC2 any IC2
     * electric item. @return EU taken
     */
    public int chargeItem(ItemStack s, int max) {
        if (s == null || max <= 0 || !tierAllows(s)) {
            return 0;
        }
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            return com.sc.item.ItemArmorSC.charge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            return com.sc.item.ItemBladeSC.charge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            return com.sc.item.ItemDrillSC.charge(s, max);
        }
        if (s.getItem() instanceof ItemWeaponSC) {
            return ItemWeaponSC.charge(s, ((ItemWeaponSC) s.getItem()).getType(), max);
        }
        if (com.sc.item.ItemWrenchSC.isElectric(s)) {
            return com.sc.item.ItemWrenchSC.charge(s, max);
        }
        if (com.sc.item.ItemBatterySC.isBattery(s)) {
            return com.sc.item.ItemBatterySC.charge(s, max);
        }
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            return Ic2Charge.charge(s, max, getTier().toIc2Tier());
        }
        return 0;
    }

    /** The mod's own item is at most this block's tier (IC2 items are checked by IC2's charge()). */
    public boolean tierAllows(ItemStack s) {
        Tier block = getTier();
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            return ((com.sc.item.ItemArmorSC) s.getItem()).getSuit().chargeTier.ordinal() <= block.ordinal();
        }
        if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            return com.sc.item.ItemBladeSC.typeOf(s).chargeTier.ordinal() <= block.ordinal();
        }
        if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            return com.sc.item.ItemDrillSC.typeOf(s).chargeTier.ordinal() <= block.ordinal();
        }
        if (s.getItem() instanceof ItemWeaponSC) {
            return ((ItemWeaponSC) s.getItem()).getType().tier.ordinal() <= block.ordinal();
        }
        if (com.sc.item.ItemWrenchSC.isElectric(s)) {
            return com.sc.item.ItemWrenchSC.tierOf(s).chargeTier.ordinal() <= block.ordinal();
        }
        if (com.sc.item.ItemBatterySC.isBattery(s)) {
            return com.sc.item.ItemBatterySC.tierOf(s).ordinal() <= block.ordinal();
        }
        return true;
    }

    /** Kept apart so IC2's API is only loaded when IC2 is. */
    private static final class Ic2Charge {
        static int charge(ItemStack s, int max, int tier) {
            if (!(s.getItem() instanceof ic2.api.item.IElectricItem) || ic2.api.item.ElectricItem.manager == null) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.charge(s, max, tier, false, false);
        }

        static int discharge(ItemStack s, int max, int tier) {
            if (!(s.getItem() instanceof ic2.api.item.IElectricItem) || ic2.api.item.ElectricItem.manager == null
                    || !((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s)) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.discharge(s, max, tier, false, true, false);
        }

        static boolean providesEnergy(ItemStack s) {
            return s.getItem() instanceof ic2.api.item.IElectricItem && ((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s);
        }
    }

    // ---- the charge slot ----

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == SLOT_CHARGE) {
            return chargeSlot;
        }
        if (slot == SLOT_DISCHARGE) {
            return dischargeSlot;
        }
        if (slot >= FIRST_EXTRA_CHARGE && slot < SLOT_COUNT) {
            return extraCharge[slot - FIRST_EXTRA_CHARGE];
        }
        return slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE ? upgradeSlots[slot - FIRST_UPGRADE_SLOT] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack s = getStackInSlot(slot);
        if (s == null || !canRemoveUpgrade(slot)) {        // a capacity upgrade holding energy stays
            return null;
        }
        ItemStack out = s.splitStack(Math.min(amount, s.stackSize));   // never more than the slot holds
        if (s.stackSize <= 0) {
            setInventorySlotContents(slot, null);
        } else if (slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE) {
            upgradesChanged();                             // e.g. one splitter of two out
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
        if (slot == SLOT_CHARGE) {
            chargeSlot = stack;
        } else if (slot == SLOT_DISCHARGE) {
            dischargeSlot = stack;
        } else if (slot >= FIRST_EXTRA_CHARGE && slot < SLOT_COUNT) {
            extraCharge[slot - FIRST_EXTRA_CHARGE] = stack;
        } else if (slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE) {
            upgradeSlots[slot - FIRST_UPGRADE_SLOT] = stack;
            if (worldObj != null && !worldObj.isRemote) {
                refreshEnergyNet();   // a transformer changes the output tier IC2 cached
            }
            upgradesChanged();
        } else {
            return;
        }
        markDirty();
    }

    /** The upgrades changed: extra outputs past the splitters go, the adaptive tier is looked at again. */
    private void upgradesChanged() {
        if (modulesChanging) {
            return;
        }
        modulesChanging = true;
        try {
            refreshModules();              // both sides: the screen reads the faces and the module too
            if (worldObj != null && worldObj.isRemote) {
                return;                    // the client gets both with the tile's NBT / the screen's sync
            }
            trimExtraOutputs();
            recomputeAdaptive();
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        } finally {
            modulesChanging = false;
        }
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.energyStorage";
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
        if (slot == SLOT_CHARGE) {
            return isChargeable(stack) && tierAllows(stack);
        }
        if (slot == SLOT_DISCHARGE) {
            return isDischargeable(stack);
        }
        if (slot >= FIRST_EXTRA_CHARGE && slot < SLOT_COUNT) {
            return slot - FIRST_EXTRA_CHARGE + 1 < chargeSlots() && isChargeable(stack) && tierAllows(stack);
        }
        return slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE && acceptsUpgradeHere(stack);
    }

    // ---- automation: hoppers and pipes reach the charge slots and the discharge slot, never the upgrades ----

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int n = chargeSlots();
        int[] out = new int[n + 1];
        for (int k = 0; k < n; k++) {
            out[k] = chargeSlotIndex(k);
        }
        out[n] = SLOT_DISCHARGE;
        return out;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return (slot < FIRST_UPGRADE_SLOT || slot >= FIRST_EXTRA_CHARGE) && getStackInSlot(slot) == null && isItemValidForSlot(slot, stack);
    }

    /**
     * Automation takes out of a charge slot only an item that is full (or can't be charged here),
     * out of the discharge slot only one that is empty (or gives nothing) - tried on a copy with
     * the same charge / discharge calls the ticks use.
     */
    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        if (slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE) {
            return false;
        }
        if (stack == null) {
            return true;
        }
        int probe = getTier().getVoltage();
        if (slot == SLOT_DISCHARGE) {
            return dischargeItem(stack.copy(), probe) <= 0;
        }
        return chargeItem(stack.copy(), probe) <= 0;
    }

    /** Weapons and armor pieces go in the charge slot (isItemValidForSlot adds the tier rule). */
    public static boolean isChargeable(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof ItemWeaponSC || stack.getItem() instanceof com.sc.item.ItemArmorSC
                || stack.getItem() instanceof com.sc.item.ItemBladeSC || stack.getItem() instanceof com.sc.item.ItemDrillSC
                || com.sc.item.ItemWrenchSC.isElectric(stack) || com.sc.item.ItemBatterySC.isBattery(stack));
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        tierFromMeta = !nbt.hasKey("TierSC");
        facing = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (facing == ForgeDirection.UNKNOWN) {
            facing = ForgeDirection.SOUTH;
        }
        readExtraOutputs(nbt);
        chargeSlot = nbt.hasKey("ChargeSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("ChargeSlot")) : null;
        dischargeSlot = nbt.hasKey("DischargeSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("DischargeSlot")) : null;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            String k = "Upgrade" + i;
            upgradeSlots[i] = nbt.hasKey(k) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(k)) : null;
        }
        for (int i = 0; i < extraCharge.length; i++) {
            String k = "ChargeSlot" + (i + 2);
            extraCharge[i] = nbt.hasKey(k) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(k)) : null;
        }
        refreshModules();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Facing", facing.ordinal());
        writeExtraOutputs(nbt);
        if (chargeSlot != null) {
            nbt.setTag("ChargeSlot", chargeSlot.writeToNBT(new NBTTagCompound()));
        }
        if (dischargeSlot != null) {
            nbt.setTag("DischargeSlot", dischargeSlot.writeToNBT(new NBTTagCompound()));
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgradeSlots[i] != null) {
                nbt.setTag("Upgrade" + i, upgradeSlots[i].writeToNBT(new NBTTagCompound()));
            }
        }
        for (int i = 0; i < extraCharge.length; i++) {
            if (extraCharge[i] != null) {
                nbt.setTag("ChargeSlot" + (i + 2), extraCharge[i].writeToNBT(new NBTTagCompound()));
            }
        }
    }

    /**
     * "OutFaces": a bit per output face, the front's included (missing - a world from before the
     * splitter: the front only); "OutOrder": the extra faces in the order they were set, 3 bits
     * each (ordinal + 1), the first in the lowest bits.
     */
    private void readExtraOutputs(NBTTagCompound nbt) {
        extraOut.clear();
        if (!nbt.hasKey("OutFaces")) {
            return;
        }
        int mask = nbt.getInteger("OutFaces");
        int order = nbt.getInteger("OutOrder");
        for (int i = 0; i < 6 && (order >> 3 * i & 7) != 0; i++) {
            ForgeDirection d = ForgeDirection.getOrientation((order >> 3 * i & 7) - 1);
            if (d != ForgeDirection.UNKNOWN && d != facing && (mask & 1 << d.ordinal()) != 0 && !extraOut.contains(d)) {
                extraOut.add(d);
            }
        }
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {     // in the mask but not the order: by face
            if (d != facing && (mask & 1 << d.ordinal()) != 0 && !extraOut.contains(d)) {
                extraOut.add(d);
            }
        }
    }

    private void writeExtraOutputs(NBTTagCompound nbt) {
        int mask = 1 << facing.ordinal(), order = 0;
        for (int i = 0; i < extraOut.size(); i++) {
            mask |= 1 << extraOut.get(i).ordinal();
            order |= (extraOut.get(i).ordinal() + 1) << 3 * i;
        }
        nbt.setInteger("OutFaces", mask);
        if (order != 0) {
            nbt.setInteger("OutOrder", order);
        }
    }

    /** Facing reaches the client with the chunk, so the front face renders on the right side. */
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
}
