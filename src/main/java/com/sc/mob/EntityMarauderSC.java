package com.sc.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.mob.ai.EntityAIBeamAttackSC;
import com.sc.mob.ai.EntityAICollectLootSC;
import com.sc.mob.ai.EntityAIRechargeAtTraceSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.3): «Мародёр» (Marauder) - a thin outcast of the Tuners in odd Nano
 * pieces painted dirty orange, a battery on insulating tape. Groups of 2..4. Nano 2..4 pieces with the orange light,
 * drained by 20..40 blockable damage (the reserve by what it wears, as the Lineman's). Nano Blade 50% (6), Ion Cutter
 * 30% (beam 4), else a club (4). With the reserve gone it runs to a Copper Trace. HP 24. Drops (rule ПТ-1): Nano
 * Scrap 40%; trophies Nano piece 2%, Nano Blade 1.5%, Ion Cutter 1.5%.
 * <p>
 * The bag (rule ПС-5): it picks up what lies around (EntityAICollectLootSC) into BAG_SLOTS slots of its own NBT
 * ("scBag") - never into its hands or armour (vanilla's loot pickup is off). The bag falls out whole when it dies, flag
 * or no flag (a player's things may be in it: MobDropsSC, first thing), and before Peaceful removes it; a Marauder with
 * something in the bag never despawns. A bridge copies it with its NBT, the bag goes along.
 * <p>
 * Fixed: the class name, NAME, the World constructor. The numbers below are what the handbook's bestiary prints.
 */
public class EntityMarauderSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "marauder";

    public static final double HP = 24D, SPEED = 0.28D, FOLLOW = 32D;
    /** The attackDamage attribute (what tools show: the club's 4); the blow itself is MobCombatSC's by what's in hand. */
    public static final int ATTACK = 4;
    /** Worn Nano pieces, the blockable damage that drains its armour (EntityLinemanSC), the light (ModelArmorGlowSC.COLORS: #FF9A30). */
    public static final int PIECES_MIN = 2, PIECES_MAX = 4, GLOW = 2;
    public static final float RESERVE_MIN = 20F, RESERVE_MAX = 40F;
    /** In hand: the Nano Blade, else the Ion Cutter, else a club. */
    public static final float BLADE_CHANCE = 0.5F, ION_CUTTER_CHANCE = 0.3F;
    /** Its blows (MobCombatSC's, here for the handbook's bestiary) and its group (SpawnRulesSC's). */
    public static final int BLADE_DAMAGE = MobCombatSC.BLADE[BladeType.NANO.ordinal()],
            BEAM_DAMAGE = MobCombatSC.beamDamage(WeaponType.ION_CUTTER), CLUB_DAMAGE = MobCombatSC.CLUB,
            GROUP_MIN = SpawnRulesSC.GROUP_MIN[MobEntitiesSC.ID_MARAUDER], GROUP_MAX = SpawnRulesSC.GROUP_MAX[MobEntitiesSC.ID_MARAUDER];
    public static final int BAG_SLOTS = 9;
    public static final String BAG_NBT = "scBag";
    /**
     * Its rare shouts to the players within SAY_RADIUS (sc.mob.marauder.say.<line>, as a chat line «<Мародёр> ...»):
     * a new player to fight, one in the mod's armour, its own armour «down», the bag full. A mob says something at most
     * once in SAY_COOLDOWN ticks, the whole server once in SAY_GAP, and only every other time.
     */
    public static final int SAY_TARGET = 0, SAY_ARMOUR = 1, SAY_DOWN = 2, SAY_BAG = 3;
    public static final int SAY_RADIUS = 16, SAY_COOLDOWN = 600, SAY_GAP = 100;
    public static final float SAY_CHANCE = 0.5F;

    /** World time of the server's last shout (any Marauder). */
    private static long lastShout = Long.MIN_VALUE / 2;

    private final ItemStack[] bag = new ItemStack[BAG_SLOTS];
    private long saidAt = Long.MIN_VALUE / 2;
    private EntityLivingBase lastTarget;
    private boolean wasDown;

    public EntityMarauderSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        setCanPickUpLoot(false);
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIRechargeAtTraceSC(this, 1.3D));      // «села» - runs for the trace
        tasks.addTask(2, new EntityAIBeamAttackSC(this, 1.0D));
        tasks.addTask(3, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(4, new EntityAICollectLootSC(this, 0.9D));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));      // the gang answers together
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(ATTACK);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПС-4: the blade 6, the club 4, the fist 3..4 (the cutter in hand) - never the held item's attributes. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return MobCombatSC.melee(this, target, MobCombatSC.meleeDamage(getHeldItem(), MobCombatSC.FIST, rand));
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.NANO, PIECES_MIN + rand.nextInt(PIECES_MAX - PIECES_MIN + 1), GLOW, rand);
            float r = rand.nextFloat();
            setCurrentItemOrArmor(0, r < BLADE_CHANCE ? MobCombatSC.litBlade(BladeType.NANO)
                    : r < BLADE_CHANCE + ION_CUTTER_CHANCE ? MobCombatSC.gunStack(WeaponType.ION_CUTTER) : new ItemStack(Items.stick));
            MobArmorSC a = mobArmor();
            a.helium = false;
            a.emergency = false;
            a.spawnReserveForDamage(RESERVE_MIN, RESERVE_MAX);
        }
        return data;
    }

    /** ПС-6: never - see EntityLinemanSC.dropEquipment (Looting would still drop real pieces at chance 0). */
    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {
    }

    /** ПТ-1 (MobDropsSC's numbers): Nano Armour Scrap, SCRAP_CHANCE; a Stolen Cargo, CARGO_CHANCE (Ф2, ПТ-10). */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < MobDropsSC.SCRAP_CHANCE) {
            ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1);
            if (scrap != null) {
                out.add(scrap);
            }
        }
        // Ф2, ПТ-10: a settlement's crate it robbed - an Elder takes it back
        if (rand.nextFloat() < MobDropsSC.CARGO_CHANCE) {
            ItemStack cargo = MobItemsSC.loot(ItemCombatLootSC.LootKind.STOLEN_CARGO, 1);
            if (cargo != null) {
                out.add(cargo);
            }
        }
    }

    // ------------------------------------------------------------------ the bag

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
        return stack.stackSize > 0 ? stack : null;
    }

    /** Empties the bag. @return what was in it */
    public List<ItemStack> takeBag() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] != null && bag[i].stackSize > 0) {
                out.add(bag[i]);
            }
            bag[i] = null;
        }
        return out;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.isStackable() && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static int limit(ItemStack s) {
        return Math.min(64, s.getMaxStackSize());
    }

    // ------------------------------------------------------------------ shouts

    /** Server, after the armour's tick (EntityArmoredMobSC): a new target, the armour going «down». */
    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        EntityLivingBase t = getAttackTarget();
        if (t != lastTarget) {
            lastTarget = t;
            if (t instanceof EntityPlayer) {
                say(wearsModArmour(t) ? SAY_ARMOUR : SAY_TARGET);
            }
        }
        boolean down = mobArmor().down() && mobArmor().reserveMax > 0F;
        if (down && !wasDown) {
            say(SAY_DOWN);
        }
        wasDown = down;
    }

    /** One of its lines (SAY_*) to the players near, if it may speak now. */
    public void say(int line) {
        long now = worldObj.getTotalWorldTime();
        if (worldObj.isRemote || now - saidAt < SAY_COOLDOWN || Math.abs(now - lastShout) < SAY_GAP || rand.nextFloat() >= SAY_CHANCE) {
            return;
        }
        saidAt = now;
        lastShout = now;
        IChatComponent msg = new ChatComponentTranslation("chat.type.text", com.sc.emp.EmpMobSC.name(this),   // the reader's language
                new ChatComponentTranslation("sc.mob.marauder.say." + line));
        msg.getChatStyle().setColor(EnumChatFormatting.GOLD);
        for (Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (getDistanceSqToEntity(p) <= SAY_RADIUS * SAY_RADIUS) {
                p.addChatComponentMessage(msg);
            }
        }
    }

    private static boolean wearsModArmour(EntityLivingBase e) {
        for (int t = 0; t < 4; t++) {
            if (MobArmorSC.modPiece(e, t) != null) {
                return true;
            }
        }
        return false;
    }

    /** Things in the bag: it stays (ПС-5). */
    @Override
    protected boolean canDespawn() {
        return bagEmpty() && super.canDespawn();
    }

    /** Peaceful makes EntityMob.onUpdate remove the mob without dying: the bag falls out first. */
    @Override
    public void onUpdate() {
        if (!worldObj.isRemote && worldObj.difficultySetting == EnumDifficulty.PEACEFUL && !bagEmpty()) {
            for (ItemStack s : takeBag()) {
                entityDropItem(s, 0.5F);
            }
        }
        super.onUpdate();
    }

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
    }

    // ------------------------------------------------------------------ sounds: an outcast of the Tuners (vanilla's, §5.9)

    @Override
    protected String getLivingSound() {
        return "mob.villager.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.villager.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.villager.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.8F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }
}
