package com.sc.embassy;

import net.minecraft.world.World;

/**
 * Подложка Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md): the embassies' registration and their shared small questions.
 * An embassy is a settlement of the global registry in the Overworld (SettlementSizeSC.EMBASSY): its core, protection, settlers and
 * guards are the Substrate's own (SettlementSC.governs lets them work there); what is its own - the generation (packages G, S), the
 * Great Ring's flights there and back (package R), the Quartermaster, the Ambassador's contracts and the Direction Finder (package T).
 * <ul>
 * <li>the structure - a vanilla one of the Overworld named in MapGenStructureIO with the prefix SCOw ("SCOwEmbassy" / "SCOwEmb*":
 * never the Substrate's SCSi14*), its starts made in ReplaceBiomeBlocks before any chunk of their footprint, its pieces built only in
 * the populate window (no cascade); a start on ground explored before is empty;</li>
 * <li>one MapGenEmbassySC for the server's Overworld (mapGen): made for that world object, forgotten when it unloads or the server
 * stops - a single player's next world starts with none of the last one's starts.</li>
 * </ul>
 * Shared core: only the coordinator edits this file.
 */
public final class EmbassySC {

    /** The structure's name (vanilla's MapGenStructureData in the Overworld's data/, the start's MapGenStructureIO name). */
    public static final String START_NAME = "SCOwEmbassy";
    /** The pieces' MapGenStructureIO names start with this ("SCOwEmbPart", "SCOwEmbGround", package S's "SCOwEmb*"). */
    public static final String PIECE_PREFIX = "SCOwEmb";

    private static MapGenEmbassySC mapGen;
    private static World mapWorld;

    private EmbassySC() {
    }

    /** SCMod.init, after ArenaSC.init: the structure names - always, the embassies off too (a saved world must load). */
    public static void init() {
        MapGenEmbassySC.registerStructures();
    }

    /** The map generator of the server's Overworld `w` (made for that world; null for a client's or another world). */
    public static MapGenEmbassySC mapGen(World w) {
        if (w == null || w.isRemote || w.provider == null || w.provider.dimensionId != EmbassySiteSC.DIM) {
            return null;
        }
        if (mapGen == null || mapWorld != w) {
            mapGen = new MapGenEmbassySC();
            mapWorld = w;
        }
        return mapGen;
    }

    /** The Overworld `w` unloads: its map generator goes with it. */
    public static void forget(World w) {
        if (w == null || w == mapWorld) {
            mapGen = null;
            mapWorld = null;
        }
    }

    /** SCMod.serverStopped: everything the embassies keep for one server's run. */
    public static void clearAll() {
        forget(null);
        EmbassySiteSC.clearCache();
        EmbassyWorldGenSC.clearAll();
        EmbassyRepairSC.clearAll();
        FlightSC.clearAll();
        EmbassyBeamSC.clearAll();
        EmbassyHintSC.clearAll();
    }
}
