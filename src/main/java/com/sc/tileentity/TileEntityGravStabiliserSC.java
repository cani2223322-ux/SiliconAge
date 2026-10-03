package com.sc.tileentity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;

/**
 * The Gravitational Stabiliser's tile entity: nothing to save, no ticks - it is there for the orb's
 * renderer (GravStabiliserRendererSC) and WAILA. Clients look for a working Singular station near
 * it now and then (glowing()).
 */
public class TileEntityGravStabiliserSC extends TileEntity {

    private long checkedAt = Long.MIN_VALUE;
    private boolean glow;

    @Override
    public boolean canUpdate() {
        return false;
    }

    /** The Singular station this stabiliser counts for (in range), or null. */
    public TileEntitySingularStationSC station() {
        if (worldObj == null) {
            return null;
        }
        int r = TileEntitySingularStationSC.STAB_RADIUS, dy = TileEntitySingularStationSC.STAB_DY;
        for (int y = -dy; y <= dy; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (worldObj.blockExists(xCoord + x, yCoord + y, zCoord + z)) {
                        TileEntity te = worldObj.getTileEntity(xCoord + x, yCoord + y, zCoord + z);
                        if (te instanceof TileEntitySingularStationSC) {
                            return (TileEntitySingularStationSC) te;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Clients: a station near it works (looked up at most twice a second). */
    @SideOnly(Side.CLIENT)
    public boolean glowing() {
        long now = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        if (checkedAt == Long.MIN_VALUE || now - checkedAt >= 10 || now < checkedAt) {
            checkedAt = now;
            TileEntitySingularStationSC st = station();
            glow = st != null && st.isWorking();
        }
        return glow;
    }
}
