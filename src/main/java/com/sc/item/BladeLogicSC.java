package com.sc.item;

import java.util.ArrayList;
import java.util.List;

import com.sc.util.ArmorSuit;
import com.sc.util.BladeFeature;
import com.sc.util.BladeType;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.INpc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;

/**
 * What the energy blades' functions (BladeFeature) do, all on the server. Set bonuses (the blade
 * with the full suit of its own tier):
 * - Nano: hits and functions cost a quarter less,
 * - Quantum: a hostile mob killed by a normal hit gives the chestplate BladeFeature.KILL_REFUND EU (less than the hit),
 * - Exo: what the blade lacks is taken from the chestplate - it never goes dark for want of charge.
 * The blade's heat is its own (NBT of the blade); while the player blocks, the once-a-second upkeep
 * and the block's own costs (energy block, deflect) are put aside and settled after - changing the
 * blade's NBT mid-block makes the client drop the block.
 */
public final class BladeLogicSC {

    private static final String DEBT_EU = "scBladeDebtEU", DEBT_HEAT = "scBladeDebtHeat", DEBT_SLOT = "scBladeDebtSlot",
            LAST_ACTION = "scBladeAction";

    private BladeLogicSC() {
    }

    /** The energy blade in the player's hand, or null. */
    public static ItemStack held(EntityPlayer p) {
        ItemStack s = p.getCurrentEquippedItem();
        return s != null && s.getItem() instanceof ItemBladeSC ? s : null;
    }

    /** In hand, has the function, it's switched on, and (for all but the blade switch itself) the blade is lit. */
    public static boolean active(EntityPlayer p, BladeFeature f) {
        ItemStack s = held(p);
        return s != null && ItemBladeSC.isEnabled(s, f) && ItemBladeSC.isLit(s);
    }

    private static boolean fullSetOf(EntityPlayer p, BladeType type) {
        return ArmorLogicSC.fullSet(p) == type.suit;
    }

    /** EU the blade's hits and functions really cost: a quarter less with the full Nano suit. */
    private static int cost(EntityPlayer p, ItemStack blade, int eu) {
        BladeType t = ItemBladeSC.typeOf(blade);
        return t == BladeType.NANO && fullSetOf(p, t) ? (int) Math.ceil(eu * 0.75) : eu;
    }

    /** The chestplate that feeds an Exo blade (the full Exo suit), or null. */
    private static ItemStack feeder(EntityPlayer p, ItemStack blade) {
        return ItemBladeSC.typeOf(blade) == BladeType.EXO && fullSetOf(p, BladeType.EXO) ? ArmorLogicSC.piece(p, 1) : null;
    }

    public static boolean canPay(EntityPlayer p, ItemStack blade, int eu) {
        int need = cost(p, blade, eu);
        int have = ItemBladeSC.chargeOf(blade);
        ItemStack chest = feeder(p, blade);
        return have >= need || (chest != null && have + ItemArmorSC.chargeOf(chest) >= need);
    }

    /** Spends the EU (all or nothing): the blade's own charge, then - Exo set - the chestplate's. */
    public static boolean pay(EntityPlayer p, ItemStack blade, int eu) {
        if (eu <= 0) {
            return true;
        }
        if (!canPay(p, blade, eu)) {
            return false;
        }
        int need = cost(p, blade, eu);
        need -= ItemBladeSC.discharge(blade, need);
        if (need > 0) {
            ItemArmorSC.discharge(feeder(p, blade), need);
        }
        return true;
    }

    // ------------------------------------------------------------------ switches (from the client)

    public static void toggle(EntityPlayer p, ItemStack blade, BladeFeature f, boolean on) {
        BladeType t = ItemBladeSC.typeOf(blade);
        if (t == null || !f.availableIn(t)) {
            return;
        }
        if (f == BladeFeature.BLADE && on && !canPay(p, blade, t.euPerHit)) {
            ItemBladeSC.setEnabled(blade, f, false);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.empty"));
        } else {
            ItemBladeSC.setEnabled(blade, f, on);
            if (f == BladeFeature.BLADE) {
                p.worldObj.playSoundAtEntity(p, on ? "mob.blaze.hit" : "random.fizz", 0.4F, on ? 1.8F : 1.4F);
            }
        }
        p.inventoryContainer.detectAndSendChanges();
        resendHeld(p);
    }

    /** The held slot sent again as it is: the client may have guessed a switch the server refused. */
    private static void resendHeld(EntityPlayer p) {
        if (p instanceof net.minecraft.entity.player.EntityPlayerMP) {
            ((net.minecraft.entity.player.EntityPlayerMP) p).playerNetServerHandler.sendPacket(
                    new net.minecraft.network.play.server.S2FPacketSetSlot(0, 36 + p.inventory.currentItem, p.inventory.getCurrentItem()));
        }
    }

    // ------------------------------------------------------------------ hitting

    /** A left-click with the lit blade: its own damage, paid per hit that lands (not while the target is still invulnerable). */
    public static void attack(EntityPlayer p, ItemStack blade, Entity target) {
        BladeType t = ItemBladeSC.typeOf(blade);
        if (!canPay(p, blade, t.euPerHit)) {
            ItemBladeSC.setEnabled(blade, BladeFeature.BLADE, false);     // empty: back to a hilt
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.empty"));
            target.attackEntityFrom(DamageSource.causePlayerDamage(p), t.offDamage);
            return;
        }
        if (hit(p, blade, target, t.onDamage)) {
            pay(p, blade, t.euPerHit);
            ItemBladeSC.addHeat(blade, BladeFeature.HIT_HEAT);
            p.addExhaustion(0.3F);
            // Quantum set: a hostile mob killed by a normal hit gives back less than the hit cost - no energy farm
            if (t == BladeType.QUANTUM && fullSetOf(p, t) && target instanceof net.minecraft.entity.monster.IMob && !target.isEntityAlive()) {
                ItemArmorSC.charge(ArmorLogicSC.piece(p, 1), BladeFeature.KILL_REFUND);
            }
        }
    }

    /**
     * One blow of the blade (a hit, the sweep, the wave, the lunge): armour pierce (Quantum), the
     * Exo blade's absolute damage, execute. @return whether it landed
     */
    private static boolean hit(EntityPlayer p, ItemStack blade, Entity target, float damage) {
        BladeType t = ItemBladeSC.typeOf(blade);
        DamageSource src = DamageSource.causePlayerDamage(p);
        if (t == BladeType.EXO) {
            src.setDamageBypassesArmor().setDamageIsAbsolute();
        } else if (ItemBladeSC.isEnabled(blade, BladeFeature.ARMOR_PIERCE)) {
            src.setDamageBypassesArmor();
        }
        EntityLivingBase living = target instanceof EntityLivingBase ? (EntityLivingBase) target : null;
        float bonus = living == null ? 0 : EnchantmentHelper.getEnchantmentModifierLiving(p, living);
        if (!target.attackEntityFrom(src, damage + bonus)) {
            return false;
        }
        if (living == null) {
            return true;
        }
        living.addVelocity(-MathHelper.sin(p.rotationYaw * (float) Math.PI / 180F) * 0.4, 0.1,
                MathHelper.cos(p.rotationYaw * (float) Math.PI / 180F) * 0.4);
        p.setLastAttacker(living);
        if (living.isEntityAlive() && ItemBladeSC.isEnabled(blade, BladeFeature.EXECUTE)
                && !(living instanceof net.minecraft.entity.boss.IBossDisplayData)       // no finishing off the dragon / wither
                && (com.sc.util.ConfigSC.bladeExecutePlayers || !(living instanceof EntityPlayer))   // players: only if the config allows
                && living.getHealth() <= living.getMaxHealth() * BladeFeature.EXECUTE_SHARE) {
            DamageSource finish = DamageSource.causePlayerDamage(p).setDamageBypassesArmor().setDamageIsAbsolute();
            living.hurtResistantTime = 0;
            living.attackEntityFrom(finish, living.getHealth() + 1000);    // the player gets the kill; a refused blow stays refused
        }
        return true;
    }

    /** Who the blade's area functions hit: living things but the player, their pets and villagers. */
    private static boolean fair(EntityPlayer p, Entity e) {
        if (!(e instanceof EntityLivingBase) || e == p || !e.isEntityAlive() || e instanceof INpc) {
            return false;
        }
        return !(e instanceof IEntityOwnable && ((IEntityOwnable) e).getOwner() == p);
    }

    /** A key function: in hand, switched on, lit, not used in the last half second, paid for. */
    private static ItemStack useAction(EntityPlayer p, BladeFeature f, int eu) {
        ItemStack blade = held(p);
        if (blade == null || !active(p, f)) {
            return null;
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - data.getLong(LAST_ACTION) < BladeFeature.ACTION_COOLDOWN) {
            return null;
        }
        if (!pay(p, blade, eu)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.low", eu));
            return null;
        }
        data.setLong(LAST_ACTION, now);
        ItemBladeSC.addHeat(blade, f.heat);
        return blade;
    }

    /** Quantum+: everything in an arc of 3 blocks in front takes a full blow. */
    public static void sweep(EntityPlayer p) {
        ItemStack blade = useAction(p, BladeFeature.SWEEP, BladeFeature.SWEEP_COST);
        if (blade == null) {
            return;
        }
        double r = BladeFeature.SWEEP_RANGE;
        Vec3 look = p.getLookVec();
        double lx = look.xCoord, lz = look.zCoord, len = Math.sqrt(lx * lx + lz * lz);
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, 1.5, r))) {
            Entity e = (Entity) o;
            double dx = e.posX - p.posX, dz = e.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
            if (!fair(p, e) || d > r + e.width / 2 || (d > 0.5 && len > 0.01 && (dx * lx + dz * lz) / (d * len) < 0.3)) {
                continue;
            }
            e.hurtResistantTime = 0;
            hit(p, blade, e, ItemBladeSC.typeOf(blade).onDamage);
        }
        p.worldObj.playSoundAtEntity(p, "mob.irongolem.throw", 1F, 1.4F);
        Vec3 front = eyes(p).addVector(lx * 1.5, -0.4, lz * 1.5);
        particles(p, front, front, "crit", 20);
    }

    /** Exo: a cut flying 16 blocks along the look, through every living thing on the way, stopped by walls. */
    public static void wave(EntityPlayer p) {
        ItemStack blade = useAction(p, BladeFeature.WAVE, BladeFeature.WAVE_COST);
        if (blade == null) {
            return;
        }
        Vec3 start = eyes(p);
        Vec3 end = beamEnd(p, start, BladeFeature.WAVE_RANGE);
        for (Entity e : along(p, start, end)) {
            e.hurtResistantTime = 0;
            hit(p, blade, e, ItemBladeSC.typeOf(blade).onDamage);
        }
        p.worldObj.playSoundAtEntity(p, "mob.ghast.fireball", 0.8F, 1.8F);
        particles(p, start, end, "witchMagic", 3);
    }

    /** Exo: a leap 6 blocks forward, every living thing on that line takes a full blow. */
    public static void lunge(EntityPlayer p) {
        ItemStack blade = useAction(p, BladeFeature.LUNGE, BladeFeature.LUNGE_COST);
        if (blade == null) {
            return;
        }
        Vec3 look = p.getLookVec();
        double len = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        Vec3 start = eyes(p);
        Vec3 end = beamEnd(p, start, BladeFeature.LUNGE_RANGE);
        for (Entity e : along(p, start, end)) {
            e.hurtResistantTime = 0;
            hit(p, blade, e, ItemBladeSC.typeOf(blade).onDamage);
        }
        if (len > 0.01) {
            p.motionX = look.xCoord / len * 1.2;
            p.motionZ = look.zCoord / len * 1.2;
            p.motionY = Math.max(p.motionY, 0.3);
            p.fallDistance = 0;
            p.velocityChanged = true;
        }
        p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.6F, 1.6F);
        particles(p, start, end, "magicCrit", 2);
    }

    private static Vec3 eyes(EntityPlayer p) {
        return Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
    }

    private static Vec3 beamEnd(EntityPlayer p, Vec3 start, double range) {
        Vec3 look = p.getLookVec();
        Vec3 end = start.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition wall = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        return wall != null && wall.hitVec != null ? wall.hitVec : end;
    }

    /** Living things the segment start-end passes through (with half a block of slack). */
    private static List<Entity> along(EntityPlayer p, Vec3 start, Vec3 end) {
        List<Entity> hits = new ArrayList<Entity>();
        net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB.getBoundingBox(
                Math.min(start.xCoord, end.xCoord), Math.min(start.yCoord, end.yCoord), Math.min(start.zCoord, end.zCoord),
                Math.max(start.xCoord, end.xCoord), Math.max(start.yCoord, end.yCoord), Math.max(start.zCoord, end.zCoord)).expand(1, 1, 1);
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, area)) {
            Entity e = (Entity) o;
            if (!fair(p, e)) {
                continue;
            }
            net.minecraft.util.AxisAlignedBB box = e.boundingBox.expand(0.5, 0.5, 0.5);
            if (box.isVecInside(start) || box.calculateIntercept(start, end) != null) {
                hits.add(e);
            }
        }
        return hits;
    }

    private static void particles(EntityPlayer p, Vec3 from, Vec3 to, String name, int perBlock) {
        if (!(p.worldObj instanceof WorldServer)) {
            return;
        }
        double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
        int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 2));
        for (int i = 0; i <= steps; i++) {
            double k = (double) i / steps;
            ((WorldServer) p.worldObj).func_147487_a(name, from.xCoord + dx * k, from.yCoord + dy * k - 0.2, from.zCoord + dz * k,
                    Math.max(1, perBlock / 2), 0.15, 0.15, 0.15, 0.05);
        }
    }

    // ------------------------------------------------------------------ blocking

    /** Blocking with the lit blade: energy block halves the hit once more (vanilla's own half comes after), paid per point. */
    public static float onHurt(EntityPlayer p, DamageSource source, float amount) {
        ItemStack blade = held(p);
        if (blade == null || !p.isBlocking() || source.isUnblockable() || amount <= 0 || !active(p, BladeFeature.ENERGY_BLOCK)) {
            return amount;
        }
        float saved = amount / 2;
        if (!owe(p, blade, (int) Math.ceil(saved) * BladeFeature.BLOCK_EU_PER_POINT, BladeFeature.ENERGY_BLOCK.heat)) {
            return amount;
        }
        p.worldObj.playSoundAtEntity(p, "random.fizz", 0.4F, 2F);
        return amount - saved;
    }

    /** Every tick, server: the upkeep put aside during a block; every other tick, blocking with deflect turns arrows and fireballs back. */
    public static void tick(EntityPlayer p) {
        if (p.worldObj.isRemote) {
            return;
        }
        settleDebt(p);
        if (p.ticksExisted % 2 != 0 || !p.isBlocking() || !active(p, BladeFeature.DEFLECT)) {
            return;
        }
        ItemStack blade = held(p);
        double r = BladeFeature.DEFLECT_RANGE;
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, r, r))) {
            Entity e = (Entity) o;
            if (!ArmorLogicSC.incomingProjectile(p, e)) {
                continue;
            }
            if (!owe(p, blade, BladeFeature.DEFLECT_COST, BladeFeature.DEFLECT.heat)) {
                return;
            }
            ArmorLogicSC.reflect(p, e);
        }
    }

    // ------------------------------------------------------------------ once a second

    /**
     * Cooling, and - lit and in hand - the upkeep: EU per second and a little heat. An empty blade
     * goes back to a hilt. While blocking it is only noted down (see the class comment).
     */
    public static void perSecond(EntityPlayer p, ItemStack blade, int slot, boolean held) {
        BladeType t = ItemBladeSC.typeOf(blade);
        int eu = 0, heat = -t.heatDissipation;
        if (held && ItemBladeSC.isLit(blade)) {
            eu = t.idlePerSecond;
            heat += BladeFeature.BLADE.heat;
        }
        NBTTagCompound data = p.getEntityData();
        if (held && p.isUsingItem() && p.getCurrentEquippedItem() == blade) {   // (getItemInUse is client-only)
            int debtEu = data.getInteger(DEBT_EU) + eu, debtHeat = data.getInteger(DEBT_HEAT) + heat;
            data.setInteger(DEBT_EU, debtEu);
            data.setInteger(DEBT_HEAT, debtHeat);
            data.setInteger(DEBT_SLOT, slot);
            if (ItemBladeSC.heatOf(blade) + debtHeat >= t.heatCapacity || !canPay(p, blade, debtEu)) {
                stopBlock(p);                       // it would overheat / run dry: the block ends, settled next tick
            }
            return;
        }
        settle(p, blade, eu, heat);
    }

    /**
     * A cost met during a block (energy block, deflect), put aside with the upkeep instead of paid at
     * once. @return false if the blade can't pay it on top of what it already owes
     */
    private static boolean owe(EntityPlayer p, ItemStack blade, int eu, int heat) {
        NBTTagCompound data = p.getEntityData();
        int debtEu = data.getInteger(DEBT_EU) + eu, debtHeat = data.getInteger(DEBT_HEAT) + heat;
        if (!canPay(p, blade, debtEu)) {
            return false;
        }
        data.setInteger(DEBT_EU, debtEu);
        data.setInteger(DEBT_HEAT, debtHeat);
        data.setInteger(DEBT_SLOT, p.inventory.currentItem);
        if (ItemBladeSC.heatOf(blade) + debtHeat >= ItemBladeSC.typeOf(blade).heatCapacity) {
            stopBlock(p);                           // one more and it overheats: the block ends, settled next tick
        }
        return true;
    }

    /** Ends the block on the server - and on the client, which only lets go when its held stack is replaced. */
    private static void stopBlock(EntityPlayer p) {
        p.stopUsingItem();
        resendHeld(p);
    }

    /** Pays the upkeep (what there is, if not all - then the blade goes dark) and heats / cools the blade. */
    private static void settle(EntityPlayer p, ItemStack blade, int eu, int heat) {
        if (eu > 0 && !pay(p, blade, eu)) {
            int need = cost(p, blade, eu);
            need -= ItemBladeSC.discharge(blade, need);
            ItemStack chest = feeder(p, blade);
            if (need > 0 && chest != null) {
                ItemArmorSC.discharge(chest, need);
            }
            ItemBladeSC.setEnabled(blade, BladeFeature.BLADE, false);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.empty"));
        }
        boolean was = ItemBladeSC.overheated(blade);
        ItemBladeSC.addHeat(blade, heat);
        if (!was && ItemBladeSC.overheated(blade)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.overheat"));
            p.worldObj.playSoundAtEntity(p, "random.fizz", 0.8F, 0.6F);
        }
    }

    /**
     * The upkeep put aside during a block, settled on the first tick after it - on the blade in that
     * slot, or (moved / dropped and picked up again) on any blade in the inventory. With no blade at
     * all the debt waits for one - throwing the blade away doesn't wipe it.
     */
    private static void settleDebt(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(DEBT_SLOT) || p.isUsingItem()) {
            return;
        }
        int slot = data.getInteger(DEBT_SLOT);
        ItemStack blade = slot >= 0 && slot < p.inventory.mainInventory.length ? p.inventory.mainInventory[slot] : null;
        if (blade == null || !(blade.getItem() instanceof ItemBladeSC)) {
            blade = null;
            for (ItemStack s : p.inventory.mainInventory) {
                if (s != null && s.getItem() instanceof ItemBladeSC) {
                    blade = s;
                    break;
                }
            }
        }
        if (blade == null) {
            return;                                   // kept until a blade turns up
        }
        int eu = data.getInteger(DEBT_EU), heat = data.getInteger(DEBT_HEAT);
        data.removeTag(DEBT_EU);
        data.removeTag(DEBT_HEAT);
        data.removeTag(DEBT_SLOT);
        settle(p, blade, eu, heat);
    }

    /** For the set-bonus line of the armour screen. */
    public static boolean setBonus(EntityPlayer p, ItemStack blade) {
        BladeType t = ItemBladeSC.typeOf(blade);
        return t != null && fullSetOf(p, t);
    }

    public static ArmorSuit suitOf(ItemStack blade) {
        BladeType t = ItemBladeSC.typeOf(blade);
        return t == null ? null : t.suit;
    }
}
