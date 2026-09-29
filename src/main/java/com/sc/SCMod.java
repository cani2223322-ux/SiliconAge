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
        dependencies = "after:" + Reference.IC2_MODID)
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
        // ...and so does WorldEvent.Unload, which frees the fallback energy net's cache of that world.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.sc.energy.EnergyNetSC.instance());
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandlerSC());
        com.sc.handler.ArmorNetSC.init();
        com.sc.handler.FieldNetSC.init();
        com.sc.handler.QuarryNetSC.init();
        com.sc.radiation.RadiationNetSC.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.sc.radiation.RadiationEventsSC());
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
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Weight 0, like most ore generators - runs alongside vanilla ore gen each chunk.
        GameRegistry.registerWorldGenerator(new OreGenSC(), 0);
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
        proxy.postInit();
    }

    /** The server has stopped (single player: back to the title): registries of that server's tiles are emptied. */
    @Mod.EventHandler
    public void serverStopped(cpw.mods.fml.common.event.FMLServerStoppedEvent event) {
        com.sc.tileentity.TileEntityWirelessSC.forgetWorld(null);
        com.sc.radiation.RadiationSC.clearSources();
    }

    /** /scenergy: what the energy nets see (debugging a network that doesn't move energy). */
    @Mod.EventHandler
    public void serverStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new com.sc.debug.CommandEnergySC());
        if (Boolean.getBoolean("sc.worldtest")) {
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestWirelessSC());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.sc.debug.WorldTestTokamakSC());
        }
    }
}
