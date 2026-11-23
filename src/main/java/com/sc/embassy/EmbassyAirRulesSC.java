package com.sc.embassy;

import com.sc.settle.ReputationSC;
import com.sc.settle.trade.TradeRulesSC;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §7, docs/idea-embassy-airfield.md §7, rules ЛП-10..ЛП-16): every number and
 * pure decision of the airfield's services, the «Лётная книжка» and the embassy's air defence - written whole by the coordinator (as
 * EmbassyRulesSC in Ф6); package R's EmbassyAirServiceSC does them on the server, the API answers the add-on by them, package D tests
 * them. All numbers TODO(balance).
 * <ul>
 * <li><b>The services</b> (§7's table, the user's choice 7): a service sold from MIN_LEVEL (the emergency one only up to MAX_LEVEL), BASE
 * chips a UNIT of it (the add-on's units: a game day of parking, EU, mB, % of wear; the core's own: the armour's mB), at most
 * PER_SERVICE units a service and PER_DAY units a game day at one embassy (0: no cap); the price = the base x the level's multiplier x
 * the embassy's spread (TradeRulesSC.playerSide: ReputationSC.PRICE x (100 +- 10) %, rounded up). Free: parking while the player holds a
 * talon or a return half of this embassy (EmbassyTicketSC) - the add-on asks the first free day itself (FREE_PARK_DAYS), the tow once a
 * game day (FREE_TOWS), the first approach chart. No service sells an item (ПО-7, the embassies' decision 7: the Capsule and the Map only);
 * no «4 services a day» cap - the caps are the resources' (a liner's turn-round is three services);</li>
 * <li><b>the codes</b> of a payment (EmbassyAirApiSC.pay): PAY_OK .. PAY_UNKNOWN (PAY_KEYS - lang sc.airfield.pay.<key>), checked in
 * refusal's order: no field / unknown service, «Враг», the level, the caps, then the chips (the server's);</li>
 * <li><b>the «Лётная книжка»</b> (the user's choice 9): the first landing at each embassy (the add-on sees it: the transponder on, the
 * machine stopped on a stand) - LOGBOOK_CHIPS Credit Chips (within the day's 64 - TradeRulesSC.CHIPS_PER_DAY) and LOGBOOK_TRUST
 * «Доверия» (ReputationSC.SRC_AIR, under the embassy's ceiling 599), once an embassy a player;</li>
 * <li><b>the air defence</b> (the user's choice 6, confirmed 2026-10-26; ЛП-1 of the audit 2026-11-04: the fire only over the embassy's
 * square and its approach corridors - AD_FIRE_MARGIN, the rest of the circle only warns): a player's machine IN THE AIR inside AD_RADIUS of the core with
 * nobody admitted on board - admitted: «Знакомый» (ADMIT_LEVEL) or higher at this embassy, never its «Враг» - is warned (chat and the
 * add-on's radio) and gets WARN_SECONDS to turn away; then the add-on fires from the emplacements. Never at a player on foot or flying
 * in armour, never at anything on the ground; a non-admitted mate of an admitted player's squad (SquadSC) within ESCORT_RANGE of him,
 * or a passenger / crew of an admitted pilot, is no target; a 7700 squawk gives one PASS_SECONDS hold-fire pass a player an embassy a
 * game day (the core's ledger: LocalRec.extra "X" {d, u}). The review's: the warning too is one a player an embassy a game day ("X" {w,
 * e}: back into the zone after it - the fire at once), and its seconds are to turn AWAY - a warned machine that closes on the field or
 * descends to it meanwhile is fired on at once (the add-on's check: landing inside the warning is no way in); nothing is fired over a
 * player's base (EmbassyAirfieldSC.playerBaseAt).</li>
 * </ul>
 */
public final class EmbassyAirRulesSC {

    /** The services, by index (append only): parking, the emergency refuel, charging, hydrogen, the armour's gases (the core's own), repair, «Электролит», the tow, the approach chart. */
    public static final int S_PARK = 0, S_EMERGENCY = 1, S_CHARGE = 2, S_HYDROGEN = 3, S_ARMOUR_GAS = 4, S_REPAIR = 5, S_ELECTROLYTE = 6,
            S_TOW = 7, S_CHART = 8;
    /** The services' keys (the API's serviceKey; lang sc.airfield.service.<key>; LocalRec.counts "air.<key>"). */
    public static final String[] SERVICE_KEYS = {"park", "emergency", "charge", "hydrogen", "armourGas", "repair", "electrolyte", "tow", "chart"};
    /** The lowest level a service is sold at (VIOLATOR: everyone but «Враг»). */
    public static final int[] MIN_LEVEL = {ReputationSC.STRANGER, ReputationSC.VIOLATOR, ReputationSC.ACQUAINTANCE, ReputationSC.ACQUAINTANCE,
            ReputationSC.ACQUAINTANCE, ReputationSC.ACQUAINTANCE, ReputationSC.ACQUAINTANCE, ReputationSC.VIOLATOR, ReputationSC.STRANGER};
    /** The highest level a service is sold at (the emergency one is for «Чужак» and «Нарушитель»: the rest buy the real thing). */
    public static final int[] MAX_LEVEL = {ReputationSC.HONOURED, ReputationSC.STRANGER, ReputationSC.HONOURED, ReputationSC.HONOURED,
            ReputationSC.HONOURED, ReputationSC.HONOURED, ReputationSC.HONOURED, ReputationSC.HONOURED, ReputationSC.HONOURED};
    /** Chips a unit (before the level and the spread). */
    public static final int[] BASE = {1, 1, 1, 1, 1, 1, 2, 1, 1};
    /** One unit: a game day; a refuel; 2 000 000 EU; 8 000 mB; 4 000 mB; 10 % of wear; one; one tow; one chart. */
    public static final int[] UNIT = {1, 1, 2000000, 8000, 4000, 10, 1, 1, 1};
    /** At most this much a service (in the service's measure - EU, mB, % ...; 0: no cap): 16 M EU (a ship's 256 M is never charged here), 48 000 mB (a liner's tank: 6 chips), 60 % (6 chips). */
    public static final int[] PER_SERVICE = {0, 0, 16000000, 48000, 0, 60, 0, 0, 0};
    /** At most this much a game day at one embassy (0: no cap): one emergency refuel, 32 M EU, 64 000 mB of hydrogen, 16 000 mB of the armour's gases. */
    public static final int[] PER_DAY = {0, 1, 32000000, 64000, 16000, 0, 0, 0, 0};
    /** The emergency refuel / charge: at most this share of the tank and the buffer (the add-on measures it). */
    public static final int EMERGENCY_PCT = 25;
    /** Free: parking's first game days (the add-on counts them); tows a game day; approach charts (the first). */
    public static final int FREE_PARK_DAYS = 1, FREE_TOWS = 1, FREE_CHARTS = 1;
    /** Stands a player may hold at one embassy (the add-on's parking). */
    public static final int SPOTS_PER_PLAYER = 2;
    /** LocalRec.counts' prefix of the services' day counters (the trade's own deals never start so). */
    public static final String COUNT_PREFIX = "air.";

    /** The payment's codes (EmbassyAirApiSC.PAY_* - the same numbers; append only). */
    public static final int PAY_OK = 0, PAY_ENEMY = 1, PAY_LEVEL = 2, PAY_LIMIT = 3, PAY_CHIPS = 4, PAY_NO_FIELD = 5, PAY_UNKNOWN = 6;
    public static final String[] PAY_KEYS = {"ok", "enemy", "level", "limit", "chips", "noField", "unknown"};

    /** The «Лётная книжка»: chips and «Доверие» for the first landing at an embassy. */
    public static final int LOGBOOK_CHIPS = 2, LOGBOOK_TRUST = 5;

    /**
     * The air defence: its circle across from the core (any height - ЛП-1 of the audit 2026-11-04: only the warning «вы приближаетесь к
     * зоне ПВО» there now); the warning's time to turn away; a 7700 pass; a squad mate's escort reach.
     */
    public static final int AD_RADIUS = 256, WARN_SECONDS = 10, PASS_SECONDS = 60, ESCORT_RANGE = 64;
    /**
     * ЛП-1 (the audit 2026-11-04, the user's choice А): the guns fire only over the embassy's own square (the level disc + its blend band,
     * EmbassyLayoutSC.LEVEL_OUT + BLEND past the dome, + AD_FIRE_MARGIN: about 145 across from the core - the land the embassy checked
     * was not explored) and its approach corridors (Field.approaches), inside AD_RADIUS - the ring past it may hold the players' bases
     * (EmbassyAirfieldSC.Field.inFireZone). TODO(balance)
     */
    public static final int AD_FIRE_MARGIN = 10;
    /** The air defence lets a player's machine through from this level on (and never its «Враг»). */
    public static final int ADMIT_LEVEL = ReputationSC.ACQUAINTANCE;
    /** The 7700 ledger's keys (LocalRec.extra "X"): the game day of the last pass, the world time its hold-fire ends. */
    public static final String LEDGER = "X", PASS_DAY = "d", PASS_UNTIL = "u";
    /**
     * The warning's ledger (the review's, beside the 7700 one in LocalRec.extra "X"): the game day of the player's warning there, the world
     * time its WARN_SECONDS to turn away end - one warning a player an embassy a game day (TODO(balance)): out of the zone and back in, he
     * gets no new seconds.
     */
    public static final String WARN_DAY = "w", WARN_END = "e";
    /** The «Лётная книжка»'s key (PlayerRec.extra "X"): the embassies of the first landings (int[]). */
    public static final String LOGBOOK = "X", LANDED = "f";

    /** The core's column: the armour's gases it fills (hydrogen, oxygen), the second click to pay within this (ticks). */
    public static final com.sc.util.ArmorGasSC.Gas[] COLUMN_GASES = {com.sc.util.ArmorGasSC.Gas.HYDROGEN, com.sc.util.ArmorGasSC.Gas.OXYGEN};
    public static final int COLUMN_CONFIRM_TICKS = 100;

    private EmbassyAirRulesSC() {
    }

    /** A service's index by its key, -1 unknown. */
    public static int service(String key) {
        for (int i = 0; key != null && i < SERVICE_KEYS.length; i++) {
            if (SERVICE_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** The lang key of a payment's code (a bad one: unknown's). */
    public static String payKey(int code) {
        return "sc.airfield.pay." + PAY_KEYS[code >= 0 && code < PAY_KEYS.length ? code : PAY_UNKNOWN];
    }

    /** Units of `amount` (in the service's measure) rounded up to whole UNITs (0 for nothing). */
    public static int units(int service, long amount) {
        if (service < 0 || service >= UNIT.length || amount <= 0) {
            return 0;
        }
        long u = amount / UNIT[service] + (amount % UNIT[service] != 0L ? 1L : 0L);     // no overflow near Long.MAX_VALUE (the review's)
        return (int) Math.min(Integer.MAX_VALUE, u);
    }

    /**
     * What refuses `service` for `amount` (its measure) to a player of `level` at an embassy (`enemy` - its «Враг»; `usedToday` of the
     * service there today) - PAY_OK when nothing (the chips come after, on the server). Pure; refusal's order is the class comment's.
     */
    public static int refusal(int service, int level, boolean enemy, long amount, long usedToday) {
        if (service < 0 || service >= SERVICE_KEYS.length || amount < 0) {
            return PAY_UNKNOWN;
        }
        if (enemy || level <= ReputationSC.ENEMY) {
            return PAY_ENEMY;
        }
        if (level < MIN_LEVEL[service] || level > MAX_LEVEL[service]) {
            return PAY_LEVEL;
        }
        if (PER_SERVICE[service] > 0 && amount > PER_SERVICE[service] || PER_DAY[service] > 0 && amount > PER_DAY[service] - Math.max(0L, usedToday)) {
            return PAY_LIMIT;
        }
        return PAY_OK;
    }

    /**
     * The chips `amount` of `service` costs a player of `level` at an embassy of `seed` (`free`: parking with a talon, the day's free
     * tow, the first chart - 0). base x whole units, the level's multiplier and the spread (TradeRulesSC.playerSide: rounded up; at most
     * Integer.MAX_VALUE - nobody holds that many: PAY_CHIPS). Pure.
     */
    public static int price(int service, int level, long seed, long amount, boolean free) {
        int u = units(service, amount);
        if (free || u <= 0 || service < 0 || service >= BASE.length) {
            return 0;
        }
        long base = (long) BASE[service] * u;                   // the review's: an amount past 2^30 must not wrap to one chip
        return TradeRulesSC.playerSide((int) Math.min(Integer.MAX_VALUE, base), level, seed, COUNT_PREFIX + SERVICE_KEYS[service]);
    }

    /** ЛП-13: the air defence lets a player of `level` (at this embassy; `enemy` - its «Враг») through. Pure. */
    public static boolean admitted(int level, boolean enemy) {
        return !enemy && level >= ADMIT_LEVEL;
    }

    /** ЛП-15: a 7700 pass may be given today (`lastDay` - the day of the last one, -1 never). Pure. */
    public static boolean passAvailable(long lastDay, long today) {
        return lastDay < today;
    }

    /**
     * The review's: the end of a player's turn-away at world time `now` of game day `today` - a new warning (`now` + WARN_SECONDS) when
     * none was given him there today (`warnDay` - the day of the last one, -1 never), else today's end `warnEnd` (past: no new seconds -
     * the fire at once). Pure.
     */
    public static long warnEnd(long warnDay, long warnEnd, long today, long now) {
        return warnDay == today ? warnEnd : now + WARN_SECONDS * 20L;
    }

    /** ЛП-16: the «Лётная книжка»'s chips that still fit today's 64 with `givenToday` given (TradeRulesSC.chipsAllowed). Pure. */
    public static int logbookChips(int givenToday) {
        return TradeRulesSC.chipsAllowed(givenToday, LOGBOOK_CHIPS);
    }

    /** The armour's gases: the units (UNIT[S_ARMOUR_GAS] mB each, rounded up) of `mb`. Pure. */
    public static int armourUnits(int mb) {
        return units(S_ARMOUR_GAS, mb);
    }
}
