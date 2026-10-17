package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.manual.Lang;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityArmoredMobSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §5.3, rule ПС-25): an elite's name over its head - «Командир · Штурмовик»
 * (sc.elite.name: the modifier, the creature), gold, in this client's language, within NAME_RANGE blocks - drawn as
 * vanilla draws a name tag (Render.func_147906_a: a dark plate, the see-through pass, then the solid one). A mob that has
 * a name tag too gets ours one line higher. MobArmorClientSC calls it from RenderLivingEvent.Specials.Post. Client only.
 */
@SideOnly(Side.CLIENT)
public final class RenderEliteNameSC {

    /** ПС-25: the name is drawn this near the camera, blocks. */
    public static final double NAME_RANGE = 32;
    /** One line of a name tag, blocks (10 font pixels at the tag's scale). */
    private static final float LINE = 0.27F;

    private RenderEliteNameSC() {
    }

    /** The name of `mob` at the render offset (x, y, z) - nothing for a plain mob, an invisible one, one farther than NAME_RANGE, or with F1. */
    public static void draw(EntityArmoredMobSC mob, RendererLivingEntity renderer, double x, double y, double z) {
        EliteModSC m = mob.eliteMod();
        RenderManager rm = RenderManager.instance;
        if (m == null || mob.isInvisible() || !Minecraft.isGuiEnabled() || rm.livingPlayer == null
                || mob.getDistanceSqToEntity(rm.livingPlayer) > NAME_RANGE * NAME_RANGE) {
            return;
        }
        FontRenderer fr = renderer.getFontRendererFromRenderManager();
        if (fr == null) {
            return;
        }
        String text = "§6" + Lang.tr("sc.elite.name", Lang.tr(m.langKey()), mob.getCommandSenderName());
        float lift = mob.hasCustomNameTag() ? LINE : 0F;
        float scale = 0.016666668F * 1.6F;
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y + mob.height + 0.5F + lift, (float) z);
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
        fr.drawString(text, -half, 0, 0x20FFFFFF);                  // seen through walls, faint (as vanilla's tag)
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        fr.drawString(text, -half, 0, -1);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }
}
