package com.sc.settle;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.NpcNamesSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.2, rules ПП-13, ПД-5, ПР-3): what a settlement's core does once a
 * second (TileEntitySettlementCoreSC.settlementTick) besides its field - kept out of the tile so the field's class
 * stays the coordinator's invariants. Server only; nothing here loads a chunk.
 * <ol>
 * <li><b>The census</b> (every CENSUS_EVERY ticks of the core): one pass over the world's loaded entities. A creature of
 * this settlement whose slot is ALIVE with its UUID - seen; with another UUID while the slot is ALIVE - a duplicate,
 * removed (setDead: no death, no slotDied); on a NEVER / DEAD slot of its kind - adopted (a lost one reloaded, a
 * restored record). An ALIVE slot unseen LOST_CENSUSES censuses in a row while every chunk of the belt is loaded - lost
 * (another mod's clean-up removed it without a death): DEAD from now, the Clone Capsule's timer runs.</li>
 * <li><b>The roster</b> (after the first census): a NEVER slot whose post is loaded and already built (its chunk and the
 * chunk whose populate builds the post are populated - else its house would be built round it later) gets its
 * creature: SettlementSC.newMember, bindTo, at its post, spawnEntityInWorld, slotAlive.</li>
 * <li><b>The Clone Capsule</b> (P_CLONE, the post without one), only with no player within SettlementSC.RESPAWN_CLEAR of
 * the core (across): a DEAD guard after NpcKindSC.respawnTicks (5 min); a DEAD Tuner after its respawnTicks (a day),
 * and at most one Tuner a game day per settlement (extra "C" CLONE_DAY).</li>
 * <li><b>Visitors</b>: a real player in the dome or belt for the first time anywhere - the Tuners' Pass (ПД-5: +20
 * «Доверие», SRC_PASS; 8 Credit Chips - into the inventory, else at the feet - not counted in the day's 64), told by
 * the nearest guard; in the belt of a settlement with a dock - that dock known (ПР-3, the bridge lists it).</li>
 * </ol>
 * At most SPAWNS_PER_TICK creatures a tick (a new City: 27 at once otherwise). restore() makes a record the registry
 * lost again from the core's own NBT (id, radius, floor) - and, while the generator's maths still puts this very
 * settlement here, its plan's dock, points and slots (all NEVER: the census adopts the creatures still there).
 */
public final class RosterSC {

    /** Core ticks (TileEntitySettlementCoreSC.SLOW_TICKS each) between two censuses. */
    public static final int CENSUS_EVERY = 5;
    /** An ALIVE slot unseen this many censuses in a row (some 30 s), the belt loaded all along: lost. */
    public static final int LOST_CENSUSES = 6;
    /** Creatures put into the world in one core tick at most. */
    public static final int SPAWNS_PER_TICK = 4;
    /** Package C's key in Settlement.extra; in it: the game day of the last Tuner's copy, a record restored by a core. */
    public static final String EXTRA = "C", CLONE_DAY = "CloneDay", RESTORED = "Restored";
    /** How far up from a post / the capsule a free place to stand is looked for. */
    private static final int FREE_UP = 3;

    private RosterSC() {
    }

    /** One core's transient state (TileEntitySettlementCoreSC keeps it; a reload starts it afresh). */
    public static final class State {
        int ticks;
        int censuses;
        int[] misses = new int[0];
    }

    /** The core's second: census, roster, Clone Capsule, visitors. `s` is this core's own record. */
    public static void tick(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, State st, long now) {
        if (st.ticks++ % CENSUS_EVERY == 0) {
            census(w, r, s, st, now);
        }
        if (st.censuses > 0) {
            int made = spawnNew(w, r, s, now, SPAWNS_PER_TICK);
            cloneCapsule(w, r, s, now, SPAWNS_PER_TICK - made);
        }
        visitors(w, r, s);
    }

    // ------------------------------------------------------------------ the census

    static void census(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, State st, long now) {
        int n = s.slots.size();
        if (st.misses.length != n) {
            st.misses = new int[n];
        }
        boolean[] seen = new boolean[n];
        for (Object o : w.loadedEntityList) {
            if (!(o instanceof ISettlementMemberSC) || !(o instanceof EntityLivingBase) || !((EntityLivingBase) o).isEntityAlive()) {
                continue;
            }
            ISettlementMemberSC m = (ISettlementMemberSC) o;
            int i = m.slot();
            if (m.settlementId() != s.id || i < 0 || i >= n || !ofCore(m, s)) {
                continue;                                       // another settlement's (an id met twice too), unbound, no slot: left alone
            }
            Entity e = (Entity) o;
            SettlementRegistrySC.Slot sl = s.slots.get(i);
            UUID u = e.getUniqueID();
            if (sl.state == SettlementRegistrySC.ALIVE) {
                if (u.equals(sl.uuid())) {
                    seen[i] = true;
                    sl.seenAt = now;                            // not worth a markDirty: saved with the next change
                } else {
                    e.setDead();                                // a duplicate - the slot's creature is another one
                }
            } else if (m.kind().ordinal() == sl.kind) {
                r.slotAlive(s.id, i, u, now);                   // back from an unloaded chunk, or a restored record: adopted
                int code = m.nameCode();
                if (code != 0 && code != sl.nameCode()) {
                    sl.nameElement = NpcNamesSC.element(code);  // the registry speaks of it by its own name
                    sl.nameNumber = NpcNamesSC.number(code);
                }
                seen[i] = true;
            }
        }
        boolean loaded = beltLoaded(w, s);
        for (int i = 0; i < n; i++) {
            SettlementRegistrySC.Slot sl = s.slots.get(i);
            if (sl.state != SettlementRegistrySC.ALIVE || seen[i] || !loaded) {
                st.misses[i] = 0;
            } else if (++st.misses[i] >= LOST_CENSUSES) {
                r.slotDied(s.id, i, now);                       // lost: the Clone Capsule's timer from now
                st.misses[i] = 0;
            }
        }
        st.censuses++;
    }

    /**
     * Record `s` is the one creature `m` was bound to: its id, and its core where the creature remembers it (an older
     * creature that keeps no core: yes). An id met twice - the registry's file lost, a new settlement given an old
     * settlement's number before that one's core loaded - is told apart here: neither adopts nor removes the other's.
     */
    public static boolean ofCore(ISettlementMemberSC m, SettlementRegistrySC.Settlement s) {
        int[] c = m.boundCore();
        return m.settlementId() == s.id && (c == null || c.length != 2 || c[0] == s.x && c[1] == s.z);
    }

    /** Every chunk within the belt's circle is loaded (a creature can't be anywhere else - the leash keeps the guards in it). */
    static boolean beltLoaded(World w, SettlementRegistrySC.Settlement s) {
        IChunkProvider cp = w.getChunkProvider();
        int reach = s.radius + SettlementSC.BELT;
        double r2 = (double) reach * reach;
        for (int cx = (s.x - reach) >> 4; cx <= (s.x + reach) >> 4; cx++) {
            for (int cz = (s.z - reach) >> 4; cz <= (s.z + reach) >> 4; cz++) {
                // the chunk's nearest point to the core
                double nx = Math.max(cx << 4, Math.min((cx << 4) + 15, s.x)) - s.x;
                double nz = Math.max(cz << 4, Math.min((cz << 4) + 15, s.z)) - s.z;
                if (nx * nx + nz * nz <= r2 && !cp.chunkExists(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the roster and the Clone Capsule

    /** NEVER slots whose posts are loaded and built. @return how many came */
    static int spawnNew(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, long now, int max) {
        int made = 0;
        for (int i = 0; i < s.slots.size() && made < max; i++) {
            SettlementRegistrySC.Slot sl = s.slots.get(i);
            if (sl.state == SettlementRegistrySC.NEVER && built(w, sl.postX, sl.postY, sl.postZ)
                    && spawn(w, r, s, i, sl.postX, sl.postY, sl.postZ, now)) {
                made++;
            }
        }
        return made;
    }

    /** ПП-13: DEAD slots back from the Clone Capsule - no player within RESPAWN_CLEAR; Tuners one a game day. @return how many came */
    static int cloneCapsule(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, long now, int max) {
        if (max <= 0 || playerNear(w, s)) {
            return 0;
        }
        long today = SettlementSC.day(w);
        NBTTagCompound c = s.extra.getCompoundTag(EXTRA);
        boolean tunerToday = c.hasKey(CLONE_DAY) && c.getLong(CLONE_DAY) == today;
        int[] at = s.point(SettlementRegistrySC.P_CLONE);
        int made = 0;
        for (int i = 0; i < s.slots.size() && made < max; i++) {
            SettlementRegistrySC.Slot sl = s.slots.get(i);
            if (sl.state != SettlementRegistrySC.DEAD) {
                continue;
            }
            NpcKindSC k = sl.kindOf();
            if (now - sl.diedAt < k.respawnTicks() || !k.guard && tunerToday) {
                continue;
            }
            int x = at != null ? at[0] : sl.postX, y = at != null ? at[1] : sl.postY, z = at != null ? at[2] : sl.postZ;
            if (!built(w, x, y, z) || !spawn(w, r, s, i, x, y, z, now)) {
                continue;
            }
            made++;
            w.playSoundEffect(x + 0.5D, y + 1D, z + 0.5D, "mob.endermen.portal", 0.6F, 1.4F);
            if (!k.guard) {
                tunerToday = true;
                c.setLong(CLONE_DAY, today);
                s.extra.setTag(EXTRA, c);
                r.markDirty();
            }
        }
        return made;
    }

    /** A player (any) within RESPAWN_CLEAR of the core, across. */
    static boolean playerNear(World w, SettlementRegistrySC.Settlement s) {
        double r = SettlementSC.RESPAWN_CLEAR;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (s.distSq(p.posX, p.posZ) <= r * r) {
                return true;
            }
        }
        return false;
    }

    /**
     * The place (x, y, z) is loaded and its settlement is built there: its chunk populated, and the chunk whose
     * populate window holds it (the structure is laid in populate, 8 blocks over) populated too. Never loads a chunk.
     */
    static boolean built(World w, int x, int y, int z) {
        if (!w.blockExists(x, y, z) || !w.getChunkFromBlockCoords(x, z).isTerrainPopulated) {
            return false;
        }
        int px = (x - 8) >> 4, pz = (z - 8) >> 4;
        return w.getChunkProvider().chunkExists(px, pz) && w.getChunkFromChunkCoords(px, pz).isTerrainPopulated;
    }

    /** Slot `i`'s creature, bound, standing at (x, y, z) - or the nearest free place round it (standAt). */
    static boolean spawn(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, int i, int x, int y, int z, long now) {
        SettlementRegistrySC.Slot sl = s.slots.get(i);
        EntityCreature e = SettlementSC.newMember(w, sl.kindOf());
        ((ISettlementMemberSC) e).bindTo(s, i);
        int[] at = standAt(w, x, y, z);
        e.setLocationAndAngles(at[0] + 0.5D, at[1], at[2] + 0.5D, w.rand.nextFloat() * 360F, 0F);
        if (!w.spawnEntityInWorld(e)) {
            return false;
        }
        r.slotAlive(s.id, i, e.getUniqueID(), now);
        return true;
    }

    /**
     * Two free blocks to stand in at (x, y, z): the place itself or up to FREE_UP over it, else beside it (the Clone
     * Capsule's point is the capsule itself, a post may be a machine); nothing free - the place as it is.
     */
    static int[] standAt(World w, int x, int y, int z) {
        for (int up = 0; up <= FREE_UP; up++) {
            if (free(w, x, y + up, z)) {
                return new int[]{x, y + up, z};
            }
        }
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int up = 0; up <= 1; up++) {
            for (int[] d : side) {
                if (w.blockExists(x + d[0], y + up, z + d[1]) && free(w, x + d[0], y + up, z + d[1])) {
                    return new int[]{x + d[0], y + up, z + d[1]};
                }
            }
        }
        return new int[]{x, y, z};
    }

    private static boolean free(World w, int x, int y, int z) {
        return y >= 0 && y + 1 < w.getHeight() && !w.getBlock(x, y, z).getMaterial().blocksMovement()
                && !w.getBlock(x, y + 1, z).getMaterial().blocksMovement();
    }

    // ------------------------------------------------------------------ visitors (ПД-5, ПР-3)

    static void visitors(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        Integer id = Integer.valueOf(s.id);
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!SettlementProtectSC.real(p) || !p.isEntityAlive() || !s.inBelt(p.posX, p.posZ)) {
                continue;
            }
            UUID u = p.getUniqueID();
            SettlementRegistrySC.PlayerRec pr = r.playerIfAny(u);
            boolean newDock = s.hasDock && (pr == null || !pr.knownDocks.contains(id));
            if (pr != null && pr.firstPass && !newDock) {
                continue;
            }
            if (pr == null) {
                pr = r.player(u);
            }
            if (!pr.firstPass) {
                pass(w, r, s, p, pr);
            }
            if (newDock) {
                pr.knownDocks.add(id);
                r.markDirty();
                SettlementProtectSC.say(p, SettlementProtectSC.colour(new ChatComponentTranslation("sc.settle.pass.dock",
                        SettlementSC.displayName(s)), EnumChatFormatting.DARK_AQUA), true);
            }
        }
    }

    /** ПД-5: the Tuners' Pass - once a player, at the first settlement they come to. */
    static void pass(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, EntityPlayer p, SettlementRegistrySC.PlayerRec pr) {
        pr.firstPass = true;
        int got = ReputationSC.give(pr, ReputationSC.PASS, ReputationSC.SRC_PASS, SettlementSC.day(w));
        ItemStack chips = SettlementItemsSC.chips(ReputationSC.PASS_CHIPS);
        if (chips != null) {
            p.inventory.addItemStackToInventory(chips);         // what doesn't fit is left in the stack
            if (chips.stackSize > 0) {
                p.dropPlayerItemWithRandomChoice(chips, false);
            }
        }
        r.markDirty();
        SettlementProtectSC.say(p, SettlementProtectSC.colour(new ChatComponentTranslation("sc.settle.pass.granted",
                SettlementProtectSC.speaker(w, s, p.posX, p.posY, p.posZ), p.getCommandSenderName(), ReputationSC.signed(got),
                String.valueOf(ReputationSC.PASS_CHIPS)), EnumChatFormatting.AQUA), true);
        SettlementProtectSC.say(p, SettlementProtectSC.colour(new ChatComponentTranslation("sc.settle.pass.hint"),
                EnumChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ a lost record

    /**
     * The registry has no record `id` (its file deleted, a world copied without it): a new one from what the core knows
     * - the id, the core, the radius, the floor - put in under that very id (add() would hand out a new one, and the
     * creatures, the structure and the field's owner all name this one). While the generator's maths still has this
     * settlement at this core, its plan gives the size, the dock, the points and the slots (NEVER - the census adopts the
     * creatures still about, the roster makes the rest); otherwise the slots stay empty. The names are made afresh from
     * the seed (the old ones were the generator's). @return the record, null if it can't be made
     */
    public static SettlementRegistrySC.Settlement restore(World w, SettlementRegistrySC r, int id, int x, int y, int z,
                                                          int radius, int floorY, long now) {
        if (id <= 0) {
            return null;
        }
        SettlementRegistrySC.Settlement had = r.byId(id);
        if (had != null) {
            return had;
        }
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.id = id;
        s.dim = w.provider.dimensionId;
        s.x = x;
        s.y = y;
        s.z = z;
        s.floorY = floorY >= 0 ? floorY : y - 1;
        s.radius = Math.max(1, radius);
        s.size = sizeOf(s.radius).ordinal();
        s.seed = w.getSeed() ^ ((long) x * 341873128712L + (long) z * 132897987541L + id);
        s.madeAt = now;
        s.version = SettlementRegistrySC.VERSION;
        boolean planned = false;
        try {
            planned = fromPlan(s, w.getSeed());
        } catch (RuntimeException ex) {
            FMLLog.warning("[Silicon Age] Si-14 settlements: the plan of settlement %d couldn't be made again (%s) - restored without its roster", id, ex);
            s.slots.clear();
            s.points.clear();
            s.hasDock = false;
        }
        Random rand = new Random(s.seed);
        int code = NpcNamesSC.random(rand);
        s.nameElement = NpcNamesSC.element(code);
        s.nameNumber = NpcNamesSC.number(code);
        Set<Integer> used = new HashSet<Integer>();
        for (SettlementRegistrySC.Slot sl : s.slots) {
            int c;
            do {
                c = NpcNamesSC.random(rand);
            } while (!used.add(Integer.valueOf(c)));
            sl.nameElement = NpcNamesSC.element(c);
            sl.nameNumber = NpcNamesSC.number(c);
        }
        NBTTagCompound mine = new NBTTagCompound();
        mine.setBoolean(RESTORED, true);
        s.extra.setTag(EXTRA, mine);
        r.restore(s);
        FMLLog.warning("[Silicon Age] Si-14 settlements: record %d was missing - made again from its core at %d %d %d in dimension %d (%s)",
                id, x, y, z, s.dim, planned ? s.slots.size() + " slots from the plan" : "no roster");
        return r.byId(id);
    }

    /** Fills `s` from the generator's plan if that plan's core is this core. @return whether it did */
    private static boolean fromPlan(SettlementRegistrySC.Settlement s, long worldSeed) {
        SettlementLayoutSC.Site site = SettlementLayoutSC.site(worldSeed, SettlementLayoutSC.regionOf(s.x), SettlementLayoutSC.regionOf(s.z));
        if (site == null || site.x != s.x || site.z != s.z) {
            return false;
        }
        SettlementLayoutSC.Plan plan = SettlementLayoutSC.plan(worldSeed, site);
        s.size = site.size.ordinal();
        s.seed = site.seed;
        s.first = site.first;
        if (plan.dock) {
            s.hasDock = true;
            s.dockX = plan.dockX;
            s.dockY = plan.dockY;
            s.dockZ = plan.dockZ;
        }
        for (int i = 0; i < plan.pointNames.size() && i < plan.points.size(); i++) {
            s.points.put(plan.pointNames.get(i), plan.points.get(i).clone());
        }
        for (SettlementLayoutSC.Post post : plan.posts) {
            SettlementRegistrySC.Slot sl = new SettlementRegistrySC.Slot();
            sl.kind = post.kind.ordinal();
            sl.postX = post.x;
            sl.postY = post.y;
            sl.postZ = post.z;
            sl.homeX = post.homeX;
            sl.homeY = post.homeY;
            sl.homeZ = post.homeZ;
            sl.state = SettlementRegistrySC.NEVER;
            s.slots.add(sl);
        }
        return true;
    }

    /** The size a dome of `radius` is (the record lost it; the self-test asks it). */
    public static SettlementSizeSC sizeOf(int radius) {
        if (radius <= SettlementSizeSC.OUTPOST.radiusMax) {
            return SettlementSizeSC.OUTPOST;
        }
        return radius <= SettlementSizeSC.SETTLEMENT.radiusMax ? SettlementSizeSC.SETTLEMENT : SettlementSizeSC.CITY;
    }
}
