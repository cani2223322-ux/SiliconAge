package com.sc.proxy;

import com.sc.block.BlockConduitSC;
import com.sc.client.ConduitRenderer;
import com.sc.client.FluidTextureHandler;

import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        super.preInit();
        MinecraftForge.EVENT_BUS.register(new FluidTextureHandler());
        com.sc.client.ArmorClientSC.register();
        com.sc.client.RainShieldClientSC.register();
        com.sc.client.RadiationClientSC.register();
        com.sc.client.BookKeySC.register();
        com.sc.client.BridgeHighlightSC.register();
        com.sc.client.BridgeHudSC.register();
        com.sc.client.BridgeVortexFxSC.register();
        com.sc.client.CarryTooltipSC.register();
        // the Singular tools: the black hole's zone frame, the blade's effects, Shift + wheel (form / mode)
        MinecraftForge.EVENT_BUS.register(com.sc.client.DrillHoleRendererSC.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.sc.client.BladeFxSC.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.sc.client.ToolWheelSC.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.sc.client.NetViewRendererSC.INSTANCE);   // the wrench's network overview (world + HUD)
        MinecraftForge.EVENT_BUS.register(new com.sc.util.SoundsSC.ClientFilter());   // the player's own sound settings (client only)
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(com.sc.client.ToolWheelSC.INSTANCE);   // ClientTickEvent
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(com.sc.client.UpdateClientSC.INSTANCE);   // «Есть обновление»
        if (com.sc.debug.PackCheckSC.wanted()) {        // developer check of a whole pack only (-Dsc.packcheck)
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(com.sc.debug.PackCheckSC.INSTANCE);
        }
        BlockConduitSC.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new ConduitRenderer(BlockConduitSC.renderId));
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityFieldGeneratorSC.class, new com.sc.client.FieldRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityTankSC.class, new com.sc.client.TankRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityQuarrySC.class, new com.sc.client.QuarryRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityArmorStationSC.class, new com.sc.client.ArmorStationRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntitySingularStationSC.class, new com.sc.client.SingularStationRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityGravStabiliserSC.class, new com.sc.client.GravStabiliserRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityBridgeVortexSC.class, new com.sc.client.BridgeVortexRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityBridgeControllerSC.class, new com.sc.client.BridgeHoloRendererSC());
    }

    @Override
    public void openBridge(int x, int y, int z) {
        Minecraft.getMinecraft().displayGuiScreen(new com.sc.client.GuiBridgeControllerSC(x, y, z));
    }

    @Override
    public void bridgeState(int x, int y, int z, net.minecraft.nbt.NBTTagCompound state) {
        net.minecraft.client.gui.GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s instanceof com.sc.client.GuiBridgeControllerSC && ((com.sc.client.GuiBridgeControllerSC) s).isFor(x, y, z)) {
            ((com.sc.client.GuiBridgeControllerSC) s).setState(state);
        }
    }

    @Override
    public void vortexTick(net.minecraft.tileentity.TileEntity vortex) {
        com.sc.client.BridgeVortexFxSC.tick((com.sc.tileentity.TileEntityBridgeVortexSC) vortex);
    }

    @Override
    public void bridgeCollapse(double x, double y, double z, int kind, int size, int axis, boolean ringless, int stability) {
        com.sc.client.BridgeVortexFxSC.collapse(x, y, z, kind, size, axis, ringless, stability);
    }

    @Override
    public void bridgeArrive(int kind) {
        com.sc.client.BridgeVortexFxSC.arrive(kind);
    }

    @Override
    public void openRemote() {
        Minecraft.getMinecraft().displayGuiScreen(new com.sc.client.GuiRemoteSC());
    }

    @Override
    public void openCoordinator() {
        Minecraft.getMinecraft().displayGuiScreen(new com.sc.client.GuiCoordinatorSC());
    }

    @Override
    public void bridgeSoftLand(int ticks) {
        com.sc.bridge.BridgeSoftLandSC.startClient(Minecraft.getMinecraft().thePlayer, ticks);
    }

    @Override
    public void bridgeFarState(net.minecraft.nbt.NBTTagCompound state) {
        net.minecraft.client.gui.GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s instanceof com.sc.client.GuiRemoteSC) {
            ((com.sc.client.GuiRemoteSC) s).setState(state);
        } else if (s instanceof com.sc.client.GuiArmorBridgeSC) {
            ((com.sc.client.GuiArmorBridgeSC) s).setState(state);
        }
        com.sc.client.BridgeHudSC.heard(state);
    }

    @Override
    public void openManual() {
        Minecraft.getMinecraft().displayGuiScreen(new com.sc.manual.GuiBook());
    }

    @Override
    public boolean playsOnRemoteServer() {
        return !Minecraft.getMinecraft().isSingleplayer();
    }

    /** World time of the last sneak + right-click with a blade - a held button repeats every 4 ticks. */
    private long lastBladeClick = -100;

    @Override
    public void toggleBlade() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = mc.theWorld.getTotalWorldTime();
        boolean fresh = now - lastBladeClick > 6;           // a new press, not the button held down
        lastBladeClick = now;
        if (fresh) {
            com.sc.client.ArmorKeyBindsSC.fire(mc, com.sc.util.BladeFeature.BLADE);
        }
    }
}
