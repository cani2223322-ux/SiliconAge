package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * Energy storage (and charge pad) screen, the large holo-screen layout (GuiBigSC, 248 x 232):
 * the charging slot, stored / capacity, the net flow per tick (averaged over a second: +
 * charging, - discharging) and the output voltage on the screen, a wide segmented charge bar
 * along its bottom in the charge colour, the tall energy gauge right of it, the player's
 * inventory below.
 */
public class GuiEnergyStorageSC extends GuiContainer {


    private final TileEntityEnergyStorageSC storage;

    public GuiEnergyStorageSC(InventoryPlayer playerInv, TileEntityEnergyStorageSC storage) {
        super(new ContainerEnergyStorageSC(playerInv, storage));
        this.storage = storage;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    private GuiPowerSC power;

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(storage, ContainerEnergyStorageSC.BTN_POWER, ContainerEnergyStorageSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
    }

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    private float fraction() {
        return (float) storage.getEnergyStored() / Math.max(1, storage.getMaxEnergyStored());
    }

    /** The battery's edge colour by tier: LV grey, MV orange, HV yellow, EV violet, IV teal, QV blue, XV magenta, SV purple. */
    private static final int[] TIER_TINT = {0xFF9AA0AA, 0xFFE0903A, 0xFFE8C040, 0xFFA070E0, 0xFF40C8B0, 0xFF4A80F0, 0xFFC040C0, 0xFFFF3C50};
    private static final int CELL_X = 56, CELL_Y = 34, CELL_W = 30, CELL_H = 72, INFO_X = 90, CARD_W = 112;

    /** How charged an item is, 0..1, from its durability bar (the mod's gear and IC2's items draw theirs that way). */
    private static float itemCharge(ItemStack s) {
        if (s == null || s.getItem() == null || !s.getItem().showDurabilityBar(s)) {
            return s == null ? 0F : 1F;
        }
        return (float) Math.max(0, Math.min(1, 1 - s.getItem().getDurabilityForDisplay(s)));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        GuiBigSC.window(x, y, true, TileEntityEnergyStorageSC.UPGRADE_SLOTS);
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        for (int k = 0; k < storage.chargeSlots(); k++) {                      // the charge column, a bar under each slot
            int sx = x + ContainerEnergyStorageSC.chargeX(k), sy = y + ContainerEnergyStorageSC.chargeY(k);
            ItemStack in = storage.getStackInSlot(TileEntityEnergyStorageSC.chargeSlotIndex(k));
            GuiHoloSC.slot(sx, sy, in != null);
            drawRect(sx, sy + 17, sx + 16, sy + 19, 0xFF04080C);
            if (in != null) {
                drawRect(sx, sy + 17, sx + (int) (16 * itemCharge(in)), sy + 19, 0xFF5AE66E);
            }
        }
        GuiHoloSC.slot(x + ContainerEnergyStorageSC.DIS_X, y + ContainerEnergyStorageSC.DIS_Y,
                storage.getStackInSlot(TileEntityEnergyStorageSC.SLOT_DISCHARGE) != null);
        int flow = storage.getFlowPerTick();
        GuiSceneSC.storageCell(x + CELL_X, y + CELL_Y, CELL_W, CELL_H, t, fraction(),
                TIER_TINT[storage.getTier().ordinal() % TIER_TINT.length], Integer.signum(flow));
        for (int c = 0; c < 2; c++) {                                          // the two cards, each split in two
            int cy = y + (c == 0 ? 53 : 74), cx = x + INFO_X;
            drawRect(cx, cy, cx + CARD_W, cy + 18, 0xFF2A6A8A);
            drawRect(cx + 1, cy + 1, cx + CARD_W - 1, cy + 17, 0xFF0E3A50);
            drawRect(cx + CARD_W / 2, cy + 3, cx + CARD_W / 2 + 1, cy + 15, 0xFF2A6A8A);
        }
        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, fraction());
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** 2480000 -> "2,5 млн", 1240000000 -> "1,24 млрд", smaller ones spaced: "12 000". */
    private static String eu(long n) {
        if (n >= 1000000000L) {
            return Lang.tr("sc.storage.gui.bn", trim(String.format(java.util.Locale.ROOT, "%.2f", n / 1e9)));
        }
        if (n >= 1000000L) {
            return Lang.tr("sc.storage.gui.mn", trim(String.format(java.util.Locale.ROOT, "%.1f", n / 1e6)));
        }
        return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    private static String trim(String v) {
        if (v.indexOf('.') >= 0) {
            v = v.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return v.replace('.', ',');
    }

    /** "59 с", "2 мин", "1 ч 5 мин". */
    private static String time(double seconds) {
        long s = (long) Math.ceil(seconds);
        if (s >= 3600) {
            return Lang.tr("sc.gui.t.hm", s / 3600, s % 3600 / 60);
        }
        return s >= 60 ? Lang.tr("sc.gui.fus.min", s / 60) : Lang.tr("sc.gui.fus.sec", s);
    }

    private void small(String text, int x, int y, int maxW, int color) {
        float k = Math.min(0.625F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(x, y, 0F);
        org.lwjgl.opengl.GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        org.lwjgl.opengl.GL11.glPopMatrix();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = Lang.tr("tile.siliconage." + (storage instanceof com.sc.tileentity.TileEntityChargePadSC ? "chargePad." : "energyStorage.")
                + storage.getTier().name().toLowerCase(java.util.Locale.ROOT) + ".name");
        fit(title, 8, 5, GuiBigSC.titleRoom(fontRendererObj, storage.getTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, storage.getTier(), GuiBigSC.W - 6, 3);
        GuiBigSC.labels(fontRendererObj, Lang.tr("sc.gui.big.upgrades"), Lang.tr("container.inventory"));
        int used = 0;
        for (int i = 0; i < TileEntityEnergyStorageSC.UPGRADE_SLOTS; i++) {
            used += storage.getStackInSlot(TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + i) != null ? 1 : 0;
        }
        fit(Lang.tr("sc.gui.big.upgrades.count", used, TileEntityEnergyStorageSC.UPGRADE_SLOTS), GuiBigSC.UPG_TEXT_X, GuiBigSC.UPG_Y,
                GuiBigSC.UPG_TEXT_W, 0x505864);
        fit(Lang.tr("sc.gui.holo.storage"), 14, 24, 80, GuiHoloSC.CYAN & 0xFFFFFF);
        small(Lang.tr("sc.storage.gui.lbl.discharge"), ContainerEnergyStorageSC.DIS_X - 1, ContainerEnergyStorageSC.DIS_Y - 6, 22, GuiHoloSC.LABEL);
        // the battery's percent
        float f = fraction();
        String pct = Math.round(f * 100) + "%";
        int ty = CELL_Y + 5 + (int) ((CELL_H - 7) * (1 - f)) - 9;
        ty = Math.max(CELL_Y + 6, Math.min(CELL_Y + CELL_H - 10, f > 0.45F ? CELL_Y + CELL_H / 2 - 4 : ty));
        fontRendererObj.drawString(pct, CELL_X + (CELL_W - fontRendererObj.getStringWidth(pct)) / 2, ty,
                f > 0.45F ? 0x0A1420 : GuiHoloSC.VALUE);
        // stored, of the capacity
        long stored = storage.getEnergyStored(), cap = storage.getMaxEnergyStored();
        fit(eu(stored) + " EU", INFO_X + 2, 35, CARD_W, GuiHoloSC.VALUE);
        small(Lang.tr("sc.storage.gui.of", eu(cap)), INFO_X + 2, 45, CARD_W, GuiHoloSC.LABEL);
        // card 1: the flow, and when it's full / empty
        int flow = storage.getFlowPerTick();
        int half = CARD_W / 2, c1 = 53, c2 = 74;
        small(Lang.tr("sc.storage.gui.c.flow"), INFO_X + 3, c1 + 2, half - 4, GuiHoloSC.LABEL);
        fit((flow > 0 ? "+" : "") + flow + " EU/t", INFO_X + 3, c1 + 8, half - 5, flow > 0 ? GuiHoloSC.OK : flow < 0 ? 0xFF8C5A : GuiHoloSC.IDLE);
        String whenLabel, when;
        int whenCol = GuiHoloSC.VALUE;
        if (flow > 0) {
            whenLabel = Lang.tr("sc.storage.gui.c.full");
            when = stored >= cap ? Lang.tr("sc.storage.gui.c.now") : time((cap - stored) / (flow * 20.0));
        } else if (flow < 0) {
            whenLabel = Lang.tr("sc.storage.gui.c.empty");
            when = time(stored / (-flow * 20.0));
            whenCol = GuiHoloSC.WARN;
        } else {
            whenLabel = Lang.tr("sc.storage.gui.c.still");
            when = "-";
            whenCol = GuiHoloSC.IDLE;
        }
        small(whenLabel, INFO_X + half + 3, c1 + 2, half - 4, GuiHoloSC.LABEL);
        fit(when, INFO_X + half + 3, c1 + 8, half - 5, whenCol);
        // card 2: the output, the comparator
        int packets = storage.packetsPerFace(), volt = storage.outputTier().getVoltage();   // per output face
        small(Lang.tr("sc.storage.gui.c.out"), INFO_X + 3, c2 + 2, half - 4, GuiHoloSC.LABEL);
        fit(packets > 1 ? volt + " x " + packets : volt + " EU/t", INFO_X + 3, c2 + 8, half - 5,
                storage.outputTier() != storage.getTier() ? GuiHoloSC.WARN : GuiHoloSC.VALUE);
        small(Lang.tr("sc.storage.gui.c.comp"), INFO_X + half + 3, c2 + 2, half - 4, GuiHoloSC.LABEL);
        fit(Lang.tr("sc.storage.gui.c.level", storage.comparatorLevel()), INFO_X + half + 3, c2 + 8, half - 5, GuiHoloSC.VALUE);
        small(Lang.tr("sc.storage.gui.hint"), INFO_X, 96, CARD_W, GuiHoloSC.LABEL);
        // the storage modules: how many outputs and what they give in all, the adaptive tier and its limit
        int line = 103;
        int faces = storage.outputFaces().length;
        if (storage.outputSplitters() > 0 || faces > 1) {
            small(Lang.tr("sc.storage.gui.outputs", faces, (long) faces * volt * packets), INFO_X, line, CARD_W, GuiHoloSC.VALUE);
            line += 6;
        }
        if (storage.hasAdaptive()) {
            com.sc.energy.Tier a = storage.getAdaptiveTier();
            String tier = a == null ? storage.baseOutputTier().name() : a.name();   // nothing to go by: no raise (Transformers still count)
            small(Lang.tr("sc.storage.gui.adaptive", tier, Lang.tr("sc.storage.gui.adaptive.by." + ADAPT_KEYS[storage.getAdaptiveWhy() & 3])),
                    INFO_X, line, CARD_W, GuiHoloSC.VALUE);
        }
        power.drawGaugeOff(fontRendererObj);
        power.drawWarning(fontRendererObj, INFO_X, 84, CARD_W, guiLeft, guiTop);
    }

    /** TileEntityEnergyStorageSC.ADAPT_NONE / CABLE / CONSUMER / CEILING -> lang key ends. */
    private static final String[] ADAPT_KEYS = {"none", "cable", "consumer", "max"};

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private List<String> tooltipAt(int mx, int my) {
        List<String> powerTip = power.tooltip(mx, my);
        if (powerTip != null) {
            return powerTip;
        }
        List<String> lines = new ArrayList<String>();
        if (GuiGaugeSC.isOver(INFO_X, 74, CARD_W, 18, mx, my) || GuiGaugeSC.isOver(INFO_X, 95, CARD_W, 6, mx, my)) {
            lines.add(Lang.tr("sc.storage.tooltip.io", storage.outputTier().getVoltage()));
            lines.add(Lang.tr("sc.storage.tooltip.wrench"));
            lines.add(Lang.tr("sc.storage.tooltip.comparator"));
            return lines;
        }
        if (GuiGaugeSC.isOver(GuiBigSC.UPG_LABEL_X, GuiBigSC.UPG_Y - 1, GuiBigSC.GAUGE_X - 4 - GuiBigSC.UPG_LABEL_X, 18, mx, my)
                && !GuiBigSC.overUpgradeSlot(mx, my, TileEntityEnergyStorageSC.UPGRADE_SLOTS)) {
            lines.add(Lang.tr("sc.gui.upgrades"));
            lines.add(Lang.tr("sc.storage.upgrades.hint.1"));
            lines.add(Lang.tr("sc.storage.upgrades.hint.2"));
            lines.add(Lang.tr("sc.storage.upgrades.hint.3"));
            lines.add(Lang.tr("sc.storage.upgrades.hint.4"));      // Output Splitter
            lines.add(Lang.tr("sc.storage.upgrades.hint.5"));      // Adaptive Transformer
            return lines;
        }
        if (storage.getStackInSlot(TileEntityEnergyStorageSC.SLOT_DISCHARGE) == null
                && GuiGaugeSC.isOver(ContainerEnergyStorageSC.DIS_X, ContainerEnergyStorageSC.DIS_Y, 16, 16, mx, my)) {
            lines.add(Lang.tr("sc.storage.gui.slot.discharge"));
            return lines;
        }
        if (GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, mx, my)
                || GuiGaugeSC.isOver(CELL_X, CELL_Y, CELL_W + 2, CELL_H, mx, my)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(storage.getEnergyStored() + " / " + storage.getMaxEnergyStored() + " EU");
            return lines;
        }
        for (int k = 0; k < storage.chargeSlots(); k++) {
            if (storage.getStackInSlot(TileEntityEnergyStorageSC.chargeSlotIndex(k)) == null
                    && GuiGaugeSC.isOver(ContainerEnergyStorageSC.chargeX(k), ContainerEnergyStorageSC.chargeY(k), 16, 16, mx, my)) {
                lines.add(Lang.tr("sc.storage.gui.slot", storage.getTier().name()));
                lines.add(Lang.tr("sc.storage.gui.slots.hint", storage.chargeSlots(), storage.getTier().getVoltage()));
                return lines;
            }
        }
        return null;
    }

    /** Tooltips in screen space - see GuiMachineSC.drawScreen. */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip == null) {
            tooltip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tooltip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tooltip, width), mouseX, mouseY, fontRendererObj);
        }
    }
}
