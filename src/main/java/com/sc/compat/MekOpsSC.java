package com.sc.compat;

import mekanism.api.energy.IStrictEnergyAcceptor;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Mekanism calls of the Energy Converter, in a class of their own: loaded only when
 * ForeignEnergySC.mekanism() said Mekanism is there (compiled against the API stubs,
 * libs/mekanism-api-stub-compileonly.jar).
 */
public final class MekOpsSC {

    private MekOpsSC() {
    }

    /** `te` takes joules through its face `side`. */
    public static boolean takes(TileEntity te, ForgeDirection side) {
        return te instanceof IStrictEnergyAcceptor && ((IStrictEnergyAcceptor) te).canReceiveEnergy(side);
    }

    /** Gives `te` up to `j` joules through its face `side`. @return joules taken */
    public static double give(TileEntity te, ForgeDirection side, double j) {
        if (!takes(te, side) || j <= 0) {
            return 0;
        }
        double used = ((IStrictEnergyAcceptor) te).transferEnergyToAcceptor(side, j);
        return Math.max(0, Math.min(j, used));
    }
}
