package com.sc.manual;

import java.util.Locale;

/** The handbook's chapters - the tiles of its first page (their titles: sc.book.ch.*). */
public enum BookChapter {
    INTRO, PATH, ORES, SILICON, MACHINES, GENERATORS, BRIDGE, ENERGY, ARMOR, FIELD, SAFETY, MATERIALS, REFERENCE, RECIPES;

    public String title() {
        return Lang.tr("sc.book.ch." + name().toLowerCase(Locale.ROOT));
    }

    public String description() {
        return Lang.tr("sc.book.ch." + name().toLowerCase(Locale.ROOT) + ".desc");
    }
}
