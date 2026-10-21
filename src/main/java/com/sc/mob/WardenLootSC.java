package com.sc.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.finale.FinaleItemsSC;
import com.sc.finale.FinaleRulesSC;
import com.sc.init.ModItems;
import com.sc.item.SingularProgressSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.6, rules ПТ-21, ПТ-22): the Breakdown Warden's personal loot that couldn't be handed
 * over at the kill (the player offline, dead, in another world) - kept in his settlements' record (SettlementRegistrySC.PlayerRec.extra
 * WardenRulesSC.EXTRA, its PENDING list of item stacks: global storage, by UUID) and given on his next login or respawn, anywhere, with
 * sc.warden.loot.pending; a full inventory - at his feet, his own for MobDropsSC.OWNER_TICKS (MobEventsSC.lockTrophy). Registered by
 * the coordinator on FML's bus. Fixed: the class, INSTANCE, deliverPending, clearAll; the bodies B's. Server only.
 * <p>
 * Package B - the whole personal loot (award, from EntityBreakdownWardenSC.ownDrops; ПТ-21..ПТ-24): its arena's own Warden
 * (EntityBreakdownWardenSC.arenaWarden: born in Si-14, summoned at its arena, its fight still the arena's current one - a stale
 * Warden left over from a reset fight gives nothing, the key stays in the Console), dying in the Substrate, doMobLoot on - else
 * experience only. Every player with lootEligible(share) of the damage (MobEventsSC.damageShares: the blows the suit's helium
 * shield takes count; the coils' shield refuses a blow whole - no damage, no share; kept in the Warden's NBT across an unload and
 * a restart - WardenFightSC) - online or not, by UUID - gets his own, never the ground's:
 * <ul>
 * <li>his record W: d - the game day (SettlementSC.day) of his last loot, k - his kills with loot, f - his first kill was, p - the
 * loot kept for him; lootDue(d) - else sc.warden.loot.cooldown (the days left) and nothing (the task counts all the same);</li>
 * <li>lootStacks: the Breakdown Matrix, CRUMBS_MIN..MAX Singularity Crumbs (real ones, past CrumbLimitSC: it is never asked),
 * CLOTS_MIN..MAX Clots, SCRAP_MIN..MAX Singular Armour Scrap, PAINT_CHANCE the paint scheme «Литограф», one trophy roll
 * (trophyMeta at gearDropMultiplier x his Looting - TrophyTableSC's step and cap; the ПЭ-1М trophy only if it held one), his first
 * kill's Journal №12 (sc.warden.journal);</li>
 * <li>online, alive and in the Substrate - into his inventory (sc.warden.loot; what doesn't fit at his feet, his own); else kept in
 * W.p (sc.warden.loot.kept if he is online) - deliverPending gives it at his next login or respawn;</li>
 * <li>SingularProgressSC.wardenDefeated for each online one (the task «Победить Смотрителя Пробоя»; it asks his worn suit itself).</li>
 * </ul>
 * A FakePlayer never gets anything (it is never in the damage shares, and deliverPending refuses it).
 */
public final class WardenLootSC {

    public static final WardenLootSC INSTANCE = new WardenLootSC();
    /** At most this many stacks wait for a player (a cap, never reached by the 3 days' cooldown in practice). */
    public static final int PENDING_MAX = 128;

    private WardenLootSC() {
    }

    /** Server stopped: nothing kept. */
    public static void clearAll() {
        // stateless: everything is in the settlements' registry
    }

    // ------------------------------------------------------------------ the kill (ПТ-21..ПТ-24)

    /**
     * Server (EntityBreakdownWardenSC.ownDrops): the personal loot of `boss`'s death for every player of `shares` (UUID -> share of
     * the damage) with lootEligible; `doMobLoot` - the gamerule. Public for the world test (shares of two registry records).
     * @return the players whose loot was given or kept (not the cooldown's)
     */
    public static int award(EntityBreakdownWardenSC boss, Map<UUID, Float> shares, boolean doMobLoot) {
        World w = boss == null ? null : boss.worldObj;
        if (w == null || w.isRemote || shares == null || !doMobLoot || !boss.arenaWarden()) {
            return 0;                                           // ПТ-1: an egg's Warden, a stale one, another world, the gamerule - experience only
        }
        SettlementRegistrySC r = SettlementSC.registry(w);
        if (r == null) {
            return 0;
        }
        long day = SettlementSC.day(w);
        boolean pe1m = boss.holdsPe1m();
        int n = 0;
        for (Map.Entry<UUID, Float> en : shares.entrySet()) {
            if (en.getKey() == null || en.getValue() == null || !WardenRulesSC.lootEligible(en.getValue().floatValue())) {
                continue;                                       // under 10%: experience only
            }
            EntityPlayerMP p = online(en.getKey());
            if (lootFor(r, en.getKey(), p, day, pe1m, boss.getRNG())) {
                n++;
            }
            if (p != null) {
                SingularProgressSC.wardenDefeated(p);           // ПК-23: the task - each of the fight's players, the cooldown too
            }
        }
        return n;
    }

    /** One player's loot by his record W. @return given or kept (false: the cooldown) */
    private static boolean lootFor(SettlementRegistrySC r, UUID id, EntityPlayerMP p, long day, boolean pe1m, Random rand) {
        SettlementRegistrySC.PlayerRec rec = r.player(id);
        NBTTagCompound wr = rec.extra.getCompoundTag(WardenRulesSC.EXTRA);
        boolean had = wr.hasKey(WardenRulesSC.LAST_DAY);
        long last = wr.getLong(WardenRulesSC.LAST_DAY);
        if (!WardenRulesSC.lootDue(had, last, day)) {
            if (p != null) {
                long left = Math.max(1L, WardenRulesSC.lootCooldownDays() - (day - last));   // ПМ-6: as the server sets it
                p.addChatComponentMessage(new ChatComponentTranslation("sc.warden.loot.cooldown", Long.valueOf(left)));
            }
            return false;
        }
        boolean first = !wr.getBoolean(WardenRulesSC.FIRST);
        List<ItemStack> loot = lootStacks(rand, first, pe1m, trophyMul(p));
        wr.setLong(WardenRulesSC.LAST_DAY, day);
        wr.setInteger(WardenRulesSC.KILLS, wr.getInteger(WardenRulesSC.KILLS) + 1);
        wr.setBoolean(WardenRulesSC.FIRST, true);
        if (p != null && p.isEntityAlive() && SubstrateSC.is(p.worldObj)) {
            hand(p, loot);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.warden.loot"));
        } else {
            keep(wr, loot);                                     // offline, dead, elsewhere: it waits for him
            if (p != null) {
                p.addChatComponentMessage(new ChatComponentTranslation("sc.warden.loot.kept"));
            }
        }
        if (first && p != null) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.warden.journal"));
        }
        rec.extra.setTag(WardenRulesSC.EXTRA, wr);
        r.markDirty();
        return true;
    }

    /**
     * ПТ-22..ПТ-24 (pure but for the dice and the registered items): one player's loot - the Matrix, the Crumbs, the Clots, the Scrap,
     * PAINT_CHANCE the scheme «Литограф», one trophy roll at `trophyMul` (the ПЭ-1М only when `pe1m` - it held one), the Journal №12
     * when `first`. Public for the self-test and the world test.
     */
    public static List<ItemStack> lootStacks(Random rand, boolean first, boolean pe1m, float trophyMul) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        add(out, RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_MATRIX, WardenRulesSC.MATRIX));
        if (ModItems.singularCrumb != null) {                    // real Crumbs, beyond the daily cap (ПТ-17: the boss is excluded)
            add(out, new ItemStack(ModItems.singularCrumb, between(rand, WardenRulesSC.CRUMBS_MIN, WardenRulesSC.CRUMBS_MAX)));
        }
        if (ModItems.singularClot != null) {
            add(out, new ItemStack(ModItems.singularClot, between(rand, WardenRulesSC.CLOTS_MIN, WardenRulesSC.CLOTS_MAX)));
        }
        add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.SINGULAR_SCRAP, between(rand, WardenRulesSC.SCRAP_MIN, WardenRulesSC.SCRAP_MAX)));
        if (rand.nextFloat() < WardenRulesSC.PAINT_CHANCE) {
            add(out, FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_LITHOGRAPHER, 1));
        }
        int meta = WardenRulesSC.trophyMeta(rand.nextFloat(), trophyMul);
        if (meta == TrophyTableSC.META_PE1M && !pe1m) {
            meta = -1;                                          // no ПЭ-1М in its hand (the mobs' ПЭ-1 off) - no such trophy
        }
        if (meta >= 0) {
            add(out, MobItemsSC.trophyStack(meta));
        }
        if (first) {
            add(out, FinaleItemsSC.journal(WardenRulesSC.FIRST_KILL_JOURNAL));
        }
        return out;
    }

    /** ПТ-23: the trophy roll's multiplier for `p` - gearDropMultiplier x his Looting (TrophyTableSC's +5% a level, at most x1.3). */
    public static float trophyMul(EntityPlayer p) {
        int looting = p == null ? 0 : EnchantmentHelper.getLootingModifier(p);
        float loot = Math.min(TrophyTableSC.LOOTING_CAP, 1F + TrophyTableSC.LOOTING_STEP * Math.max(0, looting));
        return Math.max(0F, SubstrateConfigSC.gearDropMultiplier) * loot;
    }

    private static int between(Random rand, int min, int max) {
        return min + rand.nextInt(Math.max(1, max - min + 1));
    }

    private static void add(List<ItemStack> out, ItemStack s) {
        if (s != null && s.stackSize > 0) {
            out.add(s);
        }
    }

    // ------------------------------------------------------------------ handing over, keeping

    /** Into `p`'s inventory; what doesn't fit - at his feet, his own for MobDropsSC.OWNER_TICKS. */
    private static void hand(EntityPlayerMP p, List<ItemStack> loot) {
        for (ItemStack s : loot) {
            ItemStack c = s.copy();
            p.inventory.addItemStackToInventory(c);
            if (c.stackSize > 0) {
                EntityItem e = new EntityItem(p.worldObj, p.posX, p.posY + 0.5D, p.posZ, c);
                e.delayBeforeCanPickup = 10;
                e.func_145797_a(p.getCommandSenderName());     // his (ПТ-2's lock: a friend or a Marauder can't take it at once)
                MobEventsSC.lockTrophy(e, p.worldObj.getTotalWorldTime() + MobDropsSC.OWNER_TICKS);
                p.worldObj.spawnEntityInWorld(e);
            }
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    /** Into the record's PENDING list (at most PENDING_MAX stacks). */
    private static void keep(NBTTagCompound wr, List<ItemStack> loot) {
        NBTTagList l = wr.getTagList(WardenRulesSC.PENDING, 10);
        for (ItemStack s : loot) {
            if (l.tagCount() >= PENDING_MAX) {
                break;
            }
            NBTTagCompound t = new NBTTagCompound();
            s.writeToNBT(t);
            l.appendTag(t);
        }
        wr.setTag(WardenRulesSC.PENDING, l);
    }

    /**
     * Server: the loot kept for player `id`, taken out of his record (empty: none, or no registry). Public for the world test (a
     * FakePlayer isn't given anything - the test takes the stacks itself).
     */
    public static List<ItemStack> takePending(World w, UUID id) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        SettlementRegistrySC r = w == null || w.isRemote ? null : SettlementSC.registry(w);
        SettlementRegistrySC.PlayerRec rec = r == null ? null : r.playerIfAny(id);
        if (rec == null || !rec.extra.hasKey(WardenRulesSC.EXTRA)) {
            return out;
        }
        NBTTagCompound wr = rec.extra.getCompoundTag(WardenRulesSC.EXTRA);
        if (!wr.hasKey(WardenRulesSC.PENDING)) {
            return out;
        }
        NBTTagList l = wr.getTagList(WardenRulesSC.PENDING, 10);
        for (int i = 0; i < l.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(l.getCompoundTagAt(i));
            if (s != null && s.stackSize > 0) {
                out.add(s);                                     // an item of a mod gone since: skipped
            }
        }
        wr.removeTag(WardenRulesSC.PENDING);
        rec.extra.setTag(WardenRulesSC.EXTRA, wr);
        r.markDirty();
        return out;
    }

    /** The loot kept for `id` (a count of stacks - the world test, the handbook's line). */
    public static int pendingCount(World w, UUID id) {
        SettlementRegistrySC r = w == null || w.isRemote ? null : SettlementSC.registry(w);
        SettlementRegistrySC.PlayerRec rec = r == null ? null : r.playerIfAny(id);
        return rec == null ? 0 : rec.extra.getCompoundTag(WardenRulesSC.EXTRA).getTagList(WardenRulesSC.PENDING, 10).tagCount();
    }

    /** The loot kept for `p`, given now (a real, living player). B's body. */
    public static void deliverPending(EntityPlayerMP p) {
        if (p == null || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote || !p.isEntityAlive()) {
            return;                                             // dead at login: his respawn delivers it
        }
        List<ItemStack> loot = takePending(p.worldObj, p.getUniqueID());
        if (loot.isEmpty()) {
            return;
        }
        hand(p, loot);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.warden.loot.pending"));
    }

    /** The online real player of `id`, or null. */
    private static EntityPlayerMP online(UUID id) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            deliverPending((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            deliverPending((EntityPlayerMP) e.player);
        }
    }
}
