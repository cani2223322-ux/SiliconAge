package com.sc.arena;

import java.util.Random;

import com.sc.ruin.ComponentRuinSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.2, rules ПУ-9, ПУ-10): a piece of a Breakdown Epicentre - "SCSi14ArnPart" is this
 * base piece (it builds nothing; MapGenStructureIO wants a class per name). It keeps the arena's place (NBT "Arena": its crossing i,
 * j, the pit's middle column, the floor's Y) and the helpers of its pieces. As every ruin piece (ComponentRuinSC): only inside the
 * populate window it is handed (no cascade), flag 2, from its own seed and the pure layout (ArenaLayoutSC), never Blocks.stone, a
 * lamp lit by hand where setBlock gave up (ComponentRuinSC.put - ChunkProviderSubstrateSC.glow).
 * <p>
 * The pit itself - the floor's plating, the wall ring, the air up to the sky, the funnel - is no piece: MapGenArenaSC.carve cuts it
 * into the chunks' arrays when they are made (some 160 000 blocks of air by setBlock would crawl). The floor's Breakdown scars
 * (scar: thin jagged rays of wall plating in the floor's) are pure, so the carve asks them here.
 */
public class ComponentArenaSC extends ComponentRuinSC {

    /** The floor's scars: rays from SCAR_FROM to at most SCAR_TO off the middle, the epicentre's ring round it. TODO(balance) */
    public static final int SCARS = 5, SCAR_FROM = 4, SCAR_TO = 25;
    private static final long SALT = 0xB4EA7D0L;

    /** The crossing, the pit's middle (the crossing's world x / z), the floor's Y. */
    protected int i, j, mx, mz, floorY;

    public ComponentArenaSC() {
    }

    protected ComponentArenaSC(long seed, int i, int j, int floorY, StructureBoundingBox box) {
        super(seed, box);
        this.i = i;
        this.j = j;
        this.mx = ArenaLayoutSC.centre(i);
        this.mz = ArenaLayoutSC.centre(j);
        this.floorY = floorY;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Arena", new int[]{i, j, mx, mz, floorY});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Arena");
        if (a.length == 5) {
            i = a[0];
            j = a[1];
            mx = a[2];
            mz = a[3];
            floorY = a[4];
        }
    }

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox window) {
        return ArenaBlocksSC.arena == null || super.addComponentParts(world, rand, window);
    }

    /** The arena's region (ArenaDataSC's record, the Console's setup). */
    public int ri() {
        return ArenaLayoutSC.regionOf(i);
    }

    public int rj() {
        return ArenaLayoutSC.regionOf(j);
    }

    public int floorY() {
        return floorY;
    }

    /** The pit's middle on its floor {x, floorY + 1, z}. */
    public int[] middle() {
        return new int[]{mx, floorY + 1, mz};
    }

    /** An arena block (ArenaBlocksSC meta) at the offset (dx, dz) from the middle, Y y. @return placed */
    protected boolean plate(World w, StructureBoundingBox win, int dx, int y, int dz, int meta) {
        return set(w, win, mx + dx, y, mz + dz, ArenaBlocksSC.arena, meta);
    }

    // ------------------------------------------------------------------ pure

    /** The arena of crossing (i, j)'s own seed (its scars). */
    public static long seedOf(long worldSeed, int i, int j) {
        return ArenaLayoutSC.mix(worldSeed ^ SALT, i, j);
    }

    /**
     * The floor's Breakdown scars (pure; MapGenArenaSC.carve lays them in wall plating): a thin ring round the epicentre (the
     * Warden rises there) and SCARS jagged rays out from it, their angles and lengths from the arena's seed `seed`.
     */
    public static boolean scar(long seed, int dx, int dz) {
        int d2 = dx * dx + dz * dz;
        if (d2 >= 5 && d2 <= 10) {
            return true;                                        // the epicentre's ring
        }
        if (d2 < SCAR_FROM * SCAR_FROM || d2 > SCAR_TO * SCAR_TO) {
            return false;
        }
        double r = Math.sqrt(d2), a = Math.atan2(dx, -dz);     // 0 north, clockwise
        for (int k = 0; k < SCARS; k++) {
            long h = ArenaLayoutSC.mix(seed, k, 0x5CA2L);
            if (r > SCAR_TO - ((h >>> 40) & 7)) {
                continue;                                       // this ray ends sooner
            }
            double base = (k + (((h >>> 8) & 1023) / 1024D - 0.5D) * 0.6D) * (2D * Math.PI / SCARS);
            double wobble = Math.sin(r * 0.45D + ((h >>> 20) & 255) / 40D) * 1.6D / r;
            double da = a - base - wobble;
            da -= 2D * Math.PI * Math.floor((da + Math.PI) / (2D * Math.PI));
            if (Math.abs(da) * r < 0.55D) {
                return true;
            }
        }
        return false;
    }
}
