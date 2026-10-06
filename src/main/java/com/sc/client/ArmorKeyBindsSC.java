package com.sc.client;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.sc.handler.ArmorNetSC;
import com.sc.item.ArmorLogicSC;
import com.sc.item.BladeLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBladeSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.BladeFeature;
import com.sc.util.PowerModeKey;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

/**
 * A key (or key combination with Ctrl / Shift / Alt, or a middle / side mouse button) for every
 * suit function (ArmorFeature) and every energy blade function (BladeFeature), set in the armour
 * screen (GuiArmorSC) and kept in this player's own config file. Pressing it switches the function
 * on or off - or, for dash, the annihilation pulse and the blade's sweep / wave / lunge, fires it.
 */
public final class ArmorKeyBindsSC {

    public static final int CTRL = 1, SHIFT = 2, ALT = 4;
    /** Mouse buttons are stored like vanilla's: -100 + button. */
    public static final int MOUSE_BASE = -100;

    private static final Map<Enum<?>, int[]> BINDS = new LinkedHashMap<Enum<?>, int[]>();
    private static final Map<Enum<?>, Boolean> WAS_DOWN = new HashMap<Enum<?>, Boolean>();
    private static Configuration config;

    private ArmorKeyBindsSC() {
    }

    /** The config key: the suit's functions by name, the blade's as "blade_" + name. */
    private static String configKey(Enum<?> f) {
        String name = f.name().toLowerCase(java.util.Locale.ROOT);
        return f instanceof BladeFeature ? "blade_" + name : f instanceof PowerModeKey ? "mode_" + name
                : f instanceof com.sc.util.DrillFeature ? "drill_" + name : name;
    }

    public static void load(File configDir) {
        File file = new File(configDir, "SiliconAge_armorkeys.cfg");
        File old = new File(configDir, "siliconcircuitry_armorkeys.cfg");        // the name before the mod was renamed
        if (!file.exists() && old.exists() && !old.renameTo(file)) {
            file = old;
        }
        config = new Configuration(file);
        config.load();
        java.util.List<Enum<?>> all = new java.util.ArrayList<Enum<?>>();
        java.util.Collections.addAll(all, ArmorFeature.values());
        java.util.Collections.addAll(all, BladeFeature.values());
        java.util.Collections.addAll(all, PowerModeKey.values());
        java.util.Collections.addAll(all, com.sc.util.DrillFeature.values());
        for (Enum<?> f : all) {
            String v = config.get("keys", configKey(f), "",
                    "key:modifiers (modifiers: 1 Ctrl, 2 Shift, 4 Alt; mouse buttons are -100 + button)").getString();
            int[] b = parse(v);
            if (b != null) {
                BINDS.put(f, b);
            }
        }
        String pos = config.get("hud", "singularCooldowns", HUD_POS[HUD_HOTBAR],
                "Singular suit cooldown icons (M3): off, hotbar (above the hotbar), top, right, left").getString();
        hudPos = HUD_HOTBAR;
        for (int i = 0; i < HUD_POS.length; i++) {
            if (HUD_POS[i].equalsIgnoreCase(pos.trim())) {
                hudPos = i;
            }
        }
        if (config.hasChanged()) {
            config.save();
        }
    }

    /** М3: where the Singular cooldown icons go (the K menu's Level tab cycles it; kept in this file, "hud"). */
    public static final String[] HUD_POS = {"off", "hotbar", "top", "right", "left"};
    public static final int HUD_OFF = 0, HUD_HOTBAR = 1, HUD_TOP = 2, HUD_RIGHT = 3, HUD_LEFT = 4;
    private static int hudPos = HUD_HOTBAR;

    public static int hudPos() {
        return hudPos;
    }

    /** The next place (off - above the hotbar - top - right - left), saved. */
    public static void cycleHudPos() {
        hudPos = (hudPos + 1) % HUD_POS.length;
        if (config != null) {
            config.get("hud", "singularCooldowns", HUD_POS[HUD_HOTBAR]).set(HUD_POS[hudPos]);
            config.save();
        }
    }

    private static int[] parse(String v) {
        if (v == null || !v.contains(":")) {
            return null;
        }
        try {
            String[] p = v.split(":");
            int[] r = {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
            for (int k : r) {
                if (k != 0 && !(k > 0 && k < 256) && !(k >= -100 && k < -84)) {
                    return null;                           // no such key: Keyboard.isKeyDown would throw every tick
                }
            }
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int[] get(Enum<?> f) {
        return BINDS.get(f);
    }

    public static void set(Enum<?> f, int key, int mods) {
        BINDS.put(f, new int[]{key, mods});
        save(f, key + ":" + mods);
    }

    public static void clear(Enum<?> f) {
        BINDS.remove(f);
        save(f, "");
    }

    private static void save(Enum<?> f, String value) {
        if (config != null) {
            config.get("keys", configKey(f), "").set(value);
            config.save();
        }
    }

    /** Ctrl / Shift / Alt held right now. */
    public static int modifiersDown() {
        int m = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            m |= CTRL;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            m |= SHIFT;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            m |= ALT;
        }
        return m;
    }

    public static boolean isModifierKey(int key) {
        return key == Keyboard.KEY_LCONTROL || key == Keyboard.KEY_RCONTROL || key == Keyboard.KEY_LSHIFT
                || key == Keyboard.KEY_RSHIFT || key == Keyboard.KEY_LMENU || key == Keyboard.KEY_RMENU;
    }

    /** "Ctrl+Shift+N", "Mouse 4", or a dash for none. */
    public static String describe(int[] b) {
        if (b == null) {
            return Lang.tr("sc.armorgui.bind.none");
        }
        StringBuilder sb = new StringBuilder();
        if ((b[1] & CTRL) != 0) {
            sb.append("Ctrl+");
        }
        if ((b[1] & SHIFT) != 0) {
            sb.append("Shift+");
        }
        if ((b[1] & ALT) != 0) {
            sb.append("Alt+");
        }
        sb.append(b[0] < 0 ? "Mouse " + (b[0] - MOUSE_BASE + 1) : Keyboard.getKeyName(b[0]));
        return sb.toString();
    }

    private static boolean isDown(int[] b) {
        boolean key = b[0] < 0 ? Mouse.isButtonDown(b[0] - MOUSE_BASE) : b[0] > 0 && Keyboard.isKeyDown(b[0]);
        return key && modifiersDown() == b[1];
    }

    /** A function's name for the armour screen, with its tab ("Blade: Sweep", "Mode: Combat"). */
    public static String nameOf(Enum<?> f) {
        if (f instanceof BladeFeature) {
            return Lang.tr("sc.bladegui.tab") + ": " + Lang.tr("sc.bladefn." + ((BladeFeature) f).key());
        }
        if (f instanceof com.sc.util.DrillFeature) {
            return Lang.tr("sc.drillgui.tab") + ": " + Lang.tr("sc.drillfn." + ((com.sc.util.DrillFeature) f).key());
        }
        if (f instanceof PowerModeKey) {
            PowerModeKey k = (PowerModeKey) f;
            return Lang.tr("sc.modegui.tab") + ": " + (k == PowerModeKey.CYCLE ? Lang.tr("sc.modegui.cycle.name") : Lang.tr("sc.armorgui.mode." + k.mode()));
        }
        ArmorFeature a = (ArmorFeature) f;
        return Lang.tr("sc.armorhud.piece." + a.piece) + ": " + Lang.tr("sc.armorfn." + a.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * What else the function's key is taken by: another suit / blade / drill / mode function with the
     * same key and modifiers, or a vanilla (or another mod's) KeyBinding on the same key - those
     * ignore Ctrl / Shift / Alt, so they fire together whatever the modifiers. Empty when none or unbound.
     */
    public static java.util.List<String> conflicts(Enum<?> f) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        int[] b = BINDS.get(f);
        if (b == null || b[0] == 0) {
            return out;
        }
        for (Map.Entry<Enum<?>, int[]> e : BINDS.entrySet()) {
            if (e.getKey() != f && e.getValue()[0] == b[0] && e.getValue()[1] == b[1]) {
                out.add(nameOf(e.getKey()));
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameSettings != null && mc.gameSettings.keyBindings != null) {
            for (net.minecraft.client.settings.KeyBinding kb : mc.gameSettings.keyBindings) {
                if (kb != null && kb.getKeyCode() == b[0]) {
                    out.add(net.minecraft.client.resources.I18n.format(kb.getKeyDescription()));
                }
            }
        }
        return out;
    }

    /** Client tick while playing (no screen open): fire every binding that has just been pressed. */
    public static void tick(Minecraft mc) {
        for (Map.Entry<Enum<?>, int[]> e : BINDS.entrySet()) {
            Enum<?> f = e.getKey();
            boolean down = isDown(e.getValue());
            Boolean before = WAS_DOWN.put(f, down);
            if (down && !Boolean.TRUE.equals(before)) {
                fire(mc, f);
            }
        }
    }

    public static void fire(Minecraft mc, Enum<?> f) {
        if (f instanceof BladeFeature) {
            fireBlade(mc, (BladeFeature) f);
        } else if (f instanceof PowerModeKey) {
            fireMode(mc, (PowerModeKey) f);
        } else if (f instanceof com.sc.util.DrillFeature) {
            fireDrill(mc, (com.sc.util.DrillFeature) f);
        } else {
            fireArmor(mc, (ArmorFeature) f);
        }
    }

    private static void fireArmor(Minecraft mc, ArmorFeature f) {
        String name = Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT));
        if (f == ArmorFeature.DASH) {
            if (ArmorLogicSC.active(mc.thePlayer, f)) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DASH, 0));
            }
            return;
        }
        if (f == ArmorFeature.ANNIHILATION) {
            if (ArmorLogicSC.active(mc.thePlayer, f)) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.ANNIHILATE, 0));
            } else {
                mc.ingameGUI.func_110326_a(Lang.tr("sc.armorkey.unavailable", name), false);
            }
            return;
        }
        ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, f.piece);
        if (piece == null || !f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.armorkey.unavailable", name), false);
            return;
        }
        if (!com.sc.util.SingularLevel.unlocked(mc.thePlayer, f, piece)) {        // a Singular function above the piece's level
            mc.ingameGUI.func_110326_a(Lang.tr("sc.armorkey.locked", name, com.sc.util.SingularLevel.requiredLevel(f)), false);
            return;
        }
        if (f == ArmorFeature.PHASE_DASH) {                                         // the server checks the cooldown and the gas, and says why not
            if (ArmorLogicSC.active(mc.thePlayer, f)) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.PHASE_DASH, 0));
            } else {
                mc.ingameGUI.func_110326_a(Lang.tr(ItemArmorSC.isEnabled(piece, f) ? "sc.armorkey.unavailable" : "sc.armorkey.off", name), false);
            }
            return;
        }
        byte action = ArmorNetSC.actionOf(f);
        if (action >= 0) {                  // the stage 2b key functions: the server checks the rest (level, cooldown, gases, charge) and says why not
            if (ItemArmorSC.isEnabled(piece, f)) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(action, 0));
            } else {
                mc.ingameGUI.func_110326_a(Lang.tr("sc.armorkey.off", name), false);
            }
            return;
        }
        boolean want = !ItemArmorSC.isEnabled(piece, f);
        ItemArmorSC.setEnabled(piece, f, want);
        ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.TOGGLE, f.ordinal(), want));
        mc.ingameGUI.func_110326_a(Lang.tr(want ? "sc.armorkey.on" : "sc.armorkey.off", name), false);
    }

    /** The power mode (kept on the chestplate): the next one, or straight to one - again to go back to normal. */
    public static void fireMode(Minecraft mc, PowerModeKey k) {
        ItemStack chest = ArmorLogicSC.piece(mc.thePlayer, 1);
        if (chest == null) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.modekey.nochest"), false);
            return;
        }
        int want = k.next(ItemArmorSC.powerMode(chest));
        ItemArmorSC.setPowerMode(chest, want);                                // shown at once
        ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.POWER_MODE, want));
        mc.ingameGUI.func_110326_a(Lang.tr("sc.armorgui.mode", Lang.tr("sc.armorgui.mode." + want)), false);
    }

    /** The blade in hand: fire a key function, or switch a function (the blade itself too). */
    private static void fireDrill(Minecraft mc, com.sc.util.DrillFeature f) {
        String name = Lang.tr("sc.drillfn." + f.key());
        ItemStack drill = com.sc.item.DrillLogicSC.held(mc.thePlayer);
        if (drill == null || !f.availableIn(com.sc.item.ItemDrillSC.typeOf(drill))) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.drillkey.unavailable", name), false);
            return;
        }
        if (toolLocked(mc, drill, f, name)) {
            return;
        }
        if (f.isAction()) {                 // every key function, LASER too: one generic message; the server checks the rest and says why not
            if (com.sc.item.ItemDrillSC.isEnabled(drill, f)) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DRILL_ACTION, f.ordinal()));
            } else {
                mc.ingameGUI.func_110326_a(Lang.tr("sc.armorkey.off", name), false);
            }
            return;
        }
        boolean want = !com.sc.item.ItemDrillSC.isEnabled(drill, f);
        com.sc.item.ItemDrillSC.setEnabled(drill, f, want);
        ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DRILL_TOGGLE, f.ordinal(), want));
        mc.ingameGUI.func_110326_a(Lang.tr(want ? "sc.armorkey.on" : "sc.armorkey.off", name), false);
    }

    private static void fireBlade(Minecraft mc, BladeFeature f) {
        String name = Lang.tr("sc.bladefn." + f.key());
        ItemStack blade = BladeLogicSC.held(mc.thePlayer);
        if (blade == null || !f.availableIn(ItemBladeSC.typeOf(blade))) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.bladekey.unavailable", name), false);
            return;
        }
        if (toolLocked(mc, blade, f, name)) {
            return;
        }
        if (f.isAction()) {                 // every key function (sweep / wave / lunge too): one generic message
            if (BladeLogicSC.active(mc.thePlayer, f)) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BLADE_ACTION, f.ordinal()));
            } else {
                mc.ingameGUI.func_110326_a(Lang.tr(ItemBladeSC.isLit(blade) ? "sc.bladekey.unavailable" : "sc.bladekey.dark", name), false);
            }
            return;
        }
        if (f == BladeFeature.BLADE && ItemBladeSC.overheated(blade) && !ItemBladeSC.isEnabled(blade, f)) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.bladekey.hot"), false);
            return;
        }
        boolean want = !ItemBladeSC.isEnabled(blade, f);
        ItemBladeSC.setEnabled(blade, f, want);
        ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BLADE_TOGGLE, f.ordinal(), want));
        mc.ingameGUI.func_110326_a(Lang.tr(want ? "sc.armorkey.on" : "sc.armorkey.off", name), false);
    }

    /**
     * A Singular tool's function its level or branch keeps closed (BladeLogicSC / DrillLogicSC.unlocked):
     * says so on the action bar («с ур. N» / «нужна ветка») and returns true.
     */
    private static boolean toolLocked(Minecraft mc, ItemStack tool, Enum<?> f, String name) {
        boolean blade = f instanceof BladeFeature;
        boolean open = blade ? BladeLogicSC.unlocked(mc.thePlayer, tool, (BladeFeature) f)
                : com.sc.item.DrillLogicSC.unlocked(mc.thePlayer, tool, (com.sc.util.DrillFeature) f);
        if (open) {
            return false;
        }
        int level = blade ? ((BladeFeature) f).singLevel() : ((com.sc.util.DrillFeature) f).singLevel();
        int branch = blade ? ((BladeFeature) f).branch() : ((com.sc.util.DrillFeature) f).branch();
        if (branch != 0 && com.sc.util.ToolLevelSC.effectiveLevel(mc.thePlayer, tool) >= level) {   // the level is there: the branch isn't
            mc.ingameGUI.func_110326_a(Lang.tr("sc.toolkey.branch", name, Lang.tr(com.sc.util.ToolLevelSC.branchLangKey(tool, branch))), false);
        } else {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.armorkey.locked", name, level), false);
        }
        return true;
    }

    /** While a screen is open nothing fires, and a key held when it closes doesn't fire either. */
    public static void markAllDown() {
        for (Map.Entry<Enum<?>, int[]> e : BINDS.entrySet()) {
            WAS_DOWN.put(e.getKey(), isDown(e.getValue()));
        }
    }
}
