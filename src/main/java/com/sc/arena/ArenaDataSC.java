package com.sc.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.dim.SubstrateSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §4.3, rules ПУ-11, ПС-56): the Breakdown Epicentres of this world -
 * WorldSavedData "SCSi14ArenaRec" in the Substrate's perWorldStorage (DIM-1414/data/SCSi14ArenaRec.dat - not "SCSi14Arena": that is
 * the arena structure's own MapGenStructureData, the same storage; a property of the world; a
 * deleted DIM-1414 starts afresh, as the cells' and the weather's). One record a region whose arena's structure start was made
 * (MapGenArenaSC.getStructureStart, package A - before any of its chunks):
 * <ul>
 * <li>EMPTY - its ground was explored before Ф5 (a world of 0.2.x): no arena there, ever; the Arena Coordinator passes it over;</li>
 * <li>READY - built, waiting for a Breakdown Key; FIGHT - a key in the Console, the Warden up (fightId, bossUuid); KEYED - a fight
 * reset (every player away RESET_AWAY_TICKS, the Warden gone): the key stays in the Console, a click starts it again.</li>
 * </ul>
 * Who writes what (fixed): package A - state, keyIn, fightId, bossUuid, fightStartedAt, the cracks' clean-up; package B (while the
 * Warden is loaded and ticks) - lastPlayerAt, maxPlayers, the cracks it makes (addCrack). Times are the world's total time (the
 * Overworld's clock through DerivedWorldInfo: it runs while the Substrate is unloaded - the reset is decided when the arena loads
 * again). `extra` - each package's own fields under its own key ("A", "B"). Server thread only. Shared core: only the coordinator
 * edits this file.
 */
public class ArenaDataSC extends WorldSavedData {

    public static final String NAME = "SCSi14ArenaRec";
    /** The record's states (fixed, append only). */
    public static final int EMPTY = 0, READY = 1, FIGHT = 2, KEYED = 3;

    private final Map<Long, Arena> arenas = new HashMap<Long, Arena>();

    public ArenaDataSC(String name) {
        super(name);
    }

    public ArenaDataSC() {
        this(NAME);
    }

    /** The Substrate's data (made on first use), null elsewhere / on a client / with the dimension off. */
    public static ArenaDataSC get(World w) {
        if (w == null || w.isRemote || !SubstrateSC.is(w)) {
            return null;
        }
        ArenaDataSC d = (ArenaDataSC) w.perWorldStorage.loadData(ArenaDataSC.class, NAME);
        if (d == null) {
            d = new ArenaDataSC();
            w.perWorldStorage.setData(NAME, d);
        }
        return d;
    }

    /** The record of region (ri, rj), or null (its start not made yet). */
    public Arena arena(int ri, int rj) {
        return arenas.get(Long.valueOf(ArenaLayoutSC.key(ri, rj)));
    }

    /** The record of the arena at crossing (i, j) (its region's), or null. */
    public Arena arenaAt(int i, int j) {
        Arena a = arena(ArenaLayoutSC.regionOf(i), ArenaLayoutSC.regionOf(j));
        return a != null && a.i == i && a.j == j ? a : null;
    }

    /** Package A (the start made): the record of the arena at crossing (i, j) - EMPTY or READY; an existing one is kept. */
    public Arena make(int i, int j, int floorY, boolean built) {
        int ri = ArenaLayoutSC.regionOf(i), rj = ArenaLayoutSC.regionOf(j);
        Arena a = arena(ri, rj);
        if (a == null) {
            a = new Arena();
            a.ri = ri;
            a.rj = rj;
            a.i = i;
            a.j = j;
            a.x = ArenaLayoutSC.centre(i);
            a.z = ArenaLayoutSC.centre(j);
            a.floorY = floorY;
            a.state = built ? READY : EMPTY;
            arenas.put(Long.valueOf(ArenaLayoutSC.key(ri, rj)), a);
            markDirty();
        }
        return a;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        arenas.clear();
        NBTTagList l = nbt.getTagList("Arenas", 10);
        for (int k = 0; k < l.tagCount(); k++) {
            Arena a = Arena.read(l.getCompoundTagAt(k));
            arenas.put(Long.valueOf(ArenaLayoutSC.key(a.ri, a.rj)), a);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList l = new NBTTagList();
        for (Arena a : arenas.values()) {
            l.appendTag(a.write());
        }
        nbt.setTag("Arenas", l);
    }

    /** One arena (see the class comment for who writes which field). */
    public static final class Arena {
        /** Its region, its crossing, the crossing's world x / z, the floor's Y. */
        public int ri, rj, i, j, x, z, floorY;
        public int state = READY;
        /** A Breakdown Key lies in the Console. */
        public boolean keyIn;
        /** The current fight's id (the Warden keeps it in NBT: a stale one leaves the world) and its Warden's UUID ("" none). */
        public long fightId;
        public String bossUuid = "";
        /** World times: the fight's start; the last tick a player was within WardenRulesSC.SCALE_RADIUS (package B). */
        public long fightStartedAt, lastPlayerAt;
        /** The most players seen within SCALE_RADIUS this fight - the health's scale (package B). */
        public int maxPlayers;
        /** The Breakdown cracks the Warden opened in the floor ({x, y, z}) - closed again when the fight ends (package A). */
        private final List<int[]> cracks = new ArrayList<int[]>();
        public NBTTagCompound extra = new NBTTagCompound();

        /** Package B: a crack opened at (x, y, z). */
        public void addCrack(int x, int y, int z) {
            cracks.add(new int[]{x, y, z});
        }

        /** The open cracks (a copy). */
        public List<int[]> cracks() {
            return new ArrayList<int[]>(cracks);
        }

        /** Package A (closing them): the open cracks, forgotten here. */
        public List<int[]> takeCracks() {
            List<int[]> out = new ArrayList<int[]>(cracks);
            cracks.clear();
            return out;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setIntArray("Pos", new int[]{ri, rj, i, j, x, z, floorY});
            t.setByte("State", (byte) state);
            t.setBoolean("Key", keyIn);
            t.setLong("Fight", fightId);
            t.setString("Boss", bossUuid == null ? "" : bossUuid);
            t.setLong("Start", fightStartedAt);
            t.setLong("Seen", lastPlayerAt);
            t.setInteger("Players", maxPlayers);
            int[] c = new int[cracks.size() * 3];
            for (int k = 0; k < cracks.size(); k++) {
                System.arraycopy(cracks.get(k), 0, c, k * 3, 3);
            }
            t.setIntArray("Cracks", c);
            t.setTag("Extra", extra);
            return t;
        }

        static Arena read(NBTTagCompound t) {
            Arena a = new Arena();
            int[] p = t.getIntArray("Pos");
            if (p.length == 7) {
                a.ri = p[0];
                a.rj = p[1];
                a.i = p[2];
                a.j = p[3];
                a.x = p[4];
                a.z = p[5];
                a.floorY = p[6];
            }
            a.state = t.getByte("State");
            a.keyIn = t.getBoolean("Key");
            a.fightId = t.getLong("Fight");
            a.bossUuid = t.getString("Boss");
            a.fightStartedAt = t.getLong("Start");
            a.lastPlayerAt = t.getLong("Seen");
            a.maxPlayers = t.getInteger("Players");
            int[] c = t.getIntArray("Cracks");
            for (int k = 0; k + 2 < c.length; k += 3) {
                a.cracks.add(new int[]{c[k], c[k + 1], c[k + 2]});
            }
            a.extra = t.getCompoundTag("Extra");
            return a;
        }
    }
}
