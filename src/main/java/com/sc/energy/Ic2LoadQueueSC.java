package com.sc.energy;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Energy tiles join IC2's net on the server tick after they load, not from validate(): a net
 * (Industrial Upgrade's above all) looks the new tile's neighbours up at once, and during a chunk
 * load that made Minecraft build a second, orphan instance of the neighbour - the net then fed
 * those ghosts while the real machines, the ones ticking and on screen, got nothing.
 * IC2's own documentation says the same: post EnergyTileLoadEvent once the tile is in the world.
 */
public final class Ic2LoadQueueSC {

    /** A tile that joins the net when the queue says so. */
    public interface Deferred {
        void joinEnergyNetNow();
    }

    private static final java.util.Set<TileEntity> PENDING = new java.util.LinkedHashSet<TileEntity>();

    public static void queue(TileEntity te) {
        PENDING.add(te);
    }

    /** Server stopped: drop tiles of the old world (they would keep it in memory until the next server ticks). */
    public static void clear() {
        PENDING.clear();
    }

    public static void cancel(TileEntity te) {
        PENDING.remove(te);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<TileEntity> now = new ArrayList<TileEntity>(PENDING);
        PENDING.clear();
        for (TileEntity te : now) {
            World w = te.getWorldObj();
            if (te.isInvalid() || w == null || net.minecraftforge.common.DimensionManager.getWorld(w.provider.dimensionId) != w
                    || !w.blockExists(te.xCoord, te.yCoord, te.zCoord)
                    || w.getTileEntity(te.xCoord, te.yCoord, te.zCoord) != te) {
                continue;                                     // gone again, or not the tile its block holds
            }
            ((Deferred) te).joinEnergyNetNow();
        }
    }
}
