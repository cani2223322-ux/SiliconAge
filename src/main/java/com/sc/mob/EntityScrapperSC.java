package com.sc.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAIScrapperCollectSC;
import com.sc.mob.ai.EntityAIScrapperErrandSC;
import com.sc.util.Material;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.9, rules ПС-39, ПТ-19): «Утилизатор» (Scrapper Crab) - natural armour 10; the
 * hopper on its back takes x2. A scavenger: up to 27 stacks into its bag. It comes to a dead player's things within 60 s
 * (ScrapperSC) and never despawns while it holds anything - the things have to be won back: killed, it drops all it swallowed
 * plus 2..6 crushed ores. Come at it from behind; a ПЭ-1 Point stuns it for 4 s. HP 60.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 20 in MobEntitiesSC), the
 * World constructor (EntityList), HP, IEmpReactiveSC. The rest - its AI, its numbers, its drops (IFactionLootSC) - is package N's.
 * <p>
 * The bag (ПС-39, as the Marauder's - the same NBT key scBag): EntityAIScrapperCollectSC walks up to what lies within
 * COLLECT_RADIUS and swallows it - never into its hands or armour; a dead player's things (ScrapperSC.DROP_NBT) it remembers the
 * owner of (NBT scOwners {u, t}) and tells them once (ScrapperSC.tell) when they come within ScrapperSC.TELL_RANGE. DataWatcher
 * DW_BAG: the stacks in the bag (the hopper's look, WAILA, the analyzer). Something in the bag, or an errand - it never
 * despawns. Killed by anyone or anything - the whole bag falls out (ПТ-19; MobDropsSC takes it with takeBag in LivingDropsEvent,
 * and what is still in it after the death - another mod cancelled the drops - falls out here); Peaceful drops it before the
 * removal. ПТ-1: 2..6 crushed ores on top (factionLoot).
 * <p>
 * An errand (ScrapperSC): its home the place of a death, the leash ScrapperSC.HOME_LEASH (vanilla's home area, NBT scHome
 * {x, y, z, r}); EntityAIScrapperErrandSC walks it there; once nothing tagged lies within the leash of its home (looked at every
 * ScrapperSC.CHECK_TICKS) the errand is over and it lives on as any other. It hunts nobody: the claws (PINCH) only answer a blow
 * (EntityAIHurtByTarget). The blows it takes: from behind (MobCombatSC.behind - the hopper) x BACK_MUL. A ПЭ-1 Point - STUN_TICKS
 * stunned (ACT_STUNNED: no move, no task; sparks); a Pulse or a Siphon do nothing of their own (the caller's Slowness).
 */
public class EntityScrapperSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC {

    public static final String NAME = "scrapper";
    public static final double HP = 60D;
    /** Natural armour (getTotalArmorValue); the hopper on its back takes x BACK_MUL. */
    public static final int NATURAL_ARMOUR = 10;
    public static final float BACK_MUL = 2F;
    /** ПБ-25: a Point stuns it this long. */
    public static final int STUN_TICKS = 80;
    /** Its claws, when it answers a blow (it hunts nobody). TODO(balance) */
    public static final int PINCH = 4;
    /** ПС-39: the bag (as many stacks as ScrapperSC.BAG_STACKS), the reach it picks things up in. */
    public static final int BAG_SLOTS = ScrapperSC.BAG_STACKS;
    public static final double COLLECT_RADIUS = 6D;
    public static final double SPEED = 0.25D, FOLLOW = 16D;
    /** ПТ-1: the crushed ores on top of the bag (ScrapperSC's numbers), drawn from these. */
    public static final int ORES_MIN = ScrapperSC.ORES_MIN, ORES_MAX = ScrapperSC.ORES_MAX;
    public static final Material[] ORES = {Material.COPPER, Material.TIN, Material.LEAD, Material.ZINC, Material.IRON, Material.TITANIUM};
    public static final int XP = 10;

    /** DataWatcher (§2.6): the stacks in its bag (byte). */
    public static final int DW_BAG = 20;
    public static final String BAG_NBT = EntityMarauderSC.BAG_NBT, NBT_HOME = "scHome", NBT_ERRAND = "scErrand", NBT_OWNERS = "scOwners";

    private final ItemStack[] bag = new ItemStack[BAG_SLOTS];
    /** The owners of the dead players' things in the bag not told yet: UUID string and the death's time. */
    private final List<String> owners = new ArrayList<String>();
    private final List<Long> ownersAt = new ArrayList<Long>();
    private int homeX, homeY, homeZ, homeR = -1;
    private boolean errand;
    private int stun;

    public EntityScrapperSC(World world) {
        super(world);
        setSize(1.3F, 1.1F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, 1.1D, false));
        tasks.addTask(3, new EntityAIScrapperCollectSC(this, 1.0D));
        tasks.addTask(4, new EntityAIScrapperErrandSC(this, 1.0D));
        tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 1.0D));
        tasks.addTask(6, new EntityAIWander(this, 0.6D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.5D);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(PINCH);   // what WAILA-like tools show
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_BAG, Byte.valueOf((byte) 0));
    }

    /** Natural armour 10 (plus anything worn - nothing). */
    @Override
    public int getTotalArmorValue() {
        return Math.min(20, super.getTotalArmorValue() + NATURAL_ARMOUR);
    }

    // ------------------------------------------------------------------ the bag (as the Marauder's)

    public boolean bagEmpty() {
        for (ItemStack s : bag) {
            if (s != null) {
                return false;
            }
        }
        return true;
    }

    public boolean bagFull() {
        for (ItemStack s : bag) {
            if (s == null || s.stackSize < limit(s)) {
                return false;
            }
        }
        return true;
    }

    /** Some of `stack` would go in. */
    public boolean fitsInBag(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        for (ItemStack s : bag) {
            if (s == null || (same(s, stack) && s.stackSize < limit(s))) {
                return true;
            }
        }
        return false;
    }

    /** Puts `stack` into the bag (topping up its likes first). @return what didn't fit, null if all did */
    public ItemStack putInBag(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        for (int i = 0; i < bag.length && stack.stackSize > 0; i++) {
            ItemStack s = bag[i];
            if (s != null && same(s, stack)) {
                int move = Math.min(stack.stackSize, limit(s) - s.stackSize);
                if (move > 0) {
                    s.stackSize += move;
                    stack.stackSize -= move;
                }
            }
        }
        for (int i = 0; i < bag.length && stack.stackSize > 0; i++) {
            if (bag[i] == null) {
                bag[i] = stack.copy();
                stack.stackSize = 0;
            }
        }
        syncBag();
        return stack.stackSize > 0 ? stack : null;
    }

    /** Empties the bag (MobDropsSC, ПТ-19). @return what was in it */
    public List<ItemStack> takeBag() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] != null && bag[i].stackSize > 0) {
                out.add(bag[i]);
            }
            bag[i] = null;
        }
        syncBag();
        return out;
    }

    /** Both sides: the stacks in the bag (DW_BAG). */
    public int bagStacks() {
        return dataWatcher.getWatchableObjectByte(DW_BAG);
    }

    private void syncBag() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        int n = 0;
        for (ItemStack s : bag) {
            if (s != null) {
                n++;
            }
        }
        if (bagStacks() != n) {
            dataWatcher.updateObject(DW_BAG, Byte.valueOf((byte) n));
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.isStackable() && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static int limit(ItemStack s) {
        return Math.min(64, s.getMaxStackSize());
    }

    /**
     * Server (EntityAIScrapperCollectSC): swallows what `item` holds (as much as fits); a dead player's thing - its owner is
     * remembered to be told. @return all of it went in
     */
    public boolean swallow(EntityItem item) {
        if (worldObj.isRemote || item == null || item.isDead) {
            return false;
        }
        ItemStack left = putInBag(item.getEntityItem().copy());
        NBTTagCompound data = item.getEntityData();
        if (data.hasKey(ScrapperSC.DROP_NBT)) {
            remember(data.getString(ScrapperSC.DROP_NBT), data.getLong(ScrapperSC.DROP_AT));
        }
        swingItem();
        playSound("random.eat", 0.5F, 0.6F);
        SoundsSC.play(worldObj, posX, posY + 0.5D, posZ, "machine.grind", 0.4F, 1.4F);
        if (left == null || left.stackSize <= 0) {
            item.setDead();
            return true;
        }
        item.setEntityItemStack(left);
        return false;
    }

    private void remember(String owner, long at) {
        if (owner == null || owner.isEmpty()) {
            return;
        }
        for (int i = 0; i < owners.size(); i++) {
            if (owners.get(i).equals(owner) && ownersAt.get(i).longValue() == at) {
                return;
            }
        }
        owners.add(owner);
        ownersAt.add(Long.valueOf(at));
    }

    /** Server, once a second: the owners not told yet who came within ScrapperSC.TELL_RANGE hear it (ScrapperSC.tell). */
    private void tellOwners() {
        for (int i = owners.size() - 1; i >= 0; i--) {
            UUID id = ScrapperSC.uuid(owners.get(i));
            if (id == null) {
                owners.remove(i);                               // a broken record: forgotten
                ownersAt.remove(i);
                continue;
            }
            EntityPlayer p = worldObj.func_152378_a(id);
            double r = ScrapperSC.TELL_RANGE;
            if (p != null && p.isEntityAlive() && getDistanceSqToEntity(p) <= r * r) {
                ScrapperSC.tell(p, ownersAt.get(i).longValue());
                owners.remove(i);
                ownersAt.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ the errand (ScrapperSC)

    /** Server (ScrapperSC): its home the place of a death at (x, y, z), the leash HOME_LEASH; it walks there. */
    public void errand(int x, int y, int z) {
        setHome(x, y, z, ScrapperSC.HOME_LEASH);
        errand = true;
    }

    /** Server: on an errand still (ScrapperSC ends it when nothing tagged is left near its home). */
    public boolean onErrand() {
        return errand;
    }

    /** Server (ScrapperSC): the errand is over - it lives on as any other (its home and leash stay). */
    public void endErrand() {
        errand = false;
    }

    public boolean hasOwnHome() {
        return homeR > 0;
    }

    public int homeX() {
        return homeX;
    }

    public int homeY() {
        return homeY;
    }

    public int homeZ() {
        return homeZ;
    }

    private void setHome(int x, int y, int z, int r) {
        homeX = x;
        homeY = y;
        homeZ = z;
        homeR = Math.max(1, r);
        setHomeArea(x, y, z, homeR);
    }

    // ------------------------------------------------------------------ the tick, despawning

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        if (stun > 0) {
            stun--;
            motionX = 0D;
            motionZ = 0D;
            if (stun % 10 == 0 && worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.9D, posZ, 3, 0.4D, 0.2D, 0.4D, 0.05D);
            }
            if (stun == 0 && mobAction() == ACT_STUNNED) {
                setMobAction(ACT_NONE);
            }
        }
        if (!owners.isEmpty() && (ticksExisted + getEntityId()) % 20 == 0) {
            tellOwners();
        }
    }

    /** Both sides: stunned by a Point (the server's count; a client reads ACT_STUNNED). */
    public boolean stunned() {
        return stun > 0 || worldObj.isRemote && mobAction() == ACT_STUNNED;
    }

    @Override
    protected void updateAITasks() {
        if (stun > 0) {
            return;
        }
        super.updateAITasks();
    }

    /** Something in the bag, or an errand: it stays (ПС-39). */
    @Override
    protected boolean canDespawn() {
        return !errand && bagEmpty() && super.canDespawn();
    }

    /** Peaceful makes EntityMob.onUpdate remove the mob without dying: the bag falls out first. */
    @Override
    public void onUpdate() {
        if (!worldObj.isRemote && worldObj.difficultySetting == EnumDifficulty.PEACEFUL && !bagEmpty()) {
            dropBag();
        }
        super.onUpdate();
    }

    /** ПТ-19: what is still in the bag once it died (MobDropsSC took it already, unless the drops were cancelled) falls out here. */
    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && dead && !bagEmpty()) {
            dropBag();
        }
    }

    private void dropBag() {
        for (ItemStack s : takeBag()) {
            entityDropItem(s, 0.5F);
        }
    }

    // ------------------------------------------------------------------ the blows it takes and gives

    /** The hopper on its back: a blow from behind x BACK_MUL (a projectile by where it flew from). */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        Entity from = src == null ? null : src.getSourceOfDamage();
        if (from != null && from != this && !src.isExplosion() && MobCombatSC.behind(this, from, renderYawOffset)) {
            amount *= BACK_MUL;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** The claws: PINCH as its own blow (MobCombatSC.melee - no attribute, no enchantment). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (stun > 0) {
            return false;
        }
        playSound("mob.irongolem.throw", 0.6F, 1.6F);
        return MobCombatSC.melee(this, target, PINCH);
    }

    /** ПБ-25: a Point stuns it STUN_TICKS; a Pulse, a Siphon - nothing of its own. */
    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode != EmpMode.POINT) {
            return false;
        }
        stun = STUN_TICKS;
        setMobAction(ACT_STUNNED);
        getNavigator().clearPathEntity();
        SoundsSC.play(worldObj, posX, posY, posZ, "power.off", 0.8F, 0.7F);
        if (shooter instanceof EntityLivingBase && shooter != this) {
            setRevengeTarget((EntityLivingBase) shooter);
        }
        return true;
    }

    /** ПТ-1: ORES_MIN..ORES_MAX crushed ores of a few kinds (the bag is MobDropsSC's, always). */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (ModItems.crushedOre == null) {
            return;
        }
        int n = ORES_MIN + rand.nextInt(ORES_MAX - ORES_MIN + 1);
        while (n > 0) {
            int k = 1 + rand.nextInt(Math.min(3, n));
            ItemStack s = ModItems.crushedOre.stackOf(ORES[rand.nextInt(ORES.length)]);
            if (s == null) {
                break;
            }
            s.stackSize = k;
            out.add(s);
            n -= k;
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                bag[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        tag.setTag(BAG_NBT, list);
        if (hasOwnHome()) {
            NBTTagCompound h = new NBTTagCompound();
            h.setInteger("x", homeX);
            h.setInteger("y", homeY);
            h.setInteger("z", homeZ);
            h.setInteger("r", homeR);
            tag.setTag(NBT_HOME, h);
        }
        tag.setBoolean(NBT_ERRAND, errand);
        NBTTagList ow = new NBTTagList();
        for (int i = 0; i < owners.size(); i++) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("u", owners.get(i));
            t.setLong("t", ownersAt.get(i).longValue());
            ow.appendTag(t);
        }
        tag.setTag(NBT_OWNERS, ow);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        for (int i = 0; i < bag.length; i++) {
            bag[i] = null;
        }
        NBTTagList list = tag.getTagList(BAG_NBT, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < bag.length) {
                bag[slot] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        syncBag();
        if (tag.hasKey(NBT_HOME)) {
            NBTTagCompound h = tag.getCompoundTag(NBT_HOME);
            setHome(h.getInteger("x"), h.getInteger("y"), h.getInteger("z"), h.getInteger("r"));
        }
        errand = tag.getBoolean(NBT_ERRAND);
        owners.clear();
        ownersAt.clear();
        NBTTagList ow = tag.getTagList(NBT_OWNERS, 10);
        for (int i = 0; i < ow.tagCount(); i++) {
            NBTTagCompound t = ow.getCompoundTagAt(i);
            remember(t.getString("u"), t.getLong("t"));
        }
    }

    // ------------------------------------------------------------------ sounds: a crab of scrap (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.spider.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.irongolem.death";
    }

    @Override
    protected float getSoundPitch() {
        return 1.3F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.spider.step", 0.4F, 0.8F);
    }
}
