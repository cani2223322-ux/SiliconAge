package com.sc.mob;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sc.emp.EmpMobSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ExplosionEvent;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §5, rules ПС-20..ПС-25): what an elite modifier (EliteModSC) does to an armoured
 * mob - set up once when SpawnDirectorSC makes it an elite (apply), then each tick of its life (serverTick: the Commander's
 * aura, the Repairer's recharge of its neighbours, the screen's recovery), at its death (onDeath: the Avenger's fuse), and
 * the damage its blows carry (damageMul: the Commander's group). Registered by SCMod on FML's bus (the Avenger's pending
 * blasts tick in WorldTickEvent) and on Forge's (onDetonate); clearAll on the server's stop. Server only.
 * <p>
 * Fixed signatures (EntityArmoredMobSC and SpawnDirectorSC call them); the bodies are A's:
 * <ul>
 * <li>«Командир»: while it fights (an attack target), an order every ORDER_EVERY_TICKS - the telegraph ACT_ORDER for
 * ORDER_TICKS, a line «<Командир · Штурмовик> Сомкнуть строй!» to the players within COMMANDER_RADIUS (each in its own
 * language), a horn; when the order's lead time is over, every armoured native within COMMANDER_RADIUS (and it) hits
 * x(1 + COMMANDER_BONUS) for ORDER_BUFF_TICKS (MobArmorSC.commandUntil) and, once in its life, gets COMMANDER_BONUS more
 * reserve (MobArmorSC.commandBoost);</li>
 * <li>«Заряженный», «Экранированный», «Ветеран» - once, in apply (the reserve, the personal screen - its recovery is
 * MobArmorSC's -, helium in the loop);</li>
 * <li>«Ремонтник»: REPAIR_UNITS into the reserve of every other armoured native within REPAIR_RADIUS every
 * REPAIR_EVERY_TICKS (not while a Siphon draws on it - MobArmorSC.recharge);</li>
 * <li>«Мститель»: dead, it beeps AVENGER_FUSE_TICKS, then World.newExplosion of AVENGER_RADIUS that breaks no block and
 * lights nothing (a dome's field puts it out as any blast) and hurts the living only - its own drops, the experience and
 * any item by it are taken off the blast's list (onDetonate); the pending blasts are kept here (the world, the point, the
 * tick) and dropped if their chunk is gone.</li>
 * </ul>
 * Per mob state is the mob's own (MobArmorSC's fields): two players fighting two Commanders share nothing.
 */
public final class EliteSC {

    public static final EliteSC INSTANCE = new EliteSC();
    /** The Commander's horn, the Avenger's fuse and beeps (vanilla sounds). */
    private static final String SND_ORDER = "note.pling", SND_FUSE = "game.tnt.primed", SND_BEEP = "note.pling";
    /** At most this many Avengers' blasts wait at once (a stray /kill of a crowd can't pile up work). */
    private static final int MAX_PENDING = 64;

    /** The Avenger's blasts waiting for their fuse (server thread only). */
    private static final List<Blast> PENDING = new ArrayList<Blast>();
    /** The Avenger's blast going off right now (inside World.newExplosion - onDetonate knows it by its point), else null. */
    private static Blast detonating;

    private static final class Blast {
        final int dim;
        final double x, y, z;
        /** The world's total time it goes off. */
        final long at;

        Blast(int dim, double x, double y, double z, long at) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.at = at;
        }
    }

    private EliteSC() {
    }

    /**
     * Server, once, from SpawnDirectorSC after onSpawnWithEgg (the mob dressed, its reserve set): the mob becomes an elite of
     * `mod` - MobArmorSC.elite, EntityArmoredMobSC.setEliteMod, the modifier's own change (Charged: +50% reserve; Shielded: the
     * personal screen; Veteran: helium). A mob that is already an elite or never may be one (alwaysElite) - unchanged.
     */
    public static void apply(EntityArmoredMobSC mob, EliteModSC mod) {
        if (mob == null || mod == null || mob.worldObj == null || mob.worldObj.isRemote || mob.executeImmune()) {
            return;                                     // executeImmune: an elite already (a modifier, the flag) or of its kind
        }
        MobArmorSC a = mob.mobArmor();
        a.elite = true;
        mob.setEliteMod(mod);
        switch (mod) {
            case CHARGED:
                a.reserveMax *= 1F + EliteModSC.CHARGED_BONUS;
                a.reserve = a.reserveMax;
                break;
            case SHIELDED:
                a.screen = true;                        // ПБ-19: up at once
                a.screenDownUntil = 0L;
                break;
            case VETERAN:
                a.helium = true;                        // suppression -20%; an Exo / Singular chestplate gets its shield
                a.emergency = false;
                break;
            default:
                break;                                  // the Commander, the Repairer, the Avenger work later
        }
        a.sync();
    }

    /** Server, every tick of an armoured mob (EntityArmoredMobSC.onLivingUpdate): its modifier's work; nothing for a plain one. */
    public static void serverTick(EntityArmoredMobSC mob) {
        EliteModSC m = mob.eliteMod();
        if (m == null || mob.worldObj == null || mob.worldObj.isRemote) {
            return;
        }
        long now = mob.worldObj.getTotalWorldTime();
        boolean alive = mob.isEntityAlive();
        long phase = now + mob.getEntityId();           // the elites don't all act on the same tick
        if (m == EliteModSC.COMMANDER) {
            MobArmorSC a = mob.mobArmor();
            if (a.orderAt > 0L && now - a.orderAt >= EliteModSC.ORDER_TICKS) {
                a.orderAt = 0L;
                if (mob.mobAction() == IMobActionSC.ACT_ORDER) {
                    mob.setMobAction(IMobActionSC.ACT_NONE);
                }
                if (alive) {
                    rally(mob, now);
                }
            } else if (a.orderAt == 0L && alive && phase % EliteModSC.ORDER_EVERY_TICKS == 0 && mob.getAttackTarget() != null) {
                order(mob, now);
            }
        } else if (m == EliteModSC.REPAIRER && alive && phase % EliteModSC.REPAIR_EVERY_TICKS == 0) {
            repair(mob);
        }
    }

    /** Server, the mob died (EntityArmoredMobSC.onDeath): the Avenger's fuse starts (AVENGER_FUSE_TICKS, then the blast). */
    public static void onDeath(EntityArmoredMobSC mob) {
        if (mob == null || mob.worldObj == null || mob.worldObj.isRemote || mob.eliteMod() != EliteModSC.AVENGER
                || PENDING.size() >= MAX_PENDING) {
            return;
        }
        World w = mob.worldObj;
        double y = mob.posY + mob.height * 0.5;
        PENDING.add(new Blast(w.provider.dimensionId, mob.posX, y, mob.posZ, w.getTotalWorldTime() + EliteModSC.AVENGER_FUSE_TICKS));
        w.playSoundEffect(mob.posX, y, mob.posZ, SND_FUSE, 1F, 1.2F);
    }

    /** Server: what its blows are multiplied by now (1 - none; a Commander's group: 1 + COMMANDER_BONUS). */
    public static float damageMul(EntityArmoredMobSC mob) {
        if (mob == null || mob.worldObj == null || mob.worldObj.isRemote) {
            return 1F;
        }
        return mob.worldObj.getTotalWorldTime() < mob.mobArmor().commandUntil ? 1F + EliteModSC.COMMANDER_BONUS : 1F;
    }

    /** The pending blasts (the Avenger's) whose fuse ran out this world tick. */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world == null || e.world.isRemote || PENDING.isEmpty()) {
            return;
        }
        World w = e.world;
        int dim = w.provider.dimensionId;
        long now = w.getTotalWorldTime();
        List<Blast> due = null;
        for (Iterator<Blast> it = PENDING.iterator(); it.hasNext(); ) {
            Blast b = it.next();
            if (b.dim != dim) {
                continue;
            }
            if (!w.blockExists((int) Math.floor(b.x), (int) Math.floor(b.y), (int) Math.floor(b.z))) {
                it.remove();                            // its chunk went away: no blast that would load it again
            } else if (now >= b.at) {
                it.remove();
                if (due == null) {
                    due = new ArrayList<Blast>();
                }
                due.add(b);                             // goes off past the loop: a blast that kills another Avenger adds to PENDING
            } else if ((b.at - now) % EliteModSC.AVENGER_BEEP_TICKS == 0) {
                float left = (b.at - now) / (float) EliteModSC.AVENGER_FUSE_TICKS;
                w.playSoundEffect(b.x, b.y, b.z, SND_BEEP, 1F, 2F - left);                     // faster to the ear: higher
            }
        }
        if (due == null) {
            return;
        }
        for (Blast b : due) {
            detonating = b;
            try {
                w.newExplosion(null, b.x, b.y, b.z, EliteModSC.AVENGER_RADIUS, false, false);   // ПС-21: no fire, no block broken
            } finally {
                detonating = null;
            }
        }
    }

    /** ПС-21: the Avenger's blast hurts and pushes the living only - the items and experience orbs round it are left whole. */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate e) {
        Blast b = detonating;
        Explosion x = e.explosion;
        if (b == null || e.world.isRemote || e.world.provider.dimensionId != b.dim || x.exploder != null
                || x.explosionX != b.x || x.explosionY != b.y || x.explosionZ != b.z) {
            return;                                     // not the Avenger's (a mob's death blast set off inside it, say)
        }
        for (Iterator<Entity> it = e.getAffectedEntities().iterator(); it.hasNext(); ) {
            Entity o = it.next();
            if (o instanceof EntityItem || o instanceof EntityXPOrb) {
                it.remove();
            }
        }
    }

    /** Server stopped: no pending blast kept. */
    public static void clearAll() {
        PENDING.clear();
    }

    // ------------------------------------------------------------------ the Commander, the Repairer

    /** The order is given: the telegraph (if the mob isn't winding up something else), the horn, the line to the players near. */
    private static void order(EntityArmoredMobSC mob, long now) {
        MobArmorSC a = mob.mobArmor();
        a.orderAt = now;
        if (mob.mobAction() == IMobActionSC.ACT_NONE) {
            mob.setMobAction(IMobActionSC.ACT_ORDER);
        }
        World w = mob.worldObj;
        w.playSoundEffect(mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ, SND_ORDER, 1.5F, 0.6F);
        IChatComponent who = eliteName(mob);
        who.getChatStyle().setColor(EnumChatFormatting.GOLD);
        ChatComponentTranslation line = new ChatComponentTranslation("chat.type.text", who,
                new ChatComponentTranslation("sc.elite.order." + mob.getRNG().nextInt(EliteModSC.ORDER_LINES)));
        double r2 = EliteModSC.COMMANDER_RADIUS * EliteModSC.COMMANDER_RADIUS;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer)
                    && p.getDistanceSqToEntity(mob) <= r2) {
                p.addChatComponentMessage(line);
            }
        }
    }

    /** The order's lead time is over: the group within COMMANDER_RADIUS (the Commander too) hits harder, its reserve once larger. */
    private static void rally(EntityArmoredMobSC mob, long now) {
        for (EntityArmoredMobSC o : near(mob, EliteModSC.COMMANDER_RADIUS)) {
            MobArmorSC a = o.mobArmor();
            a.commandUntil = Math.max(a.commandUntil, now + EliteModSC.ORDER_BUFF_TICKS);
            a.commandBoost(EliteModSC.COMMANDER_BONUS);
        }
    }

    /** REPAIR_UNITS into every other armoured native's reserve within REPAIR_RADIUS (a short green glint on the ones it fed). */
    private static void repair(EntityArmoredMobSC mob) {
        for (EntityArmoredMobSC o : near(mob, EliteModSC.REPAIR_RADIUS)) {
            MobArmorSC a = o.mobArmor();
            if (o == mob || a.suit() == null || a.reserve >= a.reserveMax) {
                continue;
            }
            float was = a.reserve;
            a.recharge(EliteModSC.REPAIR_UNITS);
            if (a.reserve > was && mob.worldObj instanceof WorldServer) {
                ((WorldServer) mob.worldObj).func_147487_a("happyVillager", o.posX, o.posY + o.height * 0.6, o.posZ, 2, 0.3, 0.4, 0.3, 0.0);
            }
        }
    }

    /** The living armoured natives within `radius` blocks of `mob` (it included). */
    private static List<EntityArmoredMobSC> near(EntityArmoredMobSC mob, double radius) {
        List<EntityArmoredMobSC> out = new ArrayList<EntityArmoredMobSC>();
        AxisAlignedBB box = mob.boundingBox.expand(radius, radius, radius);
        double r2 = radius * radius;
        for (Object o : mob.worldObj.getEntitiesWithinAABB(EntityArmoredMobSC.class, box)) {
            EntityArmoredMobSC e = (EntityArmoredMobSC) o;
            if (e.isEntityAlive() && e.getDistanceSqToEntity(mob) <= r2) {
                out.add(e);
            }
        }
        return out;
    }

    /** «Командир · Штурмовик» for a chat line - the reader's client translates both parts (sc.elite.name). */
    public static IChatComponent eliteName(EntityArmoredMobSC mob) {
        EliteModSC m = mob.eliteMod();
        if (m == null) {
            return EmpMobSC.name(mob);
        }
        return new ChatComponentTranslation("sc.elite.name", new ChatComponentTranslation(m.langKey()), EmpMobSC.name(mob));
    }
}
