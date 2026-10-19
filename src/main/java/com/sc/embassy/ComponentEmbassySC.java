package com.sc.embassy;

import com.sc.block.BlockSettlementDecorSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.ComponentSettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §5.3, §6.2): a piece of an embassy - a settlement's piece (ComponentSettlementSC: built
 * only inside the window it is handed, flag 2, light set by hand, the trim's palette, the lot's frame u / v / dy) under the embassies'
 * MapGenStructureIO names: this base one "SCOwEmbPart" (builds nothing), package S's pieces "SCOwEmb" + their name
 * (EmbassyPiecesSC.register), package G's ground "SCOwEmbGround". A public constructor without arguments (MapGenStructureIO).
 * <p>
 * The rules of an embassy's pieces (on top of a settlement's): they build in the Overworld - vanilla's blocks are allowed (the
 * Overworld's stone, glass, wool...) but no block a player could take out for value (no ore block, no valuable of the mod: the trim's
 * metal, coils and lead glass are BlockSettlementDecorSC, unbreakable); not one log or plank in a building (ПМ-7: the Tuners have no
 * wood - the greenhouse is the only green); every piece is idempotent - built twice it puts the same (the «Достройка» runs it again
 * over a window: ComponentSettlementSC.put's setBlock of the same block and meta changes nothing, a tile entity is never made twice -
 * a core block already standing is not set up again). Shared core: only the coordinator edits this file.
 * <p>
 * The review's (§12 п.7): the pieces draw the Substrate's Copper Trace and its lit node and vanilla's quartz block (the Heart's
 * circuit, the streets' axis, the lamps, the Vokzal's guides, the dais), but those drop crushed copper ore / themselves - a tool that
 * breaks past the events (the Ender Quarry, Equal Trade) would take them and the «Достройка» put them back for ever. So every block of
 * an embassy's piece goes through trim(): those are the trim's look-alikes (BlockSettlementDecorSC.TRACE, NODE - lit as the node -,
 * QUARTZ, QUARTZ_CHISELED, QUARTZ_PILLAR: unbreakable, no drop); set() here and ComponentEmbFrameSC.at() ask it.
 */
public class ComponentEmbassySC extends ComponentSettlementSC {

    public ComponentEmbassySC() {
    }

    /** A piece on `lot` of embassy `id`, from `dyMin` to `dyMax` over the floor. */
    protected ComponentEmbassySC(int id, int floorY, SettlementLayoutSC.Lot lot, int dyMin, int dyMax) {
        super(id, floorY, lot, dyMin, dyMax);
    }

    @Override
    protected boolean set(World w, StructureBoundingBox win, int u, int dy, int v, Block b, int meta) {
        int t = trim(b, meta);
        return t < 0 ? super.set(w, win, u, dy, v, b, meta) : super.set(w, win, u, dy, v, SettlementBlocksSC.decor, t);
    }

    /**
     * The trim's meta an embassy puts for `b` / `meta` (see the class comment), -1 - the block itself: the Copper Trace and its node,
     * the quartz block (plain, chiseled, a pillar - any pillar's way stands upright). Pure.
     */
    public static int trim(Block b, int meta) {
        if (b == null || SettlementBlocksSC.decor == null) {
            return -1;
        }
        if (b == SubstrateBlocksSC.rock && meta == SubstrateBlocksSC.COPPER_TRACE) {
            return BlockSettlementDecorSC.TRACE;
        }
        if (b == SubstrateBlocksSC.rock && meta == SubstrateBlocksSC.TRACE_NODE) {
            return BlockSettlementDecorSC.NODE;
        }
        if (b == Blocks.quartz_block) {
            return meta == 1 ? BlockSettlementDecorSC.QUARTZ_CHISELED : meta >= 2 ? BlockSettlementDecorSC.QUARTZ_PILLAR : BlockSettlementDecorSC.QUARTZ;
        }
        return -1;
    }
}
