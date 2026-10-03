package com.sc.client;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The bridge's «Проверить»: the first problem block outlined in red in the world for a few seconds
 * (seen through walls), so one finds the missing coil without counting.
 */
public final class BridgeHighlightSC {

    private static int[] pos;
    private static long until;

    private BridgeHighlightSC() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new BridgeHighlightSC());
    }

    public static void set(int[] p, int ticks) {
        if (p != null && p.length == 3) {
            pos = p.clone();
            until = System.currentTimeMillis() + Math.max(1, ticks) * 50L;
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent e) {
        if (pos == null || System.currentTimeMillis() > until) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase v = mc.renderViewEntity;
        if (v == null) {
            return;
        }
        double px = v.lastTickPosX + (v.posX - v.lastTickPosX) * e.partialTicks;
        double py = v.lastTickPosY + (v.posY - v.lastTickPosY) * e.partialTicks;
        double pz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * e.partialTicks;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(pos[0] - 0.02, pos[1] - 0.02, pos[2] - 0.02, pos[0] + 1.02, pos[1] + 1.02, pos[2] + 1.02)
                .getOffsetBoundingBox(-px, -py, -pz);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(3F);
        float a = 0.6F + 0.4F * (float) Math.sin(System.currentTimeMillis() / 150.0);
        RenderGlobal.drawOutlinedBoundingBox(box, ((int) (a * 255) << 24) | 0xFF3030);
        GL11.glPopAttrib();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
