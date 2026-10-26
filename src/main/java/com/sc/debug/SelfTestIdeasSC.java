package com.sc.debug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.hazard.RaidAlarmSC;
import com.sc.hazard.RaidRulesSC;
import com.sc.mob.WantedRulesSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SquadSC;
import com.sc.settle.trade.ContractEventsSC;
import com.sc.settle.trade.ContractLocalSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeServerSC;

/**
 * The self-test of the proposal of 2026-10-26 (docs/plan-ideas-2026-10-26.md), from SelfTestSC.run after SelfTestEmbassySC: one method an
 * item - each implementer fills only his own (the coordinator's split). The pure decisions, the numbers, the lang keys of the item (both
 * languages, the same arguments, no « » - SelfTestSC.langQuotes covers the files as a whole).
 */
final class SelfTestIdeasSC {

    private static final UUID P1 = UUID.nameUUIDFromBytes(new byte[]{'p', 'd', '1'}), P2 = UUID.nameUUIDFromBytes(new byte[]{'p', 'd', '2'});

    private SelfTestIdeasSC() {
    }

    // ------------------------------------------------------------------ helpers (shared by the items' methods)

    private static Map<String, String> en, ru;

    /** The keys missing in en_US / ru_RU or with a different number of %s there (" key" each); "" all fine. */
    static String lang(String... keys) {
        if (en == null) {
            en = SelfTestSC.langMap("en_US");
            ru = SelfTestSC.langMap("ru_RU");
        }
        StringBuilder bad = new StringBuilder();
        for (String k : keys) {
            String e = en.get(k), r = ru.get(k);
            if (e == null || r == null) {
                bad.append(" no:").append(k);
            } else if (args(e) != args(r)) {
                bad.append(" args:").append(k);
            }
        }
        return bad.toString();
    }

    /** The %s of a lang value (%% is a literal percent). */
    static int args(String v) {
        int n = 0;
        for (int i = v.indexOf('%'); i >= 0 && i + 1 < v.length(); i = v.indexOf('%', i + 2)) {
            if (v.charAt(i + 1) == 's') {
                n++;
            }
        }
        return n;
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "ideas: " + what);
    }

    static void run() {
        pd1();
        pd2();
        pd3();
        pd4();
        pd5();
        pd6();
        pd7();
        pd8();
        om1();
        om2();
        om3();
        om4();
        om5();
        om6();
        om7();
        articles();
    }

    /** The integrator's: the six new WORLD articles are in the chapter, in BookWorldSC.IDEAS_ARTICLES' order (the phases' self-tests count them). */
    private static void articles() {
        List<String> ids = new ArrayList<String>();
        for (com.sc.manual.BookEntry e : com.sc.manual.BookContent.chapter(com.sc.manual.BookChapter.WORLD)) {
            ids.add(e.id);
        }
        StringBuilder bad = new StringBuilder();
        int last = -1;
        for (String a : com.sc.manual.BookWorldSC.IDEAS_ARTICLES) {
            int i = ids.indexOf(a);
            if (i <= last) {
                bad.append(' ').append(a);
            }
            last = Math.max(last, i);
        }
        check(bad.length() == 0, "the handbook: the " + com.sc.manual.BookWorldSC.IDEAS_ARTICLES.length + " new WORLD articles in the chapter, in order"
                + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    /** ПД-1: the board, the local contracts, the orders. */
    private static void pd1() {
        List<ContractTableSC.Contract> pool = ContractTableSC.pool();
        int[] groups = new int[4];
        StringBuilder bad = new StringBuilder();
        List<String> keys = new ArrayList<String>();
        for (ContractTableSC.Contract c : pool) {
            groups[Math.max(0, Math.min(3, c.group))]++;
            if (c.chips < 6 || c.chips > 16 || c.reputation < 10 || c.reputation > 40) {
                bad.append(' ').append(c.key).append(' ').append(c.chips).append("/+").append(c.reputation);
            }
            keys.add(c.langKey());
            keys.add(c.langKey() + ".desc");
        }
        ContractTableSC.Contract big = ContractTableSC.byKey(ContractTableSC.BIG_ORDER_KEY);
        boolean bundle = big != null && big.bundle().length == 4 && big.handIn() == null;
        boolean tags = ContractLocalSC.tagsOf(new int[]{BiomeSubstrateSC.PLATEAUS, BiomeSubstrateSC.RIDGES})
                == (ContractLocalSC.TAG_RIDGES | ContractLocalSC.TAG_NODE) && ContractLocalSC.tagsOf(new int[0]) == ContractLocalSC.TAG_NODE
                && ContractLocalSC.tagsOf(null) == ContractLocalSC.TAG_NODE
                && ContractLocalSC.tagsOf(new int[]{BiomeSubstrateSC.OXIDE, BiomeSubstrateSC.RIFT}) == (ContractLocalSC.TAG_OXIDE
                | ContractLocalSC.TAG_RIFT | ContractLocalSC.TAG_NODE);
        // the board: the same when asked again, 3 different common ones, at most one local fitting the tags, one order, the Big one if on
        boolean board = true, sameTags = true;
        int ridges = ContractLocalSC.TAG_RIDGES | ContractLocalSC.TAG_NODE;
        for (UUID id : new UUID[]{P1, P2}) {
            for (long day = 0; day < 30 && board; day++) {
                for (int t : new int[]{ContractLocalSC.TAG_NODE, ridges, ContractLocalSC.TAG_OXIDE | ContractLocalSC.TAG_NODE}) {
                    for (boolean bigOn : new boolean[]{false, true}) {
                        List<String> b = ContractTableSC.substrateBoard(id, day, t, day % 2 == 0, bigOn);
                        board &= b.equals(ContractTableSC.substrateBoard(id, day, t, day % 2 == 0, bigOn));
                        int common = 0, local = 0, order = 0, bigs = 0;
                        for (String k : b) {
                            ContractTableSC.Contract c = ContractTableSC.byKey(k);
                            if (c == null) {
                                board = false;
                                continue;
                            }
                            common += c.group == ContractTableSC.G_GENERAL ? 1 : 0;
                            order += c.group == ContractTableSC.G_ORDER ? 1 : 0;
                            bigs += c.group == ContractTableSC.G_BIG ? 1 : 0;
                            if (c.group == ContractTableSC.G_LOCAL) {
                                local++;
                                board &= (c.localTag & t) != 0;
                            }
                            board &= day % 2 == 0 || c.goal != ContractTableSC.URGENT;
                        }
                        board &= common == ContractTableSC.BOARD && new HashSet<String>(b).size() == b.size() && local <= 1 && order == 1
                                && bigs == (bigOn ? 1 : 0);
                    }
                }
                // two settlements with the same tags: the same board (salted by the tags, not the settlement)
                sameTags &= ContractTableSC.substrateBoard(id, day, ridges, true, true).equals(ContractTableSC.substrateBoard(id, day, ridges, true, true));
                // the common part the same at every Elder: with no dispatch possible only «Срочная депеша» is replaced (by the next one)
                List<String> withU = ContractTableSC.substrateBoard(id, day, ridges, true, false).subList(0, ContractTableSC.BOARD);
                List<String> noU = ContractTableSC.substrateBoard(id, day, ridges, false, false).subList(0, ContractTableSC.BOARD);
                int kept = 0;
                for (String k : withU) {
                    ContractTableSC.Contract c = ContractTableSC.byKey(k);
                    if (c != null && c.goal != ContractTableSC.URGENT) {
                        sameTags &= noU.contains(k);
                        kept++;
                    }
                }
                sameTags &= kept >= ContractTableSC.BOARD - 1 && (kept == ContractTableSC.BOARD ? noU.equals(withU) : true);
            }
        }
        boolean pureOk = ContractTableSC.stormCounts(true, true) && !ContractTableSC.stormCounts(true, false) && !ContractTableSC.stormCounts(false, true)
                && !ContractTableSC.urgentLate(ContractTableSC.urgentBy(5), 5 * 24000L + 11999L) && ContractTableSC.urgentLate(ContractTableSC.urgentBy(5), 5 * 24000L + 12001L)
                && ContractTableSC.urgentTakeable(24000L * 3 + 100) && !ContractTableSC.urgentTakeable(24000L * 3 + 6000);
        // the readings: a crossing counted once
        SettlementRegistrySC.ContractRec r = new SettlementRegistrySC.ContractRec();
        r.target = 2;
        boolean readings = ContractEventsSC.readingsStep(r, 3, -2) && !ContractEventsSC.readingsStep(r, 3, -2) && r.progress == 1
                && ContractEventsSC.readingsStep(r, 4, -2) && r.progress == 2 && !ContractEventsSC.readingsStep(r, 5, -2);
        String l = lang(keys.toArray(new String[keys.size()]));
        l += lang("sc.contract.readings.step", "sc.contract.urgent.to", "sc.contract.urgent.late", "sc.trade.result.urgentTime",
                "sc.trade.result.bigWeek", "sc.contract.group.local", "sc.contract.group.order", "sc.contract.group.big",
                "sc.manual.world.trade.contracts.2");
        boolean codes = TradeServerSC.RESULT_KEYS.length >= 25 && "urgentTime".equals(TradeServerSC.RESULT_KEYS[TradeServerSC.R_URGENT_TIME])
                && "bigWeek".equals(TradeServerSC.RESULT_KEYS[TradeServerSC.R_BIG_WEEK]);
        check(groups[0] == 10 && groups[1] == 5 && groups[2] == 4 && groups[3] == 1 && bad.length() == 0 && bundle && tags && board && sameTags
                && pureOk && readings && codes && l.isEmpty(), "ПД-1 the live board: the pool " + groups[0] + " common / " + groups[1] + " local / "
                + groups[2] + " orders / " + groups[3] + " big (10/5/4/1), rewards 6..16 chips +10..+40" + (bad.length() == 0 ? "" : " - BAD:" + bad)
                + ", the Big order's 4 sides " + bundle + ", tagsOf " + tags + ", the board (3 common + <=1 local + 1 order + the Big one, the same"
                + " asked again, no dispatch without a destination) " + board + ", the same for the same tags " + sameTags + ", storm/urgent " + pureOk
                + ", readings once a Node " + readings + ", result codes " + codes + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-2: the squad's credit. */
    private static void pd2() {
        UUID killer = P1, mate10 = P2, mate9 = UUID.nameUUIDFromBytes(new byte[]{'m', '9'}), stranger = UUID.nameUUIDFromBytes(new byte[]{'s', 't'});
        Map<UUID, Float> shares = new java.util.HashMap<UUID, Float>();
        shares.put(killer, 0.30F);
        shares.put(mate10, 0.10F);
        shares.put(mate9, 0.09F);
        shares.put(stranger, 0.50F);
        java.util.Set<UUID> mates = new HashSet<UUID>(java.util.Arrays.asList(mate10, mate9));
        List<UUID> got = SquadSC.credited(shares, killer, mates, SquadSC.SHARE);
        boolean credit = got.size() == 2 && got.get(0).equals(killer) && got.contains(mate10) && !got.contains(mate9) && !got.contains(stranger)
                && SquadSC.credited(shares, null, mates, SquadSC.SHARE).isEmpty()
                && SquadSC.credited(shares, killer, new HashSet<UUID>(), SquadSC.SHARE).size() == 1      // no mates (the rule off): the killer
                && SquadSC.credited(null, killer, mates, SquadSC.SHARE).size() == 1;
        boolean pure = SquadSC.MAX == 4 && SquadSC.room(3) && !SquadSC.room(4) && SquadSC.inviteOpen(100L, 100L + SquadSC.INVITE_TICKS)
                && !SquadSC.inviteOpen(100L, 101L + SquadSC.INVITE_TICKS) && !SquadSC.inviteOpen(100L, 99L)
                && com.sc.settle.travel.TravelRulesSC.T_MATE_DEATH == 3;
        String l = lang("sc.squad.invite.sent", "sc.squad.invite.got", "sc.squad.accept", "sc.squad.decline", "sc.squad.declined",
                "sc.squad.expired", "sc.squad.joined", "sc.squad.left", "sc.squad.full", "sc.squad.none", "sc.squad.off", "sc.squad.list.head",
                "sc.squad.list.line", "sc.squad.list.offline", "sc.squad.list.far", "sc.cmd.squad.usage", "sc.bridge.target.mateDeath",
                "sc.bridge.target.mateDeath.hint", "sc.flight.target.mateMark", "sc.flight.rescueClosed", "sc.manual.world.squads.title", "sc.manual.world.squads.1",
                "sc.manual.world.squads.2", "sc.manual.world.squads.3");
        check(credit && pure && l.isEmpty(), "ПД-2 the squad: the killer always, a mate with 10% yes, with 9% no, a stranger with 50% no,"
                + " no killer - nobody, no mates - the killer alone " + credit + "; MAX 4, the invitation's minute, T_MATE_DEATH 3 " + pure
                + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-3: the alarm. */
    private static void pd3() {
        int A = RaidAlarmSC.KIND_ALARM, H = RaidAlarmSC.KIND_HEAVY;
        boolean due = RaidAlarmSC.due(A, -1L, 5L) && !RaidAlarmSC.due(A, 4L, 5L) && RaidAlarmSC.due(A, 3L, 5L) && !RaidAlarmSC.due(H, 3L, 5L)
                && RaidAlarmSC.due(H, 2L, 5L) && RaidAlarmSC.daysLeft(H, 4L, 5L) == 2 && RaidAlarmSC.daysLeft(A, -1L, 5L) == 0;
        int[][] w4 = RaidRulesSC.wave(RaidRulesSC.WAVES_HEAVY - 1, SettlementSizeSC.CITY.ordinal(), new java.util.Random(1L));
        boolean wave = RaidRulesSC.WAVES_HEAVY == 4 && RaidRulesSC.waves(H) == 4 && RaidRulesSC.waves(A) == RaidRulesSC.WAVES && RaidRulesSC.waves(0) == 3
                && w4.length == 3 && w4[0][0] == com.sc.mob.MobEntitiesSC.ID_TROOPER && w4[1][0] == com.sc.mob.MobEntitiesSC.ID_EXECUTIONER
                && w4[2][0] == com.sc.mob.MobEntitiesSC.ID_JAMMER && w4[0][1] == 1 && w4[1][1] == 1 && w4[2][1] == 1;
        float m = RaidAlarmSC.HEAVY_MUL;
        int[] rep = new int[3], chips = new int[3], rolls = new int[3];
        for (int s = 0; s < 3; s++) {
            rep[s] = RaidRulesSC.scaled(RaidRulesSC.rep(s), m);
            chips[s] = RaidRulesSC.scaled(RaidRulesSC.chips(s), m);
            rolls[s] = RaidRulesSC.scaled(RaidRulesSC.chestRolls(s), m);
        }
        boolean reward = java.util.Arrays.equals(rep, new int[]{45, 60, 75}) && java.util.Arrays.equals(chips, new int[]{9, 14, 18})
                && java.util.Arrays.equals(rolls, new int[]{3, 5, 6});
        int relay = SettlementSizeSC.RELAY.ordinal();
        boolean table = SettlementSizeSC.RELAY.tableIndex() == 0 && RaidRulesSC.rep(relay) == RaidRulesSC.rep(0) && RaidRulesSC.reach(relay) == RaidRulesSC.reach(0)
                && RaidRulesSC.wave(0, relay, new java.util.Random(2L))[0][1] == RaidRulesSC.wave(0, 0, new java.util.Random(2L))[0][1];
        boolean keys = RaidAlarmSC.kindOf(com.sc.settle.trade.TradeTableSC.ELDER_ALARM) == A && RaidAlarmSC.kindOf(com.sc.settle.trade.TradeTableSC.ELDER_HEAVY_NIGHT) == H
                && RaidAlarmSC.kindOf("techWafers") == 0 && RaidAlarmSC.refuseKey(RaidAlarmSC.OK) == null
                && "sc.alarm.refuse.captain".equals(RaidAlarmSC.refuseKey(RaidAlarmSC.R_CAPTAIN))
                && com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.ELDER_ALARM) != null
                && com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.ELDER_HEAVY_NIGHT).minLevel == RaidAlarmSC.HEAVY_LEVEL;
        String l = lang("sc.trade.offer.elderAlarm", "sc.trade.offer.elderHeavyNight", "sc.trade.offer.alarmCancel", "sc.alarm.deposit", "sc.alarm.ordered",
                "sc.alarm.orderedHeavy", "sc.alarm.cancelled", "sc.alarm.begins", "sc.alarm.captain", "sc.raid.wave4", "sc.raid.deposit.back",
                "sc.raid.deposit.lost", "sc.raid.deposit.owed", "sc.raid.summary", "sc.raid.summary.kills", "sc.raid.summary.kill", "sc.raid.summary.none",
                "sc.manual.world.raids.alarm", "sc.trade.result.alarm");
        for (String k : RaidAlarmSC.REFUSE_KEYS) {
            if (!k.isEmpty()) {
                l += lang("sc.alarm.refuse." + k);
            }
        }
        check(due && wave && reward && table && keys && l.isEmpty(), "ПД-3 the alarm: due() 2/3 days " + due + ", the heavy night's 4th wave (a"
                + " Trooper - the Commander -, the Executioner, a Jammer) " + wave + ", x1.5 rounded up 45/60/75, 9/14/18, 3/5/6 " + reward
                + ", a relay post on the Post's row " + table + ", the deals " + keys + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-4: the wanted Renegade. */
    private static void pd4() {
        boolean look = true;
        for (int n = 0; n < WantedRulesSC.NAMES; n++) {
            for (int a = 0; a < WantedRulesSC.ALIASES; a += 5) {
                int k = WantedRulesSC.packLook(n, a, 4);
                look &= WantedRulesSC.nameOf(k) == n && WantedRulesSC.aliasOf(k) == a && WantedRulesSC.mod2Of(k) == 4;
            }
        }
        com.sc.mob.EliteModSC[] m = com.sc.mob.EliteModSC.values();
        int pairs = 0;
        boolean sym = true, banned = true;
        for (com.sc.mob.EliteModSC a : m) {
            for (com.sc.mob.EliteModSC b : m) {
                boolean ok = WantedRulesSC.allowedPair(a, b);
                pairs += ok ? 1 : 0;
                sym &= ok == WantedRulesSC.allowedPair(b, a);
                banned &= !ok || a != b && a != com.sc.mob.EliteModSC.REPAIRER && b != com.sc.mob.EliteModSC.REPAIRER
                        && a != com.sc.mob.EliteModSC.AVENGER && b != com.sc.mob.EliteModSC.AVENGER;
            }
        }
        boolean ring = true, pick = true;
        for (long week = 0; week < 60; week++) {
            for (int t = 0; t < 4; t++) {
                int[] s = WantedRulesSC.spot(77L, week, 1000, -500, t);
                ring &= WantedRulesSC.inRing(s[0], s[1], 1000, -500);
            }
            int[] p = WantedRulesSC.pair(77L, week);
            pick &= WantedRulesSC.allowedPair(m[p[0]], m[p[1]]) && WantedRulesSC.city(77L, week, 3) >= 0 && WantedRulesSC.city(77L, week, 3) < 3
                    && WantedRulesSC.name(77L, week) < WantedRulesSC.NAMES && WantedRulesSC.alias(77L, week) < WantedRulesSC.ALIASES;
        }
        boolean numbers = WantedRulesSC.REWARD_SHARE == 0.10F && WantedRulesSC.REWARD_CHIPS == 12 && WantedRulesSC.REWARD_REP == 40
                && com.sc.mob.TrophyRestoreSC.TOKENS_PER_RESTORE == 3 && WantedRulesSC.minutesAgo(0L, 3600L) == 3
                && com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.CART_WANTED) != null
                && com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.ARM_RESTORE_TOKENS_PE1) != null
                && com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.ARM_RESTORE_TOKENS_EXO).in.length
                <= com.sc.settle.trade.TradeOfferSC.MAX_SERVICE_SIDES;
        List<String> keys = new ArrayList<String>();
        for (int i = 0; i < WantedRulesSC.NAMES; i++) {
            keys.add("sc.wanted.name." + i);
        }
        for (int i = 0; i < WantedRulesSC.ALIASES; i++) {
            keys.add("sc.wanted.alias." + i);
        }
        for (String k : new String[]{"Nano", "Quantum", "Exo", "ArmsNano", "ArmsQuantum", "ArmsExo", "Pe1"}) {
            keys.add("sc.trade.offer.armRestoreTokens" + k);
            keys.add("sc.trade.offer.armRestoreTokens" + k + ".any");
        }
        String l = lang(keys.toArray(new String[keys.size()])) + lang("entity.SiliconAge.wantedRenegade.name", "item.siliconage.wantedToken.name",
                "sc.tooltip.wantedToken", "sc.tooltip.wantedToken.details", "sc.tooltip.wantedToken.howto", "sc.wanted.fullName", "sc.wanted.posted",
                "sc.wanted.captain", "sc.wanted.quiet", "sc.wanted.none", "sc.wanted.hint", "sc.wanted.down", "sc.wanted.reward",
                "sc.trade.offer.cartWanted", "sc.nei.obtain.wantedToken", "sc.manual.world.wanted.title", "sc.manual.world.wanted.1",
                "sc.manual.world.wanted.2", "sc.manual.world.wanted.3", "sc.trade.result.noWanted");
        check(look && pairs == 12 && sym && banned && ring && pick && numbers && l.isEmpty(), "ПД-4 the wanted one: packLook both ways " + look
                + ", " + pairs / 2 + " allowed pairs (6), symmetric " + sym + ", no Repairer / Avenger / self " + banned + ", spots in the "
                + WantedRulesSC.RANGE_MIN + ".." + WantedRulesSC.RANGE_MAX + " ring " + ring + ", the week's picks " + pick + ", the numbers and deals "
                + numbers + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-5: the Gatherers. */
    private static void pd5() {
        boolean per = com.sc.settle.GathererSC.perSize(SettlementSizeSC.OUTPOST.ordinal()) == 0
                && com.sc.settle.GathererSC.perSize(SettlementSizeSC.SETTLEMENT.ordinal()) == 1
                && com.sc.settle.GathererSC.perSize(SettlementSizeSC.CITY.ordinal()) == 2 && com.sc.settle.GathererSC.perSize(SettlementSizeSC.RELAY.ordinal()) == 0
                && com.sc.settle.GathererSC.perSize(SettlementSizeSC.EMBASSY.ordinal()) == 0;
        boolean time = !com.sc.settle.GathererSC.workTime(999L) && com.sc.settle.GathererSC.workTime(1000L) && com.sc.settle.GathererSC.workTime(24000L * 2 + 9999L)
                && !com.sc.settle.GathererSC.workTime(10000L) && !com.sc.settle.GathererSC.workTime(18000L);
        boolean rescue = com.sc.settle.GathererSC.rescueDue(-1L, 3L) && com.sc.settle.GathererSC.rescueDue(2L, 3L) && !com.sc.settle.GathererSC.rescueDue(3L, 3L)
                && com.sc.settle.GathererSC.RESCUE_REP == 10 && com.sc.settle.GathererSC.NEAR == 96;
        String l = lang("entity.SiliconAge.gatherer.name", "sc.gatherer.line", "sc.gatherer.spot.druse", "sc.gatherer.spot.fumarole", "sc.gatherer.call",
                "sc.gatherer.saved", "sc.gatherer.savedToday", "sc.gatherer.died", "sc.manual.world.gatherers.title", "sc.manual.world.gatherers.1",
                "sc.manual.world.gatherers.2", "sc.manual.world.gatherers.3");
        check(per && time && rescue && l.isEmpty(), "ПД-5 the Gatherers: PER_SIZE (a Post 0, a Settlement 1, a City 2, none at a relay or an embassy) "
                + per + ", the work's window " + time + ", the rescue once a day " + rescue + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-6: the Breach Protocols. */
    private static void pd6() {
        StringBuilder bad = new StringBuilder();
        int triples = 0;
        for (int m = 0; m < 32; m++) {
            boolean expect = com.sc.arena.BreachProtocolsSC.count(m) <= com.sc.arena.BreachProtocolsSC.MAX
                    && com.sc.arena.BreachProtocolsSC.forbiddenPair(m) == null && com.sc.arena.BreachProtocolsSC.danger(m) <= com.sc.arena.BreachProtocolsSC.DANGER_MAX;
            if (com.sc.arena.BreachProtocolsSC.allowed(m) != expect || com.sc.arena.BreachProtocolsSC.forbiddenPair(m) != null
                    && com.sc.arena.BreachProtocolsSC.danger(m) <= com.sc.arena.BreachProtocolsSC.DANGER_MAX) {
                bad.append(' ').append(m);
            }
            triples += com.sc.arena.BreachProtocolsSC.count(m) == 3 && com.sc.arena.BreachProtocolsSC.allowed(m) ? 1 : 0;
        }
        int all3 = com.sc.arena.BreachProtocolsSC.bit(0) | com.sc.arena.BreachProtocolsSC.bit(1) | com.sc.arena.BreachProtocolsSC.bit(2);
        boolean reward = com.sc.arena.BreachProtocolsSC.crumbs(all3) == 6 && Math.abs(com.sc.arena.BreachProtocolsSC.trophyMul(all3) - 1.75F) < 1e-4F
                && com.sc.arena.BreachProtocolsSC.scheme(all3) && !com.sc.arena.BreachProtocolsSC.scheme(3) && com.sc.arena.BreachProtocolsSC.crumbs(0) == 0
                && com.sc.arena.BreachProtocolsSC.trophyMul(0) == 1F;
        boolean keys = com.sc.arena.BreachProtocolsSC.KEYS.length == com.sc.arena.BreachProtocolsSC.COUNT && "jamming".equals(com.sc.arena.BreachProtocolsSC.keyOf(4))
                && "deafCoils".equals(com.sc.arena.BreachProtocolsSC.keyOf(99)) && com.sc.arena.BreachProtocolsSC.bit(5) == 0
                && com.sc.util.SingularScheme.N.unlockable() && com.sc.util.SingularScheme.N.unlockBit() == 4 && com.sc.util.SingularScheme.N.ordinal() == 13
                && com.sc.arena.BreachFightSC.FX_JAMMING == 6;
        List<String> k = new ArrayList<String>();
        for (String key : com.sc.arena.BreachProtocolsSC.KEYS) {
            k.add("item.siliconage.breachProtocol." + key + ".name");
            k.add("sc.tooltip.breachProtocol." + key);
            k.add("sc.protocol." + key);
        }
        String l = lang(k.toArray(new String[k.size()])) + lang("sc.tooltip.breachProtocol.details", "sc.tooltip.breachProtocol.howto", "sc.protocol.inserted",
                "sc.protocol.removed", "sc.protocol.refuse.off", "sc.protocol.refuse.same", "sc.protocol.refuse.max", "sc.protocol.refuse.pair",
                "sc.protocol.refuse.busy", "sc.protocol.scheme", "sc.singular.scheme.n", "sc.manual.world.protocols.title", "sc.manual.world.protocols.1",
                "sc.manual.world.protocols.2");
        check(bad.length() == 0 && triples == 4 && reward && keys && l.isEmpty(), "ПД-6 the protocols: all 32 masks - allowed <=> (<= 3, no forbidden"
                + " pair, danger <= " + com.sc.arena.BreachProtocolsSC.DANGER_MAX + "), a forbidden pair always over it" + (bad.length() == 0 ? "" : " - BAD:" + bad)
                + "; " + triples + " allowed triples (4); crumbs / trophyMul / scheme " + reward + "; keys, scheme N (bit 4) " + keys
                + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-7: the relay post. */
    private static void pd7() {
        boolean region = com.sc.settle.RelayRulesSC.regionKey(0, 0) == com.sc.settle.RelayRulesSC.regionKey(3, 3)
                && com.sc.settle.RelayRulesSC.regionKey(0, 0) != com.sc.settle.RelayRulesSC.regionKey(4, 0)
                && com.sc.settle.RelayRulesSC.regionKey(-1, 0) == com.sc.settle.RelayRulesSC.regionKey(-4, 3)
                && com.sc.settle.RelayRulesSC.regionKey(-1, 0) != com.sc.settle.RelayRulesSC.regionKey(0, 0)
                && com.sc.settle.RelayRulesSC.regionKey(-5, 0) != com.sc.settle.RelayRulesSC.regionKey(-4, 0);
        boolean table = SettlementSizeSC.RELAY.tableIndex() == SettlementSizeSC.OUTPOST.ordinal() && SettlementSizeSC.RELAY.sentries == 2
                && SettlementSizeSC.RELAY.radiusMax == 12;
        SettlementRegistrySC reg = new SettlementRegistrySC("scSelfTestRelay");
        SettlementRegistrySC.Settlement post = new SettlementRegistrySC.Settlement(), town = new SettlementRegistrySC.Settlement();
        post.size = SettlementSizeSC.RELAY.ordinal();
        post.radius = 12;
        post.x = 100;
        post.z = 100;
        town.size = SettlementSizeSC.SETTLEMENT.ordinal();
        town.radius = 40;
        town.x = 2000;
        town.z = 2000;
        reg.add(post);
        reg.add(town);
        boolean remove = reg.remove(town.id) == null && reg.byId(town.id) == town && reg.remove(post.id) == post && reg.byId(post.id) == null
                && reg.candidates(0, 100, 100).isEmpty();
        boolean trade = com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.ELDER_RELAY) != null
                && com.sc.settle.trade.TradeTableSC.byKey(com.sc.settle.trade.TradeTableSC.ELDER_RELAY).minLevel == com.sc.settle.RelayRulesSC.BUY_LEVEL;
        String l = lang("tile.siliconage.relay.relay.name", "tile.siliconage.relay.mast.name", "sc.tooltip.relay.relay", "sc.tooltip.relay.relay.details",
                "sc.tooltip.relay.relay.howto", "sc.tooltip.relay.mast", "sc.tooltip.relay.mast.details", "sc.tooltip.relay.mast.howto",
                "sc.settle.size.relay", "sc.trade.offer.elderRelay", "sc.relay.refuse.off", "sc.relay.refuse.place", "sc.relay.refuse.region",
                "sc.relay.refuse.near", "sc.relay.refuse.built", "sc.relay.refuse.vault", "sc.relay.refuse.busy", "sc.relay.refuse.cooldown",
                "sc.relay.refuse.cooldownMe", "sc.relay.start", "sc.relay.wave", "sc.relay.left", "sc.relay.lost", "sc.relay.built",
                "sc.relay.destroyed", "sc.nei.obtain.relay", "sc.manual.world.relayPosts.title", "sc.manual.world.relayPosts.1",
                "sc.manual.world.relayPosts.2", "sc.manual.world.relayPosts.3", "sc.manual.world.relayPosts.4");
        // the review's fixes: a destroyed post's id (GONE) - its core never makes the record again; a lost defence's pause
        net.minecraft.nbt.NBTTagCompound y = new net.minecraft.nbt.NBTTagCompound();
        y.setIntArray(com.sc.settle.RelayPostSC.GONE, new int[]{post.id});
        reg.extra.setTag(com.sc.settle.RelayRulesSC.EXTRA, y);
        boolean gone = com.sc.settle.RelayPostSC.gone(reg, post.id) && !com.sc.settle.RelayPostSC.gone(reg, town.id)
                && !com.sc.settle.RelayPostSC.gone(new SettlementRegistrySC("scSelfTestRelay2"), post.id);
        boolean pause = com.sc.settle.RelayPostSC.pauseLeft(100L, 40L) == 60L && com.sc.settle.RelayPostSC.pauseLeft(40L, 100L) == 0L
                && com.sc.settle.RelayPostSC.pauseLeft(0L, 5L) == 0L && com.sc.settle.RelayPostSC.minutes(1L) == 1L
                && com.sc.settle.RelayPostSC.minutes(1200L) == 1L && com.sc.settle.RelayPostSC.minutes(1201L) == 2L
                && com.sc.settle.RelayRulesSC.LOST_PAUSE == 24000;
        check(region && table && remove && trade && gone && pause && l.isEmpty(), "ПД-7 the relay post: regionKey (0..3 one, 4 another, the"
                + " negatives) " + region + ", RELAY on the Post's row, 2 Sentries, dome 12 " + table + ", remove (a post - gone, a settlement -"
                + " refused) " + remove + ", the Elder's deal " + trade + ", a destroyed post's id kept " + gone + ", the lost defence's pause "
                + pause + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ПД-8: the tunnels. */
    private static void pd8() {
        StringBuilder bad = new StringBuilder();
        int eligible = 0, with = 0;
        boolean floors = true, chunks = true;
        for (long seed : new long[]{0L, 4242L, -77L}) {
            for (int cx = -12; cx < 14; cx++) {
                for (int cz = -12; cz < 14; cz++) {
                    boolean spawn = Math.abs(cx) <= com.sc.dim.SubstrateCellsSC.SPAWN_RING && Math.abs(cz) <= com.sc.dim.SubstrateCellsSC.SPAWN_RING;
                    boolean rift = com.sc.dim.SubstrateCellsSC.pureKind(seed, cx, cz) == BiomeSubstrateSC.RIFT;
                    boolean litho = com.sc.ruin.RuinLayoutSC.lithoSite(seed, cx, cz) != null;
                    com.sc.settle.gen.SettlementLayoutSC.Site st = com.sc.settle.gen.SettlementLayoutSC.site(seed, cx >> 1, cz >> 1);
                    boolean town = st != null && (st.x >> 8) == cx && (st.z >> 8) == cz;
                    boolean any = com.sc.ruin.TunnelLayoutSC.has(seed, cx, cz, true) || com.sc.ruin.TunnelLayoutSC.has(seed, cx, cz, false);
                    if ((spawn || rift || litho || town) && any) {
                        bad.append(' ').append(cx).append(',').append(cz);
                    }
                    if (!spawn && !rift && !litho && !town) {
                        eligible++;
                        with += com.sc.ruin.TunnelLayoutSC.has(seed, cx, cz, true) ? 1 : 0;
                    }
                    int f = com.sc.ruin.TunnelLayoutSC.floorY(seed, cx, cz);
                    floors &= f >= com.sc.ruin.TunnelLayoutSC.FLOOR_MIN && f <= com.sc.ruin.TunnelLayoutSC.FLOOR_MAX;
                    if (any && seed == 4242L) {
                        int[] s = com.sc.ruin.TunnelLayoutSC.startChunk(cx, cz);
                        int[] back = com.sc.ruin.TunnelLayoutSC.cellAtChunk(s[0], s[1]);
                        chunks &= back != null && back[0] == cx && back[1] == cz;
                        for (Object o : com.sc.ruin.MapGenTunnelSC.pieces(seed, cx, cz)) {
                            net.minecraft.world.gen.structure.StructureBoundingBox b = ((net.minecraft.world.gen.structure.StructureComponent) o).getBoundingBox();
                            chunks &= (b.minX >> 4) >= s[0] - 8 && (b.maxX >> 4) <= s[0] + 8 && (b.minZ >> 4) >= s[1] - 8 && (b.maxZ >> 4) <= s[1] + 8
                                    && b.minY >= 1 && b.maxY < 80;
                        }
                    }
                }
            }
        }
        double share = eligible == 0 ? 0D : with * 100D / eligible;
        String l = lang("sc.manual.world.tunnels.title", "sc.manual.world.tunnels.1", "sc.manual.world.tunnels.2", "sc.manual.world.tunnels.3");
        check(bad.length() == 0 && Math.abs(share - com.sc.ruin.TunnelLayoutSC.CHANCE_PCT) <= 5D && floors && chunks && l.isEmpty(), "ПД-8 the tunnels:"
                + " never the spawn's, a Rift's, a settlement's or a lithograph's cell" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; along X in "
                + String.format(java.util.Locale.ROOT, "%.1f", share) + "% of " + eligible + " free cells (" + com.sc.ruin.TunnelLayoutSC.CHANCE_PCT
                + " +-5); the floor " + com.sc.ruin.TunnelLayoutSC.FLOOR_MIN + ".." + com.sc.ruin.TunnelLayoutSC.FLOOR_MAX + " " + floors
                + "; every piece within 8 chunks of its start " + chunks + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ОМ-1: the Exo rig's Substrate table. */
    private static void om1() {
        List<com.sc.machine.ExoOreTableSC.Entry> sub = com.sc.machine.ExoOreTableSC.substrateEntries();
        boolean modOnly = true;
        java.util.Set<Integer> metas = new HashSet<Integer>();
        int tant = 0, copper = 0;
        for (com.sc.machine.ExoOreTableSC.Entry e : sub) {
            modOnly &= e.ore != null && com.sc.init.ModBlocks.oreSC != null
                    && e.ore.getItem() == net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.oreSC) && e.lens == e.ore.getItemDamage();
            metas.add(Integer.valueOf(e.ore.getItemDamage()));
            if (e.ore.getItemDamage() == com.sc.util.OreEntry.TANTALITE.meta()) {
                tant = e.weight;
            }
            if (e.ore.getItemDamage() == com.sc.util.OreEntry.CHALCOPYRITE.meta()) {
                copper = e.weight;
            }
        }
        double want = 4D * com.sc.util.OreEntry.TANTALITE.defaultVeinSize / com.sc.util.OreEntry.CHALCOPYRITE.defaultVeinSize;
        boolean ratio = copper > 0 && Math.abs(tant / (double) copper - want) < 0.15D * want;
        List<com.sc.machine.ExoOreTableSC.Entry> deep = com.sc.machine.ExoOreTableSC.substrateDeep();
        boolean deepOk = deep.size() == 2 && deep.get(0).lens < 0 && deep.get(1).lens < 0
                && deep.get(0).ore.getItem() == net.minecraft.item.Item.getItemFromBlock(com.sc.dim.SubstrateBlocksSC.rock)
                && deep.get(0).ore.getItemDamage() == com.sc.dim.SubstrateBlocksSC.MONOCRYSTAL
                && deep.get(1).ore.getItem() == net.minecraft.item.Item.getItemFromBlock(com.sc.dim.SubstrateBlocksSC.druse);
        int[] dw = com.sc.machine.ExoOreTableSC.deepWeights(900);
        boolean share = dw[0] + dw[1] == 100 && dw[0] >= 1 && dw[1] >= 1;
        String l = lang("sc.quarry.chunkLoader.substrate", "sc.tooltip.quarryModule.chunkLoader.substrate", "sc.gui.quarry.substrateTable",
                "sc.manual.quarry.exo.substrate", "sc.manual.quarry.exo.substrateChunks");
        check(modOnly && metas.size() == com.sc.util.OreEntry.values().length && ratio && deepOk && share && l.isEmpty(), "ОМ-1 the rig's Substrate"
                + " table: the mod's ores only " + modOnly + ", all " + metas.size() + " of " + com.sc.util.OreEntry.values().length + ", tantalum / copper "
                + tant + " / " + copper + " (x" + want + ") " + ratio + ", the deep pair Monocrystal + druse " + deepOk + ", their 10% " + share
                + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ОМ-2: the Substrate's loot at home. */
    private static void om2() {
        ItemStack scrap = com.sc.mob.MobItemsSC.loot(com.sc.mob.ItemCombatLootSC.LootKind.SINGULAR_SCRAP, 1);
        ItemStack thread = com.sc.mob.MobItemsSC.loot(com.sc.mob.ItemCombatLootSC.LootKind.INDUCTION_THREAD, 4);
        ItemStack plasma = com.sc.mob.MobItemsSC.loot(com.sc.mob.ItemCombatLootSC.LootKind.PLASMA_CONDENSATE, 1);
        ItemStack nanite = com.sc.mob.MobItemsSC.loot(com.sc.mob.ItemCombatLootSC.LootKind.NANITE_DUST, 4);
        ItemStack mould = new ItemStack(com.sc.init.ModItems.TOOLS.get(com.sc.util.SCToolType.MOLD_COIL));
        boolean machines = machineMakes(com.sc.machine.MachineType.CRUSHER, new ItemStack[]{scrap}, null, com.sc.init.ModItems.dust.stackOf(com.sc.util.Material.HAFNIUM))
                && machineMakes(com.sc.machine.MachineType.ROLLING_MACHINE, new ItemStack[]{thread, mould}, null, new ItemStack(com.sc.init.ModItems.component("nb3SnCoil")))
                && machineMakes(com.sc.machine.MachineType.CENTRIFUGE, new ItemStack[]{plasma}, null, null)
                && machineMakes(com.sc.machine.MachineType.CHEM_REACTOR, new ItemStack[]{nanite, new ItemStack(net.minecraft.init.Items.slime_ball)}, null,
                com.sc.init.IdeaItemsSC.nanitePaste(1));
        boolean crafts = crafted(new ItemStack(com.sc.init.ModItems.component("heLoopModule"))) >= 2
                && crafted(new ItemStack(com.sc.init.ModItems.TOOLS.get(com.sc.util.SCToolType.SEED_CRYSTAL))) >= 1;
        // the paste's repair: 1 paste first, 2 the second, 3 the third, none after; -25%; a whole tool refused
        ItemStack mask = new ItemStack(com.sc.init.ModItems.TOOLS.get(com.sc.util.SCToolType.PHOTOMASK));
        int max = mask.getMaxDamage();
        mask.setItemDamage(max - 1);
        ItemStack r1 = com.sc.init.PasteRepairRecipeSC.repair(mask, 1);
        ItemStack r2 = r1 == null ? null : com.sc.init.PasteRepairRecipeSC.repair(r1, 2);
        ItemStack r3 = r2 == null ? null : com.sc.init.PasteRepairRecipeSC.repair(r2, 3);
        ItemStack whole = new ItemStack(com.sc.init.ModItems.TOOLS.get(com.sc.util.SCToolType.PHOTOMASK));
        boolean repair = r1 != null && r1.getItemDamage() == com.sc.init.PasteRepairRecipeSC.repaired(max - 1, max)
                && com.sc.init.PasteRepairRecipeSC.repair(mask, 2) == null && r2 != null && com.sc.init.PasteRepairRecipeSC.repair(r1, 1) == null
                && r3 != null && com.sc.init.PasteRepairRecipeSC.repairs(r3) == 3 && com.sc.init.PasteRepairRecipeSC.repair(r3, 4) == null
                && com.sc.init.PasteRepairRecipeSC.repair(whole, 1) == null && com.sc.init.PasteRepairRecipeSC.pastesFor(0) == 1
                && com.sc.init.PasteRepairRecipeSC.pastesFor(2) == 3 && com.sc.init.PasteRepairRecipeSC.pastesFor(3) == 0
                && com.sc.init.PasteRepairRecipeSC.repaired(10, 16) == 6 && com.sc.init.PasteRepairRecipeSC.repaired(2, 16) == 0;
        // «never cheaper than a new one» (the review's fix - against the tools' own recipes): a life mends at most 75% of a tool; a tool
        // made of another mended tool weighs 1 + the dearest of them (SalvageSC.inputsOf - its first recipe), so mending it costs more
        // than mending what it is made of; the weight multiplies each repair's pastes
        boolean dearer = com.sc.init.PasteRepairRecipeSC.MAX_REPAIRS * com.sc.init.PasteRepairRecipeSC.REPAIR_SHARE <= 0.75F + 1e-4F;
        StringBuilder weights = new StringBuilder();
        List<ItemStack> tools = new ArrayList<ItemStack>();
        for (com.sc.util.SCToolType t : com.sc.init.PasteRepairRecipeSC.TOOLS) {
            if (com.sc.init.ModItems.TOOLS.get(t) != null) {
                tools.add(new ItemStack(com.sc.init.ModItems.TOOLS.get(t)));
            }
        }
        for (com.sc.item.ItemDrillHeadSC h : com.sc.init.ModItems.DRILL_HEADS) {
            if (h.kind.life > 0) {
                tools.add(new ItemStack(h));
            }
        }
        for (ItemStack t : tools) {
            int w = com.sc.init.PasteRepairRecipeSC.weight(t), most = 0;
            for (ItemStack in : com.sc.item.SalvageSC.inputsOf(t)) {
                most = Math.max(most, com.sc.init.PasteRepairRecipeSC.weight(in));
            }
            ItemStack worn = t.copy();
            worn.setItemDamage(worn.getMaxDamage() - 1);
            boolean ok = w == 1 + most && com.sc.init.PasteRepairRecipeSC.pastesFor(worn) == w
                    && (w == 1 || com.sc.init.PasteRepairRecipeSC.repair(worn, w - 1) == null) && com.sc.init.PasteRepairRecipeSC.repair(worn, w) != null;
            if (!ok) {
                weights.append(weights.length() == 0 ? "" : ", ").append(t.getUnlocalizedName()).append(" w").append(w).append(" parts w").append(most);
            }
        }
        dearer &= tools.size() == com.sc.init.PasteRepairRecipeSC.TOOLS.length + 3 && weights.length() == 0;
        String l = lang("item.siliconage.nanitePaste.name", "sc.tooltip.nanitePaste", "sc.tooltip.nanitePaste.details", "sc.tooltip.nanitePaste.howto",
                "sc.tooltip.pasteRepairs", "sc.tooltip.pasteRepairs.done", "sc.tooltip.pasteNext", "sc.tooltip.loot.singularScrap.howto", "sc.tooltip.loot.inductionThread.howto",
                "sc.tooltip.loot.boiledLoop.howto", "sc.tooltip.loot.plasmaCondensate.howto", "sc.tooltip.loot.naturalWafer.howto",
                "sc.tooltip.loot.naniteDust.howto");
        check(machines && crafts && repair && dearer && l.isEmpty(), "ОМ-2 the loot at home: the crusher / rolling / centrifuge / reactor recipes "
                + machines + ", the Helium Loop's second recipe and the Seed Crystal's " + crafts + ", the paste's repair (1, 2, 3 pastes, -25%, none"
                + " after 3, none on a whole tool) " + repair + ", never cheaper than new (75% a life, the weights by the recipes) " + dearer
                + (weights.length() == 0 ? "" : " - " + weights) + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ОМ-3: hafnium from zirconium. */
    private static void om3() {
        boolean fluid = net.minecraftforge.fluids.FluidRegistry.getFluid("zrcl4") != null && com.sc.util.CorrosiveFluids.isCorrosive("zrcl4")
                && "zrcl4".equals(com.sc.item.ItemFluidBucketSC.FLUIDS[24]);
        ItemStack zr = com.sc.init.ModItems.dust.stackOf(com.sc.util.Material.ZIRCONIUM).copy();
        zr.stackSize = 4;
        boolean reactor = machineMakes(com.sc.machine.MachineType.CHEM_REACTOR, new ItemStack[]{zr},
                new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.chlorine, 500), null);
        boolean refinery = machineMakes(com.sc.machine.MachineType.REFINERY, new ItemStack[0],
                new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.zrcl4, 4000), com.sc.init.ModItems.dustTiny.stackOf(com.sc.util.Material.HAFNIUM));
        boolean balance = com.sc.init.ModRecipesIdeasSC.om3ZirconiumPerPile() >= 20 && com.sc.init.ModRecipesIdeasSC.om3ChlorinePerPile() >= 0;
        String l = lang("fluid.zrcl4", "sc.book.fluid.d.zrcl4", "sc.manual.materials.hafniumZr");
        check(fluid && reactor && refinery && balance && l.isEmpty(), "ОМ-3 hafnium out of zirconium: zrcl4 registered, corrosive, the bucket's meta 24 "
                + fluid + ", the reactor's chlorides " + reactor + ", the refinery's split " + refinery + ", " + com.sc.init.ModRecipesIdeasSC.om3ZirconiumPerPile()
                + " zirconium dust and " + com.sc.init.ModRecipesIdeasSC.om3ChlorinePerPile() + " mB chlorine a small pile (no endless hafnium) " + balance
                + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ОМ-4: the EUV Lithograph and the processors. */
    private static void om4() {
        com.sc.machine.MachineType t = com.sc.machine.MachineType.EUV_LITHOGRAPH;
        boolean machine = t.ordinal() == 32 && com.sc.block.BlockMachineSC.blockFor(t) == com.sc.init.ModBlocks.machineSC3
                && com.sc.block.BlockMachineSC.metaFor(t) == 0;
        boolean metas = com.sc.util.SiliconMaterial.PROCESSOR_WAFER.ordinal() == 18 && com.sc.util.SiliconMaterial.SINGULAR_MATRIX.ordinal() == 21
                && "processorWafer".equals(com.sc.util.SiliconMaterial.PROCESSOR_WAFER.textureName)
                && "singularMatrix".equals(com.sc.util.SiliconMaterial.SINGULAR_MATRIX.textureName);
        com.sc.item.ItemSiliconMaterialSC si = com.sc.init.ModItems.siliconMaterial;
        boolean chain = recipeOut(t, si.stackOf(com.sc.util.SiliconMaterial.PROCESSOR_WAFER)) && recipeOut(com.sc.machine.MachineType.PACKAGER,
                si.stackOf(com.sc.util.SiliconMaterial.PROCESSOR)) && recipeOut(com.sc.machine.MachineType.UPGRADE_STATION_EV, si.stackOf(com.sc.util.SiliconMaterial.EXO_PROCESSOR))
                && recipeOut(com.sc.machine.MachineType.UPGRADE_STATION_EV, new ItemStack(com.sc.init.ModItems.TOOLS.get(com.sc.util.SCToolType.EUV_MASK)))
                && countOut(t, si.stackOf(com.sc.util.SiliconMaterial.SINGULAR_MATRIX)) == 2;
        StringBuilder cheap = new StringBuilder();
        for (ItemStack s : new ItemStack[]{com.sc.init.ModBlocks.generatorStack(com.sc.energy.GeneratorType.SOLAR_QUANTUM, 1),
                com.sc.init.ModBlocks.generatorStack(com.sc.energy.GeneratorType.SOLAR_EXO, 1), com.sc.init.ModBlocks.generatorStack(com.sc.energy.GeneratorType.TOKAMAK, 1),
                com.sc.init.ModBlocks.generatorStack(com.sc.energy.GeneratorType.EXO_REACTOR, 1), new ItemStack(com.sc.init.ModBlocks.energyStorageSC, 1, 5),
                new ItemStack(com.sc.init.ModBlocks.energyStorageSC, 1, 6), new ItemStack(com.sc.init.ModBlocks.singularStation)}) {
            int[] parts = partCounts(s);
            if (parts[0] < 2 || parts[1] >= parts[2]) {
                cheap.append(' ').append(s.getUnlocalizedName()).append(':').append(parts[0]).append('/').append(parts[1]).append('/').append(parts[2]);
            }
        }
        boolean nei = SelfTestIdeasSC.class.getResource("/com/sc/nei/MachineHandlersSC$EuvLithograph.class") != null;
        String l = lang("sc.machine.euv_lithograph", "tile.siliconage.machineSC3.euv_lithograph.name", "sc.manual.machine.euv_lithograph",
                "item.siliconage.siliconMaterial.processor_wafer.name", "item.siliconage.siliconMaterial.processor.name",
                "item.siliconage.siliconMaterial.exo_processor.name", "item.siliconage.siliconMaterial.singular_matrix.name", "item.siliconage.euv_mask.name",
                "sc.si.tooltip.processor_wafer", "sc.si.tooltip.processor", "sc.si.tooltip.exo_processor", "sc.si.tooltip.singular_matrix",
                "sc.si.tooltip.singular_matrix.howto", "sc.tooltip.euvMask", "sc.tool.tooltip.euv_mask", "sc.manual.path.euv");
        check(machine && metas && chain && cheap.length() == 0 && nei && l.isEmpty(), "ОМ-4 the EUV Lithograph: ordinal 32 on machineSC3 meta 0 " + machine
                + ", metas 18..21 " + metas + ", the chain (the mask, the wafer, the Processor, the Exo Processor, the matrix's two ways) " + chain
                + ", every cheaper twin cheaper, the old one kept" + (cheap.length() == 0 ? "" : " - BAD:" + cheap) + ", the NEI handler " + nei
                + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    // ------------------------------------------------------------------ recipe helpers (ОМ-2..ОМ-7)

    /** A machine recipe of `type` takes `in` (+ fluid) and gives `out` (null: any). */
    static boolean machineMakes(com.sc.machine.MachineType type, ItemStack[] in, net.minecraftforge.fluids.FluidStack fluid, ItemStack out) {
        for (ItemStack s : in) {
            if (s == null) {
                return false;
            }
        }
        com.sc.machine.MachineRecipe r = com.sc.machine.RecipeRegistry.findMatch(type, in, fluid, null);
        if (r == null) {
            return false;
        }
        if (out == null) {
            return true;
        }
        for (ItemStack o : r.outputs) {
            if (o != null && o.getItem() == out.getItem() && o.getItemDamage() == out.getItemDamage()) {
                return true;
            }
        }
        return false;
    }

    /** A machine recipe of `type` gives `out`. */
    static boolean recipeOut(com.sc.machine.MachineType type, ItemStack out) {
        return countOut(type, out) > 0;
    }

    static int countOut(com.sc.machine.MachineType type, ItemStack out) {
        int n = 0;
        for (com.sc.machine.MachineRecipe r : com.sc.machine.RecipeRegistry.recipesFor(type)) {
            for (ItemStack o : r.outputs) {
                if (o != null && out != null && o.getItem() == out.getItem() && o.getItemDamage() == out.getItemDamage()) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** The crafting recipes giving `out`. */
    static int crafted(ItemStack out) {
        int n = 0;
        for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
            ItemStack r = ((net.minecraft.item.crafting.IRecipe) o).getRecipeOutput();
            if (r != null && out != null && r.getItem() == out.getItem() && r.getItemDamage() == out.getItemDamage()) {
                n++;
            }
        }
        return n;
    }

    /** {the crafting recipes giving `out`, the parts of the last (the newest - the twin), the parts of the first (the old one)}. */
    static int[] partCounts(ItemStack out) {
        int n = 0, first = -1, last = -1;
        for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
            ItemStack r = ((net.minecraft.item.crafting.IRecipe) o).getRecipeOutput();
            if (r == null || out == null || r.getItem() != out.getItem() || r.getItemDamage() != out.getItemDamage()) {
                continue;
            }
            int parts = 0;
            Object[] in = o instanceof net.minecraftforge.oredict.ShapedOreRecipe ? ((net.minecraftforge.oredict.ShapedOreRecipe) o).getInput()
                    : o instanceof net.minecraftforge.oredict.ShapelessOreRecipe ? ((net.minecraftforge.oredict.ShapelessOreRecipe) o).getInput().toArray()
                    : new Object[0];
            for (Object x : in) {
                parts += x == null ? 0 : 1;
            }
            n++;
            if (first < 0) {
                first = parts;
            }
            last = parts;
        }
        return new int[]{n, last, first};
    }

    /** ОМ-5: comparators, the Data Port. */
    private static void om5() {
        boolean level = com.sc.energy.ComparatorSC.level(0, 1000) == 0 && com.sc.energy.ComparatorSC.level(1, 1000) == 1
                && com.sc.energy.ComparatorSC.level(500, 1000) == 8 && com.sc.energy.ComparatorSC.level(1000, 1000) == 15
                && com.sc.energy.ComparatorSC.level(5, 0) == 0;
        com.sc.tileentity.TileEntityDataPortSC a = new com.sc.tileentity.TileEntityDataPortSC();
        boolean toggled = a.mode() == com.sc.tileentity.TileEntityDataPortSC.STOP && a.toggleMode() == com.sc.tileentity.TileEntityDataPortSC.PERMIT;
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        a.writeToNBT(t);
        com.sc.tileentity.TileEntityDataPortSC b = new com.sc.tileentity.TileEntityDataPortSC();
        b.readFromNBT(t);
        net.minecraft.nbt.NBTTagCompound old = new net.minecraft.nbt.NBTTagCompound();
        new com.sc.tileentity.TileEntityDataPortSC().writeToNBT(old);
        old.removeTag(com.sc.tileentity.TileEntityDataPortSC.NBT);
        com.sc.tileentity.TileEntityDataPortSC c = new com.sc.tileentity.TileEntityDataPortSC();
        c.readFromNBT(old);
        boolean nbt = toggled && b.mode() == com.sc.tileentity.TileEntityDataPortSC.PERMIT && c.mode() == com.sc.tileentity.TileEntityDataPortSC.STOP
                && b.comparatorLevel() == 0;
        boolean blocks = com.sc.init.ModBlocks.machineSC.hasComparatorInputOverride() && com.sc.init.ModBlocks.generatorSC.hasComparatorInputOverride()
                && com.sc.init.ModBlocks.quarrySC.hasComparatorInputOverride() && com.sc.init.IdeaBlocksSC.dataPort.hasComparatorInputOverride()
                && com.sc.energy.IComparatorSC.class.isAssignableFrom(com.sc.tileentity.TileEntityMachineSC.class)
                && com.sc.energy.IComparatorSC.class.isAssignableFrom(com.sc.tileentity.TileEntityGeneratorSC.class)
                && com.sc.energy.IComparatorSC.class.isAssignableFrom(com.sc.tileentity.TileEntityQuarrySC.class)
                && com.sc.tileentity.TileEntityGeneratorSC.EVENT_PORT == 3;
        boolean recipe = crafted(com.sc.init.IdeaBlocksSC.dataPort(1)) > 0;
        String l = lang("tile.siliconage.dataPort.name", "sc.tooltip.dataPort", "sc.tooltip.dataPort.details", "sc.tooltip.dataPort.howto",
                "sc.dataport.mode.stop", "sc.dataport.mode.permit", "sc.dataport.unbound", "sc.gen.port.noPermit", "sc.gen.port.stopped",
                "sc.manual.energy.comparator", "sc.manual.energy.dataPort");
        check(level && nbt && blocks && recipe && l.isEmpty(), "ОМ-5 comparators and the Data Port: ComparatorSC.level (0, 1/1000, half, full, max 0) "
                + level + ", the port's mode STOP -> PERMIT, its NBT both ways, an old tile STOP " + nbt + ", machines / generators / quarries "
                + "give the comparator their reading, the port's stop event 3 " + blocks + ", the recipe " + recipe + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ОМ-6: salvage. */
    private static void om6() {
        net.minecraft.item.Item trophy = com.sc.mob.MobItemsSC.trophy;
        boolean refusals = com.sc.item.SalvageSC.refusal(new ItemStack(trophy, 1, com.sc.mob.TrophyTableSC.META_PE1M)) == com.sc.item.SalvageSC.R_SINGULAR
                && com.sc.item.SalvageSC.refusal(new ItemStack(trophy, 1, 12)) == com.sc.item.SalvageSC.R_SINGULAR
                && com.sc.item.SalvageSC.refusal(new ItemStack(net.minecraft.init.Blocks.stone)) == com.sc.item.SalvageSC.R_NOTHING
                && com.sc.item.SalvageSC.refusal(null) == com.sc.item.SalvageSC.R_NOTHING
                && com.sc.item.SalvageSC.refusal(new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[0])) == com.sc.item.SalvageSC.OK
                && com.sc.item.SalvageSC.refusal(new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR)[1])) == com.sc.item.SalvageSC.R_SINGULAR
                && com.sc.item.SalvageSC.refusal(new ItemStack(com.sc.init.ModItems.empGun, 1, com.sc.emp.ItemEmpGunSC.META_UPGRADED)) == com.sc.item.SalvageSC.R_SINGULAR;
        // a Quantum chestplate with two chips: the chips back whole, then 25..35% of its parts (at least one)
        ItemStack chest = new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM)[1]);
        com.sc.item.ItemArmorSC.chipsTag(chest).setInteger(com.sc.util.ChipType.SENSOR.name(), 2);
        com.sc.item.ItemArmorSC.chipsTag(chest).setInteger(com.sc.util.ChipType.POWER.name(), 1);
        List<ItemStack> got = com.sc.item.SalvageSC.result(chest);
        int chips = 0, parts = 0, partMax = 0;
        for (ItemStack s : got) {
            if (s.getItem() == com.sc.init.ModItems.armorChip) {
                chips += s.stackSize;
            } else {
                parts += s.stackSize;
            }
        }
        for (ItemStack s : com.sc.item.SalvageSC.inputsOf(new ItemStack(chest.getItem()))) {
            partMax += com.sc.item.SalvageSC.refusal(s) == com.sc.item.SalvageSC.R_NOTHING ? s.stackSize : 0;
        }
        boolean own = chips == 2 && parts >= 1 && parts <= Math.max(1, (int) Math.floor(partMax * com.sc.item.SalvageSC.SHARE_MAX));
        // every trophy meta but the Singular ones: no better than restoring it
        StringBuilder bad = new StringBuilder();
        int salvaged = 0;
        for (int m = 0; m < com.sc.mob.TrophyTableSC.METAS; m++) {
            ItemStack s = new ItemStack(trophy, 1, m);
            if (com.sc.mob.TrophyRestoreSC.singular(m)) {
                if (com.sc.item.SalvageSC.refusal(s) != com.sc.item.SalvageSC.R_SINGULAR) {
                    bad.append(" singular:").append(m);
                }
                continue;
            }
            List<ItemStack> r = com.sc.item.SalvageSC.result(s);
            if (r.isEmpty() || !com.sc.item.SalvageSC.restoreBound(m, r)) {
                bad.append(' ').append(m).append(r.isEmpty() ? ":empty" : ":over");
            } else {
                salvaged++;
            }
        }
        float sh = com.sc.item.SalvageSC.share(chest);
        boolean share = sh >= com.sc.item.SalvageSC.SHARE_MIN && sh <= com.sc.item.SalvageSC.SHARE_MAX && com.sc.item.SalvageSC.shareOf(4, 0.3F) == 1;
        // the station: slot 8 is the salvage slot; an older station's NBT has none
        com.sc.tileentity.TileEntityArmorStationSC st = new com.sc.tileentity.TileEntityArmorStationSC();
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        st.writeToNBT(t);
        com.sc.tileentity.TileEntityArmorStationSC back = new com.sc.tileentity.TileEntityArmorStationSC();
        back.readFromNBT(t);
        boolean slot = com.sc.tileentity.TileEntityArmorStationSC.SALVAGE_SLOT == 8 && back.getSizeInventory() == 9
                && back.getStackInSlot(com.sc.tileentity.TileEntityArmorStationSC.SALVAGE_SLOT) == null && back.salvages()
                && !new com.sc.tileentity.TileEntitySingularStationSC().salvages()
                && back.isItemValidForSlot(8, new ItemStack(trophy, 1, 0)) && !back.isItemValidForSlot(8, new ItemStack(net.minecraft.init.Blocks.stone));
        String l = lang("sc.salvage.slot", "sc.salvage.slot.tip", "sc.salvage.btn", "sc.salvage.confirm", "sc.salvage.preview", "sc.salvage.done",
                "sc.salvage.refuse.nothing", "sc.salvage.refuse.singular", "sc.salvage.refuse.noEu", "sc.tooltip.trophy.salvage",
                "sc.manual.armor.station.salvage");
        check(refusals && own && bad.length() == 0 && share && slot && l.isEmpty(), "ОМ-6 salvage: the refusals (Singular trophy / piece, ПЭ-1М, "
                + "stone, a Nano helmet) " + refusals + ", a Quantum chestplate with 2 chips - the chips whole + " + parts + " of " + partMax + " parts "
                + own + ", " + salvaged + " trophy metas no better than restoring" + (bad.length() == 0 ? "" : " - BAD:" + bad) + ", the share "
                + share + ", the station's slot 8 (an older station empty, not the Singular station's) " + slot + (l.isEmpty() ? "" : " - LANG:" + l));
    }

    /** ОМ-7: the Energy Bank. */
    private static void om7() {
        boolean sizes = com.sc.energy.EnergyBankMathSC.validSize(3) && com.sc.energy.EnergyBankMathSC.validSize(5)
                && com.sc.energy.EnergyBankMathSC.validSize(7) && !com.sc.energy.EnergyBankMathSC.validSize(4)
                && !com.sc.energy.EnergyBankMathSC.validSize(9) && com.sc.energy.EnergyBankMathSC.cellSlots(3) == 1
                && com.sc.energy.EnergyBankMathSC.cellSlots(5) == 27 && com.sc.energy.EnergyBankMathSC.cellSlots(7) == 125;
        boolean numbers = com.sc.energy.EnergyBankMathSC.capacity(0, 125) == 2000000000000L
                && com.sc.energy.EnergyBankMathSC.capacity(1, 1) == 20000000000L
                && com.sc.energy.EnergyBankMathSC.portRate(com.sc.energy.Tier.SV) == 524288L
                && com.sc.energy.EnergyBankMathSC.scaledCapacity(0, 125, 10F) == 20000000000000L;
        // a 3 x 3 x 3: casing everywhere, the controller in the middle of a face, a port on another, a cell inside
        int[] k = new int[27];
        java.util.Arrays.fill(k, com.sc.energy.EnergyBankMathSC.K_CASING);
        int ctl = 1 + 3 * (1 + 3 * 0), port = 1 + 3 * (1 + 3 * 2), mid = 1 + 3 * (1 + 3 * 1);
        k[ctl] = com.sc.energy.EnergyBankMathSC.K_CONTROLLER;
        k[port] = com.sc.energy.EnergyBankMathSC.K_PORT_FIRST;
        k[mid] = com.sc.energy.EnergyBankMathSC.K_CELL_4;
        boolean good = com.sc.energy.EnergyBankMathSC.check(3, k) == com.sc.energy.EnergyBankMathSC.OK;
        int[] edge = k.clone();
        edge[0] = com.sc.energy.EnergyBankMathSC.K_CELL_16;
        int[] two = k.clone();
        two[port] = com.sc.energy.EnergyBankMathSC.K_CONTROLLER;
        int[] hole = k.clone();
        hole[mid] = com.sc.energy.EnergyBankMathSC.K_NONE;
        int[] none = k.clone();
        none[ctl] = com.sc.energy.EnergyBankMathSC.K_GLASS;
        boolean bad = com.sc.energy.EnergyBankMathSC.badReason(com.sc.energy.EnergyBankMathSC.check(3, edge)) == com.sc.energy.EnergyBankMathSC.R_EDGE
                && com.sc.energy.EnergyBankMathSC.badIndex(com.sc.energy.EnergyBankMathSC.check(3, edge)) == 0
                && com.sc.energy.EnergyBankMathSC.badReason(com.sc.energy.EnergyBankMathSC.check(3, two)) == com.sc.energy.EnergyBankMathSC.R_CONTROLLERS
                && com.sc.energy.EnergyBankMathSC.badReason(com.sc.energy.EnergyBankMathSC.check(3, hole)) == com.sc.energy.EnergyBankMathSC.R_INSIDE
                && com.sc.energy.EnergyBankMathSC.badIndex(com.sc.energy.EnergyBankMathSC.check(3, hole)) == mid
                && com.sc.energy.EnergyBankMathSC.badReason(com.sc.energy.EnergyBankMathSC.check(3, none)) == com.sc.energy.EnergyBankMathSC.R_CONTROLLERS
                && com.sc.energy.EnergyBankMathSC.badReason(com.sc.energy.EnergyBankMathSC.check(4, new int[64])) == com.sc.energy.EnergyBankMathSC.R_SIZE;
        // the tiles' NBT: the controller's long energy, the port's mode and tier
        com.sc.tileentity.TileEntityEnergyBankSC c = new com.sc.tileentity.TileEntityEnergyBankSC();
        c.setEnergyFromItem(123456789012L);
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        c.writeToNBT(t);
        com.sc.tileentity.TileEntityEnergyBankSC c2 = new com.sc.tileentity.TileEntityEnergyBankSC();
        c2.readFromNBT(t);
        com.sc.tileentity.TileEntityEnergyBankPortSC p = new com.sc.tileentity.TileEntityEnergyBankPortSC(com.sc.energy.Tier.HV);
        p.toggleOut();
        net.minecraft.nbt.NBTTagCompound pt = new net.minecraft.nbt.NBTTagCompound();
        p.writeToNBT(pt);
        com.sc.tileentity.TileEntityEnergyBankPortSC p2 = new com.sc.tileentity.TileEntityEnergyBankPortSC();
        p2.readFromNBT(pt);
        boolean nbt = c2.energy() == 123456789012L && !c2.formed() && p2.isOut() && p2.isEnergySource() && !p2.isEnergySink()
                && p2.getTier() == com.sc.energy.Tier.HV && p2.packetsPerTick() == com.sc.energy.EnergyBankMathSC.PORT_AMPS;
        int recipes = 0;
        for (int m = 0; m < com.sc.block.BlockEnergyBankSC.METAS; m++) {
            recipes += crafted(com.sc.init.IdeaBlocksSC.energyBank(m, 1)) > 0 ? 1 : 0;
        }
        List<String> keys = new ArrayList<String>();
        for (String n : com.sc.block.BlockEnergyBankSC.NAMES) {
            keys.add("tile.siliconage.energyBank." + n + ".name");
        }
        for (int r = 1; r < com.sc.energy.EnergyBankMathSC.REASON_KEYS.length; r++) {
            keys.add("sc.gui.bank.reason." + com.sc.energy.EnergyBankMathSC.REASON_KEYS[r]);
        }
        String l = lang(keys.toArray(new String[keys.size()])) + lang("sc.tooltip.energyBank.casing", "sc.tooltip.energyBank.controller",
                "sc.tooltip.energyBank.cell", "sc.tooltip.energyBank.port", "sc.tooltip.energyBank.details", "sc.tooltip.energyBank.howto",
                "sc.tooltip.energyBank.stored", "sc.gui.bank.title", "sc.gui.bank.charge", "sc.gui.bank.size", "sc.gui.bank.cells",
                "sc.gui.bank.ports", "sc.gui.bank.flow", "sc.gui.bank.graph", "sc.gui.bank.unformed", "sc.bank.port.in", "sc.bank.port.out",
                "sc.manual.energy.bank.title", "sc.manual.energy.bank.1", "sc.manual.energy.bank.2");
        check(sizes && numbers && good && bad && nbt && recipes == com.sc.block.BlockEnergyBankSC.METAS && l.isEmpty(), "ОМ-7 the Energy Bank: "
                + "the sizes (3/5/7, 4 and 9 no; 1/27/125 cells) " + sizes + ", the capacity (125 x 16 billion = 2 x 10^12, x10) and SV's port 524288 "
                + numbers + ", a 3 x 3 x 3 good " + good + ", a cell on an edge / two controllers / a hole / no controller / side 4 refused " + bad
                + ", the controller's and the port's NBT " + nbt + ", recipes " + recipes + " of " + com.sc.block.BlockEnergyBankSC.METAS
                + (l.isEmpty() ? "" : " - LANG:" + l));
    }
}
