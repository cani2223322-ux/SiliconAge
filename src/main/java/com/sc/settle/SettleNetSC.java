package com.sc.settle;

import com.sc.SCMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §2.5): the settlements' messages, on the Substrate's own channel
 * "SiliconAgeDim" (SubstrateNetSC.CHANNEL; discriminator 0 is its ids / rules message): DISC_TO_CLIENT 1, DISC_TO_SERVER
 * 2. Each carries a kind and an NBT compound - the package that owns a kind owns its contents, so a new field never
 * changes this file. Kinds (fixed, append only):
 * <ul>
 * <li>to a client: TRADE_STATE (the open trade screen's deals, limits, timer, standing, contracts), TRADE_RESULT (a
 * deal done / refused, with the reason) - package T; handled by SCMod.proxy.settleMessage (ClientProxy -> TradeClientSC);</li>
 * <li>to the server: TRADE_DO {offer key, times}, CONTRACT_TAKE / CONTRACT_TURN_IN / CONTRACT_DROP {key}, TRADE_REFRESH
 * {} - package T; handled by TradeServerSC.onMessage, which checks the sender's open screen itself.</li>
 * <li>Ф6 (docs/plan-substrate-tech-f6.md §2.7, package R): to a client FLIGHT_STATE (a Dispatcher's «Рейсы» tab: the rides open to
 * this player, their prices, the day's flights left, the window's phase, the ticket's way back) and FLIGHT_RESULT (bought / refused,
 * with the reason; the soft admission's warning); to the server FLIGHT_DO {ride, target, round, friend, confirm, go, decline} and FLIGHT_REFRESH {} -
 * handled by embassy.FlightServerSC.onMessage, which checks the sender's open screen at a Dispatcher itself.</li>
 * </ul>
 * Never sent to a FakePlayer (it has no connection). Shared core: only the coordinator edits this file.
 */
public final class SettleNetSC {

    public static final int DISC_TO_CLIENT = 1, DISC_TO_SERVER = 2;
    /** To a client. */
    public static final int TRADE_STATE = 1, TRADE_RESULT = 2;
    /** To a client, Ф6 (package R): a Dispatcher's flights tab. */
    public static final int FLIGHT_STATE = 3, FLIGHT_RESULT = 4;
    /** To the server. */
    public static final int TRADE_DO = 1, CONTRACT_TAKE = 2, CONTRACT_TURN_IN = 3, CONTRACT_DROP = 4, TRADE_REFRESH = 5;
    /** To the server, Ф6 (package R): buy / board a flight at the open Dispatcher's screen; its state again. */
    public static final int FLIGHT_DO = 6, FLIGHT_REFRESH = 7;

    private static SimpleNetworkWrapper channel;

    private SettleNetSC() {
    }

    /** SubstrateNetSC.init: on its channel (preInit, both sides). */
    public static void register(SimpleNetworkWrapper ch) {
        channel = ch;
        ch.registerMessage(ToClientHandler.class, ToClient.class, DISC_TO_CLIENT, Side.CLIENT);
        ch.registerMessage(ToServerHandler.class, ToServer.class, DISC_TO_SERVER, Side.SERVER);
    }

    /** Server -> `p` (nothing for a FakePlayer or a player without a connection). */
    public static void send(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (channel == null || p == null || p instanceof FakePlayer || p.playerNetServerHandler == null) {
            return;
        }
        channel.sendTo(new ToClient(kind, data), p);
    }

    /** Client -> the server. */
    public static void sendToServer(int kind, NBTTagCompound data) {
        if (channel != null) {
            channel.sendToServer(new ToServer(kind, data));
        }
    }

    /** A kind and its compound. */
    public abstract static class Payload implements IMessage {
        public int kind;
        public NBTTagCompound data;

        public Payload() {
        }

        public Payload(int kind, NBTTagCompound data) {
            this.kind = kind;
            this.data = data == null ? new NBTTagCompound() : data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readUnsignedByte();
            data = ByteBufUtils.readTag(buf);
            if (data == null) {
                data = new NBTTagCompound();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
            ByteBufUtils.writeTag(buf, data);
        }
    }

    public static class ToClient extends Payload {
        public ToClient() {
        }

        public ToClient(int kind, NBTTagCompound data) {
            super(kind, data);
        }
    }

    public static class ToServer extends Payload {
        public ToServer() {
        }

        public ToServer(int kind, NBTTagCompound data) {
            super(kind, data);
        }
    }

    public static class ToClientHandler implements IMessageHandler<ToClient, IMessage> {
        @Override
        public IMessage onMessage(ToClient msg, MessageContext ctx) {
            SCMod.proxy.settleMessage(msg.kind, msg.data);
            return null;
        }
    }

    public static class ToServerHandler implements IMessageHandler<ToServer, IMessage> {
        @Override
        public IMessage onMessage(ToServer msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (p != null && (msg.kind == FLIGHT_DO || msg.kind == FLIGHT_REFRESH)) {
                com.sc.embassy.FlightServerSC.onMessage(p, msg.kind, msg.data);   // Ф6: the flights (package R)
            } else if (p != null) {
                com.sc.settle.trade.TradeServerSC.onMessage(p, msg.kind, msg.data);
            }
            return null;
        }
    }
}
