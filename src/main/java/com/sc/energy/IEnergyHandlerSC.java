package com.sc.energy;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Unified energy-handling contract for our own TileEntities, independent of whether IC2 is
 * present. Per design doc 01_recipes.md §9.4: TileEntityEnergyBase implements this directly,
 * and additionally implements IC2's IEnergySink/IEnergySource conditionally (via
 * cpw.mods.fml.common.Optional.Interface) when IC2 is loaded, delegating into these same
 * methods so the two code paths never diverge.
 */
public interface IEnergyHandlerSC {

    Tier getTier();

    int getEnergyStored();

    int getMaxEnergyStored();

    boolean canConnectEnergy(ForgeDirection from);

    /** True if this tile can act as an energy source for the fallback cable relay (§9.4/§12). */
    boolean isEnergySource();

    /** True if this tile can act as an energy sink for the fallback cable relay (§9.4/§12). */
    boolean isEnergySink();

    /** How much EU this tile would like to push out right now (0 if it's not a source). */
    int offerableEnergy();

    /** How much EU this tile has room to accept right now (0 if it's not a sink). */
    int demandedEnergy();

    /**
     * Offers `amount` EU at `voltage` from the given side.
     * If voltage exceeds getTier()'s voltage, the caller must treat this as an overvoltage
     * event (see ExplosionLogic) rather than calling receiveEnergy - this method assumes the
     * packet has already been voltage-checked.
     *
     * @return the amount actually accepted (<= amount).
     */
    int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate);
}
