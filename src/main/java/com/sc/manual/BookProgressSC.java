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
import net.minecraft.entity.player.InventoryPlayer;
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
    /** Кн-1: the file is there but couldn't be read - retried (at most every RETRY_MS) before anything overwrites it. */
    private static boolean readFailed;
    private static long lastTry;
    private static final long RETRY_MS = 5000;
    /** Кн-2: bookmarks read back from the file, older lists included (new ones stop at BookSearchSC.MAX_MARKS). */
    private static final int READ_MARKS_MAX = 1024;
    private static int ticks;

    private BookProgressSC() {
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "siliconage-book.cfg");
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (loaded && !(readFailed && now - lastTry >= RETRY_MS)) {
            return;
        }
        loaded = true;
        lastTry = now;
        try {
            File f = file();
            if (!f.exists()) {
                readFailed = false;
                return;
            }
            List<String> lines = org.apache.commons.io.FileUtils.readLines(f, "UTF-8");
            marks.addAll(BookSearchSC.marksFromLines(lines, READ_MARKS_MAX));    // merged with anything added meanwhile
            for (String line : lines) {
                if (line.startsWith("done=")) {
                    done.add(line.substring(5));
                }
            }
            readFailed = false;
        } catch (Exception e) {
            readFailed = true;                              // locked or broken: kept as it is, read again later
        }
    }

    private static void save() {
        if (readFailed) {
            lastTry = 0;
            load();                                         // one more try to merge what the file has
        }
        if (readFailed) {
            try {                                           // still unreadable: it is kept aside, not overwritten blind
                File f = file(), keep = new File(f.getPath() + ".unreadable");
                if (f.exists() && !keep.exists()) {
                    org.apache.commons.io.FileUtils.copyFile(f, keep);
                }
            } catch (Exception e) {
                return;                                     // couldn't even copy it: leave the file alone
            }
        }
        List<String> lines = new ArrayList<String>();
        lines.add("# Silicon Age handbook: bookmarks and first steps done (per world)");
        lines.addAll(BookSearchSC.marksToLines(new ArrayList<String>(marks)));
        for (String d : done) {
            lines.add("done=" + d);
        }
        try {
            File f = file(), tmp = new File(f.getPath() + ".tmp");
            f.getParentFile().mkdirs();
            org.apache.commons.io.FileUtils.writeLines(tmp, "UTF-8", lines);   // whole, then swapped in
            if (f.exists() && !f.delete()) {
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(f)) {
                org.apache.commons.io.FileUtils.copyFile(tmp, f);
                tmp.delete();
            }
            readFailed = false;
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

    /** Adds or removes a bookmark; false when the list is full (nothing changed). */
    public static boolean toggleMark(String id) {
        load();
        List<String> l = new ArrayList<String>(marks);
        boolean changed = BookSearchSC.removeMark(l, id) || BookSearchSC.addMark(l, id, BookSearchSC.MAX_MARKS);
        if (changed) {
            marks.clear();
            marks.addAll(l);
            save();
        }
        return changed;
    }

    public static void removeMark(String id) {
        load();
        if (marks.remove(id)) {
            save();
        }
    }

    /** Every 2 s: a step's item in the inventory, armour or on the cursor ticks the step. */
    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || ++ticks % 40 != 0
                || mc.isSingleplayer() && mc.getIntegratedServer() == null) {
            return;                                   // loading / leaving: the world's key isn't known
        }
        load();
        String w = world();
        boolean changed = false;
        for (int i = 0; i < BookContent.STEPS.length; i++) {
            String key = w + "|" + BookContent.STEPS[i];
            if (done.contains(key)) {
                continue;
            }
            if (has(mc.thePlayer.inventory, BookContent.stepItems(i))) {
                done.add(key);
                changed = true;
            }
        }
        String[] path = BookPathSC.stepIds();               // the "Path" chapter's steps, ticked the same way
        for (int i = 0; i < path.length; i++) {
            String key = w + "|" + path[i];
            if (!done.contains(key) && has(mc.thePlayer.inventory, BookPathSC.stepItems(i))) {
                done.add(key);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /** Main inventory, armour slots and the stack on the cursor. */
    private static boolean has(InventoryPlayer inv, ItemStack[] want) {
        return has(inv.mainInventory, want) || has(inv.armorInventory, want)
                || has(new ItemStack[] {inv.getItemStack()}, want);
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
