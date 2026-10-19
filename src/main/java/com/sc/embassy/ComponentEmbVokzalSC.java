package com.sc.embassy;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rule ПЕ-8): «Вокзал прокола» - "SCOwEmbVokzal", the 25 x 25 platform outside the main
 * gate on the level ground, its middle (the dock's beacon: the registry's dockX / Y / Z) VOKZAL_OUT past the dome's edge on the main
 * street. In the frame from the beacon (da along the street, p across):
 * <ul>
 * <li>the dock: the 7 x 7 landing of the Substrate's docks round the beacon (BlockDockSC PLATE, BEACON in the middle - bridges land
 * on it without scatter; the bridge's vortex needs the air over it: nothing stands there);</li>
 * <li>the platform: in the dock zone (EmbassyLayoutSC.DOCK_ZONE across from the beacon - protected) Settlement Brick, copper landing
 * guides along both axes, a trace ring with lamps on the zone's edge; outside the zone (its corners, nobody protects them) plain
 * stone bricks - no copper, nothing of value;</li>
 * <li>«Диспетчерская» round P_OFFICE (EmbassyQuartersSC.OFFICE, in the zone): a booth of brick on steel posts with lead glass windows,
 * a steel roof with a lamp, its counter towards the street - two Dispatcher's Desks (BlockShowcase2SC DISPATCH_CONSOLE, their screens
 * towards the Dispatchers inside), a gap to walk in by;</li>
 * <li>the tank farm opposite (TANKS): four Cryo Tanks two high (CRYO_TANK, their gauges towards the street) on a brick pad, pipes of
 * iron bars between them, steel corner posts with lamps; and a tank three high either side of the Great Ring, a steel coupling and
 * a bar to its coils.</li>
 * </ul>
 * The Ring's row (da = RING_BACK, |p| <= RING_R) is the Ring piece's (ComponentEmbRingSC): this piece never sets a block there.
 */
public class ComponentEmbVokzalSC extends ComponentEmbFrameSC {

    /** The dock's landing: its half (7 x 7, as a Settlement's dock). */
    public static final int DOCK_HALF = SettlementLayoutSC.DOCK_HALF;
    /** The zone's edge ring of traces: past this (squared) and inside the zone. */
    private static final int RIM_IN2 = (EmbassyLayoutSC.DOCK_ZONE - 1) * (EmbassyLayoutSC.DOCK_ZONE - 1);
    /** The tanks beside the Ring: at |p| = RING_R + 2, a coupling at RING_R + 1; three high. */
    private static final int RING_TANK_P = EmbassyLayoutSC.RING_R + 2, RING_TANK_H = 3;

    public ComponentEmbVokzalSC() {
    }

    public ComponentEmbVokzalSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyQuartersSC.VOKZAL_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        Block rock = rock();
        int beacon = radius + EmbassyLayoutSC.VOKZAL_OUT, h = EmbassyLayoutSC.VOKZAL_HALF, zone2 = EmbassyLayoutSC.DOCK_ZONE * EmbassyLayoutSC.DOCK_ZONE;
        for (int da = -h; da <= h; da++) {
            for (int p = -h; p <= h; p++) {
                if (ringRow(da, p)) {
                    continue;
                }
                int a = beacon + da, d2 = da * da + p * p;
                if (Math.abs(da) <= DOCK_HALF && Math.abs(p) <= DOCK_HALF) {
                    if (SettlementBlocksSC.dock != null) {
                        at(w, win, a, 0, p, SettlementBlocksSC.dock, da == 0 && p == 0 ? BlockDockSC.BEACON : BlockDockSC.PLATE);
                    }
                } else if (d2 > zone2) {                                     // the corners: plain stone, nothing of value
                    at(w, win, a, 0, p, Blocks.stonebrick, Math.abs(da) == h || Math.abs(p) == h ? 3 : 0);
                } else if (d2 > RIM_IN2) {                                   // the zone's edge: a trace ring, lamps at its eight points
                    boolean lamp = da == 0 || p == 0 || Math.abs(da) == Math.abs(p);
                    at(w, win, a, 0, p, rock, lamp ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE);
                } else if (da == 0 || p == 0) {                              // the landing guides
                    at(w, win, a, 0, p, rock, SubstrateBlocksSC.COPPER_TRACE);
                } else if (EmbassyQuartersSC.inside(EmbassyQuartersSC.OFFICE, da, p)) {
                    at(w, win, a, 0, p, Blocks.quartz_block, 0);
                } else {
                    at(w, win, a, 0, p, rock, BRICK);
                }
            }
        }
        office(w, win, beacon, rock);
        tanks(w, win, beacon, rock);
    }

    /** The Ring's own cells: its row across the street, the ring's width. */
    private static boolean ringRow(int da, int p) {
        return da == EmbassyLayoutSC.RING_BACK && Math.abs(p) <= EmbassyLayoutSC.RING_R;
    }

    /** «Диспетчерская»: walls on posts, the counter of two desks towards the street, a gap at its end, the roof. */
    private void office(World w, StructureBoundingBox win, int beacon, Block rock) {
        int[] o = EmbassyQuartersSC.OFFICE;
        int counter = o[3], back = o[1], mid = (o[0] + o[2]) / 2;
        for (int da = o[0]; da <= o[2]; da++) {
            for (int p = back; p <= counter; p++) {
                int a = beacon + da;
                boolean sideA = da == o[0] || da == o[2], edge = sideA || p == back || p == counter, post = sideA && (p == back || p == counter);
                if (post) {
                    for (int dy = 1; dy <= 3; dy++) {
                        at(w, win, a, dy, p, steel());
                    }
                } else if (p == counter) {                                   // the counter: two desks, the gap by the far post
                    if (da < o[2] - 1) {
                        machine2(w, win, a, 1, p, BlockShowcase2SC.DISPATCH_CONSOLE, 0, -1);
                    } else {
                        air(w, win, a, 1, p);
                        air(w, win, a, 2, p);
                    }
                } else if (edge) {                                           // the walls: brick, a lead glass window
                    at(w, win, a, 1, p, rock, BRICK);
                    at(w, win, a, 2, p, leadGlass(), 0);
                    at(w, win, a, 3, p, rock, BRICK);
                }
                boolean lamp = da == mid && p == (back + counter) / 2;
                if (lamp) {
                    at(w, win, a, 4, p, rock, SubstrateBlocksSC.TRACE_NODE);
                } else {
                    at(w, win, a, 4, p, steel());
                }
            }
        }
    }

    /** The tank farm (TANKS) and the Ring's own tanks either side of it. */
    private void tanks(World w, StructureBoundingBox win, int beacon, Block rock) {
        int[] t = EmbassyQuartersSC.TANKS;
        for (int da = t[0]; da <= t[2]; da++) {
            for (int p = t[1]; p <= t[3]; p++) {
                int a = beacon + da;
                boolean cornerA = da == t[0] || da == t[2], cornerP = p == t[1] || p == t[3];
                if (cornerA && cornerP) {                                    // the frame's posts, a lamp on each
                    for (int dy = 1; dy <= 3; dy++) {
                        at(w, win, a, dy, p, steel());
                    }
                    at(w, win, a, 4, p, rock, SubstrateBlocksSC.TRACE_NODE);
                } else if (!cornerA && !cornerP && (da - t[0]) % 2 == 1 && (p - t[1]) % 2 == 1) {   // a tank two high
                    machine2(w, win, a, 1, p, BlockShowcase2SC.CRYO_TANK, 0, -1);
                    machine2(w, win, a, 2, p, BlockShowcase2SC.CRYO_TANK, 0, -1);
                } else if (!cornerA && !cornerP && (p - t[1]) % 2 == 1) {    // the pipe between the two tanks of a row
                    at(w, win, a, 2, p, Blocks.iron_bars, 0);
                }
            }
        }
        int ra = beacon + EmbassyLayoutSC.RING_BACK;
        for (int s = -1; s <= 1; s += 2) {
            for (int dy = 1; dy <= RING_TANK_H; dy++) {
                machine2(w, win, ra, dy, s * RING_TANK_P, BlockShowcase2SC.CRYO_TANK, -1, 0);
            }
            at(w, win, ra, 1, s * (RING_TANK_P - 1), steel());              // the coupling to the ring's coils
            at(w, win, ra, 2, s * (RING_TANK_P - 1), Blocks.iron_bars, 0);
        }
    }
}
