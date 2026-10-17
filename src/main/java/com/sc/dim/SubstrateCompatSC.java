package com.sc.dim;

import com.sc.settle.SettlementCompatSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.7): other mods and the Substrate - Thaumcraft's IMC dimensionBlacklist
 * "&lt;id&gt;:0" (no aura nodes, no TC worldgen), AE2's worldgen (IWorldGen.disableWorldGenForDimension for Certus and
 * meteorites, by reflection - no compile dependency). Every call is guarded by Loader.isModLoaded and catches its own
 * failures: a missing or changed mod is one line in the log.
 * <p>
 * AE2 (rv3): AEApi.instance().registries().worldgen() has disableWorldGenForProviderID(type, provider class) and
 * disableWorldGenForDimension(type, id) - both are called for every WorldGenType (CertusQuartz, ChargedCertusQuartz,
 * Meteorites). Its ores replace only minecraft:stone anyway; the meteorites are the real reason. The methods are
 * taken from AE2's public API interfaces, not from its implementation classes (those needn't be public).
 * <p>
 * Ф4 (package W, docs/plan-substrate-tech-f4.md §4.6, ПА-8): the vents (substrateVent - the fumarole, the Breakdown crack, the
 * ion geyser, the cracked RTG: unbreakable, free helium / EU) go to AE2's Spatial IO blacklist by IMC, one message a meta, as the
 * settlements' blocks (SettlementCompatSC: the key "blacklist-block-spatial" with the block's ItemStack) - else a spatial cell
 * would carry a crack or a fumarole home. So does the Rift pillar's core (singularGlass PILLAR_CORE: unbreakable, light 12 -
 * AE2 checks no hardness, its list is by Block, so the plain Singular Glass stays put too).
 */
public final class SubstrateCompatSC {

    private SubstrateCompatSC() {
    }

    /** SubstrateSC.init (FMLInitializationEvent - IMC must go out before postInit): `dim` = the registered id. */
    static void sendImc(int dim) {
        if (Loader.isModLoaded("Thaumcraft")) {
            FMLInterModComms.sendMessage("Thaumcraft", "dimensionBlacklist", dim + ":0");
            FMLLog.info("[Silicon Age] Si-14 Substrate: Thaumcraft asked to leave dimension %d alone (dimensionBlacklist %d:0)", dim, dim);
        }
        if (Loader.isModLoaded(SettlementCompatSC.AE2) && SubstrateWildBlocksSC.vent != null) {
            int sent = 0;
            for (int m = 0; m < SubstrateWildBlocksSC.VENT_NAMES.length; m++) {
                if (FMLInterModComms.sendMessage(SettlementCompatSC.AE2, SettlementCompatSC.AE2_SPATIAL_BLACKLIST, new ItemStack(SubstrateWildBlocksSC.vent, 1, m))) {
                    sent++;
                }
            }
            if (SubstrateWildBlocksSC.glass != null && FMLInterModComms.sendMessage(SettlementCompatSC.AE2, SettlementCompatSC.AE2_SPATIAL_BLACKLIST,
                    new ItemStack(SubstrateWildBlocksSC.glass, 1, SubstrateWildBlocksSC.PILLAR_CORE))) {
                sent++;
            }
            FMLLog.info("[Silicon Age] Si-14 Substrate: %d stacks of the vents and the pillar's core sent to AE2's spatial IO blacklist (%s)", sent,
                    SettlementCompatSC.AE2_SPATIAL_BLACKLIST);
        }
    }

    /** SubstrateSC.loadComplete (FMLLoadCompleteEvent): what needs the other mods' registries done (AE2's worldgen API). */
    static void loadComplete(int dim) {
        if (Loader.isModLoaded("appliedenergistics2")) {
            ae2(dim);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void ae2(int dim) {
        try {
            Object api = Class.forName("appeng.api.AEApi").getMethod("instance").invoke(null);
            Object registries = Class.forName("appeng.api.IAppEngApi").getMethod("registries").invoke(api);
            Class iface = Class.forName("appeng.api.features.IWorldGen");
            Object worldgen = Class.forName("appeng.api.features.IRegistryContainer").getMethod("worldgen").invoke(registries);
            Class types = Class.forName("appeng.api.features.IWorldGen$WorldGenType");
            int done = 0;
            for (Object type : types.getEnumConstants()) {
                iface.getMethod("disableWorldGenForProviderID", types, Class.class).invoke(worldgen, type, WorldProviderSubstrateSC.class);
                iface.getMethod("disableWorldGenForDimension", types, int.class).invoke(worldgen, type, dim);
                done++;
            }
            FMLLog.info("[Silicon Age] Si-14 Substrate: AE2's worldgen (%d kinds: certus quartz, meteorites) is off in dimension %d", done, dim);
        } catch (Throwable t) {
            FMLLog.warning("[Silicon Age] Si-14 Substrate: AE2's worldgen API not reached (%s) - its meteorites may fall here", t);
        }
    }
}
