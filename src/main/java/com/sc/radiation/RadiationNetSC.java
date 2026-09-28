package com.sc.radiation;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Server -> client, once a second: the radiation where the player stands, their dose, how much of
 * it the protection takes, and what protects them (RadiationSC.F_*). The client keeps the numbers
 * in RadiationStateSC for the dosimeter and the on-screen warning.
 */
public final class RadiationNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeRad");

    private RadiationNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.CLIENT);
    }

    public static void send(EntityPlayerMP p, float level, float dose, int prot, int flags) {
        CHANNEL.sendTo(new Message(Math.round(level * 10F), Math.round(dose * 10F), prot, flags), p);
    }

    public static class Message implements IMessage {
        public int level, dose, prot, flags;

        public Message() {
        }

        public Message(int level, int dose, int prot, int flags) {
            this.level = level;
            this.dose = dose;
            this.prot = prot;
            this.flags = flags;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            level = buf.readShort();
            dose = buf.readShort();
            prot = buf.readByte();
            flags = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeShort(level);
            buf.writeShort(dose);
            buf.writeByte(prot);
            buf.writeByte(flags);
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            RadiationStateSC.set(msg.level / 10F, msg.dose / 10F, msg.prot, msg.flags);
            return null;
        }
    }
}
