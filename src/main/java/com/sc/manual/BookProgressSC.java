package com.sc.manual;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The handbook's memory on this computer (config/siliconage-book.cfg): the bookmarks, and the
 * first steps already done - per world, ticked the moment the step's item turns up in the
 * player's inventory (crafted, smelted or picked up).
 */
@SideOnly(Side.CLIENT)
public final class BookProgressSC {

    private static final Set<String> done = new HashSet<String>();
    private static final Set<String> marks = new LinkedHashSet<String>();
    private static boolean loaded;
    private static int ticks;

    private BookProgressSC() {
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "siliconage-book.cfg");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            File f = file();
            if (!f.exists()) {
                return;
            }
            for (String line : org.apache.commons.io.FileUtils.readLines(f, "UTF-8")) {
                if (line.startsWith("bm=")) {
                    marks.add(line.substring(3));
                } else if (line.startsWith("done=")) {
                    done.add(line.substring(5));
                }
            }
        } catch (Exception e) {
            // a broken file: start clean
        }
    }

    private static void save() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Silicon Age handbook: bookmarks and first steps done (per world)");
        for (String m : marks) {
            lines.add("bm=" + m);
        }
        for (String d : done) {
            lines.add("done=" + d);
        }
        try {
            File f = file();
            f.getParentFile().mkdirs();
            org.apache.commons.io.FileUtils.writeLines(f, "UTF-8", lines);
        } catch (Exception e) {
            // not saved - it's only a convenience
        }
    }

    /** This world (single player: its folder; a server: its address). */
    private static String world() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) {
            return "sp:" + mc.getIntegratedServer().getFolderName();
        }
        ServerData d = mc.func_147104_D();
        return "mp:" + (d == null ? "?" : d.serverIP);
    }

    public static boolean isDone(String step) {
        load();
        return done.contains(world() + "|" + step);
    }

    public static int doneCount() {
        int n = 0;
        for (String s : BookContent.STEPS) {
            n += isDone(s) ? 1 : 0;
        }
        return n;
    }

    public static List<String> bookmarks() {
        load();
        return new ArrayList<String>(marks);
    }

    public static boolean isMarked(String id) {
        load();
        return marks.contains(id);
    }

    public static void toggleMark(String id) {
        load();
        if (!marks.remove(id)) {
            marks.add(id);
        }
        save();
    }

    /** Every 2 s: a step's item in the inventory ticks the step. */
    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || ++ticks % 40 != 0) {
            return;
        }
        load();
        String w = world();
        boolean changed = false;
        for (int i = 0; i < BookContent.STEPS.length; i++) {
            String key = w + "|" + BookContent.STEPS[i];
            if (done.contains(key)) {
                continue;
            }
            if (has(mc.thePlayer.inventory.mainInventory, BookContent.stepItems(i))) {
                done.add(key);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static boolean has(ItemStack[] inv, ItemStack[] want) {
        for (ItemStack s : inv) {
            if (s == null) {
                continue;
            }
            for (ItemStack w : want) {
                if (w != null && s.getItem() == w.getItem()
                        && (w.getItemDamage() == OreDictionary.WILDCARD_VALUE || s.getItemDamage() == w.getItemDamage())) {
                    return true;
                }
            }
        }
        return false;
    }
}
