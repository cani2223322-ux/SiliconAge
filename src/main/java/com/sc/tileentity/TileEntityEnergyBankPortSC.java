package com.sc.tileentity;

import com.sc.energy.EnergyBankMathSC;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md): a port of the Energy Bank (BlockEnergyBankSC.PORT_FIRST + tier) - the energy net's side of the
 * bank: a small buffer of its tier that the controller fills (OUT) or empties (IN) every tick, at most EnergyBankMathSC.portRate. Its
 * mode (IN / OUT) by a wrench click; NBT "scBankPort" {out, cx, cy, cz} - fixed by the coordinator; ОМ-7 appends {b} (bound).
 * <p>
 * In the net as a storage of its tier (TileEntityEnergyBase: IC2 and the mod's own net, overvoltage by its tier): IN - a sink only, OUT -
 * a source only (EnergyBankMathSC.PORT_AMPS packets of its voltage a tick; IC2 + Industrial Upgrade: PORT_AMPS through IMultiEnergySource,
 * IC2 alone: one - outPackets()). The mode switched - the net is told again. The faces against the bank's own blocks (another port
 * included) are shut: a port next to a port would pass energy round in circles, under IC2 a lower tier's one would blow up.
 */
@cpw.mods.fml.common.Optional.Interface(iface = "ic2.api.energy.tile.IMultiEnergySource", modid = "industrialupgrade")
public class TileEntityEnergyBankPortSC extends TileEntityEnergyBase implements ic2.api.energy.tile.IMultiEnergySource {

    public static final String NBT = "scBankPort";

    private boolean out;
    /** The controller whose scan found the port in its cube (the WAILA / the screen; the controller drives the port itself). */
    private int cx, cy, cz;
    private boolean bound;
    /** The faces against a bank block (bit = ForgeDirection ordinal); -1 - not looked at yet (or a neighbour's chunk not loaded). */
    private int bankMask = -1;
    /** outputFaces()'s array for the mask outFacesMask (EnergyNetSC keys its face cache on the array). */
    private ForgeDirection[] outFacesCache;
    private int outFacesMask = -1;

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

    /** ЭН-1 (the item's tag, BlockEnergyBankSC.getDrops): {eu, out} - the buffer's charge and the mode a broken port carries. */
    public static final String ITEM_NBT = "scBankPortItem";

    /** ЭН-1: what the item carries - null when there is nothing to keep (empty and IN, as a new port). */
    public NBTTagCompound writeToItem() {
        if (getEnergyStored() <= 0 && !out) {
            return null;
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("eu", getEnergyStored());
        t.setBoolean("out", out);
        return t;
    }

    /** ЭН-1 (the placed port's item): its mode and charge back (the charge capped to the buffer). */
    public void readFromItem(NBTTagCompound t) {
        if (t == null) {
            return;
        }
        if (t.getBoolean("out") != out) {
            toggleOut();
        }
        restoreEnergy(Math.min(t.getInteger("eu"), getMaxEnergyStored()));
        markDirty();
    }

    /** ЭН-3: what an IN port takes a tick at most - the controller empties its buffer by portRate, under any energy net. */
    public static long inRate(Tier t) {
        return EnergyBankMathSC.portRate(t);
    }

    /** ЭН-3: what an OUT port sends a tick at most (IC2 without Industrial Upgrade: one packet - outPackets()). */
    public static long outRate(Tier t) {
        return t == null ? 0L : (long) t.getVoltage() * outPackets();
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

    /** Industrial Upgrade's energy net: PORT_AMPS packets a tick, as the mod's own net. */
    @Override
    public boolean sendMultibleEnergyPackets() {
        return out;
    }

    @Override
    public double getMultibleEnergyPacketAmount() {
        return EnergyBankMathSC.PORT_AMPS;
    }

    /** The packets an OUT port really sends a tick: PORT_AMPS, one under IC2 without Industrial Upgrade (the tooltip, the handbook). */
    public static int outPackets() {
        return TileEntityEnergyStorageSC.overdriveWorks() ? EnergyBankMathSC.PORT_AMPS : 1;
    }

    /** The faces against the bank's blocks (any meta - a port, the casing, glass, a cell) are shut both ways. */
    private int bankFaces() {
        if (bankMask >= 0 || worldObj == null) {
            return Math.max(0, bankMask);
        }
        int m = 0;
        boolean known = true;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
            if (!worldObj.blockExists(x, y, z)) {
                known = false;                       // asked again once the chunk is there
            } else if (worldObj.getBlock(x, y, z) == com.sc.init.IdeaBlocksSC.energyBank) {
                m |= 1 << d.ordinal();
            }
        }
        if (known) {
            bankMask = m;
        }
        return m;
    }

    @Override
    public boolean acceptsFrom(ForgeDirection side) {
        return side == ForgeDirection.UNKNOWN || (bankFaces() & 1 << side.ordinal()) == 0;
    }

    @Override
    public ForgeDirection[] outputFaces() {
        int m = bankFaces();
        if (outFacesCache == null || outFacesMask != m) {
            int n = 0;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                n += (m & 1 << d.ordinal()) == 0 ? 1 : 0;
            }
            ForgeDirection[] faces = new ForgeDirection[n];
            n = 0;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if ((m & 1 << d.ordinal()) == 0) {
                    faces[n++] = d;
                }
            }
            outFacesCache = faces;
            outFacesMask = m;
        }
        return outFacesCache;
    }

    @Override
    public void validate() {
        bankMask = -1;                               // looked at again in the world it joins
        super.validate();
    }

    /** BlockEnergyBankSC: a neighbouring block changed - the shut faces looked at again; IC2 caches them on load, so it's told again. */
    public void neighbourChanged() {
        int was = bankMask;
        bankMask = -1;
        if (worldObj != null && !worldObj.isRemote && bankFaces() != was) {
            refreshEnergyNet();
        }
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
