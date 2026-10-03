package com.sc.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.item.ArmorLogicSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.SingularSenseData;
import com.sc.util.SingularSenseData.Analysis;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * What the Singular helmet shows (stage 2b), from what the server sent (SingularSenseData):
 * Ш1 the scanner's outlines through walls for SCANNER_SHOW after each pulse (chests, spawners, ores,
 * mobs), Ш2 red outlines round the mobs targeting the player and small arrows at the screen's edge
 * pointing to them, Ш5 a small table next to the crosshair. Client only.
 */
public class SingularClientSC {

    /** Ш2 / Ш5 data older than this is dropped (the server sends each second / half second). */
    private static final long THREAT_MAX_AGE = 2500, ANALYSIS_MAX_AGE = 1500;

    private net.minecraft.world.World lastWorld;

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SingularClientSC());
    }

    private boolean newWorld(Minecraft mc) {
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            SingularSenseData.clear();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ through walls

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null || newWorld(mc)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean scan = ArmorLogicSC.active(p, ArmorFeature.GRAV_SCANNER)
                && SingularSenseData.fresh(SingularSenseData.scanAt, now, ArmorFeature.SCANNER_SHOW * 50L);
        boolean threat = ArmorLogicSC.active(p, ArmorFeature.THREAT_SENSE)
                && SingularSenseData.fresh(SingularSenseData.threatAt, now, THREAT_MAX_AGE) && SingularSenseData.threats.length > 0;
        if (!scan && !threat) {
            return;
        }
        float pt = event.partialTicks;
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
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
        if (scan) {
            // fading out over the last second of the five
            float alpha = Math.max(0.25F, Math.min(0.9F, (ArmorFeature.SCANNER_SHOW * 50L - (now - SingularSenseData.scanAt)) / 1000F));
            int[] b = SingularSenseData.scanBlocks;
            for (int i = 0; i + 3 < b.length; i += 4) {
                float[] c = blockColor(b[i + 3]);
                double in = 0.02;
                box(t, AxisAlignedBB.getBoundingBox(b[i] + in, b[i + 1] + in, b[i + 2] + in, b[i] + 1 - in, b[i + 1] + 1 - in, b[i + 2] + 1 - in),
                        c[0], c[1], c[2], alpha);
            }
            int[] m = SingularSenseData.scanMobs;
            for (int i = 0; i + 1 < m.length; i += 2) {
                Entity e = mc.theWorld.getEntityByID(m[i]);
                if (e != null && !e.isDead) {
                    box(t, lerpBox(e, pt), m[i + 1] != 0 ? 1F : 0.3F, m[i + 1] != 0 ? 0.35F : 1F, m[i + 1] != 0 ? 0.2F : 0.45F, alpha);
                }
            }
        }
        if (threat) {
            for (int id : SingularSenseData.threats) {
                Entity e = mc.theWorld.getEntityByID(id);
                if (e != null && !e.isDead) {
                    box(t, lerpBox(e, pt).expand(0.08, 0.08, 0.08), 1F, 0.1F, 0.1F, 0.95F);
                }
            }
        }
        t.draw();
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glLineWidth(1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /** Ш1 colours: chests gold-brown, spawners violet, ores yellow, the mod's ores cyan. */
    private static float[] blockColor(int kind) {
        switch (kind) {
            case SingularSenseData.CHEST: return new float[]{0.95F, 0.65F, 0.2F};
            case SingularSenseData.SPAWNER: return new float[]{0.85F, 0.3F, 1F};
            case SingularSenseData.MOD_ORE: return new float[]{0.25F, 0.9F, 1F};
            default: return new float[]{1F, 0.9F, 0.25F};
        }
    }

    private static AxisAlignedBB lerpBox(Entity e, float pt) {
        double dx = (e.posX - e.lastTickPosX) * pt - (e.posX - e.lastTickPosX);
        double dy = (e.posY - e.lastTickPosY) * pt - (e.posY - e.lastTickPosY);
        double dz = (e.posZ - e.lastTickPosZ) * pt - (e.posZ - e.lastTickPosZ);
        return e.boundingBox.getOffsetBoundingBox(dx, dy, dz);
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

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.theWorld == null
                || mc.currentScreen != null || mc.gameSettings.hideGUI) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        long now = System.currentTimeMillis();
        ScaledResolution sr = event.resolution;
        if (ArmorLogicSC.active(p, ArmorFeature.THREAT_SENSE) && SingularSenseData.fresh(SingularSenseData.threatAt, now, THREAT_MAX_AGE)) {
            arrows(mc, p, sr);
        }
        Analysis a = SingularSenseData.analysis;
        if (a != null && a.kind != Analysis.NONE && ArmorLogicSC.active(p, ArmorFeature.ANALYZER)
                && SingularSenseData.fresh(SingularSenseData.analysisAt, now, ANALYSIS_MAX_AGE)) {
            analysis(mc, a, sr);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Pure: the angle (degrees, -180..180, + to the right) from the look `yaw` to a point (dx, dz) away. */
    public static double relativeAngle(double yaw, double dx, double dz) {
        return MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
    }

    /** Ш2: a red arrow on an ellipse round the crosshair for each threat not straight ahead. */
    private static void arrows(Minecraft mc, EntityPlayer p, ScaledResolution sr) {
        int[] ids = SingularSenseData.threats;
        if (ids.length == 0) {
            return;
        }
        double cx = sr.getScaledWidth() / 2.0, cy = sr.getScaledHeight() / 2.0;
        double rx = cx - 24, ry = cy - 24;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        t.setColorRGBA_F(1F, 0.15F, 0.1F, 0.85F);
        for (int id : ids) {
            Entity e = mc.theWorld.getEntityByID(id);
            if (e == null || e.isDead) {
                continue;
            }
            double rel = relativeAngle(p.rotationYaw, e.posX - p.posX, e.posZ - p.posZ);
            if (Math.abs(rel) < 25) {
                continue;                                       // in front: its outline is enough
            }
            double a = Math.toRadians(rel), sx = Math.sin(a), sy = -Math.cos(a);
            double ax = cx + sx * rx, ay = cy + sy * ry;
            t.addVertex(ax + sx * 7, ay + sy * 7, 0);
            t.addVertex(ax + sy * 4, ay - sx * 4, 0);           // the base, across the direction
            t.addVertex(ax - sy * 4, ay + sx * 4, 0);
        }
        t.draw();                                               // with nothing added it only resets the tessellator
        GL11.glPopAttrib();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Ш5: the table, right of the crosshair. */
    private static void analysis(Minecraft mc, Analysis a, ScaledResolution sr) {
        List<String> lines = new ArrayList<String>();
        if (a.kind == Analysis.MOB) {
            Entity e = mc.theWorld.getEntityByID(a.entityId);
            lines.add("§f" + (e != null ? e.getCommandSenderName() : "?"));
            lines.add("§c" + Lang.tr("sc.analyzer.health", num(a.health), num(a.maxHealth)));
            lines.add("§7" + Lang.tr("sc.analyzer.armor", a.armor));
            if (a.attack >= 0) {
                lines.add("§7" + Lang.tr("sc.analyzer.attack", num(a.attack)));
            }
            String[][] flags = {{"1", "sc.analyzer.weak.water"}, {"2", "sc.analyzer.weak.heat"}, {"4", "sc.analyzer.undead"},
                    {"8", "sc.analyzer.arthropod"}, {"16", "sc.analyzer.fireimmune"}, {"32", "sc.analyzer.explodes"}};
            for (String[] f : flags) {
                if ((a.flags & Integer.parseInt(f[0])) != 0) {
                    lines.add("§e" + Lang.tr(f[1]));
                }
            }
        } else {
            Block b = mc.theWorld.getBlock(a.x, a.y, a.z);
            Item it = Item.getItemFromBlock(b);
            String name = it != null ? new ItemStack(it, 1, b.getDamageValue(mc.theWorld, a.x, a.y, a.z)).getDisplayName() : b.getLocalizedName();
            lines.add("§f" + name);
            lines.add("§b" + Lang.tr("sc.analyzer.energy", a.stored, a.capacity));
            if (!a.powerOn) {
                lines.add("§8" + Lang.tr("sc.analyzer.off"));
            }
            if (a.status >= 0) {
                lines.add("§7" + Lang.tr("sc.analyzer.status", com.sc.machine.MachineStatus.byOrdinal(a.status).localized()));
            }
            if (a.progress >= 0) {
                lines.add("§7" + Lang.tr("sc.analyzer.progress", a.progress + "%"));
            }
            if (a.output >= 0) {
                lines.add("§7" + Lang.tr("sc.analyzer.output", a.output));
            }
        }
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, mc.fontRenderer.getStringWidth(s));
        }
        int x = sr.getScaledWidth() / 2 + 14, y = sr.getScaledHeight() / 2 + 8;
        if (x + w + 4 > sr.getScaledWidth()) {
            x = Math.max(2, sr.getScaledWidth() - w - 4);
        }
        Gui.drawRect(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 1, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            mc.fontRenderer.drawStringWithShadow(lines.get(i), x, y + i * 10, 0xFFFFFF);
        }
    }

    private static String num(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
