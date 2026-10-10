package com.sc.emp.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpSC;
import com.sc.emp.ItemEmpGunSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * ПЭ-1 effects on the client, from the server's EmpNetSC.FX queue (drained here, on the client tick):
 * <ul>
 * <li>FX_POINT / FX_PULSE: a crackling beam from the shooter's muzzle to the hit (a thin blue one / a thick violet
 * one), a burst of sparks where it ends;</li>
 * <li>FX_STREAM_ON / OFF: the Siphon's stream - green motes flowing from the target to the shooter and a faint wavy
 * thread between them, until OFF, or 2 s without a renewal (the shooter still holding the Siphon keeps it going);</li>
 * <li>FX_ABSORBED: a pale beam that stops at an EMP screen's border, a ring flashing out there;</li>
 * <li>FX_SUPPRESSED: a burst of blue sparks on the target; while any of its pieces stays suppressed it keeps sparking
 * now and then (checked for every player near);</li>
 * <li>FX_REFLECTED: the Pulse goes back from the target («Страж») to the shooter, golden sparks on the target.</li>
 * </ul>
 * The suit's lights themselves (dimmed while drained, out while suppressed) are ModelArmorGlowSC's.
 */
@SideOnly(Side.CLIENT)
public final class EmpFxSC {

    /** Beam life, ticks: Point, Pulse (also the reflected one), absorbed. */
    private static final int BEAM_POINT = 6, BEAM_PULSE = 9, BEAM_ABSORBED = 6;
    /** A stream not renewed for this long (and its shooter no longer holding the Siphon) ends. */
    private static final int STREAM_TIMEOUT = 40;
    /** Suppressed players sparking: how near, how often (1 in N ticks at full particles). */
    private static final double SPARK_RANGE = 32;
    private static final int SPARK_EVERY = 5;
    private static final int RING_TICKS = 8;
    private static final int ABSORBED_RGB = 0xC8F0FF, REFLECT_SPARK_RGB = 0xFFD060, SPARK_RGB = 0x9FE6FF;

    /** The beams being drawn. */
    private static final List<Beam> BEAMS = new ArrayList<Beam>();
    /** Siphon streams by shooter entity id. */
    private static final Map<Integer, Stream> STREAMS = new HashMap<Integer, Stream>();
    /** EMP-screen flashes: {x, y, z, born}. */
    private static final List<double[]> RINGS = new ArrayList<double[]>();
    private static final Random RAND = new Random();
    private static World lastWorld;
    /** Client ticks (the effects' clock - the world's clock stops in a paused single player). */
    private static long tick;

    private EmpFxSC() {
    }

    public static void register() {
        Handler h = new Handler();
        MinecraftForge.EVENT_BUS.register(h);
        FMLCommonHandler.instance().bus().register(h);
    }

    /** A Siphon stream from this shooter is shown now (RenderEmpGunSC animates the capsule by it). */
    public static boolean streamingFrom(int shooterId) {
        return STREAMS.containsKey(shooterId);
    }

    private static final class Beam {
        final int from, fromEntityTarget;
        final double x, y, z;
        final int rgb, life;
        final float width;
        final long born;

        /** From entity `from`'s muzzle (or, fromEntityTarget >= 0, from that entity's chest) to (x, y, z). */
        Beam(int from, int fromEntityTarget, double x, double y, double z, int rgb, float width, int life) {
            this.from = from;
            this.fromEntityTarget = fromEntityTarget;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
            this.width = width;
            this.life = life;
            this.born = tick;
        }
    }

    private static final class Stream {
        final int target;
        long until;

        Stream(int target, long until) {
            this.target = target;
            this.until = until;
        }
    }

    // ------------------------------------------------------------------ the queue

    private static void handle(Minecraft mc, EmpNetSC.FxMessage fx) {
        World w = mc.theWorld;
        switch (fx.kind) {
            case EmpNetSC.FX_POINT:
            case EmpNetSC.FX_PULSE: {
                boolean pulse = fx.kind == EmpNetSC.FX_PULSE;
                int rgb = (pulse ? EmpMode.PULSE : EmpMode.POINT).rgb();
                BEAMS.add(new Beam(fx.shooterId, -1, fx.x, fx.y, fx.z, rgb, pulse ? 0.16F : 0.07F, pulse ? BEAM_PULSE : BEAM_POINT));
                burst(mc, fx.x, fx.y, fx.z, rgb, pulse ? 16 : 8, pulse ? 0.25 : 0.15);
                break;
            }
            case EmpNetSC.FX_STREAM_ON: {
                Stream s = STREAMS.get(fx.shooterId);
                if (s != null && s.target == fx.targetId) {
                    s.until = tick + STREAM_TIMEOUT;
                } else {
                    STREAMS.put(fx.shooterId, new Stream(fx.targetId, tick + STREAM_TIMEOUT));
                }
                break;
            }
            case EmpNetSC.FX_STREAM_OFF:
                STREAMS.remove(fx.shooterId);
                break;
            case EmpNetSC.FX_ABSORBED:
                BEAMS.add(new Beam(fx.shooterId, -1, fx.x, fx.y, fx.z, ABSORBED_RGB, 0.07F, BEAM_ABSORBED));
                RINGS.add(new double[]{fx.x, fx.y, fx.z, tick});
                burst(mc, fx.x, fx.y, fx.z, ABSORBED_RGB, 12, 0.2);
                break;
            case EmpNetSC.FX_SUPPRESSED: {
                Entity t = w.getEntityByID(fx.targetId);
                if (t != null) {
                    for (int i = 0; i < 3; i++) {
                        burst(mc, t.posX, t.boundingBox.minY + t.height * (0.25 + 0.3 * i), t.posZ, SPARK_RGB, 5, 0.18);
                    }
                }
                break;
            }
            case EmpNetSC.FX_REFLECTED: {
                Entity t = w.getEntityByID(fx.targetId);
                if (t != null) {
                    // the Pulse goes back: from the reflecting target to the shooter
                    Entity s = w.getEntityByID(fx.shooterId);
                    double[] end = s != null ? chest(s, 1F) : new double[]{fx.x, fx.y, fx.z};
                    BEAMS.add(new Beam(-1, fx.targetId, end[0], end[1], end[2], EmpMode.PULSE.rgb(), 0.16F, BEAM_PULSE));
                    double[] c = chest(t, 1F);
                    burst(mc, c[0], c[1], c[2], REFLECT_SPARK_RGB, 14, 0.25);
                }
                break;
            }
            default:
                break;
        }
    }

    /** Sparks flying out of a point. */
    private static void burst(Minecraft mc, double x, double y, double z, int rgb, int n, double speed) {
        n = scaled(mc, n);
        for (int i = 0; i < n; i++) {
            double vx = (RAND.nextDouble() - 0.5) * 2 * speed, vy = (RAND.nextDouble() - 0.3) * 2 * speed;
            double vz = (RAND.nextDouble() - 0.5) * 2 * speed;
            spawn(mc, new Mote(mc.theWorld, x, y, z, vx, vy, vz, rgb, 6 + RAND.nextInt(6), true, 0.04F));
        }
    }

    /** Fewer particles with the «Частицы» setting lower. */
    private static int scaled(Minecraft mc, int n) {
        int ps = mc.gameSettings.particleSetting;            // 0 all, 1 decreased, 2 minimal
        return ps == 0 ? n : ps == 1 ? Math.max(1, n / 2) : Math.max(1, n / 5);
    }

    private static void spawn(Minecraft mc, EntityFX fx) {
        if (mc.effectRenderer != null) {
            mc.effectRenderer.addEffect(fx);
        }
    }

    // ------------------------------------------------------------------ where things are

    /** An entity's interpolated position (feet). */
    private static double[] feet(Entity e, float pt) {
        double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
        double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - (e.posY - e.boundingBox.minY);
        double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
        return new double[]{x, y, z};
    }

    /** The middle of the body. */
    private static double[] chest(Entity e, float pt) {
        double[] f = feet(e, pt);
        f[1] += e.height * 0.6;
        return f;
    }

    /**
     * The ПЭ-1's needle for a shooter: ahead of the eyes, to the right and down (where the gun is seen held; in first
     * person a little nearer the screen's right-hand lower part).
     */
    private static double[] muzzle(Minecraft mc, Entity e, float pt) {
        double[] f = feet(e, pt);
        double eye = e instanceof EntityPlayer ? 1.62 : e.height * 0.85;
        float yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
        float pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * pt;
        if (e instanceof EntityLivingBase) {
            EntityLivingBase l = (EntityLivingBase) e;
            yaw = l.prevRotationYawHead + (l.rotationYawHead - l.prevRotationYawHead) * pt;
        }
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        double lx = -Math.sin(ry) * Math.cos(rp), ly = -Math.sin(rp), lz = Math.cos(ry) * Math.cos(rp);
        double rx = -Math.cos(ry), rz = -Math.sin(ry);            // the shooter's right
        boolean own = e == mc.thePlayer && mc.gameSettings.thirdPersonView == 0;
        double ahead = own ? 0.7 : 0.75, side = own ? 0.22 : 0.35, down = own ? 0.18 : 0.35;
        return new double[]{f[0] + lx * ahead + rx * side, f[1] + eye + ly * ahead - down, f[2] + lz * ahead + rz * side};
    }

    // ------------------------------------------------------------------ the tick

    private static void clientTick(Minecraft mc) {
        World w = mc.theWorld;
        if (w != lastWorld) {                                // a new world / left: nothing carries over
            lastWorld = w;
            BEAMS.clear();
            STREAMS.clear();
            RINGS.clear();
        }
        if (w == null || mc.thePlayer == null) {
            EmpNetSC.FX.clear();
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        tick++;
        EmpNetSC.FxMessage fx;
        while ((fx = EmpNetSC.FX.poll()) != null) {
            handle(mc, fx);
        }
        for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
            if (tick - it.next().born > 30) {
                it.remove();
            }
        }
        for (Iterator<double[]> it = RINGS.iterator(); it.hasNext(); ) {
            if (tick - it.next()[3] > RING_TICKS) {
                it.remove();
            }
        }
        // streams: motes flowing from the target to the shooter
        for (Iterator<Map.Entry<Integer, Stream>> it = STREAMS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Stream> e = it.next();
            Entity s = w.getEntityByID(e.getKey()), t = w.getEntityByID(e.getValue().target);
            if (s == null || t == null || tick > e.getValue().until && !holdsSiphon(s)) {
                it.remove();
                continue;
            }
            int n = scaled(mc, 2);
            if (mc.gameSettings.particleSetting == 2 && tick % 3 != 0) {
                n = 0;
            }
            double[] from = chest(t, 1F), to = muzzle(mc, s, 1F);
            for (int i = 0; i < n; i++) {
                double f = RAND.nextDouble() * 0.3;
                double x = from[0] + (to[0] - from[0]) * f + (RAND.nextDouble() - 0.5) * 0.3;
                double y = from[1] + (to[1] - from[1]) * f + (RAND.nextDouble() - 0.5) * 0.3;
                double z = from[2] + (to[2] - from[2]) * f + (RAND.nextDouble() - 0.5) * 0.3;
                double dx = to[0] - x, dy = to[1] - y, dz = to[2] - z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double speed = 0.45;
                int life = (int) Math.max(2, Math.min(40, d / speed));
                spawn(mc, new Mote(w, x, y, z, dx / d * speed, dy / d * speed, dz / d * speed, EmpMode.SIPHON.rgb(), life, false, 0F));
            }
        }
        // suppressed players spark now and then (at a suppressed piece's height)
        int every = mc.gameSettings.particleSetting == 0 ? SPARK_EVERY : SPARK_EVERY * 3;
        for (Object o : w.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (pl.getDistanceSqToEntity(mc.thePlayer) > SPARK_RANGE * SPARK_RANGE || RAND.nextInt(every) != 0
                    || !EmpSC.anySuppressed(pl)) {
                continue;
            }
            if (pl == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) {
                continue;                                    // not in our own face
            }
            int piece;
            int tries = 0;
            do {
                piece = RAND.nextInt(4);
            } while (!EmpSC.isSuppressed(pl, piece) && ++tries < 8);
            double h = piece == 0 ? 0.88 : piece == 1 ? 0.62 : piece == 2 ? 0.35 : 0.08;
            double[] f = feet(pl, 1F);
            double a = RAND.nextDouble() * Math.PI * 2;
            burst(mc, f[0] + Math.cos(a) * 0.32, f[1] + pl.height * h, f[2] + Math.sin(a) * 0.32, SPARK_RGB, 3, 0.12);
        }
    }

    /** Still holding the gun in Siphon mode with the button down (another player's use is synced to us). */
    private static boolean holdsSiphon(Entity e) {
        if (!(e instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer p = (EntityPlayer) e;
        return p.isUsingItem() && ItemEmpGunSC.held(p) != null && ItemEmpGunSC.modeOf(ItemEmpGunSC.held(p)) == EmpMode.SIPHON;
    }

    // ------------------------------------------------------------------ drawing

    private static void render(Minecraft mc, float pt) {
        if (BEAMS.isEmpty() && STREAMS.isEmpty() && RINGS.isEmpty() || mc.theWorld == null) {
            return;
        }
        Entity cam = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        double cx = cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * pt;
        double cy = cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * pt;
        double cz = cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * pt;
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-cx, -cy, -cz);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive: light
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            // the camera in world space (for the ribbons to face it; a player's posY is at the eyes on the client)
            double[] eye = {cx, cy, cz};
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            float now = tick + pt;
            for (Beam b : BEAMS) {
                float age = now - b.born;
                if (age < 0 || age > b.life) {
                    continue;
                }
                double[] start;
                if (b.fromEntityTarget >= 0) {
                    Entity e = mc.theWorld.getEntityByID(b.fromEntityTarget);
                    if (e == null) {
                        continue;
                    }
                    start = chest(e, pt);
                } else {
                    Entity e = mc.theWorld.getEntityByID(b.from);
                    if (e == null) {
                        continue;
                    }
                    start = muzzle(mc, e, pt);
                }
                float fade = 1F - age / b.life;
                long seed = b.born * 7919L + (long) age * 31L;
                crackle(t, eye, start, new double[]{b.x, b.y, b.z}, b.width * 2.6F, b.rgb, 0.35F * fade, seed, 0.25);
                crackle(t, eye, start, new double[]{b.x, b.y, b.z}, b.width, b.rgb, 0.9F * fade, seed, 0.25);
                crackle(t, eye, start, new double[]{b.x, b.y, b.z}, b.width * 0.35F, 0xFFFFFF, 0.8F * fade, seed, 0.25);
            }
            for (Map.Entry<Integer, Stream> e : STREAMS.entrySet()) {
                Entity s = mc.theWorld.getEntityByID(e.getKey()), tg = mc.theWorld.getEntityByID(e.getValue().target);
                if (s == null || tg == null) {
                    continue;
                }
                wave(t, eye, chest(tg, pt), muzzle(mc, s, pt), now);
            }
            for (double[] r : RINGS) {
                float age = now - (float) r[3];
                if (age >= 0 && age <= RING_TICKS) {
                    ring(t, eye, r, 0.25 + age * 0.14, ABSORBED_RGB, 0.8F * (1F - age / RING_TICKS));
                }
            }
            t.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A jagged ribbon from a to b, `jag` blocks off the line at its joints (fixed by the seed). */
    private static void crackle(Tessellator t, double[] eye, double[] a, double[] b, float width, int rgb, float alpha, long seed,
                                double jag) {
        Random r = new Random(seed);
        double len = dist(a, b);
        int n = Math.max(2, Math.min(24, (int) (len / 1.2)));
        double[] prev = a;
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            double[] p = {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
            if (i < n) {
                p[0] += (r.nextDouble() - 0.5) * 2 * jag;
                p[1] += (r.nextDouble() - 0.5) * 2 * jag;
                p[2] += (r.nextDouble() - 0.5) * 2 * jag;
            }
            ribbon(t, eye, prev, p, width, rgb, alpha);
            prev = p;
        }
    }

    /** The stream's thread: a sine wave running from the target to the shooter. */
    private static void wave(Tessellator t, double[] eye, double[] a, double[] b, float now) {
        double len = dist(a, b);
        int n = Math.max(4, Math.min(40, (int) (len * 2.5)));
        double[] prev = a;
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            double amp = 0.12 * Math.sin(Math.PI * f);
            double ph = f * len * 3.0 - now * 0.6;
            double[] p = {a[0] + (b[0] - a[0]) * f + amp * Math.cos(ph), a[1] + (b[1] - a[1]) * f + amp * Math.sin(ph),
                    a[2] + (b[2] - a[2]) * f + amp * Math.sin(ph + 1.3)};
            ribbon(t, eye, prev, p, 0.05F, EmpMode.SIPHON.rgb(), 0.45F);
            prev = p;
        }
    }

    /** A flat hexagonal ring facing the camera. */
    private static void ring(Tessellator t, double[] eye, double[] c, double radius, int rgb, float alpha) {
        double[] v = norm(sub(c, eye));
        double[] u = norm(cross(v, Math.abs(v[1]) > 0.9 ? new double[]{1, 0, 0} : new double[]{0, 1, 0}));
        double[] w = cross(v, u);
        double[] prev = null;
        for (int i = 0; i <= 6; i++) {
            double a = Math.PI * 2 * i / 6;
            double[] p = {c[0] + (u[0] * Math.cos(a) + w[0] * Math.sin(a)) * radius,
                    c[1] + (u[1] * Math.cos(a) + w[1] * Math.sin(a)) * radius,
                    c[2] + (u[2] * Math.cos(a) + w[2] * Math.sin(a)) * radius};
            if (prev != null) {
                ribbon(t, eye, prev, p, 0.06F, rgb, alpha);
            }
            prev = p;
        }
    }

    /** One straight piece of a camera-facing ribbon. */
    private static void ribbon(Tessellator t, double[] eye, double[] a, double[] b, float width, int rgb, float alpha) {
        double[] d = sub(b, a);
        double[] mid = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
        double[] side = cross(d, sub(mid, eye));
        double l = Math.sqrt(side[0] * side[0] + side[1] * side[1] + side[2] * side[2]);
        if (l < 1e-6) {
            return;
        }
        double k = width / 2 / l;
        t.setColorRGBA_I(rgb, (int) (Math.max(0F, Math.min(1F, alpha)) * 255));
        t.addVertex(a[0] - side[0] * k, a[1] - side[1] * k, a[2] - side[2] * k);
        t.addVertex(a[0] + side[0] * k, a[1] + side[1] * k, a[2] + side[2] * k);
        t.addVertex(b[0] + side[0] * k, b[1] + side[1] * k, b[2] + side[2] * k);
        t.addVertex(b[0] - side[0] * k, b[1] - side[1] * k, b[2] - side[2] * k);
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dist(double[] a, double[] b) {
        double[] d = sub(a, b);
        return Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-9 ? new double[]{0, 1, 0} : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    // ------------------------------------------------------------------ particles

    /** A full-bright coloured mote (the stream) or spark (bursts: the firework spark frames, falling a little, fading). */
    static final class Mote extends EntityFX {
        private final boolean spark;

        Mote(World w, double x, double y, double z, double vx, double vy, double vz, int rgb, int life, boolean spark, float gravity) {
            super(w, x, y, z, 0, 0, 0);
            motionX = vx;
            motionY = vy;
            motionZ = vz;
            this.spark = spark;
            particleGravity = gravity;
            particleMaxAge = life;
            noClip = true;
            float k = 0.85F + rand.nextFloat() * 0.15F;
            setRBGColorF(((rgb >> 16) & 255) / 255F * k, ((rgb >> 8) & 255) / 255F * k, (rgb & 255) / 255F * k);
            particleScale = spark ? 0.45F + rand.nextFloat() * 0.3F : 0.55F + rand.nextFloat() * 0.35F;
            setParticleTextureIndex(spark ? 160 : rand.nextInt(8));
        }

        @Override
        public void onUpdate() {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
            if (particleAge++ >= particleMaxAge) {
                setDead();
                return;
            }
            float f = particleAge / (float) particleMaxAge;
            motionY -= 0.04 * particleGravity;
            moveEntity(motionX, motionY, motionZ);
            if (spark) {
                motionX *= 0.82;
                motionY *= 0.82;
                motionZ *= 0.82;
                setParticleTextureIndex(160 + Math.min(7, (int) (f * 8)));
                particleAlpha = 1F - f;
            } else {
                particleAlpha = Math.min(1F, 2.5F * (1F - f));
            }
        }

        @Override
        public int getBrightnessForRender(float partial) {
            return 0xF000F0;
        }

        @Override
        public float getBrightness(float partial) {
            return 1F;
        }
    }

    /** The event handler (Forge bus: drawing; FML bus: the client tick). */
    public static final class Handler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) {
                clientTick(Minecraft.getMinecraft());
            }
        }

        @SubscribeEvent
        public void onWorldRender(RenderWorldLastEvent e) {
            render(Minecraft.getMinecraft(), e.partialTicks);
        }
    }
}
