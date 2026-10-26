package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.manual.Lang;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityWantedSC;
import com.sc.mob.WantedRulesSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLiving;

/**
 * ПД-4 (docs/plan-ideas-2026-10-26.md, «Розыск», ★ A): the wanted Renegade - a Trooper's body under its own suit (the Quantum's crimson
 * or the Exo's - the armour is the mob's own, MobArmorClientSC draws it), and over its head, above the elite's gold line, its name in
 * crimson («Хром-7 „Резчик“») and both modifiers («Командир + Ветеран») - from EntityWantedSC.DW_WANTED and the elite's DataWatcher, in
 * this client's language, within RenderEliteNameSC.NAME_RANGE. Client only (WantedClientSC registers it).
 */
@SideOnly(Side.CLIENT)
public class RenderWantedSC extends RenderTrooperSC {

    /** One line of a name tag, blocks (10 font pixels at the tag's scale). */
    private static final float LINE = 0.27F;

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float partial) {
        super.doRender(e, x, y, z, yaw, partial);
        if (e instanceof EntityWantedSC) {
            names((EntityWantedSC) e, x, y, z);
        }
    }

    private void names(EntityWantedSC e, double x, double y, double z) {
        RenderManager rm = RenderManager.instance;
        if (e.isInvisible() || !Minecraft.isGuiEnabled() || rm.livingPlayer == null
                || e.getDistanceSqToEntity(rm.livingPlayer) > RenderEliteNameSC.NAME_RANGE * RenderEliteNameSC.NAME_RANGE) {
            return;
        }
        FontRenderer fr = getFontRendererFromRenderManager();
        if (fr == null) {
            return;
        }
        int look = e.look();
        String name = "§c" + Lang.tr("sc.wanted.fullName", Lang.tr("sc.wanted.name." + WantedRulesSC.nameOf(look)),
                Lang.tr("sc.wanted.alias." + WantedRulesSC.aliasOf(look)));
        EliteModSC m1 = e.eliteMod(), m2 = EliteModSC.of(WantedRulesSC.mod2Of(look));
        String mods = "§6" + (m1 == null ? "" : Lang.tr(m1.langKey())) + (m2 == null ? "" : " + " + Lang.tr(m2.langKey()));
        float base = e.height + 0.5F + LINE * (e.hasCustomNameTag() ? 2 : 1);   // over the elite's own line
        line(fr, rm, mods, x, y + base, z);
        line(fr, rm, name, x, y + base + LINE, z);
    }

    /** One line drawn as vanilla's name tag (a dark plate, the see-through pass, the solid one). */
    private static void line(FontRenderer fr, RenderManager rm, String text, double x, double y, double z) {
        float scale = 0.016666668F * 1.6F;
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-rm.playerViewY, 0F, 1F, 0F);
        GL11.glRotatef(rm.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        int half = fr.getStringWidth(text) / 2;
        Tessellator t = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        t.startDrawingQuads();
        t.setColorRGBA_F(0F, 0F, 0F, 0.25F);
        t.addVertex(-half - 1, -1, 0D);
        t.addVertex(-half - 1, 8, 0D);
        t.addVertex(half + 1, 8, 0D);
        t.addVertex(half + 1, -1, 0D);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        fr.drawString(text, -half, 0, 0x20FFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        fr.drawString(text, -half, 0, -1);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }
}
