package com.sc.item;

import java.util.HashMap;
import java.util.Map;

import com.sc.util.PlacedChunkSetSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * СТ-2 (audit 2026-10-20, ★ A): the blocks placed by players (the Singular drill's black hole gives no crumbs for them - no
 * farming), kept in their chunks' own data: "SCPlaced" (PlacedChunkSetSC's int array) and "SCPlacedV" (1 - the chunk's marks are
 * in this format; the old journal is never asked for it again). In memory only the loaded chunks' sets, no cap, no eviction; a
 * chunk's set is written with the chunk and let go once it is saved unloaded. DrillEventsSC marks and forgets (PlaceEvent, a
 * piston's move, falling sand landing; a BreakEvent once the block is gone). The old journal (PlacedBlocksSC, "SiliconAgePlaced")
 * is read only: its marks of a chunk move into the chunk when it is first loaded. Server's thread (the Forge bus); registered in
 * SCMod.preInit.
 */
public final class PlacedChunksSC {

    public static final PlacedChunksSC INSTANCE = new PlacedChunksSC();
    public static final String TAG = "SCPlaced", TAG_V = "SCPlacedV";

    /** A loaded chunk's marks; `versioned` - written in this format already (an empty one still says "nothing to take over"). */
    private static final class Entry {
        final PlacedChunkSetSC set;
        boolean versioned;

        Entry(PlacedChunkSetSC set, boolean versioned) {
            this.set = set;
            this.versioned = versioned;
        }
    }

    /** dimension -> chunk (ChunkCoordIntPair.chunkXZ2Int) -> its marks. */
    private final Map<Integer, Map<Long, Entry>> dims = new HashMap<Integer, Map<Long, Entry>>();

    private PlacedChunksSC() {
    }

    // ------------------------------------------------------------------ the API (server)

    /** A player put the block at (x, y, z) - the black hole gives no crumbs for it. Server; a chunk not loaded - false. */
    public static boolean placed(World w, int x, int y, int z) {
        Entry e = INSTANCE.entry(w, x >> 4, z >> 4, false);
        return e != null && e.set.contains(PlacedChunkSetSC.key(x, y, z));
    }

    /** Marks (x, y, z) placed (its chunk loaded; a change marks the chunk for saving - a mark already there changes nothing). */
    public static void mark(World w, int x, int y, int z) {
        int k = PlacedChunkSetSC.key(x, y, z);
        if (k < 0 || w == null || w.isRemote || !w.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
            return;
        }
        Entry e = INSTANCE.entry(w, x >> 4, z >> 4, true);
        if (e != null && e.set.add(k)) {
            w.getChunkFromChunkCoords(x >> 4, z >> 4).setChunkModified();
        }
    }

    /** Forgets (x, y, z) (the block is gone). */
    public static void forget(World w, int x, int y, int z) {
        Entry e = INSTANCE.entry(w, x >> 4, z >> 4, false);
        if (e != null && e.set.remove(PlacedChunkSetSC.key(x, y, z)) && w.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
            w.getChunkFromChunkCoords(x >> 4, z >> 4).setChunkModified();
        }
    }

    /** The loaded chunks' sets of a dimension (the world test). */
    public static int chunksHeld(World w) {
        Map<Long, Entry> m = w == null ? null : INSTANCE.dims.get(Integer.valueOf(w.provider.dimensionId));
        return m == null ? 0 : m.size();
    }

    private Entry entry(World w, int cx, int cz, boolean make) {
        if (w == null || w.isRemote || w.provider == null) {
            return null;
        }
        Integer dim = Integer.valueOf(w.provider.dimensionId);
        Map<Long, Entry> m = dims.get(dim);
        if (m == null) {
            if (!make) {
                return null;
            }
            m = new HashMap<Long, Entry>();
            dims.put(dim, m);
        }
        Long key = Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz));
        Entry e = m.get(key);
        if (e == null && make) {
            e = new Entry(new PlacedChunkSetSC(), false);
            m.put(key, e);
        }
        return e;
    }

    // ------------------------------------------------------------------ the chunks' data

    /** A chunk read from disk: its marks; a chunk from before the format - the old journal's marks of it (then it is saved anew). */
    @SubscribeEvent
    public void onChunkLoad(ChunkDataEvent.Load e) {
        Chunk c = e.getChunk();
        World w = c == null ? null : c.worldObj;
        if (w == null || w.isRemote) {
            return;
        }
        NBTTagCompound data = e.getData();
        PlacedChunkSetSC set = PlacedChunkSetSC.fromArray(data == null ? null : data.getIntArray(TAG));
        boolean versioned = data != null && data.hasKey(TAG_V);
        if (!versioned) {
            int[] old = PlacedBlocksSC.takeChunk(w, c.xPosition, c.zPosition);
            if (old != null && old.length > 0) {
                for (int k : old) {
                    set.add(k);
                }
                versioned = true;                       // taken over: written in this format from now on
                c.setChunkModified();
            }
        }
        if (set.isEmpty() && !versioned) {
            return;                                     // nothing to keep in memory
        }
        Integer dim = Integer.valueOf(w.provider.dimensionId);
        Map<Long, Entry> m = dims.get(dim);
        if (m == null) {
            m = new HashMap<Long, Entry>();
            dims.put(dim, m);
        }
        m.put(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(c.xPosition, c.zPosition)), new Entry(set, versioned));
    }

    /**
     * A chunk written: its marks go with it. Forge fires this after ChunkEvent.Unload (the chunk is no longer loaded then) - the
     * set is let go after it is written, never before.
     */
    @SubscribeEvent
    public void onChunkSave(ChunkDataEvent.Save e) {
        Chunk c = e.getChunk();
        World w = c == null ? null : c.worldObj;
        if (w == null || w.isRemote) {
            return;
        }
        Map<Long, Entry> m = dims.get(Integer.valueOf(w.provider.dimensionId));
        Long key = Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(c.xPosition, c.zPosition));
        Entry en = m == null ? null : m.get(key);
        if (en == null) {
            return;
        }
        if (!en.set.isEmpty() || en.versioned) {
            NBTTagCompound data = e.getData();
            data.setTag(TAG, new NBTTagIntArray(en.set.toArray()));
            data.setByte(TAG_V, (byte) 1);
            en.versioned = true;
        }
        if (!c.isChunkLoaded) {
            m.remove(key);                              // saved on its way out: nothing of it stays in memory
        }
    }

    /** A world unloaded: its sets go (they were saved with their chunks). */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        if (e.world != null && !e.world.isRemote) {
            dims.remove(Integer.valueOf(e.world.provider.dimensionId));
        }
    }

    /** The server stopped: nothing kept for the next one. */
    public void clearAll() {
        dims.clear();
    }
}
