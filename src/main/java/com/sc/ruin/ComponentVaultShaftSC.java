package com.sc.ruin;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.6, rule ПХ-2): a Vault's sealed shaft - "SCSi14VltShaft" - from the middle of the
 * Dead Node's plaza down to the bunker's ceiling: a 3 x 3 column of vault plating round a ladder, sealed at both ends - the
 * plaza's hatch (the plaza's floor's middle 3 x 3, plating) and the bunker's ceiling. You dig in: through the hatch's middle
 * plate, down the ladder, through the ceiling's middle plate - and the ladder goes on down to the bunker's floor. Built after the
 * Node (populate's order), so the hatch lies over the plaza's own plates; the plaza leaves its middle free of husks
 * (ComponentNodePlazaSC, MapGenVaultSC.builtAt).
 */
public class ComponentVaultShaftSC extends ComponentVaultSC {

    public ComponentVaultShaftSC() {
    }

    public ComponentVaultShaftSC(long seed, int i, int j, int floorY, int plazaY) {
        super(seed, i, j, floorY, plazaY, new StructureBoundingBox(i * com.sc.dim.SubstrateTerrainSC.GRID - 1, floorY + VaultLayoutSC.HEIGHT - 1,
                j * com.sc.dim.SubstrateTerrainSC.GRID - 1, i * com.sc.dim.SubstrateTerrainSC.GRID + 1, Math.max(plazaY, floorY + VaultLayoutSC.HEIGHT - 1),
                j * com.sc.dim.SubstrateTerrainSC.GRID + 1));
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int bottom = ceilingY();
        for (int y = bottom; y <= plazaY; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = mx + dx, z = mz + dz;
                    if (dx != 0 || dz != 0 || y == bottom || y == plazaY) {
                        plating(w, win, x, y, z);                   // the ring; the two seals
                    } else {
                        set(w, win, x, y, z, Blocks.ladder, LADDER_NORTH);
                    }
                }
            }
        }
    }
}
