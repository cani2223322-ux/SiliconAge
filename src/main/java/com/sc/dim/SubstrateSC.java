package com.sc.dim;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.DimensionManager;

/**
 * Подложка (Si-14) - registering the world and the questions the rest of the mod asks about it
 * (docs/plan-substrate-tech.md §1-§3). Shared core: only the coordinator edits this file.
 * <p>
 * init (FMLInitializationEvent, both sides - a client needs the provider and the biomes for its WorldClient), in
 * this order, each step with its check (rules ПК-1..ПК-6):
 * <ol>
 * <li>dimension.enabled off - nothing at all;</li>
 * <li>the dimension id: not 0 / 1 / -1, not positive (DimensionManager.getNextFreeDimId hands those to Galacticraft's
 * stations and AE2's spatial cells), not registered by anyone (registerDimension would throw: a crash);</li>
 * <li>the provider type: registerProviderType false (taken) - off;</li>
 * <li>the biomes (Ф4: five - the plateaus, the canyons, the ridges, the oxide fields, the Rift, each past the ids picked
 * before it): a configured id that's taken moves to the next free one (240..254, then down to 40) and is
 * written back to the .cfg (1.7.10's BiomeGenBase constructor silently overwrites another mod's biome, and the id
 * is saved in every chunk); none free - the provider is unregistered again, off;</li>
 * <li>registerDimension; the BiomeDictionary types (explicit - other mods' hives, rubber trees and nodes pick
 * their places by them); the IMC (Thaumcraft, AE2).</li>
 * </ol>
 * Any refusal is one clear line in the log and the dimension is simply absent - never a crash, never someone
 * else's world switched off. loadComplete (FMLLoadCompleteEvent): Galacticraft's registry and the biome slots are
 * checked again (a planet or a biome registered later with the same id - a loud warning).
 * <p>
 * Everywhere else the world is recognised by its provider class (is(World)), never by comparing ids: a client's
 * own config may say another id than the server it plays on.
 */
public final class SubstrateSC {

    /**
     * Free biome ids are looked for here first (vanilla 0..39 and mutations 129..167; the pack's other mods: 40..213).
     * Never 255: in a chunk's biome array it means «not worked out yet» (Chunk.getBiomeGenForWorldCoords asks the
     * WorldChunkManager again every time and never keeps it).
     */
    public static final int BIOME_SEARCH_FROM = 240, BIOME_SEARCH_TO = 254, BIOME_SEARCH_DOWN_TO = 40;

    private static boolean active;
    private static int dim, provider;
    private static String off = "not initialised";
    private static BiomeSubstrateSC plateaus, canyons;
    /** Ф4: the Quartzite Ridges, the Oxide Fields, the Rift; all five by BiomeSubstrateSC kind. */
    private static BiomeSubstrateSC ridges, oxide, rift;
    private static final BiomeSubstrateSC[] BY_KIND = new BiomeSubstrateSC[BiomeSubstrateSC.KINDS];
    /** The config keys of the biome ids, by kind (the config's writeBiomeId, the log). */
    private static final String[] BIOME_KEYS = {"biomeIdPlateaus", "biomeIdCanyons", "biomeIdRidges", "biomeIdOxide", "biomeIdRift"};

    private SubstrateSC() {
    }

    // ------------------------------------------------------------------ registration

    public static void init() {
        if (!SubstrateConfigSC.enabled()) {
            off = "dimension.enabled = false";
            FMLLog.info("[Silicon Age] Si-14 Substrate: off in the config (dimension.enabled = false)");
            return;
        }
        int d = SubstrateConfigSC.dimensionId(), p = SubstrateConfigSC.providerId();
        String why = dimensionRefusal(d);
        if (why != null) {
            refuse(why);
            return;
        }
        if (!DimensionManager.registerProviderType(p, WorldProviderSubstrateSC.class, SubstrateConfigSC.keepLoaded())) {
            refuse("provider type " + p + " is taken by another mod - set dimension.providerId to a free negative number");
            return;
        }
        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();
        int[] want = {SubstrateConfigSC.biomeIdPlateaus(), SubstrateConfigSC.biomeIdCanyons(), SubstrateConfigSC.biomeIdRidges(),
                SubstrateConfigSC.biomeIdOxide(), SubstrateConfigSC.biomeIdRift()};
        int[] ids = new int[want.length];
        for (int k = 0; k < want.length; k++) {
            ids[k] = pickBiomeId(biomes, want[k], java.util.Arrays.copyOf(ids, k));    // Ф4: never an id picked for a biome before it
            if (ids[k] < 0) {
                DimensionManager.unregisterProviderType(p);
                refuse("no free biome id for the " + BiomeSubstrateSC.NAMES[k] + " (" + BIOME_SEARCH_DOWN_TO + ".." + BIOME_SEARCH_TO + " are all taken)");
                return;
            }
        }
        for (int k = 0; k < want.length; k++) {
            moved(BIOME_KEYS[k], want[k], ids[k]);
        }
        int bp = ids[BiomeSubstrateSC.PLATEAUS], bc = ids[BiomeSubstrateSC.CANYONS];
        try {
            DimensionManager.registerDimension(d, p);
        } catch (IllegalArgumentException e) {
            DimensionManager.unregisterProviderType(p);
            refuse("dimension " + d + " couldn't be registered: " + e.getMessage());
            return;
        }
        for (int k = 0; k < ids.length; k++) {
            BY_KIND[k] = new BiomeSubstrateSC(ids[k], k);
            BiomeDictionary.registerBiomeType(BY_KIND[k], BiomeSubstrateSC.dictionaryTypes(k));
        }
        plateaus = BY_KIND[BiomeSubstrateSC.PLATEAUS];
        canyons = BY_KIND[BiomeSubstrateSC.CANYONS];
        ridges = BY_KIND[BiomeSubstrateSC.RIDGES];
        oxide = BY_KIND[BiomeSubstrateSC.OXIDE];
        rift = BY_KIND[BiomeSubstrateSC.RIFT];
        dim = d;
        provider = p;
        active = true;
        off = null;
        FMLLog.info("[Silicon Age] Si-14 Substrate: dimension %d, provider %d, biomes %d (plateaus), %d (canyons), %d (ridges), %d (oxide fields)"
                + " and %d (the Rift)", d, p, bp, bc, ids[BiomeSubstrateSC.RIDGES], ids[BiomeSubstrateSC.OXIDE], ids[BiomeSubstrateSC.RIFT]);
        SubstrateCompatSC.sendImc(d);
    }

    /** Why `d` can't be the Substrate's id, or null if it can (pure but for DimensionManager; the self-test asks it). */
    public static String dimensionRefusal(int d) {
        if (d == 0 || d == 1 || d == -1) {
            return "dimension.dimensionId = " + d + " is vanilla's (0 / 1 / -1)";
        }
        if (d > 0) {
            return "dimension.dimensionId = " + d + " is positive: Galacticraft stations and AE2 spatial cells take those (getNextFreeDimId) - use a negative id";
        }
        if (DimensionManager.isDimensionRegistered(d)) {
            int pt = DimensionManager.getProviderType(d);
            return "dimension " + d + " is already registered (provider type " + pt + ") by another mod - set dimension.dimensionId to a free negative number";
        }
        return null;
    }

    /**
     * The biome id to use: `want` when that slot is empty (and isn't `notThis`), else the first empty slot from
     * BIOME_SEARCH_FROM up to BIOME_SEARCH_TO, then from BIOME_SEARCH_FROM - 1 down to BIOME_SEARCH_DOWN_TO. -1 none.
     * Pure (the self-test gives it its own arrays).
     */
    public static int pickBiomeId(BiomeGenBase[] slots, int want, int notThis) {
        return pickBiomeId(slots, want, new int[]{notThis});
    }

    /** Ф4: as pickBiomeId, never one of `notThese` (the ids picked for the biomes before it). Pure (the self-test asks it). */
    public static int pickBiomeId(BiomeGenBase[] slots, int want, int[] notThese) {
        if (free(slots, want, notThese)) {
            return want;
        }
        for (int i = BIOME_SEARCH_FROM; i <= BIOME_SEARCH_TO; i++) {
            if (free(slots, i, notThese)) {
                return i;
            }
        }
        for (int i = BIOME_SEARCH_FROM - 1; i >= BIOME_SEARCH_DOWN_TO; i--) {
            if (free(slots, i, notThese)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean free(BiomeGenBase[] slots, int id, int[] notThese) {
        if (id < 0 || id > BIOME_SEARCH_TO || id >= slots.length || slots[id] != null) {
            return false;
        }
        for (int n : notThese) {
            if (n == id) {
                return false;
            }
        }
        return true;
    }

    private static void moved(String key, int was, int now) {
        if (was != now) {
            boolean saved = SubstrateConfigSC.writeBiomeId(key, now);
            FMLLog.warning("[Silicon Age] Si-14 Substrate: biome id %d (dimension.%s) is taken by another mod - using %d%s", was, key, now,
                    saved ? " (written to the config)" : " (the config couldn't be saved: set it by hand)");
        }
    }

    private static void refuse(String why) {
        off = why;
        active = false;
        FMLLog.severe("[Silicon Age] Si-14 Substrate is OFF: %s. Nothing of another mod was changed; the mod's blocks and items stay.", why);
    }

    /**
     * FMLLoadCompleteEvent: what other mods registered after our init. Galacticraft: a planet / moon / station on our
     * id (GalaxyRegistry, by reflection - no compile dependency); the biome slots still hold our biomes; the provider
     * type is still ours. Warnings only: by now both worlds exist, switching one off would break someone's save.
     */
    public static void loadComplete() {
        if (!active) {
            return;
        }
        if (Loader.isModLoaded("GalacticraftCore")) {
            try {
                Object body = Class.forName("micdoodle8.mods.galacticraft.api.galaxies.GalaxyRegistry")
                        .getMethod("getCelestialBodyFromDimensionID", int.class).invoke(null, dim);
                if (body != null) {
                    FMLLog.severe("[Silicon Age] Si-14 Substrate: Galacticraft has a celestial body on dimension %d (%s) - give one of them another id!", dim, body);
                }
            } catch (Throwable t) {
                FMLLog.fine("[Silicon Age] Si-14 Substrate: Galacticraft's registry not checked (%s)", t);
            }
        }
        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();
        for (int k = 0; k < BY_KIND.length; k++) {
            BiomeSubstrateSC b = BY_KIND[k];
            if (b != null && biomes[b.biomeID] != b) {
                FMLLog.severe("[Silicon Age] Si-14 Substrate: another mod overwrote biome id %d (the %s) after us - set dimension.%s to a free id"
                        + " before anyone visits the world", b.biomeID, BiomeSubstrateSC.NAMES[k], BIOME_KEYS[k]);
            }
        }
        if (DimensionManager.getProviderType(dim) != provider) {
            FMLLog.severe("[Silicon Age] Si-14 Substrate: dimension %d now has provider type %d, not ours (%d)", dim, DimensionManager.getProviderType(dim), provider);
        }
        SubstrateCompatSC.loadComplete(dim);
    }

    // ------------------------------------------------------------------ questions

    /** The world registered (both sides, after init). */
    public static boolean active() {
        return active;
    }

    /** Why it isn't (for the self-test and /scenergy-style reports), null when active. */
    public static String offReason() {
        return off;
    }

    /** The dimension id on this side (meaningful only while active()). */
    public static int dimId() {
        return dim;
    }

    public static int providerId() {
        return provider;
    }

    /** The world is the Substrate - by its provider, never by the id (both sides). */
    public static boolean is(World w) {
        return w != null && w.provider instanceof WorldProviderSubstrateSC;
    }

    public static boolean is(Entity e) {
        return e != null && is(e.worldObj);
    }

    /** Server: dimension id `d` is the Substrate here. */
    public static boolean isDim(int d) {
        return active && d == dim;
    }

    public static BiomeSubstrateSC plateaus() {
        return plateaus;
    }

    public static BiomeSubstrateSC canyons() {
        return canyons;
    }

    /** Ф4: the Quartzite Ridges (null while off). */
    public static BiomeSubstrateSC ridges() {
        return ridges;
    }

    /** Ф4: the Oxide Fields (null while off). */
    public static BiomeSubstrateSC oxideFields() {
        return oxide;
    }

    /** Ф4: the Rift (null while off). */
    public static BiomeSubstrateSC rift() {
        return rift;
    }

    /** The biome of BiomeSubstrateSC kind `kind` (an unknown kind: the plateaus'); null while off. */
    public static BiomeSubstrateSC biome(int kind) {
        return kind >= 0 && kind < BY_KIND.length && BY_KIND[kind] != null ? BY_KIND[kind] : plateaus;
    }

    /** ПВ-9: where a player who died in the Substrate wakes up - the configured dimension if it exists, else the Overworld. */
    public static int respawnDimension() {
        int r = SubstrateConfigSC.respawnDimension;
        return r != dim && (r == 0 || DimensionManager.isDimensionRegistered(r)) ? r : 0;
    }

    /**
     * ПВ-6: the Space Bridge's extra price multiplier for a trip with an end in `targetDim` (1 for any other world). The
     * bridge multiplies it into bridge.costMultiplier in TileEntityBridgeControllerSC.plan (package E).
     */
    public static float bridgeCostMultiplier(int targetDim) {
        return isDim(targetDim) ? SubstrateConfigSC.bridgeCostMultiplier : 1F;
    }

    /** A provider made by DimensionManager.createProviderFor(dim) is ours (the bridge's dimension list, no world loaded). */
    public static boolean isProvider(WorldProvider p) {
        return p instanceof WorldProviderSubstrateSC;
    }
}
