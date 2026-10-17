package com.sc.settle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §3, rule ПП-5): every settlement of the server, the players' «Доверие»,
 * their limits and timers - one WorldSavedData "SiliconAgeSettlements" in the server's GLOBAL map storage (data/ of the
 * save, as BridgeMarksSC and the field zones): the bridge in the Overworld asks it about the Substrate without loading
 * that world, and the Substrate's own perWorldStorage would vanish with a deleted DIM-1414 while the players' standing
 * should not.
 * <p>
 * A settlement's record is made with its structure start (package S, MapGenSettlementSC.getStructureStart) - before a
 * single block of it exists - so its ground is protected, kept out of the bridge and out of players' fields from the
 * start, even in a «cold» world whose core has never ticked (TileEntityFieldGeneratorSC's ACTIVE list is empty then).
 * Look-ups go through a region index (REGION_SHIFT: 512 blocks, the generator's region) - never a walk of every record.
 * <p>
 * What lives here, and who writes it (the plan's §3 table):
 * <ul>
 * <li>Settlement - the site, the dome, the dock, named points (P_*), the roster's slots (Slot: NEVER / ALIVE / DEAD,
 * the living creature's UUID, when it died), the players' local records (LocalRec: the «Враг» status, the per-deal
 * counters of the day);</li>
 * <li>PlayerRec - the global «Доверие», the daily / weekly counters (reputation by source, chips given out, contracts
 * taken, the Singularity Crumb), the first pass, the penance day, known docks, contracts, the death mark;</li>
 * <li>`extra` compounds (world, settlement, player, local) - each package's own state under its own key ("C", "T", "B",
 * "N", "S"), so a package never needs this file changed for a field of its own.</li>
 * </ul>
 * Server thread only. A client's world has no registry (get answers null). Shared core: only the coordinator edits this
 * file - a package needing a new shared field asks in its report, or keeps it in its `extra` key.
 */
public class SettlementRegistrySC extends WorldSavedData {

    public static final String NAME = "SiliconAgeSettlements";
    /** The index's region: 512 blocks (32 chunks) a side - SettlementLayoutSC.REGION. */
    public static final int REGION_SHIFT = 9;
    /** Slot states. */
    public static final int NEVER = 0, ALIVE = 1, DEAD = 2;
    /** The record layout's version (Settlement.version: a Ф3 core adds the Captain's slots to an older city). */
    public static final int VERSION = 1;
    /** Named points of a settlement (Settlement.points). The pads are P_PAD + "0".."3". */
    public static final String P_PLAZA = "plaza", P_SQUARE = "square", P_CLONE = "clone", P_PAD = "pad", P_GATE = "gate", P_TOWER = "tower";

    private final Map<Integer, Settlement> byId = new LinkedHashMap<Integer, Settlement>();
    private final Map<Long, List<Settlement>> regions = new HashMap<Long, List<Settlement>>();
    private final Map<UUID, PlayerRec> players = new HashMap<UUID, PlayerRec>();
    private int nextId = 1;
    /** The one-time «Поставить Межпространственный якорь» contract (§7): who placed the world's first anchor, paid or not. */
    public String anchorBy = "";
    public boolean anchorPaid;
    /** Packages' own world-wide state, each under its key. */
    public NBTTagCompound extra = new NBTTagCompound();

    public SettlementRegistrySC(String name) {
        super(name);
    }

    /** The server's registry (any world of the server reaches the same global storage); null on a client or before a world. */
    public static SettlementRegistrySC get(World w) {
        if (w == null || w.isRemote || w.mapStorage == null) {
            return null;
        }
        SettlementRegistrySC r = (SettlementRegistrySC) w.mapStorage.loadData(SettlementRegistrySC.class, NAME);
        if (r == null) {
            r = new SettlementRegistrySC(NAME);
            w.mapStorage.setData(NAME, r);
        }
        return r;
    }

    /** The running server's registry (through its first world), null with no server. */
    public static SettlementRegistrySC get() {
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null || srv.worldServers == null || srv.worldServers.length == 0 || srv.worldServers[0] == null) {
            return null;
        }
        return get(srv.worldServers[0]);
    }

    // ------------------------------------------------------------------ settlements

    /** A new record: the next id, indexed by its reach (fill the record - dock included - before adding it). */
    public Settlement add(Settlement s) {
        s.id = nextId++;
        byId.put(Integer.valueOf(s.id), s);
        index(s);
        markDirty();
        return s;
    }

    /**
     * Ids up to `used` are somebody's already (the structure starts of DIM-1414/data name them while this file was lost):
     * the next add() hands out one past them - two settlements never share an id (package S, before every add).
     */
    public void reserveIds(int used) {
        if (used >= nextId) {
            nextId = used + 1;
            markDirty();
        }
    }

    /** A record lost from the file, made again by its core (RosterSC.restore) under its own id. */
    public Settlement restore(Settlement s) {
        byId.put(Integer.valueOf(s.id), s);
        index(s);
        nextId = Math.max(nextId, s.id + 1);
        markDirty();
        return s;
    }

    /** After a change of a record's site, radius or dock: its index entries again. */
    public void reindex(Settlement s) {
        for (List<Settlement> l : regions.values()) {
            l.remove(s);
        }
        index(s);
        markDirty();
    }

    private void index(Settlement s) {
        int[] r = s.reach();
        for (int rx = r[0] >> REGION_SHIFT; rx <= r[2] >> REGION_SHIFT; rx++) {
            for (int rz = r[1] >> REGION_SHIFT; rz <= r[3] >> REGION_SHIFT; rz++) {
                Long k = key(s.dim, rx, rz);
                List<Settlement> l = regions.get(k);
                if (l == null) {
                    l = new ArrayList<Settlement>(2);
                    regions.put(k, l);
                }
                if (!l.contains(s)) {
                    l.add(s);
                }
            }
        }
    }

    /** The index key of region (rx, rz) of dimension `dim`. */
    static Long key(int dim, int rx, int rz) {
        return Long.valueOf(((long) dim & 0xFFFFL) << 48 ^ ((long) rx & 0xFFFFFFL) << 24 ^ (long) rz & 0xFFFFFFL);
    }

    public Settlement byId(int id) {
        return byId.get(Integer.valueOf(id));
    }

    public Collection<Settlement> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int count() {
        return byId.size();
    }

    /** The settlement whose core column is (x, z) in `dim` - a structure start made again finds its old record (package S). */
    public Settlement atCentre(int dim, int x, int z) {
        for (Settlement s : candidates(dim, x, z)) {
            if (s.x == x && s.z == z) {
                return s;
            }
        }
        return null;
    }

    /** The records whose reach covers the region of block (x, z): the index's list, never null (don't keep it). */
    public List<Settlement> candidates(int dim, int x, int z) {
        List<Settlement> l = regions.get(key(dim, x >> REGION_SHIFT, z >> REGION_SHIFT));
        return l == null ? Collections.<Settlement>emptyList() : l;
    }

    /** ПП-7: the settlement whose protected cylinder (the dome's radius, from SettlementSC.FOUNDATION below the floor up) holds the point. */
    public Settlement protecting(int dim, double x, double y, double z) {
        for (Settlement s : candidates(dim, (int) Math.floor(x), (int) Math.floor(z))) {
            if (s.contains(x, y, z)) {
                return s;
            }
        }
        return null;
    }

    /** ПЖ-2: the settlement whose dome or belt (radius + SettlementSC.BELT, any height) holds the column. */
    public Settlement inBelt(int dim, double x, double z) {
        for (Settlement s : candidates(dim, (int) Math.floor(x), (int) Math.floor(z))) {
            if (s.inBelt(x, z)) {
                return s;
            }
        }
        return null;
    }

    /** The settlement within `margin` blocks past its dome's edge of the column (the spawn director: SettlementSC.SPAWN_MARGIN). */
    public Settlement within(int dim, double x, double z, int margin) {
        // the regions within `margin` of the column: a record is indexed `BELT` past its dome, so one whose edge is
        // `margin` off has an indexed region at most margin - BELT from here
        for (Settlement s : near(dim, (int) Math.floor(x), (int) Math.floor(z), Math.max(0, margin))) {
            double r = s.radius + margin;
            if (s.distSq(x, z) <= r * r) {
                return s;
            }
        }
        return null;
    }

    /** ПР-1: the settlement whose dock's protected zone (SettlementSC.DOCK_ZONE) holds the point. */
    public Settlement dockZoneAt(int dim, double x, double y, double z) {
        for (Settlement s : candidates(dim, (int) Math.floor(x), (int) Math.floor(z))) {
            if (s.inDockZone(x, y, z)) {
                return s;
            }
        }
        return null;
    }

    /** ПР-2: a settlement whose dock beacon is within `radius` blocks (across, any height) of (x, z). */
    public Settlement dockNear(int dim, int x, int z, int radius) {
        long r2 = (long) radius * radius;
        for (Settlement s : near(dim, x, z, radius)) {
            if (s.hasDock) {
                long dx = s.dockX - x, dz = s.dockZ - z;
                if (dx * dx + dz * dz <= r2) {
                    return s;
                }
            }
        }
        return null;
    }

    /** The records of `dim` within `reach` blocks of (x, z) by the regions round (a box look-up; callers check the distance). */
    public List<Settlement> near(int dim, int x, int z, int reach) {
        List<Settlement> out = new ArrayList<Settlement>();
        for (int rx = (x - reach) >> REGION_SHIFT; rx <= (x + reach) >> REGION_SHIFT; rx++) {
            for (int rz = (z - reach) >> REGION_SHIFT; rz <= (z + reach) >> REGION_SHIFT; rz++) {
                List<Settlement> l = regions.get(key(dim, rx, rz));
                if (l != null) {
                    for (Settlement s : l) {
                        if (!out.contains(s)) {
                            out.add(s);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** ПП-8: a settlement whose protected cylinder's box meets `box` (a player's field zone), or null. */
    public Settlement overlapping(int dim, AxisAlignedBB box) {
        int reach = (int) Math.ceil(Math.max(box.maxX - box.minX, box.maxZ - box.minZ) / 2D) + 1;
        int cx = (int) Math.floor((box.minX + box.maxX) / 2D), cz = (int) Math.floor((box.minZ + box.maxZ) / 2D);
        for (Settlement s : near(dim, cx, cz, reach + SettlementSizeSC.CITY.radiusMax)) {
            if (s.protectedBox().intersectsWith(box)) {
                return s;
            }
        }
        return null;
    }

    /**
     * The `n` settlements of `dim` with a dock nearest to (x, z), nearest first (the Settlement Map, the bridge's list).
     * `skipId` is left out (-1: none). A walk of the dimension's records - asked rarely (a map bought, a screen opened).
     */
    public List<Settlement> nearestDocks(int dim, final double x, final double z, int n, int skipId) {
        List<Settlement> l = new ArrayList<Settlement>();
        for (Settlement s : byId.values()) {
            if (s.dim == dim && s.hasDock && s.id != skipId) {
                l.add(s);
            }
        }
        Collections.sort(l, new Comparator<Settlement>() {
            @Override
            public int compare(Settlement a, Settlement b) {
                return Double.compare(a.distSq(x, z), b.distSq(x, z));
            }
        });
        return l.size() > n ? new ArrayList<Settlement>(l.subList(0, n)) : l;
    }

    /** The «Первый причал» of `dim` (the guaranteed settlement near the spawn), null none. */
    public Settlement first(int dim) {
        for (Settlement s : byId.values()) {
            if (s.dim == dim && s.first) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the roster

    /** A slot's creature died (its entity's onDeath, package N): the Clone Capsule's timer starts. */
    public void slotDied(int id, int slot, long now) {
        Slot sl = slot(id, slot);
        if (sl != null) {
            sl.state = DEAD;
            sl.diedAt = now;
            sl.uuidMost = sl.uuidLeast = 0L;
            markDirty();
        }
    }

    /** A slot's creature is in the world (spawned by the core, package C): its UUID kept. */
    public void slotAlive(int id, int slot, UUID uuid, long now) {
        Slot sl = slot(id, slot);
        if (sl != null) {
            sl.state = ALIVE;
            sl.uuidMost = uuid.getMostSignificantBits();
            sl.uuidLeast = uuid.getLeastSignificantBits();
            sl.seenAt = now;
            markDirty();
        }
    }

    public Slot slot(int id, int slot) {
        Settlement s = byId(id);
        return s == null || slot < 0 || slot >= s.slots.size() ? null : s.slots.get(slot);
    }

    // ------------------------------------------------------------------ players

    /** The player's record (made on first ask). */
    public PlayerRec player(UUID id) {
        PlayerRec p = players.get(id);
        if (p == null) {
            p = new PlayerRec();
            players.put(id, p);
            markDirty();
        }
        return p;
    }

    /** The player's record or null - for readers that shouldn't create one (the bridge's list, the handbook). */
    public PlayerRec playerIfAny(UUID id) {
        return id == null ? null : players.get(id);
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        byId.clear();
        regions.clear();
        players.clear();
        nextId = Math.max(1, nbt.getInteger("NextId"));
        anchorBy = nbt.getString("AnchorBy");
        anchorPaid = nbt.getBoolean("AnchorPaid");
        extra = nbt.getCompoundTag("Extra");
        NBTTagList ss = nbt.getTagList("Settlements", 10);
        for (int i = 0; i < ss.tagCount(); i++) {
            Settlement s = Settlement.read(ss.getCompoundTagAt(i));
            if (s.id > 0) {
                byId.put(Integer.valueOf(s.id), s);
                index(s);
                nextId = Math.max(nextId, s.id + 1);
            }
        }
        NBTTagList ps = nbt.getTagList("Players", 10);
        for (int i = 0; i < ps.tagCount(); i++) {
            NBTTagCompound t = ps.getCompoundTagAt(i);
            UUID id = uuid(t.getString("Id"));
            if (id != null) {
                players.put(id, PlayerRec.read(t));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("NextId", nextId);
        nbt.setString("AnchorBy", anchorBy == null ? "" : anchorBy);
        nbt.setBoolean("AnchorPaid", anchorPaid);
        nbt.setTag("Extra", extra);
        NBTTagList ss = new NBTTagList();
        for (Settlement s : byId.values()) {
            ss.appendTag(s.write());
        }
        nbt.setTag("Settlements", ss);
        NBTTagList ps = new NBTTagList();
        for (Map.Entry<UUID, PlayerRec> e : players.entrySet()) {
            NBTTagCompound t = e.getValue().write();
            t.setString("Id", e.getKey().toString());
            ps.appendTag(t);
        }
        nbt.setTag("Players", ps);
    }

    /** A UUID from its string, null for a bad one. */
    public static UUID uuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ================================================================== records

    /** One settlement (rule ПП-5). Coordinates are blocks; the core stands at (x, y, z), the floor at floorY. */
    public static final class Settlement {
        public int id, dim;
        public int x, y, z, floorY;
        /** SettlementSizeSC ordinal. */
        public int size;
        public int radius;
        /** The settlement's own seed: its prices (±10%), names, roster. */
        public long seed;
        /** The name «<element>-<number>» (NpcNamesSC). */
        public int nameElement, nameNumber;
        /** «Первый причал»: the guaranteed one near the world's spawn. */
        public boolean first;
        public boolean hasDock;
        /** The dock's beacon block. */
        public int dockX, dockY, dockZ;
        public int version = VERSION;
        /** The world time it was made (the start); 0 unknown. */
        public long madeAt;
        /** Named points (P_*): {x, y, z}. */
        public final Map<String, int[]> points = new LinkedHashMap<String, int[]>();
        public final List<Slot> slots = new ArrayList<Slot>();
        public final Map<UUID, LocalRec> locals = new HashMap<UUID, LocalRec>();
        /** Packages' own per-settlement state, each under its key. */
        public NBTTagCompound extra = new NBTTagCompound();

        public SettlementSizeSC sizeKind() {
            return SettlementSizeSC.of(size);
        }

        /** Squared distance across from the core's column. */
        public double distSq(double px, double pz) {
            double dx = px - (x + 0.5D), dz = pz - (z + 0.5D);
            return dx * dx + dz * dz;
        }

        /** ПП-7: inside the protected cylinder - the dome's radius across, from FOUNDATION below the floor up. */
        public boolean contains(double px, double py, double pz) {
            return py >= floorY - SettlementSC.FOUNDATION && distSq(px, pz) <= (double) radius * radius;
        }

        /** ПЖ-2: inside the dome or the belt round it (any height). */
        public boolean inBelt(double px, double pz) {
            double r = radius + SettlementSC.BELT;
            return distSq(px, pz) <= r * r;
        }

        /** ПР-1: inside the dock's protected zone (DOCK_ZONE across from the beacon, a little below it to well above). */
        public boolean inDockZone(double px, double py, double pz) {
            if (!hasDock || py < dockY - SettlementSC.DOCK_BELOW || py > dockY + SettlementSC.DOCK_ABOVE) {
                return false;
            }
            double dx = px - (dockX + 0.5D), dz = pz - (dockZ + 0.5D);
            return dx * dx + dz * dz <= (double) SettlementSC.DOCK_ZONE * SettlementSC.DOCK_ZONE;
        }

        /** The protected cylinder's box (ПП-8: players' fields). */
        public AxisAlignedBB protectedBox() {
            return AxisAlignedBB.getBoundingBox(x + 0.5D - radius, floorY - SettlementSC.FOUNDATION, z + 0.5D - radius,
                    x + 0.5D + radius, 256D, z + 0.5D + radius);
        }

        /** {minX, minZ, maxX, maxZ} of everything a look-up may ask about: the belt and the dock's zone. */
        public int[] reach() {
            int r = radius + SettlementSC.BELT + 1;
            int[] b = {x - r, z - r, x + r, z + r};
            if (hasDock) {
                int d = SettlementSC.DOCK_ZONE + 1;
                b[0] = Math.min(b[0], dockX - d);
                b[1] = Math.min(b[1], dockZ - d);
                b[2] = Math.max(b[2], dockX + d);
                b[3] = Math.max(b[3], dockZ + d);
            }
            return b;
        }

        /** The core field's owner «[Поселение-N]» (never a Minecraft name: those are [A-Za-z0-9_] only). */
        public String owner() {
            return SettlementSC.ownerName(id);
        }

        public int[] point(String name) {
            return points.get(name);
        }

        /** The player's record here (made on first ask - mark the registry dirty after a change). */
        public LocalRec local(UUID id) {
            LocalRec l = locals.get(id);
            if (l == null) {
                l = new LocalRec();
                locals.put(id, l);
            }
            return l;
        }

        public LocalRec localIfAny(UUID id) {
            return id == null ? null : locals.get(id);
        }

        /** The slots of one kind (their indices). */
        public List<Integer> slotsOf(NpcKindSC kind) {
            List<Integer> out = new ArrayList<Integer>();
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i).kind == kind.ordinal()) {
                    out.add(Integer.valueOf(i));
                }
            }
            return out;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("Id", id);
            t.setInteger("Dim", dim);
            t.setIntArray("Core", new int[]{x, y, z, floorY});
            t.setInteger("Size", size);
            t.setInteger("Radius", radius);
            t.setLong("Seed", seed);
            t.setIntArray("Name", new int[]{nameElement, nameNumber});
            t.setBoolean("First", first);
            if (hasDock) {
                t.setIntArray("Dock", new int[]{dockX, dockY, dockZ});
            }
            t.setInteger("Version", version);
            t.setLong("Made", madeAt);
            NBTTagCompound pts = new NBTTagCompound();
            for (Map.Entry<String, int[]> e : points.entrySet()) {
                pts.setIntArray(e.getKey(), e.getValue().clone());
            }
            t.setTag("Points", pts);
            NBTTagList sl = new NBTTagList();
            for (Slot s : slots) {
                sl.appendTag(s.write());
            }
            t.setTag("Slots", sl);
            NBTTagList ls = new NBTTagList();
            for (Map.Entry<UUID, LocalRec> e : locals.entrySet()) {
                NBTTagCompound l = e.getValue().write();
                l.setString("Id", e.getKey().toString());
                ls.appendTag(l);
            }
            t.setTag("Locals", ls);
            t.setTag("Extra", extra);
            return t;
        }

        static Settlement read(NBTTagCompound t) {
            Settlement s = new Settlement();
            s.id = t.getInteger("Id");
            s.dim = t.getInteger("Dim");
            int[] c = t.getIntArray("Core");
            if (c.length == 4) {
                s.x = c[0];
                s.y = c[1];
                s.z = c[2];
                s.floorY = c[3];
            }
            s.size = t.getInteger("Size");
            s.radius = Math.max(1, t.getInteger("Radius"));
            s.seed = t.getLong("Seed");
            int[] n = t.getIntArray("Name");
            if (n.length == 2) {
                s.nameElement = n[0];
                s.nameNumber = n[1];
            }
            s.first = t.getBoolean("First");
            int[] d = t.getIntArray("Dock");
            if (d.length == 3) {
                s.hasDock = true;
                s.dockX = d[0];
                s.dockY = d[1];
                s.dockZ = d[2];
            }
            s.version = t.getInteger("Version");
            s.madeAt = t.getLong("Made");
            NBTTagCompound pts = t.getCompoundTag("Points");
            for (Object k : pts.func_150296_c()) {
                int[] p = pts.getIntArray((String) k);
                if (p.length == 3) {
                    s.points.put((String) k, p);
                }
            }
            NBTTagList sl = t.getTagList("Slots", 10);
            for (int i = 0; i < sl.tagCount(); i++) {
                s.slots.add(Slot.read(sl.getCompoundTagAt(i)));
            }
            NBTTagList ls = t.getTagList("Locals", 10);
            for (int i = 0; i < ls.tagCount(); i++) {
                NBTTagCompound l = ls.getCompoundTagAt(i);
                UUID id = uuid(l.getString("Id"));
                if (id != null) {
                    s.locals.put(id, LocalRec.read(l));
                }
            }
            s.extra = t.getCompoundTag("Extra");
            return s;
        }
    }

    /** One creature of a settlement's roster (ПП-13): its kind and name, where it works and sleeps, alive or not. */
    public static final class Slot {
        /** NpcKindSC ordinal. */
        public int kind;
        public int nameElement, nameNumber;
        /** The work place (a Tuner's machine, a guard's post). */
        public int postX, postY, postZ;
        /** Where it spends the night. */
        public int homeX, homeY, homeZ;
        /** NEVER (not spawned yet), ALIVE (its creature exists, maybe in an unloaded chunk), DEAD (the timer runs). */
        public int state;
        public long uuidMost, uuidLeast;
        /** The world time of its death (DEAD). */
        public long diedAt;
        /** The world time the core last saw its creature. */
        public long seenAt;

        public NpcKindSC kindOf() {
            return NpcKindSC.of(kind);
        }

        public UUID uuid() {
            return uuidMost == 0L && uuidLeast == 0L ? null : new UUID(uuidMost, uuidLeast);
        }

        public int nameCode() {
            return com.sc.settle.npc.NpcNamesSC.code(nameElement, nameNumber);
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("Kind", kind);
            t.setIntArray("Name", new int[]{nameElement, nameNumber});
            t.setIntArray("Post", new int[]{postX, postY, postZ});
            t.setIntArray("Home", new int[]{homeX, homeY, homeZ});
            t.setByte("State", (byte) state);
            t.setLong("UM", uuidMost);
            t.setLong("UL", uuidLeast);
            t.setLong("Died", diedAt);
            t.setLong("Seen", seenAt);
            return t;
        }

        static Slot read(NBTTagCompound t) {
            Slot s = new Slot();
            s.kind = t.getInteger("Kind");
            int[] n = t.getIntArray("Name");
            if (n.length == 2) {
                s.nameElement = n[0];
                s.nameNumber = n[1];
            }
            int[] p = t.getIntArray("Post");
            if (p.length == 3) {
                s.postX = p[0];
                s.postY = p[1];
                s.postZ = p[2];
            }
            int[] h = t.getIntArray("Home");
            if (h.length == 3) {
                s.homeX = h[0];
                s.homeY = h[1];
                s.homeZ = h[2];
            }
            s.state = Math.max(NEVER, Math.min(DEAD, t.getByte("State")));
            s.uuidMost = t.getLong("UM");
            s.uuidLeast = t.getLong("UL");
            s.diedAt = t.getLong("Died");
            s.seenAt = t.getLong("Seen");
            return s;
        }
    }

    /** A player at one settlement: the local «Враг» status (ПД-1, ПГ-8) and the deals' counters of the day (ПО-4). */
    public static final class LocalRec {
        /** World time the «Враг» status ends (0: never was). */
        public long enemyUntil;
        /** The game day `counts` belong to (SettlementSC.day). */
        public long day = -1;
        /** Deal key -> times traded today. */
        public NBTTagCompound counts = new NBTTagCompound();
        /** Packages' own per-player-per-settlement state. */
        public NBTTagCompound extra = new NBTTagCompound();

        /** A new game day: yesterday's counters go (restock at dawn). @return true if anything changed */
        public boolean rollDay(long today) {
            if (today == day) {
                return false;
            }
            day = today;
            counts = new NBTTagCompound();
            return true;
        }

        public boolean enemy(long now) {
            return enemyUntil > now;
        }

        public int count(String deal) {
            return counts.getInteger(deal);
        }

        public void addCount(String deal, int n) {
            counts.setInteger(deal, counts.getInteger(deal) + n);
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("Enemy", enemyUntil);
            t.setLong("Day", day);
            t.setTag("Counts", counts);
            t.setTag("Extra", extra);
            return t;
        }

        static LocalRec read(NBTTagCompound t) {
            LocalRec l = new LocalRec();
            l.enemyUntil = t.getLong("Enemy");
            l.day = t.hasKey("Day") ? t.getLong("Day") : -1;
            l.counts = t.getCompoundTag("Counts");
            l.extra = t.getCompoundTag("Extra");
            return l;
        }
    }

    /** One player, server-wide (ПД-6): «Доверие», the global limits (ПО-5), the first pass, known docks, contracts, the death mark. */
    public static final class PlayerRec {
        /** «Доверие» (ReputationSC.MIN..MAX). */
        public int reputation;
        /** The game day the daily counters belong to. */
        public long day = -1;
        /** Today: reputation from deals, from kills of the small ones, chips given out, contracts taken. */
        public int repTrades, repKills, chips, contractsTaken;
        /** The game week (SettlementSC.week) `crumbs` belongs to; Singularity Crumbs bought from an Elder this week. */
        public long week = -1;
        public int crumbs;
        /** ПД-5: «Пропуск Наладчиков» given. */
        public boolean firstPass;
        /** ПД-4: the day of the last penance (-1 never). */
        public long penanceDay = -1;
        /** ПР-3: settlement ids whose dock the player knows (visited, or from a Settlement Map). */
        public final Set<Integer> knownDocks = new LinkedHashSet<Integer>();
        /** ПО-5: the «глоб.» deals: deal key -> {"p": period index, "n": times}. */
        public NBTTagCompound globalCounts = new NBTTagCompound();
        public final List<ContractRec> contracts = new ArrayList<ContractRec>();
        /** ПР-6: «Метка гибели» - the last death in the Substrate. */
        public boolean hasDeath;
        public int deathDim, deathX, deathY, deathZ;
        public long deathAt;
        /** Packages' own per-player state. */
        public NBTTagCompound extra = new NBTTagCompound();

        /** A new game day: the daily counters start again. @return true if they did */
        public boolean rollDay(long today) {
            if (today == day) {
                return false;
            }
            day = today;
            repTrades = repKills = chips = contractsTaken = 0;
            return true;
        }

        /** A new game week. @return true if it is */
        public boolean rollWeek(long thisWeek) {
            if (thisWeek == week) {
                return false;
            }
            week = thisWeek;
            crumbs = 0;
            return true;
        }

        /** A «глоб.» deal's count in period `period` (another period: 0). */
        public int globalCount(String deal, long period) {
            NBTTagCompound c = globalCounts.getCompoundTag(deal);
            return c.getLong("p") == period ? c.getInteger("n") : 0;
        }

        public void addGlobalCount(String deal, long period, int n) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("p", period);
            c.setInteger("n", globalCount(deal, period) + n);
            globalCounts.setTag(deal, c);
        }

        /** The death mark goes (arrived there, or a newer death replaces it). */
        public void clearDeath() {
            hasDeath = false;
        }

        /** Contracts not finished yet. */
        public List<ContractRec> open() {
            List<ContractRec> out = new ArrayList<ContractRec>();
            for (ContractRec c : contracts) {
                if (!c.done) {
                    out.add(c);
                }
            }
            return out;
        }

        /** Drops finished contracts older than `keepDays` (the board shows today's «done» ones). */
        public void pruneContracts(long today, int keepDays) {
            for (Iterator<ContractRec> it = contracts.iterator(); it.hasNext();) {
                ContractRec c = it.next();
                if (c.done && today - c.takenDay > keepDays) {
                    it.remove();
                }
            }
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("Rep", reputation);
            t.setLong("Day", day);
            t.setIntArray("Daily", new int[]{repTrades, repKills, chips, contractsTaken});
            t.setLong("Week", week);
            t.setInteger("Crumbs", crumbs);
            t.setBoolean("Pass", firstPass);
            t.setLong("Penance", penanceDay);
            int[] docks = new int[knownDocks.size()];
            int i = 0;
            for (Integer d : knownDocks) {
                docks[i++] = d.intValue();
            }
            t.setIntArray("Docks", docks);
            t.setTag("Global", globalCounts);
            NBTTagList cl = new NBTTagList();
            for (ContractRec c : contracts) {
                cl.appendTag(c.write());
            }
            t.setTag("Contracts", cl);
            if (hasDeath) {
                t.setIntArray("Death", new int[]{deathDim, deathX, deathY, deathZ});
                t.setLong("DeathAt", deathAt);
            }
            t.setTag("Extra", extra);
            return t;
        }

        static PlayerRec read(NBTTagCompound t) {
            PlayerRec p = new PlayerRec();
            p.reputation = t.getInteger("Rep");
            p.day = t.hasKey("Day") ? t.getLong("Day") : -1;
            int[] d = t.getIntArray("Daily");
            if (d.length == 4) {
                p.repTrades = d[0];
                p.repKills = d[1];
                p.chips = d[2];
                p.contractsTaken = d[3];
            }
            p.week = t.hasKey("Week") ? t.getLong("Week") : -1;
            p.crumbs = t.getInteger("Crumbs");
            p.firstPass = t.getBoolean("Pass");
            p.penanceDay = t.hasKey("Penance") ? t.getLong("Penance") : -1;
            for (int id : t.getIntArray("Docks")) {
                p.knownDocks.add(Integer.valueOf(id));
            }
            p.globalCounts = t.getCompoundTag("Global");
            NBTTagList cl = t.getTagList("Contracts", 10);
            for (int i = 0; i < cl.tagCount(); i++) {
                p.contracts.add(ContractRec.read(cl.getCompoundTagAt(i)));
            }
            int[] dm = t.getIntArray("Death");
            if (dm.length == 4) {
                p.hasDeath = true;
                p.deathDim = dm[0];
                p.deathX = dm[1];
                p.deathY = dm[2];
                p.deathZ = dm[3];
                p.deathAt = t.getLong("DeathAt");
            }
            p.extra = t.getCompoundTag("Extra");
            return p;
        }
    }

    /** One contract a player took from an Elder (ПО-8): its key in ContractTableSC, the progress, where and when it was taken. */
    public static final class ContractRec {
        public String key = "";
        public int target, progress;
        /** The settlement whose Elder gave it (-1 none). */
        public int settlement = -1;
        public long takenDay;
        public boolean done;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Key", key);
            t.setIntArray("P", new int[]{target, progress, settlement});
            t.setLong("Day", takenDay);
            t.setBoolean("Done", done);
            return t;
        }

        static ContractRec read(NBTTagCompound t) {
            ContractRec c = new ContractRec();
            c.key = t.getString("Key");
            int[] p = t.getIntArray("P");
            if (p.length == 3) {
                c.target = p[0];
                c.progress = p[1];
                c.settlement = p[2];
            }
            c.takenDay = t.getLong("Day");
            c.done = t.getBoolean("Done");
            return c;
        }
    }
}
