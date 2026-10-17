package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.8): a Substrate mob on vanilla's biped - its skin
 * textures/entity/<name>.png (the 64 x 32 layout, drawn at 128 x 64 like the mod's armour). The worn mod armour draws
 * itself (RenderBiped.shouldRenderPass -> ForgeHooksClient.getArmorModel -> ItemArmorSC.getArmorModel ->
 * ModelArmorGlowSC, which takes the mob's charge, «down», suppression and siphon from its DataWatcher); the item in
 * hand goes vanilla's way; crouching at a trace shows (RenderBiped takes isSneaking). The Lineman's skin is mostly
 * transparent - the empty shell (the alpha test cuts it): only its frame, the cable and the diode where no piece covers it.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.1, rule ПС-9; package M): the worn armour hides the mob's own model, and its model
 * takes only the sneak and the held item from the wearer - so a telegraph's pose is drawn by leaning the whole figure
 * (armour too) in preRenderCallback: a subclass answers lean() by the mob's IMobActionSC.mobAction() (degrees forward, the
 * feet stay), and scale() for a larger kind. lamp() - a full-bright part (the Jammer's antenna tips). While a gun aims
 * (IMobActionSC.ACT_AIM) a subclass may answer aimLaser() with a colour: a thin full-bright sight from its eyes along its
 * head's look, up to the first block or aimRange() - the «лазерный прицел» every client sees with the server's dotted line.
 */
@SideOnly(Side.CLIENT)
public class RenderSubstrateMobSC extends RenderBiped {

    private final ResourceLocation skin;

    public RenderSubstrateMobSC(ModelBiped model, float shadow, String name) {
        super(model, shadow);
        skin = new ResourceLocation(Reference.ASSETS, "textures/entity/" + name + ".png");
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving e) {
        return skin;
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float partial) {
        super.preRenderCallback(e, partial);
        float s = scale(e);
        if (s != 1F) {
            GL11.glScalef(s, s, s);
        }
        if (e.deathTime <= 0 && e instanceof IMobActionSC) {
            float lean = lean(e, ((IMobActionSC) e).mobAction(), partial);
            if (lean != 0F) {
                GL11.glRotatef(lean, 1F, 0F, 0F);                  // (the flipped model space: +X tips it forward)
            }
        }
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float partial) {
        super.doRender(e, x, y, z, yaw, partial);
        int rgb = e.deathTime <= 0 && e instanceof IMobActionSC && ((IMobActionSC) e).mobAction() == IMobActionSC.ACT_AIM ? aimLaser(e) : -1;
        if (rgb >= 0) {
            laser(e, x, y, z, partial, rgb);
        }
    }

    /** Ф3: the aiming sight's colour (0xRRGGBB) for this mob while ACT_AIM, -1 - none (the Ф1 mobs: the dotted line only). */
    protected int aimLaser(EntityLiving e) {
        return -1;
    }

    /** Ф3: how far the sight reaches (blocks). */
    protected double aimRange(EntityLiving e) {
        return 16D;
    }

    /** The sight: eyes -> along the head's yaw and pitch -> the first block (a client ray) or aimRange; camera-relative. */
    private void laser(EntityLiving e, double x, double y, double z, float partial, int rgb) {
        float yawHead = e.prevRotationYawHead + (e.rotationYawHead - e.prevRotationYawHead) * partial;
        float pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * partial;
        double cy = Math.cos(Math.toRadians(pitch));
        double dx = -Math.sin(Math.toRadians(yawHead)) * cy, dy = -Math.sin(Math.toRadians(pitch)), dz = Math.cos(Math.toRadians(yawHead)) * cy;
        double eye = e.getEyeHeight() - 0.1D, len = aimRange(e);
        double wx = e.lastTickPosX + (e.posX - e.lastTickPosX) * partial, wy = e.lastTickPosY + (e.posY - e.lastTickPosY) * partial + eye,
                wz = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial;
        MovingObjectPosition hit = e.worldObj.rayTraceBlocks(Vec3.createVectorHelper(wx, wy, wz),
                Vec3.createVectorHelper(wx + dx * len, wy + dy * len, wz + dz * len));
        if (hit != null && hit.hitVec != null) {
            len = Math.min(len, Math.sqrt((hit.hitVec.xCoord - wx) * (hit.hitVec.xCoord - wx) + (hit.hitVec.yCoord - wy) * (hit.hitVec.yCoord - wy)
                    + (hit.hitVec.zCoord - wz) * (hit.hitVec.zCoord - wz)));
        }
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glLineWidth(2F);
        float flicker = 0.55F + 0.35F * (float) Math.sin((e.ticksExisted + partial) * 1.7F);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, flicker);
        double sx = x, sy = y + eye, sz = z;
        t.addVertex(sx, sy, sz);
        t.addVertex(sx + dx * len, sy + dy * len, sz + dz * len);
        t.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GL11.glPopAttrib();
    }

    /** Ф3: degrees the whole figure leans forward (negative: back) for the telegraph `action`; 0 - none. */
    protected float lean(EntityLivingBase e, int action, float partial) {
        return 0F;
    }

    /** Ф3: the figure's size (1 - vanilla's biped). */
    protected float scale(EntityLivingBase e) {
        return 1F;
    }

    /** Ф3: `part` full-bright in `rgb` x `k` (no texture, no lighting) - call inside the right part's postRender. */
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
