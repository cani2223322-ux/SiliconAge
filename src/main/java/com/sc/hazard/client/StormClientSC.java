package com.sc.hazard.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.bridge.BridgeHudDataSC;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.hazard.HazardNetSC;
import com.sc.hazard.StormRulesSC;
import com.sc.hazard.StormStateSC;
import com.sc.manual.Lang;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §5.4, ПФ-4): the EMP storm on a client - ClientProxy.hazardMessage hands every
 * HazardNetSC message here (any thread: only StormStateSC's volatile fields are written). The core's part: STORM_STATE and
 * STORM_PULSE into StormStateSC (the provider's sky and fog tint by it already). S's (register):
 * <ul>
 * <li>the scan lines («строки развёртки»): textures/misc/storm_scan.png tiled over the screen, running top to bottom, by
 * StormStateSC.skyFactor - only while the player's head sees the sky (canBlockSeeTheSky, eased in and out), under the hotbar
 * (RenderGameOverlayEvent.Post HELMET);</li>
 * <li>a pulse: the whitish-violet flash FLASH_TICKS long and the crackle (storm.crackle) - weaker under cover; in the storm under
 * the sky now and then a crackle of static;</li>
 * <li>the HUD line at the top middle (under a boss bar and the bridge's line): «ЭМИ-буря через N с» (sc.storm.hud.warn),
 * «ЭМИ-буря: М:СС» (sc.storm.hud), in the Rift during the Singular Emission «Сингулярный выброс: М:СС» (sc.emission.hud);</li>
 * <li>a reset (StormStateSC.clientReset) on leaving the server (ClientDisconnectionFromServerEvent) and on leaving the
 * Substrate (a new client world that isn't it).</li>
 * </ul>
 * The fog is package W's (SubstrateClientSC), the Wireweed's sparks too (the block asks StormStateSC).
 */
@SideOnly(Side.CLIENT)
public final class StormClientSC {

    private static final StormClientSC INSTANCE = new StormClientSC();
    private static final ResourceLocation SCAN = new ResourceLocation(Reference.ASSETS, "textures/misc/storm_scan.png");
    /** The scan texture's size (GUI pixels a texel), the lines' top alpha, their run (texture heights a tick). */
    private static final int SCAN_W = 128, SCAN_H = 128;
    private static final float SCAN_ALPHA = 0.55F, SCAN_SPEED = 0.02F;
    /** A pulse's flash: this long (ticks), its colour, its alpha under the sky / under cover. */
    public static final int FLASH_TICKS = 10;
    private static final int FLASH_RGB = 0xE6D4FF;
    private static final float FLASH_SKY = 0.55F, FLASH_COVER = 0.18F;
    /** The crackle: the pulse's sound, the static's chance a tick in the storm under the sky. */
    private static final String SND_CRACKLE = Reference.ASSETS + ":storm.crackle";
    private static final int STATIC_ONE_IN = 90;
    /** The HUD's colours: the warning (blinking), the storm, the emission. */
    private static final int COL_WARN = 0xFFD040, COL_WARN_BLINK = 0xFF8A30, COL_STORM = 0xC890FF, COL_EMISSION = 0xFF6464,
            COL_HAZE = 0xC8C8B4;                          // ПМ-4: the silica haze's pale grey

    /** The messages not applied yet (written by the network thread). */
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<Pending>();

    private static final class Pending {
        final int kind;
        final NBTTagCompound data;

        Pending(int kind, NBTTagCompound data) {
            this.kind = kind;
            this.data = data;
        }
    }

    /** Client thread only: the world last seen was the Substrate; how far the head sees the sky (eased 0..1); the pulse handled. */
    private boolean wasSubstrate;
    private float skyVis;
    private long pulseSeen = Long.MIN_VALUE;

    private StormClientSC() {
    }

    /** ClientProxy.preInit: the overlay (Forge's bus), the tick and the disconnection (FML's). */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    /**
     * A HazardNetSC message (any thread - the network one): queued, applied on the client thread's next tick, when the world it
     * belongs to is there and its clock set (at a login the message may come before the client has made its world).
     */
    public static void onMessage(int kind, NBTTagCompound data) {
        if (data != null) {
            PENDING.add(new Pending(kind, data));
        }
    }

    /** Client thread: the queued messages into StormStateSC, by world `w`'s clock. */
    private static void apply(World w) {
        Pending m;
        while ((m = PENDING.poll()) != null) {
            apply(m.kind, m.data, w.getTotalWorldTime());
        }
    }

    private static void apply(int kind, NBTTagCompound data, long now) {
        if (kind == HazardNetSC.STORM_STATE) {
            int warn = data.hasKey("w") ? data.getInteger("w") : StormRulesSC.WARN_TICKS;
            StormStateSC.clientSet(data.getInteger("p"), data.getInteger("t"), warn, now);
        } else if (kind == HazardNetSC.STORM_PULSE) {
            StormStateSC.clientPulse(now);
        } else if (kind == HazardNetSC.HAZE_STATE) {
            StormStateSC.clientHaze(data.getBoolean("on"), data.getInteger("t"), now);
        } else if (kind == HazardNetSC.EMISSION_STATE) {
            StormStateSC.clientEmission(data.getBoolean("on"), data.getInteger("t"), now);
        }
    }

    // ------------------------------------------------------------------ the reset

    /** Left the server (the network thread may fire it: only StormStateSC's volatile fields). */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        PENDING.clear();
        StormStateSC.clientReset();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        boolean sub = w != null && SubstrateSC.is(w);
        if (!sub && wasSubstrate) {
            StormStateSC.clientReset();                 // out of the Substrate (another world, the menu)
        }
        if (w != null) {
            apply(w);                                   // after the reset: a message for the world coming next stays
        }
        if (!sub) {
            wasSubstrate = false;
            skyVis = 0F;
            return;
        }
        wasSubstrate = true;
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.isGamePaused()) {
            return;
        }
        boolean sky = seesSky(w, p);
        skyVis += ((sky ? 1F : 0F) - skyVis) * 0.12F;
        long now = w.getTotalWorldTime();
        long since = StormStateSC.clientSincePulse(now);
        if (since >= 0L && since < FLASH_TICKS && now - since != pulseSeen) {
            pulseSeen = now - since;
            w.playSound(p.posX, p.posY, p.posZ, SND_CRACKLE, sky ? 1.0F : 0.45F, 0.9F + w.rand.nextFloat() * 0.2F, false);
        } else if (StormStateSC.clientPhase() == StormStateSC.STORM && sky && w.rand.nextInt(STATIC_ONE_IN) == 0) {
            w.playSound(p.posX + w.rand.nextGaussian() * 4D, p.posY + 2D, p.posZ + w.rand.nextGaussian() * 4D, SND_CRACKLE,
                    0.25F + w.rand.nextFloat() * 0.2F, 1.2F + w.rand.nextFloat() * 0.5F, false);
        }
    }

    /** The head's block sees the sky (the chunk's height map: glass lets it through, as for the sun). */
    private static boolean seesSky(World w, EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        int y = MathHelper.floor_double(p.boundingBox.maxY);
        return w.canBlockSeeTheSky(x, y, z);
    }

    // ------------------------------------------------------------------ the screen

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.HELMET && e.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null || mc.thePlayer == null || !SubstrateSC.is(w)) {
            return;
        }
        if (e.type == RenderGameOverlayEvent.ElementType.HELMET) {
            screen(mc, w, e.resolution, e.partialTicks);
        } else if (mc.currentScreen == null && !mc.gameSettings.hideGUI && !mc.gameSettings.showDebugInfo) {
            hud(mc, w, e.resolution);
        }
    }

    /** The scan lines and the pulse's flash, under the hotbar. */
    private void screen(Minecraft mc, World w, ScaledResolution res, float pt) {
        int sw = res.getScaledWidth(), sh = res.getScaledHeight();
        float lines = StormStateSC.skyFactor(w, pt) * skyVis;
        if (lines > 0.01F) {
            double t = w.getTotalWorldTime() + pt;
            double scroll = (t * SCAN_SPEED) % 1D;
            double jitter = (MathHelper.floor_double(t / 3D) * 0.618D) % 1D;      // a sideways twitch now and then
            double us = sw / (double) SCAN_W, vs = sh / (double) SCAN_H;
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1F, 1F, 1F, lines * SCAN_ALPHA);
            mc.getTextureManager().bindTexture(SCAN);
            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            tes.addVertexWithUV(0D, sh, -90D, jitter, vs - scroll);
            tes.addVertexWithUV(sw, sh, -90D, jitter + us, vs - scroll);
            tes.addVertexWithUV(sw, 0D, -90D, jitter + us, -scroll);
            tes.addVertexWithUV(0D, 0D, -90D, jitter, -scroll);
            tes.draw();
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
        long since = StormStateSC.clientSincePulse(w.getTotalWorldTime());
        if (since >= 0L && since < FLASH_TICKS) {
            float k = 1F - Math.min(1F, (since + pt) / FLASH_TICKS);
            float a = k * (FLASH_COVER + (FLASH_SKY - FLASH_COVER) * skyVis);
            int alpha = Math.max(0, Math.min(255, (int) (a * 255F)));
            if (alpha > 0) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                Gui.drawRect(0, 0, sw, sh, alpha << 24 | FLASH_RGB);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glColor4f(1F, 1F, 1F, 1F);
            }
        }
    }

    /** The HUD line(s) at the top middle. */
    private static void hud(Minecraft mc, World w, ScaledResolution res) {
        long now = w.getTotalWorldTime();
        int phase = StormStateSC.clientPhase();
        FontRenderer fr = mc.fontRenderer;
        int cx = res.getScaledWidth() / 2, y = 4;
        if (BossStatus.bossName != null && BossStatus.statusBarTime > 0) {
            y += 20;                                    // under the boss bar
        }
        if (BridgeHudDataSC.shown()) {
            y += 30;                                    // under the bridge's charge line and its bar
        }
        int left = StormStateSC.clientTicksLeft(now);
        if (phase == StormStateSC.WARNING) {
            boolean blink = (now / 10L) % 2L == 0L;
            y = centred(fr, Lang.tr("sc.storm.hud.warn", String.valueOf((left + 19) / 20)), cx, y, blink ? COL_WARN : COL_WARN_BLINK);
        } else if (phase == StormStateSC.STORM) {
            y = centred(fr, Lang.tr("sc.storm.hud", clock(left)), cx, y, COL_STORM);
        }
        int haze = StormStateSC.clientHazeLeft(now);
        if (haze > 0) {                                  // ПМ-4 (audit 2026-10-20): the haze's line - "Haze: 2:31"
            y = centred(fr, Lang.tr("sc.haze.hud", clock(haze)), cx, y, COL_HAZE);
        }
        if (StormStateSC.clientEmission() && inRift(w, mc.thePlayer)) {
            centred(fr, Lang.tr("sc.emission.hud", clock(StormStateSC.clientEmissionLeft(now))), cx, y, COL_EMISSION);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The player's column is the Rift (the client reads the chunk's biome array). */
    private static boolean inRift(World w, EntityPlayer p) {
        BiomeGenBase b = w.getBiomeGenForCoords(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posZ));
        return b instanceof BiomeSubstrateSC && ((BiomeSubstrateSC) b).kind == BiomeSubstrateSC.RIFT;
    }

    /** Ticks as «М:СС». */
    static String clock(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        int m = s / 60;
        s %= 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    private static int centred(FontRenderer fr, String s, int cx, int y, int rgb) {
        fr.drawStringWithShadow(s, cx - fr.getStringWidth(s) / 2, y, rgb);
        return y + 10;
    }
}
