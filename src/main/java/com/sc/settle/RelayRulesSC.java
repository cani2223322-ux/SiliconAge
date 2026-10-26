package com.sc.settle;

/**
 * ПД-7 (docs/plan-ideas-2026-10-26.md, «Отвоевать Мёртвый Узел: ретрансляционный пост», ★ A): the relay post's numbers and pure
 * decisions - the coordinator's names and signatures, ПД-7's bodies and TODO(balance) numbers.
 * <ul>
 * <li>the Relay (block.BlockRelaySC.RELAY) is bought from an Elder at «Свой» (BUY_LEVEL, PRICE_CHIPS) and set up only on a Dead Node's
 * plaza (within PLAZA of its crossing), one post to a REGION x REGION block of crossings (the region (i >> 2, j >> 2)), at least
 * SETTLEMENT_GAP from any settlement's core, never an arena's crossing, never on a player's building (BUILT_SCAN: no block of a player
 * within the post's footprint - only the ruin's own, the Substrate's rock and air);</li>
 * <li>the defence: HOLD_TICKS (5 min) against WAVES waves (RaidRulesSC.wave of OUTPOST's size), the setter and every real player within
 * DEFEND_REACH counting; the Relay broken or nobody near for ABANDON_TICKS - lost (the Relay drops back; the crossing and the defenders
 * rest LOST_PAUSE);</li>
 * <li>held - the post is built over the plaza (SettlementSizeSC.RELAY: dome 12, 2 Sentries, a dock) - a bridge target for whoever has seen
 * its dock; the Node's patrols never come again (SpawnDirectorSC asks claimed); its raids as any settlement's - a lost one destroys it
 * (SettlementRegistrySC.remove, the blocks back to ruin).</li>
 * </ul>
 */
public final class RelayRulesSC {

    /** The plaza's reach from the crossing; the region of crossings a post owns (4 x 4); the gap to a settlement's core. */
    public static final int PLAZA = 10, REGION = 4, SETTLEMENT_GAP = 512;
    /** The Relay's price and level. TODO(balance) */
    public static final int PRICE_CHIPS = 24, BUY_LEVEL = ReputationSC.OWN;
    /** The defence: 5 minutes, 3 waves, the defenders' reach, abandoned after this long alone. TODO(balance) */
    public static final int HOLD_TICKS = 6000, WAVES = 3, DEFEND_REACH = 48, ABANDON_TICKS = 1200;
    /** The defence held: «Доверие» (ReputationSC.SRC_RELAY) to each defender. TODO(balance) */
    public static final int HELD_REP = 40;
    /**
     * ПД-7 (the review's fix): a lost defence - its crossing and each of its defenders may set a Relay up again only after this long (world
     * total time: a game day) - no waves on demand by walking off and setting it up again. TODO(balance)
     */
    public static final int LOST_PAUSE = 24000;
    /** The registry's extra key of a post (Settlement.extra): {i, j: its crossing, at: built}. */
    public static final String EXTRA = "Y";

    private RelayRulesSC() {
    }

    /** Pure: crossing (i, j)'s region (one post a region). */
    public static long regionKey(int i, int j) {
        return ((long) (i >> 2) << 32) ^ ((long) (j >> 2) & 0xFFFFFFFFL);
    }
}
