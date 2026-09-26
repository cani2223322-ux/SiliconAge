package com.sc.energy;

import com.sc.Reference;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
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
public abstract class TileEntityEnergyBase extends TileEntity implements IEnergyHandlerSC, IEnergySink, IEnergySource {

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
        if (!simulate) {
            energyStored += accepted;
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
        energyStored = Math.min(getMaxEnergyStored(), energyStored + amount);
    }

    /** For subclasses (consuming machines) spending their own buffer on an operation. */
    protected int removeEnergy(int amount) {
        int removed = Math.min(energyStored, amount);
        energyStored -= removed;
        return removed;
    }

    /** Sneak-click charging of a handheld item (ItemWeaponSC) straight out of this buffer. @return EU taken */
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
        tier = Tier.values()[Math.min(Tier.values().length - 1, Math.max(0, nbt.getInteger("TierSC")))];
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("EnergySC", energyStored);
        nbt.setInteger("TierSC", tier.ordinal());
    }

    // ---- lifecycle: register with IC2's real EnergyNet when present, our own fallback otherwise ----

    @Override
    public void validate() {
        super.validate();
        registerWithEnergyNet();
    }

    @Override
    public void invalidate() {
        unregisterFromEnergyNet();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
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
        return inputTier().toIc2Tier();
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
