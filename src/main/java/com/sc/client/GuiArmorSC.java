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
import com.sc.util.ArmorSuit;
import com.sc.util.BladeFeature;
import com.sc.util.BladeType;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;
import com.sc.util.PowerModeKey;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

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
    private static final int BLADE_TAB = 4, MODE_TAB = 5, DRILL_TAB = 6;
    private static final int BLADE_BASE = 500, MODE_BASE = 600, DRILL_BASE = 700, TAB_BASE = 900, MODE_ID = 1000, CHIPS_ID = 1001, BIND_BASE = 2000;

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
        if (selectedPiece < 0 || !tabs.contains(selectedPiece)) {
            selectedPiece = tabs.isEmpty() ? -1 : tabs.get(0);
        }
        int tabW = 70;
        int tx = width / 2 - tabs.size() * (tabW + 2) / 2;
        for (int type : tabs) {
            String label = type == BLADE_TAB ? Lang.tr("sc.bladegui.tab") : type == MODE_TAB ? Lang.tr("sc.modegui.tab")
                    : type == DRILL_TAB ? Lang.tr("sc.drillgui.tab") : Lang.tr("sc.armorhud.piece." + type);
            GuiButton tab = new GuiButton(TAB_BASE + type, tx, top + 14, tabW, 18, label);
            tab.enabled = type != selectedPiece;                // the pressed-in one is the open tab
            buttonList.add(tab);
            tx += tabW + 2;
        }
        int left = width / 2 - (NAME_W + BIND_W + 4) / 2;
        int y = top + 40;
        if (selectedPiece == DRILL_TAB) {
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
            buttonList.add(new GuiButton(CHIPS_ID, width / 2 - 90, y + 4, 180, 18, Lang.tr("sc.armorgui.chips.remove")));
            y += 22;
        }
        if (ArmorLogicSC.piece(mc.thePlayer, 1) != null && selectedPiece != BLADE_TAB && selectedPiece != MODE_TAB && selectedPiece != DRILL_TAB) {
            buttonList.add(new GuiButton(MODE_ID, width / 2 - 90, Math.max(y + 6, top + 190), 180, 18, ""));
        }
        refresh();
    }

    private void addRow(Enum<?> f, int left, int y) {
        buttonList.add(new GuiButton(switchId(f), left, y, NAME_W, 18, ""));
        buttonList.add(new GuiButton(BIND_BASE + switchId(f), left + NAME_W + 4, y, BIND_W, 18, ""));
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
            } else if (b.id >= TAB_BASE && b.id < BIND_BASE) {
                continue;
            } else if (b.id >= BIND_BASE) {
                Enum<?> f = featureOf(b.id);
                b.displayString = f == capturing ? "§e> ... <" : ArmorKeyBindsSC.describe(ArmorKeyBindsSC.get(f));
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
        }
        refresh();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.title"), width / 2, top, 0xFFFFFF);
        if (selectedPiece < 0) {
            drawCenteredString(fontRendererObj, Lang.tr("sc.armorgui.none"), width / 2, height / 2, 0xA0A0A0);
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
