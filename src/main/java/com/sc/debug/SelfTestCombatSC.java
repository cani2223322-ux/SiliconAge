package com.sc.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.sc.block.BlockRuinSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateNetSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpRulesSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.manual.BookChapter;
import com.sc.manual.BookContent;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.BookWorldSC;
import com.sc.mob.BreakdownDamageSC;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityJammerSC;
import com.sc.mob.EntityMimicSC;
import com.sc.mob.EntityNaniteSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.EntitySentryDroneSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.ICensusSC;
import com.sc.mob.IDisguisedSC;
import com.sc.mob.IExecuteImmuneSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.ItemTrophySC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobCombatSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.mob.TrophyTableSC;
import com.sc.radiation.RadiationSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.settle.ISettlementGuardSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.SingularSenseData;
import com.sc.util.WeaponType;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * PACKAGE D (docs/plan-substrate-tech-f3.md §11.1): Ф3's pure logic in the self-test (-Dsc.selftest, preInit - no world,
 * no dimension; items, blocks, tile ids exist). Called from SelfTestSC.run after SelfTestSettlementSC. One line each,
 * "[SC-TEST] PASS / FAIL combat: ...", every section on its own (a package's unfinished body fails alone): the config
 * (mobsUseEmp a rule), «Пробой» (BreakdownDamageSC, both types bypass the armour), the elite table (EliteModSC: codes,
 * chances by distance, pick never repeats what the mob has), the census and caps (SpawnRulesSC), the mobs' numbers
 * (HP, reserves, damage caps, the telegraph times), EmpMobGunSC's pure parts, the analyzer's packing (MobAnalysisSC),
 * TrophyRestoreSC (refusal, tier, restored = a fresh real stack), the trophies' SHOWN, the loot kinds' order, NpcKindSC's
 * Ф3 kinds (respawn 20 min), SettlementSizeSC.captain, the Ф3 roster and workshops (SettlementLayoutSC), the Ф3 deals
 * (TradeTableSC: ПО-7 still holds - services aside), the ruins' layout (RuinLayoutSC), the texts and the handbook.
 * <p>
 * It is built on the core alone: what packages A / M / X / S / T add beyond the core's fixed signatures (SpawnRulesSC's
 * caps, the mobs' numbers, MobAnalysisSC.pack, RosterSC.upgrade, TradeOfferSC's matchers) is asked for by name - a method
 * not there yet fails its section with what is missing; a number another package keeps under another name is listed
 * (INFO), not failed. The mobs are made without a world (World null - as EntityList would with one): a constructor that
 * needs one is listed and its checks are the world test's (WorldTestCombatSC).
 */
final class SelfTestCombatSC {

    /** The eight Ф3 WORLD articles (SelfTestSubstrateSC counts the chapter: Ф1's six, Ф2's six and these). */
    static final String[] ARTICLES = BookWorldSC.F3_ARTICLES;
    private static final String[] SECTIONS = {"config", "breakdown", "elite table", "spawn rules", "mobs", "mob gun", "mob refusal", "analyzer",
            "restoring", "trophies shown", "loot kinds", "kinds and sizes", "roster", "roster upgrade", "trade", "ruins layout", "texts", "handbook"};
    /** Ф1's six trophies and Ф3's thirteen (ПТ-14): the Quantum and Exo pieces, both blades, the Pulse Emitter, the Plasma Rifle, the ПЭ-1. */
    private static final int[] SHOWN_F3 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 16, 17, 18, 20, 21, 22, 23};
    /** Ф4 (ПТ-16, docs/plan-substrate-tech-f4.md §9.1) appends the Singular pieces and the Singular Blade. */
    private static final int[] SHOWN_F4 = {12, 13, 14, 15, 19};
    /** Ф5 (ПТ-26, docs/plan-substrate-tech-f5.md §5.7) appends the Breakdown Warden's ПЭ-1М: SHOWN is 25 now. */
    private static final int[] SHOWN_F5 = {TrophyTableSC.META_PE1M};
    /** The core's mob classes by pool id 4..11 (MobEntitiesSC). */
    private static final Class<?>[] MOBS = {EntityTrooperSC.class, EntityJammerSC.class, EntityHollowShellSC.class, EntityRepairDroneSC.class,
            EntityExecutionerSC.class, EntityNaniteSC.class, EntitySentryDroneSC.class, EntityMimicSC.class};
    private static final double[] MOB_HP = {40, 36, 50, 8, 70, 2, 16, 40};
    /** Key prefixes of Ф3's texts: each in both languages with as many arguments, no Cyrillic and no «» in English. */
    private static final String[] PREFIXES = {"sc.elite.", "sc.manual.world.mobemp", "sc.manual.world.renegade", "sc.manual.world.trooper",
            "sc.manual.world.jammer", "sc.manual.world.shell", "sc.manual.world.drone", "sc.manual.world.executioner", "sc.manual.world.noise",
            "sc.manual.world.swarm", "sc.manual.world.sentry", "sc.manual.world.mimic", "sc.manual.world.elite", "sc.manual.world.ruin",
            "sc.manual.world.restore", "sc.tooltip.ruin.", "tile.siliconage.ruin", "tile.siliconage.showcase2", "sc.nei.obtain.ruin",
            "sc.nei.obtain.trophy", "death.attack.scBreakdown", "sc.emp.hit.mob.screen", "sc.emp.hit.reactive", "sc.emp.siphon.stop.mobscreen"};
    private static final String TROOPER = "com.sc.mob.EntityTrooperSC", JAMMER = "com.sc.mob.EntityJammerSC", SHELL = "com.sc.mob.EntityHollowShellSC",
            EXECUTIONER = "com.sc.mob.EntityExecutionerSC", MIMIC = "com.sc.mob.EntityMimicSC", WARDEN = "com.sc.settle.npc.EntityWardenSC";

    private SelfTestCombatSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "combat: " + what);
    }

    static void run() {
        for (int i = 0; i < SECTIONS.length; i++) {
            try {
                section(i);
            } catch (Throwable t) {
                check(false, SECTIONS[i] + " - exception " + t);
                t.printStackTrace();
            }
        }
    }

    private static void section(int i) throws Exception {
        switch (i) {
            case 0: config(); break;
            case 1: breakdown(); break;
            case 2: eliteTable(); break;
            case 3: spawnRules(); break;
            case 4: mobs(); break;
            case 5: mobGun(); break;
            case 6: mobRefusal(); break;
            case 7: analyzer(); break;
            case 8: restoring(); break;
            case 9: trophiesShown(); break;
            case 10: lootKinds(); break;
            case 11: kindsAndSizes(); break;
            case 12: roster(); break;
            case 13: rosterUpgrade(); break;
            case 14: trade(); break;
            case 15: ruinsLayout(); break;
            case 16: texts(); break;
            default: handbook(); break;
        }
    }

    // ------------------------------------------------------------------ 1. the config (§2.1, ПБ-18)

    private static void config() {
        List<Field> rules = SubstrateNetSC.ruleFields();
        boolean rule = false;
        for (Field f : rules) {
            rule |= "mobsUseEmp".equals(f.getName()) && f.getType() == boolean.class;
        }
        // Ф4 appended empStorms and raids (docs/plan-substrate-tech-f4.md §2.1), Ф5 requireSignal and caravans (docs/plan-substrate-tech-f5.md §2.1):
        // 14 rules now (SelfTestHazardSC / SelfTestFinaleSC check the new ones)
        check(rule && rules.size() == 14, "config (§2.1, ПБ-18): dimension.mobsUseEmp is a rule the server sends (now " + SubstrateConfigSC.mobsUseEmp
                + ", true by default) " + rule + ", " + rules.size() + " rules (14 with Ф4's and Ф5's)");
    }

    // ------------------------------------------------------------------ 2. «Пробой» (ПБ-15, ПБ-16)

    private static void breakdown() {
        Entity attacker = new EntityItem((World) null);
        DamageSource mob = BreakdownDamageSC.of(attacker), world = BreakdownDamageSC.WORLD;
        boolean types = BreakdownDamageSC.TYPE.equals(mob.damageType) && mob.getEntity() == attacker && "scBreakdown".equals(BreakdownDamageSC.TYPE)
                && BreakdownDamageSC.TYPE_WORLD.equals(world.damageType) && world.getEntity() == null && "scBreakdownWorld".equals(BreakdownDamageSC.TYPE_WORLD)
                && BreakdownDamageSC.of(null) == world;
        boolean bypass = mob.isUnblockable() && world.isUnblockable() && !mob.isExplosion() && !mob.isFireDamage() && !mob.isMagicDamage()
                && !world.isExplosion() && !world.isProjectile();
        boolean is = BreakdownDamageSC.is(mob) && BreakdownDamageSC.is(world) && !BreakdownDamageSC.is(DamageSource.generic)
                && !BreakdownDamageSC.is(DamageSource.causeMobDamage(null)) && !BreakdownDamageSC.is(RadiationSC.DAMAGE) && !BreakdownDamageSC.is(DamageSource.magic)
                && !BreakdownDamageSC.is(DamageSource.inFire) && !BreakdownDamageSC.is(DamageSource.outOfWorld) && !BreakdownDamageSC.is(null);
        check(types && bypass && is, "«Пробой» (BreakdownDamageSC): a mob's blow scBreakdown names its attacker, the world's scBreakdownWorld none, of(null) is"
                + " WORLD " + types + "; both bypass the armour, neither fire, magic nor an explosion " + bypass + "; is() only for these two - not generic,"
                + " causeMobDamage, radiation, magic, fire, the void " + is + " (exoStops needs a world: WorldTestCombatSC)");
    }

    // ------------------------------------------------------------------ 3. the elite table (ПС-20..ПС-22)

    private static void eliteTable() {
        String[] keys = {"commander", "charged", "shielded", "veteran", "repairer", "avenger"};
        EliteModSC[] all = EliteModSC.values();
        boolean codes = all.length == keys.length && EliteModSC.of(0) == null && EliteModSC.of(-1) == null && EliteModSC.of(all.length + 1) == null;
        for (int i = 0; codes && i < all.length; i++) {
            codes = keys[i].equals(all[i].key) && all[i].code() == i + 1 && EliteModSC.of(i + 1) == all[i] && ("sc.elite." + keys[i]).equals(all[i].langKey());
        }
        boolean chance = EliteModSC.chancePct(0, false) == 5 && EliteModSC.chancePct(1000, false) == 9 && EliteModSC.chancePct(5000, false) == 13
                && EliteModSC.chancePct(1e6, false) == 13 && EliteModSC.chancePct(-300, false) == 5 && EliteModSC.chancePct(0, true) == 12
                && EliteModSC.chancePct(249, false) == 5 && EliteModSC.chancePct(250, false) == 6;
        Random r = new Random(1414L);
        boolean noRepeat = true;
        Set<EliteModSC> plain = EnumSet.noneOf(EliteModSC.class);
        for (int i = 0; i < 3000; i++) {
            noRepeat &= EliteModSC.pick(r, true, false) != EliteModSC.SHIELDED && EliteModSC.pick(r, false, true) != EliteModSC.VETERAN;
            EliteModSC both = EliteModSC.pick(r, true, true);
            noRepeat &= both != EliteModSC.SHIELDED && both != EliteModSC.VETERAN;
            plain.add(EliteModSC.pick(r, false, false));
        }
        boolean numbers = EliteModSC.SCREEN_RECOVER_TICKS == 600 && EliteModSC.COMMANDER_RADIUS == 16 && near(EliteModSC.COMMANDER_BONUS, 0.2F)
                && near(EliteModSC.CHARGED_BONUS, 0.5F) && near(EliteModSC.REPAIR_UNITS, 1F) && EliteModSC.REPAIR_RADIUS == 8
                && EliteModSC.AVENGER_FUSE_TICKS == 30 && near(EliteModSC.AVENGER_RADIUS, 3F) && EliteModSC.ORDER_TICKS == 20;
        check(codes && chance && noRepeat && plain.size() == all.length && numbers, "elite table (EliteModSC, ПС-20..ПС-22): six modifiers, codes 1..6 there and"
                + " back, of(0) none " + codes + "; chancePct 0 -> 5, 1000 -> 9, 5000 -> 13 (+8 at most), the Rift 12 " + chance + "; pick never gives a"
                + " screened mob «Экранированный» nor one with helium «Ветеран» " + noRepeat + ", every modifier met " + plain.size() + "/" + all.length
                + "; the numbers (screen 30 s, Commander 16 / +20%, Charged +50%, Repairer 1 / 8, Avenger 1.5 s / 3) " + numbers);
    }

    // ------------------------------------------------------------------ 4. SpawnRulesSC (package M, §4.9, ПС-7, ПС-8)

    private static void spawnRules() {
        StringBuilder missing = new StringBuilder(), bad = new StringBuilder();
        expect(bad, missing, SpawnRulesSC.class, "QUANTUM_NEAR_MAX", 2);
        expect(bad, missing, SpawnRulesSC.class, "ELITE_RADIUS", 128);
        expect(bad, missing, SpawnRulesSC.class, "ELITE_MAX", 1);
        boolean caps = SpawnRulesSC.NEAR_MAX == 8 && SpawnRulesSC.NEAR_RADIUS == 64 && SpawnRulesSC.WORLD_MAX == 40;
        // Ф1's signatures, unchanged (SelfTestSubstrateSC's checks stand)
        boolean old = SpawnRulesSC.maySpawn(7, 0, 1, false) && !SpawnRulesSC.maySpawn(8, 0, 1, false) && SpawnRulesSC.maySpawn(0, 39, 1, false)
                && !SpawnRulesSC.maySpawn(0, 40, 1, false) && !SpawnRulesSC.maySpawn(0, 0, 1, true) && SpawnRulesSC.fitGroup(6, 0, 4, 1, false) == 2
                && SpawnRulesSC.fitGroup(8, 0, 2, 1, false) == 0;
        // the weights by pool id: a place where someone spawns; the Executioner only in the canyons (§4.9's table)
        int[] plateau = SpawnRulesSC.PLATEAU_WEIGHTS, canyon = SpawnRulesSC.CANYON_WEIGHTS;
        int[] node = intArray(SpawnRulesSC.class, "NODE_WEIGHTS");
        boolean sums = SpawnRulesSC.sum(plateau) > 0 && SpawnRulesSC.sum(canyon) > 0 && (node == null || SpawnRulesSC.sum(node) > 0);
        int ex = MobEntitiesSC.ID_EXECUTIONER;
        boolean executioner = canyon.length > ex && canyon[ex] > 0 && (plateau.length <= ex || plateau[ex] == 0) && (node == null || node.length <= ex || node[ex] == 0);
        if (node == null) {
            missing.append(" SpawnRulesSC.NODE_WEIGHTS");
        }
        // the overloads M adds (§4.9: the Quantum+ count and the elite's): named here, their sense is the world test's
        StringBuilder overloads = new StringBuilder();
        for (Method m : SpawnRulesSC.class.getMethods()) {
            if (("maySpawn".equals(m.getName()) || "fitGroup".equals(m.getName())) && m.getParameterTypes().length > 5) {
                overloads.append(' ').append(m.getName()).append('/').append(m.getParameterTypes().length);
            }
        }
        if (missing.length() > 0) {
            System.out.println("[SC-TEST] INFO combat: package M's spawn numbers not found by these names:" + missing);
        }
        check(bad.length() == 0 && caps && old && sums && executioner && overloads.length() > 0, "spawn rules (package M, ПС-7, ПС-8): 8 in 64 / 40 a"
                + " world " + caps + ", the Quantum+ 2 in 64 and 1 elite in 128 by name" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; Ф1's maySpawn /"
                + " fitGroup unchanged " + old + "; every place's weights sum > 0 " + sums + "; the Executioner (pool " + ex + ") only in the canyons "
                + executioner + "; the census overloads" + (overloads.length() == 0 ? " - NONE YET" : overloads));
    }

    // ------------------------------------------------------------------ 5. the mobs (packages M, X; §4.2..§4.8, ПС-10)

    private static void mobs() {
        StringBuilder bad = new StringBuilder(), missing = new StringBuilder();
        // the core's fixed numbers
        double[] hp = {EntityTrooperSC.HP, EntityJammerSC.HP, EntityHollowShellSC.HP, EntityRepairDroneSC.HP, EntityExecutionerSC.HP, EntityNaniteSC.HP,
                EntitySentryDroneSC.HP, EntityMimicSC.HP};
        boolean hps = Arrays.equals(hp, MOB_HP);
        // Ф4 appended its ten (12..21, docs/plan-substrate-tech-f4.md §2.2), Ф5 its four (22..25, docs/plan-substrate-tech-f5.md §2.2): the pool is
        // 26 now, Ф3's 4..11 unchanged
        boolean pool = MobEntitiesSC.NAMES.length == 26 && MobEntitiesSC.EGG_COLORS.length == 26 && "trooper".equals(MobEntitiesSC.nameOf(4))
                && "mimic".equals(MobEntitiesSC.nameOf(11)) && "breakdownWarden".equals(MobEntitiesSC.nameOf(22)) && MobEntitiesSC.nameOf(26) == null
                && MobEntitiesSC.ID_TROOPER == 4 && MobEntitiesSC.ID_MIMIC == 11;
        // the caps (ПС-10): MobCombatSC's tables, the mobs' own by name
        boolean caps = MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()] == 8 && MobCombatSC.BLADE[BladeType.EXO.ordinal()] == 10
                && MobCombatSC.BEAM[WeaponType.PLASMA_RIFLE.ordinal()] == 7 && MobCombatSC.BEAM[WeaponType.PULSE_EMITTER.ordinal()] == 3
                && MobCombatSC.BEAM[WeaponType.ION_CUTTER.ordinal()] == 4;
        expect(bad, missing, EXECUTIONER, "BLADE_DAMAGE", 11);
        expect(bad, missing, EXECUTIONER, "CLEAVE_DAMAGE", 6);
        expect(bad, missing, SHELL, "FIST_DAMAGE", 6);
        expect(bad, missing, MIMIC, "CHEW_DAMAGE", 6);
        expect(bad, missing, WARDEN, "HEAVY_BLOW", 5);
        expect(bad, missing, JAMMER, "BURST", 3);
        // the telegraph's times (ПС-9)
        expect(bad, missing, TROOPER, "DASH_CUE_TICKS", 10);
        expect(bad, missing, TROOPER, "AIM_TICKS", 30);
        expect(bad, missing, EXECUTIONER, "WINDUP_TICKS", 20);
        boolean gun = EmpMobGunSC.POINT_WARN_TICKS == 20 && EmpMobGunSC.PULSE_CHARGE_TICKS == 60;
        boolean acts = IMobActionSC.ACT_NONE == 0 && IMobActionSC.ACT_AIM == 1 && IMobActionSC.ACT_WINDUP == 2 && IMobActionSC.ACT_DASH == 3
                && IMobActionSC.ACT_EMP_POINT == 4 && IMobActionSC.ACT_EMP_PULSE == 5 && IMobActionSC.ACT_REBOOT == 6 && IMobActionSC.ACT_RECOVER == 7
                && IMobActionSC.ACT_DORMANT == 8 && IMobActionSC.ACT_ORDER == 9 && IMobActionSC.ACT_REPAIR == 10 && IMobActionSC.ACT_SEARCHLIGHT == 11;
        // the reserves (§4.2..§4.5): a full suit's reserve for the plan's damage, larger for more
        int quantum = fullPoints(ArmorSuit.QUANTUM), exo = fullPoints(ArmorSuit.EXO);
        float q40 = MobArmorMathSC.reserveFor(40, quantum), q70 = MobArmorMathSC.reserveFor(70, quantum), e70 = MobArmorMathSC.reserveFor(70, exo),
                e140 = MobArmorMathSC.reserveFor(140, exo);
        boolean reserves = q40 > 0F && q70 > q40 && e70 > 0F && e140 > e70 && e70 <= 70F;
        // the classes without a world: the markers, the census (ПС-7), «Казнь» (ПС-24)
        StringBuilder kinds = new StringBuilder(), noWorld = new StringBuilder();
        for (int i = 0; i < MOBS.length; i++) {
            Entity e = make(MOBS[i], noWorld);
            if (e == null) {
                continue;
            }
            String n = MOBS[i].getSimpleName();
            if (!(e instanceof EntityMob) || !(e instanceof IMob) || !(e instanceof ISubstrateNativeSC) || !(e instanceof ICensusSC) || !(e instanceof IMobActionSC)) {
                kinds.append(' ').append(n).append(":markers");
            }
            boolean armoured = e instanceof EntityArmoredMobSC;
            if (armoured != (i != 3 && i < 5) || armoured && !(e instanceof IEmpTargetSC)) {
                kinds.append(' ').append(n).append(":armour");
            }
            if ((i == 3 || i >= 5) && !(e instanceof IEmpReactiveSC)) {
                kinds.append(' ').append(n).append(":not IEmpReactiveSC");  // the drones, the swarm, the mimic answer a ПЭ-1 hit their own way (ПБ-20)
            }
            ICensusSC c = (ICensusSC) e;
            boolean counts = i != 3 && i != 7;          // the repair drone and the mimic take no place (a swarm's - by its first particle)
            if (i != 5 && c.censusCounts() != counts) {
                kinds.append(' ').append(n).append(":censusCounts ").append(c.censusCounts());
            }
            if (i == 4 && (!c.censusElite() || c.censusTier() != ICensusSC.TIER_QUANTUM || !((IExecuteImmuneSC) e).executeImmune())) {
                kinds.append(' ').append(n).append(":not an elite of its kind");
            }
            if (i == 0 && (c.censusElite() || ((IExecuteImmuneSC) e).executeImmune())) {
                kinds.append(' ').append(n).append(":an elite without a modifier");
            }
            if (i == 7 && !(e instanceof IDisguisedSC)) {
                kinds.append(' ').append(n).append(":not IDisguisedSC");
            }
        }
        if (missing.length() > 0) {
            System.out.println("[SC-TEST] INFO combat: the mobs' numbers not found by these names (packages M / X / S):" + missing);
        }
        if (noWorld.length() > 0) {
            System.out.println("[SC-TEST] INFO combat: made without a world - no (the world test checks them):" + noWorld);
        }
        check(hps && pool && caps && bad.length() == 0 && gun && acts && reserves && kinds.length() == 0, "mobs (packages M, X): HP 40 / 36 / 50 / 8 / 70 / 2 /"
                + " 16 / 40 " + hps + ", the pool 4..11 and its eggs (22 with Ф4's) " + pool + "; the caps (blades 8 / 10, the rifle 7, the emitter 3, the cutter 4) " + caps
                + ", the Executioner 11, the Cleave 6, a Shell's fist 6, chewing 6, the Captain's blow 5, a burst of 3, the telegraph 10 / 30 / 20 by name"
                + (bad.length() == 0 ? "" : " - BAD:" + bad) + ", ПЭ-1 20 / 60 " + gun + ", the telegraph's codes " + acts + "; reserves of a full Quantum ("
                + quantum + " pts) " + n(q40) + ".." + n(q70) + ", Exo (" + exo + ") " + n(e70) + ".." + n(e140) + " " + reserves
                + "; markers, the census (the drone and the mimic no place, the Executioner an elite), ПЭ-1 reactions" + (kinds.length() == 0 ? " ok" : " - BAD:" + kinds));
    }

    // ------------------------------------------------------------------ 6. EmpMobGunSC (package A, §3.2, ПБ-17, ПБ-18)

    private static void mobGun() {
        boolean worth = !EmpMobGunSC.pulseWorth(0) && !EmpMobGunSC.pulseWorth(1) && EmpMobGunSC.pulseWorth(2) && EmpMobGunSC.pulseWorth(5);
        Random r = new Random(14L);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < 2000; i++) {
            int d = EmpMobGunSC.pointDelay(r);
            lo = Math.min(lo, d);
            hi = Math.max(hi, d);
        }
        boolean delay = lo >= 160 && hi <= 300 && lo < 180 && hi > 280;
        boolean numbers = EmpMobGunSC.POINT_EVERY_MIN == 160 && EmpMobGunSC.POINT_EVERY_MAX == 300 && EmpMobGunSC.PULSE_GROUP_MIN == 2
                && EmpMobGunSC.PULSE_GROUP_RADIUS == 5D && EmpMobGunSC.RANGE == 32 && EmpMobGunSC.PULSE_HUM_RANGE == 16;
        boolean saved = SubstrateConfigSC.mobsUseEmp;
        boolean allowed;
        try {
            SubstrateConfigSC.mobsUseEmp = false;
            boolean off = !EmpMobGunSC.allowed();
            SubstrateConfigSC.mobsUseEmp = true;
            allowed = off && EmpMobGunSC.allowed() == (ConfigSC.empEnabled && ConfigSC.empAffectsPlayers);
        } finally {
            SubstrateConfigSC.mobsUseEmp = saved;
        }
        check(worth && delay && numbers && allowed, "mob gun (EmpMobGunSC, ПБ-17): a Pulse only at 2+ players " + worth + "; a Point every " + lo + ".." + hi
                + " ticks (160..300) " + delay + "; the numbers (8-15 s, 2 in 5, reach 32, hum 16) " + numbers + "; allowed() off without dimension.mobsUseEmp,"
                + " with it = weapons.pe1.enabled && affectsPlayers " + allowed);
    }

    // ------------------------------------------------------------------ 7. EmpRulesSC.refusal for a mob shooter (§3.1)

    private static void mobRefusal() {
        HostileStub hostile = new HostileStub(), other = new HostileStub();
        GuardStub guard = new GuardStub();
        CreatureStub animal = new CreatureStub();
        boolean saved = ConfigSC.empEnabled;
        boolean ok;
        String got;
        try {
            ConfigSC.empEnabled = true;
            int self = EmpRulesSC.refusal(hostile, hostile, true), natives = EmpRulesSC.refusal(hostile, other, true),
                    settler = EmpRulesSC.refusal(hostile, guard, true), beast = EmpRulesSC.refusal(hostile, animal, true),
                    guardAtHostile = EmpRulesSC.refusal(guard, hostile, true), guardAtBeast = EmpRulesSC.refusal(guard, animal, true);
            ConfigSC.empEnabled = false;
            int disabled = EmpRulesSC.refusal(hostile, animal, true);
            ok = self == EmpNetSC.NO_OWN && natives == EmpNetSC.NO_OWN && settler == EmpNetSC.NO_OWN && beast == EmpNetSC.OK && guardAtHostile == EmpNetSC.OK
                    && guardAtBeast == EmpNetSC.NO_OWN && disabled == EmpNetSC.NO_DISABLED;
            got = self + "/" + natives + "/" + settler + "/" + beast + "/" + guardAtHostile + "/" + guardAtBeast + "/" + disabled;
        } finally {
            ConfigSC.empEnabled = saved;
        }
        check(ok, "mob refusal (EmpRulesSC.refusal(Entity...), §3.1): a hostile never at itself, another native or a settlement's creature (NO_OWN), at"
                + " any other creature OK; a guard only at the hostiles; the gun off - NO_DISABLED (" + got + "; want 3/3/3/0/0/3/4; a player target -"
                + " WorldTestCombatSC)");
    }

    // ------------------------------------------------------------------ 8. the analyzer's packing (package A, §7.2, ПИ-2)

    private static void analyzer() throws Exception {
        boolean flags = SingularSenseData.Analysis.WEAK_EMP == 64 && SingularSenseData.Analysis.EMERGENCY == 128 && SingularSenseData.Analysis.ELITE == 256
                && SingularSenseData.Analysis.SHIELD == 512 && SingularSenseData.Analysis.DOWN == 1024 && SingularSenseData.Analysis.SCREEN == 2048
                && SingularSenseData.Analysis.EMP_REACTS == 4096 && SingularSenseData.Analysis.EMP_USELESS == 8192 && SingularSenseData.Analysis.SUIT_SHIFT == 16
                && SingularSenseData.Analysis.RESERVE_SHIFT == 19 && SingularSenseData.Analysis.RESERVE_UNKNOWN == 127;
        Class<?> c = Class.forName("com.sc.mob.MobAnalysisSC");
        Method pack = c.getMethod("pack", ArmorSuit.class, int.class), suitOf = c.getMethod("suitOf", int.class), reserveOf = c.getMethod("reserveOf", int.class);
        boolean round = true;
        StringBuilder bad = new StringBuilder();
        List<ArmorSuit> suits = new ArrayList<ArmorSuit>(Arrays.asList(ArmorSuit.values()));
        suits.add(null);
        for (ArmorSuit s : suits) {
            for (int pct : new int[]{0, 1, 37, 60, 100, -1}) {                // -1: unknown - A's pack stores RESERVE_UNKNOWN, reserveOf gives -1
                int f = (Integer) pack.invoke(null, s, pct);
                int stored = (f >>> SingularSenseData.Analysis.RESERVE_SHIFT) & 127;
                boolean here = suitOf.invoke(null, f) == s && ((Integer) reserveOf.invoke(null, f)).intValue() == pct
                        && stored == (pct < 0 ? SingularSenseData.Analysis.RESERVE_UNKNOWN : pct)
                        && (f & 0xFFFF) == 0;                                  // the weakness bits below stay free
                if (!here) {
                    bad.append(' ').append(s).append('/').append(pct).append("->").append(Integer.toHexString(f));
                }
                round &= here;
            }
        }
        int q60 = (Integer) pack.invoke(null, ArmorSuit.QUANTUM, 60);
        boolean bits = q60 == ((ArmorSuit.QUANTUM.ordinal() + 1) << 16 | 60 << 19);
        int plain = (Integer) c.getMethod("flags", EntityLivingBase.class).invoke(null, new CreatureStub());
        check(flags && round && bits && plain == 0, "analyzer (MobAnalysisSC, ПИ-2): the Analysis flags 64..8192, the suit in bits 16-18, the reserve's % in"
                + " 19-25 (127 unknown) " + flags + "; pack / suitOf / reserveOf there and back for every suit and none " + round + (bad.length() == 0 ? "" : bad)
                + ", Quantum 60% in its bits " + bits + "; a creature not of the Substrate - no flags " + (plain == 0));
    }

    // ------------------------------------------------------------------ 9. TrophyRestoreSC (package T, §6.6, ПТ-11, ПТ-12)

    private static void restoring() {
        StringBuilder bad = new StringBuilder();
        for (int m = -1; m <= TrophyTableSC.METAS; m++) {
            // Ф5 (ПТ-26, docs/plan-substrate-tech-f5.md §9.6): the ПЭ-1М (24) is known now - Singular-level, the Singular station's alone
            boolean known = m >= 0 && m < TrophyTableSC.METAS;
            boolean singular = known && (TrophyTableSC.level(m) == ArmorSuit.SINGULAR || m == TrophyTableSC.META_PE1M);
            int arm = TrophyRestoreSC.refusal(m, TrophyRestoreSC.WHERE_ARMOURER), st = TrophyRestoreSC.refusal(m, TrophyRestoreSC.WHERE_STATION),
                    sing = TrophyRestoreSC.refusal(m, TrophyRestoreSC.WHERE_SINGULAR_STATION);
            int wantOther = !known ? TrophyRestoreSC.R_UNKNOWN : singular ? TrophyRestoreSC.R_SINGULAR_ONLY : TrophyRestoreSC.OK;
            int wantSing = !known ? TrophyRestoreSC.R_UNKNOWN : TrophyRestoreSC.OK;
            if (arm != wantOther || st != wantOther || sing != wantSing) {
                bad.append(' ').append(m).append(':').append(arm).append('/').append(st).append('/').append(sing);
            }
        }
        StringBuilder tiers = new StringBuilder();
        for (int m = 0; m < TrophyTableSC.METAS; m++) {
            int want = m < TrophyTableSC.META_BLADE ? m / 4 : m < TrophyTableSC.META_WEAPON ? m - TrophyTableSC.META_BLADE
                    : m == TrophyTableSC.META_WEAPON ? ArmorSuit.NANO.ordinal() : m < TrophyTableSC.META_PE1 ? ArmorSuit.QUANTUM.ordinal()
                    : m == TrophyTableSC.META_PE1 ? ArmorSuit.EXO.ordinal() : ArmorSuit.SINGULAR.ordinal();
            if (TrophyRestoreSC.tier(m) != want) {
                tiers.append(' ').append(m).append(':').append(TrophyRestoreSC.tier(m)).append("!=").append(want);
            }
        }
        boolean unknownTier = TrophyRestoreSC.tier(-1) == -1 && TrophyRestoreSC.tier(TrophyTableSC.METAS) == -1;
        StringBuilder fresh = new StringBuilder();
        for (int m = 0; m < TrophyTableSC.METAS; m++) {
            ItemStack a = TrophyRestoreSC.restored(m), b = ItemTrophySC.realStack(m);
            if (a == null || b == null || a.getItem() != b.getItem() || a.getItemDamage() != b.getItemDamage() || a.hasTagCompound() || a.stackSize != 1) {
                fresh.append(' ').append(m);
            }
        }
        boolean consts = TrophyRestoreSC.STATION_EU == 10000 && TrophyRestoreSC.SINGULAR_STATION_EU == 1000000 && TrophyRestoreSC.CONFIRM_TICKS == 100;
        check(bad.length() == 0 && tiers.length() == 0 && unknownTier && fresh.length() == 0 && consts, "restoring (TrophyRestoreSC, ПТ-11, ПТ-12): a Singular"
                + " piece or blade and the ПЭ-1М (Ф5) only at the Singular station, unknown metas refused everywhere" + (bad.length() == 0 ? "" : " - BAD:" + bad)
                + "; tier() of all 25 (armour and blades by suit, the cutter Nano, the emitter and the rifle Quantum, ПЭ-1 Exo, ПЭ-1М Singular)"
                + (tiers.length() == 0 ? "" : " - BAD:" + tiers) + ", -1 for none " + unknownTier + "; restored() - a fresh real stack, no NBT"
                + (fresh.length() == 0 ? "" : " - NOT:" + fresh) + "; 10 000 / 1 000 000 EU, 5 s to confirm " + consts);
    }

    // ------------------------------------------------------------------ 10. the trophies shown (ПТ-14)

    private static void trophiesShown() {
        Set<Integer> want = new TreeSet<Integer>(), shown = new TreeSet<Integer>();
        for (int m : SHOWN_F3) {
            want.add(m);
        }
        for (int m : SHOWN_F4) {
            want.add(m);
        }
        for (int m : SHOWN_F5) {
            want.add(m);
        }
        for (int m : TrophyTableSC.SHOWN) {
            shown.add(m);
        }
        Set<Integer> tab = new TreeSet<Integer>();
        String subErr = null;
        Item trophy = MobItemsSC.trophy;
        try {
            List<ItemStack> sub = new ArrayList<ItemStack>();
            trophy.getSubItems(trophy, trophy.getCreativeTab(), sub);
            for (ItemStack s : sub) {
                tab.add(s.getItemDamage());
            }
        } catch (Throwable t) {
            subErr = t.toString();                                        // a dedicated server has no getSubItems
        }
        float pe1Chance = staticFloat(TrophyTableSC.class, "PE1");
        boolean pe1 = near(pe1Chance, 0.02F);
        boolean chances = near(TrophyTableSC.PIECE[1], 0.01F) && near(TrophyTableSC.PIECE[2], 0.005F) && near(TrophyTableSC.BLADE[1], 0.007F)
                && near(TrophyTableSC.BLADE[2], 0.003F) && near(TrophyTableSC.WEAPON[1], 0.01F) && near(TrophyTableSC.WEAPON[2], 0.006F);
        StringBuilder droppers = new StringBuilder();
        for (int m : SHOWN_F3) {
            if (BookWorldSC.trophyDroppers(m) == null || BookWorldSC.trophyChancePct(m) == null || BookWorldSC.restoreLine(m) == null) {
                droppers.append(' ').append(m);
            }
        }
        check(shown.equals(want) && SHOWN_F3.length + SHOWN_F4.length + SHOWN_F5.length == 25 && (subErr != null || tab.equals(want)) && pe1 && chances
                && droppers.length() == 0, "trophies shown (TrophyTableSC.SHOWN, ПТ-14, ПТ-16, ПТ-26): Ф1's 6 + Ф3's 13 + Ф4's 5 + Ф5's 1 = 25 - " + shown + " "
                + shown.equals(want) + "; the creative tab / NEI the same "
                + (subErr != null ? "(not checked: " + subErr + ")" : String.valueOf(tab.equals(want))) + "; TrophyTableSC.PE1 2% " + pe1 + ", the Quantum /"
                + " Exo chances " + chances + "; the handbook and NEI name who drops each, its chance and its restoring"
                + (droppers.length() == 0 ? "" : " - NOT:" + droppers));
    }

    // ------------------------------------------------------------------ 11. the loot kinds (§2.2, ПТ-13)

    private static void lootKinds() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        // Ф4 appended five after shieldCore (docs/plan-substrate-tech-f4.md §0): 12 kinds, the last naturalWafer
        String[] keys = {"shellFirmware", "nanoScrap", "stolenCargo", "quantumScrap", "exoScrap", "naniteDust", "shieldCore", "boiledLoop", "inductionThread",
                "plasmaCondensate", "singularScrap", "naturalWafer"};
        ItemCombatLootSC.LootKind[] all = ItemCombatLootSC.LootKind.values();
        boolean order = all.length == keys.length && Arrays.equals(BookWorldSC.LOOT_NAMES, keys) && BookWorldSC.LOOT_SHIELD_CORE == 6
                && BookWorldSC.LOOT_NATURAL_WAFER == all.length - 1;
        for (int i = 0; order && i < all.length; i++) {
            order = keys[i].equals(all[i].key) && ItemCombatLootSC.LootKind.of(i) == all[i];
        }
        StringBuilder bad = new StringBuilder();
        for (ItemCombatLootSC.LootKind k : all) {
            if (SelfTestCombatSC.class.getResource("/assets/siliconage/textures/items/loot_" + k.key + ".png") == null) {
                bad.append(' ').append(k.key).append(":no icon");
            }
            for (String key : new String[]{"item.siliconage.combatLoot." + k.key + ".name", "sc.tooltip.loot." + k.key, "sc.tooltip.loot." + k.key + ".details",
                    "sc.tooltip.loot." + k.key + ".howto"}) {
                if (!en.containsKey(key) || !ru.containsKey(key)) {
                    bad.append(' ').append(key);
                }
            }
            String line = BookWorldSC.lootLine(k.ordinal());
            if (line == null || line.contains("%s") || line.contains("%d") || line.startsWith("sc.") || line.startsWith("Format error")) {
                bad.append(' ').append(k.key).append(":handbook line");
            }
        }
        check(order && "naturalWafer".equals(all[all.length - 1].key) && bad.length() == 0, "loot kinds (ItemCombatLootSC.LootKind, ПТ-13, ПТ-18): 12 in"
                + " their order (Ф3's shieldCore the 7th), the last naturalWafer, BookWorldSC.LOOT_NAMES the same " + order + "; each with its icon, name, tooltip"
                + " and handbook line"
                + (bad.length() == 0 ? "" : " - NOT:" + bad));
    }

    // ------------------------------------------------------------------ 12. NpcKindSC, SettlementSizeSC (§2.3, §6.1)

    private static void kindsAndSizes() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        String[] keys = {"elder", "technologist", "metallurgist", "gasTech", "cartographer", "sentry", "seniorWarden", "powerEngineer", "armourer",
                "salvager", "captain", "commandant"};
        boolean[] guard = {false, false, false, false, false, true, true, false, false, false, true, true};
        NpcKindSC[] kinds = NpcKindSC.values();
        boolean order = kinds.length == keys.length;
        StringBuilder noLang = new StringBuilder();
        for (int i = 0; order && i < kinds.length; i++) {
            order = keys[i].equals(kinds[i].key) && kinds[i].guard == guard[i] && ("sc.settle.kind." + keys[i]).equals(kinds[i].langKey());
            if (!en.containsKey(kinds[i].langKey()) || !ru.containsKey(kinds[i].langKey())) {
                noLang.append(' ').append(kinds[i].key);
            }
        }
        boolean chiefs = NpcKindSC.CAPTAIN.respawnTicks() == 24000 && NpcKindSC.COMMANDANT.respawnTicks() == 24000 && NpcKindSC.CHIEF_RESPAWN_TICKS == 24000
                && NpcKindSC.CAPTAIN.chief() && NpcKindSC.COMMANDANT.chief() && !NpcKindSC.SENIOR_WARDEN.chief() && !NpcKindSC.ARMOURER.chief()
                && NpcKindSC.SENTRY.respawnTicks() == NpcKindSC.GUARD_RESPAWN_TICKS && NpcKindSC.ARMOURER.respawnTicks() == NpcKindSC.TUNER_RESPAWN_TICKS
                && NpcKindSC.of(11) == NpcKindSC.COMMANDANT && NpcKindSC.of(12) == NpcKindSC.ELDER;
        Random r = new Random(77L);
        int settlement = 0, n = 10000;
        boolean always = true, never = true;
        for (int i = 0; i < n; i++) {
            long seed = r.nextLong();
            always &= SettlementSizeSC.CITY.captain(seed);
            never &= !SettlementSizeSC.OUTPOST.captain(seed);
            settlement += SettlementSizeSC.SETTLEMENT.captain(seed) ? 1 : 0;
        }
        float share = settlement / (float) n;
        boolean sizes = SettlementSizeSC.OUTPOST.captainPct == 0 && SettlementSizeSC.SETTLEMENT.captainPct == 30 && SettlementSizeSC.CITY.captainPct == 100
                && SettlementSizeSC.CITY.commandant && !SettlementSizeSC.SETTLEMENT.commandant && !SettlementSizeSC.OUTPOST.commandant;
        check(order && noLang.length() == 0 && chiefs && always && never && share > 0.25F && share < 0.35F && sizes, "kinds and sizes (NpcKindSC, ПЖ-8,"
                + " ПГ-11, ПГ-12): 12 kinds, Ф2's order kept, the Power Engineer / Armourer / Salvager settlers, the Captain / Commandant guards " + order
                + (noLang.length() == 0 ? "" : " - NO LANG:" + noLang) + "; the chiefs back after 20 min, chief() theirs alone " + chiefs + "; a Captain in"
                + " every City " + always + ", no Outpost " + never + ", " + Math.round(share * 1000F) / 10F + "% of Settlements (30); the Commandant the"
                + " City's only " + sizes);
    }

    // ------------------------------------------------------------------ 13. the roster and the workshops (package S, §6.2, §6.3)

    private static void roster() {
        int[] made = new int[SettlementSizeSC.values().length];
        StringBuilder bad = new StringBuilder();
        int checked = 0, workshops = 0, sixes = 0;
        int showcase2 = staticInt(SettlementLayoutSC.class, "SHOWCASE2", 16);
        for (long seed : new long[]{0L, 1234567L, -98765L, 14141414L}) {
            for (int rx = -6; rx <= 6 && checked < 120; rx++) {
                for (int rz = -6; rz <= 6 && checked < 120; rz++) {
                    SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, rx, rz);
                    if (s == null) {
                        continue;
                    }
                    checked++;
                    made[s.size.ordinal()]++;
                    List<NpcKindSC> kinds = SettlementLayoutSC.roster(s);
                    String why = rosterV2Problem(s.size, s.seed, kinds);
                    if (why == null) {
                        why = SelfTestSettlementSC.planProblem(seed, s);       // the posts in the dome, the dock outside, the footprint (Ф2's check, v2's roster)
                    }
                    SettlementLayoutSC.Plan p = SettlementLayoutSC.plan(seed, s);
                    // every workshop profession of the roster has its lot, the Armourer none (his post is the square's station)
                    Set<Integer> lots = new HashSet<Integer>();
                    for (SettlementLayoutSC.Lot l : p.lots) {
                        if (l.kind == SettlementLayoutSC.LOT_WORKSHOP) {
                            lots.add(l.variant);
                        }
                    }
                    Set<Integer> want = new HashSet<Integer>();
                    for (NpcKindSC k : kinds) {
                        if (!k.guard && k != NpcKindSC.ELDER && k != NpcKindSC.ARMOURER) {
                            want.add(k.ordinal());
                        }
                    }
                    if (why == null && !lots.equals(want)) {
                        why = "workshops " + lots + " for the professions " + want;
                    }
                    workshops += lots.size();
                    sixes += s.size != SettlementSizeSC.OUTPOST && lots.size() == 6 ? 1 : 0;
                    if (why != null && bad.length() < 600) {
                        bad.append(" [").append(s.size).append(' ').append(s.x).append(',').append(s.z).append(": ").append(why).append(']');
                    }
                }
            }
        }
        // the showcase machines of the Ф3 workshops (§6.3): the Power Engineer's - the storage and the transformer, the Salvager's - the
        // warehouse; package S's workshopLine (both blocks' codes; workshopMachines keeps Ф2's sense - the first block's metas only)
        int[] engineer = workshopLine(NpcKindSC.POWER_ENGINEER), salvager = workshopLine(NpcKindSC.SALVAGER);
        boolean machines = contains(engineer, showcase2 + BlockShowcase2SC.ENERGY_STORAGE) && contains(engineer, showcase2 + BlockShowcase2SC.TRANSFORMER)
                && contains(salvager, showcase2 + BlockShowcase2SC.CRATE) && contains(salvager, showcase2 + BlockShowcase2SC.SCRAP_PILE)
                && contains(salvager, showcase2 + BlockShowcase2SC.RACK);
        boolean all = made[0] > 0 && made[1] > 0 && made[2] > 0;
        check(bad.length() == 0 && all && machines && sixes > 0, "roster (package S, ПЖ-9, §6.2, §6.3): " + checked + " sites (Outposts " + made[0]
                + ", Settlements " + made[1] + ", Cities " + made[2] + ") - the roster of version 2 (the Elder first, the seven trades, the guards, the Captain"
                + " by the seed, the City's Commandant), the posts inside the dome, the dock outside, 5 chunks at most, a workshop per profession ("
                + workshops + "; " + sixes + " with all six)" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; the Power Engineer's and the Salvager's"
                + " showcase machines of the second block " + machines);
    }

    /** A workshop's codes of both showcase blocks (SettlementLayoutSC.workshopLine, package S), else the first block's (workshopMachines). */
    private static int[] workshopLine(NpcKindSC kind) {
        try {
            return (int[]) SettlementLayoutSC.class.getMethod("workshopLine", NpcKindSC.class).invoke(null, kind);
        } catch (Throwable t) {
            return SettlementLayoutSC.workshopMachines(kind);
        }
    }

    /** §6.2 (record version 2): what breaks the roster rules for a site of `size` / `seed` (null: nothing). Independent of S's own check. */
    static String rosterV2Problem(SettlementSizeSC size, long seed, List<NpcKindSC> roster) {
        String why = SelfTestSettlementSC.rosterProblem(size, seed, roster);
        if (why != null) {
            return why;
        }
        int[] n = new int[NpcKindSC.values().length];
        for (NpcKindSC k : roster) {
            n[k.ordinal()]++;
        }
        NpcKindSC[] trades = {NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH, NpcKindSC.CARTOGRAPHER, NpcKindSC.POWER_ENGINEER,
                NpcKindSC.ARMOURER, NpcKindSC.SALVAGER};
        StringBuilder c = new StringBuilder();
        for (NpcKindSC k : trades) {
            c.append(' ').append(k.key).append(' ').append(n[k.ordinal()]);
        }
        if (size == SettlementSizeSC.OUTPOST) {
            int extra = n[NpcKindSC.TECHNOLOGIST.ordinal()] + n[NpcKindSC.METALLURGIST.ordinal()] + n[NpcKindSC.GAS_TECH.ordinal()] + n[NpcKindSC.SALVAGER.ordinal()];
            boolean distinct = n[NpcKindSC.TECHNOLOGIST.ordinal()] <= 1 && n[NpcKindSC.METALLURGIST.ordinal()] <= 1 && n[NpcKindSC.GAS_TECH.ordinal()] <= 1
                    && n[NpcKindSC.SALVAGER.ordinal()] <= 1;
            return n[NpcKindSC.CARTOGRAPHER.ordinal()] == 1 && extra >= 1 && extra <= 3 && distinct && n[NpcKindSC.POWER_ENGINEER.ordinal()] == 0
                    && n[NpcKindSC.ARMOURER.ordinal()] == 0 ? null : "an Outpost:" + c;
        }
        int need = size == SettlementSizeSC.CITY ? 2 : 1;
        for (NpcKindSC k : trades) {
            int more = n[k.ordinal()] - need;
            boolean extraKind = k == NpcKindSC.TECHNOLOGIST || k == NpcKindSC.METALLURGIST || k == NpcKindSC.GAS_TECH || k == NpcKindSC.POWER_ENGINEER;
            if (more < 0 || size == SettlementSizeSC.CITY && more > 1 || size == SettlementSizeSC.SETTLEMENT && more > 0 && !extraKind) {
                return size + ":" + c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 14. RosterSC.upgrade (package S, §6.2, ПЖ-10)

    private static void rosterUpgrade() throws Exception {
        Method upgrade = Class.forName("com.sc.settle.RosterSC").getMethod("upgrade", SettlementRegistrySC.Settlement.class);
        StringBuilder bad = new StringBuilder();
        int added = 0;
        for (SettlementSizeSC size : SettlementSizeSC.values()) {
            for (long seed : new long[]{3L, 1L << 23, 77777777L, -5L}) {
                SettlementRegistrySC.Settlement s = v1Record(size, seed);
                List<String> before = slotsText(s);
                int got = ((Number) upgrade.invoke(null, s)).intValue();
                List<String> after = slotsText(s);
                List<NpcKindSC> appended = new ArrayList<NpcKindSC>();
                for (int i = before.size(); i < s.slots.size(); i++) {
                    appended.add(s.slots.get(i).kindOf());
                }
                List<NpcKindSC> want = new ArrayList<NpcKindSC>();
                if (size != SettlementSizeSC.OUTPOST) {
                    want.add(NpcKindSC.ARMOURER);
                    want.add(NpcKindSC.POWER_ENGINEER);
                }
                want.add(NpcKindSC.SALVAGER);
                if (size.captain(seed)) {
                    want.add(NpcKindSC.CAPTAIN);
                }
                if (size.commandant) {
                    want.add(NpcKindSC.COMMANDANT);
                }
                boolean kept = after.size() >= before.size() && after.subList(0, before.size()).equals(before);
                boolean kinds = new HashSet<NpcKindSC>(appended).equals(new HashSet<NpcKindSC>(want)) && appended.size() == want.size() && got == want.size();
                int from = s.extra.getCompoundTag("S").getInteger("F3From");
                boolean version = s.version == SettlementRegistrySC.VERSION && from == 1;
                boolean fresh = true;
                for (int i = before.size(); i < s.slots.size(); i++) {
                    fresh &= s.slots.get(i).state == SettlementRegistrySC.NEVER && s.slots.get(i).uuid() == null;
                }
                int again = ((Number) upgrade.invoke(null, s)).intValue();
                boolean idle = again == 0 && slotsText(s).equals(after);
                if (!(kept && kinds && version && fresh && idle)) {
                    bad.append(" [").append(size).append(' ').append(seed).append(": kept ").append(kept).append(", appended ").append(appended).append(" (want ")
                            .append(want).append(", returned ").append(got).append("), version ").append(s.version).append(" F3From ").append(from)
                            .append(", new slots NEVER ").append(fresh).append(", twice idle ").append(idle).append(']');
                }
                added += got;
            }
        }
        check(bad.length() == 0 && SettlementRegistrySC.VERSION == 2, "roster upgrade (RosterSC.upgrade, ПЖ-10): a record of version 1 gets the Armourer, the"
                + " Power Engineer and the Salvager (an Outpost the Salvager), the Captain by the seed and the City's Commandant appended at the end ("
                + added + " slots over 12 records), the old slots and their creatures untouched, version 2 with F3From 1, a second call changes nothing"
                + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    /** A Ф2 record by hand: the Ф2 roster (the Elder, the four trades, the guards) at made-up points, every slot alive with its UUID. */
    private static SettlementRegistrySC.Settlement v1Record(SettlementSizeSC size, long seed) {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.version = 1;
        s.id = 9;
        s.dim = -1414;
        s.size = size.ordinal();
        s.radius = size.radiusMin;
        s.seed = seed;
        s.x = 1000;
        s.y = 70;
        s.z = 2000;
        s.floorY = 69;
        s.hasDock = size.dock;
        for (String p : new String[]{SettlementRegistrySC.P_PLAZA, SettlementRegistrySC.P_SQUARE, SettlementRegistrySC.P_CLONE, SettlementRegistrySC.P_GATE,
                SettlementRegistrySC.P_PAD + "0", SettlementRegistrySC.P_PAD + "1"}) {
            s.points.put(p, new int[]{s.x + p.length(), s.y, s.z - p.length()});
        }
        List<NpcKindSC> kinds = new ArrayList<NpcKindSC>(Arrays.asList(NpcKindSC.ELDER, NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH,
                NpcKindSC.CARTOGRAPHER));
        for (int i = 0; i < size.sentries; i++) {
            kinds.add(NpcKindSC.SENTRY);
        }
        for (int i = 0; i < size.seniors; i++) {
            kinds.add(NpcKindSC.SENIOR_WARDEN);
        }
        for (int i = 0; i < kinds.size(); i++) {
            SettlementRegistrySC.Slot sl = new SettlementRegistrySC.Slot();
            sl.kind = kinds.get(i).ordinal();
            sl.nameElement = i;
            sl.nameNumber = 10 + i;
            sl.postX = s.x + i;
            sl.postY = s.y;
            sl.postZ = s.z + i;
            sl.homeX = s.x - i;
            sl.homeY = s.y;
            sl.homeZ = s.z - i;
            sl.state = SettlementRegistrySC.ALIVE;
            UUID u = UUID.nameUUIDFromBytes(("sc-test-slot-" + seed + "-" + i).getBytes());
            sl.uuidMost = u.getMostSignificantBits();
            sl.uuidLeast = u.getLeastSignificantBits();
            sl.seenAt = 500L;
            s.slots.add(sl);
        }
        return s;
    }

    private static List<String> slotsText(SettlementRegistrySC.Settlement s) {
        List<String> out = new ArrayList<String>();
        for (SettlementRegistrySC.Slot sl : s.slots) {
            out.add(sl.kind + ":" + sl.nameElement + "-" + sl.nameNumber + "@" + sl.postX + "," + sl.postY + "," + sl.postZ + "/" + sl.homeX + "," + sl.homeY
                    + "," + sl.homeZ + "#" + sl.state + "," + sl.uuidMost + "," + sl.uuidLeast + "," + sl.diedAt + "," + sl.seenAt);
        }
        return out;
    }

    // ------------------------------------------------------------------ 15. the trade of Ф3 (package T, §6.7, §6.8, ПО-7, ПО-10..ПО-13)

    private static void trade() throws Exception {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        int outCharge = staticInt(TradeOfferSC.class, "OUT_CHARGE", -1), outRestore = staticInt(TradeOfferSC.class, "OUT_RESTORE", -1);
        Method any = TradeOfferSC.Ingredient.class.getMethod("any"), metasOf = TradeOfferSC.Ingredient.class.getMethod("metas");
        StringBuilder bad = new StringBuilder(), noLang = new StringBuilder(), both = new StringBuilder(), forbidden = new StringBuilder();
        Set<String> keys = new HashSet<String>();
        List<TradeOfferSC.Ingredient> given = new ArrayList<TradeOfferSC.Ingredient>(), got = new ArrayList<TradeOfferSC.Ingredient>();
        int charges = 0, restores = 0, newKinds = 0;
        Set<Integer> restorable = new TreeSet<Integer>();
        for (TradeOfferSC o : TradeTableSC.all()) {
            if (o.key == null || !keys.add(o.key)) {
                bad.append(" key '").append(o.key).append('\'');
                continue;
            }
            if (!en.containsKey(o.langKey()) || !ru.containsKey(o.langKey())) {
                noLang.append(' ').append(o.key);
            }
            newKinds += o.kind == NpcKindSC.POWER_ENGINEER || o.kind == NpcKindSC.ARMOURER || o.kind == NpcKindSC.SALVAGER ? 1 : 0;
            if (o.outKind == outCharge || o.outKind == outRestore) {
                // a service: its first side a matcher, the rest paid; the level by ПТ-12 / ПО-10
                if (o.in == null || o.in.length < 2 || o.in.length > 4 || o.in[0] == null || !((Boolean) any.invoke(o.in[0])).booleanValue()) {
                    bad.append(' ').append(o.key).append(":service sides");
                    continue;
                }
                if (o.outKind == outCharge) {
                    charges++;
                    if (o.kind != NpcKindSC.POWER_ENGINEER || o.minLevel != ReputationSC.ACQUAINTANCE) {
                        bad.append(' ').append(o.key).append(":charge by ").append(o.kind).append(" from ").append(o.minLevel);
                    }
                } else {
                    restores++;
                    int tier = -2;
                    for (int m : (int[]) metasOf.invoke(o.in[0])) {
                        restorable.add(m);
                        int t = TrophyRestoreSC.tier(m);
                        tier = tier == -2 || tier == t ? t : -3;
                    }
                    int want = tier == ArmorSuit.NANO.ordinal() ? ReputationSC.ACQUAINTANCE : tier == ArmorSuit.QUANTUM.ordinal() ? ReputationSC.PARTNER
                            : ReputationSC.OWN;
                    if (o.kind != NpcKindSC.ARMOURER || tier < 0 || o.minLevel != want) {
                        bad.append(' ').append(o.key).append(":restore tier ").append(tier).append(" from ").append(o.minLevel);
                    }
                }
                continue;                                   // ПО-7: the services stand aside (the player's own thing in, the same out)
            }
            if (o.in == null || o.in.length < 1 || o.in.length > 2) {      // a deal: 1..2 sides (TradeOfferSC.MAX_SIDES)
                bad.append(' ').append(o.key).append(":sides");
                continue;
            }
            for (TradeOfferSC.Ingredient i : o.in) {
                if (i == null || i.display() == null) {
                    bad.append(' ').append(o.key).append(":nothing to show");
                } else if (!i.isChips()) {
                    given.add(i);
                }
            }
            if (o.outKind == TradeOfferSC.OUT_ITEM && o.out != null && o.out.display() != null && !o.out.isChips()) {
                got.add(o.out);
                String why = SelfTestSettlementSC.forbidden(o.out.display());
                if (why != null) {
                    forbidden.append(' ').append(o.key).append(':').append(why);
                }
            }
        }
        // ПО-7 by its one definition (TradeTableSC.boughtAndSold): a purchase's part - the Armourer's Controllers for the Defense
        // Chip - is a price, not «bought» (the Ф2 check's every-given-thing would flag the Technologist's Controllers)
        for (String n : TradeTableSC.boughtAndSold()) {
            both.append(' ').append(n);
        }
        // ПТ-12: every trophy that drops now, but the Singular ones, restored at the Armourer's (the ПЭ-1 too); Ф5's ПЭ-1М is Singular-level -
        // the Singular station's alone (ПТ-26), never the Armourer's
        StringBuilder notRestored = new StringBuilder();
        for (int m : TrophyTableSC.SHOWN) {
            if (TrophyTableSC.level(m) != ArmorSuit.SINGULAR && m != TrophyTableSC.META_PE1M && !restorable.contains(m)) {
                notRestored.append(' ').append(m);
            }
        }
        boolean outs = outCharge == 3 && outRestore == 4;
        check(outs && newKinds > 0 && charges > 0 && restores > 0 && bad.length() == 0 && noLang.length() == 0 && both.length() == 0 && forbidden.length() == 0
                && notRestored.length() == 0, "trade (TradeTableSC, package T, ПО-7, ПО-10..ПО-13): OUT_CHARGE 3 / OUT_RESTORE 4 " + outs + ", " + newKinds
                + " deals of the three new professions, " + charges + " charge and " + restores + " restore services (their matcher first, the levels of"
                + " ПТ-12)" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; keys unique with their lang" + (noLang.length() == 0 ? "" : " - NO LANG:" + noLang)
                + "; ПО-7 with the services aside" + (both.length() == 0 ? "" : " - BOTH:" + both) + (forbidden.length() == 0 ? "" : " - SOLD:" + forbidden)
                + "; every trophy shown restorable at the Armourer" + (notRestored.length() == 0 ? "" : " - NOT:" + notRestored));
    }

    // ------------------------------------------------------------------ 16. RuinLayoutSC (package X, §8.1, ПУ-1, ПУ-4)

    private static void ruinsLayout() {
        int g = SubstrateTerrainSC.GRID;
        boolean nodes = Arrays.equals(RuinLayoutSC.nodeNear(0, 0), new int[]{0, 0}) && RuinLayoutSC.nodeNear(24, 0) != null && RuinLayoutSC.nodeNear(25, 0) == null
                && RuinLayoutSC.nodeNear(0, -24) != null && RuinLayoutSC.nodeNear(-25, 3) == null && Arrays.equals(RuinLayoutSC.nodeNear(g, g), new int[]{1, 1})
                && Arrays.equals(RuinLayoutSC.nodeNear(-g + 5, 2 * g - 7), new int[]{-1, 2}) && RuinLayoutSC.nodeNear(g / 2, 0) == null
                && RuinLayoutSC.NODE_HALF == 24;
        boolean chunks = Arrays.equals(RuinLayoutSC.nodeAtChunk(0, 0), new int[]{0, 0}) && Arrays.equals(RuinLayoutSC.nodeAtChunk(16, 32), new int[]{1, 2})
                && Arrays.equals(RuinLayoutSC.nodeAtChunk(-16, 0), new int[]{-1, 0}) && RuinLayoutSC.nodeAtChunk(1, 0) == null
                && RuinLayoutSC.nodeAtChunk(0, -15) == null;
        // the lithographs: deterministic, ~30% of the free cells, never the spawn's cell or a settlement's, 80+ from the lines, the floor Y 30..40
        StringBuilder bad = new StringBuilder();
        int free = 0, with = 0;
        for (long seed : new long[]{0L, 1234567L, -98765L}) {
            Set<Long> settled = new HashSet<Long>();
            for (int rx = -11; rx <= 11; rx++) {
                for (int rz = -11; rz <= 11; rz++) {
                    SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, rx, rz);
                    if (s != null) {
                        settled.add(cellKey(floorDiv(s.x, g), floorDiv(s.z, g)));
                    }
                }
            }
            for (int cx = -20; cx < 20 && bad.length() < 500; cx++) {
                for (int cz = -20; cz < 20 && bad.length() < 500; cz++) {
                    RuinLayoutSC.LithoSite a = RuinLayoutSC.lithoSite(seed, cx, cz), b = RuinLayoutSC.lithoSite(seed, cx, cz);
                    boolean spawn = cx == 0 && cz == 0, taken = settled.contains(cellKey(cx, cz));
                    if ((a == null) != (b == null) || a != null && (a.x != b.x || a.z != b.z || a.floorY != b.floorY || a.seed != b.seed)) {
                        bad.append(" not deterministic ").append(cx).append(',').append(cz);
                    }
                    if (a != null && (spawn || taken)) {
                        bad.append(' ').append(spawn ? "the spawn's cell " : "a settlement's cell ").append(cx).append(',').append(cz);
                    }
                    if (a != null && (a.cellX != cx || a.cellZ != cz || floorDiv(a.x, g) != cx || floorDiv(a.z, g) != cz
                            || SubstrateTerrainSC.distanceToLine(a.x) < 80 || SubstrateTerrainSC.distanceToLine(a.z) < 80 || a.floorY < 30 || a.floorY > 40)) {
                        bad.append(" site ").append(cx).append(',').append(cz).append(" at ").append(a.x).append(',').append(a.floorY).append(',').append(a.z);
                    }
                    if (a != null && (RuinLayoutSC.lithoAtChunk(seed, a.x >> 4, a.z >> 4) == null || !RuinLayoutSC.inLitho(seed, a.x, a.floorY + 1, a.z)
                            || RuinLayoutSC.inLitho(seed, a.x, a.floorY + 30, a.z))) {
                        bad.append(" lithoAtChunk / inLitho ").append(cx).append(',').append(cz);
                    }
                    if (!spawn && !taken) {
                        free++;
                        with += a != null ? 1 : 0;
                    }
                }
            }
        }
        float share = free == 0 ? 0F : with / (float) free;
        check(nodes && chunks && bad.length() == 0 && share > 0.25F && share < 0.35F, "ruins layout (RuinLayoutSC, package X, ПУ-1, ПУ-4): the Node zone ±24"
                + " round every crossing " + nodes + ", its start chunk " + chunks + "; lithographs in " + with + " of " + free + " free cells ("
                + Math.round(share * 1000F) / 10F + "%, 30 ± 5), deterministic, never in the spawn's or a settlement's cell, 80+ blocks from the lines,"
                + " the floor Y 30-40, its own start chunk and hall" + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    private static long cellKey(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }

    /** a / b rounded down (Java 6 has no Math.floorDiv). */
    private static int floorDiv(int a, int b) {
        return (int) Math.floor(a / (double) b);
    }

    // ------------------------------------------------------------------ 17. texts (§10.1)

    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>();
        for (int id = MobEntitiesSC.ID_TROOPER; id <= MobEntitiesSC.ID_MIMIC; id++) {
            keys.add("entity.SiliconAge." + MobEntitiesSC.nameOf(id) + ".name");
        }
        for (int m = BookWorldSC.LOOT_QUANTUM_SCRAP; m <= BookWorldSC.LOOT_SHIELD_CORE; m++) {
            String k = BookWorldSC.LOOT_NAMES[m];
            keys.add("item.siliconage.combatLoot." + k + ".name");
            keys.add("sc.tooltip.loot." + k);
            keys.add("sc.tooltip.loot." + k + ".details");
            keys.add("sc.tooltip.loot." + k + ".howto");
        }
        for (String n : BlockRuinSC.NAMES) {
            keys.add("tile.siliconage.ruin." + n + ".name");
            keys.add("sc.tooltip.ruin." + n);
            keys.add("sc.tooltip.ruin." + n + ".details");
            keys.add("sc.tooltip.ruin." + n + ".howto");
            keys.add("sc.manual.world.ruin." + n);
        }
        for (String n : BlockShowcase2SC.NAMES) {
            keys.add("tile.siliconage.showcase2." + n + ".name");
        }
        for (EliteModSC m : EliteModSC.values()) {
            keys.add(m.langKey());
            keys.add("sc.manual.world.elite." + m.key);
        }
        int orders = staticInt(EliteModSC.class, "ORDER_LINES", 4);
        for (int i = 0; i < orders; i++) {
            keys.add("sc.elite.order." + i);
        }
        keys.addAll(Arrays.asList("sc.elite.name", "death.attack.scBreakdown", "death.attack.scBreakdownWorld", "death.attack.scBreakdownWorld.player",
                "sc.settle.kind.powerEngineer", "sc.settle.kind.armourer", "sc.settle.kind.salvager", "sc.settle.kind.captain", "sc.settle.kind.commandant",
                "sc.nei.obtain.ruin", "sc.nei.obtain.trophy.norestore", "sc.tooltip.trophy.howto"));
        StringBuilder missing = new StringBuilder();
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        // the death messages' arguments (ПБ-15): vanilla passes the victim and, for an attacker, its name
        boolean death = positional(en.get("death.attack.scBreakdown")) == 2 && positional(ru.get("death.attack.scBreakdown")) == 2
                && positional(en.get("death.attack.scBreakdownWorld")) == 1 && positional(ru.get("death.attack.scBreakdownWorld")) == 1
                && positional(en.get("death.attack.scBreakdownWorld.player")) == 2 && positional(ru.get("death.attack.scBreakdownWorld.player")) == 2
                && "%s · %s".equals(en.get("sc.elite.name")) && "%s · %s".equals(ru.get("sc.elite.name"));
        StringBuilder diff = new StringBuilder();
        int all = 0;
        for (Map<String, String> from : new Map[]{en, ru}) {
            Map<String, String> to = from == en ? ru : en;
            for (Map.Entry<String, String> e : from.entrySet()) {
                if (!ours(e.getKey())) {
                    continue;
                }
                all += from == en ? 1 : 0;
                if (!to.containsKey(e.getKey())) {
                    diff.append(' ').append(from == en ? "only en_US:" : "only ru_RU:").append(e.getKey());
                } else if (from == en && positional(e.getValue()) != positional(to.get(e.getKey()))) {
                    diff.append(" args differ:").append(e.getKey());
                } else if (from == en && e.getValue().matches(".*[\\u0400-\\u04FF«»„].*")) {
                    diff.append(" Cyrillic / «» / „ in en_US:").append(e.getKey());
                }
            }
        }
        check(missing.length() == 0 && diff.length() == 0 && death, "texts (§10.1): " + keys.size() + " keys - the creatures, the kinds, the loot, the ruin"
                + " blocks and their tooltips, the showcases, the elites, «Пробой» - in en_US and ru_RU" + missing + "; " + all + " Ф3 keys with the same"
                + " arguments in both, no Cyrillic in English" + diff + "; the death messages 2 / 1 / 2 arguments, the elite's name «%s · %s» " + death);
    }

    private static boolean ours(String k) {
        for (String p : PREFIXES) {
            if (k.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /** %s / %d and %1$s... arguments of a lang value (%% isn't one); -1 for none. */
    private static int positional(String v) {
        if (v == null) {
            return -1;
        }
        int n = 0;
        for (int i = 0; i + 1 < v.length(); i++) {
            if (v.charAt(i) != '%') {
                continue;
            }
            int j = i + 1;
            if (v.charAt(j) == '%') {
                i++;
                continue;
            }
            while (j < v.length() && (Character.isDigit(v.charAt(j)) || v.charAt(j) == '$')) {
                j++;
            }
            if (j < v.length() && (v.charAt(j) == 's' || v.charAt(j) == 'd')) {
                n++;
                i = j;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 18. the handbook (§10.2)

    private static void handbook() {
        BookContent.invalidate();
        StringBuilder bad = new StringBuilder();
        for (String id : ARTICLES) {
            BookEntry e = BookContent.byId(id);
            if (e == null) {
                bad.append(' ').append(id).append(" missing");
                continue;
            }
            if (e.chapter != BookChapter.WORLD || e.els.size() < 5) {
                bad.append(' ').append(id).append(" chapter ").append(e.chapter).append(" / ").append(e.els.size()).append(" elements");
            }
            String t = e.searchText();
            for (String raw : new String[]{"sc.manual.", "sc.book.", "sc.elite.", "sc.settle.", "sc.trade.", "sc.dim.", "sc.suit.", "sc.emp.", "tile.siliconage.",
                    "item.siliconage.", "entity.siliconage.", "%s", "%d", "format error"}) {
                if (t.contains(raw)) {
                    bad.append(' ').append(id).append(" raw '").append(raw).append('\'');
                }
            }
        }
        // the extended Ф1 / Ф2 articles read clean too
        for (String id : new String[]{"world.armoured", "world.trophies", "world.tuners", "world.trade", "world.wardens"}) {
            BookEntry e = BookContent.byId(id);
            String t = e == null ? "" : e.searchText();
            for (String raw : new String[]{"sc.manual.", "sc.elite.", "sc.settle.", "%s", "format error"}) {
                if (t.contains(raw)) {
                    bad.append(' ').append(id).append(" raw '").append(raw).append('\'');
                }
            }
        }
        int elites = rows("world.elites", 0), wardens = rows("world.wardens", 0), restore = rows("world.restore", 0), trophies = rows("world.trophies", 0);
        boolean tables = elites == EliteModSC.values().length && wardens == 4 && restore >= 1 && trophies == TrophyTableSC.SHOWN.length;
        // G: the eggs, the new loot, the ruin blocks
        StringBuilder noG = new StringBuilder();
        String[] eggTo = {"world.renegades", "world.renegades", "world.shells", "world.shells", "world.executioner", "world.noise", "world.noise", "world.noise"};
        for (int i = 0; i < eggTo.length; i++) {
            g(noG, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_TROOPER + i), eggTo[i]);
        }
        String[] lootTo = {"world.renegades", "world.executioner", "world.noise", "world.executioner"};
        for (int i = 0; i < lootTo.length; i++) {
            g(noG, MobItemsSC.combatLoot == null ? null : new ItemStack(MobItemsSC.combatLoot, 1, BookWorldSC.LOOT_QUANTUM_SCRAP + i), lootTo[i]);
        }
        for (int m = 0; m <= BlockRuinSC.MACHINE_HUSK; m++) {          // Ф3's four (Ф4's 4..6 open «Хранилища» / «Лагеря»: SelfTestHazardSC)
            g(noG, RuinBlocksSC.ruin == null ? null : new ItemStack(RuinBlocksSC.ruin, 1, m), "world.ruins");
        }
        g(noG, MobItemsSC.trophy == null ? null : new ItemStack(MobItemsSC.trophy, 1, TrophyTableSC.armorMeta(ArmorSuit.EXO, 1)), "world.trophies");
        int chapter = BookContent.chapter(BookChapter.WORLD).size();
        List<String> unresolved = BookWorldSC.unresolved();
        BookContent.invalidate();
        if (!unresolved.isEmpty()) {
            System.out.println("[SC-TEST] INFO combat: the handbook still prints the plan's numbers for (public static constants of these names in packages"
                    + " A / M / X / S / T, or direct references in BookWorldSC): " + unresolved);
        }
        int want = 20 + BookWorldSC.F4_ARTICLES.length + BookWorldSC.F5_ARTICLES.length;   // Ф1..Ф3's 20, Ф4's and Ф5's (SelfTestHazardSC / SelfTestFinaleSC)
        check(bad.length() == 0 && tables && noG.length() == 0 && chapter == want, "handbook (§10.2): the " + ARTICLES.length + " new WORLD articles (the"
                + " chapter " + chapter + " of " + want + ") build with no raw keys, the extended ones too" + (bad.length() == 0 ? "" : " -" + bad) + "; tables: the"
                + " elites " + elites + ", the ranks " + wardens + " (4), the restoring " + restore + ", the trophies " + trophies + " (SHOWN) " + tables
                + "; G on the eight eggs, the new loot, the ruin blocks" + (noG.length() == 0 ? "" : " - NOT:" + noG));
    }

    private static int rows(String id, int index) {
        BookEntry e = BookContent.byId(id);
        int n = 0;
        for (BookEl el : e == null ? new ArrayList<BookEl>() : e.els) {
            if (el.kind == BookEl.Kind.TABLE && n++ == index) {
                return el.rows.size();
            }
        }
        return -1;
    }

    private static void g(StringBuilder out, ItemStack s, String id) {
        if (s == null || s.getItem() == null) {
            out.append(" (not registered for ").append(id).append(')');
            return;
        }
        BookEntry e = BookContent.entryFor(s);
        if (e == null || !id.equals(e.id)) {
            out.append(' ').append(Item.itemRegistry.getNameForObject(s.getItem())).append(':').append(s.getItemDamage()).append(" -> ")
                    .append(e == null ? "nothing" : e.id);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A creature of `cls` made as EntityList would, but with no world; null (listed in `noWorld`) when its constructor needs one. */
    private static Entity make(Class<?> cls, StringBuilder noWorld) {
        try {
            Constructor<?> c = cls.getConstructor(World.class);
            return (Entity) c.newInstance((World) null);
        } catch (Throwable t) {
            Throwable why = t.getCause() != null ? t.getCause() : t;
            noWorld.append(' ').append(cls.getSimpleName()).append(" (").append(why).append(')');
            return null;
        }
    }

    /** Another package's number `field` of `cls` (a Class or a class name) is `want`: a wrong one in `bad`, a missing name in `missing`. */
    private static void expect(StringBuilder bad, StringBuilder missing, Object cls, String field, double want) {
        Class<?> c;
        try {
            c = cls instanceof Class ? (Class<?>) cls : Class.forName((String) cls);
        } catch (Throwable t) {
            missing.append(' ').append(cls).append('.').append(field);
            return;
        }
        try {
            Object v = c.getField(field).get(null);
            if (!(v instanceof Number) || Math.abs(((Number) v).doubleValue() - want) > 1e-4) {
                bad.append(' ').append(c.getSimpleName()).append('.').append(field).append('=').append(v).append(" (").append(n((float) want)).append(')');
            }
        } catch (Throwable t) {
            missing.append(' ').append(c.getSimpleName()).append('.').append(field);
        }
    }

    private static int[] intArray(Class<?> c, String name) {
        try {
            Object v = c.getField(name).get(null);
            return v instanceof int[] ? (int[]) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int staticInt(Class<?> c, String name, int otherwise) {
        try {
            return c.getField(name).getInt(null);
        } catch (Throwable t) {
            return otherwise;
        }
    }

    private static float staticFloat(Class<?> c, String name) {
        try {
            return ((Number) c.getField(name).get(null)).floatValue();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a == null ? new int[0] : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /** A full set's armour points (Nano 20, Quantum 22, Exo 24, Singular 28). */
    private static int fullPoints(ArmorSuit s) {
        int p = 0;
        for (int t = 0; t < 4; t++) {
            p += s.material.getDamageReductionAmount(t);
        }
        return p;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4F;
    }

    private static String n(float v) {
        return String.valueOf(Math.round(v * 10F) / 10F);
    }

    // ------------------------------------------------------------------ creatures without a world (the refusal's and the analyzer's cases)

    /** A hostile native (IMob, ISubstrateNativeSC) - a Trooper's stand-in. */
    private static final class HostileStub extends EntityMob implements ISubstrateNativeSC {
        HostileStub() {
            super(null);
        }
    }

    /** Any other creature: not hostile, not a settlement's, nobody's pet. */
    private static final class CreatureStub extends EntityCreature {
        CreatureStub() {
            super(null);
        }
    }

    /** A dome's guard (ISettlementGuardSC) of no settlement. */
    private static final class GuardStub extends EntityCreature implements ISettlementGuardSC {
        GuardStub() {
            super(null);
        }

        @Override
        public int settlementId() {
            return 0;
        }

        @Override
        public int[] boundCore() {
            return null;
        }

        @Override
        public int slot() {
            return 0;
        }

        @Override
        public NpcKindSC kind() {
            return NpcKindSC.SENTRY;
        }

        @Override
        public int nameCode() {
            return 0;
        }

        @Override
        public void bindTo(SettlementRegistrySC.Settlement s, int slot) {
        }
    }
}
