package com.sc.embassy;

import java.util.ArrayList;
import java.util.List;

import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rules ПЕ-7, ПЕ-9): an embassy's buildings as structure pieces - one a lot of the
 * plan (EmbassyLayoutSC.LOT_*), each class under its own MapGenStructureIO name "SCOwEmb" + its name (never "Part" - the base's -
 * nor "Ground" - package G's). MapGenEmbassySC puts package G's ground piece first and then pieces(id, plan) - so the buildings of a
 * window come after its ground.
 * <p>
 * Fixed (the coordinator's): the class, register() (called by MapGenEmbassySC.registerStructures - always, a saved world must load
 * its pieces by their names), pieces' signature. Package S's: the pieces (ComponentEmb*SC, all of them ComponentEmbassySC's - only
 * the window, flag 2, light by hand, vanilla's and the mod's blocks, nothing valuable, not one log or plank, idempotent for the
 * «Достройка»), their names below - fixed from here on (a saved world loads its pieces by them; append only):
 * <ul>
 * <li>"SCOwEmbHeart" - the Heart and the guard square (ComponentEmbHeartSC: the core and its setup, the Clone Capsule, the
 * Ambassador's hall, the armour station, the pads); "SCOwEmbStreet" - a street (main, back, on to the Vokzal); "SCOwEmbGate" - a
 * gate; "SCOwEmbWall" - the wall's band and the ring road inside it; "SCOwEmbTower" - a watch tower;</li>
 * <li>"SCOwEmbWorkshop" - a trade's workshop; "SCOwEmbDepot" - the supply depots; "SCOwEmbGreenhouse" - the greenhouse;
 * "SCOwEmbHouse" - a living cell;</li>
 * <li>"SCOwEmbVokzal" - the Vokzal (the dock's beacon, the office's desks, the cryo tanks); "SCOwEmbRing" - the Great Ring (its
 * coils, its core and the core's setup); "SCOwEmbEnergy" - an energy field (solar panels or wind masts - decor).</li>
 * </ul>
 * ComponentEmbFrameSC is the abstract base of the pieces built in the embassy's frame - never registered.
 */
public final class EmbassyPiecesSC {

    /** The pieces' names after EmbassySC.PIECE_PREFIX, in register()'s order (the self-test checks each is registered). */
    public static final String[] NAMES = {"Heart", "Street", "Gate", "Wall", "Tower", "Workshop", "Depot", "Greenhouse", "House", "Vokzal",
            "Ring", "Energy"};

    private EmbassyPiecesSC() {
    }

    /** The pieces' MapGenStructureIO names (package S appends its classes; the base and the ground are MapGenEmbassySC's). */
    public static void register() {
        Class<?>[] classes = {ComponentEmbHeartSC.class, ComponentEmbStreetSC.class, ComponentEmbGateSC.class, ComponentEmbWallSC.class,
                ComponentEmbTowerSC.class, ComponentEmbWorkshopSC.class, ComponentEmbDepotSC.class, ComponentEmbGreenhouseSC.class,
                ComponentEmbHouseSC.class, ComponentEmbVokzalSC.class, ComponentEmbRingSC.class, ComponentEmbEnergySC.class};
        for (int i = 0; i < classes.length; i++) {
            MapGenStructureIO.func_143031_a(classes[i], EmbassySC.PIECE_PREFIX + NAMES[i]);
        }
    }

    /** The buildings of plan `p` of embassy `id`, in the plan's order of lots (the ground is not among them). */
    public static List<StructureComponent> pieces(int id, EmbassyLayoutSC.Plan p) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        for (SettlementLayoutSC.Lot lot : p.lots) {
            StructureComponent c = piece(id, p, lot);
            if (c != null) {
                l.add(c);
            }
        }
        return l;
    }

    /** The piece of one lot (null: a kind without one). */
    static StructureComponent piece(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        switch (lot.kind) {
            case EmbassyLayoutSC.LOT_HEART:
                return new ComponentEmbHeartSC(id, p, lot);
            case EmbassyLayoutSC.LOT_STREET:
                return new ComponentEmbStreetSC(id, p, lot);
            case EmbassyLayoutSC.LOT_GATE:
                return new ComponentEmbGateSC(id, p, lot);
            case EmbassyLayoutSC.LOT_WALL:
                return new ComponentEmbWallSC(id, p, lot);
            case EmbassyLayoutSC.LOT_TOWER:
                return new ComponentEmbTowerSC(id, p, lot);
            case EmbassyLayoutSC.LOT_WORKSHOP:
                return new ComponentEmbWorkshopSC(id, p, lot);
            case EmbassyLayoutSC.LOT_DEPOT:
                return new ComponentEmbDepotSC(id, p, lot);
            case EmbassyLayoutSC.LOT_GREENHOUSE:
                return new ComponentEmbGreenhouseSC(id, p, lot);
            case EmbassyLayoutSC.LOT_HOUSE:
                return new ComponentEmbHouseSC(id, p, lot);
            case EmbassyLayoutSC.LOT_VOKZAL:
                return new ComponentEmbVokzalSC(id, p, lot);
            case EmbassyLayoutSC.LOT_RING:
                return new ComponentEmbRingSC(id, p, lot);
            case EmbassyQuartersSC.LOT_ENERGY:
                return new ComponentEmbEnergySC(id, p, lot);
            default:
                return null;
        }
    }
}
