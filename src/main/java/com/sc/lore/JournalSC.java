package com.sc.lore;

import java.util.Random;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §6.1, rules ПЛ-1, ПЛ-2; proposal §10 «Лор: ... 12 Журналов Литографа. Последний
 * намекает, что последний мост Литографов вёл в «зелёный мир с одной луной»»): the twelve Lithographer's Journals - where each is
 * found and how often. Written by the coordinator; package J may retune the TODO(balance) chances, not the pools' meaning (each
 * number is found somewhere, №12 only from the Warden).
 * <ul>
 * <li>№1, №2 - the Dead Nodes' chests (NODE_CHANCE a chest); №3, №4 - the lithographs' chests (LITHO_CHANCE);</li>
 * <li>№5..№8 - the Vaults: a bunker's chest (VAULT_CHANCE) and the Sentinel (SENTINEL_CHANCE - proposal §8 «Журнал 50%»);</li>
 * <li>№9, №10 - the Lithographers' bridge wrecks (one in each wreck's chest);</li>
 * <li>№11 - the Lithographers' Signal's cache (always);</li>
 * <li>№12 - the Breakdown Warden, a player's first kill (WardenRulesSC.FIRST_KILL_JOURNAL) - the «green world with one moon».</li>
 * </ul>
 * The item's meta is the number - 1 (ItemJournalSC); its text - lang sc.journal.<n>.title, .date and .p0 .. .p<PAGES[n-1]-1>
 * (package D writes them: the Lithographers' last days in their own words - §1 / §10 of the proposal).
 */
public final class JournalSC {

    public static final int COUNT = 12;
    /** The sources (fixed). */
    public static final int SRC_NODE = 0, SRC_LITHO = 1, SRC_VAULT = 2, SRC_SENTINEL = 3, SRC_WRECK = 4, SRC_SIGNAL = 5, SRC_WARDEN = 6;
    /** The numbers each source gives (by SRC_*). */
    public static final int[][] POOLS = {{1, 2}, {3, 4}, {5, 6, 7, 8}, {5, 6, 7, 8}, {9, 10}, {11}, {12}};
    /** A source's chance of a journal (a chest / a kill). TODO(balance) */
    public static final float NODE_CHANCE = 0.06F, LITHO_CHANCE = 0.10F, VAULT_CHANCE = 0.30F, SENTINEL_CHANCE = 0.5F, WRECK_CHANCE = 1F,
            SIGNAL_CHANCE = 1F;
    /** Pages of each journal's text (lang .p0 ..), by number - 1 (package D may change them with the texts). */
    public static final int[] PAGES = {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4};

    private JournalSC() {
    }

    /** The item's meta of journal №`number`. */
    public static int metaOf(int number) {
        return number - 1;
    }

    /** The number of the journal of meta `meta` (1..COUNT; an unknown meta: 1). */
    public static int numberOf(int meta) {
        return meta >= 0 && meta < COUNT ? meta + 1 : 1;
    }

    /** A source's chance (SRC_*), 0 for an unknown one. */
    public static float chance(int source) {
        switch (source) {
            case SRC_NODE: return NODE_CHANCE;
            case SRC_LITHO: return LITHO_CHANCE;
            case SRC_VAULT: return VAULT_CHANCE;
            case SRC_SENTINEL: return SENTINEL_CHANCE;
            case SRC_WRECK: return WRECK_CHANCE;
            case SRC_SIGNAL: return SIGNAL_CHANCE;
            case SRC_WARDEN: return 1F;
            default: return 0F;
        }
    }

    /** The journal's number one find from `source` gives (pure but for `rand`): one of its pool; 0 for an unknown source. */
    public static int pick(int source, Random rand) {
        if (source < 0 || source >= POOLS.length) {
            return 0;
        }
        int[] p = POOLS[source];
        return p[rand.nextInt(p.length)];
    }

    /** The source rolled once (its chance, then its pool): the number, or 0 - nothing this time. */
    public static int roll(int source, Random rand) {
        return rand.nextFloat() < chance(source) ? pick(source, rand) : 0;
    }
}
