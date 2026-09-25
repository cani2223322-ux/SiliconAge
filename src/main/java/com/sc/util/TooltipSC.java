package com.sc.util;

import java.util.List;

import org.lwjgl.input.Keyboard;

import com.sc.manual.Lang;

/**
 * Tooltips in Thermal Expansion's style: a short summary, "hold Shift" for the details, "hold Ctrl"
 * for how to use / charge it; long lines wrapped. Only ever called from addInformation (client), so
 * LWJGL is never touched on a dedicated server.
 */
public final class TooltipSC {

    /** Visible characters per tooltip line before wrapping. */
    private static final int WIDTH = 44;

    private TooltipSC() {
    }

    public static boolean shift() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    public static boolean ctrl() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    /** Which part to show: 0 the summary (+ hints), 1 the details (Shift), 2 the controls (Ctrl). */
    public static int page() {
        return ctrl() ? 2 : shift() ? 1 : 0;
    }

    public static void hintShift(List list) {
        list.add(Lang.tr("sc.tooltip.hold.shift"));
    }

    public static void hintCtrl(List list) {
        list.add(Lang.tr("sc.tooltip.hold.ctrl"));
    }

    /** Adds `text` wrapped at WIDTH visible characters (colour codes don't count), every line in `color`. */
    public static void wrap(List list, String text, String color) {
        StringBuilder line = new StringBuilder();
        int visible = 0;
        for (String word : text.split(" ")) {
            int len = visibleLength(word);
            if (visible > 0 && visible + 1 + len > WIDTH) {
                list.add(color + line.toString());
                line.setLength(0);
                visible = 0;
            }
            if (visible > 0) {
                line.append(' ');
                visible++;
            }
            line.append(word);
            visible += len;
        }
        if (line.length() > 0) {
            list.add(color + line.toString());
        }
    }

    /** Items two to a line ("+ name" green when on, "- name" grey when off). */
    public static void pairs(List list, List<String> names, List<Boolean> on) {
        for (int i = 0; i < names.size(); i += 2) {
            String a = mark(names.get(i), on.get(i));
            if (i + 1 < names.size()) {
                int pad = Math.max(1, 20 - visibleLength(names.get(i)));
                StringBuilder sp = new StringBuilder();
                for (int k = 0; k < pad; k++) {
                    sp.append(' ');
                }
                list.add(a + sp + mark(names.get(i + 1), on.get(i + 1)));
            } else {
                list.add(a);
            }
        }
    }

    private static String mark(String name, boolean on) {
        return (on ? "§a+ " : "§8- ") + name;
    }

    private static int visibleLength(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) {
                i++;
            } else {
                n++;
            }
        }
        return n;
    }
}
