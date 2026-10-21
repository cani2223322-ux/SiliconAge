package com.sc.energy;

import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySource;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * ЭН-1 (audit 2026-10-20, ★ A): the "line stronger than the input" check (TileEntityEnergyBase.lineTier) for IC2's own blocks - an
 * IC2 cable by its rated packet, an IC2 source (MFSU, generators, transformers) by its output tier. Holds IC2's API types, so it
 * is loaded only behind Loader.isModLoaded(Reference.IC2_MODID), as NetViewNetSC.Ic2 is. Works on the client too (IC2's tiles are
 * there; a transformer's mode may lag behind the server there - fine for a warning).
 */
public final class Ic2LineSC {

    private Ic2LineSC() {
    }

    /**
     * The tier `te` (an IC2 tile beside `receiver`) can bring into it through its side `sideOfTe` (the direction from `te` to the
     * receiver), or null: not IC2's, not giving that way, or misbehaving. A cable: IC2 gives its breakdown energy as the rated
     * packet + 1 (tin 33 - LV, copper 129 - MV, gold 513 - HV, HV cable 2049 - EV, glass fibre 8193 - IV).
     */
    static Tier tierTowards(TileEntity te, TileEntity receiver, ForgeDirection sideOfTe) {
        try {
            if (te instanceof IEnergyConductor) {
                if (!((IEnergyEmitter) te).emitsEnergyTo(receiver, sideOfTe)) {
                    return null;
                }
                return Tier.ofCapacity(((IEnergyConductor) te).getConductorBreakdownEnergy() - 1);
            }
            if (te instanceof IEnergySource && ((IEnergySource) te).emitsEnergyTo(receiver, sideOfTe)) {
                return Tier.fromIc2Tier(((IEnergySource) te).getSourceTier());
            }
        } catch (RuntimeException ex) {
            // a foreign block misbehaving - no warning from it
        }
        return null;
    }
}
