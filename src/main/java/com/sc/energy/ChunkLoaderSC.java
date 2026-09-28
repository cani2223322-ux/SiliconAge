package com.sc.energy;

import java.util.List;

import com.sc.tileentity.TileEntityWirelessSC;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

/**
 * Forge's chunk-loading callback: after a world loads, each quantum translator gets its ticket
 * back (its chunk stays loaded while the pair is up); a ticket whose block is gone is released.
 */
public class ChunkLoaderSC implements ForgeChunkManager.LoadingCallback {

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        for (ForgeChunkManager.Ticket t : tickets) {
            int x = t.getModData().getInteger("x"), y = t.getModData().getInteger("y"), z = t.getModData().getInteger("z");
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityWirelessSC && ((TileEntityWirelessSC) te).getKind() == TileEntityWirelessSC.QUANTUM) {
                ((TileEntityWirelessSC) te).adoptTicket(t);
            } else {
                ForgeChunkManager.releaseTicket(t);
            }
        }
    }
}
