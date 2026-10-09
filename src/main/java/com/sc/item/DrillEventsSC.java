package com.sc.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
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
                unpend(e.world, s.x, s.y, s.z);
                PlacedBlocksSC.mark(e.world, s.x, s.y, s.z);
            }
            return;
        }
        unpend(e.world, e.x, e.y, e.z);
        PlacedBlocksSC.mark(e.world, e.x, e.y, e.z);
    }

    /** A placement after a break in the same tick wins: the queued forget would drop the new mark. */
    private void unpend(World w, int x, int y, int z) {
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending q = it.next();
            if (q.w == w && q.x == x && q.y == y && q.z == z) {
                it.remove();
            }
        }
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
        // Б-2: forgotten only once the block is really gone - a BreakEvent is also how protection checks ask
        // (the field generator's claim probe, the quarry, wrenches, other mods), and those leave it standing
        if (pending.size() < PENDING_MAX) {
            pending.add(new Pending(e.world, e.x, e.y, e.z, e.block));
        }
    }

    private static final int PENDING_MAX = 65536, FALLING_MAX = 4096;
    private final List<Pending> pending = new ArrayList<Pending>();
    private final List<EntityFallingBlock> falling = new ArrayList<EntityFallingBlock>();

    private static final class Pending {
        final World w;
        final int x, y, z;
        final Block block;

        Pending(World w, int x, int y, int z, Block block) {
            this.w = w;
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

    /** Б-4: falling sand / gravel lands somewhere else - where it lands counts as placed. */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.entity instanceof EntityFallingBlock && falling.size() < FALLING_MAX) {
            falling.add((EntityFallingBlock) e.entity);
        }
    }

    /** Server ticks counted for the piston scan (every other tick). */
    private int pistonTick;

    /**
     * End of every server tick: broken blocks that are really gone lose their mark; a block a piston is moving
     * counts as placed where it arrives (Б-4: no Forge event for either; checked every other tick); landed falling
     * blocks likewise.
     */

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        for (Pending q : pending) {
            if (q.w.blockExists(q.x, q.y, q.z) && q.w.getBlock(q.x, q.y, q.z) != q.block) {
                PlacedBlocksSC.forget(q.w, q.x, q.y, q.z);
            }
        }
        pending.clear();
        for (Iterator<EntityFallingBlock> it = falling.iterator(); it.hasNext(); ) {
            EntityFallingBlock f = it.next();
            if (f.isDead) {
                it.remove();
                int x = MathHelper.floor_double(f.posX), y = MathHelper.floor_double(f.posY), z = MathHelper.floor_double(f.posZ);
                if (f.worldObj.blockExists(x, y, z) && !f.worldObj.isAirBlock(x, y, z)) {
                    PlacedBlocksSC.mark(f.worldObj, x, y, z);
                }
            } else if (DimensionManager.getWorld(f.worldObj.provider.dimensionId) != f.worldObj
                    || f.worldObj.getEntityByID(f.getEntityId()) != f) {
                it.remove(); // its chunk / world unloaded: never dies; a reload re-adds a fresh instance
            }
        }
        if (++pistonTick % 2 != 0) {
            return;                                         // a moving block's piston tile lives 2-3 ticks: every other tick finds it
        }
        for (WorldServer w : DimensionManager.getWorlds()) {
            for (Object o : w.loadedTileEntityList) {
                if (o instanceof TileEntityPiston) {
                    TileEntityPiston te = (TileEntityPiston) o;
                    PlacedBlocksSC.mark(w, te.xCoord, te.yCoord, te.zCoord);
                }
            }
        }
    }

    /** Server stop: no World kept into the next singleplayer session. */
    public void clearAll() {
        pending.clear();
        falling.clear();
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
