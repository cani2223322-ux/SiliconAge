package com.sc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.tileentity.TileEntityFieldGeneratorSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * The field generator's rain shield, the client's half. Rain is drawn by the client round the
 * player (about 10 blocks), from the sky's height - there's no stopping it per column without
 * patching the renderer. So the rain the player sees is thinned by how much of that ring round
 * them lies inside a shielded field: none deep inside, some at the edge, all of it outside.
 * The client never works the rain's strength out itself - the server sends it only when it
 * changes - so the real strength is kept here and given back as the player steps out (setting
 * it to 0 used to leave the whole world dry until the weather next changed).
 * And where rain meets a shielded field's shell, drops burst on it in the shell's colour.
 */
public final class RainShieldClientSC {

    private static final RainShieldClientSC INSTANCE = new RainShieldClientSC();
    /** How far from the player the shell's splashes are drawn, and how many a tick at most. */
    private static final int SPLASH_RANGE = 24, SPLASHES = 5;

    private final List<TileEntityFieldGeneratorSC> fields = new ArrayList<TileEntityFieldGeneratorSC>();
    private final Random rand = new Random();
    private World scanned;
    private int rescan;
    /** The part of the ring round the player inside a shielded field (0..1). */
    private float cover;
    /** The rain's real strength (the server's), and what we show instead - negative while we don't touch it. */
    private float realRain, shown = -1F;

    private RainShieldClientSC() {
    }

    public static void register() {
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        EntityPlayer p = mc.thePlayer;
        if (w == null || p == null) {
            fields.clear();
            scanned = null;
            cover = 0F;
            shown = -1F;
            return;
        }
        if (w != scanned) {
            shown = -1F;                                              // a new world: its own weather, untouched
        }
        if (event.phase == TickEvent.Phase.START) {
            keepShown(w);                                             // the renderer's drops and sound this tick
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        if (w != scanned || --rescan <= 0) {
            scanned = w;
            rescan = 20;
            fields.clear();
            for (Object o : w.loadedTileEntityList) {
                if (o instanceof TileEntityFieldGeneratorSC && ((TileEntityFieldGeneratorSC) o).isMaster()) {
                    fields.add((TileEntityFieldGeneratorSC) o);
                }
            }
        }
        boolean raining = w.getWorldInfo().isRaining();
        List<TileEntityFieldGeneratorSC> up = new ArrayList<TileEntityFieldGeneratorSC>();
        for (TileEntityFieldGeneratorSC f : fields) {
            if (f.isInvalid() || !f.isRainShield() || !f.isActive()) {
                continue;
            }
            up.add(f);
            if (raining && mc.gameSettings.particleSetting < 2) {
                splash(w, p, f);
            }
        }
        cover = up.isEmpty() ? 0F : cover(up, p.posX, p.boundingBox.minY + 2.0, p.posZ);
        keepShown(w);
        if (shown < 0F) {
            return;
        }
        float target = realRain * (1F - cover);
        shown = shown < target ? Math.min(target, shown + 0.03F) : Math.max(target, shown - 0.05F);
        w.setRainStrength(shown);
        if (cover <= 0F && Math.abs(shown - realRain) < 0.001F) {
            shown = -1F;                                              // back to the world's own rain: hands off
        }
    }

    /**
     * Notes a strength the server sent (it differs from what we set), and takes over when the
     * player comes under a shield; while we're in charge, puts our value back.
     */
    private void keepShown(World w) {
        float cur = w.rainingStrength;
        if (shown < 0F) {
            realRain = cur;
            if (cover > 0F) {
                shown = cur;
            }
            return;
        }
        if (Math.abs(cur - shown) > 0.0001F) {
            realRain = cur;                                           // the weather changed meanwhile
        }
        w.setRainStrength(shown);
    }

    /** The part of the ring the rain is drawn in round (x, z) - the centre, 8 points at 5 blocks, 8 at 10 - inside a field. */
    private static float cover(List<TileEntityFieldGeneratorSC> up, double x, double y, double z) {
        int in = 0, all = 0;
        for (int ring = 0; ring <= 2; ring++) {
            int n = ring == 0 ? 1 : 8;
            for (int k = 0; k < n; k++) {
                double a = k * Math.PI / 4, r = ring * 5.0;
                double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
                all++;
                for (TileEntityFieldGeneratorSC f : up) {
                    if (f.fieldContains(px, y, pz)) {
                        in++;
                        break;
                    }
                }
            }
        }
        return in / (float) all;
    }

    /** A few drops bursting on the shell near the player, where the sky rains onto it. */
    private void splash(World w, EntityPlayer p, TileEntityFieldGeneratorSC f) {
        AxisAlignedBB box = f.zoneBounds();
        int x0 = Math.max(MathHelper.floor_double(box.minX), MathHelper.floor_double(p.posX) - SPLASH_RANGE);
        int x1 = Math.min(MathHelper.floor_double(box.maxX), MathHelper.floor_double(p.posX) + SPLASH_RANGE);
        int z0 = Math.max(MathHelper.floor_double(box.minZ), MathHelper.floor_double(p.posZ) - SPLASH_RANGE);
        int z1 = Math.min(MathHelper.floor_double(box.maxZ), MathHelper.floor_double(p.posZ) + SPLASH_RANGE);
        if (x1 < x0 || z1 < z0) {
            return;
        }
        int top = Math.min(MathHelper.floor_double(box.maxY), 255), bottom = Math.max(MathHelper.floor_double(box.minY), 0);
        float[] rgb = f.rgbF(TileEntityFieldGeneratorSC.RGB_SHELL);
        int n = Math.max(1, SPLASHES * (x1 - x0 + 1) * (z1 - z0 + 1) / (SPLASH_RANGE * SPLASH_RANGE * 4));
        for (int k = 0; k < Math.min(SPLASHES, n); k++) {
            int x = x0 + rand.nextInt(x1 - x0 + 1), z = z0 + rand.nextInt(z1 - z0 + 1);
            BiomeGenBase b = w.getBiomeGenForCoords(x, z);
            if (!b.canSpawnLightningBolt() && !b.getEnableSnow()) {
                continue;                                             // no rain or snow falls here
            }
            int y = top;
            while (y >= bottom && !f.fieldContains(x + 0.5, y + 0.5, z + 0.5)) {
                y--;
            }
            if (y < bottom || y + 1 < w.getPrecipitationHeight(x, z)) {
                continue;                                             // the shell's roof here is under a roof of blocks
            }
            double px = x + rand.nextDouble(), py = y + 1.02, pz = z + rand.nextDouble();
            if (b.canSpawnLightningBolt()) {
                w.spawnParticle("splash", px, py, pz, 0, 0, 0);
            }
            // reddust: with a speed of (r, g, b) it takes that colour (r must not be 0)
            w.spawnParticle("reddust", px, py, pz, Math.max(0.01F, rgb[0]), rgb[1], rgb[2]);
        }
    }
}
