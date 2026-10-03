package com.sc.handler;

import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * Client -> server messages for the suits: switch one function of a worn piece on or off, step the
 * power mode, dash, take the chips out. The server checks everything against what the player actually wears.
 */
public final class ArmorNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeArmor");

    public static final byte TOGGLE = 0, POWER_MODE = 1, DASH = 2, ANNIHILATE = 3;
    /** The energy blade in hand: switch one of its functions, or fire one of its key functions. */
    public static final byte BLADE_TOGGLE = 4, BLADE_SWEEP = 5, BLADE_WAVE = 6, BLADE_LUNGE = 7;
    /** Every chip out of the worn chestplate, back into the inventory. */
    public static final byte REMOVE_CHIPS = 8;
    /** The drill in hand: switch one of its functions, or fire its laser. */
    public static final byte DRILL_TOGGLE = 9, DRILL_LASER = 10;
    /** The light colour of every worn piece of the mod (feature = the colour index). */
    public static final byte GLOW_COLOR = 11;
    /** Life support: pour the gas container in the player's inventory slot `feature` into the worn suit (K screen). */
    public static final byte GAS_FILL = 12;
    /**
     * Life support: pour every suitable gas container of the main inventory into the worn suit - the
     * server walks the inventory itself (same rules as GAS_FILL, slot by slot) and answers with ONE chat line.
     */
    public static final byte GAS_FILL_ALL = 13;
    /** Singular leggings: the phase dash (ArmorLogicSC.phaseDash). */
    public static final byte PHASE_DASH = 14;
    /** GAS_FILL_ALL: at most this many single pours per press, and the press at most once per this many ticks. */
    private static final int FILL_ALL_MAX = 64, FILL_ALL_COOLDOWN = 10;
    private static final String FILL_ALL_TIME_TAG = "ScGasFillAllT";

    private ArmorNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.SERVER);
        CHANNEL.registerMessage(CooldownHandler.class, CooldownMessage.class, 1, Side.CLIENT);
    }

    /**
     * Server -> client: a function's cooldown ends at world tick `end` (0: none); feature -1 clears them
     * all (login). The client keeps it in SingularCooldowns (no client classes here: safe on a server).
     */
    public static class CooldownMessage implements IMessage {
        public int feature;
        public long end;

        public CooldownMessage() {
        }

        public CooldownMessage(int feature, long end) {
            this.feature = feature;
            this.end = end;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            feature = buf.readByte();
            end = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(feature);
            buf.writeLong(end);
        }
    }

    public static class CooldownHandler implements IMessageHandler<CooldownMessage, IMessage> {
        @Override
        public IMessage onMessage(CooldownMessage msg, MessageContext ctx) {
            com.sc.util.SingularCooldowns.clientSet(msg.feature, msg.end);
            return null;
        }
    }

    public static class Message implements IMessage {
        public byte action;
        public byte feature;
        /** TOGGLE: the state wanted (not "flip" - two quick clicks would fight the server's reply). */
        public boolean value;

        public Message() {
        }

        public Message(byte action, int feature) {
            this(action, feature, false);
        }

        public Message(byte action, int feature, boolean value) {
            this.action = action;
            this.feature = (byte) feature;
            this.value = value;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            action = buf.readByte();
            feature = buf.readByte();
            value = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(action);
            buf.writeByte(feature);
            buf.writeBoolean(value);
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (p == null || p.isDead || p.getHealth() <= 0) {
                return null;                               // on the death screen: nothing to act with
            }
            switch (msg.action) {
                case TOGGLE: {
                    ArmorFeature f = ArmorFeature.of(msg.feature);
                    ItemStack piece = f == null ? null : ArmorLogicSC.piece(p, f.piece);
                    if (piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)
                            && (!msg.value || com.sc.util.SingularLevel.unlocked(p, f, piece))) {    // a locked function can't be switched on
                        ItemArmorSC.setEnabled(piece, f, msg.value);
                        p.inventoryContainer.detectAndSendChanges();
                    }
                    break;
                }
                case POWER_MODE: {                        // feature: the mode wanted (0..2), anything else - the next one
                    ItemStack chest = ArmorLogicSC.piece(p, 1);
                    if (chest != null) {
                        int want = msg.feature >= 0 && msg.feature <= 2 ? msg.feature : (ItemArmorSC.powerMode(chest) + 1) % 3;
                        ItemArmorSC.setPowerMode(chest, want);
                        p.inventoryContainer.detectAndSendChanges();
                    }
                    break;
                }
                case DASH:
                    ArmorLogicSC.dash(p);
                    break;
                case ANNIHILATE:
                    ArmorLogicSC.annihilate(p);
                    break;
                case BLADE_TOGGLE: {
                    com.sc.util.BladeFeature f = com.sc.util.BladeFeature.of(msg.feature);
                    ItemStack blade = com.sc.item.BladeLogicSC.held(p);
                    if (f != null && blade != null) {
                        com.sc.item.BladeLogicSC.toggle(p, blade, f, msg.value);
                    }
                    break;
                }
                case BLADE_SWEEP:
                    com.sc.item.BladeLogicSC.sweep(p);
                    break;
                case BLADE_WAVE:
                    com.sc.item.BladeLogicSC.wave(p);
                    break;
                case BLADE_LUNGE:
                    com.sc.item.BladeLogicSC.lunge(p);
                    break;
                case REMOVE_CHIPS:
                    com.sc.item.ItemArmorChipSC.removeAll(p);
                    break;
                case DRILL_TOGGLE: {
                    com.sc.util.DrillFeature f = com.sc.util.DrillFeature.of(msg.feature);
                    ItemStack drill = com.sc.item.DrillLogicSC.held(p);
                    if (f != null && drill != null) {
                        com.sc.item.DrillLogicSC.toggle(p, drill, f, msg.value);
                    }
                    break;
                }
                case DRILL_LASER:
                    com.sc.item.DrillLogicSC.laser(p);
                    break;
                case GLOW_COLOR: {
                    for (int i = 0; i < 4; i++) {
                        ItemStack s = ArmorLogicSC.piece(p, i);
                        if (s != null) {
                            ItemArmorSC.setGlowColor(s, msg.feature);
                        }
                    }
                    p.inventoryContainer.detectAndSendChanges();
                    break;
                }
                case GAS_FILL:
                    fillSuit(p, msg.feature);
                    break;
                case GAS_FILL_ALL:
                    fillSuitAll(p);
                    break;
                case com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION:   // hydrogen: a second jump in mid-air
                    com.sc.item.ArmorLogicSC.airJump(p);
                    break;
                case PHASE_DASH:
                    com.sc.item.ArmorLogicSC.phaseDash(p);
                    break;
                default:
                    break;
            }
            return null;
        }
    }

    /**
     * Pours the gas container in main-inventory slot `slot` into the worn suit's tanks. A container
     * that only empties whole (a bucket, a cell from the FluidContainerRegistry) goes in only if all of
     * it fits - otherwise nothing is spent; a tank-in-an-item (IFluidContainerItem) gives what fits and
     * keeps the rest. The empty container comes back to the inventory (or drops at the feet).
     * Creative players keep their containers full, as with a machine.
     */
    public static void fillSuit(EntityPlayerMP p, int slot) {
        if (pour(p, slot, null)) {
            p.worldObj.playSoundAtEntity(p, "random.fizz", 0.3F, 1.6F);
            p.inventory.markDirty();
            p.inventoryContainer.detectAndSendChanges();
        }
    }

    /**
     * "Fill everything": every slot of the main inventory in order, each poured exactly as a single
     * GAS_FILL would (a stack of whole-only containers - one by one while whole ones fit). No chat line
     * per container: one summary at the end ("Suit filled: Helium 2000 mB, Hydrogen 1000 mB" / nothing to fill).
     */
    public static void fillSuitAll(EntityPlayerMP p) {
        long now = p.worldObj.getTotalWorldTime();
        net.minecraft.nbt.NBTTagCompound data = p.getEntityData();
        long last = data.getLong(FILL_ALL_TIME_TAG);
        if (last > 0 && now >= last && now - last < FILL_ALL_COOLDOWN) {
            return;                                             // a flood of presses: one per half a second
        }
        data.setLong(FILL_ALL_TIME_TAG, now);
        int[] got = new int[ArmorGasSC.Gas.values().length];
        int pours = 0;
        for (int slot = 0; slot < p.inventory.mainInventory.length && pours < FILL_ALL_MAX; slot++) {
            while (pours < FILL_ALL_MAX && pour(p, slot, got)) {   // until the slot has nothing more that fits
                pours++;
            }
        }
        if (pours > 0) {
            p.worldObj.playSoundAtEntity(p, "random.fizz", 0.3F, 1.6F);
            p.inventory.markDirty();
            p.inventoryContainer.detectAndSendChanges();
        }
        ChatComponentText list = new ChatComponentText("");
        boolean any = false;
        for (ArmorGasSC.Gas g : ArmorGasSC.Gas.values()) {
            if (got[g.ordinal()] <= 0) {
                continue;
            }
            if (any) {
                list.appendText(", ");
            }
            list.appendSibling(new ChatComponentTranslation("sc.chat.gas.fillall.part", new ChatComponentTranslation("sc.gas." + g.key()),
                    String.valueOf(got[g.ordinal()])));
            any = true;
        }
        p.addChatComponentMessage(any ? new ChatComponentTranslation("sc.chat.gas.fillall", list)
                : new ChatComponentTranslation("sc.chat.gas.fillall.none"));
    }

    /**
     * One pour from main-inventory slot `slot` (the rules above). `got` null: a single fill - every
     * outcome gets its chat line; otherwise silent, the poured mB added to got[gas ordinal].
     * @return true when gas went into the suit
     */
    private static boolean pour(EntityPlayerMP p, int slot, int[] got) {
        if (slot < 0 || slot >= p.inventory.mainInventory.length) {
            return false;
        }
        ItemStack stack = p.inventory.mainInventory[slot];
        if (stack == null || stack.stackSize <= 0) {
            return false;
        }
        boolean creative = p.capabilities.isCreativeMode;
        if (FluidContainerRegistry.isFilledContainer(stack)) {
            FluidStack in = FluidContainerRegistry.getFluidForFilledItem(stack);
            ArmorGasSC.Gas g = in == null ? null : ArmorGasSC.Gas.of(in.getFluid());
            if (g == null || in.amount <= 0) {
                return false;                                   // not one of the suit's gases
            }
            if (ArmorGasSC.suitCapacity(p, g) <= 0) {
                if (got == null) {
                    tell(p, "sc.chat.gas.notank", g, in.amount);
                }
                return false;
            }
            if (ArmorGasSC.suitFill(p, g, in.amount, true) != in.amount) {
                if (got == null) {
                    tell(p, "sc.chat.gas.nofit", g, in.amount);   // a whole-only container: nothing spent
                }
                return false;
            }
            ArmorGasSC.suitFill(p, g, in.amount, false);
            if (!creative) {
                ItemStack empty = FluidContainerRegistry.drainFluidContainer(stack);
                if (empty == null && stack.getItem().hasContainerItem(stack)) {
                    empty = stack.getItem().getContainerItem(stack);
                }
                if (stack.stackSize <= 1) {
                    p.inventory.setInventorySlotContents(slot, empty);
                } else {
                    stack.stackSize--;
                    if (empty != null && !p.inventory.addItemStackToInventory(empty)) {
                        p.dropPlayerItemWithRandomChoice(empty, false);
                    }
                }
            }
            report(got, p, g, in.amount);
        } else if (stack.getItem() instanceof IFluidContainerItem && stack.stackSize == 1) {
            IFluidContainerItem item = (IFluidContainerItem) stack.getItem();
            FluidStack carried = item.getFluid(stack);
            ArmorGasSC.Gas g = carried == null || carried.amount <= 0 ? null : ArmorGasSC.Gas.of(carried.getFluid());
            if (g == null) {
                return false;
            }
            if (ArmorGasSC.suitCapacity(p, g) <= 0) {
                if (got == null) {
                    tell(p, "sc.chat.gas.notank", g, carried.amount);
                }
                return false;
            }
            int room = ArmorGasSC.suitFill(p, g, carried.amount, true);
            if (room <= 0) {
                if (got == null) {
                    tell(p, "sc.chat.gas.full", g, 0);
                }
                return false;
            }
            FluidStack taken = item.drain(stack, room, false);      // what the container really gives
            int put = taken == null ? 0 : Math.min(room, taken.amount);
            if (put <= 0) {
                return false;
            }
            if (!creative) {
                FluidStack drained = item.drain(stack, put, true);
                put = drained == null ? 0 : Math.min(put, drained.amount);
            }
            ArmorGasSC.suitFill(p, g, put, false);
            if (put <= 0) {
                return false;                                   // the container gave nothing after all
            }
            report(got, p, g, put);
        } else {
            return false;
        }
        return true;
    }

    private static void report(int[] got, EntityPlayerMP p, ArmorGasSC.Gas g, int mb) {
        if (got == null) {
            tell(p, "sc.chat.gas.in", g, mb);
        } else {
            got[g.ordinal()] += mb;
        }
    }

    private static void tell(EntityPlayerMP p, String key, ArmorGasSC.Gas g, int mb) {
        p.addChatComponentMessage(new ChatComponentTranslation(key, mb, new ChatComponentTranslation("sc.gas." + g.key())));
    }
}
