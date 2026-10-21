package com.sc.mob;

import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §5, rules ПС-50..ПС-58, ПТ-21..ПТ-24; proposal §9 «Босс: Смотритель Пробоя»): the
 * Breakdown Warden's numbers and its pure decisions - written by the coordinator so that the boss (package B), the arena (package
 * A), the handbook and the self-test (package D) read the same thing. Package B may retune the TODO(balance) numbers, not the
 * contract (the names, the meaning, the functions' signatures).
 * <ul>
 * <li>HP: BASE_HP x (1 + PER_PLAYER x (n - 1)), n - every player within SCALE_RADIUS of the arena and every player who has dealt
 * it damage from further off (the most seen this fight - the health never shrinks when one leaves), no cap; the current health
 * keeps its share when it grows;</li>
 * <li>phases by the health's share: «Удержание» above P2_AT, «Пробой» above P3_AT, «Перегрузка» below;</li>
 * <li>«Удержание»: invulnerable while a coil is lit; a coil goes dark for COIL_OFF_TICKS from a ПЭ-1 Point, or breaks when its
 * COIL_RESERVE is shot away; all four dark - a WINDOW_TICKS window with the armour as iron (MobArmorSC.emergency), then every
 * coil is lit again (a broken one with its full reserve); Hollow Shells come in waves;</li>
 * <li>«Пробой»: every HIGHLIGHT_EVERY one piece glows white for HIGHLIGHT_TICKS - a Point into it suppresses HIGHLIGHT_MUL times
 * longer and takes HIGHLIGHT_RESERVE of the reserve; the chestplate glowing - a Point suppresses the whole set SET_TICKS; the
 * gravity press; «Импульс глушения» every SILENCE_EVERY (SILENCE_HUM of a hum, SILENCE_RADIUS, the line of sight - cover saves,
 * the 20 s immunity holds); Breakdown cracks in the floor; nanite swarms under the shared cap;</li>
 * <li>«Перегрузка»: no shield; every OVERHEAT_EVERY the heat is full and the protection is off for OVERHEAT_TICKS; FINISH_TICKS to
 * finish it, else «сброс ядра» - CORE_RESET_DAMAGE of «Пробой» within CORE_RESET_RADIUS (the columns save; no block broken), then
 * again;</li>
 * <li>all players away RESET_AWAY_TICKS - the fight resets, the key stays in the Console;</li>
 * <li>the loot (ПТ-21): personal - every player with LOOT_SHARE of the damage, at most once in LOOT_COOLDOWN_DAYS game days.</li>
 * </ul>
 */
public final class WardenRulesSC {

    /** ПС-50: the size (3.5 blocks), the base health, its growth a player, the players counted within this of the arena. */
    public static final float WIDTH = 1.4F, HEIGHT = 3.5F;
    public static final double BASE_HP = 600D, PER_PLAYER = 0.6D;
    public static final int SCALE_RADIUS = 48;
    /** ПС-51: the phases' thresholds (share of the health) and numbers. */
    public static final float P2_AT = 0.60F, P3_AT = 0.25F;
    public static final int PHASE_HOLD = 1, PHASE_BREAKDOWN = 2, PHASE_OVERLOAD = 3;
    /** ПС-52, «Удержание»: four coils, a coil's reserve, dark from a Point (15 s), the window (10 s). */
    public static final int COILS = 4, COIL_OFF_TICKS = 300, WINDOW_TICKS = 200;
    public static final float COIL_RESERVE = 100F;
    /** ПС-52: Hollow Shell waves - every WAVE_EVERY ticks WAVE_SHELLS + one a player beyond the first, at most WAVE_MAX alive. TODO(balance) */
    public static final int WAVE_EVERY = 600, WAVE_SHELLS = 2, WAVE_MAX = 6;
    /** ПС-53, «Пробой»: the highlight (every 15 s, glows 5 s - TODO(balance)), x1.5 and 40 reserve, the whole set 6 s. */
    public static final int HIGHLIGHT_EVERY = 300, HIGHLIGHT_TICKS = 100, SET_TICKS = 120;
    public static final float HIGHLIGHT_MUL = 1.5F, HIGHLIGHT_RESERVE = 40F;
    /** ПС-53: the gravity press (as the Sentinel's: every 30 s, 3 s of a hum, 5 s held; its radius - TODO(balance)). */
    public static final int PRESS_EVERY = 600, PRESS_HUM = 60, PRESS_TICKS = 100, PRESS_RADIUS = 8;
    /** ПС-53: «Импульс глушения» - every 30 s, 3 s of a hum, radius 16. */
    public static final int SILENCE_EVERY = 600, SILENCE_HUM = 60, SILENCE_RADIUS = 16;
    /** ПС-53: Breakdown cracks - one every CRACK_EVERY, at most CRACKS_MAX in the floor; a swarm every SWARM_EVERY. TODO(balance) */
    public static final int CRACK_EVERY = 200, CRACKS_MAX = 8, SWARM_EVERY = 400;
    /** ПС-54, «Перегрузка»: the heat (every 15 s, 4 s with no protection), the time to finish (60 s), the blast. */
    public static final int OVERHEAT_EVERY = 300, OVERHEAT_TICKS = 80, FINISH_TICKS = 1200, CORE_RESET_WARN = 100;
    public static final float CORE_RESET_DAMAGE = 20F;
    /**
     * ПС-54: the blast's reach round the middle (horizontal) - the whole SCALE_RADIUS, not the pit alone: whoever is counted in the
     * fight (the rim, the funnel's ledges, the canyons' mouths) needs cover too, a shooter from the edge isn't safe. TODO(balance)
     */
    public static final int CORE_RESET_RADIUS = SCALE_RADIUS;
    /** ПС-55: the blade's blow (the boss's cap - proposal §8 «у босса 16»), the reserve on the summons, its recharge. TODO(balance) */
    public static final float BLADE_DAMAGE = 16F, RESERVE_MIN = 250F, RESERVE_MAX = 300F, REGEN_PER_SECOND = 4F;
    public static final int REGEN_DELAY_TICKS = 100;
    /** ПС-56: the arena's reset - every player away (out of SCALE_RADIUS) this long (5 min); the fight's own leash round the centre. */
    public static final int RESET_AWAY_TICKS = 6000, LEASH = 26;
    /** ПТ-21: the share of the damage for the personal loot (10%), its cooldown in game days. */
    public static final float LOOT_SHARE = 0.10F;
    public static final int LOOT_COOLDOWN_DAYS = 3;
    /** ПТ-22: the guaranteed loot - one Breakdown Matrix, 4..8 Crumbs (beyond the daily cap), 1..2 Clots, 4..8 Singular scrap. */
    public static final int MATRIX = 1, CRUMBS_MIN = 4, CRUMBS_MAX = 8, CLOTS_MIN = 1, CLOTS_MAX = 2, SCRAP_MIN = 4, SCRAP_MAX = 8;
    /** ПТ-23: the scheme «Литограф» 25%; the trophies - the Singular chestplate 5%, the Singular Blade 3%, the ПЭ-1М 2%. */
    public static final float PAINT_CHANCE = 0.25F, TROPHY_CHEST = 0.05F, TROPHY_BLADE = 0.03F, TROPHY_PE1M = 0.02F;
    /** ПТ-24: the Lithographer's Journal of a player's first kill (JournalSC: №12, meta 11). */
    public static final int FIRST_KILL_JOURNAL = 12;
    /** The Singular progress points of the kill - the existing rule for any IBossDisplayData (SingularLevel.killPoints), the killer's. */
    public static final int PROGRESS_POINTS = 500;
    /** The persisted record of a player (SettlementRegistrySC.PlayerRec.extra "W"): the last loot's day, kills, the first kill, loot to come. */
    public static final String EXTRA = "W", LAST_DAY = "d", KILLS = "k", FIRST = "f", PENDING = "p";

    private WardenRulesSC() {
    }

    /** ПС-50 (pure): the health for `players` players (at least one); ПМ-6: times dimension.wardenHealthMultiplier. */
    public static float maxHealth(int players) {
        return (float) (BASE_HP * (1D + PER_PLAYER * (Math.max(1, players) - 1)) * com.sc.dim.SubstrateConfigSC.wardenHealthMultiplier);
    }

    /** ПМ-6 (audit 2026-10-20): the game days between two of a player's loot - dimension.wardenLootCooldownDays (LOOT_COOLDOWN_DAYS). */
    public static int lootCooldownDays() {
        return Math.max(0, com.sc.dim.SubstrateConfigSC.wardenLootCooldownDays);
    }

    /** ПС-50 (pure): the health after the scale grew from `oldMax` to `newMax` - the same share of it. */
    public static float rescaled(float health, float oldMax, float newMax) {
        return oldMax <= 0F ? newMax : Math.max(0F, Math.min(newMax, health / oldMax * newMax));
    }

    /** ПС-51 (pure): the phase of `health` out of `max`. */
    public static int phaseOf(float health, float max) {
        float f = max <= 0F ? 0F : health / max;
        return f > P2_AT ? PHASE_HOLD : f > P3_AT ? PHASE_BREAKDOWN : PHASE_OVERLOAD;
    }

    /** ПС-52 (pure): «Удержание» - the window opens (and the boss takes damage) only with no coil lit. */
    public static boolean windowOpens(int coilsLit) {
        return coilsLit <= 0;
    }

    /** ПС-52 (pure): Hollow Shells of a wave for `players` (2 + one a player beyond the first). */
    public static int waveSize(int players) {
        return WAVE_SHELLS + Math.max(0, players - 1);
    }

    /** ПС-53 (pure): a Point into the highlighted piece - its suppression `ticks` made HIGHLIGHT_MUL times longer. */
    public static int highlightTicks(int ticks) {
        return Math.round(ticks * HIGHLIGHT_MUL);
    }

    /** ПС-54 (pure): `sincePhase` ticks into «Перегрузка» - the protection is off now (the last OVERHEAT_TICKS of every OVERHEAT_EVERY). */
    public static boolean overheated(long sincePhase) {
        return sincePhase >= 0 && sincePhase % OVERHEAT_EVERY >= OVERHEAT_EVERY - OVERHEAT_TICKS;
    }

    /** ПС-54 (pure): `sinceClock` ticks since the 60 s began (the phase, or the last blast) - the blast is due. */
    public static boolean coreResetDue(long sinceClock) {
        return sinceClock >= FINISH_TICKS;
    }

    /** ПС-56 (pure): every player was away from `lastPlayerAt` to `now` long enough - the fight resets. */
    public static boolean resetDue(long lastPlayerAt, long now) {
        return now - lastPlayerAt >= RESET_AWAY_TICKS;
    }

    /** ПТ-21 (pure): a player with `share` of the damage has a personal loot. */
    public static boolean lootEligible(float share) {
        return share + 1.0E-4F >= LOOT_SHARE;
    }

    /** ПТ-21 (pure): the loot is due on `day` (none before, or lootCooldownDays() since the last one - ПМ-6). */
    public static boolean lootDue(boolean hadLoot, long lastDay, long day) {
        return !hadLoot || day - lastDay >= lootCooldownDays();
    }

    /**
     * ПТ-23 (pure): the trophy of one player's roll `roll` (0..1) at multiplier `mul` (gearDropMultiplier x Looting, TrophyTableSC.chance
     * caps it): the Singular chestplate, the Singular Blade, the ПЭ-1М by their chances, -1 none.
     */
    public static int trophyMeta(float roll, float mul) {
        float m = Math.max(0F, mul);
        if (roll < TROPHY_CHEST * m) {
            return TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 1);
        }
        if (roll < (TROPHY_CHEST + TROPHY_BLADE) * m) {
            return TrophyTableSC.bladeMeta(BladeType.SINGULAR);
        }
        return roll < (TROPHY_CHEST + TROPHY_BLADE + TROPHY_PE1M) * m ? TrophyTableSC.META_PE1M : -1;
    }
}
