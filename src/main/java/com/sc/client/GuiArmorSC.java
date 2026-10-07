package com.sc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.sc.handler.ArmorNetSC;
import com.sc.inventory.TextFitSC;
import com.sc.item.ArmorLogicSC;
import com.sc.item.BladeLogicSC;
import com.sc.item.DrillLogicSC;
import com.sc.item.ItemArmorChipSC;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBladeSC;
import com.sc.item.ItemDrillSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeFeature;
import com.sc.util.BladeForm;
import com.sc.util.BladeType;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;
import com.sc.util.PowerModeKey;
import com.sc.util.ToolLevelSC;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * Armour settings (the "armour" key, K): a tab for each worn piece, a "Blade" tab while an
 * energy blade is in hand, a "Drill" tab while a drill is in hand (opened on its own when no armour
 * is worn), a "Mode" tab (keys for the power mode) while a chestplate is worn and a "Gases" tab (life
 * support); for each function a switch, the gas it runs on and the key that switches it (or fires it -
 * dash, annihilation pulse, the blade's sweep / wave / lunge) - any key, with Ctrl / Shift / Alt,
 * or a middle / side mouse button. Plus the power mode, the set bonus and (chestplate tab) taking the chips out.
 * Every tab: a status strip (charge, heat, power mode, emergency) and, on the left, the worn pieces
 * with their gas tanks (a click opens the piece's tab). The window is about 470 px wide and narrows
 * on small screens: the left panel loses its percentages, then goes; two columns become one.
 * Switches go to the server (ArmorNetSC) and show at once; keys live in the player's own config.
 */
public class GuiArmorSC extends GuiScreen {

    /** The blade's tab (after the four armour pieces). */
    private static final int BLADE_TAB = 4, MODE_TAB = 5, DRILL_TAB = 6, LIFE_TAB = 7;
    /** Stage 5: the Singular levels, branches, profiles and HUD (any Singular piece worn). */
    private static final int LEVEL_TAB = 8;
    /** The Ground / Space Bridge's tab (a Singular helmet worn): its own screen, GuiArmorBridgeSC, with this window's tab row. */
    public static final int BRIDGE_TAB = 9;

    public GuiArmorSC() {
    }

    /** Opens on tab `tab` (GuiArmorBridgeSC's tab row). */
    public GuiArmorSC(int tab) {
        selectedPiece = tab;
    }

    /** The tabs this player has now (the same rule as initGui), for GuiArmorBridgeSC's tab row. */
    public static List<Integer> tabsFor(net.minecraft.client.Minecraft mc) {
        List<Integer> out = new ArrayList<Integer>();
        for (int type = 0; type < 4; type++) {
            if (ArmorLogicSC.piece(mc.thePlayer, type) != null) {
                out.add(type);
            }
        }
        if (BladeLogicSC.held(mc.thePlayer) != null) {
            out.add(BLADE_TAB);
        }
        if (DrillLogicSC.held(mc.thePlayer) != null) {
            out.add(DRILL_TAB);
        }
        if (ArmorLogicSC.piece(mc.thePlayer, 1) != null) {
            out.add(MODE_TAB);
        }
        boolean any = false;
        for (int t = 0; t < 4; t++) {
            any |= ArmorLogicSC.piece(mc.thePlayer, t) != null;
        }
        if (any) {
            out.add(LIFE_TAB);
        }
        if (com.sc.util.SingularLevel.wearsSingular(mc.thePlayer)) {
            out.add(LEVEL_TAB);
        }
        if (com.sc.util.SingularLevel.isSingular(mc.thePlayer.getCurrentArmor(3))) {
            out.add(BRIDGE_TAB);
        }
        return out;
    }

    /** A tab's caption. */
    public static String tabLabel(int type) {
        return type == BRIDGE_TAB ? Lang.tr("sc.bridge.armour.tab") : type == LEVEL_TAB ? Lang.tr("sc.levelgui.tab") : type == LIFE_TAB
                ? Lang.tr("sc.lifegui.tab.short") : type == BLADE_TAB ? Lang.tr("sc.bladegui.tab") : type == MODE_TAB ? Lang.tr("sc.modegui.tab")
                : type == DRILL_TAB ? Lang.tr("sc.drillgui.tab") : Lang.tr("sc.armorhud.piece." + type);
    }
    /** Level tab: branch buttons (level 3 A / B, level 5 A / B), profiles 0..2, "save current", the page switch, the HUD place. */
    private static final int LV_BR = 1020, LV_PROF = 1024, LV_SAVE = 1027, LV_PAGE = 1028, LV_HUD = 1029;
    private static final int BLADE_BASE = 500, MODE_BASE = 600, DRILL_BASE = 700, TAB_BASE = 900, MODE_ID = 1000, CHIPS_ID = 1001, COLOR_ID = 1002,
            FILL_ALL_ID = 1003, BIND_BASE = 2000;
    /** Chestplate tab: the free branch choice (Р2) - the first / the second side of the lowest level not chosen yet. */
    private static final int BRANCH_A_ID = 1004, BRANCH_B_ID = 1005;
    /** Life support tab: "fill from this inventory slot" buttons (FILL_BASE + slot, under BIND_BASE). */
    private static final int FILL_BASE = 1100, FILL_MAX = 5;
    /** "Fill everything": the count on its button stops at this many containers (the server pours them all with one GAS_FILL_ALL). */
    private static final int FILL_ALL_MAX = 64;
    /**
     * The Singular blade / drill tabs (docs/plan-singular-tools.md §4): the page switch (functions / level), the
     * branches (TL_BRANCH + branch - 1), the forms (TL_FORM + BladeForm ordinal, under TL_FORM_MAX), the drill's mode step.
     * All between FILL_BASE + 64 and BIND_BASE: refresh() and the feature hovers leave them alone.
     */
    private static final int TL_PAGE = 1200, TL_BRANCH = 1201, TL_FORM = 1210, TL_FORM_MAX = 20, TL_HOLE = 1230;

    // ---- layout ----
    private static final int WIN_W = 470, WIN_H = 236, KEY_W = 20, CHIP_W = 26, GAP = 3, COL_GAP = 8, GAS_PITCH = 11;
    private static final int LOW_PCT = 20, CRIT_PCT = 5;
    private static final int C_FRAME = 0xB0101418, C_EDGE = 0xFF46505A, C_PANEL = 0xC01E252C, C_STRIP = 0xC0242C34, C_SEL = 0xFF2D3C4B;
    private static final int ORANGE = 0xFF8C3C, RED = 0xFF5050, DIM = 0x909090, HEAD = 0x8C8C8C;

    private int winX, winW, top, winH, contentX, contentW, contentY, footY;
    /** Left panel: 2 full, 1 without the percentages, 0 hidden. */
    private int panelMode;
    /** Pitch of the gas lines in the panel. */
    private int panelLine = 9;
    /** Per piece: {x, y, w, h} of its block in the panel. */
    private final int[][] pieceRect = new int[4][];
    private int pitch = 16, btnH = 14;
    private final List<Integer> tabs = new ArrayList<Integer>();

    /** A function's row: switch, gas chip (armour only), key. */
    private static final class Row {
        final Enum<?> f;
        final int x, y, w, h;
        final boolean chip;

        Row(Enum<?> f, int x, int y, int w, int h, boolean chip) {
            this.f = f;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.chip = chip;
        }
    }

    private final List<Row> rows = new ArrayList<Row>();
    /** Column headings over the rows: {x, y, switch width, chip 1/0, column width}. */
    private final List<int[]> heads = new ArrayList<int[]>();
    /** Mode tab: {mode key ordinal, description x, y, width}. */
    private final List<int[]> modeDescs = new ArrayList<int[]>();
    private int modeHeadW;

    // ---- life support tab layout (set by initGui) ----
    private int tableX, tableW, lifeY = -1, coolY, sysX, sysW, sysHeadY = -1, fillX, fillW, fillHeadY = -1;
    /** What the tab was built for - rebuilt when the worn pieces or the inventory's gas containers change. */
    private String layoutSig = "";
    /** Per fill button: {slot, gas ordinal, mB, whole-only 1/0, fits 1/0}. */
    private final List<int[]> fills = new ArrayList<int[]>();
    /** Ticks the "fill everything" button stays off after a press (the server answers meanwhile). */
    private int fillAllWait;
    /** The power mode caption in the status strip, {x, y, w, h} as last drawn (a click steps the mode), or null. */
    private int[] modeLabel;
    /** Life support tab: the dim "N more ..." lines under the systems / the fill buttons (y -1: none), with their text. */
    private int sysMoreY = -1, fillMoreY = -1;
    private String sysMore = "", fillMore = "";

    /**
     * The life support systems the armour logic adds at the end of ArmorFeature. Looked up by name,
     * so this screen builds (and simply lists fewer rows) while they don't exist yet.
     */
    private static final String[] LIFE_FEATURE_NAMES = {"BOOSTER", "SEARCHLIGHT", "FUSION_CELL"};

    private static List<ArmorFeature> lifeFeatures() {
        List<ArmorFeature> out = new ArrayList<ArmorFeature>();
        for (String n : LIFE_FEATURE_NAMES) {
            try {
                out.add(ArmorFeature.valueOf(n));
            } catch (IllegalArgumentException e) {
                // not in this build
            }
        }
        return out;
    }

    private int selectedPiece = -1;
    // ---- level tab layout (set by initLevel) ----
    /** Two columns, or one with two pages (levelPage 0: the levels, 1: branches / profiles / bonuses). */
    private boolean lvTwo;
    private int levelPage, lvLx, lvLw, lvRx, lvRw, lvPageW, lvBranchY = -1, lvBranchRows, lvNoteY = -1, lvProfHeadY = -1, lvKeyY = -1, lvBonusY = -1;
    /** The "remove chips" button is on screen (rebuilt when the chips come or go). */
    private boolean chipsShown;
    // ---- Singular tool tabs (set by initGui / initToolInfo) ----
    /** 0: the functions (rows), 1: level, form / mode, branch, gas. */
    private int toolPage;
    /** The page switch's width at the foot (0: none - not a Singular tool). */
    private int tlPageW;
    private int tlTitleY = -1, tlLevelY = -1, tlReadyY = -1, tlFormY = -1, tlBranchY = -1, tlNoteY = -1, tlPerkY = -1, tlGasY = -1, tlOpensY = -1;
    /** The function (ArmorFeature, BladeFeature or PowerModeKey) whose key is being set (waiting for a key press), or null. */
    private Enum<?> capturing;

    private ItemStack blade() {
        return BladeLogicSC.held(mc.thePlayer);
    }

    private ItemStack drill() {
        return DrillLogicSC.held(mc.thePlayer);
    }

    /** The function behind a switch or key button, or null. */
    private static Enum<?> featureOf(int id) {
        if (id >= BIND_BASE) {
            id -= BIND_BASE;
        }
        if (id >= DRILL_BASE && id < TAB_BASE) {
            return DrillFeature.of(id - DRILL_BASE);
        }
        if (id >= MODE_BASE && id < DRILL_BASE) {
            return PowerModeKey.of(id - MODE_BASE);
        }
        if (id >= BLADE_BASE && id < MODE_BASE) {
            return BladeFeature.of(id - BLADE_BASE);
        }
        return id < BLADE_BASE ? ArmorFeature.of(id) : null;
    }

    private static int switchId(Enum<?> f) {
        return f instanceof BladeFeature ? BLADE_BASE + f.ordinal() : f instanceof PowerModeKey ? MODE_BASE + f.ordinal()
                : f instanceof DrillFeature ? DRILL_BASE + f.ordinal() : f.ordinal();
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void initGui() {
        buttonList.clear();
        rows.clear();
        heads.clear();
        modeDescs.clear();
        fills.clear();
        fillHeadY = -1;
        sysHeadY = -1;
        sysMoreY = -1;
        fillMoreY = -1;
        lifeY = -1;
        tlPageW = 0;
        tabs.clear();
        for (int type = 0; type < 4; type++) {
            if (ArmorLogicSC.piece(mc.thePlayer, type) != null) {
                tabs.add(type);
            }
        }
        if (blade() != null) {
            tabs.add(BLADE_TAB);
        }
        if (drill() != null) {
            tabs.add(DRILL_TAB);
        }
        if (ArmorLogicSC.piece(mc.thePlayer, 1) != null) {
            tabs.add(MODE_TAB);
        }
        if (anyPieceWorn()) {
            tabs.add(LIFE_TAB);
        }
        if (com.sc.util.SingularLevel.wearsSingular(mc.thePlayer)) {
            tabs.add(LEVEL_TAB);
        }
        if (com.sc.util.SingularLevel.isSingular(mc.thePlayer.getCurrentArmor(3))) {
            tabs.add(BRIDGE_TAB);
        }
        if (selectedPiece == BRIDGE_TAB && tabs.contains(BRIDGE_TAB)) {
            mc.displayGuiScreen(new GuiArmorBridgeSC());       // the bridge tab is its own screen
            return;
        }
        if (selectedPiece < 0 || !tabs.contains(selectedPiece)) {
            selectedPiece = tabs.isEmpty() ? -1 : tabs.get(0);
        }
        layoutSig = signature();

        winW = Math.min(WIN_W, width - 8);
        winX = (width - winW) / 2;
        winH = Math.min(WIN_H, height - 4);
        top = Math.max(2, (height - winH) / 2);
        contentY = top + 53;
        footY = top + winH - 11;

        int n = tabs.size();
        int tabW = n == 0 ? 70 : Math.max(24, Math.min(80, (winW - 8) / n - 2));   // eight tabs on a narrow screen
        int tx = width / 2 - n * (tabW + 2) / 2 + 1;
        for (int type : tabs) {
            String label = tabLabel(type);
            GuiButton tab = new TextFitSC.Button(TAB_BASE + type, tx, top + 15, tabW, 16, label);
            tab.enabled = type != selectedPiece;                // the pressed-in one is the open tab
            buttonList.add(tab);
            tx += tabW + 2;
        }
        setPanel(0);
        if (selectedPiece < 0) {
            refresh();
            return;
        }
        if (selectedPiece == LIFE_TAB) {
            initLife();
        } else if (selectedPiece == LEVEL_TAB) {
            initLevel();
        } else if (selectedPiece == MODE_TAB) {
            int[] r = chooseRows(PowerModeKey.values().length, 130, 0);
            int btnW = Math.max(110, Math.min(150, contentW - KEY_W - GAP - 6 - 100));
            btnW = Math.min(btnW, contentW - KEY_W - GAP);
            modeHeadW = btnW + GAP + KEY_W;
            int y = contentY + 10;
            for (PowerModeKey k : PowerModeKey.values()) {
                addRow(k, contentX, y, btnW, false);
                int dx = contentX + btnW + GAP + KEY_W + 6;
                modeDescs.add(new int[]{k.ordinal(), dx, y + (btnH - 8) / 2, contentX + contentW - dx});
                y += r[1];
            }
        } else if (toolPage == 1 && singularTool() != null) {
            initToolInfo(singularTool());
            addToolPageButton();
        } else {
            List<Enum<?>> list = new ArrayList<Enum<?>>();
            boolean armour = selectedPiece < 4;
            boolean chips = armour || singularTool() != null;    // the Singular tools' functions show their gas (from the armour) too
            if (selectedPiece == DRILL_TAB) {
                DrillType t = ItemDrillSC.typeOf(drill());
                for (DrillFeature f : DrillFeature.values()) {
                    if (f.availableIn(t)) {
                        list.add(f);
                    }
                }
            } else if (selectedPiece == BLADE_TAB) {
                BladeType t = ItemBladeSC.typeOf(blade());
                for (BladeFeature f : BladeFeature.values()) {
                    if (f.availableIn(t)) {
                        list.add(f);
                    }
                }
            } else {
                ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, selectedPiece);
                for (ArmorFeature f : ArmorFeature.values()) {
                    if (f.availableIn(ArmorLogicSC.suitOf(piece), selectedPiece)) {
                        list.add(f);
                    }
                }
            }
            boolean chest = selectedPiece == 1;
            int branchLevel = chest ? pendingBranch() : 0;
            int[] r = chooseRows(list.size(), chips ? 150 : 140, chest ? (branchLevel > 0 ? 40 : 20) : 0);
            int cols = r[0];
            int colW = (contentW - (cols - 1) * COL_GAP) / cols;
            if (cols == 1) {
                colW = Math.min(colW, 240);                // one column: no endless buttons on a wide window
            }
            int per = Math.max(1, (list.size() + cols - 1) / cols);
            int rowsY = contentY + 10;
            for (int c = 0; c < cols && c * per < list.size(); c++) {
                int x = contentX + c * (colW + COL_GAP);
                heads.add(new int[]{x, contentY, colW - KEY_W - GAP - (chips ? CHIP_W + GAP : 0), chips ? 1 : 0, colW});
            }
            for (int i = 0; i < list.size(); i++) {
                int x = contentX + (i / per) * (colW + COL_GAP);
                addRow(list.get(i), x, rowsY + (i % per) * r[1], colW - KEY_W - GAP - (chips ? CHIP_W + GAP : 0), chips);
            }
            if (singularTool() != null) {
                addToolPageButton();
            }
            int y = rowsY + per * r[1] + 4;
            chipsShown = chest && ItemArmorChipSC.hasChips(ArmorLogicSC.piece(mc.thePlayer, 1));
            if (chest) {                                   // chips out / light colour / power mode, in one row
                List<Integer> ids = new ArrayList<Integer>();
                if (chipsShown) {
                    ids.add(CHIPS_ID);
                }
                ids.add(COLOR_ID);
                ids.add(MODE_ID);
                int span = cols == 1 ? colW : contentW;
                int bw = (span - (ids.size() - 1) * 4) / ids.size();
                for (int i = 0; i < ids.size(); i++) {
                    String label = ids.get(i) == CHIPS_ID ? Lang.tr("sc.armorgui.chips.remove") : "";
                    buttonList.add(new TextFitSC.Button(ids.get(i), contentX + i * (bw + 4), y, bw, 16, label));
                }
                if (branchLevel > 0) {                     // Р2: the chestplate reached a branch level - pick a side (free once)
                    int hw = (span - 4) / 2;
                    for (int c = 1; c <= 2; c++) {
                        ArmorFeature bf = com.sc.util.SingularLevel.branchFeature(branchLevel, c);
                        buttonList.add(new TextFitSC.Button(c == 1 ? BRANCH_A_ID : BRANCH_B_ID, contentX + (c - 1) * (hw + 4), y + 20, hw, 16,
                                "§d" + Lang.tr("sc.armorgui.branch.pick", branchLevel, nameOf(bf))));
                    }
                }
            }
        }
        refresh();
    }

    private int panelW(int mode) {
        return mode == 2 ? 100 : mode == 1 ? 80 : 0;
    }

    private void setPanel(int mode) {
        panelMode = mode;
        int pw = panelW(mode);
        contentX = winX + 5 + (pw > 0 ? pw + 5 : 0);
        contentW = winX + winW - 5 - contentX;
    }

    /** The tanks of a worn piece, in the gases' order. */
    private static List<Gas> tanksOf(ItemStack w) {
        List<Gas> out = new ArrayList<Gas>();
        if (w != null) {
            for (Gas g : Gas.values()) {
                if (ArmorGasSC.capacity(w, g) > 0) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** Lays the left panel out for `mode` (pieceRect); false when it doesn't fit (or there's nothing to show). */
    private boolean layoutPanel(int mode) {
        if (mode == 0) {
            return true;
        }
        if (!anyPieceWorn()) {
            return false;
        }
        int px = winX + 5, py = top + 51, pw = panelW(mode), ph = top + winH - 4 - py;
        for (int lp = 9; lp >= 8; lp--) {
            int y = py + 3, gap = 4;
            boolean fits = true;
            for (int t = 0; t < 4; t++) {
                int h = Math.max(20, tanksOf(ArmorGasSC.worn(mc.thePlayer, t)).size() * lp + 4);
                pieceRect[t] = new int[]{px + 2, y, pw - 4, h};
                y += h + gap;
                fits &= y <= py + ph;
            }
            if (fits) {
                panelLine = lp;
                return true;
            }
        }
        return false;
    }

    /** Tries `cols` / pitch for n rows in width cw: {cols, pitch}, or null when it would be cramped (loose: the best there is). */
    private int[] fitRows(int n, int cw, int colMin, int extraH, boolean loose) {
        int avail = (footY - 2) - (contentY + 10) - extraH;
        int[] order = n > 8 ? new int[]{2, 1} : new int[]{1, 2};
        for (int cols : order) {
            int colW = (cw - (cols - 1) * COL_GAP) / cols;
            if (colW < colMin || cols > Math.max(1, n)) {
                continue;
            }
            int per = Math.max(1, (n + cols - 1) / cols);
            int p = Math.min(16, avail / per);
            if (p >= 15) {
                return new int[]{cols, p};
            }
        }
        if (!loose) {
            return null;
        }
        // two columns also when one column can't hold the rows even at the smallest pitch (Exo chestplate, 320x200)
        int cols = n > 1 && (cw >= 2 * colMin + COL_GAP || avail / n < 12) ? 2 : 1;
        int per = Math.max(1, (n + cols - 1) / cols);
        return new int[]{cols, Math.max(12, Math.min(16, avail / per))};
    }

    /** Picks the panel and the columns for a tab of n rows; sets pitch / btnH. @return {cols, pitch} */
    private int[] chooseRows(int n, int colMin, int extraH) {
        int[] r = null;
        for (int mode = 2; mode >= 0 && r == null; mode--) {
            if (layoutPanel(mode)) {
                setPanel(mode);
                r = fitRows(n, contentW, colMin, extraH, false);
            }
        }
        if (r == null) {                                   // a tiny screen: no panel, rows squeezed
            setPanel(0);
            r = fitRows(n, contentW, colMin, extraH, true);
        }
        pitch = r[1];
        btnH = pitch - 2;
        return r;
    }

    private void addRow(Enum<?> f, int x, int y, int switchW, boolean chip) {
        buttonList.add(new TextFitSC.Button(switchId(f), x, y, switchW, btnH, ""));
        int keyX = x + switchW + GAP + (chip ? CHIP_W + GAP : 0);
        buttonList.add(new TextFitSC.Button(BIND_BASE + switchId(f), keyX, y, KEY_W, btnH, ""));
        rows.add(new Row(f, x, y, switchW, btnH, chip));
    }

    // ------------------------------------------------------------------ life support tab

    private boolean anyPieceWorn() {
        for (int t = 0; t < 4; t++) {
            if (ArmorGasSC.worn(mc.thePlayer, t) != null) {
                return true;
            }
        }
        return false;
    }

    /** The gases the tab (and the HUD) shows: tanks in the worn suit, or helium radiators without the chestplate's loop. */
    private List<Gas> shownGases() {
        List<Gas> out = new ArrayList<Gas>();
        for (Gas g : Gas.values()) {
            if (ArmorGasSC.suitCapacity(mc.thePlayer, g) > 0 || g == Gas.HELIUM && noHeliumLoop()) {
                out.add(g);
            }
        }
        return out;
    }

    /** Helium radiators worn but no chestplate: the loop is missing. */
    private boolean noHeliumLoop() {
        if (ArmorGasSC.worn(mc.thePlayer, ArmorGasSC.CHEST) != null) {
            return false;
        }
        for (int t : new int[]{ArmorGasSC.HELMET, ArmorGasSC.LEGS, ArmorGasSC.BOOTS}) {
            if (ArmorGasSC.baseCapacity(ArmorGasSC.worn(mc.thePlayer, t), Gas.HELIUM) > 0) {
                return true;
            }
        }
        return false;
    }

    /** The gas and amount of a container: {gas ordinal, mB, whole-only 1/0}, or null if it carries none of the suit's gases. */
    private static int[] gasIn(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return null;
        }
        FluidStack fs = null;
        boolean whole;
        if (FluidContainerRegistry.isFilledContainer(s)) {
            fs = FluidContainerRegistry.getFluidForFilledItem(s);
            whole = true;
        } else if (s.getItem() instanceof IFluidContainerItem && s.stackSize == 1) {
            fs = ((IFluidContainerItem) s.getItem()).getFluid(s);
            whole = false;
        } else {
            return null;
        }
        Gas g = fs == null || fs.amount <= 0 ? null : Gas.of(fs.getFluid());
        return g == null ? null : new int[]{g.ordinal(), fs.amount, whole ? 1 : 0};
    }

    /** The lowest branch level (3 / 5) the worn chestplate has reached without a choice, 0: none (creative: none - both work). */
    private int pendingBranch() {
        ItemStack chest = ArmorLogicSC.piece(mc.thePlayer, 1);
        if (mc.thePlayer.capabilities.isCreativeMode) {
            return 0;
        }
        return com.sc.util.SingularLevel.branchPending(chest, 3) ? 3 : com.sc.util.SingularLevel.branchPending(chest, 5) ? 5 : 0;
    }

    /** A Singular branch function the chestplate's choice (or no choice yet) keeps off; creative: none. */
    private boolean branchLocked(Enum<?> f) {
        if (f instanceof BladeFeature || f instanceof DrillFeature) {
            ItemStack t = f instanceof BladeFeature ? blade() : drill();
            return t != null && !toolUnlocked(f) && lockedLevel(f) == 0;
        }
        if (!(f instanceof ArmorFeature) || mc.thePlayer.capabilities.isCreativeMode) {
            return false;
        }
        ArmorFeature a = (ArmorFeature) f;
        ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, a.piece);
        return piece != null && !com.sc.util.SingularLevel.branchAllowed(mc.thePlayer, a, piece);
    }

    /** The worn pieces and their tanks - what the left panel and the rows were laid out for. */
    private String wornSignature() {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < 4; t++) {
            ItemStack w = ArmorGasSC.worn(mc.thePlayer, t);
            sb.append(w == null ? "-" : Item.getIdFromItem(w.getItem()) + ":" + ArmorGasSC.capacityBonusPercent(w)
                    + ":" + com.sc.util.SingularLevel.levelOf(w)).append(';');
        }
        sb.append(pendingBranch()).append(';');                     // a branch picked / a new one to pick: the chestplate's buttons
        ItemStack tool = blade() != null ? blade() : drill();    // a blade / drill taken in hand: its tab and rows
        sb.append(tool == null ? "-" : String.valueOf(Item.getIdFromItem(tool.getItem())));
        return sb.toString();
    }

    /** The worn pieces, their tanks and the inventory's gas containers - what initLife() laid out. */
    private String lifeSignature() {
        StringBuilder sb = new StringBuilder(wornSignature());
        for (ArmorFeature f : lifeFeatures()) {
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, f.piece);
            sb.append(piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece) ? 'f' : '-');
        }
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            int[] gi = gasIn(inv[i]);
            if (gi != null) {
                sb.append('|').append(i).append(':').append(Item.getIdFromItem(inv[i].getItem())).append(':').append(inv[i].getItemDamage())
                        .append(':').append(inv[i].stackSize).append(':').append(gi[0]).append(':').append(gi[1]);
            }
        }
        return sb.toString();
    }

    /** A tank filling up or running down turns a fill button off or on. */
    private void refreshFills() {
        for (int[] f : fills) {
            int room = ArmorGasSC.suitFill(mc.thePlayer, Gas.values()[f[1]], f[2], true);
            f[4] = (f[3] == 1 ? room == f[2] : room > 0) ? 1 : 0;
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.id == FILL_BASE + f[0]) {
                    b.enabled = f[4] == 1;
                }
            }
        }
        int plan = fillAllPlan().size();
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == FILL_ALL_ID) {
                b.displayString = Lang.tr("sc.lifegui.fillall", plan);
                b.enabled = plan > 0 && fillAllWait <= 0;
            }
        }
    }

    /**
     * "Fill everything": the inventory slots to pour in, in order, one entry per single pour -
     * whole-only containers (buckets, cells) as many times as whole ones fit, a tank-in-an-item once.
     * Counted against the room the tanks have now, so the server (which checks it all again) takes
     * every one of them; a stale count at worst gets a "doesn't fit" and nothing is spent.
     */
    private List<Integer> fillAllPlan() {
        List<Integer> out = new ArrayList<Integer>();
        EntityPlayer p = mc.thePlayer;
        int[] room = new int[Gas.values().length];
        for (Gas g : Gas.values()) {
            room[g.ordinal()] = ArmorGasSC.suitFill(p, g, 1 << 30, true);
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && out.size() < FILL_ALL_MAX; i++) {
            int[] gi = gasIn(inv[i]);
            if (gi == null || room[gi[0]] <= 0) {
                continue;
            }
            if (gi[2] == 1) {
                int k = Math.min(inv[i].stackSize, room[gi[0]] / gi[1]);
                for (int j = 0; j < k && out.size() < FILL_ALL_MAX; j++) {
                    out.add(i);
                    room[gi[0]] -= gi[1];
                }
            } else {
                out.add(i);
                room[gi[0]] -= Math.min(room[gi[0]], gi[1]);
            }
        }
        return out;
    }

    private void initLife() {
        // the panel and the columns: the table with the controls beside it, or under it
        boolean side = false, halves = false;
        for (int mode = 2; mode >= 0 && !side; mode--) {
            if (layoutPanel(mode)) {
                setPanel(mode);
                side = contentW >= 344;
            }
        }
        if (!side) {
            boolean found = false;
            for (int mode = 2; mode >= 0 && !found; mode--) {
                if (layoutPanel(mode)) {
                    setPanel(mode);
                    found = contentW >= 288;
                }
            }
            halves = found;
            if (!found) {
                setPanel(0);
            }
        }
        pitch = 16;
        btnH = 14;
        int rc = side ? Math.max(136, Math.min(150, contentW - 208)) : 0;
        tableX = contentX;
        tableW = side ? contentW - COL_GAP - rc : contentW;
        lifeY = contentY + 10;
        coolY = lifeY + shownGases().size() * GAS_PITCH + 2;
        int y;
        if (side) {
            sysX = fillX = contentX + tableW + COL_GAP;
            sysW = fillW = rc;
            y = contentY;
        } else {
            y = coolY + 14;
            sysX = contentX;
            sysW = halves ? (contentW - COL_GAP) / 2 : contentW;
            fillX = halves ? sysX + sysW + COL_GAP : contentX;
            fillW = sysW;
        }
        int bottom = footY - 2;
        List<ArmorFeature> sys = new ArrayList<ArmorFeature>();
        for (ArmorFeature f : lifeFeatures()) {
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, f.piece);
            if (piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)) {
                sys.add(f);
            }
        }
        int sysTop = y;
        if (!sys.isEmpty() && y + 10 + btnH <= bottom) {
            sysHeadY = y;
            y += 10;
            int fitAll = (bottom - y - btnH) / pitch + 1;       // rows that fit with nothing under them
            int shown = sys.size() <= fitAll ? sys.size() : Math.max(0, (bottom - 9 - y) / pitch);   // else room for the note
            for (int k = 0; k < shown; k++) {
                addRow(sys.get(k), sysX, y, sysW - KEY_W - GAP, false);
                y += pitch;
            }
            if (shown < sys.size()) {                           // not cut silently: the rest is on the pieces' tabs
                sysMoreY = y;
                sysMore = Lang.tr("sc.lifegui.systems.more", sys.size() - shown);
                y += 10;
            }
            y += 4;
        } else if (!sys.isEmpty() && y + 9 <= bottom) {
            sysMoreY = y;
            sysMore = Lang.tr("sc.lifegui.systems.more", sys.size());
            y += 14;
        }
        if (halves) {
            y = sysTop;                                       // the refuelling beside the systems
        }
        // one button per kind of container (same item, gas and amount), the first slot holding it
        List<String> seen = new ArrayList<String>();
        List<Integer> counts = new ArrayList<Integer>();
        List<String> extraKinds = new ArrayList<String>();
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            int[] gi = gasIn(inv[i]);
            if (gi == null || ArmorGasSC.suitCapacity(mc.thePlayer, Gas.values()[gi[0]]) <= 0) {
                continue;
            }
            String kind = Item.getIdFromItem(inv[i].getItem()) + ":" + inv[i].getItemDamage() + ":" + gi[0] + ":" + gi[1];
            int at = seen.indexOf(kind);
            if (at >= 0) {
                counts.set(at, counts.get(at) + inv[i].stackSize);
                continue;
            }
            if (seen.size() >= FILL_MAX) {
                if (!extraKinds.contains(kind)) {
                    extraKinds.add(kind);                       // no button of its own: counted in the "N more" line
                }
                continue;
            }
            seen.add(kind);
            counts.add(inv[i].stackSize);
            Gas g = Gas.values()[gi[0]];
            int room = ArmorGasSC.suitFill(mc.thePlayer, g, gi[1], true);
            boolean fits = gi[2] == 1 ? room == gi[1] : room > 0;
            fills.add(new int[]{i, gi[0], gi[1], gi[2], fits ? 1 : 0});
        }
        if (y + 9 > bottom) {
            return;
        }
        fillHeadY = y;
        y += 10;
        boolean allShown = false;
        if (!fills.isEmpty() && y + btnH <= bottom) {
            GuiButton all = new TextFitSC.Button(FILL_ALL_ID, fillX, y, fillW, btnH, "");
            buttonList.add(all);
            y += pitch + 2;
            allShown = true;
        }
        int total = fills.size() + extraKinds.size();
        int fitAll = y + btnH <= bottom ? (bottom - y - btnH) / pitch + 1 : 0;
        int shown = Math.min(fills.size(), total <= fitAll ? total : Math.max(0, (bottom - 9 - y) / pitch));   // else room for the note
        if (shown < total && y + 9 <= bottom && !fills.isEmpty()) {   // not cut silently: "fill everything" takes the rest too
            fillMoreY = y + shown * pitch;
            fillMore = Lang.tr(allShown ? "sc.lifegui.fill.more" : "sc.lifegui.fill.more.plain", total - shown);
        }
        for (int k = 0; k < shown; k++) {
            int[] f = fills.get(k);
            String label = Lang.tr("sc.lifegui.fill", GasUiSC.shortName(Gas.values()[f[1]]), f[2]) + (counts.get(k) > 1 ? " §7(" + counts.get(k) + ")" : "");
            GuiButton b = new TextFitSC.Button(FILL_BASE + f[0], fillX, y, fillW, btnH, label);
            b.enabled = f[4] == 1;
            buttonList.add(b);
            y += pitch;
        }
        refreshFills();
    }

    // ------------------------------------------------------------------ level tab (stage 5: М3 / М4, plan §6 "Отображение")

    /** What the open tab was laid out for - rebuilt when it changes. */
    private String signature() {
        return selectedPiece == LIFE_TAB ? lifeSignature() : selectedPiece == LEVEL_TAB ? levelSignature() : wornSignature() + toolSignature();
    }

    /** The worn pieces plus what the level tab's buttons show: the branch choices, the profiles, the page. */
    private String levelSignature() {
        StringBuilder sb = new StringBuilder(wornSignature());
        ItemStack chest = com.sc.util.SingularProfiles.holder(ArmorGasSC.wornSet(mc.thePlayer));
        sb.append('|').append(com.sc.util.SingularLevel.branchChoice(chest, 3)).append(com.sc.util.SingularLevel.branchChoice(chest, 5))
                .append('|').append(com.sc.util.SingularProfiles.active(chest));
        for (int i = 0; i < com.sc.util.SingularProfiles.COUNT; i++) {
            sb.append(com.sc.util.SingularProfiles.has(chest, i) ? 'p' : '-');
        }
        sb.append('|').append(levelPage).append(mc.thePlayer.capabilities.isCreativeMode ? 'c' : 's');
        return sb.toString();
    }

    private static String fkey(ArmorFeature f) {
        return f.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Two columns (the levels | branches, profiles, bonuses) where they fit - the left panel goes first;
     * a narrow window: one column, two pages switched by a button at the top right.
     */
    private void initLevel() {
        boolean two = false;
        for (int mode = 2; mode >= 0 && !two; mode--) {
            if (layoutPanel(mode)) {
                setPanel(mode);
                two = contentW >= 330;
            }
        }
        if (!two) {
            setPanel(0);
            two = contentW >= 290;
        }
        lvTwo = two;
        pitch = 16;
        btnH = 14;
        lvBranchY = lvNoteY = lvProfHeadY = lvKeyY = lvBonusY = -1;
        lvBranchRows = 0;
        if (two) {
            levelPage = 0;
            lvPageW = 0;
            lvLx = contentX;
            lvLw = (contentW - COL_GAP) * 54 / 100;
            lvRx = lvLx + lvLw + COL_GAP;
            lvRw = contentX + contentW - lvRx;
        } else {
            lvLx = lvRx = contentX;
            lvLw = lvRw = contentW;
            lvPageW = Math.min(96, contentW / 3);
            buttonList.add(new TextFitSC.Button(LV_PAGE, contentX + contentW - lvPageW, contentY - 3, lvPageW, 12,
                    Lang.tr(levelPage == 0 ? "sc.levelgui.page.next" : "sc.levelgui.page.back")));
            if (levelPage == 0) {
                return;
            }
        }
        EntityPlayer p = mc.thePlayer;
        boolean creative = p.capabilities.isCreativeMode;
        ItemStack chest = com.sc.util.SingularProfiles.holder(ArmorGasSC.wornSet(p));
        int bottom = footY - 2;
        int y = contentY;
        lvBranchY = y;
        y += 11;
        int labW = 30, bw = (lvRw - labW - 4) / 2;
        for (int bl : new int[]{3, 5}) {
            if (y + 14 > bottom) {
                break;
            }
            for (int c = 1; c <= 2; c++) {
                GuiButton b = new TextFitSC.Button(LV_BR + (bl == 3 ? 0 : 2) + c - 1, lvRx + labW + (c - 1) * (bw + 4), y, bw, 14, branchLabel(chest, bl, c));
                b.enabled = chest != null && !creative && com.sc.util.SingularLevel.branchPending(chest, bl);
                buttonList.add(b);
            }
            lvBranchRows++;
            y += 16;
        }
        lvNoteY = y;
        y += 13;
        if (y + 10 + 14 <= bottom) {
            lvProfHeadY = y;
            y += 11;
            int act = com.sc.util.SingularProfiles.active(chest);
            int pw = (lvRw - 8) / 3;
            for (int i = 0; i < com.sc.util.SingularProfiles.COUNT; i++) {
                String n = Lang.tr("sc.armor.sing.profile.name." + i);
                String label = i == act ? "§d" + n : com.sc.util.SingularProfiles.has(chest, i) ? n : "§7" + n;
                GuiButton b = new TextFitSC.Button(LV_PROF + i, lvRx + i * (pw + 4), y, pw, 14, label);
                b.enabled = chest != null;
                buttonList.add(b);
            }
            y += 16;
            if (y + 14 <= bottom) {
                int sw = (lvRw - 4) * 52 / 100;
                GuiButton save = new TextFitSC.Button(LV_SAVE, lvRx, y, sw, 14, Lang.tr("sc.levelgui.profile.save"));
                save.enabled = chest != null && act >= 0;
                buttonList.add(save);
                buttonList.add(new TextFitSC.Button(LV_HUD, lvRx + sw + 4, y, lvRw - sw - 4, 14, hudLabel()));
                y += 16;
            }
            lvKeyY = y;
            y += 13;
        }
        lvBonusY = y;
    }

    private static String hudLabel() {
        return Lang.tr("sc.levelgui.hud", Lang.tr("sc.levelgui.hud." + ArmorKeyBindsSC.HUD_POS[ArmorKeyBindsSC.hudPos()]));
    }

    /** A branch button: the chosen side green between > <, the other dark; a free pick violet; not reached yet grey. */
    private String branchLabel(ItemStack chest, int bl, int c) {
        ArmorFeature f = com.sc.util.SingularLevel.branchFeature(bl, c);
        String n = Lang.tr("sc.levelgui.branch.short." + fkey(f));
        if (mc.thePlayer.capabilities.isCreativeMode) {
            return "§a" + n;
        }
        if (chest == null) {
            return "§8" + n;
        }
        int ch = com.sc.util.SingularLevel.branchChoice(chest, bl);
        if (ch == c) {
            return "§a> " + n + " <";
        }
        if (ch != com.sc.util.SingularLevel.BRANCH_NONE) {
            return "§8" + n;
        }
        return com.sc.util.SingularLevel.levelOf(chest) >= bl ? "§d" + n : "§7" + n;
    }

    private void levelAction(GuiButton b) {
        if (b.id == LV_PAGE) {
            levelPage = 1 - levelPage;
            initGui();
            return;
        }
        if (b.id == LV_HUD) {
            ArmorKeyBindsSC.cycleHudPos();
            b.displayString = hudLabel();
            return;
        }
        if (b.id == LV_SAVE) {
            int act = com.sc.util.SingularProfiles.activeOf(mc.thePlayer);
            if (act >= 0) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.PROFILE_SAVE, act));
            }
            return;
        }
        if (b.id >= LV_PROF && b.id < LV_PROF + com.sc.util.SingularProfiles.COUNT) {   // the server applies it and sends the pieces back
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.PROFILE_SELECT, b.id - LV_PROF));
            return;
        }
        if (b.id >= LV_BR && b.id < LV_BR + 4) {                // Р2: the free first choice, as on the chestplate's tab
            int bl = b.id - LV_BR < 2 ? 3 : 5, c = (b.id - LV_BR) % 2 + 1;
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BRANCH, ArmorNetSC.branchFeature(bl, c)));
            b.enabled = false;
        }
    }

    /** The level the tasks are shown for: the Singular chestplate's next one, else the lowest worn piece's; 0: all at the top. */
    private static int focusTarget(ItemStack[] w) {
        ItemStack chest = w[ArmorGasSC.CHEST];
        if (com.sc.util.SingularLevel.isSingular(chest) && com.sc.util.SingularLevel.levelOf(chest) < com.sc.util.SingularLevel.MAX) {
            return com.sc.util.SingularLevel.levelOf(chest) + 1;
        }
        int lo = 0;
        for (ItemStack s : w) {
            if (com.sc.util.SingularLevel.isSingular(s) && com.sc.util.SingularLevel.levelOf(s) < com.sc.util.SingularLevel.MAX) {
                lo = lo == 0 ? com.sc.util.SingularLevel.levelOf(s) : Math.min(lo, com.sc.util.SingularLevel.levelOf(s));
            }
        }
        return lo == 0 ? 0 : lo + 1;
    }

    private static String mmss(int s) {
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    /** A task's progress: times as m:ss, gases in mB, the rest as numbers. */
    private static String taskProgress(int target, int i, int[] pr) {
        int code = target * 10 + i;
        if (code == 31 || code == 41) {
            return mmss(pr[0]) + " / " + mmss(pr[1]);
        }
        if (code == 22 || code == 52) {
            return pr[0] + " / " + pr[1] + " " + Lang.tr("sc.levelgui.mb");
        }
        return pr[0] + " / " + pr[1];
    }

    private static String amount(long v) {
        return com.sc.util.SingularStationMath.shortAmount(v, Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"), Lang.tr("sc.singStation.unit.b"));
    }

    private void drawLevel() {
        EntityPlayer p = mc.thePlayer;
        ItemStack[] w = ArmorGasSC.wornSet(p);
        if (lvTwo || levelPage == 0) {
            drawLevelLeft(p, w);
        }
        if (lvTwo || levelPage == 1) {
            drawLevelRight(p, w);
        }
    }

    /** The left column: each piece's level and points, the sync, the next level's tasks, the readiness and the cost. */
    private void drawLevelLeft(EntityPlayer p, ItemStack[] w) {
        int x = lvLx, cw = lvLw, bottom = footY - 1, y = contentY;
        fit(Lang.tr("sc.levelgui.pieces"), x, y, lvTwo ? cw : cw - lvPageW - 4, HEAD);
        y += 11;
        int nameW = Math.min(58, cw / 4), lvW = 26, numW = Math.min(70, cw / 3);
        int barX = x + nameW + lvW, barW = cw - nameW - lvW - numW - 4;
        for (int t = 0; t < 4; t++) {
            ItemStack s = w[t];
            boolean sing = com.sc.util.SingularLevel.isSingular(s);
            fit(Lang.tr("sc.armorhud.piece." + t), x, y, nameW - 2, sing ? 0xE0E0E0 : 0x707070);
            if (!sing) {
                fit(Lang.tr(s == null ? "sc.levelgui.piece.none" : "sc.levelgui.piece.other"), x + nameW, y, cw - nameW, 0x707070);
                y += 11;
                continue;
            }
            int lvl = com.sc.util.SingularLevel.levelOf(s), need = com.sc.util.SingularLevel.threshold(lvl), pts = com.sc.util.SingularLevel.points(s);
            boolean top = lvl >= com.sc.util.SingularLevel.MAX, full = com.sc.util.SingularLevel.pointsFull(s);
            fit(Lang.tr("sc.singStation.lv", lvl), x + nameW, y, lvW - 2, 0xC080FF);
            if (barW >= 16) {
                bar(barX, y + 1, barW, 7, top ? 1 : pts, top ? 1 : need, 0xB060FF, false);
            }
            int nx = barX + Math.max(0, barW) + 4;
            fit(top ? Lang.tr("sc.levelgui.max") : full ? Lang.tr("sc.levelgui.points.full", pts, need) : pts + " / " + need, nx, y, x + cw - nx,
                    top || full ? 0x60FF60 : 0xA0A0A0);
            List<String> tip = new ArrayList<String>();
            tip.add(s.getDisplayName());
            tip.add("§d" + Lang.tr("sc.tooltip.armor.singular.level", lvl, com.sc.util.SingularLevel.MAX));
            if (!top) {
                tip.add((full ? "§a" : "§7") + Lang.tr(full ? "sc.tooltip.armor.singular.points.ready" : "sc.tooltip.armor.singular.points", pts, need));
                tip.add(com.sc.util.SingularLevel.readyToUpgrade(p, s) ? "§a" + Lang.tr("sc.levelgui.ready") : "§7" + Lang.tr("sc.levelgui.piece.tip"));
            }
            TextFitSC.hover(x, y - 1, cw, 11, tip);
            y += 11;
        }
        // Р4: the sync
        int synced = com.sc.util.SingularLevel.syncedLevel(w);
        String sync;
        int syncColor = 0x909090;
        boolean all = true;
        int hi = 0;
        for (int t = 0; t < 4; t++) {
            all &= com.sc.util.SingularLevel.isSingular(w[t]);
            hi = Math.max(hi, com.sc.util.SingularLevel.isSingular(w[t]) ? com.sc.util.SingularLevel.levelOf(w[t]) : 0);
        }
        if (synced > 0) {
            sync = Lang.tr("sc.levelgui.sync.yes", synced, com.sc.util.SingularLevel.SYNC_PCT);
            syncColor = 0x60FF60;
        } else if (!all) {
            sync = Lang.tr("sc.levelgui.sync.notall", com.sc.util.SingularLevel.SYNC_PCT);
        } else if (hi <= 1) {
            sync = Lang.tr("sc.levelgui.sync.one", com.sc.util.SingularLevel.SYNC_PCT);
        } else {
            StringBuilder lag = new StringBuilder();
            for (int t = 0; t < 4; t++) {
                if (com.sc.util.SingularLevel.levelOf(w[t]) < hi) {
                    lag.append(lag.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + t).toLowerCase(Locale.ROOT));
                }
            }
            sync = Lang.tr("sc.levelgui.sync.no", com.sc.util.SingularLevel.SYNC_PCT, lag);
        }
        fit(sync, x, y + 1, cw, syncColor);
        y += 14;
        if (y + 9 > bottom) {
            return;
        }
        int target = focusTarget(w);
        if (target == 0) {
            fit(Lang.tr("sc.levelgui.allmax"), x, y, cw, 0x60FF60);
            return;
        }
        fit(Lang.tr("sc.levelgui.tasks", target), x, y, cw, HEAD);
        TextFitSC.hover(x, y - 1, cw, 10, Lang.tr("sc.levelgui.tasks.tip"));
        y += 11;
        boolean done = com.sc.util.SingularLevel.taskDone(p, target);
        for (int i = 0; i < com.sc.util.SingularLevel.TASKS && y + 9 <= bottom; i++) {
            int[] pr = com.sc.util.SingularLevel.taskProgress(p, target, i);
            boolean ok = pr[1] > 0 && pr[0] >= pr[1];
            drawRect(x, y, x + 7, y + 7, ok ? 0xFF50E050 : 0xFF3A3A44);
            String prog = taskProgress(target, i, pr);
            int pw = Math.min(fontRendererObj.getStringWidth(prog), cw / 2);
            fit(Lang.tr("sc.levelgui.task." + target + "." + i), x + 11, y, cw - 11 - pw - 6, ok ? 0xFFFFFF : done ? 0x808080 : 0xC0C0C0);
            fit(prog, x + cw - pw, y, pw, ok ? 0x60FF60 : 0xA0A0A0);
            y += 10;
        }
        y += 2;
        if (y + 9 > bottom) {
            return;
        }
        int ready = 0;
        int[] levels = new int[4];
        for (int t = 0; t < 4; t++) {
            if (com.sc.util.SingularLevel.readyToUpgrade(p, w[t])) {
                ready++;
                levels[t] = com.sc.util.SingularLevel.levelOf(w[t]);
            }
        }
        if (ready > 0) {
            fit(Lang.tr("sc.levelgui.ready"), x, y, cw, 0x60FF60);
        } else {
            fit(Lang.tr(done ? "sc.levelgui.needpoints" : "sc.levelgui.needtask"), x, y, cw, done ? 0xFFC040 : 0x909090);
            for (int t = 0; t < 4; t++) {                       // not ready yet: what the whole set would cost next
                levels[t] = com.sc.util.SingularLevel.isSingular(w[t]) && com.sc.util.SingularLevel.levelOf(w[t]) < com.sc.util.SingularLevel.MAX
                        ? com.sc.util.SingularLevel.levelOf(w[t]) : 0;
            }
        }
        y += 11;
        int n = com.sc.util.SingularStationMath.pieces(levels);
        if (n <= 0 || y + 9 > bottom) {
            return;
        }
        long[] cost = com.sc.util.SingularStationMath.moderniseCost(levels, false);
        int minutes = com.sc.util.SingularStationMath.moderniseTicks(levels) / com.sc.util.SingularStationMath.TICKS_PER_MINUTE;
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr(ready > 0 ? "sc.levelgui.cost.ready" : "sc.levelgui.cost.next", n, amount(cost[com.sc.util.SingularStationMath.R_EU]),
                Lang.tr("sc.singStation.time.min", minutes)));
        StringBuilder gases = new StringBuilder();
        for (int r = 1; r < com.sc.util.SingularStationMath.RESOURCES; r++) {
            if (cost[r] > 0) {
                gases.append(gases.length() > 0 ? " · " : "").append(GasUiSC.shortName(com.sc.util.SingularStationMath.GAS[r])).append(' ').append(cost[r]);
                tip.add("§7" + GasUiSC.name(com.sc.util.SingularStationMath.GAS[r]) + ": " + cost[r] + " " + Lang.tr("sc.levelgui.mb"));
            }
        }
        tip.add("§7" + Lang.tr(n >= 4 ? "sc.singStation.mul.set" : "sc.singStation.mul.noset", n));
        if (com.sc.util.SingularStationMath.needsCatalyst(levels)) {
            tip.add("§d" + Lang.tr("sc.singStation.catalyst.hint"));
        }
        tip.add("§7" + Lang.tr("sc.levelgui.cost.tip"));
        fit(tip.get(0), x, y, cw, 0xB0B0B0);
        TextFitSC.hover(x, y - 1, cw, 21, tip);
        y += 10;
        if (y + 9 <= bottom && gases.length() > 0) {
            fit(gases.toString(), x, y, cw, 0x909090);
        }
    }

    /** The right column: the branches, the profiles (buttons from initLevel), the level bonuses and what opens next. */
    private void drawLevelRight(EntityPlayer p, ItemStack[] w) {
        int x = lvRx, cw = lvRw, bottom = footY - 1;
        boolean creative = p.capabilities.isCreativeMode;
        ItemStack chest = com.sc.util.SingularProfiles.holder(w);
        if (lvBranchY >= 0) {
            fit(Lang.tr("sc.levelgui.branches"), x, lvBranchY, lvTwo ? cw : cw - lvPageW - 4, HEAD);
            int[] bls = {3, 5};
            for (int k = 0; k < lvBranchRows; k++) {
                int ry = lvBranchY + 11 + k * 16;
                boolean reached = creative || chest != null && com.sc.util.SingularLevel.levelOf(chest) >= bls[k];
                fit(Lang.tr("sc.levelgui.branch.lv", bls[k]), x, ry + 3, 28, reached ? 0xE0E0E0 : 0x707070);
            }
        }
        if (lvNoteY >= 0 && lvNoteY + 9 <= bottom) {
            String note = creative ? Lang.tr("sc.levelgui.branch.creative") : chest == null ? Lang.tr("sc.levelgui.nochest")
                    : Lang.tr("sc.levelgui.branch.note", com.sc.util.SingularStationMath.BRANCH_SM);
            fit(note, x, lvNoteY, cw, 0x808080);
        }
        if (lvProfHeadY >= 0) {
            fit(Lang.tr("sc.levelgui.profiles"), x, lvProfHeadY, cw, HEAD);
        }
        if (lvKeyY >= 0 && lvKeyY + 9 <= bottom) {
            int code = ArmorClientSC.KEY_PROFILE == null ? 0 : ArmorClientSC.KEY_PROFILE.getKeyCode();
            fit(code == 0 ? Lang.tr("sc.levelgui.profile.nokey")
                    : Lang.tr("sc.levelgui.profile.key", net.minecraft.client.settings.GameSettings.getKeyDisplayString(code)), x, lvKeyY, cw, 0x808080);
        }
        int y = lvBonusY;
        if (y < 0 || y + 9 > bottom) {
            return;
        }
        ItemStack ref = chest;
        for (ItemStack s : w) {
            if (ref == null && com.sc.util.SingularLevel.isSingular(s)) {
                ref = s;
            }
        }
        int lvl = com.sc.util.SingularLevel.levelOf(ref);
        boolean sync = com.sc.util.SingularLevel.synced(ref);
        fit(Lang.tr("sc.levelgui.bonus.head", lvl), x, y, cw, HEAD);
        y += 11;
        if (y + 9 > bottom) {
            return;
        }
        String bonus = lvl > 1 ? Lang.tr("sc.tooltip.armor.singular.bonus", com.sc.util.SingularLevel.bonusPercent(lvl, sync, com.sc.util.SingularLevel.TANK_PCT),
                com.sc.util.SingularLevel.bonusPercent(lvl, sync, com.sc.util.SingularLevel.PROTECT_PCT),
                com.sc.util.SingularLevel.bonusPercent(lvl, sync, com.sc.util.SingularLevel.EU_PCT)) + (sync ? " " + Lang.tr("sc.tooltip.armor.singular.sync") : "")
                : Lang.tr("sc.levelgui.bonus.none");
        fit(bonus, x, y, cw, 0xE0E0E0);
        y += 10;
        if (lvl >= com.sc.util.SingularLevel.MAX || y + 9 > bottom) {
            return;
        }
        StringBuilder opens = new StringBuilder();
        for (ArmorFeature f : ArmorFeature.values()) {
            if (f.minSuit == ArmorSuit.SINGULAR && com.sc.util.SingularLevel.requiredLevel(f) == lvl + 1) {
                opens.append(opens.length() > 0 ? ", " : "").append(nameOf(f));
            }
        }
        fit(Lang.tr("sc.tooltip.armor.singular.opens", lvl + 1, opens), x, y, cw, 0xA0A0A0);
    }

    /** Hover texts of the level tab's buttons (registered last: over the buttons' own cut-label ones). */
    private void levelButtonTips() {
        ItemStack chest = com.sc.util.SingularProfiles.holder(ArmorGasSC.wornSet(mc.thePlayer));
        boolean creative = mc.thePlayer.capabilities.isCreativeMode;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            List<String> tip = new ArrayList<String>();
            if (b.id >= LV_BR && b.id < LV_BR + 4) {
                int bl = b.id - LV_BR < 2 ? 3 : 5, c = (b.id - LV_BR) % 2 + 1;
                ArmorFeature f = com.sc.util.SingularLevel.branchFeature(bl, c);
                tip.add(nameOf(f));
                tip.add("§7" + Lang.tr("sc.armorfn." + fkey(f) + ".desc"));
                int ch = com.sc.util.SingularLevel.branchChoice(chest, bl);
                tip.add(creative ? "§a" + Lang.tr("sc.levelgui.branch.creative") : chest == null ? "§c" + Lang.tr("sc.levelgui.nochest")
                        : ch == c ? "§a" + Lang.tr("sc.levelgui.branch.tip.chosen", com.sc.util.SingularStationMath.BRANCH_SM)
                        : ch != 0 ? "§7" + Lang.tr("sc.levelgui.branch.tip.other", com.sc.util.SingularStationMath.BRANCH_SM)
                        : com.sc.util.SingularLevel.levelOf(chest) >= bl ? "§d" + Lang.tr("sc.levelgui.branch.tip.pick")
                        : "§7" + Lang.tr("sc.levelgui.branch.tip.later", bl));
            } else if (b.id >= LV_PROF && b.id < LV_PROF + com.sc.util.SingularProfiles.COUNT) {
                int i = b.id - LV_PROF;
                tip.add(Lang.tr("sc.armor.sing.profile.name." + i));
                tip.add("§7" + Lang.tr(com.sc.util.SingularProfiles.has(chest, i) ? "sc.levelgui.profile.tip" : "sc.levelgui.profile.tip.empty"));
                tip.add("§7" + Lang.tr("sc.levelgui.profile.tip2"));
            } else if (b.id == LV_SAVE) {
                tip.add(Lang.tr("sc.levelgui.profile.save.tip"));
            } else if (b.id == LV_HUD) {
                tip.add(Lang.tr("sc.levelgui.hud.tip"));
            } else {
                continue;
            }
            TextFitSC.hover(b.xPosition, b.yPosition, b.width, b.height, tip);
        }
    }

    // ------------------------------------------------------------------ Singular blade / drill (docs/plan-singular-tools.md §4)

    /** The Singular blade / drill of the open tab, or null (any other tab or tool). */
    private ItemStack singularTool() {
        if (selectedPiece == BLADE_TAB) {
            ItemStack b = blade();
            return ToolLevelSC.isBlade(b) ? b : null;
        }
        if (selectedPiece == DRILL_TAB) {
            ItemStack d = drill();
            return ToolLevelSC.isDrill(d) ? d : null;
        }
        return null;
    }

    /** Level, branch, form / mode, page, creative, Singular armour worn: what the tool's buttons were built for. */
    private String toolSignature() {
        ItemStack t = singularTool();
        if (t == null) {
            return "";
        }
        int mode = ToolLevelSC.isBlade(t) ? ItemBladeSC.formOf(t).ordinal() * 10 + com.sc.item.BladeSingularSC.effectiveForm(mc.thePlayer, t).ordinal()
                : DrillLogicSC.holeSize(mc.thePlayer, t) * 10 + ItemDrillSC.tunnelDepth(t);
        return "|t" + ToolLevelSC.levelOf(t) + ToolLevelSC.branchOf(t) + ":" + mode + ":" + toolPage
                + (mc.thePlayer.capabilities.isCreativeMode ? 'c' : 's') + (com.sc.util.SingularLevel.wearsSingular(mc.thePlayer) ? 'w' : '-');
    }

    // the feature metadata of the Singular tools (old functions: level 1, no branch, no gas, no cooldown)
    private static int singLevelOf(Enum<?> f) {
        return f instanceof BladeFeature ? ((BladeFeature) f).singLevel() : f instanceof DrillFeature ? ((DrillFeature) f).singLevel() : 1;
    }

    private static int branchOfFeature(Enum<?> f) {
        return f instanceof BladeFeature ? ((BladeFeature) f).branch() : f instanceof DrillFeature ? ((DrillFeature) f).branch() : 0;
    }

    /** The Singular blade's form that counts now: FORM_ATTACK's gas, cost and cooldown follow it. */
    private BladeForm attackForm() {
        return com.sc.item.BladeSingularSC.effectiveForm(mc.thePlayer, blade());
    }

    private Gas toolGas(Enum<?> f) {
        if (f == BladeFeature.FORM_ATTACK) {
            return BladeFeature.formAttackGas(attackForm());
        }
        return f instanceof BladeFeature ? ((BladeFeature) f).gas() : f instanceof DrillFeature ? ((DrillFeature) f).gas() : null;
    }

    private int toolGasMb(Enum<?> f) {
        if (f == BladeFeature.FORM_ATTACK) {
            return BladeFeature.formAttackGasMb(attackForm());
        }
        return f instanceof BladeFeature ? ((BladeFeature) f).gasMb() : f instanceof DrillFeature ? ((DrillFeature) f).gasMb() : 0;
    }

    private static boolean toolGasPerSecond(Enum<?> f) {
        return f instanceof BladeFeature ? ((BladeFeature) f).gasPerSecond() : f instanceof DrillFeature && ((DrillFeature) f).gasPerSecond();
    }

    /** Base cooldown: FORM_ATTACK - the form's; the black hole - its zone's now (12x12 only, none for the Miner at 3). */
    private int toolCooldown(Enum<?> f) {
        if (f == BladeFeature.FORM_ATTACK) {
            return BladeFeature.formAttackCooldown(attackForm());
        }
        if (f == DrillFeature.BLACK_HOLE) {
            ItemStack d = drill();
            int size = DrillLogicSC.holeSize(mc.thePlayer, d);
            return size > 0 ? DrillLogicSC.holeCooldown(mc.thePlayer, d, size) : 0;
        }
        return f instanceof BladeFeature ? ((BladeFeature) f).cooldownTicks() : f instanceof DrillFeature ? ((DrillFeature) f).cooldownTicks() : 0;
    }

    private static String toolKey(Enum<?> f) {
        return f instanceof BladeFeature ? ((BladeFeature) f).key() : ((DrillFeature) f).key();
    }

    /** One of the Singular tools' own functions (not an Exo-era one). */
    private static boolean singularOnly(Enum<?> f) {
        return f instanceof BladeFeature ? ((BladeFeature) f).minType == BladeType.SINGULAR
                : f instanceof DrillFeature && ((DrillFeature) f).minType == DrillType.SINGULAR;
    }

    /** Open on the tool in hand (its level / branch; creative: all); any non-Singular tool: always. */
    private boolean toolUnlocked(Enum<?> f) {
        if (f instanceof BladeFeature) {
            ItemStack t = blade();
            return !ToolLevelSC.isSingularTool(t) || BladeLogicSC.unlocked(mc.thePlayer, t, (BladeFeature) f);
        }
        if (f instanceof DrillFeature) {
            ItemStack t = drill();
            return !ToolLevelSC.isSingularTool(t) || DrillLogicSC.unlocked(mc.thePlayer, t, (DrillFeature) f);
        }
        return true;
    }

    /** The extra hover lines of a Singular tool's function: lock / level, the Exo-era discount, the gas, the cooldown. */
    private void toolTip(List<String> tip, Enum<?> f) {
        ItemStack t = f instanceof BladeFeature ? blade() : drill();
        if (!ToolLevelSC.isSingularTool(t)) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        tip.add(0, nameOf(f));
        int lock = lockedLevel(f), br = branchOfFeature(f);
        if (lock > 0) {
            tip.add("§c" + Lang.tr("sc.armorgui.tip.locked", lock) + (br != 0 ? " · " + Lang.tr(ToolLevelSC.branchLangKey(t, br)) : ""));
        } else if (branchLocked(f)) {
            tip.add("§c" + Lang.tr("sc.toolgui.tip.branch", Lang.tr(ToolLevelSC.branchLangKey(t, br))));
        } else if (singLevelOf(f) > 1) {
            tip.add("§d" + Lang.tr("sc.armorgui.tip.level", singLevelOf(f)));
        }
        if (!singularOnly(f)) {
            tip.add("§7" + Lang.tr("sc.toolgui.tip.legacy", Math.round((1F - ToolLevelSC.LEGACY_MUL) * 100)));
        }
        if (f == BladeFeature.FORM_ATTACK) {                   // the attack of the form that counts now
            BladeForm form = attackForm();
            String key = BladeFeature.formAttackKey(form);
            tip.add("§d" + Lang.tr("sc.toolgui.tip.formattack", Lang.tr(form.langKey()), Lang.tr(key)));
            tip.add("§7" + Lang.trOr(key + ".desc", ""));
        } else if (f == DrillFeature.BLACK_HOLE) {             // a mode: the zone as the level lets it work
            tip.add("§d" + SingularHudSC.holeLine(mc.thePlayer, t));
            tip.add("§7" + Lang.tr("sc.toolgui.tip.holemode"));
        }
        Gas g = toolGas(f);
        if (g != null) {
            int mb = toolGasMb(f), amount = ArmorGasSC.suitAmount(p, g);
            tip.add("§7" + Lang.tr(toolGasPerSecond(f) ? "sc.armorgui.tip.use.s" : "sc.armorgui.tip.use.u", mb));
            tip.add((amount < mb || amount <= 0 ? "§c" : "§b") + Lang.tr("sc.toolgui.tip.gas", GasUiSC.name(g), amount));
        } else if (singularOnly(f)) {
            tip.add("§7" + Lang.tr("sc.toolgui.tip.nogas"));
        }
        int cd = toolCooldown(f);
        if (cd > 0) {
            int left = ToolLevelSC.cooldownLeft(t, toolKey(f), mc.theWorld);
            tip.add(left > 0 ? "§6" + Lang.tr("sc.armorgui.tip.cooldown", (left + 19) / 20)
                    : "§7" + Lang.tr("sc.toolgui.tip.cd", num(ToolLevelSC.cooldownTicks(p, cd) / 20F)));
        }
    }

    /** The switch between the functions and the level page, at the foot on the right. */
    private void addToolPageButton() {
        tlPageW = Math.min(120, Math.max(60, contentW / 3));
        buttonList.add(new TextFitSC.Button(TL_PAGE, contentX + contentW - tlPageW, footY - 1, tlPageW, 11,
                Lang.tr(toolPage == 0 ? "sc.toolgui.page.level" : "sc.toolgui.page.functions")));
    }

    /**
     * The level page of a Singular tool (mock-up tools_2_ktab): the title, the level and its points, the
     * readiness, the forms (blade) / the black hole mode (drill), the branch buttons, the gas line, what the next
     * level opens. The left panel stays while the page is at least 300 px wide; lines that don't fit are left out.
     */
    private void initToolInfo(ItemStack t) {
        boolean wide = false;
        for (int mode = 2; mode >= 0 && !wide; mode--) {
            if (layoutPanel(mode)) {
                setPanel(mode);
                wide = contentW >= 300;
            }
        }
        if (!wide) {
            setPanel(0);
        }
        pitch = 16;
        btnH = 14;
        EntityPlayer p = mc.thePlayer;
        boolean isBlade = ToolLevelSC.isBlade(t), creative = p.capabilities.isCreativeMode;
        int x = contentX, cw = contentW, bottom = footY - 2, y = contentY;
        tlFormY = tlBranchY = tlNoteY = tlPerkY = tlGasY = tlOpensY = -1;
        tlTitleY = y;
        y += 11;
        tlLevelY = y;
        y += 10;
        tlReadyY = y;
        y += 12;
        if (y + 10 + 14 <= bottom) {
            tlFormY = y;
            y += 10;
            if (isBlade) {
                BladeForm[] forms = BladeForm.values();
                int n = Math.min(forms.length, TL_FORM_MAX);
                int bw = (cw - (n - 1) * 3) / n;
                BladeForm cur = ItemBladeSC.formOf(t);
                int lv = ToolLevelSC.effectiveLevel(p, t), br = ToolLevelSC.branchOf(t);
                for (int i = 0; i < n; i++) {
                    BladeForm f = forms[i];
                    boolean open = f.open(lv, br, creative);
                    String name = Lang.trOr("sc.toolgui.form.short." + f.key(), Lang.tr(f.langKey()));
                    GuiButton b = new TextFitSC.Button(TL_FORM + i, x + i * (bw + 3), y, bw, 14, f == cur ? "§d" + name : open ? name : "§8" + name);
                    b.enabled = open && f != cur;                 // the current one pressed in, the closed ones grey
                    buttonList.add(b);
                }
            } else {
                buttonList.add(new TextFitSC.Button(TL_HOLE, x, y, Math.min(cw, 200), 14, "§d" + SingularHudSC.holeLine(p, t)));
            }
            y += 18;
        }
        int count = ToolLevelSC.branchCount(t);
        if (count > 0 && y + 10 + 14 <= bottom) {
            tlBranchY = y;
            y += 10;
            int bw = (cw - (count - 1) * 4) / count;
            int chosen = ToolLevelSC.branchOf(t);
            boolean pending = ToolLevelSC.branchPending(t);
            for (int b = 1; b <= count; b++) {
                String name = Lang.tr(ToolLevelSC.branchLangKey(t, b));
                String label = creative ? "§a" + name : chosen == b ? "§a" + name + " " + Lang.tr("sc.toolgui.branch.mark")
                        : chosen != 0 ? "§8" + name : pending ? "§d" + name : "§7" + name;
                GuiButton gb = new TextFitSC.Button(TL_BRANCH + b - 1, x + (b - 1) * (bw + 4), y, bw, 14, label);
                gb.enabled = !creative && pending;                // a free pick only while pending; later - the station
                buttonList.add(gb);
            }
            y += 17;
            if (y + 9 <= bottom) {
                tlNoteY = y;
                y += 10;
            }
            if ((chosen != 0 || creative) && y + 9 <= bottom) {
                tlPerkY = y;
                y += 10;
            }
        }
        if (y + 9 <= bottom) {
            tlGasY = y;
            y += 10;
        }
        if (ToolLevelSC.levelOf(t) < ToolLevelSC.MAX && y + 9 <= bottom) {
            tlOpensY = y;
        }
    }

    /** What branch `b` gives: its functions (and the blade's forms) with their levels, "—" when none are listed. */
    private String branchGives(ItemStack t, int b) {
        StringBuilder sb = new StringBuilder();
        boolean isBlade = ToolLevelSC.isBlade(t);
        Enum<?>[] all = isBlade ? (Enum<?>[]) BladeFeature.values() : (Enum<?>[]) DrillFeature.values();
        for (Enum<?> f : all) {
            if (branchOfFeature(f) == b) {
                sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.toolgui.lv", nameOf(f), singLevelOf(f)));
            }
        }
        if (isBlade) {
            for (BladeForm f : BladeForm.values()) {
                if (f.branch == b) {
                    sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.toolgui.lv", Lang.tr(f.langKey()), f.level));
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    /** What the next level opens: the functions (no other branch's) and the forms of level `lv`. */
    private String opensAt(ItemStack t, int lv) {
        StringBuilder sb = new StringBuilder();
        boolean isBlade = ToolLevelSC.isBlade(t);
        int chosen = ToolLevelSC.branchOf(t);
        Enum<?>[] all = isBlade ? (Enum<?>[]) BladeFeature.values() : (Enum<?>[]) DrillFeature.values();
        for (Enum<?> f : all) {
            int br = branchOfFeature(f);
            if (singularOnly(f) && singLevelOf(f) == lv && (br == 0 || chosen == 0 || br == chosen)) {
                sb.append(sb.length() > 0 ? ", " : "").append(nameOf(f));
            }
        }
        if (isBlade) {
            for (BladeForm f : BladeForm.values()) {
                if (f.level == lv && (f.branch == 0 || chosen == 0 || f.branch == chosen)) {
                    sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr(f.langKey()));
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private void drawToolInfo() {
        ItemStack t = singularTool();
        if (t == null) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        boolean isBlade = ToolLevelSC.isBlade(t), creative = p.capabilities.isCreativeMode;
        int x = contentX, cw = contentW, bottom = footY - 1;
        int lvl = ToolLevelSC.levelOf(t), chosen = ToolLevelSC.branchOf(t);
        String title = Lang.tr("sc.toolgui.title", t.getDisplayName(), lvl)
                + (chosen != 0 ? " · " + Lang.tr("sc.toolgui.branch.of", Lang.tr(ToolLevelSC.branchLangKey(t, chosen))) : "");
        fit(title, x, tlTitleY, cw, 0xC080FF);
        // the level and its points
        boolean top = lvl >= ToolLevelSC.MAX;
        int need = ToolLevelSC.threshold(t), pts = ToolLevelSC.points(t);
        int labW = Math.min(76, cw / 4), numW = Math.min(110, cw / 3);
        fit(top ? Lang.tr("sc.toolgui.level.top", lvl) : Lang.tr("sc.toolgui.level.next", lvl, lvl + 1), x, tlLevelY, labW - 4, 0xE0E0E0);
        int barX = x + labW, barW = cw - labW - numW - 4;
        if (barW >= 16) {
            bar(barX, tlLevelY + 1, barW, 7, top ? 1 : pts, top ? 1 : need, 0xB060FF, false);
        }
        int nx = barX + Math.max(0, barW) + 4;
        boolean full = ToolLevelSC.pointsFull(t);
        fit(top ? Lang.tr("sc.levelgui.max") : Lang.tr("sc.toolgui.points", amount(pts), amount(need)), nx, tlLevelY, x + cw - nx,
                top || full ? 0x60FF60 : 0xA0A0A0);
        List<String> lvTip = new ArrayList<String>();
        lvTip.add("§d" + Lang.tr("sc.toolgui.level.tip", lvl, ToolLevelSC.MAX));
        if (!top) {
            lvTip.add("§7" + Lang.tr("sc.toolgui.points", pts, need));
        }
        lvTip.add("§7" + Lang.tr(isBlade ? "sc.toolgui.earn.blade" : "sc.toolgui.earn.drill"));
        if (creative) {
            lvTip.add("§a" + Lang.tr("sc.toolgui.creative"));
        }
        TextFitSC.hover(x, tlLevelY - 1, cw, 10, lvTip);
        String ready = top ? Lang.tr("sc.toolgui.ready.top") : ToolLevelSC.readyToUpgrade(t) ? Lang.tr("sc.toolgui.ready")
                : Lang.tr(isBlade ? "sc.toolgui.earn.blade" : "sc.toolgui.earn.drill");
        fit(ready, x, tlReadyY, cw, !top && ToolLevelSC.readyToUpgrade(t) ? 0x60FF60 : 0x808080);
        if (tlFormY >= 0) {
            int hw = fit(Lang.tr(isBlade ? "sc.toolgui.form.head" : "sc.toolgui.hole.head"), x, tlFormY, cw, HEAD);
            if (isBlade && cw - hw > 60) {
                fit(Lang.tr("sc.toolgui.form.grey"), x + hw + 8, tlFormY, cw - hw - 8, 0x707070);
            }
        }
        if (tlBranchY >= 0) {
            fit(Lang.tr("sc.toolgui.branch.head", ToolLevelSC.BRANCH_LEVEL, ToolLevelSC.BRANCH_PERK_LEVEL), x, tlBranchY, cw, HEAD);
        }
        if (tlNoteY >= 0 && tlNoteY + 9 <= bottom) {
            boolean pending = ToolLevelSC.branchPending(t);
            String note = creative ? Lang.tr("sc.toolgui.branch.creative") : chosen != 0 ? Lang.tr("sc.toolgui.branch.station")
                    : pending ? Lang.tr("sc.toolgui.branch.free") : Lang.tr("sc.toolgui.branch.later", ToolLevelSC.BRANCH_LEVEL);
            fit(note, x, tlNoteY, cw, !creative && pending ? 0xC080FF : 0x808080);
        }
        if (tlPerkY >= 0 && tlPerkY + 9 <= bottom && chosen != 0) {
            fit(Lang.tr("sc.toolgui.branch.gives", branchGives(t, chosen)), x, tlPerkY, cw, 0xA0A0A0);
        }
        if (tlGasY >= 0 && tlGasY + 9 <= bottom) {
            boolean worn = com.sc.util.SingularLevel.wearsSingular(p);
            int w = fit(Lang.tr(worn ? "sc.toolgui.gas.ok" : "sc.toolgui.gas.none"), x, tlGasY, cw, worn ? 0x60FF60 : RED);
            List<String> tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.toolgui.gas.tip"));
            if (!worn) {
                tip.add("§c" + Lang.tr("sc.toolgui.gas.tip.none"));
            }
            TextFitSC.hover(x, tlGasY - 1, w, 10, tip);
        }
        if (tlOpensY >= 0 && tlOpensY + 9 <= bottom && !top) {
            fit(Lang.tr("sc.toolgui.opens", lvl + 1, opensAt(t, lvl + 1)), x, tlOpensY, cw, 0xA0A0A0);
        }
    }

    /** Hover texts of the tool page's buttons (after the buttons' own cut-label ones). */
    private void toolButtonTips() {
        ItemStack t = singularTool();
        if (t == null) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        boolean creative = p.capabilities.isCreativeMode;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            List<String> tip = new ArrayList<String>();
            if (b.id == TL_PAGE) {
                tip.add(Lang.tr(toolPage == 0 ? "sc.toolgui.page.level.tip" : "sc.toolgui.page.functions.tip"));
            } else if (b.id == TL_HOLE) {
                tip.add(SingularHudSC.holeLine(p, t));
                if (ItemDrillSC.blackHoleSize(t) > DrillLogicSC.holeSize(p, t)) {   // a size kept from creative / above the level
                    tip.add("§c" + Lang.tr("sc.toolgui.hole.capped", ItemDrillSC.blackHoleSize(t)));
                }
                tip.add("§7" + Lang.tr("sc.toolgui.hole.tip"));
            } else if (b.id >= TL_FORM && b.id < TL_FORM + TL_FORM_MAX && ToolLevelSC.isBlade(t)) {
                BladeForm f = BladeForm.of(b.id - TL_FORM);
                tip.add(Lang.tr(f.langKey()));
                String desc = Lang.trOr(f.langKey() + ".desc", "");
                if (!desc.isEmpty()) {
                    tip.add("§7" + desc);
                }
                if (f == ItemBladeSC.formOf(t)) {
                    tip.add("§d" + Lang.tr("sc.toolgui.form.tip.current"));
                } else if (f.open(p, t)) {
                    tip.add("§e" + Lang.tr("sc.toolgui.form.tip.click"));
                } else if (f.branch != 0 && ToolLevelSC.effectiveLevel(p, t) >= f.level) {
                    tip.add("§c" + Lang.tr("sc.toolgui.tip.branch", Lang.tr(ToolLevelSC.branchLangKey(t, f.branch))));
                } else {
                    tip.add("§c" + Lang.tr("sc.armorgui.tip.locked", f.level)
                            + (f.branch != 0 ? " · " + Lang.tr(ToolLevelSC.branchLangKey(t, f.branch)) : ""));
                }
                tip.add("§7" + Lang.tr("sc.toolgui.form.tip.wheel"));
            } else if (b.id >= TL_BRANCH && b.id < TL_BRANCH + 3) {
                int br = b.id - TL_BRANCH + 1, chosen = ToolLevelSC.branchOf(t);
                tip.add(Lang.tr(ToolLevelSC.branchLangKey(t, br)));
                tip.add("§7" + Lang.tr("sc.toolgui.branch.gives", branchGives(t, br)));
                tip.add(creative ? "§a" + Lang.tr("sc.toolgui.branch.creative") : chosen == br ? "§a" + Lang.tr("sc.toolgui.branch.station")
                        : chosen != 0 ? "§7" + Lang.tr("sc.toolgui.branch.station")
                        : ToolLevelSC.branchPending(t) ? "§d" + Lang.tr("sc.toolgui.branch.free")
                        : "§7" + Lang.tr("sc.toolgui.branch.later", ToolLevelSC.BRANCH_LEVEL));
            } else {
                continue;
            }
            TextFitSC.hover(b.xPosition, b.yPosition, b.width, b.height, tip);
        }
    }

    private void toolAction(GuiButton b) {
        if (b.id == TL_PAGE) {
            toolPage = 1 - toolPage;
            initGui();
            return;
        }
        ItemStack t = singularTool();
        if (t == null) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        if (b.id == TL_HOLE) {                                  // the server steps it; the button follows by the signature
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DRILL_MODE, 1));
        } else if (b.id >= TL_BRANCH && b.id < TL_BRANCH + 3) {  // the free first choice; the server checks and answers
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.TOOL_BRANCH, b.id - TL_BRANCH + 1, ToolLevelSC.isBlade(t)));
            b.enabled = false;
        } else if (b.id >= TL_FORM && b.id < TL_FORM + TL_FORM_MAX && ToolLevelSC.isBlade(t)) {
            BladeForm to = BladeForm.of(b.id - TL_FORM);
            int steps = ArmorNetSC.formSteps(ItemBladeSC.formOf(t), to, ToolLevelSC.effectiveLevel(p, t), ToolLevelSC.branchOf(t),
                    p.capabilities.isCreativeMode);
            if (steps != 0) {                                     // one message: the server walks the steps in one go
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BLADE_FORM, steps));
                ItemBladeSC.setForm(t, to);                     // shown at once
            }
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void box(int x, int y, int w, int h, int fill, int edge) {
        drawRect(x, y, x + w, y + h, edge);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static boolean low(int amount, int cap) {
        return cap > 0 && (long) amount * 100 < (long) LOW_PCT * cap;
    }

    private static boolean critical(int amount, int cap) {
        return cap > 0 && (long) amount * 100 < (long) CRIT_PCT * cap;
    }

    /** Text colour of a tank's level: red empty (blinking under 5%), orange under 20%, else `normal`. */
    private static int levelColor(int amount, int cap, int normal) {
        if (cap <= 0) {
            return 0x808080;
        }
        if (amount <= 0) {
            return RED;
        }
        if (critical(amount, cap)) {
            return GasUiSC.blinkOff() ? 0xA02020 : RED;
        }
        return low(amount, cap) ? ORANGE : normal;
    }

    /** A level bar: frame (orange low, red empty), dark inside, the fill in `color` (orange when low, blinking under 5%). */
    private static void bar(int x, int y, int w, int h, int amount, int cap, int color, boolean gasRules) {
        int edge = 0xFF55555F, fill = 0xFF000000 | color;
        if (gasRules && cap > 0) {
            if (amount <= 0) {
                edge = 0xFF000000 | RED;
            } else if (critical(amount, cap)) {
                edge = fill = 0xFF000000 | RED;
            } else if (low(amount, cap)) {
                edge = fill = 0xFF000000 | ORANGE;
            }
        }
        drawRect(x, y, x + w, y + h, edge);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0C0C0E);
        int fw = cap <= 0 ? 0 : (int) ((long) (w - 2) * Math.max(0, Math.min(amount, cap)) / cap);
        if (amount > 0 && cap > 0 && fw == 0) {
            fw = 1;
        }
        boolean blink = gasRules && amount > 0 && critical(amount, cap) && GasUiSC.blinkOff();
        if (fw > 0 && !blink) {
            drawRect(x + 1, y + 1, x + 1 + fw, y + h - 1, fill);
        }
    }

    private int fit(String text, int x, int y, int maxW, int color) {
        return TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, true, 0, 0);
    }

    private static int pct(int amount, int cap) {
        if (cap <= 0 || amount <= 0) {
            return 0;
        }
        return Math.max(1, (int) ((long) amount * 100 / cap));   // not empty: never "0"
    }

    private static String num(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v).replace(".", Lang.tr("sc.num.decimal"));
    }

    /** "~17 min" / "~27 h" at the measured rate; "..." while measuring, a dash while it isn't used. */
    private String timeLeft(Gas g, int amount) {
        float rate = GasUiSC.perMinute(g);
        if (rate < 0) {
            return "...";
        }
        if (rate < 0.05F) {
            return "—";
        }
        int minutes = (int) Math.min(Integer.MAX_VALUE, amount / rate);
        return minutes >= 120 ? Lang.tr("sc.lifegui.left.h", minutes / 60) : Lang.tr("sc.lifegui.left.min", minutes);
    }

    private void drawIcon(ItemStack s, int x, int y) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), s, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ status strip

    private void drawStrip() {
        EntityPlayer p = mc.thePlayer;
        int sx = winX + 5, sy = top + 35, sw = winW - 10;
        box(sx, sy, sw, 13, C_STRIP, C_EDGE);
        int ty = sy + 3;
        boolean narrow = sw < 400;
        int barW = narrow ? 30 : 56;
        int x = sx + 5;
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        // charge: the tool of the tab, or the whole suit
        int charge = -1, heat = -1;
        boolean hot = false;
        List<String> chargeTip = new ArrayList<String>();
        ItemStack tool = selectedPiece == BLADE_TAB ? blade() : selectedPiece == DRILL_TAB ? drill() : null;
        if (tool != null) {
            boolean isBlade = selectedPiece == BLADE_TAB;
            int cap = isBlade ? ItemBladeSC.capacityOf(tool) : ItemDrillSC.capacityOf(tool);
            charge = pct(isBlade ? ItemBladeSC.chargeOf(tool) : ItemDrillSC.chargeOf(tool), cap);
            heat = isBlade ? ItemBladeSC.heatPercent(tool) : ItemDrillSC.heatPercent(tool);
            hot = isBlade ? ItemBladeSC.overheated(tool) : ItemDrillSC.overheated(tool);
            chargeTip.add(tool.getDisplayName() + ": " + charge + "%");
        } else {
            long sum = 0, cap = 0;
            for (int t = 0; t < 4; t++) {
                ItemStack s = ArmorLogicSC.piece(p, t);
                if (s != null) {
                    sum += ItemArmorSC.chargeOf(s);
                    cap += ItemArmorSC.capacityOf(s);
                    chargeTip.add(Lang.tr("sc.armorhud.piece." + t) + ": " + pct(ItemArmorSC.chargeOf(s), ItemArmorSC.capacityOf(s)) + "%");
                }
            }
            charge = cap > 0 ? (sum > 0 ? (int) Math.max(1, sum * 100 / cap) : 0) : -1;
            if (chest != null) {
                ArmorSuit suit = ArmorLogicSC.suitOf(chest);
                heat = chest.hasTagCompound() ? chest.getTagCompound().getInteger("HeatSC") * 100 / suit.heatCapacity : 0;
                hot = chest.hasTagCompound() && chest.getTagCompound().getBoolean("ChipsOffSC");
            }
        }
        if (charge >= 0) {
            int x0 = x;
            if (!narrow) {
                x += fit(Lang.tr("sc.armorgui.strip.charge"), x, ty, 50, HEAD) + 4;
            }
            bar(x, ty, barW, 7, charge, 100, 0x50C8FF, false);
            x += barW + 3;
            x += fit(charge + "%", x, ty, 24, charge <= 15 ? RED : 0xE0E0E0) + 8;
            chargeTip.add(0, Lang.tr("sc.armorgui.strip.charge"));
            TextFitSC.hover(x0, sy, x - x0, 13, chargeTip);
        }
        if (heat >= 0) {
            int x0 = x;
            if (!narrow) {
                x += fit(Lang.tr("sc.armorgui.strip.heat"), x, ty, 50, HEAD) + 4;
            }
            int hc = hot || heat > 80 ? RED : 0xFFAA3C;
            bar(x, ty, barW, 7, Math.min(heat, 100), 100, hc, false);
            x += barW + 3;
            x += fit(heat + "%", x, ty, 24, hot ? RED : heat > 80 ? ORANGE : 0xE0E0E0) + 8;
            String key = tool == null ? (hot ? "sc.armorhud.overheat" : "sc.armorhud.heat")
                    : selectedPiece == BLADE_TAB ? (hot ? "sc.bladehud.overheat" : "sc.bladehud.heat") : (hot ? "sc.drillhud.overheat" : "sc.drillhud.heat");
            TextFitSC.hover(x0, sy, x - x0, 13, Lang.tr(key, heat));
        }
        int end = sx + sw - 5;
        // emergency (no helium in the loop) or all well
        boolean emergency = ArmorLogicSC.emergency(ArmorGasSC.wornSet(p));
        String state = emergency ? Lang.tr("sc.gas.emergency") : anyPieceWorn() ? Lang.tr("sc.armorgui.strip.ok") : "";
        int stateColor = emergency ? (GasUiSC.blinkOff() ? 0xB02020 : 0xFF4040) : 0x8FE3FF;
        String mode = "";
        int modeColor = 0;
        if (chest != null) {
            int m = ArmorLogicSC.powerMode(p);
            String name = Lang.tr("sc.armorgui.mode." + m);
            mode = Lang.tr("sc.armorgui.mode", name);
            if (narrow || fontRendererObj.getStringWidth(mode) + 10 + fontRendererObj.getStringWidth(state) > end - x) {
                mode = name;                               // "Power mode: Normal" or just "Normal"
            }
            modeColor = m == 0 ? 0x8FE3FF : m == 2 ? 0xFF7050 : 0x60FF60;
        }
        int rest = end - x;
        int mw = fontRendererObj.getStringWidth(mode), stw = fontRendererObj.getStringWidth(state);
        if (mw + 10 + stw > rest && !mode.isEmpty() && !state.isEmpty()) {
            mw = Math.min(mw, Math.max(rest * 2 / 5, rest - 10 - stw));
        }
        if (!mode.isEmpty() && rest > 10) {
            int w = fit(mode, x, ty, Math.min(mw, rest), modeColor);
            List<String> tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.armorgui.mode", Lang.tr("sc.armorgui.mode." + ArmorLogicSC.powerMode(p))));
            if (ArmorLogicSC.regenOn(p)) {
                tip.add("§d" + Lang.tr("sc.armorhud.regen"));
            }
            tip.add("§e" + Lang.tr("sc.armorgui.strip.mode.click"));
            TextFitSC.hover(x, sy, w, 13, tip);
            modeLabel = new int[]{x, sy, w, 13};
            x += w + 10;
        }
        if (!state.isEmpty() && end - x > 10) {
            int w = fit(state, x, ty, end - x, stateColor);
            if (!emergency) {
                List<String> tip = new ArrayList<String>();
                float cool = ArmorGasSC.coolingFactor(p);
                tip.add(cool <= 0F ? Lang.tr("sc.lifegui.cool.none")
                        : Lang.tr("sc.lifegui.cool", String.format(Locale.ROOT, "%.2f", cool), Math.round(ArmorGasSC.RADIATOR_BONUS * 100)));
                tip.add("§7" + Lang.tr("sc.lifegui.cool.tip"));
                TextFitSC.hover(x, sy, w, 13, tip);
            } else {
                List<String> tip = new ArrayList<String>();
                tip.add("§c" + Lang.tr("sc.gas.emergency"));
                tip.add("§7" + Lang.tr("sc.gas.warn.emergency"));
                TextFitSC.hover(x, sy, w, 13, tip);
            }
        }
    }

    // ------------------------------------------------------------------ left panel: the worn pieces and their tanks

    private void drawPanel() {
        if (panelMode == 0) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        int px = winX + 5, py = top + 51, pw = panelW(panelMode), ph = top + winH - 4 - py;
        box(px, py, pw, ph, C_PANEL, C_EDGE);
        boolean chest = ArmorGasSC.worn(p, ArmorGasSC.CHEST) != null;
        int lastBottom = py;
        for (int t = 0; t < 4; t++) {
            int[] r = pieceRect[t];
            if (r == null) {
                continue;
            }
            lastBottom = r[1] + r[3];
            if (t == selectedPiece) {
                box(r[0], r[1], r[2], r[3], C_SEL, 0xFFFFFFFF);
            }
            ItemStack w = ArmorGasSC.worn(p, t);
            int ix = r[0] + 2, iy = r[1] + (r[3] - 16) / 2;
            List<String> pieceTip = new ArrayList<String>();
            pieceTip.add(Lang.tr("sc.armorhud.piece." + t));
            if (w == null) {
                drawRect(ix, iy, ix + 16, iy + 16, 0xFF2A2A2A);
                drawRect(ix + 1, iy + 1, ix + 15, iy + 15, 0xFF161616);
                fit(Lang.tr("sc.lifegui.part.none"), r[0] + 21, r[1] + (r[3] - 8) / 2, r[2] - 23, 0x707070);
                pieceTip.add("§7" + Lang.tr("sc.lifegui.part.none"));
                TextFitSC.hover(r[0], r[1], r[2], r[3], pieceTip);
                continue;
            }
            pieceTip.set(0, w.getDisplayName());
            pieceTip.add(Lang.tr("sc.armorgui.panel.charge", pct(ItemArmorSC.chargeOf(w), ItemArmorSC.capacityOf(w))));
            List<Gas> tanks = tanksOf(w);
            int lp = panelLine;
            int ly = r[1] + (r[3] - tanks.size() * lp) / 2 + 1;
            int symX = r[0] + 20, barX = r[0] + 42;
            int barW = panelMode == 2 ? 32 : r[0] + r[2] - 3 - barX;
            if (tanks.isEmpty()) {
                fit(Lang.tr("sc.lifegui.part.notanks"), symX, r[1] + (r[3] - 8) / 2, r[2] - 22, 0x707070);
                pieceTip.add("§7" + Lang.tr("sc.lifegui.part.notanks"));
            }
            for (Gas g : tanks) {
                int cap = ArmorGasSC.capacity(w, g), amount = ArmorGasSC.amount(w, g);
                boolean dead = g == Gas.HELIUM && !chest;               // radiators without the loop
                fit(GasUiSC.shortName(g), symX, ly, barX - symX - 2, dead ? 0x707070 : g.color);
                if (dead) {
                    bar(barX, ly + 1, barW, 6, 0, 1, 0x505050, false);
                } else {
                    bar(barX, ly + 1, barW, 6, amount, cap, g.color, true);
                }
                if (panelMode == 2) {
                    fit(dead ? "-" : String.valueOf(pct(amount, cap)), barX + barW + 3, ly, r[0] + r[2] - (barX + barW + 3), dead ? 0x707070
                            : levelColor(amount, cap, 0xA0A0A0));
                }
                List<String> tip = new ArrayList<String>();
                tip.add(Lang.tr("sc.lifegui.part.tank", GasUiSC.name(g), amount, cap) + " (" + pct(amount, cap) + "%)");
                if (dead) {
                    tip.add("§7" + Lang.tr("sc.lifegui.noloop"));
                }
                TextFitSC.hover(symX, ly - 1, r[0] + r[2] - symX, lp, tip);
                ly += lp;
            }
            if (t != ArmorGasSC.CHEST && ArmorGasSC.baseCapacity(w, Gas.HELIUM) > 0) {
                pieceTip.add("§b" + Lang.tr("sc.lifegui.part.radiator", Math.round(ArmorGasSC.RADIATOR_BONUS * 100)));
            } else if (t == ArmorGasSC.CHEST && ArmorGasSC.baseCapacity(w, Gas.HELIUM) > 0) {
                pieceTip.add("§b" + Lang.tr("sc.lifegui.part.loop"));
            }
            TextFitSC.hover(r[0], r[1], symX - r[0], r[3], pieceTip);
            drawIcon(w, ix, iy);
        }
        if (lastBottom + 13 <= py + ph) {
            fit(Lang.tr("sc.armorgui.panel.hint"), px + 4, py + ph - 11, pw - 8, 0x707070);
        }
    }

    // ------------------------------------------------------------------ rows: headings, gas chips, mode descriptions

    /** The gas the function's chip shows: its own gas where the suit runs on gases (Quantum / Exo; breathing in every suit), else null - EU. */
    private Gas chipGas(Enum<?> f) {
        if (f instanceof BladeFeature || f instanceof DrillFeature) {
            return toolGas(f);                             // a Singular tool's function: its gas comes from the worn armour
        }
        if (!(f instanceof ArmorFeature)) {
            return null;
        }
        ArmorFeature a = (ArmorFeature) f;
        ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, a.piece);
        if (piece == null || !(ArmorLogicSC.strict(ArmorLogicSC.suitOf(piece)) || a == ArmorFeature.AIR)) {
            return null;
        }
        return a.gas();
    }

    private void drawRows() {
        EntityPlayer p = mc.thePlayer;
        for (int[] h : heads) {
            fit(Lang.tr("sc.armorgui.col.function"), h[0], h[1], h[2], HEAD);
            if (h[3] == 1) {
                fit(Lang.tr("sc.armorgui.col.gas"), h[0] + h[2] + GAP, h[1], CHIP_W, HEAD);
            }
            String k = Lang.tr("sc.armorgui.col.key");
            int kw = Math.min(fontRendererObj.getStringWidth(k), KEY_W + 8);
            fit(k, h[0] + h[4] - kw, h[1], kw, HEAD);
        }
        if (selectedPiece == MODE_TAB && !rows.isEmpty()) {
            Row r0 = rows.get(0);
            fit(Lang.tr("sc.modegui.col.mode"), r0.x, contentY, r0.w, HEAD);
            String k = Lang.tr("sc.armorgui.col.key");
            int kw = Math.min(fontRendererObj.getStringWidth(k), KEY_W + 8);
            fit(k, r0.x + modeHeadW - kw, contentY, kw, HEAD);
            for (int[] d : modeDescs) {
                PowerModeKey k2 = PowerModeKey.of(d[0]);
                if (d[3] >= 40 && k2 != null) {
                    fit(Lang.tr("sc.modegui." + k2.key() + ".desc"), d[1], d[2], d[3], DIM);
                }
            }
        }
        for (Row r : rows) {
            if (!r.chip) {
                continue;
            }
            int cx = r.x + r.w + GAP, ch = Math.min(12, r.h), cy = r.y + (r.h - ch) / 2;
            Gas g = chipGas(r.f);
            int edge = 0xFF50505A;
            if (g != null) {
                int cap = ArmorGasSC.suitCapacity(p, g), amount = ArmorGasSC.suitAmount(p, g);
                boolean empty = cap <= 0 || amount <= 0 || g == Gas.HELIUM && !ArmorLogicSC.heliumReady(ArmorGasSC.wornSet(p));
                edge = empty ? 0xFF000000 | RED : low(amount, cap) ? 0xFF000000 | ORANGE : edge;
                box(cx, cy, CHIP_W, ch, 0xFF19191E, edge);
                drawRect(cx + 2, cy + (ch - 6) / 2, cx + 8, cy + (ch - 6) / 2 + 6, 0xFF000000 | g.color);
                fit(GasUiSC.shortName(g), cx + 10, cy + (ch - 8) / 2 + 1, CHIP_W - 11, empty ? RED : g.color);
            } else {
                boolean none = singularOnly(r.f);           // a Singular tool's own function without gas: no EU either
                box(cx, cy, CHIP_W, ch, 0xFF19191E, edge);
                drawRect(cx + 2, cy + (ch - 6) / 2, cx + 8, cy + (ch - 6) / 2 + 6, none ? 0xFF505050 : 0xFFAAAAAA);
                fit(none ? "—" : "EU", cx + 10, cy + (ch - 8) / 2 + 1, CHIP_W - 11, none ? 0x707070 : 0xAAAAAA);
            }
            TextFitSC.hover(cx, cy, CHIP_W, ch, featureTip(r.f));
        }
    }

    // ------------------------------------------------------------------ the gases tab

    private void drawLife() {
        EntityPlayer p = mc.thePlayer;
        int x = tableX;
        int symX = x + 9, nameX = x + 31;
        int leftW = 34, pctW = 20;
        int barW = Math.max(30, Math.min(60, tableW / 4));
        int nameW = tableW - 31 - barW - 4 - pctW - leftW - 6;
        if (nameW < 24) {
            nameW = 0;
        }
        int barX = nameW > 0 ? nameX + nameW + 4 : nameX;
        int pctX = barX + barW + 4, leftX = pctX + pctW + 2;
        leftW = x + tableW - leftX;
        fit(Lang.tr("sc.lifegui.col.gas"), x, contentY, barX - x - 2, HEAD);
        fit(Lang.tr("sc.lifegui.col.stock"), barX, contentY, barW, HEAD);
        fit("%", pctX, contentY, pctW, HEAD);
        fit(Lang.tr("sc.lifegui.col.left"), leftX, contentY, leftW, HEAD);
        int y = lifeY;
        for (Gas g : shownGases()) {
            int cap = ArmorGasSC.suitCapacity(p, g);
            List<String> tip = new ArrayList<String>();
            if (cap <= 0) {                                 // helium radiators with no loop
                drawRect(x, y + 1, x + 6, y + 7, 0xFF505050);
                fit(GasUiSC.shortName(g), symX, y, nameX - symX - 2, 0x808080);
                if (nameW > 0) {
                    fit(GasUiSC.name(g), nameX, y, nameW, 0x808080);
                }
                bar(barX, y + 1, barW, 7, 0, 1, 0x505050, false);
                fit(Lang.tr("sc.gashud.noloop"), pctX, y, x + tableW - pctX, 0x808080);
                tip.add(GasUiSC.name(g));
                tip.add("§7" + Lang.tr("sc.lifegui.noloop"));
            } else {
                int amount = ArmorGasSC.suitAmount(p, g);
                drawRect(x, y + 1, x + 6, y + 7, 0xFF000000 | g.color);
                fit(GasUiSC.shortName(g), symX, y, nameX - symX - 2, critical(amount, cap) && GasUiSC.blinkOff() ? RED : g.color);
                if (nameW > 0) {
                    fit(GasUiSC.name(g), nameX, y, nameW, 0xE0E0E0);
                }
                bar(barX, y + 1, barW, 7, amount, cap, g.color, true);
                fit(String.valueOf(pct(amount, cap)), pctX, y, pctW, levelColor(amount, cap, 0xE0E0E0));
                fit(timeLeft(g, amount), leftX, y, leftW, levelColor(amount, cap, DIM));
                tip.add(Lang.tr("sc.lifegui.gas.head", GasUiSC.name(g), amount, cap));
                tip.add("§7" + GasUiSC.usage(p, g));
                for (int t = 0; t < 4; t++) {
                    ItemStack w = ArmorGasSC.worn(p, t);
                    int c = ArmorGasSC.capacity(w, g);
                    if (c > 0) {
                        tip.add("§7" + Lang.tr("sc.lifegui.part.tank", Lang.tr("sc.armorhud.piece." + t), ArmorGasSC.amount(w, g), c));
                    }
                }
                StringBuilder users = new StringBuilder();
                for (ArmorFeature f : ArmorFeature.values()) {
                    ItemStack piece = ArmorLogicSC.piece(p, f.piece);
                    if (f.gas() == g && piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)) {
                        users.append(users.length() > 0 ? ", " : "").append(nameOf(f));
                    }
                }
                tip.add("§b" + (users.length() > 0 ? Lang.tr("sc.lifegui.gas.users", users) : Lang.tr("sc.lifegui.gas.users.none")));
            }
            TextFitSC.hover(x, y - 1, tableW, GAS_PITCH, tip);
            y += GAS_PITCH;
        }
        // helium cooling
        float cool = ArmorGasSC.coolingFactor(p);
        String coolText = cool <= 0F ? Lang.tr("sc.lifegui.cool.none")
                : Lang.tr("sc.lifegui.cool", String.format(Locale.ROOT, "%.2f", cool), Math.round(ArmorGasSC.RADIATOR_BONUS * 100));
        fit(coolText, x, coolY, tableW, cool <= 0F ? 0x808080 : 0x8FE3FF);
        if (sysHeadY >= 0) {
            fit(Lang.tr("sc.lifegui.systems"), sysX, sysHeadY, sysW, HEAD);
        }
        if (fillHeadY >= 0) {
            fit(Lang.tr(fills.isEmpty() ? "sc.lifegui.fill.none" : "sc.lifegui.fill.head"), fillX, fillHeadY, fillW, fills.isEmpty() ? 0x808080 : HEAD);
        }
        if (sysMoreY >= 0) {                                    // rows that didn't fit: said, not cut silently
            fit(sysMore, sysX, sysMoreY, sysW, 0x707070);
        }
        if (fillMoreY >= 0) {
            fit(fillMore, fillX, fillMoreY, fillW, 0x707070);
        }
    }

    /** Tooltips of the life support tab: the cooling line, a fill button. */
    private List<String> lifeTip(int mouseX, int mouseY) {
        EntityPlayer p = mc.thePlayer;
        List<String> tip = new ArrayList<String>();
        if (mouseX >= tableX && mouseX < tableX + tableW && mouseY >= coolY && mouseY < coolY + 9) {
            tip.add(Lang.tr("sc.lifegui.cool.tip"));
            ItemStack coolChest = ArmorGasSC.worn(p, ArmorGasSC.CHEST);       // a Singular suit's radiators cool more
            tip.add("§7" + Lang.tr("sc.lifegui.cool.tip2", Math.round(ArmorGasSC.radiatorBonus(ArmorLogicSC.suitOf(coolChest)) * 100)));
            return tip;
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (mouseX < b.xPosition || mouseY < b.yPosition || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
                continue;
            }
            if (b.id == FILL_ALL_ID) {
                tip.add(Lang.tr("sc.lifegui.fillall.tip"));
                tip.add("§7" + Lang.tr("sc.lifegui.fillall.tip2"));
                return tip;
            }
            if (b.id < FILL_BASE || b.id >= FILL_BASE + 64) {
                continue;
            }
            int slot = b.id - FILL_BASE;
            ItemStack s = p.inventory.mainInventory[slot];
            for (int[] f : fills) {
                if (f[0] != slot) {
                    continue;
                }
                tip.add(s != null ? s.getDisplayName() : GasUiSC.name(Gas.values()[f[1]]));
                tip.add("§7" + Lang.tr("sc.lifegui.fill.tip", f[2], GasUiSC.name(Gas.values()[f[1]])));
                if (f[4] == 0) {
                    boolean full = ArmorGasSC.suitFill(p, Gas.values()[f[1]], 1, true) <= 0;
                    tip.add("§c" + Lang.tr(full ? "sc.lifegui.fill.full" : "sc.lifegui.fill.nofit"));
                } else if (f[3] == 0) {
                    tip.add("§7" + Lang.tr("sc.lifegui.fill.partial"));
                }
            }
            return tip;
        }
        return tip;
    }

    // ------------------------------------------------------------------ state

    private boolean isOn(Enum<?> f) {
        if (f instanceof DrillFeature) {
            ItemStack d = drill();
            return d != null && ItemDrillSC.isEnabled(d, (DrillFeature) f);
        }
        if (f instanceof BladeFeature) {
            ItemStack b = blade();
            return b != null && ItemBladeSC.isEnabled(b, (BladeFeature) f);
        }
        ArmorFeature a = (ArmorFeature) f;
        ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, a.piece);
        return piece != null && ItemArmorSC.isEnabled(piece, a);
    }

    /** The level a locked Singular function opens at (SingularLevel; creative: nothing locked), 0 when it's open. */
    private int lockedLevel(Enum<?> f) {
        if (f instanceof BladeFeature || f instanceof DrillFeature) {
            ItemStack t = f instanceof BladeFeature ? blade() : drill();
            if (t == null || toolUnlocked(f)) {
                return 0;
            }
            int lv = singLevelOf(f);
            return ToolLevelSC.effectiveLevel(mc.thePlayer, t) >= lv ? 0 : lv;      // the level is there: the branch keeps it (branchLocked)
        }
        if (!(f instanceof ArmorFeature)) {
            return 0;
        }
        ArmorFeature a = (ArmorFeature) f;
        ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, a.piece);
        return piece == null || com.sc.util.SingularLevel.unlocked(mc.thePlayer, a, piece) ? 0 : com.sc.util.SingularLevel.requiredLevel(a);
    }

    /** The shield of a Singular chestplate while Н2 (the event horizon) works in its place. */
    private boolean replacedByHorizon(Enum<?> f) {
        return f == ArmorFeature.SHIELD && ArmorLogicSC.active(mc.thePlayer, ArmorFeature.EVENT_HORIZON);
    }

    /**
     * Why an armour function can't work for the gases (ArmorLogicSC.gasAllows): "Нужен газ: X" or the
     * emergency mode line; null when it can (or it isn't an armour function).
     */
    private String gasBlock(Enum<?> f) {
        if (!(f instanceof ArmorFeature) || f == ArmorFeature.HUD) {
            return null;
        }
        ArmorFeature a = (ArmorFeature) f;
        ItemStack[] worn = ArmorGasSC.wornSet(mc.thePlayer);
        if (worn[a.piece] == null || ArmorLogicSC.gasAllows(worn, a)) {
            return null;
        }
        if (ArmorLogicSC.pieceEmergency(worn, ArmorLogicSC.suitOf(worn[a.piece]))) {
            return Lang.tr("sc.gas.emergency");
        }
        Gas g = ArmorLogicSC.missingGas(worn, a);
        return g == null ? null : Lang.tr("sc.armorgui.needgas", GasUiSC.name(g));
    }

    private static String nameOf(Enum<?> f) {
        if (f instanceof DrillFeature) {
            return Lang.tr("sc.drillfn." + ((DrillFeature) f).key());
        }
        return f instanceof BladeFeature ? Lang.tr("sc.bladefn." + ((BladeFeature) f).key())
                : Lang.tr("sc.armorfn." + f.name().toLowerCase(Locale.ROOT));
    }

    /** A key in three letters at most for the small key button ("F", "^N", "LSH", "M4"); a dash for none. */
    private static String shortKey(int[] b) {
        if (b == null || b[0] == 0) {
            return "—";
        }
        String k = b[0] < 0 ? "M" + (b[0] - ArmorKeyBindsSC.MOUSE_BASE + 1) : Keyboard.getKeyName(b[0]);
        if (k == null) {
            k = "?";
        }
        if (k.startsWith("NUMPAD") && k.length() > 6) {
            k = "N" + k.substring(6);
        }
        boolean mods = b[1] != 0;
        int max = mods ? 2 : 3;
        if (k.length() > max) {
            k = k.substring(0, max);
        }
        return (mods ? "^" : "") + k;
    }

    /** Hover text of a function: what it does, why it's blocked, its gas (where it's held, the use, how long it lasts). */
    private List<String> featureTip(Enum<?> f) {
        List<String> tip = new ArrayList<String>();
        if (f instanceof PowerModeKey) {
            tip.add(Lang.tr("sc.modegui." + ((PowerModeKey) f).key() + ".desc"));
            return tip;
        }
        if (f instanceof DrillFeature) {
            tip.add(Lang.tr("sc.drillfn." + ((DrillFeature) f).key() + ".desc"));
            toolTip(tip, f);
            return tip;
        }
        if (f instanceof BladeFeature) {
            tip.add(Lang.tr("sc.bladefn." + ((BladeFeature) f).key() + ".desc"));
            toolTip(tip, f);
            return tip;
        }
        ArmorFeature a = (ArmorFeature) f;
        EntityPlayer p = mc.thePlayer;
        tip.add(nameOf(a));
        tip.add("§7" + Lang.tr("sc.armorfn." + a.name().toLowerCase(Locale.ROOT) + ".desc"));
        int lock = lockedLevel(a);
        if (lock > 0) {
            tip.add("§c" + Lang.tr("sc.armorgui.tip.locked", lock));
        } else if (branchLocked(a)) {
            tip.add("§c" + Lang.tr("sc.armorgui.tip.branch"));
        } else if (com.sc.util.SingularLevel.requiredLevel(a) > 1) {
            tip.add("§d" + Lang.tr("sc.armorgui.tip.level", com.sc.util.SingularLevel.requiredLevel(a)));
        }
        if (replacedByHorizon(a)) {
            tip.add("§d" + Lang.tr("sc.armorgui.tip.replaced"));
        }
        if (a.offOnLowCharge()) {
            tip.add("§7" + Lang.tr("sc.armorgui.tip.lowcharge", Math.round(ArmorFeature.SING_LOW_CHARGE * 100)));
        }
        int cd = com.sc.util.SingularCooldowns.get(p, a);
        if (cd > 0) {
            tip.add("§6" + Lang.tr("sc.armorgui.tip.cooldown", (cd + 19) / 20));
        }
        String why = gasBlock(a);
        if (why != null) {
            tip.add("§c" + why);
            tip.add("§7" + Lang.tr("sc.armorgui.row.state", Lang.tr(isOn(a) ? "sc.armorgui.on" : "sc.armorgui.off")));
        }
        Gas g = chipGas(a);
        if (g == null) {
            if (a != ArmorFeature.HUD) {
                tip.add("§7" + Lang.tr("sc.armorgui.tip.eu"));
            }
        } else {
            int cap = ArmorGasSC.suitCapacity(p, g), amount = ArmorGasSC.suitAmount(p, g);
            if (cap > 0) {
                StringBuilder where = new StringBuilder();
                for (int t = 0; t < 4; t++) {
                    if (ArmorGasSC.capacity(ArmorGasSC.worn(p, t), g) > 0) {
                        where.append(where.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + t).toLowerCase(Locale.ROOT));
                    }
                }
                String c = amount <= 0 ? "§c" : low(amount, cap) ? "§6" : "§b";
                tip.add(c + Lang.tr("sc.armorgui.tip.gas", GasUiSC.name(g).toLowerCase(Locale.ROOT), where, amount, cap));
            } else {
                tip.add("§c" + Lang.tr("sc.armorgui.tip.gas.none", GasUiSC.name(g)));
            }
            char kind = a.gasUseKind();
            String use = kind == ArmorFeature.USE_SECOND ? Lang.tr("sc.armorgui.tip.use.s", num(a.gasUse()))
                    : kind == ArmorFeature.USE_MINUTE ? Lang.tr("sc.armorgui.tip.use.m", num(a.gasUse()))
                    : kind == ArmorFeature.USE_ONCE ? Lang.tr(a.gasPerPoint() ? "sc.armorgui.tip.use.p" : "sc.armorgui.tip.use.u", num(a.gasUse()))
                    : kind == ArmorFeature.USE_COOLING ? Lang.tr("sc.armorgui.tip.use.h") : Lang.tr("sc.armorgui.tip.use.r");
            tip.add("§7" + use);
            if (cap > 0) {
                tip.add("§7" + GasUiSC.usage(p, g));
            }
        }
        if (a.euPerSecond > 0) {
            tip.add("§7" + Lang.tr("sc.armorgui.tip.euuse", a.euPerSecond));
        }
        return tip;
    }

    private void refresh() {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == MODE_ID) {
                b.displayString = Lang.tr("sc.armorgui.mode", Lang.tr("sc.armorgui.mode." + ArmorLogicSC.powerMode(mc.thePlayer)));
            } else if (b.id == COLOR_ID) {
                b.displayString = Lang.tr("sc.armorgui.glow", Lang.tr("sc.armorgui.glow." + ItemArmorSC.glowColor(ArmorLogicSC.piece(mc.thePlayer, 1))));
            } else if (b.id >= TAB_BASE && b.id < BIND_BASE) {
                continue;
            } else if (b.id >= BIND_BASE) {
                Enum<?> f = featureOf(b.id);
                int[] k = ArmorKeyBindsSC.get(f);
                b.displayString = f == capturing ? "§e..." : !ArmorKeyBindsSC.conflicts(f).isEmpty() ? "§c" + shortKey(k)   // red: the key is taken
                        : k == null ? "§7" + shortKey(k) : "§e" + shortKey(k);
            } else if (featureOf(b.id) instanceof PowerModeKey) {
                PowerModeKey k = (PowerModeKey) featureOf(b.id);
                int mode = ArmorLogicSC.powerMode(mc.thePlayer);
                b.displayString = k == PowerModeKey.CYCLE
                        ? Lang.tr("sc.modegui.cycle", Lang.tr("sc.armorgui.mode." + mode))
                        : k.mode() == mode ? "§a> " + Lang.tr("sc.armorgui.mode." + k.mode()) + " <" : "§7" + Lang.tr("sc.armorgui.mode." + k.mode());
            } else {
                Enum<?> f = featureOf(b.id);
                boolean on = isOn(f);
                String why = gasBlock(f);                          // no gas of its own / emergency mode: grey
                int lock = lockedLevel(f);
                if (lock > 0) {                                    // a Singular function above the piece's level
                    b.displayString = "§8" + nameOf(f) + ": " + Lang.tr("sc.armorgui.row.locked", lock);
                } else if (branchLocked(f)) {                      // Р2: the other side chosen, or nothing chosen yet
                    b.displayString = "§8" + nameOf(f) + ": " + Lang.tr("sc.armorgui.row.branch");
                } else if (replacedByHorizon(f)) {                 // the Singular chestplate: Н2 does the shield's work
                    b.displayString = "§8" + nameOf(f) + ": " + Lang.tr("sc.armorgui.row.replaced");
                } else if (why != null) {
                    boolean emergency = f instanceof ArmorFeature
                            && ArmorLogicSC.pieceEmergency(ArmorGasSC.wornSet(mc.thePlayer), ArmorLogicSC.suitOf(ArmorLogicSC.piece(mc.thePlayer, ((ArmorFeature) f).piece)));
                    b.displayString = "§8" + nameOf(f) + ": " + Lang.tr(emergency ? "sc.armorgui.row.emergency" : "sc.armorgui.row.needgas");
                } else {
                    b.displayString = (on ? "§a" : "§7") + nameOf(f) + ": " + Lang.tr(on ? "sc.armorgui.on" : "sc.armorgui.off");
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void actionPerformed(GuiButton b) {
        if (capturing != null) {
            return;
        }
        if (b.id >= TAB_BASE && b.id < MODE_ID) {
            selectedPiece = b.id - TAB_BASE;
            if (selectedPiece == BRIDGE_TAB) {
                mc.displayGuiScreen(new GuiArmorBridgeSC());
                return;
            }
            initGui();
            return;
        }
        if (b.id >= FILL_BASE && b.id < FILL_BASE + 64) {       // the server pours it in and sends the tanks back
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.GAS_FILL, b.id - FILL_BASE));
            return;
        }
        if (b.id == FILL_ALL_ID) {                              // one message: the server walks the inventory and answers with one chat line
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.GAS_FILL_ALL, 0));
            fillAllWait = 20;
            b.enabled = false;
            return;
        }
        if (b.id == COLOR_ID) {                         // the next colour, on every worn piece (shown at once)
            int next = (ItemArmorSC.glowColor(ArmorLogicSC.piece(mc.thePlayer, 1)) + 1) % ItemArmorSC.GLOW_COLORS;
            for (int i = 0; i < 4; i++) {
                ItemStack s = ArmorLogicSC.piece(mc.thePlayer, i);
                if (s != null) {
                    ItemArmorSC.setGlowColor(s, next);
                }
            }
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.GLOW_COLOR, next));
            refresh();
            return;
        }
        if (b.id == BRANCH_A_ID || b.id == BRANCH_B_ID) {      // the server checks and answers; the tab is rebuilt by the signature
            int lvl = pendingBranch();
            if (lvl > 0) {
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BRANCH, ArmorNetSC.branchFeature(lvl, b.id == BRANCH_A_ID ? 1 : 2)));
            }
            b.enabled = false;
            return;
        }
        if (b.id >= LV_BR && b.id <= LV_HUD) {
            levelAction(b);
            return;
        }
        if (b.id >= TL_PAGE && b.id <= TL_HOLE) {
            toolAction(b);
            return;
        }
        if (b.id == CHIPS_ID) {
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.REMOVE_CHIPS, 0));   // the button goes once the chestplate comes back without them
            return;
        }
        if (b.id == MODE_ID) {
            cyclePowerMode();
        } else if (b.id >= BIND_BASE) {
            capturing = featureOf(b.id);          // the next key press becomes its key
        } else if (featureOf(b.id) instanceof PowerModeKey) {
            PowerModeKey k = (PowerModeKey) featureOf(b.id);
            ItemStack chest = ArmorLogicSC.piece(mc.thePlayer, 1);
            if (chest != null) {
                int want = k == PowerModeKey.CYCLE ? k.next(ItemArmorSC.powerMode(chest)) : k.mode();   // a click picks it
                ItemArmorSC.setPowerMode(chest, want);
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.POWER_MODE, want));
            }
        } else if (featureOf(b.id) instanceof DrillFeature) {
            DrillFeature f = (DrillFeature) featureOf(b.id);
            ItemStack drill = drill();
            if (drill != null && lockedLevel(f) == 0 && !branchLocked(f)) {     // a Singular function its level / branch keeps closed: no
                boolean want = !ItemDrillSC.isEnabled(drill, f);
                ItemDrillSC.setEnabled(drill, f, want);
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DRILL_TOGGLE, f.ordinal(), want));
            }
        } else if (featureOf(b.id) instanceof BladeFeature) {
            BladeFeature f = (BladeFeature) featureOf(b.id);
            ItemStack blade = blade();
            if (blade != null && lockedLevel(f) == 0 && !branchLocked(f)) {
                boolean want = !ItemBladeSC.isEnabled(blade, f);
                if (!(want && f == BladeFeature.BLADE && ItemBladeSC.overheated(blade))) {   // a hot blade stays dark
                    ItemBladeSC.setEnabled(blade, f, want);
                    ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BLADE_TOGGLE, f.ordinal(), want));
                }
            }
        } else if (featureOf(b.id) instanceof ArmorFeature) {
            ArmorFeature f = (ArmorFeature) featureOf(b.id);
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, f.piece);
            if (piece != null && lockedLevel(f) == 0) {
                boolean want = !ItemArmorSC.isEnabled(piece, f);
                ItemArmorSC.setEnabled(piece, f, want);
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.TOGGLE, f.ordinal(), want));
            }
        }
        refresh();
    }

    /** The next power mode (the chestplate tab's button, the strip's caption): shown at once, the server told the mode wanted. */
    private void cyclePowerMode() {
        ItemStack chest = ArmorLogicSC.piece(mc.thePlayer, 1);
        if (chest != null) {
            ItemArmorSC.setPowerMode(chest, (ItemArmorSC.powerMode(chest) + 1) % 3);
        }
        ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.POWER_MODE, ArmorLogicSC.powerMode(mc.thePlayer)));
    }

    /** Setting a key: Esc cancels, Backspace / Delete clears, a modifier alone waits for the real key. */
    @Override
    protected void keyTyped(char c, int key) {
        if (capturing == null) {
            super.keyTyped(c, key);
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            capturing = null;
        } else if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) {
            ArmorKeyBindsSC.clear(capturing);
            capturing = null;
        } else if (key > 0 && !ArmorKeyBindsSC.isModifierKey(key)) {
            ArmorKeyBindsSC.set(capturing, key, ArmorKeyBindsSC.modifiersDown());
            capturing = null;
        }
        refresh();
    }

    /** A middle or side mouse button can be a function's key too (left / right click stay the screen's); a piece in the panel opens its tab. */
    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (capturing != null) {
            if (button >= 2) {
                ArmorKeyBindsSC.set(capturing, ArmorKeyBindsSC.MOUSE_BASE + button, ArmorKeyBindsSC.modifiersDown());
                capturing = null;
                refresh();
            }
            return;
        }
        int[] ml = modeLabel;
        if (button == 0 && ml != null && ArmorLogicSC.piece(mc.thePlayer, 1) != null
                && x >= ml[0] && y >= ml[1] && x < ml[0] + ml[2] && y < ml[1] + ml[3]) {   // the strip's mode caption: the next mode
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            cyclePowerMode();
            refresh();
            return;
        }
        if (button == 0 && panelMode > 0) {
            for (int t = 0; t < 4; t++) {
                int[] r = pieceRect[t];
                if (r != null && t != selectedPiece && tabs.contains(t) && x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3]) {
                    mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                    selectedPiece = t;
                    initGui();
                    return;
                }
            }
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        if (fillAllWait > 0) {
            fillAllWait--;
        }
        String sig = signature();
        if (selectedPiece == LEVEL_TAB && !com.sc.util.SingularLevel.wearsSingular(mc.thePlayer)
                || selectedPiece == BLADE_TAB && blade() == null || selectedPiece == DRILL_TAB && drill() == null || selectedPiece == MODE_TAB && ArmorLogicSC.piece(mc.thePlayer, 1) == null
                || selectedPiece == 1 && chipsShown != ItemArmorChipSC.hasChips(ArmorLogicSC.piece(mc.thePlayer, 1))) {
            initGui();                                           // the blade left the hand / the chips came out
        } else if (selectedPiece == LIFE_TAB && !anyPieceWorn() || !layoutSig.equals(sig)) {
            initGui();                                           // other pieces worn, a container used up or picked up
        } else if (selectedPiece == LIFE_TAB) {
            refreshFills();
        }
        refresh();
    }

    // ------------------------------------------------------------------ drawing

    private List<String> wrap(List<String> tip) {
        int maxW = Math.max(120, Math.min(220, width / 2 - 20));
        List<String> wrapped = new ArrayList<String>();
        for (String line : tip) {
            String color = line.startsWith("§") && line.length() > 1 ? line.substring(0, 2) : "";
            for (Object part : fontRendererObj.listFormattedStringToWidth(line, maxW)) {
                String s = (String) part;
                wrapped.add(s.startsWith("§") ? s : color + s);    // a wrapped grey line stays grey
            }
        }
        return wrapped;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        modeLabel = null;                                        // set again by drawStrip while the chestplate is worn
        drawDefaultBackground();
        box(winX, top, winW, winH, C_FRAME, C_EDGE);
        drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.title"), width / 2, top + 4, 0xFFFFFF);
        if (selectedPiece < 0) {
            drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.none"), width / 2, height / 2, 0xA0A0A0);
        } else {
            drawStrip();
            drawPanel();
            if (selectedPiece == LIFE_TAB) {
                drawLife();
            } else if (selectedPiece == LEVEL_TAB) {
                drawLevel();
            } else if (toolPage == 1 && singularTool() != null) {
                drawToolInfo();
            } else {
                drawRows();
            }
            // the set bonus at the foot of the window
            String bonus = null;
            if (selectedPiece == DRILL_TAB) {
                ItemStack d = drill();
                if (d != null && DrillLogicSC.setBonus(mc.thePlayer, d)) {
                    bonus = Lang.tr("sc.drillgui.set." + ItemDrillSC.typeOf(d).key());
                }
            } else if (selectedPiece == BLADE_TAB) {
                ItemStack b = blade();
                if (b != null && BladeLogicSC.setBonus(mc.thePlayer, b)) {
                    bonus = Lang.tr("sc.bladegui.set." + ItemBladeSC.typeOf(b).key());
                }
            } else {
                ArmorSuit set = ArmorLogicSC.bonusSet(mc.thePlayer);
                if (set != null) {
                    bonus = Lang.tr("sc.armorgui.set." + set.name().toLowerCase(Locale.ROOT));
                }
            }
            int footW = contentW - (tlPageW > 0 ? tlPageW + 4 : 0);   // the Singular tool's page switch at the right
            if (bonus != null) {
                TextFitSC.drawCentered(fontRendererObj, bonus, contentX, footY, footW, 0x80FF80, true, 0, 0);
            } else if (singularTool() != null && !com.sc.util.SingularLevel.wearsSingular(mc.thePlayer)) {
                TextFitSC.drawCentered(fontRendererObj, Lang.tr("sc.toolgui.gas.none.short"), contentX, footY, footW, RED, true, 0, 0);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (selectedPiece == LEVEL_TAB) {
            levelButtonTips();                                   // over the buttons' own cut-label hovers
        } else if (singularTool() != null) {
            toolButtonTips();
        }
        if (capturing != null) {                                 // over the strip: what to press
            List<?> lines = fontRendererObj.listFormattedStringToWidth(Lang.tr("sc.armorgui.bind.wait"), winW - 20);
            int bh = lines.size() * 10 + 6, by = top + 34;
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);                  // the panel's item icons (z +50) would show through
            box(winX + 5, by, winW - 10, bh, 0xF0181A10, 0xFFFFE060);
            for (int i = 0; i < lines.size(); i++) {
                drawCenteredString(fontRendererObj, (String) lines.get(i), width / 2, by + 4 + i * 10, 0xFFE060);
            }
            if (depth) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
            return;
        }
        boolean overFeature = false;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            overFeature |= !(b.id >= TAB_BASE && b.id < BIND_BASE) && mouseX >= b.xPosition && mouseY >= b.yPosition
                    && mouseX < b.xPosition + b.width && mouseY < b.yPosition + b.height;
        }
        List<String> cut = TextFitSC.hoverAt(mouseX, mouseY);
        List<String> life = selectedPiece == LIFE_TAB && !overFeature ? lifeTip(mouseX, mouseY) : null;
        if (life != null && !life.isEmpty()) {
            drawHoveringText(wrap(life), mouseX, mouseY, fontRendererObj);
        } else if (!overFeature && cut != null) {             // a caption too long for its place, a tank, a gas chip
            drawHoveringText(wrap(cut), mouseX, mouseY, fontRendererObj);
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id >= TAB_BASE && b.id < BIND_BASE || mouseX < b.xPosition || mouseY < b.yPosition
                    || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
                continue;
            }
            Enum<?> f = featureOf(b.id);
            if (f == null) {
                continue;
            }
            List<String> tip;
            if (b.id >= BIND_BASE) {
                boolean action = f instanceof PowerModeKey || (f instanceof DrillFeature ? ((DrillFeature) f).isAction()
                        : f instanceof BladeFeature ? ((BladeFeature) f).isAction() : ((ArmorFeature) f).isAction());
                tip = new ArrayList<String>();
                tip.add(Lang.tr(action ? "sc.armorgui.bind.tip.action" : "sc.armorgui.bind.tip"));
                tip.add("§e" + Lang.tr("sc.armorgui.bind.current", ArmorKeyBindsSC.describe(ArmorKeyBindsSC.get(f))));
                tip.add("§7" + Lang.tr("sc.armorgui.bind.tip2"));
                List<String> taken = ArmorKeyBindsSC.conflicts(f);
                if (!taken.isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (String n : taken) {
                        names.append(names.length() > 0 ? ", " : "").append(n);
                    }
                    tip.add("§c" + Lang.tr("sc.armorgui.bind.conflict", names));
                }
            } else {
                tip = featureTip(f);
            }
            drawHoveringText(wrap(tip), mouseX, mouseY, fontRendererObj);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
