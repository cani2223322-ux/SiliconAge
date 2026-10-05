package com.sc.compat;

import cofh.api.energy.IEnergyContainerItem;
import cofh.api.energy.IEnergyReceiver;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Redstone Flux calls of the Energy Converter, kept in a class of their own: it is loaded only when
 * ForeignEnergySC.rfApi() said the API is there (compiled against libs/rf-api-compileonly.jar).
 */
public final class RfOpsSC {

    private RfOpsSC() {
    }

    /** `te` takes RF through its face `side` (the face touching the converter). */
    public static boolean takes(TileEntity te, ForgeDirection side) {
        return te instanceof IEnergyReceiver && ((IEnergyReceiver) te).canConnectEnergy(side);
    }

    /** Gives `te` up to `rf` RF through its face `side`. @return RF taken */
    public static int give(TileEntity te, ForgeDirection side, int rf) {
        if (!takes(te, side) || rf <= 0) {
            return 0;
        }
        return Math.max(0, ((IEnergyReceiver) te).receiveEnergy(side, rf, false));
    }

    /** An RF item (IEnergyContainerItem). */
    public static boolean isItem(ItemStack s) {
        return s != null && s.getItem() instanceof IEnergyContainerItem;
    }

    /** Charges an RF item with up to `rf`. @return RF taken */
    public static int charge(ItemStack s, int rf, boolean simulate) {
        if (!isItem(s) || rf <= 0) {
            return 0;
        }
        return Math.max(0, ((IEnergyContainerItem) s.getItem()).receiveEnergy(s, rf, simulate));
    }
}
