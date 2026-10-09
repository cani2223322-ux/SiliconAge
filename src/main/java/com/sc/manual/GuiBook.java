package com.sc.manual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.sc.machine.MachineRecipe;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * The illustrated handbook (variant "В"): a first page of chapter tiles (with the bookmarks and
 * the first steps' progress), then a chapter - its articles listed on the left with their icons,
 * the open article on paper on the right, scrolled: headings, paragraphs, item icons (hover: the
 * tooltip, click: its page or, with NEI, its recipe), crafting grids, machine recipes, ore
 * routes, ore cards, tables, multiblocks layer by layer, pictures, the first steps' ticks.
 * The search box (Ctrl+F or /) looks through every article's title and text (BookSearchSC; in the
 * Recipes chapter: every recipe) and lists the hits with a snippet; Enter / a click opens one at its
 * first match. Back, Home, the bookmark list and a bookmark star in the header; Backspace goes back,
 * Esc clears the search or closes back to where the book was opened from (E too).
 */
@SideOnly(Side.CLIENT)
public class GuiBook extends GuiScreen {

    private static final int HEADER = 18, LIST_W = 124, ROW = 14, PAD = 6;
    private static final int COVER = 0xFF1C2740, COVER_EDGE = 0xFF0E1526, PAPER = 0xFFF3EBD6, PAPER_EDGE = 0xFFC9B993,
            INK = 0x2A2622, INK_DIM = 0x6A6258, INK_HEAD = 0x1C3A6A, LINK = 0x1F5FB0, GOLD = 0xFFE6C850;

    /** Where the book was left (it opens there again). */
    private static String lastState = "home";

    private BookChapter chapter;
    private BookEntry entry;
    private final Deque<String> history = new ArrayDeque<String>();
    private GuiTextField search;
    private String query = "";
    private int listScroll, pageScroll, pageMax, listMax;
    private List<BookEntry> listed = new ArrayList<BookEntry>();
    private List<BookEl> recipeEls;
    private String recipeQuery;

    // search results (parallel to `listed`), the bookmark list, the jump to a match
    private static final int RES_ROW = 24, MARK_ROW = 18, FLASH_MS = 1800;
    private List<BookSearchSC.Hit> hits = new ArrayList<BookSearchSC.Hit>();
    private int hitTotal, resSel, resScroll, resMax, resRows = 1, marksScroll, marksMax;
    /** The right side shows the hit list (else the open article); the bookmark list fills the book. */
    private boolean resultsView, marksView;
    private int jumpEl = -1, flashEl = -1;
    private long flashUntil, toastUntil;
    private String toast;

    // frame state: the book's box, what the mouse is over, what a click does
    private int bx, by, bw, bh, mx, my;
    private ItemStack hoverStack;
    private List<String> hoverLines;
    private final List<int[]> clickBoxes = new ArrayList<int[]>();
    private final List<Object> clickActions = new ArrayList<Object>();

    /** The screen the book was opened from (Esc, E and the book key go back there); null: the world. */
    private GuiScreen parent;

    public GuiBook() {
        this((BookEntry) null);
    }

    /** Open at an article (the G key on an item), or where the book was left. */
    public GuiBook(BookEntry at) {
        if (at != null) {
            show(at);
        } else {
            restore(lastState);
        }
    }

    /** Open on the search for `q` (the G key on an item with no article of its own); no hits: an empty result. */
    public static GuiBook search(String q) {
        GuiBook b = new GuiBook((BookEntry) null);
        b.entry = null;
        b.chapter = null;
        b.pageScroll = 0;
        b.recipeEls = null;
        String n = q == null ? "" : net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(q).trim();
        b.query = n.length() > 32 ? n.substring(0, 32) : n;
        b.marksView = false;
        if (b.searching()) {
            b.runSearch();
            b.resultsView = true;                                     // no hits: the empty list
            if (!b.hits.isEmpty()) {
                b.open(b.hits.get(0));                                // Backspace: the whole list
            }
        }
        return b;
    }

    /** Remember the screen the book was opened from. */
    public GuiBook from(GuiScreen p) {
        parent = p;
        return this;
    }

    /** Close: back to the screen it was opened from, or the world. */
    private void close() {
        GuiScreen p = parent;
        parent = null;
        mc.displayGuiScreen(p);
    }

    /**
     * Back from NEI's recipes: NEI's close may have shut the server's container, so a container screen
     * (other than the player's own inventory) can't be gone back to any more - then Esc goes to the world.
     */
    public void afterNei() {
        if (parent instanceof net.minecraft.client.gui.inventory.GuiContainer && mc != null && mc.thePlayer != null
                && !(parent instanceof net.minecraft.client.gui.inventory.GuiContainerCreative)
                && !parent.getClass().getName().startsWith("codechicken.nei.recipe.")
                && ((net.minecraft.client.gui.inventory.GuiContainer) parent).inventorySlots != mc.thePlayer.inventoryContainer) {
            // close it on the server too: else it keeps the container open and the inventory clicks stop matching
            mc.thePlayer.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0DPacketCloseWindow(
                    ((net.minecraft.client.gui.inventory.GuiContainer) parent).inventorySlots.windowId));
            mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
            parent = null;
        }
    }

    /** NEI's recipes for an item; when NEI's screen is closed the book opens again on this page. */
    private void nei(ItemStack s) {
        com.sc.nei.NeiBookSC.recipes(s);
        if (mc.currentScreen != this && mc.currentScreen != null) {
            com.sc.client.BookKeySC.returnAfterNei(this);
        }
    }

    // ------------------------------------------------------------------ navigation

    private String state() {
        if (marksView) {
            return "marks";
        }
        if (resultsView && searching()) {
            return "s:" + query;
        }
        if (entry != null && entry.chapter == BookChapter.RECIPES && chapter == BookChapter.RECIPES
                && query.trim().length() > 0) {
            return "r:" + query;                                      // ИФ-2: the recipe search keeps its query
        }
        if (entry != null) {
            return "e:" + entry.id;
        }
        return chapter != null ? "c:" + chapter.name() : "home";
    }

    private void restore(String s) {
        entry = null;
        chapter = null;
        resultsView = false;
        marksView = false;
        jumpEl = -1;
        flashEl = -1;
        pageScroll = 0;
        recipeEls = null;
        if (s.startsWith("s:")) {                                     // the hit list (not remembered as lastState)
            setQuery(s.substring(2));
            if (searching()) {
                resultsView = true;
                runSearch();
                return;
            }
            s = "home";
        } else if ("marks".equals(s)) {
            marksView = true;
            return;
        } else if (s.startsWith("r:")) {                              // the "Recipes" chapter with its query
            setQuery(s.substring(2));
            s = "e:recipes";
        }
        if (s.startsWith("e:")) {
            BookEntry e = BookContent.byId(s.substring(2));
            if (e != null) {
                entry = e;
                chapter = e.chapter;
            }
        } else if (s.startsWith("c:")) {
            try {
                chapter = BookChapter.valueOf(s.substring(2));
            } catch (IllegalArgumentException ex) {
                chapter = null;
            }
        }
        if (chapter != null && entry == null) {
            List<BookEntry> in = BookContent.chapter(chapter);
            entry = in.isEmpty() ? null : in.get(0);
        }
        pageScroll = 0;
        recipeEls = null;
        lastState = state();
    }

    private void go(String s) {
        String now = state();
        if (!now.equals(s)) {
            history.push(now);
        }
        restore(s);
        if (!s.startsWith("s:") && !s.startsWith("r:")) {
            setQuery("");
        }
        scrollListTo();
    }

    private void setQuery(String q) {
        query = q;
        if (search != null && !search.getText().equals(q)) {
            search.setText(q);
        }
    }

    private void show(BookEntry e) {
        entry = e;
        chapter = e.chapter;
        pageScroll = 0;
        resultsView = false;
        marksView = false;
        jumpEl = -1;
        flashEl = -1;
        lastState = state();
    }

    /** A hit: its article, scrolled to the first match's element, which glows a moment. */
    private void open(BookSearchSC.Hit h) {
        BookEntry e = BookSearchSC.entry(h);
        if (e == null) {
            return;
        }
        String now = state();
        if (!now.equals("e:" + e.id)) {
            history.push(now);
        }
        show(e);
        jumpEl = h.el;
        flashEl = h.el;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
        scrollListTo();
    }

    private void back() {
        if (!history.isEmpty()) {
            restore(history.pop());
            if (searching() && !resultsView) {
                runSearch();
            }
            scrollListTo();
        }
    }

    /** Out of the hit list: back where the search was started from. */
    private void leaveResults() {
        resultsView = false;
        String p = history.isEmpty() ? "home" : history.pop();
        String q = query;
        restore(p.startsWith("s:") ? "home" : p);
        setQuery(q);
        scrollListTo();
    }

    /** Esc in the search: the query cleared, the list left. */
    private void clearSearch() {
        boolean wasList = resultsView && searching();
        setQuery("");
        search.setFocused(false);
        if (wasList) {
            leaveResults();
        }
    }

    /** Runs the query over the book: the hits and their articles (the list on the left). */
    private void runSearch() {
        int[] total = new int[1];
        List<BookSearchSC.Hit> found = BookSearchSC.searchBook(query, total);
        hits = new ArrayList<BookSearchSC.Hit>();
        listed = new ArrayList<BookEntry>();
        for (BookSearchSC.Hit h : found) {
            BookEntry e = BookSearchSC.entry(h);
            if (e != null) {
                hits.add(h);
                listed.add(e);
            }
        }
        hitTotal = Math.max(total[0], hits.size());
        resSel = Math.max(0, Math.min(resSel, hits.size() - 1));
    }

    private void moveSel(int d) {
        if (hits.isEmpty()) {
            return;
        }
        resSel = Math.max(0, Math.min(hits.size() - 1, resSel + d));
        if (resSel < resScroll) {
            resScroll = resSel;
        } else if (resSel >= resScroll + resRows) {
            resScroll = resSel - resRows + 1;
        }
        int rows = Math.max(1, (bh - HEADER - 2 * PAD) / ROW);
        if (resSel < listScroll) {
            listScroll = resSel;
        } else if (resSel >= listScroll + rows) {
            listScroll = resSel - rows + 1;
        }
    }

    private void scrollListTo() {
        if (entry == null) {
            listScroll = 0;
            return;
        }
        int i = (searching() ? listed : BookContent.chapter(entry.chapter)).indexOf(entry);
        int rows = Math.max(1, (bh - HEADER - 2 * PAD) / ROW);
        if (i >= 0 && (i < listScroll || i >= listScroll + rows)) {
            listScroll = Math.max(0, i - rows / 2);
        }
    }

    /** An item's click: its page, or (NEI) its recipe. */
    private void openStack(ItemStack s) {
        BookEntry e = BookContent.entryFor(s);
        if (e != null && e != entry) {
            go("e:" + e.id);
        } else if (e == null && cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) {
            nei(s);
        }
    }

    // ------------------------------------------------------------------ screen

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        bw = Math.min(width - 8, 470);
        bh = Math.min(height - 8, 280);
        bx = (width - bw) / 2;
        by = (height - bh) / 2;
        search = new GuiTextField(fontRendererObj, bx + bw - 120, by + 3, 115, 13);
        search.setMaxStringLength(32);
        search.setText(query);
        search.setEnableBackgroundDrawing(false);
        search.setTextColor(0xE6F0FA);
        scrollListTo();
        openedAt = System.currentTimeMillis();
    }

    /** When the screen opened: the book key held a moment longer mustn't close it again or type into search. */
    private long openedAt;

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    private boolean searching() {
        return query.trim().length() >= 2 && chapter != BookChapter.RECIPES;
    }

    @Override
    protected void keyTyped(char c, int key) {
        boolean focused = search.isFocused(), list = resultsView && searching();
        if (key == Keyboard.KEY_ESCAPE) {
            if (!search.getText().isEmpty()) {
                clearSearch();                                        // 1st Esc: the search (also from an opened hit), 2nd: the book
            } else {
                close();
            }
            return;
        }
        if (key == Keyboard.KEY_F && isCtrlKeyDown() || c == '/' && !focused) {
            search.setFocused(true);
            search.setCursorPositionEnd();
            search.setSelectionPos(0);
            return;
        }
        if (list && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT)) {
            moveSel(key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1 : key == Keyboard.KEY_PRIOR ? -resRows : resRows);
            return;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && searching()) {
            if (!hits.isEmpty()) {
                click();
                open(hits.get(list ? Math.max(0, Math.min(resSel, hits.size() - 1)) : 0));
                search.setFocused(false);
            }
            return;
        }
        int bookKey = com.sc.client.BookKeySC.KEY_BOOK.getKeyCode();
        if (bookKey == Keyboard.KEY_NONE) {
            bookKey = Integer.MIN_VALUE;                              // unbound: a letter with no key code must not close the book
        }
        // Кн-3: while a search is typed (field focused or a query kept) the book and inventory keys are letters
        boolean typing = search.isFocused() || !search.getText().isEmpty();
        if (key == bookKey && !typing && (Keyboard.isRepeatEvent() || System.currentTimeMillis() - openedAt < 400)) {
            return;                                                   // the key that opened the book, still held
        }
        if (key == bookKey && !typing) {
            close();
            return;
        }
        int invKey = mc.gameSettings.keyBindInventory.getKeyCode();
        if (key == invKey && invKey != Keyboard.KEY_NONE && !typing) {
            close();                                                  // the inventory key closes the book like Esc
            return;
        }
        if (!search.isFocused() && key == Keyboard.KEY_BACK) {
            back();
            return;
        }
        if (!search.isFocused() && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT)) {
            int d = key == Keyboard.KEY_UP ? -10 : key == Keyboard.KEY_DOWN ? 10 : key == Keyboard.KEY_PRIOR ? -120 : 120;
            pageScroll = Math.max(0, Math.min(pageMax, pageScroll + d));
            return;
        }
        if (!search.isFocused() && c >= ' ' && !Character.isISOControl(c)) {
            search.setFocused(true);                                 // typing anywhere searches
        }
        String before = state();
        if (search.textboxKeyTyped(c, key) && !search.getText().equals(query)) {
            query = search.getText();
            listScroll = 0;
            resSel = 0;
            resScroll = 0;
            if (searching()) {
                if (!resultsView) {
                    history.push(before);                             // Esc / a short query: back here
                    resultsView = true;
                    marksView = false;
                }
                runSearch();
            } else if (resultsView) {
                leaveResults();
            }
            if (query.isEmpty()) {
                search.setFocused(false);                             // Backspace goes back again
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int step = wheel > 0 ? -1 : 1;
        if (marksView) {
            marksScroll = Math.max(0, Math.min(marksMax, marksScroll + step));
        } else if (chapter != null || searching()) {
            boolean list = resultsView && searching() && !hits.isEmpty();
            if (mx < bx + PAD + LIST_W) {
                listScroll = Math.max(0, Math.min(listMax, listScroll + step * 3));
                if (list) {                                           // Кн-4: the selection stays among the rows shown
                    int rows = Math.max(1, (bh - HEADER - 2 * PAD) / ROW);
                    resSel = Math.max(listScroll, Math.min(listScroll + rows - 1, resSel));
                    resSel = Math.max(0, Math.min(hits.size() - 1, resSel));
                }
            } else if (resultsView && searching()) {
                resScroll = Math.max(0, Math.min(resMax, resScroll + step));
                if (list) {                                           // Кн-4: Enter opens what is seen
                    resSel = Math.max(resScroll, Math.min(resScroll + resRows - 1, resSel));
                    resSel = Math.max(0, Math.min(hits.size() - 1, resSel));
                }
            } else {
                pageScroll = Math.max(0, Math.min(pageMax, pageScroll + step * 24));
            }
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        search.mouseClicked(x, y, button);
        for (int i = clickBoxes.size() - 1; i >= 0; i--) {
            int[] b = clickBoxes.get(i);
            if (x >= b[0] && y >= b[1] && x < b[0] + b[2] && y < b[1] + b[3]) {
                act(clickActions.get(i), button);
                return;
            }
        }
    }

    private void act(Object a, int button) {
        String before = state();
        if (a instanceof ItemStack) {
            if (button == 1 && cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) {
                click();
                nei((ItemStack) a);
            } else {
                openStack((ItemStack) a);
                if (!state().equals(before)) {
                    click();
                }
            }
            return;
        }
        click();
        if (a instanceof BookSearchSC.Hit) {
            int i = hits.indexOf(a);
            if (i >= 0) {
                resSel = i;                                           // Кн-4: Backspace comes back to this row
            }
            open((BookSearchSC.Hit) a);
        } else if (a instanceof BookChapter) {
            go("c:" + ((BookChapter) a).name());
        } else if (a instanceof BookEntry) {
            BookEntry e = (BookEntry) a;
            int i = searching() && !marksView ? listed.indexOf(e) : -1;
            if (i >= 0) {
                resSel = i;
                open(hits.get(i));                                    // a hit on the left: at its match too
            } else if (searching() && !marksView) {
                if (!state().equals("e:" + e.id)) {
                    history.push(state());
                }
                show(e);
            } else {
                go("e:" + e.id);
            }
        } else if ("home".equals(a)) {
            go("home");
        } else if ("back".equals(a)) {
            back();
        } else if ("marks".equals(a)) {
            go("marks");
        } else if ("mark".equals(a) && entry != null) {
            if (!BookProgressSC.toggleMark(entry.id)) {
                toast = Lang.tr("sc.book.marks.full", BookSearchSC.MAX_MARKS);
                toastUntil = System.currentTimeMillis() + 2500;
            }
        } else if (a instanceof String && ((String) a).startsWith("unmark:")) {
            BookProgressSC.removeMark(((String) a).substring(7));
        } else if (a instanceof String && ((String) a).startsWith("e:")) {
            go((String) a);
        }
    }

    private void click() {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.4F));
    }

    /** While the page draws, its boxes are cut to the visible page (a half-scrolled row mustn't catch clicks on the header). */
    private boolean drawingPage;

    private void click(int x, int y, int w, int h, Object action) {
        if (drawingPage) {
            int top = Math.max(y, ptop), bottom = Math.min(y + h, ptop + ph);
            if (bottom <= top) {
                return;
            }
            y = top;
            h = bottom - top;
        }
        clickBoxes.add(new int[]{x, y, w, h});
        clickActions.add(action);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        mx = mouseX;
        my = mouseY;
        hoverStack = null;
        hoverLines = null;
        clickBoxes.clear();
        clickActions.clear();
        drawDefaultBackground();
        drawRect(bx - 2, by - 2, bx + bw + 2, by + bh + 2, COVER_EDGE);
        drawRect(bx, by, bx + bw, by + bh, COVER);
        header();
        if (marksView) {
            marksPage();
        } else if (chapter == null && !searching()) {
            home();
        } else {
            sideList();
            if (resultsView && searching()) {
                resultsPage();
            } else {
                page();
            }
        }
        super.drawScreen(mouseX, mouseY, partial);
        if (hoverStack != null) {
            renderToolTip(display(hoverStack), mouseX, mouseY);
        } else if (hoverLines != null) {
            drawHoveringText(hoverLines, mouseX, mouseY, fontRendererObj);
        }
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    private void header() {
        int x = bx + PAD, y = by + 4;
        x += headButton(x, y, "<<", "home", Lang.tr("sc.book.home"));
        x += headButton(x, y, "<", "back", Lang.tr("sc.book.back"));
        int nMarks = BookProgressSC.bookmarks().size();
        headBox(x, y, "marks", Lang.tr("sc.book.marks.open", nMarks, BookSearchSC.MAX_MARKS), marksView);
        mask(RIBBON, x + 4, y + 1, marksView ? GOLD : 0xFFE6DCC0, false);
        x += 17;
        boolean list = resultsView && searching();
        if (entry != null && chapter != null && !list) {
            boolean m = BookProgressSC.isMarked(entry.id);
            headBox(x, y, "mark", Lang.tr(m ? "sc.book.unmark" : "sc.book.mark"), false);
            mask(STAR, x + 3, y + 1, m ? GOLD : 0xFFE6DCC0, !m);
            x += 17;
        }
        String t = chapter == null ? Lang.tr("sc.book.title") : entry != null
                ? chapter.title() + " > " + entry.title : chapter.title();
        int tc = 0xE6DCC0;
        if (marksView) {
            t = Lang.tr("sc.book.marks.title", nMarks, BookSearchSC.MAX_MARKS);
        } else if (list) {
            t = Lang.tr("sc.book.results", hitTotal);
        }
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            t = toast;
            tc = 0xFF9A7A;
        }
        fit(t, x + 4, y + 2, bx + bw - 124 - x - 6, tc, 1F);
        drawRect(bx + bw - 122, y - 1, bx + bw - 4, y + 12, 0xFF0A101E);
        drawRect(bx + bw - 122, y + 11, bx + bw - 4, y + 12, search.isFocused() ? GOLD : 0xFF3A4A6A);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            fontRendererObj.drawString(Lang.tr(chapter == BookChapter.RECIPES ? "sc.book.searchrecipes" : "sc.book.search"), bx + bw - 118, y + 2, 0x6A7A9A);
        }
    }

    private int headButton(int x, int y, String label, Object action, String tip) {
        headBox(x, y, action, tip, false);
        fontRendererObj.drawString(label, x + (14 - fontRendererObj.getStringWidth(label)) / 2, y + 1, 0xE6DCC0);
        return 17;
    }

    /** A header button's box (14 x 13), its click and tooltip; true when the mouse is over it. */
    private boolean headBox(int x, int y, Object action, String tip, boolean on) {
        int w = 14;
        boolean over = mx >= x && my >= y - 1 && mx < x + w && my < y + 12;
        drawRect(x, y - 1, x + w, y + 12, over ? 0xFF3A5A8A : on ? 0xFF34486E : 0xFF26344E);
        click(x, y - 1, w, 13, action);
        if (over) {
            hoverLines = lines(tip);
        }
        return over;
    }

    /** Pixel glyphs the vanilla font lacks: a star (bookmark this) and a ribbon (the bookmark list). */
    private static final String[] STAR = {
            "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..#####..", ".###.###.", ".##...##."};
    private static final String[] RIBBON = {
            "#######", "#######", "#######", "#######", "#######", "#######", "#######", "###.###", "##...##"};

    /** Draws a mask; `hollow`: only its edge pixels. */
    private void mask(String[] m, int x, int y, int color, boolean hollow) {
        for (int r = 0; r < m.length; r++) {
            for (int c = 0; c < m[r].length(); c++) {
                if (m[r].charAt(c) != '#') {
                    continue;
                }
                if (hollow && on(m, r - 1, c) && on(m, r + 1, c) && on(m, r, c - 1) && on(m, r, c + 1)) {
                    continue;
                }
                drawRect(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    private static boolean on(String[] m, int r, int c) {
        return r >= 0 && r < m.length && c >= 0 && c < m[r].length() && m[r].charAt(c) == '#';
    }

    // ------------------------------------------------------------------ the first page

    private void home() {
        int top = by + HEADER + PAD, left = bx + PAD, w = bw - 2 * PAD;
        drawRect(left, top, left + w, by + bh - PAD, PAPER);
        BookChapter[] chs = BookChapter.values();
        int cols = Math.max(1, Math.min(5, (w - 8) / 86)), tw = (w - 8 - (cols - 1) * 6) / cols;
        int tileRows = (chs.length + cols - 1) / cols;
        int th = Math.min(52, (bh - HEADER - 2 * PAD - 52) / tileRows - 6);
        boolean compact = th < 40;                                    // ИФ-1: big GUI scale / small window
        int gap = compact ? 3 : 6;
        if (compact) {
            th = 20;                                                  // a 16 px icon and the title on one line
        }
        boolean big = th >= 48;
        for (int i = 0; i < chs.length; i++) {
            int tx = left + 4 + (i % cols) * (tw + 6), ty = top + 6 + (i / cols) * (th + gap);
            boolean over = mx >= tx && my >= ty && mx < tx + tw && my < ty + th;
            drawRect(tx, ty, tx + tw, ty + th, over ? 0xFFE8D9B0 : 0xFFEADFC3);
            frame(tx, ty, tw, th, over ? 0xFF8A6A2A : PAPER_EDGE);
            String title = chs[i].title();
            if (compact) {
                item(BookContent.chapterIcon(chs[i]), tx + 2, ty + 2, false);
                fit(title, tx + 20, ty + 6, tw - 22, INK_HEAD, 1F);
            } else {
                GL11.glPushMatrix();
                GL11.glTranslatef(tx + tw / 2F - (big ? 16 : 8), ty + 3, 0F);
                if (big) {
                    GL11.glScalef(2F, 2F, 1F);
                }
                item(BookContent.chapterIcon(chs[i]), 0, 0, false);
                GL11.glPopMatrix();
                float k = Math.min(1F, (tw - 4) / (float) fontRendererObj.getStringWidth(title));
                fit(title, tx + (int) (tw - fontRendererObj.getStringWidth(title) * k) / 2, ty + th - 11, tw - 4, INK_HEAD, 1F);
            }
            click(tx, ty, tw, th, chs[i]);
            if (over) {
                hoverLines = lines(chs[i].title(), "§7" + chs[i].description(),
                        "§8" + Lang.tr("sc.book.articles", BookContent.chapter(chs[i]).size()));
            }
        }
        int y = top + 6 + tileRows * (th + gap) + 4;
        int done = BookProgressSC.doneCount(), all = BookContent.STEPS.length;
        fontRendererObj.drawString(Lang.tr("sc.book.progress", done, all), left + 6, y, INK_HEAD);
        int pw = 120, px = left + 6 + fontRendererObj.getStringWidth(Lang.tr("sc.book.progress", done, all)) + 8;
        drawRect(px, y + 1, px + pw, y + 7, 0xFFB8A880);
        drawRect(px + 1, y + 2, px + 1 + (pw - 2) * done / all, y + 6, 0xFF4AA84A);
        click(left + 4, y - 2, px + pw - left, 12, "e:start");
        y += 14;
        List<String> marks = BookProgressSC.bookmarks();
        String label = Lang.tr(marks.isEmpty() ? "sc.book.nomarks" : "sc.book.marks");
        int lw = fontRendererObj.getStringWidth(label);
        boolean overLabel = !marks.isEmpty() && mx >= left + 6 && my >= y + 2 && mx < left + 6 + lw && my < y + 14;
        fontRendererObj.drawString((overLabel ? "§n" : "") + label, left + 6, y + 4, marks.isEmpty() ? INK_DIM : LINK);
        if (!marks.isEmpty()) {
            click(left + 6, y + 2, lw, 12, "marks");                 // the whole list
            if (overLabel) {
                hoverLines = lines(Lang.tr("sc.book.marks.open", marks.size(), BookSearchSC.MAX_MARKS));
            }
        }
        int ix = left + 8 + lw;
        for (String id : marks) {
            BookEntry e = BookContent.byId(id);
            if (e == null || ix > left + w - 20) {
                continue;
            }
            item(e.icon, ix, y, false);
            if (mx >= ix && my >= y && mx < ix + 16 && my < y + 16) {
                hoverLines = lines(e.title);
            }
            click(ix, y, 16, 16, e);
            ix += 18;
        }
        if (y + 18 <= by + bh - PAD - 11) {                           // no room under the bookmarks: no hint
            fontRendererObj.drawString(Lang.tr("sc.book.hint"), left + 6, by + bh - PAD - 11, INK_DIM);
        }
    }

    // ------------------------------------------------------------------ the list on the left

    private void sideList() {
        int x = bx + PAD, top = by + HEADER + PAD, h = bh - HEADER - 2 * PAD;
        drawRect(x, top, x + LIST_W, top + h, 0xFF141D33);
        List<BookEntry> items = searching() ? listed : BookContent.chapter(chapter);
        int rows = h / ROW;
        listMax = Math.max(0, items.size() - rows);
        listScroll = Math.min(listScroll, listMax);
        boolean list = resultsView && searching();
        for (int i = 0; i < rows && listScroll + i < items.size(); i++) {
            BookEntry e = items.get(listScroll + i);
            int ry = top + i * ROW;
            boolean sel = list ? listScroll + i == resSel : e == entry, over = mx >= x && my >= ry && mx < x + LIST_W && my < ry + ROW;
            if (sel || over) {
                drawRect(x, ry, x + LIST_W, ry + ROW, sel ? 0xFF3A5070 : 0xFF223050);
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 2, ry + 1, 0F);
            GL11.glScalef(0.75F, 0.75F, 1F);
            item(e.icon, 0, 0, false);
            GL11.glPopMatrix();
            fit(e.title, x + 17, ry + 3, LIST_W - 20, sel ? 0xFFE6A0 : 0xD8D0BC, 0.85F);
            click(x, ry, LIST_W, ROW, e);
            if (over && fontRendererObj.getStringWidth(e.title) * 0.85F > LIST_W - 20) {
                hoverLines = lines(e.title);
            }
        }
        if (listMax > 0) {
            int th = Math.max(8, h * rows / Math.max(1, items.size()));
            int ty = top + (h - th) * listScroll / listMax;
            drawRect(x + LIST_W - 2, ty, x + LIST_W, ty + th, 0xFF6A7A9A);
        }
    }

    // ------------------------------------------------------------------ the page

    private int px, pw, ptop, ph;

    /** The paper on the right. */
    private void layoutPage() {
        px = bx + PAD + LIST_W + PAD;
        pw = bx + bw - PAD - px;
        ptop = by + HEADER + PAD;
        ph = bh - HEADER - 2 * PAD;
        drawRect(px, ptop, px + pw, ptop + ph, PAPER);
        frame(px, ptop, pw, ph, PAPER_EDGE);
    }

    private void page() {
        layoutPage();
        List<BookEl> els = pageEls();
        int cw = pw - 2 * PAD - 4, cx = px + PAD;
        int total = 0;
        for (BookEl e : els) {
            total += height(e, cw);
        }
        pageMax = Math.max(0, total - ph + 2 * PAD);
        if (jumpEl >= 0 && entry != null && els == entry.els && jumpEl < els.size()) {
            int off = 0;                                              // opened from a hit: its element near the top
            for (int i = 0; i < jumpEl; i++) {
                off += height(els.get(i), cw);
            }
            pageScroll = off - 10;
        }
        jumpEl = -1;
        pageScroll = Math.max(0, Math.min(pageMax, pageScroll));
        scissor(px + 1, ptop + 1, pw - 2, ph - 2);
        drawingPage = true;
        int y = ptop + PAD - pageScroll;
        long now = System.currentTimeMillis();
        for (int i = 0; i < els.size(); i++) {
            BookEl e = els.get(i);
            int h = height(e, cw);
            if (y + h >= ptop && y <= ptop + ph) {
                if (i == flashEl && now < flashUntil) {
                    int a = (int) (0xA0L * (flashUntil - now) / FLASH_MS);
                    drawRect(cx - 3, y - 2, cx + cw + 3, y + h - 1, a << 24 | 0xF2C850);
                }
                draw(e, cx, y, cw);
            }
            y += h;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        drawingPage = false;
        if (pageMax > 0) {
            int th = Math.max(10, ph * ph / (total + 2 * PAD));
            int ty = ptop + (ph - th) * pageScroll / pageMax;
            drawRect(px + pw - 4, ptop + 2, px + pw - 2, ptop + ph - 2, 0x30000000);
            drawRect(px + pw - 4, ty, px + pw - 2, ty + th, 0xFF8A7A5A);
        }
    }

    // ------------------------------------------------------------------ the hit list

    private void resultsPage() {
        layoutPage();
        int cw = pw - 2 * PAD - 4, left = px + PAD - 2;
        if (hits.isEmpty()) {
            int yy = ptop + PAD;
            for (String l : wrap(Lang.tr("sc.book.noresults") + " " + Lang.tr("sc.book.res.none"), cw)) {
                fontRendererObj.drawString(l, px + PAD, yy, INK_DIM);
                yy += 10;
            }
            return;
        }
        resRows = Math.max(1, (ph - 2 * PAD - 10) / RES_ROW);
        resMax = Math.max(0, hits.size() - resRows);
        resScroll = Math.max(0, Math.min(resMax, resScroll));
        for (int i = 0; i < resRows && resScroll + i < hits.size(); i++) {
            int idx = resScroll + i;
            BookSearchSC.Hit h = hits.get(idx);
            BookEntry e = listed.get(idx);
            int ry = ptop + PAD + i * RES_ROW, rw = cw + 4;
            boolean over = mx >= left && my >= ry - 1 && mx < left + rw && my < ry + RES_ROW - 2;
            if (idx == resSel || over) {
                drawRect(left, ry - 1, left + rw, ry + RES_ROW - 2, idx == resSel ? 0xFFE6D6A8 : 0xFFEDE2C6);
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(left + 2, ry, 0F);
            GL11.glScalef(0.75F, 0.75F, 1F);
            item(e.icon, 0, 0, false);
            GL11.glPopMatrix();
            int tx = left + 16;
            String ch = h.doc.chapter;
            int chW = Math.min((int) (fontRendererObj.getStringWidth(ch) * 0.75F) + 1, rw / 3);
            fit(ch, left + rw - chW - 3, ry + 1, chW, INK_DIM, 0.75F);
            fit(h.doc.title, tx, ry, left + rw - chW - 8 - tx, INK_HEAD, 1F);
            snippet(h, tx, ry + 11, left + rw - 3 - tx);
            click(left, ry - 1, rw, RES_ROW - 1, h);
            if (over) {
                hoverLines = lines(h.doc.title, "§7" + ch, "§8" + Lang.tr("sc.book.res.count", h.count));
            }
        }
        String hint = Lang.tr("sc.book.res.hint");
        if (hitTotal > hits.size()) {
            hint = Lang.tr("sc.book.res.more", hits.size(), hitTotal) + " " + hint;
        }
        fit(hint, px + PAD, ptop + ph - PAD - 7, cw, INK_DIM, 0.85F);
        if (resMax > 0) {
            int area = resRows * RES_ROW, th = Math.max(8, area * resRows / hits.size());
            int ty = ptop + PAD + (area - th) * resScroll / resMax;
            drawRect(px + pw - 4, ty, px + pw - 2, ty + th, 0xFF8A7A5A);
        }
    }

    /** A hit's snippet on one line, the match on a highlight. */
    private void snippet(BookSearchSC.Hit h, int x, int y, int w) {
        int chars = Math.max(12, w / 5);
        BookSearchSC.Snip s = BookSearchSC.snippet(h.doc.body, h.at, h.len, chars / 3, chars);
        String pre = s.text.substring(0, s.hl), mid = s.text.substring(s.hl, s.hl + s.hlLen), post = s.text.substring(s.hl + s.hlLen);
        int mw = fontRendererObj.getStringWidth(mid);
        if (mw > w) {
            mid = fontRendererObj.trimStringToWidth(mid, w);
            mw = fontRendererObj.getStringWidth(mid);
            pre = "";
            post = "";
        }
        int preMax = Math.max(0, (w - mw) / 3);
        if (fontRendererObj.getStringWidth(pre) > preMax) {
            int dots = fontRendererObj.getStringWidth("...");
            pre = preMax > dots ? "..." + fontRendererObj.trimStringToWidth(pre, preMax - dots, true) : "";
        }
        int pw0 = fontRendererObj.getStringWidth(pre);
        post = fontRendererObj.trimStringToWidth(post, Math.max(0, w - pw0 - mw));
        fontRendererObj.drawString(pre, x, y, INK);
        if (mw > 0) {
            drawRect(x + pw0 - 1, y - 1, x + pw0 + mw, y + 9, 0xFFF2CF5A);
            fontRendererObj.drawString(mid, x + pw0, y, 0x2A1A00);
        }
        fontRendererObj.drawString(post, x + pw0 + mw, y, INK);
    }

    // ------------------------------------------------------------------ the bookmark list

    private void marksPage() {
        int top = by + HEADER + PAD, left = bx + PAD, w = bw - 2 * PAD, h = bh - HEADER - 2 * PAD;
        drawRect(left, top, left + w, top + h, PAPER);
        frame(left, top, w, h, PAPER_EDGE);
        List<String> marks = BookProgressSC.bookmarks();
        if (marks.isEmpty()) {
            int yy = top + 8;
            for (String l : wrap(Lang.tr("sc.book.marks.empty"), w - 16)) {
                fontRendererObj.drawString(l, left + 8, yy, INK_DIM);
                yy += 10;
            }
            return;
        }
        int rows = Math.max(1, (h - 8) / MARK_ROW);
        marksMax = Math.max(0, marks.size() - rows);
        marksScroll = Math.max(0, Math.min(marksMax, marksScroll));
        int rx = left + 4, rw = w - 8 - 4;
        for (int i = 0; i < rows && marksScroll + i < marks.size(); i++) {
            String id = marks.get(marksScroll + i);
            BookEntry e = BookContent.byId(id);
            int ry = top + 4 + i * MARK_ROW, xb = rx + rw - 14;
            boolean over = mx >= rx && my >= ry && mx < rx + rw && my < ry + MARK_ROW - 1;
            boolean overX = mx >= xb && my >= ry + 2 && mx < xb + 12 && my < ry + 14;
            if (over) {
                drawRect(rx, ry, rx + rw, ry + MARK_ROW - 1, 0xFFE8D9B0);
            }
            if (e != null) {
                item(e.icon, rx + 2, ry + 1, false);
            }
            String title = e != null ? e.title : Lang.tr("sc.book.marks.missing", id);
            String ch = e != null ? e.chapter.title() : "";
            int chW = Math.min(fontRendererObj.getStringWidth(ch), rw / 3);
            fit(title, rx + 22, ry + 5, xb - chW - 10 - (rx + 22), e != null ? INK_HEAD : INK_DIM, 1F);
            fit(ch, xb - chW - 6, ry + 5, chW, INK_DIM, 1F);
            drawRect(xb, ry + 2, xb + 12, ry + 14, overX ? 0xFFB04040 : 0xFFD8C8A0);
            fontRendererObj.drawString("x", xb + 3, ry + 3, overX ? 0xFFFFFF : INK);
            if (e != null) {
                click(rx, ry, rw, MARK_ROW - 1, e);
            }
            click(xb, ry + 2, 12, 12, "unmark:" + id);
            if (overX) {
                hoverLines = lines(Lang.tr("sc.book.unmark"));
            } else if (over && e != null) {
                hoverLines = lines(e.title, "§7" + ch);
            }
        }
        if (marksMax > 0) {
            int area = rows * MARK_ROW, th = Math.max(8, area * rows / marks.size());
            int ty = top + 4 + (area - th) * marksScroll / marksMax;
            drawRect(left + w - 5, ty, left + w - 3, ty + th, 0xFF8A7A5A);
        }
    }

    private List<BookEl> pageEls() {
        if (entry == null) {
            List<BookEl> none = new ArrayList<BookEl>();
            none.add(BookEl.dim(Lang.tr(searching() ? "sc.book.noresults" : "sc.book.empty")));
            return none;
        }
        if (entry.chapter == BookChapter.RECIPES && chapter == BookChapter.RECIPES) {
            if (recipeEls == null || !query.equals(recipeQuery)) {
                recipeQuery = query;
                recipeEls = new ArrayList<BookEl>(entry.els);
                recipeEls.addAll(BookContent.recipes(query, 40));
            }
            return recipeEls;
        }
        return entry.els;
    }

    private boolean inPage(int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h && my >= ptop && my < ptop + ph;
    }

    // ------------------------------------------------------------------ elements: height

    private List<String> wrap(String text, int w) {
        return fontRendererObj.listFormattedStringToWidth(text == null ? "" : text, Math.max(20, w));
    }

    private int height(BookEl e, int w) {
        switch (e.kind) {
            case TITLE: return wrap(e.text, w).size() * 11 + 6;
            case HEAD: return Math.max(e.stack != null ? 18 : 0, wrap(e.text, w - (e.stack != null ? 20 : 0)).size() * 10) + 5;
            case PARA: return wrap(e.text, w).size() * 10 + 3;
            case ITEM: return Math.max(18, 10 + (e.sub == null ? 0 : wrap(e.sub, w - 20).size() * 9)) + 3;
            case ITEMS: return ((e.stacks.length + perRow(w) - 1) / perRow(w)) * 18 + 3;
            case CRAFT: return 70;
            case MACHINE: return 36;
            case CHAIN: return chainRows(e, w) * 30 + 2;
            case ORE: return 84;
            case TABLE: return (e.rows.size() + 1) * rowH(e) + 6;
            case LAYERS: {
                int per = Math.max(1, (w + 8) / (grid(e) + 8));
                return ((e.layers.length + per - 1) / per) * (grid(e) + 14) + 4;
            }
            case IMAGE: return Math.min(w, e.imgW) * e.imgH / e.imgW + 6;
            case CHECK: return 19;
            case LINK: return 13;
            default: return 6;
        }
    }

    private static int perRow(int w) {
        return Math.max(1, w / 18);
    }

    private static int rowH(BookEl e) {
        for (ItemStack s : e.rowIcons) {
            if (s != null) {
                return 18;
            }
        }
        return 11;
    }

    private static int grid(BookEl e) {
        return e.layers[0].length * 13;
    }

    private int chainRows(BookEl e, int w) {
        int x = 0, rows = 1;
        for (int i = 0; i < e.stacks.length; i++) {
            int need = i == 0 ? 18 : 44;
            if (x + need > w && x > 0) {
                rows++;
                x = 18 + 44;
            } else {
                x += need;
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ elements: drawing

    private void draw(BookEl e, int x, int y, int w) {
        switch (e.kind) {
            case TITLE: {
                int yy = y;
                for (String l : wrap(e.text, w)) {
                    fontRendererObj.drawString("§l" + l, x, yy, e.color != 0 ? e.color : INK_HEAD);
                    yy += 11;
                }
                drawRect(x, yy, x + w, yy + 1, 0xFFB8A070);
                break;
            }
            case HEAD: {
                int tx = x;
                if (e.stack != null) {
                    item(e.stack, x, y, true);
                    tx += 20;
                }
                int yy = y + (e.stack != null ? 4 : 1);
                for (String l : wrap(e.text, w - (tx - x))) {
                    fontRendererObj.drawString(l, tx, yy, e.color != 0 ? e.color : INK_HEAD);
                    yy += 10;
                }
                break;
            }
            case PARA: {
                int yy = y;
                for (String l : wrap(e.text, w)) {
                    fontRendererObj.drawString(l, x, yy, e.color != 0 ? e.color : INK);
                    yy += 10;
                }
                break;
            }
            case ITEM: {
                if (e.stack != null) {
                    item(e.stack, x, y, true);
                } else {
                    drawRect(x + 6, y + 4, x + 10, y + 8, 0xFF8A6A2A);
                }
                fontRendererObj.drawString(e.text, x + 20, y + 1, INK_HEAD);
                int yy = y + 10;
                if (e.sub != null) {
                    for (String l : wrap(e.sub, w - 20)) {
                        fontRendererObj.drawString(l, x + 20, yy, INK_DIM);
                        yy += 9;
                    }
                }
                break;
            }
            case ITEMS: {
                int per = perRow(w);
                for (int i = 0; i < e.stacks.length; i++) {
                    item(e.stacks[i], x + (i % per) * 18, y + (i / per) * 18, true);
                }
                break;
            }
            case CRAFT: drawCraft(e, x, y, w); break;
            case MACHINE: drawMachine((MachineRecipe) e.recipe, x, y, w); break;
            case CHAIN: drawChain(e, x, y, w); break;
            case ORE: drawOre(e, x, y, w); break;
            case TABLE: drawTable(e, x, y, w); break;
            case LAYERS: drawLayers(e, x, y, w); break;
            case IMAGE: drawImage(e, x, y, w); break;
            case CHECK: {
                boolean done = BookProgressSC.isDone(e.target);
                drawRect(x, y + 4, x + 10, y + 14, 0xFF6A5A3A);
                drawRect(x + 1, y + 5, x + 9, y + 13, done ? 0xFFDFF5D0 : 0xFFF8F2E4);
                if (done) {
                    for (int k = 0; k < 3; k++) {
                        drawRect(x + 2 + k, y + 8 + k, x + 3 + k, y + 10 + k, 0xFF2E8A2E);
                    }
                    for (int k = 0; k < 4; k++) {
                        drawRect(x + 5 + k, y + 9 - k, x + 6 + k, y + 11 - k, 0xFF2E8A2E);
                    }
                }
                item(e.stack, x + 14, y + 1, true);
                fit(e.text, x + 34, y + 5, w - 36, done ? 0x3A7A3A : INK, 1F);
                break;
            }
            case LINK: {
                boolean over = inPage(x, y, fontRendererObj.getStringWidth("-> " + e.text), 10);
                fontRendererObj.drawString((over ? "§n" : "") + "-> " + e.text, x, y + 1, LINK);
                click(x, y, fontRendererObj.getStringWidth("-> " + e.text), 10, "e:" + e.target);
                break;
            }
            default:
        }
    }

    // ---- crafting grid

    private void drawCraft(BookEl e, int x, int y, int w) {
        IRecipe r = (IRecipe) e.recipe;
        ItemStack out = r.getRecipeOutput();
        fit(Lang.tr("sc.book.crafting") + ": " + (out == null ? "?" : out.getDisplayName()), x, y, w, INK_DIM, 1F);
        int gx = x + 4, gy = y + 11;
        Object[] grid = grid(r);
        for (int i = 0; i < 9; i++) {
            int cx = gx + (i % 3) * 18, cy = gy + (i / 3) * 18;
            cell(cx, cy);
            ItemStack s = pick(grid[i]);
            if (s != null) {
                item(s, cx + 1, cy + 1, true);
            }
        }
        arrow(gx + 58, gy + 22, 20);
        cell(gx + 84, gy + 18);
        if (out != null) {
            item(out, gx + 85, gy + 19, true);
        }
        if (r instanceof ShapelessRecipes || r instanceof ShapelessOreRecipe) {
            fontRendererObj.drawString(Lang.tr("sc.book.shapeless"), gx + 60, gy + 42, INK_DIM);
        }
    }

    private static Object[] grid(IRecipe r) {
        Object[] g = new Object[9];
        try {
            if (r instanceof ShapedRecipes) {
                ShapedRecipes s = (ShapedRecipes) r;
                for (int i = 0; i < s.recipeItems.length; i++) {
                    g[(i / s.recipeWidth) * 3 + i % s.recipeWidth] = s.recipeItems[i];
                }
            } else if (r instanceof ShapedOreRecipe) {
                Object[] in = ((ShapedOreRecipe) r).getInput();
                int w = (Integer) cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(ShapedOreRecipe.class, (ShapedOreRecipe) r, "width");
                for (int i = 0; i < in.length; i++) {
                    g[(i / w) * 3 + i % w] = in[i];
                }
            } else if (r instanceof ShapelessRecipes) {
                List<?> in = ((ShapelessRecipes) r).recipeItems;
                for (int i = 0; i < in.size() && i < 9; i++) {
                    g[i] = in.get(i);
                }
            } else if (r instanceof ShapelessOreRecipe) {
                List<?> in = ((ShapelessOreRecipe) r).getInput();
                for (int i = 0; i < in.size() && i < 9; i++) {
                    g[i] = in.get(i);
                }
            }
        } catch (Throwable t) {
            // an odd recipe class: the grid stays empty, the output still shows
        }
        return g;
    }

    /** A slot's item: an ore-dictionary list cycles once a second; any-metadata shows the first. */
    private static ItemStack pick(Object o) {
        ItemStack s = null;
        if (o instanceof ItemStack) {
            s = (ItemStack) o;
        } else if (o instanceof List && !((List<?>) o).isEmpty()) {
            List<?> l = (List<?>) o;
            s = (ItemStack) l.get((int) (net.minecraft.client.Minecraft.getSystemTime() / 1000L % l.size()));
        }
        return s;
    }

    /**
     * A machine input the machine takes from any mod (its shared ore-dictionary name: ingotCopper, dustTin...): every
     * such item in turn, once a second, as the crafting grids do - in a pack that unifies metals (UniDict) ours may
     * be hidden and another mod's is the one players have.
     */
    private static ItemStack equivalent(ItemStack s) {
        String name = com.sc.init.OreRecipes.sharedName(s);
        if (name == null) {
            return s;
        }
        List<ItemStack> all = OreDictionary.getOres(name);
        if (all.size() < 2) {
            return s;
        }
        ItemStack c = all.get((int) (net.minecraft.client.Minecraft.getSystemTime() / 1000L % all.size())).copy();
        c.stackSize = s.stackSize;
        return display(c);
    }

    private static ItemStack display(ItemStack s) {
        if (s.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
            ItemStack c = s.copy();
            c.setItemDamage(0);
            return c;
        }
        return s;
    }

    // ---- a machine recipe: the machine, inputs (items, fluids) -> outputs, by-products; time, EU

    private void drawMachine(MachineRecipe r, int x, int y, int w) {
        drawRect(x, y, x + w, y + 32, 0x14000000);
        ItemStack m = BookContent.machineStack(r.type);
        item(m, x + 2, y + 2, true);
        int cx = x + 22;
        for (ItemStack s : r.inputs) {
            cell(cx, y + 1);
            item(equivalent(s), cx + 1, y + 2, true);
            cx += 18;
        }
        cx = fluid(r.fluidInputA, cx, y + 1);
        cx = fluid(r.fluidInputB, cx, y + 1);
        arrow(cx + 2, y + 9, 16);
        cx += 22;
        for (ItemStack s : r.outputs) {
            cell(cx, y + 1);
            item(s, cx + 1, y + 2, true);
            cx += 18;
        }
        cx = fluid(r.fluidOutputA, cx, y + 1);
        cx = fluid(r.fluidOutputB, cx, y + 1);
        for (int i = 0; i < r.byproducts.length && cx + 18 <= x + w; i++) {
            cell(cx, y + 1);
            item(r.byproducts[i], cx + 1, y + 2, true);
            small(BookContent.percent(r.byproductChances[i]) + "%", cx + 1, y + 20, 0x8A5A1A);
            cx += 18;
        }
        String meta = r.type.localizedName() + " · " + String.format(Locale.ROOT, "%.1f", r.ticks / 20F) + " s · " + r.type.euPerTick + " EU/t";
        if (r.defectChance > 0) {
            meta += " · " + Lang.tr("sc.manual.recipes.defect", BookContent.percent(r.defectChance));
        }
        small(meta, x + 2, y + 25, INK_DIM);
    }

    private int fluid(FluidStack f, int x, int y) {
        if (f == null || f.getFluid() == null) {
            return x;
        }
        cell(x, y);
        Fluid fl = f.getFluid();
        IIcon icon = fl.getIcon(f);
        if (icon != null) {
            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            int c = fl.getColor(f);
            GL11.glColor4f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 1F);
            drawTexturedModelRectFromIcon(x + 1, y + 1, icon, 16, 16);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        } else {
            drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF4060A0);
        }
        small(f.amount >= 1000 ? f.amount / 1000 + "B" : String.valueOf(f.amount), x + 2, y + 12, 0xFFFFFF);
        if (inPage(x, y, 18, 18)) {
            hoverLines = lines(fl.getLocalizedName(f), "§7" + f.amount + " " + BookContent.mb());
        }
        return x + 18;
    }

    // ---- a route: item -> [machine] -> item ...

    private void drawChain(BookEl e, int x, int y, int w) {
        int cx = x, cy = y;
        for (int i = 0; i < e.stacks.length; i++) {
            if (i > 0) {
                if (cx + 44 > x + w && cx > x) {
                    cx = x;
                    cy += 30;
                    small("->", cx + 4, cy + 12, INK_DIM);
                    cx += 18;
                }
                arrow(cx + 2, cy + 17, 22);
                ItemStack m = i - 1 < e.between.length ? e.between[i - 1] : null;
                if (m != null) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(cx + 7, cy + 1, 0F);
                    GL11.glScalef(0.75F, 0.75F, 1F);
                    item(m, 0, 0, false);
                    GL11.glPopMatrix();
                    if (inPage(cx + 7, cy + 1, 12, 12)) {
                        hoverStack = m;
                    }
                    click(cx + 7, cy + 1, 12, 12, m);
                }
                cx += 26;
            }
            cell(cx, cy + 9);
            item(e.stacks[i], cx + 1, cy + 10, true);
            cx += 18;
        }
    }

    // ---- an ore card: the block, where (a height bar), biomes, the pickaxe

    private void drawOre(BookEl e, int x, int y, int w) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(2F, 2F, 1F);
        item(e.stack, 0, 0, false);
        GL11.glPopMatrix();
        com.sc.util.ConfigSC.OreGenSettings g = BookContent.gen(e.ore);
        fontRendererObj.drawString("§l" + e.stack.getDisplayName(), x + 38, y + 1, INK_HEAD);
        fit(Lang.tr("sc.book.ore.where", g.minY, g.maxY, g.veinSize, g.veinsPerChunk), x + 38, y + 12, w - 38, INK, 1F);
        fit(Lang.tr("sc.book.ore.biomes", BookContent.biomes(e.ore)), x + 38, y + 22, w - 38, INK, 1F);
        ItemStack pick = BookContent.pickaxe(e.ore);
        item(pick, x + 38, y + 31, true);
        fit(Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool." + e.ore.tool.name().toLowerCase(Locale.ROOT))), x + 56, y + 35, w - 56, INK, 1F);
        // the height bar: 0..128, the vein band, sea level
        int bx0 = x + 14, bw0 = w - 20, top = y + 56;
        fontRendererObj.drawString("Y", x, top - 1, INK_DIM);
        drawRect(bx0, top, bx0 + bw0, top + 8, 0xFF8A8278);
        drawRect(bx0 + 1, top + 1, bx0 + bw0 - 1, top + 7, 0xFFB8AE9C);
        int a = bx0 + bw0 * Math.max(0, g.minY) / 128, b = bx0 + bw0 * Math.min(128, g.maxY) / 128;
        drawRect(a, top + 1, Math.max(a + 2, b), top + 7, 0xFFC06A30);
        int sea = bx0 + bw0 * 64 / 128;
        drawRect(sea, top - 2, sea + 1, top + 10, 0xFF3A6AC8);
        for (int v = 0; v <= 128; v += 32) {
            int tx = bx0 + bw0 * v / 128;
            drawRect(tx, top + 8, tx + 1, top + 11, 0xFF6A6258);
            small(String.valueOf(v), tx - 3, top + 13, INK_DIM);
        }
        small(Lang.tr("sc.book.ore.sea"), sea + 3, top - 7, 0x3A6AC8);
        if (inPage(bx0, top, bw0, 10)) {
            hoverLines = lines(Lang.tr("sc.book.ore.band", g.minY, g.maxY));
        }
    }

    // ---- a table

    private void drawTable(BookEl e, int x, int y, int w) {
        int rh = rowH(e), icon = rh == 18 ? 18 : 0, n = e.header.length;
        int[] cw = new int[n];
        for (int c = 0; c < n; c++) {
            cw[c] = fontRendererObj.getStringWidth(e.header[c]) + 8;
            for (String[] r : e.rows) {
                cw[c] = Math.max(cw[c], fontRendererObj.getStringWidth(c < r.length ? r[c] : "") + 8);
            }
        }
        int sum = 0;
        for (int c : cw) {
            sum += c;
        }
        float k = Math.min(1F, (w - icon) / (float) sum);
        drawRect(x, y, x + w, y + rh, 0xFFD8C8A0);
        int cx = x + icon;
        for (int c = 0; c < n; c++) {
            fit("§l" + e.header[c], cx + 2, y + (rh - 8) / 2, (int) (cw[c] * k) - 2, INK_HEAD, k);
            cx += (int) (cw[c] * k);
        }
        for (int i = 0; i < e.rows.size(); i++) {
            int ry = y + (i + 1) * rh;
            if (i % 2 == 1) {
                drawRect(x, ry, x + w, ry + rh, 0x18000000);
            }
            ItemStack ic = e.rowIcons.get(i);
            if (ic != null) {
                item(ic, x + 1, ry + 1, true);
            }
            cx = x + icon;
            String[] r = e.rows.get(i);
            for (int c = 0; c < n; c++) {
                fit(c < r.length ? r[c] : "", cx + 2, ry + (rh - 8) / 2, (int) (cw[c] * k) - 2, c == 0 ? INK_HEAD : INK, k);
                cx += (int) (cw[c] * k);
            }
        }
        frame(x, y, w, (e.rows.size() + 1) * rh, 0xFFB8A070);
    }

    // ---- a multiblock, layer by layer

    private void drawLayers(BookEl e, int x, int y, int w) {
        int g = grid(e), per = Math.max(1, (w + 8) / (g + 8));
        for (int l = 0; l < e.layers.length; l++) {
            int gx = x + (l % per) * (g + 8), gy = y + (l / per) * (g + 14);
            small(e.layerNames[l], gx, gy, INK_HEAD);
            drawRect(gx - 1, gy + 7, gx + g + 1, gy + 9 + g, 0xFFB8A070);
            drawRect(gx, gy + 8, gx + g, gy + 8 + g, 0xFFE4D8B8);
            ItemStack[][] layer = e.layers[l];
            for (int z = 0; z < layer.length; z++) {
                for (int xx = 0; xx < layer[z].length; xx++) {
                    ItemStack s = layer[z][xx];
                    int cx = gx + xx * 13, cy = gy + 8 + z * 13;
                    if (s == null) {
                        continue;
                    }
                    GL11.glPushMatrix();
                    GL11.glTranslatef(cx + 0.5F, cy + 0.5F, 0F);
                    GL11.glScalef(0.75F, 0.75F, 1F);
                    item(s, 0, 0, false);
                    GL11.glPopMatrix();
                    if (inPage(cx, cy, 13, 13)) {
                        hoverStack = s;
                    }
                    click(cx, cy, 13, 13, s);
                }
            }
        }
    }

    // ---- a picture

    private void drawImage(BookEl e, int x, int y, int w) {
        int dw = Math.min(w, e.imgW), dh = e.imgH * dw / e.imgW;
        mc.getTextureManager().bindTexture(new ResourceLocation("siliconage", "textures/gui/book/" + e.image + ".png"));
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glEnable(GL11.GL_BLEND);
        func_146110_a(x + (w - dw) / 2, y + 2, 0, 0, dw, dh, dw, dh);
        GL11.glDisable(GL11.GL_BLEND);
    }

    // ------------------------------------------------------------------ drawing helpers

    /** An item icon at (x, y); `hover`: its tooltip and click (only for unscaled icons on the page). */
    private void item(ItemStack s, int x, int y, boolean hover) {
        if (s == null || s.getItem() == null) {
            return;
        }
        ItemStack d = display(s);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        itemRender.zLevel = 50F;
        try {
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), d, x, y);
            itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), d, x, y, d.stackSize > 1 ? String.valueOf(d.stackSize) : null);
        } catch (Throwable t) {
            // another mod's item that can't draw here
        }
        itemRender.zLevel = 0F;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        if (hover && (chapter == null && !searching() ? mx >= x && my >= y && mx < x + 16 && my < y + 16 : inPage(x, y, 16, 16))) {
            hoverStack = s;
        }
        if (hover && (chapter != null || searching())) {
            click(x, y, 16, 16, s);
        }
    }

    private void cell(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF8A7A5A);
        drawRect(x + 1, y + 1, x + 18, y + 18, 0xFFFFFAEA);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFFC8BC9C);
    }

    private void arrow(int x, int y, int len) {
        drawRect(x, y, x + len - 4, y + 2, 0xFF6A5A3A);
        for (int k = 0; k < 4; k++) {
            drawRect(x + len - 5 + k, y - 3 + k, x + len - 4 + k, y + 5 - k, 0xFF6A5A3A);
        }
    }

    private void frame(int x, int y, int w, int h, int c) {
        drawRect(x, y, x + w, y + 1, c);
        drawRect(x, y + h - 1, x + w, y + h, c);
        drawRect(x, y, x + 1, y + h, c);
        drawRect(x + w - 1, y, x + w, y + h, c);
    }

    /** Text squeezed to its room (never above `max` scale), cut with "..." below 0.6. */
    private void fit(String text, int x, int y, int maxW, int color, float max) {
        if (text == null || maxW <= 4) {
            return;
        }
        int tw = fontRendererObj.getStringWidth(text);
        float k = Math.min(max, maxW / (float) Math.max(1, tw));
        if (k < 0.6F) {
            k = 0.6F;
            text = fontRendererObj.trimStringToWidth(text, (int) (maxW / k) - 6) + "...";
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (1F - k) * 4F, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void small(String text, int x, int y, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(0.6F, 0.6F, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void scissor(int x, int y, int w, int h) {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int k = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * k, mc.displayHeight - (y + h) * k, w * k, h * k);
    }

    private static List<String> lines(String... s) {
        List<String> l = new ArrayList<String>();
        for (String x : s) {
            l.add(x);
        }
        return l;
    }
}
