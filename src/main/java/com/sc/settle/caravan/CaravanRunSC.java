package com.sc.settle.caravan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.hazard.HazardNetSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.ai.NpcNavSC;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.1-§8.4, rules ПН-13..ПН-16): one caravan under way, in memory only (CaravanSC keeps the
 * one of the world; a restart forgets it - its creatures leave on their next tick, its ambushers are ordinary mobs again on their next
 * load). Made by begin, a step every CaravanSC.RUN_TICKS:
 * <ul>
 * <li>the start (ПН-13): its source - the nearest other settlement of the registry within NEIGHBOUR_MAX, else the dock, else the gate;
 * the first of CaravanRunPathSC.spawnSpots that is loaded with SPAWN_LOADED round it (a creature farther than that from an unloaded
 * chunk never ticks), is not in a dome's belt and has room - the master there, its GUARDS on either side; the route
 * (CaravanRunPathSC.route) from there to the street's point at the guard post's depth; sc.caravan.coming to those near;</li>
 * <li>the walk is the master's own (EntityAICaravanWalkSC: the waypoints - waypoint / passed - by the move helper), the guards' escort
 * and fight theirs (EntityAICaravanEscortSC, EntityAICaravanGuardTargetSC);</li>
 * <li>the ambush (ПН-14): when the master passes the route's half-way waypoint and the day rolled one (ambushRoll), not on Peaceful -
 * ambushSize(n) Renegades (n - the players near: Marauders, from the second player on a Trooper each - SpawnDirectorSC.spawnAt, born in
 * Si-14, the usual elite roll) AMBUSH_DIST off the route, on its outer side first (out of the dome's belt), never in a dome; they go
 * for the caravan (a target each step: the nearest of its creatures; players - their own AI). No room: tried again AMBUSH_RETRIES
 * steps, then none. Ambushed (AMBUSHED - the master stands behind its guards) until none of them is left: every one killed and the
 * master alive - beaten off (ПН-15): each real player within DEFENDER_REACH of the master (not in creative; the world test's `by` too)
 * REWARD_REP «Доверие» (SRC_CARAVAN, no day cap) and the rare deal (CaravanSC.grantDeal), sc.caravan.defended; any of them removed
 * alive (a cleaner mod, Peaceful switched on - one killed and the rest gone with it is no defence) - called off, nothing; then on
 * its way again;</li>
 * <li>the end: the master killed (ПН-15) - sc.caravan.lost, nothing; the master within ARRIVE_REACH of the gate, at the route's end or
 * TIMEOUT_TICKS after the start (ПН-16) - ARRIVED, sc.caravan.arrived; either way its creatures still loaded leave (the sparks) and the
 * ambushers still about lose their NBT and persistence. The master removed alive, its record gone - over, quietly.</li>
 * </ul>
 * Its creatures are found again after their chunk slept by claim (the entities' first tick: by UUID). Server only.
 */
public final class CaravanRunSC {

    /** The state the master shows its clients (EntityCaravanMasterSC.DW_CARAVAN). */
    public static final int WALKING = 0, AMBUSHED = 1, ARRIVED = 2;
    /** How a run ended (CaravanSC.lastEnd - the world test): not yet, arrived (or its time ran out), lost (the master killed), gone. */
    public static final int END_NONE = 0, END_ARRIVED = 1, END_LOST = 2, END_GONE = 3;
    /** The ambushers' entity NBT key (getEntityData): their caravan's id. */
    public static final String AMBUSH_NBT = "scCaravanAmbush";
    /** The start needs the chunks this far round it loaded (vanilla updates an entity only with 32 blocks round it loaded). */
    public static final int SPAWN_LOADED = 32;
    /** The guards stand this far to either side of the master at the start. */
    static final double GUARD_SPREAD = 1.5D;
    /** One ambusher: this many spots tried, spread this far along the route and +-AMBUSH_JITTER off its distance. */
    static final int AMBUSH_TRIES = 8, AMBUSH_SPREAD = 6, AMBUSH_JITTER = 2;
    /** An ambush that found no room is tried again this many steps, then there is none. */
    static final int AMBUSH_RETRIES = 5;
    /** The lines go to the real players within PLAYER_REACH + this of the core, and within DEFENDER_REACH of the master. */
    static final int AUDIENCE_MARGIN = 32;
    /** The ambushers' march to the caravan when they have nobody in reach (a multiple of their speed). */
    static final double MARCH = 1.0D;

    final long id;
    final int dim, settlementId;
    private final long startedAt;
    /** The world test's defender (a FakePlayer is in no player list), or null. */
    private final EntityPlayer by;
    /** The route's waypoints {x, z} and their heights (0 - not looked up yet). */
    private final int[][] route;
    private final int[] heights;
    private final int half;
    private int next;
    /** The guard post (P_GATE), or the route's last point. */
    private final int[] gate;
    private EntityCaravanMasterSC master;
    private final UUID masterId;
    private final EntityCaravanGuardSC[] guards;
    private final UUID[] guardIds;
    private final List<EntityLiving> ambushers = new ArrayList<EntityLiving>();
    private boolean ambushDue, ambushActive;
    /** An ambusher was removed alive (Peaceful, a cleaner mod, another world) - the ambush ends with no reward. */
    private boolean ambushLost;
    private int ambushTries, ambushKilled;
    private int state = WALKING;
    /** Players rewarded by the last beaten-off ambush (the world test). */
    private int rewarded;

    private CaravanRunSC(long id, WorldServer w, SettlementRegistrySC.Settlement s, EntityPlayer by, int[][] route, int[] gate,
            boolean ambush, EntityCaravanMasterSC master, EntityCaravanGuardSC[] guards) {
        this.id = id;
        this.dim = w.provider.dimensionId;
        this.settlementId = s.id;
        this.startedAt = w.getTotalWorldTime();
        this.by = by;
        this.route = route;
        this.heights = new int[route.length];
        this.half = CaravanRunPathSC.half(route);
        this.gate = gate;
        this.ambushDue = ambush;
        this.master = master;
        this.masterId = master.getUniqueID();
        this.guards = guards;
        this.guardIds = new UUID[guards.length];
        for (int i = 0; i < guards.length; i++) {
            guardIds[i] = guards[i] == null ? null : guards[i].getUniqueID();
        }
    }

    // ------------------------------------------------------------------ the start

    /**
     * ПН-13: a caravan of id `id` to `s` begins - its source, its start, its route, its creatures in the world, sc.caravan.coming.
     * `ambush` - it will be ambushed half-way. @return the run, or null (no loaded room on the plateau for its start)
     */
    static CaravanRunSC begin(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, EntityPlayer by, boolean ambush, long id) {
        int dimId = w.provider.dimensionId;
        int[] g = s.point(SettlementRegistrySC.P_GATE);
        boolean hasGate = g != null && g.length == 3 && g[1] > 0;
        double sx, sz;                                          // the source's way from the core
        SettlementRegistrySC.Settlement from = neighbour(r, s);
        if (from != null) {
            sx = from.x - s.x;
            sz = from.z - s.z;
        } else if (s.hasDock) {
            sx = s.dockX - s.x;
            sz = s.dockZ - s.z;
        } else if (hasGate) {
            sx = g[0] - s.x;
            sz = g[2] - s.z;
        } else {
            double a = w.rand.nextDouble() * Math.PI * 2D;
            sx = Math.cos(a);
            sz = Math.sin(a);
        }
        double ax = s.hasDock ? s.dockX - s.x : hasGate ? g[0] - s.x : sx, az = s.hasDock ? s.dockZ - s.z : hasGate ? g[2] - s.z : sz;
        int axis = CaravanRunPathSC.axis(ax, az);
        int along = hasGate ? Math.max(1, Math.abs(axis % 2 == 0 ? g[2] - s.z : g[0] - s.x)) : Math.max(1, s.radius - 4);
        int[] gatePoint = hasGate ? new int[]{g[0], g[1], g[2]} : null;

        EntityCaravanMasterSC m = new EntityCaravanMasterSC(w);
        m.join(s, id);
        for (int[] c : CaravanRunPathSC.spawnSpots(s.x, s.z, s.radius, Math.atan2(sz, sx))) {
            if (!startFits(w, r, dimId, c[0], c[1], m)) {
                continue;
            }
            int[][] route = CaravanRunPathSC.route(s.x, s.z, s.radius, c[0], c[1], axis, along);
            if (route.length == 0) {
                continue;
            }
            if (gatePoint == null) {
                int[] last = route[route.length - 1];
                gatePoint = new int[]{last[0], s.floorY + 1, last[1]};
            }
            int[] first = route[0];
            m.rotationYaw = m.rotationYawHead = m.renderYawOffset = yawTo(m.posX, m.posZ, first[0] + 0.5D, first[1] + 0.5D);
            if (!w.spawnEntityInWorld(m)) {
                return null;
            }
            EntityCaravanGuardSC[] gs = new EntityCaravanGuardSC[CaravanRulesSC.GUARDS];
            double yaw = Math.toRadians(m.rotationYaw), px = Math.cos(yaw), pz = Math.sin(yaw);
            for (int i = 0; i < gs.length; i++) {
                int side = i % 2 == 0 ? -1 : 1;
                EntityCaravanGuardSC gd = new EntityCaravanGuardSC(w);
                gd.join(s, id, side);
                double spread = GUARD_SPREAD * (1 + i / 2);
                double x = m.posX + px * side * spread, z = m.posZ + pz * side * spread;
                gd.setLocationAndAngles(x, m.posY, z, m.rotationYaw, 0F);
                if (!room(w, gd)) {
                    gd.setLocationAndAngles(m.posX, m.posY, m.posZ, m.rotationYaw, 0F);
                }
                gd.setMasterEntityId(m.getEntityId());
                gs[i] = w.spawnEntityInWorld(gd) ? gd : null;
            }
            CaravanRunSC run = new CaravanRunSC(id, w, s, by, route, gatePoint, ambush, m, gs);
            for (EntityPlayer p : run.audience(w, s)) {
                tell(p, EnumChatFormatting.AQUA, "sc.caravan.coming", SettlementSC.displayName(s));
            }
            return run;
        }
        return null;
    }

    /** ПН-13: the nearest other settlement of the registry within NEIGHBOUR_MAX of `s` (the same world), or null. */
    static SettlementRegistrySC.Settlement neighbour(SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        SettlementRegistrySC.Settlement best = null;
        double bd = (double) CaravanRulesSC.NEIGHBOUR_MAX * CaravanRulesSC.NEIGHBOUR_MAX;
        for (SettlementRegistrySC.Settlement o : r.near(s.dim, s.x, s.z, CaravanRulesSC.NEIGHBOUR_MAX)) {
            if (o.id == s.id || o.dim != s.dim) {
                continue;
            }
            double d = s.distSq(o.x + 0.5D, o.z + 0.5D);
            if (d <= bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** The master `m` may start at column (x, z): loaded SPAWN_LOADED round, out of every dome's belt, ground and room (`m` put there). */
    private static boolean startFits(WorldServer w, SettlementRegistrySC r, int dimId, int x, int z, EntityCaravanMasterSC m) {
        if (!w.checkChunksExist(x - SPAWN_LOADED, 0, z - SPAWN_LOADED, x + SPAWN_LOADED, 0, z + SPAWN_LOADED)
                || r.inBelt(dimId, x + 0.5D, z + 0.5D) != null) {
            return false;
        }
        int y = NpcNavSC.groundY(w, x, z);
        if (y <= 0) {
            return false;
        }
        m.setLocationAndAngles(x + 0.5D, y, z + 0.5D, 0F, 0F);
        return room(w, m);
    }

    /** No block in its box, no liquid. */
    private static boolean room(World w, EntityLiving e) {
        return w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() && !w.isAnyLiquid(e.boundingBox);
    }

    // ------------------------------------------------------------------ the creatures (claim, the AI's questions)

    /**
     * The caravan's creature `e` (its scCaravan matched this run's id) - this run's master or one of its guards by UUID: its instance
     * taken (its chunk loaded again: a new one), its side and its master's id set. @return it is this run's
     */
    boolean claim(EntityLiving e) {
        UUID u = e.getUniqueID();
        if (e instanceof EntityCaravanMasterSC) {
            if (!u.equals(masterId)) {
                return false;
            }
            master = (EntityCaravanMasterSC) e;
            master.setCaravanState(state);
            return true;
        }
        if (e instanceof EntityCaravanGuardSC) {
            for (int i = 0; i < guardIds.length; i++) {
                if (u.equals(guardIds[i])) {
                    EntityCaravanGuardSC g = (EntityCaravanGuardSC) e;
                    guards[i] = g;
                    g.setSide(i % 2 == 0 ? -1 : 1);
                    if (master != null) {
                        g.setMasterEntityId(master.getEntityId());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** `e` is this run's master or guard (the instance it knows). */
    boolean member(Entity e) {
        if (e == null) {
            return false;
        }
        if (e == master) {
            return true;
        }
        for (EntityCaravanGuardSC g : guards) {
            if (g == e) {
                return true;
            }
        }
        return false;
    }

    /** The master, if it is loaded and alive now; else null. */
    public EntityCaravanMasterSC master() {
        return loaded(master) ? master : null;
    }

    /** The guards loaded and alive now (a new list). */
    public List<EntityCaravanGuardSC> guards() {
        List<EntityCaravanGuardSC> out = new ArrayList<EntityCaravanGuardSC>(guards.length);
        for (EntityCaravanGuardSC g : guards) {
            if (loaded(g)) {
                out.add(g);
            }
        }
        return out;
    }

    /** The ambushers loaded and alive now (a new list; none - empty). */
    public List<EntityLiving> ambushers() {
        if (ambushers.isEmpty()) {
            return Collections.emptyList();
        }
        List<EntityLiving> out = new ArrayList<EntityLiving>(ambushers.size());
        for (EntityLiving e : ambushers) {
            if (loaded(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** ПН-14: the caravan is ambushed now - the master stands behind its guards. */
    public boolean holds() {
        return ambushActive;
    }

    /** The ambusher nearest to the master (loaded, alive), or null. */
    public EntityLivingBase threat() {
        EntityCaravanMasterSC m = master();
        if (m == null) {
            return null;
        }
        EntityLivingBase best = null;
        double bd = Double.MAX_VALUE;
        for (EntityLiving e : ambushers) {
            if (loaded(e)) {
                double d = m.getDistanceSqToEntity(e);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /** The waypoint the master walks to now {x, y, z} (its height looked up the first time: the loaded top, else the terrace), null - the route's end. */
    public int[] waypoint(World w) {
        if (next >= route.length) {
            return null;
        }
        if (heights[next] <= 0) {
            int x = route[next][0], z = route[next][1];
            int y = NpcNavSC.groundY(w, x, z);
            heights[next] = y > 0 ? y : SubstrateTerrainSC.surfaceY(w.getSeed(), x, z) + 1;
        }
        return new int[]{route[next][0], heights[next], route[next][1]};
    }

    /** The master passed (or gave up) the waypoint: the next one. */
    public void passed() {
        if (next < route.length) {
            next++;
        }
    }

    /** The route's waypoints (a copy, the world test and the handbook). */
    public int[][] route() {
        int[][] out = new int[route.length][];
        for (int i = 0; i < route.length; i++) {
            out[i] = route[i].clone();
        }
        return out;
    }

    int state() {
        return state;
    }

    int rewarded() {
        return rewarded;
    }

    // ------------------------------------------------------------------ the step

    /** One step (CaravanSC's tick). @return END_NONE while it runs, else how it ended (its creatures gone, the ambushers released) */
    int step(WorldServer w) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(settlementId);
        if (s == null || s.dim != dim || w.provider.dimensionId != dim) {
            end(w);
            return END_GONE;
        }
        if (master != null && master.getHealth() <= 0F) {
            for (EntityPlayer p : audience(w, s)) {           // ПН-15: the master killed - nothing
                tell(p, EnumChatFormatting.RED, "sc.caravan.lost");
            }
            end(w);
            return END_LOST;
        }
        if (master == null || master.isDead) {
            end(w);                                             // removed alive (another mod, another world): over, quietly
            return END_GONE;
        }
        EntityCaravanMasterSC m = master();
        boolean timeUp = w.getTotalWorldTime() - startedAt >= CaravanRulesSC.TIMEOUT_TICKS;
        boolean there = m != null && !ambushActive && (next >= route.length || atGate(m));
        if (timeUp || there) {
            arrive(w, s);
            return END_ARRIVED;
        }
        for (EntityCaravanGuardSC g : guards) {
            if (loaded(g) && m != null) {
                g.setMasterEntityId(m.getEntityId());
            }
        }
        if (ambushDue && m != null && next >= half) {
            if (peaceful(w)) {
                ambushDue = false;                              // its Renegades would remove themselves at once
            } else if (spawnAmbush(w, r, s, m, half) > 0) {
                ambushDue = false;
            } else if (++ambushTries > AMBUSH_RETRIES) {
                ambushDue = false;                              // no room off the route: no ambush today
            }
        }
        if (ambushActive) {
            watchAmbush(w, r, s);
        }
        if (m != null) {
            m.setCaravanState(state);
        }
        return END_NONE;
    }

    /** The master within ARRIVE_REACH (across) of the guard post. */
    private boolean atGate(EntityCaravanMasterSC m) {
        double dx = gate[0] + 0.5D - m.posX, dz = gate[2] + 0.5D - m.posZ;
        return dx * dx + dz * dz <= (double) CaravanRulesSC.ARRIVE_REACH * CaravanRulesSC.ARRIVE_REACH;
    }

    // ------------------------------------------------------------------ the ambush (ПН-14, ПН-15)

    /**
     * ПН-14: the ambush by the route's waypoint `at` (or the master: a test) - ambushSize(n) Renegades AMBUSH_DIST off the route.
     * @return how many came (0 - no room)
     */
    int spawnAmbush(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, EntityCaravanMasterSC m, int at) {
        int i = Math.max(0, Math.min(route.length - 1, at));
        int ax, az;
        double dx, dz;
        if (at < 0 || route.length == 0) {
            ax = MathHelper.floor_double(m.posX);
            az = MathHelper.floor_double(m.posZ);
            int[] p = waypoint(w);
            dx = p == null ? 1D : p[0] + 0.5D - m.posX;
            dz = p == null ? 0D : p[2] + 0.5D - m.posZ;
        } else {
            ax = route[i][0];
            az = route[i][1];
            int[] a = route[Math.max(0, i - 1)], b = route[Math.min(route.length - 1, i + 1)];
            dx = b[0] - a[0];
            dz = b[1] - a[1];
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01D) {
            dx = 1D;
            dz = 0D;
            len = 1D;
        }
        dx /= len;
        dz /= len;
        double nx = -dz, nz = dx;                              // across the route
        if (nx * (ax + 0.5D - (s.x + 0.5D)) + nz * (az + 0.5D - (s.z + 0.5D)) < 0D) {
            nx = -nx;                                           // its outer side (away from the dome) first
            nz = -nz;
        }
        int n = CaravanRulesSC.ambushSize(present(w, m).size());
        int cellX = SubstrateCellsSC.cellOf(s.x), cellZ = SubstrateCellsSC.cellOf(s.z);
        int made = 0;
        for (int k = 0; k < n; k++) {
            int kind = k < CaravanRulesSC.AMBUSHERS ? MobEntitiesSC.ID_MARAUDER : MobEntitiesSC.ID_TROOPER;
            for (int t = 0; t < AMBUSH_TRIES; t++) {
                boolean outer = t < AMBUSH_TRIES / 2;
                double side = outer ? 1D : -1D;
                double off = CaravanRulesSC.AMBUSH_DIST + (w.rand.nextInt(AMBUSH_JITTER * 2 + 1) - AMBUSH_JITTER);
                double along = (w.rand.nextDouble() * 2D - 1D) * AMBUSH_SPREAD;
                int x = MathHelper.floor_double(ax + 0.5D + nx * side * off + dx * along);
                int z = MathHelper.floor_double(az + 0.5D + nz * side * off + dz * along);
                if (!CaravanRunPathSC.plateau(cellX, cellZ, x, z) || s.inBelt(x + 0.5D, z + 0.5D) && (outer || s.contains(x + 0.5D, s.floorY, z + 0.5D))
                        || r.protecting(dim, x + 0.5D, s.floorY + 1D, z + 0.5D) != null) {
                    continue;                                   // off the plateau, in a dome (the belt only on the inner side)
                }
                int y = NpcNavSC.groundY(w, x, z);
                if (y <= 0) {
                    continue;                                   // not loaded (never loads a chunk)
                }
                EntityLiving e = SpawnDirectorSC.spawnAt(w, kind, x + 0.5D, y, z + 0.5D, true, false);
                if (e == null) {
                    continue;
                }
                enlist(e, id);
                ambushers.add(e);
                made++;
                aim(e, m);
                break;
            }
        }
        if (made > 0) {
            ambushDue = false;                                  // one ambush a caravan (the world test's ambushNow too)
            ambushActive = true;
            ambushLost = false;
            state = AMBUSHED;
            m.setCaravanState(state);
            for (EntityPlayer p : audience(w, s)) {
                tell(p, EnumChatFormatting.RED, "sc.caravan.ambush");
            }
        }
        return made;
    }

    /**
     * ПН-14 / ПН-15: the ambush's dead counted, its living steered; none left - beaten off (every one killed, the master alive) or
     * called off (one removed alive: Peaceful, a cleaner - nothing).
     */
    private void watchAmbush(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        for (Iterator<EntityLiving> it = ambushers.iterator(); it.hasNext(); ) {
            EntityLiving e = it.next();
            if (e.getHealth() <= 0F) {
                ambushKilled++;                                 // killed for real (a blow, the void)
                it.remove();
            } else if (e.isDead) {
                it.remove();                                    // removed alive (Peaceful, a cleaner) - no kill
                ambushLost = true;
            }
        }
        EntityCaravanMasterSC m = master();
        if (!ambushers.isEmpty()) {
            if (m != null) {
                for (EntityLiving e : ambushers) {
                    if (loaded(e)) {
                        steer(e, m);
                    }
                }
            }
            return;
        }
        ambushActive = false;
        state = WALKING;
        // beaten off only when every ambusher was killed: one killed and the rest gone with Peaceful is no defence
        if (m != null && CaravanRulesSC.defended(m.getHealth() > 0F, 0) && ambushKilled > 0 && !ambushLost) {
            reward(w, r, s, m);
        }
    }

    /** ПН-15: the ambush beaten off - each defender within DEFENDER_REACH of the master: «Доверие», the rare deal, the line. */
    private void reward(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, EntityCaravanMasterSC m) {
        long day = SettlementSC.day(w);
        rewarded = 0;
        for (EntityPlayer p : defenders(w, m)) {
            int got = ReputationSC.give(r.player(p.getUniqueID()), CaravanRulesSC.REWARD_REP, ReputationSC.SRC_CARAVAN, day);
            CaravanSC.grantDeal(p);                             // a FakePlayer (the world test's): no deal - the core's rule
            rewarded++;
            tell(p, EnumChatFormatting.GREEN, "sc.caravan.defended", ReputationSC.signed(got), String.valueOf(CaravanRulesSC.RARE_DAYS));
        }
        r.markDirty();
    }

    /** An ambusher's orders: keep a live target in reach (a caravan's creature or a player); else the nearest of the caravan; else march to the master. */
    private void steer(EntityLiving e, EntityCaravanMasterSC m) {
        if (!(e instanceof EntityCreature)) {
            return;
        }
        EntityCreature c = (EntityCreature) e;
        double follow = c.getEntityAttribute(SharedMonsterAttributes.followRange).getAttributeValue();
        EntityLivingBase t = c.getAttackTarget();
        if (t != null && t.isEntityAlive() && (t instanceof EntityPlayer || member(t)) && c.getDistanceSqToEntity(t) <= follow * follow) {
            return;
        }
        if (!aim(c, m) && c.getNavigator().noPath()) {
            NpcNavSC.walkTo(c, m.posX, m.posY, m.posZ, MARCH);
        }
    }

    /** The ambusher's target - the caravan's creature nearest to it within its follow range. @return one was set */
    private boolean aim(EntityLiving e, EntityCaravanMasterSC m) {
        double follow = e.getEntityAttribute(SharedMonsterAttributes.followRange).getAttributeValue();
        EntityLivingBase best = null;
        double bd = follow * follow;
        if (loaded(m)) {
            double d = e.getDistanceSqToEntity(m);
            if (d <= bd) {
                bd = d;
                best = m;
            }
        }
        for (EntityCaravanGuardSC g : guards) {
            if (loaded(g)) {
                double d = e.getDistanceSqToEntity(g);
                if (d <= bd) {
                    bd = d;
                    best = g;
                }
            }
        }
        if (best != null) {
            e.setAttackTarget(best);
        }
        return best != null;
    }

    // ------------------------------------------------------------------ the end

    /** ПН-16: in at the gate, or its time out - ARRIVED, sc.caravan.arrived, into the dome. */
    private void arrive(WorldServer w, SettlementRegistrySC.Settlement s) {
        state = ARRIVED;
        if (loaded(master)) {
            master.setCaravanState(state);
        }
        for (EntityPlayer p : audience(w, s)) {
            tell(p, EnumChatFormatting.AQUA, "sc.caravan.arrived", SettlementSC.displayName(s));
        }
        end(w);
    }

    /** The run is over: its creatures still loaded leave (the sparks; the unloaded ones on their next tick), the ambushers released. */
    void end(World w) {
        if (loaded(master)) {
            master.leave();
        }
        for (EntityCaravanGuardSC g : guards) {
            if (loaded(g)) {
                g.leave();
            }
        }
        releaseAll();
    }

    /** The ambushers still about are ordinary mobs again (an unloaded one: on its next load - CaravanSC.onJoin). */
    void releaseAll() {
        for (EntityLiving e : ambushers) {
            if (loaded(e)) {
                release(e);
            }
        }
        ambushers.clear();
        ambushActive = false;
    }

    /** An ambusher loaded again (a new instance of the same UUID: CaravanSC.onJoin) - it takes its old self's place. */
    void adopt(EntityLiving e) {
        UUID u = e.getUniqueID();
        for (Iterator<EntityLiving> it = ambushers.iterator(); it.hasNext(); ) {
            EntityLiving o = it.next();
            if (o != e && u.equals(o.getUniqueID())) {
                it.remove();
            }
        }
        if (!ambushers.contains(e)) {
            ambushers.add(e);
        }
    }

    /** An ambusher of caravan `id`: the NBT, and it stays while the caravan runs. */
    static void enlist(EntityLiving e, long id) {
        e.getEntityData().setLong(AMBUSH_NBT, id);
        e.func_110163_bv();
    }

    /** No ambusher any more: the NBT off, vanilla's despawn again (EntityLiving.persistenceRequired). */
    static void release(EntityLiving e) {
        e.getEntityData().removeTag(AMBUSH_NBT);
        try {
            ReflectionHelper.setPrivateValue(EntityLiving.class, e, Boolean.FALSE, "persistenceRequired", "field_82179_bU");
        } catch (RuntimeException ex) {
            // another mapping: it stays (as a raid's raider would)
        }
    }

    // ------------------------------------------------------------------ who

    /** ПН-14: the players the ambush is sized for - real ones near (within PLAYER_REACH of the core or DEFENDER_REACH of the master), not in creative; the world test's `by`. */
    private List<EntityPlayer> present(WorldServer w, EntityCaravanMasterSC m) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(settlementId);
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        double core = CaravanRulesSC.PLAYER_REACH, near = CaravanRulesSC.DEFENDER_REACH;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (real(p) && p.isEntityAlive() && !p.capabilities.isCreativeMode
                    && (s != null && s.distSq(p.posX, p.posZ) <= core * core || m.getDistanceSqToEntity(p) <= near * near)) {
                out.add(p);
            }
        }
        if (by != null && !real(by) && by.isEntityAlive() && by.worldObj == w && !out.contains(by)) {
            out.add(by);                                        // the world test's FakePlayer
        }
        return out;
    }

    /** ПН-15: the defenders - real players within DEFENDER_REACH of the master, alive, not in creative (and the world test's `by` there). */
    private List<EntityPlayer> defenders(WorldServer w, EntityCaravanMasterSC m) {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        double reach = CaravanRulesSC.DEFENDER_REACH;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (real(p) && p.isEntityAlive() && !p.capabilities.isCreativeMode && m.getDistanceSqToEntity(p) <= reach * reach) {
                out.add(p);
            }
        }
        if (by != null && !real(by) && by.isEntityAlive() && by.worldObj == w && m.getDistanceSqToEntity(by) <= reach * reach && !out.contains(by)) {
            out.add(by);
        }
        return out;
    }

    /** Who hears the lines: the real players within PLAYER_REACH + AUDIENCE_MARGIN of the core, or within DEFENDER_REACH of the master. */
    List<EntityPlayer> audience(WorldServer w, SettlementRegistrySC.Settlement s) {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        double core = CaravanRulesSC.PLAYER_REACH + AUDIENCE_MARGIN, near = CaravanRulesSC.DEFENDER_REACH;
        EntityCaravanMasterSC m = master();
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (HazardNetSC.reachable(p) && (s.distSq(p.posX, p.posZ) <= core * core || m != null && m.getDistanceSqToEntity(p) <= near * near)) {
                out.add(p);
            }
        }
        return out;
    }

    /** A real player on the server (a FakePlayer gets no packets and earns nothing - but the world test's own `by`). */
    static boolean real(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer);
    }

    /** A chat line `key` in `colour` (its arguments inherit it) - to a real, connected player only. */
    static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (!HazardNetSC.reachable(p)) {
            return;
        }
        IChatComponent c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(c);
    }

    static boolean peaceful(World w) {
        return w.difficultySetting == EnumDifficulty.PEACEFUL;
    }

    /** Alive and in its world now (its chunk loaded). */
    static boolean loaded(EntityLiving e) {
        return e != null && !e.isDead && e.getHealth() > 0F && e.worldObj != null && e.worldObj.getEntityByID(e.getEntityId()) == e;
    }

    /** The yaw (degrees, Minecraft's: 0 = south) from (ox, oz) to (x, z). */
    private static float yawTo(double ox, double oz, double x, double z) {
        return (float) (Math.atan2(z - oz, x - ox) * 180D / Math.PI) - 90F;
    }
}
