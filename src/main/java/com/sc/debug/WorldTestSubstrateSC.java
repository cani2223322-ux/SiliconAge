package com.sc.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.bridge.BridgeTeleportSC;
import com.sc.block.BlockCrystalDruseSC;
import com.sc.block.BlockSubstrateRockSC;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.EntryRulesSC;
import com.sc.dim.INamedDimensionSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateEntrySC;
import com.sc.dim.SafeSpotSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.TeleportQueueSC;
import com.sc.dim.WorldProviderSubstrateSC;
import com.sc.emp.EmpGunLogicSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.EmpShotSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.BornInSubstrateSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.MobDropsSC;
import com.sc.mob.MobEventsSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;
import com.sc.worldgen.OreGenSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * The Substrate (Si-14) in a running server (-Dsc.worldtest=true, docs/plan-substrate-tech.md §8.2; registered by
 * SCMod on FML's bus). SKIP while the dimension is off. One line each: "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO
 * substrate: ...", as WorldTestEmpSC.
 * <ol>
 * <li>registration: the provider type; a provider made without a world (the bridge's list) is ours, named, with its
 * lang key; the biome slots hold our biomes with their BiomeDictionary types and no spawn lists; the price multiplier
 * and the entry hook;</li>
 * <li>the world: loaded through the server, known by its provider; three chunks (0, 0), (8, 8) and (0, 5) at a Scribe
 * line - no Minecraft stone in Y 0..120, Quartz Turf on top of the plateau, the canyon biome on the line and the
 * plateau one at (128, 128); no rain;</li>
 * <li>a Lineman's armour (package A), on a mob not spawned: a blow of 10 split as MobArmorMathSC says, a blow past the
 * armour all to the health, the reserve run out - «down», no armour points; an Exo chestplate with helium - the
 * shield takes a bypassing blow off the reserve, lets the overflow on, and falls with a Point on the chestplate;</li>
 * <li>ПЭ-1 on a mob: Point on the Nano chestplate for the table's time, no longer with a repeat, then immune; the
 * points fall to iron; the drain at 500 EU a unit. Then through the gun itself (EmpGunLogicSC, a fake shooter, a
 * forced chunk): dimension.empMobTargets off - only Slowness, on - a piece suppressed;</li>
 * <li>the «born in Si-14» flag and the bridge (package E): BridgeTeleportSC keeps a native back with
 * nativesCrossBridge off; on, its copy arrives in the Overworld without the flag;</li>
 * <li>drops (package M): a marked mob killed by a FakePlayer drops no trophy, no loot, no armour;</li>
 * <li>the spawn director (package M) on the real terrain: one group for a player on the plateau, 24..48 blocks off,
 * marked, standing on rock in its armour, none with the world's spawnHostileMobs off; the way-back spot finder
 * (package E) on the plateau and in the Overworld;</li>
 * <li>a bed refused (ПВ-10) - also to a sneaking player with a wrench (doesSneakBypassUse), not to one sneaking with
 * a stick; a trophy's lock (ПТ-2) back on an EntityItem loaded from its NBT, lifted on one whose time ran out;</li>
 * <li>the glow (package W): fresh chunks far off generated with no neighbours round - every Trace Node and druse
 * populate laid has its block light (6, 10);</li>
 * <li>the nether portal kept dead (package E): an entity's timeUntilPortal is held up there;</li>
 * <li>the ПЭ-1 world test on players runs as before (its own lines, ПБ-13);</li>
 * <li>a report: with the chunk released and nobody there the world unloads.</li>
 * </ol>
 * Every switch it flips is put back, every entity it made removed, the chunk ticket released.
 */
public final class WorldTestSubstrateSC {

    private static final int START = 140, Y = 200, GUN_TIMEOUT = 200, UNLOAD_WAIT = 1200;
    /** The chunk the multi-tick checks run in (forced): above the plateau at (128..143, 128..143). */
    private static final int CX = 8, CZ = 8, GX = CX * 16 + 8, GZ = CZ * 16 + 2;

    private int ticks;
    private boolean done, finished;
    private int dim;
    private WorldServer w;
    private ForgeChunkManager.Ticket ticket;
    /** The two switches the checks flip, as they were (saved: they were read). */
    private boolean saved, savedEmp, savedNatives;
    private FakePlayer shooter;
    private ItemStack gun;
    private EntityLinemanSC gunTarget;
    private int gunPhase = -1, gunTicks;
    private Entity portalMob;
    private long portalAt = -1;
    private int unloadAt = -1;
    private final Drops drops = new Drops();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        try {
            if (ticks == START) {
                start();
            } else if (ticks > START && !finished) {
                step();
            } else if (finished && ticks >= unloadAt) {
                unloaded();
            }
        } catch (Throwable t) {
            System.out.println("[SC-WORLDTEST] FAIL substrate: exception " + t);
            t.printStackTrace();
            finish();
        }
    }

    // ------------------------------------------------------------------ the checks of one tick

    private void start() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: the dimension is off (" + SubstrateSC.offReason() + ")");
            done = true;
            return;
        }
        dim = SubstrateSC.dimId();
        savedEmp = SubstrateConfigSC.empMobTargets;
        savedNatives = SubstrateConfigSC.nativesCrossBridge;
        saved = true;
        registration();
        w = MinecraftServer.getServer().worldServerForDimension(dim);
        if (w == null) {
            print(false, "the world: worldServerForDimension(" + dim + ") gave nothing");
            finish();
            return;
        }
        world();
        ticket = ForgeChunkManager.requestTicket(SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
        if (ticket != null) {
            ticket.getModData().setString("Kind", "worldtest");          // ChunkLoaderSC releases it on a later load
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(CX, CZ));
        }
        armour();
        shield();
        empDirect();
        bridge();
        drops();
        director();
        safeSpot();
        overworldVeins();
        bed();
        trophyLock();
        glow();
        if (ticket == null) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: no chunk ticket - the gun and the portal need a forced chunk");
            finish();
            return;
        }
        startPortal();
        startGun();
    }

    /** 1: what SubstrateSC.init registered. */
    private void registration() {
        int type = DimensionManager.getProviderType(dim);
        WorldProvider p = DimensionManager.createProviderFor(dim);
        boolean provider = type == SubstrateSC.providerId() && p instanceof WorldProviderSubstrateSC && p instanceof INamedDimensionSC
                && WorldProviderSubstrateSC.NAME.equals(p.getDimensionName()) && WorldProviderSubstrateSC.NAME_KEY.equals(((INamedDimensionSC) p).nameKey())
                && SubstrateSC.isProvider(p) && !p.canRespawnHere() && p.isSurfaceWorld();
        BiomeGenBase[] slots = BiomeGenBase.getBiomeGenArray();
        BiomeSubstrateSC bp = SubstrateSC.plateaus(), bc = SubstrateSC.canyons();
        boolean biomes = bp != null && bc != null && slots[bp.biomeID] == bp && slots[bc.biomeID] == bc && bp.kind == BiomeSubstrateSC.PLATEAUS
                && bc.kind == BiomeSubstrateSC.CANYONS && dictionary(bp) && dictionary(bc) && noSpawns(bp) && noSpawns(bc);
        boolean price = SubstrateSC.bridgeCostMultiplier(dim) == SubstrateConfigSC.bridgeCostMultiplier && SubstrateSC.bridgeCostMultiplier(0) == 1F
                && SubstrateSC.isDim(dim) && !SubstrateSC.isDim(0);
        print(provider && biomes && price, "registration: dimension " + dim + ", provider type " + type + " (" + SubstrateSC.providerId()
                + "), a world-less provider " + (p == null ? "null" : p.getClass().getSimpleName() + " \"" + p.getDimensionName() + "\"") + " " + provider
                + "; biomes " + (bp == null ? "-" : bp.biomeID + " / " + bc.biomeID) + " in their slots, WASTELAND and nothing that invites other mods, "
                + "no spawn lists " + biomes + "; the bridge's x" + SubstrateSC.bridgeCostMultiplier(dim) + " only here " + price);
    }

    /** 2: the world and three chunks of it. */
    private void world() {
        boolean known = SubstrateSC.is(w) && w.provider instanceof WorldProviderSubstrateSC && w.provider.dimensionId == dim;
        int stone = 0;
        int[][] chunks = {{0, 0}, {CX, CZ}, {0, 5}};
        for (int[] c : chunks) {
            Chunk ch = w.getChunkFromChunkCoords(c[0], c[1]);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y <= 120; y++) {
                        stone += ch.getBlock(x, y, z) == Blocks.stone ? 1 : 0;
                    }
                }
            }
        }
        Chunk mid = w.getChunkFromChunkCoords(CX, CZ);
        int turf = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = mid.getHeightValue(x, z) - 1;
                if (top > 0 && SubstrateBlocksSC.rock != null && mid.getBlock(x, top, z) == SubstrateBlocksSC.rock
                        && mid.getBlockMetadata(x, top, z) == SubstrateBlocksSC.QUARTZ_TURF) {
                    turf++;
                }
            }
        }
        BiomeGenBase onLine = w.getBiomeGenForCoords(0, 5 * 16 + 8), plateau = w.getBiomeGenForCoords(128, 128);
        boolean biomes = onLine == SubstrateSC.canyons() && plateau == SubstrateSC.plateaus();
        boolean dry = w.getRainStrength(1F) == 0F && w.getWeightedThunderStrength(1F) == 0F;
        print(known && stone == 0 && turf >= 128 && biomes && dry, "the world (package W): known by its provider " + known + ", Minecraft stone in Y 0..120"
                + " of 3 chunks: " + stone + " (0), Quartz Turf on top of " + turf + " of 256 columns at (128, 128) (most), biome on the line "
                + (onLine == null ? "null" : onLine.biomeName) + ", at (128, 128) " + (plateau == null ? "null" : plateau.biomeName) + " " + biomes
                + ", rain and thunder 0 " + dry);
    }

    /** 3a: the reserve takes blockable blows by the maths, not a bypassing one; empty - «down». */
    private void armour() {
        EntityLinemanSC m = lineman(GX + 0.5, GZ + 8.5);
        wear(m, ArmorSuit.NANO, 1);
        MobArmorSC a = m.mobArmor();
        a.helium = false;
        a.emergency = false;
        a.spawnReserve(25F, 25F);
        FakePlayer hitter = fake("SubstrateHitter");
        int points = a.points();
        float r0 = a.reserve;
        float[] want = MobArmorMathSC.absorb(10F, points, r0);
        float lost = hit(m, DamageSource.causePlayerDamage(hitter), 10F);
        boolean blow = points == ArmorSuit.NANO.material.getDamageReductionAmount(1) && near(lost, want[0]) && near(r0 - a.reserve, want[1]);
        float r1 = a.reserve;
        float bypassLost = hit(m, bypass(), 3F);
        boolean past = near(bypassLost, 3F) && near(a.reserve, r1);
        a.reserve = 1F;
        float[] last = MobArmorMathSC.absorb(10F, a.points(), 1F);
        float lastLost = hit(m, DamageSource.causePlayerDamage(hitter), 10F);
        boolean down = a.down() && a.points() == 0 && m.getTotalArmorValue() == 0 && near(lastLost, last[0]);
        print(blow && past && down, "a Lineman's armour (package A): Nano chestplate " + points + " points, reserve " + r0 + ": a blow of 10 -> "
                + lost + " to the health (want " + want[0] + "), " + (r0 - r1) + " off the reserve (want " + want[1] + ") " + blow + "; a bypassing 3 -> "
                + bypassLost + " to the health, reserve kept " + past + "; the last unit gone - down " + a.down() + ", armour points "
                + m.getTotalArmorValue() + " (0), that blow " + lastLost + " (want " + last[0] + ")");
    }

    /** 3b: the shield of an Exo chestplate with helium (ПБ-4) and «Сердце комплекта». */
    private void shield() {
        EntityLinemanSC m = lineman(GX + 2.5, GZ + 8.5);
        wear(m, ArmorSuit.EXO, 1);
        MobArmorSC a = m.mobArmor();
        a.helium = true;
        a.emergency = false;
        a.spawnReserve(10F, 10F);
        float took = hit(m, bypass(), 4F);
        boolean first = near(took, 0F) && near(a.reserve, 6F);
        float over = hit(m, bypass(), 10F);
        boolean overflow = near(over, 4F) && a.reserve <= 0F;
        a.reserve = 10F;
        a.sync();
        m.empHit(fake("SubstrateShooter"), EmpMode.POINT, false, new int[]{1});
        float heart = hit(m, bypass(), 4F);
        boolean down = m.empSuppressed(1) && near(heart, 4F) && near(a.reserve, 10F);
        print(first && overflow && down, "the shield (package A): Exo chestplate with helium, reserve 10 - a bypassing 4 -> " + took + " to the health"
                + " (0), the reserve 6 " + first + "; a bypassing 10 -> " + over + " (4, the overflow) " + overflow + "; Point on the chestplate - the shield"
                + " down, a bypassing 4 -> " + heart + " to the health, reserve kept " + down);
    }

    /** 4a: ПЭ-1 on a mob directly (IEmpTargetSC), the immunity, iron points, the siphon's drain. */
    private void empDirect() {
        EntityLinemanSC m = lineman(GX + 4.5, GZ + 8.5);
        wear(m, ArmorSuit.NANO, 1);
        MobArmorSC a = m.mobArmor();
        a.spawnReserve(25F, 25F);
        FakePlayer s = fake("SubstrateShooter");
        int want = EmpMathSC.durationTicks(ArmorSuit.NANO, EmpMode.POINT, false, 0, false, ConfigSC.empDurationMultiplier);
        EmpSC.HitResult r = m.empHit(s, EmpMode.POINT, false, new int[]{1});
        int got = m.empSuppressedTicks(1);
        boolean point = r.ticks[1] == want && got == want && m.empSuppressed(1);
        int iron = a.points();
        boolean ironOk = iron == MobArmorMathSC.ironPoints(1);
        m.empHit(s, EmpMode.POINT, false, new int[]{1});
        boolean noLonger = m.empSuppressedTicks(1) <= want;
        a.suppressedUntil[1] = w.getTotalWorldTime();                    // the suppression over: now its immunity
        EmpSC.HitResult again = m.empHit(s, EmpMode.POINT, false, new int[]{1});
        boolean immune = again.immune[1] && again.ticks[1] == 0 && !m.empSuppressed(1) && m.empImmuneTicks(1) > 0;
        print(point && ironOk && noLonger && immune, "PE-1 on a mob (package A): Point on the Nano chestplate " + got + " ticks (want " + want
                + " = 16 s), the points " + iron + " (iron " + MobArmorMathSC.ironPoints(1) + ") " + ironOk + "; a repeat at once - no longer " + noLonger
                + "; after it the piece is immune " + immune + " (" + m.empImmuneTicks(1) + " ticks)");

        EntityLinemanSC d = lineman(GX + 6.5, GZ + 8.5);
        wear(d, ArmorSuit.NANO, 1);
        d.mobArmor().spawnReserve(25F, 25F);
        long cap = d.empCapacityEu();
        int taken = d.empDrainEu(5000);
        float left = d.mobArmor().reserve;
        int rest = d.empDrainEu(100000);
        boolean drain = cap == 25L * ArmorSuit.NANO.euPerDamage && taken == 5000 && near(left, 25F - 5000F / ArmorSuit.NANO.euPerDamage)
                && rest == (int) (left * ArmorSuit.NANO.euPerDamage) && d.mobArmor().down();
        print(drain, "the siphon's drain (package A): a Nano reserve of 25 = " + cap + " EU (" + ArmorSuit.NANO.euPerDamage + " EU a unit); 5000 EU taken "
                + taken + ", the reserve " + left + " (15); the rest " + rest + " EU, down " + d.mobArmor().down());
    }

    /** 5: the flag and the bridge (ПВ-8). */
    private void bridge() {
        EntityMarauderSC m = new EntityMarauderSC(w);
        m.setLocationAndAngles(GX + 0.5, Y, GZ + 12.5, 0F, 0F);
        m.func_110163_bv();
        w.spawnEntityInWorld(m);
        BornInSubstrateSC.mark(m);
        boolean marked = BornInSubstrateSC.isBorn(m);
        WorldServer over = DimensionManager.getWorld(0);
        ChunkCoordinates spawn = over.getSpawnPoint();
        SubstrateConfigSC.nativesCrossBridge = false;
        Entity kept = BridgeTeleportSC.teleport(m, 0, spawn.posX + 0.5, Y, spawn.posZ + 0.5, 0F);
        boolean refused = kept == null && !m.isDead && m.worldObj == w;
        SubstrateConfigSC.nativesCrossBridge = true;
        Entity copy = BridgeTeleportSC.teleport(m, 0, spawn.posX + 0.5, Y, spawn.posZ + 0.5, 0F);
        SubstrateConfigSC.nativesCrossBridge = savedNatives;
        boolean crossed = copy != null && copy.worldObj != null && copy.worldObj.provider.dimensionId == 0 && !BornInSubstrateSC.isBorn(copy);
        if (copy != null) {
            copy.setDead();
        }
        m.setDead();
        boolean hook = SubstrateEntrySC.allowsArrival(fake("SubstrateHitter"), SubstrateEntrySC.VIA_BRIDGE)
                && SubstrateEntrySC.allowsArrival(fake("SubstrateHitter"), "sc.worldtest") == !SubstrateConfigSC.strictEntry;
        print(marked && refused && crossed && hook, "the flag and the bridge (package E): marked " + marked + "; nativesCrossBridge off - the bridge"
                + " keeps it back " + refused + "; on - the copy in the Overworld without the flag " + crossed + "; allowsArrival: the bridge yes, an"
                + " unknown way " + (SubstrateConfigSC.strictEntry ? "no" : "yes (strictEntry off)") + " " + hook);
    }

    /** 6: a marked mob killed by a FakePlayer - only experience (ПТ-1). */
    private void drops() {
        EntityLinemanSC m = lineman(GX + 4.5, GZ + 12.5);
        wear(m, ArmorSuit.NANO, 0, 1);
        m.mobArmor().spawnReserve(5F, 5F);
        m.func_110163_bv();
        w.spawnEntityInWorld(m);
        BornInSubstrateSC.mark(m);
        FakePlayer killer = fake("SubstrateKiller");
        drops.watch = m;
        drops.got = null;
        MinecraftForge.EVENT_BUS.register(drops);
        try {
            m.hurtResistantTime = 0;
            m.attackEntityFrom(DamageSource.causePlayerDamage(killer), 1000F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(drops);
        }
        List<EntityItem> got = drops.got;
        int trophies = 0, loot = 0, armour = 0;
        StringBuilder what = new StringBuilder();
        for (EntityItem e : got == null ? new ArrayList<EntityItem>() : got) {
            ItemStack s = e.getEntityItem();
            if (s == null || s.getItem() == null) {
                continue;
            }
            trophies += MobItemsSC.trophy != null && s.getItem() == MobItemsSC.trophy ? 1 : 0;
            loot += MobItemsSC.combatLoot != null && s.getItem() == MobItemsSC.combatLoot ? 1 : 0;
            armour += s.getItem() instanceof ItemArmorSC ? 1 : 0;
            what.append(' ').append(s.getDisplayName()).append(" x").append(s.stackSize);
        }
        boolean died = m.getHealth() <= 0F && got != null;
        m.setDead();
        print(died && trophies == 0 && loot == 0 && armour == 0, "drops (package M): a marked Lineman killed in the Substrate by a FakePlayer - died "
                + died + ", trophies " + trophies + ", loot " + loot + ", worn armour " + armour + " (all 0; drops:" + (what.length() == 0 ? " none" : what) + ")");
        System.out.println("[SC-WORLDTEST] INFO substrate: the trophy roll for a real EntityPlayerMP is MobDropsSC's own decision (package M's tests)");
    }

    /** 6b: SpawnDirectorSC.tryGroup for a (fake) player standing on the plateau - the chunks round him loaded first. */
    private void director() {
        if (w.difficultySetting == EnumDifficulty.PEACEFUL || !w.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                || !SpawnDirectorSC.hostilesAllowed(w)) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: the spawn director - Peaceful, doMobSpawning off or spawn-monsters=false");
            return;
        }
        int r = (SpawnRulesSC.MAX_DIST + SpawnRulesSC.GROUP_SPREAD + 15) >> 4;
        for (int cx = CX - r; cx <= CX + r; cx++) {
            for (int cz = CZ - r; cz <= CZ + r; cz++) {
                w.getChunkFromChunkCoords(cx, cz);
            }
        }
        int py = w.getHeightValue(GX, GZ);
        FakePlayer p = fake("SubstrateWalker");
        p.setLocationAndAngles(GX + 0.5, py, GZ + 0.5, 0F, 0F);
        List<Entity> before = new ArrayList<Entity>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityArmoredMobSC) {
                before.add((Entity) o);
            }
        }
        int made = 0, tries = 0;
        while (made == 0 && tries < 8) {
            made = SpawnDirectorSC.tryGroup(w, p);
            tries++;
        }
        List<EntityArmoredMobSC> group = new ArrayList<EntityArmoredMobSC>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityArmoredMobSC && !before.contains(o) && !((Entity) o).isDead) {
                group.add((EntityArmoredMobSC) o);
            }
        }
        int lo = SpawnRulesSC.MIN_DIST - SpawnRulesSC.GROUP_SPREAD - 1, hi = SpawnRulesSC.MAX_DIST + SpawnRulesSC.GROUP_SPREAD + 1;
        boolean placed = made >= 1 && made <= 4 && group.size() == made;
        StringBuilder where = new StringBuilder();
        for (EntityArmoredMobSC m : group) {
            double d = Math.sqrt((m.posX - p.posX) * (m.posX - p.posX) + (m.posZ - p.posZ) * (m.posZ - p.posZ));
            int x = MathHelper.floor_double(m.posX), y = MathHelper.floor_double(m.posY), z = MathHelper.floor_double(m.posZ);
            boolean onRock = w.getBlock(x, y - 1, z) == SubstrateBlocksSC.rock || w.getBlock(x, y - 1, z) == SubstrateBlocksSC.ore;
            placed &= d >= lo && d <= hi && BornInSubstrateSC.isBorn(m) && onRock && !w.isAnyLiquid(m.boundingBox) && m.mobArmor().suit() != null
                    && m.mobArmor().reserve > 0F;
            where.append(' ').append(m.getClass().getSimpleName().replace("Entity", "").replace("SC", "")).append(" at ").append((int) Math.round(d))
                    .append(onRock ? "" : " (not on rock)");
            m.setDead();
        }
        print(placed, "the spawn director (package M): a player on the plateau at Y " + py + " - " + made + " mob(s) after " + tries + " tr"
                + (tries == 1 ? "y" : "ies") + ":" + (where.length() == 0 ? " none" : where.toString()) + " blocks off (want 1..4, " + lo + ".." + hi
                + ", marked, on the Substrate's rock, armoured)");

        // server.properties spawn-monsters=false: the world's spawnHostileMobs off - vanilla spawns no monster, nor does the director
        w.setAllowedSpawnTypes(false, true);
        boolean off = !SpawnDirectorSC.hostilesAllowed(w);
        int none = 0;
        for (int i = 0; i < 8; i++) {
            none += SpawnDirectorSC.tryGroup(w, p);
        }
        MinecraftServer s = MinecraftServer.getServer();
        s.func_147139_a(s.func_147135_j());                            // every world's spawn flags back from the server's settings
        boolean back = SpawnDirectorSC.hostilesAllowed(w);
        print(off && none == 0 && back, "the spawn director with the world's spawnHostileMobs off (spawn-monsters=false): read " + off + ", "
                + none + " mob(s) in 8 tries (0); the flag back from the server's settings " + back);
    }

    /** 6d: ПВ-10 - a bed in the Substrate is refused before it explodes, also to a sneaking player whose wrench reaches the block. */
    private void bed() {
        int x = GX + 3, y = Y + 10, z = GZ + 12;
        if (ModItems.WRENCHES.isEmpty() || !w.isAirBlock(x, y, z)) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: the bed - no wrench registered or no air at (" + x + ", " + y + ", " + z + ")");
            return;
        }
        w.setBlock(x, y, z, Blocks.bed, 0, 2);
        FakePlayer p = fake("SubstrateSleeper");
        boolean plain = clickBed(p, x, y, z, false, null);
        boolean wrench = clickBed(p, x, y, z, true, new ItemStack(ModItems.WRENCHES.get(0)));
        boolean stick = clickBed(p, x, y, z, true, new ItemStack(Items.stick));
        boolean whole = w.getBlock(x, y, z) == Blocks.bed;
        w.setBlockToAir(x, y, z);
        p.inventory.mainInventory[0] = null;
        print(plain && wrench && !stick && whole, "the bed (package E, ПВ-10): a right click refused with an empty hand " + plain + ", sneaking with a wrench"
                + " (doesSneakBypassUse) " + wrench + ", let through sneaking with a stick (the item is used, not the bed) " + !stick + "; the bed still there "
                + whole);
    }

    /** One right click on the block as ItemInWorldManager posts it. @return it was cancelled */
    private boolean clickBed(FakePlayer p, int x, int y, int z, boolean sneak, ItemStack held) {
        p.setSneaking(sneak);
        p.inventory.currentItem = 0;
        p.inventory.mainInventory[0] = held;
        PlayerInteractEvent e = new PlayerInteractEvent(p, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x, y, z, 1, w);
        MinecraftForge.EVENT_BUS.post(e);
        p.setSneaking(false);
        return e.isCanceled();
    }

    /**
     * 6e: ПТ-2 - the trophy's lock rides in its NBT: an EntityItem read back from a locked one (a chunk unloaded, a
     * restart) is locked again with its owner; one read back after the 15 s is free at once.
     */
    private void trophyLock() {
        long now = w.getTotalWorldTime();
        EntityItem a = new EntityItem(w, GX + 0.5, Y, GZ + 6.5, new ItemStack(Items.stick));
        a.func_145797_a("SubstrateKiller");
        MobEventsSC.lockTrophy(a, now + MobDropsSC.OWNER_TICKS);
        NBTTagCompound saved = new NBTTagCompound();
        a.writeToNBT(saved);
        int locks = MobEventsSC.locks();
        EntityItem b = new EntityItem(w);
        b.readFromNBT(saved);
        MobEventsSC.relock(b, now + 20);                               // as EntityJoinWorldEvent does, 1 s later
        boolean kept = "SubstrateKiller".equals(b.func_145798_i()) && MobEventsSC.locks() == locks + 1;
        EntityItem c = new EntityItem(w);
        c.readFromNBT(saved);
        MobEventsSC.relock(c, now + MobDropsSC.OWNER_TICKS);
        boolean lifted = c.func_145798_i() == null && !c.getEntityData().hasKey(MobEventsSC.LOCK_NBT) && MobEventsSC.locks() == locks + 1;
        a.setDead();                                                   // their locks go at the next server tick
        b.setDead();
        print(kept && lifted, "the trophy lock (package M, ПТ-2) in the item's NBT (" + MobEventsSC.LOCK_NBT + "): a copy loaded within the "
                + MobDropsSC.OWNER_TICKS / 20 + " s is locked again, its owner kept " + kept + "; one loaded after them is free at once " + lifted);
    }

    /**
     * 6f: the glow (package W) - a strip of fresh chunks far off, loaded with nothing round them (as at the edge of the
     * generated land: World.updateLightByType wants every chunk 17 blocks round), so populate's setBlock works out no
     * light; every Trace Node it laid must have its 6 all the same, every druse crystal its 10. A few strips until one
     * has a node (the traces lie 1 in 2 origin chunks); new ones every run (the server's random).
     */
    private void glow() {
        int nodes = 0, nodesLit = 0, druses = 0, drusesLit = 0, strips = 0;
        String dark = null;
        while (nodes == 0 && strips < 4) {
            int cx0 = 20000 + w.rand.nextInt(20000), cz0 = 20000 + w.rand.nextInt(20000);
            strips++;
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 5; dx++) {
                    w.getChunkFromChunkCoords(cx0 + dx, cz0 + dz);     // the last of each 2 x 2 populates its corner chunk's window
                }
            }
            for (int k = 0; k < 10; k++) {                                // every glowing block here was laid by these windows' populate
                Chunk ch = w.getChunkFromChunkCoords(cx0 + k % 5, cz0 + k / 5);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 1; y < 255; y++) {
                            Block b = ch.getBlock(x, y, z);
                            int want = b == SubstrateBlocksSC.druse ? BlockCrystalDruseSC.LIGHT : SubstrateBlocksSC.rock != null && b == SubstrateBlocksSC.rock
                                    && ch.getBlockMetadata(x, y, z) == SubstrateBlocksSC.TRACE_NODE ? BlockSubstrateRockSC.NODE_LIGHT : 0;
                            if (want == 0) {
                                continue;
                            }
                            boolean lit = ch.getSavedLightValue(EnumSkyBlock.Block, x, y, z) >= want;
                            if (want == BlockCrystalDruseSC.LIGHT) {
                                druses++;
                                drusesLit += lit ? 1 : 0;
                            } else {
                                nodes++;
                                nodesLit += lit ? 1 : 0;
                            }
                            if (!lit && dark == null) {
                                dark = "(" + (ch.xPosition * 16 + x) + ", " + y + ", " + (ch.zPosition * 16 + z) + ") light "
                                        + ch.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
                            }
                        }
                    }
                }
            }
        }
        if (nodes == 0) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: the glow - no Trace Node in " + strips + " strips of fresh chunks (druses lit "
                    + drusesLit + " of " + druses + ")");
            return;
        }
        print(nodesLit == nodes && drusesLit == druses, "the glow (package W): fresh chunks with no neighbours round (" + strips + " strip"
                + (strips == 1 ? "" : "s") + ") - Trace Nodes lit " + nodesLit + " of " + nodes + " (" + BlockSubstrateRockSC.NODE_LIGHT + "), druse crystals "
                + drusesLit + " of " + druses + " (" + BlockCrystalDruseSC.LIGHT + ")" + (dark == null ? "" : "; dark at " + dark));
    }

    /** 6c: SafeSpotSC (strictEntry's way back): a place on the plateau from above, none inside the rock, one in the Overworld. */
    private void safeSpot() {
        int surface = SubstrateTerrainSC.surfaceY(w.getSeed(), GX, GZ) + 1;
        int[] s = SafeSpotSC.find(w, null, GX, Y, GZ, TeleportQueueSC.SEARCH_RADIUS);
        boolean here = s != null && Math.abs(s[0] - GX) <= TeleportQueueSC.SEARCH_RADIUS && Math.abs(s[2] - GZ) <= TeleportQueueSC.SEARCH_RADIUS
                && SafeSpotSC.fits(w, null, s[0], s[1], s[2]) && w.isAirBlock(s[0], s[1], s[2]) && w.isAirBlock(s[0], s[1] + 1, s[2])
                && Math.abs(s[1] - surface) <= 12;
        boolean notInRock = !SafeSpotSC.fits(w, null, GX, 30, GZ) || w.isAirBlock(GX, 30, GZ);
        WorldServer over = DimensionManager.getWorld(0);
        ChunkCoordinates sp = over == null ? null : over.getSpawnPoint();
        int[] o = sp == null ? null : SafeSpotSC.find(over, null, sp.posX, SafeSpotSC.surfaceY(over, sp.posX, sp.posY, sp.posZ), sp.posZ,
                TeleportQueueSC.SEARCH_RADIUS);
        print(here && notInRock && o != null, "the way-back spot (package E): from Y " + Y + " above the plateau - " + (s == null ? "none"
                : "(" + s[0] + ", " + s[1] + ", " + s[2] + ")") + " (the surface about Y " + surface + ") " + here + ", none inside the rock at Y 30 "
                + notInRock + "; the Overworld's spawn - " + (o == null ? "none" : "(" + o[0] + ", " + o[1] + ", " + o[2] + ")"));
    }

    /**
     * W2: the Overworld's small veins. In a stone cube in the air above the spawn, 20 veins each of tantalite (2),
     * wolframite (3) and copper-sized 6 through OreGenSC.vein: every small vein places 1..size blocks, the big one
     * still goes through WorldGenMinable. Vanilla WorldGenMinable at 2 is counted for the record. The cube is cleared.
     */
    private void overworldVeins() {
        WorldServer over = DimensionManager.getWorld(0);
        ChunkCoordinates sp = over == null ? null : over.getSpawnPoint();
        if (sp == null) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: the Overworld's small veins - no Overworld");
            return;
        }
        int cx = sp.posX, cy = 200, cz = sp.posZ, r = 6;
        int[] sizes = {2, 3, 6};
        int[] got = new int[sizes.length];
        boolean each = true;
        for (int k = 0; k < sizes.length; k++) {
            for (int i = 0; i < 20; i++) {
                cube(over, cx, cy, cz, r, Blocks.stone);
                OreGenSC.vein(over, new Random(1000L * k + i), OreEntry.TANTALITE, sizes[k], null, cx - 8, cy, cz - 8);
                int n = count(over, cx, cy, cz, r);
                got[k] += n;
                if (sizes[k] < 4 && (n < 1 || n > sizes[k])) {
                    each = false;
                }
            }
        }
        int vanilla = 0;
        for (int i = 0; i < 20; i++) {
            cube(over, cx, cy, cz, r, Blocks.stone);
            new net.minecraft.world.gen.feature.WorldGenMinable(ModBlocks.oreSC, OreEntry.TANTALITE.meta(), 2, Blocks.stone)
                    .generate(over, new Random(i), cx - 8, cy, cz - 8);
            vanilla += count(over, cx, cy, cz, r);
        }
        cube(over, cx, cy, cz, r, Blocks.air);
        print(each && got[0] >= 20 && got[1] >= 20 && got[2] > 0, "the Overworld's small veins (W2): 20 veins of 2 - " + got[0]
                + " blocks, of 3 - " + got[1] + ", of 6 (WorldGenMinable) - " + got[2] + "; each small vein 1..size " + each
                + " (vanilla WorldGenMinable at 2 placed " + vanilla + ")");
    }

    private static void cube(World w, int cx, int cy, int cz, int r, Block b) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    w.setBlock(x, y, z, b, 0, 2);
                }
            }
        }
    }

    private static int count(World w, int cx, int cy, int cz, int r) {
        int n = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    n += w.getBlock(x, y, z) == ModBlocks.oreSC ? 1 : 0;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the checks over several ticks (the forced chunk)

    /** 7: any entity there keeps its portal cooldown up (ПВ-4). */
    private void startPortal() {
        EntityPig pig = new EntityPig(w);
        pig.setLocationAndAngles(GX + 0.5, Y, GZ + 10.5, 0F, 0F);
        pig.func_110163_bv();
        w.spawnEntityInWorld(pig);
        pig.timeUntilPortal = 0;
        portalMob = pig;
        portalAt = w.getTotalWorldTime();
    }

    /** 4b: the gun itself at a mob: phase 0 with empMobTargets off (Slowness only), phase 1 with it on (suppressed). */
    private void startGun() {
        if (w.difficultySetting == EnumDifficulty.PEACEFUL || !ConfigSC.empEnabled) {
            System.out.println("[SC-WORLDTEST] SKIP substrate: PE-1 through the gun - " + (ConfigSC.empEnabled ? "the world is Peaceful (no mobs live)"
                    : "weapons.pe1.enabled = false"));
            return;
        }
        shooter = new FakePlayer(w, profile("SubstrateGunner"));
        shooter.setLocationAndAngles(GX + 0.5, Y, GZ + 0.5, 0F, 0F);
        gunTarget = lineman(GX + 0.5, GZ + 4.5);
        wear(gunTarget, ArmorSuit.NANO, 0, 1, 2, 3);
        gunTarget.mobArmor().spawnReserve(25F, 25F);
        gunTarget.func_110163_bv();
        w.spawnEntityInWorld(gunTarget);
        gun = new ItemStack(ModItems.empGun);
        ItemEmpGunSC.setCharge(gun, ItemEmpGunSC.capacityOf(gun));
        ItemEmpGunSC.setMode(gun, EmpMode.POINT);
        shooter.inventory.currentItem = 0;
        shooter.inventory.mainInventory[0] = gun;
        SubstrateConfigSC.empMobTargets = false;
        gunPhase = 0;
        pull();
    }

    private void step() {
        if (portalMob != null) {
            pin(portalMob, GX + 0.5, Y, GZ + 10.5);
            if (w.getTotalWorldTime() >= portalAt + EntryRulesSC.PORTAL_CHECK + 2) {
                boolean held = portalMob.timeUntilPortal >= 20;
                print(held, "the nether portal (package E): an entity in the Substrate, timeUntilPortal 0 -> " + portalMob.timeUntilPortal + " after "
                        + (w.getTotalWorldTime() - portalAt) + " ticks (>= 20, held at " + EntryRulesSC.PORTAL_HOLD + " every " + EntryRulesSC.PORTAL_CHECK + ")");
                portalMob.setDead();
                portalMob = null;
            }
        }
        if (gunPhase >= 0) {
            gunStep();
        }
        if (portalMob == null && gunPhase < 0) {
            finish();
        }
    }

    private void gunStep() {
        pin(gunTarget, GX + 0.5, Y, GZ + 4.5);
        aim(shooter, gunTarget);
        gunTicks++;
        EmpGunLogicSC.usingTick(gun, shooter, gunTicks);                 // FakePlayers aren't ticked: drive the item and the charge-up by hand
        EmpGunLogicSC.playerTick(shooter);
        if (shooter.isUsingItem() && gunTicks <= GUN_TIMEOUT) {
            return;
        }
        boolean slow = gunTarget.isPotionActive(Potion.moveSlowdown), suppressed = false;
        for (int i = 0; i < 4; i++) {
            suppressed |= gunTarget.empSuppressed(i);
        }
        if (gunPhase == 0) {
            print(gunTicks <= GUN_TIMEOUT && slow && !suppressed, "PE-1 through the gun, dimension.empMobTargets off: Point at a Lineman in Nano - after "
                    + gunTicks + " ticks Slowness " + slow + ", a piece suppressed " + suppressed + " (want Slowness only, as before)");
            gunTarget.removePotionEffect(Potion.moveSlowdown.id);
            SubstrateConfigSC.empMobTargets = true;
            gunPhase = 1;
            pull();
            return;
        }
        print(gunTicks <= GUN_TIMEOUT && suppressed && !slow, "PE-1 through the gun (package A), dimension.empMobTargets on: after " + gunTicks
                + " ticks a piece suppressed " + suppressed + ", no Slowness " + !slow + "; the gun has " + ItemEmpGunSC.chargeOf(gun) + " EU left");
        SubstrateConfigSC.empMobTargets = savedEmp;
        gunPhase = -1;
    }

    /** Right-click with the gun (a release first: the last shot's "button still held" is forgotten). */
    private void pull() {
        EmpGunLogicSC.stoppedUsing(gun, w, shooter, 0);
        aim(shooter, gunTarget);
        ItemStack held = EmpGunLogicSC.rightClick(gun, w, shooter);
        if (held != null && held != gun) {
            gun = held;
            shooter.inventory.mainInventory[0] = gun;
        }
        gunTicks = 0;
    }

    // ------------------------------------------------------------------ the end

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (saved) {
            SubstrateConfigSC.empMobTargets = savedEmp;
            SubstrateConfigSC.nativesCrossBridge = savedNatives;
        }
        for (Entity e : new Entity[]{gunTarget, portalMob}) {
            if (e != null) {
                e.setDead();
            }
        }
        if (shooter != null) {
            EmpGunLogicSC.forget(shooter);
            shooter.inventory.mainInventory[0] = null;
        }
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
        gunPhase = -1;
        portalMob = null;
        System.out.println("[SC-WORLDTEST] INFO substrate: the PE-1 world test on players (its 'pe1 ...' lines) must pass as before (ПБ-13)");
        if (w == null) {
            done = true;
            System.out.println("[SC-WORLDTEST] DONE substrate");
            return;
        }
        w = null;
        unloadAt = ticks + UNLOAD_WAIT;
    }

    /** 9: a report only - the world goes ~20 s after the last player (keepLoaded off), unless something holds it. */
    private void unloaded() {
        boolean gone = DimensionManager.getWorld(dim) == null;
        System.out.println("[SC-WORLDTEST] INFO substrate: " + UNLOAD_WAIT + " ticks after the test, with no players and the ticket released, the world is "
                + (gone ? "unloaded" : "still loaded" + (SubstrateConfigSC.keepLoaded() ? " (dimension.keepLoaded)" : " (a chunk loader or a player?)")));
        done = true;
        System.out.println("[SC-WORLDTEST] DONE substrate");
    }

    // ------------------------------------------------------------------

    /** The drops of the one mob watched, as the last listener sees them (then nothing is spawned). */
    public static final class Drops {
        Entity watch;
        List<EntityItem> got;

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onDrops(LivingDropsEvent e) {
            if (watch != null && e.entityLiving == watch) {
                got = new ArrayList<EntityItem>(e.drops);
                e.setCanceled(true);
            }
        }
    }

    private EntityLinemanSC lineman(double x, double z) {
        EntityLinemanSC m = new EntityLinemanSC(w);
        m.setLocationAndAngles(x, Y, z, 0F, 0F);
        return m;
    }

    /** The mod's suit pieces of these armour types (0 helmet .. 3 boots) worn: equipment slot 4 - armorType. */
    private static void wear(EntityLiving m, ArmorSuit suit, int... armorTypes) {
        for (int t : armorTypes) {
            m.setCurrentItemOrArmor(4 - t, new ItemStack(ModItems.ARMOR.get(suit)[t]));
        }
    }

    /** One blow at full health with the hurt-resistance frames off. @return the health it took */
    private static float hit(EntityLivingBase e, DamageSource src, float amount) {
        e.setHealth(e.getMaxHealth());
        e.hurtResistantTime = 0;
        float before = e.getHealth();
        e.attackEntityFrom(src, amount);
        return before - e.getHealth();
    }

    /** A blow past the armour (as the Exo blade's), not the void's. */
    private static DamageSource bypass() {
        return new DamageSource("scWorldTest").setDamageBypassesArmor();
    }

    private FakePlayer fake(String name) {
        FakePlayer p = new FakePlayer(w, profile(name));
        p.setLocationAndAngles(GX + 0.5, Y, GZ + 0.5, 0F, 0F);
        return p;
    }

    private static GameProfile profile(String name) {
        return new GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name);
    }

    private static void pin(Entity e, double x, double y, double z) {
        e.setPosition(x, y, z);
        e.motionX = e.motionY = e.motionZ = 0;
        e.fallDistance = 0;
    }

    /** Turns `p` to look at the middle of `t` (the hitscan goes along the look). */
    private static void aim(EntityPlayer p, Entity t) {
        Vec3 eyes = EmpShotSC.eyes(p), mid = EmpShotSC.centre(t);
        double dx = mid.xCoord - eyes.xCoord, dy = mid.yCoord - eyes.yCoord, dz = mid.zCoord - eyes.zCoord;
        p.rotationYaw = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
        p.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180D / Math.PI);
        p.rotationYawHead = p.rotationYaw;
        p.prevRotationYaw = p.rotationYaw;
        p.prevRotationPitch = p.rotationPitch;
    }

    private static boolean dictionary(BiomeGenBase b) {
        if (!BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.WASTELAND)) {
            return false;
        }
        for (BiomeDictionary.Type t : new BiomeDictionary.Type[]{BiomeDictionary.Type.FOREST, BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.JUNGLE,
                BiomeDictionary.Type.SWAMP, BiomeDictionary.Type.WATER, BiomeDictionary.Type.MAGICAL, BiomeDictionary.Type.SPOOKY, BiomeDictionary.Type.OCEAN,
                BiomeDictionary.Type.RIVER, BiomeDictionary.Type.LUSH, BiomeDictionary.Type.CONIFEROUS}) {
            if (BiomeDictionary.isBiomeOfType(b, t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean noSpawns(BiomeGenBase b) {
        for (EnumCreatureType t : new EnumCreatureType[]{EnumCreatureType.monster, EnumCreatureType.creature, EnumCreatureType.ambient,
                EnumCreatureType.waterCreature}) {
            List<?> l = b.getSpawnableList(t);
            if (l != null && !l.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " substrate: " + what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3F;
    }
}
