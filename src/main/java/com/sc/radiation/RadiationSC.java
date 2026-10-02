package com.sc.radiation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ArmorLogicSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Radiation. RTGs and running reactors report themselves once a second (level, reach); a player's
 * level is the sum of what reaches them - weaker with distance, and through every block in the
 * way (lead almost stops it) - plus what they carry (isotope capsules, monazite ore). A field
 * with its radiation shield, the lead suit and the Quantum / Exo radiation shield take their share;
 * what's left builds up a dose (0-100%) that makes the player ill, and it falls again away from
 * radiation. Server side, once a second per player; the numbers go to the player's client
 * (RadiationNetSC) for the dosimeter and the warning on screen.
 */
public final class RadiationSC {

    /** Player data: the dose (0-100) and the radiation shield's heat below one whole unit. */
    public static final String DOSE = "scRadDose", HEAT_FRAC = "scRadHeat", NAUSEA = "scRadNausea", PROT = "scRadProt";
    /** Dose a second for each level that gets through; the fall a second while nothing gets through. */
    public static final float DOSE_PER_LEVEL = 1.0F, DOSE_DECAY = 0.1F;
    /**
     * Acute effects, from the radiation getting through right now (not the dose): nausea from this
     * level, slowness from this, weakness and hunger from this, harm and wither from this.
     * Entering radiation with nothing stopping it: a warning and nausea at once.
     */
    public static final float ACUTE_NAUSEA = 1F, ACUTE_SLOW = 3F, ACUTE_WEAK = 5F, ACUTE_HARM = 8F, ENTER_LEVEL = 0.3F;
    /**
     * Nausea only shows (the screen sways) while more than 3 seconds of it are left, and it builds
     * up over ~7 seconds - so it's given long and topped up, never in short bursts.
     */
    public static final int NAUSEA_TICKS = 200;
    public static final String LEFT = "scRadLeft", INSIDE = "scRadInside", WARNED = "scRadWarned";
    /** "Inside" ends only well below the entering level; the warning comes at most once in this many ticks. */
    public static final float LEAVE_LEVEL = 0.1F;
    public static final int WARN_COOLDOWN = 600;
    /** Dose steps: nausea, weakness and hunger, wither, harm. */
    public static final float STAGE_NAUSEA = 10F, STAGE_WEAK = 25F, STAGE_SLOW = 50F, STAGE_WITHER = 75F, STAGE_HARM = 100F;
    /** A field's radiation shield: EU a second for each level it stops round a player. */
    public static final int FIELD_EU_PER_LEVEL = 300;
    /** What one carried item gives: an isotope capsule; a full stack of monazite ore. */
    public static final float CAPSULE_LEVEL = 0.3F, MONAZITE_STACK_LEVEL = 0.25F;
    /** Share of the radiation one block lets through: lead, lead glass, water, any other solid block. */
    public static final float THROUGH_LEAD = 0.02F, THROUGH_LEAD_GLASS = 0.05F, THROUGH_WATER = 0.6F, THROUGH_SOLID = 0.7F;
    /** Nothing reaches past this (a stray sum of many sources is still bounded). */
    public static final float MAX_LEVEL = 20F;

    public static final DamageSource DAMAGE = new DamageSource("sc.radiation").setDamageBypassesArmor();

    private static final class Source {
        final int x, y, z;
        float level;
        int radius;
        long seen;
        /** A burst that walls don't stop (the Singular Reactor's flash): no blocks in the way count. */
        boolean pierce;

        Source(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** dimension -> packed position -> source; a source not reported for two seconds is gone. */
    private static final Map<Integer, Map<Long, Source>> SOURCES = new HashMap<Integer, Map<Long, Source>>();

    private RadiationSC() {
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** A source says it's radiating (once a second). */
    public static void report(World w, int x, int y, int z, float level, int radius) {
        report(w, x, y, z, level, radius, false);
    }

    /** A source whose radiation goes through any wall, lead too (the Singular Reactor's flash) - once a second. */
    public static void reportPiercing(World w, int x, int y, int z, float level, int radius) {
        report(w, x, y, z, level, radius, true);
    }

    private static void report(World w, int x, int y, int z, float level, int radius, boolean pierce) {
        if (w == null || w.isRemote || level <= 0 || radius <= 0) {
            return;
        }
        Map<Long, Source> map = SOURCES.get(w.provider.dimensionId);
        if (map == null) {
            map = new HashMap<Long, Source>();
            SOURCES.put(w.provider.dimensionId, map);
        }
        long k = key(x, y, z);
        Source s = map.get(k);
        if (s == null) {
            s = new Source(x, y, z);
            map.put(k, s);
        }
        s.level = level;
        s.radius = radius;
        s.pierce = pierce;
        s.seen = w.getTotalWorldTime();
    }

    /** What the sources send to a point (after distance and the blocks in the way). */
    public static float fromSources(World w, double px, double py, double pz) {
        Map<Long, Source> map = SOURCES.get(w.provider.dimensionId);
        if (map == null) {
            return 0F;
        }
        long now = w.getTotalWorldTime();
        float sum = 0F;
        for (Iterator<Source> it = map.values().iterator(); it.hasNext(); ) {
            Source s = it.next();
            if (now - s.seen > 40 || now < s.seen) {
                it.remove();
                continue;
            }
            double sx = s.x + 0.5, sy = s.y + 0.5, sz = s.z + 0.5;
            double d = Math.sqrt((px - sx) * (px - sx) + (py - sy) * (py - sy) + (pz - sz) * (pz - sz));
            if (d >= s.radius) {
                continue;
            }
            float base = (float) (s.level * (1.0 - d / s.radius));
            if (base > 0.01F) {
                sum += s.pierce ? base : base * through(w, sx, sy, sz, px, py, pz, s.x, s.y, s.z);
            }
        }
        return sum;
    }

    /** Level of one source at a distance, before any blocks (the handbook's and the screen's numbers). */
    public static float atDistance(float level, int radius, double d) {
        return d >= radius ? 0F : (float) (level * (1.0 - d / radius));
    }

    /**
     * Share of the radiation that gets from one point to another: every block the line passes
     * through takes its part (a voxel walk - a diagonal wall has no gaps). The source's own block
     * and the player's two blocks don't count.
     */
    public static float through(World w, double x0, double y0, double z0, double x1, double y1, double z1, int sx, int sy, int sz) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int x = MathHelper.floor_double(x0), y = MathHelper.floor_double(y0), z = MathHelper.floor_double(z0);
        int ex = MathHelper.floor_double(x1), ey = MathHelper.floor_double(y1), ez = MathHelper.floor_double(z1);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0, stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0, stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double inf = Double.POSITIVE_INFINITY;
        double tMaxX = stepX == 0 ? inf : (stepX > 0 ? x + 1 - x0 : x0 - x) / Math.abs(dx);
        double tMaxY = stepY == 0 ? inf : (stepY > 0 ? y + 1 - y0 : y0 - y) / Math.abs(dy);
        double tMaxZ = stepZ == 0 ? inf : (stepZ > 0 ? z + 1 - z0 : z0 - z) / Math.abs(dz);
        double dtX = stepX == 0 ? inf : 1.0 / Math.abs(dx), dtY = stepY == 0 ? inf : 1.0 / Math.abs(dy), dtZ = stepZ == 0 ? inf : 1.0 / Math.abs(dz);
        float f = 1F;
        for (int guard = 0; guard < 256; guard++) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > 1) {
                    break;
                }
                x += stepX;
                tMaxX += dtX;
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > 1) {
                    break;
                }
                y += stepY;
                tMaxY += dtY;
            } else {
                if (tMaxZ > 1) {
                    break;
                }
                z += stepZ;
                tMaxZ += dtZ;
            }
            if (x == ex && z == ez && (y == ey || y == ey - 1)) {
                break;                                      // the player's own blocks
            }
            f *= blockPasses(w.getBlock(x, y, z), w.getBlockMetadata(x, y, z));
            if (f < 0.001F) {
                return 0F;
            }
        }
        return f;
    }

    /** Share one block lets through. */
    public static float blockPasses(Block b) {
        return blockPasses(b, 0);
    }

    /** Blocks by id and meta: registered as "blockLead" (this mod's or another mod's block of lead). */
    private static final Map<Integer, Boolean> LEAD_BLOCKS = new HashMap<Integer, Boolean>();

    private static boolean isLeadBlock(Block b, int meta) {
        int key = (Block.getIdFromBlock(b) << 4) | (meta & 15);
        Boolean known = LEAD_BLOCKS.get(key);
        if (known == null) {
            known = false;
            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(b);
            if (item != null) {
                int lead = net.minecraftforge.oredict.OreDictionary.getOreID("blockLead");
                for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(new ItemStack(item, 1, meta))) {
                    known |= id == lead;
                }
            }
            LEAD_BLOCKS.put(key, known);
        }
        return known;
    }

    public static float blockPasses(Block b, int meta) {
        if (b == null || b.getMaterial() == net.minecraft.block.material.Material.air) {
            return 1F;
        }
        if (b == ModBlocks.leadBlock || isLeadBlock(b, meta)) {
            return THROUGH_LEAD;
        }
        if (b == ModBlocks.leadGlass) {
            return THROUGH_LEAD_GLASS;
        }
        if (b.getMaterial() == net.minecraft.block.material.Material.water) {
            return THROUGH_WATER;
        }
        return b.isOpaqueCube() ? THROUGH_SOLID : 1F;
    }

    /** What a player carries. */
    public static float carried(EntityPlayer p) {
        float sum = 0F;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null) {
                continue;
            }
            if (s.getItem() == ModItems.isotopeCapsule) {
                sum += CAPSULE_LEVEL * s.stackSize;
            } else if (isMonaziteOre(s)) {
                sum += MONAZITE_STACK_LEVEL * s.stackSize / 64F;
            }
        }
        return sum;
    }

    private static boolean isMonaziteOre(ItemStack s) {
        return s.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.oreSC)
                && s.getItemDamage() == com.sc.util.OreEntry.MONAZITE.meta();
    }

    /** The radiation level where a player is (sources and carried things, x the config's multiplier). */
    public static float levelAt(EntityPlayer p) {
        float level = fromSources(p.worldObj, p.posX, p.posY + 1.0, p.posZ) + carried(p);
        return Math.min(MAX_LEVEL, level * ConfigSC.radiationMultiplier);
    }

    public static float doseOf(EntityPlayer p) {
        return p.getEntityData().getFloat(DOSE);
    }

    /** The share of the radiation the protection took last second, %. */
    public static int lastProtection(EntityPlayer p) {
        return p.getEntityData().getInteger(PROT);
    }

    public static void setDose(EntityPlayer p, float dose) {
        p.getEntityData().setFloat(DOSE, Math.max(0F, Math.min(STAGE_HARM, dose)));
    }

    /** The lead suit's share (ConfigSC.leadSuitPartProtection a piece, 22% by default). */
    public static float leadShare(EntityPlayer p) {
        return LeadSuitSC.parts(p) * Math.max(0, Math.min(25, ConfigSC.leadSuitPartProtection)) / 100F;
    }

    /** The radiation shield's share for the worn chestplate and power mode (0 without it). */
    public static int armorSharePct(EntityPlayer p) {
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        if (chest == null) {
            return 0;
        }
        ArmorSuit suit = ArmorLogicSC.suitOf(chest);
        if (suit == null || suit.ordinal() < ArmorSuit.QUANTUM.ordinal()) {
            return 0;
        }
        int pct = suit == ArmorSuit.EXO ? ArmorFeature.RAD_EXO_PCT : ArmorFeature.RAD_QUANTUM_PCT;
        return ArmorLogicSC.powerMode(p) == 0 ? pct - ArmorFeature.RAD_ECO_PCT_LESS : pct;
    }

    /** Result flags sent to the client. */
    public static final int F_FIELD = 1, F_LEAD = 2, F_ARMOR = 4, F_ARMOR_FAIL = 8;

    /** Once a second, server side. */
    public static void perSecond(EntityPlayer p) {
        if (p.worldObj.isRemote) {
            return;
        }
        if (!ConfigSC.radiation) {
            if (doseOf(p) > 0) {
                setDose(p, 0F);
            }
            send(p, 0F, 0F, 0, 0);
            return;
        }
        float level = levelAt(p);
        float left = level;
        int flags = 0;
        if (left > 0.01F) {
            if (TileEntityFieldGeneratorSC.payRadiationAt(p.worldObj, p, p.posX, p.posY + 1.0, p.posZ, (int) Math.ceil(left * FIELD_EU_PER_LEVEL))) {
                left = 0F;
                flags |= F_FIELD;
            }
        }
        if (left > 0.01F && LeadSuitSC.parts(p) > 0) {
            left *= 1F - leadShare(p);
            flags |= F_LEAD;
        }
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        boolean shieldOn = chest != null && com.sc.item.ItemArmorSC.isEnabled(chest, ArmorFeature.RAD_SHIELD) && armorSharePct(p) > 0;
        if (left > 0.01F && shieldOn) {
            boolean exo = ArmorLogicSC.suitOf(chest) == ArmorSuit.EXO;
            float absorbed = left * armorSharePct(p) / 100F;
            int eu = (int) Math.ceil(absorbed * (exo ? ArmorFeature.RAD_EXO_EU : ArmorFeature.RAD_QUANTUM_EU));
            if (ArmorLogicSC.active(p, ArmorFeature.RAD_SHIELD) && ArmorLogicSC.pay(p, ArmorFeature.RAD_SHIELD, eu)) {
                left -= absorbed;
                flags |= F_ARMOR;
                NBTTagCompound data = p.getEntityData();
                float heat = data.getFloat(HEAT_FRAC) + absorbed * ArmorFeature.RAD_HEAT_PER_LEVEL;
                int whole = (int) heat;
                data.setFloat(HEAT_FRAC, heat - whole);
                if (whole > 0) {
                    ArmorLogicSC.addHeat(p, whole);
                }
            } else {
                flags |= F_ARMOR_FAIL;                      // switched on but overheated or out of charge
            }
        }
        float dose = doseOf(p);
        if (left > 0.01F && !p.capabilities.isCreativeMode) {
            dose += left * DOSE_PER_LEVEL;
        } else {
            dose -= DOSE_DECAY;
        }
        setDose(p, dose);
        if (!(p instanceof net.minecraftforge.common.util.FakePlayer) && !p.capabilities.isCreativeMode) {
            // (no connection: a potion effect on a fake player would crash; creative: no sickness)
            float harm = effects(p, doseOf(p)) + acute(p, left);
            if (harm > 0) {
                p.attackEntityFrom(DAMAGE, harm);                           // one blow: a second would fall in the hurt pause
            }
        }
        int prot = level <= 0.01F ? 0 : Math.round((1F - left / level) * 100F);
        p.getEntityData().setInteger(PROT, prot);
        send(p, level, doseOf(p), prot, flags);
    }

    private static void send(EntityPlayer p, float level, float dose, int prot, int flags) {
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null
                && !(p instanceof net.minecraftforge.common.util.FakePlayer)) {
            RadiationNetSC.send((EntityPlayerMP) p, level, dose, prot, flags);
        }
    }

    /** What the radiation getting through does at once, and the warning on the way in. */
    /** @return the harm it does this second */
    private static float acute(EntityPlayer p, float left) {
        NBTTagCompound data = p.getEntityData();
        data.setFloat(LEFT, left);
        boolean was = data.getBoolean(INSIDE);
        boolean inside = was ? left >= LEAVE_LEVEL : left >= ENTER_LEVEL;
        long now = p.worldObj.getTotalWorldTime();
        if (inside && !was && now - data.getLong(WARNED) > WARN_COOLDOWN) {
            data.setLong(WARNED, now);
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.chat.rad.enter", fmt(left)));
            p.addPotionEffect(new PotionEffect(Potion.confusion.id, NAUSEA_TICKS, 0, true));
        }
        data.setBoolean(INSIDE, inside);
        if (left >= ACUTE_NAUSEA) {
            p.addPotionEffect(new PotionEffect(Potion.confusion.id, NAUSEA_TICKS, 0, true));
        }
        if (left >= ACUTE_SLOW) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 45, left >= ACUTE_HARM ? 1 : 0, true));
        }
        if (left >= ACUTE_WEAK) {
            p.addPotionEffect(new PotionEffect(Potion.weakness.id, 45, 0, true));
            p.addPotionEffect(new PotionEffect(Potion.hunger.id, 45, 1, true));
        }
        if (left >= ACUTE_HARM) {
            p.addPotionEffect(new PotionEffect(Potion.wither.id, 45, 0, true));
            return 2.0F;
        }
        return 0F;
    }

    /** What the dose does: nausea now and then, then weakness and hunger, then wither, at the top harm every second. */
    /** @return the harm it does this second */
    private static float effects(EntityPlayer p, float dose) {
        if (dose >= STAGE_NAUSEA) {
            PotionEffect now = p.getActivePotionEffect(Potion.confusion);
            if (now == null || now.getDuration() < NAUSEA_TICKS - 60) {
                p.addPotionEffect(new PotionEffect(Potion.confusion.id, NAUSEA_TICKS, 0, true));
            }
        }
        if (dose >= STAGE_WEAK) {
            p.addPotionEffect(new PotionEffect(Potion.weakness.id, 45, 0, true));
            p.addPotionEffect(new PotionEffect(Potion.hunger.id, 45, 0, true));
        }
        if (dose >= STAGE_SLOW) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 45, 1, true));
            p.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 45, 1, true));
        }
        if (dose >= STAGE_WITHER) {
            p.addPotionEffect(new PotionEffect(Potion.wither.id, 45, 1, true));
        }
        return dose >= STAGE_HARM ? 2.0F : 0F;
    }

    /** Level the numbers stand for, one decimal ("4.5"). */
    public static String fmt(float level) {
        int tenths = Math.round(level * 10F);
        return (tenths / 10) + "." + (tenths % 10);
    }

    /** Server stop (and tests): forget every source. */
    public static void clearSources() {
        SOURCES.clear();
        LEAD_BLOCKS.clear();          // keyed by block id - the next world may number its blocks differently
    }

    /** A dimension unloads: its sources go. */
    public static void forgetDimension(int dim) {
        SOURCES.remove(dim);
    }

    /** The dose keeps up this effect (the suit's cleansing leaves it: radiation sickness isn't a poison). */
    public static boolean sicknessHolds(EntityPlayer p, int potionId) {
        float d = doseOf(p), now = p.getEntityData().getFloat(LEFT);
        return potionId == Potion.confusion.id && (d >= STAGE_NAUSEA || now >= ACUTE_NAUSEA)
                || (potionId == Potion.weakness.id || potionId == Potion.hunger.id) && (d >= STAGE_WEAK || now >= ACUTE_WEAK)
                || potionId == Potion.wither.id && (d >= STAGE_WITHER || now >= ACUTE_HARM);
    }

    /** A new player entity not from a death (leaving the End): the dose goes with it. */
    public static void copy(EntityPlayer from, EntityPlayer to) {
        NBTTagCompound a = from.getEntityData(), b = to.getEntityData();
        b.setFloat(DOSE, a.getFloat(DOSE));
        b.setFloat(HEAT_FRAC, a.getFloat(HEAT_FRAC));
        b.setInteger(NAUSEA, a.getInteger(NAUSEA));
    }

    /** Respawned after a death: the dose goes with the player, but no higher than the weakness step. */
    public static void copyAfterDeath(EntityPlayer from, EntityPlayer to) {
        setDose(to, Math.min(doseOf(from), STAGE_WEAK - 0.1F));
    }
}
