package com.sc.mob;

import java.util.Random;

/**
 * Подложка Ф3 (docs/idea-dimension-substrate.md §9 «Элитные модификаторы», docs/plan-substrate-tech-f3.md §5, rules
 * ПС-20..ПС-25): the elite modifiers of the armoured mobs and their numbers - pure, so the self-test and the handbook read
 * them. The ordinal + 1 is what EntityArmoredMobSC keeps (DataWatcher DW_ELITE, NBT "scEliteMod"; 0 - none): append only.
 * What a modifier does is EliteSC's (package A). Lang: sc.elite.<key> (the name over its head: sc.elite.name «%s · %s»).
 * <p>
 * Who: 5% of the armoured mobs SpawnDirectorSC makes (12% in the Rift - Ф4), +1% for every 250 blocks from the world's
 * spawn point, at most +8% (§10 «Сложность растёт с расстоянием»). Not the Executioner (an elite of its kind), not a mob
 * from an egg. An elite: «Казнь» doesn't finish it (IExecuteImmuneSC), its trophy chance x2 (TrophyTableSC.ELITE_MUL),
 * WAILA shows a star, its name hangs over its head. Owner after the core: package A.
 */
public enum EliteModSC {

    /** «Командир»: its group within COMMANDER_RADIUS deal COMMANDER_BONUS more and get it on their reserve; an order is heard first. */
    COMMANDER("commander"),
    /** «Заряженный»: CHARGED_BONUS more reserve. */
    CHARGED("charged"),
    /** «Экранированный»: a personal EMP screen, as the Jammer's (the first ПЭ-1 shot is lost on it, back in SCREEN_RECOVER_TICKS). */
    SHIELDED("shielded"),
    /** «Ветеран»: helium in the loop - suppression 20% shorter, and the shield where its suit has one (Exo, Singular). */
    VETERAN("veteran"),
    /** «Ремонтник»: REPAIR_UNITS a second into the reserve of every armoured native within REPAIR_RADIUS (not its own). */
    REPAIRER("repairer"),
    /** «Мститель»: dead, it beeps AVENGER_FUSE_TICKS, then a blast of AVENGER_RADIUS that breaks no block. */
    AVENGER("avenger");

    /** ПС-20: % of the armoured spawns; +PER_STEP_PCT a STEP_BLOCKS from the spawn point, at most +MAX_EXTRA_PCT; the Rift's (Ф4). TODO(balance) */
    public static final int CHANCE_PCT = 5, RIFT_CHANCE_PCT = 12, PER_STEP_PCT = 1, STEP_BLOCKS = 250, MAX_EXTRA_PCT = 8;
    /** ПС-21: the Commander's bonus to its group's damage and reserve, the group's radius; the order's lead time (ticks). TODO(balance) */
    public static final float COMMANDER_BONUS = 0.2F;
    public static final int COMMANDER_RADIUS = 16, ORDER_TICKS = 20;
    /** ПС-21 (package A): an order once in ORDER_EVERY_TICKS while the Commander fights, its group's bonus lasts ORDER_BUFF_TICKS;
     * the lines sc.elite.order.0..ORDER_LINES-1. TODO(balance): 100 / 200 ticks */
    public static final int ORDER_EVERY_TICKS = 100, ORDER_BUFF_TICKS = 200, ORDER_LINES = 4;
    /** ПС-21: the Charged one's extra reserve. */
    public static final float CHARGED_BONUS = 0.5F;
    /** ПС-21, ПБ-19: the personal EMP screen comes back this long after it took a shot (30 s - the Jammer's too). */
    public static final int SCREEN_RECOVER_TICKS = 600;
    /** ПС-21: the Repairer's units a second (REPAIR_UNITS every REPAIR_EVERY_TICKS) and reach. TODO(balance): REPAIR_RADIUS (the proposal names none) */
    public static final float REPAIR_UNITS = 1F;
    public static final int REPAIR_RADIUS = 8, REPAIR_EVERY_TICKS = 20;
    /** ПС-21: the Avenger's fuse (1.5 s, a beep every AVENGER_BEEP_TICKS) and blast radius (no block broken). */
    public static final int AVENGER_FUSE_TICKS = 30, AVENGER_BEEP_TICKS = 5;
    public static final float AVENGER_RADIUS = 3F;

    /** The lang key's tail (sc.elite.<key>). */
    public final String key;

    EliteModSC(String key) {
        this.key = key;
    }

    public String langKey() {
        return "sc.elite." + key;
    }

    /** What the DataWatcher / NBT keep: 0 none, else ordinal + 1. */
    public int code() {
        return ordinal() + 1;
    }

    /** The modifier of a kept code, null for 0 or an unknown one (a newer world). */
    public static EliteModSC of(int code) {
        EliteModSC[] all = values();
        return code >= 1 && code <= all.length ? all[code - 1] : null;
    }

    /** ПС-20: the elite chance in % of a spawn `blocks` from the world's spawn point (pure). */
    public static int chancePct(double blocks, boolean rift) {
        int extra = (int) Math.min(MAX_EXTRA_PCT, Math.max(0D, blocks) / STEP_BLOCKS * PER_STEP_PCT);
        return (rift ? RIFT_CHANCE_PCT : CHANCE_PCT) + extra;
    }

    /**
     * ПС-20: a modifier for a mob that is already screened (the Jammer) / already has helium in its loop - those two never
     * repeat what it has; the rest equally likely (pure but for the dice). Never null.
     */
    public static EliteModSC pick(Random rand, boolean screened, boolean helium) {
        EliteModSC[] all = values();
        EliteModSC m;
        do {
            m = all[rand.nextInt(all.length)];
        } while (m == SHIELDED && screened || m == VETERAN && helium);
        return m;
    }
}
