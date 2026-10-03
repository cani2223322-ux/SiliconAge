package com.sc.tileentity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A vortex cell's memory: which controller's portal it belongs to (its world and position, the opening's
 * id), which end (0 at the ring, 1 the far one) and which tile of the swirl it shows. Every 2 s it checks
 * that its portal is still open - if not, it takes itself away (a vortex never stays behind).
 */
public class TileEntityBridgeVortexSC extends TileEntity {

    private int cdim, cx, cy, cz, end, tileU, tileV;
    private long openId;
    private int age;
    /** С3: the vortex shakes (stability under 30%) - the client draws this cell jittering (BridgeVortexRendererSC). */
    private boolean unstable;

    public boolean isUnstable() {
        return unstable;
    }

    /** Server: the controller tells it once a second; the clients hear of a change. */
    public void setUnstable(boolean u) {
        if (u != unstable) {
            unstable = u;
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    /** The shaking cell is drawn by its renderer in the translucent pass. */
    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    public void setup(int dim, int x, int y, int z, int end, long openId, int tileU, int tileV) {
        this.cdim = dim;
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.end = end;
        this.openId = openId;
        this.tileU = tileU;
        this.tileV = tileV;
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
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (++age % 40 == 0 && controller() == null) {
            worldObj.setBlockToAir(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        int[] a = nbt.getIntArray("Vortex");
        if (a.length == 7) {
            cdim = a[0];
            cx = a[1];
            cy = a[2];
            cz = a[3];
            end = a[4];
            tileU = a[5];
            tileV = a[6];
        }
        openId = nbt.getLong("OpenId");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setIntArray("Vortex", new int[]{cdim, cx, cy, cz, end, tileU, tileV});
        nbt.setLong("OpenId", openId);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setIntArray("Tile", new int[]{tileU, tileV});
        nbt.setBoolean("U", unstable);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        int[] t = pkt.func_148857_g().getIntArray("Tile");
        if (t.length == 2) {
            tileU = t[0];
            tileV = t[1];
            unstable = pkt.func_148857_g().getBoolean("U");
            if (worldObj != null) {
                worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
            }
        }
    }
}
