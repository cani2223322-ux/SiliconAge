package com.sc.manual;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * §4 of 02_guide_book.md ("тёмно-синяя обложка... вкладки как выступающие закладки") - built
 * as flat colour panels (Gui.drawRect) rather than a painted texture, same pragmatic tradeoff
 * as the machine GUIs' backgrounds: correct layout and real content, not hand-drawn art.
 * Left-side tab buttons switch ManualTab; content is plain word-wrapped text from
 * ManualContent, scrollable with the mouse wheel, arrow keys and Page Up/Down. The RECIPES tab additionally
 * reads a text field for the search query (§2 point 8: "текстовое поле поиска").
 */
public class GuiManual extends GuiScreen {

    private static final int PANEL_X = 20;
    private static final int PANEL_Y = 10;
    private static final int TAB_WIDTH = 100;
    private static final int CONTENT_LINE_HEIGHT = 10;

    private ManualTab currentTab = ManualTab.INTRO;
    private int scroll;
    // Pages are built from the registries and word-wrapped once per (tab, query, width), not
    // every frame - the recipe tab alone walks every machine and crafting recipe.
    private List<String> cachedLines;
    private String cacheKey;
    private String searchQuery = "";
    private int searchCursorTicks;

    @Override
    public void initGui() {
        buttonList.clear();
        // Nine tabs need 180 px; on a short screen (large GUI scale) the buttons shrink to fit.
        int step = Math.max(12, Math.min(20, (height - PANEL_Y - 20) / ManualTab.values().length));
        int y = PANEL_Y + 10;
        int id = 0;
        for (ManualTab tab : ManualTab.values()) {
            buttonList.add(new GuiButton(id++, PANEL_X, y, TAB_WIDTH, step - 2, tabLabel(tab)));
            y += step;
        }
        cachedLines = null;
    }

    private static String tabLabel(ManualTab tab) {
        return Lang.tr("sc.manual.tab." + tab.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < ManualTab.values().length) {
            currentTab = ManualTab.values()[button.id];
            scroll = 0;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            b.enabled = b.id != currentTab.ordinal();     // the open tab reads as "pressed"
        }
    }

    /** Mouse wheel scrolls the page, 3 lines per notch. */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, scroll + (wheel > 0 ? -3 : 3));
        }
    }

    private List<String> pageLines(int contentWidth) {
        String query = currentTab == ManualTab.RECIPES ? searchQuery : null;
        String key = currentTab + "|" + query + "|" + contentWidth;
        if (cachedLines == null || !key.equals(cacheKey)) {
            List<String> wrapped = new java.util.ArrayList<String>();
            for (String line : ManualContent.linesFor(currentTab, query)) {
                if (line.isEmpty()) {
                    wrapped.add("");
                } else {
                    wrapped.addAll(fontRendererObj.listFormattedStringToWidth(line, contentWidth));
                }
            }
            cachedLines = wrapped;
            cacheKey = key;
        }
        return cachedLines;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int contentX = PANEL_X + TAB_WIDTH + 20;
        int contentY = PANEL_Y + 10;
        int contentWidth = width - contentX - 20;
        int contentHeight = height - contentY - 20;

        drawRect(PANEL_X - 5, PANEL_Y - 5, PANEL_X + TAB_WIDTH + 5, height - 10, 0xE0142035);
        drawRect(contentX - 10, contentY - 5, contentX + contentWidth + 10, contentY + contentHeight + 5, 0xD0EDE6D6);

        super.drawScreen(mouseX, mouseY, partialTicks);

        List<String> wrapped = pageLines(contentWidth);

        int maxScroll = Math.max(0, wrapped.size() - contentHeight / CONTENT_LINE_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int y = contentY;
        for (int i = scroll; i < wrapped.size() && y < contentY + contentHeight; i++) {
            fontRendererObj.drawString(wrapped.get(i), contentX, y, 0x1A1A1A);
            y += CONTENT_LINE_HEIGHT;
        }

        int promptEnd = contentX;
        if (currentTab == ManualTab.RECIPES) {
            String prompt = Lang.tr("sc.manual.search") + ": " + searchQuery
                    + (++searchCursorTicks / 10 % 2 == 0 ? "_" : "");
            fontRendererObj.drawStringWithShadow(prompt, contentX, contentY + contentHeight + 8, 0xFFFFFF);
            promptEnd = contentX + fontRendererObj.getStringWidth(prompt);
        }
        if (maxScroll > 0) {
            // Right-aligned, and skipped when it would run into the search prompt (narrow
            // screens / long queries) - it's only a hint, the prompt is what the player types into.
            String hint = Lang.tr("sc.manual.scrollhint") + "  " + Lang.tr("sc.manual.position", scroll * 100 / maxScroll);
            int hintX = contentX + contentWidth - fontRendererObj.getStringWidth(hint);
            if (hintX > promptEnd + 6) {
                fontRendererObj.drawStringWithShadow(hint, hintX, contentY + contentHeight + 8, 0xFFFFFF);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 200) { // up
            scroll = Math.max(0, scroll - 1);
        } else if (keyCode == 208) { // down
            scroll++;
        } else if (keyCode == 201) { // page up
            scroll = Math.max(0, scroll - 10);
        } else if (keyCode == 209) { // page down
            scroll += 10;
        } else if (keyCode == 1) { // escape
            mc.thePlayer.closeScreen();
        } else if (currentTab == ManualTab.RECIPES) {
            if (keyCode == 14 && searchQuery.length() > 0) { // backspace
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            } else if (typedChar >= ' ' && searchQuery.length() < 32) {
                searchQuery += typedChar;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
