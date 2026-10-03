package com.sc.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** The controller link the bridge parts share: kept in NBT, sent to the client with the block. */
final class BridgeLinkSC {

    private int[] pos;

    int[] get() {
        return pos == null ? null : pos.clone();
    }

    /** @return true if it changed */
    boolean set(int[] p) {
        if (p == null ? pos == null : pos != null && pos[0] == p[0] && pos[1] == p[1] && pos[2] == p[2]) {
            return false;
        }
        pos = p == null ? null : new int[]{p[0], p[1], p[2]};
        return true;
    }

    /** The controller it points to, if it is still there (server: loads nothing). */
    TileEntityBridgeControllerSC controller(World w) {
        if (pos == null || w == null || !w.blockExists(pos[0], pos[1], pos[2])) {
            return null;
        }
        TileEntity te = w.getTileEntity(pos[0], pos[1], pos[2]);
        return te instanceof TileEntityBridgeControllerSC ? (TileEntityBridgeControllerSC) te : null;
    }

    void write(NBTTagCompound nbt) {
        if (pos != null) {
            nbt.setIntArray("BridgeCtrl", pos);
        }
    }

    void read(NBTTagCompound nbt) {
        int[] p = nbt.getIntArray("BridgeCtrl");
        pos = p.length == 3 ? p : null;
    }
}
