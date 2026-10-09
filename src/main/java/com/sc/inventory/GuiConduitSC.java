package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.conduit.ConduitMode;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityConduitBundleSC;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Ender IO style connector menu for one side of a conduit bundle. A tab on the right for each
 * conduit in the bundle; at the top "Mode < ... >" steps through the modes (cable: in/out,
 * extract, insert, off; pipe and tube: insert, extract, in/out, off; a link to the next bundle:
 * connected / off). Below, the options of the mode:
 * - extracting: the redstone control (all), the colour channel and round robin (tubes),
 * - inserting: the colour channel (tubes) and the priority (pipes, tubes).
 * Left click steps forward, right click back; shift + click on the priority steps by 10.
 * A tube connector also has its item slots: extract filter + speed upgrades while it extracts,
 * insert filter while it inserts. A slot that doesn't apply is moved out of sight - unless it still
 * holds something: then it stays, dimmed, so the item can be taken out (nothing can be put in).
 */
public class GuiConduitSC extends GuiContainer {

    private static final ResourceLocation ICONS = new ResourceLocation(Reference.ASSETS, "textures/gui/conduitGui.png");
    private static final int W = 196, H = 202, TAB = 26;
    /** The machine sheet, for its 18x18 slot pocket sprite. */
    private static final ResourceLocation SHEET = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");
    private static final int TEXT_BOTTOM = 105;
    private static final int ROW_X = 96, ROW_1 = 30, ROW_STEP = 26;

    private final TileEntityConduitBundleSC bundle;
    private final ForgeDirection side;
    private final List<ConduitKind> kinds = new ArrayList<ConduitKind>();
    private ConduitKind selected;
    /** What the layout was built for - rebuilt when the mode or the bundle's conduits change. */
    private ConduitMode builtMode;
    private boolean builtConnector;
    private boolean[] builtShown = new boolean[ContainerConduitSC.CONNECTOR_SLOTS];
    private int extractRow = -1, insertRow = -1;
    private int prioX;

    public GuiConduitSC(TileEntityConduitBundleSC bundle, ForgeDirection side, net.minecraft.entity.player.InventoryPlayer playerInv) {
        super(new ContainerConduitSC(bundle, side, playerInv));
        this.bundle = bundle;
        this.side = side;
        collectKinds();
        for (ConduitKind kind : kinds) {             // open on a conduit that actually reaches this side
            if (selected == null || (bundle.connects(kind, side) && !bundle.connects(selected, side))) {
                selected = kind;
            }
        }
        xSize = W;
        ySize = H;
    }

    private void collectKinds() {
        kinds.clear();
        for (ConduitKind kind : ConduitKind.values()) {
            if (bundle.has(kind)) {
                kinds.add(kind);
            }
        }
    }

    private boolean connector() {
        return ContainerConduitSC.isConnector(bundle, side);
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        extractRow = -1;
        insertRow = -1;
        placeConnectorSlots();
        if (selected == null) {
            return;
        }
        buttonList.add(new TextFitSC.Button(ContainerConduitSC.buttonId(selected, ContainerConduitSC.MODE_PREV), guiLeft + 46, guiTop + 6, 12, 16, "<"));
        buttonList.add(new TextFitSC.Button(ContainerConduitSC.buttonId(selected, ContainerConduitSC.MODE_NEXT), guiLeft + 164, guiTop + 6, 12, 16, ">"));
        ConduitMode mode = bundle.mode(selected, side);
        builtMode = mode;
        builtConnector = connector();
        if (!builtConnector) {
            return;
        }
        int row = ROW_1;
        boolean tube = selected == ConduitKind.TUBE;
        if (mode.extracts(selected)) {
            extractRow = row;
            int x = guiLeft + ROW_X;
            if (tube) {
                buttonList.add(new OptionButton(ContainerConduitSC.OUT_COLOR_NEXT, x, guiTop + row));
                x += 22;
            }
            buttonList.add(new OptionButton(ContainerConduitSC.RS_NEXT, x, guiTop + row));
            x += 22;
            if (tube) {
                buttonList.add(new OptionButton(ContainerConduitSC.ROUND_ROBIN, x, guiTop + row));
            }
            row += ROW_STEP;
        }
        if (mode.inserts(selected) && selected != ConduitKind.CABLE) {
            insertRow = row;
            int x = guiLeft + ROW_X;
            if (tube) {
                buttonList.add(new OptionButton(ContainerConduitSC.IN_COLOR_NEXT, x, guiTop + row));
                x += 22;
            }
            prioX = x - guiLeft;
            buttonList.add(new TextFitSC.Button(ContainerConduitSC.buttonId(selected, ContainerConduitSC.PRIO_DOWN), x, guiTop + row + 4, 12, 12, "-"));
            buttonList.add(new TextFitSC.Button(ContainerConduitSC.buttonId(selected, ContainerConduitSC.PRIO_UP), x + 44, guiTop + row + 4, 12, 12, "+"));
        }
    }

    /** Which connector slots work: a tube connector's extract filter + speed while extracting, insert filter while inserting. */
    private boolean[] connectorSlotsUsed() {
        boolean tube = selected == ConduitKind.TUBE && connector();
        ConduitMode m = bundle.has(ConduitKind.TUBE) ? bundle.mode(ConduitKind.TUBE, side) : ConduitMode.OFF;
        boolean out = tube && m.extracts(ConduitKind.TUBE), in = tube && m.inserts(ConduitKind.TUBE);
        return new boolean[]{out, out, in};
    }

    /** Which connector slots show: the working ones, and on the tube's tab the idle ones still holding an item. */
    private boolean[] connectorSlotsShown() {
        boolean[] shown = connectorSlotsUsed();
        for (int i = 0; i < shown.length; i++) {
            shown[i] |= selected == ConduitKind.TUBE && ((net.minecraft.inventory.Slot) inventorySlots.inventorySlots.get(i)).getHasStack();
        }
        return shown;
    }

    private void placeConnectorSlots() {
        boolean[] shown = connectorSlotsShown();
        builtShown = shown;
        for (int i = 0; i < ContainerConduitSC.CONNECTOR_SLOTS; i++) {
            net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) inventorySlots.inventorySlots.get(i);
            slot.xDisplayPosition = shown[i] ? ContainerConduitSC.SLOT_POS[i][0] : -2000;
            slot.yDisplayPosition = shown[i] ? ContainerConduitSC.SLOT_POS[i][1] : -2000;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        List<ConduitKind> before = new ArrayList<ConduitKind>(kinds);
        collectKinds();
        if (kinds.isEmpty() || bundle.isInvalid()) {
            mc.thePlayer.closeScreen();
            return;
        }
        if (!kinds.contains(selected)) {
            selected = kinds.get(0);
        }
        if (!before.equals(kinds) || bundle.mode(selected, side) != builtMode || connector() != builtConnector
                || !java.util.Arrays.equals(connectorSlotsShown(), builtShown)) {
            initGui();
        }
    }

    // ------------------------------------------------------------------ input

    private static int action(GuiButton b) {
        return b.id % 32;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        int a = action(button);
        if (GuiScreen.isShiftKeyDown() && (a == ContainerConduitSC.PRIO_UP || a == ContainerConduitSC.PRIO_DOWN)) {
            id += ContainerConduitSC.PRIO_UP_10 - ContainerConduitSC.PRIO_UP;
        }
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, id);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < kinds.size(); i++) {         // tabs
            int tx = guiLeft + W - 2, ty = guiTop + 4 + i * (TAB + 2);
            if (button == 0 && mouseX >= tx && mouseX < tx + TAB && mouseY >= ty && mouseY < ty + TAB) {
                if (selected != kinds.get(i)) {
                    selected = kinds.get(i);
                    mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
                            new ResourceLocation("gui.button.press"), 1.0F));
                    initGui();
                }
                return;
            }
        }
        if (button == 1) {                               // right click: an option one step back
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.mousePressed(mc, mouseX, mouseY)) {
                    int a = action(b);
                    int back = a == ContainerConduitSC.RS_NEXT ? ContainerConduitSC.RS_PREV
                            : a == ContainerConduitSC.OUT_COLOR_NEXT ? ContainerConduitSC.OUT_COLOR_PREV
                            : a == ContainerConduitSC.IN_COLOR_NEXT ? ContainerConduitSC.IN_COLOR_PREV
                            : a == ContainerConduitSC.MODE_NEXT ? ContainerConduitSC.MODE_PREV
                            : a == ContainerConduitSC.MODE_PREV ? ContainerConduitSC.MODE_NEXT
                            : a == ContainerConduitSC.PRIO_UP ? ContainerConduitSC.PRIO_DOWN
                            : a == ContainerConduitSC.PRIO_DOWN ? ContainerConduitSC.PRIO_UP : a;
                    b.func_146113_a(mc.getSoundHandler());
                    mc.playerController.sendEnchantPacket(inventorySlots.windowId, ContainerConduitSC.buttonId(selected, back));
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ drawing

    /** A panel in the machine sheets' frame: 2 px white top / left, 2 px dark right / bottom. */
    private void frame(int x0, int y0, int x1, int y1, int fill) {
        drawRect(x0, y0, x1, y1, fill);
        drawRect(x0, y0, x1 - 2, y0 + 2, 0xFFFFFFFF);
        drawRect(x0, y0, x0 + 2, y1 - 2, 0xFFFFFFFF);
        drawRect(x1 - 2, y0 + 2, x1, y1, 0xFF373737);
        drawRect(x0 + 2, y1 - 2, x1, y1, 0xFF373737);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // tabs first, so the selected one can merge into the panel's right edge
        for (int i = 0; i < kinds.size(); i++) {
            int tx = guiLeft + W - 2, ty = guiTop + 4 + i * (TAB + 2);
            boolean sel = kinds.get(i) == selected;
            frame(tx, ty, tx + TAB, ty + TAB, sel ? 0xFFC6C6C6 : 0xFF9A9A9A);
        }
        frame(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xFFC6C6C6);
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i) == selected) {
                int ty = guiTop + 4 + i * (TAB + 2);
                drawRect(guiLeft + W - 2, ty + 2, guiLeft + W + 1, ty + TAB - 2, 0xFFC6C6C6);
            }
        }
        // slot pockets: the connector slots that show, and the player's inventory
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(SHEET);
        boolean[] shown = connectorSlotsShown();
        for (int i = 0; i < ContainerConduitSC.CONNECTOR_SLOTS; i++) {
            if (shown[i]) {
                drawTexturedModalRect(guiLeft + ContainerConduitSC.SLOT_POS[i][0] - 1, guiTop + ContainerConduitSC.SLOT_POS[i][1] - 1,
                        GuiGaugeSC.SPR_SLOT_U, GuiGaugeSC.SPR_SLOT_V, 18, 18);
            }
        }
        for (int row = 0; row < 4; row++) {
            int y = guiTop + ContainerConduitSC.INV_Y - 1 + (row < 3 ? row * 18 : 58);
            for (int col = 0; col < 9; col++) {
                drawTexturedModalRect(guiLeft + ContainerConduitSC.INV_X - 1 + col * 18, y, GuiGaugeSC.SPR_SLOT_U, GuiGaugeSC.SPR_SLOT_V, 18, 18);
            }
        }
        // mode box between the arrows
        drawRect(guiLeft + 60, guiTop + 6, guiLeft + 162, guiTop + 22, 0xFF8B8B8B);
        if (insertRow >= 0) {
            drawRect(guiLeft + prioX + 13, guiTop + insertRow + 3, guiLeft + prioX + 43, guiTop + insertRow + 17, 0xFF373737);
        }
        // conduit icons on the tabs
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        for (int i = 0; i < kinds.size(); i++) {
            ItemStack part = bundle.partStack(kinds.get(i));
            if (part != null) {
                itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), part,
                        guiLeft + W + 3, guiTop + 9 + i * (TAB + 2));
            }
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        TextFitSC.draw(fontRendererObj, Lang.tr("sc.conduit.gui.mode"), 8, 10, 36, 0x404040, guiLeft, guiTop);
        String mode = Lang.tr("sc.conduit.mode." + bundle.mode(selected, side).menuKey(selected, connector()));
        TextFitSC.drawCentered(fontRendererObj, mode, 60, 10, 102, 0xFFFFFF, true, guiLeft, guiTop);
        if (extractRow >= 0) {
            TextFitSC.draw(fontRendererObj, Lang.tr("sc.conduit.gui.extracting"), 8, extractRow + 6, W - 16, 0x404040, guiLeft, guiTop);
        }
        if (insertRow >= 0) {
            TextFitSC.draw(fontRendererObj, Lang.tr("sc.conduit.gui.inserting"), 8, insertRow + 6, prioX - 12, 0x404040, guiLeft, guiTop);
            String p = String.valueOf(bundle.priority(selected, side));
            fontRendererObj.drawString(p, prioX + 28 - fontRendererObj.getStringWidth(p) / 2, insertRow + 6, 0xFFFFFF);
        }
        String sideName = Lang.tr("sc.side." + side.name().toLowerCase(Locale.ROOT));
        String where = Lang.tr("sc.conduit.gui.where", sideName, targetName());
        TextFitSC.draw(fontRendererObj, where, 8, TEXT_BOTTOM, W - 16, 0x606060, guiLeft, guiTop);
        // idle slots that still hold an item: dimmed, with a note to take it out
        boolean[] used = connectorSlotsUsed(), shown = connectorSlotsShown();
        boolean idle = false;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < ContainerConduitSC.CONNECTOR_SLOTS; i++) {
            if (shown[i] && !used[i]) {
                int x = ContainerConduitSC.SLOT_POS[i][0], y = ContainerConduitSC.SLOT_POS[i][1];
                drawRect(x, y, x + 16, y + 16, 0xA0303030);
                idle = true;
            }
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        if (idle) {
            TextFitSC.draw(fontRendererObj, Lang.tr("sc.conduit.gui.slot.idle"), 82, 88, W - 90, 0x8B2020, guiLeft, guiTop);
        }
    }

    /** Name of the block on this side (what the connector plugs into). */
    private String targetName() {
        int x = bundle.xCoord + side.offsetX, y = bundle.yCoord + side.offsetY, z = bundle.zCoord + side.offsetZ;
        try {
            Block block = bundle.getWorldObj().getBlock(x, y, z);
            if (block.isAir(bundle.getWorldObj(), x, y, z)) {
                return Lang.tr("sc.conduit.gui.nothing");
            }
            ItemStack pick = block.getPickBlock(null, bundle.getWorldObj(), x, y, z);
            if (pick != null && pick.getItem() != null) {
                return pick.getDisplayName();
            }
            return block.getLocalizedName();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tip = tooltip(mouseX, mouseY);
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    private List<String> tooltip(int mouseX, int mouseY) {
        List<String> tip = new ArrayList<String>();
        String[] slotNames = {"sc.conduit.gui.slot.outfilter", "sc.conduit.gui.slot.speed", "sc.conduit.gui.slot.infilter"};
        for (int i = 0; i < ContainerConduitSC.CONNECTOR_SLOTS; i++) {
            net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) inventorySlots.inventorySlots.get(i);
            int sx = guiLeft + slot.xDisplayPosition, sy = guiTop + slot.yDisplayPosition;
            if (!slot.getHasStack() && mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) {
                tip.add(Lang.tr(slotNames[i]));
                tip.add("§7" + Lang.tr(slotNames[i] + ".hint"));
                return tip;
            }
        }
        for (int i = 0; i < kinds.size(); i++) {
            int tx = guiLeft + W - 2, ty = guiTop + 4 + i * (TAB + 2);
            if (mouseX >= tx && mouseX < tx + TAB && mouseY >= ty && mouseY < ty + TAB) {
                ItemStack part = bundle.partStack(kinds.get(i));
                tip.add(part == null ? "?" : part.getDisplayName());
                return tip;
            }
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (mouseX < b.xPosition || mouseY < b.yPosition || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
                continue;
            }
            switch (action(b)) {
                case ContainerConduitSC.RS_NEXT:
                    tip.add(Lang.tr("sc.conduit.gui.rs"));
                    tip.add("§7" + Lang.tr("sc.conduit.gui.rs." + bundle.redstoneMode(selected, side).name().toLowerCase(Locale.ROOT)));
                    return tip;
                case ContainerConduitSC.OUT_COLOR_NEXT:
                    tip.add(Lang.tr("sc.conduit.gui.channel.out", colorName(bundle.extractColor(selected, side))));
                    tip.add("§7" + Lang.tr("sc.conduit.gui.channel.hint"));
                    return tip;
                case ContainerConduitSC.IN_COLOR_NEXT:
                    tip.add(Lang.tr("sc.conduit.gui.channel.in", colorName(bundle.insertColor(selected, side))));
                    tip.add("§7" + Lang.tr("sc.conduit.gui.channel.hint"));
                    return tip;
                case ContainerConduitSC.ROUND_ROBIN:
                    tip.add(Lang.tr(bundle.roundRobin(selected, side) ? "sc.conduit.gui.rr.on" : "sc.conduit.gui.rr.off"));
                    tip.add("§7" + Lang.tr("sc.conduit.gui.rr.hint"));
                    return tip;
                case ContainerConduitSC.PRIO_UP:
                case ContainerConduitSC.PRIO_DOWN:
                    tip.add(Lang.tr("sc.conduit.gui.priority", bundle.priority(selected, side)));
                    tip.add("§7" + Lang.tr("sc.conduit.gui.priority.hint"));
                    return tip;
                default:
                    tip.add("§7" + Lang.tr("sc.conduit.gui.desc." + bundle.mode(selected, side).menuKey(selected, connector())
                            + "." + selected.name().toLowerCase(Locale.ROOT)));
                    return tip;
            }
        }
        return null;
    }

    private static String colorName(int color) {
        return StatCollector.translateToLocal("item.fireworksCharge." + ItemDye.field_150923_a[color & 15]);
    }

    /** An option button drawn with its state: a colour swatch, the redstone control, or round robin. */
    private class OptionButton extends GuiButton {

        OptionButton(int action, int x, int y) {
            super(ContainerConduitSC.buttonId(selected, action), x, y, 20, 20, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) {
                return;
            }
            super.drawButton(mc, mouseX, mouseY);
            int a = id % 32;
            if (a == ContainerConduitSC.OUT_COLOR_NEXT || a == ContainerConduitSC.IN_COLOR_NEXT) {
                int color = a == ContainerConduitSC.OUT_COLOR_NEXT ? bundle.extractColor(selected, side) : bundle.insertColor(selected, side);
                drawRect(xPosition + 4, yPosition + 4, xPosition + 16, yPosition + 16, 0xFF000000);
                drawRect(xPosition + 5, yPosition + 5, xPosition + 15, yPosition + 15, 0xFF000000 | ItemDye.field_150922_c[color & 15]);
                return;
            }
            int icon = a == ContainerConduitSC.RS_NEXT ? bundle.redstoneMode(selected, side).ordinal()
                    : bundle.roundRobin(selected, side) ? 4 : 5;
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glEnable(GL11.GL_BLEND);
            mc.getTextureManager().bindTexture(ICONS);
            drawTexturedModalRect(xPosition + 2, yPosition + 2, icon * 16, 0, 16, 16);
        }
    }
}
