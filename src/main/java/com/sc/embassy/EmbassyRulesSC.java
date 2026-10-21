package com.sc.embassy;

import com.sc.bridge.BridgeMathSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.travel.TravelRulesSC;

/**
 * Подложка Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md §7, rules ПЯ-1..ПЯ-16): the Great Ring's flights as pure
 * numbers and decisions - the window, the rides, their levels, prices and limits, the round-trip ticket, the rescue flight, the
 * guarantee, the soft admission. Written whole by the coordinator (as WardenRulesSC, CaravanRulesSC in Ф5): package R runs them
 * (FlightServerSC, FlightSC), package D tests and prints them (the handbook's tables are made of these). The proposal's numbers
 * (docs/idea-embassy-substrate.md §6, §10, §14); every one TODO(balance) after the build is played.
 * <ul>
 * <li>The window (ПЯ-3): the first paid passenger starts it - COUNTDOWN_TICKS (10 s), then the vortex VORTEX_TICKS (the bridge's
 * SPACE_LIFE_S, 20 s) during which a passenger with a talon who steps into it is carried, then COOL_TICKS (the bridge's COOL_S, 60 s)
 * when nothing opens; PASSENGERS (4) a window, each with his own target (friends by LAN fly in one window).</li>
 * <li>The rides (ПЯ-4..ПЯ-9): out of an embassy - INTRO (to «Первый причал», «Знакомый», once a player), FIRST (to «Первый
 * причал», «Партнёр»), DOCK (to a dock the player knows, «Партнёр»), RESCUE (to the player's death mark, scattered, «Знакомый»,
 * outside the day's limit: one a death, within RESCUE_TICKS of it); back from a Substrate dock with a Dispatcher - HOME (to the
 * embassy the player flew out of, «Знакомый»), BACK (the return half of a round-trip ticket, already paid, within BACK_TICKS),
 * OTHER (from a City of the Substrate to another embassy the player has been to, «Свой», OTHER_PER_DAY).</li>
 * <li>The day's limit (ПЯ-10): FLIGHTS_PER_DAY (2) for every ride but the rescue, there and back together, over every embassy and
 * dock (PlayerRec.globalCounts COUNT_RIDES, period a day).</li>
 * <li>Prices (ПЯ-11): RIDE_BASE x the level's multiplier (ReputationSC.PRICE: «Знакомый» x1.25, «Партнёр» x1, «Свой» x0.9,
 * «Почётный» x0.85) x the settlement's spread (TradeRulesSC.seedPct of the ride's key, +-10%), rounded up - TradeRulesSC.playerSide,
 * the deals' own maths. The round trip (ПЯ-12): (the ride + HOME) x ROUND_TRIP_PCT, rounded up (24 + 20 -> 40).</li>
 * <li>The guarantee (ПЯ-13): a «Партнёр» pays a friend's ride (the friend at least «Чужак», not this embassy's «Враг», within
 * NEAR_DISPATCHER of the Dispatcher) at the friend's ride x GUARANTEE_PCT by the guarantor's own multiplier; GUARANTEE_PER_DAY; the
 * friend made «Враг» of that embassy within GUARANTEE_DAYS - the guarantor GUARANTEE_PENALTY.</li>
 * <li>The soft admission (ПЯ-14): no piece of this mod's armour worn, or no Return Capsule carried - a warning, and the ride needs a
 * second click within CONFIRM_TICKS.</li>
 * </ul>
 * Shared core: only the coordinator edits this file (the numbers may be tuned on a package's report).
 */
public final class EmbassyRulesSC {

    // ------------------------------------------------------------------ the window (ПЯ-3)

    /** The countdown after the first paid passenger, the vortex, the cooling (ticks). TODO(balance) */
    public static final int COUNTDOWN_TICKS = 200, VORTEX_TICKS = BridgeMathSC.SPACE_LIFE_S * 20, COOL_TICKS = BridgeMathSC.COOL_S * 20;
    /** Passengers of one window at most (their talons; a fifth waits for the next one). */
    public static final int PASSENGERS = 4;
    /** The window's phases (FlightSC; the ring's tile shows them - TileEntityEmbassyRingSC.phase). Append only. */
    public static final int PHASE_IDLE = 0, PHASE_COUNTDOWN = 1, PHASE_VORTEX = 2, PHASE_COOL = 3;

    // ------------------------------------------------------------------ the rides (ПЯ-4..ПЯ-9)

    /** The rides (FLIGHT_DO "ride", the talon's "r", lang sc.flight.ride.<RIDE_KEYS[ride]>). Append only. */
    public static final int RIDE_INTRO = 0, RIDE_FIRST = 1, RIDE_DOCK = 2, RIDE_RESCUE = 3, RIDE_HOME = 4, RIDE_BACK = 5, RIDE_OTHER = 6;
    public static final String[] RIDE_KEYS = {"intro", "first", "dock", "rescue", "home", "back", "other"};
    /** The base price in Credit Chips («Партнёр»'s, before the spread); BACK is paid with its ticket. TODO(balance) */
    public static final int[] RIDE_BASE = {12, 24, 32, 20, 20, 0, 40};
    /** The level a ride needs (ReputationSC levels; BACK - paid already: anyone but the dock's «Враг»). */
    public static final int[] RIDE_LEVEL = {ReputationSC.ACQUAINTANCE, ReputationSC.PARTNER, ReputationSC.PARTNER, ReputationSC.ACQUAINTANCE,
            ReputationSC.ACQUAINTANCE, ReputationSC.VIOLATOR, ReputationSC.OWN};
    /** The target values of a talon that aren't a settlement id: «Первый причал» not made yet (the pure plan's dock), the death mark. */
    public static final int TARGET_FIRST_PURE = -2, TARGET_MARK = -3;

    /** ПЯ-10: flights a day there and back together (every ride but the rescue), over every embassy and dock. TODO(balance) */
    public static final int FLIGHTS_PER_DAY = 2;
    /** ПЯ-9: flights to another embassy a day; ПЯ-13: guarantees a day. */
    public static final int OTHER_PER_DAY = 1, GUARANTEE_PER_DAY = 1;
    /** The global counters (PlayerRec.globalCounts, period a day). */
    public static final String COUNT_RIDES = "embRide", COUNT_OTHER = "embOther", COUNT_GUARANTEE = "embGuar";

    /** ПЯ-12: a round trip costs (the ride + HOME) x this %, rounded up; its return half is good this long. TODO(balance) */
    public static final int ROUND_TRIP_PCT = 90;
    public static final long BACK_TICKS = 3L * SettlementSC.DAY;
    /** ПЯ-7: the rescue flight - within this of the death, one a death; its end scatters as the bridge's to a mark. */
    public static final long RESCUE_TICKS = 2L * SettlementSC.DAY;
    public static final int RESCUE_SCATTER = TravelRulesSC.DEATH_SCATTER;

    /** ПЯ-13: the guarantee's price %, the guarantor's and the friend's least levels, the penalty, its days; the friend stands this near. */
    public static final int GUARANTEE_PCT = 150, GUARANTOR_LEVEL = ReputationSC.PARTNER, FRIEND_LEVEL = ReputationSC.STRANGER, GUARANTEE_PENALTY = -30;
    public static final long GUARANTEE_TICKS = 3L * SettlementSC.DAY;
    public static final int NEAR_DISPATCHER = 8;

    /** ПЯ-14: the soft admission's second click must come within this. */
    public static final int CONFIRM_TICKS = 100;
    /** ПЯ-15: a Substrate dock's passengers board on its plate - this far across from the beacon (the 7 x 7 plate), any height of its zone. */
    public static final int BOARD_DOCK = 3;

    /** Why a ride is refused (lang sc.flight.refuse.<REFUSE_KEYS[code]>); OK none. Append only. */
    public static final int OK = 0, R_OFF = 1, R_ENEMY = 2, R_VIOLATOR = 3, R_LEVEL = 4, R_LIMIT = 5, R_USED = 6, R_NO_MARK = 7, R_NO_TARGET = 8,
            R_SIGNAL = 9, R_CHIPS = 10, R_BUSY = 11, R_NOT_HERE = 12, R_NO_BACK = 13, R_FRIEND = 14, R_PENDING = 15, R_CONFIRM = 16, R_CHANNEL = 17,
            R_HAS_BACK = 18;
    public static final String[] REFUSE_KEYS = {"ok", "off", "enemy", "violator", "level", "limit", "used", "noMark", "noTarget", "signal", "chips",
            "busy", "notHere", "noBack", "friend", "pending", "confirm", "channel", "hasBack"};

    private EmbassyRulesSC() {
    }

    // ------------------------------------------------------------------ the window

    /** The window's phase `elapsed` ticks after its countdown began (negative or past the cooling: IDLE). */
    public static int phaseAt(long elapsed) {
        if (elapsed < 0) {
            return PHASE_IDLE;
        }
        if (elapsed < COUNTDOWN_TICKS) {
            return PHASE_COUNTDOWN;
        }
        if (elapsed < COUNTDOWN_TICKS + VORTEX_TICKS) {
            return PHASE_VORTEX;
        }
        return elapsed < COUNTDOWN_TICKS + VORTEX_TICKS + COOL_TICKS ? PHASE_COOL : PHASE_IDLE;
    }

    /** Ticks left of the phase the window is in `elapsed` ticks after its start (0 when idle). */
    public static int phaseTicksLeft(long elapsed) {
        long[] ends = {COUNTDOWN_TICKS, COUNTDOWN_TICKS + VORTEX_TICKS, COUNTDOWN_TICKS + VORTEX_TICKS + COOL_TICKS};
        for (long end : ends) {
            if (elapsed >= 0 && elapsed < end) {
                return (int) (end - elapsed);
            }
        }
        return 0;
    }

    /** A new passenger may still join a window in this phase (idle - he starts one; the countdown and the vortex - he joins it). */
    public static boolean joinable(int phase, int passengers) {
        return phase != PHASE_COOL && (phase == PHASE_IDLE || passengers < PASSENGERS);
    }

    // ------------------------------------------------------------------ the rides

    public static boolean valid(int ride) {
        return ride >= 0 && ride < RIDE_KEYS.length;
    }

    /** Out of an embassy into the Substrate (INTRO, FIRST, DOCK, RESCUE); else back to the Overworld. */
    public static boolean outbound(int ride) {
        return ride == RIDE_INTRO || ride == RIDE_FIRST || ride == RIDE_DOCK || ride == RIDE_RESCUE;
    }

    /** ПЯ-10: the ride counts towards FLIGHTS_PER_DAY (all but the rescue). */
    public static boolean counted(int ride) {
        return valid(ride) && ride != RIDE_RESCUE;
    }

    /** ПЯ-12: the ride can be bought as a round trip (INTRO, FIRST, DOCK). */
    public static boolean roundTrip(int ride) {
        return ride == RIDE_INTRO || ride == RIDE_FIRST || ride == RIDE_DOCK;
    }

    /** The level a ride needs (a bad ride: «Почётный» - never open). */
    public static int levelOf(int ride) {
        return valid(ride) ? RIDE_LEVEL[ride] : ReputationSC.HONOURED + 1;
    }

    /** The settlement's spread key of a ride (TradeRulesSC.seedPct). */
    public static String priceKey(int ride) {
        return "flight." + (valid(ride) ? RIDE_KEYS[ride] : "none");
    }

    /**
     * ПЯ-11: what the ride costs a player of `level` at a settlement of `seed` (the embassy's - out; the Substrate dock's - back):
     * RIDE_BASE x the level's multiplier x the spread, rounded up - TradeRulesSC.playerSide. BACK (paid with its ticket) and a closed
     * level (ENEMY) cost 0 - the caller refuses those before.
     */
    public static int price(int ride, int level, long seed) {
        if (!valid(ride) || RIDE_BASE[ride] <= 0 || level <= ReputationSC.ENEMY) {
            return 0;
        }
        return TradeRulesSC.playerSide(base(ride), level, seed, priceKey(ride));
    }

    /**
     * ПС-8 (audit 2026-10-20): a ride's base price - RIDE_BASE x the server's embassy.flightPriceMultiplier, rounded up, at least 1
     * (0 stays 0: BACK is paid with its ticket).
     */
    public static int base(int ride) {
        if (!valid(ride) || RIDE_BASE[ride] <= 0) {
            return 0;
        }
        return (int) Math.max(1L, (long) Math.ceil(RIDE_BASE[ride] * (double) com.sc.dim.SubstrateConfigSC.flightPriceMultiplier - 1e-4));
    }

    /** ПС-8: flights a day there and back together - the server's embassy.flightsPerDay (FLIGHTS_PER_DAY by default). */
    public static int flightsPerDay() {
        return Math.max(1, com.sc.dim.SubstrateConfigSC.flightsPerDay);
    }

    /** ПЯ-12: a round trip of `ride` (out) and HOME (back, the same settlement's spread), x ROUND_TRIP_PCT, rounded up. 0: none. */
    public static int roundTripPrice(int ride, int level, long seed) {
        if (!roundTrip(ride)) {
            return 0;
        }
        int sum = price(ride, level, seed) + price(RIDE_HOME, level, seed);
        return (sum * ROUND_TRIP_PCT + 99) / 100;
    }

    /** ПЯ-13: a guarantee's price - the friend's ride at the guarantor's level, x GUARANTEE_PCT, rounded up. */
    public static int guaranteePrice(int ride, int guarantorLevel, long seed) {
        int p = price(ride, guarantorLevel, seed);
        return (p * GUARANTEE_PCT + 99) / 100;
    }

    /**
     * ПЯ-5..ПЯ-10, ПВ-13: whether a ride may be bought (OK) or why not, from what the server knows of the player and the place. Pure.
     * `channel` - the bridge (bridge.enabled, crossDimension) and the Substrate are on; `level` - the player's level here (ENEMY when
     * this settlement's «Враг»); `ridesToday` / `otherToday` - the day's counters; `introUsed` - INTRO taken already; `target` - the
     * ride has its target (a dock known, the death mark fresh and not rescued yet, the embassy flown out of, the return half still good);
     * `signal` - requireSignal is off or the player has the Navigation Record (outbound only); `here` - the ride is sold at this place
     * (out - an embassy's Dispatcher; HOME, BACK - a Substrate dock's; OTHER - a City's); `pending` - a talon of this player waits.
     */
    public static int refusal(int ride, boolean channel, int level, int ridesToday, int otherToday, boolean introUsed, boolean target,
                              boolean signal, boolean here, boolean pending) {
        if (!valid(ride) || !here) {
            return R_NOT_HERE;
        }
        if (!channel) {
            return R_OFF;
        }
        if (level <= ReputationSC.ENEMY) {
            return R_ENEMY;
        }
        if (level == ReputationSC.VIOLATOR && ride != RIDE_BACK) {
            return R_VIOLATOR;                                  // a paid return half still takes a «Нарушитель» home
        }
        if (level < levelOf(ride)) {
            return R_LEVEL;
        }
        if (outbound(ride) && !signal) {
            return R_SIGNAL;
        }
        if (pending) {
            return R_PENDING;
        }
        if (ride == RIDE_INTRO && introUsed) {
            return R_USED;
        }
        if (counted(ride) && ridesToday >= flightsPerDay() || ride == RIDE_OTHER && otherToday >= OTHER_PER_DAY) {   // ПС-8
            return R_LIMIT;
        }
        if (!target) {
            return ride == RIDE_RESCUE ? R_NO_MARK : ride == RIDE_BACK ? R_NO_BACK : R_NO_TARGET;
        }
        return OK;
    }

    /** ПЯ-7: the death at `deathAt` may still be rescued at `now` (within RESCUE_TICKS; not the one rescued already - `rescuedAt`). */
    public static boolean rescuable(boolean hasDeath, long deathAt, long now, long rescuedAt) {
        return hasDeath && now >= deathAt && now - deathAt <= RESCUE_TICKS && rescuedAt != deathAt;
    }

    /** ПЯ-12: a return half good until `until` is still good at `now`. */
    public static boolean backValid(long until, long now) {
        return until > 0 && now < until;
    }

    /** ПЯ-13: the friend may be guaranteed - his level here (not «Враг», at least «Чужак»), the guarantor's (at least «Партнёр»). */
    public static boolean guaranteeOpen(int guarantorLevel, int friendLevel) {
        return guarantorLevel >= GUARANTOR_LEVEL && friendLevel >= FRIEND_LEVEL;
    }

    /** ПЯ-13: the friend became the embassy's «Враг» (his status began at `enemySince`) within GUARANTEE_TICKS of the guarantee at `at`. */
    public static boolean guaranteeBroken(long at, long enemySince) {
        return enemySince >= at && enemySince - at <= GUARANTEE_TICKS;
    }

    /** ПЯ-14: the soft admission's warning is due - no piece of this mod's armour worn, or no Return Capsule carried. */
    public static boolean softWarning(boolean modArmour, boolean capsule) {
        return !modArmour || !capsule;
    }

    /** ПЯ-14: a second click at `now` confirms a warning given at `warnedAt` (within CONFIRM_TICKS; 0 - never warned). */
    public static boolean confirmed(long warnedAt, long now) {
        return warnedAt > 0 && now >= warnedAt && now - warnedAt <= CONFIRM_TICKS;
    }

    /** The refusal's lang key, sc.flight.refuse.<key> (a bad code: "notHere"). */
    public static String refuseKey(int code) {
        return "sc.flight.refuse." + REFUSE_KEYS[code >= 0 && code < REFUSE_KEYS.length ? code : R_NOT_HERE];
    }

    /** The ride's lang key, sc.flight.ride.<key>. */
    public static String rideKey(int ride) {
        return "sc.flight.ride." + (valid(ride) ? RIDE_KEYS[ride] : "none");
    }
}
