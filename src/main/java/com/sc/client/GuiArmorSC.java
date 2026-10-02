package com.sc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;

import com.sc.handler.ArmorNetSC;
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
import com.sc.util.BladeType;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;
import com.sc.util.PowerModeKey;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * Armour settings (the "armour" key, K): a tab for each worn piece, a "Blade" tab while an
 * energy blade is in hand, a "Drill" tab while a drill is in hand (opened on its own when no armour
 * is worn), and a "Mode" tab (keys for the power mode) while a chestplate is worn; for each function a switch and the key that switches it (or fires it -
 * dash, annihilation pulse, the blade's sweep / wave / lunge) - any key, with Ctrl / Shift / Alt,
 * or a middle / side mouse button. Plus the power mode, the set bonus and (chestplate tab) taking the chips out.
 * Switches go to the server (ArmorNetSC) and show at once; keys live in the player's own config.
 */
public class GuiArmorSC extends GuiScreen {

    private static final int NAME_W = 170, BIND_W = 90, ROW = 20;
    /** The blade's tab (after the four armour pieces). */
    private static final int BLADE_TAB = 4, MODE_TAB = 5, DRILL_TAB = 6, LIFE_TAB = 7;
    private static final int BLADE_BASE = 500, MODE_BASE = 600, DRILL_BASE = 700, TAB_BASE = 900, MODE_ID = 1000, CHIPS_ID = 1001, COLOR_ID = 1002, BIND_BASE = 2000;
    /** Life support tab: "fill from this inventory slot" buttons (FILL_BASE + slot, under BIND_BASE). */
    private static final int FILL_BASE = 1100, FILL_MAX = 5;

    // ---- life support tab layout (set by initGui) ----
    private static final int SIL_W = 60, GAS_W = 200, CTRL_W = 200, GAS_ROW = 20;
    private int silX, gasX, ctrlX, lifeY, fillHeadY = -1, coolY;
    /** What the tab was built for - rebuilt when the worn pieces or the inventory's gas containers change. */
    private String lifeSig = "";
    /** Per fill button: {slot, gas ordinal, mB, whole-only 1/0, fits 1/0}. */
    private final List<int[]> fills = new ArrayList<int[]>();

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
    /** The "remove chips" button is on screen (rebuilt when the chips come or go). */
    private boolean chipsShown;
    private int top;
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

    @Override
    public void initGui() {
        buttonList.clear();
        top = Math.max(4, height / 2 - 105);
        List<Integer> tabs = new ArrayList<Integer>();
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
        if (selectedPiece < 0 || !tabs.contains(selectedPiece)) {
            selectedPiece = tabs.isEmpty() ? -1 : tabs.get(0);
        }
        int tabW = tabs.isEmpty() ? 70 : Math.max(40, Math.min(70, (width - 8) / tabs.size() - 2));   // eight tabs on a narrow screen
        int tx = width / 2 - tabs.size() * (tabW + 2) / 2;
        for (int type : tabs) {
            String label = type == LIFE_TAB ? Lang.tr("sc.lifegui.tab") : type == BLADE_TAB ? Lang.tr("sc.bladegui.tab") : type == MODE_TAB ? Lang.tr("sc.modegui.tab")
                    : type == DRILL_TAB ? Lang.tr("sc.drillgui.tab") : Lang.tr("sc.armorhud.piece." + type);
            GuiButton tab = new com.sc.inventory.TextFitSC.Button(TAB_BASE + type, tx, top + 14, tabW, 18, label);
            tab.enabled = type != selectedPiece;                // the pressed-in one is the open tab
            buttonList.add(tab);
            tx += tabW + 2;
        }
        int left = width / 2 - (NAME_W + BIND_W + 4) / 2;
        int y = top + 40;
        fills.clear();
        fillHeadY = -1;
        if (selectedPiece == LIFE_TAB) {
            initLife();
        } else if (selectedPiece == DRILL_TAB) {
            DrillType t = ItemDrillSC.typeOf(drill());
            List<DrillFeature> rows = new ArrayList<DrillFeature>();
            for (DrillFeature f : DrillFeature.values()) {
                if (f.availableIn(t)) {
                    rows.add(f);
                }
            }
            int rowW = NAME_W + BIND_W + 4;
            boolean twoCols = rows.size() > 8 && width >= rowW * 2 + 16;     // the Exo drill's 12 rows don't fit one column
            int perCol = twoCols ? (rows.size() + 1) / 2 : rows.size();
            int colLeft = twoCols ? width / 2 - rowW - 4 : left;
            for (int i = 0; i < rows.size(); i++) {
                int col = i / perCol;
                addRow(rows.get(i), colLeft + col * (rowW + 8), y + (i % perCol) * ROW);
            }
            y += perCol * ROW;
        } else if (selectedPiece == MODE_TAB) {
            for (PowerModeKey k : PowerModeKey.values()) {
                addRow(k, left, y);
                y += ROW;
            }
        } else if (selectedPiece == BLADE_TAB) {
            BladeType t = ItemBladeSC.typeOf(blade());
            for (BladeFeature f : BladeFeature.values()) {
                if (f.availableIn(t)) {
                    addRow(f, left, y);
                    y += ROW;
                }
            }
        } else if (selectedPiece >= 0) {
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, selectedPiece);
            for (ArmorFeature f : ArmorFeature.values()) {
                if (f.availableIn(ArmorLogicSC.suitOf(piece), selectedPiece)) {
                    addRow(f, left, y);
                    y += ROW;
                }
            }
        }
        chipsShown = selectedPiece == 1 && ItemArmorChipSC.hasChips(ArmorLogicSC.piece(mc.thePlayer, 1));
        if (chipsShown) {
            buttonList.add(new com.sc.inventory.TextFitSC.Button(CHIPS_ID, width / 2 - 90, y + 4, 180, 18, Lang.tr("sc.armorgui.chips.remove")));
            y += 22;
        }
        if (selectedPiece == 1) {                          // the chestplate's tab: the light colour of the whole suit
            buttonList.add(new com.sc.inventory.TextFitSC.Button(COLOR_ID, width / 2 - 90, y + 4, 180, 18, ""));
            y += 22;
        }
        if (ArmorLogicSC.piece(mc.thePlayer, 1) != null && selectedPiece != BLADE_TAB && selectedPiece != MODE_TAB && selectedPiece != DRILL_TAB
                && selectedPiece != LIFE_TAB) {
            buttonList.add(new com.sc.inventory.TextFitSC.Button(MODE_ID, width / 2 - 90, Math.max(y + 6, top + 190), 180, 18, ""));
        }
        refresh();
    }

    private void addRow(Enum<?> f, int left, int y) {
        addRow(f, left, y, NAME_W, BIND_W);
    }

    private void addRow(Enum<?> f, int left, int y, int nameW, int bindW) {
        buttonList.add(new com.sc.inventory.TextFitSC.Button(switchId(f), left, y, nameW, 18, ""));
        buttonList.add(new com.sc.inventory.TextFitSC.Button(BIND_BASE + switchId(f), left + nameW + 4, y, bindW, 18, ""));
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

    /** The worn pieces, their tanks and the inventory's gas containers - what initLife() laid out. */
    private String lifeSignature() {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < 4; t++) {
            ItemStack w = ArmorGasSC.worn(mc.thePlayer, t);
            sb.append(w == null ? "-" : Item.getIdFromItem(w.getItem()) + ":" + ArmorGasSC.capacityBonusPercent(w)).append(';');
        }
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
    }

    private void initLife() {
        lifeSig = lifeSignature();
        int rows = shownGases().size();
        lifeY = top + 40;
        coolY = lifeY + rows * GAS_ROW + 2;
        int total = SIL_W + 8 + GAS_W + 8 + CTRL_W;
        boolean wide = width >= total + 8;
        silX = width / 2 - (wide ? total : SIL_W + 8 + GAS_W) / 2;
        gasX = silX + SIL_W + 8;
        int y;
        if (wide) {
            ctrlX = gasX + GAS_W + 8;
            y = lifeY;
        } else {                                              // narrow: the controls under the gases
            ctrlX = gasX;
            y = coolY + 14;
        }
        for (ArmorFeature f : lifeFeatures()) {
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, f.piece);
            if (piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)) {
                addRow(f, ctrlX, y, CTRL_W - 60, 56);
                y += ROW;
            }
        }
        fillHeadY = y + 4;
        y = fillHeadY + 12;
        // one button per kind of container (same item, gas and amount), the first slot holding it
        List<String> seen = new ArrayList<String>();
        List<Integer> counts = new ArrayList<Integer>();
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
                continue;
            }
            seen.add(kind);
            counts.add(inv[i].stackSize);
            Gas g = Gas.values()[gi[0]];
            int room = ArmorGasSC.suitFill(mc.thePlayer, g, gi[1], true);
            boolean fits = gi[2] == 1 ? room == gi[1] : room > 0;
            fills.add(new int[]{i, gi[0], gi[1], gi[2], fits ? 1 : 0});
        }
        for (int k = 0; k < fills.size(); k++) {
            int[] f = fills.get(k);
            String label = Lang.tr("sc.lifegui.fill", GasUiSC.shortName(Gas.values()[f[1]]), f[2]) + (counts.get(k) > 1 ? " §7(" + counts.get(k) + ")" : "");
            GuiButton b = new com.sc.inventory.TextFitSC.Button(FILL_BASE + f[0], ctrlX, y, CTRL_W, 18, label);
            b.enabled = f[4] == 1;
            buttonList.add(b);
            y += ROW;
        }
    }

    /** Silhouette parts: {x, y, w, h} relative to (silX, lifeY), by piece type. */
    private static final int[][] PART = {{20, 0, 20, 18}, {10, 20, 40, 38}, {14, 60, 32, 30}, {12, 92, 36, 14}};

    private void drawLife(int mouseX, int mouseY) {
        net.minecraft.entity.player.EntityPlayer p = mc.thePlayer;
        // the suit: each worn piece with its own tanks as small level bars
        for (int t = 0; t < 4; t++) {
            int x0 = silX + PART[t][0], y0 = lifeY + PART[t][1], x1 = x0 + PART[t][2], y1 = y0 + PART[t][3];
            ItemStack w = ArmorGasSC.worn(p, t);
            drawRect(x0, y0, x1, y1, w != null ? 0xFF6A7380 : 0xFF303030);
            drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, w != null ? 0xFF22262E : 0xC0101010);
            if (w == null) {
                continue;
            }
            int bx = x0 + 2;                                // three 4px bars fit the 20px helmet (krypton was cut off at +3)
            for (Gas g : Gas.values()) {
                int cap = ArmorGasSC.capacity(w, g);
                if (cap <= 0 || bx + 4 > x1 - 2) {
                    continue;
                }
                int h = y1 - y0 - 6;
                int lvl = (int) ((long) ArmorGasSC.amount(w, g) * h / cap);
                drawRect(bx, y0 + 3, bx + 4, y1 - 3, 0xFF0A0A0A);
                if (lvl > 0) {
                    drawRect(bx, y1 - 3 - lvl, bx + 4, y1 - 3, 0xFF000000 | g.color);
                }
                bx += 6;
            }
        }
        // the gases: name, bar, amount; under it the use and how long it lasts
        int y = lifeY;
        for (Gas g : shownGases()) {
            int cap = ArmorGasSC.suitCapacity(p, g);
            int barX = gasX + 56, barW = 70;
            if (cap <= 0) {                                 // helium radiators with no loop
                fontRendererObj.drawStringWithShadow(GasUiSC.shortName(g), gasX, y, 0x808080);
                drawRect(barX - 1, y + 1, barX + barW + 1, y + 8, 0xFF000000);
                drawRect(barX, y + 2, barX + barW, y + 7, 0xFF505050);
                fontRendererObj.drawStringWithShadow(Lang.tr("sc.gashud.noloop"), barX + barW + 4, y, 0x808080);
                fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(Lang.tr("sc.lifegui.noloop"), GAS_W), gasX, y + 10, 0x707070);
            } else {
                int amount = ArmorGasSC.suitAmount(p, g);
                boolean low = (long) amount * 100 < 15L * cap;
                boolean blink = low && GasUiSC.blinkOff();
                fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(GasUiSC.name(g), 54), gasX, y, blink ? 0xFF5050 : g.color);
                drawRect(barX - 1, y + 1, barX + barW + 1, y + 8, 0xFF000000);
                int fill = (int) ((long) amount * barW / cap);
                if (fill > 0) {
                    drawRect(barX, y + 2, barX + fill, y + 7, 0xFF000000 | g.color);
                }
                fontRendererObj.drawStringWithShadow(Lang.tr("sc.lifegui.amount", amount, cap), barX + barW + 4, y, low ? 0xFF5050 : 0xE0E0E0);
                fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(GasUiSC.usage(p, g), GAS_W), gasX, y + 10, 0x909090);
            }
            y += GAS_ROW;
        }
        // helium cooling
        float cool = ArmorGasSC.coolingFactor(p);
        String coolText = cool <= 0F ? Lang.tr("sc.lifegui.cool.none")
                : Lang.tr("sc.lifegui.cool", String.format(Locale.ROOT, "%.2f", cool), Math.round(ArmorGasSC.RADIATOR_BONUS * 100));
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(coolText, GAS_W), gasX, coolY, cool <= 0F ? 0x808080 : 0x8FE3FF);
        // refuelling
        if (fillHeadY >= 0) {
            fontRendererObj.drawStringWithShadow(Lang.tr(fills.isEmpty() ? "sc.lifegui.fill.none" : "sc.lifegui.fill.head"), ctrlX, fillHeadY,
                    fills.isEmpty() ? 0x808080 : 0xE0E0E0);
        }
    }

    /** Tooltips of the life support tab: a piece of the silhouette, the cooling line, a fill button. */
    private List<String> lifeTip(int mouseX, int mouseY) {
        net.minecraft.entity.player.EntityPlayer p = mc.thePlayer;
        List<String> tip = new ArrayList<String>();
        for (int t = 0; t < 4; t++) {
            int x0 = silX + PART[t][0], y0 = lifeY + PART[t][1];
            if (mouseX < x0 || mouseY < y0 || mouseX >= x0 + PART[t][2] || mouseY >= y0 + PART[t][3]) {
                continue;
            }
            ItemStack w = ArmorGasSC.worn(p, t);
            tip.add(Lang.tr("sc.armorhud.piece." + t));
            if (w == null) {
                tip.add("§7" + Lang.tr("sc.lifegui.part.none"));
                return tip;
            }
            boolean any = false;
            for (Gas g : Gas.values()) {
                int cap = ArmorGasSC.capacity(w, g);
                if (cap > 0) {
                    any = true;
                    tip.add(Lang.tr("sc.lifegui.part.tank", GasUiSC.name(g), ArmorGasSC.amount(w, g), cap));
                }
            }
            if (!any) {
                tip.add("§7" + Lang.tr("sc.lifegui.part.notanks"));
            }
            if (t != ArmorGasSC.CHEST && ArmorGasSC.baseCapacity(w, Gas.HELIUM) > 0) {
                tip.add("§b" + Lang.tr("sc.lifegui.part.radiator", Math.round(ArmorGasSC.RADIATOR_BONUS * 100)));
            } else if (t == ArmorGasSC.CHEST && ArmorGasSC.baseCapacity(w, Gas.HELIUM) > 0) {
                tip.add("§b" + Lang.tr("sc.lifegui.part.loop"));
            }
            return tip;
        }
        if (mouseX >= gasX && mouseX < gasX + GAS_W && mouseY >= coolY && mouseY < coolY + 9) {
            tip.add(Lang.tr("sc.lifegui.cool.tip"));
            tip.add("§7" + Lang.tr("sc.lifegui.cool.tip2", Math.round(ArmorGasSC.RADIATOR_BONUS * 100)));
            return tip;
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id < FILL_BASE || b.id >= FILL_BASE + 64 || mouseX < b.xPosition || mouseY < b.yPosition
                    || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
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

    private static String nameOf(Enum<?> f) {
        if (f instanceof DrillFeature) {
            return Lang.tr("sc.drillfn." + ((DrillFeature) f).key());
        }
        return f instanceof BladeFeature ? Lang.tr("sc.bladefn." + ((BladeFeature) f).key())
                : Lang.tr("sc.armorfn." + f.name().toLowerCase(Locale.ROOT));
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
                b.displayString = f == capturing ? "§e> ... <"
                        : (ArmorKeyBindsSC.conflicts(f).isEmpty() ? "" : "§c") + ArmorKeyBindsSC.describe(ArmorKeyBindsSC.get(f));   // red: the key is taken
            } else if (featureOf(b.id) instanceof PowerModeKey) {
                PowerModeKey k = (PowerModeKey) featureOf(b.id);
                int mode = ArmorLogicSC.powerMode(mc.thePlayer);
                b.displayString = k == PowerModeKey.CYCLE
                        ? Lang.tr("sc.modegui.cycle", Lang.tr("sc.armorgui.mode." + mode))
                        : (k.mode() == mode ? "§a" : "§7") + Lang.tr("sc.armorgui.mode." + k.mode());
            } else {
                Enum<?> f = featureOf(b.id);
                boolean on = isOn(f);
                b.displayString = (on ? "§a" : "§7") + nameOf(f) + ": " + Lang.tr(on ? "sc.armorgui.on" : "sc.armorgui.off");
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (capturing != null) {
            return;
        }
        if (b.id >= TAB_BASE && b.id < MODE_ID) {
            selectedPiece = b.id - TAB_BASE;
            initGui();
            return;
        }
        if (b.id >= FILL_BASE && b.id < FILL_BASE + 64) {       // the server pours it in and sends the tanks back
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.GAS_FILL, b.id - FILL_BASE));
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
        if (b.id == CHIPS_ID) {
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.REMOVE_CHIPS, 0));   // the button goes once the chestplate comes back without them
            return;
        }
        if (b.id == MODE_ID) {
            ItemStack chest = ArmorLogicSC.piece(mc.thePlayer, 1);
            if (chest != null) {
                ItemArmorSC.setPowerMode(chest, (ItemArmorSC.powerMode(chest) + 1) % 3);   // shown at once
            }
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.POWER_MODE, ArmorLogicSC.powerMode(mc.thePlayer)));
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
            if (drill != null) {
                boolean want = !ItemDrillSC.isEnabled(drill, f);
                ItemDrillSC.setEnabled(drill, f, want);
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DRILL_TOGGLE, f.ordinal(), want));
            }
        } else if (featureOf(b.id) instanceof BladeFeature) {
            BladeFeature f = (BladeFeature) featureOf(b.id);
            ItemStack blade = blade();
            if (blade != null) {
                boolean want = !ItemBladeSC.isEnabled(blade, f);
                if (!(want && f == BladeFeature.BLADE && ItemBladeSC.overheated(blade))) {   // a hot blade stays dark
                    ItemBladeSC.setEnabled(blade, f, want);
                    ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BLADE_TOGGLE, f.ordinal(), want));
                }
            }
        } else {
            ArmorFeature f = (ArmorFeature) featureOf(b.id);
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, f.piece);
            if (piece != null) {
                boolean want = !ItemArmorSC.isEnabled(piece, f);
                ItemArmorSC.setEnabled(piece, f, want);
                ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.TOGGLE, f.ordinal(), want));
            }
        }
        refresh();
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

    /** A middle or side mouse button can be a function's key too (left / right click stay the screen's). */
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
        super.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        if (selectedPiece == BLADE_TAB && blade() == null || selectedPiece == DRILL_TAB && drill() == null || selectedPiece == MODE_TAB && ArmorLogicSC.piece(mc.thePlayer, 1) == null
                || selectedPiece == 1 && chipsShown != ItemArmorChipSC.hasChips(ArmorLogicSC.piece(mc.thePlayer, 1))) {
            initGui();                                           // the blade left the hand / the chips came out
        } else if (selectedPiece == LIFE_TAB && (!anyPieceWorn() || !lifeSig.equals(lifeSignature()))) {
            initGui();                                           // other pieces worn, a container used up or picked up
        } else if (selectedPiece == LIFE_TAB) {
            refreshFills();
        }
        refresh();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        com.sc.inventory.TextFitSC.beginFrame();
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.title"), width / 2, top, 0xFFFFFF);
        if (selectedPiece < 0) {
            drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.none"), width / 2, height / 2, 0xA0A0A0);
        } else if (selectedPiece == LIFE_TAB) {
            drawLife(mouseX, mouseY);
        } else if (selectedPiece == MODE_TAB) {
            drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.mode", Lang.tr("sc.armorgui.mode." + ArmorLogicSC.powerMode(mc.thePlayer))),
                    width / 2, top + 35 - 2, 0xE0E0E0);
        } else if (selectedPiece == DRILL_TAB) {
            ItemStack d = drill();
            if (d != null) {
                int cap = ItemDrillSC.capacityOf(d);
                int pct = cap <= 0 ? 0 : (int) ((long) ItemDrillSC.chargeOf(d) * 100 / cap);
                drawCenteredString(fontRendererObj, d.getDisplayName() + " §7(" + pct + "%, "
                        + Lang.tr(ItemDrillSC.overheated(d) ? "sc.drillhud.overheat" : "sc.drillhud.heat", ItemDrillSC.heatPercent(d)) + ")",
                        width / 2, top + 35 - 2, 0xE0E0E0);
            }
        } else if (selectedPiece == BLADE_TAB) {
            ItemStack b = blade();
            if (b != null) {
                int cap = ItemBladeSC.capacityOf(b);
                int pct = cap <= 0 ? 0 : (int) ((long) ItemBladeSC.chargeOf(b) * 100 / cap);
                drawCenteredString(fontRendererObj, b.getDisplayName() + " §7(" + pct + "%, "
                        + Lang.tr(ItemBladeSC.overheated(b) ? "sc.bladehud.overheat" : "sc.bladehud.heat", ItemBladeSC.heatPercent(b)) + ")",
                        width / 2, top + 35 - 2, 0xE0E0E0);
            }
        } else {
            ItemStack piece = ArmorLogicSC.piece(mc.thePlayer, selectedPiece);
            if (piece != null) {
                int cap = ItemArmorSC.capacityOf(piece);
                int pct = cap <= 0 ? 0 : (int) ((long) ItemArmorSC.chargeOf(piece) * 100 / cap);
                drawCenteredString(fontRendererObj, piece.getDisplayName() + " §7(" + pct + "%)", width / 2, top + 35 - 2, 0xE0E0E0);
            }
        }
        if (capturing != null) {
            drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.bind.wait"), width / 2, height - 26, 0xFFE060);
        }
        if (selectedPiece == DRILL_TAB) {
            ItemStack d = drill();
            if (d != null && DrillLogicSC.setBonus(mc.thePlayer, d)) {
                drawCenteredString(fontRendererObj, Lang.tr("sc.drillgui.set." + ItemDrillSC.typeOf(d).key()),
                        width / 2, height - 14, 0x80FF80);
            }
        } else if (selectedPiece == BLADE_TAB) {
            ItemStack b = blade();
            if (b != null && BladeLogicSC.setBonus(mc.thePlayer, b)) {
                drawCenteredString(fontRendererObj, Lang.tr("sc.bladegui.set." + ItemBladeSC.typeOf(b).key()),
                        width / 2, height - 14, 0x80FF80);
            }
        } else {
            ArmorSuit set = ArmorLogicSC.fullSet(mc.thePlayer);
            if (set != null) {
                drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.set." + set.name().toLowerCase(Locale.ROOT)),
                        width / 2, height - 14, 0x80FF80);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        boolean overFeature = false;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            overFeature |= !(b.id >= TAB_BASE && b.id < BIND_BASE) && mouseX >= b.xPosition && mouseY >= b.yPosition
                    && mouseX < b.xPosition + b.width && mouseY < b.yPosition + b.height;
        }
        List<String> cut = com.sc.inventory.TextFitSC.hoverAt(mouseX, mouseY);
        List<String> life = selectedPiece == LIFE_TAB && !overFeature ? lifeTip(mouseX, mouseY) : null;
        if (life != null && !life.isEmpty()) {
            drawHoveringText(life, mouseX, mouseY, fontRendererObj);
        } else if (!overFeature && cut != null) {             // a caption too long for its button or tab
            drawHoveringText(cut, mouseX, mouseY, fontRendererObj);
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id >= TAB_BASE && b.id < BIND_BASE || mouseX < b.xPosition || mouseY < b.yPosition
                    || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
                continue;
            }
            List<String> tip = new ArrayList<String>();
            Enum<?> f = featureOf(b.id);
            boolean action = f instanceof PowerModeKey || (f instanceof DrillFeature ? ((DrillFeature) f).isAction()
                    : f instanceof BladeFeature ? ((BladeFeature) f).isAction() : ((ArmorFeature) f).isAction());
            if (b.id >= BIND_BASE) {
                tip.add(Lang.tr(action ? "sc.armorgui.bind.tip.action" : "sc.armorgui.bind.tip"));
                tip.add("§7" + Lang.tr("sc.armorgui.bind.tip2"));
                List<String> taken = ArmorKeyBindsSC.conflicts(f);
                if (!taken.isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (String n : taken) {
                        names.append(names.length() > 0 ? ", " : "").append(n);
                    }
                    tip.add("§c" + Lang.tr("sc.armorgui.bind.conflict", names));
                }
            } else if (f instanceof PowerModeKey) {
                tip.add(Lang.tr("sc.modegui." + ((PowerModeKey) f).key() + ".desc"));
            } else if (f instanceof DrillFeature) {
                tip.add(Lang.tr("sc.drillfn." + ((DrillFeature) f).key() + ".desc"));
            } else if (f instanceof BladeFeature) {
                tip.add(Lang.tr("sc.bladefn." + ((BladeFeature) f).key() + ".desc"));
            } else {
                tip.add(Lang.tr("sc.armorfn." + f.name().toLowerCase(Locale.ROOT) + ".desc"));
            }
            // long descriptions wrapped - one line ran off the screen's edge
            int maxW = Math.max(120, Math.min(220, width / 2 - 20));
            List<String> wrapped = new ArrayList<String>();
            for (String line : tip) {
                String color = line.startsWith("§") && line.length() > 1 ? line.substring(0, 2) : "";
                for (Object part : fontRendererObj.listFormattedStringToWidth(line, maxW)) {
                    String s = (String) part;
                    wrapped.add(s.startsWith("§") ? s : color + s);    // a wrapped grey line stays grey
                }
            }
            drawHoveringText(wrapped, mouseX, mouseY, fontRendererObj);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
