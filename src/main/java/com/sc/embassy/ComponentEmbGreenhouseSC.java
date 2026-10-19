package com.sc.embassy;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.1, §6.2, rule ПЕ-7): «Оранжерея» - "SCOwEmbGreenhouse", EmbassyQuartersSC.GREENHOUSE
 * square, its door towards the core: the embassy's only green (the Tuners have no wood - ПМ-7 - and keep what the green world grows
 * under glass). Walls of glass on steel posts, a glass roof on steel ribs with a lamp where they cross; inside,
 * a quartz rim, brick paths (round the edge, a cross through the middle, nodes at the corners) and four beds of grass - in each a
 * shrub of vanilla leaves that never decays (one kind a bed) and flowers between; in the middle the Tuners' «кварцевое дерево»: a
 * quartz pillar under a crown of leaves. Leaves and flowers only - no log, no plank, no sapling; the grass under glass keeps its
 * light. Idempotent: every block of it is set once a pass.
 */
public class ComponentEmbGreenhouseSC extends ComponentEmbassySC {

    /** Leaves that never decay (the «no decay» bit 4): oak, birch, spruce, jungle of Blocks.leaves; acacia, dark oak of leaves2. */
    private static final int NO_DECAY = 4;
    /** The four beds' leaves: {block (0 leaves, 1 leaves2), its kind}. */
    private static final int[][] SHRUBS = {{0, 0}, {0, 2}, {1, 0}, {0, 3}};
    /** The flowers: red_flower's nine (poppy .. oxeye daisy) and the dandelion (-1). */
    private static final int[] FLOWERS = {0, 1, 2, 3, 4, 5, 6, 7, 8, -1};

    public ComponentEmbGreenhouseSC() {
    }

    public ComponentEmbGreenhouseSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, EmbassyQuartersSC.GREENHOUSE_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int n = width(), c = n / 2;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                boolean edgeU = u == 0 || u == n - 1, edgeV = v == 0 || v == n - 1, edge = edgeU || edgeV;
                boolean path = u == 1 || u == n - 2 || v == 1 || v == n - 2 || u == c || v == c;
                int bed = bed(u, v, c);
                // the floor
                if (edge) {
                    set(w, win, u, 0, v, Blocks.quartz_block, 0);
                } else if (path) {
                    boolean node = (u == 1 || u == n - 2) && (v == 1 || v == n - 2);
                    set(w, win, u, 0, v, rock(), node ? SubstrateBlocksSC.TRACE_NODE : BRICK);
                } else {
                    set(w, win, u, 0, v, Blocks.grass, 0);
                }
                // the roof: steel on the rim and the cross, glass between, the lamp where the ribs cross
                if (u == c && v == c) {
                    set(w, win, u, 5, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                } else if (edge || u == c || v == c) {
                    set(w, win, u, 5, v, steel());
                } else {
                    set(w, win, u, 5, v, Blocks.glass, 0);
                }
                if (edge) {                                                // the walls: glass on steel posts, the door 3 wide
                    boolean post = edgeU && edgeV || (edgeV ? u : v) % 4 == 0;
                    boolean door = v == 0 && Math.abs(u - c) <= 1;
                    for (int dy = 1; dy <= 4; dy++) {
                        if (door && dy <= 3) {
                            clear(w, win, u, dy, v);
                        } else if (post || dy == 4) {
                            set(w, win, u, dy, v, steel());
                        } else {
                            set(w, win, u, dy, v, Blocks.glass, 0);
                        }
                    }
                    continue;
                }
                if (u == c && v == c) {                                    // «кварцевое дерево»: the pillar
                    for (int dy = 1; dy <= 3; dy++) {
                        set(w, win, u, dy, v, Blocks.quartz_block, 2);
                    }
                    set(w, win, u, 4, v, Blocks.leaves, NO_DECAY);
                } else if (Math.abs(u - c) <= 1 && Math.abs(v - c) <= 1) {  // its crown
                    set(w, win, u, 4, v, Blocks.leaves, NO_DECAY);
                } else if (bed >= 0) {
                    plant(w, win, u, v, c, bed);
                }
            }
        }
    }

    /** The bed (0..3) of an inner cell off the paths, or -1. */
    private static int bed(int u, int v, int c) {
        if (u <= 1 || v <= 1 || u == c || v == c) {
            return -1;
        }
        return (u < c ? 0 : 1) + (v < c ? 0 : 2);
    }

    /** A bed's cell: the shrub in the bed's middle (two high, a ring of leaves round its foot), flowers on every other cell. */
    private void plant(World w, StructureBoundingBox win, int u, int v, int c, int bed) {
        int n = width();
        if (u >= n - 2 || v >= n - 2) {
            return;                                                        // the far ring path
        }
        int u0 = u < c ? 2 : c + 1, v0 = v < c ? 2 : c + 1, mu = u0 + (c - 3) / 2, mv = v0 + (c - 3) / 2;
        int[] s = SHRUBS[bed % SHRUBS.length];
        Block leaves = s[0] == 0 ? Blocks.leaves : Blocks.leaves2;
        int du = Math.abs(u - mu), dv = Math.abs(v - mv);
        if (du == 0 && dv == 0) {
            set(w, win, u, 1, v, leaves, s[1] | NO_DECAY);
            set(w, win, u, 2, v, leaves, s[1] | NO_DECAY);
        } else if (du + dv == 1) {
            set(w, win, u, 1, v, leaves, s[1] | NO_DECAY);
        } else if ((u + v) % 2 == 0) {
            int f = FLOWERS[(u * 7 + v * 3 + bed * 5) % FLOWERS.length];
            if (f < 0) {
                set(w, win, u, 1, v, Blocks.yellow_flower, 0);
            } else {
                set(w, win, u, 1, v, Blocks.red_flower, f);
            }
        }
    }
}
