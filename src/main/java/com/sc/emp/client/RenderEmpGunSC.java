package com.sc.emp.client;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModItems;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

/**
 * ПЭ-1 drawn as its 3D model «Сифон-капсула» (docs/pe1-model-mockup.py m_capsule, docs/pe1-mockup3d.png): a steel
 * body, the energy capsule in a cage of bars between two brass rings, a needle emitter between two claws, a cable down
 * to the wrapped grip. Built from boxes and cylinders with the Tessellator (no texture, no model file), in the mock-up's
 * units: x along the barrel (0 the back of the body .. 11.6 the needle's tip), y up (-3.4 the grip's foot), z across.
 *
 * The capsule glows full-bright in the current mode's colour (EmpMode.rgb) and is filled from the back as far as the
 * gun is charged; bands run back through it while a Siphon stream pulls energy in, and it throbs faster through a
 * Point / Pulse charge-up. ПЭ-1М: a second pair of copper rings round the capsule and a brass band on the needle.
 * In a mob's hand (Подложка Ф3, EmpMobGunSC - it has no charge of its own) the capsule glows a third full at rest and fills
 * bright in the mode's colour through a charge-up the mob's telegraph tells (IMobActionSC.ACT_EMP_POINT: the barrel turns
 * blue; ACT_EMP_PULSE: violet) - the warning every client sees.
 *
 * Poses: in a slot a fixed 3/4 view from a little above; dropped, Forge turns and bobs it (~0.75 blocks long); in the
 * hand (first and third person) Forge / vanilla first apply their flat-item chain (ForgeHooksClient.renderEquippedItem
 * without EQUIPPED_BLOCK, ItemRenderer.renderItemInFirstPerson, RenderPlayer.renderEquippedItems' 2D branch) - the
 * hand poses undo that chain exactly and put the gun where FP_* / TP_* say (barrel ahead and level, the grip in the
 * hand). Tune those constants, not the chain maths.
 * GL state: everything touched is pushed and popped.
 */
@SideOnly(Side.CLIENT)
public final class RenderEmpGunSC implements IItemRenderer {

    public static final RenderEmpGunSC INSTANCE = new RenderEmpGunSC();

    // ------------------------------------------------------------------ palette (the mock-up's)
    private static final int STEEL = 0xB2BAC6, STEEL_D = 0x787F8C, GUN = 0x4E535E, GUN_D = 0x383C44, COPPER = 0xD68040,
            BRASS = 0xE0BA5C, WRAP = 0x423C38, WRAP_D = 0x322E2B, GLASS = 0x2A3038, LED = 0xF0FFF0, LED_OFF = 0x506050;

    // ------------------------------------------------------------------ the model's key places
    /** The capsule: from CAP_X0 to CAP_X1 along the barrel, axis at (CAP_Y, 0), radius CAP_R. */
    private static final float CAP_X0 = 2.85F, CAP_X1 = 5.55F, CAP_Y = 1.0F, CAP_R = 1.1F;
    /** One band of the capsule's energy (its animation step). */
    private static final float BAND = 0.27F;
    /** The needle's axis height. */
    private static final float NEEDLE_Y = 0.9F;
    /** The model's middle along the barrel, and its grip's foot. */
    private static final float MID_X = 5.8F, FOOT_Y = -3.4F;

    // ------------------------------------------------------------------ poses
    /** Slot: view tilt from above, turn (negative: the barrel goes away), roll of the barrel up, GUI pixels per unit. */
    private static final float INV_TILT = 22F, INV_YAW = -20F, INV_ROLL = 12F, INV_SCALE = 1.25F;
    /** Dropped: units in Forge's 0.5-scaled block space (11.6 units * 0.125 * 0.5 = ~0.73 blocks). */
    private static final float ENTITY_SCALE = 0.125F;
    /**
     * First person, in the camera space at vanilla's hand anchor (its first translate, (0.56, -0.52, -0.72) at rest):
     * the barrel's direction (a little left and up, towards the crosshair), blocks per unit, where the gun's origin goes.
     */
    private static final double[] FP_BARREL = {-0.10, 0.04, -1}, FP_UP = {0, 1, 0}, FP_AT = {-0.14, 0.19, 0.17};
    private static final double FP_SCALE = 0.07;
    /**
     * Third person, in the right arm's space after RenderPlayer's (-0.0625, 0.4375, 0.0625) (y down the arm, -z ahead):
     * the arm hangs ARM_TILT degrees forward while holding (ModelBiped: -PI/10), so the barrel is tilted back up by it to
     * be level; the gun's grip point TP_GRIP sits at TP_HAND.
     */
    private static final double ARM_TILT = 18, TP_SCALE = 0.055;
    private static final double[] TP_GRIP = {1.65, -1.2, 0}, TP_HAND = {0, 0.05, -0.03};

    private static final FloatBuffer FP_POSE = firstPersonPose(), TP_POSE = thirdPersonPose();

    /** A mob's gun at rest shows this much of its capsule (Ф3). */
    private static final float MOB_REST_FILL = 0.35F;
    /** Render thread: a mob's charge-up as first seen - entity id -> {telegraph, System ms} (the client doesn't know its start). */
    private static final Map<Integer, long[]> MOB_CHARGE = new HashMap<Integer, long[]>();

    private RenderEmpGunSC() {
    }

    public static void register() {
        MinecraftForgeClient.registerItemRenderer(ModItems.empGun, INSTANCE);
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null && (type == ItemRenderType.INVENTORY || type == ItemRenderType.ENTITY || type == ItemRenderType.EQUIPPED
                || type == ItemRenderType.EQUIPPED_FIRST_PERSON);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        // dropped: Forge's turn, bob and block scale; in hand: the flat-item chain (undone by the poses); slot: our own
        return type == ItemRenderType.ENTITY && (helper == ItemRendererHelper.ENTITY_ROTATION
                || helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.BLOCK_3D);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        EntityPlayer holder = null;
        EntityLivingBase mobHolder = null;
        if ((type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) && data.length > 1
                && data[1] instanceof EntityPlayer) {
            holder = (EntityPlayer) data[1];
        } else if (type == ItemRenderType.EQUIPPED && data.length > 1 && data[1] instanceof EntityLivingBase) {
            mobHolder = (EntityLivingBase) data[1];             // Ф3: a Jammer carrier, the Commandant
        }
        EmpMode mode = ItemEmpGunSC.modeOf(item);
        int cap = ItemEmpGunSC.capacityOf(item);
        float fill = cap <= 0 ? 0F : Math.min(1F, (float) ItemEmpGunSC.chargeOf(item) / cap);
        long ms = Minecraft.getSystemTime();
        // what the capsule shows: the stream pulling energy in, the charge-up's progress (0..1), or rest (-1)
        boolean streaming = false;
        float chargeUp = -1F;
        if (holder != null && holder.isUsingItem() && ItemEmpGunSC.isGun(holder.getItemInUse())) {
            if (mode == EmpMode.SIPHON) {
                streaming = holder != Minecraft.getMinecraft().thePlayer || ownStreamRuns();
            } else {
                int ticks = EmpMathSC.chargeTicks(mode, ItemEmpGunSC.upgraded(item));
                chargeUp = ticks <= 0 ? 1F : Math.min(1F, holder.getItemInUseDuration() / (float) ticks);
            }
        }
        if (holder != null && EmpFxSC.streamingFrom(holder.getEntityId())) {
            streaming = true;
        }
        if (mobHolder != null) {
            // a mob's ПЭ-1: its charge-up by its telegraph (the barrel turns blue - ПБ-17), a third of the capsule lit at rest
            int act = mobHolder instanceof IMobActionSC ? ((IMobActionSC) mobHolder).mobAction() : IMobActionSC.ACT_NONE;
            chargeUp = mobChargeUp(mobHolder.getEntityId(), act, ItemEmpGunSC.upgraded(item), ms);
            mode = act == IMobActionSC.ACT_EMP_PULSE ? EmpMode.PULSE : EmpMode.POINT;
            fill = chargeUp >= 0F ? MOB_REST_FILL + (1F - MOB_REST_FILL) * chargeUp : MOB_REST_FILL;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_TRANSFORM_BIT);
        GL11.glPushMatrix();
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        try {
            pose(type);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            solid(t, mode, fill, ItemEmpGunSC.upgraded(item));
            t.draw();
            // the lit parts: full-bright, unshaded
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            t.startDrawingQuads();
            glow(t, mode, fill, streaming, chargeUp, ms);
            t.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /**
     * A mob's charge-up 0..1 by the time its telegraph has been on (seen by this client), -1 at rest. The client doesn't know
     * the server's start: a mob first seen mid-charge starts from there - the warning is only ever late, never early.
     */
    private static float mobChargeUp(int id, int act, boolean upgraded, long ms) {
        if (act != IMobActionSC.ACT_EMP_POINT && act != IMobActionSC.ACT_EMP_PULSE) {
            MOB_CHARGE.remove(id);
            return -1F;
        }
        long[] c = MOB_CHARGE.get(id);
        if (c == null || c[0] != act) {
            if (MOB_CHARGE.size() > 64) {
                MOB_CHARGE.clear();                             // mobs unloaded mid-charge: never many
            }
            c = new long[]{act, ms};
            MOB_CHARGE.put(id, c);
        }
        int ticks = EmpMobGunSC.chargeTicks(act == IMobActionSC.ACT_EMP_PULSE ? EmpMode.PULSE : EmpMode.POINT, upgraded);
        return ticks <= 0 ? 1F : Math.max(0F, Math.min(1F, (ms - c[1]) / (ticks * 50F)));
    }

    /** Our own player's stream runs (the server's readout, fresh). */
    private static boolean ownStreamRuns() {
        EmpNetSC.AimMessage a = EmpNetSC.lastAim;
        return a != null && a.streaming && System.currentTimeMillis() - EmpNetSC.lastAimAt < 1000L;
    }

    private static void pose(ItemRenderType type) {
        if (type == ItemRenderType.INVENTORY) {
            // the slot: 16 x 16 GUI pixels from (0, 0), y down
            GL11.glTranslatef(8F, 8.5F, 50F);
            RenderHelper.enableGUIStandardItemLighting();          // before the turn: the light stays put
            GL11.glRotatef(180F, 1F, 0F, 0F);                      // y up, z away
            GL11.glRotatef(-INV_TILT, 1F, 0F, 0F);
            GL11.glRotatef(INV_YAW, 0F, 1F, 0F);
            GL11.glRotatef(INV_ROLL, 0F, 0F, 1F);
            GL11.glScalef(INV_SCALE, INV_SCALE, INV_SCALE);
            GL11.glTranslatef(-MID_X, 0.5F, 0F);                    // the model's middle (y -3.4 .. 2.4)
        } else if (type == ItemRenderType.ENTITY) {
            GL11.glScalef(ENTITY_SCALE, ENTITY_SCALE, ENTITY_SCALE);
            GL11.glTranslatef(-MID_X, -FOOT_Y, 0F);                 // stands on its grip's foot, centred along
        } else {
            GL11.glMultMatrix(type == ItemRenderType.EQUIPPED_FIRST_PERSON ? FP_POSE : TP_POSE);
        }
    }

    // ------------------------------------------------------------------ the model

    /** Everything lit by the world: body, rings, cage, empty glass, needle base, claws, cable, grip. */
    private static void solid(Tessellator t, EmpMode mode, float fill, boolean upgraded) {
        box(t, 0F, 0F, -0.9F, 8.5F, 1.8F, 0.9F, STEEL);                        // the body
        box(t, 0.4F, 1.8F, -0.35F, 2.3F, 2.05F, 0.35F, STEEL_D);                 // a rear sight rail
        // the empty part of the capsule: dark glass with a hint of the mode's colour
        float fillEnd = fillEnd(fill);
        if (fillEnd < CAP_X1) {
            cylX(t, fillEnd, CAP_X1, CAP_Y, 0F, CAP_R, CAP_R, 20, false, mix(GLASS, mode.rgb(), 0.18F));
        }
        for (int k = 0; k < 6; k++) {                                           // the cage bars
            double a = 2 * Math.PI * (k + 0.5) / 6;
            cylX(t, 2.8F, 5.6F, CAP_Y + 1.22F * (float) Math.cos(a), 1.22F * (float) Math.sin(a), 0.1F, 0.1F, 6, true, STEEL_D);
        }
        cylX(t, 2.5F, CAP_X0, CAP_Y, 0F, 1.4F, 1.4F, 20, true, BRASS);          // the brass rings
        cylX(t, CAP_X1, 5.9F, CAP_Y, 0F, 1.4F, 1.4F, 20, true, BRASS);
        if (upgraded) {                                                          // ПЭ-1М: the second row of rings
            cylX(t, 2.1F, 2.4F, CAP_Y, 0F, 1.28F, 1.28F, 20, true, COPPER);
            cylX(t, 6.0F, 6.3F, CAP_Y, 0F, 1.28F, 1.28F, 20, true, COPPER);
        }
        for (int i = 0; i < 4; i++) {                                           // vents on the front block
            box(t, 6.4F + i * 0.5F, 1.8F, -0.6F, 6.65F + i * 0.5F, 2.05F, 0.6F, GUN);
        }
        cylX(t, 8.5F, 10.2F, NEEDLE_Y, 0F, 0.45F, 0.45F, 12, true, GUN_D);      // the needle's base
        cylX(t, 9.0F, 9.3F, NEEDLE_Y, 0F, 0.52F, 0.52F, 12, true, upgraded ? BRASS : GUN);
        if (fill <= 0F) {
            cylX(t, 10.2F, 11.6F, NEEDLE_Y, 0F, 0.18F, 0.05F, 8, true, GUN);    // an empty gun: the needle dark
        }
        for (int s = -1; s <= 1; s += 2) {                                       // the claws, above and below the needle
            box(t, 9.6F, NEEDLE_Y + s * 0.6F, -0.2F, 10.4F, NEEDLE_Y + s * 1.3F, 0.2F, STEEL);
            box(t, 10.4F, NEEDLE_Y + s * 1.3F, -0.2F, 11.4F, NEEDLE_Y + s * 1.6F, 0.2F, STEEL);
            box(t, 11.2F, NEEDLE_Y + s * 0.9F, -0.2F, 11.6F, NEEDLE_Y + s * 1.6F, 0.2F, STEEL);
        }
        // the cable: from under the capsule down the side to the grip, a chain of small blocks (on the -z side: the
        // one seen in a slot and in first person)
        float[][] cable = {{3.5F, -0.05F}, {3.15F, -0.3F}, {2.8F, -0.5F}, {2.5F, -0.68F}, {2.25F, -0.85F}};
        for (float[] c : cable) {
            box(t, c[0] - 0.14F, c[1] - 0.14F, -0.78F, c[0] + 0.14F, c[1] + 0.14F, -0.52F, COPPER);
        }
        // the grip (the mock-up's grip(m, 1.0)): wrapped, three rings, a guard and the trigger
        box(t, 1.0F, FOOT_Y, -0.55F, 2.3F, 0F, 0.55F, WRAP);
        for (int i = 1; i <= 3; i++) {
            float y = -0.8F * i;
            box(t, 0.95F, y - 0.15F, -0.6F, 2.35F, y + 0.15F, 0.6F, WRAP_D);
        }
        box(t, 2.3F, -1.6F, -0.25F, 3.6F, -1.4F, 0.25F, GUN_D);
        box(t, 3.4F, -1.6F, -0.25F, 3.6F, -0.2F, 0.25F, GUN_D);
        box(t, 2.6F, -1.0F, -0.12F, 2.8F, -0.1F, 0.12F, BRASS);
    }

    /** Where the energy in the capsule ends (from its back, CAP_X0); a charged gun shows at least a sliver. */
    private static float fillEnd(float fill) {
        if (fill <= 0F) {
            return CAP_X0;
        }
        return CAP_X0 + Math.max(0.12F, (CAP_X1 - CAP_X0) * fill);
    }

    /** The full-bright parts: the capsule's energy, the needle, the side lights. */
    private static void glow(Tessellator t, EmpMode mode, float fill, boolean streaming, float chargeUp, long ms) {
        float end = fillEnd(fill);
        int n = (int) Math.ceil((end - CAP_X0) / BAND - 1e-3);
        for (int i = 0; i < n; i++) {
            float x0 = CAP_X0 + i * BAND, x1 = Math.min(end, x0 + BAND);
            float k;
            if (streaming) {
                k = 0.8F + 0.35F * (float) Math.sin(x0 * 2.4 + ms * 0.012);      // bands running back: energy coming in
            } else if (chargeUp >= 0F) {
                k = 0.85F + chargeUp * 0.25F + 0.2F * (float) Math.sin(ms * (0.01 + 0.035 * chargeUp));
            } else {
                k = 0.88F + 0.08F * (float) Math.sin(ms * 0.003 + x0);
            }
            cylX(t, x0, x1, CAP_Y, 0F, CAP_R - 0.01F, CAP_R - 0.01F, 20, i == 0 || x1 >= end, bright(mode.rgb(), k));
        }
        if (fill > 0F) {
            float k = chargeUp >= 0F ? 0.8F + 0.4F * chargeUp : streaming ? 1F : 0.85F;
            cylX(t, 10.2F, 11.6F, NEEDLE_Y, 0F, 0.18F, 0.05F, 8, true, bright(mode.rgb(), k));
        }
        // three little lights on each side; they blink while the gun works
        float[][] leds = {{3.4F, -0.1F, 0.95F}, {4.6F, -0.3F, 0.9F}, {4.0F, 0.1F, 0.98F}};
        boolean working = streaming || chargeUp >= 0F;
        for (int i = 0; i < leds.length; i++) {
            boolean on = fill > 0F && (!working || (ms / 120L + i) % 3 != 0);
            float[] l = leds[i];
            for (int side = -1; side <= 1; side += 2) {
                float z = l[2] * side;
                box(t, l[0] - 0.12F, l[1] - 0.12F, z - 0.12F, l[0] + 0.12F, l[1] + 0.12F, z + 0.12F, on ? LED : LED_OFF);
            }
        }
    }

    // ------------------------------------------------------------------ primitives

    private static void color(Tessellator t, int rgb) {
        t.setColorOpaque((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }

    /** RGB times k; above 1 it goes towards white. */
    private static int bright(int rgb, float k) {
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float over = Math.max(0F, k - 1F), m = Math.min(k, 1F);
        r = Math.max(0F, Math.min(1F, r * m + over));
        g = Math.max(0F, Math.min(1F, g * m + over));
        b = Math.max(0F, Math.min(1F, b * m + over));
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** An axis-aligned box (corners in any order). */
    private static void box(Tessellator t, float xa, float ya, float za, float xb, float yb, float zb, int rgb) {
        float x0 = Math.min(xa, xb), x1 = Math.max(xa, xb), y0 = Math.min(ya, yb), y1 = Math.max(ya, yb);
        float z0 = Math.min(za, zb), z1 = Math.max(za, zb);
        color(t, rgb);
        t.setNormal(0F, 1F, 0F);
        t.addVertex(x0, y1, z0);
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        t.setNormal(0F, -1F, 0F);
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x0, y0, z1);
        t.setNormal(0F, 0F, -1F);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z0);
        t.setNormal(0F, 0F, 1F);
        t.addVertex(x0, y0, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z1);
        t.setNormal(-1F, 0F, 0F);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y1, z0);
        t.setNormal(1F, 0F, 0F);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y0, z1);
    }

    /** A cylinder along x from x0 (radius r0) to x1 (radius r1 - a cone when different), axis at (cy, cz); n sides. */
    private static void cylX(Tessellator t, float x0, float x1, float cy, float cz, float r0, float r1, int n, boolean caps, int rgb) {
        color(t, rgb);
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            t.setNormal(0F, c0, s0);
            t.addVertex(x0, cy + r0 * c0, cz + r0 * s0);
            t.addVertex(x1, cy + r1 * c0, cz + r1 * s0);
            t.setNormal(0F, c1, s1);
            t.addVertex(x1, cy + r1 * c1, cz + r1 * s1);
            t.addVertex(x0, cy + r0 * c1, cz + r0 * s1);
            if (caps) {                                     // the ends as degenerate quads (a triangle each)
                t.setNormal(-1F, 0F, 0F);
                t.addVertex(x0, cy, cz);
                t.addVertex(x0, cy + r0 * c1, cz + r0 * s1);
                t.addVertex(x0, cy + r0 * c0, cz + r0 * s0);
                t.addVertex(x0, cy, cz);
                t.setNormal(1F, 0F, 0F);
                t.addVertex(x1, cy, cz);
                t.addVertex(x1, cy + r1 * c0, cz + r1 * s0);
                t.addVertex(x1, cy + r1 * c1, cz + r1 * s1);
                t.addVertex(x1, cy, cz);
            }
        }
    }

    // ------------------------------------------------------------------ the hand poses (pure maths, built once)

    /**
     * Forge's flat-item chain inside the hand (ForgeHooksClient.renderEquippedItem without EQUIPPED_BLOCK):
     * q -> (0, -0.3, 0) + 1.5 * Ry(50) Rz(335) (q + (-0.9375, -0.0625, 0)). Returns {rotation (3x3 row-major), offset};
     * the 1.5 is left to the callers' k.
     */
    private static double[][] forgeChain() {
        double[] r = mul(rotY(50), rotZ(335));
        double[] off = add(new double[]{0, -0.3, 0}, scale(apply(r, new double[]{-0.9375, -0.0625, 0}), 1.5));
        return new double[][]{r, off};
    }

    /** ItemRenderer.renderItemInFirstPerson at rest, after its first translate: Ry(45), x0.4, then Forge's chain. */
    private static FloatBuffer firstPersonPose() {
        double[][] f = forgeChain();
        double[] c = mul(rotY(45), f[0]);
        double[] c0 = scale(apply(rotY(45), f[1]), 0.4);
        return gunPose(c, 0.4 * 1.5, c0, basis(FP_BARREL, FP_UP), FP_SCALE, FP_AT);
    }

    /**
     * RenderPlayer.renderEquippedItems' flat-item branch after the arm's (-0.0625, 0.4375, 0.0625):
     * (0.25, 0.1875, -0.1875), x0.375, Rz(60) Rx(-90) Rz(20), then Forge's chain.
     */
    private static FloatBuffer thirdPersonPose() {
        double[][] f = forgeChain();
        double[] a = mul(mul(rotZ(60), rotX(-90)), rotZ(20));
        double[] c = mul(a, f[0]);
        double[] c0 = add(new double[]{0.25, 0.1875, -0.1875}, scale(apply(a, f[1]), 0.375));
        double[] tilt = rotX(ARM_TILT);
        double[] d = basis(apply(tilt, new double[]{0, 0, -1}), apply(tilt, new double[]{0, -1, 0}));
        double[] at = add(TP_HAND, scale(apply(d, TP_GRIP), -TP_SCALE));
        return gunPose(c, 0.375 * 1.5, c0, d, TP_SCALE, at);
    }

    /**
     * The local transform that, under a vanilla chain q -> c0 + k C q, puts gun point g at `at` + s D g:
     * q = C^T (at - c0) / k + (s / k) C^T D g. As a column-major 4x4 for glMultMatrix.
     */
    private static FloatBuffer gunPose(double[] c, double k, double[] c0, double[] d, double s, double[] at) {
        double[] ct = transpose(c);
        double[] m = mul(ct, d);
        double[] tr = scale(apply(ct, add(at, scale(c0, -1))), 1 / k);
        FloatBuffer b = BufferUtils.createFloatBuffer(16);
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                b.put((float) (m[row * 3 + col] * s / k));
            }
            b.put(0F);
        }
        b.put((float) tr[0]).put((float) tr[1]).put((float) tr[2]).put(1F);
        b.flip();
        return b;
    }

    /** Row-major 3x3 whose columns are: the barrel (x), up made square to it (y), their cross (z). */
    private static double[] basis(double[] barrel, double[] upHint) {
        double[] x = norm(barrel);
        double[] y = norm(add(upHint, scale(x, -dot(x, upHint))));
        double[] z = {x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]};
        return new double[]{x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2]};
    }

    private static double[] rotX(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static double[] rotY(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static double[] rotZ(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    private static double[] transpose(double[] a) {
        return new double[]{a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
    }

    private static double[] apply(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] scale(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return scale(a, 1 / l);
    }
}
