package com.sc.embassy;

import java.util.Random;

import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * PACKAGE G (docs/plan-substrate-tech-f6.md §5.4..§5.6, rules ПЕ-2..ПЕ-6, ПК-27): the embassies' generation in the Overworld.
 * Registered by SCMod: INSTANCE on Forge's EVENT_BUS (ChunkProviderEvent.ReplaceBiomeBlocks, WorldEvent.Load / Unload) and as the
 * IWorldGenerator of weight WEIGHT (1000: after every other mod's generator), DECOR on TERRAIN_GEN_BUS (PopulateChunkEvent.Populate and
 * DecorateBiomeEvent.Decorate - Forge 1614 posts both there; ReplaceBiomeBlocks goes to EVENT_BUS).
 * <ul>
 * <li><b>ReplaceBiomeBlocks</b> (the Overworld, ChunkProviderGenerate's - ChunkProviderHell posts the same event: dim 0 only;
 * EmbassySiteSC.worldFits): first EmbassySC.mapGen(world).func_151539_a(provider, world, cx, cz, null) - the starts within 10 chunks
 * made before any chunk of their footprint; then, if a non-empty start's footprint holds this chunk, its columns levelled IN THE
 * ARRAYS (index (x * 16 + z) * height + y, height = blockArray.length / 256 - Chunk's own layout, checked in its bytecode): on the
 * level ground stone up to the floor and air over it (water too), over the blend band EmbassyLayoutSC.targetY to the column's own
 * height (its top stone in the array); biomeArray untouched; vanilla's surface pass then lays grass / sand on the new top
 * (EmbassyTerrainSC). Nothing while a new world looks for its spawn (findingSpawnPoint, not initialised yet): the guaranteed
 * embassy is chosen by the spawn, which isn't set then (and no embassy stands that near it);</li>
 * <li><b>populate</b> (generate, the Overworld): mapGen.generateStructuresInChunk(world, rand, cx, cz) - only the window;</li>
 * <li><b>the bans</b> (DECOR): Populate LAKE, LAVA, DUNGEON, ANIMALS and Decorate TREE, BIG_SHROOM, CACTUS, PUMPKIN, REED, LAKE,
 * LILYPAD - DENY in a window that touches an embassy's area + BAN_MARGIN (covers); Decorate's chunkX / chunkZ are BLOCK coordinates
 * (BiomeDecorator's chunk_X), Populate's are chunks;</li>
 * <li><b>the load</b>: EmbassySiteSC.decideGuaranteed (the Overworld, the server, embassiesGenerate) - before the spawn area's chunks
 * are made; the unload: the map generator forgotten (EmbassySC.forget), the band's remembered tops too.</li>
 * </ul>
 * The levelling and populate serve the starts already made also when no new one may come (embassy.embassies off, the world's
 * structures switched off later): a made start's chunks are always levelled and built. Fixed (the coordinator's): the class,
 * INSTANCE / DECOR, WEIGHT, the handlers' signatures. The bodies G's.
 */
public final class EmbassyWorldGenSC implements IWorldGenerator {

    public static final EmbassyWorldGenSC INSTANCE = new EmbassyWorldGenSC();
    /** The TERRAIN_GEN_BUS half (populate and decorate bans). */
    public static final Decor DECOR = new Decor();
    /** The IWorldGenerator's weight: generators run from the lowest - ours last. */
    public static final int WEIGHT = 1000;
    /** ПЕ-6: the bans reach this far past an embassy's area. */
    public static final int BAN_MARGIN = 8;

    private EmbassyWorldGenSC() {
    }

    /** ПЕ-4, ПЕ-6: the starts first, then the levelling of this chunk's arrays (see the class comment). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onReplaceBiomeBlocks(ChunkProviderEvent.ReplaceBiomeBlocks e) {
        World w = e.world;
        if (w == null || w.isRemote || w.provider == null || w.provider.dimensionId != EmbassySiteSC.DIM || e.blockArray == null
                || w.findingSpawnPoint || w.getWorldInfo() == null || !w.getWorldInfo().isInitialized()) {
            return;
        }
        MapGenEmbassySC m = EmbassySC.mapGen(w);
        if (m == null) {
            return;
        }
        if (EmbassySiteSC.worldFits(w)) {
            m.func_151539_a(e.chunkProvider, w, e.chunkX, e.chunkZ, null);
        }
        int x0 = e.chunkX << 4, z0 = e.chunkZ << 4;
        for (StructureEmbassyStartSC st : m.builtIn(w, x0, z0, x0 + 15, z0 + 15)) {
            ComponentEmbGroundSC g = ComponentEmbGroundSC.of(st);
            if (g == null) {
                continue;
            }
            byte[] band = new byte[256];
            if (g.levelChunk(e.blockArray, e.metaArray, e.chunkX, e.chunkZ, band) > 0) {
                EmbassyTerrainSC.rememberBand(e.chunkX, e.chunkZ, band);
            }
        }
    }

    /** ПЕ-7: the embassies' pieces inside this populate window. */
    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
        if (world == null || world.isRemote || world.provider == null || world.provider.dimensionId != EmbassySiteSC.DIM) {
            return;
        }
        MapGenEmbassySC m = EmbassySC.mapGen(world);
        if (m != null) {
            m.generateStructuresInChunk(world, random, chunkX, chunkZ);
        }
    }

    /** ПЕ-2: the guaranteed embassy decided as the Overworld loads. */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        if (e.world instanceof WorldServer && !e.world.isRemote && e.world.provider != null && e.world.provider.dimensionId == EmbassySiteSC.DIM
                && EmbassySiteSC.worldFits(e.world) && SettlementSC.embassiesGenerate()) {
            EmbassySiteSC.decideGuaranteed((WorldServer) e.world);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        if (e.world != null && !e.world.isRemote && e.world.provider != null && e.world.provider.dimensionId == EmbassySiteSC.DIM) {
            EmbassySC.forget(e.world);
            EmbassyTerrainSC.clearBand();
        }
    }

    /** ПЕ-6: the window of a chunk (blocks x0..x0 + 15 shifted by 8, as populate's) touches an embassy's area + BAN_MARGIN (a built start). */
    public static boolean banned(World w, int blockX, int blockZ) {
        if (w == null || w.isRemote || w.provider == null || w.provider.dimensionId != EmbassySiteSC.DIM) {
            return false;
        }
        MapGenEmbassySC m = EmbassySC.mapGen(w);
        // the window [x0 + 8 .. x0 + 23] and BAN_MARGIN round it: from its middle (x0 + 16) half its side and the margin
        return m != null && m.covers(w, blockX + 16, blockZ + 16, 8 + BAN_MARGIN);
    }

    /** Server stopped. */
    public static void clearAll() {
        EmbassyTerrainSC.clearBand();
    }

    /** TERRAIN_GEN_BUS: the populate and decorate bans (ПЕ-6). */
    public static final class Decor {
        Decor() {
        }

        @SubscribeEvent
        public void onPopulate(PopulateChunkEvent.Populate e) {
            PopulateChunkEvent.Populate.EventType t = e.type;
            if ((t == PopulateChunkEvent.Populate.EventType.LAKE || t == PopulateChunkEvent.Populate.EventType.LAVA
                    || t == PopulateChunkEvent.Populate.EventType.DUNGEON || t == PopulateChunkEvent.Populate.EventType.ANIMALS)
                    && banned(e.world, e.chunkX << 4, e.chunkZ << 4)) {
                e.setResult(Event.Result.DENY);
            }
        }

        @SubscribeEvent
        public void onDecorate(DecorateBiomeEvent.Decorate e) {
            DecorateBiomeEvent.Decorate.EventType t = e.type;
            if ((t == DecorateBiomeEvent.Decorate.EventType.TREE || t == DecorateBiomeEvent.Decorate.EventType.BIG_SHROOM
                    || t == DecorateBiomeEvent.Decorate.EventType.CACTUS || t == DecorateBiomeEvent.Decorate.EventType.PUMPKIN
                    || t == DecorateBiomeEvent.Decorate.EventType.REED || t == DecorateBiomeEvent.Decorate.EventType.LAKE
                    || t == DecorateBiomeEvent.Decorate.EventType.LILYPAD)
                    && banned(e.world, e.chunkX, e.chunkZ)) {           // Decorate's chunkX / chunkZ: blocks (chunk_X)
                e.setResult(Event.Result.DENY);
            }
        }
    }
}
