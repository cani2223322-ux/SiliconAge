package com.sc.mob;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.item.ItemArmorSC;
import com.sc.radiation.RadiationSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE A (docs/plan-substrate-tech.md §4): the armour of one Substrate mob - the reserve in damage units, the
 * shield, the ПЭ-1 suppression - kept in the entity and its DataWatcher, never in the worn stacks (they are only the
 * look). The player's armour code (ItemArmorSC, ArmorLogicSC) is never asked about a mob.
 * <p>
 * The model (rules ПБ-1..ПБ-7): while the reserve is above 0 the armour stops min(1, points / 25) of a blockable
 * blow and the stopped part comes off the reserve (Nano 20 points = 80%, Quantum 22 = 88%, Exo 24 = 96%, Singular
 * 28 = 100%; a suppressed piece and the emergency mode give iron points); an Exo / Singular mob with helium has a
 * shield that takes any blow first - unblockable and absolute too - off the reserve, the overflow goes on; reserve
 * 0: the armour is «down» (no protection, the lights out). The scale is the player's: ArmorSuit.euPerDamage EU a
 * unit (Nano 500 .. Singular 8000) - what the siphon converts by.
 * <p>
 * Signatures are fixed for packages M (spawnReserve, recharge, down, the client readers) and A's EMP code
 * (empHit, drainEu, streamSecond); the bodies are A's.
 * <p>
 * Server state only changes here, and every change ends in sync() (the DataWatcher sends only what changed; it is
 * refreshed every SYNC_EVERY ticks besides, for the ends of suppression / immunity / a stream that nothing touches).
 * Going «down» (the reserve crossing 0 by a blow, the shield or the siphon - not a mob that never had one) plays
 * power.off and a burst of sparks once; recharging above 0 lights it up again.
 */
public final class MobArmorSC {

    /**
     * DataWatcher slots. EntityLiving uses 0..11 (zombies and skeletons 12..14, not in our tree); an EntityMob subclass
     * has 12..31 free: 20..23 are these, 24..31 belong to the mob classes (package M).
     */
    public static final int DW_RESERVE = 20, DW_RESERVE_MAX = 21, DW_FLAGS = 22, DW_EMP = 23;
    /** DW_FLAGS bits. Ф3 (docs/plan-substrate-tech-f3.md §2.4): FLAG_SCREEN - the personal EMP screen is up (the Jammer, a «Экранированный» elite). */
    public static final int FLAG_SHIELD = 1, FLAG_EMERGENCY = 2, FLAG_DRAINED = 4, FLAG_ELITE = 8, FLAG_DOWN = 16, FLAG_SCREEN = 32;
    /** DW_EMP: bits 0..3 suppressed pieces (by armorType), 4..7 immune ones. */
    public static final int EMP_IMMUNE_SHIFT = 4;
    /** The reserve travels to clients in tenths of a unit (DW_RESERVE / DW_RESERVE_MAX are ints). */
    public static final int WATCH_SCALE = 10;
    /** The tag inside the entity's NBT. */
    public static final String NBT = "scMobArmor";
    /** The «down» sound (sounds.json, the machines' power switch) and its sparks. */
    private static final String SND_DOWN = "power.off";
    private static final int DOWN_SPARKS = 14;

    private final EntityLiving mob;

    /** Reserve now / at full, damage units (server). */
    public float reserve, reserveMax;
    /** Helium in the loop: Exo / Singular get the shield, suppression -20%. */
    public boolean helium;
    /** The emergency mode (no helium where the suit needs it): iron points, no shield - the «lower» version of a mob. */
    public boolean emergency;
    public boolean elite;
    /** Its own recharge: units a second, after this many ticks without damage (0 = none; Lineman / Marauder: at a Copper Trace only). */
    public float regenPerSecond;
    public int regenDelayTicks;
    /** The Defense chip equivalent, 0..3. */
    public int defenseTier;
    /** World time each piece's suppression / immunity ends (armorType 0..3). */
    public final long[] suppressedUntil = new long[4], immuneUntil = new long[4];
    /** World time a siphon stream last drew (its recharge waits), and the last damage taken. */
    public long streamUntil, lastDamageAt;
    /** EU the current siphon stream has taken so far (capped per stream, ПБ-12). */
    public long streamDrawnEu;
    /**
     * Ф3, ПБ-19: a personal EMP screen (the Jammer's, a «Экранированный» elite's) - it takes the first ПЭ-1 shot and is down
     * for EliteModSC.SCREEN_RECOVER_TICKS after (until then the next shot gets through). Saved: whether it has one; the
     * downtime is transient (a reloaded mob has its screen up).
     */
    public boolean screen;
    public long screenDownUntil;
    /** Ф3, ПС-21: a Commander's order already raised its reserve's maximum (once in its life - saved, "cmd"). */
    public boolean commanded;
    /**
     * Ф3, ПС-21 (EliteSC, server, not saved): world time a Commander's order stops raising this mob's blows (damageMul), and -
     * on a Commander itself - when its current order was given (0: none under way; the group gets the bonus ORDER_TICKS after).
     */
    public long commandUntil, orderAt;
    /** The «down» effects were played for this reserve-0 (not again until it is recharged above 0). Not saved. */
    private boolean downShown;

    public MobArmorSC(EntityLiving mob) {
        this.mob = mob;
    }

    /** Called from the mob's entityInit (inside the Entity constructor, before any field of the mob exists). */
    public static void addWatchers(DataWatcher dw) {
        dw.addObject(DW_RESERVE, Integer.valueOf(0));
        dw.addObject(DW_RESERVE_MAX, Integer.valueOf(0));
        dw.addObject(DW_FLAGS, Byte.valueOf((byte) 0));
        dw.addObject(DW_EMP, Byte.valueOf((byte) 0));
    }

    private long now() {
        return mob.worldObj == null ? 0L : mob.worldObj.getTotalWorldTime();
    }

    private boolean server() {
        return mob.worldObj != null && !mob.worldObj.isRemote;
    }

    // ------------------------------------------------------------------ the worn pieces

    /** The stack worn in armour slot `armorType` (0 helmet .. 3 boots), or null. */
    public static ItemStack piece(EntityLivingBase mob, int armorType) {
        return armorType < 0 || armorType > 3 ? null : mob.getEquipmentInSlot(4 - armorType);
    }

    /** The mod's suit piece in that slot, or null. */
    public static ItemStack modPiece(EntityLivingBase mob, int armorType) {
        ItemStack s = piece(mob, armorType);
        return s != null && s.getItem() instanceof ItemArmorSC ? s : null;
    }

    /** The suit of the mod's piece in that slot, or null. */
    public static ArmorSuit pieceSuit(EntityLivingBase mob, int armorType) {
        ItemStack s = modPiece(mob, armorType);
        return s == null ? null : ((ItemArmorSC) s.getItem()).getSuit();
    }

    /** The suit the mob «is»: its chestplate's, else the best piece's; null without the mod's armour. */
    public ArmorSuit suit() {
        ArmorSuit chest = pieceSuit(mob, 1);
        if (chest != null) {
            return chest;
        }
        ArmorSuit best = null;
        for (int i = 0; i < 4; i++) {
            ArmorSuit s = pieceSuit(mob, i);
            if (s != null && (best == null || s.ordinal() > best.ordinal())) {
                best = s;
            }
        }
        return best;
    }

    /** Helium really in the loop: not in the emergency mode (which is «no helium where the suit needs it»). */
    public boolean heliumOn() {
        return helium && !emergency;
    }

    // ------------------------------------------------------------------ spawning, recharge (package M calls these)

    /** Server, on spawning (M): a reserve between min and max units, full; the look synced. */
    public void spawnReserve(float min, float max) {
        reserveMax = min + mob.getRNG().nextFloat() * Math.max(0F, max - min);
        reserve = reserveMax;
        downShown = false;
        sync();
    }

    /**
     * Server, on spawning (M), the pieces worn already: a reserve that blockable blows of minDamage..maxDamage in all
     * run dry, whatever the mob wears (MobArmorMathSC.reserveFor) - ПБ-5's «few pieces, a small reserve».
     */
    public void spawnReserveForDamage(float minDamage, float maxDamage) {
        int p = wornPoints();
        spawnReserve(MobArmorMathSC.reserveFor(minDamage, p), MobArmorMathSC.reserveFor(maxDamage, p));
    }

    /** Server: `units` back into the reserve (a Copper Trace, a charge pad, a repair drone); not while a siphon draws. */
    public void recharge(float units) {
        if (units <= 0F || reserveMax <= 0F || !server() || streamUntil > now()) {
            return;                                     // ПБ-11: while a stream draws, the reserve doesn't come back
        }
        reserve = Math.min(reserveMax, reserve + units);
        if (reserve > 0F) {
            downShown = false;                          // «села» goes out: the lights are back
        }
        sync();
    }

    /** The reserve is gone: no protection, the lights out. */
    public boolean down() {
        return reserve <= 0F;
    }

    /** Server: `units` off the reserve (a blow, the shield, the siphon); crossing 0 the suit goes «down» - once. */
    private void take(float units) {
        if (units <= 0F) {
            return;
        }
        boolean was = reserve > 0F;
        reserve = Math.max(0F, reserve - units);
        if (reserve < MobArmorMathSC.RESERVE_EPSILON) {
            reserve = 0F;
        }
        if (was && down()) {
            wentDown();
        }
        sync();
    }

    /** ПБ-6: power.off and sparks at the mob, once per reserve-0 (FLAG_DOWN goes with the next sync). */
    private void wentDown() {
        if (downShown) {
            return;
        }
        downShown = true;
        World w = mob.worldObj;
        double y = mob.posY + mob.height * 0.6;
        if (w instanceof WorldServer) {
            ((WorldServer) w).func_147487_a("fireworksSpark", mob.posX, y, mob.posZ, DOWN_SPARKS, 0.25, 0.35, 0.25, 0.06);
        }
        SoundsSC.play(w, mob.posX, y, mob.posZ, SND_DOWN, 0.8F, 0.9F + mob.getRNG().nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------ damage (EntityArmoredMobSC.damageEntity calls these)

    /** ПБ-4: the shield is up now - an Exo / Singular chestplate, helium, the chestplate not suppressed, a reserve. */
    public boolean shieldUp() {
        return MobArmorMathSC.shieldUp(pieceSuit(mob, 1), heliumOn(), suppressed(1), reserve);
    }

    /** Radiation is the radiation shield's job, not the energy shield's (as ArmorLogicSC.exoStops for a player). */
    private static boolean radiation(DamageSource src) {
        return src == RadiationSC.DAMAGE || RadiationSC.DAMAGE.damageType.equals(src.damageType);
    }

    /**
     * Server: the shield first (Exo / Singular, helium, the chestplate not suppressed, the reserve above 0): it takes
     * any blow - unblockable and absolute too, not outOfWorld / radiation - off the reserve. @return the damage that
     * goes on (the overflow past the reserve; all of it without a shield)
     */
    public float shield(DamageSource src, float amount) {
        if (amount <= 0F || src == null) {
            return amount;
        }
        lastDamageAt = now();                           // every blow that lands comes here first: the regen delay starts
        if (src == DamageSource.outOfWorld || radiation(src) || BreakdownDamageSC.is(src) || !shieldUp()) {
            return amount;                              // Ф3, ПБ-16: «Пробой» passes the shield, as the player's exoStops lets it
        }
        float[] r = MobArmorMathSC.shield(amount, reserve);
        take(r[1]);
        MobEventsSC.shieldTook(mob, src, r[1]);         // Ф3: a blow the shield took is the fight too (the trophy's player, ПТ-2)
        return r[0];
    }

    /**
     * Server, vanilla's applyArmorCalculations for the mob: a blockable blow loses min(1, points / 25) of itself while
     * the reserve lasts, that part off the reserve. @return the damage to the health
     */
    public float applyArmor(DamageSource src, float amount) {
        if (amount <= 0F || src == null) {
            return amount;
        }
        lastDamageAt = now();
        if (src.isUnblockable() || down()) {
            return amount;                              // ПБ-3: past the armour (and the down suit stops nothing)
        }
        float[] r = MobArmorMathSC.absorb(amount, points(), reserve);
        take(r[1]);
        return r[0];
    }

    /**
     * The armour points now: the mod's pieces (suppressed, or their suit in the emergency mode: iron's), other mods'
     * armour its own damageReduceAmount; 0 while down.
     */
    public int points() {
        return down() ? 0 : wornPoints();
    }

    /** The worn pieces' points as points() counts them, the reserve aside (spawnReserveForDamage: still 0 then). */
    public int wornPoints() {
        int p = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = piece(mob, i);
            if (s == null || !(s.getItem() instanceof ItemArmor)) {
                continue;
            }
            ItemArmor a = (ItemArmor) s.getItem();
            if (a instanceof ItemArmorSC) {
                ArmorSuit suit = ((ItemArmorSC) a).getSuit();
                boolean iron = suppressed(i) || emergency && MobArmorMathSC.hasEmergencyMode(suit);
                p += MobArmorMathSC.piecePoints(a.damageReduceAmount, i, iron);
            } else {
                p += a.damageReduceAmount;
            }
        }
        return p;
    }

    /** Server, every tick of the mob: regeneration, the ends of suppression / streams, the DataWatcher. */
    public void serverTick() {
        if (!server()) {
            return;
        }
        long now = now();
        long phase = now + mob.getEntityId();           // the mobs don't all do it on the same tick
        if (regenPerSecond > 0F && reserve < reserveMax && phase % 20 == 0
                && MobArmorMathSC.mayRegen(now, lastDamageAt, regenDelayTicks, streamUntil)) {
            recharge(regenPerSecond);
        }
        if (phase % MobArmorMathSC.SYNC_EVERY == 0 || screen && screenDownUntil == now) {
            sync();                                     // Ф3: the personal screen's bubble back on the tick it recovers
        }
    }

    /** Server: the DataWatcher from the fields (DataWatcher sends only what changed). */
    public void sync() {
        if (!server()) {
            return;
        }
        DataWatcher dw = mob.getDataWatcher();
        dw.updateObject(DW_RESERVE, Integer.valueOf(Math.round(reserve * WATCH_SCALE)));
        dw.updateObject(DW_RESERVE_MAX, Integer.valueOf(Math.round(reserveMax * WATCH_SCALE)));
        long now = now();
        int flags = (shieldUp() ? FLAG_SHIELD : 0) | (emergency ? FLAG_EMERGENCY : 0) | (elite ? FLAG_ELITE : 0)
                | (down() ? FLAG_DOWN : 0) | (screenUp() ? FLAG_SCREEN : 0);
        if (streamUntil > now) {
            flags |= FLAG_DRAINED;
        }
        dw.updateObject(DW_FLAGS, Byte.valueOf((byte) flags));
        int emp = 0;
        for (int i = 0; i < 4; i++) {
            if (suppressedUntil[i] > now) {
                emp |= 1 << i;
            } else if (immuneUntil[i] > now) {
                emp |= 1 << (EMP_IMMUNE_SHIFT + i);
            }
        }
        dw.updateObject(DW_EMP, Byte.valueOf((byte) emp));
    }

    // ------------------------------------------------------------------ ПЭ-1 (IEmpTargetSC on EntityArmoredMobSC delegates here)

    public boolean suppressed(int piece) {
        return piece >= 0 && piece < 4 && mob.worldObj != null && suppressedUntil[piece] > mob.worldObj.getTotalWorldTime();
    }

    /** The piece's immunity runs (only after its suppression ended - as EmpStateSC.immune for a player). */
    public boolean immune(int piece) {
        long now = now();
        return piece >= 0 && piece < 4 && immuneUntil[piece] > now && suppressedUntil[piece] <= now;
    }

    public boolean anySuppressed() {
        for (int i = 0; i < 4; i++) {
            if (suppressed(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Server: a Point / Pulse (rules and the screen already passed): EmpMathSC's durations by each piece's suit - the
     * same as EmpSC.applyHit for a player (ПЭ-1М, the Defense chip, helium -20%, the config, then the immunity), without
     * the heat and the helium boil (not modelled on mobs in Ф1). A suppressed Exo / Singular chestplate drops the shield
     * (shieldUp asks suppressed(1): «Сердце комплекта»). None of the mod's armour in the zone: Slowness.
     */
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        EmpSC.HitResult r = new EmpSC.HitResult();
        if (!server() || mode == null || mode == EmpMode.SIPHON || pieces == null || !ConfigSC.empEnabled) {
            return r;
        }
        long now = now();
        boolean he = heliumOn();
        for (int slot : pieces) {
            ArmorSuit suit = pieceSuit(mob, slot);
            if (suit == null) {
                continue;
            }
            r.anyArmour = true;
            if (immune(slot)) {
                r.immune[slot] = true;
                continue;
            }
            int t = EmpMathSC.durationTicks(suit, mode, upgraded, defenseTier, he, ConfigSC.empDurationMultiplier);
            long until = Math.max(suppressedUntil[slot], now + t);
            suppressedUntil[slot] = until;
            immuneUntil[slot] = until + EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
            r.ticks[slot] = (int) (until - now);
        }
        if (!r.anyArmour) {
            EmpSC.applyNoArmour(mob);
            return r;
        }
        sync();
        return r;
    }

    /**
     * Server, siphon: takes up to `eu` EU out of the reserve (units x the suit's euPerDamage; less than 1 EU left counts
     * as empty). @return EU taken
     */
    public int drainEu(int eu) {
        ArmorSuit s = suit();
        if (eu <= 0 || s == null || reserve <= 0F || !server()) {
            return 0;
        }
        float units = Math.min(reserve, MobArmorMathSC.euToUnits(eu, s));
        if (reserve - units < 1F / s.euPerDamage) {
            units = reserve;                            // a crumb under 1 EU would keep the suit «up» with nothing to give
        }
        long got = MobArmorMathSC.unitsToEu(units, s);
        take(units);
        return (int) Math.max(0L, Math.min(eu, got));
    }

    /**
     * Server, once a second of a stream on this mob: the mob counts as drained for STREAM_HOLD_TICKS (no recharge,
     * FLAG_DRAINED dims its lights). A stream that starts after the last one ended gets its own ПБ-12 cap.
     */
    public void streamSecond(Entity shooter) {
        if (!server()) {
            return;
        }
        long now = now();
        if (streamUntil <= now) {
            streamDrawnEu = 0L;
        }
        streamUntil = now + MobArmorMathSC.STREAM_HOLD_TICKS;
        sync();
    }

    /**
     * Ф3, ПБ-19: the personal EMP screen is up now (server) - it has one, it isn't recovering, and the suit holds a reserve
     * (a «down» suit powers nothing: no bubble, and the ПЭ-1's answer is the Slowness of an empty suit anyway).
     */
    public boolean screenUp() {
        return screen && screenDownUntil <= now() && !down();
    }

    /**
     * Ф3, ПС-21, server: a Commander's order - the reserve's maximum `share` larger, once in the mob's life (commanded), the
     * new room filled as a recharge (not while a siphon draws). @return it was raised now
     */
    public boolean commandBoost(float share) {
        if (commanded || share <= 0F || reserveMax <= 0F || !server()) {
            return false;
        }
        commanded = true;
        float add = reserveMax * share;
        reserveMax += add;
        recharge(add);                                  // syncs; a stream's mob gets only the larger maximum
        sync();
        return true;
    }

    /**
     * Ф3, ПБ-19, server: a ПЭ-1 shot (or a stream's second) meets the personal screen - if it is up it takes the shot and is
     * down for EliteModSC.SCREEN_RECOVER_TICKS. @return true when the screen took it (the shot does nothing else)
     */
    public boolean screenTakes() {
        if (!server() || !screenUp()) {
            return false;
        }
        screenDownUntil = now() + EliteModSC.SCREEN_RECOVER_TICKS;
        sync();
        return true;
    }

    /** ПБ-12: EU this mob's stream may still deliver to the gun's receivers. */
    public long streamRoomEu() {
        return Math.max(0L, MobArmorMathSC.MOB_SIPHON_STREAM_CAP - streamDrawnEu);
    }

    // ------------------------------------------------------------------ client readers (any side; the DataWatcher)

    /** The reserve's share 0..1 (1 for an entity that isn't an armoured mob). */
    public static float clientCharge(EntityLivingBase e) {
        if (!(e instanceof IArmoredMobSC)) {
            return 1F;
        }
        int max = e.getDataWatcher().getWatchableObjectInt(DW_RESERVE_MAX);
        return max <= 0 ? 0F : Math.max(0F, Math.min(1F, e.getDataWatcher().getWatchableObjectInt(DW_RESERVE) / (float) max));
    }

    public static boolean clientFlag(EntityLivingBase e, int flag) {
        return e instanceof IArmoredMobSC && (e.getDataWatcher().getWatchableObjectByte(DW_FLAGS) & flag) != 0;
    }

    public static boolean clientSuppressed(EntityLivingBase e, int piece) {
        return e instanceof IArmoredMobSC && piece >= 0 && piece < 4 && (e.getDataWatcher().getWatchableObjectByte(DW_EMP) & (1 << piece)) != 0;
    }

    public static boolean clientImmune(EntityLivingBase e, int piece) {
        return e instanceof IArmoredMobSC && piece >= 0 && piece < 4
                && (e.getDataWatcher().getWatchableObjectByte(DW_EMP) & (1 << (EMP_IMMUNE_SHIFT + piece))) != 0;
    }

    // ------------------------------------------------------------------ NBT

    public void write(NBTTagCompound tag) {
        NBTTagCompound t = new NBTTagCompound();
        t.setFloat("r", reserve);
        t.setFloat("max", reserveMax);
        t.setBoolean("he", helium);
        t.setBoolean("em", emergency);
        t.setBoolean("el", elite);
        t.setFloat("regen", regenPerSecond);
        t.setInteger("regenDelay", regenDelayTicks);
        t.setByte("def", (byte) defenseTier);
        t.setBoolean("scr", screen);
        t.setBoolean("cmd", commanded);
        // suppression / streams are seconds long: not saved (a reloaded mob starts clean)
        tag.setTag(NBT, t);
    }

    public void read(NBTTagCompound tag) {
        if (!tag.hasKey(NBT)) {
            return;
        }
        NBTTagCompound t = tag.getCompoundTag(NBT);
        reserve = t.getFloat("r");
        reserveMax = t.getFloat("max");
        helium = t.getBoolean("he");
        emergency = t.getBoolean("em");
        elite = t.getBoolean("el");
        regenPerSecond = t.getFloat("regen");
        regenDelayTicks = t.getInteger("regenDelay");
        defenseTier = t.getByte("def");
        screen = t.getBoolean("scr");
        commanded = t.getBoolean("cmd");
        downShown = down();                             // a mob saved «down» doesn't spark again on loading
    }
}
