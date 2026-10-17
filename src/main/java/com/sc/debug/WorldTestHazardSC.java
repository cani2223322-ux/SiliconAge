package com.sc.debug;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateRadiationSC;
import com.sc.dim.SubstrateSC;
import com.sc.emp.EmpSC;
import com.sc.emp.EmpStateSC;
import com.sc.hazard.BridgeEchoSC;
import com.sc.hazard.EmpStormSC;
import com.sc.hazard.RaidRulesSC;
import com.sc.hazard.RaidSC;
import com.sc.hazard.StormDataSC;
import com.sc.hazard.StormRulesSC;
import com.sc.hazard.StormStateSC;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.BornInSubstrateSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityNaniteSC;
import com.sc.mob.MobDropsSC;
import com.sc.radiation.RadiationSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.settle.CrumbLimitSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * PACKAGE D (docs/plan-substrate-tech-f4.md §11.2): Ф4's world events on a real server (-Dsc.worldtest=true, after
 * WorldTestArmourerSC) - the background radiation, the EMP storm (a pulse on a FakePlayer and on a mob under the sky and under
 * cover, the solar panels' switch, a Shell's reboot, the settlers spared, the trophies x1.5), the haze's sight and the
 * Emission's radiation, a raid (its waves at the gate, born in Si-14, a victory's rewards, a closed trade refusing), the
 * bridge's echo (home without the Shield, with it, the squad into the Substrate, none at a dock), the daily cap of the Crumbs
 * and a Sealed Crumb opening. Output "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO hazard: ...".
 * <ol>
 * <li>the radiation: a FakePlayer on a column of a decided Oxide Fields cell (the chunk's own biome) - RadiationSC.levelAt 1
 * and more; on a Rift column 2 and more, x3 with the Emission's window set; a settlement's dome pays the radiation (ПА-3);</li>
 * <li>the haze's window set - EmpStormSC.haze, sightRange at most 24; restored;</li>
 * <li>the Crumbs: a FakePlayer never gets one; a real (never spawned) player three a day, then none; a Sealed Crumb opens into
 * a Crumb within the cap and stays sealed past it;</li>
 * <li>the storm: startNow(20, 400) - WARNING, then STORM; one pulse - the FakePlayer in a Nano chestplate under the sky has it
 * suppressed 60..80 ticks, the one under 3 blocks nothing, the dressed Lineman a piece, the Hollow Shell reboots, a dome
 * warden nothing; again at once after its end - immune; the panels blocked, the trophies x1.5; stopNow - CALM;</li>
 * <li>a raid on a settlement with its core loaded, a never-spawned defender of Trust 150: startNow, the waves outside the
 * dome (scRaid, born in Si-14), each killed - the next, the third down - won: Trust + rep(size), the chips in the defender's
 * inventory; a closed trade (extra "R".closed) - TradeServerSC.execute refuses R_RAID; on Peaceful none begins; a wave removed
 * alive (setDead) - called off, no reward, the trade open;</li>
 * <li>the echo: crossed() home without the Shield until the roll hits - 3..6 particles at the arrival, gone after 60 s; with
 * the Shield never; into the Substrate - a squad of Marauders 60 s later near the arrival, none at a dock.</li>
 * </ol>
 * Built on the core alone (packages S / C / M add the bodies; a missing one fails its own lines). The test players are never
 * spawned: a pulse sees them because they are in playerEntities for that one call only (as the mobs in loadedEntityList);
 * every ticket released, every block placed removed, the weather's data and the registry's records put back, the creatures
 * the events made removed. WorldTestWildSC waits for `finished`.
 */
public final class WorldTestHazardSC {

    /** After WorldTestArmourerSC (START 4500, done within ~1300 ticks). */
    static final int START = 6000;
    /** WorldTestWildSC starts after this. */
    static volatile boolean finished;
    private static final int WARN = 20, STORM = 400, POLL = 20, RAID_MAX = 2000, ECHO_WAIT = BridgeEchoSC.SQUAD_DELAY + 160, ECHO_TRIES = 80;
    private static final int PH_STORM = 1, PH_RAID = 2, PH_ECHO = 3;
    private static final GameProfile SKY = profile("sky"), ROOF = profile("roof"), RAD = profile("rad"), TRADER = profile("trader"), CRUMBS = profile("crumbs"),
            DEFENDER = profile("defender"), HOME = profile("home");

    private int ticks, phase, phaseAt, lastPoll;
    private boolean done, started;
    private WorldServer w, w0;
    private int bx, by, bz;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final List<int[]> placed = new ArrayList<int[]>();
    private final List<Entity> madeHere = new ArrayList<Entity>();
    /** The storm's actors. */
    private FakePlayer sky, roof;
    private EntityLinemanSC lineman;
    private EntityHollowShellSC shell;
    private EntityWardenSC warden;
    private float stormChance;
    /** The raid. */
    private SettlementRegistrySC reg;
    private SettlementRegistrySC.Settlement raided;
    private NBTTagCompound savedRaid;
    private FakePlayer defender;
    private int repBefore, waves, raidAt;
    private final Set<Integer> raiders = new HashSet<Integer>();
    private boolean raidBegan, raidWavesOk = true;
    private final StringBuilder raidLog = new StringBuilder();
    /** The echo. */
    private int echoAt, homeParticles = -1, shieldParticles;
    private double hx, hy, hz, ax, ay, az, dx, dy, dz;
    private boolean echoHomeHit, arrivalQueued, dockTried, echoChecked;
    private final Set<Integer> marauderIdsBefore = new HashSet<Integer>(), nanitesSeen = new HashSet<Integer>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        if (ticks < START) {
            return;
        }
        try {
            if (!started) {
                started = true;
                start();
            } else {
                step(ticks - phaseAt);
            }
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
            finish();
        }
    }

    // ------------------------------------------------------------------ the schedule

    private void start() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP hazard: the dimension is off (" + SubstrateSC.offReason() + ")");
            done = true;
            finished = true;
            return;
        }
        w = MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId());
        w0 = MinecraftServer.getServer().worldServerForDimension(0);
        reg = w == null ? null : SettlementSC.registry(w);
        if (w == null || w0 == null || reg == null) {
            print(false, "the Substrate's world / the Overworld / the settlements' registry not there");
            finish();
            return;
        }
        base();
        step("radiation", new Runnable() {
            public void run() {
                radiation();
            }
        });
        step("haze and emission", new Runnable() {
            public void run() {
                hazeAndEmission();
            }
        });
        step("crumbs", new Runnable() {
            public void run() {
                crumbs();
            }
        });
        step("echo home", new Runnable() {
            public void run() {
                echoStart();
            }
        });
        step("storm start", new Runnable() {
            public void run() {
                stormStart();
            }
        });
        phase = PH_STORM;
        phaseAt = ticks;
        echoAt = ticks;
    }

    private void step(int t) {
        if (ticks - START > RAID_MAX + ECHO_WAIT + 600) {
            print(false, "timed out in phase " + phase);
            finish();
            return;
        }
        if (!echoChecked && ticks - echoAt >= ECHO_WAIT) {             // the echo's own clock, whatever the raid is at
            echoChecked = true;
            step("echo check", new Runnable() {
                public void run() {
                    echoCheck();
                }
            });
        }
        if (phase == PH_STORM && t >= WARN + 5) {
            step("storm pulse", new Runnable() {
                public void run() {
                    stormPulse();
                }
            });
            step("raid start", new Runnable() {
                public void run() {
                    raidStart();
                }
            });
            phase = PH_RAID;
            phaseAt = ticks;
        } else if (phase == PH_RAID && ticks - lastPoll >= POLL) {
            lastPoll = ticks;
            boolean over;
            try {
                over = raidStep(t);
            } catch (Throwable e) {
                print(false, "raid - exception " + e);
                e.printStackTrace();
                over = true;
            }
            if (over) {
                step("raid end", new Runnable() {
                    public void run() {
                        raidEnd();
                    }
                });
                step("raid called off", new Runnable() {
                    public void run() {
                        raidCalledOff();
                    }
                });
                phase = PH_ECHO;
                phaseAt = ticks;
            }
        } else if (phase == PH_ECHO && echoChecked) {
            finish();
        }
    }

    /** One step by itself: an exception fails it alone. */
    private void step(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            print(false, what + " - exception " + t);
            t.printStackTrace();
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        try {
            if (w != null) {
                EmpStormSC.stopNow(w);
            }
        } catch (Throwable t) {
            // S's body: the storm data cleared below anyway
        }
        if (w != null) {
            StormDataSC d = StormDataSC.get(w);
            if (d != null && d.forced) {
                d.clearStorm();
            }
            for (int[] p : placed) {
                w.setBlockToAir(p[0], p[1], p[2]);
            }
            if (raided != null && savedRaid != null) {
                raided.extra.setTag(RaidSC.EXTRA, savedRaid);
                reg.markDirty();
            }
        }
        for (Entity e : madeHere) {
            if (e != null && !e.isDead) {
                e.setDead();
            }
        }
        for (ForgeChunkManager.Ticket t : tickets) {
            ForgeChunkManager.releaseTicket(t);
        }
        tickets.clear();
        placed.clear();
        madeHere.clear();
        for (FakePlayer p : new FakePlayer[]{sky, roof}) {
            if (p != null) {
                clear(p);
            }
        }
        w = null;
        w0 = null;
        finished = true;
        System.out.println("[SC-WORLDTEST] DONE hazard");
    }

    /** The test's own place: far out on the plateaus, no settlement within 128, its chunks forced. */
    private void base() {
        bx = 31 * SubstrateCellsSC.CELL + 128;
        bz = -29 * SubstrateCellsSC.CELL + 128;
        for (int k = 0; k < 8 && reg.within(w.provider.dimensionId, bx, bz, 128) != null; k++) {
            bx += 2 * SubstrateCellsSC.CELL;
        }
        force(w, bx >> 4, bz >> 4, 2);
        by = ground(w, bx, bz);
    }

    // ------------------------------------------------------------------ 1. the background radiation (ПА-1..ПА-3)

    private void radiation() {
        int[] oxide = decidedCell(BiomeSubstrateSC.OXIDE), rift = decidedCell(BiomeSubstrateSC.RIFT);
        FakePlayer p = FakePlayerFactory.get(w, RAD);
        if (oxide == null || rift == null) {
            print(false, "radiation (ПА-1): no decided Oxide Fields / Rift cell within 24 cells of the test's place (" + (oxide != null) + " / " + (rift != null) + ")");
            return;
        }
        float lvOxide = levelOn(p, oxide), lvRift = levelOn(p, rift);
        boolean biomes = biomeAt(oxide) == BiomeSubstrateSC.OXIDE && biomeAt(rift) == BiomeSubstrateSC.RIFT;
        // the Emission: the Rift's background x3 (its window set by hand, put back)
        StormDataSC d = StormDataSC.get(w);
        long s0 = d.emissionStartAt, e0 = d.emissionEndAt, now = w.getWorldTime();
        d.emissionStartAt = now - 1;
        d.emissionEndAt = now + 200;
        float lvEmission;
        boolean on;
        try {
            on = EmpStormSC.emission(w);
            lvEmission = levelOn(p, rift);
        } finally {
            d.emissionStartAt = s0;
            d.emissionEndAt = e0;
            d.markDirty();
        }
        float bg = SubstrateRadiationSC.background(p);
        SettlementRegistrySC.Settlement first = reg.first(w.provider.dimensionId);
        if (first != null) {
            force(w, first.x >> 4, first.z >> 4, 0);                        // its core ticks before the dome's check (raidStart)
        }
        boolean ok = biomes && lvOxide >= SubstrateRadiationSC.OXIDE - 0.01F && lvRift >= SubstrateRadiationSC.RIFT - 0.01F && on
                && lvEmission >= SubstrateRadiationSC.RIFT * StormRulesSC.EMISSION_RADIATION_MUL - 0.01F;
        print(ok, "radiation (ПА-1, ПА-2, ПФ-10): the chunks' own biomes Oxide / Rift " + biomes + "; levelAt on the Oxide Fields " + n(lvOxide) + " (>= 1), in"
                + " the Rift " + n(lvRift) + " (>= 2), with the Emission's window " + on + " " + n(lvEmission) + " (>= 6); after it the background " + n(bg));
    }

    /** ПА-3: a settlement's dome takes the radiation for free (its core has F_RADIATION) - its core loaded and ticked by now. */
    private void domeRadiation() {
        FakePlayer p = FakePlayerFactory.get(w, RAD);
        String dome = "no settlement core loaded";
        boolean ok = false;
        for (SettlementRegistrySC.Settlement s : reg.all()) {
            if (s.dim == w.provider.dimensionId && w.blockExists(s.x, s.y, s.z) && w.getTileEntity(s.x, s.y, s.z) instanceof TileEntityFieldGeneratorSC) {
                TileEntityFieldGeneratorSC core = (TileEntityFieldGeneratorSC) w.getTileEntity(s.x, s.y, s.z);
                p.setLocationAndAngles(s.x + 0.5, s.floorY + 1, s.z + 3.5, 0F, 0F);
                ok = core.has(TileEntityFieldGeneratorSC.F_RADIATION) && TileEntityFieldGeneratorSC.payRadiationAt(w, p, p.posX, p.posY + 1, p.posZ, 1000);
                dome = "settlement " + s.id + ": F_RADIATION " + core.has(TileEntityFieldGeneratorSC.F_RADIATION) + ", pays " + ok;
                break;
            }
        }
        print(ok, "radiation in a dome (ПА-3): " + dome);
    }

    /** A decided (not LEGACY) cell of `kind` nearest the test's place, its middle chunk generated; {x, z} of its middle, or null. */
    private int[] decidedCell(int kind) {
        int c0x = bx >> 8, c0z = bz >> 8;
        for (int r = 0; r <= 24; r++) {
            for (int cx = c0x - r; cx <= c0x + r; cx++) {
                for (int cz = c0z - r; cz <= c0z + r; cz++) {
                    if (Math.max(Math.abs(cx - c0x), Math.abs(cz - c0z)) != r || SubstrateCellsSC.pureKind(w.getSeed(), cx, cz) != kind) {
                        continue;
                    }
                    if (SubstrateCellsSC.cellKind(w, cx, cz) == kind && !SubstrateCellsSC.legacy(w, cx, cz)) {
                        int x = cx * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2, z = cz * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2;
                        force(w, x >> 4, z >> 4, 0);
                        w.getChunkFromChunkCoords(x >> 4, z >> 4);
                        return new int[]{x + 3, z + 3};                // off the Rift's pillar (radius 2)
                    }
                }
            }
        }
        return null;
    }

    private int biomeAt(int[] xz) {
        Chunk c = w.getChunkFromBlockCoords(xz[0], xz[1]);
        int id = c.getBiomeArray()[(xz[1] & 15) << 4 | xz[0] & 15] & 255;
        for (int k = 0; k < BiomeSubstrateSC.KINDS; k++) {
            if (SubstrateSC.biome(k) != null && SubstrateSC.biome(k).biomeID == id && SubstrateSC.biome(k).kind == k) {
                return k;
            }
        }
        return -1;
    }

    private float levelOn(FakePlayer p, int[] xz) {
        p.setLocationAndAngles(xz[0] + 0.5, Math.max(ground(w, xz[0], xz[1]), 5) + 1, xz[1] + 0.5, 0F, 0F);
        return RadiationSC.levelAt(p);
    }

    // ------------------------------------------------------------------ 2. the haze and the Emission (ПФ-9, ПФ-10)

    private void hazeAndEmission() {
        StormDataSC d = StormDataSC.get(w);
        long s0 = d.hazeStartAt, e0 = d.hazeEndAt, now = w.getWorldTime();
        boolean before = EmpStormSC.haze(w);
        d.hazeStartAt = now - 1;
        d.hazeEndAt = now + 200;
        boolean on;
        double sight;
        try {
            on = EmpStormSC.haze(w);
            sight = EmpStormSC.sightRange(w, 64D);
        } finally {
            d.hazeStartAt = s0;
            d.hazeEndAt = e0;
            d.markDirty();
        }
        boolean after = EmpStormSC.haze(w) == before;
        print(on && sight <= StormRulesSC.HAZE_SIGHT && after && EmpStormSC.sightRange(w, 64D) == (before ? StormRulesSC.HAZE_SIGHT : 64D),
                "haze (ПФ-9): its window set - EmpStormSC.haze " + on + ", a mob's sight " + n((float) sight) + " (<= " + StormRulesSC.HAZE_SIGHT + "); put back "
                + after);
    }

    // ------------------------------------------------------------------ 3. the Crumbs' daily cap (ПТ-17)

    private void crumbs() {
        FakePlayer fake = FakePlayerFactory.get(w, profile("crumbs-fake"));
        boolean fakeNone = CrumbLimitSC.take(fake, 1) == 0 && CrumbLimitSC.left(fake) == 0;
        WorldTestArmourerSC.StationPlayer p = new WorldTestArmourerSC.StationPlayer(w, CRUMBS);
        p.setLocationAndAngles(bx + 0.5, by, bz + 0.5, 0F, 0F);
        SettlementRegistrySC.PlayerRec rec = reg.player(p.getUniqueID());
        rec.extra.removeTag(CrumbLimitSC.EXTRA);
        int a = CrumbLimitSC.take(p, 1), b = CrumbLimitSC.take(p, 1), c = CrumbLimitSC.take(p, 2), e = CrumbLimitSC.take(p, 1), left = CrumbLimitSC.left(p);
        boolean cap = a == 1 && b == 1 && c == 1 && e == 0 && left == 0;
        // a Sealed Crumb: opens into a Crumb within the cap, stays sealed past it (package C's ItemRelicSC)
        rec.extra.removeTag(CrumbLimitSC.EXTRA);
        String sealed = "relicSC not registered";
        if (RelicItemsSC.relic != null && ModItems.singularCrumb != null) {
            boolean opened = useSealed(p), held = false;
            CrumbLimitSC.take(p, CrumbLimitSC.DAY_CAP);              // today's cap reached
            held = !useSealed(p);
            sealed = opened + " / past the cap stays sealed " + held + " (" + CrumbLimitSC.left(p) + " left)";
            cap &= opened && held;
        }
        rec.extra.removeTag(CrumbLimitSC.EXTRA);
        reg.markDirty();
        print(fakeNone && cap, "crumbs (CrumbLimitSC, ПТ-17): a FakePlayer none " + fakeNone + "; a real player 1 + 1 + 1 of 1 / 1 / 2 asked, then " + e + ", "
                + left + " left (" + CrumbLimitSC.DAY_CAP + " a day) " + (a == 1 && b == 1 && c == 1 && e == 0 && left == 0) + "; a Sealed Crumb opens " + sealed);
    }

    /** Right-click a Sealed Crumb in `p`'s hand; @return it became a Singularity Crumb. */
    private boolean useSealed(EntityPlayer p) {
        ItemStack s = RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1);
        p.inventory.currentItem = 0;
        p.inventory.mainInventory[0] = s;
        ItemStack out = s.getItem().onItemRightClick(s, w, p);
        ItemStack hand = p.inventory.mainInventory[0];
        boolean crumb = out != null && out.getItem() == ModItems.singularCrumb || hand != null && hand.getItem() == ModItems.singularCrumb;
        for (ItemStack st : p.inventory.mainInventory) {
            crumb |= st != null && st != s && st.getItem() == ModItems.singularCrumb;
        }
        clear(p);
        return crumb;
    }

    // ------------------------------------------------------------------ 4. the EMP storm (ПФ-1..ПФ-7)

    private void stormStart() {
        EmpStormSC.stopNow(w);
        EmpStormSC.startNow(w, WARN, STORM);
        int ph = EmpStormSC.phase(w);
        print(ph == StormStateSC.WARNING && EmpStormSC.warning(w), "storm start (ПФ-1, ПФ-4): startNow(" + WARN + ", " + STORM + ") - the phase " + ph
                + " (WARNING " + StormStateSC.WARNING + ")");
    }

    private void stormPulse() {
        int ph = EmpStormSC.phase(w);
        boolean active = EmpStormSC.active(w) && ph == StormStateSC.STORM, solar = EmpStormSC.blocksSolar(w, bx, by, bz);
        // the actors: under the sky / under three blocks (a Nano chestplate each), a dressed Lineman, an Exo Shell, a dome warden
        sky = FakePlayerFactory.get(w, SKY);
        roof = FakePlayerFactory.get(w, ROOF);
        dressOne(sky, bx, by, bz);
        dressOne(roof, bx + 6, by, bz);
        for (int dy = 2; dy <= 4; dy++) {
            place(bx + 6, by + dy, bz, Blocks.stonebrick);
        }
        lineman = new EntityLinemanSC(w);
        lineman.setLocationAndAngles(bx - 4.5, by, bz + 0.5, 0F, 0F);
        WorldTestCombatSC.dress(lineman, ArmorSuit.NANO, false, 20F);
        shell = new EntityHollowShellSC(w);
        shell.setLocationAndAngles(bx + 0.5, by, bz - 5.5, 0F, 0F);
        WorldTestCombatSC.dress(shell, ArmorSuit.EXO, true, 60F);
        warden = (EntityWardenSC) SettlementSC.newMember(w, NpcKindSC.SENTRY);
        warden.setLocationAndAngles(bx + 0.5, by, bz + 5.5, 0F, 0F);
        for (int t = 0; t < 4; t++) {
            warden.setCurrentItemOrArmor(4 - t, new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[t]));
        }
        boolean coverSky = EmpStormSC.coveredFrom(w, sky), coverRoof = EmpStormSC.coveredFrom(w, roof);
        long now = w.getTotalWorldTime();
        int hit = pulse();
        long skyLeft = maxLeft(sky, now), roofLeft = maxLeft(roof, now);
        boolean mob = lineman.mobArmor().anySuppressed(), reboot = shell.rebooting(), spared = !warden.mobArmor().anySuppressed();
        boolean sup = skyLeft >= StormRulesSC.SUPPRESS_MIN && skyLeft <= StormRulesSC.SUPPRESS_MAX && roofLeft <= 0;
        // again at once after its end: immune (the usual immunity after a suppression)
        EmpStateSC s = EmpSC.state(sky);
        for (int i = 0; i < 4; i++) {
            if (s.until[i] > now) {
                s.until[i] = w.getTotalWorldTime();
            }
        }
        EmpSC.save(sky, s);
        pulse();
        boolean immune = maxLeft(sky, w.getTotalWorldTime()) <= 0;
        stormChance = MobDropsSC.trophyChance(lineman, null);
        print(active && solar && hit > 0 && sup && !coverSky && coverRoof && mob && reboot && spared && immune, "storm pulse (ПФ-2, ПФ-3, ПФ-5, ПБ-21): the phase "
                + ph + " (STORM " + StormStateSC.STORM + ") " + active + ", the mod's panels blocked " + solar + "; a pulse suppressed " + hit + "; under the sky "
                + skyLeft + " ticks (" + StormRulesSC.SUPPRESS_MIN + ".." + StormRulesSC.SUPPRESS_MAX + "), under three blocks " + roofLeft + " (coveredFrom "
                + coverSky + " / " + coverRoof + ") " + sup + "; the Lineman's piece " + mob + ", the Shell reboots " + reboot + ", the warden spared " + spared
                + "; at once again - immune " + immune);
        // the trophies x1.5 (ПФ-7): the same Lineman's chance in the storm and after it
        EmpStormSC.stopNow(w);
        float calm = MobDropsSC.trophyChance(lineman, null);
        boolean x15 = calm > 0F && Math.abs(stormChance / calm - StormRulesSC.TROPHY_MUL) < 0.01F;
        boolean stopped = EmpStormSC.phase(w) == StormStateSC.CALM && !EmpStormSC.blocksSolar(w, bx, by, bz);
        print(x15 && stopped, "storm stop (ПФ-7): a Lineman's trophy chance " + n(stormChance * 100F) + "% in the storm, " + n(calm * 100F) + "% after (x"
                + n(StormRulesSC.TROPHY_MUL) + ") " + x15 + "; stopNow - CALM, the panels work " + stopped);
        clear(sky);
        clear(roof);
    }

    /** One EmpStormSC.pulse with the test players in playerEntities and the test mobs in loadedEntityList for this call only. */
    @SuppressWarnings("unchecked")
    private int pulse() {
        List<EntityPlayer> players = w.playerEntities;
        List<Entity> loaded = w.loadedEntityList;
        players.add(sky);
        players.add(roof);
        loaded.add(lineman);
        loaded.add(shell);
        loaded.add(warden);
        try {
            return EmpStormSC.pulse(w);
        } finally {
            players.remove(sky);
            players.remove(roof);
            loaded.remove(lineman);
            loaded.remove(shell);
            loaded.remove(warden);
        }
    }

    private void dressOne(FakePlayer p, int x, int y, int z) {
        clear(p);
        resetEmp(p);
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        ItemStack chest = new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[1]);
        ItemArmorSC.setCharge(chest, ItemArmorSC.capacityOf(chest));
        p.inventory.armorInventory[2] = chest;
    }

    /** The longest suppression still running on `p` (ticks after `now`). */
    private static long maxLeft(EntityPlayer p, long now) {
        EmpStateSC s = EmpSC.state(p);
        long m = 0;
        for (int i = 0; i < 4; i++) {
            m = Math.max(m, s.until[i] - now);
        }
        return m;
    }

    // ------------------------------------------------------------------ 5. a raid (ПН-1..ПН-6)

    private void raidStart() {
        step("dome radiation", new Runnable() {
            public void run() {
                domeRadiation();
            }
        });
        raided = reg.first(w.provider.dimensionId);                   // «Первый причал», else any settlement of the Substrate
        for (SettlementRegistrySC.Settlement s : reg.all()) {
            if (raided == null && s.dim == w.provider.dimensionId) {
                raided = s;
            }
        }
        if (raided == null) {
            print(false, "raid (ПН-1): no settlement in the registry to raid");
            return;
        }
        int r = raided.radius + RaidRulesSC.SPAWN_OUT_MAX + 16;
        force(w, raided.x >> 4, raided.z >> 4, (r >> 4) + 1);
        savedRaid = (NBTTagCompound) raided.extra.getCompoundTag(RaidSC.EXTRA).copy();
        raided.extra.removeTag(RaidSC.EXTRA);
        defender = FakePlayerFactory.get(w, DEFENDER);                 // the world test's `by`: present by itself (it is in no player list)
        clear(defender);
        defender.setHealth(defender.getMaxHealth());
        defender.setLocationAndAngles(raided.x + 0.5, raided.floorY + 1, raided.z + raided.radius - 4 + 0.5, 0F, 0F);
        SettlementRegistrySC.PlayerRec pr = reg.player(defender.getUniqueID());
        pr.reputation = 150;
        pr.rollDay(SettlementSC.day(w));
        repBefore = pr.reputation;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityMarauderSC) {
                marauderIdsBefore.add(((Entity) o).getEntityId());
            }
        }
        raidBegan = RaidSC.startNow(w, raided, defender);
        raidAt = ticks;
        print(raidBegan && RaidSC.running(raided.id), "raid start (ПН-1): startNow on settlement " + raided.id + " (" + raided.x + ", " + raided.z + ", size "
                + raided.size + ") with a defender of Trust 150 - began " + raidBegan + ", running " + RaidSC.running(raided.id));
    }

    /** @return the raid is over (won, lost, or never began) */
    private boolean raidStep(int t) {
        if (!raidBegan || raided == null) {
            return true;
        }
        List<EntityLivingBase> now = new ArrayList<EntityLivingBase>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityLivingBase && !((Entity) o).isDead && ((Entity) o).getEntityData().hasKey(RaidSC.RAIDER_NBT)
                    && ((Entity) o).getEntityData().getInteger(RaidSC.RAIDER_NBT) == raided.id) {
                now.add((EntityLivingBase) o);
            }
        }
        boolean fresh = false;
        for (EntityLivingBase e : now) {
            fresh |= raiders.add(e.getEntityId());
        }
        if (fresh) {
            // a wave came: born in Si-14, outside the dome on the gate's side, its size by RaidRulesSC.wave
            int born = 0, outside = 0;
            for (EntityLivingBase e : now) {
                born += BornInSubstrateSC.isBorn(e) ? 1 : 0;
                outside += raided.distSq(e.posX, e.posZ) > (double) raided.radius * raided.radius ? 1 : 0;
            }
            int want = 0;
            for (int[] g : RaidRulesSC.wave(Math.min(waves, RaidRulesSC.WAVES - 1), raided.size, new java.util.Random(waves))) {
                want += g[1];
            }
            boolean ok = born == now.size() && outside == now.size() && now.size() >= Math.max(1, want - 2);
            raidWavesOk &= ok;
            raidLog.append(" wave ").append(waves + 1).append(": ").append(now.size()).append(" (").append(want).append(") born ").append(born)
                    .append(" outside ").append(outside).append(ok ? "" : " BAD");
            waves++;
            for (EntityLivingBase e : now) {
                e.setHealth(0F);                                     // a death whatever a shield would take
                e.onDeath(DamageSource.outOfWorld);
                e.setDead();
            }
        }
        return !RaidSC.running(raided.id) || ticks - raidAt > RAID_MAX;
    }

    private void raidEnd() {
        if (raided == null) {
            return;
        }
        boolean over = !RaidSC.running(raided.id);
        if (raidBegan) {
            SettlementRegistrySC.PlayerRec pr = reg.player(defender.getUniqueID());
            int gain = pr.reputation - repBefore, chips = 0;
            ItemStack one = SettlementItemsSC.chips(1);
            for (ItemStack s : defender.inventory.mainInventory) {
                chips += s != null && one != null && s.getItem() == one.getItem() ? s.stackSize : 0;
            }
            int wantRep = RaidRulesSC.rep(raided.size), wantChips = RaidRulesSC.chips(raided.size);
            print(over && waves == RaidRulesSC.WAVES && raidWavesOk && gain == wantRep && chips == wantChips, "raid (ПН-2, ПН-3, ПН-6): over " + over + " after "
                    + (ticks - raidAt) + " ticks, " + waves + " waves (" + RaidRulesSC.WAVES + ")" + raidLog + "; won - Trust +" + gain + " (" + wantRep + "), "
                    + chips + " chips in the inventory (" + wantChips + ")");
            clear(defender);
        }
        // the lost raid's closed trade (ПН-4, ПО-16): TradeServerSC refuses R_RAID while extra "R".closed is ahead
        NBTTagCompound r = new NBTTagCompound();
        r.setLong(RaidSC.CLOSED, Math.max(w.getTotalWorldTime(), w.getWorldTime()) + RaidRulesSC.CLOSED_TICKS);
        raided.extra.setTag(RaidSC.EXTRA, r);
        boolean closed = RaidSC.tradeClosed(w, raided);
        String refused = "no Tuner slot";
        for (int i = 0; i < raided.slots.size(); i++) {
            NpcKindSC kind = raided.slots.get(i).kindOf();
            List<TradeOfferSC> offers = kind == null || kind.guard ? null : TradeTableSC.offers(kind);
            if (offers == null || offers.isEmpty()) {
                continue;
            }
            EntityCreature c = SettlementSC.newMember(w, kind);
            if (!(c instanceof EntityTunerSC)) {
                continue;
            }
            ((ISettlementMemberSC) c).bindTo(raided, i);
            c.setLocationAndAngles(raided.x + 0.5, raided.floorY + 1, raided.z + 0.5, 0F, 0F);
            FakePlayer trader = FakePlayerFactory.get(w, TRADER);
            trader.setLocationAndAngles(c.posX + 1, c.posY, c.posZ, 0F, 0F);
            int code = TradeServerSC.execute(trader, (EntityTunerSC) c, offers.get(0).key, 1);
            refused = code + " (" + (code >= 0 && code < TradeServerSC.RESULT_KEYS.length ? TradeServerSC.RESULT_KEYS[code] : "?") + ")";
            closed &= code == resultCode("raid");
            break;
        }
        raided.extra.setTag(RaidSC.EXTRA, savedRaid);
        reg.markDirty();
        print(closed, "raid lost (ПН-4, ПО-16): extra \"" + RaidSC.EXTRA + "\"." + RaidSC.CLOSED + " a day ahead - tradeClosed, the exchange refuses " + refused
                + " (raid)");
        // the raiders that stayed: removed
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof Entity && ((Entity) o).getEntityData().hasKey(RaidSC.RAIDER_NBT) && ((Entity) o).getEntityData().getInteger(RaidSC.RAIDER_NBT) == raided.id) {
                ((Entity) o).setDead();
            }
        }
        reg.player(defender.getUniqueID()).reputation = 0;
    }

    /**
     * The review's: on Peaceful no raid begins (its mobs would remove themselves - a free win); a wave removed alive (setDead, no
     * death: a cleaner mod, Peaceful) is no kill - the raid is called off, no reward, the trade open.
     */
    private void raidCalledOff() {
        if (raided == null || defender == null || !raidBegan) {
            return;
        }
        NBTTagCompound kept = (NBTTagCompound) raided.extra.getCompoundTag(RaidSC.EXTRA).copy();
        raided.extra.removeTag(RaidSC.EXTRA);
        SettlementRegistrySC.PlayerRec pr = reg.player(defender.getUniqueID());
        pr.reputation = 150;
        defender.setHealth(defender.getMaxHealth());
        EnumDifficulty was = w.difficultySetting;
        boolean none;
        w.difficultySetting = EnumDifficulty.PEACEFUL;
        try {
            none = !RaidSC.startNow(w, raided, defender) && !RaidSC.running(raided.id);
        } finally {
            w.difficultySetting = was;
        }
        boolean began = RaidSC.startNow(w, raided, defender);
        int removed = 0;
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof EntityLivingBase && !((Entity) o).isDead && ((Entity) o).getEntityData().hasKey(RaidSC.RAIDER_NBT)
                    && ((Entity) o).getEntityData().getInteger(RaidSC.RAIDER_NBT) == raided.id) {
                ((Entity) o).setDead();                                  // removed alive: its health left
                removed++;
            }
        }
        boolean over = began && RaidSC.stepNow(w, raided.id) && !RaidSC.running(raided.id);
        int trust = pr.reputation;
        boolean calm = trust == 150 && !RaidSC.tradeClosed(w, raided);
        if (RaidSC.running(raided.id)) {
            RaidSC.clearAll();                                           // never left running by the test
        }
        raided.extra.setTag(RaidSC.EXTRA, kept);
        reg.markDirty();
        pr.reputation = 0;
        clear(defender);
        print(none && began && removed > 0 && over && calm, "raid called off (ПН-1): on Peaceful no raid begins " + none + "; a wave of " + removed
                + " removed alive (setDead) - the raid over at the next step " + over + ", no reward and the trade open " + calm + " (Trust " + trust + ")");
    }

    /** TradeServerSC's result code of RESULT_KEYS key `key` (package C appends "raid", "crumbs"), -1 none. */
    private static int resultCode(String key) {
        for (int i = 0; i < TradeServerSC.RESULT_KEYS.length; i++) {
            if (key.equals(TradeServerSC.RESULT_KEYS[i])) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ 6. the bridge's echo (ПН-9..ПН-11)

    /** BridgeEchoSC.forceRoll(Float) (package S's test hook, by name): @return it was there */
    private static boolean forceRoll(Float roll) {
        try {
            BridgeEchoSC.class.getMethod("forceRoll", Float.class).invoke(null, roll);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void echoStart() {
        boolean forced = forceRoll(Float.valueOf(0F));
        try {
            echoCross(forced);
        } finally {
            forceRoll(null);
        }
    }

    private void echoCross(boolean forced) {
        // home: out of the Substrate into the Overworld without the Shield - until the 10% roll hits
        hx = 3000.5;
        hz = -3000.5;
        force(w0, (int) Math.floor(hx) >> 4, (int) Math.floor(hz) >> 4, 1);
        hy = 200;
        WorldTestArmourerSC.StationPlayer home = new WorldTestArmourerSC.StationPlayer(w0, HOME);
        home.setLocationAndAngles(hx, hy, hz, 0F, 0F);
        Set<Integer> before = nanitesNear(w0, hx, hy, hz);
        int calls = 0;
        for (; calls < (forced ? 1 : ECHO_TRIES) && homeParticles <= 0; calls++) {
            BridgeEchoSC.crossed(home, SubstrateSC.dimId(), 0, hx, hy, hz, false);
            Set<Integer> got = nanitesNear(w0, hx, hy, hz);
            got.removeAll(before);
            homeParticles = got.isEmpty() ? 0 : got.size();
            nanitesSeen.addAll(got);
        }
        echoHomeHit = homeParticles > 0;
        // with the Shield: never
        Set<Integer> before2 = nanitesNear(w0, hx, hy, hz);
        for (int i = 0; i < (forced ? 3 : ECHO_TRIES); i++) {
            BridgeEchoSC.crossed(home, SubstrateSC.dimId(), 0, hx, hy, hz, true);
        }
        Set<Integer> got2 = nanitesNear(w0, hx, hy, hz);
        got2.removeAll(before2);
        shieldParticles = got2.size();
        print(echoHomeHit && homeParticles >= BridgeEchoSC.PARTICLES_MIN && homeParticles <= BridgeEchoSC.PARTICLES_MAX && shieldParticles == 0, "echo home (ПН-9):"
                + " without the Shield " + homeParticles + " particles after " + calls + " crossing(s) (" + BridgeEchoSC.PARTICLES_MIN + ".." + BridgeEchoSC.PARTICLES_MAX
                + (forced ? ", the roll forced 0" : ", a 10% roll") + "), with the Shield " + shieldParticles + " (0); they fade in " + (BridgeEchoSC.ECHO_LIFE_TICKS / 20) + " s - checked later");
        // into the Substrate: the squad comes SQUAD_DELAY later near the arrival (not at a dock, not in a dome)
        ax = bx + 64.5;
        az = bz + 0.5;
        force(w, (int) Math.floor(ax) >> 4, (int) Math.floor(az) >> 4, 3);
        ay = ground(w, (int) Math.floor(ax), (int) Math.floor(az));
        for (int i = 0; i < (forced ? 1 : 31) && !arrivalQueued; i++) {
            WorldTestArmourerSC.StationPlayer arrive = new WorldTestArmourerSC.StationPlayer(w, profile("arrive-" + i));
            arrive.setLocationAndAngles(ax, ay, az, 0F, 0F);
            BridgeEchoSC.crossed(arrive, 0, SubstrateSC.dimId(), ax, ay, az, false);
            arrivalQueued = forced || i >= 30;                       // unforced: ~96% that one of 31 rolls hit (no player has a cooldown yet)
        }
        // at a dock: never (the same tries)
        SettlementRegistrySC.Settlement dock = null;
        for (SettlementRegistrySC.Settlement s : reg.all()) {
            if (s.dim == w.provider.dimensionId && s.hasDock) {
                dock = s;
                break;
            }
        }
        if (dock != null) {
            dx = dock.dockX + 0.5;
            dy = dock.dockY + 1;
            dz = dock.dockZ + 0.5;
            force(w, dock.dockX >> 4, dock.dockZ >> 4, 3);
            for (int i = 0; i < (forced ? 3 : 30); i++) {
                WorldTestArmourerSC.StationPlayer docked = new WorldTestArmourerSC.StationPlayer(w, profile("docked-" + i));
                docked.setLocationAndAngles(dx, dy, dz, 0F, 0F);
                BridgeEchoSC.crossed(docked, 0, SubstrateSC.dimId(), dx, dy, dz, false);
            }
            dockTried = true;
        }
    }

    private void echoCheck() {
        Set<Integer> left = nanitesNear(w0, hx, hy, hz);
        left.retainAll(nanitesSeen);
        int squad = newMaraudersNear(ax, ay, az), atDock = dockTried ? newMaraudersNear(dx, dy, dz) : -1;
        print(echoHomeHit && left.isEmpty(), "echo home (ПН-9): " + homeParticles + " particles, " + left.size() + " left after " + (ECHO_WAIT / 20) + " s (0)");
        print(squad >= BridgeEchoSC.SQUAD_MIN && (atDock <= 0), "echo arrival (ПН-10, ПН-11): " + squad + " new Marauders within " + (BridgeEchoSC.SQUAD_DIST_MAX + 8)
                + " of the arrival after " + (ECHO_WAIT / 20) + " s (" + BridgeEchoSC.SQUAD_MIN + "+ from 30 arrivals), at a dock " + (dockTried ? atDock + " (0)"
                : "- no dock in the registry"));
        // the echo's creatures: removed
        for (Object o : new ArrayList<Object>(w0.loadedEntityList)) {
            if (o instanceof EntityNaniteSC && nanitesSeen.contains(((Entity) o).getEntityId())) {
                ((Entity) o).setDead();
            }
        }
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof EntityMarauderSC && !marauderIdsBefore.contains(((Entity) o).getEntityId())
                    && (((Entity) o).getDistanceSq(ax, ay, az) < 48 * 48 || dockTried && ((Entity) o).getDistanceSq(dx, dy, dz) < 48 * 48)) {
                ((Entity) o).setDead();
            }
        }
    }

    private static Set<Integer> nanitesNear(World world, double x, double y, double z) {
        Set<Integer> out = new HashSet<Integer>();
        for (Object o : world.loadedEntityList) {
            if (o instanceof EntityNaniteSC && !((Entity) o).isDead && ((Entity) o).getDistanceSq(x, y, z) < 24 * 24) {
                out.add(((Entity) o).getEntityId());
            }
        }
        return out;
    }

    private int newMaraudersNear(double x, double y, double z) {
        int n = 0;
        double r = BridgeEchoSC.SQUAD_DIST_MAX + 8;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityMarauderSC && !((Entity) o).isDead && !marauderIdsBefore.contains(((Entity) o).getEntityId())
                    && !((Entity) o).getEntityData().hasKey(RaidSC.RAIDER_NBT) && ((Entity) o).getDistanceSq(x, y, z) < r * r) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    private void force(WorldServer world, int cx, int cz, int r) {
        ForgeChunkManager.Ticket t = null;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (t == null || t.getChunkList().size() >= t.getChunkListDepth()) {
                    t = ForgeChunkManager.requestTicket(SCMod.instance, world, ForgeChunkManager.Type.NORMAL);
                    if (t == null) {
                        return;
                    }
                    t.getModData().setString("Kind", "worldtest");          // ChunkLoaderSC releases it on a later load
                    tickets.add(t);
                }
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(x, z));
                world.theChunkProviderServer.loadChunk(x, z);                 // now, not on the next tick
            }
        }
    }

    /** The top solid block's Y + 1 at (x, z) (its chunk loaded here). */
    private static int ground(World world, int x, int z) {
        world.getChunkFromBlockCoords(x, z);
        return world.getTopSolidOrLiquidBlock(x, z);
    }

    private void place(int x, int y, int z, Block b) {
        if (w.isAirBlock(x, y, z)) {
            w.setBlock(x, y, z, b, 0, 2);
            placed.add(new int[]{x, y, z});
        }
    }

    private static void clear(EntityPlayer p) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            p.inventory.mainInventory[i] = null;
        }
        for (int i = 0; i < 4; i++) {
            p.inventory.armorInventory[i] = null;
        }
    }

    /** FakePlayers are cached across runs: a clean ПЭ-1 state. */
    private static void resetEmp(EntityPlayer p) {
        EmpStateSC s = EmpSC.state(p);
        for (int i = 0; i < 4; i++) {
            s.until[i] = 0;
            s.immuneUntil[i] = 0;
        }
        s.boilMul = 0;
        s.boilUntil = 0;
        EmpSC.save(p, s);
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("sc-hazard-" + name).getBytes()), "[SCTest" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "]");
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " hazard: " + what);
    }

    private static String n(float v) {
        return String.valueOf(Math.round(v * 100F) / 100F);
    }

    /** A public static field of `c` by name (another package's), null none. */
    static Object staticField(Class<?> c, String name) {
        try {
            Field f = c.getField(name);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
