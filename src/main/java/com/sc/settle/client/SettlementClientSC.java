package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.dim.SubstrateSC;
import com.sc.manual.Lang;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.9): the settlements' creatures on the client - the renderers of
 * EntityTunerSC (RenderTunerSC: textures/entity/tuner_<kind>.png, the mood lamp) and EntityWardenSC (RenderWardenSC:
 * warden.png, the alarm lamp; the worn armour draws itself through ModelArmorGlowSC), and what both share: the name
 * over the head - NpcNamesSC translated here, by each client in its own language (not a custom name tag: that is one
 * string for everyone), within NAME_RANGE blocks, the profession / rank under it - and the full-bright lamps. Called
 * from ClientProxy.preInit. Textures: tools/gen_settlement_mobs_tex.py. Ф3 (docs/plan-substrate-tech-f3.md §6.1, package S):
 * the same two renderers draw the Power Engineer, the Armourer and the Salvager (RenderTunerSC: their parts and skins) and
 * the Captain and the Commandant (RenderWardenSC: their skins, cloaks, the wind-up) - nothing more to register; the new
 * kinds' names under the name are their lang keys (sc.settle.kind.<key>); tools/gen_settlement3_tex.py. Ф6 (docs/plan-substrate-tech-f6.md
 * §8.6, package T, ПЖ-11, ПЖ-12, ПЖ-14): the Dispatcher and the Quartermaster are drawn by RenderTunerSC (their parts and skins -
 * tools/gen_settlement_mobs_tex.py); an embassy's Elder is «Посол» under his name - kind() asks NpcKindSC.langKey(embassy), the world
 * told by its provider (not the Substrate: an embassy of the Overworld), the same for WAILA's line (compat.WailaEntitySC).
 */
@SideOnly(Side.CLIENT)
public final class SettlementClientSC {

    /** ПЖ-6: the name over the head is drawn this near the camera (blocks). */
    public static final double NAME_RANGE = 16D;

    private SettlementClientSC() {
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityTunerSC.class, new RenderTunerSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityWardenSC.class, new RenderWardenSC());
    }

    /** «Тантал-3» in the reader's language. */
    public static String name(ISettlementMemberSC m) {
        return NpcNamesSC.name(m.nameCode()).getUnformattedText();
    }

    /** «Технолог», «Постовой»; Ф6: an embassy's Elder «Посол». */
    public static String kind(ISettlementMemberSC m) {
        return Lang.tr(m.kind().langKey(embassy(m)));
    }

    /** Ф6 (ПЖ-14): `m` stands in an embassy - a creature of a settlement outside the Substrate (the client knows the world by its provider). */
    public static boolean embassy(ISettlementMemberSC m) {
        return m instanceof Entity && ((Entity) m).worldObj != null && !SubstrateSC.is(((Entity) m).worldObj);
    }

    /**
     * Vanilla's name tag (Render.func_147906_a), two lines: `top` in `topColor` over `bottom` (grey, may be null), at
     * the vanilla height over the entity; faint through walls, full where seen. Within NAME_RANGE of the camera only,
     * not with the HUD hidden (F1), not on an invisible one.
     */
    public static void label(RenderManager rm, FontRenderer fr, Entity e, double x, double y, double z, String top, int topColor, String bottom) {
        Minecraft mc = Minecraft.getMinecraft();
        if (rm.livingPlayer == null || fr == null || !Minecraft.isGuiEnabled() || e.getDistanceSqToEntity(rm.livingPlayer) > NAME_RANGE * NAME_RANGE
                || mc.thePlayer != null && e.isInvisibleToPlayer(mc.thePlayer)) {
            return;
        }
        float s = 0.016666668F * 1.6F;
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y + e.height + 0.5F, (float) z);
        GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-rm.playerViewY, 0F, 1F, 0F);
        GL11.glRotatef(rm.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-s, -s, s);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        int yTop = bottom == null ? 0 : -10;
        int half = Math.max(fr.getStringWidth(top), bottom == null ? 0 : fr.getStringWidth(bottom)) / 2;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(0F, 0F, 0F, 0.25F);
        t.addVertex(-half - 1, yTop - 1, 0D);
        t.addVertex(-half - 1, 8D, 0D);
        t.addVertex(half + 1, 8D, 0D);
        t.addVertex(half + 1, yTop - 1, 0D);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int pass = 0; pass < 2; pass++) {                  // faint through walls, then full where it is seen
            if (pass == 1) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
            }
            fr.drawString(top, -fr.getStringWidth(top) / 2, yTop, pass == 0 ? 0x20FFFFFF : topColor);
            if (bottom != null) {
                fr.drawString(bottom, -fr.getStringWidth(bottom) / 2, 0, pass == 0 ? 0x20FFFFFF : 0xB0B0B0);
            }
        }
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    /**
     * A lamp: `part` drawn untextured, full-bright, in `rgb` x `k` (the caller has moved the matrix to its parent:
     * ModelRenderer.postRender). The lighting, the lightmap and the texture state are put back.
     */
    public static void lamp(ModelRenderer part, int rgb, float k) {
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glColor4f((rgb >> 16 & 255) / 255F * k, (rgb >> 8 & 255) / 255F * k, (rgb & 255) / 255F * k, 1F);
        part.render(0.0625F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GL11.glPopAttrib();
    }
}
