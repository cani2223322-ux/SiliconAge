package com.sc.inventory;

import com.sc.tileentity.SingularProcessSC;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.SingularStationMath;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

/**
 * The Singular Service Station's screen (GuiSingularStationSC, W x H - fits a 320 x 240 screen):
 * the four armour slots, the module row, the donor and catalyst slots, the six conversion material
 * slots (Б-1, 3 x 2), the player's inventory; the
 * station's numbers and its running process synced. A slot a process holds can't be taken (ПР6).
 * The positions here are the compact layout's start; the screen moves every slot to its layout and tab
 * (the slots of the other tabs off screen - they take nothing then, see slotInTab).
 */
public class ContainerSingularStationSC extends Container {

    /** The armour station's buttons, then the Singular ones (BTN_BRANCH + (level 5 ? 2 : 0) + side - 1). */
    public static final int BTN_POWER = ContainerArmorStationSC.BTN_POWER, BTN_REDSTONE = ContainerArmorStationSC.BTN_REDSTONE,
            BTN_FILL = ContainerArmorStationSC.BTN_FILL, BTN_GAS = ContainerArmorStationSC.BTN_GAS, BTN_CHARGE = 14,
            BTN_MODERNISE = 40, BTN_CANCEL = 41, BTN_SYNC = 42, BTN_TRANSFER = 43, BTN_SCHEME_PREV = 44, BTN_SCHEME_NEXT = 45,
            BTN_CONVERT = 46, BTN_BRANCH = 50, BTN_CLEAR = ContainerArmorStationSC.BTN_CLEAR, BTN_HELIUM = ContainerArmorStationSC.BTN_HELIUM,
            BTN_TAB = 60;
    /**
     * The screen's tabs (GuiSingularStationSC); the client tells the server which one is open (BTN_TAB + tab), the
     * slots of the other tabs then take nothing (shift-click included) - what lies in them stays there.
     */
    public static final int TAB_MODERN = 0, TAB_CONVERT = 1, TAB_TRANSFER = 2, TAB_SYNC = 3, TAB_BRANCH = 4, TABS = 5;
    public static final int W = 320, H = 236;
    /** The armour column: rows ROW_STEP apart from ROW_Y; slot (item coordinates) at PIECE_X, row + 3. */
    public static final int ROW_Y = 17, ROW_STEP = 22, PIECE_X = 7;
    /**
     * The donor and the catalyst (item coordinates); the six material slots MAT_COLS x 2 from MAT_X, MAT_Y (Б-1).
     * Left column, x: donor / catalyst 6..23 (frame), their labels 25..42, materials 43..96; the centre panel from 98.
     * y: the labels' line 107..113 (under the last armour row's text, ..106), the slots 115..150, the separator 152.
     */
    public static final int DONOR_X = 7, CATALYST_X = 7, EXTRA_Y = 116, CATALYST_Y = 134, MAT_X = 44, MAT_Y = 116, MAT_COLS = 3,
            EXTRA_LABEL_X = 25, EXTRA_LABEL_W = 18;

    /** Material slot i's position (item coordinates): [x, y]. */
    public static int matX(int i) {
        return MAT_X + (i % MAT_COLS) * 18;
    }

    public static int matY(int i) {
        return MAT_Y + (i / MAT_COLS) * 18;
    }
    /** The module row, the player's inventory. */
    public static final int UPG_X = 227, UPG_Y = 117, INV_X = 7, INV_Y = 155, HOTBAR_Y = 213;

    private static final int GASES = Gas.values().length, R = SingularStationMath.RESOURCES;
    /** energy, status, players, settings, power flags, tanks x8, singular flags, short mask, kind, mask, progress, base, target, cost x2R, drawn x2R, catalyst x2. */
    private static final int TANKS = 5, SFLAGS = TANKS + GASES, SHORT = SFLAGS + 1, KIND = SHORT + 1, MASK = KIND + 1, PROG = MASK + 1,
            BASE = PROG + 1, TARGET = BASE + 1, COST = TARGET + 1, DRAWN = COST + 2 * R, CAT = DRAWN + 2 * R, COUNT = CAT + 2;
    private final TileEntitySingularStationSC te;
    private final IntSyncSC sync = new IntSyncSC(COUNT);
    /** The tab the screen shows (-1: not told yet - every slot takes its items, as before the tabs). */
    private int tab = -1;

    public int getTab() {
        return tab;
    }

    /** The client's screen: the tab it shows now. */
    public void setTab(int t) {
        tab = t >= 0 && t < TABS ? t : -1;
    }

    /** Whether the station's slot `slot` belongs to tab `tab` (the armour and module slots: every tab; -1: all). */
    public static boolean slotInTab(int tab, int slot) {
        if (tab < 0 || slot < TileEntitySingularStationSC.DONOR_SLOT) {
            return true;
        }
        if (slot == TileEntitySingularStationSC.DONOR_SLOT) {
            return tab == TAB_TRANSFER;
        }
        if (slot == TileEntitySingularStationSC.CATALYST_SLOT) {
            return tab == TAB_MODERN || tab == TAB_CONVERT;
        }
        return tab == TAB_CONVERT;
    }

    /** The tab a process kind belongs to. */
    public static int tabOf(int kind) {
        switch (kind) {
            case SingularProcessSC.KIND_TRANSFER: return TAB_TRANSFER;
            case SingularProcessSC.KIND_SYNC: return TAB_SYNC;
            case SingularProcessSC.KIND_CONVERT: return TAB_CONVERT;
            default: return TAB_MODERN;
        }
    }

    public ContainerSingularStationSC(InventoryPlayer playerInv, TileEntitySingularStationSC te) {
        this.te = te;
        for (int i = 0; i < TileEntityArmorStationSC.SLOTS; i++) {
            addSlotToContainer(new SlotPieceLocked(te, i, PIECE_X, ROW_Y + i * ROW_STEP + 3));
        }
        for (int i = 0; i < TileEntityArmorStationSC.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new ContainerArmorStationSC.SlotModule(te, TileEntityArmorStationSC.FIRST_UPGRADE_SLOT + i, UPG_X + i * 18, UPG_Y));
        }
        addSlotToContainer(new SlotExtra(te, TileEntitySingularStationSC.DONOR_SLOT, DONOR_X, EXTRA_Y).in(this));
        addSlotToContainer(new SlotExtra(te, TileEntitySingularStationSC.CATALYST_SLOT, CATALYST_X, CATALYST_Y).in(this));
        for (int i = 0; i < TileEntitySingularStationSC.MATERIAL_SLOTS; i++) {
            addSlotToContainer(new SlotMaterial(te, TileEntitySingularStationSC.MATERIAL_SLOT + i, matX(i), matY(i)).in(this));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
        if (playerInv.player != null && playerInv.player.worldObj != null && !playerInv.player.worldObj.isRemote) {
            com.sc.item.SingularProgressSC.sync(playerInv.player);       // the task counters fresh for the screen's checklist
        }
    }

    /** An armour slot: the station's rule, and not while a process holds it. */
    public static class SlotPieceLocked extends ContainerArmorStationSC.SlotPiece {
        private final TileEntitySingularStationSC st;

        public SlotPieceLocked(TileEntitySingularStationSC st, int type, int x, int y) {
            super(st, type, x, y);
            this.st = st;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return !st.isLocked(getSlotIndex()) && super.isItemValid(stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return !st.isLocked(getSlotIndex());
        }
    }

    /** The donor (a Singular piece) or the catalyst (a Singular core): one item, locked during a process. */
    public static class SlotExtra extends Slot {
        private final TileEntitySingularStationSC st;
        private ContainerSingularStationSC owner;

        public SlotExtra(IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
            this.st = (TileEntitySingularStationSC) inv;
        }

        SlotExtra in(ContainerSingularStationSC c) {
            owner = c;
            return this;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return (owner == null || slotInTab(owner.tab, getSlotIndex())) && st.isItemValidForSlot(getSlotIndex(), stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return !st.isLocked(getSlotIndex());
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /** A conversion material slot (Б-1): the materials only, stacks as usual. */
    public static class SlotMaterial extends Slot {
        private ContainerSingularStationSC owner;

        public SlotMaterial(IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        SlotMaterial in(ContainerSingularStationSC c) {
            owner = c;
            return this;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return (owner == null || slotInTab(owner.tab, getSlotIndex())) && inventory.isItemValidForSlot(getSlotIndex(), stack);
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.isUseableByPlayer(player);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player)) {
            return false;
        }
        if (id >= BTN_GAS && id < BTN_GAS + GASES) {
            te.toggleGas(Gas.values()[id - BTN_GAS]);
            return true;
        }
        if (id >= BTN_TAB && id < BTN_TAB + TABS) {
            setTab(id - BTN_TAB);
            return true;
        }
        if (id >= BTN_CLEAR && id < BTN_CLEAR + GASES) {
            te.clearTank(Gas.values()[id - BTN_CLEAR]);
            return true;
        }
        if (id >= BTN_BRANCH && id < BTN_BRANCH + 4) {
            int k = id - BTN_BRANCH;
            say(player, te.changeBranch(k < 2 ? 3 : 5, k % 2 + 1));
            return true;
        }
        switch (id) {
            case BTN_POWER:
                te.setPowerOn(!te.isPowerOn());
                com.sc.util.SoundsSC.powerClick(te, te.isPowerOn());
                return true;
            case BTN_REDSTONE: te.setRedstoneMode((te.getRedstoneMode() + 1) % 3); return true;
            case BTN_FILL: te.toggleFillGases(); return true;
            case BTN_HELIUM: te.toggleHeliumOnly(); return true;
            case BTN_CHARGE: te.toggleCharge(); return true;
            case BTN_MODERNISE: say(player, te.startModernise(player)); return true;
            case BTN_CONVERT: say(player, te.startConvert(player)); return true;
            case BTN_CANCEL: te.cancelProcess(); return true;
            case BTN_SYNC: say(player, te.startSync(player)); return true;
            case BTN_TRANSFER: say(player, te.startTransfer(player)); return true;
            case BTN_SCHEME_PREV: te.cycleScheme(-1); return true;
            case BTN_SCHEME_NEXT: te.cycleScheme(1); return true;
            default: return false;
        }
    }

    /** Why a button did nothing, in the chat. */
    private static void say(EntityPlayer p, String key) {
        if (key != null && p != null) {
            p.addChatComponentMessage(new ChatComponentTranslation(key));
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] v = new int[COUNT];
        v[0] = te.getEnergyStored();
        v[1] = te.getStatus();
        v[2] = te.getPlayers();
        v[3] = te.settingsFlags();
        v[4] = te.powerFlags();
        for (Gas g : Gas.values()) {
            v[TANKS + g.ordinal()] = te.tankAmount(g);
        }
        v[SFLAGS] = te.getStabilisers() | (te.hasResonance() ? 1 << 8 : 0) | (te.isPausedOff() ? 1 << 9 : 0) | (te.isChargeOn() ? 1 << 10 : 0);
        v[SHORT] = te.getShortMask();
        SingularProcessSC p = te.getProcess();
        if (p != null) {
            v[KIND] = p.kind + 1;
            v[MASK] = p.mask;
            v[PROG] = (int) Math.round(p.progress * 1000000);
            v[BASE] = p.baseTicks;
            v[TARGET] = p.target;
            for (int r = 0; r < R; r++) {
                v[COST + 2 * r] = (int) p.cost[r];
                v[COST + 2 * r + 1] = (int) (p.cost[r] >>> 32);
                v[DRAWN + 2 * r] = (int) p.drawn[r];
                v[DRAWN + 2 * r + 1] = (int) (p.drawn[r] >>> 32);
            }
            v[CAT] = (int) p.catalystEu;
            v[CAT + 1] = (int) (p.catalystEu >>> 32);
        }
        sync.send(this, crafters, v);
    }

    private static long joined(IntSyncSC s, int lo) {
        return (s.value(lo) & 0xFFFFFFFFL) | ((long) s.value(lo + 1) << 32);
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id < 0) {
            return;
        }
        if (id == 0) {
            te.setEnergyStoredClient(sync.value(0));
        } else if (id == 4) {
            te.setPowerFlagsClient(sync.value(4));
        } else if (id <= 3) {
            te.setScreenClient(sync.value(1), sync.value(2), sync.value(3));
        } else if (id < SFLAGS) {
            te.setTankClient(id - TANKS, sync.value(id));
        } else if (id <= SHORT) {
            int f = sync.value(SFLAGS);
            te.setSingularClient(f & 0xFF, (f & 1 << 8) != 0, sync.value(SHORT), (f & 1 << 9) != 0, (f & 1 << 10) != 0);
        } else {
            int kind = sync.value(KIND);
            if (kind <= 0) {
                te.setProcessClient(null);
                return;
            }
            SingularProcessSC p = te.getProcess() != null ? te.getProcess() : new SingularProcessSC();
            p.kind = kind - 1;
            p.mask = sync.value(MASK);
            p.progress = sync.value(PROG) / 1000000.0;
            p.baseTicks = Math.max(1, sync.value(BASE));
            p.target = sync.value(TARGET);
            for (int r = 0; r < R; r++) {
                p.cost[r] = joined(sync, COST + 2 * r);
                p.drawn[r] = joined(sync, DRAWN + 2 * r);
            }
            p.catalystEu = joined(sync, CAT);
            te.setProcessClient(p);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack() || !slot.canTakeStack(player)) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int own = TileEntitySingularStationSC.SING_SLOTS, end = inventorySlots.size(),
                hotbar = own + 27;
        if (index < own) {
            if (!mergeItemStack(original, own, end, true)) {
                return null;
            }
        } else if (TileEntityArmorStationSC.acceptsModule(original)
                && SlotMergeSC.mergeValid(inventorySlots, original, TileEntityArmorStationSC.FIRST_UPGRADE_SLOT, TileEntityArmorStationSC.ALL_SLOTS)) {
            // a module: into the module row
        } else {
            boolean moved = false;
            int[] targets = {0, 1, 2, 3, TileEntitySingularStationSC.DONOR_SLOT, TileEntitySingularStationSC.CATALYST_SLOT};
            for (int k = 0; k < targets.length && !moved; k++) {
                Slot target = (Slot) inventorySlots.get(targets[k]);
                if (!target.getHasStack() && target.isItemValid(original)) {
                    target.putStack(original.splitStack(1));
                    moved = true;
                }
            }
            if (!moved && TileEntitySingularStationSC.materialKind(original) >= 0) {   // a conversion material: into the material slots
                moved = SlotMergeSC.mergeValid(inventorySlots, original, TileEntitySingularStationSC.MATERIAL_SLOT, TileEntitySingularStationSC.SING_SLOTS);
            }
            if (!moved && !mergeItemStack(original, index < hotbar ? hotbar : own, index < hotbar ? end : hotbar, false)) {
                return null;
            }
        }
        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;
        }
        if (slotId >= 0 && slotId < TileEntitySingularStationSC.SING_SLOTS && slotId < inventorySlots.size()
                && te.isLocked(((Slot) inventorySlots.get(slotId)).getSlotIndex())) {
            return null;                                   // ПР6: a slot under a process takes no clicks at all
        }
        return super.slotClick(slotId, button, mode, player);
    }
}
