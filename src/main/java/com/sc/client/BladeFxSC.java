package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.item.BladeLogicSC;
import com.sc.util.ArmorGasSC;
import com.sc.util.BladeFeature;
import com.sc.util.ToolGasSC;
import com.sc.util.ToolLevelSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * The Singular blade's client effects: «Чутьё охотника» - hostile mobs within HUNTER_RANGE outlined in
 * orange through walls while the function works (the blade lit in hand, on, open, krypton in the worn
 * armour; the server drains it). The client finds the mobs itself. Client only; registered by the proxy.
 */
public class BladeFxSC {

    public static final BladeFxSC INSTANCE = new BladeFxSC();

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null || !ToolLevelSC.isBlade(BladeLogicSC.held(p))
                || !BladeLogicSC.active(p, BladeFeature.HUNTER_SENSE) || !ToolGasSC.has(p, ArmorGasSC.Gas.KRYPTON, 1)) {
            return;
        }
        float pt = event.partialTicks;
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        double r2 = BladeFeature.HUNTER_RANGE * BladeFeature.HUNTER_RANGE;
        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2F);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        for (Object o : mc.theWorld.loadedEntityList) {
            Entity e = (Entity) o;
            if (!(e instanceof EntityLivingBase) || !(e instanceof IMob) || e.isDead || e == p || e.getDistanceSqToEntity(p) > r2) {
                continue;
            }
            double dx = (e.posX - e.lastTickPosX) * pt - (e.posX - e.lastTickPosX);
            double dy = (e.posY - e.lastTickPosY) * pt - (e.posY - e.lastTickPosY);
            double dz = (e.posZ - e.lastTickPosZ) * pt - (e.posZ - e.lastTickPosZ);
            box(t, e.boundingBox.getOffsetBoundingBox(dx, dy, dz).expand(0.05, 0.05, 0.05), 1F, 0.55F, 0.1F, 0.85F);
        }
        t.draw();                                           // with nothing added it only resets the tessellator
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glLineWidth(1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static void box(Tessellator t, AxisAlignedBB b, float r, float g, float bl, float a) {
        t.setColorRGBA_F(r, g, bl, a);
        double[][] c = {{b.minX, b.minY, b.minZ}, {b.maxX, b.minY, b.minZ}, {b.maxX, b.minY, b.maxZ}, {b.minX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.maxZ}, {b.minX, b.maxY, b.maxZ}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            t.addVertex(c[e[0]][0], c[e[0]][1], c[e[0]][2]);
            t.addVertex(c[e[1]][0], c[e[1]][1], c[e[1]][2]);
        }
    }
}
