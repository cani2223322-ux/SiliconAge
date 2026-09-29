package com.sc.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;

/** A handbook article: its chapter, icon, title, elements, and the items whose page it is (G on them opens it). */
public final class BookEntry {

    public final String id;
    public final BookChapter chapter;
    public final ItemStack icon;
    public final String title;
    public final List<BookEl> els = new ArrayList<BookEl>();
    /** The items this page is about: links and the G key on them lead here. */
    public final List<ItemStack> about = new ArrayList<ItemStack>();
    private String searchText;

    public BookEntry(String id, BookChapter chapter, ItemStack icon, String title) {
        this.id = id;
        this.chapter = chapter;
        this.icon = icon;
        this.title = title;
    }

    public BookEntry add(BookEl e) {
        els.add(e);
        return this;
    }

    public BookEntry addAll(List<BookEl> list) {
        els.addAll(list);
        return this;
    }

    public BookEntry about(ItemStack... stacks) {
        for (ItemStack s : stacks) {
            if (s != null) {
                about.add(s);
            }
        }
        return this;
    }

    /** The title and every word on the page, lower-case. */
    public String searchText() {
        if (searchText == null) {
            StringBuilder sb = new StringBuilder(title).append('\n');
            for (BookEl e : els) {
                e.collectText(sb);
            }
            searchText = sb.toString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
        }
        return searchText;
    }
}
