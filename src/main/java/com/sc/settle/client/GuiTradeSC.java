package com.sc.settle.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.sc.Reference;
import com.sc.block.BlockBridgeSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.embassy.FlightTargetsSC;
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
import com.sc.tileentity.TileEntityEmbassyRingSC;

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
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §7.2, rules ПЯ-1..ПЯ-14; package R - this file is R's in Ф6): the Dispatcher's «Рейсы» tab
 * (his screen opens on it; the «Сделки» tab shows only if he has deals) - what TradeClientSC last heard of FLIGHT_STATE: a row a ride
 * and target this Dispatcher sells (its icon, name, the price for this player - red when the inventory holds fewer chips; a closed
 * one greyed with its reason: EmbassyRulesSC.refuseKey, a level lock), the chosen one's details (the target, one way / the round
 * trip / for a friend, why not, the window's phase and its timer - counted down here, the server's FLIGHT_STATE corrects it at every
 * change), «Купить», «Туда-обратно», «За друга» (the friend line cycles through the players near the Dispatcher with its screen
 * open, a click; a guarantor's screen asks for the state every FRIENDS_POLL ticks - a friend who comes up is listed), «Лететь» (by
 * the player's own talon of this place); a talon a guarantor paid turns «Купить» into «Отказаться» (FLIGHT_DO decline - the chips
 * go back to him); the soft admission's warning (R_CONFIRM) turns the pressed button into «Лететь всё
 * равно» for CONFIRM_TICKS. The status line: the last answer, the talon, the flights left today. The title says «Посол» for an
 * embassy's Elder: NpcKindSC.langKey(SettlementClientSC.embassy(tuner)) - the client knows the world by its provider. At an embassy the
 * contracts are taken from the state's "cLevel" and the anchor row is left out ("noAnchor"); the dispatch's icon is a paper.
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
    /** Ф6: the level this Elder's contracts are taken from (the state's "cLevel": the Ambassador's «Чужак»; older states - «Знакомый»). */
    private int cLevel = ReputationSC.ACQUAINTANCE;
    private long stateDay = -1;
    private int seenState = -1, seenResult = -1, waited, sel, csel, scroll, cscroll;
    private String resultText = "";
    private int resultColour, resultTicks;
    private GuiButton tradeBtn, contractBtn, dropBtn, dealsTab, contractsTabBtn;

    // ------------------------------------------------------------------ Ф6: the Dispatcher's «Рейсы» (package R)

    private static final int FL_BUY = 3, FL_ROUND = 4, FL_FRIEND = 5, FL_GO = 6, TAB_FLIGHTS = 12;
    /** The flights' buttons: two rows, the upper one this far over BY. */
    private static final int FLY = BY - BH - 2;
    /** The status line's colours: a warning (the soft admission). */
    private static final int TEXT_WARN = 0x9A5A00;
    /** A guarantor's «Рейсы» tab asks for the state this often (ticks): the players near the Dispatcher change. */
    private static final int FRIENDS_POLL = 40;

    /** One ride and target as the server priced it for this player. */
    private static final class FRow {
        int ride, target, name, x, z, price, round, guar, code;
        boolean embassy, xz;
    }

    private final List<FRow> frows = new ArrayList<FRow>();
    /** The players near the Dispatcher: {name, UUID}; their levels here. */
    private final List<String[]> friends = new ArrayList<String[]>();
    private final List<Integer> friendLevels = new ArrayList<Integer>();
    private boolean fhas, flightsTab = true, guarantor, talonHere, talonSoldAtEmbassy, talonGift;
    private int fplace = FlightTargetsSC.PLACE_NONE, fleft, fmax = EmbassyRulesSC.flightsPerDay(), guarLeft, fphase, fticks, fpass;
    private int seenFState = -1, seenFResult = -1, fwaited, fsel, fscroll, friend, myChips, refreshIn, fpoll;
    private int talonRide = -1, talonSoldAt, backDays, backId = -1;
    /** The soft admission: the button that was warned, ticks left to press it again, the request it sends then. */
    private int confirmBtn = -1, confirmTicks, lastBtn = -1;
    private NBTTagCompound lastReq, confirmReq;
    /** Where the details' friend line was drawn (relative y; -1 none) - a click there picks the next friend. */
    private int friendLineY = -1;
    private GuiButton buyBtn, roundBtn, friendBtn, goBtn, flightsTabBtn;

    public GuiTradeSC(EntityPlayer player, EntityTunerSC tuner) {
        super(new ContainerTradeSC(player, tuner));
        this.tuner = tuner;
        xSize = W;
        ySize = H;
    }

    private boolean elder() {
        return tuner.kind() == NpcKindSC.ELDER;
    }

    /** Ф6: a Dispatcher - his screen is the «Рейсы» tab. */
    private boolean dispatcher() {
        return tuner.kind() == NpcKindSC.DISPATCHER;
    }

    /** Ф6: a Dispatcher with deals shows both tabs (none: the flights only). */
    private boolean dispatcherTabs() {
        return dispatcher() && !rows.isEmpty();
    }

    /** Ф6: the flights are on the screen. */
    private boolean flights() {
        return dispatcher() && (flightsTab || rows.isEmpty());
    }

    private int detailsY() {
        return elder() || dispatcherTabs() ? TAB_Y + TAB_H + 2 : TAB_Y;
    }

    /** Where the details' screen ends (the flights have two rows of buttons). */
    private int detailsBottom() {
        return flights() ? FLY - 2 : BY - 2;
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
        if (dispatcher()) {                                     // Ф6: the flights' buttons and (with deals) the tabs
            int half = RW / 2 - 1;
            buyBtn = new TextFitSC.Button(FL_BUY, x, guiTop + FLY, half, BH, Lang.tr("sc.flight.buy"));
            roundBtn = new TextFitSC.Button(FL_ROUND, x + RW / 2 + 1, guiTop + FLY, half, BH, Lang.tr("sc.flight.round"));
            friendBtn = new TextFitSC.Button(FL_FRIEND, x, y, half, BH, Lang.tr("sc.flight.friend"));
            goBtn = new TextFitSC.Button(FL_GO, x + RW / 2 + 1, y, half, BH, Lang.tr("sc.flight.go"));
            buttonList.add(buyBtn);
            buttonList.add(roundBtn);
            buttonList.add(friendBtn);
            buttonList.add(goBtn);
            dealsTab = new TextFitSC.Tab(TAB_DEALS, x, guiTop + TAB_Y, half, TAB_H, SettlementItemsSC.chips(1),
                    Lang.tr("sc.trade.tab.deals"), Lang.tr("sc.trade.tab.deals"));
            flightsTabBtn = new TextFitSC.Tab(TAB_FLIGHTS, x + RW / 2 + 1, guiTop + TAB_Y, half, TAB_H, new ItemStack(Items.ender_pearl),
                    Lang.tr("sc.flight.tab"), Lang.tr("sc.flight.tab"));
            buttonList.add(dealsTab);
            buttonList.add(flightsTabBtn);
        }
        pickUp();
        pickUpFlights();
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
            if (dispatcher()) {
                SettleNetSC.sendToServer(SettleNetSC.FLIGHT_REFRESH, new NBTTagCompound());   // the talons, the day's flights
            }
        }
        if (dispatcher()) {
            flightTick();
        }
        countHave();
        if (resultTicks > 0) {
            resultTicks--;
        }
        updateButtons();
    }

    /** Ф6: the flights' state asked for if lost, the window's timer counted on, the soft admission's confirmation running out. */
    private void flightTick() {
        pickUpFlights();
        if (!fhas && ++fwaited == ASK_AFTER) {
            SettleNetSC.sendToServer(SettleNetSC.FLIGHT_REFRESH, new NBTTagCompound());
        }
        if (fticks > 0) {
            fticks--;
        } else if (fphase != EmbassyRulesSC.PHASE_IDLE) {
            fphase = fphase == EmbassyRulesSC.PHASE_COOL ? EmbassyRulesSC.PHASE_IDLE : fphase + 1;    // on to the next; the server confirms
            fticks = TileEntityEmbassyRingSC.length(fphase);
            refreshIn = ASK_AFTER;
        }
        if (refreshIn > 0 && --refreshIn == 0) {
            SettleNetSC.sendToServer(SettleNetSC.FLIGHT_REFRESH, new NBTTagCompound());
        } else if (fhas && guarantor && flights() && ++fpoll >= FRIENDS_POLL) {
            fpoll = 0;                                          // the friends near (who came, who left), their levels, the window's passengers
            SettleNetSC.sendToServer(SettleNetSC.FLIGHT_REFRESH, new NBTTagCompound());
        }
        if (confirmTicks > 0 && --confirmTicks == 0) {
            confirmBtn = -1;
            confirmReq = null;
        }
        myChips = TradeOfferSC.Ingredient.chips(1).countIn(mc.thePlayer == null ? null : mc.thePlayer.inventory.mainInventory);
    }

    /** Ф6: the newest FLIGHT_STATE / FLIGHT_RESULT for this Dispatcher, if any came since the last look. */
    private void pickUpFlights() {
        if (!dispatcher()) {
            return;
        }
        int seq = TradeClientSC.flightStateSeq();
        if (seq != seenFState) {
            seenFState = seq;
            NBTTagCompound s = TradeClientSC.flightState();
            if (s != null && s.getInteger("tuner") == tuner.getEntityId()) {
                parseFlights(s);
            }
        }
        seq = TradeClientSC.flightResultSeq();
        if (seq != seenFResult) {
            seenFResult = seq;
            NBTTagCompound r = TradeClientSC.flightResult();
            if (r != null && r.getInteger("tuner") == tuner.getEntityId()) {
                answerFlight(r);
            }
        }
    }

    private void parseFlights(NBTTagCompound s) {
        fplace = s.getByte("place");
        fleft = s.getInteger("left");
        fmax = s.hasKey("max") ? s.getInteger("max") : EmbassyRulesSC.flightsPerDay();   // ПС-8
        guarantor = s.getBoolean("guarantor");
        guarLeft = s.getInteger("guarLeft");
        frows.clear();
        NBTTagList l = s.getTagList("rides", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            FRow r = new FRow();
            r.ride = e.getByte("r");
            r.target = e.getInteger("t");
            r.name = e.getInteger("n");
            r.embassy = e.getBoolean("e");
            r.xz = e.getBoolean("xz");
            r.x = e.getInteger("x");
            r.z = e.getInteger("z");
            r.price = e.getInteger("p");
            r.round = e.getInteger("o");
            r.guar = e.getInteger("g");
            r.code = e.getByte("c");
            if (EmbassyRulesSC.valid(r.ride)) {
                frows.add(r);
            }
        }
        backId = -1;
        if (s.hasKey("back", 10)) {
            NBTTagCompound b = s.getCompoundTag("back");
            backId = b.getInteger("e");
            backDays = b.getInteger("d");
        }
        talonRide = -1;
        talonGift = false;
        if (s.hasKey("talon", 10)) {
            NBTTagCompound t = s.getCompoundTag("talon");
            talonRide = t.getInteger("r");
            talonHere = t.getBoolean("here");
            talonGift = t.getBoolean("g");
            talonSoldAt = t.getInteger("sn");
            talonSoldAtEmbassy = t.getBoolean("se");
        }
        fphase = s.getByte("phase");
        fticks = s.getInteger("ticks");
        fpass = s.getInteger("pass");
        refreshIn = 0;
        String picked = friend >= 0 && friend < friends.size() ? friends.get(friend)[1] : "";
        friends.clear();
        friendLevels.clear();
        NBTTagList f = s.getTagList("friends", 10);
        for (int i = 0; i < f.tagCount(); i++) {
            NBTTagCompound e = f.getCompoundTagAt(i);
            friends.add(new String[]{e.getString("n"), e.getString("u")});
            friendLevels.add(Integer.valueOf(e.getByte("l")));
            if (e.getString("u").equals(picked)) {
                friend = i;                                     // the same friend stays picked
            }
        }
        friend = friends.isEmpty() ? 0 : Math.max(0, Math.min(friends.size() - 1, friend));
        fhas = true;
        fsel = clamp(fsel, frows.size());
        fscroll = Math.max(0, Math.min(fscroll, frows.size() - ROWS));
    }

    /** A FLIGHT_RESULT: the status line's text; the soft admission's warning arms the pressed button for the second click. */
    private void answerFlight(NBTTagCompound r) {
        int code = r.getInteger("code");
        String text;
        int colour;
        if (code == EmbassyRulesSC.R_CONFIRM) {
            boolean armour = r.getBoolean("noArmour"), capsule = r.getBoolean("noCapsule");
            String what = Lang.tr(armour && capsule ? "sc.flight.warn.both" : armour ? "sc.flight.warn.armour" : "sc.flight.warn.capsule");
            String friendName = r.getString("friend");
            text = friendName.isEmpty() ? Lang.tr("sc.flight.warn", what) : Lang.tr("sc.flight.warn.friend", friendName, what);
            colour = TEXT_WARN;
            confirmBtn = lastBtn;
            confirmReq = lastReq;
            confirmTicks = Math.max(20, EmbassyRulesSC.CONFIRM_TICKS - 10);   // the server's 5 s, less the way there and back
        } else if (code == EmbassyRulesSC.OK) {
            boolean ring = r.getByte("place") == FlightTargetsSC.PLACE_EMBASSY;
            String chips = String.valueOf(r.getInteger("chips"));
            if (r.getBoolean("go")) {
                text = Lang.tr(ring ? "sc.flight.boarding.ring" : "sc.flight.boarding.dock");
            } else if (r.getBoolean("decline")) {
                text = sentGiveBack ? Lang.tr("sc.flight.refund", chips) : Lang.tr("sc.flight.declined");   // ПС-3
                sentGiveBack = false;
            } else if (!r.getString("friend").isEmpty()) {
                text = Lang.tr("sc.flight.bought.friend", r.getString("friend"), chips);
            } else {
                text = Lang.tr(ring ? "sc.flight.bought.ring" : "sc.flight.bought.dock", Lang.tr(EmbassyRulesSC.rideKey(r.getInteger("ride"))), chips);
            }
            colour = 0x1E7A2E;
            confirmTicks = 0;
            confirmBtn = -1;
        } else {
            text = Lang.tr(EmbassyRulesSC.refuseKey(code));
            colour = 0xA02018;
            confirmTicks = 0;
            confirmBtn = -1;
        }
        resultText = text;
        resultColour = colour;
        resultTicks = Math.max(RESULT_TICKS, confirmTicks);
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
            cLevel = s.hasKey("cLevel") ? s.getByte("cLevel") : ReputationSC.ACQUAINTANCE;
            if (!s.getBoolean("noAnchor")) {                     // Ф6: no anchor contract at an embassy
                CRow a = new CRow();
                a.c = ContractTableSC.ANCHOR_CONTRACT;
                a.anchor = true;
                a.st = anchor;
                crows.add(a);
            }
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

    /** ПС-5 (audit 2026-10-20): a gain of «Доверие» as it lands here - an embassy stops it at its ceiling. */
    private int gainHere(int amount) {
        return SettlementClientSC.embassy(tuner) ? ReputationSC.gainUnder(rep, amount, ReputationSC.EMBASSY_CEILING) : amount;
    }

    /** ПС-5: the gain's text for "+%s" - "50", or with the embassy's ceiling cutting it "39 (ceiling 599)". */
    private String gainArg(int amount) {
        int eff = gainHere(amount);
        return eff < amount ? Lang.tr("sc.trade.rep.capped", eff, ReputationSC.EMBASSY_CEILING) : String.valueOf(amount);
    }

    private static String reason(int code) {
        if (code == TradeServerSC.R_CEILING) {
            return Lang.tr("sc.trade.refuse.ceiling", ReputationSC.EMBASSY_CEILING);   // ПС-5
        }
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
        boolean deals = !contractsTab && !flights();
        flightButtons();
        tradeBtn.visible = deals;
        Row r = deals && sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
        tradeBtn.enabled = r != null && canOnce(r);
        contractBtn.visible = dropBtn.visible = contractsTab && !flights();
        CRow c = contractsTab && csel >= 0 && csel < crows.size() ? crows.get(csel) : null;
        boolean take = c != null && !c.anchor && c.st == TradeServerSC.C_FREE;
        contractBtn.displayString = Lang.tr(take ? "sc.trade.btn.take" : "sc.trade.btn.turnIn");
        contractBtn.enabled = c != null && (take ? taken < TradeRulesSC.CONTRACTS_PER_DAY && level >= cLevel : canTurnIn(c));
        dropBtn.enabled = c != null && !c.anchor && c.st == TradeServerSC.C_OPEN;
        if (dealsTab != null && contractsTabBtn != null) {
            dealsTab.enabled = contractsTab;
            contractsTabBtn.enabled = !contractsTab;
        }
    }

    /** Ф6: the flights' buttons - shown on the «Рейсы» tab, lit when the server's numbers and the inventory allow it. */
    private void flightButtons() {
        if (buyBtn == null) {
            return;
        }
        boolean fl = flights();
        buyBtn.visible = roundBtn.visible = friendBtn.visible = goBtn.visible = fl;
        if (dealsTab != null && flightsTabBtn != null) {
            dealsTab.visible = flightsTabBtn.visible = dispatcherTabs();
            dealsTab.enabled = fl;
            flightsTabBtn.enabled = !fl;
        }
        FRow r = fsel >= 0 && fsel < frows.size() ? frows.get(fsel) : null;
        boolean open = fhas && r != null && r.code == EmbassyRulesSC.OK;
        boolean back = r != null && r.ride == EmbassyRulesSC.RIDE_BACK;
        buyBtn.displayString = Lang.tr(declining() ? "sc.flight.decline" : givingBack() ? "sc.flight.giveback" : back ? "sc.flight.useBack"
                : "sc.flight.buy");
        buyBtn.enabled = declining() || givingBack() || open && (back || myChips >= r.price);
        roundBtn.displayString = Lang.tr("sc.flight.round");
        roundBtn.enabled = open && r.round > 0 && myChips >= r.round && backId < 0;   // ПЯ-12: one return half at a time
        friendBtn.displayString = Lang.tr("sc.flight.friend");
        friendBtn.enabled = fhas && r != null && EmbassyRulesSC.outbound(r.ride) && guarantor && guarLeft > 0 && !friends.isEmpty() && r.guar > 0
                && myChips >= r.guar;
        goBtn.displayString = Lang.tr("sc.flight.go");
        goBtn.enabled = fhas && talonRide >= 0 && talonHere && fphase != EmbassyRulesSC.PHASE_COOL;
        if (confirmTicks > 0 && confirmBtn >= 0) {             // ПЯ-14: the warned button, once more within the server's 5 s
            GuiButton b = confirmBtn == FL_BUY ? buyBtn : confirmBtn == FL_ROUND ? roundBtn : confirmBtn == FL_FRIEND ? friendBtn : null;
            if (b != null) {
                b.displayString = Lang.tr("sc.flight.confirm");
                b.enabled = true;
            }
        }
    }

    /** ПЯ-13: this place's talon was paid by a guarantor - «Купить» is «Отказаться» (no purchase while a talon waits anyway). */
    private boolean declining() {
        return fhas && talonRide >= 0 && talonHere && talonGift;
    }

    /** ПС-3 (audit 2026-10-20): the player's own talon of this Dispatcher - «Купить» becomes «Сдать талон». */
    private boolean givingBack() {
        return fhas && talonRide >= 0 && talonHere && !talonGift;
    }

    /** ПС-3: the last FLIGHT_DO was «Сдать талон» (its answer comes as a decline's). */
    private boolean sentGiveBack;

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
            case TAB_FLIGHTS:
                contractsTab = b.id == TAB_CONTRACTS;
                flightsTab = b.id == TAB_FLIGHTS;
                updateButtons();
                return;
            case FL_BUY:
            case FL_ROUND:
            case FL_FRIEND:
            case FL_GO:
                flightAction(b.id);
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

    /** Ф6: a flights' button - FLIGHT_DO for the chosen row (the warned button once more: the same request, confirmed). */
    private void flightAction(int id) {
        if (id == confirmBtn && confirmTicks > 0 && confirmReq != null) {
            NBTTagCompound d = (NBTTagCompound) confirmReq.copy();
            d.setBoolean("confirm", true);
            confirmTicks = 0;
            confirmBtn = -1;
            send(id, d);
            return;
        }
        NBTTagCompound d = new NBTTagCompound();
        if (id == FL_GO) {
            d.setBoolean("go", true);
            send(id, d);
            return;
        }
        if (id == FL_BUY && (declining() || givingBack())) {
            sentGiveBack = givingBack();                        // ПС-3: one's own talon handed back (FlightRunSC.decline -> giveBack)
            d.setBoolean("decline", true);                      // the guarantor's talon back to him
            send(id, d);
            return;
        }
        FRow r = fsel >= 0 && fsel < frows.size() ? frows.get(fsel) : null;
        if (r == null) {
            return;
        }
        d.setInteger("ride", r.ride);
        d.setInteger("target", r.target);
        d.setBoolean("round", id == FL_ROUND);
        d.setString("friend", id == FL_FRIEND && friend >= 0 && friend < friends.size() ? friends.get(friend)[1] : "");
        d.setBoolean("confirm", false);
        send(id, d);
    }

    private void send(int btn, NBTTagCompound d) {
        lastBtn = btn;
        lastReq = d;
        SettleNetSC.sendToServer(SettleNetSC.FLIGHT_DO, d);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        if (flights()) {
            flightClick(mx, my, button);
            return;
        }
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

    /** Ф6: a click on the flights' list (a row) or on the details' friend line (the next friend). */
    private void flightClick(int mx, int my, int button) {
        if (button != 0) {
            return;
        }
        int rx = mx - guiLeft, ry = my - guiTop;
        if (friendLineY >= 0 && rx >= RX + 2 && rx < RX + RW - 2 && ry >= friendLineY - 1 && ry < friendLineY + 9 && friends.size() > 1) {
            friend = (friend + 1) % friends.size();
            confirmTicks = 0;
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            updateButtons();
            return;
        }
        int x = rx - LX, y = ry - LY - 2;
        if (x < 2 || x >= LW - 8 || y < 0 || y >= ROWS * ROW_H) {
            return;
        }
        int i = fscroll + y / ROW_H;
        if (i < frows.size()) {
            fsel = i;
            confirmTicks = 0;                                   // another ride: the warning was for the last one
            confirmBtn = -1;
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            updateButtons();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && flights()) {
            fscroll = Math.max(0, Math.min(Math.max(0, frows.size() - ROWS), fscroll + (wheel > 0 ? -1 : 1)));
        } else if (wheel != 0) {
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
        GuiHoloSC.screen(guiLeft + RX, guiTop + dy, RW, detailsBottom() - dy);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Everything but the item icons (drawScreen draws those over it, as vanilla's merchant does). */
    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        // the title: name · profession (Ф6: an embassy's Elder is its «Посол» - the client knows the world by its provider); level · «Доверие»
        String name = NpcNamesSC.name(tuner.nameCode()).getUnformattedText() + " • " + Lang.tr(tuner.kind().langKey(SettlementClientSC.embassy(tuner)));
        String lv = has ? levelName(level) + " • " + rep : "…";
        int lvW = Math.min(90, fontRendererObj.getStringWidth(lv));
        TextFitSC.draw(fontRendererObj, name, 9, 6, W - 22 - lvW, 0xE6F0FA, guiLeft, guiTop);
        TextFitSC.draw(fontRendererObj, lv, W - 8 - lvW, 6, lvW, has ? LEVEL_COLOURS[Math.max(0, Math.min(6, level))] : 0x8898A8, guiLeft, guiTop);
        TextFitSC.hover(guiLeft + 4, guiTop + 4, W - 8, 11, titleTip());
        friendLineY = -1;
        if (flights()) {
            drawFlights(mx, my);
        } else if (!has) {
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
        } else if (flights()) {
            left = flightStatus();
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
        int outCol = GuiHoloSC.VALUE;
        if (o.outKind == TradeOfferSC.OUT_REPUTATION) {
            out = Lang.tr("sc.trade.out.rep", gainArg(o.outAmount));    // ПС-5: at an embassy - what its ceiling leaves
            outCol = gainHere(o.outAmount) <= 0 ? GuiHoloSC.IDLE : outCol;
        } else {
            ItemStack s = outStack(r);
            out = s == null ? "?" : Lang.tr("sc.trade.count", s.stackSize, s.getDisplayName());
        }
        y = wrapped(out, x, y, w, outCol, 2);
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

    /**
     * The selected service's slot framed in the inventory below (what will be taken - the hand's is framed too). ПС-4 (audit
     * 2026-10-20): a deal's and a contract's too - the slots their sides are taken from, in the server's order (TradeOfferSC
     * takePlan: vanilla logs before another mod's precious ones).
     */
    private void frameTaken() {
        ItemStack[] inv = mc.thePlayer == null ? null : mc.thePlayer.inventory.mainInventory;
        if (inv == null || flights()) {
            return;
        }
        if (contractsTab) {
            CRow c = csel >= 0 && csel < crows.size() ? crows.get(csel) : null;
            TradeOfferSC.Ingredient in = c == null || c.anchor ? null : c.c.handIn();
            if (in != null) {
                int need = Math.max(0, c.c.target - c.progress);
                frameSlots(in.takePlan(inv, Math.min(need, in.countIn(inv)), -1));
            }
            return;
        }
        Row r = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
        if (r == null) {
            return;
        }
        if (r.offer.service()) {
            if (r.slot >= 0 && r.slot < 36) {
                frameSlots(new int[]{r.slot});
            }
        }
        for (int k = r.offer.service() ? 1 : 0; r.n != null && k < r.offer.in.length && k < r.n.length; k++) {
            TradeOfferSC.Ingredient in = r.offer.in[k];
            if (in != null && in.countIn(inv) >= r.n[k]) {
                frameSlots(in.takePlan(inv, r.n[k], in.any() ? mc.thePlayer.inventory.currentItem : -1));
            }
        }
    }

    /** Frames main-inventory slots `slots` (0..35) as frameTaken does. */
    private void frameSlots(int[] slots) {
        int c = 0xFF6EE6FF;
        for (int s : slots) {
            if (s < 0 || s >= 36) {
                continue;
            }
            int x = ContainerTradeSC.INV_X + (s % 9) * 18, y = s < 9 ? ContainerTradeSC.HOTBAR_Y : ContainerTradeSC.INV_Y + (s / 9 - 1) * 18;
            drawRect(x - 1, y - 1, x + 17, y, c);
            drawRect(x - 1, y + 16, x + 17, y + 17, c);
            drawRect(x - 1, y, x, y + 16, c);
            drawRect(x + 16, y, x + 17, y + 16, c);
        }
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
                sub = Lang.tr("sc.contract.state.free", c.c.chips, gainArg(c.c.reputation));   // ПС-5
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
        tail.add(Lang.tr("sc.contract.reward", c.c.chips, gainArg(c.c.reputation)));   // ПС-5
        if (c.c.modulePct > 0) {
            tail.add(Lang.tr("sc.contract.reward.module", c.c.modulePct));
        }
        if (!c.anchor && c.st == TradeServerSC.C_OPEN) {
            tail.add(c.c.handIn() != null ? Lang.tr("sc.contract.handInLine", c.progress, c.c.target, c.have)
                    : Lang.tr("sc.contract.state.open", c.progress, c.c.target));
        } else if (!c.anchor && c.st == TradeServerSC.C_FREE && level < cLevel) {
            tail.add(Lang.tr("sc.trade.need.level", levelName(cLevel)));
        }
        int descLines = Math.max(0, (bottom - y) / 9 - tail.size());
        y = wrapped(Lang.tr(c.c.langKey() + ".desc", c.c.target), x, y, w, GuiHoloSC.LABEL, descLines);
        for (int i = 0; i < tail.size(); i++) {
            int col = i == 0 ? GuiHoloSC.OK : i == tail.size() - 1 && level < cLevel && c.st == TradeServerSC.C_FREE ? GuiHoloSC.WARN : GuiHoloSC.VALUE;
            TextFitSC.draw(fontRendererObj, tail.get(i), x, y, w, col, guiLeft, guiTop);
            y += 9;
        }
    }

    // ================================================================== Ф6: the flights (package R)

    /** The «Рейсы» list: a row a ride and target - its name and price, the target or why it is closed; the chosen one's details. */
    private void drawFlights(int mx, int my) {
        if (!fhas) {
            fontRendererObj.drawString(Lang.tr("sc.trade.wait"), LX + 6, LY + 6, GuiHoloSC.IDLE);
            return;
        }
        if (frows.isEmpty()) {
            TextFitSC.draw(fontRendererObj, Lang.tr("sc.flight.none"), LX + 6, LY + 6, LW - 12, GuiHoloSC.IDLE, guiLeft, guiTop);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = fscroll + i;
            if (idx >= frows.size()) {
                break;
            }
            FRow r = frows.get(idx);
            int y = LY + 2 + i * ROW_H;
            rowBack(idx == fsel, y, mx, my);
            boolean open = r.code == EmbassyRulesSC.OK;
            int tx = LX + 22, tw = LW - 8 - 22;
            String price = r.ride == EmbassyRulesSC.RIDE_BACK ? Lang.tr("sc.flight.price.paid") : r.price > 0 ? Lang.tr("sc.flight.price", r.price) : "";
            int pw = open && !price.isEmpty() ? fontRendererObj.getStringWidth(price) : 0;
            TextFitSC.draw(fontRendererObj, Lang.tr(EmbassyRulesSC.rideKey(r.ride)), tx, y + 1, tw - (pw > 0 ? pw + 4 : 0), open ? GuiHoloSC.VALUE : GuiHoloSC.IDLE,
                    guiLeft, guiTop);
            if (pw > 0) {
                boolean afford = r.ride == EmbassyRulesSC.RIDE_BACK || myChips >= r.price;
                fontRendererObj.drawString(price, LX + LW - 10 - pw, y + 1, afford ? GuiHoloSC.OK : GuiHoloSC.BAD);
            }
            if (r.code == EmbassyRulesSC.R_LEVEL) {
                bindTex();
                drawTexturedModalRect(tx, y + 9, LOCK_U, SPR_V, 7, 9);
                TextFitSC.draw(fontRendererObj, levelName(EmbassyRulesSC.levelOf(r.ride)), tx + 9, y + 10, tw - 9, GuiHoloSC.WARN, guiLeft, guiTop);
                continue;
            }
            String sub = open ? targetText(r) : Lang.tr(EmbassyRulesSC.refuseKey(r.code));
            if (!open && r.target >= 0 && (r.ride == EmbassyRulesSC.RIDE_DOCK || r.ride == EmbassyRulesSC.RIDE_OTHER)) {
                sub = targetText(r) + " - " + sub;              // a row a target: which one is closed
            }
            TextFitSC.draw(fontRendererObj, sub, tx, y + 10, tw, open ? GuiHoloSC.LABEL : GuiHoloSC.IDLE, guiLeft, guiTop);
        }
        scrollbar(frows.size(), fscroll);
        FRow r = fsel >= 0 && fsel < frows.size() ? frows.get(fsel) : null;
        if (r != null) {
            flightDetails(r);
        } else {
            phaseLine(RX + 4, detailsBottom() - 11, RW - 8);
        }
    }

    /**
     * The chosen ride: its name, its target (one line; the coordinates in the tooltip); open - its prices (one way, the round trip),
     * closed - why; the friend line (the guarantee); the window at the bottom.
     */
    private void flightDetails(FRow r) {
        int x = RX + 4, w = RW - 8, y = detailsY() + 4, bottom = detailsBottom() - 12;
        y = wrapped(Lang.tr(EmbassyRulesSC.rideKey(r.ride)), x, y, w, GuiHoloSC.VALUE, 2);
        y = line(targetText(r), x, y, w, GuiHoloSC.LABEL, bottom);
        if (r.xz && r.target != FlightTargetsSC.NO_TARGET) {
            TextFitSC.hover(guiLeft + x, guiTop + y - 9, w, 9, Lang.tr("sc.flight.target.at", r.x, r.z));
        }
        boolean friendLine = EmbassyRulesSC.outbound(r.ride) && guarantor && r.guar > 0;
        if (r.code == EmbassyRulesSC.OK) {
            if (r.ride == EmbassyRulesSC.RIDE_BACK) {
                y = line(Lang.tr("sc.flight.price.paid"), x, y, w, GuiHoloSC.OK, bottom);
            } else if (r.price > 0) {
                y = line(Lang.tr("sc.flight.price.one", r.price), x, y, w, myChips >= r.price ? GuiHoloSC.OK : GuiHoloSC.BAD, bottom);
            }
            if (r.round > 0) {
                int ry = y;
                y = line(Lang.tr("sc.flight.price.round", r.round), x, y, w, myChips >= r.round ? GuiHoloSC.OK : GuiHoloSC.BAD, bottom);
                if (y != ry) {
                    TextFitSC.hover(guiLeft + x, guiTop + ry, w, 9, Lang.tr("sc.flight.round.tip", r.round, EmbassyRulesSC.BACK_TICKS / SettlementSC.DAY));
                }
            }
        } else if (y + 8 <= bottom) {
            String why = r.code == EmbassyRulesSC.R_LEVEL ? Lang.tr("sc.trade.need.level", levelName(EmbassyRulesSC.levelOf(r.ride)))
                    : Lang.tr(EmbassyRulesSC.refuseKey(r.code));
            int room = (bottom - y + 1) / 9 - (friendLine ? 1 : 0);
            y = wrapped(why, x, y, w, GuiHoloSC.WARN, Math.max(1, room)) - 1;
        }
        if (friendLine) {
            if (friends.isEmpty()) {
                line(Lang.tr("sc.flight.friend.none"), x, y, w, GuiHoloSC.IDLE, bottom);
            } else {
                int f = Math.max(0, Math.min(friends.size() - 1, friend));
                String who = Lang.tr("sc.flight.friend.pick", friends.get(f)[0], levelName(friendLevels.get(f).intValue()), r.guar);
                if (line(who, x, y, w, guarLeft > 0 ? GuiHoloSC.VALUE : GuiHoloSC.IDLE, bottom) != y) {
                    friendLineY = y;                                // drawn: a click on it picks the next friend
                    List<String> tip = new ArrayList<String>();
                    tip.add(Lang.tr("sc.flight.friend.tip", friends.get(f)[0], r.guar));
                    if (friends.size() > 1) {
                        tip.add(Lang.tr("sc.flight.friend.next"));
                    }
                    if (guarLeft <= 0) {
                        tip.add(Lang.tr("sc.flight.friend.used"));
                    }
                    TextFitSC.hover(guiLeft + x, guiTop + y, w, 9, tip);
                }
            }
        }
        phaseLine(x, detailsBottom() - 11, w);
    }

    /** One line if there is room. @return the next y */
    private int line(String text, int x, int y, int w, int colour, int bottom) {
        if (y + 8 > bottom) {
            return y;                                           // no room above the window's line
        }
        TextFitSC.draw(fontRendererObj, text, x, y, w, colour, guiLeft, guiTop);
        return y + 9;
    }

    /** The window: its phase and timer (counted down here), the passengers and where to board in the tooltip. */
    private void phaseLine(int x, int y, int w) {
        String text;
        int colour;
        long secs = (fticks + 19) / 20;
        String time = String.format("%d:%02d", secs / 60, secs % 60);
        switch (fphase) {
            case EmbassyRulesSC.PHASE_COUNTDOWN:
                text = Lang.tr("sc.flight.phase.countdown", time);
                colour = GuiHoloSC.WARN;
                break;
            case EmbassyRulesSC.PHASE_VORTEX:
                text = Lang.tr("sc.flight.phase.vortex", time);
                colour = GuiHoloSC.OK;
                break;
            case EmbassyRulesSC.PHASE_COOL:
                text = Lang.tr("sc.flight.phase.cool", time);
                colour = GuiHoloSC.IDLE;
                break;
            default:
                text = Lang.tr("sc.flight.phase.idle");
                colour = GuiHoloSC.LABEL;
        }
        TextFitSC.draw(fontRendererObj, text, x, y, w, colour, guiLeft, guiTop);
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr(fplace == FlightTargetsSC.PLACE_EMBASSY ? "sc.flight.board.ring" : "sc.flight.board.dock"));
        if (fphase != EmbassyRulesSC.PHASE_IDLE) {
            tip.add(Lang.tr("sc.flight.phase.tip", fpass, EmbassyRulesSC.PASSENGERS));
        }
        TextFitSC.hover(guiLeft + x, guiTop + y, w, 9, tip);
    }

    /** The status line on the «Рейсы» tab: the talon waiting, else the flights left today. */
    private String flightStatus() {
        if (!fhas) {
            return "";
        }
        if (talonRide >= 0) {
            String ride = Lang.tr(EmbassyRulesSC.rideKey(talonRide));
            if (talonHere) {
                return Lang.tr("sc.flight.talon", ride);
            }
            String at = new ChatComponentTranslation(talonSoldAtEmbassy ? "sc.embassy.name" : "sc.settle.name", NpcNamesSC.name(talonSoldAt))
                    .getUnformattedText();
            return Lang.tr("sc.flight.talon.other", ride, at);
        }
        return Lang.tr("sc.flight.left", fleft, fmax);
    }

    /** A row's target in words: a dome, an embassy (the return half with its days), the death mark, none. */
    private String targetText(FRow r) {
        if (r.ride == EmbassyRulesSC.RIDE_RESCUE) {
            return r.target == EmbassyRulesSC.TARGET_MARK && r.xz ? Lang.tr("sc.flight.target.mark", r.x, r.z) : Lang.tr("sc.flight.target.none");
        }
        if (r.target == FlightTargetsSC.NO_TARGET) {
            return Lang.tr("sc.flight.target.none");
        }
        String name = new ChatComponentTranslation(r.embassy ? "sc.embassy.name" : "sc.settle.name", NpcNamesSC.name(r.name)).getUnformattedText();
        if (r.ride == EmbassyRulesSC.RIDE_BACK && backId >= 0) {
            return Lang.tr("sc.flight.back", name, backDays);
        }
        return name;
    }

    /** A ride's picture: the map (introductory), the pearl (to «Первый причал»), the Settlement Map (a known dock), a skull (the
     * rescue), the compass (home), the paper ticket (the return half), the eye (another embassy). */
    private static ItemStack rideIcon(int ride) {
        switch (ride) {
            case EmbassyRulesSC.RIDE_INTRO:
                return new ItemStack(Items.map);
            case EmbassyRulesSC.RIDE_FIRST:
                return new ItemStack(Items.ender_pearl);
            case EmbassyRulesSC.RIDE_DOCK:
                return SettlementItemsSC.settlementMap == null ? new ItemStack(Items.ender_pearl) : new ItemStack(SettlementItemsSC.settlementMap);
            case EmbassyRulesSC.RIDE_RESCUE:
                return new ItemStack(Items.skull, 1, 0);
            case EmbassyRulesSC.RIDE_HOME:
                return new ItemStack(Items.compass);
            case EmbassyRulesSC.RIDE_BACK:
                return new ItemStack(Items.paper);
            default:
                return new ItemStack(Items.ender_eye);
        }
    }

    /** The flights' row icons (absolute coordinates, over the foreground; no item tooltips - the row says it all). */
    private void flightIcons() {
        if (!fhas) {
            return;
        }
        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        itemRender.zLevel = 100F;
        for (int i = 0; i < ROWS && fscroll + i < frows.size(); i++) {
            ItemStack s = rideIcon(frows.get(fscroll + i).ride);
            if (s != null && s.getItem() != null) {
                itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), s, guiLeft + LX + 3, guiTop + LY + 2 + i * ROW_H + 1);
            }
        }
        itemRender.zLevel = 0F;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < ROWS && fscroll + i < frows.size(); i++) {
            if (frows.get(fscroll + i).code != EmbassyRulesSC.OK) {
                int x = guiLeft + LX + 3, y = guiTop + LY + 2 + i * ROW_H + 1;
                drawRect(x, y, x + 16, y + 16, 0x900A1420);      // dimmed: closed to this player
            }
        }
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The flights' buttons' tooltips: what each does now (the price, the friend), or why it is dark. */
    private List<String> flightButtonTip(int mx, int my) {
        FRow r = fsel >= 0 && fsel < frows.size() ? frows.get(fsel) : null;
        List<String> l = new ArrayList<String>();
        if (buyBtn != null && buyBtn.visible && inside(buyBtn, mx, my) && declining()) {
            l.add(Lang.tr("sc.flight.decline.tip"));
        } else if (buyBtn != null && buyBtn.visible && inside(buyBtn, mx, my) && givingBack()) {
            l.add(Lang.tr("sc.flight.giveback.tip"));          // ПС-3
        } else if (buyBtn != null && buyBtn.visible && inside(buyBtn, mx, my) && r != null) {
            l.add(r.ride == EmbassyRulesSC.RIDE_BACK ? Lang.tr("sc.flight.useBack.tip") : Lang.tr("sc.flight.buy.tip", r.price));
        } else if (roundBtn != null && roundBtn.visible && inside(roundBtn, mx, my) && r != null) {
            l.add(backId >= 0 ? Lang.tr(EmbassyRulesSC.refuseKey(EmbassyRulesSC.R_HAS_BACK))
                    : r.round > 0 ? Lang.tr("sc.flight.round.tip", r.round, EmbassyRulesSC.BACK_TICKS / SettlementSC.DAY) : Lang.tr("sc.flight.round.none"));
        } else if (friendBtn != null && friendBtn.visible && inside(friendBtn, mx, my)) {
            l.add(fplace != FlightTargetsSC.PLACE_EMBASSY ? Lang.tr("sc.flight.friend.embassy")
                    : !guarantor ? Lang.tr("sc.flight.friend.level", levelName(EmbassyRulesSC.GUARANTOR_LEVEL))
                    : friends.isEmpty() ? Lang.tr("sc.flight.friend.none") : guarLeft <= 0 ? Lang.tr("sc.flight.friend.used")
                    : Lang.tr("sc.flight.friend.tip", friends.get(Math.max(0, Math.min(friends.size() - 1, friend)))[0], r == null ? 0 : r.guar));
        } else if (goBtn != null && goBtn.visible && inside(goBtn, mx, my)) {
            l.add(Lang.tr(talonRide < 0 ? "sc.flight.go.none" : !talonHere ? "sc.flight.go.elsewhere" : "sc.flight.go.tip"));
        }
        if (!l.isEmpty() && confirmTicks > 0) {
            l.add(Lang.tr("sc.flight.confirm.tip"));
        }
        return l.isEmpty() ? null : l;
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

    /** «Обменять»: Shift - how many at most; a disabled contract button: why. Ф6: the flights' buttons. */
    private List<String> buttonTip(int mx, int my) {
        if (flights()) {
            return flightButtonTip(mx, my);
        }
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
        if (flights()) {
            flightIcons();
            return null;
        }
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
                    int eff = gainHere(r.offer.outAmount);       // ПС-5: the embassy's ceiling
                    String t = "+" + eff;
                    fontRendererObj.drawStringWithShadow(t, x + 17 - fontRendererObj.getStringWidth(t), y + 9, eff <= 0 ? 0x8A8A8A : 0xFFFFFF);
                    TextFitSC.hover(x, y, 16, 16, Lang.tr("sc.trade.out.rep", gainArg(r.offer.outAmount)));
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

    /** A contract's picture: a sword (kill), the thing handed in (deliver, cargo), the ПЭ-1 (suppress), the anchor, a paper (Ф6: the dispatch). */
    private static ItemStack contractIcon(CRow c) {
        if (c.anchor) {
            return ModBlocks.bridge == null ? null : new ItemStack(ModBlocks.bridge, 1, BlockBridgeSC.ANCHOR);
        }
        switch (c.c.goal) {
            case ContractTableSC.KILL:
                return new ItemStack(Items.iron_sword);
            case ContractTableSC.SUPPRESS_KILL:
                return ModItems.empGun == null ? null : new ItemStack(ModItems.empGun);
            case ContractTableSC.DISPATCH:
                return new ItemStack(Items.paper);
            default:
                TradeOfferSC.Ingredient in = c.c.handIn();
                return in == null ? null : in.display();
        }
    }
}
