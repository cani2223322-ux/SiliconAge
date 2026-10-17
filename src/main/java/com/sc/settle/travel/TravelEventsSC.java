package com.sc.settle.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sc.dim.SafeSpotSC;
import com.sc.dim.SubstrateEntrySC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.TeleportQueueSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * PACKAGE B (docs/plan-substrate-tech-f2.md §8.4-§8.6): the travel side's events - registered by SCMod on Forge's bus
 * (LivingDeathEvent: the death mark; LivingHurtEvent: a capsule knocked down) and FML's (PlayerTickEvent: the capsule's
 * countdown, the death mark put out on arrival; PlayerLoggedOutEvent). Per player UUID, server only; emptied when the
 * server stops. The signatures are fixed; the bodies are package B's.
 * <p>
 * ПР-4, the Return Capsule: a right-click in the Substrate starts a timer here - the server's tick it began at, by the
 * player's UUID (never the item-in-use: its accessors are client-only and the capsule must not need the button held).
 * A line «Капсула: подготовка… N с» every CAPSULE_SAY ticks and a few portal sparks round the player (both LAN players
 * see them: WorldServer sends the particles); any damage knocks it down, leaving the Substrate or dying drops it;
 * after CAPSULE_TICKS one capsule goes from the inventory (none left - nothing happens) and TeleportQueueSC carries
 * the player to SubstrateEntrySC.NBT_FROM (the persistent NBT: where the bridge took them from), or, with no such
 * point, to the Overworld's spawn; SafeSpotSC puts them down in a free place outside other players' fields.
 * <p>
 * ПР-6, the death mark: a real player's death in the Substrate writes PlayerRec.hasDeath / deathDim / x, y, z /
 * deathAt (a newer death replaces it) and says so; the bridge lists it (TileEntityBridgeControllerSC.si14Targets). It
 * goes out once the player, alive in the Substrate, is closer than DEATH_CLEAR to it (checked every DEATH_CHECK ticks).
 * Another mod's FakePlayer never starts a capsule and never leaves a mark.
 */
public final class TravelEventsSC {

    public static final TravelEventsSC INSTANCE = new TravelEventsSC();

    /** A capsule being prepared: the server tick it started at, how many countdown lines were told. */
    private static final class Prep {
        final int start;
        int said;

        Prep(int start) {
            this.start = start;
        }
    }

    /** Server thread only (the item's right-click, the events and the ticks all run there). */
    private static final Map<UUID, Prep> CAPSULES = new HashMap<UUID, Prep>();
    /** ПР-5: the server tick a player last had a Settlement Map read out (ItemSettlementMapSC). */
    private static final Map<UUID, Integer> MAP_READ = new HashMap<UUID, Integer>();

    private TravelEventsSC() {
    }

    /** ПР-4 (ItemReturnCapsuleSC, server): `p` starts a capsule's preparation. @return false if refused (not in the Substrate, already running) */
    public static boolean startCapsule(EntityPlayerMP p) {
        if (p == null || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote || p.getHealth() <= 0F) {
            return false;
        }
        if (!SubstrateSC.is(p.worldObj)) {
            say(p, EnumChatFormatting.GRAY, "sc.capsule.notHere");
            return false;
        }
        UUID id = p.getUniqueID();
        if (CAPSULES.containsKey(id)) {
            return false;                                // a held button repeats the click: the countdown already speaks
        }
        Prep c = new Prep(tick());
        CAPSULES.put(id, c);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.orb", 0.6F, 0.6F);
        countdown(p, c, 0);
        return true;
    }

    /** ПР-4: `p`'s capsule is being prepared (the world test, the handbook). */
    public static boolean preparing(EntityPlayer p) {
        return p != null && CAPSULES.containsKey(p.getUniqueID());
    }

    /** ПР-5 (ItemSettlementMapSC, server): the map may be read out to `p` now (not again within MAP_COOLDOWN ticks). */
    public static boolean mayReadMap(EntityPlayer p) {
        UUID id = p.getUniqueID();
        int now = tick();
        Integer last = MAP_READ.get(id);
        if (last != null && now >= last.intValue() && now - last.intValue() < TravelRulesSC.MAP_COOLDOWN) {
            return false;
        }
        MAP_READ.put(id, Integer.valueOf(now));
        return true;
    }

    /** Server stopped: the running preparations go. */
    public static void clearAll() {
        CAPSULES.clear();
        MAP_READ.clear();
    }

    // ------------------------------------------------------------------ the events

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER || !(event.player instanceof EntityPlayerMP)
                || event.player instanceof FakePlayer || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        if (!CAPSULES.isEmpty()) {
            capsuleTick(p);
        }
        if (p.ticksExisted % TravelRulesSC.DEATH_CHECK == 0) {
            markTick(p);
        }
    }

    /** ПР-4: any damage knocks the capsule down (after armour and other mods: a hit cancelled before here is no hit). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (CAPSULES.isEmpty() || !(event.entityLiving instanceof EntityPlayerMP) || event.entityLiving.worldObj.isRemote || event.ammount <= 0F) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.entityLiving;
        if (CAPSULES.remove(p.getUniqueID()) != null) {
            say(p, EnumChatFormatting.RED, "sc.capsule.hit");
            p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.fizz", 0.7F, 1.4F);
        }
    }

    /** ПР-6: a real player's death in the Substrate leaves the death mark (LOWEST: a death another mod cancelled is none). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP) || event.entityLiving instanceof FakePlayer || event.entityLiving.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.entityLiving;
        CAPSULES.remove(p.getUniqueID());
        if (!SubstrateSC.is(p.worldObj)) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
        if (r == null) {
            return;
        }
        int x = MathHelper.floor_double(p.posX), y = Math.max(1, Math.min(255, MathHelper.floor_double(p.posY))), z = MathHelper.floor_double(p.posZ);
        SettlementRegistrySC.PlayerRec rec = r.player(p.getUniqueID());
        rec.hasDeath = true;                             // a newer death replaces the old mark
        rec.deathDim = p.worldObj.provider.dimensionId;
        rec.deathX = x;
        rec.deathY = y;
        rec.deathZ = z;
        rec.deathAt = p.worldObj.getTotalWorldTime();
        r.markDirty();
        say(p, EnumChatFormatting.DARK_AQUA, "sc.death.marked", x, y, z);
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            CAPSULES.remove(event.player.getUniqueID());
            MAP_READ.remove(event.player.getUniqueID());
        }
    }

    // ------------------------------------------------------------------ the capsule

    private static void capsuleTick(EntityPlayerMP p) {
        UUID id = p.getUniqueID();
        Prep c = CAPSULES.get(id);
        if (c == null) {
            return;
        }
        if (p.isDead || p.getHealth() <= 0F) {
            CAPSULES.remove(id);
            return;
        }
        if (!SubstrateSC.is(p.worldObj)) {
            CAPSULES.remove(id);                         // carried out some other way: only a way out of the Substrate
            say(p, EnumChatFormatting.GRAY, "sc.capsule.left");
            return;
        }
        int elapsed = tick() - c.start;
        if (elapsed < 0 || elapsed >= TravelRulesSC.CAPSULE_TICKS) {
            CAPSULES.remove(id);
            finish(p);
            return;
        }
        countdown(p, c, elapsed);
        if (elapsed % 5 == 0 && p.worldObj instanceof WorldServer) {
            ((WorldServer) p.worldObj).func_147487_a("portal", p.posX, p.posY + 1.0D, p.posZ, 4 + elapsed / 25, 0.35D, 0.6D, 0.35D, 0.4D);
        }
    }

    /** The countdown's next line, when due («Капсула: подготовка… N с»). */
    private static void countdown(EntityPlayerMP p, Prep c, int elapsed) {
        if (TravelRulesSC.capsuleSayDue(elapsed, c.said)) {
            c.said = elapsed / TravelRulesSC.CAPSULE_SAY + 1;
            say(p, EnumChatFormatting.DARK_AQUA, "sc.capsule.wait", TravelRulesSC.capsuleSecondsLeft(elapsed));
        }
    }

    /** The preparation is over: one capsule used up, the move queued for the next tick. */
    private static void finish(EntityPlayerMP p) {
        int slot = capsuleSlot(p);
        if (slot < 0) {
            say(p, EnumChatFormatting.GRAY, "sc.capsule.none");
            return;
        }
        double[] to = destination(p);
        if (to == null) {
            say(p, EnumChatFormatting.GRAY, "sc.capsule.nowhere");
            return;
        }
        if (!p.capabilities.isCreativeMode) {
            p.inventory.decrStackSize(slot, 1);
            p.inventoryContainer.detectAndSendChanges();
        }
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 1.0F, 1.3F);
        say(p, EnumChatFormatting.DARK_AQUA, to[4] != 0 ? "sc.capsule.goSpawn" : "sc.capsule.go");
        TeleportQueueSC.queue(p, (int) to[0], to[1], to[2], to[3], p.rotationYaw, "sc.capsule.arrived");
    }

    /** The inventory slot of a Return Capsule (the held one first), -1 none. */
    private static int capsuleSlot(EntityPlayerMP p) {
        ItemStack[] inv = p.inventory.mainInventory;
        int held = p.inventory.currentItem;
        if (held >= 0 && held < inv.length && isCapsule(inv[held])) {
            return held;
        }
        for (int i = 0; i < inv.length; i++) {
            if (isCapsule(inv[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCapsule(ItemStack s) {
        return s != null && s.stackSize > 0 && SettlementItemsSC.returnCapsule != null && s.getItem() == SettlementItemsSC.returnCapsule;
    }

    /**
     * Where the capsule carries `p`: {dim, x, y, z, toSpawn} - the departure point the bridge noted (SubstrateEntrySC.NBT_FROM,
     * ПВ-7), else the Overworld's spawn (its surface). null only with no Overworld at all.
     */
    private static double[] destination(EntityPlayerMP p) {
        NBTTagCompound kept = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (kept.hasKey(SubstrateEntrySC.NBT_FROM, 10)) {
            NBTTagCompound f = kept.getCompoundTag(SubstrateEntrySC.NBT_FROM);
            int d = f.getInteger("dim");
            if (f.hasKey("dim") && !SubstrateSC.isDim(d) && DimensionManager.isDimensionRegistered(d)) {
                return new double[]{d, f.getDouble("x"), f.getDouble("y"), f.getDouble("z"), 0};
            }
        }
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer w = srv == null ? null : srv.worldServerForDimension(0);
        if (w == null) {
            return null;
        }
        ChunkCoordinates s = w.getSpawnPoint();
        return new double[]{0, s.posX + 0.5D, SafeSpotSC.surfaceY(w, s.posX, s.posY, s.posZ), s.posZ + 0.5D, 1};
    }

    // ------------------------------------------------------------------ the death mark

    /** ПР-6: `p`, alive in the Substrate, closer than DEATH_CLEAR to their mark - it goes out (the place is told once more). */
    private static void markTick(EntityPlayerMP p) {
        if (p.isDead || p.getHealth() <= 0F || !SubstrateSC.is(p.worldObj)) {
            return;                                      // still on the death screen: standing right on the mark
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = r == null ? null : r.playerIfAny(p.getUniqueID());
        if (rec == null || !rec.hasDeath || rec.deathDim != p.worldObj.provider.dimensionId) {
            return;
        }
        if (TravelRulesSC.markReached(p.posX - (rec.deathX + 0.5D), p.posY - rec.deathY, p.posZ - (rec.deathZ + 0.5D))) {
            rec.clearDeath();
            r.markDirty();
            say(p, EnumChatFormatting.DARK_AQUA, "sc.death.cleared", rec.deathX, rec.deathY, rec.deathZ);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void say(EntityPlayerMP p, EnumChatFormatting color, String key, Object... args) {
        p.addChatComponentMessage(new ChatComponentTranslation(key, args).setChatStyle(new ChatStyle().setColor(color)));
    }

    /** The server's tick (the same clock in every dimension). */
    private static int tick() {
        MinecraftServer srv = MinecraftServer.getServer();
        return srv == null ? 0 : srv.getTickCounter();
    }
}
