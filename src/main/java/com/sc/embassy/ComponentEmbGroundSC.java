package com.sc.embassy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.sc.settle.SettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE G (docs/plan-substrate-tech-f6.md §5.5, rule ПЕ-6): an embassy's ground - "SCOwEmbGround", always the first piece of its
 * start, its box the plan's footprint (y 1..255). The land was levelled in the chunk's own arrays (EmbassyWorldGenSC's
 * ReplaceBiomeBlocks); vanilla's caves and ravines cut it after that, so per column of the window on the level ground
 * (EmbassyLayoutSC.levelPast <= 0) this piece fills every air or liquid block from SettlementSC.FOUNDATION under the floor up to it
 * (the biome's filler under the top, stone deeper), puts the biome's top block (grass, sand) at the floor where it is missing, and
 * clears what grew or fell over the floor before the buildings (a tree from a neighbour's window, a lake's ice, a meteor's crater
 * rim) up to the dome's shell height. Fixed (the coordinator's): the class, its name, the constructor from a plan. The rest G's.
 * <ul>
 * <li><b>The first build</b> (populate - the buildings of the window come after this piece, and no piece builds in a window before
 * its populate): over the floor under the dome's shell (a hemisphere of the dome's radius on the floor) and over the Vokzal's
 * platform (VOKZAL_CLEAR high) every block goes, higher in the cylinder growth only (a treetop over the shell at the dome's edge -
 * every lot of the city is under the shell); in the blend band only the caves under its new top are filled (the tops the
 * levelling remembered this run - EmbassyTerrainSC.bandTarget; a band column levelled in another run is left as it is);</li>
 * <li><b>the «Достройка»</b> (EmbassyRepairSC.repairing: the same window again, the city standing): only the protected ground - the
 * cylinder and the Vokzal's dock zone (a player's dig out on the open level ground stays dug), the greenhouse's lots not (their beds
 * and water are the greenhouse piece's) - is filled the same way, and over the floor only growth goes (foreign: logs - not one in an
 * embassy's building, ПМ-7 -, huge mushrooms, snow, ice, cactus; leaves, plants, vines and gourds too outside the greenhouse's
 * lots): a building's own block is never touched, so a pass changes nothing that is in place.</li>
 * <li><b>Thaumcraft's taint</b> (the review's: its fibres and crust are of TC's own material - not replaceable, not plants - so
 * neither rule above saw them): Thaumcraft:blockTaintFibres and Thaumcraft:blockTaint (found by name once, TC absent - none) are
 * growth over the floor and hollow at and under it; a protected column whose biome turned TC's Tainted Land (the fibres spread only
 * in it) gets the generator's own back during the «Достройка» (TC's own Utils.setBiomeAt, which tells the clients round; it missing -
 * the chunk's array).</li>
 * </ul>
 * NBT (G's): "Core" {core x, z, radius, level radius}, "Dock" {beacon x, z, dock zone}, "Green" {x0, z0, x1, z1} per greenhouse lot.
 */
public class ComponentEmbGroundSC extends ComponentEmbassySC {

    /** Over the Vokzal's platform the first build clears this high over the floor (the ring's top is RING_DY + RING_R). */
    public static final int VOKZAL_CLEAR = 40;
    /** Under the top: this many blocks of the biome's filler, then stone (SettlementSC.FOUNDATION deep in all). */
    public static final int FILLER = 3;

    private int coreX, coreZ, radius, levelRadius, dockX, dockZ, dockZone;
    /** The greenhouse lots {x0, z0, x1, z1} (their leaves and flowers are the city's own). */
    private int[] greens = new int[0];

    public ComponentEmbGroundSC() {
    }

    public ComponentEmbGroundSC(int id, EmbassyLayoutSC.Plan p) {
        super(id, p.floorY, new com.sc.settle.gen.SettlementLayoutSC.Lot(-1, p.footprint[0], p.footprint[1], p.footprint[2], p.footprint[3],
                p.gateDir, 0, 0), -255, 255);
        boundingBox.minY = 1;
        boundingBox.maxY = 255;
        coreX = p.coreX;
        coreZ = p.coreZ;
        radius = p.radius;
        levelRadius = p.levelRadius;
        dockX = p.dockX;
        dockZ = p.dockZ;
        dockZone = EmbassyLayoutSC.DOCK_ZONE;
        List<Integer> g = new ArrayList<Integer>();
        for (SettlementLayoutSC.Lot lot : p.lots) {
            if (lot.kind == EmbassyLayoutSC.LOT_GREENHOUSE) {
                g.add(Integer.valueOf(lot.x0));
                g.add(Integer.valueOf(lot.z0));
                g.add(Integer.valueOf(lot.x1));
                g.add(Integer.valueOf(lot.z1));
            }
        }
        greens = new int[g.size()];
        for (int i = 0; i < greens.length; i++) {
            greens[i] = g.get(i).intValue();
        }
    }

    /** The ground piece of an embassy's start (its first), or null. */
    public static ComponentEmbGroundSC of(StructureStart st) {
        if (st == null) {
            return null;
        }
        for (Object o : st.getComponents()) {
            if (o instanceof ComponentEmbGroundSC) {
                return (ComponentEmbGroundSC) o;
            }
        }
        return null;
    }

    /** ПЕ-6: chunk (cx, cz)'s arrays levelled for this embassy (EmbassyTerrainSC.levelChunk with its numbers). @return columns levelled */
    public int levelChunk(Block[] blocks, byte[] metas, int cx, int cz, byte[] bandOut) {
        return EmbassyTerrainSC.levelChunk(blocks, metas, cx, cz, coreX, coreZ, levelRadius, floorY, bandOut);
    }

    public int coreX() {
        return coreX;
    }

    public int coreZ() {
        return coreZ;
    }

    public int levelRadius() {
        return levelRadius;
    }

    /** The dome's radius (a lost record's site: EmbassySiteSC.siteOfStart). */
    public int radius() {
        return radius;
    }

    public int floor() {
        return floorY;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Core", new int[]{coreX, coreZ, radius, levelRadius});
        nbt.setIntArray("Dock", new int[]{dockX, dockZ, dockZone});
        nbt.setIntArray("Green", greens);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] c = ints(nbt, "Core", 4);
        coreX = c[0];
        coreZ = c[1];
        radius = Math.max(1, c[2]);
        levelRadius = Math.max(radius, c[3]);
        int[] d = ints(nbt, "Dock", 3);
        dockX = d[0];
        dockZ = d[1];
        dockZone = d[2] > 0 ? d[2] : EmbassyLayoutSC.DOCK_ZONE;
        int[] g = nbt.getIntArray("Green");
        greens = g.length % 4 == 0 ? g : new int[0];
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        boolean repair = EmbassyRepairSC.repairing();
        int x0 = Math.max(boundingBox.minX, win.minX), x1 = Math.min(boundingBox.maxX, win.maxX);
        int z0 = Math.max(boundingBox.minZ, win.minZ), z1 = Math.min(boundingBox.maxZ, win.maxZ);
        long r2 = (long) radius * radius, zone2 = (long) dockZone * dockZone;
        int vh = EmbassyLayoutSC.VOKZAL_HALF;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - coreX, dz = z - coreZ;
                double past = EmbassyLayoutSC.levelPast(dx, dz, levelRadius);
                if (past >= EmbassyLayoutSC.BLEND) {
                    continue;
                }
                long d2 = (long) dx * dx + (long) dz * dz, bx = x - dockX, bz = z - dockZ;
                boolean dome = d2 <= r2, vokzal = Math.abs(bx) <= vh && Math.abs(bz) <= vh, zone = bx * bx + bz * bz <= zone2;
                if (repair && !dome && !zone) {
                    continue;                                   // the «Достройка» puts back only the protected ground
                }
                if (repair) {
                    untaint(w, x, z);                           // before the fill: Tainted Land's top block is its tainted soil
                }
                int top = floorY;
                if (past > 0D) {
                    top = repair ? -1 : EmbassyTerrainSC.bandTarget(x, z);
                    if (top < 0) {
                        continue;                               // a band column whose new top this run doesn't know
                    }
                }
                boolean green = green(x, z);
                if (!repair || !green) {
                    fill(w, win, x, z, top);                    // (a standing greenhouse keeps its own beds and water)
                }
                if (past > 0D) {
                    continue;                                   // the band: its caves only
                }
                if (dome || vokzal) {
                    int shell = dome ? floorY + (int) Math.floor(Math.sqrt((double) (r2 - d2))) : floorY;
                    clear(w, x, z, floorY + 1, vokzal ? Math.max(shell, floorY + VOKZAL_CLEAR) : shell, repair, green);
                }
            }
        }
    }

    /** The column's air and liquid from FOUNDATION under `top` up to it: the biome's top at it, its filler under, stone deeper. */
    private static void fill(World w, StructureBoundingBox win, int x, int z, int top) {
        BiomeGenBase bio = w.getBiomeGenForCoords(x, z);
        Block topBlock = bio != null && bio.topBlock != null ? bio.topBlock : Blocks.grass;
        int topMeta = bio != null && bio.topBlock != null ? bio.field_150604_aj & 15 : 0;
        Block filler = bio != null && bio.fillerBlock != null ? bio.fillerBlock : Blocks.dirt;
        if (top < EmbassyTerrainSC.SEA - 1) {                   // under the sea's surface vanilla's top is the filler too
            topBlock = filler;
            topMeta = 0;
        }
        for (int y = Math.max(1, top - SettlementSC.FOUNDATION); y <= Math.min(255, top); y++) {
            if (!hollow(w.getBlock(x, y, z))) {
                continue;
            }
            if (y == top) {
                put(w, win, x, y, z, topBlock, topMeta);
            } else if (y >= top - FILLER) {
                put(w, win, x, y, z, filler, 0);
            } else {
                put(w, win, x, y, z, Blocks.stone, 0);
            }
        }
    }

    /** What a fill takes: air, a liquid, or what a block replaces (tall grass, a snow layer, a vine); Thaumcraft's taint. */
    private static boolean hollow(Block b) {
        Material m = b.getMaterial();
        return m == Material.air || m.isLiquid() || m.isReplaceable() || taint(b);
    }

    /** Thaumcraft's taint blocks (its crust, soil and flesh; its fibres), looked up by name once - null without Thaumcraft. */
    private static Block taintBlock, taintFibres;
    private static boolean taintLooked;
    /** Thaumcraft's Tainted Land biome id (-1 none), looked for once (-2 not yet); its Utils.setBiomeAt (null: not there). */
    private static int taintBiome = -2;
    private static Method setBiome;
    private static boolean setBiomeLooked;

    /** `b` is Thaumcraft:blockTaint or Thaumcraft:blockTaintFibres. */
    static boolean taint(Block b) {
        if (!taintLooked) {
            taintLooked = true;
            taintBlock = GameRegistry.findBlock("Thaumcraft", "blockTaint");
            taintFibres = GameRegistry.findBlock("Thaumcraft", "blockTaintFibres");
        }
        return b != null && (b == taintBlock || b == taintFibres);
    }

    /** Column (x, z)'s biome is Thaumcraft's Tainted Land: the generator's own back (TC's setBiomeAt tells the clients). */
    private static void untaint(World w, int x, int z) {
        if (taintBiome == -2) {
            taintBiome = -1;
            for (BiomeGenBase g : BiomeGenBase.getBiomeGenArray()) {
                if (g != null && "thaumcraft.common.lib.world.biomes.BiomeGenTaint".equals(g.getClass().getName())) {
                    taintBiome = g.biomeID;
                    break;
                }
            }
        }
        if (taintBiome < 0) {
            return;
        }
        Chunk c = w.getChunkFromBlockCoords(x, z);
        byte[] a = c.getBiomeArray();
        int i = (z & 15) << 4 | (x & 15);
        if ((a[i] & 255) != taintBiome) {
            return;
        }
        BiomeGenBase own = w.getWorldChunkManager().getBiomeGenAt(x, z);
        if (own == null || own.biomeID == taintBiome) {
            return;
        }
        if (!setBiomeLooked) {
            setBiomeLooked = true;
            try {
                setBiome = Class.forName("thaumcraft.common.lib.utils.Utils").getMethod("setBiomeAt", World.class, int.class, int.class, BiomeGenBase.class);
            } catch (Throwable t) {
                setBiome = null;
            }
        }
        boolean told = false;
        if (setBiome != null) {
            try {
                setBiome.invoke(null, w, Integer.valueOf(x), Integer.valueOf(z), own);
                told = true;
            } catch (Throwable t) {
                setBiome = null;                                // another Thaumcraft: the array only from now on
            }
        }
        if (!told) {
            a[i] = (byte) (own.biomeID & 255);
            c.setBiomeArray(a);
        }
        c.setChunkModified();
    }

    /**
     * Over the floor of column (x, z) from y0 up to its chunk's top filled section: up to y1 (the shell) the first build takes every
     * block - nothing of the city is there yet -, the «Достройка» growth only (foreign); over y1 both take growth only (a treetop over
     * the shell at the dome's edge). Flag 2: no neighbour is told (every neighbour within 8 of a window is in the window's chunks).
     */
    private static void clear(World w, int x, int z, int y0, int y1, boolean repair, boolean green) {
        int cap = Math.min(255, w.getChunkFromBlockCoords(x, z).getTopFilledSegment() + 15);
        for (int y = y0; y <= cap; y++) {
            Block b = w.getBlock(x, y, z);
            if (b.getMaterial() == Material.air || b.isAir(w, x, y, z)) {
                continue;
            }
            if (!repair && y <= y1 || foreign(w, b, x, y, z, green)) {
                w.setBlock(x, y, z, Blocks.air, 0, 2);
            }
        }
    }

    /**
     * Growth over an embassy's floor - never a building's block (no log in a building, ПМ-7; the greenhouse's green is its own);
     * Thaumcraft's taint (fibres, crust) anywhere - a building's block it took is put back by its piece.
     */
    static boolean foreign(World w, Block b, int x, int y, int z, boolean green) {
        if (b.isWood(w, x, y, z) || b == Blocks.log || b == Blocks.log2 || b == Blocks.brown_mushroom_block || b == Blocks.red_mushroom_block
                || taint(b)) {
            return true;
        }
        Material m = b.getMaterial();
        if (m == Material.ice || m == Material.packedIce || m == Material.snow || m == Material.craftedSnow || m == Material.cactus) {
            return true;
        }
        return !green && (b.isLeaves(w, x, y, z) || m == Material.leaves || m == Material.plants || m == Material.vine || m == Material.gourd);
    }

    /** Column (x, z) is in a greenhouse lot. */
    private boolean green(int x, int z) {
        for (int i = 0; i + 3 < greens.length; i += 4) {
            if (x >= greens[i] && z >= greens[i + 1] && x <= greens[i + 2] && z <= greens[i + 3]) {
                return true;
            }
        }
        return false;
    }
}
