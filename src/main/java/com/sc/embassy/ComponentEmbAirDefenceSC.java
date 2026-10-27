package com.sc.embassy;

import com.sc.block.BlockSettlementDecorSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbAirDefence" - an emplacement «Зенитный пост Наладчиков» (LOT_AIR_DEFENCE, variant - its index in Field.aaPosts): a 5 x 5 platform of the trim
 * (AA_HALF), a parapet one high round it, the gun BlockShowcase2SC.AA_POST at exactly Field.aaBlock(variant) (its front away from the
 * core), H_AD high at most. The Vehicles add-on fires from that gun (the core's emplacements are silent without it).
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy -1 .. H_AD + 2). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: a steel platform (the trim's) at the floor; a stone brick parapet one high round it on titanium corner posts, each with a lit
 * node on top (H_AD), a gap in the middle of the side towards the core (the way in); the gun in the middle, its front away from the
 * core along the post's longer offset (the add-on's shells leave through the parapet's open sky over the platform).
 */
public class ComponentEmbAirDefenceSC extends ComponentEmbAirSC {

    public ComponentEmbAirDefenceSC() {
    }

    public ComponentEmbAirDefenceSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyAirfieldSC.H_AD + 2);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        if (in == null || variant < 0 || variant >= f.aaPosts.length || !trimReady()) {
            return;
        }
        int[] p = f.aaPosts[variant];
        int k = EmbassyAirfieldSC.AA_HALF;
        int fa = Math.abs(p[0]) >= Math.abs(p[1]) ? Integer.signum(p[0]) : 0, fq = fa != 0 ? 0 : Integer.signum(p[1]);
        for (int du = -k; du <= k; du++) {
            for (int dv = -k; dv <= k; dv++) {
                int a = p[0] + du, q = p[1] + dv;
                boolean rim = Math.abs(du) == k || Math.abs(dv) == k, corner = Math.abs(du) == k && Math.abs(dv) == k;
                boolean entry = fa != 0 ? du == -fa * k && dv == 0 : dv == -fq * k && du == 0;
                atq(w, in, a, 0, q, SettlementBlocksSC.decor, BlockSettlementDecorSC.STEEL);
                if (du == 0 && dv == 0) {
                    machineq(w, in, a, EmbassyAirfieldSC.AA_GUN_DY, q, BlockShowcase2SC.AA_POST, fa, fq);
                    airq(w, in, a, EmbassyAirfieldSC.AA_GUN_DY + 1, q);
                } else if (corner) {
                    atq(w, in, a, 1, q, SettlementBlocksSC.decor, BlockSettlementDecorSC.TITANIUM);
                    atq(w, in, a, EmbassyAirfieldSC.H_AD, q, SettlementBlocksSC.decor, BlockSettlementDecorSC.NODE);
                } else {
                    if (rim && !entry) {
                        atq(w, in, a, 1, q, Blocks.stonebrick, 0);
                    } else {
                        airq(w, in, a, 1, q);
                    }
                    airq(w, in, a, EmbassyAirfieldSC.H_AD, q);
                }
            }
        }
    }
}
