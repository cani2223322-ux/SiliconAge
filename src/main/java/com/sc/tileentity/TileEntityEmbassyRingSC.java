package com.sc.tileentity;

import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.embassy.FlightSC;
import com.sc.settle.SettlementRegistrySC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.4, rule ПЯ-3): «Ядро Большого кольца» - a light tile under the Great Ring's middle on
 * the Vokzal (package S's ring piece places the block and calls setup(embassyId)); not a TileEntityBridgeControllerSC - nobody points
 * it anywhere: the flights are the server's (FlightSC's window of its embassy). It shows the window: FlightSC.setPhase -> setPhase
 * (phase and its ticks left) -> the description packet -> the clients' vortex and the coils' glow (RingClientSC's renderer).
 * Unbreakable, no inventory, no energy.
 * <p>
 * Fixed (the coordinator's): the class, the NBT key NBT {id}, setup / embassyId / setPhase / phase / ticksLeft, the description
 * packet's fields (p, t). R's: the client's countdown between packets, anything visual it keeps.
 * <p>
 * R's details: the description packet also carries where the ring's middle is from this block and which way the ring stands (dx,
 * dy, dz, a - the server reads them off the embassy's record: its P_RING point and the dock's beacon; a block placed by hand has
 * none - the client guesses: the middle RING_DY over it, the plane across X unless a coil stands RING_R off along Z); a client
 * counts the phase down between the server's packets and, at its end, goes on to the next phase itself (COUNTDOWN -> VORTEX ->
 * COOL -> IDLE: the server's next packet confirms it), keeps how long the phase has been on (age - the renderer's fades) and makes
 * the coils' sparks every other tick (BlockEmbassyRingSC.sparks). The server's tile drops a phase whose window is gone (a window
 * closed by an error, a restart: never a vortex burning on).
 */
public class TileEntityEmbassyRingSC extends TileEntity {

    public static final String NBT = "scRing";
    /** The ring's plane: across X (its normal along Z) / across Z (its normal along X); unknown yet. */
    public static final int AXIS_X = 0, AXIS_Z = 1, AXIS_UNKNOWN = -1;

    /** The embassy (settlement id) whose ring this is (-1: not set up - a placed one). */
    private int embassyId = -1;
    /** The window's phase (EmbassyRulesSC.PHASE_*) and its ticks left - the server's, and what a client last heard. */
    private int phase = EmbassyRulesSC.PHASE_IDLE, ticksLeft;
    /** R, the client: the ring's middle from this block, its plane, the ticks the phase has been on (not saved). */
    private int ringDx, ringDy = EmbassyLayoutSC.RING_DY, ringDz, axis = AXIS_UNKNOWN, age;

    public TileEntityEmbassyRingSC() {
    }

    /** The ring piece, right after placing the block: the ring of embassy `id`. */
    public void setup(int id) {
        embassyId = id;
        markDirty();
    }

    public int embassyId() {
        return embassyId;
    }

    public int phase() {
        return phase;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    /** Server (FlightSC): the window's phase changed - the clients are told. */
    public void setPhase(int newPhase, int ticks) {
        if (newPhase == phase && Math.abs(ticks - ticksLeft) < 20) {
            ticksLeft = ticks;
            return;
        }
        phase = newPhase;
        ticksLeft = ticks;
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ------------------------------------------------------------------ R: what the renderer reads (client)

    /** The ring's middle from this block (blocks). */
    public int ringDx() {
        return ringDx;
    }

    public int ringDy() {
        return ringDy;
    }

    public int ringDz() {
        return ringDz;
    }

    /** The ring's plane (AXIS_X / AXIS_Z; a hand-placed one: guessed from the coils round its middle). */
    public int axis() {
        if (axis == AXIS_UNKNOWN && worldObj != null) {
            int y = yCoord + ringDy, r = EmbassyLayoutSC.RING_R;
            boolean alongZ = !worldObj.isAirBlock(xCoord + ringDx, y, zCoord + ringDz + r) && worldObj.isAirBlock(xCoord + ringDx + r, y, zCoord + ringDz);
            return alongZ ? AXIS_Z : AXIS_X;
        }
        return axis;
    }

    /** Ticks the current phase has been on (as this side counted it). */
    public int age() {
        return age;
    }

    /** A phase's whole length (ticks; IDLE 0). */
    public static int length(int phase) {
        switch (phase) {
            case EmbassyRulesSC.PHASE_COUNTDOWN:
                return EmbassyRulesSC.COUNTDOWN_TICKS;
            case EmbassyRulesSC.PHASE_VORTEX:
                return EmbassyRulesSC.VORTEX_TICKS;
            case EmbassyRulesSC.PHASE_COOL:
                return EmbassyRulesSC.COOL_TICKS;
            default:
                return 0;
        }
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (ticksLeft > 0) {
            ticksLeft--;                                        // both sides count down between the server's packets
        }
        age++;
        if (worldObj != null && worldObj.isRemote && phase != EmbassyRulesSC.PHASE_IDLE && (age & 1) == 0) {
            com.sc.block.BlockEmbassyRingSC.sparks(worldObj, this, worldObj.rand);   // the coils' sparks (a client's own particles)
        }
        if (ticksLeft > 0 || phase == EmbassyRulesSC.PHASE_IDLE || worldObj == null) {
            return;
        }
        if (worldObj.isRemote) {
            int next = phase == EmbassyRulesSC.PHASE_COOL ? EmbassyRulesSC.PHASE_IDLE : phase + 1;
            phase = next;                                       // the phase ran out here first: on to the next, the server confirms
            ticksLeft = length(next);
            age = 0;
        } else if (FlightSC.phase(embassyId) == EmbassyRulesSC.PHASE_IDLE) {
            setPhase(EmbassyRulesSC.PHASE_IDLE, 0);             // no window any more (closed by an error, the server restarted)
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", embassyId);
        nbt.setTag(NBT, t);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        embassyId = nbt.hasKey(NBT) ? nbt.getCompoundTag(NBT).getInteger("id") : -1;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("p", phase);
        t.setInteger("t", ticksLeft);
        geometry(t);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        int was = phase;
        phase = t.getInteger("p");
        ticksLeft = t.getInteger("t");
        if (phase != was) {
            age = Math.max(0, length(phase) - ticksLeft);       // joined mid-phase: as long as it has really been on
        }
        if (t.hasKey("a")) {
            axis = t.getByte("a") == AXIS_Z ? AXIS_Z : AXIS_X;
            ringDx = t.getInteger("dx");
            ringDy = t.getInteger("dy");
            ringDz = t.getInteger("dz");
        }
    }

    /** The server: the ring's middle and plane from the embassy's record (its P_RING point; the street runs from the beacon to it). */
    private void geometry(NBTTagCompound t) {
        SettlementRegistrySC reg = worldObj == null || worldObj.isRemote || embassyId < 0 ? null : SettlementRegistrySC.get(worldObj);
        SettlementRegistrySC.Settlement s = reg == null ? null : reg.byId(embassyId);
        int[] r = s == null ? null : s.point(EmbassyLayoutSC.P_RING);
        int reach = EmbassyLayoutSC.RING_R + EmbassyLayoutSC.RING_DY + 4;
        if (r == null || Math.abs(r[0] - xCoord) > reach || Math.abs(r[1] - yCoord) > reach || Math.abs(r[2] - zCoord) > reach) {
            return;                                             // not this ring's record: the client guesses
        }
        t.setByte("a", (byte) (Math.abs(r[0] - s.dockX) >= Math.abs(r[2] - s.dockZ) ? AXIS_Z : AXIS_X));
        t.setInteger("dx", r[0] - xCoord);
        t.setInteger("dy", r[1] - yCoord);
        t.setInteger("dz", r[2] - zCoord);
    }

    /** The vortex and the ring are big: drawn from afar (the TESR's box round the ring's middle). */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.AxisAlignedBB getRenderBoundingBox() {
        int r = EmbassyLayoutSC.RING_R + 3, x = xCoord + ringDx, y = yCoord + ringDy, z = zCoord + ringDz;
        return net.minecraft.util.AxisAlignedBB.getBoundingBox(Math.min(x, xCoord) - r, Math.min(y, yCoord) - r, Math.min(z, zCoord) - r,
                Math.max(x, xCoord) + r + 1, Math.max(y, yCoord) + r + 1, Math.max(z, zCoord) + r + 1);
    }
}
