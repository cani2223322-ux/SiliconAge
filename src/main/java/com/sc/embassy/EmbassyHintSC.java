package com.sc.embassy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE T (docs/plan-substrate-tech-f6.md §8.5, rule ПЕ-11; proposal §8 «строка при первом входе»): a player's first login into a
 * world with embassies (the persistent NBT flag NBT, by UUID - the LAN friend gets his own) - one chat line a few seconds after it:
 * «Перехвачен сигнал: Посольство „…“, ≈ N блоков на запад» - the nearest embassy to him within ItemEmbassyFinderSC.SEARCH (most often
 * the guaranteed one), its name if made, else «без позывного»; none - no line (and the flag set). Registered by SCMod on FML's bus.
 * Fixed (the coordinator's): the class, INSTANCE, NBT, DELAY_TICKS. Bodies T's.
 * <p>
 * T's details: the login (or an arrival in the Overworld - a player who logged in in the Substrate hears it when he comes up) puts him
 * on the list for DELAY_TICKS of the server's ticks; then, if he is still on the server and in the Overworld, ItemEmbassyFinderSC.heard
 * from his feet answers, the flag is set and the line - if anything was heard - goes to him. A world without embassies (embassy
 * embassies off, the Substrate off, a flat world or structures off - EmbassySiteSC.worldFits) sets no flag: the line waits for a world
 * that has them. Never a FakePlayer. Server thread only.
 */
public final class EmbassyHintSC {

    public static final EmbassyHintSC INSTANCE = new EmbassyHintSC();
    /** In the player's persistent NBT: the line was told. */
    public static final String NBT = "scEmbHint";
    /** The line comes this long after the login (ticks): after the welcome lines. */
    public static final int DELAY_TICKS = 100;

    /** Players waiting for the line: UUID -> the server tick it is due at. */
    private static final Map<UUID, Long> DUE = new HashMap<UUID, Long>();
    /** The server's ticks since it started (ServerTickEvent END). */
    private static long ticks;

    private EmbassyHintSC() {
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        schedule(e.player);
    }

    /** Came up into the Overworld (the bridge, a flight, a command) without having heard it yet. */
    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.toDim == EmbassySiteSC.DIM) {
            schedule(e.player);
        }
    }

    /** Respawned (a death in the Substrate brings him up to the Overworld): the same - tell() looks where he is. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        schedule(e.player);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (DUE.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Long>> it = DUE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> d = it.next();
            if (ticks < d.getValue().longValue()) {
                continue;
            }
            it.remove();
            EntityPlayerMP p = online(d.getKey());
            if (p != null) {
                tell(p);
            }
        }
    }

    /** The player's flag: he heard the line (or there was nothing to hear in a world with embassies). */
    public static boolean told(EntityPlayer p) {
        return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getBoolean(NBT);
    }

    /** Server stopped. */
    public static void clearAll() {
        DUE.clear();
        ticks = 0L;
        ItemEmbassyFinderSC.clearAll();
    }

    // ------------------------------------------------------------------

    private static void schedule(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP) || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote || told(p)
                || !SettlementSC.embassiesGenerate()) {
            return;
        }
        UUID id = p.getUniqueID();
        if (!DUE.containsKey(id)) {
            DUE.put(id, Long.valueOf(ticks + DELAY_TICKS));
        }
    }

    /** Due now: in the Overworld of a world with embassies - the flag, and the line if an embassy is on the air. */
    private static void tell(EntityPlayerMP p) {
        if (told(p) || !SettlementSC.embassiesGenerate() || !(p.worldObj instanceof WorldServer)
                || p.worldObj.provider.dimensionId != EmbassySiteSC.DIM || !EmbassySiteSC.worldFits(p.worldObj)) {
            return;                                             // elsewhere: his arrival in the Overworld asks again
        }
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        ItemEmbassyFinderSC.Heard h = ItemEmbassyFinderSC.heard((WorldServer) p.worldObj, x, z);
        NBTTagCompound all = p.getEntityData(), data = all.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        data.setBoolean(NBT, true);
        all.setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
        if (h == null) {
            return;                                             // nothing within SEARCH: no line, and never again
        }
        int dx = h.site.x - x, dz = h.site.z - z;
        ChatComponentTranslation dir = new ChatComponentTranslation("sc.finder.dir."
                + com.sc.ruin.ItemRuinFinderSC.DIRS[com.sc.ruin.ItemRuinFinderSC.direction(dx, dz)]);
        String dist = String.valueOf(ItemEmbassyFinderSC.distance(dx, dz));
        ChatComponentTranslation m = h.name() != null ? new ChatComponentTranslation("sc.embassy.hint", h.name(), dist, dir)
                : new ChatComponentTranslation("sc.embassy.hint.nameless", dist, dir);
        m.getChatStyle().setColor(EnumChatFormatting.AQUA);
        p.addChatComponentMessage(m);
    }

    /** The real player of `id` on the server now, or null. */
    private static EntityPlayerMP online(UUID id) {
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null || s.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : s.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }
}
