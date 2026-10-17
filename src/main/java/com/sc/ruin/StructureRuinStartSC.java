package com.sc.ruin;

import java.util.List;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.2): a ruin's structure start - empty (explored ground, no site: a 1 x 1 box in
 * its chunk, never built - vanilla saves and intersects every start's box, null breaks it) or with its pieces. A public
 * constructor without arguments (MapGenStructureIO, on loading). The Dead Node's and the lithograph's start classes extend it
 * (MapGenStructureIO wants a class per name) and keep their place in its NBT (NodeI / NodeJ, LithoCX / LithoCZ).
 */
public class StructureRuinStartSC extends StructureStart {

    public StructureRuinStartSC() {
        boundingBox = new StructureBoundingBox(0, 0, 0, 0, 0, 0);
    }

    /** An empty start at chunk (cx, cz). */
    public StructureRuinStartSC(int cx, int cz) {
        super(cx, cz);
        boundingBox = new StructureBoundingBox(cx << 4, 0, cz << 4, cx << 4, 0, cz << 4);
    }

    /** A start with `parts` (the box round them). */
    @SuppressWarnings("unchecked")
    public StructureRuinStartSC(int cx, int cz, List<StructureComponent> parts) {
        super(cx, cz);
        components.addAll(parts);
        updateBoundingBox();
    }

    @Override
    public boolean isSizeableStructure() {
        return !components.isEmpty();
    }

    /**
     * ПУ-3 (as the settlements' ПП-2): a chunk of the block area [x0..x1] x [z0..z1] is on disk already (a world of Ф1 / Ф2
     * explored before the update - AnvilChunkLoader.chunkExists through the server's chunk loader) or loaded - its start
     * must be empty. The chunk being made right now is neither yet. Server; never loads a chunk.
     */
    public static boolean explored(World w, int x0, int z0, int x1, int z1) {
        IChunkProvider cp = w.getChunkProvider();
        ChunkProviderServer server = cp instanceof ChunkProviderServer ? (ChunkProviderServer) cp : null;
        IChunkLoader loader = server == null ? null : server.currentChunkLoader;
        for (int x = x0 >> 4; x <= x1 >> 4; x++) {
            for (int z = z0 >> 4; z <= z1 >> 4; z++) {
                if (server != null && server.chunkExists(x, z)
                        || loader instanceof AnvilChunkLoader && ((AnvilChunkLoader) loader).chunkExists(w, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }
}
