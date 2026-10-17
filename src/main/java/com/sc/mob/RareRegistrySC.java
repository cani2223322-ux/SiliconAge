package com.sc.mob;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §2.8, rule ПС-30; proposal §12 «Уникальные и редкие мобы (Затмение, Часовые,
 * Вожаки) учитываются в WorldSavedData по UUID и времени последнего появления. Счёт по загруженным сущностям сломался бы при
 * выгрузке мира»): who of the rare ones lives, by a key -
 * <ul>
 * <li>ECLIPSE - «Затмение», one in the whole dimension (package M: SpawnDirectorSC spawns it in the Rift only while this
 * record isn't ALIVE and its cooldown is over);</li>
 * <li>vaultKey(i, j) - a Vault's Singular Sentinel (package C: placed with the Vault, back after its respawn time);</li>
 * <li>campKey(axis, line, seg) - a camp's Warlord, and the camp's own state in the record's extra (package C: the safe, the
 * restore after 3 days).</li>
 * </ul>
 * A record: ALIVE with the entity's UUID, the time it came and was last seen and where; DEAD with the time it died. The entity
 * keeps its key in its NBT (KEY_NBT) and reports its own death (died) - a rare one removed without dying (a command, another
 * mod's purge) is caught by the owner's «lost» check: the record's place loaded, no entity of that UUID there for LOST_CHECKS
 * looks - then it counts as dead now (missed). DIM-1414/data/SCSi14Rare.dat (the Substrate's perWorldStorage: the creatures
 * are that world's - a deleted DIM-1414 forgets them with it). Server thread only. Shared core: only the coordinator edits this
 * file.
 */
public class RareRegistrySC extends WorldSavedData {

    public static final String NAME = "SCSi14Rare";
    /** The entity NBT key of a rare creature's registry key. */
    public static final String KEY_NBT = "scRareKey";
    /** Record states. */
    public static final int NONE = 0, ALIVE = 1, DEAD = 2;
    /** The single «Затмение». */
    public static final String ECLIPSE = "eclipse";
    /** Looks at a loaded place without the creature before it counts as gone (the owner's check, about once a second each). */
    public static final int LOST_CHECKS = 10;

    private final Map<String, Rec> recs = new LinkedHashMap<String, Rec>();

    public RareRegistrySC(String name) {
        super(name);
    }

    /** The registry of world `w` (the Substrate's own storage); null on a client or with no storage. */
    public static RareRegistrySC get(World w) {
        if (w == null || w.isRemote || w.perWorldStorage == null) {
            return null;
        }
        RareRegistrySC r = (RareRegistrySC) w.perWorldStorage.loadData(RareRegistrySC.class, NAME);
        if (r == null) {
            r = new RareRegistrySC(NAME);
            w.perWorldStorage.setData(NAME, r);
        }
        return r;
    }

    /** A Vault's Sentinel - the Vault under the Dead Node of crossing (i, j). */
    public static String vaultKey(int i, int j) {
        return "vault:" + i + "," + j;
    }

    /** A camp's Warlord - the camp of canyon segment `seg` of Scribe line `line` along axis `axis` (CampLayoutSC). */
    public static String campKey(int axis, int line, int seg) {
        return "camp:" + axis + "," + line + "," + seg;
    }

    /** Tags `e` as the rare one of `key` (its NBT keeps it). */
    public static void tag(Entity e, String key) {
        if (e != null && key != null) {
            e.getEntityData().setString(KEY_NBT, key);
        }
    }

    /** The registry key `e` carries, or null for none. */
    public static String keyOf(Entity e) {
        if (e == null || !e.getEntityData().hasKey(KEY_NBT)) {
            return null;
        }
        String k = e.getEntityData().getString(KEY_NBT);
        return k.isEmpty() ? null : k;
    }

    /** Pure: a record of state `state` that died at `diedAt` may come back at `now` after `cooldown` ticks (NONE: at once). */
    public static boolean mayRespawn(int state, long diedAt, long now, long cooldown) {
        return state == NONE || state == DEAD && now - diedAt >= cooldown;
    }

    // ------------------------------------------------------------------ records

    /** The record of `key` (made, NONE, on first ask). */
    public Rec rec(String key) {
        Rec r = recs.get(key);
        if (r == null) {
            r = new Rec(key);
            recs.put(key, r);
            markDirty();
        }
        return r;
    }

    /** The record of `key` or null. */
    public Rec recIfAny(String key) {
        return key == null ? null : recs.get(key);
    }

    public Collection<Rec> all() {
        return recs.values();
    }

    /** `e` came (spawned / placed) as the rare one of `key`: ALIVE, its UUID, the time, the place; the entity tagged. */
    public void alive(String key, Entity e, long now) {
        Rec r = rec(key);
        UUID id = e.getUniqueID();
        r.state = ALIVE;
        r.uuidMost = id.getMostSignificantBits();
        r.uuidLeast = id.getLeastSignificantBits();
        r.spawnedAt = now;
        r.seenAt = now;
        r.misses = 0;
        r.at(e);
        tag(e, key);
        markDirty();
    }

    /** The rare one of `key` died now (its onDeath - only when the death wasn't cancelled). */
    public void died(String key, long now) {
        Rec r = recs.get(key);
        if (r != null && r.state == ALIVE) {
            r.state = DEAD;
            r.diedAt = now;
            r.misses = 0;
            markDirty();
        }
    }

    /** The rare one of `key` is about now (its own slow tick): the time and the place; a stranger of the same key (an old copy) - false. */
    public boolean seen(String key, Entity e, long now) {
        Rec r = recs.get(key);
        if (r == null || r.state != ALIVE || !r.uuid().equals(e.getUniqueID())) {
            return false;
        }
        r.seenAt = now;
        r.misses = 0;
        r.at(e);
        markDirty();
        return true;
    }

    /** The owner's «lost» check: one more look at the loaded place without it; LOST_CHECKS of them - dead now. @return it counts as dead now */
    public boolean missed(String key, long now) {
        Rec r = recs.get(key);
        if (r == null || r.state != ALIVE) {
            return false;
        }
        if (++r.misses < LOST_CHECKS) {
            return false;
        }
        died(key, now);
        return true;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        recs.clear();
        NBTTagList l = nbt.getTagList("Recs", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            Rec r = Rec.read(l.getCompoundTagAt(i));
            if (r != null) {
                recs.put(r.key, r);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList l = new NBTTagList();
        for (Rec r : recs.values()) {
            l.appendTag(r.write());
        }
        nbt.setTag("Recs", l);
    }

    /** One rare creature's record. */
    public static final class Rec {
        public final String key;
        public int state = NONE;
        public long uuidMost, uuidLeast;
        public long spawnedAt, diedAt, seenAt;
        /** Where it was last seen. */
        public int x, y, z, dim;
        /** Looks without it (not saved). */
        public int misses;
        /** The owner package's own fields (C: the camp's safe, its restore; M: the Eclipse's cooldown roll...). */
        public NBTTagCompound extra = new NBTTagCompound();

        Rec(String key) {
            this.key = key;
        }

        public UUID uuid() {
            return new UUID(uuidMost, uuidLeast);
        }

        void at(Entity e) {
            x = (int) Math.floor(e.posX);
            y = (int) Math.floor(e.posY);
            z = (int) Math.floor(e.posZ);
            dim = e.dimension;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Key", key);
            t.setByte("State", (byte) state);
            t.setLong("UM", uuidMost);
            t.setLong("UL", uuidLeast);
            t.setLong("Spawned", spawnedAt);
            t.setLong("Died", diedAt);
            t.setLong("Seen", seenAt);
            t.setIntArray("At", new int[]{x, y, z, dim});
            t.setTag("Extra", extra);
            return t;
        }

        static Rec read(NBTTagCompound t) {
            String key = t.getString("Key");
            if (key.isEmpty()) {
                return null;
            }
            Rec r = new Rec(key);
            r.state = t.getByte("State");
            r.uuidMost = t.getLong("UM");
            r.uuidLeast = t.getLong("UL");
            r.spawnedAt = t.getLong("Spawned");
            r.diedAt = t.getLong("Died");
            r.seenAt = t.getLong("Seen");
            int[] at = t.getIntArray("At");
            if (at.length >= 4) {
                r.x = at[0];
                r.y = at[1];
                r.z = at[2];
                r.dim = at[3];
            }
            r.extra = t.getCompoundTag("Extra");
            return r;
        }
    }
}
