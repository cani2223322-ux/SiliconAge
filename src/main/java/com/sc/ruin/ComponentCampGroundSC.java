package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.tileentity.TileEntityStolenFieldSC;
import com.sc.tileentity.TileEntityStolenSolarSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.2, rules ПЗ-1..ПЗ-6): a Renegades' camp - "SCSi14CmpGround" - the whole camp on
 * the canyon's floor (CampLayoutSC's box; the spots - ComponentCampSC):
 * <ul>
 * <li>the floor levelled at the middle's floor (Crystallite filled up to it, the rock over it cleared) across the canyon - the
 * walls stay; Ruin Metal plates round the generator, a few scattered over the yard;</li>
 * <li>four tarps (ruinSC campTarp) TARP_H over the floor along both walls, TARP_DEPTH deep, on Ruin Metal posts; under them the
 * camp's junk (tank and machine husks, a burnt panel) and, by the +c wall, the Renegades' safe (campSC CAMP_SAFE);</li>
 * <li>the Stolen Field Generator in the middle - setup(FIELD_RADIUS, the camp's key) right after its block, its buffer full;</li>
 * <li>the four stolen panels at the box's ends (outside the dome, under the sky), each bound to the generator; the stolen
 * charge pad at the Executioner's end, if the camp has one;</li>
 * <li>ПЗ-6, once (NBT "Residents"), in the populate window that holds the middle - all its chunks are loaded then: the Warlord
 * nearest the middle, the camp's Marauders about the yard, the Executioner nearest the pad - SpawnDirectorSC.spawnAt (the
 * Warlord without the elite roll: it is one), born in Si-14, persistent, at home in the box (RuinWorldSC.settle); the Warlord
 * carries its safe (scCampSafe) and goes into RareRegistrySC under the camp's key; the camp's record keeps its spots
 * (RuinWorldSC.campMade) for its return.</li>
 * </ul>
 * No block of the mod's own machines (ПЗ-2, ПЗ-4): the generator, panels and pad are the camp's blocks, scrap when broken.
 */
public class ComponentCampGroundSC extends ComponentCampSC {

    /** Scattered plates over the yard (%), how far along from the middle. */
    public static final int PLATE_PCT = 6, YARD_A = 14;
    /** The camp's junk under the tarps: {a, c sign (+1: the +c wall), Ruin meta, height}; c is the wall's column. */
    private static final int[][] JUNK = {{4, 1, BlockRuinSC.TANK_HUSK, 2}, {8, 1, BlockRuinSC.TANK_HUSK, 1},
            {4, -1, BlockRuinSC.MACHINE_HUSK, 1}, {7, -1, BlockRuinSC.MACHINE_HUSK, 1},
            {-4, 1, BlockRuinSC.RUIN_METAL, 1}, {-7, 1, BlockRuinSC.TANK_HUSK, 1},
            {-5, -1, BlockRuinSC.TANK_HUSK, 2}, {-8, -1, BlockRuinSC.MACHINE_HUSK, 1}};

    private boolean residents;

    public ComponentCampGroundSC() {
    }

    public ComponentCampGroundSC(CampLayoutSC.Site s, int hw) {
        super(s, hw);
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setBoolean("Residents", residents);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        residents = nbt.getBoolean("Residents");
    }

    /** The residents were made (the world test asks). */
    public boolean residentsMade() {
        return residents;
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        if (CampBlocksSC.camp == null || CampBlocksSC.stolenField == null) {
            return;
        }
        ground(w, win);
        tarps(w, win);
        junk(w, win);
        int[] s = safe();
        set(w, win, s[0], s[1], s[2], CampBlocksSC.camp, CampBlocksSC.CAMP_SAFE);
        int[] f = field();
        if (set(w, win, f[0], f[1], f[2], CampBlocksSC.stolenField, 0)) {
            TileEntity te = w.getTileEntity(f[0], f[1], f[2]);
            if (te instanceof TileEntityStolenFieldSC) {
                TileEntityStolenFieldSC g = (TileEntityStolenFieldSC) te;
                g.setup(CampLayoutSC.FIELD_RADIUS, key());
                g.feed(g.getMaxEnergyStored());                         // the buffer full: the dome up from the first tick
            }
        }
        for (int[] p : panels()) {
            if (set(w, win, p[0], p[1], p[2], CampBlocksSC.camp, CampBlocksSC.STOLEN_SOLAR)) {
                TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof TileEntityStolenSolarSC) {
                    ((TileEntityStolenSolarSC) te).bind(f[0], f[1], f[2]);
                }
            }
        }
        int[] pad = pad();
        if (pad != null) {
            set(w, win, pad[0], pad[1], pad[2], CampBlocksSC.camp, CampBlocksSC.STOLEN_PAD);
        }
        if (!residents && win.isVecInside(mx, floorY + 1, mz)) {
            residents = true;                                   // once, whatever happens below (the start is saved after this window)
            residents(w, win);
        }
    }

    /** The floor levelled across the canyon (the walls stay): filled up to floorY, cleared over it; the plates. */
    private void ground(World w, StructureBoundingBox win) {
        long ws = w.getSeed();
        int x0 = Math.max(boundingBox.minX, win.minX), x1 = Math.min(boundingBox.maxX, win.maxX);
        int z0 = Math.max(boundingBox.minZ, win.minZ), z1 = Math.min(boundingBox.maxZ, win.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int a = la(x, z), c = lc(x, z);
                if (Math.abs(c) > hw) {
                    continue;                                   // a wall's column
                }
                int natural = SubstrateTerrainSC.canyonFloorY(ws, x, z);
                for (int y = Math.max(floorY - BELOW, natural + 1); y < floorY; y++) {
                    rock(w, win, x, y, z, SubstrateBlocksSC.CRYSTALLITE);
                }
                boolean plate = Math.abs(a) <= 2 && Math.abs(c) <= 1 || Math.abs(a) <= YARD_A && roll(x, floorY, z, 31) < PLATE_PCT;
                if (plate) {
                    ruin(w, win, x, floorY, z, BlockRuinSC.RUIN_METAL);
                } else {
                    rock(w, win, x, floorY, z, SubstrateBlocksSC.CRYSTALLITE);
                }
                for (int y = floorY + 1; y <= floorY + ABOVE; y++) {
                    air(w, win, x, y, z);
                }
            }
        }
    }

    /** The tarps along both walls and their posts. */
    private void tarps(World w, StructureBoundingBox win) {
        for (int side = -1; side <= 1; side += 2) {
            for (int[] stretch : TARP_A) {
                for (int a = stretch[0]; a <= stretch[1]; a++) {
                    for (int k = 0; k < TARP_DEPTH; k++) {
                        int c = side * (hw - k);
                        ruin(w, win, wx(a, c), floorY + TARP_H, wz(a, c), BlockRuinSC.CAMP_TARP);
                    }
                }
                int c = side * (hw - TARP_DEPTH + 1);
                for (int end = 0; end < 2; end++) {
                    int a = stretch[end];
                    for (int y = 1; y < TARP_H; y++) {
                        ruin(w, win, wx(a, c), floorY + y, wz(a, c), BlockRuinSC.RUIN_METAL);
                    }
                }
            }
        }
    }

    /** The camp's junk by the walls under the tarps (a burnt panel heaped on the first machine). */
    private void junk(World w, StructureBoundingBox win) {
        for (int i = 0; i < JUNK.length; i++) {
            int[] j = JUNK[i];
            int a = j[0], c = j[1] * hw;
            for (int y = 1; y <= j[3]; y++) {
                ruin(w, win, wx(a, c), floorY + y, wz(a, c), j[2]);
            }
            if (i == 2) {
                ruin(w, win, wx(a, c), floorY + 2, wz(a, c), BlockRuinSC.BURNT_PANEL);
            }
        }
    }

    // ------------------------------------------------------------------ ПЗ-6: the residents, once

    /** The Warlord, the Marauders, the Executioner - inside the window (it is loaded); the camp's record. */
    private void residents(World w, StructureBoundingBox win) {
        List<int[]> spots = spots(w, win);
        Random r = layout(9);
        int[] mid = middle(), safe = safe(), pad = pad();
        RuinWorldSC.campMade(w, key(), mid, safe, field(), panels(), pad, boxArray(), CampLayoutSC.HALF_LEN, marauders, executioner);
        if (!(w instanceof WorldServer) || spots.isEmpty()) {
            return;                                             // the record stands: RuinWorldSC brings them when it can
        }
        WorldServer ws = (WorldServer) w;
        int[] home = {mx, floorY + 1, mz, CampLayoutSC.HALF_LEN};
        int[] spot = take(spots, 0, 0, null);
        if (spot != null) {
            RuinWorldSC.warlord(ws, key(), spot[0], spot[1], spot[2], home, safe);
        }
        if (executioner && pad != null) {
            spot = take(spots, la(pad[0], pad[2]), lc(pad[0], pad[2]), null);
            if (spot != null) {
                RuinWorldSC.resident(ws, MobEntitiesSC.ID_EXECUTIONER, spot[0], spot[1], spot[2], home);
            }
        }
        for (int m = 0; m < marauders && !spots.isEmpty(); m++) {
            spot = take(spots, 0, 0, r);
            if (spot != null) {
                RuinWorldSC.resident(ws, MobEntitiesSC.ID_MARAUDER, spot[0], spot[1], spot[2], home);
            }
        }
    }

    /** The floor spots inside the window with two blocks of air over them, off the generator and the tarps' posts. */
    private List<int[]> spots(World w, StructureBoundingBox win) {
        List<int[]> out = new ArrayList<int[]>();
        int y = floorY + 1;
        for (int a = -CampLayoutSC.HALF_LEN + 3; a <= CampLayoutSC.HALF_LEN - 3; a++) {
            for (int c = -(hw - 1); c <= hw - 1; c++) {
                int x = wx(a, c), z = wz(a, c);
                if (Math.abs(a) <= 1 && Math.abs(c) <= 1 || !win.isVecInside(x, y, z) || !win.isVecInside(x, y + 1, z)
                        || !w.isAirBlock(x, y, z) || !w.isAirBlock(x, y + 1, z) || !solid(w, win, x, floorY, z)) {
                    continue;
                }
                out.add(new int[]{x, y, z});
            }
        }
        return out;
    }

    /** Takes a spot out of the list: the one nearest local (a, c), or a random one when `r` is given. */
    private int[] take(List<int[]> spots, int a, int c, Random r) {
        if (spots.isEmpty()) {
            return null;
        }
        int best = 0;
        if (r != null) {
            best = r.nextInt(spots.size());
        } else {
            int bestD = Integer.MAX_VALUE;
            for (int i = 0; i < spots.size(); i++) {
                int[] s = spots.get(i);
                int da = la(s[0], s[2]) - a, dc = lc(s[0], s[2]) - c, d = da * da + dc * dc;
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
        }
        return spots.remove(best);
    }

    /** The camp's box {minX, minZ, maxX, maxZ}. */
    private int[] boxArray() {
        return new int[]{boundingBox.minX, boundingBox.minZ, boundingBox.maxX, boundingBox.maxZ};
    }
}
