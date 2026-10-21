package com.sc.emp.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpModule;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpSC;
import com.sc.emp.EmpTooltipSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.emp.ItemEmpModuleSC;
import com.sc.inventory.TextFitSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE C: the K menu's «ПЭ-1» tab (docs/plan-pe1.md §8) and the «ЭМИ» line of the armour tabs, kept out of
 * GuiArmorSC so its own change stays thin. GuiArmorSC builds the three mode rows (switch + key button) with its row
 * machinery (EmpMode is a bindable function there, ArmorKeyBindsSC "emp_<key>"); this class lays out and draws the
 * rest: the readout (charge, heat, mode and price, the siphon's receiver), the settings, the modules and the target.
 * Its buttons are GuiArmorSC ids ID_FIRST..ID_LAST (between TAB_BASE and BIND_BASE: GuiArmorSC's refresh and feature
 * hovers leave them alone). Settings are shown at once on the client's copy and sent (EmpNetSC.send); the server checks
 * them against the gun in hand (EmpActionsSC). Left column: modes, settings; right: readout, modules, target.
 */
@SideOnly(Side.CLIENT)
public final class GuiEmpTabSC {

    /** Buttons: the Point zone, receiver switches (+ position 0..2), receiver "up" (+ position), limit, warning, modules (+ ordinal), reboot. */
    public static final int ID_FIRST = 1300, ID_ZONE = 1300, ID_RECV = 1301, ID_RECV_UP = 1304, ID_LIMIT = 1307, ID_WARN = 1308,
            ID_MODULE = 1310, ID_REBOOT = 1320, ID_LAST = 1329;
    /**
     * The «Предел» steps a click walks (Shift + click: back), ascending: no limit - the overflow vented (the default),
     * «пока моя броня не наберёт N%», «пока приёмник не полон» (2026-10-11).
     */
    private static final int[] LIMIT_STEPS = {EmpMathSC.LIMIT_VENT, 25, 50, 75, 90, 100, EmpMathSC.LIMIT_FULL};
    /** The aim readout is fresh this long (it comes every AIM_SCAN_TICKS while the gun is in hand). */
    private static final long AIM_FRESH_MS = 1000L;
    /** The armour tabs' «ЭМИ» line with its reboot button: height GuiArmorSC reserves under the rows. */
    public static final int ARMOUR_LINE_H = 18;
    private static final int HEAD = 0x8C8C8C, DIM = 0x909090, RED = 0xFF5050, ORANGE = 0xFF8C3C, TEXT = 0xE0E0E0, CYAN = 0x8FE3FF;
    private static final int UP_W = 14, LINE = 10;

    // ---- the tab's layout (init) ----
    private int lx, lw, rx, rw, pitch, btnH, modesY, settingsY, readoutY, modulesY, modPitch, targetY, footY, keyX, keyW;
    // ---- the armour line (initArmourLine), y -1: none ----
    private int armX, armY = -1, armW, rebootW;

    // ------------------------------------------------------------------ the tab

    public static ItemStack gun() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? null : ItemEmpGunSC.held(mc.thePlayer);
    }

    /**
     * The gun is in use (charging up or streaming): the server keeps the copy it started with and would undo a change at
     * the end, so the settings and modules wait (grey, «Нельзя во время набора или потока»). Modes and the reboot don't.
     */
    private static boolean busy() {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        return p != null && p.isUsingItem() && ItemEmpGunSC.isGun(p.getItemInUse());
    }

    /** The buttons busy() locks: the zone, the receivers (+ "up"), the limit, the warning, the modules. */
    private static boolean lockedWhileBusy(int id) {
        return id >= ID_ZONE && id <= ID_WARN || id >= ID_MODULE && id < ID_MODULE + EmpModule.values().length;
    }

    /**
     * Lays the tab out in (x, y, w) down to `footY` and adds its buttons. @return {x, y, switch width, pitch} of the first
     * mode row: GuiArmorSC adds the EmpMode rows under each other at that pitch (button height pitch - 2), the key button
     * keyW wide after a gap.
     */
    @SuppressWarnings("unchecked")
    public int[] init(List buttons, int x, int y, int w, int footY, int keyW, int gap) {
        this.footY = footY;
        this.keyW = keyW;
        int colGap = 8;
        lw = (w - colGap) / 2;
        lx = x;
        rx = x + lw + colGap;
        rw = x + w - rx;
        int avail = footY - 2 - y;
        pitch = Math.max(12, Math.min(16, (avail - 2 * LINE - 2) / 9));     // modes 3, zone, receivers 3, limit, warning
        btnH = pitch - 2;
        modesY = y;
        settingsY = modesY + LINE + 3 * pitch + 2;
        int sy = settingsY + LINE;
        buttons.add(new TextFitSC.Button(ID_ZONE, lx, sy, lw, btnH, ""));
        sy += pitch;
        for (int i = 0; i < EmpMathSC.RECV_COUNT; i++) {
            buttons.add(new TextFitSC.Button(ID_RECV + i, lx, sy, lw - UP_W - 2, btnH, ""));
            buttons.add(new TextFitSC.Button(ID_RECV_UP + i, lx + lw - UP_W, sy, UP_W, btnH, "▲"));
            sy += pitch;
        }
        buttons.add(new TextFitSC.Button(ID_LIMIT, lx, sy, lw, btnH, ""));
        sy += pitch;
        buttons.add(new TextFitSC.Button(ID_WARN, lx, sy, lw, btnH, ""));
        // right: three readout lines, the modules, the target
        readoutY = y;
        modulesY = readoutY + 3 * LINE + 2;
        modPitch = Math.max(11, Math.min(pitch, (avail - 3 * LINE - 2 - LINE - 2 - LINE - 2 * LINE) / EmpModule.values().length));
        int my = modulesY + LINE;
        for (EmpModule m : EmpModule.values()) {
            buttons.add(new TextFitSC.Button(ID_MODULE + m.ordinal(), rx, my, rw, modPitch - 2, ""));
            my += modPitch;
        }
        targetY = my + 2;
        int switchW = lw - keyW - gap;
        keyX = lx + switchW + gap;
        return new int[]{lx, modesY + LINE, switchW, pitch};
    }

    /** Captions and on / off of the tab's buttons and of the armour line's reboot button (every tick). */
    public void refresh(List buttons) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        ItemStack gun = gun();
        boolean busy = busy();
        for (Object o : buttons) {
            GuiButton b = (GuiButton) o;
            if (b.id < ID_FIRST || b.id > ID_LAST) {
                continue;
            }
            if (b.id == ID_REBOOT) {
                int cd = p == null ? 0 : EmpSC.rebootCooldownTicks(p);
                b.enabled = cd <= 0;
                b.displayString = cd > 0 ? Lang.tr("sc.armorgui.emp.reboot.cd", secs(cd)) : Lang.tr("sc.armorgui.emp.reboot");
                continue;
            }
            if (gun == null) {
                b.enabled = false;
                continue;
            }
            if (b.id == ID_ZONE) {
                b.displayString = Lang.tr("sc.empgui.zone", Lang.tr("sc.empgui.zone." + ItemEmpGunSC.zoneOf(gun)));
            } else if (b.id >= ID_RECV && b.id < ID_RECV + EmpMathSC.RECV_COUNT) {
                int pos = b.id - ID_RECV;
                int r = ItemEmpGunSC.receiverOrder(gun)[pos];
                boolean on = ItemEmpGunSC.receiverEnabled(gun, r);
                b.displayString = (on ? "§a" : "§7") + (pos + 1) + ". " + Lang.tr("sc.empgui.recv." + r) + ": "
                        + Lang.tr(on ? "sc.armorgui.on" : "sc.armorgui.off");
            } else if (b.id == ID_LIMIT) {
                int lim = ItemEmpGunSC.limitOf(gun);
                b.displayString = Lang.tr("sc.empgui.limit", lim == EmpMathSC.LIMIT_FULL ? Lang.tr("sc.empgui.limit.full")
                        : lim == EmpMathSC.LIMIT_VENT ? Lang.tr("sc.empgui.limit.vent") : Lang.tr("sc.empgui.limit.pct", lim));
            } else if (b.id == ID_WARN) {
                boolean on = ItemEmpGunSC.warnOn(gun);
                b.displayString = (on ? "§a" : "§7") + Lang.tr("sc.empgui.warn", Lang.tr(on ? "sc.armorgui.on" : "sc.armorgui.off"));
            } else if (b.id >= ID_MODULE && b.id < ID_MODULE + EmpModule.values().length) {
                EmpModule m = EmpModule.of(b.id - ID_MODULE);
                String name = Lang.tr("sc.empgui.mod." + m.key());
                if (ItemEmpGunSC.hasModule(gun, m)) {
                    b.enabled = true;
                    b.displayString = "§a" + Lang.tr("sc.empgui.module.remove", name);
                } else if (moduleSlot(p, m) >= 0) {
                    b.enabled = true;
                    b.displayString = "§e" + Lang.tr("sc.empgui.module.install", name);
                } else {
                    b.enabled = false;
                    b.displayString = Lang.tr("sc.empgui.module.none", name);
                }
            }
            if (lockedWhileBusy(b.id)) {
                b.enabled = !busy && (b.enabled || b.id < ID_MODULE);   // the settings come back on once the use ends
            }
        }
    }

    /** A click on one of this class's buttons (GuiArmorSC routes ID_FIRST..ID_LAST here). */
    public void action(GuiButton b) {
        if (lockedWhileBusy(b.id) && busy()) {
            return;                                                // charging up or streaming: nothing changed here, nothing sent
        }
        if (b.id == ID_REBOOT) {
            EmpNetSC.send(EmpNetSC.EMERGENCY_REBOOT, 0, 0);       // the server pays, refuses or says why not
            return;
        }
        ItemStack gun = gun();
        if (gun == null) {
            return;
        }
        int dir = GuiScreen.isShiftKeyDown() ? -1 : 1;
        if (b.id == ID_ZONE) {
            int z = ((ItemEmpGunSC.zoneOf(gun) + dir) % EmpMathSC.ZONE_COUNT + EmpMathSC.ZONE_COUNT) % EmpMathSC.ZONE_COUNT;
            ItemEmpGunSC.setZone(gun, z);                          // shown at once; the server's copy follows
            EmpNetSC.send(EmpNetSC.SET_ZONE, z, 0);
        } else if (b.id >= ID_RECV && b.id < ID_RECV + EmpMathSC.RECV_COUNT) {
            int r = ItemEmpGunSC.receiverOrder(gun)[b.id - ID_RECV];
            boolean on = !ItemEmpGunSC.receiverEnabled(gun, r);
            ItemEmpGunSC.setReceiverEnabled(gun, r, on);
            EmpNetSC.send(EmpNetSC.TOGGLE_RECEIVER, r, on ? 1 : 0);
        } else if (b.id >= ID_RECV_UP && b.id < ID_RECV_UP + EmpMathSC.RECV_COUNT) {
            int packed = ItemEmpGunSC.packOrder(moveUp(ItemEmpGunSC.receiverOrder(gun), b.id - ID_RECV_UP));
            ItemEmpGunSC.setReceiverOrder(gun, packed);
            EmpNetSC.send(EmpNetSC.SET_RECEIVER_ORDER, packed, 0);
        } else if (b.id == ID_LIMIT) {
            int lim = nextLimit(ItemEmpGunSC.limitOf(gun), dir);
            ItemEmpGunSC.setLimit(gun, lim);
            EmpNetSC.send(EmpNetSC.SET_LIMIT, lim, 0);
        } else if (b.id == ID_WARN) {
            boolean on = !ItemEmpGunSC.warnOn(gun);
            ItemEmpGunSC.setWarn(gun, on);
            EmpNetSC.send(EmpNetSC.SET_WARN, 0, on ? 1 : 0);
        } else if (b.id >= ID_MODULE && b.id < ID_MODULE + EmpModule.values().length) {
            EmpModule m = EmpModule.of(b.id - ID_MODULE);           // the inventory changes: the server does it and sends it back
            if (ItemEmpGunSC.hasModule(gun, m)) {
                EmpNetSC.send(EmpNetSC.MODULE_REMOVE, m.ordinal(), 0);
            } else {
                int slot = moduleSlot(Minecraft.getMinecraft().thePlayer, m);
                if (slot >= 0) {
                    EmpNetSC.send(EmpNetSC.MODULE_INSTALL, m.ordinal(), slot);
                }
            }
        }
    }

    /** Pure: the receiver at `pos` one place up (the first goes to the end). */
    public static int[] moveUp(int[] order, int pos) {
        int[] o = order.clone();
        if (pos <= 0) {
            int first = o[0];
            System.arraycopy(o, 1, o, 0, o.length - 1);
            o[o.length - 1] = first;
        } else if (pos < o.length) {
            int t = o[pos - 1];
            o[pos - 1] = o[pos];
            o[pos] = t;
        }
        return o;
    }

    /** Pure: the next limit step from `cur` (dir +1 / -1, round). */
    public static int nextLimit(int cur, int dir) {
        int i = 0;
        while (i < LIMIT_STEPS.length - 1 && LIMIT_STEPS[i] < cur) {
            i++;
        }
        if (LIMIT_STEPS[i] != cur) {                               // between two steps (an old value): the step above / below
            return dir > 0 ? LIMIT_STEPS[i] : LIMIT_STEPS[Math.max(0, i - 1)];
        }
        return LIMIT_STEPS[((i + dir) % LIMIT_STEPS.length + LIMIT_STEPS.length) % LIMIT_STEPS.length];
    }

    /** Main inventory slot holding module `m`, or -1. */
    private static int moduleSlot(EntityPlayer p, EmpModule m) {
        if (p == null) {
            return -1;
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemEmpModuleSC && ItemEmpModuleSC.moduleOf(inv[i]) == m) {
                return i;
            }
        }
        return -1;
    }

    /** Headings, the readout, the module totals and the target; hovers for the settings and modules. */
    public void draw(FontRenderer font, List buttons) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        ItemStack gun = gun();
        if (gun == null || p == null) {
            return;
        }
        // left: headings over the modes and the settings
        fit(font, Lang.tr("sc.empgui.modes"), lx, modesY, keyX - lx - 2, HEAD);
        String k = Lang.tr("sc.armorgui.col.key");
        int kw = Math.min(font.getStringWidth(k), keyW + 8);
        fit(font, k, keyX + keyW - kw, modesY, kw, HEAD);
        fit(font, Lang.tr("sc.empgui.settings"), lx, settingsY, lw, HEAD);
        // right: the readout
        int y = readoutY;
        int charge = ItemEmpGunSC.chargeOf(gun), cap = ItemEmpGunSC.capacityOf(gun);
        int heat = ItemEmpGunSC.heatOf(gun);
        boolean hot = ItemEmpGunSC.overheated(gun);
        EmpNetSC.AimMessage live = freshAim();
        boolean streaming = live != null && live.streaming && live.heat >= 0;
        if (streaming) {
            heat = live.heat;                                    // the stream's heat isn't written until it ends (rule 0.4.1)
            hot = live.throttled;
        }
        String line = Lang.tr("sc.empgui.charge", eu(charge), eu(cap)) + " • " + Lang.tr("sc.empgui.heat", heat)
                + (streaming && live.throttled ? " • " + Lang.tr("sc.emp.hud.throttled")
                : heat > 0 && !streaming ? " • " + Lang.tr("sc.empgui.cool", EmpMathSC.coolFullSeconds(heat)) : "");
        fit(font, line, rx, y, rw, hot ? RED : heat > 80 ? ORANGE : TEXT);
        y += LINE;
        EmpMode mode = ItemEmpGunSC.modeOf(gun);
        int cd = ItemEmpGunSC.pulseCooldown(gun, p.worldObj.getTotalWorldTime());
        line = Lang.tr("sc.empgui.mode", Lang.tr("sc.emp.mode." + mode.key())) + " • " + Lang.tr("sc.empgui.price", eu(EmpMathSC.euCost(mode)))
                + (mode == EmpMode.PULSE && cd > 0 ? " • " + Lang.tr("sc.emp.cooldown", secs(cd)) : "");
        fit(font, line, rx, y, rw, 0xFF000000 | mode.rgb());
        y += LINE;
        EmpNetSC.AimMessage aim = freshAim();
        if (aim == null) {
            fit(font, Lang.tr("sc.empgui.receiver", "—", "—"), rx, y, rw, DIM);
        } else if (aim.receiver < 0) {
            fit(font, Lang.tr("sc.empgui.noreceiver"), rx, y, rw, RED);
        } else {
            fit(font, Lang.tr("sc.empgui.receiver", Lang.tr("sc.empgui.recv." + aim.receiver), eu(aim.receiverRoom)), rx, y, rw, 0xFF000000 | EmpMode.SIPHON.rgb());
        }
        // the modules: heading with the totals as its hover
        int hw = fit(font, Lang.tr("sc.empgui.modules"), rx, modulesY, rw, HEAD);
        TextFitSC.hover(rx, modulesY, Math.max(hw, 40), LINE, totals(gun));
        boolean busy = busy();
        for (Object o : buttons) {
            GuiButton b = (GuiButton) o;
            List<String> tip = buttonTip(gun, b.id);
            if (tip != null && busy && lockedWhileBusy(b.id)) {
                tip.add("§c" + Lang.tr("sc.empgui.busy"));
            }
            if (tip != null) {
                TextFitSC.hover(b.xPosition, b.yPosition, b.width, b.height, tip);
            }
        }
        drawTarget(font, mc, aim);
    }

    private void drawTarget(FontRenderer font, Minecraft mc, EmpNetSC.AimMessage aim) {
        int y = targetY;
        if (y + LINE > footY) {
            return;
        }
        fit(font, Lang.tr("sc.empgui.target"), rx, y, rw, HEAD);
        y += LINE;
        Entity e = aim == null || aim.targetId == 0 || mc.theWorld == null ? null : mc.theWorld.getEntityByID(aim.targetId);
        if (e == null) {
            if (y + 8 <= footY + 2) {
                fit(font, Lang.tr("sc.empgui.target.none"), rx, y, rw, DIM);
            }
            return;
        }
        List<String> lines = new ArrayList<String>();
        List<Integer> colors = new ArrayList<Integer>();
        ArmorSuit[] suits = ArmorSuit.values();
        String suit = aim.suit >= 0 && aim.suit < suits.length ? Lang.tr("sc.suit." + suits[aim.suit].name().toLowerCase(Locale.ROOT))
                : Lang.tr("sc.empgui.target.nosuit");
        lines.add(e.getCommandSenderName() + " • " + Lang.tr("sc.empgui.target.suit", suit));
        colors.add(TEXT);
        if (aim.reason != EmpNetSC.OK) {
            lines.add(Lang.tr("sc.emp.refuse." + aim.reason));
            colors.add(RED);
        } else if ((aim.slots & 15) != 0) {
            lines.add(Lang.tr("sc.empgui.target.supp", pieces(aim.slots & 15), secs(aim.suppressedTicks)));
            colors.add(0xFF000000 | EmpMode.PULSE.rgb());
        } else if ((aim.slots >> 4 & 15) != 0) {
            lines.add(Lang.tr("sc.empgui.target.immune", secs(aim.immuneTicks)));
            colors.add(CYAN);
        }
        String charge = aim.chargePct >= 0 ? Lang.tr("sc.empgui.target.charge", aim.chargePct) : "";
        if (aim.streaming) {
            charge += (charge.isEmpty() ? "" : " • ") + Lang.tr("sc.empgui.target.stream", eu(aim.received))
                    + (aim.vented > 0 ? " • " + Lang.tr("sc.emp.hud.vented", eu(aim.vented)) : "");
        }
        if (!charge.isEmpty()) {
            lines.add(charge);
            colors.add(0xFF000000 | EmpMode.SIPHON.rgb());
        }
        for (int i = 0; i < lines.size() && y + 8 <= footY + 2; i++) {
            fit(font, lines.get(i), rx, y, rw, colors.get(i));
            y += LINE;
        }
    }

    /** «Буфер 1,2 млн EU · Точка 48 бл. · Сифон 24 бл., до 8 192 EU/t, потери 10%» and the gun's heat. */
    private static List<String> totals(ItemStack gun) {
        List<String> tip = new ArrayList<String>();
        boolean focus = ItemEmpGunSC.hasModule(gun, EmpModule.FOCUS);
        boolean wide = ItemEmpGunSC.hasModule(gun, EmpModule.WIDE);
        tip.add(Lang.tr("sc.empgui.modules"));
        tip.add("§7" + Lang.tr("sc.empgui.totals", eu(ItemEmpGunSC.capacityOf(gun)), EmpMathSC.range(EmpMode.POINT, focus),
                EmpMathSC.range(EmpMode.SIPHON, focus), EmpTooltipSC.siphonPct(ArmorSuit.SINGULAR, wide), EmpTooltipSC.siphonPct(ArmorSuit.NANO, wide),
                EmpMathSC.siphonLossPct(ItemEmpGunSC.hasModule(gun, EmpModule.EFFICIENT), false, ConfigSC.empSiphonLoss)));
        boolean cooling = ItemEmpGunSC.hasModule(gun, EmpModule.COOLING);
        tip.add("§7" + Lang.tr("sc.empgui.totals.heat", EmpMathSC.gunHeat(EmpMode.POINT, cooling), EmpMathSC.gunHeat(EmpMode.PULSE, cooling),
                EmpMathSC.siphonOverheatSeconds(cooling), EmpMathSC.coolFullSeconds(EmpMathSC.GUN_HEAT_MAX)));
        return tip;
    }

    /** Hover lines of a settings / module button, or null. */
    private static List<String> buttonTip(ItemStack gun, int id) {
        List<String> tip = new ArrayList<String>();
        if (id == ID_ZONE) {
            tip.add(Lang.tr("sc.empgui.zone.tip"));
        } else if (id >= ID_RECV && id < ID_RECV_UP + EmpMathSC.RECV_COUNT) {
            tip.add(Lang.tr("sc.empgui.recv.tip"));
        } else if (id == ID_LIMIT) {
            tip.add(Lang.tr("sc.empgui.limit.tip"));
        } else if (id == ID_WARN) {
            tip.add(Lang.tr("sc.empgui.warn.tip", EmpMathSC.OVERHEAT_WARN_SECONDS));
        } else if (id >= ID_MODULE && id < ID_MODULE + EmpModule.values().length) {
            EmpModule m = EmpModule.of(id - ID_MODULE);
            tip.add(Lang.tr("sc.empgui.mod." + m.key()));
            tip.add("§7" + Lang.tr("sc.emp.module." + m.key() + ".desc"));
            return tip;
        } else {
            return null;
        }
        if (id != ID_WARN) {
            tip.add("§7" + Lang.tr("sc.empgui.shift.back"));
        }
        return tip;
    }

    // ------------------------------------------------------------------ the mode rows (GuiArmorSC's rows)

    /** The mode switch's caption: the current mode marked, on / off. */
    public static String modeLabel(ItemStack gun, EmpMode m) {
        String name = Lang.tr("sc.emp.mode." + m.key());
        if (gun == null) {
            return "§8" + name;
        }
        boolean on = ItemEmpGunSC.modeEnabled(gun, m);
        String state = name + ": " + Lang.tr(on ? "sc.armorgui.on" : "sc.armorgui.off");
        return ItemEmpGunSC.modeOf(gun) == m ? "§b> " + state + " <" : (on ? "§a" : "§7") + state;
    }

    /** A click on the mode's switch: on / off (the last one on stays on). */
    public static void toggleMode(ItemStack gun, EmpMode m) {
        if (gun == null) {
            return;
        }
        boolean want = !ItemEmpGunSC.modeEnabled(gun, m);
        if (ItemEmpGunSC.setModeEnabled(gun, m, want)) {           // shown at once (the current mode steps on as on the server)
            EmpNetSC.send(EmpNetSC.TOGGLE_MODE, m.ordinal(), want ? 1 : 0);
        } else if (!want) {
            Minecraft.getMinecraft().ingameGUI.func_110326_a(Lang.tr("sc.empgui.lastmode"), false);
        }
    }

    /** The mode's hover: what it does, price, range, charge-up, cooldown, times by suit / the stream's speed, loss, receivers. */
    public static List<String> modeTip(ItemStack gun, EmpMode m) {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.emp.mode." + m.key()));
        tip.add("§7" + Lang.tr("sc.emp.mode." + m.key() + ".desc"));
        boolean focus = ItemEmpGunSC.hasModule(gun, EmpModule.FOCUS), up = ItemEmpGunSC.upgraded(gun);
        int range = EmpMathSC.range(m, focus);
        String price = eu(EmpMathSC.euCost(m));
        if (m == EmpMode.SIPHON) {
            int loss = EmpMathSC.siphonLossPct(ItemEmpGunSC.hasModule(gun, EmpModule.EFFICIENT), false, ConfigSC.empSiphonLoss);
            tip.add("§b" + Lang.tr("sc.empgui.tip.siphon", price, range, loss));
            tip.add("§7" + Lang.tr("sc.empgui.tip.siphon.rate", EmpTooltipSC.siphonRates(ItemEmpGunSC.hasModule(gun, EmpModule.WIDE))));
            StringBuilder recv = new StringBuilder();
            for (int r : ItemEmpGunSC.receiverOrder(gun)) {
                if (ItemEmpGunSC.receiverEnabled(gun, r)) {
                    recv.append(recv.length() > 0 ? " → " : "").append(Lang.tr("sc.empgui.recv." + r));
                }
            }
            tip.add("§7" + Lang.tr("sc.empgui.tip.siphon.recv", recv.length() > 0 ? recv.toString() : Lang.tr("sc.empgui.noreceiver")));
        } else {
            String charge = num(EmpMathSC.chargeTicks(m, up) / 20F);
            if (m == EmpMode.POINT) {
                tip.add("§b" + Lang.tr("sc.empgui.tip.point", price, range, charge));
            } else {
                tip.add("§b" + Lang.tr("sc.empgui.tip.pulse", price, range, charge, EmpMathSC.PULSE_COOLDOWN / 20));
            }
            for (ArmorSuit s : new ArmorSuit[]{ArmorSuit.NANO, ArmorSuit.QUANTUM, ArmorSuit.EXO, ArmorSuit.SINGULAR}) {
                int t = EmpMathSC.durationTicks(s, m, up, 0, false, ConfigSC.empDurationMultiplier);
                tip.add("§7  " + Lang.tr("sc.empgui.tip.suit", Lang.tr("sc.suit." + s.name().toLowerCase(Locale.ROOT)), num(t / 20F)));
            }
            tip.add("§8" + Lang.tr("sc.empgui.tip.dur.note"));
        }
        tip.add("§e" + Lang.tr("sc.empgui.tip.click"));
        return tip;
    }

    // ------------------------------------------------------------------ the armour tabs' «ЭМИ» line

    /** Adds the reboot button for the line at (x, y), `w` wide (GuiArmorSC reserves ARMOUR_LINE_H under the rows). */
    @SuppressWarnings("unchecked")
    public void initArmourLine(List buttons, FontRenderer font, int x, int y, int w) {
        armX = x;
        armY = y;
        armW = w;
        String label = Lang.tr("sc.armorgui.emp.reboot");
        rebootW = Math.min(w / 2, font.getStringWidth(label) + 12);
        buttons.add(new TextFitSC.Button(ID_REBOOT, x + w - rebootW, y, rebootW, 16, label));
    }

    public void clearArmourLine() {
        armY = -1;
    }

    /** «ЭМИ: подавлено: шлем 6 с» / «ЭМИ: невосприимчивость 12 с» / «ЭМИ: норма», with the reboot button's hover. */
    public void drawArmourLine(FontRenderer font) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (armY < 0 || p == null) {
            return;
        }
        StringBuilder sup = new StringBuilder();
        int immune = 0;
        for (int t = 0; t < 4; t++) {
            int s = EmpSC.suppressedTicks(p, t);
            if (s > 0) {
                sup.append(sup.length() > 0 ? ", " : "").append(Lang.tr("sc.armorgui.emp.part",
                        Lang.tr("sc.armorhud.piece." + t).toLowerCase(Locale.ROOT), secs(s)));
            }
            immune = Math.max(immune, EmpSC.immuneTicks(p, t));
        }
        String state;
        int color;
        if (sup.length() > 0) {
            state = Lang.tr("sc.armorgui.emp.suppressed", sup);
            color = RED;
        } else if (immune > 0) {
            state = Lang.tr("sc.armorgui.emp.immune", secs(immune));
            color = CYAN;
        } else {
            state = Lang.tr("sc.armorgui.emp.normal");
            color = DIM;
        }
        int ty = armY + 4, tw = armW - rebootW - 6;
        int w = fit(font, Lang.tr("sc.armorgui.emp") + ": " + state, armX, ty, tw, color);
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.armorgui.emp"));
        tip.add("§7" + Lang.tr("sc.armorgui.emp.tip"));
        TextFitSC.hover(armX, armY, Math.max(w, 20), 16, tip);
        List<String> rt = new ArrayList<String>();
        rt.add(Lang.tr("sc.armorgui.emp.reboot"));
        rt.add("§7" + Lang.tr("sc.armorgui.emp.reboot.tip", EmpMathSC.REBOOT_ARGON_MB, Math.round(EmpMathSC.REBOOT_CHARGE_SHARE * 100),
                EmpMathSC.REBOOT_COOLDOWN / 20));
        int cd = EmpSC.rebootCooldownTicks(p);
        if (cd > 0) {
            rt.add("§6" + Lang.tr("sc.armorgui.tip.cooldown", secs(cd)));
        }
        TextFitSC.hover(armX + armW - rebootW, armY, rebootW, 16, rt);
    }

    /** A function of a suppressed piece (breathing excepted): grey with the «ЭМИ» mark in the armour tabs. */
    public static boolean suppressedFeature(Enum<?> f) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (!(f instanceof com.sc.util.ArmorFeature) || f == com.sc.util.ArmorFeature.AIR || p == null) {
            return false;
        }
        return EmpSC.isSuppressed(p, ((com.sc.util.ArmorFeature) f).piece);
    }

    /** Seconds left on the suppressed piece of that function (for its hover). */
    public static int suppressedSeconds(Enum<?> f) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        return p == null || !(f instanceof com.sc.util.ArmorFeature) ? 0 : secs(EmpSC.suppressedTicks(p, ((com.sc.util.ArmorFeature) f).piece));
    }

    // ------------------------------------------------------------------ helpers

    /** The aim readout if it is fresh, else null. */
    public static EmpNetSC.AimMessage freshAim() {
        EmpNetSC.AimMessage a = EmpNetSC.lastAim;
        return a != null && System.currentTimeMillis() - EmpNetSC.lastAimAt <= AIM_FRESH_MS ? a : null;
    }

    /** "шлем, нагрудник" for slot bits 0..3. */
    private static String pieces(int bits) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < 4; t++) {
            if ((bits & (1 << t)) != 0) {
                sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + t).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static int secs(int ticks) {
        return (ticks + 19) / 20;
    }

    private static String eu(long v) {
        return com.sc.util.SingularStationMath.shortAmount(v, Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"),
                Lang.tr("sc.singStation.unit.b"));
    }

    private static String num(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v).replace(".", Lang.tr("sc.num.decimal"));
    }

    private static int fit(FontRenderer font, String text, int x, int y, int maxW, int color) {
        return TextFitSC.draw(font, text, x, y, maxW, color, true, 0, 0);
    }
}
