package com.sc.tileentity;

import com.sc.conduit.ConduitKind;

import net.minecraft.tileentity.TileEntity;

/**
 * An Item Pneumatic Tube from before conduit bundles, as saved in old worlds. On its first
 * server tick it replaces its block with a conduit bundle holding a tube.
 */
public class TileEntityItemTubeSC extends TileEntity {

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote) {
            TileEntityConduitBundleSC.convertLegacy(worldObj, xCoord, yCoord, zCoord, ConduitKind.TUBE, 0, null);
        }
    }
}
