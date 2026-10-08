package com.sc.inventory;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.Tier;
import com.sc.item.ItemEntangledCrystalSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityWirelessSC;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * The transmitter's, the receiver's and the quantum translator's screen (GuiBigSC's layout): the
 * link card on the left (who's at the other end, where, how far, the loss), a scene on the right
 * (a dish sending or receiving waves; for the pair two worlds and their nodes), the flow row, the
 * buttons; the power switch, the gauge and (sending ends) the battery slot on the right.
 */
public class GuiWirelessSC extends GuiContainer {

    private final TileEntityWirelessSC te;
    private GuiPowerSC power;
    private GuiBatterySlotSC battery;
    private static final int CARD_X = 14, CARD_Y = 34, SCENE_X = 104, SCENE_Y = 34, SCENE_W = 102, SCENE_H = 44;

    public GuiWirelessSC(InventoryPlayer inv, TileEntityWirelessSC te) {
        super(new ContainerWirelessSC(inv, te));
        this.te = te;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    private boolean quantum() {
        return te.getKind() == TileEntityWirelessSC.QUANTUM;
    }

    /** The tier the link works at (range, loss): the lower of the two ends; unlinked, this block's own. */
    private Tier linkTier() {
        Tier own = te.getTier();
        int pt = te.partnerPos()[4];
        if (quantum() || !te.hasLink() || pt < 0) {
            return own;
        }
        Tier other = Tier.byOrdinal(pt);
        return other.ordinal() < own.ordinal() ? other : own;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(te, ContainerWirelessSC.BTN_POWER, ContainerWirelessSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
        battery = te.getKind() == TileEntityWirelessSC.RECEIVER ? null
                : new GuiBatterySlotSC(te, ContainerWirelessSC.BTN_BATTERY_MODE, GuiBigSC.GAUGE_X, GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H);
        if (battery != null) {
            battery.addButton(buttonList, guiLeft, guiTop);
        }
        int x = guiLeft, y = guiTop;
        buttonList.add(new GuiFieldGeneratorSC.HoloButton(quantum() ? ContainerWirelessSC.BTN_PAUSE : ContainerWirelessSC.BTN_UNLINK,
                x + 104, y + 104, 58, 9, ""));
        if (te.getKind() != TileEntityWirelessSC.RECEIVER) {
            buttonList.add(new GuiFieldGeneratorSC.HoloButton(quantum() ? ContainerWirelessSC.BTN_ROLE : ContainerWirelessSC.BTN_BEAM,
                    x + 164, y + 104, 42, 9, ""));
        }
        refresh();
    }

    private void refresh() {
        boolean may = te.allowed(mc.thePlayer);
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == ContainerWirelessSC.BTN_UNLINK) {
                b.displayString = Lang.tr("sc.wl.btn.unlink");
                b.enabled = may && te.hasLink();
            } else if (b.id == ContainerWirelessSC.BTN_PAUSE) {
                b.displayString = Lang.tr(te.isPaused() ? "sc.wl.btn.resume" : "sc.wl.btn.pause");
                b.enabled = may;
            } else if (b.id == ContainerWirelessSC.BTN_BEAM) {
                b.displayString = (te.isBeam() ? "§a" : "§7") + Lang.tr("sc.wl.btn.beam");
                b.enabled = may;
            } else if (b.id == ContainerWirelessSC.BTN_ROLE) {
                b.displayString = Lang.tr(te.isGiving() ? "sc.wl.btn.give" : "sc.wl.btn.take");
                b.enabled = may;
            } else if (b.id == ContainerWirelessSC.BTN_POWER || b.id == ContainerWirelessSC.BTN_REDSTONE
                    || b.id == ContainerWirelessSC.BTN_BATTERY_MODE) {
                b.enabled = may;                                     // as the quarry's and the field's: the server refuses a stranger anyway
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        refresh();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    // ------------------------------------------------------------------ background

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        GuiBigSC.window(x, y, false, 0);
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        rect(x + CARD_X, y + CARD_Y, 86, 17, 0xFF2A6A8A);
        rect(x + CARD_X + 1, y + CARD_Y + 1, 84, 15, 0xFF0E3A50);
        rect(x + SCENE_X, y + SCENE_Y, SCENE_W, SCENE_H, 0xFF1E3444);
        rect(x + SCENE_X + 1, y + SCENE_Y + 1, SCENE_W - 2, SCENE_H - 2, 0xFF0A1218);
        if (quantum()) {
            quantumScene(x + SCENE_X, y + SCENE_Y, t);
            GuiHoloSC.slot(x + ContainerWirelessSC.CRYSTAL_X, y + ContainerWirelessSC.CRYSTAL_Y,
                    te.getStackInSlot(TileEntityWirelessSC.SLOT_CRYSTAL) != null);
        } else {
            dishScene(x + SCENE_X, y + SCENE_Y, t);
        }
        rect(x + 12, y + 84, 194, 1, 0xFF1E3444);
        if (te.getKind() == TileEntityWirelessSC.TRANSMITTER && te.getStatus() != TileEntityWirelessSC.ST_NO_LINK) {
            int range = TileEntityWirelessSC.range(linkTier());
            if (range != Integer.MAX_VALUE) {                        // how much of the range the link uses
                GuiHoloSC.bar(x + 14, y + 76, 86, 3, Math.min(1F, te.getDistance() / (float) range), 16, 0xFF6EE6FF);
            }
        }
        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H,
                (float) te.getEnergyStored() / Math.max(1, te.getMaxEnergyStored()));
        if (battery != null) {
            battery.draw(x, y, te.getStackInSlot(TileEntityWirelessSC.SLOT_BATTERY));
        }
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static void rect(int x, int y, int w, int h, int c) {
        drawRect(x, y, x + w, y + h, c);
    }

    private boolean moving() {
        return te.getStatus() == TileEntityWirelessSC.ST_OK;
    }

    /** A mast with a dish; waves go out (transmitter) or come in (receiver) while energy moves. */
    private void dishScene(int x, int y, float t) {
        rect(x + 1, y + 38, SCENE_W - 2, 5, 0xFF2A3A2A);
        rect(x + 1, y + 38, SCENE_W - 2, 1, 0xFF4A8A3A);
        boolean rx = te.getKind() == TileEntityWirelessSC.RECEIVER;
        int mx = rx ? x + 78 : x + 6;
        rect(mx + 7, y + 16, 3, 22, 0xFF6A707A);
        rect(mx + 2, y + 35, 13, 3, 0xFF4A505A);
        for (int k = 0; k < 8; k++) {
            int dx = (int) Math.round(4 * Math.sin(k / 7.0 * Math.PI));
            rect(rx ? mx + 7 + dx : mx + 7 - dx, y + 4 + k * 2, 3, 2, 0xFFB0B8C4);
        }
        rect(rx ? mx + 2 : mx + 14, y + 11, 3, 3, moving() ? 0xFF6EE6FF : 0xFF3A4450);
        if (!moving()) {
            return;
        }
        int x0 = rx ? x + 4 : x + 24, x1 = rx ? x + 76 : x + SCENE_W - 4;
        for (int k = 0; k < 5; k++) {
            float f = (t * 0.03F + k / 5F) % 1F;
            if (rx) {
                f = 1F - f;
            }
            int wx = (int) (x0 + (x1 - x0) * f);
            int a = (int) (255 * (1 - Math.abs(f - 0.5F) * 1.6F));
            for (int dy = -6; dy <= 6; dy++) {
                int dx = (int) Math.round(2.5 * (1 - (dy / 6.0) * (dy / 6.0)));
                rect(rx ? wx - dx : wx + dx, y + 13 + dy, 1, 1, (Math.max(0, Math.min(255, a)) << 24) | 0x6EE6FF);
            }
        }
    }

    /** Two worlds side by side, a node in each; entangled particles over the divide while the pair is up. */
    private void quantumScene(int x, int y, float t) {
        int[] here = dimColors(mc.theWorld == null ? 0 : mc.theWorld.provider.dimensionId);
        int[] there = dimColors(te.partnerPos()[3]);
        rect(x + 1, y + 1, 49, SCENE_H - 2, here[0]);
        rect(x + 51, y + 1, 50, SCENE_H - 2, there[0]);
        rect(x + 1, y + SCENE_H - 7, 49, 6, here[1]);
        rect(x + 51, y + SCENE_H - 7, 50, 6, there[1]);
        boolean up = moving() || te.getStatus() == TileEntityWirelessSC.ST_IDLE || te.getStatus() == TileEntityWirelessSC.ST_FULL;
        ring(x + 8, y + 6, t, 0xFFB050FF, 0xFF6EE6FF);
        ring(x + 72, y + 6, up ? t : 0, up ? 0xFFB050FF : 0xFF4A3A5A, up ? 0xFFFF7A50 : 0xFF3A3040);
        if (up) {
            for (int k = 0; k < 9; k++) {
                int dy = (int) (8 * Math.sin(t * 0.08 + k * 0.7));
                rect(x + 33 + k * 4, y + 18 + dy, 2, 2, 0xFFE05AF0);
            }
        } else {
            for (int k = 0; k < 4; k++) {
                rect(x + 33 + k * 4, y + 18, 2, 2, 0xFF5A3A6A);
            }
            rect(x + 50, y + 14, 2, 10, 0xFFFF5A50);
            rect(x + 47, y + 18, 8, 2, 0xFFFF5A50);
        }
    }

    private void ring(int x, int y, float t, int rim, int core) {
        for (int k = 0; k < 40; k++) {
            double a = k / 40.0 * 2 * Math.PI;
            rect((int) (x + 10 + 9 * Math.cos(a)), (int) (y + 12 + 11 * Math.sin(a)), 2, 2, rim);
        }
        for (int k = 0; k < 20; k++) {
            double a = k / 20.0 * 2 * Math.PI + t * 0.05;
            double r = 2.5 + (k % 4) * 1.4;
            rect((int) (x + 10.5 + r * Math.cos(a)), (int) (y + 12.5 + r * Math.sin(a)), 1, 1, core);
        }
        rect(x + 10, y + 12, 2, 2, 0xFFFFFFFF);
    }

    /** Sky and ground colours of a dimension: the Overworld, the Nether, the End, anything else. */
    private static int[] dimColors(int dim) {
        switch (dim) {
            case -1: return new int[]{0xFF2A0E0E, 0xFF6A2020};
            case 1: return new int[]{0xFF14141E, 0xFFB8B884};
            case 0: return new int[]{0xFF1A2A1A, 0xFF3A6A2A};
            default: return new int[]{0xFF141C2A, 0xFF3A4A6A};
        }
    }

    // ------------------------------------------------------------------ texts

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private void small(String text, int x, int y, int maxW, int color) {
        float k = Math.min(0.625F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static String dimName(int dim) {
        return Lang.trOr("sc.wl.dim." + dim, Lang.tr("sc.wl.dim.other", dim));
    }

    private int statusColor(int st) {
        switch (st) {
            case TileEntityWirelessSC.ST_OK: return GuiHoloSC.OK;
            case TileEntityWirelessSC.ST_IDLE:
            case TileEntityWirelessSC.ST_OFF:
            case TileEntityWirelessSC.ST_PAUSED:
            case TileEntityWirelessSC.ST_NO_LINK:
            case TileEntityWirelessSC.ST_NO_CRYSTAL: return GuiHoloSC.IDLE;
            case TileEntityWirelessSC.ST_SPENT:
            case TileEntityWirelessSC.ST_TOO_FAR:
            case TileEntityWirelessSC.ST_OTHER_DIM: return GuiHoloSC.BAD;
            default: return GuiHoloSC.WARN;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String key = te.getKind() == TileEntityWirelessSC.TRANSMITTER ? "wirelessTx." + te.getTier().name().toLowerCase(java.util.Locale.ROOT)
                : te.getKind() == TileEntityWirelessSC.RECEIVER ? "wirelessRx." + te.getTier().name().toLowerCase(java.util.Locale.ROOT)
                : "quantumTranslator";
        fit(Lang.tr("tile.siliconage." + key + ".name"), 8, 5, GuiBigSC.titleRoom(fontRendererObj, te.getTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, te.getTier(), GuiBigSC.W - 6, 3);
        fontRendererObj.drawString(Lang.tr("container.inventory"), GuiBigSC.INV_X, GuiBigSC.INV_Y - 10, 0x404040);
        int cap = GuiHoloSC.CYAN & 0xFFFFFF, c = GuiHoloSC.VALUE, dim = GuiHoloSC.LABEL, st = te.getStatus();
        String caption = Lang.tr(quantum() ? "sc.wl.cap.q" : te.getKind() == TileEntityWirelessSC.TRANSMITTER ? "sc.wl.cap.tx" : "sc.wl.cap.rx");
        fontRendererObj.drawString(caption, 14, 25, cap);
        int cw = fontRendererObj.getStringWidth(caption);
        small(Lang.tr("sc.wl.status." + st), 18 + cw, 26, 206 - 18 - cw - 2, statusColor(st));
        int[] p = te.partnerPos();
        if (quantum()) {
            quantumTexts(p, c, dim);
        } else {
            linkTexts(p, c, dim);
        }
        power.drawGaugeOff(fontRendererObj);
        small(Lang.tr(quantum() ? "sc.wl.row.q" : "sc.wl.row.a", TileEntityWirelessSC.blocksPerPercent(linkTier())), 8, 124, 200, 0x505864);
    }

    private void linkTexts(int[] p, int c, int dim) {
        boolean tx = te.getKind() == TileEntityWirelessSC.TRANSMITTER, linked = te.hasLink();
        small(Lang.tr(tx ? "sc.wl.link" : "sc.wl.source"), CARD_X + 3, CARD_Y + 2, 80, dim);
        String who = !linked ? "-" : Lang.tr(tx ? "sc.wl.rxname" : "sc.wl.txname", p[4] >= 0 ? Tier.byOrdinal(p[4]).name() : "?");
        fit(who, CARD_X + 3, CARD_Y + 8, 80, linked ? c : GuiHoloSC.IDLE);
        if (!linked) {
            small(Lang.tr("sc.wl.hint.1"), 14, 54, 86, c);
            small(Lang.tr("sc.wl.hint.2"), 14, 60, 86, c);
            small(Lang.tr("sc.wl.hint.3"), 14, 66, 86, c);
            int range = TileEntityWirelessSC.range(te.getTier());
            small(range == Integer.MAX_VALUE ? Lang.tr("sc.wl.rangeall") : Lang.tr("sc.wl.range", te.getTier().name(), range), 14, 74, 86, dim);
            small(Lang.tr(tx ? "sc.wl.nolink.tx" : "sc.wl.nolink.rx"), 14, 89, 190, GuiHoloSC.IDLE);
            return;
        }
        small(Lang.tr("sc.wl.coords", p[0], p[1], p[2]), 14, 54, 86, dim);
        int range = TileEntityWirelessSC.range(linkTier());
        small(range == Integer.MAX_VALUE ? Lang.tr("sc.wl.distinf", te.getDistance())
                : Lang.tr("sc.wl.dist", te.getDistance(), range), 14, 62, 86, c);
        small(Lang.tr("sc.wl.loss", te.getLossPct()), 14, 69, 86, GuiHoloSC.WARN);
        int flow = te.getFlow(), keep = 100 - te.getLossPct();
        String[][] cols = tx
                ? new String[][]{{"sc.wl.flow.air", flow + " EU/t"}, {"sc.wl.flow.lost", "-" + (flow - flow * keep / 100) + " EU/t"},
                        {"sc.wl.flow.arrive", flow * keep / 100 + " EU/t"}}
                : new String[][]{{"sc.wl.flow.recv", flow + " EU/t"}, {"sc.wl.flow.loss", te.getLossPct() + "%"},
                        {"sc.wl.flow.out", Lang.tr("sc.wl.face")}};
        int[] colors = {GuiHoloSC.CYAN & 0xFFFFFF, GuiHoloSC.WARN, GuiHoloSC.OK};
        for (int i = 0; i < 3; i++) {
            small(Lang.tr(cols[i][0]), 14 + i * 42, 88, 40, dim);
            fit(cols[i][1], 14 + i * 42, 94, 40, colors[i]);
        }
    }

    private void quantumTexts(int[] p, int c, int dim) {
        ItemStack crystal = te.getStackInSlot(TileEntityWirelessSC.SLOT_CRYSTAL);
        long pair = ItemEntangledCrystalSC.pairOf(crystal);
        small(Lang.tr("sc.wl.crystal"), CARD_X + 3, CARD_Y + 2, 80, dim);
        fit(pair == 0 ? "-" : Lang.tr("sc.wl.pairno", ItemEntangledCrystalSC.pairName(pair)), CARD_X + 3, CARD_Y + 8, 80, pair == 0 ? GuiHoloSC.IDLE : c);
        int st = te.getStatus();
        if (pair == 0) {
            small(Lang.tr("sc.wl.qhint.1"), 14, 54, 86, c);
            small(Lang.tr("sc.wl.qhint.2"), 14, 60, 86, c);
            small(Lang.tr("sc.wl.qhint.3"), 14, 66, 86, c);
        } else if (st == TileEntityWirelessSC.ST_PAIR_MISSING) {
            small(Lang.tr("sc.wl.missing.1"), 14, 54, 86, GuiHoloSC.WARN);
            small(Lang.tr("sc.wl.missing.2"), 14, 60, 86, GuiHoloSC.WARN);
        } else {
            small(Lang.tr("sc.wl.other", dimName(p[3])), 14, 54, 86, dim);
            small(Lang.tr("sc.wl.coords", p[0], p[1], p[2]), 14, 60, 86, dim);
            small(Lang.tr(te.isGiving() ? "sc.wl.gives" : "sc.wl.takes", te.getFlow()), 14, 68, 86, GuiHoloSC.OK);
        }
        small(Lang.tr("sc.wl.noloss"), 14, 76, 86, dim);
        int life = crystal == null ? -1 : (int) Math.ceil(ItemEntangledCrystalSC.lifeOf(crystal) * 100.0 / ItemEntangledCrystalSC.LIFE_MAX);
        String[][] cols = {{"sc.wl.upkeep", te.isGiving() ? TileEntityWirelessSC.quantumUpkeep() + " EU/t" : "-"},
                {"sc.wl.chunk", pair == 0 || st == TileEntityWirelessSC.ST_PAUSED || st == TileEntityWirelessSC.ST_OFF ? "-" : Lang.tr("sc.wl.chunk.held")},
                {"sc.wl.crystal", life < 0 ? "-" : life + "%"}};
        int[] colors = {GuiHoloSC.WARN, c, life >= 0 && life <= 20 ? GuiHoloSC.BAD : GuiHoloSC.CYAN & 0xFFFFFF};
        for (int i = 0; i < 3; i++) {
            small(Lang.tr(cols[i][0]), 14 + i * 42, 88, 40, dim);
            fit(cols[i][1], 14 + i * 42, 94, 40, colors[i]);
        }
        small(Lang.tr("sc.wl.halfslot"), 150, 91, 34, dim);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tip = power.tooltip(mouseX - guiLeft, mouseY - guiTop);
        if (tip == null && battery != null) {
            tip = battery.tooltip(mouseX - guiLeft, mouseY - guiTop, te.getStackInSlot(TileEntityWirelessSC.SLOT_BATTERY));
        }
        if (tip == null && GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H,
                mouseX - guiLeft, mouseY - guiTop)) {
            tip = new java.util.ArrayList<String>();
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(te.getEnergyStored() + " / " + te.getMaxEnergyStored() + " EU");
        }
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }
}
