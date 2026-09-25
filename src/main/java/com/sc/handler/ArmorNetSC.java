package com.sc.handler;

import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorFeature;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

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
                default:
                    break;
            }
            return null;
        }
    }
}
