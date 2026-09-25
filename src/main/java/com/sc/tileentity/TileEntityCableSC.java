package com.sc.tileentity;

import com.sc.conduit.ConduitKind;
import com.sc.energy.CableType;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * A cable from before conduit bundles, as saved in old worlds. On its first server tick it
 * replaces its block with a conduit bundle holding the same cable.
 */
public class TileEntityCableSC extends TileEntity {

    private CableType cableType = CableType.COPPER_BARE;

    public CableType getCableType() {
        return cableType;
    }

    public void setCableType(CableType cableType) {
        this.cableType = cableType;
    }

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote) {
            TileEntityConduitBundleSC.convertLegacy(worldObj, xCoord, yCoord, zCoord, ConduitKind.CABLE, cableType.ordinal(), null);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        CableType[] types = CableType.values();
        int ordinal = nbt.getInteger("CableType");
        cableType = types[ordinal >= 0 && ordinal < types.length ? ordinal : 0];
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("CableType", cableType.ordinal());
    }
}
