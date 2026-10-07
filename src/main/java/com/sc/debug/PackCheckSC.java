package com.sc.debug;

import java.io.File;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Developer check of a whole mod pack, client only, never in normal play: with -Dsc.packcheck=<dir> the game
 * opens a flat creative test world from the main menu, waits for NEI's plugins, then writes
 * <dir>/recipe-conflicts.txt (RecipeConflictsSC) and <dir>/nei-coverage.txt (NeiCoverageSC) and quits.
 */
@SideOnly(Side.CLIENT)
public final class PackCheckSC {

    public static final PackCheckSC INSTANCE = new PackCheckSC();

    private int state, ticks, lastHandlers = -1, stable, menuTicks;

    private PackCheckSC() {
    }

    public static boolean wanted() {
        return System.getProperty("sc.packcheck") != null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (state == 0 && mc.theWorld == null && mc.currentScreen != null && ++menuTicks >= 100) {
            // any screen once the game has loaded (a pack may show its own menu or a notice instead of GuiMainMenu)
            System.out.println("[SC-PACKCHECK] opening the test world from " + mc.currentScreen.getClass().getName());
            state = 1;
            mc.launchIntegratedServer("sc-packcheck", "sc-packcheck",
                    new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
            return;
        }
        if (state != 1 || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        ticks++;
        int h = Loader.isModLoaded("NotEnoughItems") ? com.sc.nei.NeiCoverageSC.handlers() : 0;
        stable = h == lastHandlers && h > 0 ? stable + 1 : 0;
        lastHandlers = h;
        if (ticks < 200 || stable < 200 && ticks < 2400) {
            return;                                         // NEI loads its plugins on a thread after the world opens
        }
        state = 2;
        File dir = new File(System.getProperty("sc.packcheck"));
        dir.mkdirs();
        try {
            MinecraftServer srv = MinecraftServer.getServer();
            World w = srv != null && srv.worldServers.length > 0 ? srv.worldServers[0] : mc.theWorld;
            write(new File(dir, "recipe-conflicts.txt"), RecipeConflictsSC.run(w));
            if (Loader.isModLoaded("NotEnoughItems")) {
                write(new File(dir, "nei-coverage.txt"), com.sc.nei.NeiCoverageSC.run());
            }
            write(new File(dir, "done.txt"), java.util.Collections.singletonList("ok, NEI handlers " + h));
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                write(new File(dir, "done.txt"), java.util.Collections.singletonList("failed: " + t));
            } catch (Exception ignored) {
                // nothing more to say
            }
        }
        mc.shutdown();
    }

    private static void write(File f, List<String> lines) throws java.io.IOException {
        org.apache.commons.io.FileUtils.writeLines(f, "UTF-8", lines);
    }
}
