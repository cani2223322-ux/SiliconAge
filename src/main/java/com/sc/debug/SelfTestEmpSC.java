package com.sc.debug;

import java.util.Arrays;
import java.util.Map;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpModule;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpStateSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.ArmorSuit;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Self-checks of ПЭ-1 (docs/plan-pe1-tech.md §6, run from SelfTestSC with -Dsc.selftest=true): EmpMathSC's numbers
 * and arithmetic (durations, Point zones, heat, boil, siphon, the gun's heat), the gun's NBT (charge with the
 * Capacity module, modes, receivers), EmpStateSC, the network discriminators, the texts in both languages,
 * the recipes and the handbook articles.
 */
final class SelfTestEmpSC {

    private SelfTestEmpSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "pe1: " + what);
    }

    static void run() {
        durations();
        zones();
        heat();
        boil();
        siphon();
        gunHeat();
        gunNbt();
        modesAndReceivers();
        state();
        network();
        texts();
        recipes();
        handbook();
    }

    // ------------------------------------------------------------------ EmpMathSC

    /** §2: the table, ПЭ-1М (Point only), the Defense chip, helium, the config multiplier, the minimum. */
    private static void durations() {
        int[][] table = {{16, 14}, {13, 11}, {11, 8}, {8, 6}};
        boolean base = true;
        StringBuilder got = new StringBuilder();
        for (ArmorSuit suit : ArmorSuit.values()) {
            int[] row = table[Math.min(table.length - 1, suit.ordinal())];
            int p = EmpMathSC.durationTicks(suit, EmpMode.POINT, false, 0, false, 1F);
            int u = EmpMathSC.durationTicks(suit, EmpMode.PULSE, false, 0, false, 1F);
            base &= p == row[0] * 20 && u == row[1] * 20;
            got.append(' ').append(suit.name()).append('=').append(p / 20).append('/').append(u / 20);
        }
        boolean mods = EmpMathSC.durationTicks(ArmorSuit.NANO, EmpMode.POINT, true, 0, false, 1F) == 400        // 16 s x1.25
                && EmpMathSC.durationTicks(ArmorSuit.NANO, EmpMode.PULSE, true, 0, false, 1F) == 280             // Pulse: no ПЭ-1М bonus
                && EmpMathSC.durationTicks(ArmorSuit.NANO, EmpMode.POINT, false, 3, false, 1F) == 224            // chip III -30%
                && EmpMathSC.durationTicks(ArmorSuit.NANO, EmpMode.POINT, false, 1, false, 1F) == 288            // chip I -10%
                && EmpMathSC.durationTicks(ArmorSuit.QUANTUM, EmpMode.PULSE, false, 0, true, 1F) == 176         // 11 s x0.8 helium
                && EmpMathSC.durationTicks(ArmorSuit.SINGULAR, EmpMode.PULSE, false, 0, false, 0.5F) == 60      // config x0.5
                && EmpMathSC.durationTicks(ArmorSuit.SINGULAR, EmpMode.PULSE, false, 3, true, 0.1F) == EmpMathSC.MIN_DURATION_TICKS
                && EmpMathSC.durationTicks(ArmorSuit.EXO, EmpMode.SIPHON, false, 0, false, 1F) == 0
                && EmpMathSC.durationTicks(null, EmpMode.POINT, false, 0, false, 1F) == 0
                && EmpMathSC.immunityTicks(20) == 400 && EmpMathSC.immunityTicks(-3) == 0;
        check(base && mods, "suppression time: the table" + got + ", ПЭ-1М Point x1.25, chip -10/30%, helium -20%, config, at least "
                + EmpMathSC.MIN_DURATION_TICKS + " ticks, Siphon / no suit 0");
        boolean gun = EmpMathSC.range(EmpMode.POINT, false) == 32 && EmpMathSC.range(EmpMode.POINT, true) == 48
                && EmpMathSC.range(EmpMode.PULSE, true) == 32 && EmpMathSC.range(EmpMode.SIPHON, false) == 16
                && EmpMathSC.range(EmpMode.SIPHON, true) == 24 && EmpMathSC.chargeTicks(EmpMode.POINT, true) == 20
                && EmpMathSC.chargeTicks(EmpMode.PULSE, false) == 60 && EmpMathSC.chargeTicks(EmpMode.PULSE, true) == 40
                && EmpMathSC.chargeTicks(EmpMode.SIPHON, false) == 0 && EmpMathSC.euCost(EmpMode.POINT) == 50000
                && EmpMathSC.euCost(EmpMode.PULSE) == 150000 && EmpMathSC.euCost(EmpMode.SIPHON) == 5000
                && EmpMathSC.capacity(false) == 600000 && EmpMathSC.capacity(true) == 1200000 && EmpMathSC.PULSE_COOLDOWN == 400;
        check(gun, "the gun's numbers: ranges 32 (Focus 48) / 32 / 16 (Focus 24), charge-up 1 / 3 (ПЭ-1М 2) s, 50 000 / 150 000 / 5 000 EU, "
                + "buffer 600 000 (Capacity 1.2M), Pulse cooldown 20 s");
    }

    /** §1: Point zones by the hit's height and the K menu's "куда бить". */
    private static void zones() {
        int a = EmpMathSC.ZONE_AUTO;
        boolean ok = Arrays.equals(EmpMathSC.pointPieces(a, 0.95), new int[]{0}) && Arrays.equals(EmpMathSC.pointPieces(a, 0.72), new int[]{0})
                && Arrays.equals(EmpMathSC.pointPieces(a, 0.71), new int[]{1}) && Arrays.equals(EmpMathSC.pointPieces(a, 0.40), new int[]{1})
                && Arrays.equals(EmpMathSC.pointPieces(a, 0.39), new int[]{2, 3}) && Arrays.equals(EmpMathSC.pointPieces(a, 0.0), new int[]{2, 3})
                && Arrays.equals(EmpMathSC.pointPieces(EmpMathSC.ZONE_HEAD, 0.1), new int[]{0})
                && Arrays.equals(EmpMathSC.pointPieces(EmpMathSC.ZONE_BODY, 0.95), new int[]{1})
                && Arrays.equals(EmpMathSC.pointPieces(EmpMathSC.ZONE_LEGS, 0.95), new int[]{2, 3})
                && Arrays.equals(EmpMathSC.pointPieces(9, 0.95), new int[]{0});                      // a bad setting: by the hit
        check(ok, "Point zones: head >= 0.72 -> helmet, body >= 0.40 -> chestplate, below -> leggings + boots; fixed zones override");
    }

    /** §3: 15 / 35 / 3%, the repeat halves (not the stream), immunity, Singular, the Defense chip, the config. */
    private static void heat() {
        int cap = 1000;
        boolean ok = EmpMathSC.heat(cap, EmpMode.POINT, false, false, false, 0, 1F) == 150
                && EmpMathSC.heat(cap, EmpMode.PULSE, false, false, false, 0, 1F) == 350
                && EmpMathSC.heat(cap, EmpMode.SIPHON, false, false, false, 0, 1F) == 30
                && EmpMathSC.heat(cap, EmpMode.PULSE, true, false, false, 0, 1F) == 175
                && EmpMathSC.heat(cap, EmpMode.SIPHON, true, false, false, 0, 1F) == 30
                && EmpMathSC.heat(cap, EmpMode.POINT, false, true, false, 0, 1F) == 75
                && EmpMathSC.heat(cap, EmpMode.PULSE, false, false, true, 0, 1F) == 175
                && EmpMathSC.heat(cap, EmpMode.POINT, false, false, false, 1, 1F) == 135
                && EmpMathSC.heat(cap, EmpMode.PULSE, false, false, false, 3, 1F) == 245
                && EmpMathSC.heat(cap, EmpMode.PULSE, true, true, true, 0, 1F) == 44                       // 350 / 8 = 43.75
                && EmpMathSC.heat(cap, EmpMode.POINT, false, false, false, 0, 0F) == 0
                && EmpMathSC.heat(cap, EmpMode.POINT, false, false, false, 0, 2F) == 300;
        // the design's "≈ 4 Импульса или 8 Точек" to overheat counts the suit's own heat too: only printed, not asserted
        int pulses = hitsToFull(EmpMode.PULSE), points = hitsToFull(EmpMode.POINT);
        check(ok, "suit heat: 15 / 35 / 3%, repeats x0.5 (not the stream), immune x0.5, Singular x0.5, chip -10/30%, config; "
                + "from cold to the limit by ПЭ-1 alone: " + pulses + " Pulses, " + points + " Points in a row");
    }

    private static int hitsToFull(EmpMode m) {
        int cap = 1000, sum = 0, n = 0;
        while (sum < cap && n < 50) {
            sum += EmpMathSC.heat(cap, m, n > 0, false, false, 0, 1F);
            n++;
        }
        return n;
    }

    /** §3: boil shares, x1/2 immune, config; boils don't stack - the stronger wins, the same one is extended. */
    private static void boil() {
        boolean pct = near(EmpMathSC.boilLoopPctPerSecond(3, false), 0.5F) && near(EmpMathSC.boilLoopPctPerSecond(2, false), 5F / 15F)
                && EmpMathSC.boilLoopPctPerSecond(1, false) == 0F && near(EmpMathSC.boilLoopPctPerSecond(1, true), 1F)
                && near(EmpMathSC.boilMbPerSecond(1000, 3, false, false, 1F), 5F) && near(EmpMathSC.boilMbPerSecond(1000, 3, false, true, 1F), 2.5F)
                && near(EmpMathSC.boilMbPerSecond(1000, 2, false, false, 2F), 1000 * (5F / 15F) / 100F * 2F)
                && EmpMathSC.boilMbPerSecond(1000, 3, false, false, 0F) == 0F
                && EmpMathSC.boilMul(EmpMode.POINT) == 2 && EmpMathSC.boilMul(EmpMode.PULSE) == 3 && EmpMathSC.boilMul(EmpMode.SIPHON) == 2
                && EmpMathSC.boilTicks(EmpMode.POINT) == 300 && EmpMathSC.boilTicks(EmpMode.PULSE) == 400;
        long now = 100;
        boolean merge = Arrays.equals(EmpMathSC.mergeBoil(3, 500, 2, 600, now), new long[]{3, 500})       // weaker while stronger runs
                && Arrays.equals(EmpMathSC.mergeBoil(2, 500, 2, 600, now), new long[]{2, 600})             // the same one is extended
                && Arrays.equals(EmpMathSC.mergeBoil(2, 700, 2, 600, now), new long[]{2, 700})             // ...never shortened
                && Arrays.equals(EmpMathSC.mergeBoil(2, 500, 3, 400, now), new long[]{3, 400})             // stronger wins
                && Arrays.equals(EmpMathSC.mergeBoil(3, 50, 2, 400, now), new long[]{2, 400});             // the old one is over
        check(pct && merge, "helium boil: Pulse 10% / 20 s, Point 5% / 15 s, stream 1%/s, immune x0.5, config; no stacking, extended by the same");
    }

    /** §4: the rate = min(gun, receiver, target) x chip x config; losses 25 / 10 / 0; delivered. */
    private static void siphon() {
        boolean rate = EmpMathSC.gunRate(false) == 2048 && EmpMathSC.gunRate(true) == 8192
                && EmpMathSC.siphonRate(2048, 512, 2048, 0, 1F) == 512 && EmpMathSC.siphonRate(2048, 8192, 8192, 0, 1F) == 2048
                && EmpMathSC.siphonRate(8192, 8192, 8192, 0, 1F) == 8192 && EmpMathSC.siphonRate(8192, 2048, 32768, 0, 1F) == 2048
                && EmpMathSC.siphonRate(2048, 8192, 8192, 1, 1F) == 1638 && EmpMathSC.siphonRate(2048, 8192, 8192, 3, 1F) == 1228
                && EmpMathSC.siphonRate(2048, 8192, 8192, 0, 0.5F) == 1024;
        boolean loss = EmpMathSC.siphonLossPct(false, false, 25) == 25 && EmpMathSC.siphonLossPct(true, false, 25) == 10
                && EmpMathSC.siphonLossPct(false, true, 25) == 0 && EmpMathSC.siphonLossPct(true, true, 25) == 0
                && EmpMathSC.siphonLossPct(true, false, 50) == 20 && EmpMathSC.siphonLossPct(false, false, 150) == 100
                && EmpMathSC.delivered(1000, 25) == 750 && EmpMathSC.delivered(1000, 0) == 1000 && EmpMathSC.delivered(-5, 25) == 0
                && EmpMathSC.delivered(Integer.MAX_VALUE, 10) == (int) (Integer.MAX_VALUE * 90L / 100);
        check(rate && loss, "siphon: min(gun EV 2048 / Wide IV 8192, receiver, target) x chip -20/40% x config; loss 25%, Efficient 10%, "
                + "suppressed 0; delivered without overflow");
    }

    /** §5: the gun's heat 10 / 30 / 11 a second, Cooling x0.7, the 2 s warning, the cool-down time. */
    private static void gunHeat() {
        boolean heat = EmpMathSC.gunHeat(EmpMode.POINT, false) == 10 && EmpMathSC.gunHeat(EmpMode.PULSE, false) == 30
                && EmpMathSC.gunHeat(EmpMode.SIPHON, false) == 11 && EmpMathSC.gunHeat(EmpMode.POINT, true) == 7
                && EmpMathSC.gunHeat(EmpMode.PULSE, true) == 21 && EmpMathSC.gunHeat(EmpMode.SIPHON, true) == 8;
        boolean warn = !EmpMathSC.overheatWarn(77, false) && EmpMathSC.overheatWarn(78, false) && !EmpMathSC.overheatWarn(100, false)
                && EmpMathSC.overheatWarn(84, true) && !EmpMathSC.overheatWarn(83, true);
        boolean cool = EmpMathSC.coolSeconds(100) == 13 && EmpMathSC.coolSeconds(0) == 0 && EmpMathSC.coolSeconds(8) == 1
                && EmpMathSC.coolSeconds(9) == 2;
        int stream = 0, seconds = 0;
        while (stream < EmpMathSC.GUN_HEAT_MAX && seconds < 60) {
            stream += EmpMathSC.gunHeat(EmpMode.SIPHON, false);
            seconds++;
        }
        check(heat && warn && cool && seconds >= 8 && seconds <= 10, "gun heat: Point 10, Pulse 30, Siphon 11/s (Cooling x0.7), warned 2 s ahead, "
                + "cools 100 in 13 s; a stream overheats it in " + seconds + " s (8-10)");
    }

    // ------------------------------------------------------------------ ItemEmpGunSC

    private static void gunNbt() {
        ItemStack g = new ItemStack(ModItems.empGun);
        boolean charge = ItemEmpGunSC.capacityOf(g) == 600000 && ItemEmpGunSC.chargeOf(g) == 0;
        ItemEmpGunSC.setCharge(g, 10000000);
        charge &= ItemEmpGunSC.chargeOf(g) == 600000 && ItemEmpGunSC.charge(g, 100) == 0;
        ItemEmpGunSC.setModule(g, EmpModule.CAPACITY, true);
        charge &= ItemEmpGunSC.capacityOf(g) == 1200000 && ItemEmpGunSC.charge(g, 2000000) == 600000 && ItemEmpGunSC.chargeOf(g) == 1200000;
        ItemEmpGunSC.setModule(g, EmpModule.CAPACITY, false);
        charge &= ItemEmpGunSC.capacityOf(g) == 600000 && ItemEmpGunSC.chargeOf(g) == 600000;                // the Capacity taken out: clamped
        charge &= ItemEmpGunSC.discharge(g, 1000) == 1000 && ItemEmpGunSC.chargeOf(g) == 599000 && ItemEmpGunSC.discharge(g, 10000000) == 599000;
        ItemEmpGunSC.setModule(g, EmpModule.FOCUS, true);
        ItemEmpGunSC.setModule(g, EmpModule.WIDE, true);
        boolean mods = ItemEmpGunSC.hasModule(g, EmpModule.FOCUS) && ItemEmpGunSC.hasModule(g, EmpModule.WIDE)
                && !ItemEmpGunSC.hasModule(g, EmpModule.COOLING) && !ItemEmpGunSC.hasModule(g, EmpModule.CAPACITY);
        boolean bits = true;
        for (EmpModule m : EmpModule.values()) {
            bits &= m.bit() == 1 << m.ordinal() && EmpModule.of(m.ordinal()) == m;
        }
        bits &= EmpModule.of(-1) == null && EmpModule.of(EmpModule.values().length) == null && EmpMode.of(99) == EmpMode.POINT;
        ItemStack m = new ItemStack(ModItems.empGun, 1, ItemEmpGunSC.META_UPGRADED);
        boolean upgraded = ItemEmpGunSC.upgraded(m) && !ItemEmpGunSC.upgraded(g) && m.getUnlocalizedName().endsWith("empGunM")
                && g.getUnlocalizedName().endsWith("empGun");
        ItemEmpGunSC.setLastPulseAt(g, 1000);
        boolean cooldown = ItemEmpGunSC.pulseCooldown(g, 1100) == 300 && ItemEmpGunSC.pulseCooldown(g, 1400) == 0
                && ItemEmpGunSC.pulseCooldown(g, 999) == 0 && ItemEmpGunSC.pulseCooldown(new ItemStack(ModItems.empGun), 5) == 0;
        ItemEmpGunSC.setHeat(g, 150, 10);
        boolean hot = ItemEmpGunSC.heatOf(g) == 100 && ItemEmpGunSC.overheated(g) && ItemEmpGunSC.lastHeatAt(g) == 10;
        ItemEmpGunSC.setHeat(g, 50, 20);
        hot &= ItemEmpGunSC.overheated(g) && ItemEmpGunSC.lastHeatAt(g) == 10;                              // cooling doesn't move the heat time
        ItemEmpGunSC.setHeat(g, 0, 30);
        hot &= !ItemEmpGunSC.overheated(g);
        boolean settings = ItemEmpGunSC.zoneOf(g) == EmpMathSC.ZONE_AUTO && ItemEmpGunSC.warnOn(g) && ItemEmpGunSC.limitOf(g) == 0;
        ItemEmpGunSC.setZone(g, EmpMathSC.ZONE_LEGS);
        ItemEmpGunSC.setZone(g, 7);
        ItemEmpGunSC.setLimit(g, 150);
        ItemEmpGunSC.setWarn(g, false);
        settings &= ItemEmpGunSC.zoneOf(g) == EmpMathSC.ZONE_LEGS && ItemEmpGunSC.limitOf(g) == 100 && !ItemEmpGunSC.warnOn(g);
        ItemEmpGunSC.setWarn(g, true);
        settings &= ItemEmpGunSC.warnOn(g) && !g.getTagCompound().hasKey(ItemEmpGunSC.NO_WARN);
        check(charge && mods && bits && upgraded && cooldown && hot && settings, "gun NBT: charge clamped to 600 000 / 1.2M with Capacity (cut back when "
                + "it's removed), modules as bits, ПЭ-1М = meta 1 (empGunM), Pulse cooldown, overheat lock until 0, zone / limit / warning");
    }

    private static void modesAndReceivers() {
        ItemStack g = new ItemStack(ModItems.empGun);
        boolean modes = ItemEmpGunSC.modeOf(g) == EmpMode.POINT && ItemEmpGunSC.step(g, EmpMode.POINT, 1) == EmpMode.PULSE
                && ItemEmpGunSC.step(g, EmpMode.POINT, -1) == EmpMode.SIPHON;
        modes &= ItemEmpGunSC.setModeEnabled(g, EmpMode.PULSE, false) && !ItemEmpGunSC.modeEnabled(g, EmpMode.PULSE)
                && ItemEmpGunSC.step(g, EmpMode.POINT, 1) == EmpMode.SIPHON && ItemEmpGunSC.step(g, EmpMode.SIPHON, 1) == EmpMode.POINT
                && ItemEmpGunSC.step(g, EmpMode.SIPHON, -1) == EmpMode.POINT;
        ItemEmpGunSC.setMode(g, EmpMode.SIPHON);
        modes &= ItemEmpGunSC.setModeEnabled(g, EmpMode.SIPHON, false) && ItemEmpGunSC.modeOf(g) == EmpMode.POINT;   // the current mode moves on
        modes &= !ItemEmpGunSC.setModeEnabled(g, EmpMode.POINT, false) && ItemEmpGunSC.modeEnabled(g, EmpMode.POINT)  // never all three off
                && ItemEmpGunSC.step(g, EmpMode.POINT, 1) == EmpMode.POINT;
        modes &= !ItemEmpGunSC.setModeEnabled(g, EmpMode.POINT, true) && ItemEmpGunSC.setModeEnabled(g, EmpMode.PULSE, true);

        int[] def = ItemEmpGunSC.receiverOrder(new ItemStack(ModItems.empGun));
        boolean recv = Arrays.equals(def, new int[]{EmpMathSC.RECV_BATTERY, EmpMathSC.RECV_ARMOR, EmpMathSC.RECV_GUN})
                && ItemEmpGunSC.unpackOrder(0) == null && ItemEmpGunSC.unpackOrder(ItemEmpGunSC.packOrder(new int[]{0, 0, 1})) == null
                && ItemEmpGunSC.unpackOrder(3 | (0 << 2) | (1 << 4)) == null;
        int[][] perms = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] p : perms) {
            recv &= Arrays.equals(ItemEmpGunSC.unpackOrder(ItemEmpGunSC.packOrder(p)), p);
        }
        ItemEmpGunSC.setReceiverOrder(g, ItemEmpGunSC.packOrder(new int[]{2, 0, 1}));
        ItemEmpGunSC.setReceiverOrder(g, 63);                                         // not a permutation: ignored
        recv &= Arrays.equals(ItemEmpGunSC.receiverOrder(g), new int[]{2, 0, 1});
        ItemEmpGunSC.setReceiverEnabled(g, EmpMathSC.RECV_ARMOR, false);
        recv &= !ItemEmpGunSC.receiverEnabled(g, EmpMathSC.RECV_ARMOR) && ItemEmpGunSC.receiverEnabled(g, EmpMathSC.RECV_GUN);
        ItemEmpGunSC.setReceiverEnabled(g, EmpMathSC.RECV_ARMOR, true);
        recv &= ItemEmpGunSC.receiverEnabled(g, EmpMathSC.RECV_ARMOR);
        check(modes && recv, "modes: the wheel skips switched-off modes, the current one moves on, the last can't be switched off; "
                + "receiver order packs / unpacks all 6 orders, rejects the rest, switches per receiver");
    }

    // ------------------------------------------------------------------ EmpStateSC

    private static void state() {
        EmpStateSC s = new EmpStateSC();
        boolean empty = s.idle(1000) && !s.anySuppressed(0) && s.boilMulAt(0) == 1;
        s.until[0] = 100;
        s.immuneUntil[0] = 500;
        s.until[3] = 80;
        s.immuneUntil[3] = 480;
        s.boilMul = 3;
        s.boilUntil = 300;
        s.streamUntil = 120;
        s.drainBy = 42;
        s.lastHeatHit = 60;
        s.aimedUntil = 90;
        s.aimedBy = 7;
        s.rebootReadyAt = 1260;
        EmpStateSC r = EmpStateSC.read(s.write());
        boolean io = Arrays.equals(r.until, s.until) && Arrays.equals(r.immuneUntil, s.immuneUntil) && r.boilMul == 3 && r.boilUntil == 300
                && r.streamUntil == 120 && r.drainBy == 42 && r.lastHeatHit == 60 && r.aimedUntil == 90 && r.aimedBy == 7
                && r.rebootReadyAt == 1260 && EmpStateSC.read((NBTTagCompound) null).idle(0);
        boolean phases = s.suppressed(0, 50) && !s.immune(0, 50)                // suppressed: not immune yet
                && !s.suppressed(0, 100) && s.immune(0, 100) && s.immune(0, 499) && !s.immune(0, 500)
                && s.suppressed(3, 79) && s.immune(3, 80) && !s.suppressed(1, 50) && !s.immune(1, 50) && !s.suppressed(5, 0)
                && s.anySuppressed(99) && !s.anySuppressed(100) && s.anyImmune(100) && !s.anyImmune(50)
                && s.boilMulAt(299) == 3 && s.boilMulAt(300) == 1 && s.streaming(119) && !s.streaming(120)
                && !s.idle(600) && s.idle(1300);
        r.until[0] = 999;
        boolean copy = s.copy().until[0] == 100 && s.until[0] == 100;
        check(empty && io && phases && copy, "state: write / read keeps every field, immunity only after the suppression ends, boil and stream "
                + "end on time, idle when nothing is left");
    }

    // ------------------------------------------------------------------ network

    /** EmpNetSC's discriminators 7..10 on ArmorNetSC.CHANNEL belong to EmpNetSC's messages alone. */
    private static void network() {
        String clash = "";
        boolean ok;
        try {
            Object codec = field(EmpNetSC.channel(), "packetCodec");
            Object types = field(codec, "types");                              // class -> discriminator
            Object[] classes = (Object[]) types.getClass().getMethod("keys").invoke(types);
            java.lang.reflect.Method get = types.getClass().getMethod("get", Object.class);
            int found = 0;
            for (Object c : classes) {
                int d = ((Byte) get.invoke(types, c)) & 0xFF;
                if (d >= EmpNetSC.DISC_ACTION && d <= EmpNetSC.DISC_FX) {
                    if (((Class<?>) c).getName().startsWith("com.sc.emp.EmpNetSC$")) {
                        found++;
                    } else {
                        clash += " " + d + "=" + ((Class<?>) c).getName();
                    }
                }
            }
            ok = found == 4 && clash.isEmpty();
            clash += " (" + found + " of 4 found)";
        } catch (Exception e) {
            ok = false;
            clash = " " + e;
        }
        check(ok, "network: discriminators " + EmpNetSC.DISC_ACTION + ".." + EmpNetSC.DISC_FX + " on the armour channel are ПЭ-1's only" + clash);
    }

    private static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ------------------------------------------------------------------ texts

    /** The keys of docs/plan-pe1-tech.md §8 (and the handbook's / tooltips' own) every package uses. */
    private static final String[] KEYS = {"item.siliconage.empGun.name", "item.siliconage.empGunM.name", "sc.emp.nocharge", "sc.emp.overheat",
            "sc.emp.overheat.warn", "sc.emp.cooldown", "sc.emp.modeoff", "sc.emp.noreceiver", "sc.emp.cancelled", "sc.emp.absorbed",
            "sc.emp.reflected", "sc.emp.immune", "sc.emp.hit", "sc.emp.noarmour", "sc.emp.siphon.stop.released", "sc.emp.siphon.stop.range",
            "sc.emp.siphon.stop.cover", "sc.emp.siphon.stop.full", "sc.emp.siphon.stop.limit", "sc.emp.siphon.stop.empty",
            "sc.emp.siphon.stop.overheat", "sc.emp.siphon.stop.screen", "sc.emp.siphon.stop.refused", "sc.emp.module.installed",
            "sc.emp.module.removed", "sc.emp.module.already", "sc.emp.module.missing", "sc.emp.module.full", "sc.emp.wheel",
            "sc.emp.warn.flightcut", "sc.emp.suppressed.you", "sc.emp.reboot.done", "sc.emp.reboot.cooldown", "sc.emp.reboot.noargon",
            "sc.emp.reboot.nocharge", "sc.emp.reboot.nothing", "sc.empgui.tab", "sc.empgui.charge", "sc.empgui.heat", "sc.empgui.cool",
            "sc.empgui.mode", "sc.empgui.price", "sc.empgui.receiver", "sc.empgui.noreceiver", "sc.empgui.modes", "sc.empgui.settings",
            "sc.empgui.zone", "sc.empgui.zone.0", "sc.empgui.zone.1", "sc.empgui.zone.2", "sc.empgui.zone.3", "sc.empgui.recv.0",
            "sc.empgui.recv.1", "sc.empgui.recv.2", "sc.empgui.limit", "sc.empgui.limit.full", "sc.empgui.limit.pct", "sc.empgui.warn",
            "sc.empgui.modules", "sc.empgui.module.install", "sc.empgui.module.remove", "sc.empgui.module.none", "sc.empgui.target",
            "sc.empgui.target.none", "sc.empgui.target.suit", "sc.empgui.target.immune", "sc.empgui.target.charge", "sc.empgui.target.refuse",
            "sc.empgui.tip.point", "sc.empgui.tip.pulse", "sc.empgui.tip.siphon", "sc.armorgui.emp", "sc.armorgui.emp.suppressed",
            "sc.armorgui.emp.immune", "sc.armorgui.emp.normal", "sc.armorgui.emp.reboot", "sc.armorgui.emp.reboot.cd", "sc.armorgui.emp.mark",
            "key.sc.emp.point", "key.sc.emp.pulse", "key.sc.emp.siphon", "key.sc.emp.cancel", "key.sc.emp.reboot", "sc.emp.hud.charge",
            "sc.emp.hud.noreceiver", "sc.emp.hud.overheat", "sc.emp.hud.suppressed", "sc.emp.hud.boil", "sc.emp.hud.aimed", "sc.emp.hud.drain",
            "sc.emp.hud.target", "sc.emp.hud.received", "sc.tooltip.emp.summary", "sc.tooltip.emp.state", "sc.tooltip.emp.statehot",
            "sc.tooltip.emp.modules", "sc.tooltip.emp.nomodules", "sc.tooltip.emp.modes", "sc.tooltip.emp.line.point", "sc.tooltip.emp.line.pulse",
            "sc.tooltip.emp.line.siphon", "sc.tooltip.emp.off", "sc.tooltip.emp.table", "sc.tooltip.emp.tablerow", "sc.tooltip.emp.immunity",
            "sc.tooltip.emp.upgraded", "sc.tooltip.emp.howto", "sc.tooltip.emp.charge", "sc.tooltip.emp.recipe", "sc.tooltip.emp.upgrade",
            "sc.tooltip.emp.defense", "sc.tooltip.emp.module.capacity", "sc.tooltip.emp.module.focus", "sc.tooltip.emp.module.cooling",
            "sc.tooltip.emp.module.efficient", "sc.tooltip.emp.module.wide", "sc.tooltip.emp.module.howto", "sc.manual.emp.title",
            "sc.manual.emp.defense.title"};

    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        StringBuilder missing = new StringBuilder();
        java.util.List<String> keys = new java.util.ArrayList<String>(Arrays.asList(KEYS));
        for (EmpMode m : EmpMode.values()) {
            keys.add("sc.emp.mode." + m.key());
            keys.add("sc.emp.mode." + m.key() + ".desc");
        }
        for (EmpModule m : EmpModule.values()) {
            keys.add("item.siliconage.empModule." + m.key() + ".name");
            keys.add("sc.emp.module." + m.key() + ".desc");
            keys.add("sc.tooltip.emp.mod." + m.key());
        }
        for (int i = EmpNetSC.NO_PVP; i <= EmpNetSC.NO_PLAYERS; i++) {
            keys.add("sc.emp.refuse." + i);
        }
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        // every ПЭ-1 key in one file is in the other, with as many % arguments
        StringBuilder diff = new StringBuilder();
        for (Map<String, String> from : new Map[]{en, ru}) {
            Map<String, String> to = from == en ? ru : en;
            for (Map.Entry<String, String> e : from.entrySet()) {
                String k = e.getKey();
                if (!(k.startsWith("sc.emp.") || k.startsWith("sc.empgui.") || k.startsWith("sc.armorgui.emp") || k.startsWith("sc.tooltip.emp.")
                        || k.startsWith("sc.manual.emp.") || k.startsWith("key.sc.emp.") || k.startsWith("item.siliconage.emp"))) {
                    continue;
                }
                if (!to.containsKey(k)) {
                    diff.append(' ').append(from == en ? "only en_US:" : "only ru_RU:").append(k);
                } else if (from == en && args(e.getValue()) != args(to.get(k))) {
                    diff.append(" args differ:").append(k);
                }
            }
        }
        check(missing.length() == 0 && diff.length() == 0, "texts: " + keys.size() + " ПЭ-1 keys in en_US and ru_RU, the same keys and "
                + "arguments in both" + missing + diff);
    }

    /** The number of %s / %d arguments in a lang value (%% isn't one). */
    private static int args(String v) {
        int n = 0;
        for (int i = 0; i + 1 < v.length(); i++) {
            if (v.charAt(i) == '%') {
                char c = v.charAt(i + 1);
                if (c == '%') {
                    i++;
                } else if (c == 's' || c == 'd') {
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ recipes and the handbook

    /** Package A's recipes: ПЭ-1 and each module on the crafting table, ПЭ-1М in the Upgrade Station (HV). */
    private static void recipes() {
        boolean gun = false;
        boolean[] mods = new boolean[EmpModule.values().length];
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            ItemStack out = ((IRecipe) o).getRecipeOutput();
            if (out == null) {
                continue;
            }
            if (out.getItem() == ModItems.empGun && out.getItemDamage() == ItemEmpGunSC.META_BASIC) {
                gun = true;
            } else if (out.getItem() == ModItems.empModule && out.getItemDamage() >= 0 && out.getItemDamage() < mods.length) {
                mods[out.getItemDamage()] = true;
            }
        }
        boolean upgrade = false;
        for (MachineRecipe r : RecipeRegistry.recipesFor(MachineType.UPGRADE_STATION_HV)) {
            boolean in = false, outM = false;
            for (ItemStack s : r.inputs) {
                in |= s != null && s.getItem() == ModItems.empGun && s.getItemDamage() == ItemEmpGunSC.META_BASIC;
            }
            for (ItemStack s : r.outputs) {
                outM |= s != null && s.getItem() == ModItems.empGun && s.getItemDamage() == ItemEmpGunSC.META_UPGRADED;
            }
            upgrade |= in && outM;
        }
        StringBuilder noMod = new StringBuilder();
        for (EmpModule m : EmpModule.values()) {
            if (!mods[m.ordinal()]) {
                noMod.append(' ').append(m.key());
            }
        }
        check(gun && noMod.length() == 0 && upgrade, "recipes: ПЭ-1 " + gun + ", every module" + (noMod.length() == 0 ? "" : " - missing" + noMod)
                + ", ПЭ-1 -> ПЭ-1М in the Upgrade Station (HV) " + upgrade);
    }

    /** The two articles build, link each other, and G on the gun / ПЭ-1М / a module opens «ПЭ-1». */
    private static void handbook() {
        com.sc.manual.BookEntry gun = com.sc.manual.BookContent.byId("emp"), def = com.sc.manual.BookContent.byId("empdefense");
        com.sc.manual.BookEntry byGun = com.sc.manual.BookContent.entryFor(new ItemStack(ModItems.empGun));
        com.sc.manual.BookEntry byM = com.sc.manual.BookContent.entryFor(new ItemStack(ModItems.empGun, 1, ItemEmpGunSC.META_UPGRADED));
        com.sc.manual.BookEntry byModule = com.sc.manual.BookContent.entryFor(new ItemStack(ModItems.empModule, 1, EmpModule.WIDE.ordinal()));
        boolean ok = gun != null && def != null && gun.els.size() > 20 && def.els.size() > 6 && gun.chapter == com.sc.manual.BookChapter.ARMOR
                && byGun == gun && byM == gun && byModule == gun;
        boolean table = false;
        if (gun != null) {
            for (com.sc.manual.BookEl el : gun.els) {
                table |= el.kind == com.sc.manual.BookEl.Kind.TABLE && el.rows.size() == ArmorSuit.values().length;
            }
        }
        com.sc.manual.BookContent.invalidate();
        check(ok && table, "handbook: «ПЭ-1» (" + (gun == null ? "missing" : gun.els.size() + " elements") + ", the suppression table by suit "
                + table + ") and «Защита от ЭМИ» (" + (def == null ? "missing" : def.els.size() + " elements") + "), G on the gun / ПЭ-1М / a module");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4F;
    }
}
