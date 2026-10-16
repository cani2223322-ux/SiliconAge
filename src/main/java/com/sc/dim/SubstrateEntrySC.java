package com.sc.dim;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sc.block.BlockQuarrySC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.util.TickClock;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

/**
 * PACKAGE E (docs/plan-substrate-tech.md §6): who gets into the Substrate and how (rules ПВ-1..ПВ-12) - strictEntry
 * (PlayerChangedDimensionEvent: an arrival without a grant goes back through TeleportQueueSC next tick), the nether
 * portal kept dead (timeUntilPortal), beds refused (PlayerInteractEvent on a bed - with canRespawnHere = false vanilla's
 * bed explodes in onBlockActivated BEFORE PlayerSleepInBedEvent; that one is caught too for other mods' beds), the
 * first arrival (the message, «Буфер прокола»: Resistance II 10 s), natives at the bridge.
 * <p>
 * Registered by SCMod on both buses (Forge's and FML's); E adds the @SubscribeEvent methods. The static API below is
 * fixed: the bridge (BridgeTeleportSC) grants every player it carries - and the Vehicles add-on's Jump carries its
 * riders through BridgeTeleportSC, so it is let in already; its next release may ask allowsArrival first.
 * <p>
 * How it holds (Forge 1614 has no event before a dimension change - nothing can be cancelled in advance):
 * <ul>
 * <li>a grant is a line in a server map by UUID - the way and the server tick it runs out (GRANT_TICKS); the arrival
 * uses it up, any other dimension change drops it. Creative and server ops always pass;</li>
 * <li>an arrival without one is put on TeleportQueueSC for the next tick (never moved from inside the server's own
 * transfer): back to the dimension it came from, to the last safe place the player stood there (noted every
 * SPOT_TICKS outside the Substrate: on the ground, dry, not riding), else to that world's spawn, else the
 * Overworld's. The queue checks the place (SafeSpotSC);</li>
 * <li>the nether portal: Entity.setInPortal only renews the cooldown while timeUntilPortal &gt; 0, never sets
 * inPortal - so every entity in the Substrate keeps timeUntilPortal &gt;= PORTAL_HOLD (set as it comes in, then
 * players by their tick, the rest by the world's), and the portal there never carries anyone (else it would be a
 * free way out through the Nether).</li>
 * </ul>
 * The maps are the server's and its thread's only; serverStopped clears them (clearAll).
 */
public final class SubstrateEntrySC {

    public static final SubstrateEntrySC INSTANCE = new SubstrateEntrySC();

    /** The ways in the core knows: the Space Bridge (BridgeTeleportSC), the Vehicles ship's Jump. */
    public static final String VIA_BRIDGE = "bridge", VIA_JUMP = "sva.jump";

    /**
     * In the player's kept data (EntityPlayer.PERSISTED_NBT_TAG - it outlives a death): been here once (the welcome
     * only the first time); {dim, x, y, z, via} where the last lawful trip in started - next to the bridge's end, for
     * the Return Capsule (Ф2).
     */
    public static final String NBT_SEEN = "scSi14Seen", NBT_FROM = "scSi14From";

    /** A grant: the way, the server tick it runs out at, where the player stood when it was given. */
    private static final class Grant {
        final String via;
        final int until, dim;
        final double x, y, z;

        Grant(String via, int until, int dim, double x, double y, double z) {
            this.via = via;
            this.until = until;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** The last safe place a player stood at outside the Substrate (strictEntry's way back). */
    private static final class Spot {
        final int dim;
        final double x, y, z;
        final float yaw;

        Spot(int dim, double x, double y, double z, float yaw) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    private static final Map<UUID, Grant> GRANTS = new HashMap<UUID, Grant>();
    private static final Map<UUID, Spot> SPOTS = new HashMap<UUID, Spot>();
    /** The server tick a chat line may come again from: [SAY_PORTAL], [SAY_BED]. */
    private static final Map<UUID, int[]> SAID = new HashMap<UUID, int[]>();
    private static final int SAY_PORTAL = 0, SAY_BED = 1;

    private SubstrateEntrySC() {
    }

    /**
     * May `p` arrive in the Substrate by `via` (VIA_BRIDGE, VIA_JUMP, another mod's name)? The add-on's hook: asked
     * before a Jump; true also when strictEntry is off or the Substrate is absent.
     */
    public static boolean allowsArrival(EntityPlayer p, String via) {
        if (!SubstrateSC.active()) {
            return true;
        }
        boolean creative = p != null && p.capabilities.isCreativeMode;
        return EntryRulesSC.lawful(SubstrateConfigSC.strictEntry, EntryRulesSC.knownWay(via), creative, BlockQuarrySC.isOp(p));
    }

    /** Server: `p` is about to be carried into the Substrate by `via` - the next arrival within a few ticks is lawful. */
    public static void grantArrival(EntityPlayer p, String via) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer) {
            return;
        }
        GRANTS.put(p.getUniqueID(), new Grant(via, tick() + EntryRulesSC.GRANT_TICKS, p.worldObj.provider.dimensionId,
                p.posX, p.posY, p.posZ));
    }

    /** A grant waits for `p` (the world test). */
    public static boolean granted(EntityPlayer p) {
        Grant g = p == null ? null : GRANTS.get(p.getUniqueID());
        return g != null && tick() <= g.until;
    }

    /** Server: may entity `e` leave through a bridge to `toDim` (natives with nativesCrossBridge off: no). */
    public static boolean mayCross(Entity e, int toDim) {
        if (e == null) {
            return true;
        }
        int at = e.worldObj != null ? e.worldObj.provider.dimensionId : e.dimension;
        return EntryRulesSC.mayCross(e instanceof EntityPlayer, e instanceof ISubstrateNativeSC, at != toDim, SubstrateConfigSC.nativesCrossBridge);
    }

    /** Server stopped: every grant and record of this server dropped. */
    public static void clearAll() {
        GRANTS.clear();
        SPOTS.clear();
        SAID.clear();
    }

    // ------------------------------------------------------------------ strictEntry and the arrival (FML's bus)

    /** Fires inside ServerConfigurationManager.transferPlayerToDimension, the player already in the new world. */
    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!real(event.player)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        UUID id = p.getUniqueID();
        Grant g = GRANTS.remove(id);                       // used up by this change, whatever it is
        if (!SubstrateSC.isDim(event.toDim)) {
            SPOTS.remove(id);                              // a new world: its own first safe place is noted afresh
            return;
        }
        holdPortal(p);                                     // from the first tick (a creative player needs only one in a portal)
        boolean granted = g != null && tick() <= g.until;
        if (EntryRulesSC.lawful(SubstrateConfigSC.strictEntry, granted, p.capabilities.isCreativeMode, BlockQuarrySC.isOp(p))) {
            arrived(p, event.fromDim, granted ? g : null);
            return;
        }
        double[] b = wayBack(p, event.fromDim);
        TeleportQueueSC.queue(p, (int) b[0], b[1], b[2], b[3], (float) b[4], "sc.dim.entry.refused");
        FMLLog.info("[Silicon Age] Si-14 Substrate: %s came in from dimension %d not by the bridge (dimension.strictEntry) - sent back to dimension %d",
                p.getCommandSenderName(), event.fromDim, (int) b[0]);
    }

    /** ПВ-7: a lawful arrival - the «Буфер прокола», the way back noted, the welcome the first time. */
    private static void arrived(EntityPlayerMP p, int fromDim, Grant g) {
        p.addPotionEffect(new PotionEffect(Potion.resistance.id, EntryRulesSC.BUFFER_TICKS, EntryRulesSC.BUFFER_AMPLIFIER));
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound kept = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        Spot s = SPOTS.get(p.getUniqueID());
        NBTTagCompound from = null;
        if (g != null) {
            from = point(g.dim, g.x, g.y, g.z);            // where the bridge took the player from
            if (g.via != null) {
                from.setString("via", g.via);
            }
        } else if (s != null && s.dim == fromDim) {
            from = point(s.dim, s.x, s.y, s.z);            // creative / an op / strictEntry off: the last place there
        }
        if (from != null) {
            kept.setTag(NBT_FROM, from);
        }
        boolean first = !kept.getBoolean(NBT_SEEN);
        kept.setBoolean(NBT_SEEN, true);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, kept);
        if (first) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.dim.entry.welcome")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_AQUA)));
            p.addChatComponentMessage(new ChatComponentTranslation("sc.dim.entry.book")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        }
    }

    private static NBTTagCompound point(int dim, double x, double y, double z) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("dim", dim);
        t.setDouble("x", x);
        t.setDouble("y", y);
        t.setDouble("z", z);
        return t;
    }

    /**
     * ПВ-3: {dim, x, y, z, yaw} to send `p` back to - the last safe place in fromDim; else that world's spawn (its top
     * under the sky); else the Overworld's spawn (fromDim gone, or it won't load).
     */
    private static double[] wayBack(EntityPlayerMP p, int fromDim) {
        Spot s = SPOTS.get(p.getUniqueID());
        if (s != null && s.dim == fromDim) {
            return new double[]{s.dim, s.x, s.y, s.z, s.yaw};
        }
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer w = null;
        if (srv != null && !SubstrateSC.isDim(fromDim) && DimensionManager.isDimensionRegistered(fromDim)) {
            try {
                w = srv.worldServerForDimension(fromDim);
            } catch (RuntimeException ex) {
                w = null;
            }
        }
        if (w == null && srv != null) {
            w = srv.worldServerForDimension(0);
        }
        if (w == null) {
            return new double[]{0, 0.5, 64, 0.5, p.rotationYaw};
        }
        ChunkCoordinates c = w.getSpawnPoint();
        return new double[]{w.provider.dimensionId, c.posX + 0.5, SafeSpotSC.surfaceY(w, c.posX, c.posY, c.posZ), c.posZ + 0.5, p.rotationYaw};
    }

    /** Players: the portal kept dead in the Substrate; outside it, the last safe place noted (strictEntry's way back). */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !real(event.player)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        if (SubstrateSC.is(p.worldObj)) {
            if (TickClock.mod(p, EntryRulesSC.PORTAL_CHECK) == 0) {
                holdPortal(p);
                if (inPortal(p)) {
                    // ПВ-4: «Прокол глушит портал» - the frame sparks round the player standing in it
                    ((WorldServer) p.worldObj).func_147487_a("fireworksSpark", p.posX, p.posY + 1, p.posZ, 8, 0.3, 0.6, 0.3, 0.05);
                    if (say(p, SAY_PORTAL, "sc.dim.portal.dead", EntryRulesSC.PORTAL_SAY_TICKS)) {
                        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.fizz", 0.5F, 1.6F);
                    }
                }
            }
            return;
        }
        UUID id = p.getUniqueID();
        if ((TickClock.mod(p, EntryRulesSC.SPOT_TICKS) == 0 || !SPOTS.containsKey(id)) && p.getHealth() > 0F
                && EntryRulesSC.safeToNote(p.onGround, p.isInWater() || p.handleLavaMovement(), p.ridingEntity != null, p.isPlayerSleeping())) {
            SPOTS.put(id, new Spot(p.worldObj.provider.dimensionId, p.posX, p.posY, p.posZ, p.rotationYaw));
        }
    }

    /** Every other entity in the Substrate: the portal kept dead, every PORTAL_CHECK ticks by the server's clock. */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        World w = event.world;
        if (event.phase != TickEvent.Phase.END || w == null || w.isRemote || !SubstrateSC.is(w) || tick() % EntryRulesSC.PORTAL_CHECK != 0) {
            return;
        }
        for (Object o : w.loadedEntityList) {
            if (o instanceof Entity && !(o instanceof EntityPlayer)) {
                holdPortal((Entity) o);
            }
        }
    }

    /** Forge's bus: whatever comes into the Substrate (spawned, carried, loaded with its chunk) has the portal dead at once. */
    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity != null && event.world != null && !event.world.isRemote && SubstrateSC.is(event.world)) {
            holdPortal(event.entity);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            UUID id = event.player.getUniqueID();
            GRANTS.remove(id);
            SPOTS.remove(id);
            SAID.remove(id);
        }
    }

    private static void holdPortal(Entity e) {
        if (e.timeUntilPortal < EntryRulesSC.PORTAL_HOLD) {
            e.timeUntilPortal = EntryRulesSC.PORTAL_HOLD;
        }
    }

    /** The player's feet or head in a nether portal block. */
    private static boolean inPortal(EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.boundingBox.minY + 0.01), z = MathHelper.floor_double(p.posZ);
        return p.worldObj.getBlock(x, y, z) == Blocks.portal || p.worldObj.getBlock(x, y + 1, z) == Blocks.portal;
    }

    // ------------------------------------------------------------------ beds (Forge's bus)

    /**
     * ПВ-10: a right click on a bed in the Substrate is refused before the bed is used - with canRespawnHere false
     * vanilla's BlockBed explodes in onBlockActivated, before any sleeping event. Server side: a client's click still
     * goes to the server (its own BlockBed does nothing there), which refuses it, says why and puts the block back.
     * Sneaking with an item in hand uses the item, not the bed - left alone; but not an item whose doesSneakBypassUse
     * is true (wrenches: the mod's own, BC's...) - ItemInWorldManager activates the block for it all the same.
     */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        EntityPlayer p = event.entityPlayer;
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || p == null || p.worldObj == null || p.worldObj.isRemote
                || !SubstrateSC.is(p.worldObj)) {
            return;
        }
        World w = p.worldObj;
        ItemStack held = p.getHeldItem();
        if (p.isSneaking() && held != null && held.getItem() != null
                && !held.getItem().doesSneakBypassUse(w, event.x, event.y, event.z, p)) {
            return;                                        // the item is used, the bed never is
        }
        if (w.getBlock(event.x, event.y, event.z).isBed(w, event.x, event.y, event.z, p)) {
            event.setCanceled(true);
            say(p, SAY_BED, "sc.dim.bed", EntryRulesSC.BED_SAY_TICKS);
        }
    }

    /** ПВ-10: other mods' beds and sleeping bags (whatever goes through EntityPlayer.sleepInBedAt). */
    @SubscribeEvent
    public void onSleep(PlayerSleepInBedEvent event) {
        EntityPlayer p = event.entityPlayer;
        if (p == null || p.worldObj == null || p.worldObj.isRemote || !SubstrateSC.is(p.worldObj)) {
            return;
        }
        event.result = EntityPlayer.EnumStatus.OTHER_PROBLEM;
        say(p, SAY_BED, "sc.dim.bed", EntryRulesSC.BED_SAY_TICKS);
    }

    // ------------------------------------------------------------------ helpers

    /** A real player on the server (not another mod's FakePlayer). */
    private static boolean real(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && p.worldObj != null && !p.worldObj.isRemote;
    }

    /** The chat line `key`, at most once in `every` ticks per kind. @return whether it was said */
    private static boolean say(EntityPlayer p, int kind, String key, int every) {
        if (p instanceof FakePlayer) {
            return false;
        }
        UUID id = p.getUniqueID();
        int[] next = SAID.get(id);
        if (next == null) {
            next = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
            SAID.put(id, next);
        }
        int now = tick();
        if (now < next[kind]) {
            return false;
        }
        next[kind] = now + every;
        p.addChatComponentMessage(new ChatComponentTranslation(key));
        return true;
    }

    /** The server's tick (the same clock in every dimension, unlike a world's time under another mod's WorldInfo). */
    private static int tick() {
        MinecraftServer srv = MinecraftServer.getServer();
        return srv == null ? 0 : srv.getTickCounter();
    }
}
