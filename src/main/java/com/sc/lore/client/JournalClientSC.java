package com.sc.lore.client;

import com.sc.finale.FinaleItemsSC;
import com.sc.lore.JournalSC;
import com.sc.manual.BookChapter;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.GuiBook;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §6.1, rule ПЛ-1): a Lithographer's Journal's pages - the handbook's screen (GuiBook) on
 * an article built here on the fly (a BookEntry "journal.<n>" of chapter WORLD, never in BookContent's lists: the journals are read
 * by finding them, not from the handbook's index or search) - its title (sc.journal.<n>.title), the date line (.date, dim), the pages
 * (.p0 .. - JournalSC.PAGES, paragraphs), the cover's icon. ClientProxy.openJournal calls open (the client thread). Fixed: the class,
 * open; the body J's. Client only.
 * <p>
 * The article's header is the item's name («Журнал Литографа №N», its cover as the icon), its first line the journal's own title. It
 * is built anew at every opening (the language may have changed); a page the lang lacks is left out - no raw key on the paper. Closing
 * the book goes back to the world; the handbook opened later starts at its first page (the journal's article is in no chapter's list).
 */
@SideOnly(Side.CLIENT)
public final class JournalClientSC {

    private JournalClientSC() {
    }

    /** Journal №`number`'s pages on the screen. */
    public static void open(int number) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        mc.displayGuiScreen(new GuiBook(entry(number)));
    }

    /** Journal №`number`'s article (an unknown number - №1's). */
    public static BookEntry entry(int number) {
        int n = number >= 1 && number <= JournalSC.COUNT ? number : 1;
        String key = "sc.journal." + n;
        BookEntry e = new BookEntry("journal." + n, BookChapter.WORLD, FinaleItemsSC.journal(n), Lang.tr("item.siliconage.journalSC.name", n));
        e.add(BookEl.title(Lang.tr(StatCollector.canTranslate(key + ".title") ? key + ".title" : "sc.tooltip.journal")));
        if (StatCollector.canTranslate(key + ".date")) {
            e.add(BookEl.dim(Lang.tr(key + ".date")));
        }
        int pages = n - 1 < JournalSC.PAGES.length ? JournalSC.PAGES[n - 1] : 0;
        for (int p = 0; p < pages; p++) {
            String page = key + ".p" + p;
            if (StatCollector.canTranslate(page)) {
                e.add(BookEl.para(Lang.tr(page)));
            }
        }
        return e;
    }
}
