package com.sc.settle.npc;

import java.util.Random;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.1, rule ПЖ-6): the names of the settlements' creatures and of the
 * settlements themselves - an element and a number, «Тантал-3», «Ниобий-7». Kept as a code: (element + 1) x 1000 +
 * number (0 = no name), in the registry's Slot and the entities' DataWatcher; turned into words by each client
 * (sc.settle.element.<key>, sc.settle.npcName = "%s-%s") - a Russian server, an English guest, both read their own.
 * Pure; the self-test asks it. ELEMENTS is append only (the codes are saved).
 */
public final class NpcNamesSC {

    /** The elements, by index (lang sc.settle.element.<key>). Append only. */
    public static final String[] ELEMENTS = {"tantalum", "niobium", "hafnium", "germanium", "gallium", "indium", "tungsten",
            "zirconium", "titanium", "cobalt", "nickel", "platinum", "palladium", "iridium", "osmium", "rhenium", "molybdenum",
            "vanadium", "yttrium", "lanthanum", "cerium", "neodymium", "scandium", "bismuth", "selenium", "tellurium", "antimony",
            "beryllium", "lithium", "silicon"};
    /** Numbers 1..MAX_NUMBER. */
    public static final int MAX_NUMBER = 99;

    private NpcNamesSC() {
    }

    /** The code of element `element` (an ELEMENTS index) and `number` (1..999); 0 for a bad pair. */
    public static int code(int element, int number) {
        if (element < 0 || element >= ELEMENTS.length || number < 1 || number > 999) {
            return 0;
        }
        return (element + 1) * 1000 + number;
    }

    /** The element index of a code, -1 none. */
    public static int element(int code) {
        int e = code / 1000 - 1;
        return code <= 0 || e < 0 || e >= ELEMENTS.length ? -1 : e;
    }

    /** The number of a code, 0 none. */
    public static int number(int code) {
        return element(code) < 0 ? 0 : code % 1000;
    }

    /** A random name's code. */
    public static int random(Random r) {
        return code(r.nextInt(ELEMENTS.length), 1 + r.nextInt(MAX_NUMBER));
    }

    /** The name as a chat component every client translates («Тантал-3»); a code 0: «?». */
    public static IChatComponent name(int code) {
        int e = element(code);
        if (e < 0) {
            return new ChatComponentTranslation("sc.settle.npcName.none");
        }
        return new ChatComponentTranslation("sc.settle.npcName", new ChatComponentTranslation("sc.settle.element." + ELEMENTS[e]),
                String.valueOf(number(code)));
    }
}
