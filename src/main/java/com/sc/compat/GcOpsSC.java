package com.sc.compat;

import micdoodle8.mods.galacticraft.api.transmission.NetworkType;
import micdoodle8.mods.galacticraft.api.transmission.grid.IElectricityNetwork;
import micdoodle8.mods.galacticraft.api.transmission.tile.IConductor;
import micdoodle8.mods.galacticraft.api.transmission.tile.IElectrical;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Galacticraft calls of the Energy Converter, in a class of their own: loaded only when
 * ForeignEnergySC.galacticraft() said Galacticraft is there. A wire (IConductor) gets the gJ into
 * its network (produce), a machine (IElectrical) directly.
 */
public final class GcOpsSC {

    private GcOpsSC() {
    }

    /** `te` takes gJ through its face `side`. */
    public static boolean takes(TileEntity te, ForgeDirection side) {
        if (te instanceof IConductor) {
            return ((IConductor) te).getNetwork() != null;
        }
        return te instanceof IElectrical && ((IElectrical) te).canConnect(side, NetworkType.POWER);
    }

    /** Gives `te` up to `gj` gJ through its face `side`; `from` is the converter. @return gJ taken */
    public static double give(TileEntity te, ForgeDirection side, double gj, TileEntity from) {
        if (gj <= 0) {
            return 0;
        }
        float amount = (float) Math.min(gj, Float.MAX_VALUE);
        if (te instanceof IConductor) {
            IElectricityNetwork net = ((IConductor) te).getNetwork();
            if (net == null) {
                return 0;
            }
            float left = net.produce(amount, true, 1, from);       // what nothing on the network wanted
            return Math.max(0, Math.min(amount, amount - left));
        }
        if (te instanceof IElectrical && ((IElectrical) te).canConnect(side, NetworkType.POWER)) {
            return Math.max(0, Math.min(amount, ((IElectrical) te).receiveElectricity(side, amount, 1, true)));
        }
        return 0;
    }
}
