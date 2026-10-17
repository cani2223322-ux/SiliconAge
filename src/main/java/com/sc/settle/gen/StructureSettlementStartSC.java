package com.sc.settle.gen;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.4): one settlement's structure start - its pieces and the registry id
 * (kept in its NBT: "SettlementId"; an EMPTY start - see MapGenSettlementSC - has id -1 and no pieces). A public
 * constructor without arguments (MapGenStructureIO makes it so when the world loads); the others give the start its
 * chunk (vanilla keeps every start in DIM-1414/data under "[chunkX,chunkZ]" - one without it would overwrite the
 * others').
 */
public class StructureSettlementStartSC extends StructureStart {

    /** The registry's id, -1 an empty start. */
    protected int settlementId = -1;

    public StructureSettlementStartSC() {
        boundingBox = new StructureBoundingBox(0, 0, 0, 0, 0, 0);   // vanilla saves / intersects every start's box: never null
    }

    /** An empty start at chunk (cx, cz): a 1 x 1 box in it, no pieces, never built (isSizeableStructure false). */
    public StructureSettlementStartSC(int cx, int cz) {
        super(cx, cz);
        boundingBox = new StructureBoundingBox(cx << 4, 0, cz << 4, cx << 4, 0, cz << 4);
    }

    /** A settlement's start at chunk (cx, cz): the record `id`, its pieces (the ground first), the box round them. */
    @SuppressWarnings("unchecked")
    public StructureSettlementStartSC(int cx, int cz, int id, List<StructureComponent> parts) {
        super(cx, cz);
        settlementId = id;
        components.addAll(parts);
        updateBoundingBox();
    }

    public int settlementId() {
        return settlementId;
    }

    @Override
    public boolean isSizeableStructure() {
        return settlementId >= 0 && !components.isEmpty();
    }

    @Override
    public void func_143022_a(NBTTagCompound nbt) {
        super.func_143022_a(nbt);
        nbt.setInteger("SettlementId", settlementId);
    }

    @Override
    public void func_143017_b(NBTTagCompound nbt) {
        super.func_143017_b(nbt);
        settlementId = nbt.hasKey("SettlementId") ? nbt.getInteger("SettlementId") : -1;
    }
}
