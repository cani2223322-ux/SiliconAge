package com.sc.embassy;

import java.util.List;

import com.sc.settle.gen.StructureSettlementStartSC;

import net.minecraft.world.gen.structure.StructureComponent;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §5.3): one embassy's structure start - a settlement start under its own
 * MapGenStructureIO name (EmbassySC.START_NAME "SCOwEmbassy", the Overworld's data/SCOwEmbassy.dat): its pieces and the registry id
 * ("SettlementId"; an EMPTY start - old land, ПЕ-4 - has -1 and no pieces). A public constructor without arguments (MapGenStructureIO
 * makes it so when the world loads); the others give it its chunk. Shared core: only the coordinator edits this file.
 */
public class StructureEmbassyStartSC extends StructureSettlementStartSC {

    public StructureEmbassyStartSC() {
    }

    /** An empty start at chunk (cx, cz): never built (isSizeableStructure false). */
    public StructureEmbassyStartSC(int cx, int cz) {
        super(cx, cz);
    }

    /** An embassy's start at chunk (cx, cz): the record `id`, its pieces (the ground first), the box round them. */
    public StructureEmbassyStartSC(int cx, int cz, int id, List<StructureComponent> parts) {
        super(cx, cz, id, parts);
    }
}
