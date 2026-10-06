package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.item.DrillLogicSC;
import com.sc.util.ToolLevelSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * The drill's zone frame on the looked-at block (docs/plan-singular-tools.md §3.2 «рамка зоны подсвечивается заранее»):
 * the Singular drill's black hole zone - and any drill's area / funnel / tunnel face - outlined before the hit,
 * the same box the server will take (DrillLogicSC.zoneFor). The Singular drill: its colour scheme's accent, pulsing;
 * other drills: a faint white. Client only; registered on MinecraftForge.EVENT_BUS.
 */
public final class DrillHoleRendererSC {

    public static final DrillHoleRendererSC INSTANCE = new DrillHoleRendererSC();

    private DrillHoleRendererSC() {
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (p == null || hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mc.gameSettings.hideGUI) {
            return;
        }
        ItemStack drill = DrillLogicSC.held(p);
        if (drill == null) {
            return;
        }
        int[] box = DrillLogicSC.zoneFor(p, drill, hit.blockX, hit.blockY, hit.blockZ, hit.sideHit);
        if (box == null) {
            return;
        }
        EntityLivingBase v = mc.renderViewEntity;
        if (v == null) {
            return;
        }
        boolean sing = ToolLevelSC.isDrill(drill);
        boolean hole = DrillLogicSC.holeSize(p, drill) > 0;
        int rgb = sing ? ToolLevelSC.schemeOf(drill).accent & 0xFFFFFF : 0xFFFFFF;
        double px = v.lastTickPosX + (v.posX - v.lastTickPosX) * e.partialTicks;
        double py = v.lastTickPosY + (v.posY - v.lastTickPosY) * e.partialTicks;
        double pz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * e.partialTicks;
        double g = 0.004;
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(box[0] - g, box[1] - g, box[2] - g, box[3] + 1 + g, box[4] + 1 + g, box[5] + 1 + g)
                .getOffsetBoundingBox(-px, -py, -pz);
        float pulse = 0.75F + 0.25F * (float) Math.sin(System.currentTimeMillis() / 200.0);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        // the face on the wall, bright; then the whole box faintly through the rock
        GL11.glLineWidth(hole ? 3F : 2F);
        edges(bb, rgb, sing ? pulse : 0.5F);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1F);
        edges(bb, rgb, sing ? 0.25F : 0.12F);
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The box's 12 edges in one colour with alpha (RenderGlobal's outline is always opaque). */
    private static void edges(AxisAlignedBB b, int rgb, float alpha) {
        GL11.glColor4f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, alpha);
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                GL11.glVertex3d(b.minX, ys[i], zs[j]);
                GL11.glVertex3d(b.maxX, ys[i], zs[j]);
                GL11.glVertex3d(xs[i], b.minY, zs[j]);
                GL11.glVertex3d(xs[i], b.maxY, zs[j]);
                GL11.glVertex3d(xs[i], ys[j], b.minZ);
                GL11.glVertex3d(xs[i], ys[j], b.maxZ);
            }
        }
        GL11.glEnd();
    }
}
