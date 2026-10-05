package com.sc.tileentity;

import com.sc.bridge.BridgeMathSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A vortex cell's memory: which controller's portal it belongs to (its world and position, the opening's
 * id), which end (0 at the ring, 1 the far one) and where in the opening it sits (tileU / tileV of an n x n
 * opening, the opening's plane `axis`). Every 2 s it checks that its portal is still open - if not, it takes
 * itself away (a vortex never stays behind).
 * <p>
 * The look: the cells draw nothing; the centre cell (the "master") draws the whole portal as one disc
 * (client/BridgeVortexRendererSC), plays its hum and spins its particles. For that the clients get the opening's
 * size and plane, whether the end has a ring round it, when it opened (the growing animation) and the stability
 * (the colour, the cracks, the shaking).
 */
public class TileEntityBridgeVortexSC extends TileEntity {

    private int cdim, cx, cy, cz, end, tileU, tileV;
    private long openId;
    private int age;
    /** The opening's size (cells a side), its plane (0: along X, 1: along Z), the end has no ring (a projected end). */
    private int size = 3, axis;
    private boolean ringless;
    /** The total world time the portal opened (the clients grow the disc from it). */
    private long openedAt;
    /** The portal's stability, % (rounded to 5 for the clients - a packet only when it moves a step). */
    private int stability = 100;

    /** С3: the vortex shakes (stability under 30%). */
    public boolean isUnstable() {
        return stability < BridgeMathSC.TURBULENCE;
    }

    public int getStability() {
        return stability;
    }

    /** Server: the controller tells it once a second; the clients hear of a step (5%). */
    public void setStability(int s) {
        int q = Math.max(0, Math.min(100, s / 5 * 5));
        if (q != stability) {
            stability = q;
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    /** Only the master draws (in the translucent pass). */
    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1 && isMaster();
    }

    public void setup(int dim, int x, int y, int z, int end, long openId, int tileU, int tileV, int size, int axis, boolean ringless,
            long openedAt, int stability) {
        this.cdim = dim;
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.end = end;
        this.openId = openId;
        this.tileU = tileU;
        this.tileV = tileV;
        this.size = Math.max(1, size);
        this.axis = axis;
        this.ringless = ringless;
        this.openedAt = openedAt;
        this.stability = Math.max(0, Math.min(100, stability / 5 * 5));
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public int getTileU() {
        return tileU;
    }

    public int getTileV() {
        return tileV;
    }

    public int getEnd() {
        return end;
    }

    public long getOpenId() {
        return openId;
    }

    public int getSize() {
        return size;
    }

    public int getAxis() {
        return axis;
    }

    public boolean isRingless() {
        return ringless;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    /** The centre cell of the opening: it draws the whole disc. */
    public boolean isMaster() {
        int c = (size - 1) / 2;
        return tileU == c && tileV == c;
    }

    /** The kind (block meta): BridgeMathSC.GROUND / SPACE. */
    public int getKind() {
        return worldObj != null && worldObj.getBlockMetadata(xCoord, yCoord, zCoord) == 1 ? BridgeMathSC.SPACE : BridgeMathSC.GROUND;
    }

    /** The controller whose open portal this cell is part of, or null (server). */
    public TileEntityBridgeControllerSC controller() {
        World w = DimensionManager.getWorld(cdim);
        if (w == null || !w.blockExists(cx, cy, cz)) {
            return null;
        }
        TileEntity te = w.getTileEntity(cx, cy, cz);
        if (!(te instanceof TileEntityBridgeControllerSC)) {
            return null;
        }
        TileEntityBridgeControllerSC c = (TileEntityBridgeControllerSC) te;
        return c.isOpen() && c.getOpenId() == openId ? c : null;
    }

    public void entered(Entity e) {
        TileEntityBridgeControllerSC c = controller();
        if (c != null) {
            c.enter(e, end);
        }
    }

    @Override
    public void updateEntity() {
        if (worldObj == null) {
            return;
        }
        if (worldObj.isRemote) {
            if (isMaster()) {
                com.sc.SCMod.proxy.vortexTick(this);        // particles, the hum (client only)
            }
            return;
        }
        if (++age % 40 == 0 && controller() == null) {
            worldObj.setBlockToAir(xCoord, yCoord, zCoord);
        }
    }

    /** The master's disc reaches past its block: the whole opening and the halo round it. */
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (!isMaster()) {
            return super.getRenderBoundingBox();
        }
        double r = size * 0.5 * 1.7 + 0.5;
        return AxisAlignedBB.getBoundingBox(xCoord + 0.5 - r, yCoord + 0.5 - r, zCoord + 0.5 - r, xCoord + 0.5 + r, yCoord + 0.5 + r,
                zCoord + 0.5 + r);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 96 * 96;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        int[] a = nbt.getIntArray("Vortex");
        if (a.length >= 7) {
            cdim = a[0];
            cx = a[1];
            cy = a[2];
            cz = a[3];
            end = a[4];
            tileU = a[5];
            tileV = a[6];
        }
        if (a.length >= 10) {
            size = Math.max(1, a[7]);
            axis = a[8];
            ringless = a[9] != 0;
        }
        openId = nbt.getLong("OpenId");
        openedAt = nbt.getLong("OpenedAt");
        stability = nbt.hasKey("Stab") ? nbt.getInteger("Stab") : 100;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setIntArray("Vortex", new int[]{cdim, cx, cy, cz, end, tileU, tileV, size, axis, ringless ? 1 : 0});
        nbt.setLong("OpenId", openId);
        nbt.setLong("OpenedAt", openedAt);
        nbt.setInteger("Stab", stability);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setIntArray("Tile", new int[]{tileU, tileV, size, axis, ringless ? 1 : 0, stability});
        nbt.setLong("At", openedAt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound nbt = pkt.func_148857_g();
        int[] t = nbt.getIntArray("Tile");
        if (t.length >= 6) {
            tileU = t[0];
            tileV = t[1];
            size = Math.max(1, t[2]);
            axis = t[3];
            ringless = t[4] != 0;
            stability = t[5];
            openedAt = nbt.getLong("At");
        }
    }
}
