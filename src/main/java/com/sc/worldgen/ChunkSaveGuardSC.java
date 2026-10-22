package com.sc.worldgen;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Closes a window of Forge 1.7.10's chunk saving (the crash of 2026-10-22, "Exception ticking world": a NULL chunk in WorldServer's
 * block ticks, World.func_147467_a). AnvilChunkLoader.writeNextIO (the File IO thread) takes a chunk off its save queue
 * (chunksToRemove / pendingAnvilChunksCoordinates, under syncLockObject) and only then compresses it and writes it into its region
 * file - for those milliseconds the chunk is neither queued nor on disk. Asked for then:
 * <ul>
 * <li>ChunkProviderServer.loadChunk's chunkExists answered "yes" a moment before (still queued), ChunkIOExecutor's read
 * (loadChunk__Async) finds nothing, ChunkIOProvider.callStage2 makes the chunk anew (originalLoadChunk) but the executor hands back
 * the read's NULL - whoever asked crashes (the block ticks: World.func_147467_a; a block's World.getBlock);</li>
 * <li>asked a moment later, chunkExists says "no": the chunk is made anew from the seed and its saved one is overwritten later (what
 * was built or dug in it lost); a chunk saved before comes back as its older copy.</li>
 * </ul>
 * Vanilla asks for such chunks all the time while a player flies fast: the blocks' random ticks at the view's edge (grass, lava)
 * load the chunk past it, each save queues every loaded chunk no player watches for unloading, and the next ticks ask them again -
 * the embassy fly-through world test counted about 900 such asks of chunks still queued in 100 s of a flight at view 16, with up to
 * 2700 chunks in the queue (the File IO thread writes about 100 a second).
 * <p>
 * The guard: every server world whose chunk loader is Forge's own AnvilChunkLoader saves through Loader (WorldEvent.Load, before
 * its first save) - the same loader, but a chunk taken off the queue stays readable (WRITING, under the queue's own lock) until its
 * bytes are in the region file; chunkExists and loadChunk__Async look at the queue and the chunks being written together, then at
 * the region file (Forge's own - super - for the region file's part). Another mod's loader is left alone; -Dsc.noChunkSaveGuard=true
 * leaves Forge's own everywhere; one INFO line a dimension says which. The guard checks itself: every save it is handed
 * (addChunkToPending) must be in the queue as Forge keeps it, every queued element a PendingChunk - else (a hybrid server, another
 * mod's patch of AnvilChunkLoader) it is broken: one WARN, and from then on all its methods are Forge's own (super). A chunk read
 * from the queue or from the writing is a copy (ChunkDataEvent.Load's handlers may change it while the File IO thread writes it).
 * Registered by SCMod on Forge's EVENT_BUS.
 */
public final class ChunkSaveGuardSC {

    public static final ChunkSaveGuardSC INSTANCE = new ChunkSaveGuardSC();
    /** The JVM property that leaves Forge's own chunk saving. */
    public static final String OFF = "sc.noChunkSaveGuard";

    private ChunkSaveGuardSC() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load e) {
        if (e.world instanceof WorldServer && !e.world.isRemote) {
            install((WorldServer) e.world);
        }
    }

    /** `w` saves its chunks through the guard (Loader) now, and it is whole (its self-check found nothing wrong). */
    public static boolean guarded(WorldServer w) {
        IChunkLoader l = w == null || w.theChunkProviderServer == null ? null : w.theChunkProviderServer.currentChunkLoader;
        return l instanceof Loader && !((Loader) l).broken();
    }

    /**
     * Puts the guard in place of `w`'s chunk loader if that is Forge's own AnvilChunkLoader with nothing queued (a world just
     * loaded); one INFO line says which. @return whether `w` saves through the guard now
     */
    public static boolean install(WorldServer w) {
        ChunkProviderServer cp = w == null ? null : w.theChunkProviderServer;
        IChunkLoader l = cp == null ? null : cp.currentChunkLoader;
        int dim = w == null || w.provider == null ? 0 : w.provider.dimensionId;
        if (l instanceof Loader) {
            return true;
        }
        if (Boolean.getBoolean(OFF) || l == null || l.getClass() != AnvilChunkLoader.class) {
            FMLLog.info("[Silicon Age] chunk save guard: dimension %d left as it is - %s", dim, Boolean.getBoolean(OFF) ? "-D" + OFF
                    : l == null ? "no chunk loader" : "another mod's chunk loader " + l.getClass().getName());
            return false;
        }
        AnvilChunkLoader old = (AnvilChunkLoader) l;
        try {
            Loader guard = new Loader(old.chunkSaveLocation, "dimension " + dim);
            Object lock = field(old, LOCK);
            synchronized (lock) {
                int queued = ((List<?>) field(old, QUEUE)).size();
                if (queued > 0) {
                    FMLLog.info("[Silicon Age] chunk save guard: dimension %d left as it is - %d chunks already queued for saving", dim, queued);
                    return false;                               // never at a world's load
                }
                cp.currentChunkLoader = guard;
            }
            FMLLog.info("[Silicon Age] chunk save guard: dimension %d saves through it (%s in place of %s)", dim, guard.getClass().getName(),
                    old.getClass().getName());
            return true;
        } catch (Throwable t) {
            FMLLog.warning("[Silicon Age] chunk save guard: dimension %d left as it is (%s) - Forge's own chunk saving stays", dim, t);
            return false;
        }
    }

    /** AnvilChunkLoader's private fields (SRG, MCP) and its PendingChunk's. */
    private static final String[] QUEUE = {"field_75828_a", "chunksToRemove"}, QUEUED = {"field_75826_b", "pendingAnvilChunksCoordinates"},
            LOCK = {"field_75827_c", "syncLockObject"}, P_AT = {"field_76548_a", "chunkCoordinate"}, P_NBT = {"field_76547_b", "nbtTags"};

    private static Field find(Class<?> c, String[] names) throws NoSuchFieldException {
        for (String n : names) {
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // the other name
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + names[1]);
    }

    private static Object field(Object o, String[] names) throws Exception {
        return find(AnvilChunkLoader.class, names).get(o);
    }

    /**
     * Forge's AnvilChunkLoader with the window closed (see the class comment): its queue, its lock and its writing as they are; a chunk
     * the File IO thread took off the queue is in `writing` (the lock held while it moves) until its region file has it. Broken (its
     * self-check, or a queue element it can't read): Forge's own from then on.
     */
    public static final class Loader extends AnvilChunkLoader {
        private final List<?> queue;
        private final Set<?> queued;
        private final Object lock;
        private final Field at, nbt;
        private final String name;
        /** The chunks being written now (the File IO thread's - one at a time) and their saves. Under `lock`. */
        private final Map<ChunkCoordIntPair, NBTTagCompound> writing = new HashMap<ChunkCoordIntPair, NBTTagCompound>();
        /** The queue isn't Forge's as this guard knows it: every method is Forge's own (super). */
        private volatile boolean broken;

        public Loader(File dir) throws Exception {
            this(dir, dir.getPath());
        }

        public Loader(File dir, String name) throws Exception {
            super(dir);
            this.name = name;
            queue = (List<?>) field(this, QUEUE);
            queued = (Set<?>) field(this, QUEUED);
            lock = field(this, LOCK);
            Class<?> pending = Class.forName(AnvilChunkLoader.class.getName() + "$PendingChunk");
            at = find(pending, P_AT);
            nbt = find(pending, P_NBT);
            if (queue == null || queued == null || lock == null) {
                throw new IllegalStateException("AnvilChunkLoader's queue not found");
            }
        }

        /** Switched itself off (its self-check). */
        public boolean broken() {
            return broken;
        }

        private void breakDown(String why, Throwable t) {
            if (!broken) {
                broken = true;
                FMLLog.warning("[Silicon Age] chunk save guard: %s - %s%s; Forge's own chunk saving from now on", name, why, t == null ? "" : " (" + t + ")");
            }
        }

        /** Being written now (the test's look). */
        public boolean writing(int x, int z) {
            synchronized (lock) {
                return writing.containsKey(new ChunkCoordIntPair(x, z));
            }
        }

        /** Queued for writing (the test's look). */
        public boolean queued(int x, int z) {
            synchronized (lock) {
                return queued.contains(new ChunkCoordIntPair(x, z));
            }
        }

        /** The self-check: the save just handed over is in the queue as Forge keeps it (the lock held over both - the writer waits). */
        @Override
        protected void addChunkToPending(ChunkCoordIntPair c, NBTTagCompound tag) {
            synchronized (lock) {
                super.addChunkToPending(c, tag);
                if (!broken && queuedSave(c) != tag) {
                    breakDown("a chunk handed over for saving (" + c.chunkXPos + "," + c.chunkZPos + ") isn't in the save queue as Forge 1.7.10 keeps it", null);
                }
            }
        }

        @Override
        public boolean chunkExists(World w, int x, int z) {
            if (!broken) {
                ChunkCoordIntPair c = new ChunkCoordIntPair(x, z);
                synchronized (lock) {
                    if (writing.containsKey(c) || queuedSave(c) != null) {
                        return true;
                    }
                }
            }
            return super.chunkExists(w, x, z);                  // the queue looked at again, then the region file - Forge's own
        }

        @Override
        public Object[] loadChunk__Async(World w, int x, int z) throws IOException {
            if (!broken) {
                ChunkCoordIntPair c = new ChunkCoordIntPair(x, z);
                NBTTagCompound tag;
                synchronized (lock) {
                    tag = queuedSave(c);                        // the newest first: queued again while an older copy is written
                    if (tag == null) {
                        tag = writing.get(c);
                    }
                }
                if (tag != null) {
                    return checkedReadChunkFromNBT__Async(w, x, z, (NBTTagCompound) tag.copy());   // the writer's own stays untouched
                }
            }
            return super.loadChunk__Async(w, x, z);             // neither queued nor being written: its region file has it, or nothing does
        }

        @Override
        public boolean writeNextIO() {
            if (broken) {
                return super.writeNextIO();
            }
            ChunkCoordIntPair c = null;
            NBTTagCompound tag = null;
            synchronized (lock) {
                if (queue.isEmpty()) {
                    return false;
                }
                try {
                    Object p = queue.get(0);
                    c = (ChunkCoordIntPair) at.get(p);
                    tag = (NBTTagCompound) nbt.get(p);
                    if (c == null || tag == null) {
                        throw new IllegalStateException("an empty save");
                    }
                } catch (Exception e) {
                    breakDown("a save queue element it can't read", e);
                }
                if (!broken) {
                    writing.put(c, tag);                        // readable while it is written - before it leaves the queue
                    queue.remove(0);
                    queued.remove(c);
                }
            }
            if (broken) {
                return super.writeNextIO();                     // the lock let go first: the disk is written outside it
            }
            try {
                DataOutputStream out = RegionFileCache.getChunkOutputStream(chunkSaveLocation, c.chunkXPos, c.chunkZPos);
                CompressedStreamTools.write(tag, out);
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                synchronized (lock) {
                    if (writing.get(c) == tag) {
                        writing.remove(c);
                    }
                }
            }
            return true;
        }

        /** The queued save of `c` (the lock held), or null; a queue it can't read breaks the guard (null then). */
        private NBTTagCompound queuedSave(ChunkCoordIntPair c) {
            if (broken || !queued.contains(c)) {
                return null;
            }
            try {
                for (Object p : queue) {
                    if (c.equals(at.get(p))) {
                        return (NBTTagCompound) nbt.get(p);
                    }
                }
            } catch (Exception e) {
                breakDown("a save queue element it can't read", e);
            }
            return null;
        }
    }
}
