package com.sc.manual;

import java.util.Locale;

/** The handbook's chapters - the tiles of its first page (their titles: sc.book.ch.*). */
public enum BookChapter {
    INTRO, ORES, SILICON, MACHINES, GENERATORS, ENERGY, ARMOR, FIELD, SAFETY, RECIPES;

    public String title() {
        return Lang.tr("sc.book.ch." + name().toLowerCase(Locale.ROOT));
    }

    public String description() {
        return Lang.tr("sc.book.ch." + name().toLowerCase(Locale.ROOT) + ".desc");
    }
}
