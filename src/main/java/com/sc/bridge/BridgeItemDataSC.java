package com.sc.bridge;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * What the bridge items keep in their NBT (pure - the self-test reads and writes it on plain stacks):
 *  - the Coordinator: "Pt" {x, y, z, dim}, "Nm" its name, "Safe" (the place check passed when it was recorded);
 *  - the remotes: "Br" {x, y, z, dim} of the bound controller, "BrId" its id, "BrNm" / "BrKind", "Charge" (EU),
 *    "Coord" (the inserted coordinator's stack), "Key" (the Space remote's Singular Matter cell), "Hist" (targets);
 *  - the Singular helmet's link (Armour Link Module, §8): "BrLink" {"Mod", "L" [{p, id, n, k}], "Sel", "Last", "Hist"}.
 * A link's entry and a history entry: {"p": {x, y, z, dim}, "n": name}; a link adds "id" (the controller's id) and "k" (kind).
 */
public final class BridgeItemDataSC {

    private BridgeItemDataSC() {
    }

    public static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        return s.getTagCompound();
    }

    private static String cut(String s, int n) {
        String t = s == null ? "" : s.trim();
        return t.length() > n ? t.substring(0, n) : t;
    }

    // ------------------------------------------------------------------ the coordinator

    /** The point {x, y, z, dim}, or null. */
    public static int[] point(ItemStack s) {
        if (s == null || !s.hasTagCompound()) {
            return null;
        }
        int[] p = s.getTagCompound().getIntArray("Pt");
        return p.length == 4 ? p : null;
    }

    public static void setPoint(ItemStack s, int x, int y, int z, int dim, boolean safe) {
        NBTTagCompound t = tag(s);
        t.setIntArray("Pt", new int[]{x, y, z, dim});
        t.setBoolean("Safe", safe);
    }

    public static String pointName(ItemStack s) {
        return s != null && s.hasTagCompound() ? s.getTagCompound().getString("Nm") : "";
    }

    public static void setPointName(ItemStack s, String name) {
        tag(s).setString("Nm", cut(name, 32));
    }

    public static boolean safe(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().getBoolean("Safe");
    }

    /** Copies the point, the name and the safe flag of `from` into `to`. */
    public static void copyPoint(ItemStack from, ItemStack to) {
        int[] p = point(from);
        if (p == null || to == null) {
            return;
        }
        setPoint(to, p[0], p[1], p[2], p[3], safe(from));
        setPointName(to, pointName(from));
    }

    // ------------------------------------------------------------------ the remotes

    /** The bound controller {x, y, z, dim}, or null. */
    public static int[] remoteLink(ItemStack s) {
        if (s == null || !s.hasTagCompound()) {
            return null;
        }
        int[] p = s.getTagCompound().getIntArray("Br");
        return p.length == 4 ? p : null;
    }

    public static long remoteId(ItemStack s) {
        return s != null && s.hasTagCompound() ? s.getTagCompound().getLong("BrId") : 0;
    }

    public static String remoteBridgeName(ItemStack s) {
        return s != null && s.hasTagCompound() ? s.getTagCompound().getString("BrNm") : "";
    }

    public static void bindRemote(ItemStack s, int x, int y, int z, int dim, long id, String name, int kind) {
        NBTTagCompound t = tag(s);
        t.setIntArray("Br", new int[]{x, y, z, dim});
        t.setLong("BrId", id);
        t.setString("BrNm", cut(name, 32));
        t.setInteger("BrKind", kind);
    }

    public static long charge(ItemStack s) {
        return s != null && s.hasTagCompound() ? Math.max(0, s.getTagCompound().getLong("Charge")) : 0;
    }

    public static void setCharge(ItemStack s, long eu) {
        tag(s).setLong("Charge", Math.max(0, eu));
    }

    /** The stack kept inside under `key` ("Coord" / "Key"), or null. */
    public static ItemStack inside(ItemStack s, String key) {
        if (s == null || !s.hasTagCompound() || !s.getTagCompound().hasKey(key)) {
            return null;
        }
        return ItemStack.loadItemStackFromNBT(s.getTagCompound().getCompoundTag(key));
    }

    public static void setInside(ItemStack s, String key, ItemStack in) {
        if (in == null) {
            if (s.hasTagCompound()) {
                s.getTagCompound().removeTag(key);
            }
            return;
        }
        tag(s).setTag(key, in.writeToNBT(new NBTTagCompound()));
    }

    // ------------------------------------------------------------------ history (remote and helmet)

    public static NBTTagCompound entry(int[] p, String name) {
        NBTTagCompound e = new NBTTagCompound();
        e.setIntArray("p", p.clone());
        e.setString("n", cut(name, 32));
        return e;
    }

    /** Adds a target to the front of a history list (the same point moves up), at most BridgeMathSC.HISTORY. */
    public static void pushHistory(NBTTagCompound holder, int[] p, String name) {
        NBTTagList old = holder.getTagList("Hist", 10);
        NBTTagList now = new NBTTagList();
        now.appendTag(entry(p, name));
        for (int i = 0; i < old.tagCount() && now.tagCount() < BridgeMathSC.HISTORY; i++) {
            NBTTagCompound e = old.getCompoundTagAt(i);
            if (!java.util.Arrays.equals(e.getIntArray("p"), p)) {
                now.appendTag(e);
            }
        }
        holder.setTag("Hist", now);
    }

    // ------------------------------------------------------------------ the helmet's link

    /** The helmet's link data, or null when it has no Armour Link Module. */
    public static NBTTagCompound helmetLink(ItemStack helmet) {
        if (helmet == null || !helmet.hasTagCompound() || !helmet.getTagCompound().hasKey("BrLink")) {
            return null;
        }
        NBTTagCompound l = helmet.getTagCompound().getCompoundTag("BrLink");
        return l.getBoolean("Mod") ? l : null;
    }

    public static boolean hasModule(ItemStack helmet) {
        return helmetLink(helmet) != null;
    }

    /** The Singular Station merges the module in: an empty link list. */
    public static void installModule(ItemStack helmet) {
        NBTTagCompound l = new NBTTagCompound();
        l.setBoolean("Mod", true);
        l.setTag("L", new NBTTagList());
        tag(helmet).setTag("BrLink", l);
    }

    public static NBTTagList links(ItemStack helmet) {
        NBTTagCompound l = helmetLink(helmet);
        return l == null ? new NBTTagList() : l.getTagList("L", 10);
    }

    /** Links (or renames, if already linked by id) a bridge: @return its index, or -1 when there are MAX_LINKS already */
    public static int addLink(ItemStack helmet, int x, int y, int z, int dim, long id, String name, int kind) {
        NBTTagCompound l = helmetLink(helmet);
        if (l == null) {
            return -1;
        }
        NBTTagList list = l.getTagList("L", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int[] p = e.getIntArray("p");
            if ((id != 0 && e.getLong("id") == id) || java.util.Arrays.equals(p, new int[]{x, y, z, dim})) {
                e.setIntArray("p", new int[]{x, y, z, dim});
                e.setLong("id", id);
                e.setString("n", cut(name, 32));
                e.setInteger("k", kind);
                l.setTag("L", list);
                return i;
            }
        }
        if (list.tagCount() >= BridgeMathSC.MAX_LINKS) {
            return -1;
        }
        NBTTagCompound e = entry(new int[]{x, y, z, dim}, name);
        e.setLong("id", id);
        e.setInteger("k", kind);
        list.appendTag(e);
        l.setTag("L", list);
        return list.tagCount() - 1;
    }

    public static void removeLink(ItemStack helmet, int index) {
        NBTTagCompound l = helmetLink(helmet);
        if (l == null) {
            return;
        }
        NBTTagList list = l.getTagList("L", 10), now = new NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            if (i != index) {
                now.appendTag(list.getCompoundTagAt(i));
            }
        }
        l.setTag("L", now);
        int sel = l.getInteger("Sel");
        if (index >= 0 && index < list.tagCount() && index < sel) {
            sel--;                                       // the links after the removed one moved up: keep the same bridge
        }
        l.setInteger("Sel", Math.max(0, Math.min(sel, now.tagCount() - 1)));
    }

    public static void clearLinks(ItemStack helmet) {
        NBTTagCompound l = helmetLink(helmet);
        if (l != null) {
            l.setTag("L", new NBTTagList());
            l.setInteger("Sel", 0);
        }
    }

    public static int selected(ItemStack helmet) {
        NBTTagCompound l = helmetLink(helmet);
        return l == null ? 0 : Math.max(0, Math.min(Math.max(0, links(helmet).tagCount() - 1), l.getInteger("Sel")));
    }

    public static void select(ItemStack helmet, int i) {
        NBTTagCompound l = helmetLink(helmet);
        if (l != null) {
            l.setInteger("Sel", Math.max(0, Math.min(BridgeMathSC.MAX_LINKS - 1, i)));
        }
    }
}
