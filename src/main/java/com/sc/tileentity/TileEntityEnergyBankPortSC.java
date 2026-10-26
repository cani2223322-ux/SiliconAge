package com.sc.tileentity;

import com.sc.energy.EnergyBankMathSC;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;

import net.minecraft.nbt.NBTTagCompound;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md): a port of the Energy Bank (BlockEnergyBankSC.PORT_FIRST + tier) - the energy net's side of the
 * bank: a small buffer of its tier that the controller fills (OUT) or empties (IN) every tick, at most EnergyBankMathSC.portRate. Its
 * mode (IN / OUT) by a wrench click; NBT "scBankPort" {out, cx, cy, cz} - fixed by the coordinator; ОМ-7 appends {b} (bound).
 * <p>
 * In the net as a storage of its tier (TileEntityEnergyBase: IC2 and the mod's own net, overvoltage by its tier): IN - a sink only, OUT -
 * a source only (EnergyBankMathSC.PORT_AMPS packets of its voltage a tick; IC2 takes one). The mode switched - the net is told again.
 */
public class TileEntityEnergyBankPortSC extends TileEntityEnergyBase {

    public static final String NBT = "scBankPort";

    private boolean out;
    /** The controller whose scan found the port in its cube (the WAILA / the screen; the controller drives the port itself). */
    private int cx, cy, cz;
    private boolean bound;

    /** No-arg for the NBT's reflection: the tier comes from the saved "TierSC" (TileEntityEnergyBase), else the block's meta. */
    public TileEntityEnergyBankPortSC() {
        super();
    }

    public TileEntityEnergyBankPortSC(Tier tier) {
        super(tier);
    }

    public boolean isOut() {
        return out;
    }

    /** ОМ-7 (a wrench click): IN <-> OUT; the net is told again. @return the new mode (true - out) */
    public boolean toggleOut() {
        out = !out;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            refreshEnergyNet();
        }
        return out;
    }

    /** ОМ-7 (the controller's scan): the port is in the cube of the controller at (x, y, z). */
    public void bind(int x, int y, int z) {
        if (!bound || cx != x || cy != y || cz != z) {
            cx = x;
            cy = y;
            cz = z;
            bound = true;
            markDirty();
        }
    }

    public boolean isBound() {
        return bound;
    }

    public int[] controllerAt() {
        return bound ? new int[]{cx, cy, cz} : null;
    }

    /** The room left in the buffer. */
    public int room() {
        return Math.max(0, getMaxEnergyStored() - getEnergyStored());
    }

    /** ОМ-7 (the controller, OUT): `eu` into the buffer (no more than the room). @return EU put */
    public int bankPut(int eu) {
        int n = Math.max(0, Math.min(eu, room()));
        addEnergy(n);
        return n;
    }

    /** ОМ-7 (the controller, IN): up to `eu` out of the buffer. @return EU taken */
    public int bankTake(int eu) {
        return eu <= 0 ? 0 : removeEnergy(eu);
    }

    @Override
    public boolean isEnergySink() {
        return !out;
    }

    @Override
    public boolean isEnergySource() {
        return out;
    }

    /** OUT: PORT_AMPS packets of its voltage a tick (EnergyNetSC). */
    @Override
    public int packetsPerTick() {
        return EnergyBankMathSC.PORT_AMPS;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        NBTTagCompound t = nbt.getCompoundTag(NBT);
        out = t.getBoolean("out");
        bound = t.getBoolean("b");
        cx = t.getInteger("cx");
        cy = t.getInteger("cy");
        cz = t.getInteger("cz");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("out", out);
        t.setBoolean("b", bound);
        t.setInteger("cx", cx);
        t.setInteger("cy", cy);
        t.setInteger("cz", cz);
        nbt.setTag(NBT, t);
    }
}
