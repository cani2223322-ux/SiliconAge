package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * The Armour Service Station's screen (its own wide layout, ContainerArmorStationSC.W x H): one
 * holo screen with the four armour slots down the left - each with its name and a thin bar per gas
 * that piece holds - the station's eight tanks in the middle (one per gas, singular matter the 8th) (the machines' tank gauge, the gas's
 * short name over it with the x that pours it out, the "fill with this gas" box under it), then
 * "filled / still to go" and the status; right of the screen the power switch, the redstone mode,
 * a tall energy gauge and the Fill / Helium only switches; under it the module row with the
 * input, speed and the tanks' extra room, and the player's inventory.
 */
public class GuiArmorStationSC extends GuiContainer {

    private static final int GASES = Gas.values().length;
    private static final int W = ContainerArmorStationSC.W, H = ContainerArmorStationSC.H;
    /** The holo screen. */
    private static final int SCREEN_X = 8, SCREEN_Y = 22, SCREEN_W = 344, SCREEN_H = 140, SCREEN_RIGHT = SCREEN_X + SCREEN_W - 3;
    /** The armour column: slot, then the piece's name and its gas bars. */
    private static final int PART_TEXT_X = 36, PART_TEXT_W = 50, DIVIDER_X = 89;
    /** The tanks: gauges TANK_STEP apart, the short name and x over them, the check box under them. */
    private static final int TANK_X = 94, TANK_STEP = 32, HEAD_Y = 25, NAME_Y = 36, TANK_Y = 46, CHECK_Y = 120, CHECK = 9,
            SUM_Y = 133, STATUS_Y = 145, TEXT_W = SCREEN_RIGHT - TANK_X;
    /** Right of the screen: power + redstone over the energy gauge, the two switches under it. */
    private static final int GAUGE_X = 361, GAUGE_W = 30, POWER_Y = 22, GAUGE_Y = 36, GAUGE_H = 106, BTN_X = 355, BTN_W = 42,
            FILL_Y = 146, HELIUM_Y = 158, BTN_H = 10;
    /** Under the screen: the module row's caption and text, the separator. */
    private static final int UPG_X = ContainerArmorStationSC.UPG_X, UPG_Y = ContainerArmorStationSC.UPG_Y, UPG_LABEL_X = 8,
            UPG_TEXT_X = UPG_X + 4 * 18 + 4, UPG_TEXT_W = W - 8 - UPG_TEXT_X, SEPARATOR_Y = 168;

    private static final int PANEL = 0xFFB9C1CC, TITLE_BAR = 0xFF2E3642;

    private final TileEntityArmorStationSC te;
    private GuiPowerSC power;
    private final GuiBigSC.ClearButton[] clear = new GuiBigSC.ClearButton[GASES];

    public GuiArmorStationSC(InventoryPlayer inv, TileEntityArmorStationSC te) {
        super(new ContainerArmorStationSC(inv, te));
        this.te = te;
        xSize = W;
        ySize = H;
    }

    private static int tankX(Gas g) {
        return TANK_X + g.ordinal() * TANK_STEP;
    }

    private static int partY(int piece) {
        return ContainerArmorStationSC.PIECE_Y + piece * ContainerArmorStationSC.PIECE_STEP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(te, ContainerArmorStationSC.BTN_POWER, ContainerArmorStationSC.BTN_REDSTONE,
                GAUGE_X, POWER_Y, GAUGE_W, GAUGE_Y, GAUGE_H);
        power.addButtons(buttonList, guiLeft, guiTop);
        for (Gas g : Gas.values()) {
            buttonList.add(new CheckButton(ContainerArmorStationSC.BTN_GAS + g.ordinal(), guiLeft + tankX(g) + 7, guiTop + CHECK_Y, g));
            GuiBigSC.ClearButton b = new GuiBigSC.ClearButton(ContainerArmorStationSC.BTN_CLEAR + g.ordinal());
            b.visible = true;
            b.xPosition = guiLeft + tankX(g) + GuiTankGaugeSC.WIDTH - 7;
            b.yPosition = guiTop + NAME_Y;
            clear[g.ordinal()] = b;
            buttonList.add(b);
        }
        buttonList.add(new HoloButton(ContainerArmorStationSC.BTN_FILL, guiLeft + BTN_X, guiTop + FILL_Y, BTN_W, BTN_H));
        buttonList.add(new HoloButton(ContainerArmorStationSC.BTN_HELIUM, guiLeft + BTN_X, guiTop + HELIUM_Y, BTN_W, BTN_H));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        window(x, y);
        GuiHoloSC.screen(x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        for (int i = 0; i < TileEntityArmorStationSC.SLOTS; i++) {
            GuiHoloSC.slot(x + ContainerArmorStationSC.PIECE_X, y + partY(i), te.getStackInSlot(i) != null && te.isActive());
            pieceBars(te.getStackInSlot(i), x + PART_TEXT_X, y + partY(i) + 9);
        }
        rect(x + DIVIDER_X, y + SCREEN_Y + 4, 1, SCREEN_H - 8, GuiHoloSC.CYAN_DIM);
        int cap = te.tankCapacity();
        for (Gas g : Gas.values()) {
            GuiTankGaugeSC.draw(mc, x + tankX(g), y + TANK_Y, te.getTank(g).getFluid(), cap, g.fluid, false);
            if (!te.isFillGases() || !te.gasEnabled(g)) {
                rect(x + tankX(g), y + TANK_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT - 8, 0x70060A10);   // not filled with: dimmed
            }
        }
        GuiEnergyGaugeSC.draw(x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, (float) te.getEnergyStored() / Math.max(1, te.getMaxEnergyStored()));
        GuiHoloSC.glint(x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The station's own steel window: border, inset title bar, the separator under the screen, the pockets. */
    private static void window(int x, int y) {
        rect(x, y, W, H, 0xFF1E2024);
        rect(x + 1, y + 1, W - 2, H - 2, PANEL);
        rect(x + 1, y + 1, W - 3, 1, 0xFFECF0F6);
        rect(x + 1, y + 1, 1, H - 3, 0xFFE2E8F0);
        rect(x + 2, y + H - 2, W - 3, 1, 0xFF6E747E);
        rect(x + W - 2, y + 2, 1, H - 3, 0xFF767C86);
        rect(x + 3, y + 3, W - 6, 1, 0xFF6E747E);
        rect(x + 3, y + 3, 1, 13, 0xFF6E747E);
        rect(x + 4, y + 4, W - 8, 11, TITLE_BAR);
        rect(x + 4, y + 15, W - 8, 1, 0xFFF0F2F6);
        rect(x + 7, y + SEPARATOR_Y, W - 14, 1, 0xFF5A606A);
        rect(x + 7, y + SEPARATOR_Y + 1, W - 14, 1, 0xFFF0F2F6);
        for (int i = 0; i < TileEntityArmorStationSC.UPGRADE_SLOTS; i++) {
            GuiBigSC.pocket(x + UPG_X + i * 18, y + UPG_Y);
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                GuiBigSC.pocket(x + ContainerArmorStationSC.INV_X + c * 18, y + ContainerArmorStationSC.INV_Y + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            GuiBigSC.pocket(x + ContainerArmorStationSC.INV_X + c * 18, y + ContainerArmorStationSC.HOTBAR_Y);
        }
    }

    /** The gases a piece has tanks for (ArmorGasSC.capacity > 0), in Gas order. */
    private static List<Gas> gasesOf(ItemStack piece) {
        List<Gas> out = new ArrayList<Gas>();
        if (piece != null) {
            for (Gas g : Gas.values()) {
                if (ArmorGasSC.capacity(piece, g) > 0) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** A thin bar per gas the piece holds, in the gas's colour, filled to amount / capacity. */
    private static void pieceBars(ItemStack piece, int x, int y) {
        List<Gas> gases = gasesOf(piece);
        if (gases.isEmpty()) {
            return;
        }
        int step = gases.size() <= 5 ? 4 : Math.max(2, 20 / gases.size()), h = Math.max(1, step - 1);
        for (int k = 0; k < gases.size(); k++) {
            Gas g = gases.get(k);
            int cap = ArmorGasSC.capacity(piece, g), a = Math.min(cap, ArmorGasSC.amount(piece, g));
            int by = y + k * step;
            rect(x, by, PART_TEXT_W - 2, h, 0xFF14202C);
            rect(x, by, Math.round((PART_TEXT_W - 2) * (float) a / Math.max(1, cap)), h, 0xFF000000 | g.color);
        }
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            drawRect(x, y, x + w, y + h, c);
        }
    }

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    static String gasName(Gas g) {
        return Lang.tr("sc.armorStation.gas." + g.key());
    }

    private int statusColor(int st) {
        switch (st) {
            case TileEntityArmorStationSC.ST_WORKING: return GuiHoloSC.OK;
            case TileEntityArmorStationSC.ST_FULL:
            case TileEntityArmorStationSC.ST_IDLE:
            case TileEntityArmorStationSC.ST_OFF: return GuiHoloSC.IDLE;
            default: return GuiHoloSC.WARN;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String tier = te.getTier().name();
        fit(Lang.tr("tile.siliconage.armorStation.name"), 8, 5, W - 6 - (fontRendererObj.getStringWidth(tier) + 4) - 4 - 8, GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, te.getTier(), W - 6, 3);
        fontRendererObj.drawString(Lang.tr("sc.gui.big.upgrades"), UPG_LABEL_X, UPG_Y + 4, 0x404040);
        fontRendererObj.drawString(Lang.tr("container.inventory"), ContainerArmorStationSC.INV_X, ContainerArmorStationSC.INV_Y - 10, 0x404040);
        drawModuleLine();
        for (int i = 0; i < TileEntityArmorStationSC.SLOTS; i++) {
            fit(Lang.tr("sc.armorhud.piece." + i), PART_TEXT_X, partY(i) - 1, PART_TEXT_W, te.getStackInSlot(i) != null ? GuiHoloSC.VALUE : GuiHoloSC.IDLE);
        }
        fit(Lang.tr("sc.armorStation.tanks.head", te.tankCapacity()), TANK_X, HEAD_Y, TEXT_W, GuiHoloSC.LABEL);
        for (Gas g : Gas.values()) {
            boolean on = te.isFillGases() && te.gasEnabled(g);
            TextFitSC.drawCentered(fontRendererObj, com.sc.client.GasUiSC.shortName(g), tankX(g), NAME_Y, GuiTankGaugeSC.WIDTH - 7,
                    on ? g.color : 0x4A5A6A, false, guiLeft, guiTop);
        }
        long have = 0, room = 0;
        for (Gas g : Gas.values()) {
            if (te.isFillGases() && te.gasEnabled(g)) {
                have += te.shownAmount(g);
                room += Math.max(0, te.shownCapacity(g) - te.shownAmount(g));
            }
        }
        fit(Lang.tr("sc.armorStation.sum", String.valueOf(have), String.valueOf(room)), TANK_X, SUM_Y, TEXT_W,
                room > 0 ? GuiHoloSC.LABEL : GuiHoloSC.OK);
        int st = te.getStatus();
        String status = Lang.tr("sc.armorStation.status.label", Lang.tr("sc.armorStation.status." + st));
        if (te.getPlayers() > 0) {
            status += ", " + Lang.tr("sc.armorStation.onpad", te.getPlayers());
        }
        fit(status, TANK_X, STATUS_Y, TEXT_W, statusColor(st));
        power.drawGaugeOff(fontRendererObj);
    }

    private static String oneDecimal(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Beside the module row: the input tier, the speed and the tanks' extra room, then "modules N of 4". */
    private void drawModuleLine() {
        String in = te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.any") : te.inputTier().name();
        fit(Lang.tr("sc.armorStation.modules.line2", in, oneDecimal(te.speedFactor()), te.tankCapacity() - TileEntityArmorStationSC.TANK_CAPACITY),
                UPG_TEXT_X, UPG_Y, UPG_TEXT_W, 0x505864);
        fit(Lang.tr("sc.gui.big.upgrades.count", te.modulesUsed(), TileEntityArmorStationSC.UPGRADE_SLOTS),
                UPG_TEXT_X, UPG_Y + 9, UPG_TEXT_W, 0x808894);
    }

    /** The module row's tooltip: what goes in, and what the modules give now. */
    private List<String> moduleTip() {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.gui.upgrades"));
        tip.add(Lang.tr("sc.armorStation.modules.hint"));
        tip.add(te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.anyinput")
                : Lang.tr("sc.gui.input", te.inputTier().name(), te.inputTier().getVoltage()));
        tip.add(Lang.tr("sc.armorStation.modules.gas", te.gasPerTick(), oneDecimal(te.affordableGas(1000) / 1000.0)));
        tip.add(Lang.tr("sc.armorStation.modules.charge", te.chargePerRound() / TileEntityArmorStationSC.EVERY));
        tip.add(Lang.tr("sc.armorStation.modules.tanks", te.tankCapacity(), te.tankCapacity() - TileEntityArmorStationSC.TANK_CAPACITY));
        return tip;
    }

    /** A piece's tooltip: its name and what each of its gas tanks holds. */
    private List<String> pieceTip(int i) {
        List<String> tip = new ArrayList<String>();
        ItemStack piece = te.getStackInSlot(i);
        tip.add(Lang.tr("sc.armorhud.piece." + i));
        if (piece == null) {
            tip.add("§7" + Lang.tr("sc.armorStation.part.empty"));
            return tip;
        }
        tip.add("§7" + piece.getDisplayName());
        for (Gas g : gasesOf(piece)) {
            tip.add(Lang.tr("sc.armorStation.part.gas", gasName(g), ArmorGasSC.amount(piece, g), ArmorGasSC.capacity(piece, g)));
        }
        return tip;
    }

    /** A tank's tooltip: the gas's full name, mB / capacity, filled with or not, what x would cost. */
    private List<String> tankTip(Gas g) {
        List<String> tip = new ArrayList<String>();
        int a = te.tankAmount(g), cap = te.tankCapacity();
        tip.add(gasName(g));
        tip.add(Lang.tr("sc.armorStation.tank.amount", a, cap));
        if (a > cap) {
            tip.add("§6" + Lang.tr("sc.armorStation.tank.over"));
        }
        tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.tank.on" : "sc.armorStation.tank.off"));
        if (a > 0) {
            tip.add("§7" + Lang.tr("sc.armorStation.tank.clearhint", te.clearCost(g)));
        }
        return tip;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (Gas g : Gas.values()) {
            clear[g.ordinal()].enabled = te.tankAmount(g) > 0 && te.getEnergyStored() >= te.clearCost(g);
        }
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = power.tooltip(mx, my);
        if (tip == null && GuiGaugeSC.isOver(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mx, my)) {
            tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(te.getEnergyStored() + " / " + te.getMaxEnergyStored() + " EU");
        }
        for (Gas g : Gas.values()) {
            if (tip != null) {
                break;
            }
            if (clear[g.ordinal()].over(mouseX, mouseY)) {
                tip = GuiBigSC.clearTip(te.tankAmount(g), te.clearCost(g), te.getEnergyStored());
            } else if (GuiGaugeSC.isOver(tankX(g) + 7, CHECK_Y, CHECK, CHECK, mx, my)) {
                tip = new ArrayList<String>();
                tip.add(gasName(g));
                tip.add(Lang.tr("sc.armorStation.gasuse." + g.key()));
                tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.gas.on" : "sc.armorStation.gas.off"));
            } else if (GuiGaugeSC.isOver(tankX(g), NAME_Y - 1, GuiTankGaugeSC.WIDTH, TANK_Y - NAME_Y + GuiTankGaugeSC.HEIGHT, mx, my)) {
                tip = tankTip(g);
            }
        }
        for (int i = 0; i < TileEntityArmorStationSC.SLOTS && tip == null; i++) {
            if (GuiGaugeSC.isOver(PART_TEXT_X - 2, partY(i) - 2, PART_TEXT_W + 2, 30, mx, my)) {
                tip = pieceTip(i);
            }
        }
        if (tip == null && GuiGaugeSC.isOver(BTN_X, FILL_Y, BTN_W, BTN_H, mx, my)) {
            tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.armorStation.fill") + ": " + Lang.tr(te.isFillGases() ? "sc.armorStation.on" : "sc.armorStation.off"));
            tip.add(Lang.tr("sc.armorStation.fill.hint"));
        }
        if (tip == null && GuiGaugeSC.isOver(BTN_X, HELIUM_Y, BTN_W, BTN_H, mx, my)) {
            tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.armorStation.helium"));
            tip.add(Lang.tr("sc.armorStation.helium.hint"));
        }
        if (tip == null && GuiGaugeSC.isOver(UPG_LABEL_X, UPG_Y - 1, W - 8 - UPG_LABEL_X, 18, mx, my) && !overUpgradeSlot(mx, my)) {
            tip = moduleTip();
        }
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    private static boolean overUpgradeSlot(int mx, int my) {
        for (int i = 0; i < TileEntityArmorStationSC.UPGRADE_SLOTS; i++) {
            if (GuiGaugeSC.isOver(UPG_X - 1 + i * 18, UPG_Y - 1, 18, 18, mx, my)) {
                return true;
            }
        }
        return false;
    }

    /** A gas's check box under its tank: framed in the gas's colour, a lit square while it's filled with. */
    private class CheckButton extends GuiButton {
        private final Gas gas;

        CheckButton(int id, int x, int y, Gas gas) {
            super(id, x, y, CHECK, CHECK, "");
            this.gas = gas;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            int col = 0xFF000000 | gas.color;
            rect(xPosition, yPosition, CHECK, CHECK, over ? GuiHoloSC.CYAN : col);
            rect(xPosition + 1, yPosition + 1, CHECK - 2, CHECK - 2, 0xFF0A1218);
            if (te.gasEnabled(gas)) {
                rect(xPosition + 2, yPosition + 2, CHECK - 4, CHECK - 4, te.isFillGases() ? col : 0xFF4A5A6A);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A flat holo switch: a short label; lit while it's on (the tooltip says the rest). */
    private class HoloButton extends GuiButton {
        HoloButton(int id, int x, int y, int w, int h) {
            super(id, x, y, w, h, "");
        }

        private boolean lit() {
            return id == ContainerArmorStationSC.BTN_FILL ? te.isFillGases() : te.getGasMask() == 1 << Gas.HELIUM.ordinal();
        }

        private String label() {
            return Lang.tr(id == ContainerArmorStationSC.BTN_FILL ? "sc.armorStation.fill.short" : "sc.armorStation.helium.short");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean lit = lit();
            rect(xPosition, yPosition, width, height, over ? GuiHoloSC.CYAN : lit ? GuiHoloSC.CYAN_MID : 0xFF343C48);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, lit ? 0xFF0E2A36 : 0xFF141C26);
            TextFitSC.drawCentered(mc.fontRenderer, label(), xPosition + 2, yPosition + (height - 8) / 2, width - 4,
                    lit ? GuiHoloSC.VALUE : GuiHoloSC.IDLE, false, 0, 0);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
