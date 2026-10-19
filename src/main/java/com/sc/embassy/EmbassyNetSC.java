package com.sc.embassy;

import com.sc.SCMod;
import com.sc.settle.SettleNetSC;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §2.7): the embassies' messages on the Substrate's channel "SiliconAgeDim"
 * (SubstrateNetSC.CHANNEL; 0 - its ids / rules, 1 and 2 - the settlements', 3 - the weather's, 4 - the finale's): DISC_TO_CLIENT 5, a
 * kind and an NBT compound, as SettleNetSC's (the package that owns a kind owns its contents). Kinds (fixed, append only), handled by
 * SCMod.proxy.embassyMessage (ClientProxy -> embassy.client.EmbassyClientSC, which hands each to its package's client class):
 * <ul>
 * <li>BEAMS (package S) {list: [{x, y, z}]} - the embassies' beams this client draws in the Overworld (EmbassyBeamSC; the client
 * keeps the newest list, forgets it leaving the Overworld or the server - embassy.client.EmbassyBeamClientSC);</li>
 * <li>FLIGHT_FX (package R) {k: kind, x, y, z, t: ticks} - a flight window's effect at a Substrate dock (no ring there: the channel's
 * column of light) or a passenger carried, to the players within 64 (embassy.client.RingClientSC.onFx).</li>
 * </ul>
 * The flights' screen goes by SettleNetSC (FLIGHT_STATE / FLIGHT_RESULT / FLIGHT_DO / FLIGHT_REFRESH - the trade screen's own channel);
 * the ring's phase by its tile's description packet. Never sent to a FakePlayer. Shared core: only the coordinator edits this file.
 */
public final class EmbassyNetSC {

    public static final int DISC_TO_CLIENT = 5;
    public static final int BEAMS = 1, FLIGHT_FX = 2;

    private static SimpleNetworkWrapper channel;

    private EmbassyNetSC() {
    }

    /** SubstrateNetSC.init: on its channel (preInit, both sides). */
    public static void register(SimpleNetworkWrapper ch) {
        channel = ch;
        ch.registerMessage(ToClientHandler.class, ToClient.class, DISC_TO_CLIENT, Side.CLIENT);
    }

    /** Server -> `p` (nothing for a FakePlayer or a player without a connection). */
    public static void send(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (channel == null || p == null || p instanceof FakePlayer || p.playerNetServerHandler == null) {
            return;
        }
        channel.sendTo(new ToClient(kind, data), p);
    }

    /** Server -> every real player in world `w` within `range` of (x, z) (range <= 0: all of them). */
    public static void sendNear(World w, double x, double z, int range, int kind, NBTTagCompound data) {
        if (w == null || w.isRemote) {
            return;
        }
        double r2 = (double) range * range;
        for (Object o : w.playerEntities) {
            if (!(o instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            double dx = p.posX - x, dz = p.posZ - z;
            if (range <= 0 || dx * dx + dz * dz <= r2) {
                send((EntityPlayerMP) p, kind, data);
            }
        }
    }

    public static class ToClient extends SettleNetSC.Payload {
        public ToClient() {
        }

        public ToClient(int kind, NBTTagCompound data) {
            super(kind, data);
        }
    }

    public static class ToClientHandler implements IMessageHandler<ToClient, IMessage> {
        @Override
        public IMessage onMessage(ToClient msg, MessageContext ctx) {
            SCMod.proxy.embassyMessage(msg.kind, msg.data);
            return null;
        }
    }
}
