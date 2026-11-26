package com.sc.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md): the Data Port's tile - its mode (STOP: a rising redstone edge soft-stops the reactor; PERMIT: the
 * reactor may light only while the port is powered, and losing the signal while lit soft-stops it), the reactor it belongs to (found by
 * the reactor's own structure scan - the port never searches), the last comparator level and redstone state. NBT "scDataPort" {mode} -
 * fixed by the coordinator; ОМ-5 appends {rx, ry, rz, b} (the reactor it was told of).
 * <p>
 * Ticks once every TICK (10): the redstone (isBlockIndirectlyGettingPowered) against the last one, the reactor's comparator level.
 * The first tick after a load only remembers the signal (no stop from a lever already on).
 */
public class TileEntityDataPortSC extends TileEntity implements com.sc.energy.IComparatorSC {

    public static final String NBT = "scDataPort";
    public static final int STOP = 0, PERMIT = 1;
    /** ОМ-5: the port's check, ticks. */
    public static final int TICK = 10;

    protected int mode = STOP;
    /** ОМ-5: the reactor that last told the port it is in its wall (its generator block). */
    private int rx, ry, rz;
    private boolean bound;
    /** ОМ-5: the signal at the last check; the first check done (after the load). */
    private boolean lastPowered, checked;
    private int comparatorLast = -1;

    public int mode() {
        return mode;
    }

    /** ОМ-5 (the block's right-click): STOP <-> PERMIT. @return the new mode */
    public int toggleMode() {
        mode = mode == STOP ? PERMIT : STOP;
        markDirty();
        TileEntityGeneratorSC g = reactor();
        if (g != null) {
            g.portsChanged();                                   // Э11: the reactor asks its ports again at once
        }
        return mode;
    }

    /** ОМ-5 (the reactor's scan - TileEntityGeneratorSC.bindPortsSC): the port is in the wall of the reactor at (x, y, z). */
    public void bind(int x, int y, int z) {
        if (!bound || rx != x || ry != y || rz != z) {
            rx = x;
            ry = y;
            rz = z;
            bound = true;
            markDirty();
        }
    }

    /** ОМ-5: the reactor whose wall the port is in (its scan still lists it), else null. Server. */
    public TileEntityGeneratorSC reactor() {
        if (!bound || worldObj == null || !worldObj.blockExists(rx, ry, rz)) {
            return null;
        }
        TileEntity te = worldObj.getTileEntity(rx, ry, rz);
        if (!(te instanceof TileEntityGeneratorSC)) {
            return null;
        }
        TileEntityGeneratorSC g = (TileEntityGeneratorSC) te;
        return g.hasPort(xCoord, yCoord, zCoord) ? g : null;
    }

    /** ОМ-5: the reactor's block, {x, y, z} (WAILA, the right-click's line), or null - unbound. */
    public int[] boundAt() {
        return reactor() == null ? null : new int[]{rx, ry, rz};
    }

    /** ГН-2 (WAILA): the port gets a redstone signal now. Server. */
    public boolean powered() {
        return worldObj != null && worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
    }

    /** ОМ-5: the reactor's comparator reading (the plasma's stability, the hole's mass); unbound - 0. */
    @Override
    public int comparatorLevel() {
        TileEntityGeneratorSC g = reactor();
        return g == null ? 0 : g.comparatorLevel();
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || (worldObj.getTotalWorldTime() + (xCoord + zCoord & 7)) % TICK != 0) {
            return;
        }
        boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        boolean first = !checked;
        checked = true;
        boolean rising = powered && !lastPowered;
        lastPowered = powered;
        comparatorLast = com.sc.energy.ComparatorSC.notify(this, comparatorLast);
        if (first) {
            return;
        }
        TileEntityGeneratorSC g = reactor();
        if (g == null || !g.litForPorts()) {
            return;
        }
        if (mode == STOP ? rising : !powered) {
            g.portStop(mode == STOP);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("mode", (byte) mode);
        if (bound) {
            t.setInteger("rx", rx);
            t.setInteger("ry", ry);
            t.setInteger("rz", rz);
            t.setBoolean("b", true);
        }
        nbt.setTag(NBT, t);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        NBTTagCompound t = nbt.getCompoundTag(NBT);
        mode = t.getByte("mode") == PERMIT ? PERMIT : STOP;
        bound = t.getBoolean("b");
        rx = t.getInteger("rx");
        ry = t.getInteger("ry");
        rz = t.getInteger("rz");
    }
}
