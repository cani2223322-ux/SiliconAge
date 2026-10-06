package com.sc.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import com.sc.energy.CableType;
import com.sc.energy.Tier;
import com.sc.handler.NetViewNetSC;
import com.sc.manual.Lang;
import com.sc.util.NetViewScanSC;
import com.sc.util.NetViewScanSC.Snapshot;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * The wrench's network overview: a hologram over every cable of the network (green / yellow / red
 * by load, pulsing red past the rating or overvolted), boxes on the blocks plugged into it (sources
 * green, consumers blue, storages purple, transformers orange, other mods' grey), EU/t labels on
 * those within LABEL_RANGE, and a HUD panel with the totals. Through walls at a lower alpha.
 * The still part is two display lists (through walls / in front), built once per snapshot and
 * again when the player has moved far enough for the CULL_RANGE window to shift.
 * Registered on the Forge bus in ClientProxy.
 */
public final class NetViewRendererSC {

    public static final NetViewRendererSC INSTANCE = new NetViewRendererSC();

    private static final int[] LOAD_RGB = {0x3CCB6E, 0xFFD23C, 0xFF3C3C, 0xFF2020};
    private static final int[] KIND_RGB = {0x40E070, 0x4A90FF, 0xB060FF, 0xFF9A30, 0xB0B0B0};
    private static final int REBUILD_STEP = 16;
    private static final int MAX_LABELS = 48;

    private Snapshot view;
    private long shownAt;
    private Object world;
    private int[] level;
    private final List<Integer> pulsing = new ArrayList<Integer>();
    private Set<Long> cableSet;

    private int lists = -1;
    private boolean dirty;
    private int builtX, builtY, builtZ;

    private NetViewRendererSC() {
    }

    // ------------------------------------------------------------------ state

    /** Takes the snapshot the network handed over; hides when the time, the distance or the world says so. */
    private void poll(Minecraft mc) {
        Snapshot s = NetViewNetSC.received;
        if (s != null) {
            NetViewNetSC.received = null;
            take(s, mc);
        }
        if (view == null) {
            return;
        }
        boolean gone = mc.theWorld == null || mc.thePlayer == null || mc.theWorld != world
                || mc.thePlayer.dimension != view.dim
                || System.currentTimeMillis() - shownAt > NetViewScanSC.LIFE_TICKS * 50L;
        if (!gone) {
            double dx = mc.thePlayer.posX - view.ox - 0.5, dy = mc.thePlayer.posY - view.oy - 0.5, dz = mc.thePlayer.posZ - view.oz - 0.5;
            gone = dx * dx + dy * dy + dz * dz > (double) NetViewScanSC.HIDE_RANGE * NetViewScanSC.HIDE_RANGE;
        }
        if (gone) {
            hide();
        }
    }

    /** A click on the network already shown hides it; anything else replaces it. */
    private void take(Snapshot s, Minecraft mc) {
        boolean same = view != null && view.dim == s.dim && cableSet.contains(NetViewScanSC.pack(s.ox, s.oy, s.oz));
        if (same || (s.flags & NetViewScanSC.S_PING) != 0) {
            if (same) {
                hide();
            }
            return;
        }
        view = s;
        shownAt = System.currentTimeMillis();
        world = mc.theWorld;
        level = new int[s.n];
        pulsing.clear();
        cableSet = new HashSet<Long>();
        for (int i = 0; i < s.n; i++) {
            level[i] = NetViewScanSC.level(s.cflow[i], CableType.values()[clampType(s.ctype[i])].maxThroughput(), s.cflags[i]);
            if (level[i] == NetViewScanSC.L_OVER) {
                pulsing.add(i);
            }
            cableSet.add(NetViewScanSC.pack(s.cx[i], s.cy[i], s.cz[i]));
        }
        dirty = true;
    }

    private void hide() {
        view = null;
        cableSet = null;
        level = null;
        pulsing.clear();
        world = null;
        if (lists >= 0) {
            GLAllocation.deleteDisplayLists(lists);
            lists = -1;
        }
    }

    private static int clampType(int t) {
        return Math.max(0, Math.min(CableType.values().length - 1, t));
    }

    // ------------------------------------------------------------------ world

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        poll(mc);
        EntityLivingBase v = mc.renderViewEntity;
        if (view == null || v == null) {
            return;
        }
        double px = v.lastTickPosX + (v.posX - v.lastTickPosX) * e.partialTicks;
        double py = v.lastTickPosY + (v.posY - v.lastTickPosY) * e.partialTicks;
        double pz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * e.partialTicks;
        int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
        if (dirty || lists < 0 || Math.abs(bx - builtX) > REBUILD_STEP || Math.abs(by - builtY) > REBUILD_STEP
                || Math.abs(bz - builtZ) > REBUILD_STEP) {
            build(bx, by, bz);
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);              // the faint pass is below the default 0.1 cut
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2F);
        GL11.glPushMatrix();
        GL11.glTranslated(view.ox - px, view.oy - py, view.oz - pz);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glCallList(lists);                         // through walls, faint
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glCallList(lists + 1);                     // in front, full
        if (!pulsing.isEmpty()) {
            float a = 0.35F + 0.3F * (float) Math.sin(System.currentTimeMillis() / 140.0);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            int drawn = 0;
            for (int i : pulsing) {
                if (drawn++ > 1024 || far(view.cx[i], view.cy[i], view.cz[i], bx, by, bz)) {
                    continue;
                }
                t.setColorRGBA_I(LOAD_RGB[NetViewScanSC.L_OVER], (int) (a * 255));
                box(t, view.cx[i] - view.ox, view.cy[i] - view.oy, view.cz[i] - view.oz, 0.22, 0.78);
            }
            t.draw();
        }
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        labels(mc, px, py, pz);
        GL11.glPopAttrib();
        GL11.glDepthMask(true);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static boolean far(int x, int y, int z, int bx, int by, int bz) {
        int r = NetViewScanSC.CULL_RANGE;
        return Math.abs(x - bx) > r || Math.abs(y - by) > r || Math.abs(z - bz) > r;
    }

    /** The two still lists: cores and their links coloured by load, endpoint boxes coloured by kind. */
    private void build(int bx, int by, int bz) {
        if (lists < 0) {
            lists = GLAllocation.generateDisplayLists(2);
        }
        builtX = bx;
        builtY = by;
        builtZ = bz;
        dirty = false;
        float[] alpha = {0.16F, 0.42F};
        Tessellator t = Tessellator.instance;
        for (int pass = 0; pass < 2; pass++) {
            GL11.glNewList(lists + pass, GL11.GL_COMPILE);
            t.startDrawingQuads();
            int ca = (int) (alpha[pass] * 255), ea = (int) (alpha[pass] * 0.55F * 255);
            for (int i = 0; i < view.n; i++) {
                int x = view.cx[i], y = view.cy[i], z = view.cz[i];
                if (far(x, y, z, bx, by, bz)) {
                    continue;
                }
                double rx = x - view.ox, ry = y - view.oy, rz = z - view.oz;
                t.setColorRGBA_I(LOAD_RGB[level[i]], ca);
                if (level[i] != NetViewScanSC.L_OVER) {              // those pulse, drawn each frame
                    box(t, rx, ry, rz, 0.3, 0.7);
                }
                // a bar to each linked cable in +x / +y / +z (each link once)
                if (cableSet.contains(NetViewScanSC.pack(x + 1, y, z))) {
                    bar(t, rx, ry, rz, 0);
                }
                if (cableSet.contains(NetViewScanSC.pack(x, y + 1, z))) {
                    bar(t, rx, ry, rz, 1);
                }
                if (cableSet.contains(NetViewScanSC.pack(x, y, z + 1))) {
                    bar(t, rx, ry, rz, 2);
                }
            }
            for (int e = 0; e < view.m; e++) {
                int x = view.ex[e], y = view.ey[e], z = view.ez[e];
                if (far(x, y, z, bx, by, bz)) {
                    continue;
                }
                t.setColorRGBA_I(KIND_RGB[Math.max(0, Math.min(KIND_RGB.length - 1, view.ekind[e]))], ea);
                box(t, x - view.ox, y - view.oy, z - view.oz, 0.03, 0.97);
            }
            t.draw();
            // endpoint outlines
            t.startDrawing(GL11.GL_LINES);
            int la = pass == 0 ? 110 : 230;
            for (int e = 0; e < view.m; e++) {
                int x = view.ex[e], y = view.ey[e], z = view.ez[e];
                if (far(x, y, z, bx, by, bz)) {
                    continue;
                }
                t.setColorRGBA_I(KIND_RGB[Math.max(0, Math.min(KIND_RGB.length - 1, view.ekind[e]))], la);
                outline(t, x - view.ox, y - view.oy, z - view.oz, 0.02, 0.98);
            }
            t.draw();
            GL11.glEndList();
        }
    }

    /** Bar from this core's face to the next core along axis 0 = x, 1 = y, 2 = z. */
    private static void bar(Tessellator t, double x, double y, double z, int axis) {
        double lo = 0.36, hi = 0.64;
        double x0 = x + (axis == 0 ? 0.7 : lo), x1 = x + (axis == 0 ? 1.3 : hi);
        double y0 = y + (axis == 1 ? 0.7 : lo), y1 = y + (axis == 1 ? 1.3 : hi);
        double z0 = z + (axis == 2 ? 0.7 : lo), z1 = z + (axis == 2 ? 1.3 : hi);
        quads(t, x0, y0, z0, x1, y1, z1);
    }

    private static void box(Tessellator t, double x, double y, double z, double lo, double hi) {
        quads(t, x + lo, y + lo, z + lo, x + hi, y + hi, z + hi);
    }

    private static void quads(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
        t.addVertex(x0, y0, z0); t.addVertex(x1, y0, z0); t.addVertex(x1, y0, z1); t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z0); t.addVertex(x0, y1, z1); t.addVertex(x1, y1, z1); t.addVertex(x1, y1, z0);
        t.addVertex(x0, y0, z0); t.addVertex(x0, y1, z0); t.addVertex(x1, y1, z0); t.addVertex(x1, y0, z0);
        t.addVertex(x0, y0, z1); t.addVertex(x1, y0, z1); t.addVertex(x1, y1, z1); t.addVertex(x0, y1, z1);
        t.addVertex(x0, y0, z0); t.addVertex(x0, y0, z1); t.addVertex(x0, y1, z1); t.addVertex(x0, y1, z0);
        t.addVertex(x1, y0, z0); t.addVertex(x1, y1, z0); t.addVertex(x1, y1, z1); t.addVertex(x1, y0, z1);
    }

    private static void outline(Tessellator t, double x, double y, double z, double lo, double hi) {
        double a = x + lo, b = x + hi, c = y + lo, d = y + hi, f = z + lo, g = z + hi;
        double[][] edges = {
                {a, c, f, b, c, f}, {a, c, g, b, c, g}, {a, d, f, b, d, f}, {a, d, g, b, d, g},
                {a, c, f, a, d, f}, {b, c, f, b, d, f}, {a, c, g, a, d, g}, {b, c, g, b, d, g},
                {a, c, f, a, c, g}, {b, c, f, b, c, g}, {a, d, f, a, d, g}, {b, d, f, b, d, g}};
        for (double[] e : edges) {
            t.addVertex(e[0], e[1], e[2]);
            t.addVertex(e[3], e[4], e[5]);
        }
    }

    /** EU/t over the blocks within LABEL_RANGE, nearest first, through walls. */
    private void labels(Minecraft mc, double px, double py, double pz) {
        FontRenderer fr = mc.fontRenderer;
        int r = NetViewScanSC.LABEL_RANGE, shown = 0;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int e = 0; e < view.m && shown < MAX_LABELS; e++) {
            double dx = view.ex[e] + 0.5 - px, dy = view.ey[e] + 0.5 - py, dz = view.ez[e] + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > r * r) {
                continue;
            }
            String text = label(e);
            if (text == null) {
                continue;
            }
            shown++;
            GL11.glPushMatrix();
            GL11.glTranslated(dx, dy + 0.85, dz);
            GL11.glNormal3f(0F, 1F, 0F);
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0F, 1F, 0F);
            GL11.glRotatef(RenderManager.instance.playerViewX, 1F, 0F, 0F);
            GL11.glScalef(-0.022F, -0.022F, 0.022F);
            int w = fr.getStringWidth(text) / 2;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.setColorRGBA_F(0F, 0F, 0F, 0.5F);
            t.addVertex(-w - 2, -1, 0);
            t.addVertex(-w - 2, 9, 0);
            t.addVertex(w + 2, 9, 0);
            t.addVertex(w + 2, -1, 0);
            t.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            fr.drawString(text, -w, 0, 0xFF000000 | KIND_RGB[Math.max(0, Math.min(KIND_RGB.length - 1, view.ekind[e]))]);
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private String label(int e) {
        String tier = view.etier[e] >= 0 ? Tier.byOrdinal(view.etier[e]).name() + " " : "";
        String rate = NetViewScanSC.compact(view.erate[e]);
        switch (view.ekind[e]) {
            case NetViewScanSC.K_SOURCE:
                return tier + Lang.tr("sc.netview.label.out", rate);
            case NetViewScanSC.K_CONSUMER:
                return tier + Lang.tr("sc.netview.label.in", rate);
            case NetViewScanSC.K_STORAGE:
                return tier + Lang.tr("sc.netview.label.storage", String.valueOf(Math.max(0, view.epct[e])), rate);
            case NetViewScanSC.K_TRANSFORMER:
                return tier + Lang.tr("sc.netview.label.transformer", rate);
            default:
                return tier + Lang.tr("sc.netview.label.foreign", rate);
        }
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        poll(mc);
        if (view == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        Snapshot s = view;
        List<String> lines = new ArrayList<String>();
        List<Integer> colors = new ArrayList<Integer>();
        String mode = Lang.tr((s.flags & NetViewScanSC.S_MEASURED) != 0 ? "sc.netview.hud.measured" : "sc.netview.hud.estimate");
        add(lines, colors, Lang.tr("sc.netview.hud.title", mode), 0x7FD8FF);
        add(lines, colors, Lang.tr("sc.netview.hud.size", String.valueOf(s.n), String.valueOf(s.m)), 0xE0E0E0);
        add(lines, colors, Lang.tr("sc.netview.hud.flow", NetViewScanSC.compact(s.gen), NetViewScanSC.compact(s.cons)), 0xE0E0E0);
        long pct = s.gen > 0 ? Math.round(100.0 * s.loss / s.gen) : 0;
        add(lines, colors, Lang.tr("sc.netview.hud.loss", NetViewScanSC.compact(s.loss), String.valueOf(pct)), 0xE0E0E0);
        CableType cable = CableType.values()[clampType(s.n > 0 ? s.ctype[0] : 0)];
        String src = s.maxSourceTier >= 0 ? Tier.byOrdinal(s.maxSourceTier).name() : Lang.tr("sc.netview.hud.nosource");
        add(lines, colors, Lang.tr("sc.netview.hud.bottlenecks", String.valueOf(s.bottlenecks)),
                s.bottlenecks > 0 ? 0xFF6A5A : 0x7CE08A);
        add(lines, colors, Lang.tr("sc.netview.hud.tiers", cable.tier.name(), NetViewScanSC.compact(cable.maxThroughput()), src),
                s.maxSourceTier > cable.tier.ordinal() ? 0xFF6A5A : 0xC0C0C0);
        if (s.capacity > 0) {
            add(lines, colors, Lang.tr("sc.netview.hud.stored", NetViewScanSC.compact(s.stored), NetViewScanSC.compact(s.capacity),
                    String.valueOf(Math.round(100.0 * s.stored / s.capacity))), 0xC9A0FF);
        }
        if ((s.flags & NetViewScanSC.S_TRUNCATED) != 0) {
            add(lines, colors, Lang.tr("sc.netview.hud.truncated"), 0xFFD23C);
        }
        if ((s.flags & NetViewScanSC.S_HIDDEN) != 0) {
            add(lines, colors, Lang.tr("sc.netview.hud.hidden"), 0xFFD23C);
        }
        long left = Math.max(0, (NetViewScanSC.LIFE_TICKS * 50L - (System.currentTimeMillis() - shownAt) + 999) / 1000);
        add(lines, colors, Lang.tr("sc.netview.hud.close", String.valueOf(left)), 0xA0A0A0);

        FontRenderer fr = mc.fontRenderer;
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, fr.getStringWidth(l));
        }
        int h = lines.size() * 10 + 6, x = 4;
        int y = Math.max(4, Math.min(e.resolution.getScaledHeight() - h - 40, (e.resolution.getScaledHeight() - h) / 2 + 20));
        Gui.drawRect(x, y, x + w + 8, y + h, 0xA0101820);
        Gui.drawRect(x, y, x + 2, y + h, 0xFF7FD8FF);
        for (int i = 0; i < lines.size(); i++) {
            fr.drawStringWithShadow(lines.get(i), x + 5, y + 4 + i * 10, colors.get(i));
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static void add(List<String> lines, List<Integer> colors, String s, int color) {
        lines.add(s);
        colors.add(color);
    }
}
