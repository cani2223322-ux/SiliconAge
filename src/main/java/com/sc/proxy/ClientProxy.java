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
        BlockConduitSC.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new ConduitRenderer(BlockConduitSC.renderId));
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityFieldGeneratorSC.class, new com.sc.client.FieldRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityTankSC.class, new com.sc.client.TankRendererSC());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.sc.tileentity.TileEntityQuarrySC.class, new com.sc.client.QuarryRendererSC());
    }

    @Override
    public void openManual() {
        Minecraft.getMinecraft().displayGuiScreen(new com.sc.manual.GuiBook());
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
