package com.sc.settle.npc;

import java.util.List;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.item.ArmorLogicSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobArmorSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.NpcKindSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.WorldServer;

/**
 * 2026-10-18 (rules ПГ-15..ПГ-20, docs/plan-substrate.md §25a): a dome warden's suit works as the player's of its tier as far as a
 * mob can use it - on top of MobArmorSC (the reserve, the shield, the ПЭ-1 suppression), only for EntityWardenSC (the Sentry, the
 * Senior Warden, the Captain, the Commandant; the caravans' guards are Sentries). The hostiles of the Substrate keep their old
 * balance: nothing here touches them.
 * <ul>
 * <li>ПГ-15, the reserve: the Sentry and the Senior Warden carry a charged player set of their suit - 4 pieces x the piece's EU / the
 * EU a stopped point costs (ItemArmorSC.cap / damageCost, the config's armorCapacity and armorDamageCost): Nano 800, Quantum 2000
 * units. A charged Exo / Singular set (4000 / 32000 units behind a shield that stops everything) would take a Quantum blade 3..27
 * minutes - the chiefs carry CAPTAIN_RESERVE / COMMANDANT_RESERVE instead. The share of a blockable blow the plating stops is the
 * player's: points / 25 (Nano 80%, Quantum 88%, Exo 96%, Singular 100%); the Quantum set pays QUANTUM_SET_ABSORB a unit (the
 * player's set bonus) while it is whole, cool and not suppressed.</li>
 * <li>ПГ-16: a warden's plating is not pierced - a blade's «Пробивание брони» is stopped as a blockable blow (a player's plating
 * isn't proof against it: the one deviation, without it a Quantum blade cuts a Sentry down in two blows). Absolute damage (the
 * Exo blade) passes the plating - only the Exo / Singular shield takes it, as the player's does; «Пробой» passes both (ПБ-16).
 * «Казнь» never finishes a warden unless it may finish players (weapons.bladeExecutePlayers).</li>
 * <li>ПГ-17: the chestplate's Regeneration - ArmorFeature.regenHeal HP a second (0.5 / 1 / 2 / 3) while hurt, for its
 * REGENERATION.euPerSecond and heat (not suppressed, not overheated, the reserve up); out of combat (REGEN_DELAY ticks without a
 * blow) the dome's induction gives REGEN_SHARE of the full reserve a second back; a charging plate PAD_SHARE a second.</li>
 * <li>ПГ-18, the heat: the suit's heatCapacity; the shield's heat a blow, Regeneration's a second, the ПЭ-1's as for a player
 * (EmpMathSC.heat: Point 15%, Pulse 35%, Siphon 3% a second, the Singular half); it sheds heatDissipation a second and, with
 * helium in the loop (Quantum and up), the pump's ArmorGasSC.HELIUM_PUMP more. At 100% it overheats (as the player's chips shut
 * down): no shield, no Regeneration, no set bonus, Slowness II and Weakness, until it is down to HEAT_RESUME_PCT. The dome keeps
 * the loop full: the helium never runs out, the ПЭ-1 doesn't boil it (not modelled on a mob).</li>
 * <li>ПГ-19, the chestplate's Shield (Quantum and up): arrows, fireballs, thrown things turned back within REFLECT_RANGE, for
 * ArmorFeature.SHIELD_COST EU each. The Commandant's Singular perks: «Аварийный выброс тепла» (Н11 - at 100% a wave throws the
 * hostiles within HEAT_VENT_RADIUS back and sets them on fire, half the heat goes, once a minute) and К9 (Wither taken off).</li>
 * <li>A suppressed chestplate (ПГ-20) loses all of it - the shield, Regeneration, the Shield function, the Singular perks; any
 * suppressed piece breaks the set bonus; a suppressed piece gives iron's points (MobArmorSC, as before).</li>
 * </ul>
 * The reference fight (the self-test prints it, the handbook too): a player's Quantum blade, REF_BLOW a blow, REF_HITS_PER_SECOND
 * blows a second landed (with the knockback and the warden's own blows) - fightSeconds. TODO(balance): every number here.
 */
public final class WardenSuitSC {

    /** NBT: the suit's version (a warden saved before ПГ-15 is refitted on loading, its reserve's share kept), heat, overheat. */
    public static final String NBT = "scWardenSuit";
    public static final int VERSION = 1;
    /** ПГ-15: the chiefs' reserve, damage units (a charged player's Exo set holds 4000, a Singular one 32000). TODO(balance) */
    public static final float CAPTAIN_RESERVE = 1600F, COMMANDANT_RESERVE = 3000F;
    /** ПГ-15: what a stopped unit costs a whole Quantum set (ArmorLogicSC.absorbCostMul's set bonus). */
    public static final float QUANTUM_SET_ABSORB = 0.7F;
    /** ПГ-17: out of combat - this long without a blow, then this share of the full reserve a second. TODO(balance) */
    public static final int REGEN_DELAY = 200;
    public static final float REGEN_SHARE = 0.01F;
    /** ПГ-3 / ПГ-17: a charging plate gives this share of the full reserve a second (at least EntityWardenSC.PAD_UNITS). TODO(balance) */
    public static final float PAD_SHARE = 0.05F;
    /** ПГ-18: an overheated suit works again at this % (CommonEventHandler's 50 for a player). */
    public static final int HEAT_RESUME_PCT = 50;
    /** ПГ-18: the overheat's effects, renewed every second (the player's: Slowness II, Weakness). */
    public static final int HOT_EFFECT_TICKS = 25;
    /** ПГ-19: the Shield function's reach (the player's 3 out of combat mode). */
    public static final double REFLECT_RANGE = 3D;
    /** The reference attacker: a Quantum blade's blow, landed this often. TODO(balance) */
    public static final float REF_BLOW = BladeType.QUANTUM.onDamage, REF_HITS_PER_SECOND = 1F;

    private final EntityWardenSC g;
    /** Server: heat units, overheated, world time Н11 is ready again, the last ПЭ-1 heat (the player's diminishing window). */
    public int heat;
    public boolean hot;
    public long ventReadyAt, lastEmpHeatAt;
    /** The version this warden's suit was fitted by (NBT; 0 - before ПГ-15). */
    public int version = VERSION;

    WardenSuitSC(EntityWardenSC g) {
        this.g = g;
    }

    // ------------------------------------------------------------------ the numbers (pure)

    /** ПГ-15: what a charged player's set of `s` stops before it is empty, damage units (config-scaled as ItemArmorSC). */
    public static float playerSetUnits(ArmorSuit s) {
        if (s == null) {
            return 0F;
        }
        double cap = ConfigSC.scale(s.maxCharge, ConfigSC.armorCapacity, 1);
        double cost = Math.max(1.0, s.euPerDamage * (double) ConfigSC.armorDamageCost);
        return (float) (4 * cap / cost);
    }

    /** ПГ-15: the rank's full reserve, damage units. */
    public static float reserveOf(NpcKindSC rank) {
        if (rank == NpcKindSC.CAPTAIN) {
            return CAPTAIN_RESERVE;
        }
        if (rank == NpcKindSC.COMMANDANT) {
            return COMMANDANT_RESERVE;
        }
        return playerSetUnits(EntityWardenSC.suitOf(rank));
    }

    /** The points of 4/4 of `s` (iron's 15 when `iron`: the whole set suppressed). */
    public static int setPoints(ArmorSuit s, boolean iron) {
        int p = 0;
        for (int t = 0; t < 4; t++) {
            int own = s == null ? 0 : s.material.getDamageReductionAmount(t);
            p += iron ? MobArmorMathSC.piecePoints(own, t, true) : own;
        }
        return p;
    }

    /** ПГ-17: HP a second the chestplate's Regeneration heals (the player's ArmorFeature.regenHeal). */
    public static float regenHeal(ArmorSuit s) {
        return ArmorFeature.regenHeal(s);
    }

    /** ПГ-17: what a second of Regeneration costs the reserve, units (REGENERATION.euPerSecond EU / the suit's EU a unit). */
    public static float regenUnits(ArmorSuit s) {
        return s == null ? 0F : ArmorFeature.REGENERATION.euPerSecond / (float) s.euPerDamage;
    }

    /** ПГ-18: heat a second the suit sheds - its own dissipation, and the helium pump's with helium in the loop. */
    public static int cooling(ArmorSuit s, boolean helium) {
        if (s == null) {
            return 0;
        }
        int pump = helium ? ArmorGasSC.HELIUM_PUMP[Math.min(ArmorGasSC.HELIUM_PUMP.length - 1, s.ordinal())] : 0;
        return s.heatDissipation + pump;
    }

    /** ПГ-18: overheated after a second at `heat` of `cap` (100% - yes; HEAT_RESUME_PCT or less - no; between - as it was). */
    public static boolean hotAfter(boolean hot, int heat, int cap) {
        int pct = cap <= 0 ? 0 : heat * 100 / cap;
        return pct >= 100 || hot && pct > HEAT_RESUME_PCT;
    }

    /** ПГ-3: units a charging plate gives every EntityWardenSC.PAD_EVERY ticks. */
    public static float padUnits(float reserveMax) {
        return Math.max(EntityWardenSC.PAD_UNITS, reserveMax * PAD_SHARE * EntityWardenSC.PAD_EVERY / 20F);
    }

    /**
     * The reference fight, pure (the self-test, the handbook): seconds until a warden of `rank` at full HP and reserve falls to
     * `blow` of armour-piercing damage `hitsPerSecond` times a second - the shield first (Exo / Singular), the plating by the
     * reserve, Regeneration every second while hurt. `pulsed`: the whole set suppressed all the while (iron's points, no
     * shield, no Regeneration, no set bonus). 3600 at most.
     */
    public static float fightSeconds(NpcKindSC rank, float blow, float hitsPerSecond, boolean pulsed) {
        ArmorSuit s = EntityWardenSC.suitOf(rank);
        float hp = (float) EntityWardenSC.hpOf(rank), max = hp, r = reserveOf(rank);
        boolean shield = ArmorSuit.exoClass(s) && !pulsed;
        int points = setPoints(s, pulsed);
        float cost = !pulsed && s == ArmorSuit.QUANTUM ? QUANTUM_SET_ABSORB : 1F;
        float heal = pulsed ? 0F : regenHeal(s), healCost = regenUnits(s);
        double dt = 1.0 / Math.max(0.01F, hitsPerSecond);
        int seconds = 1;
        for (int n = 0; n < 1000000; n++) {
            double t = n * dt;
            if (t > 3600) {
                break;
            }
            while (seconds <= t) {
                if (heal > 0F && hp < max && r > 0F) {
                    hp = Math.min(max, hp + heal);
                    r = Math.max(0F, r - healCost);
                }
                seconds++;
            }
            float d = blow;
            if (shield && r > 0F) {
                float[] sh = MobArmorMathSC.shield(d, r);
                r -= sh[1];
                d = sh[0];
            }
            if (d > 0F && r > 0F) {
                float[] ab = MobArmorMathSC.absorb(d, points, r / cost);
                r = Math.max(0F, r - ab[1] * cost);
                d = ab[0];
            }
            hp -= d;
            if (hp <= 0F) {
                return (float) t;
            }
        }
        return 3600F;
    }

    // ------------------------------------------------------------------ fitting (server)

    /**
     * Server: the suit fitted for `rank` - the plating proof against piercing, the reserve (full: as dressed; else the share it
     * had kept - a warden saved before ПГ-15), the out-of-combat induction. Helium, the Defense chip, HP are EntityWardenSC.equip's.
     */
    static void fit(MobArmorSC a, NpcKindSC rank, boolean full) {
        float max = reserveOf(rank);
        a.pierceProof = true;
        a.regenPerSecond = max * REGEN_SHARE;
        a.regenDelayTicks = REGEN_DELAY;
        if (full || a.reserveMax <= 0F) {
            a.spawnReserve(max, max);
            return;
        }
        float share = Math.max(0F, Math.min(1F, a.reserve / a.reserveMax));
        a.reserveMax = max;
        a.reserve = max * share;
        a.sync();
    }

    // ------------------------------------------------------------------ the work (server, EntityWardenSC.onLivingUpdate)

    /** Server, every tick: the flags MobArmorSC asks (overheat, set bonus); the Shield function every 2 ticks; the rest a second. */
    void serverTick() {
        MobArmorSC a = g.mobArmor();
        ArmorSuit s = a.suit();
        if (s == null || g.worldObj == null || g.worldObj.isRemote) {
            return;
        }
        long phase = g.worldObj.getTotalWorldTime() + g.getEntityId();
        a.overheated = hot;
        a.absorbCost = setBonus(a, s) ? QUANTUM_SET_ABSORB : 1F;
        if (phase % 2 == 0 && chestWorks(a, s) && s.atLeast(ArmorSuit.QUANTUM)) {
            reflect(a, s);
        }
        if (phase % 20 == 0) {
            second(a, s);
        }
    }

    /** The chestplate's functions work: not suppressed, not overheated, the reserve up. */
    private boolean chestWorks(MobArmorSC a, ArmorSuit s) {
        return !hot && !a.suppressed(1) && !a.down() && MobArmorSC.pieceSuit(g, 1) != null;
    }

    /** ПГ-15: the Quantum set bonus - all four Quantum, helium in the loop, not overheated, nothing suppressed (ArmorLogicSC.activeBonusSet). */
    private boolean setBonus(MobArmorSC a, ArmorSuit s) {
        if (s != ArmorSuit.QUANTUM || hot || !a.heliumOn() || a.anySuppressed()) {
            return false;
        }
        for (int t = 0; t < 4; t++) {
            if (MobArmorSC.pieceSuit(g, t) != ArmorSuit.QUANTUM) {
                return false;
            }
        }
        return true;
    }

    private void second(MobArmorSC a, ArmorSuit s) {
        int cap = s.heatCapacity;
        heat += a.shieldHits * ArmorFeature.SHIELD.heat;
        a.shieldHits = 0;
        // ПГ-17: Regeneration while hurt
        if (g.isEntityAlive() && g.getHealth() < g.getMaxHealth() && chestWorks(a, s)) {
            g.heal(regenHeal(s));
            a.spend(regenUnits(s));
            heat += ArmorFeature.REGENERATION.heat;
        }
        // К9: the Singular suit takes the Wither off
        if (s == ArmorSuit.SINGULAR && chestWorks(a, s) && g.isPotionActive(Potion.wither)) {
            g.removePotionEffect(Potion.wither.id);
        }
        heat = Math.max(0, Math.min(cap, heat - cooling(s, a.heliumOn())));
        if (heat >= cap && !hot && s == ArmorSuit.SINGULAR && !a.suppressed(1) && g.worldObj.getTotalWorldTime() >= ventReadyAt) {
            vent();
        }
        boolean was = hot;
        hot = hotAfter(hot, heat, cap);
        a.overheated = hot;
        if (hot) {
            g.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, HOT_EFFECT_TICKS, 1));
            g.addPotionEffect(new PotionEffect(Potion.weakness.id, HOT_EFFECT_TICKS, 0));
        }
        if (was != hot) {
            a.sync();                                           // the shield's bubble goes out / comes back
        }
    }

    /** ПГ-19, Н11: the wave - the hostiles within HEAT_VENT_RADIUS thrown back and set alight, half the heat out, a minute's cooldown. */
    private void vent() {
        double r = ArmorFeature.HEAT_VENT_RADIUS;
        List<?> list = g.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, g.boundingBox.expand(r, r, r));
        for (Object o : list) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == g || !(e instanceof IMob) || e instanceof ISettlementMemberSC || !e.isEntityAlive() || g.getDistanceSqToEntity(e) > r * r) {
                continue;
            }
            double dx = e.posX - g.posX, dz = e.posZ - g.posZ, d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            e.motionX += dx / d * 1.2;
            e.motionZ += dz / d * 1.2;
            e.motionY = Math.max(e.motionY, 0.45);
            e.velocityChanged = true;
            e.setFire(3);
        }
        heat = ArmorLogicSC.ventedHeat(heat);
        ventReadyAt = g.worldObj.getTotalWorldTime() + ArmorFeature.HEAT_VENT_COOLDOWN;
        g.worldObj.playSoundEffect(g.posX, g.posY, g.posZ, "random.fizz", 1.0F, 0.5F);
        if (g.worldObj instanceof WorldServer) {
            ((WorldServer) g.worldObj).func_147487_a("flame", g.posX, g.posY + 1, g.posZ, 40, 1.5, 0.6, 1.5, 0.15);
            ((WorldServer) g.worldObj).func_147487_a("cloud", g.posX, g.posY + 1, g.posZ, 20, 1.0, 0.6, 1.0, 0.1);
        }
    }

    /** ПГ-19: projectiles coming at the warden turned back (as ArmorLogicSC's for a player), SHIELD_COST EU each off the reserve. */
    private void reflect(MobArmorSC a, ArmorSuit s) {
        double r = REFLECT_RANGE;
        List<?> list = g.worldObj.getEntitiesWithinAABBExcludingEntity(g, g.boundingBox.expand(r, r, r));
        for (Object o : list) {
            Entity e = (Entity) o;
            if (!incoming(e) || a.down()) {
                continue;
            }
            a.spend(ArmorFeature.SHIELD_COST / (float) s.euPerDamage);
            heat += ArmorFeature.SHIELD.heat;
            e.motionX = -e.motionX;
            e.motionY = -e.motionY;
            e.motionZ = -e.motionZ;
            if (e instanceof EntityFireball) {
                EntityFireball f = (EntityFireball) e;
                f.accelerationX = -f.accelerationX;
                f.accelerationY = -f.accelerationY;
                f.accelerationZ = -f.accelerationZ;
            }
            e.getEntityData().setBoolean("scDeflected", true);  // the player's mark: turned back once
            e.velocityChanged = true;
            SoundsSC.play(g.worldObj, e.posX, e.posY, e.posZ, "random.fizz", 0.5F, 1.8F);
        }
    }

    /** An arrow / fireball / thrown thing on its way to the warden, not its own, not turned back already. */
    private boolean incoming(Entity e) {
        boolean projectile = e instanceof EntityArrow || e instanceof EntityFireball || e instanceof EntityThrowable;
        if (!projectile || e.isDead || e.getEntityData().getBoolean("scDeflected")) {
            return false;
        }
        Entity owner = e instanceof EntityArrow ? ((EntityArrow) e).shootingEntity : e instanceof EntityFireball ? ((EntityFireball) e).shootingEntity
                : ((EntityThrowable) e).getThrower();
        if (owner == g) {
            return false;
        }
        double tx = g.posX - e.posX, ty = g.posY + 1 - e.posY, tz = g.posZ - e.posZ;
        return tx * e.motionX + ty * e.motionY + tz * e.motionZ > 0;
    }

    /**
     * ПГ-18, server: a ПЭ-1 hit (a Point / a Pulse that reached the armour) or a Siphon stream's second heats the suit as a
     * player's (EmpMathSC.heat by the chestplate's suit: repeats within DIMINISH_WINDOW half, immune pieces half, the Singular
     * half, the Defense chip). @return the heat added
     */
    int empHeat(EmpMode mode, boolean allImmune) {
        ArmorSuit cs = MobArmorSC.pieceSuit(g, 1);
        if (cs == null || mode == null || g.worldObj == null) {
            return 0;
        }
        long now = g.worldObj.getTotalWorldTime();
        boolean repeat = mode != EmpMode.SIPHON && lastEmpHeatAt > 0 && now - lastEmpHeatAt < EmpMathSC.DIMINISH_WINDOW;
        int h = EmpMathSC.heat(cs.heatCapacity, mode, repeat, allImmune, cs == ArmorSuit.SINGULAR, g.mobArmor().defenseTier, ConfigSC.empHeatMultiplier);
        heat = Math.min(cs.heatCapacity, heat + h);
        if (mode != EmpMode.SIPHON) {
            lastEmpHeatAt = now;
        }
        return h;
    }

    // ------------------------------------------------------------------ NBT

    void write(NBTTagCompound tag) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("v", version);
        t.setInteger("heat", heat);
        t.setBoolean("hot", hot);
        tag.setTag(NBT, t);
    }

    /** @return the version it was saved by (0: before ПГ-15 - the caller refits it). */
    int read(NBTTagCompound tag) {
        NBTTagCompound t = tag.getCompoundTag(NBT);
        heat = Math.max(0, t.getInteger("heat"));
        hot = t.getBoolean("hot");
        return tag.hasKey(NBT) ? t.getInteger("v") : 0;
    }
}
