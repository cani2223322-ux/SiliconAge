package com.sc.settle.npc.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sc.emp.EmpMobGunSC;
import com.sc.mob.MobCombatSC;
import com.sc.settle.GuardAlertSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE N (rules ПГ-5..ПГ-8, ПГ-10): a guard's target and alert, decided every EntityWardenSC.THINK ticks (each guard
 * on its own tick) - the only task of its targetTasks. In this order:
 * <ol>
 * <li>past its leash (NpcLinkSC.leash from the core; back within LEASH_BACK of it) - no target, it walks back
 * (EntityAIWardenReturnSC): it doesn't chase anyone out of its dome's reach and forgets;</li>
 * <li>the offender package C names (GuardAlertSC.target: provoked past the 2 s warning, or a local «Враг» in sight) -
 * the only way a player becomes its target; the warned player (GuardAlertSC.warned: it faces him, blade drawn,
 * alarm) and the watched one (GuardAlertSC.watched: it walks up and watches);</li>
 * <li>its current mob target while it is still huntable and seen within UNSEEN thinks; a non-player that hit it;</li>
 * <li>the nearest seen hostile (IMob, not a settlement's creature) within FOLLOW of it that is in the dome or HUNT_OUT
 * past its edge, or within HELP_RADIUS of a «Партнёр» (ПГ-10) - always within the leash. With a blade it leaves
 * creepers alone (a gun takes them).</li>
 * </ol>
 * The alert it shows: GuardAlertSC.alert, ATTACKING while it has a target. A stray (no record): only who hit it.
 * Players in creative are never its targets.
 * <p>
 * Ф3, package S (docs/plan-substrate-tech-f3.md §6.4, §6.5) - the Sentries' and the Senior Wardens' choice as above; in
 * steps 3-4 the chiefs choose their own way (the leash and the offender stay everyone's):
 * <ul>
 * <li>the Captain (ПГ-11) leads the guard: a hostile anywhere in the dome or its belt is his, however far from him -
 * the nearest seen, else the nearest in the dome (he walks to it: the attack tasks, legs of NpcNavSC); he keeps it while
 * it is there, seen at least once in CAPTAIN_UNSEEN thinks; he leaves creepers to the guns as any blade;</li>
 * <li>the Commandant (ПГ-12) keeps to his plaza (EntityAICommandantPostSC; never walked up to a target): a hostile
 * within his blade's reach of the plaza first; else, while EmpMobGunSC.allowed(), the nearest seen hostile in the mod's
 * armour (EntityWardenSC.empWorthy) in the dome or the belt within his ПЭ-1's reach (EntityAICommandantPulseSC).</li>
 * </ul>
 */
public class EntityAIWardenBrainSC extends EntityAIBase {

    /** Back from past the leash: until this much inside it. */
    public static final int LEASH_BACK = 8;
    /** A target unseen for this many thinks is dropped. */
    public static final int UNSEEN = 6;
    /** How far it looks for «Партнёры» to help or nod to (blocks). */
    public static final int PARTNER_REACH = 48;
    /** A player target farther than this is dropped (whatever package C says). */
    public static final double PLAYER_REACH = 64D;
    /** How many of the nearest hostiles it checks for a line of sight. */
    private static final int SIGHT_CHECKS = 3;
    /** Ф3: the Captain keeps a target unseen this many thinks (he walks round the buildings to it). */
    public static final int CAPTAIN_UNSEEN = 20;
    /** Ф3: the Captain looks over his dome and belt every this many thinks while he has nothing to fight. */
    private static final int CAPTAIN_LOOK = 2;
    /** Ф3: the chiefs look for hostiles this far below / above their dome's floor; the Commandant's plaza look at the least this far. */
    private static final int CHIEF_BELOW = 8, CHIEF_ABOVE = 16, PLAZA_LOOK = 16;

    private final EntityWardenSC g;
    private int unseen, looks;

    public EntityAIWardenBrainSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return true;
    }

    @Override
    public void updateTask() {
        if ((g.ticksExisted + g.getEntityId()) % EntityWardenSC.THINK == 0) {
            think();
        }
    }

    private void think() {
        long now = g.worldObj.getTotalWorldTime();
        SettlementRegistrySC.Settlement s = g.link().get();
        EntityLivingBase cur = g.getAttackTarget();
        if (s == null) {                                        // a stray: only who hits it (a player - while he keeps hitting)
            g.setBrain(null, null, false, null);
            EntityLivingBase rev = g.getAITarget();
            if (alive(rev) && !(rev instanceof ISettlementMemberSC) && (!(rev instanceof EntityPlayer) || playerOk((EntityPlayer) rev))) {
                cur = rev;
            } else if (!alive(cur) || cur instanceof EntityPlayer
                    || g.getDistanceSqToEntity(cur) > (EntityWardenSC.FOLLOW + 8D) * (EntityWardenSC.FOLLOW + 8D)) {
                cur = null;
            }
            target(cur);
            g.showAlert(cur != null ? GuardAlertSC.ATTACKING : GuardAlertSC.CALM);
            return;
        }
        int leash = NpcLinkSC.leash(s);
        double back = g.returning() ? leash - LEASH_BACK : leash;
        if (s.distSq(g.posX, g.posZ) > back * back) {
            target(null);
            g.setBrain(null, null, true, null);
            g.showAlert(GuardAlertSC.alert(s.id, g, now));
            return;
        }
        EntityPlayer off = GuardAlertSC.target(s.id, g, now);
        if (off != null && !playerOk(off)) {
            off = null;
        }
        EntityPlayer warned = off == null ? GuardAlertSC.warned(s.id, g, now) : null;
        EntityPlayer watched = off == null && warned == null ? GuardAlertSC.watched(s.id, g, now) : null;
        List<EntityPlayer> partners = partners(s, now, off);
        g.setBrain(warned, watched, false, partners);
        EntityLivingBase t;
        NpcKindSC rank = g.kind();
        if (off != null) {
            t = off;
        } else if (rank == NpcKindSC.CAPTAIN) {
            t = captain(s, cur, partners, leash);
        } else if (rank == NpcKindSC.COMMANDANT) {
            t = commandant(s, cur, leash);
        } else {
            if (cur instanceof EntityPlayer || !keep(s, cur, partners)) {
                cur = null;                                     // a player only while package C names him
            }
            EntityLivingBase rev = g.getAITarget();
            if (cur == null && alive(rev) && !(rev instanceof EntityPlayer) && !(rev instanceof ISettlementMemberSC)
                    && inLeash(s, rev, leash)) {
                cur = rev;
            }
            t = cur != null ? cur : hunt(s, partners, leash);
        }
        target(t);
        if (rank != NpcKindSC.COMMANDANT) {                    // the Commandant is never walked off his plaza
            approach(t);
        }
        int a = GuardAlertSC.alert(s.id, g, now);
        g.showAlert(t != null ? Math.max(a, GuardAlertSC.ATTACKING) : a);
    }

    /**
     * A target farther than the attack tasks' path search reaches (an offender package C names up to 96 blocks off):
     * a leg of the way towards it (NpcNavSC) while nothing else steers - closer, the attack tasks take over.
     */
    private void approach(EntityLivingBase t) {
        if (t != null && g.getNavigator().noPath() && g.getDistanceSqToEntity(t) > NpcNavSC.DIRECT * NpcNavSC.DIRECT) {
            NpcNavSC.walkTo(g, t.posX, t.posY, t.posZ, EntityWardenSC.CHASE);
        }
    }

    private void target(EntityLivingBase t) {
        if (t != g.getAttackTarget()) {
            g.setAttackTarget(t);
            unseen = 0;
        }
    }

    private static boolean alive(EntityLivingBase e) {
        return e != null && e.isEntityAlive();
    }

    /** A player it may fight: a real one, alive, here, not in creative, not too far. */
    private boolean playerOk(EntityPlayer p) {
        return !(p instanceof FakePlayer) && p.isEntityAlive() && p.worldObj == g.worldObj && !p.capabilities.isCreativeMode
                && g.getDistanceSqToEntity(p) <= PLAYER_REACH * PLAYER_REACH;
    }

    private static boolean inLeash(SettlementRegistrySC.Settlement s, EntityLivingBase e, int leash) {
        return s.distSq(e.posX, e.posZ) <= (double) leash * leash;
    }

    /** ПГ-5: in the dome or HUNT_OUT past its edge. */
    private static boolean inHunt(SettlementRegistrySC.Settlement s, EntityLivingBase e) {
        double r = s.radius + EntityWardenSC.HUNT_OUT;
        return s.distSq(e.posX, e.posZ) <= r * r;
    }

    /** ПГ-10: within HELP_RADIUS of one of the «Партнёры». */
    private static boolean nearPartner(EntityLivingBase e, List<EntityPlayer> partners) {
        double r = EntityWardenSC.HELP_RADIUS;
        for (EntityPlayer p : partners) {
            if (p.getDistanceSqToEntity(e) <= r * r) {
                return true;
            }
        }
        return false;
    }

    private boolean huntable(SettlementRegistrySC.Settlement s, EntityLivingBase e, List<EntityPlayer> partners, int leash) {
        return alive(e) && !(e instanceof ISettlementMemberSC) && inLeash(s, e, leash) && (inHunt(s, e) || nearPartner(e, partners))
                && (!(e instanceof EntityCreeper) || MobCombatSC.gun(g) != null);
    }

    /** The current mob target stays: still huntable (or the one that hit it), near enough, seen lately. */
    private boolean keep(SettlementRegistrySC.Settlement s, EntityLivingBase cur, List<EntityPlayer> partners) {
        int leash = NpcLinkSC.leash(s);
        if (!alive(cur) || cur instanceof ISettlementMemberSC || !inLeash(s, cur, leash)
                || g.getDistanceSqToEntity(cur) > (EntityWardenSC.FOLLOW + 8D) * (EntityWardenSC.FOLLOW + 8D)) {
            return false;
        }
        if (cur != g.getAITarget() && !huntable(s, cur, partners, leash)) {
            return false;
        }
        unseen = g.getEntitySenses().canSee(cur) ? 0 : unseen + 1;
        return unseen <= UNSEEN;
    }

    /** The nearest seen huntable hostile within FOLLOW, or null. */
    private EntityLivingBase hunt(SettlementRegistrySC.Settlement s, List<EntityPlayer> partners, int leash) {
        double r = EntityWardenSC.FOLLOW;
        List<?> l = g.worldObj.getEntitiesWithinAABB(IMob.class, g.boundingBox.expand(r, 8D, r));
        if (l.isEmpty()) {
            return null;
        }
        List<EntityLivingBase> found = new ArrayList<EntityLivingBase>();
        for (Object o : l) {
            if (o instanceof EntityLivingBase && huntable(s, (EntityLivingBase) o, partners, leash)
                    && g.getDistanceSqToEntity((EntityLivingBase) o) <= r * r) {
                found.add((EntityLivingBase) o);
            }
        }
        for (int i = 0; i < SIGHT_CHECKS && !found.isEmpty(); i++) {
            EntityLivingBase best = null;
            double bd = Double.MAX_VALUE;
            for (EntityLivingBase e : found) {
                double d = g.getDistanceSqToEntity(e);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (g.getEntitySenses().canSee(best)) {
                return best;
            }
            found.remove(best);
        }
        return null;
    }

    // ------------------------------------------------------------------ Ф3: the chiefs

    /** ПГ-11: the Captain's target - his current one while he may keep it, the one that hit him, else his dome's nearest hostile. */
    private EntityLivingBase captain(SettlementRegistrySC.Settlement s, EntityLivingBase cur, List<EntityPlayer> partners, int leash) {
        if (cur instanceof EntityPlayer || !keepLed(s, cur, partners, leash)) {
            cur = null;
        }
        EntityLivingBase rev = g.getAITarget();
        if (cur == null && alive(rev) && !(rev instanceof EntityPlayer) && !(rev instanceof ISettlementMemberSC) && inLeash(s, rev, leash)) {
            cur = rev;
        }
        if (cur == null && ++looks % CAPTAIN_LOOK == 0) {
            cur = lead(s, partners, leash);
        }
        return cur;
    }

    /** ПГ-11: the Captain keeps his target while it is in the belt (or huntable), within the leash, seen in CAPTAIN_UNSEEN thinks. */
    private boolean keepLed(SettlementRegistrySC.Settlement s, EntityLivingBase cur, List<EntityPlayer> partners, int leash) {
        if (!alive(cur) || cur instanceof ISettlementMemberSC || !inLeash(s, cur, leash)) {
            return false;
        }
        if (cur != g.getAITarget() && !s.inBelt(cur.posX, cur.posZ) && !huntable(s, cur, partners, leash)) {
            return false;
        }
        unseen = g.getEntitySenses().canSee(cur) ? 0 : unseen + 1;
        return unseen <= CAPTAIN_UNSEEN;
    }

    /** ПГ-11: the nearest seen hostile in the dome or the belt (within the leash); none seen - the nearest one in the dome. */
    private EntityLivingBase lead(SettlementRegistrySC.Settlement s, List<EntityPlayer> partners, int leash) {
        double r = s.radius + SettlementSC.BELT;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(s.x - r, s.floorY - CHIEF_BELOW, s.z - r, s.x + 1 + r, s.floorY + CHIEF_ABOVE, s.z + 1 + r);
        List<?> l = g.worldObj.getEntitiesWithinAABB(IMob.class, box);
        List<EntityLivingBase> found = new ArrayList<EntityLivingBase>();
        for (Object o : l) {
            if (o instanceof EntityLivingBase) {
                EntityLivingBase e = (EntityLivingBase) o;
                if (alive(e) && !(e instanceof ISettlementMemberSC) && inLeash(s, e, leash) && (s.inBelt(e.posX, e.posZ) || nearPartner(e, partners))
                        && (!(e instanceof EntityCreeper) || MobCombatSC.gun(g) != null)) {
                    found.add(e);
                }
            }
        }
        List<EntityLivingBase> inDome = new ArrayList<EntityLivingBase>();
        for (EntityLivingBase e : found) {
            if (s.contains(e.posX, e.posY, e.posZ)) {
                inDome.add(e);
            }
        }
        EntityLivingBase seen = nearestSeen(found, SIGHT_CHECKS);
        return seen != null ? seen : nearest(inDome);
    }

    /**
     * ПГ-12: the Commandant's target - one within his blade's reach of the plaza first (the current, the one that hit him, a
     * hostile there), else, while the ПЭ-1 is allowed, a hostile in the mod's armour in the dome or the belt within his
     * Pulse's reach, seen.
     */
    private EntityLivingBase commandant(SettlementRegistrySC.Settlement s, EntityLivingBase cur, int leash) {
        int[] plaza = g.plaza();
        boolean pe = EmpMobGunSC.allowed();
        double range = g.pulseRange(), reach = EntityAICommandantPostSC.BLADE_OUT;
        if (cur instanceof EntityPlayer || !keepCommandant(s, cur, plaza, pe, range, leash)) {
            cur = null;
        }
        EntityLivingBase rev = g.getAITarget();
        if (alive(rev) && !(rev instanceof EntityPlayer) && !(rev instanceof ISettlementMemberSC) && inLeash(s, rev, leash)
                && EntityWardenSC.nearPlaza(rev, plaza, reach) && (cur == null || !EntityWardenSC.nearPlaza(cur, plaza, reach))) {
            cur = rev;                                          // who hits him at the plaza comes before a far Shell
        }
        if (cur != null && EntityWardenSC.nearPlaza(cur, plaza, reach)) {
            return cur;
        }
        double r = Math.max(range, PLAZA_LOOK);
        List<?> l = g.worldObj.getEntitiesWithinAABB(IMob.class, g.boundingBox.expand(r, CHIEF_ABOVE, r));
        List<EntityLivingBase> near = new ArrayList<EntityLivingBase>(), armoured = new ArrayList<EntityLivingBase>();
        for (Object o : l) {
            if (!(o instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase e = (EntityLivingBase) o;
            if (!alive(e) || e instanceof ISettlementMemberSC || !inLeash(s, e, leash) || !s.inBelt(e.posX, e.posZ)) {
                continue;
            }
            if (EntityWardenSC.nearPlaza(e, plaza, reach)) {
                if (!(e instanceof EntityCreeper)) {
                    near.add(e);
                }
            } else if (pe && EntityWardenSC.empWorthy(e) && g.getDistanceSqToEntity(e) <= range * range) {
                armoured.add(e);
            }
        }
        EntityLivingBase t = nearestSeen(near, SIGHT_CHECKS);
        if (t != null) {
            return t;
        }
        return cur != null ? cur : nearestSeen(armoured, SIGHT_CHECKS);
    }

    /** ПГ-12: the Commandant keeps his target - at the plaza, or a seen far armoured hostile his Pulse may take. */
    private boolean keepCommandant(SettlementRegistrySC.Settlement s, EntityLivingBase cur, int[] plaza, boolean pe, double range, int leash) {
        if (!alive(cur) || cur instanceof ISettlementMemberSC || !inLeash(s, cur, leash) || !s.inBelt(cur.posX, cur.posZ)) {
            return false;
        }
        boolean ok = EntityWardenSC.nearPlaza(cur, plaza, EntityAICommandantPostSC.BLADE_OUT)
                || pe && EntityWardenSC.empWorthy(cur) && g.getDistanceSqToEntity(cur) <= range * range;
        if (!ok) {
            return false;
        }
        unseen = g.getEntitySenses().canSee(cur) ? 0 : unseen + 1;
        return unseen <= UNSEEN;
    }

    /** The nearest of `found` it sees, checking at most `checks` of the nearest (the unseen are taken out of `found`); null none. */
    private EntityLivingBase nearestSeen(List<EntityLivingBase> found, int checks) {
        for (int i = 0; i < checks && !found.isEmpty(); i++) {
            EntityLivingBase best = nearest(found);
            if (g.getEntitySenses().canSee(best)) {
                return best;
            }
            found.remove(best);
        }
        return null;
    }

    /** The nearest of `l` to the guard, null for none. */
    private EntityLivingBase nearest(List<EntityLivingBase> l) {
        EntityLivingBase best = null;
        double bd = Double.MAX_VALUE;
        for (EntityLivingBase e : l) {
            double d = g.getDistanceSqToEntity(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** ПГ-10: the players within PARTNER_REACH at «Партнёр» or better who aren't this dome's «Враг» (nor the offender). */
    private List<EntityPlayer> partners(SettlementRegistrySC.Settlement s, long now, EntityPlayer off) {
        List<EntityPlayer> out = null;
        SettlementRegistrySC r = null;
        double reach = PARTNER_REACH;
        for (Object o : g.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p == off || !p.isEntityAlive() || g.getDistanceSqToEntity(p) > reach * reach) {
                continue;
            }
            if (r == null) {
                r = SettlementRegistrySC.get(g.worldObj);
                if (r == null) {
                    break;
                }
            }
            SettlementRegistrySC.PlayerRec rec = r.playerIfAny(p.getUniqueID());
            SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
            if (rec != null && ReputationSC.level(rec.reputation) >= ReputationSC.PARTNER && (l == null || !l.enemy(now))) {
                if (out == null) {
                    out = new ArrayList<EntityPlayer>(2);
                }
                out.add(p);
            }
        }
        return out == null ? Collections.<EntityPlayer>emptyList() : out;
    }
}
