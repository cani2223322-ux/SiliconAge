package com.sc.settle.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.sc.Reference;
import com.sc.block.BlockBridgeSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.inventory.ContainerTradeSC;
import com.sc.inventory.GuiGaugeSC;
import com.sc.inventory.GuiHoloSC;
import com.sc.inventory.TextFitSC;
import com.sc.manual.Lang;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettleNetSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeServicesSC;
import com.sc.settle.trade.TradeTableSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.1): the «Терминал обмена» screen - like vanilla's: the deals on the
 * left (two things in -> one out, the counts as this player pays them, what is left today), the restock timer, the
 * standing; «Обменять» (shift: as many as allowed); the Elder's «Контракты» tab (take / hand in) and the penance. It
 * shows only what TradeClientSC last heard (SettleNetSC.TRADE_STATE) and asks the server for everything
 * (SettleNetSC.sendToServer) - nothing is decided here. Client only.
 * <p>
 * The look is the mod's big screens' (GuiBigSC, the window baked into textures/gui/trade.png by tools/gen_trade_tex.py):
 * the title bar - the Tuner's name and profession, the player's level and «Доверие»; a holo list of the deals (or the
 * contracts) on the left; the chosen one's details and its buttons on the right; a status line (the last answer, the
 * day's chips or contracts, the time to dawn); the player's inventory. What the player holds is counted here from the
 * client's own inventory every tick (red counts: not enough), the limits and the level come from the server.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §6.8, rule ПО-13): a service's row (the Power Engineer's charge, the Armourer's
 * restoring) shows the thing it will work on - the one in the hand, else the first that fits (TradeServicesSC.slot, on
 * the client's inventory: the server finds the same) - with what it becomes on the right; nothing fits - its icon cycles
 * through what would, marked «*» («any matching»), and the details say what is wanted. The slot that will be taken is
 * framed in the inventory below. A matcher side of a plain deal (the Salvager's Nano trophy) cycles the same way.
 */
@SideOnly(Side.CLIENT)
public class GuiTradeSC extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation(Reference.ASSETS, "textures/gui/trade.png");
    public static final int W = 248, H = 232;
    /** The list (holo screen) and its rows. */
    private static final int LX = 7, LY = 19, LW = 152, ROWS = 6, ROW_H = 18, LH = ROWS * ROW_H + 4;
    /** The right column: the Elder's tabs, the details' screen above the buttons. */
    private static final int RX = 163, RW = 78, TAB_Y = 19, TAB_H = 16, BY = 115, BH = 16;
    private static final int STATUS_Y = 133;
    /** Sprites of trade.png (below the window). */
    private static final int SPR_V = 232, ARROW_U = 0, ARROW_OFF_U = 9, BADGE_U = 18, LOCK_U = 34, CHECK_U = 41, KNOB_U = 50;
    private static final int BTN_TRADE = 0, BTN_CONTRACT = 1, BTN_DROP = 2, TAB_DEALS = 10, TAB_CONTRACTS = 11;
    /** How long an answer stays in the status line; how long to wait for the first state before asking again. */
    private static final int RESULT_TICKS = 100, ASK_AFTER = 20;
    private static final int TEXT_DARK = 0x404040;
    /** The levels' colours (ReputationSC order). */
    private static final int[] LEVEL_COLOURS = {0xFF5A50, 0xFF8A70, 0xC8D0D8, 0x9AD8FF, 0x7CF0B0, 0x5AE66E, 0xFFD060};

    protected final EntityTunerSC tuner;

    /** One deal as this player sees it (the server's numbers) and what the inventory holds of each side (counted here). */
    private static final class Row {
        TradeOfferSC offer;
        int[] n;
        /** What one deal gives out here (TradeRulesSC.outCount - a trophy's sale scales its chips). */
        int out;
        int left, why;
        int[] have;
        /** A service: the main inventory's slot it will work on (TradeServicesSC.slot), -1 none. */
        int slot = -1;
    }

    /** One contract line: on the board, open from an earlier day, or the one-time anchor. */
    private static final class CRow {
        ContractTableSC.Contract c;
        int st, progress;
        boolean anchor;
        int have;
    }

    private final List<Row> rows = new ArrayList<Row>();
    private final List<CRow> crows = new ArrayList<CRow>();
    private boolean has, enemy, contractsTab;
    private int rep, level = ReputationSC.STRANGER, chips, sname, taken, anchor;
    private long stateDay = -1;
    private int seenState = -1, seenResult = -1, waited, sel, csel, scroll, cscroll;
    private String resultText = "";
    private int resultColour, resultTicks;
    private GuiButton tradeBtn, contractBtn, dropBtn, dealsTab, contractsTabBtn;

    public GuiTradeSC(EntityPlayer player, EntityTunerSC tuner) {
        super(new ContainerTradeSC(player, tuner));
        this.tuner = tuner;
        xSize = W;
        ySize = H;
    }

    private boolean elder() {
        return tuner.kind() == NpcKindSC.ELDER;
    }

    private int detailsY() {
        return elder() ? TAB_Y + TAB_H + 2 : TAB_Y;
    }

    // ================================================================== state

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int x = guiLeft + RX, y = guiTop + BY;
        tradeBtn = new TextFitSC.Button(BTN_TRADE, x, y, RW, BH, Lang.tr("sc.trade.btn.trade"));
        contractBtn = new TextFitSC.Button(BTN_CONTRACT, x, y, RW / 2 - 1, BH, Lang.tr("sc.trade.btn.take"));
        dropBtn = new TextFitSC.Button(BTN_DROP, x + RW / 2 + 1, y, RW / 2 - 1, BH, Lang.tr("sc.trade.btn.drop"));
        buttonList.add(tradeBtn);
        buttonList.add(contractBtn);
        buttonList.add(dropBtn);
        if (elder()) {
            ItemStack chip = SettlementItemsSC.chips(1);
            dealsTab = new TextFitSC.Tab(TAB_DEALS, x, guiTop + TAB_Y, RW / 2 - 1, TAB_H, chip,
                    Lang.tr("sc.trade.tab.deals"), Lang.tr("sc.trade.tab.deals"));
            contractsTabBtn = new TextFitSC.Tab(TAB_CONTRACTS, x + RW / 2 + 1, guiTop + TAB_Y, RW / 2 - 1, TAB_H, new ItemStack(Items.paper),
                    Lang.tr("sc.trade.tab.contracts"), Lang.tr("sc.trade.tab.contracts"));
            buttonList.add(dealsTab);
            buttonList.add(contractsTabBtn);
        }
        pickUp();
        updateButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tuner == null || tuner.isDead) {
            mc.thePlayer.closeScreen();
            return;
        }
        pickUp();
        if (!has && ++waited == ASK_AFTER) {
            SettleNetSC.sendToServer(SettleNetSC.TRADE_REFRESH, new NBTTagCompound());   // the state got lost (or went to an older screen)
        }
        if (has && mc.theWorld != null && SettlementSC.day(mc.theWorld) != stateDay) {
            stateDay = SettlementSC.day(mc.theWorld);                                     // dawn: the shops restock, a new board
            SettleNetSC.sendToServer(SettleNetSC.TRADE_REFRESH, new NBTTagCompound());
        }
        countHave();
        if (resultTicks > 0) {
            resultTicks--;
        }
        updateButtons();
    }

    /** The newest state / answer for this Tuner, if any came since the last look. */
    private void pickUp() {
        int seq = TradeClientSC.stateSeq();
        if (seq != seenState) {
            seenState = seq;
            NBTTagCompound s = TradeClientSC.state();
            if (s != null && s.getInteger("tuner") == tuner.getEntityId()) {
                parse(s);
            }
        }
        seq = TradeClientSC.resultSeq();
        if (seq != seenResult) {
            seenResult = seq;
            NBTTagCompound r = TradeClientSC.result();
            if (r != null && r.getInteger("tuner") == tuner.getEntityId()) {
                answer(r);
            }
        }
    }

    private void parse(NBTTagCompound s) {
        rep = s.getInteger("rep");
        level = s.getByte("level");
        enemy = s.getBoolean("enemy");
        chips = s.getInteger("chips");
        sname = s.getInteger("sname");
        rows.clear();
        NBTTagList l = s.getTagList("offers", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            TradeOfferSC o = TradeTableSC.byKey(e.getString("k"));
            int[] n = e.getIntArray("n");
            if (o == null || n.length != o.in.length) {
                continue;                                       // a deal this client doesn't know (never with the same mod)
            }
            Row r = new Row();
            r.offer = o;
            r.n = n;
            r.out = e.hasKey("o") ? e.getInteger("o") : o.out == null ? 0 : o.out.count;
            r.left = e.getInteger("left");
            r.why = e.getByte("why");
            r.have = new int[n.length];
            rows.add(r);
        }
        crows.clear();
        if (s.hasKey("board")) {
            for (String list : new String[]{"board", "open"}) {
                NBTTagList b = s.getTagList(list, 10);
                for (int i = 0; i < b.tagCount(); i++) {
                    NBTTagCompound e = b.getCompoundTagAt(i);
                    ContractTableSC.Contract c = ContractTableSC.byKey(e.getString("k"));
                    if (c != null) {
                        CRow r = new CRow();
                        r.c = c;
                        r.st = e.getByte("st");
                        r.progress = e.getInteger("p");
                        crows.add(r);
                    }
                }
            }
            taken = s.getInteger("taken");
            anchor = s.getByte("anchor");
            CRow a = new CRow();
            a.c = ContractTableSC.ANCHOR_CONTRACT;
            a.anchor = true;
            a.st = anchor;
            crows.add(a);
        }
        stateDay = mc.theWorld == null ? -1 : SettlementSC.day(mc.theWorld);
        has = true;
        sel = clamp(sel, rows.size());
        csel = clamp(csel, crows.size());
        scroll = Math.max(0, Math.min(scroll, rows.size() - ROWS));
        cscroll = Math.max(0, Math.min(cscroll, crows.size() - ROWS));
        countHave();
    }

    private static int clamp(int i, int size) {
        return size == 0 ? -1 : Math.max(0, Math.min(size - 1, i));
    }

    /** A TRADE_RESULT: the status line's text for RESULT_TICKS. */
    private void answer(NBTTagCompound r) {
        int code = r.getInteger("code"), done = r.getInteger("done"), kind = r.getInteger("kind");
        String text;
        boolean good;
        if (kind == SettleNetSC.TRADE_DO && done > 0) {
            TradeOfferSC o = TradeTableSC.byKey(r.getString("key"));
            text = Lang.tr(o != null && o.service() ? "sc.trade.service.done" : "sc.trade.done", done);
            if (code != TradeServerSC.OK && code != TradeServerSC.R_MISSING) {
                text += " - " + reason(code);                   // stopped by a limit, not by running out
            }
            good = true;
        } else if (code == TradeServerSC.R_PARTIAL) {
            text = Lang.tr("sc.trade.result.partial", done);
            good = true;
        } else if (code == TradeServerSC.OK) {
            String k = kind == SettleNetSC.CONTRACT_TAKE ? "sc.contract.taken" : kind == SettleNetSC.CONTRACT_DROP ? "sc.contract.dropped"
                    : "sc.contract.handedIn";
            text = Lang.tr(k);
            good = true;
        } else {
            text = reason(code);
            good = false;
        }
        resultText = text;
        resultColour = good ? 0x1E7A2E : 0xA02018;
        resultTicks = RESULT_TICKS;
    }

    private static String reason(int code) {
        String[] keys = TradeServerSC.RESULT_KEYS;
        return Lang.tr("sc.trade.result." + keys[code >= 0 && code < keys.length ? code : TradeServerSC.R_UNKNOWN]);
    }

    /** What the client's inventory holds of every side (its own count, so a stack moved here shows at once). */
    private void countHave() {
        ItemStack[] inv = mc.thePlayer == null ? null : mc.thePlayer.inventory.mainInventory;
        int held = mc.thePlayer == null ? -1 : mc.thePlayer.inventory.currentItem;
        for (Row r : rows) {
            for (int k = 0; k < r.n.length; k++) {
                r.have[k] = r.offer.in[k].countIn(inv);
            }
            r.slot = r.offer.service() ? TradeServicesSC.slot(inv, held, r.offer) : -1;
        }
        for (CRow r : crows) {
            TradeOfferSC.Ingredient in = r.anchor ? null : r.c.handIn();
            r.have = in == null ? 0 : in.countIn(inv);
        }
    }

    /** One more deal of `r` could be made now (the server decides; this only lights the button). */
    private static boolean canOnce(Row r) {
        if (r.why != TradeServerSC.OK || r.offer.service() && r.slot < 0) {
            return false;
        }
        for (int k = 0; k < r.n.length; k++) {
            if (r.have[k] < r.n[k]) {
                return false;
            }
        }
        return true;
    }

    /** How many deals of `r` a Shift-press would ask for at most (the server may stop sooner: the chips' cap). */
    private static int maxTimes(Row r) {
        if (!canOnce(r)) {
            return 0;
        }
        int m = Math.min(r.left, TradeRulesSC.MAX_TIMES);
        for (int k = 0; k < r.n.length; k++) {
            m = Math.min(m, r.have[k] / Math.max(1, r.n[k]));
        }
        return m;
    }

    private void updateButtons() {
        if (tradeBtn == null) {
            return;
        }
        boolean deals = !contractsTab;
        tradeBtn.visible = deals;
        Row r = deals && sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
        tradeBtn.enabled = r != null && canOnce(r);
        contractBtn.visible = dropBtn.visible = !deals;
        CRow c = !deals && csel >= 0 && csel < crows.size() ? crows.get(csel) : null;
        boolean take = c != null && !c.anchor && c.st == TradeServerSC.C_FREE;
        contractBtn.displayString = Lang.tr(take ? "sc.trade.btn.take" : "sc.trade.btn.turnIn");
        contractBtn.enabled = c != null && (take ? taken < TradeRulesSC.CONTRACTS_PER_DAY && level >= ReputationSC.ACQUAINTANCE : canTurnIn(c));
        dropBtn.enabled = c != null && !c.anchor && c.st == TradeServerSC.C_OPEN;
        if (dealsTab != null) {
            dealsTab.enabled = contractsTab;
            contractsTabBtn.enabled = !contractsTab;
        }
    }

    private static boolean canTurnIn(CRow c) {
        if (c.anchor) {
            return c.st == TradeServerSC.ANCHOR_YOURS;
        }
        if (c.st != TradeServerSC.C_OPEN) {
            return false;
        }
        return c.c.handIn() != null ? c.have > 0 : c.progress >= c.c.target;
    }

    // ================================================================== input

    @Override
    protected void actionPerformed(GuiButton b) {
        NBTTagCompound d = new NBTTagCompound();
        switch (b.id) {
            case TAB_DEALS:
            case TAB_CONTRACTS:
                contractsTab = b.id == TAB_CONTRACTS;
                updateButtons();
                return;
            case BTN_TRADE:
                if (sel >= 0 && sel < rows.size()) {
                    d.setString("key", rows.get(sel).offer.key);
                    d.setInteger("times", isShiftKeyDown() ? TradeRulesSC.MAX_TIMES : 1);
                    SettleNetSC.sendToServer(SettleNetSC.TRADE_DO, d);
                }
                return;
            case BTN_CONTRACT:
            case BTN_DROP:
                if (csel >= 0 && csel < crows.size()) {
                    CRow c = crows.get(csel);
                    d.setString("key", c.c.key);
                    int kind = b.id == BTN_DROP ? SettleNetSC.CONTRACT_DROP
                            : !c.anchor && c.st == TradeServerSC.C_FREE ? SettleNetSC.CONTRACT_TAKE : SettleNetSC.CONTRACT_TURN_IN;
                    SettleNetSC.sendToServer(kind, d);
                }
                return;
            default:
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        int x = mx - guiLeft - LX, y = my - guiTop - LY - 2;
        if (button != 0 || x < 2 || x >= LW - 8 || y < 0 || y >= ROWS * ROW_H) {
            return;
        }
        int i = (contractsTab ? cscroll : scroll) + y / ROW_H;
        if (i < (contractsTab ? crows.size() : rows.size())) {
            if (contractsTab) {
                csel = i;
            } else {
                sel = i;
            }
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            updateButtons();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (contractsTab) {
                cscroll = Math.max(0, Math.min(Math.max(0, crows.size() - ROWS), cscroll + (wheel > 0 ? -1 : 1)));
            } else {
                scroll = Math.max(0, Math.min(Math.max(0, rows.size() - ROWS), scroll + (wheel > 0 ? -1 : 1)));
            }
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        TradeClientSC.forget(tuner.getEntityId());
    }

    // ================================================================== drawing

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(TEX);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, W, H);
        GuiHoloSC.screen(guiLeft + LX, guiTop + LY, LW, LH);
        int dy = detailsY();
        GuiHoloSC.screen(guiLeft + RX, guiTop + dy, RW, BY - 2 - dy);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Everything but the item icons (drawScreen draws those over it, as vanilla's merchant does). */
    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        // the title: name · profession; level · «Доверие»
        String name = NpcNamesSC.name(tuner.nameCode()).getUnformattedText() + " · " + Lang.tr(tuner.kind().langKey());
        String lv = has ? levelName(level) + " · " + rep : "…";
        int lvW = Math.min(90, fontRendererObj.getStringWidth(lv));
        TextFitSC.draw(fontRendererObj, name, 9, 6, W - 22 - lvW, 0xE6F0FA, guiLeft, guiTop);
        TextFitSC.draw(fontRendererObj, lv, W - 8 - lvW, 6, lvW, has ? LEVEL_COLOURS[Math.max(0, Math.min(6, level))] : 0x8898A8, guiLeft, guiTop);
        TextFitSC.hover(guiLeft + 4, guiTop + 4, W - 8, 11, titleTip());
        if (!has) {
            fontRendererObj.drawString(Lang.tr("sc.trade.wait"), LX + 6, LY + 6, GuiHoloSC.IDLE);
        } else if (contractsTab) {
            drawContracts(mx, my);
        } else {
            drawDeals(mx, my);
        }
        // the status line: the last answer, else the day's count; the time to dawn on the right
        long ticks = mc.theWorld == null ? SettlementSC.DAY : SettlementSC.ticksToDawn(mc.theWorld);
        long secs = (ticks + 19) / 20;
        String dawn = Lang.tr("sc.trade.dawn", String.format("%d:%02d", secs / 60, secs % 60));
        int dawnW = fontRendererObj.getStringWidth(dawn);
        fontRendererObj.drawString(dawn, W - 8 - dawnW, STATUS_Y, TEXT_DARK);
        TextFitSC.hover(guiLeft + W - 8 - dawnW, guiTop + STATUS_Y, dawnW, 9, Lang.tr("sc.trade.dawn.tip"));
        String left;
        int colour = TEXT_DARK;
        if (resultTicks > 0) {
            left = resultText;
            colour = resultColour;
        } else if (contractsTab) {
            left = Lang.tr("sc.contract.takenToday", taken, TradeRulesSC.CONTRACTS_PER_DAY);
        } else {
            left = Lang.tr("sc.trade.chipsToday", chips, TradeRulesSC.CHIPS_PER_DAY);
        }
        TextFitSC.draw(fontRendererObj, left, 8, STATUS_Y, W - 22 - dawnW, colour, guiLeft, guiTop);
        fontRendererObj.drawString(Lang.tr("container.inventory"), ContainerTradeSC.INV_X, ContainerTradeSC.INV_Y - 10, TEXT_DARK);
        frameTaken();
        GuiHoloSC.glint(LX, LY, LW, LH);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private List<String> titleTip() {
        List<String> l = new ArrayList<String>();
        if (sname != 0) {
            l.add(new ChatComponentTranslation("sc.settle.name", NpcNamesSC.name(sname)).getUnformattedText());
        } else {
            l.add(Lang.tr("sc.trade.unbound"));
        }
        if (has) {
            l.add(Lang.tr("sc.trade.tip.level", levelName(level), rep, num(ReputationSC.priceMultiplier(level))));
        }
        return l;
    }

    /** 1.25 -> "1.25" ("1,25" in Russian: the handbook's sc.num.decimal), 1.0 -> "1". */
    private static String num(float v) {
        float r = Math.round(v * 100F) / 100F;
        String s = r == (int) r ? String.valueOf((int) r) : String.valueOf(r);
        String mark = Lang.trOr("sc.num.decimal", ".");
        return mark.isEmpty() || ".".equals(mark) ? s : s.replace(".", mark);
    }

    private static String levelName(int level) {
        return Lang.tr("sc.settle.rep." + ReputationSC.LEVEL_KEYS[Math.max(0, Math.min(ReputationSC.LEVEL_KEYS.length - 1, level))]);
    }

    private void drawDeals(int mx, int my) {
        if (rows.isEmpty()) {
            fontRendererObj.drawString(Lang.tr(enemy ? "sc.trade.result.enemy" : "sc.trade.none"), LX + 6, LY + 6, GuiHoloSC.IDLE);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) {
                break;
            }
            Row r = rows.get(idx);
            int y = LY + 2 + i * ROW_H;
            rowBack(idx == sel, y, mx, my);
            boolean open = r.why == TradeServerSC.OK;
            bindTex();
            drawTexturedModalRect(LX + 38, y + 5, open ? ARROW_U : ARROW_OFF_U, SPR_V, 9, 7);
            if (r.offer.outKind == TradeOfferSC.OUT_REPUTATION) {
                drawTexturedModalRect(LX + 49, y + 1, BADGE_U, SPR_V, 16, 16);
            }
            int tx = LX + 68, tw = LW - 8 - 68;
            TextFitSC.draw(fontRendererObj, Lang.tr(r.offer.langKey()), tx, y + 1, tw, open ? GuiHoloSC.VALUE : GuiHoloSC.IDLE, guiLeft, guiTop);
            String sub;
            int col;
            if (r.why == TradeServerSC.R_LEVEL) {
                bindTex();
                drawTexturedModalRect(tx, y + 9, LOCK_U, SPR_V, 7, 9);
                sub = levelName(r.offer.minLevel);
                col = GuiHoloSC.WARN;
                TextFitSC.draw(fontRendererObj, sub, tx + 9, y + 10, tw - 9, col, guiLeft, guiTop);
                continue;
            } else if (r.why != TradeServerSC.OK) {
                sub = Lang.tr("sc.trade.row." + TradeServerSC.RESULT_KEYS[Math.max(0, Math.min(TradeServerSC.RESULT_KEYS.length - 1, r.why))]);
                col = GuiHoloSC.IDLE;
            } else if (r.offer.service()) {                       // which thing it will take, or that none fits
                ItemStack t = taken(r);
                sub = t != null ? Lang.tr("sc.trade.service.row", t.getDisplayName()) : Lang.tr("sc.trade.row.noItem");
                col = t == null ? GuiHoloSC.WARN : canOnce(r) ? GuiHoloSC.OK : GuiHoloSC.LABEL;
            } else {
                sub = leftLine(r);
                col = canOnce(r) ? GuiHoloSC.OK : GuiHoloSC.LABEL;
            }
            TextFitSC.draw(fontRendererObj, sub, tx, y + 10, tw, col, guiLeft, guiTop);
        }
        scrollbar(rows.size(), scroll);
        Row r = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
        if (r != null) {
            dealDetails(r);
        }
    }

    /** «ещё 3 из 8» / «на этой неделе: 0 из 1» / «без лимита». */
    private static String leftLine(Row r) {
        if (r.offer.unlimited()) {
            return Lang.tr("sc.trade.left.none");
        }
        if (r.offer.global && r.offer.periodDays > 1) {
            return Lang.tr("sc.trade.left.week", r.left, r.offer.limit);
        }
        return Lang.tr("sc.trade.left.day", r.left, r.offer.limit);
    }

    private void dealDetails(Row r) {
        if (r.offer.service()) {
            serviceDetails(r);
            return;
        }
        int x = RX + 4, w = RW - 8, y = detailsY() + 4;
        TradeOfferSC o = r.offer;
        String out;
        if (o.outKind == TradeOfferSC.OUT_REPUTATION) {
            out = Lang.tr("sc.trade.out.rep", o.outAmount);
        } else {
            ItemStack s = outStack(r);
            out = s == null ? "?" : Lang.tr("sc.trade.count", s.stackSize, s.getDisplayName());
        }
        y = wrapped(out, x, y, w, GuiHoloSC.VALUE, 2);
        fontRendererObj.drawString(Lang.tr("sc.trade.give"), x, y, GuiHoloSC.LABEL);
        y += 10;
        for (int k = 0; k < o.in.length; k++) {
            ItemStack s = o.in[k].display();
            String name = o.in[k].any() && o.anyLangKey() != null ? Lang.tr(o.anyLangKey()) : s == null ? o.in[k].ore : s.getDisplayName();
            String line = r.have[k] + "/" + r.n[k] + " " + name;     // a matcher (the Salvager's trophy): what fits, in words
            TextFitSC.draw(fontRendererObj, line, x, y, w, r.have[k] >= r.n[k] ? GuiHoloSC.OK : GuiHoloSC.BAD, guiLeft, guiTop);
            y += 10;
        }
        TextFitSC.draw(fontRendererObj, leftLine(r), x, y, w, GuiHoloSC.LABEL, guiLeft, guiTop);
        y += 10;
        String why = null;
        if (r.why == TradeServerSC.R_LEVEL) {
            why = Lang.tr("sc.trade.need.level", levelName(o.minLevel));
        } else if (r.why != TradeServerSC.OK) {
            why = reason(r.why);
        } else if (!canOnce(r)) {
            why = reason(TradeServerSC.R_MISSING);
        }
        int bottom = BY - 4;
        if (why != null) {
            wrapped(why, x, y, w, r.why == TradeServerSC.OK ? GuiHoloSC.BAD : GuiHoloSC.WARN, Math.max(1, (bottom - y) / 9));
        }
    }

    /**
     * A service's details: what the thing becomes, where it is taken from (the hand / the framed slot) or what would fit,
     * the price (in[1..]), the limit, why not.
     */
    private void serviceDetails(Row r) {
        int x = RX + 4, w = RW - 8, y = detailsY() + 4, bottom = BY - 4;
        TradeOfferSC o = r.offer;
        ItemStack t = taken(r);
        ItemStack res = TradeServicesSC.result(o, t != null ? t : o.in[0].display());
        String what = res == null ? "?" : res.getDisplayName();
        y = wrapped(Lang.tr(o.outKind == TradeOfferSC.OUT_CHARGE ? "sc.trade.service.charge" : "sc.trade.service.restore", what), x, y, w,
                GuiHoloSC.VALUE, 2);
        if (t != null) {
            boolean hand = mc.thePlayer != null && r.slot == mc.thePlayer.inventory.currentItem;
            TextFitSC.draw(fontRendererObj, Lang.tr(hand ? "sc.trade.service.taken.hand" : "sc.trade.service.taken.inv"), x, y, w, GuiHoloSC.OK,
                    guiLeft, guiTop);
            y += 10;
        } else if (o.anyLangKey() != null) {
            y = wrapped(Lang.tr("sc.trade.service.need", Lang.tr(o.anyLangKey())), x, y, w, GuiHoloSC.BAD, 2);
        }
        fontRendererObj.drawString(Lang.tr("sc.trade.give"), x, y, GuiHoloSC.LABEL);
        y += 10;
        for (int k = 1; k < o.in.length; k++) {
            ItemStack s = o.in[k].display();
            String line = r.have[k] + "/" + r.n[k] + " " + (s == null ? o.in[k].ore : s.getDisplayName());
            TextFitSC.draw(fontRendererObj, line, x, y, w, r.have[k] >= r.n[k] ? GuiHoloSC.OK : GuiHoloSC.BAD, guiLeft, guiTop);
            y += 10;
        }
        TextFitSC.draw(fontRendererObj, leftLine(r), x, y, w, GuiHoloSC.LABEL, guiLeft, guiTop);
        y += 10;
        String why = null;
        if (r.why == TradeServerSC.R_LEVEL) {
            why = Lang.tr("sc.trade.need.level", levelName(o.minLevel));
        } else if (r.why != TradeServerSC.OK) {
            why = reason(r.why);
        } else if (t == null) {
            why = reason(TradeServerSC.R_NO_ITEM);
        } else if (!canOnce(r)) {
            why = reason(TradeServerSC.R_MISSING);
        }
        if (why != null && y < bottom) {
            wrapped(why, x, y, w, r.why == TradeServerSC.OK ? GuiHoloSC.BAD : GuiHoloSC.WARN, Math.max(1, (bottom - y) / 9));
        }
    }

    /** The thing a service row will work on (the client's inventory), null none. */
    private ItemStack taken(Row r) {
        ItemStack[] inv = mc.thePlayer == null ? null : mc.thePlayer.inventory.mainInventory;
        return inv != null && r.slot >= 0 && r.slot < inv.length ? inv[r.slot] : null;
    }

    /** A matcher's icon: one of its choices, a new one every second («any matching»). */
    private static ItemStack cycle(List<ItemStack> choices) {
        return choices.isEmpty() ? null : choices.get((int) ((Minecraft.getSystemTime() / 1000L) % choices.size()));
    }

    /** The selected service's slot framed in the inventory below (what will be taken - the hand's is framed too). */
    private void frameTaken() {
        Row r = !contractsTab && sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
        if (r == null || !r.offer.service() || r.slot < 0 || r.slot >= 36) {
            return;
        }
        int x = ContainerTradeSC.INV_X + (r.slot % 9) * 18, y = r.slot < 9 ? ContainerTradeSC.HOTBAR_Y : ContainerTradeSC.INV_Y + (r.slot / 9 - 1) * 18;
        int c = 0xFF6EE6FF;
        drawRect(x - 1, y - 1, x + 17, y, c);
        drawRect(x - 1, y + 16, x + 17, y + 17, c);
        drawRect(x - 1, y, x, y + 16, c);
        drawRect(x + 16, y, x + 17, y + 16, c);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void drawContracts(int mx, int my) {
        for (int i = 0; i < ROWS; i++) {
            int idx = cscroll + i;
            if (idx >= crows.size()) {
                break;
            }
            CRow c = crows.get(idx);
            int y = LY + 2 + i * ROW_H;
            rowBack(idx == csel, y, mx, my);
            int tx = LX + 24, tw = LW - 8 - 24;
            boolean done = !c.anchor && c.st == TradeServerSC.C_DONE || c.anchor && c.st == TradeServerSC.ANCHOR_PAID;
            TextFitSC.draw(fontRendererObj, Lang.tr(c.c.langKey()), tx, y + 1, tw - (done ? 10 : 0), done ? GuiHoloSC.IDLE : GuiHoloSC.VALUE, guiLeft, guiTop);
            String sub;
            int col;
            if (c.anchor) {
                String[] k = {"open", "yours", "paid", "other"};
                sub = Lang.tr("sc.contract.anchor." + k[Math.max(0, Math.min(3, c.st))]);
                col = c.st == TradeServerSC.ANCHOR_YOURS ? GuiHoloSC.OK : GuiHoloSC.LABEL;
            } else if (c.st == TradeServerSC.C_FREE) {
                sub = Lang.tr("sc.contract.state.free", c.c.chips, c.c.reputation);
                col = GuiHoloSC.LABEL;
            } else if (c.st == TradeServerSC.C_OPEN) {
                boolean ready = c.progress >= c.c.target;
                sub = ready ? Lang.tr("sc.contract.state.ready") : Lang.tr("sc.contract.state.open", c.progress, c.c.target);
                col = ready || canTurnIn(c) ? GuiHoloSC.OK : GuiHoloSC.VALUE;
            } else {
                sub = Lang.tr("sc.contract.state.done");
                col = GuiHoloSC.IDLE;
            }
            TextFitSC.draw(fontRendererObj, sub, tx, y + 10, tw, col, guiLeft, guiTop);
            if (done) {
                bindTex();
                drawTexturedModalRect(LX + LW - 18, y + 2, CHECK_U, SPR_V, 9, 7);
            }
        }
        scrollbar(crows.size(), cscroll);
        CRow c = csel >= 0 && csel < crows.size() ? crows.get(csel) : null;
        if (c != null) {
            contractDetails(c);
        }
    }

    private void contractDetails(CRow c) {
        int x = RX + 4, w = RW - 8, y = detailsY() + 4, bottom = BY - 4;
        y = wrapped(Lang.tr(c.c.langKey()), x, y, w, GuiHoloSC.VALUE, 2);
        List<String> tail = new ArrayList<String>();
        tail.add(Lang.tr("sc.contract.reward", c.c.chips, c.c.reputation));
        if (c.c.modulePct > 0) {
            tail.add(Lang.tr("sc.contract.reward.module", c.c.modulePct));
        }
        if (!c.anchor && c.st == TradeServerSC.C_OPEN) {
            tail.add(c.c.handIn() != null ? Lang.tr("sc.contract.handInLine", c.progress, c.c.target, c.have)
                    : Lang.tr("sc.contract.state.open", c.progress, c.c.target));
        } else if (!c.anchor && c.st == TradeServerSC.C_FREE && level < ReputationSC.ACQUAINTANCE) {
            tail.add(Lang.tr("sc.trade.need.level", levelName(ReputationSC.ACQUAINTANCE)));
        }
        int descLines = Math.max(0, (bottom - y) / 9 - tail.size());
        y = wrapped(Lang.tr(c.c.langKey() + ".desc", c.c.target), x, y, w, GuiHoloSC.LABEL, descLines);
        for (int i = 0; i < tail.size(); i++) {
            int col = i == 0 ? GuiHoloSC.OK : i == tail.size() - 1 && level < ReputationSC.ACQUAINTANCE && c.st == TradeServerSC.C_FREE ? GuiHoloSC.WARN : GuiHoloSC.VALUE;
            TextFitSC.draw(fontRendererObj, tail.get(i), x, y, w, col, guiLeft, guiTop);
            y += 9;
        }
    }

    /** `text` wrapped to `w`, at most `max` lines (the last one cut with its full text as a tooltip). @return the next y */
    @SuppressWarnings("unchecked")
    private int wrapped(String text, int x, int y, int w, int colour, int max) {
        List<String> lines = fontRendererObj.listFormattedStringToWidth(text, w);
        for (int i = 0; i < lines.size() && i < max; i++) {
            String s = lines.get(i);
            if (i == max - 1 && lines.size() > max) {
                StringBuilder rest = new StringBuilder(s);
                for (int j = i + 1; j < lines.size(); j++) {
                    rest.append(' ').append(lines.get(j));
                }
                TextFitSC.draw(fontRendererObj, rest.toString(), x, y, w, colour, guiLeft, guiTop);
            } else {
                fontRendererObj.drawString(s, x, y, colour);
            }
            y += 9;
        }
        return y + 1;
    }

    private void rowBack(boolean selected, int y, int mx, int my) {
        boolean hover = mx - guiLeft >= LX + 2 && mx - guiLeft < LX + LW - 8 && my - guiTop >= y && my - guiTop < y + ROW_H;
        if (selected) {
            drawRect(LX + 2, y, LX + LW - 8, y + ROW_H, 0x5036AADC);
            drawRect(LX + 2, y, LX + 3, y + ROW_H, 0xFF6EE6FF);
        } else if (hover) {
            drawRect(LX + 2, y, LX + LW - 8, y + ROW_H, 0x2036AADC);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void scrollbar(int total, int at) {
        if (total <= ROWS) {
            return;
        }
        int x = LX + LW - 6, top = LY + 3, h = LH - 6;
        drawRect(x, top, x + 4, top + h, 0xFF0A1420);
        int knob = top + (h - 12) * at / Math.max(1, total - ROWS);
        bindTex();
        drawTexturedModalRect(x, knob, KNOB_U, SPR_V, 4, 12);
    }

    private void bindTex() {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(TEX);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        TextFitSC.beginFrame();
        super.drawScreen(mx, my, partial);
        ItemStack hovered = drawIcons(mx, my);
        if (hovered != null) {
            renderToolTip(hovered, mx, my);
        } else {
            List<String> tip = buttonTip(mx, my);
            if (tip == null) {
                tip = TextFitSC.hoverAt(mx, my);
            }
            if (tip != null) {
                drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mx, my, fontRendererObj);
            }
        }
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
    }

    /** «Обменять»: Shift - how many at most; a disabled contract button: why. */
    private List<String> buttonTip(int mx, int my) {
        if (tradeBtn != null && tradeBtn.visible && inside(tradeBtn, mx, my) && sel >= 0 && sel < rows.size()) {
            List<String> l = new ArrayList<String>();
            l.add(Lang.tr("sc.trade.btn.trade.tip", Math.max(1, maxTimes(rows.get(sel)))));
            return l;
        }
        return null;
    }

    private static boolean inside(GuiButton b, int mx, int my) {
        return mx >= b.xPosition && my >= b.yPosition && mx < b.xPosition + b.width && my < b.yPosition + b.height;
    }

    /** The item icons of the list (absolute coordinates, over the foreground). @return the stack under the mouse, or null */
    private ItemStack drawIcons(int mx, int my) {
        if (!has) {
            return null;
        }
        ItemStack hovered = null;
        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        itemRender.zLevel = 100F;
        List<int[]> counts = new ArrayList<int[]>();      // x, y, row, side, the out count, «any» (to draw after the icons, unlit)
        if (!contractsTab) {
            for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
                Row r = rows.get(scroll + i);
                int y = guiTop + LY + 2 + i * ROW_H + 1;
                ItemStack first = null;
                for (int k = 0; k < r.offer.in.length && k < 2; k++) {
                    int x = guiLeft + LX + 3 + k * 17;
                    TradeOfferSC.Ingredient side = r.offer.in[k];
                    ItemStack t = k == 0 ? taken(r) : null;
                    ItemStack s = t != null ? t : side.any() ? cycle(side.choices()) : side.display();
                    if (k == 0) {
                        first = s;
                    }
                    hovered = icon(s, x, y, mx, my, hovered);
                    counts.add(new int[]{x, y, scroll + i, k, 0, side.any() && t == null ? 1 : 0});
                }
                ItemStack out = r.offer.service() ? TradeServicesSC.result(r.offer, first) : outStack(r);
                int ox = guiLeft + LX + 49;
                if (out != null) {
                    hovered = icon(out, ox, y, mx, my, hovered);
                    counts.add(new int[]{ox, y, scroll + i, -1, out.stackSize, 0});
                }
            }
        } else {
            for (int i = 0; i < ROWS && cscroll + i < crows.size(); i++) {
                CRow c = crows.get(cscroll + i);
                hovered = icon(contractIcon(c), guiLeft + LX + 4, guiTop + LY + 2 + i * ROW_H + 1, mx, my, hovered);
            }
        }
        itemRender.zLevel = 0F;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int[] c : counts) {
            Row r = rows.get(c[2]);
            String text;
            int colour = 0xFFFFFF;
            if (c[3] < 0) {
                int n = c[4];
                text = n > 1 ? String.valueOf(n) : null;
            } else {
                text = String.valueOf(r.n[c[3]]);
                colour = r.have[c[3]] >= r.n[c[3]] ? 0xFFFFFF : 0xFF6A60;
            }
            if (text != null && (c[3] >= 0 ? r.n[c[3]] > 1 || colour != 0xFFFFFF : true)) {
                fontRendererObj.drawStringWithShadow(text, c[0] + 17 - fontRendererObj.getStringWidth(text), c[1] + 9, colour);
            }
            if (c[5] != 0) {
                fontRendererObj.drawStringWithShadow("*", c[0], c[1] - 1, 0x6EE6FF);        // «any matching»: the icon is one of them
            }
            if (r.why != TradeServerSC.OK) {
                drawRect(c[0], c[1], c[0] + 16, c[1] + 16, 0x900A1420);      // dimmed: not open to this player
            }
        }
        if (!contractsTab) {
            for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
                Row r = rows.get(scroll + i);
                if (r.offer.outKind == TradeOfferSC.OUT_REPUTATION) {   // the badge's amount, and its tooltip
                    int x = guiLeft + LX + 49, y = guiTop + LY + 2 + i * ROW_H + 1;
                    String t = "+" + r.offer.outAmount;
                    fontRendererObj.drawStringWithShadow(t, x + 17 - fontRendererObj.getStringWidth(t), y + 9, 0xFFFFFF);
                    TextFitSC.hover(x, y, 16, 16, Lang.tr("sc.trade.out.rep", r.offer.outAmount));
                }
            }
        }
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
        return hovered;
    }

    private ItemStack icon(ItemStack s, int x, int y, int mx, int my, ItemStack hovered) {
        if (s == null || s.getItem() == null) {
            return hovered;
        }
        ItemStack one = s.copy();
        one.stackSize = 1;
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), one, x, y);
        return mx >= x && my >= y && mx < x + 16 && my < y + 16 ? one : hovered;
    }

    /** What the row's deal gives out, its count as the server said (a sale's chips by the level - TradeRulesSC.outCount). */
    private static ItemStack outStack(Row r) {
        ItemStack s = r.offer.outStack();
        if (s != null && r.offer.outKind == TradeOfferSC.OUT_ITEM && r.out > 0) {
            s.stackSize = r.out;
        }
        return s;
    }

    /** A contract's picture: a sword (kill), the thing handed in (deliver, cargo), the ПЭ-1 (suppress), the anchor. */
    private static ItemStack contractIcon(CRow c) {
        if (c.anchor) {
            return ModBlocks.bridge == null ? null : new ItemStack(ModBlocks.bridge, 1, BlockBridgeSC.ANCHOR);
        }
        switch (c.c.goal) {
            case ContractTableSC.KILL:
                return new ItemStack(Items.iron_sword);
            case ContractTableSC.SUPPRESS_KILL:
                return ModItems.empGun == null ? null : new ItemStack(ModItems.empGun);
            default:
                TradeOfferSC.Ingredient in = c.c.handIn();
                return in == null ? null : in.display();
        }
    }
}
