package com.sc.bridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * С5 familiar places and С7 scouting (docs/plan-ground-bridge.md §9): every player remembers the chunks he knows -
 * where he stood holding a Coordinator or wearing a Singular helmet with the scanner on (3x3 chunks round him),
 * the chunk of every point a Coordinator recorded and of every bridge end that opened for him - at most
 * FAMILIAR_CAP chunks, the one looked at longest ago forgotten first; and the dimensions he has already reached by
 * a Space bridge. Kept in the server's global map storage ("SiliconAgeBridgeFamiliar") by the player's name, so a
 * friend who is offline keeps his too.
 */
public class BridgeFamiliarSC extends WorldSavedData {

    public static final String NAME = "SiliconAgeBridgeFamiliar";

    /** One player's memory: an LRU of chunks and the scouted dimensions (pure - the self-test runs it). */
    public static final class Store {
        private final int cap;
        private final LinkedHashMap<Long, Boolean> chunks;
        private final Set<Integer> scouted = new HashSet<Integer>();

        public Store(int cap) {
            this.cap = Math.max(1, cap);
            final int c = this.cap;
            chunks = new LinkedHashMap<Long, Boolean>(64, 0.75F, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> e) {
                    return size() > c;
                }
            };
        }

        public static long key(int dim, int cx, int cz) {
            return ((long) (dim & 0xFFFF) << 48) | ((long) (cx & 0xFFFFFF) << 24) | (cz & 0xFFFFFF);
        }

        /** Remembers a chunk (or freshens it). @return whether it was new */
        public boolean mark(int dim, int cx, int cz) {
            return chunks.put(key(dim, cx, cz), Boolean.TRUE) == null;
        }

        /** Whether the chunk is known (freshens it, as a visit of the memory). */
        public boolean has(int dim, int cx, int cz) {
            return chunks.get(key(dim, cx, cz)) != null;
        }

        public int size() {
            return chunks.size();
        }

        public int cap() {
            return cap;
        }

        public boolean scouted(int dim) {
            return scouted.contains(dim);
        }

        /** @return whether it was new */
        public boolean scout(int dim) {
            return scouted.add(dim);
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            int[] a = new int[chunks.size() * 2];
            int i = 0;
            for (Iterator<Long> it = chunks.keySet().iterator(); it.hasNext(); ) {
                long k = it.next();
                a[i++] = (int) (k >>> 32);
                a[i++] = (int) k;
            }
            t.setTag("C", new NBTTagIntArray(a));
            int[] s = new int[scouted.size()];
            i = 0;
            for (int d : scouted) {
                s[i++] = d;
            }
            t.setTag("S", new NBTTagIntArray(s));
            return t;
        }

        void read(NBTTagCompound t) {
            chunks.clear();
            int[] a = t.getIntArray("C");
            for (int i = 0; i + 1 < a.length; i += 2) {
                chunks.put(((long) a[i] << 32) | (a[i + 1] & 0xFFFFFFFFL), Boolean.TRUE);
            }
            scouted.clear();
            for (int d : t.getIntArray("S")) {
                scouted.add(d);
            }
        }
    }

    private final Map<String, Store> players = new HashMap<String, Store>();

    public BridgeFamiliarSC(String name) {
        super(name);
    }

    public static BridgeFamiliarSC get(World w) {
        if (w == null || w.mapStorage == null) {
            return null;
        }
        BridgeFamiliarSC m = (BridgeFamiliarSC) w.mapStorage.loadData(BridgeFamiliarSC.class, NAME);
        if (m == null) {
            m = new BridgeFamiliarSC(NAME);
            w.mapStorage.setData(NAME, m);
        }
        return m;
    }

    public Store of(String player) {
        String k = player == null ? "" : player.toLowerCase(Locale.ROOT);
        Store s = players.get(k);
        if (s == null) {
            s = new Store(BridgeMathSC.FAMILIAR_CAP);
            players.put(k, s);
        }
        return s;
    }

    /** Remembers the chunk of block x z (and `around` chunks each way) for a player. */
    public void markBlock(String player, int dim, int x, int z, int around) {
        if (player == null || player.length() == 0) {
            return;
        }
        Store s = of(player);
        boolean any = false;
        for (int dx = -around; dx <= around; dx++) {
            for (int dz = -around; dz <= around; dz++) {
                any |= s.mark(dim, (x >> 4) + dx, (z >> 4) + dz);
            }
        }
        if (any) {
            markDirty();
        }
    }

    public boolean familiar(String player, int dim, int x, int z) {
        return player != null && player.length() > 0 && of(player).has(dim, x >> 4, z >> 4);
    }

    public boolean scouted(String player, int dim) {
        return player != null && of(player).scouted(dim);
    }

    public void scout(String player, int dim) {
        if (player != null && player.length() > 0 && of(player).scout(dim)) {
            markDirty();
        }
    }

    // ------------------------------------------------------------------ the players' side

    /** A player stands here with a Coordinator in hand (around 0) or the scanner on (around 1). */
    public static void visit(EntityPlayer p, int around) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || p instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        BridgeFamiliarSC f = get(p.worldObj);
        if (f != null) {
            f.markBlock(p.getCommandSenderName(), p.worldObj.provider.dimensionId, (int) Math.floor(p.posX), (int) Math.floor(p.posZ), around);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        players.clear();
        NBTTagCompound all = nbt.getCompoundTag("Players");
        for (Object o : all.func_150296_c()) {
            String k = (String) o;
            Store s = new Store(BridgeMathSC.FAMILIAR_CAP);
            s.read(all.getCompoundTag(k));
            players.put(k, s);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagCompound all = new NBTTagCompound();
        for (Map.Entry<String, Store> e : players.entrySet()) {
            all.setTag(e.getKey(), e.getValue().write());
        }
        nbt.setTag("Players", all);
    }
}
