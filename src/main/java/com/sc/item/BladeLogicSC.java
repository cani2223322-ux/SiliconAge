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
 * The Singular blade counts as Exo for every Exo rule (with the full Singular suit as its set), and its
 * Exo-era functions (sweep, wave, lunge, energy block, deflect, cutting) cost ToolLevelSC.LEGACY_MUL of their EU.
 * The blade's heat is its own (NBT of the blade); while the player blocks, the once-a-second upkeep
 * and the block's own costs (energy block, deflect) are put aside and settled after - changing the
 * blade's NBT mid-block makes the client drop the block.
 * The Singular blade's own functions, forms and branches live in BladeSingularSC (its transient state per
 * player is kept there, never in the blade's NBT); with the full Singular suit the blade's heat goes into the
 * suit (heat()). Key functions and form changes come from the network thread and are queued for the server
 * thread (action, cycleForm).
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

    /** In hand, has the function, it's switched on, open at the blade's level, and the blade is lit. Client-safe. */
    public static boolean active(EntityPlayer p, BladeFeature f) {
        ItemStack s = held(p);
        return s != null && ItemBladeSC.isEnabled(s, f) && ItemBladeSC.isLit(s) && unlocked(p, s, f);
    }

    /**
     * The tool has the function and its level / branch opens it: the older blades - whatever their tier
     * has; the Singular blade - its own functions from their singLevel (creative: all). Client-safe (NBT only).
     */
    public static boolean unlocked(EntityPlayer p, ItemStack tool, BladeFeature f) {
        BladeType t = ItemBladeSC.typeOf(tool);
        if (t == null || f == null || !f.availableIn(t)) {
            return false;
        }
        if (t != BladeType.SINGULAR) {
            return true;
        }
        boolean creative = p != null && p.capabilities.isCreativeMode;
        return f.openAt(com.sc.util.ToolLevelSC.effectiveLevel(p, tool), com.sc.util.ToolLevelSC.branchOf(tool), creative);
    }

    // ------------------------------------------------------------------ keys and the form (from the network thread)

    /** Any key function of the blade (the Exo-era sweep / wave / lunge too): queued, run on the server thread. */
    public static void action(net.minecraft.entity.player.EntityPlayerMP p, BladeFeature f) {
        if (p != null && f != null && f.isAction()) {
            BladeSingularSC.queue(p, f);
        }
    }

    /** Shift + wheel with the Singular blade: the next open form `delta` steps on (queued, server thread). */
    public static void cycleForm(net.minecraft.entity.player.EntityPlayerMP p, int delta) {
        if (p != null && delta != 0) {
            BladeSingularSC.queue(p, Integer.valueOf(delta));
        }
    }

    /** Server thread: a queued key function. */
    static void runAction(EntityPlayer p, BladeFeature f) {
        if (p.isDead || p.getHealth() <= 0) {
            return;
        }
        ItemStack blade = held(p);
        if (blade == null || !f.availableIn(ItemBladeSC.typeOf(blade))) {
            return;
        }
        if (!unlocked(p, blade, f)) {
            ArmorLogicSC.warnArgs(p, "sc.armorkey.locked", 40, new ChatComponentTranslation("sc.bladefn." + f.key()),
                    String.valueOf(f.singLevel()));
            return;
        }
        switch (f) {
            case SWEEP:
                sweep(p);
                break;
            case WAVE:
                wave(p);
                break;
            case LUNGE:
                lunge(p);
                break;
            default:
                BladeSingularSC.action(p, blade, f);
                break;
        }
    }

    /** Server thread: a queued form change - BladeForm.cycle over the forms the blade's level and branch open. */
    static void runCycleForm(EntityPlayer p, int delta) {
        ItemStack blade = held(p);
        if (!com.sc.util.ToolLevelSC.isBlade(blade)) {
            return;
        }
        if (p.isUsingItem()) {
            // mid-block: refused - the client doesn't guess then; if it did just before raising the block, its copy is put
            // right once the block ends (a resend now would end its block only)
            BladeSingularSC.state(p).resendOnRelease = true;
            return;
        }
        if (!BladeSingularSC.wheelReady(p)) {
            resendHeld(p);                              // too fast: refused - the client's guess is put right
            return;
        }
        com.sc.util.BladeForm from = ItemBladeSC.formOf(blade);
        com.sc.util.BladeForm to = com.sc.util.BladeForm.cycle(from, delta, com.sc.util.ToolLevelSC.effectiveLevel(p, blade),
                com.sc.util.ToolLevelSC.branchOf(blade), p.capabilities.isCreativeMode);
        if (to == from) {
            return;                                     // only the sword open
        }
        ItemBladeSC.setForm(blade, to);
        p.inventoryContainer.detectAndSendChanges();
        resendHeld(p);
        formSwitchEffect(p, blade);
    }

    /**
     * The form has changed (server): a low warp whoosh at the player and a burst of the scheme's accent colour
     * round the blade in hand (reddust, its "velocity" the colour), seen by everyone near. Server only - the
     * client's own guess (ToolWheelSC) shows no effect, so it is not doubled.
     */
    static void formSwitchEffect(EntityPlayer p, ItemStack blade) {
        p.worldObj.playSoundAtEntity(p, "mob.endermen.portal", 0.45F, 1.6F);
        p.worldObj.playSoundAtEntity(p, "random.fizz", 0.2F, 1.9F);
        if (!(p.worldObj instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) p.worldObj;
        int acc = com.sc.util.ToolLevelSC.schemeOf(blade).accent;
        float r = Math.max(0.01F, ((acc >> 16) & 255) / 255F), g = ((acc >> 8) & 255) / 255F, b = (acc & 255) / 255F;
        double yaw = Math.toRadians(p.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);                // forward (horizontal)
        double rx = -fz, rz = fx;                                      // right
        double hx = p.posX + fx * 0.45 + rx * 0.35;                    // the right hand, a little ahead
        double hy = p.posY + p.getEyeHeight() - 0.55;
        double hz = p.posZ + fz * 0.45 + rz * 0.35;
        for (int i = 0; i < 20; i++) {                                 // a ring round the hand
            double a = Math.PI * 2 * i / 20;
            double rad = 0.35 + p.worldObj.rand.nextDouble() * 0.12;
            double side = Math.cos(a) * rad, up = Math.sin(a) * rad;
            ws.func_147487_a("reddust", hx + rx * side + fx * up * 0.3, hy + up, hz + rz * side + fz * up * 0.3, 0, r, g, b, 1.0);
        }
        for (int i = 0; i < 8; i++) {                                  // and up along the blade
            double k = 0.15 + i * 0.1;
            ws.func_147487_a("reddust", hx + fx * k * 0.6, hy + k, hz + fz * k * 0.6, 0, r, g, b, 1.0);
        }
        ws.func_147487_a("witchMagic", hx, hy + 0.3, hz, 6, 0.15, 0.25, 0.15, 0.02);
    }

    // ------------------------------------------------------------------ heat (the full Singular suit takes it)

    /** Singular blade with the full Singular suit working: the blade's heat goes into the suit (its helium cools it). */
    static boolean dumpsHeat(EntityPlayer p, ItemStack blade) {
        return com.sc.util.ToolLevelSC.isBlade(blade) && ArmorLogicSC.bonusSet(p) == ArmorSuit.SINGULAR;
    }

    /** Heat of a hit / a function: into the suit (see dumpsHeat), noted down mid-block, or onto the blade. */
    static void heat(EntityPlayer p, ItemStack blade, int h) {
        if (h <= 0 || blade == null) {
            return;
        }
        if (dumpsHeat(p, blade)) {
            ArmorLogicSC.addHeat(p, h);
        } else if (p.isUsingItem() && p.getCurrentEquippedItem() == blade) {
            owe(p, blade, 0, h);                        // not the NBT mid-block
        } else {
            ItemBladeSC.addHeat(blade, h);
        }
    }

    /** Destroyer, level 3: area blows (sweep, wave, lunge, the forms' attacks, chain) x1.25. */
    static float areaMul(EntityPlayer p, ItemStack blade) {
        return com.sc.util.ToolLevelSC.hasBranch(p, blade, com.sc.util.ToolLevelSC.BLADE_DESTROYER, com.sc.util.ToolLevelSC.BRANCH_LEVEL)
                ? BladeFeature.DESTROYER_AREA_MUL : 1F;
    }

    private static boolean fullSetOf(EntityPlayer p, BladeType type) {
        com.sc.util.ArmorSuit set = ArmorLogicSC.bonusSet(p);  // none in emergency mode (no helium)
        return set == type.suit || type.suit == com.sc.util.ArmorSuit.EXO && set == com.sc.util.ArmorSuit.SINGULAR;   // Singular keeps the Exo bonuses
    }

    /** EU the blade's hits and functions really cost: a quarter less with the full Nano suit. */
    private static int cost(EntityPlayer p, ItemStack blade, int eu) {
        BladeType t = ItemBladeSC.typeOf(blade);
        return t == BladeType.NANO && fullSetOf(p, t) ? (int) Math.ceil(eu * 0.75) : eu;
    }

    /** The chestplate that feeds an Exo (or Singular) blade with its full set, or null. */
    private static ItemStack feeder(EntityPlayer p, ItemStack blade) {
        BladeType t = ItemBladeSC.typeOf(blade);
        return t != null && t.exoClass() && fullSetOf(p, t) ? ArmorLogicSC.piece(p, 1) : null;
    }

    /** EU of an Exo-era function on this blade: ToolLevelSC.LEGACY_MUL of it on the Singular blade. */
    public static int legacy(ItemStack blade, int eu) {
        return com.sc.util.ToolLevelSC.legacyCost(blade, eu);
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
        if (on && !unlocked(p, blade, f)) {
            resendHeld(p);                              // a locked function can't be switched on
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
    static void resendHeld(EntityPlayer p) {
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
        // Singular: the form, cascade and the bonuses earned by blocking (BladeSingularSC)
        BladeSingularSC.Hit sing = t == BladeType.SINGULAR ? BladeSingularSC.beforeHit(p, blade, target) : null;
        float damage = sing == null ? t.onDamage : sing.damage(t.onDamage);
        if (hit(p, blade, target, damage)) {
            pay(p, blade, t.euPerHit);
            heat(p, blade, BladeFeature.HIT_HEAT);
            p.addExhaustion(0.3F);
            // Quantum set: a hostile mob killed by a normal hit gives back less than the hit cost - no energy farm
            if (t == BladeType.QUANTUM && fullSetOf(p, t) && target instanceof net.minecraft.entity.monster.IMob && !target.isEntityAlive()) {
                ItemArmorSC.charge(ArmorLogicSC.piece(p, 1), BladeFeature.KILL_REFUND);
            }
            if (sing != null) {
                BladeSingularSC.afterHit(p, blade, target, sing, damage);
            }
        }
    }

    /** An area blow on one target: refused ones (a private field, another mod's claim) skipped, the hit frames ignored. */
    static boolean areaHit(EntityPlayer p, ItemStack blade, Entity e, float damage) {
        if (!fair(p, e) || !claimed(p, e)) {
            return false;
        }
        e.hurtResistantTime = 0;
        return hit(p, blade, e, damage);
    }

    /**
     * One blow of the blade (a hit, the sweep, the wave, the lunge): armour pierce (Quantum), the
     * Exo blade's absolute damage, execute. @return whether it landed
     */
    static boolean hit(EntityPlayer p, ItemStack blade, Entity target, float damage) {
        BladeType t = ItemBladeSC.typeOf(blade);
        DamageSource src = DamageSource.causePlayerDamage(p);
        if (t.exoClass()) {
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
    static boolean fair(EntityPlayer p, Entity e) {
        if (!(e instanceof EntityLivingBase) || e == p || !e.isEntityAlive() || e instanceof INpc) {
            return false;
        }
        return !(e instanceof IEntityOwnable && ((IEntityOwnable) e).getOwner() == p);
    }

    /**
     * БР-2: an area blow is a player's attack like a left-click - Forge's AttackEntityEvent goes out
     * for each target, and one somebody cancels (a private field, another mod's claim) is skipped.
     */
    static boolean claimed(EntityPlayer p, Entity e) {
        return !net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.AttackEntityEvent(p, e));
    }

    /** No key function in the last ACTION_COOLDOWN ticks (one press, two messages; every blade key shares it). */
    static boolean actionGap(EntityPlayer p) {
        long now = p.worldObj.getTotalWorldTime(), at = p.getEntityData().getLong(LAST_ACTION);
        return now - at >= BladeFeature.ACTION_COOLDOWN || now < at;
    }

    static void markAction(EntityPlayer p) {
        p.getEntityData().setLong(LAST_ACTION, p.worldObj.getTotalWorldTime());
    }

    /** A key function: in hand, switched on, lit, not used in the last half second, paid for. */
    private static ItemStack useAction(EntityPlayer p, BladeFeature f, int eu) {
        return useAction(p, f, eu, f.heat);
    }

    private static ItemStack useAction(EntityPlayer p, BladeFeature f, int eu, int heat) {
        ItemStack blade = held(p);
        if (blade == null || !active(p, f) || !actionGap(p)) {
            return null;
        }
        if (!pay(p, blade, eu)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.low", eu));
            return null;
        }
        markAction(p);
        heat(p, blade, heat);
        return blade;
    }

    /** Quantum+: everything in an arc of 3 blocks in front takes a full blow. */
    public static void sweep(EntityPlayer p) {
        ItemStack blade = useAction(p, BladeFeature.SWEEP, legacy(held(p), BladeFeature.SWEEP_COST));
        if (blade == null) {
            return;
        }
        float damage = ItemBladeSC.typeOf(blade).onDamage * areaMul(p, blade);
        double r = BladeFeature.SWEEP_RANGE;
        Vec3 look = p.getLookVec();
        double lx = look.xCoord, lz = look.zCoord, len = Math.sqrt(lx * lx + lz * lz);
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, 1.5, r))) {
            Entity e = (Entity) o;
            double dx = e.posX - p.posX, dz = e.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
            if (!fair(p, e) || d > r + e.width / 2 || (d > 0.5 && len > 0.01 && (dx * lx + dz * lz) / (d * len) < 0.3)
                    || !claimed(p, e)) {
                continue;
            }
            e.hurtResistantTime = 0;
            hit(p, blade, e, damage);
        }
        p.worldObj.playSoundAtEntity(p, "mob.irongolem.throw", 1F, 1.4F);
        Vec3 front = eyes(p).addVector(lx * 1.5, -0.4, lz * 1.5);
        particles(p, front, front, "crit", 20);
    }

    /** Exo: a cut flying 16 blocks along the look, through every living thing on the way, stopped by walls. */
    public static void wave(EntityPlayer p) {
        waveAs(p, BladeFeature.WAVE);
    }

    /**
     * The wave fired by its own key, or (the Singular blade's sword form) by FORM_ATTACK - each key's own
     * switch. Singular: x the event horizon's stored projectiles (spent), x1.25 for the Destroyer.
     */
    static void waveAs(EntityPlayer p, BladeFeature key) {
        ItemStack blade = useAction(p, key, legacy(held(p), BladeFeature.WAVE_COST), BladeFeature.WAVE.heat);
        if (blade == null) {
            return;
        }
        float damage = ItemBladeSC.typeOf(blade).onDamage * areaMul(p, blade) * BladeSingularSC.takeHorizon(p);
        Vec3 start = eyes(p);
        Vec3 end = beamEnd(p, start, BladeFeature.WAVE_RANGE);
        for (Entity e : along(p, start, end)) {
            if (!claimed(p, e)) {
                continue;
            }
            e.hurtResistantTime = 0;
            hit(p, blade, e, damage);
        }
        p.worldObj.playSoundAtEntity(p, "mob.ghast.fireball", 0.8F, 1.8F);
        particles(p, start, end, "witchMagic", 3);
    }

    /** Exo: a leap 6 blocks forward, every living thing on that line takes a full blow. */
    public static void lunge(EntityPlayer p) {
        ItemStack blade = useAction(p, BladeFeature.LUNGE, legacy(held(p), BladeFeature.LUNGE_COST));
        if (blade == null) {
            return;
        }
        Vec3 look = p.getLookVec();
        double len = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        Vec3 start = eyes(p);
        Vec3 end = beamEnd(p, start, BladeFeature.LUNGE_RANGE);
        float damage = ItemBladeSC.typeOf(blade).onDamage * areaMul(p, blade);
        for (Entity e : along(p, start, end)) {
            if (!claimed(p, e)) {
                continue;
            }
            e.hurtResistantTime = 0;
            hit(p, blade, e, damage);
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

    static Vec3 eyes(EntityPlayer p) {
        return Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
    }

    static Vec3 beamEnd(EntityPlayer p, Vec3 start, double range) {
        Vec3 look = p.getLookVec();
        Vec3 end = start.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition wall = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        return wall != null && wall.hitVec != null ? wall.hitVec : end;
    }

    /** Living things the segment start-end passes through (with half a block of slack). */
    static List<Entity> along(EntityPlayer p, Vec3 start, Vec3 end) {
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

    static void particles(EntityPlayer p, Vec3 from, Vec3 to, String name, int perBlock) {
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
        if (blade == null || !p.isBlocking() || source.isUnblockable() || amount <= 0) {
            return amount;
        }
        float left = amount;
        if (active(p, BladeFeature.ENERGY_BLOCK)) {
            float saved = amount / 2;
            int eu = BladeSingularSC.blockFree(p, blade) ? 0 : legacy(blade, (int) Math.ceil(saved) * BladeFeature.BLOCK_EU_PER_POINT);
            if (owe(p, blade, eu, BladeFeature.ENERGY_BLOCK.heat)) {
                p.worldObj.playSoundAtEntity(p, "random.fizz", 0.4F, 2F);
                left = amount - saved;
            }
        }
        BladeSingularSC.blocked(p, blade, amount, left);   // riposte: what the block took
        return left;
    }

    /** Every tick, server: the upkeep put aside during a block; every other tick, blocking with deflect turns arrows and fireballs back. */
    public static void tick(EntityPlayer p) {
        if (p.worldObj.isRemote) {
            return;
        }
        settleDebt(p);
        BladeSingularSC.tick(p);                      // Singular: blocking, the spear / whip reach, once a second
        if (p.ticksExisted % 2 != 0 || !p.isBlocking()) {
            return;
        }
        ItemStack blade = held(p);
        // Singular: the event horizon swallows, the gravity shield turns back (free), else the paid deflect
        boolean absorb = BladeSingularSC.horizonWorks(p), repel = BladeSingularSC.shieldWorks(p);
        boolean deflect = active(p, BladeFeature.DEFLECT);
        if (blade == null || !absorb && !repel && !deflect) {
            return;
        }
        double r = BladeFeature.DEFLECT_RANGE;
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, r, r))) {
            Entity e = (Entity) o;
            if (!ArmorLogicSC.incomingProjectile(p, e)) {
                continue;
            }
            if (absorb) {
                BladeSingularSC.absorb(p, blade, e);
                continue;
            }
            if (repel) {
                ArmorLogicSC.reflect(p, e);
                continue;
            }
            int eu = BladeSingularSC.blockFree(p, blade) ? 0 : legacy(blade, BladeFeature.DEFLECT_COST);
            if (!owe(p, blade, eu, BladeFeature.DEFLECT.heat)) {
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
            if (dumpsHeat(p, blade)) {
                ArmorLogicSC.addHeat(p, BladeFeature.BLADE.heat);    // full Singular suit: the blade only cools
            } else {
                heat += BladeFeature.BLADE.heat;
            }
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
    static boolean owe(EntityPlayer p, ItemStack blade, int eu, int heat) {
        NBTTagCompound data = p.getEntityData();
        int debtEu = data.getInteger(DEBT_EU) + eu, debtHeat = data.getInteger(DEBT_HEAT);
        if (!canPay(p, blade, debtEu)) {
            return false;
        }
        if (heat > 0 && dumpsHeat(p, blade)) {
            ArmorLogicSC.addHeat(p, heat);              // full Singular suit: into the suit, nothing to settle
        } else {
            debtHeat += heat;
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
    static void stopBlock(EntityPlayer p) {
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
