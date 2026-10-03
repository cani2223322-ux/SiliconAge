package com.sc.energy;

import java.util.List;

import com.sc.tileentity.TileEntityQuarrySC;
import com.sc.tileentity.TileEntityWirelessSC;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

/**
 * Forge's chunk-loading callback: after a world loads, each quantum translator gets its ticket
 * back (its chunk stays loaded while the pair is up), and so does each quarry with the chunk-keeping
 * module (its "Kind" mod data is "quarry"); a ticket whose block is gone is released.
 */
public class ChunkLoaderSC implements ForgeChunkManager.LoadingCallback {

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        for (ForgeChunkManager.Ticket t : tickets) {
            int x = t.getModData().getInteger("x"), y = t.getModData().getInteger("y"), z = t.getModData().getInteger("z");
            TileEntity te = world.getTileEntity(x, y, z);
            if ("bridge".equals(t.getModData().getString("Kind"))) {
                // the controller's own world: it takes its ticket back while its portal is open; the far end's
                // ticket goes - the open controller asks for a new one on its next tick
                if (te instanceof com.sc.tileentity.TileEntityBridgeControllerSC && t.getModData().getInteger("dim") == world.provider.dimensionId) {
                    ((com.sc.tileentity.TileEntityBridgeControllerSC) te).adoptTicket(t);
                } else {
                    ForgeChunkManager.releaseTicket(t);
                }
            } else if ("bridgeRemote".equals(t.getModData().getString("Kind"))) {
                // «Дистанционный режим»: the controller keeps its own chunk while the mode is on
                if (te instanceof com.sc.tileentity.TileEntityBridgeControllerSC && t.getModData().getInteger("dim") == world.provider.dimensionId) {
                    ((com.sc.tileentity.TileEntityBridgeControllerSC) te).adoptRemoteTicket(t);
                } else {
                    ForgeChunkManager.releaseTicket(t);
                }
            } else if ("quarry".equals(t.getModData().getString("Kind"))) {
                if (te instanceof TileEntityQuarrySC) {
                    ((TileEntityQuarrySC) te).adoptTicket(t);
                } else {
                    ForgeChunkManager.releaseTicket(t);
                }
            } else if (te instanceof TileEntityWirelessSC && ((TileEntityWirelessSC) te).getKind() == TileEntityWirelessSC.QUANTUM) {
                ((TileEntityWirelessSC) te).adoptTicket(t);
            } else {
                ForgeChunkManager.releaseTicket(t);
            }
        }
    }
}
