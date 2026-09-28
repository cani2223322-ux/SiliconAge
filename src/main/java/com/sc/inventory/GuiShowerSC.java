package com.sc.inventory;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.manual.Lang;
import com.sc.radiation.RadiationStateSC;
import com.sc.tileentity.TileEntityShowerSC;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * The decontamination shower's screen (GuiBigSC's layout): status, who stands on the grate, what a
 * wash costs, your own dose; a figure under the spray; the water tank; the power switch, the gauge
 * and the battery slot on the right.
 */
public class GuiShowerSC extends GuiContainer {

    private final TileEntityShowerSC te;
    private GuiPowerSC power;
    private GuiBatterySlotSC battery;
    private static final int SCENE_X = 110, SCENE_Y = 34, SCENE_W = 56, SCENE_H = 72, TANK_X = 172, TANK_Y = 36;

    public GuiShowerSC(InventoryPlayer inv, TileEntityShowerSC te) {
        super(new ContainerShowerSC(inv, te));
        this.te = te;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(te, ContainerShowerSC.BTN_POWER, ContainerShowerSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
        battery = new GuiBatterySlotSC(te, ContainerShowerSC.BTN_BATTERY_MODE, GuiBigSC.GAUGE_X, GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H);
        battery.addButton(buttonList, guiLeft, guiTop);
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
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        GuiBigSC.window(x, y, false, 0);
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        rect(x + SCENE_X, y + SCENE_Y, SCENE_W, SCENE_H, 0xFF1E3444);
        rect(x + SCENE_X + 1, y + SCENE_Y + 1, SCENE_W - 2, SCENE_H - 2, 0xFF0A1218);
        scene(x + SCENE_X, y + SCENE_Y, t);
        // your dose: a bar under the numbers
        float dose = RadiationStateSC.fresh() ? RadiationStateSC.dose : 0F;
        GuiHoloSC.bar(x + 14, y + 100, 90, 3, dose / 100F, 20, dose >= 50 ? 0xFFE63C3C : dose >= 25 ? 0xFFFF9628 : 0xFF5AE66E);
        GuiTankGaugeSC.draw(mc, x + TANK_X, y + TANK_Y, te.getTank().getFluid(), TileEntityShowerSC.TANK, "water", false);
        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H,
                (float) te.getEnergyStored() / Math.max(1, te.getMaxEnergyStored()));
        battery.draw(x, y, te.getStackInSlot(TileEntityShowerSC.SLOT_BATTERY));
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** A shower head over a grate, a figure on it; while it washes, drops fall and the figure's glow fades. */
    private void scene(int x, int y, float t) {
        boolean on = te.getStatus() == TileEntityShowerSC.ST_WASHING;
        rect(x + 14, y + 6, 28, 3, 0xFF8A96A6);                 // the head and its pipe
        rect(x + 26, y + 2, 4, 4, 0xFF6A7686);
        for (int i = 0; i < 6; i++) {
            rect(x + 16 + i * 4, y + 9, 2, 1, 0xFF4A5666);
        }
        rect(x + 8, y + SCENE_H - 8, SCENE_W - 16, 3, 0xFF6A7686);  // the grate
        for (int i = 0; i < 9; i++) {
            rect(x + 10 + i * 4, y + SCENE_H - 7, 1, 1, 0xFF1A2230);
        }
        boolean someone = te.getPlayers() > 0;
        if (someone) {
            int fc = on ? 0xFF6EE6FF : te.getStatus() == TileEntityShowerSC.ST_CLEAN ? 0xFF5AE66E : 0xFFB0B8C4;
            int cx = x + SCENE_W / 2;
            rect(cx - 3, y + 24, 6, 6, fc);                   // head
            rect(cx - 5, y + 31, 10, 14, fc);                 // body
            rect(cx - 5, y + 45, 4, 13, fc);                  // legs
            rect(cx + 1, y + 45, 4, 13, fc);
            rect(cx - 8, y + 32, 3, 11, fc);                  // arms
            rect(cx + 5, y + 32, 3, 11, fc);
        }
        if (on) {
            for (int k = 0; k < 18; k++) {
                int col = k % 6;
                float fall = ((t * 2.2F + k * 13.7F) % 54F);
                rect(x + 16 + col * 4 + (k / 6) % 2, (int) (y + 11 + fall), 1, 3, 0xFF78A0FF);
            }
        }
    }

    private static void rect(int x, int y, int w, int h, int c) {
        drawRect(x, y, x + w, y + h, c);
    }

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private int statusColor(int st) {
        switch (st) {
            case TileEntityShowerSC.ST_WASHING: return GuiHoloSC.OK;
            case TileEntityShowerSC.ST_CLEAN:
            case TileEntityShowerSC.ST_IDLE:
            case TileEntityShowerSC.ST_OFF: return GuiHoloSC.IDLE;
            default: return GuiHoloSC.WARN;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(Lang.tr("tile.siliconage.shower.name"), 8, 5, GuiBigSC.titleRoom(fontRendererObj, te.getTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, te.getTier(), GuiBigSC.W - 6, 3);
        fontRendererObj.drawString(Lang.tr("container.inventory"), GuiBigSC.INV_X, GuiBigSC.INV_Y - 10, 0x404040);
        int cap = GuiHoloSC.CYAN & 0xFFFFFF, c = GuiHoloSC.VALUE, dim = GuiHoloSC.LABEL, st = te.getStatus();
        fit(Lang.tr("sc.shower.cap"), 14, 25, 92, cap);
        fit(Lang.tr("sc.shower.status." + st), 14, 36, 92, statusColor(st));
        fit(Lang.tr("sc.shower.players", te.getPlayers()), 14, 48, 92, c);
        fit(Lang.tr("sc.shower.rate", (int) TileEntityShowerSC.DOSE_PER_SECOND), 14, 58, 92, dim);
        fit(Lang.tr("sc.shower.cost", TileEntityShowerSC.EU_PER_TICK), 14, 68, 92, dim);
        fit(Lang.tr("sc.shower.watercost", TileEntityShowerSC.WATER_PER_TICK), 14, 77, 92, dim);
        float dose = RadiationStateSC.fresh() ? RadiationStateSC.dose : 0F;
        fit(Lang.tr("sc.shower.yourdose", com.sc.radiation.RadiationSC.fmt(dose)), 14, 88, 92,
                dose >= 50 ? GuiHoloSC.BAD : dose >= 25 ? GuiHoloSC.WARN : c);
        power.drawGaugeOff(fontRendererObj);
        fit(Lang.tr("sc.shower.hint"), 8, 124, 200, 0x505864);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = power.tooltip(mx, my);
        if (tip == null) {
            tip = battery.tooltip(mx, my, te.getStackInSlot(TileEntityShowerSC.SLOT_BATTERY));
        }
        if (tip == null && GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, mx, my)) {
            tip = new java.util.ArrayList<String>();
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(te.getEnergyStored() + " / " + te.getMaxEnergyStored() + " EU");
        }
        if (tip == null && GuiGaugeSC.isOver(TANK_X, TANK_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mx, my)) {
            tip = new java.util.ArrayList<String>();
            tip.add(Lang.tr("sc.shower.water"));
            tip.add(te.getTank().getFluidAmount() + " / " + TileEntityShowerSC.TANK + " mB");
        }
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }
}
