package com.sc.energy;

import com.sc.Reference;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Base TileEntity for every SC machine, per design doc 01_recipes.md §9.4.
 *
 * Implements our own IEnergyHandlerSC always, and IC2's IEnergySink/IEnergySource
 * conditionally: the interfaces are declared via @Optional.InterfaceList so the class
 * compiles fine (IC2 is on the compile classpath, see build.gradle) whether or not IC2 is
 * present at runtime, and each IC2-facing method is @Optional.Method-annotated so FML's
 * class transformer strips those method bindings out of the loaded class entirely when IC2
 * isn't installed - this class never touches an IC2 class unless Loader.isModLoaded(IC2_MODID)
 * is true first.
 *
 * When IC2 is loaded, this tile becomes a real node on IC2's own EnergyNet (registered via
 * EnergyTileLoadEvent/Unload) and IC2 cables/generators talk to it directly through
 * injectEnergy/getDemandedEnergy/etc. When IC2 is absent, it instead registers with our
 * fallback EnergyNetSC and relies on receiveEnergy(...) being called directly by our own
 * cable TileEntities (added in a later step).
 */
@Optional.InterfaceList({
        @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = Reference.IC2_MODID),
        @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySource", modid = Reference.IC2_MODID)
})
public abstract class TileEntityEnergyBase extends TileEntity implements IEnergyHandlerSC, IEnergySink, IEnergySource, Ic2LoadQueueSC.Deferred {

    private Tier tier;
    private int energyStored;
    private boolean registeredWithEnergyNet;

    /**
     * No-arg for Minecraft's NBT-reflection instantiation (GameRegistry.registerTileEntity
     * requires it) - defaults to LV until setTier() is called. Concrete single-purpose
     * subclasses should still use the Tier constructor; subclasses whose type/tier is only
     * known after construction (e.g. TileEntityMachineSC, set from block metadata via
     * Block.createTileEntity) use this constructor + setTier().
     */
    protected TileEntityEnergyBase() {
        this.tier = Tier.LV;
    }

    protected TileEntityEnergyBase(Tier tier) {
        this.tier = tier;
    }

    protected void setTier(Tier tier) {
        this.tier = tier;
    }

    // ---- IEnergyHandlerSC ----

    @Override
    public Tier getTier() {
        return tier;
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    /**
     * Client-side only, written by the Container's updateProgressBar() (see
     * ContainerMachineSC/ContainerGeneratorSC/ContainerFieldGeneratorSC.detectAndSendChanges()).
     * Without this, a GUI's energy bar reads the client's copy of `energyStored`, which is only
     * ever set from chunk-load NBT and otherwise frozen - it would never visibly move while the
     * player has the screen open, no matter how much EU the server side gains or spends.
     */
    public void setEnergyStoredClient(int value) {
        this.energyStored = value;
    }

    @Override
    public int getMaxEnergyStored() {
        return tier.getBuffer();
    }

    /** Highest voltage this tile takes without blowing up. A transformer's differs from its output. */
    public Tier inputTier() {
        return tier;
    }

    /** Voltage of the energy this tile sends out. */
    public Tier outputTier() {
        return tier;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    // ---- the power switch (machines, generators, storages): off, the tile neither takes nor gives
    // energy, so no line can overvolt it; a redstone mode can hold it too ----

    protected boolean powerOn = true;
    /** 0 works always, 1 only with a redstone signal, 2 only without one. */
    protected int redstoneMode;

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

    /** Switch, redstone mode and the battery slot's mode as one int for a container's sync. */
    public int powerFlags() {
        return (powerOn ? 1 : 0) | redstoneMode << 1 | batteryMode << 3;
    }

    public void setPowerFlagsClient(int flags) {
        powerOn = (flags & 1) != 0;
        redstoneMode = (flags >> 1) & 3;
        batteryMode = (flags >> 3) & 1;
    }

    // ---- the battery slot (machines, quarries, the field generator): a portable battery tops up
    // the buffer at the input voltage - only while it's under half (the grid first), or always ----

    /** BatteryFeedSC.MODE_RESERVE or MODE_ALWAYS. */
    protected int batteryMode;

    public int getBatteryMode() {
        return batteryMode;
    }

    public void cycleBatteryMode() {
        batteryMode = (batteryMode + 1) % com.sc.item.BatteryFeedSC.MODES;
        markDirty();
    }

    /** Whether the battery in the slot gives energy now (also on the client, for the screen's arrows). */
    public boolean batteryFeeds(ItemStack battery) {
        if (!powerOn || battery == null || com.sc.item.BatteryFeedSC.chargeOf(battery) <= 0) {
            return false;
        }
        int stored = getEnergyStored(), cap = getMaxEnergyStored();
        return batteryMode == com.sc.item.BatteryFeedSC.MODE_ALWAYS ? stored < cap : stored * 2 < cap;
    }

    /**
     * A generator's battery slot: whether its output charges the battery now - "surplus" (mode 0)
     * only while the buffer is over half (what the grid doesn't take), "always" (1) whenever
     * there's energy. Also on the client, for the arrows.
     */
    public boolean batteryCharges(ItemStack battery) {
        if (!powerOn || battery == null || com.sc.item.BatteryFeedSC.chargeOf(battery) >= com.sc.item.BatteryFeedSC.capacityOf(battery)) {
            return false;
        }
        int stored = getEnergyStored(), cap = getMaxEnergyStored();
        return batteryMode == com.sc.item.BatteryFeedSC.MODE_ALWAYS ? stored > 0 : stored * 2 > cap;
    }

    /** One tick of a generator's battery slot: charges it at up to the output voltage. @return EU given */
    protected int chargeBattery(ItemStack battery) {
        if (!batteryCharges(battery)) {
            return 0;
        }
        int max = Math.min(getEnergyStored(), outputTier().getVoltage());
        if (batteryMode != com.sc.item.BatteryFeedSC.MODE_ALWAYS) {
            max = Math.min(max, getEnergyStored() - getMaxEnergyStored() / 2);
        }
        int moved = com.sc.item.BatteryFeedSC.charge(battery, max);
        if (moved > 0) {
            removeEnergy(moved);
        }
        return moved;
    }

    /** One tick of the battery slot: tops the buffer up from it. @return EU taken */
    protected int feedFromBattery(ItemStack battery) {
        if (!batteryFeeds(battery)) {
            return 0;
        }
        int want = Math.min(getMaxEnergyStored() - getEnergyStored(), inputTier().getVoltage());
        int got = com.sc.item.BatteryFeedSC.discharge(battery, want);
        if (got > 0) {
            addEnergy(got);
        }
        return got;
    }

    protected boolean redstoneAllows() {
        if (redstoneMode == 0 || worldObj == null) {
            return true;
        }
        boolean signal = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        return redstoneMode == 1 ? signal : !signal;
    }

    /** Switched on and not held by redstone. */
    protected boolean switchedOn() {
        return powerOn && redstoneAllows();
    }

    /**
     * The strongest voltage a neighbour can bring in on a face that takes energy: a cable, or an
     * energy source touching it; null when there's none. Works on the client too (for the screens).
     */
    public Tier lineTier() {
        Tier best = null;
        if (worldObj == null) {
            return null;
        }
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            int x = xCoord + dir.offsetX, y = yCoord + dir.offsetY, z = zCoord + dir.offsetZ;
            if (!acceptsFrom(dir) || !worldObj.blockExists(x, y, z)) {
                continue;
            }
            net.minecraft.tileentity.TileEntity te = worldObj.getTileEntity(x, y, z);
            Tier t = null;
            if (te instanceof com.sc.tileentity.TileEntityConduitBundleSC && ((com.sc.tileentity.TileEntityConduitBundleSC) te).getCable() != null) {
                t = ((com.sc.tileentity.TileEntityConduitBundleSC) te).getCable().tier;
            } else if (te instanceof com.sc.tileentity.TileEntityCableSC && ((com.sc.tileentity.TileEntityCableSC) te).getCableType() != null) {
                t = ((com.sc.tileentity.TileEntityCableSC) te).getCableType().tier;
            } else if (te instanceof TileEntityEnergyBase && ((TileEntityEnergyBase) te).isEnergySource()) {
                t = ((TileEntityEnergyBase) te).outputTier();
            }
            if (t != null && (best == null || t.ordinal() > best.ordinal())) {
                best = t;
            }
        }
        return best;
    }

    /** It takes energy, and a line beside it is stronger than its input: switching on would blow it up. */
    public boolean lineTooStrong() {
        if (!isEnergySink() || acceptsAnyVoltage()) {
            return false;
        }
        Tier t = lineTier();
        return t != null && inputTier().excessTiersOf(t) > 0;
    }

    /** The IC2 sink tier of "any voltage" - see getSinkTier. */
    public static final int ANY_VOLTAGE_IC2_TIER = 13;

    /** True when nothing overvolts this tile at all (a universal transformer upgrade). */
    public boolean acceptsAnyVoltage() {
        return false;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (from != ForgeDirection.UNKNOWN && !acceptsFrom(from)) {
            return 0;                                   // e.g. an energy storage's output face
        }
        Tier packetTier = voltageTierOf(voltage);
        if (inputTier().excessTiersOf(packetTier) > 0) {
            if (simulate) {
                // A real delivery at this voltage would overvolt-explode the receiver, but
                // `simulate` promises no side effects - exploding the block just because
                // something probed capacity would violate that contract, so just report "can't
                // safely accept this" instead of detonating.
                return 0;
            }
            ExplosionLogic.checkOvervoltageAndExplode(this, inputTier(), packetTier);
            // Receiver no longer exists (block was replaced with air by the explosion) -
            // report the whole packet as "accepted" so callers don't try to redirect it.
            return amount;
        }
        int room = Math.max(0, getMaxEnergyStored() - energyStored);
        int accepted = Math.max(0, Math.min(room, amount));
        if (!simulate && accepted > 0) {
            energyStored += accepted;
            energyChanged();
        }
        return accepted;
    }

    private static Tier voltageTierOf(int voltage) {
        for (Tier t : Tier.values()) {
            if (voltage <= t.getVoltage()) {
                return t;
            }
        }
        return Tier.XV;
    }

    /** For subclasses (generators) that produce energy internally rather than receiving it. */
    /** Energy a placed item brings back: not clipped to the buffer, whose storage upgrades come later. */
    protected void restoreEnergy(int amount) {
        energyStored = Math.max(energyStored, Math.max(0, amount));
    }

    protected void addEnergy(int amount) {
        int before = energyStored;
        energyStored = (int) Math.max(0L, Math.min(getMaxEnergyStored(), (long) energyStored + amount));
        if (energyStored != before) {
            energyChanged();
        }
    }

    /** For subclasses (consuming machines) spending their own buffer on an operation. */
    protected int removeEnergy(int amount) {
        int removed = Math.min(energyStored, amount);
        energyStored -= removed;
        if (removed != 0) {
            energyChanged();
        }
        return removed;
    }

    /** World tick the chunk was last told it has something to save (energyChanged). */
    private long lastChunkMark = -1;

    /**
     * The buffer changes without markDirty(): tell the chunk it has something to save (once a tick
     * at most) - else a chunk with no entity in it isn't written on server stop / autosave and the
     * buffer comes back as it was at the last save.
     */
    private void energyChanged() {
        if (worldObj != null && !worldObj.isRemote) {
            long now = worldObj.getTotalWorldTime();
            if (now != lastChunkMark) {
                lastChunkMark = now;
                worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
            }
        }
    }

    /** Sneak-click charging of a handheld item (ItemWeaponSC) straight out of this buffer. @return EU taken */
    /**
     * May this player charge an item from this block (sneak + right-click)? Not inside someone
     * else's private field (the chat says whose); a field generator also checks its own access.
     */
    public boolean canItemCharge(net.minecraft.entity.player.EntityPlayer p) {
        return !com.sc.ShieldEventHandler.privateFor(worldObj, p, xCoord, yCoord, zCoord);
    }

    public int extractForItemCharging(int max) {
        return max <= 0 ? 0 : removeEnergy(max);
    }

    /**
     * Subclasses that only consume power (most machines from §13/§14) leave this true and
     * isEnergySource() false; generators (§15) do the opposite. A tile is never both in step
     * 1's model - that distinction belongs to concrete machine/generator subclasses added in
     * later steps.
     */
    @Override
    public boolean isEnergySink() {
        return true;
    }

    @Override
    public boolean isEnergySource() {
        return false;
    }

    /**
     * At most one tier-voltage packet per tick, like IC2's own BasicSource (min(stored, power)).
     * Offering the whole buffer made IC2 route e.g. 3200 EU in one go - read as an EV packet by
     * every LV consumer it reached - and drained the buffer a tick early.
     */
    /** Faces energy may come in through. Everything but an energy storage takes it on every face. */
    public boolean acceptsFrom(ForgeDirection side) {
        return true;
    }

    /** Faces a source sends energy out of (EnergyNetSC starts its search only there). */
    public ForgeDirection[] outputFaces() {
        return ForgeDirection.VALID_DIRECTIONS;
    }

    public boolean isOutputFace(ForgeDirection side) {
        for (ForgeDirection d : outputFaces()) {
            if (d == side) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int offerableEnergy() {
        return isEnergySource() ? Math.min(energyStored, outputTier().getVoltage()) : 0;
    }

    /**
     * Packets of the output voltage this tile may send a tick: 1, more for a generator whose
     * upgrades make more than one packet holds (an LV generator under Overdrive, 48 EU/t = 2 x 32).
     */
    public int packetsPerTick() {
        return 1;
    }

    /**
     * Packets of the output voltage one output face may send a tick (EnergyNetSC keeps each face
     * to it): all of packetsPerTick() - only a storage with extra output faces (Output Splitter)
     * gives each face a stream of its own.
     */
    public int packetsPerFace() {
        return packetsPerTick();
    }

    /**
     * `eu` left through output face `face` (UNKNOWN: IC2's net took it, the face not told) - a
     * storage pays its extra faces' upkeep here.
     */
    protected void sentOut(ForgeDirection face, int eu) {
    }

    /** EnergyNetSC rebuilt the networks this tile touches (before anything moves on them). */
    public void energyNetRebuilt() {
    }

    /** EnergyNetSC's handle on sentOut. */
    final void reportSent(ForgeDirection face, int eu) {
        if (eu > 0) {
            sentOut(face, eu);
        }
    }

    @Override
    public int demandedEnergy() {
        return isEnergySink() ? Math.max(0, getMaxEnergyStored() - energyStored) : 0;
    }

    // ---- NBT persistence (missing until now - energyStored was silently lost on every
    // chunk save/reload for every energy tile in the mod; subclasses that add their own
    // fields must call super.readFromNBT/writeToNBT). ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        energyStored = nbt.getInteger("EnergySC");
        powerOn = !nbt.getBoolean("PowerOff");                  // tiles saved before the switch: on
        redstoneMode = Math.max(0, Math.min(2, nbt.getInteger("RedstoneMode")));
        batteryMode = Math.max(0, Math.min(com.sc.item.BatteryFeedSC.MODES - 1, nbt.getInteger("BatteryMode")));
        tier = Tier.values()[Math.min(Tier.values().length - 1, Math.max(0, nbt.getInteger("TierSC")))];
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("PowerOff", !powerOn);
        nbt.setInteger("RedstoneMode", redstoneMode);
        nbt.setInteger("BatteryMode", batteryMode);
        nbt.setInteger("EnergySC", energyStored);
        nbt.setInteger("TierSC", tier.ordinal());
    }

    // ---- lifecycle: register with IC2's real EnergyNet when present, our own fallback otherwise ----

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote && Loader.isModLoaded(Reference.IC2_MODID)) {
            Ic2LoadQueueSC.queue(this);                   // IC2: on the next server tick (Ic2LoadQueueSC)
        } else {
            registerWithEnergyNet();
        }
    }

    /** Ic2LoadQueueSC: the tile is in its world now - join the net. */
    @Override
    public void joinEnergyNetNow() {
        if (!registeredWithEnergyNet) {
            registerWithEnergyNet();
        }
    }

    @Override
    public void invalidate() {
        Ic2LoadQueueSC.cancel(this);
        unregisterFromEnergyNet();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        Ic2LoadQueueSC.cancel(this);
        unregisterFromEnergyNet();
        super.onChunkUnload();
    }

    /** Re-announce the tile after its input/output faces changed - IC2 caches them on load. */
    protected void refreshEnergyNet() {
        if (registeredWithEnergyNet) {
            unregisterFromEnergyNet();
            registerWithEnergyNet();
        }
    }

    private void registerWithEnergyNet() {
        if (worldObj != null && worldObj.isRemote) {
            return;
        }
        if (Loader.isModLoaded(Reference.IC2_MODID)) {
            postIc2LoadEvent();
        } else {
            EnergyNetSC.instance().addTile(this);
        }
        registeredWithEnergyNet = true;
    }

    private void unregisterFromEnergyNet() {
        if (!registeredWithEnergyNet) {
            return;
        }
        if (Loader.isModLoaded(Reference.IC2_MODID)) {
            postIc2UnloadEvent();
        } else {
            EnergyNetSC.instance().removeTile(this);
        }
        registeredWithEnergyNet = false;
    }

    @Optional.Method(modid = Reference.IC2_MODID)
    private void postIc2LoadEvent() {
        MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
    }

    @Optional.Method(modid = Reference.IC2_MODID)
    private void postIc2UnloadEvent() {
        MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
    }

    // ---- IC2 IEnergySink / IEnergySource (+ inherited IEnergyAcceptor / IEnergyEmitter) ----
    // All delegate into the IEnergyHandlerSC methods above so IC2-present and IC2-absent
    // behaviour can never diverge (§9.4's whole point).

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double getDemandedEnergy() {
        return demandedEnergy();
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public int getSinkTier() {
        // A universal transformer: tier 13 (536 870 912 EU a packet) - far above anything that
        // exists, so nothing overvolts it. Not Integer.MAX_VALUE: Industrial Upgrade's net turns a
        // tier into power as 8 << 2 * tier, which overflows to 0 there, and IC2's grid (running
        // beside it) divides by that power - NaN through the whole grid, no energy moved at all.
        return acceptsAnyVoltage() ? ANY_VOLTAGE_IC2_TIER : inputTier().toIc2Tier();
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double injectEnergy(ForgeDirection directionFrom, double amount, double voltage) {
        if (!isEnergySink()) {
            return amount;
        }
        // IC2's net enforces tiers itself (it compares against getSinkTier() and blows up the
        // sink); its `voltage` argument follows IC2's own packet model, which isn't the per-
        // source voltage our receiveEnergy() expects - feeding it through made machines explode
        // on packets IC2 itself considered safe. So accept at our own rated voltage here.
        int accepted = receiveEnergy(directionFrom, inputTier().getVoltage(), (int) amount, false);
        return amount - accepted;
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double getOfferedEnergy() {
        return offerableEnergy();
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public void drawEnergy(double amount) {
        int eu = (int) Math.round(amount);
        if (eu >= 0) {
            removeEnergy(eu);
            reportSent(ForgeDirection.UNKNOWN, eu);
        } else {
            addEnergy(-eu);     // IC2 hands unrouted surplus back as a negative draw
        }
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public int getSourceTier() {
        return outputTier().toIc2Tier();
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection side) {
        return isEnergySink() && canConnectEnergy(side) && acceptsFrom(side);
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection side) {
        return isEnergySource() && canConnectEnergy(side) && isOutputFace(side);
    }
}
