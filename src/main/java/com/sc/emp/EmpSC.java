package com.sc.emp;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;
import com.sc.util.ConfigSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * ПЭ-1 «Подавитель электроники» - the shared core (docs/plan-pe1.md, docs/plan-pe1-tech.md): the per-player
 * suppression state (EmpStateSC) on both sides and the public hooks the rest of the mod asks.
 *
 * Hooks (both sides unless noted; a client answers from the last StateMessage, comparing with its world time):
 * isSuppressed / suppressedTicks / immuneTicks per armour slot (0 helmet .. 3 boots), anySuppressed,
 * boilMultiplier / boilTicks, drained / drainedBy, aimedAt / aimedBy, rebootCooldownTicks;
 * server only: boilExtraMbPerSecond, applyHit, applyNoArmour, streamSecond, markAimed, clearSuppression, sync.
 *
 * Everything answers "nothing" while ConfigSC.empEnabled is off.
 */
public final class EmpSC {

    /** The player's entity data key the server keeps the state under. */
    public static final String NBT = "scEmp";
    /** Sound names (sounds.json, package D): "siliconage:" + these. */
    public static final String SND_CHARGE = "emp.charge", SND_PULSE_HUM = "emp.hum", SND_SHOT = "emp.shot", SND_PULSE = "emp.pulse",
            SND_SIPHON = "emp.siphon", SND_OVERHEAT = "emp.overheat", SND_SUPPRESS = "emp.suppress", SND_REBOOT = "emp.reboot";
    /** Radius the state and the effects are sent in. */
    public static final double SYNC_RANGE = 64;

    private static final Map<EntityPlayer, EmpStateSC> SERVER = new WeakHashMap<EntityPlayer, EmpStateSC>();
    /** Client copies by entity id (written by the network thread: whole objects replaced). */
    private static final Map<Integer, EmpStateSC> CLIENT = new ConcurrentHashMap<Integer, EmpStateSC>();

    private EmpSC() {
    }

    // ------------------------------------------------------------------ state access

    /** The state of a player on its side (server: from the entity data, cached; client: the synced copy, or an empty one). Never null. */
    public static EmpStateSC state(EntityPlayer p) {
        if (p.worldObj != null && p.worldObj.isRemote) {
            EmpStateSC s = CLIENT.get(p.getEntityId());
            return s != null ? s : new EmpStateSC();
        }
        synchronized (SERVER) {
            EmpStateSC s = SERVER.get(p);
            if (s == null) {
                s = EmpStateSC.read(p.getEntityData().hasKey(NBT) ? p.getEntityData().getCompoundTag(NBT) : null);
                SERVER.put(p, s);
            }
            return s;
        }
    }

    /** Server: writes the state back into the entity data (an idle one is removed). */
    public static void save(EntityPlayer p, EmpStateSC s) {
        if (s.idle(now(p))) {
            p.getEntityData().removeTag(NBT);
        } else {
            p.getEntityData().setTag(NBT, s.write());
        }
    }

    public static long now(Entity e) {
        return e.worldObj == null ? 0L : e.worldObj.getTotalWorldTime();
    }

    /** Client (network thread): the synced state of entity `id`. */
    public static void clientSet(int id, EmpStateSC s) {
        if (s == null) {
            CLIENT.remove(id);
        } else {
            CLIENT.put(id, s);
        }
    }

    /** Client: the synced state of an entity id, or null. */
    public static EmpStateSC clientState(int id) {
        return CLIENT.get(id);
    }

    /** Client: leaving a world. */
    public static void clientClear() {
        CLIENT.clear();
    }

    /** Server stopped. */
    public static void clearAll() {
        synchronized (SERVER) {
            SERVER.clear();
        }
    }

    // ------------------------------------------------------------------ hooks: what the armour, the HUD and the K menu ask

    /** The piece in slot `piece` (0 helmet .. 3 boots) is suppressed: its functions (but breathing) off, iron-grade plating, chips off (chestplate). */
    public static boolean isSuppressed(EntityPlayer p, int piece) {
        return p != null && ConfigSC.empEnabled && state(p).suppressed(piece, now(p));
    }

    public static boolean anySuppressed(EntityPlayer p) {
        return p != null && ConfigSC.empEnabled && state(p).anySuppressed(now(p));
    }

    /** Ticks of suppression left on the slot, 0 none. */
    public static int suppressedTicks(EntityPlayer p, int piece) {
        if (!isSuppressed(p, piece)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, state(p).until[piece] - now(p));
    }

    /** Ticks of immunity left on the slot (only after its suppression ended), 0 none. */
    public static int immuneTicks(EntityPlayer p, int piece) {
        if (p == null || !ConfigSC.empEnabled) {
            return 0;
        }
        EmpStateSC s = state(p);
        long now = now(p);
        return s.immune(piece, now) ? (int) Math.min(Integer.MAX_VALUE, s.immuneUntil[piece] - now) : 0;
    }

    /** The ПЭ-1 helium boil multiplier now (1 = none; 2 / 3) - the HUD's «×3 расход». */
    public static int boilMultiplier(EntityPlayer p) {
        if (p == null || !ConfigSC.empEnabled) {
            return 1;
        }
        EmpStateSC s = state(p);
        long now = now(p);
        return s.streaming(now) ? Math.max(EmpMathSC.BOIL_MUL_SIPHON, s.boilMulAt(now)) : s.boilMulAt(now);
    }

    /** Ticks the boil goes on (the stream: at least its show time), 0 none. */
    public static int boilTicks(EntityPlayer p) {
        if (boilMultiplier(p) <= 1) {
            return 0;
        }
        EmpStateSC s = state(p);
        return (int) Math.max(0, Math.max(s.boilUntil, s.streamUntil) - now(p));
    }

    /**
     * Server, once a second (package B, CommonEventHandler.tickArmorHeat): the extra helium mB the ПЭ-1 boils
     * out of a loop of `loopCapacityMb` this second (the Recuperator still applies - pass it through
     * ArmorGasSC's helium use). 0 without a boil.
     */
    public static float boilExtraMbPerSecond(EntityPlayer p, int loopCapacityMb) {
        if (p == null || !ConfigSC.empEnabled || loopCapacityMb <= 0) {
            return 0F;
        }
        EmpStateSC s = state(p);
        long now = now(p);
        boolean stream = s.streaming(now);
        int mul = s.boilMulAt(now);
        if (!stream && mul <= 1) {
            return 0F;
        }
        return EmpMathSC.boilMbPerSecond(loopCapacityMb, mul, stream, s.anyImmune(now), ConfigSC.empHeliumBoilMultiplier);
    }

    /** A siphon stream drains this player now («Энергия утекает!», dimmed lights). */
    public static boolean drained(EntityPlayer p) {
        return p != null && ConfigSC.empEnabled && state(p).streaming(now(p));
    }

    /** Entity id of the drainer (valid while drained). */
    public static int drainedBy(EntityPlayer p) {
        return state(p).drainBy;
    }

    /** «ЭМИ-наведение»: a ПЭ-1 aims at this player (shown only with a Sensor chip - the HUD checks that). */
    public static boolean aimedAt(EntityPlayer p) {
        return p != null && ConfigSC.empEnabled && state(p).aimedUntil > now(p);
    }

    public static int aimedBy(EntityPlayer p) {
        return state(p).aimedBy;
    }

    /** Ticks until the emergency reboot is ready, 0 ready. */
    public static int rebootCooldownTicks(EntityPlayer p) {
        long left = state(p).rebootReadyAt - now(p);
        return left > 0 ? (int) Math.min(Integer.MAX_VALUE, left) : 0;
    }

    // ------------------------------------------------------------------ target facts (both sides, from the worn pieces)

    /** The mod's suit piece in slot `piece`, or null. */
    public static ItemStack modPiece(EntityPlayer p, int piece) {
        ItemStack s = ArmorLogicSC.piece(p, piece);
        return s != null && s.getItem() instanceof ItemArmorSC ? s : null;
    }

    public static boolean wearsModArmour(EntityPlayer p) {
        for (int i = 0; i < 4; i++) {
            if (modPiece(p, i) != null) {
                return true;
            }
        }
        return false;
    }

    /** The Defense chip's tier (0..3) in the worn chestplate. */
    public static int defenseTier(EntityPlayer p) {
        return ArmorGasSC.chipTier(modPiece(p, 1), ChipType.DEFENSE);
    }

    /**
     * A Sensor chip in the worn chestplate (§7/§8 say "в шлеме", but this mod's chips all live in the chestplate):
     * the shooter sees the target's charge; the target gets «ЭМИ-наведение».
     */
    public static boolean hasSensorChip(EntityPlayer p) {
        return ArmorGasSC.chipTier(modPiece(p, 1), ChipType.SENSOR) > 0;
    }

    /** Helium in the loop (ArmorLogicSC.heliumReady): suppression -20%. */
    public static boolean heliumInLoop(EntityPlayer p) {
        return ArmorLogicSC.heliumReady(ArmorGasSC.wornSet(p));
    }

    // ------------------------------------------------------------------ server: applying hits

    /** What applyHit did. */
    public static final class HitResult {
        /** Suppression ticks given per slot (0: none - no mod piece, immune, or still suppressed). */
        public final int[] ticks = new int[4];
        /** Slots that were immune. */
        public final boolean[] immune = new boolean[4];
        /** ОР-1 (2026-10-21): ticks still left on slots that were suppressed already - the hit didn't extend them (0: not so). */
        public final int[] already = new int[4];
        /** Some aimed slot held a mod piece. */
        public boolean anyArmour;
        /** Heat units given. */
        public int heat;
        /** Ф3, ПБ-19: a mob's personal EMP screen took the shot (nothing else happened; the screen is down for 30 s now). */
        public boolean screened;

        public boolean anySuppressed() {
            for (int t : ticks) {
                if (t > 0) {
                    return true;
                }
            }
            return false;
        }

        /** ОР-1: some aimed slot was still suppressed (left as it was). */
        public boolean anyAlready() {
            for (int t : already) {
                if (t > 0) {
                    return true;
                }
            }
            return false;
        }

        /** ОР-1: the most ticks left on a still suppressed slot, 0 none. */
        public int maxAlready() {
            int m = 0;
            for (int t : already) {
                m = Math.max(m, t);
            }
            return m;
        }
    }

    /**
     * Server: a Point / Pulse that got through (rules, EMP screen and the Guardian's reflection are the caller's -
     * package A, see docs/plan-pe1-tech.md): suppresses the mod pieces in `pieces` (immune ones only heat half),
     * heats the suit, starts the helium boil, syncs. A target with no mod piece in those slots: Slowness
     * (applyNoArmour). Siphon: use streamSecond.
     * ОР-1 (2026-10-21): a piece still suppressed is not extended - its end and its immunity stay those of the hit that
     * suppressed it (HitResult.already says how long is left); the heat and the boil still come, at the full (not immune) rate.
     */
    public static HitResult applyHit(EntityPlayer target, EmpMode mode, boolean upgraded, int[] pieces) {
        HitResult r = new HitResult();
        if (target == null || target.worldObj.isRemote || mode == EmpMode.SIPHON || !ConfigSC.empEnabled) {
            return r;
        }
        long now = now(target);
        EmpStateSC s = state(target);
        int defense = defenseTier(target);
        boolean helium = heliumInLoop(target);
        boolean allImmune = true;
        for (int slot : pieces) {
            ItemStack piece = modPiece(target, slot);
            if (piece == null) {
                continue;
            }
            r.anyArmour = true;
            if (s.immune(slot, now)) {
                r.immune[slot] = true;
                continue;
            }
            allImmune = false;
            if (s.suppressed(slot, now)) {
                r.already[slot] = (int) Math.min(Integer.MAX_VALUE, s.until[slot] - now);   // ОР-1: until / immuneUntil untouched
                continue;
            }
            ArmorSuit suit = ((ItemArmorSC) piece.getItem()).getSuit();
            int t = EmpMathSC.durationTicks(suit, mode, upgraded, defense, helium, ConfigSC.empDurationMultiplier);
            long until = Math.max(s.until[slot], now + t);
            s.until[slot] = until;
            s.immuneUntil[slot] = until + EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
            r.ticks[slot] = (int) (until - now);
        }
        if (!r.anyArmour) {
            applyNoArmour(target);
            return r;
        }
        ItemStack chest = modPiece(target, 1);
        if (chest != null) {
            ArmorSuit cs = ((ItemArmorSC) chest.getItem()).getSuit();
            boolean repeat = s.lastHeatHit > 0 && now - s.lastHeatHit < EmpMathSC.DIMINISH_WINDOW;
            r.heat = EmpMathSC.heat(cs.heatCapacity, mode, repeat, allImmune, cs == ArmorSuit.SINGULAR, defense, ConfigSC.empHeatMultiplier);
            if (r.heat > 0) {
                ArmorLogicSC.addHeat(target, r.heat);
            }
            s.lastHeatHit = now;
            long[] b = EmpMathSC.mergeBoil(s.boilMulAt(now), s.boilUntil, EmpMathSC.boilMul(mode), now + EmpMathSC.boilTicks(mode), now);
            s.boilMul = (int) b[0];
            s.boilUntil = b[1];
        }
        EmpArmorSC.notifySuppressed(target, r);                  // «Ваша броня подавлена ЭМИ» to the target
        save(target, s);
        sync(target);
        return r;
    }

    /**
     * Ф4, ПФ-2 (docs/plan-substrate-tech-f4.md §5.3, the coordinator's; only a new helper - the ПЭ-1's paths are untouched): an
     * EMP storm's pulse on the worn piece `slot` of `target` - suppressed for `ticks` (the storm's own 3..4 s), the usual
     * immunity after it, the helium loop boiling `boilMul` times as fast for `boilTicks` («гелий подкипает»); no heat, no
     * Slowness. An immune piece, none of the mod's in that slot, ПЭ-1 off in the config: nothing. ОР-1 (2026-10-21): a piece
     * still suppressed - nothing either (not extended, its immunity keeps its time). Syncs, and tells the target as a ПЭ-1 hit
     * does («Ваша броня подавлена ЭМИ»). @return the ticks given (0: nothing happened)
     */
    public static int stormHit(EntityPlayer target, int slot, int ticks, int boilMul, int boilTicks) {
        if (target == null || target.worldObj.isRemote || slot < 0 || slot > 3 || ticks <= 0 || !ConfigSC.empEnabled) {
            return 0;
        }
        ItemStack piece = modPiece(target, slot);
        long now = now(target);
        EmpStateSC s = state(target);
        if (piece == null || s.immune(slot, now) || s.suppressed(slot, now)) {
            return 0;
        }
        long until = now + ticks;
        s.until[slot] = until;
        s.immuneUntil[slot] = until + EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
        if (boilMul > 1 && boilTicks > 0 && modPiece(target, 1) != null) {
            long[] b = EmpMathSC.mergeBoil(s.boilMulAt(now), s.boilUntil, boilMul, now + boilTicks, now);
            s.boilMul = (int) b[0];
            s.boilUntil = b[1];
        }
        HitResult r = new HitResult();
        r.anyArmour = true;
        r.ticks[slot] = (int) (until - now);
        EmpArmorSC.notifySuppressed(target, r);
        save(target, s);
        sync(target);
        return r.ticks[slot];
    }

    /** Server: a target without the mod's armour in the hit zone (players and mobs): Slowness 2 s. */
    public static void applyNoArmour(EntityLivingBase target) {
        if (target != null && !target.worldObj.isRemote) {
            target.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, EmpMathSC.NO_ARMOR_SLOW_TICKS, 0));
        }
    }

    /**
     * Server, once a second of a running siphon stream on `target` (package A): the stream's heat (3%/s, no
     * diminishing), the stream boil (1% of the loop a second, then x2 for 10 s), "Энергия утекает!" for
     * DRAIN_SHOW_TICKS more, the drainer's id; syncs. @return heat given
     */
    public static int streamSecond(EntityPlayer target, Entity shooter) {
        if (target == null || target.worldObj.isRemote || !ConfigSC.empEnabled) {
            return 0;
        }
        long now = now(target);
        EmpStateSC s = state(target);
        s.streamUntil = now + Math.max(EmpMathSC.DRAIN_SHOW_TICKS, 25);
        s.drainBy = shooter == null ? 0 : shooter.getEntityId();
        int heat = 0;
        ItemStack chest = modPiece(target, 1);
        if (chest != null) {
            ArmorSuit cs = ((ItemArmorSC) chest.getItem()).getSuit();
            heat = EmpMathSC.heat(cs.heatCapacity, EmpMode.SIPHON, false, s.anyImmune(now), cs == ArmorSuit.SINGULAR, defenseTier(target),
                    ConfigSC.empHeatMultiplier);
            if (heat > 0) {
                ArmorLogicSC.addHeat(target, heat);
            }
            long[] b = EmpMathSC.mergeBoil(s.boilMulAt(now), s.boilUntil, EmpMathSC.BOIL_MUL_SIPHON,
                    s.streamUntil + EmpMathSC.BOIL_TICKS_SIPHON_AFTER, now);
            s.boilMul = (int) b[0];
            s.boilUntil = b[1];
        }
        save(target, s);
        sync(target);                                            // once a second: the new ends to the target and the watchers
        return heat;
    }

    /** Server (package A's aim scan, every AIM_SCAN_TICKS): `shooter` holds a ПЭ-1 on `target` - «ЭМИ-наведение» for AIMED_SHOW_TICKS; sent to the target only. */
    public static void markAimed(EntityPlayer target, Entity shooter) {
        if (target == null || target.worldObj.isRemote || !ConfigSC.empEnabled) {
            return;
        }
        EmpStateSC s = state(target);
        s.aimedUntil = now(target) + EmpMathSC.AIMED_SHOW_TICKS;
        s.aimedBy = shooter.getEntityId();
        save(target, s);
        syncTo(target, target);
    }

    /**
     * Server: the emergency reboot's effect (package B pays for it and checks the cooldown): every suppressed
     * slot ends now - its immunity starts now; the cooldown is set. @return whether anything was suppressed
     */
    public static boolean clearSuppression(EntityPlayer target) {
        long now = now(target);
        EmpStateSC s = state(target);
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            if (s.until[i] > now) {
                s.until[i] = now;
                s.immuneUntil[i] = now + EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
                any = true;
            }
        }
        s.rebootReadyAt = now + EmpMathSC.REBOOT_COOLDOWN;
        save(target, s);
        sync(target);
        return any;
    }

    // ------------------------------------------------------------------ sync

    /** Server: the player's state to everyone within SYNC_RANGE (the target too: it is at the centre). */
    public static void sync(EntityPlayer p) {
        if (p == null || p.worldObj.isRemote) {
            return;
        }
        EmpNetSC.channel().sendToAllAround(new EmpNetSC.StateMessage(p.getEntityId(), state(p)),
                new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, SYNC_RANGE));
    }

    /** Server: the state of `about` to one player (login, start tracking, aim marks). */
    public static void syncTo(EntityPlayer about, EntityPlayer to) {
        if (about != null && to instanceof EntityPlayerMP && !(to instanceof net.minecraftforge.common.util.FakePlayer) && !about.worldObj.isRemote) {
            EmpNetSC.channel().sendTo(new EmpNetSC.StateMessage(about.getEntityId(), state(about)), (EntityPlayerMP) to);
        }
    }
}
