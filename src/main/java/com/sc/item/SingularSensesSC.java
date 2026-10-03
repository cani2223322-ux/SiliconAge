package com.sc.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sc.handler.ArmorNetSC;
import com.sc.init.ModBlocks;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;
import com.sc.util.SingularSenseData;
import com.sc.util.SingularSenseData.Analysis;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * The Singular helmet's senses and the set's resonance (stage 2b, docs/plan-singular-armor.md §3-§4),
 * server side: Ш1 the gravity scanner (a pulse every 5 s, the found blocks and mobs sent to the
 * client), Ш2 the threat sense (the mobs targeting the wearer, every second), Ш5 the analyzer (the
 * mob / machine looked at, every half second), К2 resonance (a running Singular reactor or a powered
 * field generator nearby charges, cools and refills the suit). The client draws: client.SingularClientSC.
 */
public final class SingularSensesSC {

    /** Ш5 looks every this many ticks. */
    public static final int ANALYZE_EVERY = 10;

    private static final String SCAN_AT = "scScanAt", THREAT_SENT = "scThreatSent", ANALYZE_TARGET = "scAnalyzeTarget",
            RES_CHECK = "scResCheck", RES_NEAR = "scResNear", RES_D_FRAC = "scResDFrac";
    /** Player entity data: К2 works now (the client is told - SingularCooldowns.P_RES, the HUD). */
    public static final String RES_ON = "scResOn";
    /** Persisted counters for stage 3's tasks: seconds of resonance with a Singular reactor; spawners the scanner has found. */
    public static final String RESONANCE_REACTOR_SECONDS = "scResReactorSec", SPAWNERS_SCANNED = "scSpawnersScanned";
    private static final int SPAWNERS_KEPT = 64;

    private SingularSensesSC() {
    }

    // ================================================================== once a second

    /** Server, once a second (ArmorLogicSC.singularSecond): Ш1, Ш2, К2. @return heat */
    static int second(EntityPlayer p) {
        int heat = scannerSecond(p);
        threatSecond(p);
        resonanceSecond(p);
        return heat;
    }

    // ------------------------------------------------------------------ Ш1 gravity scanner

    private static int scannerSecond(EntityPlayer p) {
        ArmorFeature f = ArmorFeature.GRAV_SCANNER;
        if (!(p instanceof EntityPlayerMP) || !ArmorLogicSC.active(p, f) || !ArmorLogicSC.pay(p, f, f.euPerSecond)) {
            return 0;
        }
        float mul = SingularPowersSC.costMul(p);
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        ArmorGasSC.drainFraction(worn, Gas.KRYPTON, ArmorFeature.SING_KR_SCANNER_PER_MIN / 60F * mul);
        com.sc.bridge.BridgeFamiliarSC.visit(p, 1);                     // bridge С5: the scanned land round the wearer becomes familiar
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime(), at = data.getLong(SCAN_AT);
        if (!data.hasKey(SCAN_AT) || now - at >= ArmorFeature.SCANNER_EVERY || now < at) {
            if (ArmorGasSC.drainExact(worn, Gas.KRYPTON, (int) Math.ceil(ArmorFeature.SING_KR_PER_PULSE * mul))) {
                data.setLong(SCAN_AT, now);
                pulse((EntityPlayerMP) p);
            }
        }
        return f.heat;
    }

    private static java.util.Set<Block> ores;

    /** The ores the scanner shows: vanilla ones and this mod's. */
    public static int oreKind(Block b) {
        if (b == ModBlocks.oreSC) {
            return SingularSenseData.MOD_ORE;
        }
        if (ores == null) {
            java.util.Set<Block> s = new java.util.HashSet<Block>();
            Collections.addAll(s, Blocks.coal_ore, Blocks.iron_ore, Blocks.gold_ore, Blocks.diamond_ore, Blocks.emerald_ore,
                    Blocks.lapis_ore, Blocks.redstone_ore, Blocks.lit_redstone_ore, Blocks.quartz_ore);
            ores = s;
        }
        return ores.contains(b) ? SingularSenseData.ORE : -1;
    }

    /**
     * Chests (trapped too), mob spawners and ores within the cube of half-side `r` round (cx, cy, cz)
     * in loaded chunks: {x, y, z, kind, distance²} each, at most `limit` ores (the rarer chests and
     * spawners always).
     */
    static List<int[]> scanBlocks(World w, int cx, int cy, int cz, int r, int limit) {
        List<int[]> out = new ArrayList<int[]>();
        int y0 = Math.max(0, cy - r), y1 = Math.min(255, cy + r), ores = 0;
        for (int chX = (cx - r) >> 4; chX <= (cx + r) >> 4; chX++) {
            for (int chZ = (cz - r) >> 4; chZ <= (cz + r) >> 4; chZ++) {
                if (!w.getChunkProvider().chunkExists(chX, chZ)) {
                    continue;
                }
                Chunk c = w.getChunkFromChunkCoords(chX, chZ);
                for (Object o : c.chunkTileEntityMap.values()) {
                    TileEntity te = (TileEntity) o;
                    int kind = te instanceof TileEntityChest ? SingularSenseData.CHEST : te instanceof TileEntityMobSpawner ? SingularSenseData.SPAWNER : -1;
                    if (kind >= 0 && Math.abs(te.xCoord - cx) <= r && Math.abs(te.zCoord - cz) <= r && te.yCoord >= y0 && te.yCoord <= y1) {
                        out.add(new int[]{te.xCoord, te.yCoord, te.zCoord, kind, dist2(te.xCoord - cx, te.yCoord - cy, te.zCoord - cz)});
                    }
                }
                for (ExtendedBlockStorage sec : c.getBlockStorageArray()) {
                    if (sec == null || sec.isEmpty() || sec.getYLocation() + 15 < y0 || sec.getYLocation() > y1) {
                        continue;
                    }
                    int by = sec.getYLocation();
                    for (int ly = 0; ly < 16; ly++) {
                        int y = by + ly;
                        if (y < y0 || y > y1) {
                            continue;
                        }
                        for (int lz = 0; lz < 16; lz++) {
                            int z = (chZ << 4) + lz;
                            if (Math.abs(z - cz) > r) {
                                continue;
                            }
                            for (int lx = 0; lx < 16; lx++) {
                                int x = (chX << 4) + lx;
                                if (Math.abs(x - cx) > r) {
                                    continue;
                                }
                                int kind = oreKind(sec.getBlockByExtId(lx, ly, lz));
                                if (kind >= 0 && ores < limit) {
                                    out.add(new int[]{x, y, z, kind, dist2(x - cx, y - cy, z - cz)});
                                    ores++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static int dist2(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz;
    }

    /** Pure: the blocks the client gets - chests and spawners first, then the nearest; at most `max`. */
    public static List<int[]> capScan(List<int[]> found, int max) {
        List<int[]> sorted = new ArrayList<int[]>(found);
        Collections.sort(sorted, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                int ra = a[3] <= SingularSenseData.SPAWNER ? 0 : 1, rb = b[3] <= SingularSenseData.SPAWNER ? 0 : 1;
                return ra != rb ? ra - rb : a[4] < b[4] ? -1 : a[4] > b[4] ? 1 : 0;
            }
        });
        return sorted.size() > max ? new ArrayList<int[]>(sorted.subList(0, max)) : sorted;
    }

    /** One pulse: blocks and mobs within SCANNER_RADIUS, capped, sent to the wearer's client. */
    private static void pulse(EntityPlayerMP p) {
        int r = ArmorFeature.SCANNER_RADIUS;
        int cx = MathHelper.floor_double(p.posX), cy = MathHelper.floor_double(p.posY), cz = MathHelper.floor_double(p.posZ);
        List<int[]> blocks = capScan(scanBlocks(p.worldObj, cx, cy, cz, r, 4096), ArmorFeature.SCANNER_MAX_BLOCKS);
        com.sc.bridge.BridgeFarSC.noteFinds(p, blocks);                 // the bridge link's «Находки сканера» (chests first: capScan's order)
        int[] b = new int[blocks.size() * 4];
        for (int i = 0; i < blocks.size(); i++) {
            int[] o = blocks.get(i);
            System.arraycopy(o, 0, b, i * 4, 4);
            if (o[3] == SingularSenseData.SPAWNER) {
                noteSpawner(p, o);
            }
        }
        final EntityPlayer me = p;
        List<EntityLiving> mobs = new ArrayList<EntityLiving>();
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(r, r, r))) {
            EntityLiving e = (EntityLiving) o;
            if (e.isEntityAlive()) {
                mobs.add(e);
            }
        }
        Collections.sort(mobs, new Comparator<EntityLiving>() {
            @Override
            public int compare(EntityLiving a, EntityLiving c) {
                return Double.compare(a.getDistanceSqToEntity(me), c.getDistanceSqToEntity(me));
            }
        });
        int n = Math.min(mobs.size(), ArmorFeature.SCANNER_MAX_MOBS);
        int[] m = new int[n * 2];
        for (int i = 0; i < n; i++) {
            m[i * 2] = mobs.get(i).getEntityId();
            m[i * 2 + 1] = mobs.get(i) instanceof IMob ? 1 : 0;
        }
        ArmorNetSC.CHANNEL.sendTo(new ArmorNetSC.ScanMessage(b, m), p);
        p.worldObj.playSoundAtEntity(p, "note.hat", 0.3F, 0.6F);
    }

    /** Stage 3's task "scan 20 spawners": each spawner found once (up to SPAWNERS_KEPT kept). */
    private static void noteSpawner(EntityPlayer p, int[] o) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound seen = persisted.getCompoundTag(SPAWNERS_SCANNED);
        String key = p.dimension + ":" + o[0] + ":" + o[1] + ":" + o[2];
        if (seen.hasKey(key) || seen.func_150296_c().size() >= SPAWNERS_KEPT) {
            return;
        }
        seen.setBoolean(key, true);
        persisted.setTag(SPAWNERS_SCANNED, seen);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    // ------------------------------------------------------------------ Ш2 threat sense

    /** Whether this mob has the player as its target (the new AI's attack target or the old AI's entityToAttack). */
    static boolean targets(EntityLiving e, EntityPlayer p) {
        return e.getAttackTarget() == p || (e instanceof EntityCreature && ((EntityCreature) e).getEntityToAttack() == p);
    }

    private static void threatSecond(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP)) {
            return;
        }
        ArmorFeature f = ArmorFeature.THREAT_SENSE;
        NBTTagCompound data = p.getEntityData();
        if (!ArmorLogicSC.active(p, f) || !ArmorLogicSC.pay(p, f, f.euPerSecond)) {
            if (data.getBoolean(THREAT_SENT)) {
                data.removeTag(THREAT_SENT);
                ArmorNetSC.CHANNEL.sendTo(new ArmorNetSC.ThreatMessage(new int[0]), (EntityPlayerMP) p);
            }
            return;
        }
        ArmorGasSC.drainFraction(ArmorGasSC.wornSet(p), Gas.KRYPTON, ArmorFeature.SING_KR_THREAT_PER_MIN / 60F * SingularPowersSC.costMul(p));
        double r = ArmorFeature.THREAT_RANGE;
        List<int[]> found = new ArrayList<int[]>();
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(r, r, r))) {
            EntityLiving e = (EntityLiving) o;
            if (e.isEntityAlive() && targets(e, p)) {
                found.add(new int[]{e.getEntityId(), (int) Math.min(Integer.MAX_VALUE, e.getDistanceSqToEntity(p))});
            }
        }
        Collections.sort(found, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0;
            }
        });
        int n = Math.min(found.size(), ArmorFeature.THREAT_MAX);
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = found.get(i)[0];
        }
        ArmorNetSC.CHANNEL.sendTo(new ArmorNetSC.ThreatMessage(ids), (EntityPlayerMP) p);
        data.setBoolean(THREAT_SENT, true);
    }

    // ------------------------------------------------------------------ Ш5 analyzer

    /** Pure: the weakness flags of a mob from what's known about it. */
    public static int weaknessFlags(boolean waterHurts, boolean meltsInHeat, boolean undead, boolean arthropod, boolean fireImmune, boolean explodes) {
        return (waterHurts ? Analysis.WEAK_WATER : 0) | (meltsInHeat ? Analysis.WEAK_HEAT : 0) | (undead ? Analysis.UNDEAD : 0)
                | (arthropod ? Analysis.ARTHROPOD : 0) | (fireImmune ? Analysis.FIRE_IMMUNE : 0) | (explodes ? Analysis.EXPLODES : 0);
    }

    static int weaknessFlags(EntityLivingBase e) {
        return weaknessFlags(e instanceof EntityBlaze || e instanceof EntityEnderman, e instanceof EntitySnowman, e.isEntityUndead(),
                e.getCreatureAttribute() == EnumCreatureAttribute.ARTHROPOD, e.isImmuneToFire(), e instanceof EntityCreeper);
    }

    /** The mod's energy block looked at within `range` (not through other blocks), or null. */
    private static com.sc.energy.TileEntityEnergyBase lookMachine(EntityPlayer p, double range, double[] dist) {
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + 1.62, p.posZ);
        Vec3 look = p.getLookVec();
        Vec3 end = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition hit = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        TileEntity te = p.worldObj.getTileEntity(hit.blockX, hit.blockY, hit.blockZ);
        if (!(te instanceof com.sc.energy.TileEntityEnergyBase)) {
            return null;
        }
        dist[0] = hit.hitVec == null ? range : eye.distanceTo(hit.hitVec);
        return (com.sc.energy.TileEntityEnergyBase) te;
    }

    /**
     * Server, every ANALYZE_EVERY ticks: Ш5 finds the mob (within ANALYZE_MOB_RANGE) or the mod's machine
     * (within ANALYZE_BLOCK_RANGE) looked at - the nearer of the two - and sends its table; a new
     * target costs SING_KR_ANALYZE krypton and ANALYZE_EU.
     */
    static void analyzer(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) p;
        NBTTagCompound data = p.getEntityData();
        String last = data.getString(ANALYZE_TARGET);
        ArmorFeature f = ArmorFeature.ANALYZER;
        if (!ArmorLogicSC.active(p, f)) {
            forget(mp, data, last);
            return;
        }
        Entity mob = SingularPowersSC.lookEntity(p, ArmorFeature.ANALYZE_MOB_RANGE, true, false);
        double[] dist = {0};
        com.sc.energy.TileEntityEnergyBase te = lookMachine(p, ArmorFeature.ANALYZE_BLOCK_RANGE, dist);
        if (mob != null && te != null && p.getDistanceToEntity(mob) > dist[0]) {
            mob = null;                                         // the machine is in front of the mob
        }
        String key = mob != null ? "e" + mob.getEntityId() : te != null ? "b" + te.xCoord + "," + te.yCoord + "," + te.zCoord : "";
        if (key.isEmpty()) {
            forget(mp, data, last);
            return;
        }
        if (!key.equals(last)) {
            ItemStack[] worn = ArmorGasSC.wornSet(p);
            int kr = SingularPowersSC.scaled(p, ArmorFeature.SING_KR_ANALYZE);
            if (ArmorGasSC.amountOf(worn, Gas.KRYPTON) < kr || !ArmorLogicSC.pay(p, f, ArmorFeature.ANALYZE_EU)) {
                ArmorLogicSC.warnArgs(p, "sc.armor.analyzer.cant", 200);
                forget(mp, data, last);
                return;
            }
            ArmorGasSC.drainExact(worn, Gas.KRYPTON, kr);
            data.setString(ANALYZE_TARGET, key);
        }
        Analysis a = new Analysis();
        if (mob != null) {
            EntityLivingBase e = (EntityLivingBase) mob;
            a.kind = Analysis.MOB;
            a.entityId = e.getEntityId();
            a.health = e.getHealth();
            a.maxHealth = e.getMaxHealth();
            a.armor = e.getTotalArmorValue();
            IAttributeInstance atk = e.getEntityAttribute(SharedMonsterAttributes.attackDamage);
            a.attack = atk == null ? -1F : (float) atk.getAttributeValue();
            a.flags = weaknessFlags(e);
        } else {
            a.kind = Analysis.MACHINE;
            a.x = te.xCoord;
            a.y = te.yCoord;
            a.z = te.zCoord;
            a.stored = te.getEnergyStored();
            a.capacity = te.getMaxEnergyStored();
            a.powerOn = te.isPowerOn();
            if (te instanceof com.sc.tileentity.TileEntityMachineSC) {
                com.sc.tileentity.TileEntityMachineSC m = (com.sc.tileentity.TileEntityMachineSC) te;
                a.status = (byte) m.getStatus().ordinal();
                a.progress = (byte) (m.getCurrentRecipeTicks() > 0 ? Math.min(100, m.getProgressTicks() * 100 / m.getCurrentRecipeTicks()) : -1);
            }
            if (te instanceof com.sc.tileentity.TileEntityGeneratorSC) {
                a.output = ((com.sc.tileentity.TileEntityGeneratorSC) te).getLastOutput();
            }
        }
        ArmorNetSC.CHANNEL.sendTo(new ArmorNetSC.AnalyzeMessage(a), mp);
    }

    private static void forget(EntityPlayerMP p, NBTTagCompound data, String last) {
        if (!last.isEmpty()) {
            data.removeTag(ANALYZE_TARGET);
            ArmorNetSC.CHANNEL.sendTo(new ArmorNetSC.AnalyzeMessage(new Analysis()), p);
        }
    }

    // ------------------------------------------------------------------ К2 resonance

    /** What a tile entity gives the resonance: 2 a running Singular reactor, 1 a powered, switched-on field generator, 0 nothing. */
    public static int resonanceKind(TileEntity te) {
        if (te instanceof com.sc.tileentity.TileEntityGeneratorSC) {
            com.sc.tileentity.TileEntityGeneratorSC g = (com.sc.tileentity.TileEntityGeneratorSC) te;
            if (g.singular() && g.getSingular() != null && g.getSingular().getPhase() == com.sc.tileentity.SingularReactorSC.PHASE_RUN) {
                return 2;
            }
        }
        if (te instanceof com.sc.tileentity.TileEntityFieldGeneratorSC) {
            com.sc.tileentity.TileEntityFieldGeneratorSC f = (com.sc.tileentity.TileEntityFieldGeneratorSC) te;
            if (f.isPowerOn() && !f.isRedstoneOff() && f.getEnergyStored() > 0) {
                return 1;
            }
        }
        return 0;
    }

    /** The best resonance source within `r` of (x, y, z) in loaded chunks (resonanceKind). */
    public static int sourceNear(World w, int x, int y, int z, int r) {
        int best = 0;
        for (int chX = (x - r) >> 4; chX <= (x + r) >> 4; chX++) {
            for (int chZ = (z - r) >> 4; chZ <= (z + r) >> 4; chZ++) {
                if (!w.getChunkProvider().chunkExists(chX, chZ)) {
                    continue;
                }
                for (Object o : w.getChunkFromChunkCoords(chX, chZ).chunkTileEntityMap.values()) {
                    TileEntity te = (TileEntity) o;
                    if (dist2(te.xCoord - x, te.yCoord - y, te.zCoord - z) <= r * r) {
                        best = Math.max(best, resonanceKind(te));
                        if (best == 2) {
                            return best;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** К2 works for the wearer: all four Singular pieces (any charge - it charges them), switched on, open at the chestplate's level. */
    static boolean resonanceOn(EntityPlayer p) {
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        if (chest == null || !ItemArmorSC.isEnabled(chest, ArmorFeature.RESONANCE) || !SingularLevel.unlocked(p, ArmorFeature.RESONANCE, chest)) {
            return false;
        }
        for (int t = 0; t < 4; t++) {
            if (ArmorLogicSC.suitOf(ArmorLogicSC.piece(p, t)) != ArmorSuit.SINGULAR) {
                return false;
            }
        }
        return true;
    }

    /**
     * К2, once a second: next to a source (looked for every RESONANCE_RESCAN seconds, cached) the suit
     * gets RESONANCE_EU, RESONANCE_COOL heat off, RES_HE_PER_SECOND helium and RES_D_PER_SECOND
     * deuterium - drawn from nothing (the source isn't drained). Works overheated and in emergency
     * mode too: it's how a dry suit refills.
     */
    private static void resonanceSecond(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!resonanceOn(p)) {
            data.removeTag(RES_CHECK);
            if (data.getBoolean(RES_ON)) {
                com.sc.util.SingularCooldowns.sendState(p, com.sc.util.SingularCooldowns.P_RES, 0L);
            }
            data.removeTag(RES_ON);
            return;
        }
        long now = p.worldObj.getTotalWorldTime(), at = data.getLong(RES_CHECK);
        int kind;
        if (!data.hasKey(RES_CHECK) || now - at >= ArmorFeature.RESONANCE_RESCAN * 20L || now < at) {
            kind = sourceNear(p.worldObj, MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY), MathHelper.floor_double(p.posZ),
                    ArmorFeature.RESONANCE_RADIUS);
            data.setLong(RES_CHECK, now);
            data.setInteger(RES_NEAR, kind);
        } else {
            kind = data.getInteger(RES_NEAR);
        }
        boolean was = data.getBoolean(RES_ON);
        if ((kind > 0) != was) {
            data.setBoolean(RES_ON, kind > 0);
            com.sc.util.SingularCooldowns.sendState(p, com.sc.util.SingularCooldowns.P_RES, kind > 0 ? 1L : 0L);
            p.addChatComponentMessage(new ChatComponentTranslation(kind > 0 ? "sc.armor.resonance" : "sc.armor.resonance.lost"));
        }
        if (kind <= 0) {
            return;
        }
        ArmorLogicSC.chargeSuit(p, ArmorFeature.RESONANCE_EU);
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        NBTTagCompound tag = chest.getTagCompound();
        if (tag != null && tag.getInteger("HeatSC") > 0) {
            tag.setInteger("HeatSC", Math.max(0, tag.getInteger("HeatSC") - ArmorFeature.RESONANCE_COOL));
        }
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        ArmorGasSC.fillOf(worn, Gas.HELIUM, (int) ArmorFeature.RES_HE_PER_SECOND, false);
        float frac = data.getFloat(RES_D_FRAC) + ArmorFeature.RES_D_PER_SECOND;
        int whole = (int) frac;
        if (whole > 0) {
            ArmorGasSC.fillOf(worn, Gas.DEUTERIUM, whole, false);
        }
        data.setFloat(RES_D_FRAC, frac - whole);
        if (kind == 2) {                                       // stage 3's task "5 min of resonance with a Singular reactor"
            NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            persisted.setInteger(RESONANCE_REACTOR_SECONDS, persisted.getInteger(RESONANCE_REACTOR_SECONDS) + 1);
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
    }
}
