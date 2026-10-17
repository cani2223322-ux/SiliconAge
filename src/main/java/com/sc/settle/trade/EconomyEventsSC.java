package com.sc.settle.trade;

import com.sc.block.BlockBridgeSC;
import com.sc.bridge.BridgeMarksSC;
import com.sc.dim.SubstrateSC;
import com.sc.init.ModBlocks;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.MobDropsSC;
import com.sc.mob.MobEventsSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.6, §7.2, rules ПД-2, ПО-8, ПТ-10): the economy's events - SCMod registers
 * it on Forge's bus and FML's. LivingDeathEvent of a hostile native (ISubstrateNativeSC and IMob) under rule ПТ-1
 * (born in Si-14, died in the Substrate, MobEventsSC.killerOf a real EntityPlayerMP - asked before MobDropsSC forgets the
 * log): «Доверие» if within SettlementSC.KILL_REACH of a dome (ReputationSC.killReward, the small ones capped), the
 * open contracts' progress (KILL, SUPPRESS_KILL). BlockEvent.PlaceEvent of an Interdimensional Anchor
 * (ModBlocks.bridge, BlockBridgeSC.ANCHOR) in the Substrate: the world's first one's placer noted
 * (SettlementRegistrySC.anchorBy) for the one-time contract - an anchor already there (a world of 0.2.1, BridgeMarksSC
 * keeps its mark) closes it for everyone instead (anchorTaken). The bodies are package T's.
 * <p>
 * Both handlers run last (LOWEST) and never see a cancelled event: a death another mod undid, a placement the
 * settlement's protection refused, count for nothing. The kill's «Доверие» is silent; a contract's step says one gray
 * line, its last step a coloured one («сдайте Старейшине»). Nothing transient is kept here.
 */
public final class EconomyEventsSC {

    public static final EconomyEventsSC INSTANCE = new EconomyEventsSC();
    /** SettlementRegistrySC.anchorBy of the contract closed by an older anchor (never a UUID). */
    public static final String ANCHOR_TAKEN = "-";

    private EconomyEventsSC() {
    }

    /** Server stopped. */
    public static void clearAll() {
    }

    /** ПД-2, ПО-8: a hostile born in Si-14 killed by a real player in the Substrate. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase mob = e.entityLiving;
        if (mob == null || mob.worldObj == null || mob.worldObj.isRemote || !(mob instanceof ISubstrateNativeSC) || !(mob instanceof IMob)) {
            return;
        }
        EntityPlayerMP killer = MobEventsSC.killerOf(mob);        // the damage log is still there: MobDropsSC forgets it later
        if (!MobDropsSC.qualifies(mob, killer)) {
            return;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(mob.worldObj);
        if (reg == null) {
            return;
        }
        long day = SettlementSC.day(mob.worldObj);
        SettlementRegistrySC.PlayerRec rec = reg.player(killer.getUniqueID());
        MobArmorSC armor = mob instanceof IArmoredMobSC ? ((IArmoredMobSC) mob).mobArmor() : null;
        ArmorSuit suit = armor == null ? null : armor.suit();
        boolean changed = false;
        // ПД-2: near a dome (its edge + KILL_REACH, by the region index)
        if (reg.within(mob.worldObj.provider.dimensionId, mob.posX, mob.posZ, SettlementSC.KILL_REACH) != null) {
            int src = ReputationSC.smallKill(suit) ? ReputationSC.SRC_KILL_SMALL : ReputationSC.SRC_KILL_BIG;
            changed |= ReputationSC.give(rec, ReputationSC.killReward(suit), src, day) != 0;
        }
        // ПО-8: the open contracts that count this kill
        boolean suppressed = armor != null && suit != null && armor.anySuppressed();
        String name = mobName(mob);
        for (SettlementRegistrySC.ContractRec r : rec.open()) {
            ContractTableSC.Contract k = ContractTableSC.byKey(r.key);
            if (k == null || r.progress >= r.target) {
                continue;
            }
            boolean counts = k.goal == ContractTableSC.KILL ? k.what.equals(name) : k.goal == ContractTableSC.SUPPRESS_KILL && suppressed;
            if (counts) {
                r.progress++;
                changed = true;
                say(killer, k, r);
            }
        }
        if (changed) {
            reg.markDirty();
        }
    }

    /** The first Interdimensional Anchor placed in the Substrate by a real player is theirs to be paid for (once a world). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (e.world == null || e.world.isRemote || e.player == null || e.player instanceof FakePlayer || ModBlocks.bridge == null
                || e.placedBlock != ModBlocks.bridge || e.world.getBlockMetadata(e.x, e.y, e.z) != BlockBridgeSC.ANCHOR
                || !SubstrateSC.is(e.world)) {
            return;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(e.world);
        if (reg == null || (reg.anchorBy != null && !reg.anchorBy.isEmpty()) || anchorTaken(e.world, reg, e.x, e.y, e.z)) {
            return;
        }
        reg.anchorBy = e.player.getUniqueID().toString();
        reg.markDirty();
        e.player.addChatComponentMessage(new ChatComponentTranslation("sc.contract.anchor.noted",
                String.valueOf(ReputationSC.ANCHOR_CHIPS), String.valueOf(ReputationSC.ANCHOR))
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_AQUA)));
    }

    /**
     * The one-time contract is for the world's first anchor in the Substrate: one already standing there (placed before
     * the contract existed - a world of 0.2.1 -, BridgeMarksSC's ANCHOR mark) closes it for everyone - anchorBy
     * ANCHOR_TAKEN (TradeServerSC.ANCHOR_OTHER), nobody paid. (x, y, z): the anchor being placed now, not counted
     * (Integer.MIN_VALUE: none). Server. @return closed by this call
     */
    public static boolean anchorTaken(World w, SettlementRegistrySC reg, int x, int y, int z) {
        if (reg == null || reg.anchorBy != null && !reg.anchorBy.isEmpty()) {
            return false;
        }
        BridgeMarksSC marks = BridgeMarksSC.get(w);
        if (marks == null || !marks.anyOther(BridgeMarksSC.ANCHOR, SubstrateSC.dimId(), x, y, z)) {
            return false;
        }
        reg.anchorBy = ANCHOR_TAKEN;
        reg.markDirty();
        return true;
    }

    /** The registry name of a mob without the mod's prefix ("SiliconAge.marauder" -> "marauder"); "" unknown. */
    public static String mobName(EntityLivingBase mob) {
        String s = EntityList.getEntityString(mob);
        if (s == null) {
            return "";
        }
        int dot = s.lastIndexOf('.');
        return dot < 0 ? s : s.substring(dot + 1);
    }

    /** One line for a contract's step: the count, or - the last one - where to hand it in. */
    private static void say(EntityPlayer p, ContractTableSC.Contract k, SettlementRegistrySC.ContractRec r) {
        ChatComponentTranslation title = new ChatComponentTranslation(k.langKey());
        if (r.progress >= r.target) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.contract.ready", title)
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
        } else {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.contract.progress", title, String.valueOf(r.progress), String.valueOf(r.target))
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        }
    }
}
