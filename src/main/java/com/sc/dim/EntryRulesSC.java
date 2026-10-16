package com.sc.dim;

/**
 * PACKAGE E (docs/plan-substrate-tech.md §6): the entry and bridge rules as pure decisions (ПВ-3, ПВ-6, ПВ-8 of
 * docs/plan-substrate.md) - SubstrateEntrySC and the bridge ask these, the self-test checks them without a player.
 * Signatures fixed; E may add more.
 */
public final class EntryRulesSC {

    /** Ticks a grant (the bridge's carry, grantArrival) stays good. */
    public static final int GRANT_TICKS = 100;
    /** Resistance II this long on every lawful arrival («Буфер прокола»). */
    public static final int BUFFER_TICKS = 200, BUFFER_AMPLIFIER = 1;
    /** The nether portal kept dead: every PORTAL_CHECK ticks timeUntilPortal is put back to at least PORTAL_HOLD. */
    public static final int PORTAL_CHECK = 10, PORTAL_HOLD = 40;
    /** A player's last safe place outside the Substrate is noted this often (strictEntry's way back, ПВ-3). */
    public static final int SPOT_TICKS = 40;
    /** «Прокол глушит портал» / «Здесь не уснуть» at most once in this many ticks to a player. */
    public static final int PORTAL_SAY_TICKS = 60, BED_SAY_TICKS = 40;

    private EntryRulesSC() {
    }

    /** ПВ-3: an arrival in the Substrate stands (true) or goes back (false). */
    public static boolean lawful(boolean strictEntry, boolean granted, boolean creative, boolean op) {
        return !strictEntry || granted || creative || op;
    }

    /**
     * ПВ-2: a way in the core lets in by itself - the Space Bridge's carry and the Vehicles ship's Jump (its riders go
     * by the bridge's teleport). Another mod's name: not.
     */
    public static boolean knownWay(String via) {
        return SubstrateEntrySC.VIA_BRIDGE.equals(via) || SubstrateEntrySC.VIA_JUMP.equals(via);
    }

    /** ПВ-6: the Substrate's price multiplier of a bridge trip - only a trip between dimensions with an end in the Substrate. */
    public static float costMultiplier(boolean crossDimension, boolean substrateEnd, float configMul) {
        return crossDimension && substrateEnd ? configMul : 1F;
    }

    /** ПВ-8: an entity may go through a bridge (a player always; a native only with nativesCrossBridge, or within its dimension). */
    public static boolean mayCross(boolean player, boolean nativeOfSubstrate, boolean leavesDimension, boolean nativesCrossBridge) {
        return player || !nativeOfSubstrate || !leavesDimension || nativesCrossBridge;
    }

    /** ПВ-3: a place worth keeping as the way back - on the ground, not in water or lava, not riding, not in a bed. */
    public static boolean safeToNote(boolean onGround, boolean inLiquid, boolean riding, boolean sleeping) {
        return onGround && !inLiquid && !riding && !sleeping;
    }
}
