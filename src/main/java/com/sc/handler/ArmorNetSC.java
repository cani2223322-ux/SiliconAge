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

    private ArmorNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.SERVER);
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
                    if (piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)) {
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
                case com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION:   // hydrogen: a second jump in mid-air
                    com.sc.item.ArmorLogicSC.airJump(p);
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
        if (slot < 0 || slot >= p.inventory.mainInventory.length) {
            return;
        }
        ItemStack stack = p.inventory.mainInventory[slot];
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        boolean creative = p.capabilities.isCreativeMode;
        if (FluidContainerRegistry.isFilledContainer(stack)) {
            FluidStack in = FluidContainerRegistry.getFluidForFilledItem(stack);
            ArmorGasSC.Gas g = in == null ? null : ArmorGasSC.Gas.of(in.getFluid());
            if (g == null || in.amount <= 0) {
                return;                                         // not one of the suit's gases
            }
            if (ArmorGasSC.suitCapacity(p, g) <= 0) {
                tell(p, "sc.chat.gas.notank", g, in.amount);
                return;
            }
            if (ArmorGasSC.suitFill(p, g, in.amount, true) != in.amount) {
                tell(p, "sc.chat.gas.nofit", g, in.amount);   // a whole-only container: nothing spent
                return;
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
            tell(p, "sc.chat.gas.in", g, in.amount);
        } else if (stack.getItem() instanceof IFluidContainerItem && stack.stackSize == 1) {
            IFluidContainerItem item = (IFluidContainerItem) stack.getItem();
            FluidStack carried = item.getFluid(stack);
            ArmorGasSC.Gas g = carried == null || carried.amount <= 0 ? null : ArmorGasSC.Gas.of(carried.getFluid());
            if (g == null) {
                return;
            }
            if (ArmorGasSC.suitCapacity(p, g) <= 0) {
                tell(p, "sc.chat.gas.notank", g, carried.amount);
                return;
            }
            int room = ArmorGasSC.suitFill(p, g, carried.amount, true);
            if (room <= 0) {
                tell(p, "sc.chat.gas.full", g, 0);
                return;
            }
            FluidStack taken = item.drain(stack, room, false);      // what the container really gives
            int put = taken == null ? 0 : Math.min(room, taken.amount);
            if (put <= 0) {
                return;
            }
            if (!creative) {
                FluidStack drained = item.drain(stack, put, true);
                put = drained == null ? 0 : Math.min(put, drained.amount);
            }
            ArmorGasSC.suitFill(p, g, put, false);
            tell(p, "sc.chat.gas.in", g, put);
        } else {
            return;
        }
        p.worldObj.playSoundAtEntity(p, "random.fizz", 0.3F, 1.6F);
        p.inventory.markDirty();
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void tell(EntityPlayerMP p, String key, ArmorGasSC.Gas g, int mb) {
        p.addChatComponentMessage(new ChatComponentTranslation(key, mb, new ChatComponentTranslation("sc.gas." + g.key())));
    }
}
