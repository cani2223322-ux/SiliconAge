package com.sc.arena;

import com.sc.tileentity.TileEntityBreakdownConsoleSC;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.2, rules ПУ-9, ПУ-10): what stands in a Breakdown Epicentre's pit - "SCSi14ArnPit",
 * the whole box (+-ArenaLayoutSC.HALF round the crossing, from the floor up to the wall's top), so the start's box is the arena's
 * (covers, the carve). The pit was carved when its chunks were made (MapGenArenaSC.carve); populate adds, by ArenaLayoutSC:
 * <ul>
 * <li>eight 2 x 2 columns COLUMN_H high (columnSpot) - the cover from the «сброс ядра» and the «Импульс глушения»;</li>
 * <li>four coil pedestals (coilSpot) - the Warden's summon stands its Containment Coils on them;</li>
 * <li>the Breakdown Console (CONSOLE_DX / DZ), set up for the arena's region at once (TileEntityBreakdownConsoleSC.setup);</li>
 * <li>eight lamps (lampSpot) LAMP_DY over the floor, each on a short bracket of wall plating back to the wall ring.</li>
 * </ul>
 * Every block is the arena's unbreakable plating (ArenaBlocksSC); no chest, no Node, no husk - the loot here is the Warden's only.
 * Each window places its share (a block lies in one window), so the Console is placed and set up exactly once.
 */
public class ComponentArenaPitSC extends ComponentArenaSC {

    /** The lamps' height over the floor (on the wall's face). TODO(balance) */
    public static final int LAMP_DY = 5;
    /** A lamp's bracket reaches this far back towards the wall ring at most. */
    private static final int BRACKET = 3;

    public ComponentArenaPitSC() {
    }

    public ComponentArenaPitSC(long seed, int i, int j, int floorY) {
        super(seed, i, j, floorY, new StructureBoundingBox(ArenaLayoutSC.centre(i) - ArenaLayoutSC.HALF, floorY,
                ArenaLayoutSC.centre(j) - ArenaLayoutSC.HALF, ArenaLayoutSC.centre(i) + ArenaLayoutSC.HALF, floorY + ArenaLayoutSC.WALL_H,
                ArenaLayoutSC.centre(j) + ArenaLayoutSC.HALF));
    }

    /** The Console's block {x, y, z}. */
    public int[] console() {
        return new int[]{mx + ArenaLayoutSC.CONSOLE_DX, floorY + 1, mz + ArenaLayoutSC.CONSOLE_DZ};
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        for (int k = 0; k < ArenaLayoutSC.COLUMNS; k++) {
            int[] c = ArenaLayoutSC.columnSpot(k);
            for (int y = floorY + 1; y <= floorY + ArenaLayoutSC.COLUMN_H; y++) {
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < 2; b++) {
                        plate(w, win, c[0] + a, y, c[1] + b, ArenaBlocksSC.COLUMN);
                    }
                }
            }
        }
        for (int k = 0; k < ArenaLayoutSC.COILS; k++) {
            int[] c = ArenaLayoutSC.coilSpot(k);
            plate(w, win, c[0], floorY + 1, c[1], ArenaBlocksSC.PEDESTAL);
        }
        for (int k = 0; k < ArenaLayoutSC.LAMPS; k++) {
            lamp(w, win, ArenaLayoutSC.lampSpot(k));
        }
        console(w, win);
    }

    /** A lamp at `spot`, LAMP_DY up, and its bracket of wall plating out along the ray to the ring (where the floor's disk is). */
    private void lamp(World w, StructureBoundingBox win, int[] spot) {
        int y = floorY + LAMP_DY;
        plate(w, win, spot[0], y, spot[1], ArenaBlocksSC.LAMP);
        double r = Math.sqrt(spot[0] * spot[0] + spot[1] * spot[1]);
        for (int t = 1; t <= BRACKET; t++) {
            int dx = (int) Math.round(spot[0] * (r + t) / r), dz = (int) Math.round(spot[1] * (r + t) / r);
            if (!ArenaLayoutSC.inPit(dx, dz)) {
                break;                                          // the wall ring itself
            }
            plate(w, win, dx, y, dz, ArenaBlocksSC.WALL);
        }
    }

    /** The Breakdown Console, set up for its arena right away (its window holds it, so its chunk is loaded). */
    private void console(World w, StructureBoundingBox win) {
        int[] c = console();
        if (!set(w, win, c[0], c[1], c[2], ArenaBlocksSC.arena, ArenaBlocksSC.CONSOLE)) {
            return;
        }
        TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
        if (te instanceof TileEntityBreakdownConsoleSC) {
            ((TileEntityBreakdownConsoleSC) te).setup(ri(), rj());
        }
    }
}
