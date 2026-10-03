package com.sc.bridge;

import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBridgeRemoteSC;
import com.sc.item.ItemCoordinatorSC;
import com.sc.item.ItemSingularCellSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.SingularLevel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * The bridge far away (docs/plan-ground-bridge.md §8, §10): what a remote in the hand and the Singular helmet's
 * link do on the server. Every command is checked here - the remote / the helmet really there, bound, its
 * bridge's id, owner or friend, the remote's charge and the Space remote's key, the helmet's module, level 3
 * and its switch; the distance is unlimited, the dimension rules are the bridge's (Ground: its own world only).
 * A bridge answers only while its chunk is loaded - «Дистанционный режим» keeps it so.
 * The answer (an NBT the remote's screen and the K menu's «Мост» tab read) carries the bridge's state, the plan
 * of the order the screen shows (cost, what is missing, where the ends stand) and the item's own data.
 */
public final class BridgeFarSC {

    private BridgeFarSC() {
    }

    public static final int SRC_REMOTE = 0, SRC_ARMOUR = 1, SRC_COORD = 2;
    public static final int F_STATE = 1, F_OPEN = 2, F_PROBE = 3, F_CLOSE = 4, F_BM_ADD = 5, F_COORD_SLOT = 6, F_TO_COORD = 7, F_SELECT = 8,
            F_MARK = 9, F_HOME = 10, F_LAST = 11, F_KEY_SLOT = 12, F_RENAME = 13, F_UNLINK = 14;
    /** Why a bridge isn't answering. */
    public static final int R_OK = 0, R_UNBOUND = 1, R_UNREACHABLE = 2, R_LOST = 3, R_ACCESS = 4;
    /** Krypton the armour spends on «Проверить место». */
    public static final int ARMOUR_PROBE_KR = BridgeMathSC.PROBE_KR;

    /** The server's tick (consent requests count by it). */
    public static long now() {
        MinecraftServer s = MinecraftServer.getServer();
        return s == null ? 0 : s.getTickCounter();
    }

    // ------------------------------------------------------------------ finding the bridge

    /** The loaded controller at a link {x, y, z, dim} with that id (0: any), or null. */
    public static TileEntityBridgeControllerSC find(int[] link, long id) {
        if (link == null) {
            return null;
        }
        World w = DimensionManager.getWorld(link[3]);
        if (w == null || !w.blockExists(link[0], link[1], link[2])) {
            return null;
        }
        TileEntity te = w.getTileEntity(link[0], link[1], link[2]);
        return te instanceof TileEntityBridgeControllerSC ? (TileEntityBridgeControllerSC) te : null;
    }

    /** R_* for a link. */
    public static int reach(int[] link, long id, EntityPlayer p) {
        if (link == null) {
            return R_UNBOUND;
        }
        World w = DimensionManager.getWorld(link[3]);
        if (w == null || !w.blockExists(link[0], link[1], link[2])) {
            return R_UNREACHABLE;
        }
        TileEntityBridgeControllerSC c = find(link, id);
        if (c == null || (id != 0 && c.getBridgeId() != 0 && c.getBridgeId() != id)) {
            return R_LOST;
        }
        return c.trusted(p) ? R_OK : R_ACCESS;
    }

    // ------------------------------------------------------------------ the armour

    /** The worn Singular helmet with the link module, or null. */
    public static ItemStack helmet(EntityPlayer p) {
        ItemStack h = p == null ? null : p.getCurrentArmor(3);
        return SingularLevel.isSingular(h) && ((ItemArmorSC) h.getItem()).armorType == 0 && BridgeItemDataSC.hasModule(h) ? h : null;
    }

    /** Why the armour can't command a bridge (lang key), or null: the module, level 3, the function switched on. */
    public static String armourBlock(EntityPlayer p) {
        ItemStack h = p == null ? null : p.getCurrentArmor(3);
        if (!SingularLevel.isSingular(h) || ((ItemArmorSC) h.getItem()).armorType != 0) {
            return "sc.bridge.armour.nohelmet";
        }
        if (!BridgeItemDataSC.hasModule(h)) {
            return "sc.bridge.armour.nomodule";
        }
        if (!SingularLevel.unlocked(p, ArmorFeature.BRIDGE_LINK, h)) {
            return "sc.bridge.armour.level";
        }
        if (!ItemArmorSC.isEnabled(h, ArmorFeature.BRIDGE_LINK)) {
            return "sc.bridge.armour.off";
        }
        return null;
    }

    private static int[] linkAt(ItemStack helmet, int i) {
        NBTTagList l = BridgeItemDataSC.links(helmet);
        if (i < 0 || i >= l.tagCount()) {
            return null;
        }
        int[] p = l.getCompoundTagAt(i).getIntArray("p");
        return p.length == 4 ? p : null;
    }

    private static long linkId(ItemStack helmet, int i) {
        NBTTagList l = BridgeItemDataSC.links(helmet);
        return i < 0 || i >= l.tagCount() ? 0 : l.getCompoundTagAt(i).getLong("id");
    }

    // ------------------------------------------------------------------ orders from the screens

    /** v = {mode, hasPoint, x, y, z, dim, toMe}; text = friend + "\n" + the point's name. */
    public static TileEntityBridgeControllerSC.Order order(int[] v, String text, int source) {
        if (v == null || v.length < 7) {
            return null;
        }
        TileEntityBridgeControllerSC.Order o = new TileEntityBridgeControllerSC.Order();
        o.mode = Math.max(0, Math.min(BridgeMathSC.MODES - 1, v[0]));
        if (o.mode == BridgeMathSC.MODE_BASE) {
            o.mode = BridgeMathSC.MODE_REMOTE;                   // from afar «С базы» is «С базы удалённо»
        }
        o.hasPoint = v[1] != 0;
        o.px = v[2];
        o.py = v[3];
        o.pz = v[4];
        o.pdim = v[5];
        o.toMe = v[6] != 0;
        o.fromFind = v.length > 7 && v[7] != 0;
        o.source = source;
        o.precise = source != BridgeMathSC.SRC_REMOTE;                   // the remote's far end isn't marked by the armour
        String t = text == null ? "" : text;
        int nl = t.indexOf('\n');
        o.friend = (nl < 0 ? t : t.substring(0, nl)).trim();
        o.pointName = nl < 0 ? "" : t.substring(nl + 1).trim();
        return o;
    }

    private static void say(EntityPlayer p, String key, Object... args) {
        if (p != null) {
            p.addChatComponentMessage(new ChatComponentTranslation(key, args));
        }
    }

    /** The message for the screen's status line. */
    private static void msg(NBTTagCompound out, BridgeMsgSC m) {
        if (m != null) {
            out.setTag("msg", m.write());
        }
    }

    /**
     * One command from a screen or a key (BridgeNetSC.Far). @return the answer for the screen (null: nothing to say)
     */
    public static NBTTagCompound handle(EntityPlayer p, int src, int slot, int action, int[] v, String text) {
        if (p == null) {
            return null;
        }
        if (v == null) {
            v = new int[0];
        }
        if (src == SRC_COORD) {
            ItemStack c = p.getHeldItem();
            if (action == F_RENAME && ItemCoordinatorSC.isCoordinator(c)) {
                BridgeItemDataSC.setPointName(c, text);
                p.inventoryContainer.detectAndSendChanges();
            }
            return null;
        }
        NBTTagCompound out = new NBTTagCompound();
        out.setInteger("src", src);
        if (src == SRC_REMOTE) {
            ItemStack r = p.getHeldItem();
            if (!ItemBridgeRemoteSC.isRemote(r)) {
                return null;
            }
            return remote(p, r, action, v, text, out);
        }
        return armour(p, slot, action, v, text, out);
    }

    private static NBTTagCompound remote(EntityPlayer p, ItemStack r, int action, int[] v, String text, NBTTagCompound out) {
        // the slots first: they work without a bridge
        if (action == F_COORD_SLOT || action == F_KEY_SLOT) {
            String key = action == F_COORD_SLOT ? "Coord" : "Key";
            ItemStack in = BridgeItemDataSC.inside(r, key);
            if (in != null) {
                if (!p.inventory.addItemStackToInventory(in)) {
                    p.dropPlayerItemWithRandomChoice(in, false);
                }
                BridgeItemDataSC.setInside(r, key, null);
            } else if (action == F_KEY_SLOT && !ItemBridgeRemoteSC.isSpace(r)) {
                return null;
            } else {
                ItemStack[] inv = p.inventory.mainInventory;
                for (int i = 0; i < inv.length; i++) {
                    boolean fits = action == F_COORD_SLOT ? ItemCoordinatorSC.isCoordinator(inv[i])
                            : inv[i] != null && inv[i].getItem() instanceof ItemSingularCellSC && ItemSingularCellSC.amountOf(inv[i]) > 0;
                    if (fits && inv[i] != r) {
                        ItemStack one = inv[i].splitStack(1);
                        if (inv[i].stackSize <= 0) {
                            inv[i] = null;
                        }
                        BridgeItemDataSC.setInside(r, key, one);
                        break;
                    }
                }
                if (BridgeItemDataSC.inside(r, key) == null) {
                    msg(out, new BridgeMsgSC(action == F_COORD_SLOT ? "sc.bridge.refuse.nocoord" : "sc.bridge.remote.nocell"));
                }
            }
            p.inventoryContainer.detectAndSendChanges();
            action = F_STATE;
        }
        int[] link = BridgeItemDataSC.remoteLink(r);
        long id = BridgeItemDataSC.remoteId(r);
        int reach = reach(link, id, p);
        out.setInteger("reach", reach);
        item(out, r);
        TileEntityBridgeControllerSC c = reach == R_OK ? find(link, id) : null;
        TileEntityBridgeControllerSC.Order o = order(v, text, BridgeMathSC.SRC_REMOTE);
        if (c == null) {
            if (action != F_STATE && action != F_TO_COORD) {
                msg(out, new BridgeMsgSC("sc.bridge.far." + (reach == R_UNBOUND ? "unbound" : reach == R_LOST ? "lost" : reach == R_ACCESS ? "access" : "unreachable")));
            }
            if (action == F_TO_COORD) {
                toCoord(p, v, text, out, r);
            }
            return out;
        }
        if (o != null && o.mode != BridgeMathSC.MODE_HOME && c.bridgeKind() == BridgeMathSC.GROUND && o.hasPoint && o.pdim != c.getWorldObj().provider.dimensionId) {
            o.pdim = c.getWorldObj().provider.dimensionId;               // a Ground remote has no dimension choice
        }
        switch (action) {
            case F_OPEN: {
                if (o == null) {
                    break;
                }
                if (ItemBridgeRemoteSC.isSpace(r) && !ItemBridgeRemoteSC.hasKey(r)) {
                    msg(out, c.refuseFar(p, new BridgeMsgSC("sc.bridge.remote.nokey")));
                    break;
                }
                long after = BridgeMathSC.remoteAfterSignal(BridgeItemDataSC.charge(r), p.capabilities.isCreativeMode);
                if (after < 0) {
                    msg(out, c.refuseFar(p, new BridgeMsgSC("sc.bridge.remote.nocharge",
                            BridgeMathSC.shortEu(BridgeMathSC.REMOTE_SIGNAL_EU, "M", "G"))));
                    break;
                }
                BridgeMsgSC m = c.openOrder(p, o);
                if (m == null || "sc.bridge.msg.consentSent".equals(m.key)) {
                    BridgeItemDataSC.setCharge(r, after);
                    if (o.hasPoint && BridgeMathSC.needsPoint(o.mode)) {
                        BridgeItemDataSC.pushHistory(BridgeItemDataSC.tag(r), new int[]{o.px, o.py, o.pz, o.pdim}, o.pointName);
                    }
                    p.inventoryContainer.detectAndSendChanges();
                }
                msg(out, m == null ? new BridgeMsgSC("sc.bridge.far.opened") : m);
                break;
            }
            case F_PROBE:
                probe(p, c, v, out, true);
                break;
            case F_CLOSE:
                c.closeFrom(p);
                break;
            case F_BM_ADD:
                if (v.length >= 4) {
                    BridgeMsgSC m = c.addBookmark(p, text, v[0], v[1], v[2], v[3]);
                    msg(out, m == null ? new BridgeMsgSC("sc.bridge.far.bmAdded", text) : m);
                }
                return out;
            case F_TO_COORD:
                toCoord(p, v, text, out, r);
                return out;
            default:
                break;
        }
        item(out, r);
        out.setTag("bridge", c.writeFarState(p, o));
        return out;
    }

    /** The remote's own data for its screen. */
    private static void item(NBTTagCompound out, ItemStack r) {
        NBTTagCompound it = new NBTTagCompound();
        it.setLong("charge", BridgeItemDataSC.charge(r));
        it.setLong("max", ItemBridgeRemoteSC.capacityOf(r));
        it.setBoolean("space", ItemBridgeRemoteSC.isSpace(r));
        it.setBoolean("key", ItemBridgeRemoteSC.hasKey(r));
        ItemStack coord = BridgeItemDataSC.inside(r, "Coord");
        if (coord != null) {
            NBTTagCompound c = new NBTTagCompound();
            int[] pt = BridgeItemDataSC.point(coord);
            if (pt != null) {
                c.setIntArray("p", pt);
            }
            c.setString("n", BridgeItemDataSC.pointName(coord));
            c.setBoolean("safe", BridgeItemDataSC.safe(coord));
            it.setTag("coord", c);
        }
        it.setTag("hist", BridgeItemDataSC.tag(r).getTagList("Hist", 10).copy());
        int[] l = BridgeItemDataSC.remoteLink(r);
        if (l != null) {
            it.setIntArray("link", l);
            it.setString("bname", BridgeItemDataSC.remoteBridgeName(r));
        }
        out.setTag("item", it);
    }

    private static void probe(EntityPlayer p, TileEntityBridgeControllerSC c, int[] v, NBTTagCompound out, boolean fromBridge) {
        if (v.length < 4) {
            return;
        }
        NBTTagCompound place = c.probeAt(p, v[0], v[1], v[2], v[3], fromBridge);
        if (place == null) {
            msg(out, c.refuseFar(p, fromBridge ? new BridgeMsgSC("sc.bridge.refuse.probekr", BridgeMathSC.PROBE_KR)
                    : new BridgeMsgSC("sc.bridge.refuse.nodim", v[3])));
            return;
        }
        out.setTag("place", place);
        msg(out, BridgeMsgSC.read(place.getCompoundTag("msg")));
    }

    /** «В координатор»: the point into the coordinator inside the remote, else one in the inventory (an empty one first). */
    private static void toCoord(EntityPlayer p, int[] v, String text, NBTTagCompound out, ItemStack remote) {
        if (v.length < 4) {
            return;
        }
        ItemStack inside = remote == null ? null : BridgeItemDataSC.inside(remote, "Coord");
        ItemStack target = inside;
        if (target == null) {
            ItemStack[] inv = p.inventory.mainInventory;
            for (int i = 0; i < inv.length && target == null; i++) {
                if (ItemCoordinatorSC.isCoordinator(inv[i]) && BridgeItemDataSC.point(inv[i]) == null) {
                    target = inv[i];
                }
            }
            for (int i = 0; i < inv.length && target == null; i++) {
                if (ItemCoordinatorSC.isCoordinator(inv[i])) {
                    target = inv[i];
                }
            }
        }
        if (target == null) {
            msg(out, new BridgeMsgSC("sc.bridge.refuse.nocoord"));
            return;
        }
        int y = v[1];
        boolean safe = false;
        World w = DimensionManager.getWorld(v[3]);
        if (w != null && y != TileEntityBridgeControllerSC.AUTO_Y) {
            safe = BridgeSpaceSC.check(BridgeSpaceSC.of(w), v[0], y, v[2], BridgeMathSC.vortexSize(BridgeMathSC.GROUND), 0).free;
        } else if (w != null) {
            int ay = BridgeSpaceSC.autoY(BridgeSpaceSC.of(w), v[0], v[2], BridgeMathSC.vortexSize(BridgeMathSC.GROUND), 0, BridgeSpaceSC.AUTO_DEPTH);
            safe = ay >= 0;
            y = ay >= 0 ? ay : y;
        }
        BridgeItemDataSC.setPoint(target, v[0], y, v[2], v[3], safe);
        String n = text == null ? "" : text.contains("\n") ? text.substring(text.indexOf('\n') + 1) : text;
        if (n.trim().length() > 0) {
            BridgeItemDataSC.setPointName(target, n);
        }
        if (inside != null) {
            BridgeItemDataSC.setInside(remote, "Coord", target);
        }
        p.inventoryContainer.detectAndSendChanges();
        msg(out, new BridgeMsgSC("sc.bridge.msg.toCoord", v[0], y == TileEntityBridgeControllerSC.AUTO_Y ? "@auto" : String.valueOf(y), v[2]));
    }

    // ------------------------------------------------------------------ the armour's «Мост» tab and keys

    private static NBTTagCompound armour(EntityPlayer p, int slot, int action, int[] v, String text, NBTTagCompound out) {
        String block = armourBlock(p);
        ItemStack h = p.getCurrentArmor(3);
        out.setString("block", block == null ? "" : block);
        out.setInteger("level", SingularLevel.isSingular(h) ? SingularLevel.effectiveLevel(p, h) : 0);
        out.setBoolean("module", BridgeItemDataSC.hasModule(h));
        out.setInteger("kr", h == null ? 0 : ArmorGasSC.amount(h, ArmorGasSC.Gas.KRYPTON));
        int coords = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            coords += ItemCoordinatorSC.isCoordinator(s) ? 1 : 0;
        }
        out.setInteger("coords", coords);
        out.setTag("finds", finds(p));
        if (block != null && !"sc.bridge.armour.off".equals(block) && !"sc.bridge.armour.level".equals(block)) {
            if (action != F_STATE) {
                msg(out, new BridgeMsgSC(block));
                say(p, block);
            }
            return out;
        }
        if (block != null && action != F_STATE) {                // the level / the switch: shown, nothing done
            msg(out, new BridgeMsgSC(block, SingularLevel.requiredLevel(ArmorFeature.BRIDGE_LINK)));
            say(p, block, String.valueOf(SingularLevel.requiredLevel(ArmorFeature.BRIDGE_LINK)));
            action = F_STATE;
        }
        if (action == F_SELECT) {
            BridgeItemDataSC.select(h, slot);
            p.inventoryContainer.detectAndSendChanges();
        }
        if (action == F_UNLINK) {
            BridgeItemDataSC.removeLink(h, slot);
            p.inventoryContainer.detectAndSendChanges();
            slot = BridgeItemDataSC.selected(h);
        }
        int sel = action == F_HOME || action == F_LAST || action == F_MARK || slot < 0 ? BridgeItemDataSC.selected(h) : slot;
        out.setInteger("sel", sel);
        // every link's line
        NBTTagList ls = BridgeItemDataSC.links(h), info = new NBTTagList();
        for (int i = 0; i < ls.tagCount(); i++) {
            NBTTagCompound e = ls.getCompoundTagAt(i);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("n", e.getString("n"));
            row.setInteger("k", e.getInteger("k"));
            int[] lp = e.getIntArray("p");
            int rr = reach(lp.length == 4 ? lp : null, e.getLong("id"), p);
            row.setInteger("reach", rr);
            TileEntityBridgeControllerSC lc = rr == R_OK ? find(lp, e.getLong("id")) : null;
            if (lc != null) {
                row.setBoolean("open", lc.isOpen());
                row.setInteger("cool", lc.getCoolTicks());
                row.setBoolean("ready", lc.isCalibrated() && lc.isPowerOn() && !lc.isOpen() && lc.getCoolTicks() == 0);
                row.setLong("cap", lc.capacitorEnergy());
                row.setLong("capMax", lc.capacitorMax());
                row.setString("n", lc.getBridgeName());
                if (lp[3] == p.worldObj.provider.dimensionId) {
                    row.setLong("dist", BridgeMathSC.distance(lp[0], 0, lp[2], (int) Math.floor(p.posX), 0, (int) Math.floor(p.posZ)));
                } else {
                    row.setLong("dist", -1);
                }
            }
            info.appendTag(row);
        }
        out.setTag("links", info);
        out.setTag("hist", BridgeItemDataSC.helmetLink(h) == null ? new NBTTagList() : BridgeItemDataSC.helmetLink(h).getTagList("Hist", 10).copy());
        int[] link = linkAt(h, sel);
        long id = linkId(h, sel);
        int reach = reach(link, id, p);
        out.setInteger("reach", reach);
        TileEntityBridgeControllerSC c = reach == R_OK ? find(link, id) : null;
        TileEntityBridgeControllerSC.Order o;
        if (action == F_HOME) {
            o = order(new int[]{BridgeMathSC.MODE_HOME, 0, 0, 0, 0, 0, 0}, "", BridgeMathSC.SRC_ARMOUR);
        } else if (action == F_LAST) {
            NBTTagCompound last = BridgeItemDataSC.helmetLink(h).getCompoundTag("Last");
            o = last.hasNoTags() ? null : TileEntityBridgeControllerSC.Order.read(last);
            if (o == null) {
                msg(out, new BridgeMsgSC("sc.bridge.armour.nolast"));
                say(p, "sc.bridge.armour.nolast");
                return out;
            }
            o.source = BridgeMathSC.SRC_ARMOUR;
            o.consented.clear();
        } else {
            o = order(v, text, BridgeMathSC.SRC_ARMOUR);
        }
        if (c == null) {
            if (action == F_OPEN || action == F_HOME || action == F_LAST || action == F_PROBE || action == F_MARK || action == F_BM_ADD) {
                String k = "sc.bridge.far." + (reach == R_UNBOUND ? "unbound" : reach == R_LOST ? "lost" : reach == R_ACCESS ? "access" : "unreachable");
                msg(out, new BridgeMsgSC(k));
                say(p, k);
            }
            if (action == F_TO_COORD) {
                toCoord(p, v, text, out, null);
            }
            return out;
        }
        if (o != null && c.bridgeKind() == BridgeMathSC.GROUND && o.hasPoint) {
            o.pdim = c.getWorldObj().provider.dimensionId;
        }
        if (o != null && o.fromFind) {                                   // level 5: precise portals to the scanner's finds
            o.precise = SingularLevel.effectiveLevel(p, h) >= SingularLevel.BRIDGE_FINDS_LEVEL;
        }
        switch (action) {
            case F_OPEN:
            case F_HOME:
            case F_LAST: {
                if (o == null) {
                    break;
                }
                BridgeMsgSC m = c.openOrder(p, o);
                if (m == null || "sc.bridge.msg.consentSent".equals(m.key)) {
                    NBTTagCompound l = BridgeItemDataSC.helmetLink(h);
                    NBTTagCompound last = o.write();
                    last.removeTag("c");
                    l.setTag("Last", last);
                    if (o.hasPoint && BridgeMathSC.needsPoint(o.mode)) {
                        BridgeItemDataSC.pushHistory(l, new int[]{o.px, o.py, o.pz, o.pdim}, o.pointName);
                    }
                    p.inventoryContainer.detectAndSendChanges();
                }
                msg(out, m == null ? new BridgeMsgSC("sc.bridge.far.opened") : m);
                break;
            }
            case F_PROBE:
                if (ArmorGasSC.drain(h, ArmorGasSC.Gas.KRYPTON, ARMOUR_PROBE_KR, true) < ARMOUR_PROBE_KR && !p.capabilities.isCreativeMode) {
                    msg(out, new BridgeMsgSC("sc.bridge.armour.nokr", ARMOUR_PROBE_KR));
                    break;
                }
                if (!p.capabilities.isCreativeMode) {
                    ArmorGasSC.drain(h, ArmorGasSC.Gas.KRYPTON, ARMOUR_PROBE_KR, false);
                    p.inventoryContainer.detectAndSendChanges();
                }
                probe(p, c, v, out, false);
                break;
            case F_CLOSE:
                c.closeFrom(p);
                break;
            case F_MARK: {
                int x = (int) Math.floor(p.posX), y = (int) Math.floor(p.boundingBox.minY + 0.001), z = (int) Math.floor(p.posZ);
                BridgeMsgSC m = c.addBookmark(p, text, x, y, z, p.worldObj.provider.dimensionId);
                BridgeMsgSC said = m == null ? new BridgeMsgSC("sc.bridge.armour.marked", text, x, y, z, c.nameArg()) : m;
                if (m == null) {
                    p.addChatComponentMessage(said.chat());
                }
                msg(out, said);
                break;
            }
            case F_BM_ADD:
                if (v.length >= 4) {
                    BridgeMsgSC m = c.addBookmark(p, text, v[0], v[1], v[2], v[3]);
                    msg(out, m == null ? new BridgeMsgSC("sc.bridge.far.bmAdded", text) : m);
                }
                break;
            case F_TO_COORD:
                toCoord(p, v, text, out, null);
                break;
            default:
                break;
        }
        out.setTag("bridge", c.writeFarState(p, o != null ? o : order(new int[]{BridgeMathSC.MODE_HOME, 0, 0, 0, 0, 0, 0}, "", BridgeMathSC.SRC_ARMOUR)));
        return out;
    }

    // ------------------------------------------------------------------ Ш1: the scanner's finds (the «Находки сканера» list)

    public static final String FINDS = "scBridgeFinds";

    /** Remembers the chests, spawners and ores a scanner pulse found (the newest FINDS, the same block once). */
    public static void noteFinds(EntityPlayer p, java.util.List<int[]> blocks) {
        NBTTagCompound persisted = SingularLevel.persisted(p, true);
        NBTTagList old = persisted.getTagList(FINDS, 10), now = new NBTTagList();
        int dim = p.worldObj.provider.dimensionId;
        java.util.Set<String> seen = new java.util.HashSet<String>();
        int taken = 0;
        for (int[] b : blocks) {
            if (taken >= 4) {
                break;
            }
            if (b[3] == com.sc.util.SingularSenseData.ORE) {
                continue;                                    // vanilla ores: too many - chests, spawners and the mod's ores only
            }
            String k = dim + ":" + b[0] + ":" + b[1] + ":" + b[2];
            if (seen.add(k)) {
                NBTTagCompound e = new NBTTagCompound();
                e.setIntArray("p", new int[]{b[0], b[1], b[2], dim});
                e.setInteger("k", b[3]);
                now.appendTag(e);
                taken++;
            }
        }
        for (int i = 0; i < old.tagCount() && now.tagCount() < BridgeMathSC.FINDS; i++) {
            NBTTagCompound e = old.getCompoundTagAt(i);
            int[] q = e.getIntArray("p");
            if (q.length == 4 && seen.add(q[3] + ":" + q[0] + ":" + q[1] + ":" + q[2])) {
                now.appendTag(e);
            }
        }
        persisted.setTag(FINDS, now);
    }

    public static NBTTagList finds(EntityPlayer p) {
        return (NBTTagList) SingularLevel.persisted(p, false).getTagList(FINDS, 10).copy();
    }

    // ------------------------------------------------------------------ consent in the chat

    /** «Имя хочет открыть портал моста «Мост» рядом с вами (Забрать друга). [Принять] [Отклонить]» - 30 s. */
    public static IChatComponent consentQuestion(int id, String from, Object bridge, int mode) {
        IChatComponent all = new ChatComponentText("");
        all.appendSibling(new ChatComponentTranslation("sc.bridge.consent.ask", from, bridge,
                new ChatComponentTranslation(BridgeMsgSC.RES + "mode." + mode), String.valueOf(BridgeMathSC.CONSENT_TICKS / 20)));
        all.appendSibling(new ChatComponentText(" "));
        IChatComponent yes = new ChatComponentTranslation("sc.bridge.consent.accept");
        yes.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN).setBold(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/scbridge accept " + id)));
        IChatComponent no = new ChatComponentTranslation("sc.bridge.consent.decline");
        no.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/scbridge decline " + id)));
        all.appendSibling(yes);
        all.appendSibling(new ChatComponentText(" "));
        all.appendSibling(no);
        return all;
    }

    /**
     * `who` answers consent request `id` (the /scbridge command; the world test). On «Принять» the bridge opens
     * the order now with that consent (the requester must still be online and the owner's friend).
     * @return the controller's answer (null: opened) or what went wrong, as a message
     */
    public static BridgeMsgSC answer(EntityPlayer who, int id, boolean accept) {
        BridgeConsentSC cs = BridgeConsentSC.server();
        int st = cs.answer(id, who == null ? "" : who.getCommandSenderName(), accept, now());
        if (st == BridgeConsentSC.NOT_FOUND || st == BridgeConsentSC.CLOSED) {
            return new BridgeMsgSC("sc.bridge.consent.gone");
        }
        if (st == BridgeConsentSC.NOT_YOURS) {
            return new BridgeMsgSC("sc.bridge.consent.notyours");
        }
        BridgeConsentSC.Request r = cs.get(id);
        EntityPlayer from = TileEntityBridgeControllerSC.playerByName(r.from);
        if (!accept) {
            if (from != null) {
                say(from, "sc.bridge.consent.declined", who.getCommandSenderName());
            }
            return new BridgeMsgSC("sc.bridge.consent.youDeclined");
        }
        int[] at = r.order.getIntArray("ctrl");
        TileEntityBridgeControllerSC c = at.length == 4 ? find(at, 0) : null;
        if (c == null || from == null) {
            return new BridgeMsgSC(c == null ? "sc.bridge.far.unreachable" : "sc.bridge.consent.requesterGone");
        }
        cs.use(id);
        TileEntityBridgeControllerSC.Order o = TileEntityBridgeControllerSC.Order.read(r.order);
        o.consented.add(who.getCommandSenderName());
        BridgeMsgSC m = c.openOrder(from, o);
        return m == null ? new BridgeMsgSC("sc.bridge.consent.youAccepted", r.from) : m;
    }
}
