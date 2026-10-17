package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.emp.EmpSC;
import com.sc.item.ArmorLogicSC;
import com.sc.mob.EntityColossusSC;
import com.sc.mob.EntityLeechSC;
import com.sc.mob.EntityPhantomSC;
import com.sc.mob.EntityScrapperSC;
import com.sc.mob.EntityStriderSC;
import com.sc.mob.EntityWaferCrabSC;
import com.sc.util.ArmorFeature;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.11): the renders of Ф4's «noise» and the crab - the Induction Phantom (drawn only
 * to a player who sees it: the night vision of a helmet on, or the Singular helmet's sensors - else a faint shimmer), the Current
 * Leech, the Quartzite Colossus, the Arc Strider (its arc), the Scrapper Crab, the Wafer Crab. Called from ClientProxy.preInit
 * (the coordinator's line).
 * <p>
 * Each creature has its own model (Model*SC, textures/entity/<name>.png - tools/gen_substrate_noise_tex.py, the models' UV
 * drawn 2 x 2 as the mod's other skins) and renderer (Render*SC); the telegraphs come from the DataWatcher (IMobActionSC), so
 * every client draws the same - no packets. The shared bits here: seesPhantoms (whether this client's player sees an Induction
 * Phantom now - its helmet's night vision: the potion with a helmet of the mod's on and not suppressed; or the Singular helmet's
 * Ш1 gravity scanner / Ш2 threat sense running - ArmorLogicSC.active, both-sided; asked once a client tick, cached), lamp (a
 * part full-bright in a colour) and the full-bright helpers.
 */
@SideOnly(Side.CLIENT)
public final class NoiseClientSC {

    private static long seenTick = Long.MIN_VALUE;
    private static boolean seen;
    private static float lastX, lastY;

    private NoiseClientSC() {
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityPhantomSC.class, new RenderPhantomSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityLeechSC.class, new RenderLeechSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityColossusSC.class, new RenderColossusSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityStriderSC.class, new RenderStriderSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityScrapperSC.class, new RenderScrapperSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityWaferCrabSC.class, new RenderWaferCrabSC());
    }

    /** This client's player sees Induction Phantoms now (cached for the client tick). */
    public static boolean seesPhantoms() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            return false;
        }
        long now = mc.theWorld.getTotalWorldTime();
        if (now != seenTick) {
            seenTick = now;
            seen = sees(p);
        }
        return seen;
    }

    /** `p` sees them: the night vision of a helmet of the mod's (not suppressed), or the Singular helmet's Ш1 / Ш2 running. */
    public static boolean sees(EntityPlayer p) {
        if (p == null) {
            return false;
        }
        if (p.isPotionActive(Potion.nightVision) && ArmorLogicSC.piece(p, 0) != null && !EmpSC.isSuppressed(p, 0)) {
            return true;
        }
        return ArmorLogicSC.active(p, ArmorFeature.GRAV_SCANNER) || ArmorLogicSC.active(p, ArmorFeature.THREAT_SENSE);
    }

    /** Full-bright from here (the lightmap at 240 / 240; brightOff puts it back). */
    public static void brightOn() {
        lastX = OpenGlHelper.lastBrightnessX;
        lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
    }

    public static void brightOff() {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    /** `part` (with its children) full-bright, untextured, in `rgb` x `k` - a lamp, an electrode, an eye (the model's matrix). */
    public static void lamp(ModelRenderer part, int rgb, float k) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        brightOn();
        GL11.glColor4f((rgb >> 16 & 255) / 255F * k, (rgb >> 8 & 255) / 255F * k, (rgb & 255) / 255F * k, 1F);
        part.render(0.0625F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        brightOff();
        GL11.glPopAttrib();
    }

    /** The red / green / blue of `rgb` (0..1). */
    public static float r(int rgb) {
        return (rgb >> 16 & 255) / 255F;
    }

    public static float g(int rgb) {
        return (rgb >> 8 & 255) / 255F;
    }

    public static float b(int rgb) {
        return (rgb & 255) / 255F;
    }
}
