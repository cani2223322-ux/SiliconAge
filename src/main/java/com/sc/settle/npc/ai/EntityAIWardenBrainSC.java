package com.sc.settle.npc.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sc.mob.MobCombatSC;
import com.sc.settle.GuardAlertSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
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

    private final EntityWardenSC g;
    private int unseen;

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
        if (off != null) {
            t = off;
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
        approach(t);
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
