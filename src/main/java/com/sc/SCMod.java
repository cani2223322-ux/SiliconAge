package com.sc;

import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.init.ModRecipesComponents;
import com.sc.init.ModRecipesCrafting;
import com.sc.init.ModRecipesInfrastructure;
import com.sc.init.ModRecipesMachineBlocks;
import com.sc.init.ModRecipesMachines;
import com.sc.init.ModRecipesOreProcessing;
import com.sc.init.ModRecipesUpgradeStation;
import com.sc.proxy.CommonProxy;
import com.sc.util.ConfigSC;
import com.sc.worldgen.OreGenSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid = Reference.MODID, name = Reference.NAME, version = Reference.VERSION,
        dependencies = "after:" + Reference.IC2_MODID + ";after:GalacticraftCore")
public class SCMod {

    @Mod.Instance(Reference.MODID)
    public static SCMod instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY, serverSide = Reference.COMMON_PROXY)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigSC.load(event.getSuggestedConfigurationFile());
        ModFluids.init();
        ModBlocks.init();
        ModItems.init();
        // CommonEventHandler only subscribes to cpw.mods.fml.common.gameevent types (TickEvent,
        // PlayerEvent), and those are posted ONLY to FML's own bus - FMLCommonHandler's
        // onPre/PostWorldTick, onPlayerPre/PostTick and firePlayerCraftingEvent all go through
        // bus().post(...), and that class never touches MinecraftForge.EVENT_BUS at all.
        // Registering on the Forge bus instead silently did nothing: the fallback EnergyNet never
        // ticked (so no power moved without IC2), armour heat never accumulated, and the manual
        // was never handed out on first craft/pickup.
        FMLCommonHandler.instance().bus().register(new CommonEventHandler());
        FMLCommonHandler.instance().bus().register(new com.sc.energy.Ic2LoadQueueSC());   // energy tiles join IC2's net a tick late
        // World events (explosions) live on the Forge bus, not FML's.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ShieldEventHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.sc.bridge.BridgeSoftLandSC());   // §7б soft landing out of an air end
        // sneak + left-click with the wrench on a storage's face: an extra output (Output Splitter)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.sc.item.ItemWrenchSC.StorageFaceClick());
        // ...and so does WorldEvent.Unload, which frees the fallback energy net's cache of that world.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.energy.EnergyNetSC.instance());
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandlerSC());
        com.sc.handler.ArmorNetSC.init();
        com.sc.handler.FieldNetSC.init();
        com.sc.handler.QuarryNetSC.init();
        com.sc.handler.NetViewNetSC.init();             // the wrench's network overview, server -> client
        com.sc.bridge.BridgeNetSC.init();
        com.sc.handler.ConfigSyncSC.init();             // the server's config to joining clients
        com.sc.radiation.RadiationNetSC.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.sc.radiation.RadiationEventsSC());
        // the Singular blade's and drill's events (docs/plan-singular-tools.md): Forge's (hits, drops, block breaks) and FML's (ticks)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.item.BladeEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.item.BladeEventsSC.INSTANCE);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.item.DrillEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.item.DrillEventsSC.INSTANCE);
        // ПЭ-1 (docs/plan-pe1-tech.md): login / tracking sync, package A's per-tick work
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.emp.EmpEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.emp.EmpEventsSC.INSTANCE);
        // Подложка (Si-14), docs/plan-substrate-tech.md: the server's ids / rules to clients, the entry rules (package E),
        // the delayed teleports, the mobs' events and the spawn director (package M)
        com.sc.dim.SubstrateNetSC.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.dim.SubstrateEntrySC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.dim.SubstrateEntrySC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.dim.TeleportQueueSC.INSTANCE);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.mob.MobEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.mob.MobEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.mob.SpawnDirectorSC.INSTANCE);
        // Подложка Ф2, docs/plan-substrate-tech-f2.md: the settlements' protection (package C - Forge's bus, priority HIGH,
        // before ShieldEventHandler), the economy's events (package T), the docks / capsule / death mark (package B)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.settle.SettlementProtectSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.settle.SettlementProtectSC.INSTANCE);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.settle.trade.EconomyEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.settle.trade.EconomyEventsSC.INSTANCE);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.settle.travel.TravelEventsSC.INSTANCE);
        FMLCommonHandler.instance().bus().register(com.sc.settle.travel.TravelEventsSC.INSTANCE);
        proxy.preInit();
        // Developer check only (see SelfTestSC): run early, since a dedicated test server
        // without an accepted EULA never gets past preInit.
        if (Boolean.getBoolean("sc.selftest")) {
            registerRecipes();
            com.sc.item.ItemFluidBucketSC.registerContainers(ModItems.fluidBucket);
            com.sc.debug.SelfTestSC.run();
        }
        if (System.getProperty("sc.dumpRecipes") != null) {
            registerRecipes();
            com.sc.debug.RecipeDumpSC.dump(System.getProperty("sc.dumpRecipes"));
        }
    }

    private boolean recipesRegistered;

    private void registerRecipes() {
        if (recipesRegistered) {
            return;
        }
        recipesRegistered = true;
        ModRecipesOreProcessing.init();
        ModRecipesMachines.init();
        ModRecipesInfrastructure.init();
        ModRecipesUpgradeStation.init();
        ModRecipesComponents.init();
        ModRecipesCrafting.init();
        ModRecipesMachineBlocks.init();
        com.sc.dim.SubstrateRecipesSC.init();            // the Substrate's blocks (package W)
        com.sc.mob.MobRecipesSC.init();                  // the Substrate mobs' combat loot (package M)
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Weight 0, like most ore generators - runs alongside vanilla ore gen each chunk.
        GameRegistry.registerWorldGenerator(new OreGenSC(), 0);
        // Подложка (Si-14): biomes, provider, dimension - each with its check, IMC (docs/plan-substrate-tech.md §1);
        // its mobs in this mod's own entity pool
        com.sc.dim.SubstrateSC.init();
        com.sc.mob.MobEntitiesSC.init();
        com.sc.settle.SettlementSC.init();               // Ф2: the settlements' structure names (MapGenStructureIO), the IMC
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(instance, new com.sc.energy.ChunkLoaderSC());
        registerRecipes();
        com.sc.item.ItemFluidBucketSC.registerContainers(ModItems.fluidBucket);
        // WAILA finds its plugin through IMC and loads the class itself - nothing of WAILA's is
        // touched when it isn't installed.
        cpw.mods.fml.common.event.FMLInterModComms.sendMessage("Waila", "register", "com.sc.compat.WailaSC.callbackRegister");
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ModRecipesCrafting.metalBlocksLate();            // after every mod's ore dictionary and recipes
        proxy.postInit();
    }

    /** Every mod has registered everything: the Substrate's ids checked against Galacticraft and the biome slots again. */
    @Mod.EventHandler
    public void loadComplete(cpw.mods.fml.common.event.FMLLoadCompleteEvent event) {
        com.sc.dim.SubstrateSC.loadComplete();
    }

    /** The server has stopped (single player: back to the title): registries of that server's tiles are emptied. */
    @Mod.EventHandler
    public void serverStopped(cpw.mods.fml.common.event.FMLServerStoppedEvent event) {
        com.sc.tileentity.TileEntityWirelessSC.forgetWorld(null);
        com.sc.radiation.RadiationSC.clearSources();
        com.sc.energy.Ic2LoadQueueSC.clear();
        com.sc.tileentity.TileEntityGeneratorSC.forgetPorts(null);
        com.sc.bridge.BridgeConsentSC.reset();
        com.sc.tileentity.TileEntityBridgeControllerSC.TEST_PLAYERS.clear();
        com.sc.item.BladeSingularSC.clearAll();          // the Singular tools' queued keys, collapses, tethers, per-player state
        com.sc.item.SingularPowersSC.clearAll();         // the Singular suit's queued keys, pinned / held mobs, fields
        com.sc.emp.EmpSC.clearAll();                     // ПЭ-1: the cached per-player states
        com.sc.emp.EmpGunLogicSC.forget(null);           // ПЭ-1: streams / charge-ups (package A)
        com.sc.item.DrillEventsSC.INSTANCE.clearAll();   // the drill's queued forgets and tracked falling blocks (no World kept between sessions)
        com.sc.tileentity.TileEntityMachineSC.clearSmeltCache();   // furnace answers: the next world may bring changed furnace recipes
        com.sc.dim.TeleportQueueSC.clear();              // Подложка: the queued moves, the arrival grants, the director's counts
        com.sc.dim.SubstrateEntrySC.clearAll();
        com.sc.mob.SpawnDirectorSC.clearAll();
        com.sc.settle.SettlementProtectSC.clearAll();    // Ф2: the escalation windows, the guards' grudges, the trade screens, the capsules
        com.sc.settle.GuardAlertSC.clearAll();
        com.sc.settle.trade.TradeServerSC.clearAll();
        com.sc.settle.trade.EconomyEventsSC.clearAll();
        com.sc.settle.travel.TravelEventsSC.clearAll();
    }

    /** /scenergy: what the energy nets see (debugging a network that doesn't move energy). */
    @Mod.EventHandler
    public void serverStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        com.sc.tileentity.TileEntityMachineSC.clearSmeltCache();   // МШ-8: furnace answers are taken afresh for this world (MineTweaker scripts and the like)
        event.registerServerCommand(new com.sc.debug.CommandEnergySC());
        if (event.getServer().isDedicatedServer()) {
            com.sc.util.UpdateCheckSC.start();               // operators hear about a newer version as they log in
        }
        event.registerServerCommand(new com.sc.bridge.CommandBridgeSC());          // [Принять] / [Отклонить] of a bridge consent
        if (Boolean.getBoolean("sc.worldtest")) {
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestWirelessSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestTokamakSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestSingularSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestSingStationSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestSingConvertSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestBridgeSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestBridge2SC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestBridge3SC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestBridgeAirSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestConverterSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestEmpSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestSubstrateSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestSettlementSC());
        }
    }
}
