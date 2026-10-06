package com.sc.item;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

/**
 * The Singular drill's world events (common side; registered on MinecraftForge.EVENT_BUS and the FML bus):
 * the record of player-placed blocks (PlacedBlocksSC) that the black hole's singularity crumbs ignore.
 * Last in line and only for events nobody cancelled.
 */
public final class DrillEventsSC {

    public static final DrillEventsSC INSTANCE = new DrillEventsSC();

    private DrillEventsSC() {
    }

    /** A player placed a block (doors, beds: every part): remember it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (e.world == null || e.world.isRemote) {
            return;
        }
        if (e instanceof BlockEvent.MultiPlaceEvent) {
            for (Object o : ((BlockEvent.MultiPlaceEvent) e).getReplacedBlockSnapshots()) {
                BlockSnapshot s = (BlockSnapshot) o;
                PlacedBlocksSC.mark(e.world, s.x, s.y, s.z);
            }
            return;
        }
        PlacedBlocksSC.mark(e.world, e.x, e.y, e.z);
    }

    /**
     * A block broken by a player: it is no longer a placed one. Vanilla fires the clicked block's event before the
     * drill's onBlockStartBreak - so for a Singular drill in hand whether it was placed is kept for that player
     * (wasPlaced) for the drill to read this tick.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.world == null || e.world.isRemote) {
            return;
        }
        EntityPlayer p = e.getPlayer();
        if (p != null && com.sc.util.ToolLevelSC.isDrill(p.getCurrentEquippedItem())) {
            NBTTagCompound data = p.getEntityData();
            if (PlacedBlocksSC.placed(e.world, e.x, e.y, e.z)) {
                data.setIntArray(BROKE_PLACED, new int[]{e.world.provider.dimensionId, e.x, e.y, e.z});
                data.setLong(BROKE_PLACED_AT, e.world.getTotalWorldTime());
            } else {
                data.removeTag(BROKE_PLACED);
            }
        }
        PlacedBlocksSC.forget(e.world, e.x, e.y, e.z);
    }

    /** Player data: the last placed block broken with the Singular drill in hand {dim, x, y, z} and its world tick. */
    private static final String BROKE_PLACED = "scDrillBrokePlaced", BROKE_PLACED_AT = "scDrillBrokePlacedAt";

    /** The block at x, y, z, whose BreakEvent this player fired this tick, was a player-placed one (or still is). */
    static boolean wasPlaced(EntityPlayer p, int x, int y, int z) {
        if (PlacedBlocksSC.placed(p.worldObj, x, y, z)) {
            return true;
        }
        NBTTagCompound data = p.getEntityData();
        int[] a = data.getIntArray(BROKE_PLACED);
        return a.length == 4 && a[0] == p.worldObj.provider.dimensionId && a[1] == x && a[2] == y && a[3] == z
                && data.getLong(BROKE_PLACED_AT) == p.worldObj.getTotalWorldTime();
    }
}
