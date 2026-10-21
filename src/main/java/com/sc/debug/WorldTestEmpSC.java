package com.sc.debug;

import com.mojang.authlib.GameProfile;
import com.sc.emp.EmpArmorSC;
import com.sc.emp.EmpGunLogicSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpRulesSC;
import com.sc.emp.EmpSC;
import com.sc.emp.EmpStateSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModItems;
import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.MobArmorSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * ПЭ-1 in a running world (-Dsc.worldtest=true, docs/plan-pe1-tech.md §6): fake players in a Quantum suit with
 * helium. Point on the helmet: only the helmet suppressed for the table's time x0.8 (helium), night vision off and
 * oxygen breathing still on (package B), the boil x2; the emergency reboot (B) / EmpSC.clearSuppression: immunity
 * at once, the 60 s cooldown. Pulse: every piece for 11 s x0.8, the suit's heat, the boil x3; after the end the
 * pieces are immune - a second Pulse suppresses nothing, heats and boils at half. Siphon through EmpGunLogicSC
 * (package A, изменено 2026-10-11), three streams of 3 s on a Quantum target: (1) it loses 5% of its full charge a
 * second, the shooter's suit gets it less 25%; (2) the shooter's suit nearly full - the target still loses the full
 * amount, the suit is filled and the rest vented; (3) an overheated gun - the stream runs at 25% speed.
 * ОР-1 (2026-10-21): a Point at a fake player's helmet and at an (unspawned) Lineman's Nano chestplate, a second one
 * AGAIN_AFTER ticks later - nothing extended (the ends and the immunity as after the first, HitResult.already = the ticks
 * left, the player's suit still heated), an EMP storm's pulse on them nothing either; once each suppression is over - immune
 * until exactly its end + the immunity, a third Point blocked.
 */
public final class WorldTestEmpSC {

    /** ArmorLogicSC's (private) per-player pending heat key - ПЭ-1's heat lands there. */
    private static final String HEAT_KEY = "scArmorHeat";
    private static final int Y = 200, SIPHON_TICKS = 60;
    /** Phase 1: the shooter's suit this many EU short of full. */
    private static final int VENT_ROOM = 20000;

    private int ticks;
    private boolean done;
    private int x, z;
    private FakePlayer point, pulse, drained, shooter;
    private long pulseEndAt;
    private int pulseTicks;
    private int siphonTick = -1, siphonPhase;
    private ItemStack gun;
    private long targetBefore, shooterBefore;
    private Chunk chunk;
    /** ОР-1: the second Point this many ticks after the first (at most half the shorter suppression). */
    private static final int AGAIN_AFTER = 100;
    private FakePlayer again;
    private EntityLinemanSC againMob;
    /** ОР-1: world times - the second Point, the player's / the mob's end check (0: none pending); the first hit's ends. */
    private long againAt, againEndAt, mobEndAt;
    private long againFirstAt, againUntil, againImmune, mobUntil, mobImmune;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done && !againPending()) {
            return;
        }
        ticks++;
        WorldServer w = DimensionManager.getWorld(0);
        if (w == null) {
            return;
        }
        try {
            if (ticks == 100) {
                if (!ConfigSC.empEnabled) {
                    System.out.println("[SC-WORLDTEST] SKIP pe1: weapons.pe1.enabled = false in the config");
                    done = true;
                    return;
                }
                start(w);
                return;
            }
            againStep(w.getTotalWorldTime());                       // ОР-1: on its own clock, alongside the rest
            if (done) {
                return;
            }
            if (pulseEndAt > 0 && w.getTotalWorldTime() >= pulseEndAt) {
                pulseEndAt = 0;
                checkImmunity();
                startSiphon(w);
            } else if (siphonTick >= 0) {
                siphonStep(w);
            }
        } catch (Throwable t) {
            System.out.println("[SC-WORLDTEST] FAIL pe1: exception " + t);
            t.printStackTrace();
            boolean wasDone = done;
            cleanAgain();                                           // says DONE when the rest was over already
            if (!wasDone) {
                clean();
            }
        }
    }

    // ------------------------------------------------------------------ Point, the reboot, Pulse

    private void start(WorldServer w) {
        ChunkCoordinates spawn = w.getSpawnPoint();
        x = spawn.posX;
        z = spawn.posZ;
        point = fake(w, "EmpPoint", x + 6, z);
        pulse = fake(w, "EmpPulse", x + 9, z);
        checkPoint();
        checkReboot();
        checkPulse();
        startAgain(w);
    }

    private void checkPoint() {
        wear(point, ArmorSuit.QUANTUM, 100);
        boolean helium = EmpSC.heliumInLoop(point);
        boolean nightBefore = ArmorLogicSC.active(point, ArmorFeature.NIGHT_VISION), airBefore = ArmorLogicSC.active(point, ArmorFeature.AIR);
        int heat0 = point.getEntityData().getInteger(HEAT_KEY);
        int[] slots = EmpMathSC.pointPieces(EmpMathSC.ZONE_AUTO, 0.9);
        EmpSC.HitResult r = EmpSC.applyHit(point, EmpMode.POINT, false, slots);
        int want = EmpMathSC.durationTicks(ArmorSuit.QUANTUM, EmpMode.POINT, false, 0, helium, ConfigSC.empDurationMultiplier);
        boolean core = helium && slots.length == 1 && slots[0] == 0 && r.ticks[0] == want && r.ticks[1] == 0 && r.ticks[2] == 0 && r.ticks[3] == 0
                && EmpSC.isSuppressed(point, 0) && !EmpSC.isSuppressed(point, 1) && EmpSC.suppressedTicks(point, 0) == want
                && EmpSC.immuneTicks(point, 0) == 0 && EmpSC.boilMultiplier(point) == EmpMathSC.BOIL_MUL_POINT && r.heat > 0
                && point.getEntityData().getInteger(HEAT_KEY) > heat0;
        System.out.println("[SC-WORLDTEST] " + (core ? "PASS" : "FAIL") + " pe1 Point on the helmet: suppressed " + r.ticks[0] + " ticks (want "
                + want + " = 13 s x0.8 helium " + helium + "), the other pieces " + r.ticks[1] + "/" + r.ticks[2] + "/" + r.ticks[3]
                + ", boil x" + EmpSC.boilMultiplier(point) + ", heat +" + r.heat);
        boolean night = ArmorLogicSC.active(point, ArmorFeature.NIGHT_VISION), air = ArmorLogicSC.active(point, ArmorFeature.AIR);
        System.out.println("[SC-WORLDTEST] " + (nightBefore && !night && airBefore && air ? "PASS" : "FAIL")
                + " pe1 suppressed helmet (package B): night vision " + nightBefore + " -> " + night + " (off), oxygen breathing "
                + airBefore + " -> " + air + " (stays on)");
    }

    /** B's emergency reboot pays argon (100 mB if the suit has it) + 20% charge; then the core's effect, the cooldown, a refusal. */
    private void checkReboot() {
        long charge0 = suitCharge(point);
        int argonCap = ArmorGasSC.capacityOf(ArmorGasSC.wornSet(point), ArmorGasSC.Gas.ARGON);
        int argon0 = ArmorGasSC.amountOf(ArmorGasSC.wornSet(point), ArmorGasSC.Gas.ARGON);
        EmpArmorSC.emergencyReboot(point);
        long charge1 = suitCharge(point);
        int argon1 = ArmorGasSC.amountOf(ArmorGasSC.wornSet(point), ArmorGasSC.Gas.ARGON);
        boolean paid = !EmpSC.isSuppressed(point, 0) && charge1 < charge0
                && (argonCap <= 0 || argon1 == argon0 - EmpMathSC.REBOOT_ARGON_MB);
        System.out.println("[SC-WORLDTEST] " + (paid ? "PASS" : "FAIL") + " pe1 emergency reboot (package B): suppression gone "
                + !EmpSC.isSuppressed(point, 0) + ", charge " + charge0 + " -> " + charge1 + " (20%), argon " + argon0 + " -> " + argon1
                + " (-" + EmpMathSC.REBOOT_ARGON_MB + " mB when the suit has a tank: " + (argonCap > 0) + ")");
        if (EmpSC.isSuppressed(point, 0)) {
            EmpSC.clearSuppression(point);                         // B didn't: still check the core's part
        }
        int immunity = EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
        boolean core = !EmpSC.isSuppressed(point, 0) && EmpSC.immuneTicks(point, 0) == immunity
                && EmpSC.rebootCooldownTicks(point) == EmpMathSC.REBOOT_COOLDOWN;
        long charge2 = suitCharge(point);
        EmpArmorSC.emergencyReboot(point);                         // nothing suppressed (and on cooldown): refused, nothing paid
        boolean refused = suitCharge(point) == charge2 && EmpSC.rebootCooldownTicks(point) == EmpMathSC.REBOOT_COOLDOWN;
        System.out.println("[SC-WORLDTEST] " + (core && refused ? "PASS" : "FAIL") + " pe1 clearSuppression: immune at once "
                + EmpSC.immuneTicks(point, 0) + " ticks (want " + immunity + "), reboot cooldown " + EmpSC.rebootCooldownTicks(point)
                + " (want " + EmpMathSC.REBOOT_COOLDOWN + "); a second reboot refused without payment " + refused);
    }

    private void checkPulse() {
        wear(pulse, ArmorSuit.QUANTUM, 100);
        boolean helium = EmpSC.heliumInLoop(pulse);
        int heat0 = pulse.getEntityData().getInteger(HEAT_KEY);
        EmpSC.HitResult r = EmpSC.applyHit(pulse, EmpMode.PULSE, false, new int[]{0, 1, 2, 3});
        int want = EmpMathSC.durationTicks(ArmorSuit.QUANTUM, EmpMode.PULSE, false, 0, helium, ConfigSC.empDurationMultiplier);
        boolean all = true;
        for (int i = 0; i < 4; i++) {
            all &= r.ticks[i] == want && EmpSC.isSuppressed(pulse, i);
        }
        int wantHeat = EmpMathSC.heat(ArmorSuit.QUANTUM.heatCapacity, EmpMode.PULSE, false, false, false, 0, ConfigSC.empHeatMultiplier);
        int loop = ArmorGasSC.capacityOf(ArmorGasSC.wornSet(pulse), ArmorGasSC.Gas.HELIUM);
        float boil = EmpSC.boilExtraMbPerSecond(pulse, loop);
        boolean ok = helium && all && r.heat == wantHeat && pulse.getEntityData().getInteger(HEAT_KEY) - heat0 >= wantHeat
                && EmpSC.boilMultiplier(pulse) == EmpMathSC.BOIL_MUL_PULSE && EmpSC.boilTicks(pulse) == EmpMathSC.BOIL_TICKS_PULSE && boil > 0F;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " pe1 Pulse: every piece suppressed " + all + " for " + r.ticks[0]
                + " ticks (want " + want + " = 11 s x0.8 helium " + helium + "), heat +" + r.heat + " (want " + wantHeat + "), boil x"
                + EmpSC.boilMultiplier(pulse) + " for " + EmpSC.boilTicks(pulse) + " ticks, " + boil + " mB/s extra of a " + loop + " mB loop");
        pulseTicks = want;
        pulseEndAt = EmpSC.now(pulse) + want + 2;
    }

    /** After the Pulse: immune, not suppressed; a second Pulse suppresses nothing, heats (a repeat, immune) and boils at half. */
    private void checkImmunity() {
        boolean immune = !EmpSC.anySuppressed(pulse);
        int left = EmpSC.immuneTicks(pulse, 0);
        for (int i = 0; i < 4; i++) {
            immune &= EmpSC.immuneTicks(pulse, i) > 0;
        }
        EmpSC.HitResult r = EmpSC.applyHit(pulse, EmpMode.PULSE, false, new int[]{0, 1, 2, 3});
        boolean blocked = !r.anySuppressed() && !EmpSC.anySuppressed(pulse);
        for (int i = 0; i < 4; i++) {
            blocked &= r.immune[i];
        }
        int wantHeat = EmpMathSC.heat(ArmorSuit.QUANTUM.heatCapacity, EmpMode.PULSE, true, true, false, 0, ConfigSC.empHeatMultiplier);
        int loop = ArmorGasSC.capacityOf(ArmorGasSC.wornSet(pulse), ArmorGasSC.Gas.HELIUM);
        float boil = EmpSC.boilExtraMbPerSecond(pulse, loop);
        float wantBoil = EmpMathSC.boilMbPerSecond(loop, EmpMathSC.BOIL_MUL_PULSE, false, true, ConfigSC.empHeliumBoilMultiplier);
        boolean ok = immune && blocked && r.heat == wantHeat && Math.abs(boil - wantBoil) < 1e-3F;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " pe1 immunity after " + pulseTicks + " ticks of Pulse: immune " + immune
                + " (" + left + " ticks left), a second Pulse suppresses nothing " + blocked + ", heat +" + r.heat + " (want " + wantHeat
                + ", repeat + immune), boil " + boil + " mB/s (want " + wantBoil + ", half)");
    }

    // ------------------------------------------------------------------ ОР-1: a hit on a piece still suppressed

    private boolean againPending() {
        return againAt > 0 || againEndAt > 0 || mobEndAt > 0;
    }

    /** The first Point at the player's helmet and at the mob's chestplate; the second one is due AGAIN_AFTER ticks later. */
    private void startAgain(WorldServer w) {
        again = fake(w, "EmpAgain", x + 12, z);
        wear(again, ArmorSuit.QUANTUM, 100);
        EmpSC.HitResult r = EmpSC.applyHit(again, EmpMode.POINT, false, new int[]{0});
        EmpStateSC s = EmpSC.state(again);
        againUntil = s.until[0];
        againImmune = s.immuneUntil[0];
        againMob = new EntityLinemanSC(w);                          // never spawned: only its MobArmorSC is asked
        againMob.setLocationAndAngles(x + 15.5, Y, z + 0.5, 0F, 0F);
        againMob.setCurrentItemOrArmor(4 - 1, new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[1]));
        EmpSC.HitResult m = againMob.empHit(again, EmpMode.POINT, false, new int[]{1});
        MobArmorSC a = againMob.mobArmor();
        mobUntil = a.suppressedUntil[1];
        mobImmune = a.immuneUntil[1];
        againFirstAt = w.getTotalWorldTime();
        long shortest = Math.min(againUntil, mobUntil) - againFirstAt;
        if (r.ticks[0] <= 0 || m.ticks[1] <= 0 || shortest < 2) {
            System.out.println("[SC-WORLDTEST] FAIL pe1 a Point on a piece still suppressed (ОР-1): the first Point suppressed the player's helmet "
                    + r.ticks[0] + " ticks, the mob's chestplate " + m.ticks[1] + " (both want > 1)");
            cleanAgain();
            return;
        }
        againAt = againFirstAt + Math.min(AGAIN_AFTER, shortest / 2);
    }

    /** The due checks of the repeat (world time `now`). */
    private void againStep(long now) {
        if (againAt > 0 && now >= againAt) {
            againAt = 0;
            checkAgain(now);
        }
        if (againEndAt > 0 && now >= againEndAt) {
            againEndAt = 0;
            checkAgainEndPlayer(now);
        }
        if (mobEndAt > 0 && now >= mobEndAt) {
            mobEndAt = 0;
            checkAgainEndMob(now);
        }
        if (!againPending() && again != null) {
            cleanAgain();
        }
    }

    /** The second Point (and a storm's pulse) while both pieces are still suppressed: nothing extended, the heat still comes. */
    private void checkAgain(long now) {
        int immunity = EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
        EmpSC.HitResult r = EmpSC.applyHit(again, EmpMode.POINT, false, new int[]{0});
        EmpStateSC s = EmpSC.state(again);
        int wantHeat = EmpMathSC.heat(ArmorSuit.QUANTUM.heatCapacity, EmpMode.POINT, now - againFirstAt < EmpMathSC.DIMINISH_WINDOW, false, false, 0,
                ConfigSC.empHeatMultiplier);
        boolean player = r.ticks[0] == 0 && !r.immune[0] && !r.anySuppressed() && r.anyAlready() && r.already[0] == (int) (againUntil - now)
                && s.until[0] == againUntil && s.immuneUntil[0] == againImmune && againImmune == againUntil + immunity && r.heat == wantHeat;
        boolean storm = EmpSC.stormHit(again, 0, 80, 2, 40) == 0 && s.until[0] == againUntil && s.immuneUntil[0] == againImmune;
        EmpSC.HitResult m = againMob.empHit(again, EmpMode.POINT, false, new int[]{1});
        MobArmorSC a = againMob.mobArmor();
        boolean mob = m.ticks[1] == 0 && !m.immune[1] && m.already[1] == (int) (mobUntil - now) && a.suppressedUntil[1] == mobUntil
                && a.immuneUntil[1] == mobImmune && mobImmune == mobUntil + immunity;
        boolean mobStorm = !a.stormHit(1, 80) && a.suppressedUntil[1] == mobUntil && a.immuneUntil[1] == mobImmune;
        System.out.println("[SC-WORLDTEST] " + (player && storm && mob && mobStorm ? "PASS" : "FAIL") + " pe1 a second Point "
                + (now - againFirstAt) + " ticks later on a piece still suppressed (ОР-1): the player's helmet not extended " + player + " ("
                + r.already[0] + " ticks left, want " + (againUntil - now) + "; immune until its end + " + immunity + "; heat +" + r.heat
                + ", want " + wantHeat + "), a storm's pulse nothing " + storm + "; the Lineman's chestplate not extended " + mob + " ("
                + m.already[1] + " ticks left, want " + (mobUntil - now) + "), a storm's pulse nothing " + mobStorm);
        againEndAt = againUntil + 1;
        mobEndAt = mobUntil + 1;
    }

    /** The player's helmet after its (first) suppression: immune exactly until its end + the immunity, a third Point blocked. */
    private void checkAgainEndPlayer(long now) {
        int immune = EmpSC.immuneTicks(again, 0);
        boolean free = !EmpSC.isSuppressed(again, 0);
        EmpSC.HitResult r = EmpSC.applyHit(again, EmpMode.POINT, false, new int[]{0});
        boolean ok = againImmune > now ? free && immune == (int) (againImmune - now) && r.immune[0] && r.ticks[0] == 0 && r.already[0] == 0
                && EmpSC.state(again).immuneUntil[0] == againImmune : free;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " pe1 after the not extended suppression (ОР-1): the player's helmet "
                + "free " + free + ", immune " + immune + " ticks (want " + Math.max(0L, againImmune - now) + " = the first hit's end + the immunity), "
                + "a third Point blocked " + r.immune[0]);
    }

    /** The same for the mob's chestplate. */
    private void checkAgainEndMob(long now) {
        MobArmorSC a = againMob.mobArmor();
        boolean free = !a.suppressed(1), immune = a.immune(1);
        EmpSC.HitResult m = againMob.empHit(again, EmpMode.POINT, false, new int[]{1});
        boolean ok = mobImmune > now ? free && immune && m.immune[1] && m.ticks[1] == 0 && m.already[1] == 0 && a.immuneUntil[1] == mobImmune : free;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " pe1 after the not extended suppression (ОР-1): the Lineman's chestplate "
                + "free " + free + ", immune " + immune + " until " + a.immuneUntil[1] + " (want " + mobImmune
                + " = the first hit's end + the immunity), a third Point blocked " + m.immune[1]);
    }

    private void cleanAgain() {
        againAt = 0;
        againEndAt = 0;
        mobEndAt = 0;
        if (again != null) {
            reset(again);
            for (int t = 0; t < 4; t++) {
                again.inventory.armorInventory[t] = null;
            }
            again = null;
        }
        againMob = null;
        if (done) {
            System.out.println("[SC-WORLDTEST] DONE pe1");
        }
    }

    // ------------------------------------------------------------------ Siphon (package A)

    private void startSiphon(WorldServer w) {
        drained = new TargetPlayer(w, new GameProfile(java.util.UUID.nameUUIDFromBytes("EmpDrained".getBytes()), "EmpDrained"));
        drained.setLocationAndAngles(x + 0.5, Y, z + 4 + 0.5, 0F, 0F);
        reset(drained);
        shooter = fake(w, "EmpShooter", x, z);                      // facing south (+Z), the target 4 blocks ahead
        chunk = w.getChunkFromBlockCoords(x, z + 4);
        chunk.addEntity(drained);                                   // findable by the hitscan, never ticked (not in the world's list)
        idMap(w).addKey(drained.getEntityId(), drained);            // and by id, as the stream finds its target each tick
        gun = new ItemStack(ModItems.empGun);
        ItemEmpGunSC.setCharge(gun, ItemEmpGunSC.capacityOf(gun));
        ItemEmpGunSC.setMode(gun, EmpMode.SIPHON);
        ItemEmpGunSC.setReceiverEnabled(gun, EmpMathSC.RECV_BATTERY, false);
        ItemEmpGunSC.setReceiverEnabled(gun, EmpMathSC.RECV_GUN, false);   // only the shooter's suit takes
        shooter.inventory.currentItem = 0;
        shooter.inventory.mainInventory[0] = gun;
        int refusal = EmpRulesSC.refusal(shooter, drained, false);
        System.out.println("[SC-WORLDTEST] INFO pe1 siphon: the rules say " + refusal + " (0 = OK) for one fake player on another");
        siphonPhase = 0;
        startStream(w);
    }

    /** Phase 0: an empty shooter suit; 1: the shooter's suit VENT_ROOM short of full (venting); 2: an overheated gun. */
    private void startStream(WorldServer w) {
        wear(drained, ArmorSuit.QUANTUM, 100);
        wear(shooter, ArmorSuit.QUANTUM, siphonPhase == 1 ? 100 : 0);
        if (siphonPhase == 1) {
            ItemStack chest = shooter.inventory.armorInventory[2];
            ItemArmorSC.setCharge(chest, ItemArmorSC.capacityOf(chest) - VENT_ROOM);
        }
        ItemEmpGunSC.setHeat(gun, siphonPhase == 2 ? EmpMathSC.GUN_HEAT_MAX : 0, w.getTotalWorldTime());
        targetBefore = suitCharge(drained);
        shooterBefore = suitCharge(shooter);
        ItemStack held = EmpGunLogicSC.rightClick(gun, w, shooter);
        if (held != null && held != gun) {
            gun = held;
            shooter.inventory.mainInventory[0] = gun;
        }
        siphonTick = 0;
    }

    private void siphonStep(WorldServer w) {
        siphonTick++;
        if (siphonTick <= SIPHON_TICKS) {
            EmpGunLogicSC.usingTick(gun, shooter, siphonTick);       // FakePlayers aren't ticked: drive the item and the player tick by hand
            EmpGunLogicSC.playerTick(shooter);
            return;
        }
        boolean draining = EmpSC.drained(drained);
        boolean running = shooter.isUsingItem();                    // not cut by a full receiver / the overheat
        EmpGunLogicSC.stoppedUsing(gun, w, shooter, SIPHON_TICKS);
        shooter.clearItemInUse();
        long lost = targetBefore - suitCharge(drained), got = suitCharge(shooter) - shooterBefore;
        long cap = suitCapacity(drained);
        int lossPct = EmpMathSC.siphonLossPct(false, false, ConfigSC.empSiphonLoss);
        // 60 ticks = 12 transfers of 5 ticks (the first at once): 5%/s of the full charge = 1/4 of that a step
        int step = EmpMathSC.throttle(EmpMathSC.siphonRate(cap, ArmorSuit.QUANTUM, false, 0, ConfigSC.empSiphonRateMultiplier, 5), siphonPhase == 2);
        long wantLost = 12L * step;
        boolean lostOk = Math.abs(lost - wantLost) <= step;
        String pctPerSecond = String.format(java.util.Locale.ROOT, "%.2f", lost * 100D / cap / (SIPHON_TICKS / 20D));
        boolean ok;
        String what;
        if (siphonPhase == 0) {
            long want = lost * (100 - lossPct) / 100;
            ok = lostOk && got > 0 && Math.abs(got - want) <= lost / 50 + 100 && draining && running;
            what = "Siphon (package A), " + SIPHON_TICKS + " ticks on a Quantum suit: it lost " + lost + " EU = " + pctPerSecond
                    + "% of its full charge a second (want ~" + wantLost + ", 5%/s), the shooter's suit got " + got + " (want ~" + want + ", loss "
                    + lossPct + "%), the target saw the drain " + draining + ", the gun has " + ItemEmpGunSC.chargeOf(gun) + " EU";
        } else if (siphonPhase == 1) {
            long vented = lost * (100 - lossPct) / 100 - got;
            ok = lostOk && got > 0 && got <= VENT_ROOM && suitCharge(shooter) == suitCapacity(shooter) && vented > 0 && running;
            what = "Siphon overflow: the shooter's suit " + VENT_ROOM + " EU short of full - the target still lost " + lost + " EU (want ~"
                    + wantLost + "), the suit got " + got + " and is full, ~" + vented + " EU vented, the stream kept running " + running;
        } else {
            ok = lostOk && running && ItemEmpGunSC.overheated(gun);
            what = "Siphon soft overheat: an overheated gun streams at " + EmpMathSC.SIPHON_THROTTLED_PCT + "% - the target lost " + lost
                    + " EU = " + pctPerSecond + "%/s (want ~" + wantLost + "), still running " + running + ", the gun after: heat "
                    + ItemEmpGunSC.heatOf(gun) + ", overheated " + ItemEmpGunSC.overheated(gun);
        }
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " pe1 " + what);
        if (++siphonPhase <= 2) {
            startStream(w);
        } else {
            clean();
        }
    }

    // ------------------------------------------------------------------

    /**
     * The siphon's target: Forge's FakePlayer refuses every attack (canAttackPlayer = false), so EmpRulesSC would say
     * NO_PVP; this one answers as a real EntityPlayerMP does - the server's PvP, then the scoreboard team's friendly fire.
     */
    private static final class TargetPlayer extends FakePlayer {
        TargetPlayer(WorldServer w, GameProfile profile) {
            super(w, profile);
        }

        @Override
        public boolean canAttackPlayer(EntityPlayer other) {
            MinecraftServer s = MinecraftServer.getServer();
            if (s != null && !s.isPVPEnabled()) {
                return false;
            }
            Team mine = getTeam(), theirs = other == null ? null : other.getTeam();
            return mine == null || !mine.isSameTeam(theirs) || mine.getAllowFriendlyFire();
        }
    }

    /** WorldServer's id -> entity map (getEntityByID): the target isn't spawned, so it goes in by hand. */
    private static IntHashMap idMap(WorldServer w) {
        return (IntHashMap) ReflectionHelper.getPrivateValue(WorldServer.class, w, "entityIdMap", "field_73066_T");
    }

    private FakePlayer fake(WorldServer w, String name, int px, int pz) {
        FakePlayer p = FakePlayerFactory.get(w, new GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.setLocationAndAngles(px + 0.5, Y, pz + 0.5, 0F, 0F);
        reset(p);
        return p;
    }

    /** FakePlayers are cached across runs: a clean ПЭ-1 state and no pending heat. */
    private static void reset(FakePlayer p) {
        EmpStateSC s = EmpSC.state(p);
        for (int i = 0; i < 4; i++) {
            s.until[i] = 0;
            s.immuneUntil[i] = 0;
        }
        s.boilMul = 0;
        s.boilUntil = 0;
        s.streamUntil = 0;
        s.drainBy = 0;
        s.lastHeatHit = 0;
        s.aimedUntil = 0;
        s.aimedBy = 0;
        s.rebootReadyAt = 0;
        EmpSC.save(p, s);
        p.getEntityData().removeTag(HEAT_KEY);
    }

    /** A full suit at `pct` charge, every tank full; night vision and oxygen breathing on. */
    private static void wear(FakePlayer p, ArmorSuit suit, int pct) {
        for (int t = 0; t < 4; t++) {
            ItemStack s = new ItemStack(ModItems.ARMOR.get(suit)[t]);
            ItemArmorSC.setCharge(s, (int) ((long) ItemArmorSC.capacityOf(s) * pct / 100));
            for (ArmorGasSC.Gas g : ArmorGasSC.Gas.values()) {
                ArmorGasSC.setAmount(s, g, ArmorGasSC.capacity(s, g));
            }
            if (t == 0) {
                ItemArmorSC.setEnabled(s, ArmorFeature.NIGHT_VISION, true);
                ItemArmorSC.setEnabled(s, ArmorFeature.AIR, true);
            }
            p.inventory.armorInventory[3 - t] = s;
        }
    }

    private static long suitCapacity(FakePlayer p) {
        long sum = 0;
        for (int t = 0; t < 4; t++) {
            ItemStack s = ArmorLogicSC.piece(p, t);
            sum += s == null ? 0 : ItemArmorSC.capacityOf(s);
        }
        return sum;
    }

    private static long suitCharge(FakePlayer p) {
        long sum = 0;
        for (int t = 0; t < 4; t++) {
            ItemStack s = ArmorLogicSC.piece(p, t);
            sum += s == null ? 0 : ItemArmorSC.chargeOf(s);
        }
        return sum;
    }

    private void clean() {
        done = true;
        siphonTick = -1;
        pulseEndAt = 0;
        if (chunk != null && drained != null) {
            chunk.removeEntity(drained);
            idMap((WorldServer) drained.worldObj).removeObject(drained.getEntityId());
        }
        for (FakePlayer p : new FakePlayer[]{point, pulse, drained, shooter}) {
            if (p == null) {
                continue;
            }
            EmpGunLogicSC.forget(p);
            reset(p);
            for (int t = 0; t < 4; t++) {
                p.inventory.armorInventory[t] = null;
            }
            p.inventory.mainInventory[0] = null;
        }
        if (!againPending()) {
            System.out.println("[SC-WORLDTEST] DONE pe1");          // else cleanAgain says it, once the ОР-1 checks are over
        }
    }
}
