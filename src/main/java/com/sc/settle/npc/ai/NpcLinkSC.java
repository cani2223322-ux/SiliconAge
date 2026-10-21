package com.sc.settle.npc.ai;

import java.util.UUID;

import com.sc.settle.ISettlementGuardSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.RosterSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.ViolationRulesSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.1): a settlement creature's link to its record - the server's
 * SettlementRegistrySC asked at most once every RESOLVE_TICKS (a map look-up, cached between), the record of another
 * world refused, and a record of the same id whose core isn't the creature's own (RosterSC.ofCore: an id met twice after
 * a lost registry - no home at a stranger's core). A bound creature out of the Substrate is removed (exiled: ПВ-8,
 * AE2's Spatial IO or another mod's teleport took it - the Clone Capsule makes it again at home). Re-sets the home area
 * every time the record is found again - vanilla doesn't save EntityCreature's home, so a creature loaded from its
 * chunk would otherwise wander off. The day's clock and the named points for the AI tasks (settle.npc.ai).
 * <p>
 * Server only: a client answers null for everything (the registry lives on the server).
 */
public final class NpcLinkSC {

    /** How often the record is looked up again (it may be made anew by package C after a lost file). */
    public static final int RESOLVE_TICKS = 200;
    /** ПЖ-4: by its machine until 11 500 (17:30), on the plaza until 13 500 (19:30), at home till dawn. TODO(balance) */
    public static final int WORK_UNTIL = 11500, PLAZA_UNTIL = 13500;
    public static final int PHASE_WORK = 0, PHASE_PLAZA = 1, PHASE_HOME = 2;
    /** A Tuner's home area: the dome less this (ПЖ-1: setHomeArea, radius - 2). */
    public static final int TUNER_HOME_INSET = 2;

    private final EntityCreature mob;
    private final ISettlementMemberSC member;
    private SettlementRegistrySC.Settlement cached;
    private long resolvedAt = -1L;

    public NpcLinkSC(EntityCreature mob) {
        this.mob = mob;
        this.member = (ISettlementMemberSC) mob;
    }

    /** The record (server), null for a stray (settlement -1), a lost record or another world's. */
    public SettlementRegistrySC.Settlement get() {
        if (member.settlementId() < 0 || mob.worldObj == null || mob.worldObj.isRemote) {
            return null;
        }
        long now = mob.worldObj.getTotalWorldTime();
        if (resolvedAt < 0L || now - resolvedAt >= RESOLVE_TICKS || now < resolvedAt) {
            resolvedAt = now;
            SettlementRegistrySC r = SettlementRegistrySC.get(mob.worldObj);
            SettlementRegistrySC.Settlement s = r == null ? null : r.byId(member.settlementId());
            if (s != null && (s.dim != mob.worldObj.provider.dimensionId || !RosterSC.ofCore(member, s))) {
                s = null;                                       // another world's, or another core's under the same id
            }
            if (s != null && (s != cached || !mob.hasHome())) {
                setHome(mob, s);
            }
            cached = s;
        }
        return cached;
    }

    /**
     * ПВ-8: the Substrate's natives stay in it - a bound creature found in another world (AE2's Spatial IO took it with
     * its chunk, another mod's teleport) is removed: setDead - no death, no drop, no slotDied; its core's census finds
     * the slot's creature lost (RosterSC.LOST_CENSUSES) and the Clone Capsule makes it again at home, so a copy taken
     * away never trades nor guards there. A stray (a spawn egg's) lives anywhere. Server, now and then from the
     * creature's living update. @return removed
     * <p>
     * Ф6 (docs/plan-substrate-tech-f6.md §0, ПП-14): «out of its world» is a world that has no settlements (SettlementSC.governs - the
     * Substrate, the Overworld's embassies), or one other than its record's (an embassy's settler carried into the Substrate, a
     * Substrate settler into the Overworld): the record's dimension decides when the record is there.
     */
    public static boolean exiled(EntityCreature mob) {
        int id = ((ISettlementMemberSC) mob).settlementId();
        if (mob.worldObj == null || mob.worldObj.isRemote || id < 0) {
            return false;
        }
        if (SettlementSC.governs(mob.worldObj)) {
            SettlementRegistrySC r = SettlementRegistrySC.get(mob.worldObj);
            SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
            if (s == null || s.dim == mob.worldObj.provider.dimensionId) {
                return false;                                   // at home (or its record is lost: the core makes it again)
            }
        } else if (mob.worldObj.provider.dimensionId == SettlementSC.EMBASSY_DIM && !com.sc.dim.SubstrateConfigSC.embassies) {
            // ПС-7 (audit 2026-10-20): the rule took the embassies away - its slot as never filled, so all come back at once with it
            SettlementRegistrySC r = SettlementRegistrySC.get(mob.worldObj);
            SettlementRegistrySC.Slot sl = r == null ? null : r.slot(id, ((ISettlementMemberSC) mob).slot());
            if (sl != null && sl.state == SettlementRegistrySC.ALIVE && sl.uuidMost == mob.getUniqueID().getMostSignificantBits()
                    && sl.uuidLeast == mob.getUniqueID().getLeastSignificantBits()) {
                r.slotNever(id, ((ISettlementMemberSC) mob).slot());
            }
        }
        mob.setDead();
        return true;
    }

    /** Look the record up again on the next ask (bindTo, a test). */
    public void forget() {
        resolvedAt = -1L;
        cached = null;
    }

    /** The creature's slot of its record, or null. */
    public SettlementRegistrySC.Slot slot() {
        SettlementRegistrySC.Settlement s = get();
        int i = member.slot();
        return s == null || i < 0 || i >= s.slots.size() ? null : s.slots.get(i);
    }

    /**
     * The home area: a Tuner's - the dome less TUNER_HOME_INSET; a guard's - its leash (the core's column, from the floor). Ф6
     * (ПЖ-11): a Tuner whose post lies out past that (an embassy's Dispatchers at the Vokzal, outside the dome) - round its post,
     * OUTPOST_HOME blocks, so the home's pull never drags it back into the dome.
     */
    public static void setHome(EntityCreature mob, SettlementRegistrySC.Settlement s) {
        if (!(mob instanceof ISettlementGuardSC)) {
            int[] post = postOf(mob, s);
            int r = Math.max(4, s.radius - TUNER_HOME_INSET);
            if (post != null && s.distSq(post[0] + 0.5D, post[2] + 0.5D) > (double) r * r) {
                mob.setHomeArea(post[0], post[1], post[2], OUTPOST_HOME);
                return;
            }
            mob.setHomeArea(s.x, s.floorY + 1, s.z, r);
            return;
        }
        mob.setHomeArea(s.x, s.floorY + 1, s.z, leash(s));
    }

    /** Ф6: the home area's radius round a Tuner's post out past its dome (an embassy's Vokzal office). */
    public static final int OUTPOST_HOME = 8;

    /** The post {x, y, z} of the slot `mob` holds in `s`, or null. */
    private static int[] postOf(EntityCreature mob, SettlementRegistrySC.Settlement s) {
        int i = ((ISettlementMemberSC) mob).slot();
        if (i < 0 || i >= s.slots.size()) {
            return null;
        }
        SettlementRegistrySC.Slot sl = s.slots.get(i);
        return new int[]{sl.postX, sl.postY, sl.postZ};
    }

    /**
     * ПГ-6: how far from the core a guard goes - ViolationRulesSC.LEASH (48: an Outpost's dome and its belt), a bigger
     * dome's guards the same belt past its edge (a City's edge is 56..64 from its core: a flat 48 wouldn't even let
     * them stand there). The same number GuardAlertSC forgets an offender by: ViolationRulesSC.leash. Pure.
     */
    public static int leash(SettlementRegistrySC.Settlement s) {
        return ViolationRulesSC.leash(s.radius);
    }

    /**
     * The creature died (its onDeath, server): its slot is DEAD from now - the Clone Capsule's timer (package C). Not
     * for a duplicate: a slot whose living creature is another (its UUID) stays as it is.
     */
    public static void died(EntityCreature mob) {
        ISettlementMemberSC m = (ISettlementMemberSC) mob;
        if (mob.worldObj == null || mob.worldObj.isRemote || m.settlementId() < 0) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(mob.worldObj);
        SettlementRegistrySC.Slot sl = r == null ? null : r.slot(m.settlementId(), m.slot());
        if (sl == null || sl.state == SettlementRegistrySC.DEAD) {
            return;
        }
        UUID u = sl.uuid();
        if (u == null || u.equals(mob.getUniqueID())) {
            r.slotDied(m.settlementId(), m.slot(), mob.worldObj.getTotalWorldTime());
        }
    }

    // ------------------------------------------------------------------ the clock (pure)

    /** PHASE_WORK / PHASE_PLAZA / PHASE_HOME at world time `t` (the Overworld's clock: 0 is dawn). */
    public static int phase(long t) {
        int d = (int) (t % SettlementSC.DAY);
        if (d < 0) {
            d += (int) SettlementSC.DAY;
        }
        return d < WORK_UNTIL ? PHASE_WORK : d < PLAZA_UNTIL ? PHASE_PLAZA : PHASE_HOME;
    }

    /** The night (PHASE_HOME): the Tuners are at home, two guards at the Heart. */
    public static boolean night(long t) {
        return phase(t) == PHASE_HOME;
    }

    // ------------------------------------------------------------------ points

    /** A point that was set (a slot's post or home of a settlement made without it is {0, 0, 0}). */
    public static boolean set(int[] p) {
        return p != null && p.length == 3 && p[1] > 0;
    }

    public static int[] post(SettlementRegistrySC.Slot sl) {
        int[] p = sl == null ? null : new int[]{sl.postX, sl.postY, sl.postZ};
        return set(p) ? p : null;
    }

    public static int[] home(SettlementRegistrySC.Slot sl) {
        int[] p = sl == null ? null : new int[]{sl.homeX, sl.homeY, sl.homeZ};
        return set(p) ? p : null;
    }

    /** A named point (SettlementRegistrySC.P_*), null if the plan has none. */
    public static int[] point(SettlementRegistrySC.Settlement s, String name) {
        int[] p = s == null ? null : s.point(name);
        return set(p) ? p : null;
    }

    /** Where to stand by the core: its column at the floor (the core itself sits on a pedestal under a glass cap). */
    public static int[] core(SettlementRegistrySC.Settlement s) {
        return new int[]{s.x, s.floorY + 1, s.z};
    }

    /** Squared distance across from the point's block centre to the entity. */
    public static double distSq(Entity e, int[] p) {
        double dx = p[0] + 0.5D - e.posX, dz = p[2] + 0.5D - e.posZ;
        return dx * dx + dz * dz;
    }

    /** Within `reach` across and a storey up or down of the point. */
    public static boolean near(Entity e, int[] p, double reach) {
        return p != null && distSq(e, p) <= reach * reach && Math.abs(p[1] - e.posY) < 2.5D;
    }
}
