package com.sc.debug;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpSC;
import com.sc.emp.EmpStateSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModItems;
import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.mob.BreakdownDamageSC;
import com.sc.mob.EliteModSC;
import com.sc.mob.EliteSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityJammerSC;
import com.sc.mob.EntityMimicSC;
import com.sc.mob.EntityNaniteSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.EntitySentryDroneSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.ICensusSC;
import com.sc.mob.IDisguisedSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.ruin.ComponentRuinSC;
import com.sc.ruin.MapGenDeadNodeSC;
import com.sc.ruin.MapGenLithographSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.WardenSuitSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.WeaponType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.ExplosionEvent;

/**
 * PACKAGE D (docs/plan-substrate-tech-f3.md §11.2): Ф3's behaviour on a real server (-Dsc.worldtest=true), after the Ф1 and
 * Ф2 world tests: «Пробой» through a FakePlayer's full Exo set and a mob's shield (and nothing else through them), the mobs
 * (registration, dressing, reserves, damage caps), ПЭ-1 in a mob's hands (a Point on a FakePlayer in the open and behind
 * cover, immunity, the field's screen; the Commandant's Pulse on a Shell), the personal screen, the reactive targets
 * (drone, swarm, mimic), the elites, the spawn director's caps, the ruins' generation (no cascade, no Blocks.stone, the
 * explored ground empty). Output "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO combat: ...".
 * <ol>
 * <li>registration: the eight new entity names, the ruins' MapGenStructureIO names, ruinSC and showcase2SC;</li>
 * <li>«Пробой» (ПБ-15, ПБ-16): ArmorLogicSC.exoStops lets it through a FakePlayer's full Exo set with helium and the shield
 * on - and still stops a mob's blow, generic, magic, fire and an explosion; 5 of it really come off the player's health; a
 * Hollow Shell's shield lets 6 of it into the health (the reserve untouched) and takes an ordinary 6;</li>
 * <li>the mobs (packages M, X): each of the eight dressed by onSpawnWithEgg - its suit, reserve, weapon; a blow at a
 * FakePlayer within its cap;</li>
 * <li>ПЭ-1 in a mob's hands (package A): a Jammer carrier's Point on a FakePlayer in Nano - the chestplate suppressed for its
 * time; after the reboot (immune) a second does nothing; behind a wall no shot; a target inside a settlement's dome (its
 * core's EMP screen) - absorbed; dimension.mobsUseEmp off - allowed() false, no carrier in 100 spawns;</li>
 * <li>the personal screen (ПБ-19) through a FakePlayer's own ПЭ-1 (EmpGunLogicSC): the first Point screened, the second
 * suppresses, the screen up again after 30 s;</li>
 * <li>the reactive targets (ПБ-20): a Point downs both drones, a Pulse burns the swarm round the hit (not a particle 9
 * blocks off), a Point wakes a mimic and it stays put while stunned;</li>
 * <li>the Commandant (a warden of that rank) Pulses a Shell, and never aims at a FakePlayer;</li>
 * <li>the elites (package A): each modifier's change, the Repairer's recharge of a neighbour, the Commander's x1.2, the
 * Avenger's blast 30 ticks on that breaks no block, «Казнь» refused to an elite;</li>
 * <li>the spawn director's caps (package M, ПС-7): 200 tries at a Dead Node keep at most 2 of the Quantum tier within 64,
 * 200 in a canyon at most 1 elite within 128;</li>
 * <li>the ruins (package X): a fresh Dead Node and lithograph generated - their blocks, chests with loot, the lithograph's
 * residents, no Minecraft stone, not one chunk past the ring loaded (no cascade); a start on explored ground empty;</li>
 * <li>the Ф1 / Ф2 world tests run as before - their own lines in the log.</li>
 * </ol>
 * Built on the core alone (what packages add is asked for by name). Everything it flips is put back, every ticket released,
 * every creature it made removed; the FakePlayers are never spawned (only added to a chunk's list where a hitscan must find
 * them, as WorldTestEmpSC does).
 */
public final class WorldTestCombatSC {

    /** After the settlements' test: it starts at 2400 and is done within ~1000 ticks (WorldTestSettlementSC). */
    static final int START = 3600;
    /** The series of the spawn director (§11.2.9), one try a tick. */
    private static final int SERIES = 200;
    private static final int Y = 200;
    private static final String TROOPER = "com.sc.mob.EntityTrooperSC", JAMMER = "com.sc.mob.EntityJammerSC", SHELL = "com.sc.mob.EntityHollowShellSC",
            EXECUTIONER = "com.sc.mob.EntityExecutionerSC";
    private static final Class<?>[] MOBS = {EntityTrooperSC.class, EntityJammerSC.class, EntityHollowShellSC.class, EntityRepairDroneSC.class,
            EntityExecutionerSC.class, EntityNaniteSC.class, EntitySentryDroneSC.class, EntityMimicSC.class};

    private int ticks;
    private boolean done, started;
    private WorldServer w;
    private long seed;
    /** The test's area: the middle of a free plateau cell (in the air at Y for the creatures never spawned, on the ground for the others). */
    private int ax, az;
    private final List<Step> steps = new ArrayList<Step>();
    private final List<Entity> made = new ArrayList<Entity>();
    private final List<FakePlayer> fakes = new ArrayList<FakePlayer>();
    private final List<int[]> placed = new ArrayList<int[]>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private boolean savedMobsUseEmp, saved;
    /** The blows a creature tried on `victim` (LivingAttackEvent), the explosions that started (ExplosionEvent.Start). */
    private EntityLivingBase victim;
    private float struck;
    private final List<double[]> blasts = new ArrayList<double[]>();

    /** One check of the schedule: run `at` ticks after the start. */
    private abstract static class Step {
        final int at;
        final String name;

        Step(int at, String name) {
            this.at = at;
            this.name = name;
        }

        abstract void run();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        if (ticks < START) {
            return;
        }
        int t = ticks - START;
        if (!started) {
            started = true;
            try {
                start();
            } catch (Throwable e) {
                print(false, "exception at the start " + e);
                e.printStackTrace();
                finish();
                return;
            }
        }
        int last = 0;
        for (Step s : new ArrayList<Step>(steps)) {
            last = Math.max(last, s.at);
            if (s.at == t) {
                try {
                    s.run();
                } catch (Throwable e) {
                    print(false, s.name + " - exception " + e);
                    e.printStackTrace();
                }
            }
        }
        if (t >= last && !done) {
            finish();
        }
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (victim != null && e.entityLiving == victim) {
            struck = Math.max(struck, e.ammount);
        }
    }

    @SubscribeEvent
    public void onBlast(ExplosionEvent.Start e) {
        if (w != null && e.world == w) {
            blasts.add(new double[]{e.explosion.explosionX, e.explosion.explosionY, e.explosion.explosionZ, w.getTotalWorldTime()});
        }
    }

    // ------------------------------------------------------------------ the schedule

    private void start() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the dimension is off (" + SubstrateSC.offReason() + ")");
            done = true;
            return;
        }
        w = MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId());
        if (w == null) {
            print(false, "the world: worldServerForDimension(" + SubstrateSC.dimId() + ") is null");
            done = true;
            return;
        }
        seed = w.getSeed();
        savedMobsUseEmp = SubstrateConfigSC.mobsUseEmp;
        saved = true;
        MinecraftForge.EVENT_BUS.register(this);
        pickArea();
        force(ax >> 4, az >> 4, 3);
        registration();
        breakdown();
        mobs();
        mobGun();
        commandant();
        elitesNow();
        reactive();
        ruins();
        screen();
        elitesLater();
        limits();
        fieldScreen();
        packagesBehaviour();
        wardenSuit();
        steps.add(new Step(700, "regression") {
            @Override
            void run() {
                System.out.println("[SC-WORLDTEST] INFO combat: the regression of Ф1 / Ф2 (§11.2.11) - the lines of WorldTestEmpSC (pe1), WorldTestSubstrateSC"
                        + " (substrate) and WorldTestSettlementSC (settlement) above run as before Ф3");
            }
        });
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (saved) {
            SubstrateConfigSC.mobsUseEmp = savedMobsUseEmp;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        for (Entity e : made) {
            if (e.worldObj != null && e.worldObj.loadedEntityList.contains(e)) {
                e.setDead();
            }
            unlist(e);
        }
        made.clear();
        if (w != null) {
            for (int[] p : placed) {
                w.setBlockToAir(p[0], p[1], p[2]);
            }
            for (FakePlayer p : fakes) {
                resetEmp(p);
                for (int i = 0; i < 4; i++) {
                    p.inventory.armorInventory[i] = null;
                }
                for (int i = 0; i < p.inventory.mainInventory.length; i++) {
                    p.inventory.mainInventory[i] = null;
                }
                unlist(p);
            }
        }
        placed.clear();
        for (ForgeChunkManager.Ticket t : tickets) {
            ForgeChunkManager.releaseTicket(t);
        }
        tickets.clear();
        w = null;
        System.out.println("[SC-WORLDTEST] DONE combat");
    }

    /** The middle of a plateau cell with no settlement and no lithograph, away from the other tests' places (Ф1 near 0, 0; Ф2's regions 0, 24+, 60+). */
    private void pickArea() {
        int g = SubstrateTerrainSC.GRID;
        for (int k = 3; k < 40; k++) {
            int cx = -k, cz = -k - 2;
            int x = cx * g + g / 2, z = cz * g + g / 2;
            SettlementLayoutSC.Site s = SettlementLayoutSC.siteNear(seed, x, z);
            boolean settled = s != null && Math.hypot(s.x - x, s.z - z) < 200;
            if (!settled && RuinLayoutSC.lithoSite(seed, cx, cz) == null) {
                ax = x;
                az = z;
                return;
            }
        }
        ax = -3 * g + g / 2;
        az = -5 * g + g / 2;
    }

    // ------------------------------------------------------------------ 1. registration

    private void registration() {
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < MOBS.length; i++) {
            String name = "SiliconAge." + MobEntitiesSC.nameOf(MobEntitiesSC.ID_TROOPER + i);
            if (EntityList.stringToClassMapping.get(name) != MOBS[i]) {
                bad.append(' ').append(name);
            }
        }
        String node = MapGenStructureIO.func_143033_a(new MapGenDeadNodeSC.StructureDeadNodeStartSC()),
                litho = MapGenStructureIO.func_143033_a(new MapGenLithographSC.StructureLithographStartSC()),
                nodePart = MapGenStructureIO.func_143036_a(new ComponentRuinSC()), litPart = MapGenStructureIO.func_143036_a(new ComponentRuinSC.Lit());
        boolean structures = MapGenDeadNodeSC.START_NAME.equals(node) && "SCSi14DeadNode".equals(node) && "SCSi14Litho".equals(litho)
                && nodePart != null && nodePart.startsWith("SCSi14Node") && litPart != null && litPart.startsWith("SCSi14Lit");
        boolean blocks = RuinBlocksSC.ruin != null && "SiliconAge:ruinSC".equals(Block.blockRegistry.getNameForObject(RuinBlocksSC.ruin))
                && SettlementBlocksSC.showcase2 != null && "SiliconAge:showcase2SC".equals(Block.blockRegistry.getNameForObject(SettlementBlocksSC.showcase2));
        print(bad.length() == 0 && structures && blocks, "registration (§2.2): the eight creatures in EntityList" + (bad.length() == 0 ? "" : " - NOT:" + bad)
                + "; the structures " + node + " / " + litho + ", pieces " + nodePart + " / " + litPart + " " + structures + "; ruinSC and showcase2SC " + blocks);
    }

    // ------------------------------------------------------------------ 2. «Пробой» (ПБ-15, ПБ-16)

    private void breakdown() {
        HurtablePlayer p = hurtable("[SCTestExo]", ax, Y, az);
        wear(p, ArmorSuit.EXO, 100, true);
        EntityHollowShellSC shell = new EntityHollowShellSC(w);
        shell.setPosition(ax + 2.5, Y, az + 0.5);
        boolean full = ArmorLogicSC.shieldActive(p);
        boolean passes = !ArmorLogicSC.exoStops(p, BreakdownDamageSC.of(shell), 5F) && !ArmorLogicSC.exoStops(p, BreakdownDamageSC.WORLD, 5F);
        boolean mob = ArmorLogicSC.exoStops(p, DamageSource.causeMobDamage(shell), 5F), generic = ArmorLogicSC.exoStops(p, DamageSource.generic, 5F),
                magic = ArmorLogicSC.exoStops(p, DamageSource.magic, 5F), fire = ArmorLogicSC.exoStops(p, DamageSource.inFire, 5F),
                blast = ArmorLogicSC.exoStops(p, DamageSource.setExplosionSource(null), 5F);
        // 5 of it really come off: the attack goes through every handler (ShieldEventHandler asks exoStops) and the armour
        p.setHealth(p.getMaxHealth());
        p.hurtResistantTime = 0;
        float h0 = p.getHealth();
        p.attackEntityFrom(BreakdownDamageSC.of(shell), 5F);
        float lost = h0 - p.getHealth(), want = onPlayer(w, BreakdownDamageSC.of(shell), 5F);   // a mob's blow: the difficulty's scaling first
        p.hurtResistantTime = 0;
        float h1 = p.getHealth();
        p.attackEntityFrom(DamageSource.causeMobDamage(shell), 5F);
        float stopped = h1 - p.getHealth();
        p.setHealth(p.getMaxHealth());
        print(full && passes && mob && generic && magic && fire && blast && Math.abs(lost - want) < 0.01F && stopped < 0.01F, "«Пробой» through the player's"
                + " shield (ArmorLogicSC.exoStops, ПБ-16): a full Exo set, helium, the shield on " + full + "; «Пробой» (a mob's and the world's) passes "
                + passes + "; still stopped - a mob's blow " + mob + ", generic " + generic + ", magic " + magic + ", fire " + fire + ", an explosion " + blast
                + "; a blow of 5 «Пробой» took " + n(lost) + " HP (" + n(want) + " - 5 at the difficulty " + w.difficultySetting + "), an ordinary 5 - "
                + n(stopped) + " (0)");

        // the mob's shield (ПБ-16): «Пробой» into the health, the reserve untouched; an ordinary blow off the reserve
        dress(shell, ArmorSuit.EXO, true, 200F);
        boolean up = shell.mobArmor().shieldUp();
        EntityTrooperSC attacker = new EntityTrooperSC(w);
        attacker.setPosition(ax + 4.5, Y, az + 0.5);
        shell.hurtResistantTime = 0;
        float hp0 = shell.getHealth(), r0 = shell.mobArmor().reserve;
        shell.attackEntityFrom(BreakdownDamageSC.of(attacker), 6F);
        float hp1 = shell.getHealth(), r1 = shell.mobArmor().reserve;
        shell.hurtResistantTime = 0;
        shell.attackEntityFrom(DamageSource.causeMobDamage(attacker), 6F);
        float hp2 = shell.getHealth(), r2 = shell.mobArmor().reserve;
        boolean through = Math.abs(hp0 - hp1 - 6F) < 0.01F && Math.abs(r0 - r1) < 0.01F, taken = Math.abs(hp1 - hp2) < 0.01F && r1 - r2 > 5.99F;
        print(up && through && taken, "«Пробой» through a mob's shield (MobArmorSC.shield, ПБ-16): a Hollow Shell in Exo with helium, the shield up " + up
                + "; 6 «Пробой» - HP " + n(hp0) + " -> " + n(hp1) + ", the reserve " + n(r0) + " -> " + n(r1) + " " + through + "; an ordinary 6 - HP "
                + n(hp2) + ", the reserve -> " + n(r2) + " (the shield took it) " + taken);
    }

    // ------------------------------------------------------------------ 3. the mobs (packages M, X)

    private void mobs() {
        StringBuilder bad = new StringBuilder(), kit = new StringBuilder();
        HurtablePlayer target = hurtable("[SCTestTarget]", ax, Y, az + 8);
        ArmorSuit[] suits = {ArmorSuit.QUANTUM, ArmorSuit.QUANTUM, ArmorSuit.EXO, null, ArmorSuit.EXO, null, null, null};
        float[] caps = {8F, 8F, 10F, -1F, 11F, -1F, -1F, 6F};      // the drones and the swarm don't fight hand to hand (-1: not asked)
        float[] lo = {40F, 40F, 70F, 0F, 100F, 0F, 0F, 0F}, hi = {70F, 60F, 110F, 0F, 140F, 0F, 0F, 0F};
        String[] cls = {TROOPER, JAMMER, SHELL, null, EXECUTIONER, null, null, null};
        for (int i = 0; i < MOBS.length; i++) {
            EntityLiving e;
            try {
                e = (EntityLiving) MOBS[i].getConstructor(net.minecraft.world.World.class).newInstance(w);
            } catch (Exception ex) {
                bad.append(' ').append(MOBS[i].getSimpleName()).append(":not made ").append(ex);
                continue;
            }
            e.setPosition(ax + 2.5 * i, Y, az + 6.5);
            e.onSpawnWithEgg(null);
            String n = MOBS[i].getSimpleName().replace("Entity", "").replace("SC", "");
            ItemStack held = e.getHeldItem();
            kit.append(' ').append(n).append('[').append(held == null ? "-" : held.getDisplayName());
            if (suits[i] != null) {
                EntityArmoredMobSC a = (EntityArmoredMobSC) e;
                MobArmorSC m = a.mobArmor();
                int worn = m.wornPoints(), pieces = 0;
                for (int t = 0; t < 4; t++) {
                    pieces += MobArmorSC.pieceSuit(a, t) == suits[i] ? 1 : 0;
                }
                float min = MobArmorMathSC.reserveFor(num(cls[i], "RESERVE_DAMAGE_MIN", lo[i]), worn), max = MobArmorMathSC.reserveFor(num(cls[i],
                        "RESERVE_DAMAGE_MAX", hi[i]), worn);
                kit.append(", ").append(m.suit()).append(' ').append(pieces).append("/4, reserve ").append(n(m.reserveMax));
                if (pieces != 4 || m.reserveMax < min - 0.01F || m.reserveMax > max + 0.01F || m.reserve <= 0F) {
                    bad.append(' ').append(n).append(":armour ").append(pieces).append("/4 ").append(m.suit()).append(" reserve ").append(n(m.reserveMax))
                            .append(" (").append(n(min)).append("..").append(n(max)).append(')');
                }
                if (!weaponOk(i, held)) {
                    bad.append(' ').append(n).append(":holds ").append(held == null ? "nothing" : held.getDisplayName());
                }
            }
            if (Math.abs(e.getMaxHealth() - (float) hpOf(i)) > 0.01F) {
                bad.append(' ').append(n).append(":HP ").append(n(e.getMaxHealth()));
            }
            // a blow at a FakePlayer: never past the cap (ПС-4, ПС-10); damageMul 1 - no elite
            if (caps[i] < 0F) {
                kit.append(']');
                continue;
            }
            target.setHealth(target.getMaxHealth());
            target.hurtResistantTime = 0;
            victim = target;
            struck = 0F;
            e.attackEntityAsMob(target);
            victim = null;
            if (struck > caps[i] + 0.01F) {
                bad.append(' ').append(n).append(":hit ").append(n(struck)).append(" > ").append(n(caps[i]));
            }
            kit.append(struck > 0F ? ", hit " + n(struck) : "").append(']');
        }
        target.setHealth(target.getMaxHealth());
        print(bad.length() == 0, "the mobs (packages M, X, §4.2..§4.8): the eight made by onSpawnWithEgg - the suit 4/4, the reserve in the bounds, the"
                + " weapon of the table, HP; a blow at a FakePlayer within the cap (8 / 8 / 10 / - / 11 / - / - / 6)" + (bad.length() == 0 ? "" : " - BAD:" + bad)
                + " -" + kit);
    }

    /** The weapon table (§4.2..§4.5): the Trooper's blade or rifle, the Jammer's emitter (a carrier's ПЭ-1 / Ion Cutter), the Shell's blades or fists, the Executioner's Exo Blade. */
    private static boolean weaponOk(int i, ItemStack held) {
        Item it = held == null ? null : held.getItem();
        switch (i) {
            case 0:
                return it == ModItems.BLADES.get(BladeType.QUANTUM) || it == ModItems.WEAPONS.get(WeaponType.PLASMA_RIFLE);
            case 1:
                return it == ModItems.WEAPONS.get(WeaponType.PULSE_EMITTER) || it == ModItems.empGun || it == ModItems.WEAPONS.get(WeaponType.ION_CUTTER);
            case 2:
                return it == null || it == ModItems.BLADES.get(BladeType.EXO) || it == ModItems.BLADES.get(BladeType.QUANTUM);
            case 4:
                return it == ModItems.BLADES.get(BladeType.EXO);
            default:
                return true;
        }
    }

    private static double hpOf(int i) {
        double[] hp = {EntityTrooperSC.HP, EntityJammerSC.HP, EntityHollowShellSC.HP, EntityRepairDroneSC.HP, EntityExecutionerSC.HP, EntityNaniteSC.HP,
                EntitySentryDroneSC.HP, EntityMimicSC.HP};
        return hp[i];
    }

    // ------------------------------------------------------------------ 4. ПЭ-1 in a mob's hands (package A, §3.2, ПБ-17, ПБ-18)

    private void mobGun() {
        SubstrateConfigSC.mobsUseEmp = true;
        if (!EmpMobGunSC.allowed()) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the mob's ПЭ-1 - weapons.pe1.enabled / affectsPlayers off in the config");
        } else {
            EntityJammerSC jammer = carrier(ax + 0.5, az + 20.5);
            FakePlayer t = fake("[SCTestPoint]", ax, Y, az + 30);
            wear(t, ArmorSuit.NANO, 100, false);
            EmpSC.HitResult r = EmpMobGunSC.fire(jammer, t, EmpMode.POINT, false, EmpMobGunSC.RANGE);
            int want = EmpMathSC.durationTicks(ArmorSuit.NANO, EmpMode.POINT, false, 0, EmpSC.heliumInLoop(t), ConfigSC.empDurationMultiplier);
            boolean chest = r != null && EmpSC.isSuppressed(t, 1) && Math.abs(EmpSC.suppressedTicks(t, 1) - want) <= 1 && !EmpSC.isSuppressed(t, 0)
                    && !EmpSC.isSuppressed(t, 3);
            int got = EmpSC.suppressedTicks(t, 1);
            EmpSC.clearSuppression(t);                                  // the emergency reboot: immune at once
            EmpSC.HitResult again = EmpMobGunSC.fire(jammer, t, EmpMode.POINT, false, EmpMobGunSC.RANGE);
            boolean immune = !EmpSC.isSuppressed(t, 1) && EmpSC.immuneTicks(t, 1) > 0 && (again == null || !again.anySuppressed());
            // behind a wall: an iron screen between them - no shot at all
            FakePlayer hidden = fake("[SCTestCover]", ax + 6, Y, az + 30);
            wear(hidden, ArmorSuit.NANO, 100, false);
            EntityJammerSC behind = carrier(ax + 6.5, az + 20.5);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    place(ax + 6 + dx, Y + dy, az + 25, Blocks.iron_block);
                }
            }
            boolean sight = sight(behind, hidden);
            EmpSC.HitResult walled = EmpMobGunSC.fire(behind, hidden, EmpMode.POINT, false, EmpMobGunSC.RANGE);
            boolean cover = !sight && walled == null && !EmpSC.anySuppressed(hidden);
            for (int[] p : new ArrayList<int[]>(placed)) {
                w.setBlockToAir(p[0], p[1], p[2]);
            }
            placed.clear();
            print(chest && immune && cover, "the mob's ПЭ-1 (EmpMobGunSC.fire, ПБ-17): a Jammer carrier's Point on a FakePlayer in Nano 10 blocks off - the"
                    + " chestplate suppressed " + got + " ticks (" + want + ") " + chest + "; rebooted, a second Point - immune, nothing new " + immune
                    + "; behind an iron wall - out of sight, no shot " + cover);
        }
        // the rule off: no mob holds a ПЭ-1 (the carrier its Ion Cutter)
        SubstrateConfigSC.mobsUseEmp = false;
        boolean off = !EmpMobGunSC.allowed();
        int carriers = 0, cutters = 0;
        for (int i = 0; i < 100; i++) {
            EntityJammerSC j = new EntityJammerSC(w);
            j.setPosition(ax + 0.5, Y, az + 40.5);
            j.onSpawnWithEgg(null);
            ItemStack held = j.getHeldItem();
            carriers += held != null && held.getItem() == ModItems.empGun ? 1 : 0;
            cutters += held != null && held.getItem() == ModItems.WEAPONS.get(WeaponType.ION_CUTTER) ? 1 : 0;
        }
        SubstrateConfigSC.mobsUseEmp = true;
        int on = 0;
        for (int i = 0; EmpMobGunSC.allowed() && i < 100; i++) {
            EntityJammerSC j = new EntityJammerSC(w);
            j.setPosition(ax + 0.5, Y, az + 40.5);
            j.onSpawnWithEgg(null);
            on += j.getHeldItem() != null && j.getHeldItem().getItem() == ModItems.empGun ? 1 : 0;
        }
        SubstrateConfigSC.mobsUseEmp = savedMobsUseEmp;
        print(off && carriers == 0, "dimension.mobsUseEmp off (ПБ-18): allowed() false " + off + ", ПЭ-1 carriers in 100 Jammers " + carriers + " (0; Ion Cutters "
                + cutters + "); with the rule on - " + on + " of 100 (1 in 50 expected)");
    }

    /** A Jammer holding a basic ПЭ-1 (a carrier), never spawned, at (x, Y, z) facing +Z. */
    private EntityJammerSC carrier(double x, double z) {
        EntityJammerSC j = new EntityJammerSC(w);
        j.setLocationAndAngles(x, Y, z, 0F, 0F);
        j.rotationYawHead = 0F;
        j.setCurrentItemOrArmor(0, new ItemStack(ModItems.empGun, 1, ItemEmpGunSC.META_BASIC));
        return j;
    }

    /** 4b: a target inside a settlement's dome (its core's EMP screen pays always), the shooter outside - absorbed (§3.2, ПБ-17). */
    private void fieldScreen() {
        final SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        final SettlementRegistrySC.Settlement rec = reg == null ? null : reg.first(w.provider.dimensionId);
        if (rec == null || !EmpMobGunSC.allowed()) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the field's EMP screen - " + (rec == null ? "no settlement in this world" : "the mob's ПЭ-1 is off"));
            return;
        }
        force(rec.x >> 4, rec.z >> 4, 1);                               // the core ticks: its field comes up among the active ones
        steps.add(new Step(40, "the field's screen") {
            @Override
            void run() {
                TileEntity te = w.getTileEntity(rec.x, rec.y, rec.z);
                TileEntitySettlementCoreSC core = te instanceof TileEntitySettlementCoreSC ? (TileEntitySettlementCoreSC) te : null;
                boolean active = core != null && TileEntityFieldGeneratorSC.activeFieldsIn(w).contains(core);
                int y = rec.floorY + 15;
                FakePlayer in = fake("[SCTestDome]", rec.x + 8, y, rec.z);
                wear(in, ArmorSuit.NANO, 100, false);
                EntityJammerSC out = carrier(rec.x + rec.radius + 10.5, rec.z + 0.5);
                out.setPosition(rec.x + rec.radius + 10.5, y, rec.z + 0.5);
                out.rotationYaw = out.rotationYawHead = 90F;
                if (!active || !sight(out, in)) {
                    System.out.println("[SC-WORLDTEST] SKIP combat: the field's EMP screen - the core's field " + (active ? "up" : "not up") + ", the line of sight "
                            + sight(out, in) + " over the dome at Y " + y);
                    return;
                }
                EmpSC.HitResult r = EmpMobGunSC.fire(out, in, EmpMode.POINT, false, 64);
                boolean absorbed = r == null && !EmpSC.anySuppressed(in);
                print(absorbed, "the field's EMP screen (EmpRulesSC.screenAbsorbs for a mob, ПБ-17): a Jammer carrier outside the dome of «" + rec.id + "» fires a"
                        + " Point at a FakePlayer inside - absorbed at the border, nothing suppressed " + absorbed);
            }
        });
    }

    // ------------------------------------------------------------------ 5. the personal screen (ПБ-19) through a player's ПЭ-1

    private void screen() {
        final FakePlayer shooter = fake("[SCTestScreen]", ax + 12, Y, az);
        final EntityJammerSC jammer = new EntityJammerSC(w);
        jammer.setLocationAndAngles(ax + 12.5, Y, az + 4.5, 180F, 0F);
        dress(jammer, ArmorSuit.QUANTUM, true, 100F);
        jammer.mobArmor().screen = true;
        jammer.mobArmor().screenDownUntil = 0L;
        jammer.mobArmor().sync();
        list(jammer);
        final ItemStack gun = new ItemStack(ModItems.empGun);
        ItemEmpGunSC.setCharge(gun, ItemEmpGunSC.capacityOf(gun));
        ItemEmpGunSC.setMode(gun, EmpMode.POINT);
        shooter.inventory.currentItem = 0;
        shooter.inventory.mainInventory[0] = gun;
        final int charge = EmpMathSC.chargeTicks(EmpMode.POINT, false);
        final long[] firstAt = new long[1];
        steps.add(new Step(2, "the personal screen") {
            @Override
            void run() {
                shoot(shooter, gun);
            }
        });
        for (int i = 1; i <= charge + 2; i++) {
            steps.add(new Step(2 + i, "the personal screen") {
                @Override
                void run() {
                    com.sc.emp.EmpGunLogicSC.playerTick(shooter);
                }
            });
        }
        steps.add(new Step(charge + 6, "the personal screen") {
            @Override
            void run() {
                firstAt[0] = w.getTotalWorldTime();
                MobArmorSC a = jammer.mobArmor();
                boolean screened = !a.screenUp() && a.screenDownUntil > w.getTotalWorldTime() && !a.anySuppressed();
                print(screened, "the personal EMP screen (ПБ-19): a FakePlayer's Point (EmpGunLogicSC) at a screened Jammer - the screen took it (down "
                        + (a.screenDownUntil - w.getTotalWorldTime()) + " more ticks, " + EliteModSC.SCREEN_RECOVER_TICKS + " in all), nothing suppressed "
                        + screened);
                com.sc.emp.EmpGunLogicSC.stoppedUsing(gun, w, shooter, charge);
                shooter.clearItemInUse();
                ItemEmpGunSC.setHeat(gun, 0, w.getTotalWorldTime());
            }
        });
        int second = charge + 16;
        steps.add(new Step(second, "the personal screen") {
            @Override
            void run() {
                shoot(shooter, gun);
            }
        });
        for (int i = 1; i <= charge + 2; i++) {
            steps.add(new Step(second + i, "the personal screen") {
                @Override
                void run() {
                    com.sc.emp.EmpGunLogicSC.playerTick(shooter);
                }
            });
        }
        steps.add(new Step(second + charge + 4, "the personal screen") {
            @Override
            void run() {
                boolean suppressed = jammer.mobArmor().anySuppressed();
                print(suppressed, "the personal EMP screen (ПБ-19): a second Point right after it - the armour suppressed " + suppressed);
                com.sc.emp.EmpGunLogicSC.stoppedUsing(gun, w, shooter, charge);
                shooter.clearItemInUse();
            }
        });
        steps.add(new Step(charge + 6 + EliteModSC.SCREEN_RECOVER_TICKS + 2, "the personal screen") {
            @Override
            void run() {
                boolean back = jammer.mobArmor().screenUp();
                print(back, "the personal EMP screen (ПБ-19): " + (w.getTotalWorldTime() - firstAt[0]) + " ticks after it took a shot - up again " + back);
                unlist(jammer);
            }
        });
    }

    /** The player's right-click with the gun (EmpGunLogicSC: the charge-up starts; FakePlayers aren't ticked - their tick is driven by hand). */
    private void shoot(FakePlayer p, ItemStack gun) {
        ItemStack held = com.sc.emp.EmpGunLogicSC.rightClick(gun, w, p);
        if (held != null && held != gun) {
            p.inventory.mainInventory[0] = held;
        }
    }

    // ------------------------------------------------------------------ 6. the reactive targets (ПБ-20)

    private void reactive() {
        if (!SubstrateConfigSC.empMobTargets) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the reactive targets - dimension.empMobTargets is off");
            return;
        }
        FakePlayer shooter = fake("[SCTestReact]", ax - 10, Y, az);
        StringBuilder bad = new StringBuilder();
        // the drones: any Point downs them
        EntityRepairDroneSC repair = new EntityRepairDroneSC(w);
        repair.setPosition(ax - 10.5, Y, az + 5.5);
        EntitySentryDroneSC sentry = new EntitySentryDroneSC(w);
        sentry.setPosition(ax - 12.5, Y, az + 5.5);
        boolean drones = struck(repair, shooter, EmpMode.POINT) && struck(sentry, shooter, EmpMode.POINT) && down(repair) && down(sentry);
        // the swarm on the ground: a Pulse burns every particle within 5 of the hit, not one 9 blocks off
        int gx = ax - 30, gz = az + 10, gy = ground(gx, gz);
        int n = EntityNaniteSC.spawnSwarm(w, gx + 0.5, gy + 1.5, gz + 0.5, 6, new Random(14L));
        List<EntityNaniteSC> swarm = new ArrayList<EntityNaniteSC>();
        for (Object o : w.getEntitiesWithinAABB(EntityNaniteSC.class, AxisAlignedBB.getBoundingBox(gx - 8, gy - 4, gz - 8, gx + 9, gy + 8, gz + 9))) {
            swarm.add((EntityNaniteSC) o);
            made.add((Entity) o);
        }
        boolean swarmOk = false;
        String swarmWhat = n + " particles made";
        if (swarm.size() >= 3) {
            EntityNaniteSC far = swarm.get(swarm.size() - 1);
            far.setPosition(gx + 9.5, far.posY, gz + 0.5);
            EntityNaniteSC hit = swarm.get(0);
            for (EntityNaniteSC s : swarm) {
                if (s != far) {
                    s.setPosition(gx + 0.5 + (s.getEntityId() % 3) * 0.5, gy + 1.5, gz + 0.5);
                }
            }
            boolean reacted = struck(hit, shooter, EmpMode.PULSE);
            int nearDead = 0, near = 0;
            for (EntityNaniteSC s : swarm) {
                if (s != far) {
                    near++;
                    nearDead += down(s) ? 1 : 0;
                }
            }
            swarmOk = reacted && nearDead == near && !down(far);
            swarmWhat += ", a Pulse burned " + nearDead + " of the " + near + " near ones, the one 9 blocks off " + (down(far) ? "burned" : "alive");
        }
        // the mimic on the ground: a Point wakes it and stuns it - it stays put
        int mx = ax - 40, mz = az - 10, my = ground(mx, mz);
        final EntityMimicSC mimic = new EntityMimicSC(w);
        mimic.setLocationAndAngles(mx + 0.5, my, mz + 0.5, 0F, 0F);
        mimic.onSpawnWithEgg(null);
        w.spawnEntityInWorld(mimic);
        made.add(mimic);
        boolean asleep = mimic instanceof IDisguisedSC && ((IDisguisedSC) mimic).disguised();
        boolean woke = struck(mimic, shooter, EmpMode.POINT) && !((IDisguisedSC) mimic).disguised() && !down(mimic);
        final double px = mimic.posX, pz = mimic.posZ;
        if (!drones) {
            bad.append(" drones");
        }
        print(drones && swarmOk && woke, "the reactive targets (ПБ-20, packages M / X): a Point downs the Repair Drone and the sentry drone " + drones + "; the"
                + " swarm - " + swarmWhat + " " + swarmOk + "; a mimic asleep " + asleep + ", a Point woke it " + woke);
        steps.add(new Step(45, "the mimic's stun") {
            @Override
            void run() {
                double moved = Math.hypot(mimic.posX - px, mimic.posZ - pz);
                print(moved < 0.3 && !mimic.isDead, "the reactive targets (ПБ-20): the stunned mimic moved " + n((float) moved) + " blocks in 2 s (0)");
                mimic.setDead();
            }
        });
    }

    /** IEmpReactiveSC.empStruck from `shooter` at the creature's middle; false if it isn't a reactive one at all. */
    private static boolean struck(Entity e, Entity shooter, EmpMode mode) {
        return e instanceof IEmpReactiveSC && ((IEmpReactiveSC) e).empStruck(shooter, mode, e.posX, e.posY + e.height / 2, e.posZ);
    }

    private static boolean down(EntityLivingBase e) {
        return e.isDead || e.getHealth() <= 0F;
    }

    // ------------------------------------------------------------------ 7. the Commandant (ПГ-12)

    private void commandant() {
        if (!EmpMobGunSC.allowed()) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the Commandant's ПЭ-1 - the mob's ПЭ-1 is off");
            return;
        }
        EntityLiving c = SettlementSC.newMember(w, NpcKindSC.COMMANDANT);
        c.setLocationAndAngles(ax + 20.5, Y, az + 0.5, 0F, 0F);
        c.onSpawnWithEgg(null);
        EntityHollowShellSC shell = new EntityHollowShellSC(w);
        shell.setPosition(ax + 20.5, Y, az + 14.5);
        dress(shell, ArmorSuit.EXO, true, 200F);
        FakePlayer p = fake("[SCTestCommandant]", ax + 24, Y, az + 10);
        wear(p, ArmorSuit.NANO, 100, false);
        EmpSC.HitResult r = EmpMobGunSC.fire(c, shell, EmpMode.PULSE, false, 64);
        boolean suppressed = r != null && shell.mobArmor().anySuppressed();
        int atPlayer = EmpMobGunSC.refusal(c, p, EmpMode.PULSE);
        EmpSC.HitResult none = EmpMobGunSC.fire(c, p, EmpMode.PULSE, false, 64);
        boolean spared = atPlayer != EmpNetSC.OK && none == null && !EmpSC.anySuppressed(p);
        print(suppressed && spared, "the Commandant (ПГ-12): a warden of that rank (" + ((EntityWardenSC) c).kind() + ") Pulses a Hollow Shell in Exo 14 blocks off"
                + " - suppressed " + suppressed + "; at a FakePlayer - refused (" + atPlayer + "), nothing fired " + spared);
    }

    // ------------------------------------------------------------------ 8. the elites (package A, §5, ПС-20..ПС-25)

    private void elitesNow() {
        StringBuilder bad = new StringBuilder();
        for (EliteModSC m : EliteModSC.values()) {
            EntityTrooperSC t = new EntityTrooperSC(w);
            t.setPosition(ax - 20.5, Y, az - 20.5);
            dress(t, ArmorSuit.QUANTUM, false, 50F);
            t.mobArmor().emergency = true;
            float max0 = t.mobArmor().reserveMax;
            EliteSC.apply(t, m);
            MobArmorSC a = t.mobArmor();
            boolean common = t.eliteMod() == m && a.elite && t.executeImmune() && t.censusElite();
            boolean own;
            switch (m) {
                case CHARGED:
                    own = Math.abs(a.reserveMax - max0 * (1F + EliteModSC.CHARGED_BONUS)) < 0.05F && Math.abs(a.reserve - a.reserveMax) < 0.05F;
                    break;
                case SHIELDED:
                    own = a.screen && a.screenUp();
                    break;
                case VETERAN:
                    own = a.helium && !a.emergency;
                    break;
                default:
                    own = true;
            }
            if (!common || !own) {
                bad.append(' ').append(m.key).append(": the modifier ").append(t.eliteMod()).append(", elite ").append(a.elite).append(", «Казнь» refused ")
                        .append(t.executeImmune()).append(", its own change ").append(own);
            }
        }
        EntityTrooperSC plain = new EntityTrooperSC(w);
        EntityExecutionerSC ex = new EntityExecutionerSC(w);
        ex.setPosition(ax - 22.5, Y, az - 20.5);
        dress(ex, ArmorSuit.EXO, true, 100F);
        EliteSC.apply(ex, EliteModSC.CHARGED);
        boolean kinds = !plain.executeImmune() && ex.eliteMod() == null && ex.executeImmune();
        print(bad.length() == 0 && kinds, "the elites (EliteSC.apply, ПС-21, ПС-24): each modifier marks the mob an elite that «Казнь» won't finish - Charged x1.5"
                + " reserve and full, Shielded its screen up, Veteran helium and no emergency mode" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; a plain"
                + " Trooper finishable, the Executioner takes no modifier and is spared anyway " + kinds);
    }

    private void elitesLater() {
        int gx = ax + 30, gz = az - 30, gy = ground(gx, gz);
        ground(gx + 16, gz);
        ground(gx, gz + 24);
        clearNatives(gx, gz + 12, 40);                  // an interrupted run's Repairer left here would feed both of the pair
        // the Repairer: +1 a second to a neighbour within 8, not to itself
        final EntityTrooperSC repairer = spawned(new EntityTrooperSC(w), gx + 0.5, gy, gz + 0.5);
        final EntityTrooperSC patient = spawned(new EntityTrooperSC(w), gx + 3.5, ground(gx + 3, gz), gz + 0.5);
        dress(repairer, ArmorSuit.QUANTUM, true, 50F);
        dress(patient, ArmorSuit.QUANTUM, true, 50F);
        EliteSC.apply(repairer, EliteModSC.REPAIRER);
        patient.mobArmor().reserve = 20F;
        repairer.mobArmor().reserve = 20F;
        // the Commander: its group hits x1.2 after an order - 24 blocks off the Repairer's pair, past its order's 16 (the
        // rally's +20% of the reserve's maximum would count as «repaired» there)
        int cz = gz + 24, cy = ground(gx, cz);
        final EntityTrooperSC commander = spawned(new EntityTrooperSC(w), gx + 0.5, cy, cz + 0.5);
        final EntityTrooperSC soldier = spawned(new EntityTrooperSC(w), gx + 4.5, ground(gx + 4, cz), cz + 0.5);
        dress(commander, ArmorSuit.QUANTUM, true, 50F);
        dress(soldier, ArmorSuit.QUANTUM, true, 50F);
        EliteSC.apply(commander, EliteModSC.COMMANDER);
        final FakePlayer foe = fake("[SCTestFoe]", gx + 2, cy, cz + 6);
        final float[] mul = new float[1];
        for (int i = 1; i <= 150; i++) {
            steps.add(new Step(i, "the Commander") {
                @Override
                void run() {
                    commander.setAttackTarget(foe);                // fights - so it gives its orders
                    mul[0] = Math.max(mul[0], soldier.damageMul());
                }
            });
        }
        steps.add(new Step(45, "the Repairer") {
            @Override
            void run() {
                float got = patient.mobArmor().reserve - 20F, own = repairer.mobArmor().reserve - 20F;
                print(got >= EliteModSC.REPAIR_UNITS - 0.01F && own < 0.01F, "the elites (ПС-21): «Ремонтник» - its neighbour 3 blocks off got " + n(got)
                        + " units in 2 s (+" + n(EliteModSC.REPAIR_UNITS) + "/s), itself " + n(own) + " (0)");
            }
        });
        steps.add(new Step(151, "the Commander") {
            @Override
            void run() {
                boolean boosted = Math.abs(mul[0] - (1F + EliteModSC.COMMANDER_BONUS)) < 0.01F;
                print(boosted, "the elites (ПС-21): «Командир» - a Trooper of its group hit x" + n(mul[0]) + " within 7.5 s of fighting (x"
                        + n(1F + EliteModSC.COMMANDER_BONUS) + ")");
            }
        });
        // the Avenger: dead, a blast 30 ticks on; no block broken, no item or orb by it burnt, a creature near it hurt
        final int bx = gx + 12, bz = gz, by = ground(bx, bz);
        final EntityTrooperSC avenger = spawned(new EntityTrooperSC(w), bx + 0.5, by, bz + 0.5);
        dress(avenger, ArmorSuit.QUANTUM, true, 50F);
        EliteSC.apply(avenger, EliteModSC.AVENGER);
        final EntityTrooperSC bystander = spawned(new EntityTrooperSC(w), bx - 1.5, by, bz + 0.5);
        place(bx + 2, by, bz, Blocks.glass);
        final long[] diedAt = new long[1];
        final Entity[] loot = new Entity[2];
        steps.add(new Step(5, "the Avenger") {
            @Override
            void run() {
                blasts.clear();
                diedAt[0] = w.getTotalWorldTime();
                avenger.attackEntityFrom(DamageSource.outOfWorld, 1000F);
                // what its drops are: an item where MobDropsSC puts them, an orb as the death's experience
                EntityItem item = new EntityItem(w, avenger.posX, avenger.posY + 0.5D, avenger.posZ, new ItemStack(Items.iron_ingot));
                item.delayBeforeCanPickup = 32767;
                loot[0] = item;
                loot[1] = new EntityXPOrb(w, avenger.posX, avenger.posY + 0.5D, avenger.posZ, 3);
                for (Entity o : loot) {
                    w.spawnEntityInWorld(o);
                    made.add(o);
                }
            }
        });
        steps.add(new Step(5 + EliteModSC.AVENGER_FUSE_TICKS + 10, "the Avenger") {
            @Override
            void run() {
                long at = -1;
                for (double[] b : blasts) {
                    if (Math.abs(b[0] - (bx + 0.5)) < 2 && Math.abs(b[2] - (bz + 0.5)) < 2) {
                        at = (long) b[3];
                    }
                }
                long fuse = at < 0 ? -1 : at - diedAt[0];
                boolean glass = w.getBlock(bx + 2, by, bz) == Blocks.glass;
                boolean kept = !loot[0].isDead && !loot[1].isDead;
                boolean hurt = !bystander.isEntityAlive() || bystander.getHealth() < bystander.getMaxHealth();
                print(fuse >= EliteModSC.AVENGER_FUSE_TICKS - 1 && fuse <= EliteModSC.AVENGER_FUSE_TICKS + 2 && glass && kept && hurt, "the elites (ПС-21):"
                        + " «Мститель» - dead, its blast " + (fuse < 0 ? "never came" : fuse + " ticks later (" + EliteModSC.AVENGER_FUSE_TICKS + ")")
                        + ", the glass beside it whole " + glass + ", the item and the orb by it whole " + kept + ", a Trooper 2 blocks off hurt " + hurt);
            }
        });
    }

    /** A creature on the ground that never walks (its speed 0) and stays until finish(). */
    /**
     * What an earlier run left (the server stopped before finish(): its mobs were saved with the chunks - an elite among them
     * keeps working): every native in the loaded chunks within `r` of (x, z) that this run didn't make - removed.
     */
    private void clearNatives(int x, int z, int r) {
        for (Object o : w.getEntitiesWithinAABB(EntityLiving.class, AxisAlignedBB.getBoundingBox(x - r, 0, z - r, x + r, 256, z + r))) {
            if (o instanceof ISubstrateNativeSC && !made.contains(o)) {
                ((Entity) o).setDead();
            }
        }
    }

    private <T extends EntityLiving> T spawned(T e, double x, double y, double z) {
        e.setLocationAndAngles(x, y, z, 0F, 0F);
        e.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0D);
        // the ticket's chunks load only on a later tick: a spot a block past the one ground() loaded would refuse the spawn
        w.getChunkFromBlockCoords(net.minecraft.util.MathHelper.floor_double(x), net.minecraft.util.MathHelper.floor_double(z));
        w.spawnEntityInWorld(e);
        made.add(e);
        return e;
    }

    // ------------------------------------------------------------------ 9. the spawn director's caps (package M, ПС-7)

    private void limits() {
        if (w.difficultySetting == EnumDifficulty.PEACEFUL || !SpawnDirectorSC.hostilesAllowed(w)) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the spawn caps - Peaceful or no hostile spawning in this world");
            return;
        }
        int g = SubstrateTerrainSC.GRID, at = 9;
        while (at < 40 && (settledNear(-at * g, -at * g) || settledNear(-at * g, -at * g - 2 * g + g / 2))) {
            at++;                                                       // no settlement near: its dome and margin would stop the director
        }
        final int nx = -at * g, nz = -at * g;                           // a Dead Node's crossing far from the other tests
        final int kx = nx, kz = nz - 2 * g + g / 2;                     // a canyon (on a line, between two crossings)
        force(nx >> 4, nz >> 4, 4);
        force(kx >> 4, kz >> 4, 4);
        final FakePlayer anchor = fake("[SCTestAnchor]", nx, ground(nx, nz), nz);
        final int quantumMax = (int) num("com.sc.mob.SpawnRulesSC", "QUANTUM_NEAR_MAX", 2F), eliteMax = (int) num("com.sc.mob.SpawnRulesSC", "ELITE_MAX", 1F);
        final int eliteRadius = (int) num("com.sc.mob.SpawnRulesSC", "ELITE_RADIUS", 128F);
        final int[] most = new int[2], spawned = new int[2];
        for (int i = 0; i < 2 * SERIES; i++) {
            final int k = i;
            steps.add(new Step(10 + i, "the spawn caps") {
                @Override
                void run() {
                    boolean node = k < SERIES;
                    int x = node ? nx : kx, z = node ? nz : kz;
                    anchor.setLocationAndAngles(x + 0.5, ground(x, z), z + 0.5, 0F, 0F);
                    // what the last try made: keep the Quantum tier (at the Node) / the elites (in the canyon), the rest away - the caps alone stop it
                    int kept = 0;
                    for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
                        if (!(o instanceof ICensusSC) || !(o instanceof ISubstrateNativeSC) || ((Entity) o).isDead) {
                            continue;
                        }
                        Entity e = (Entity) o;
                        double d = Math.hypot(e.posX - x, e.posZ - z);
                        ICensusSC c = (ICensusSC) e;
                        boolean keep = node ? c.censusCounts() && c.censusTier() == ICensusSC.TIER_QUANTUM && d <= SpawnRulesSC.NEAR_RADIUS
                                : c.censusElite() && d <= eliteRadius;
                        if (keep) {
                            kept++;
                            if (!made.contains(e)) {
                                made.add(e);
                                spawned[node ? 0 : 1]++;
                            }
                        } else if (d <= eliteRadius && !made.contains(e)) {
                            e.setDead();
                        }
                    }
                    most[node ? 0 : 1] = Math.max(most[node ? 0 : 1], kept);
                    if (k == SERIES - 1 || k == 2 * SERIES - 1) {
                        print(most[node ? 0 : 1] <= (node ? quantumMax : eliteMax), "the spawn caps (SpawnDirectorSC, ПС-7): " + SERIES + " tries " + (node
                                ? "at a Dead Node - the Quantum tier within " + SpawnRulesSC.NEAR_RADIUS + " at most " + most[0] + " (" + quantumMax + "), "
                                + spawned[0] + " of them made"
                                : "in a canyon - elites within " + eliteRadius + " at most " + most[1] + " (" + eliteMax + "), " + spawned[1] + " made"));
                    }
                    if (k < 2 * SERIES - 1) {
                        SpawnDirectorSC.tryGroup(w, anchor);
                    }
                }
            });
        }
    }

    /** A settlement's site within 200 blocks of (x, z) (SettlementLayoutSC.siteNear). */
    private boolean settledNear(int x, int z) {
        SettlementLayoutSC.Site s = SettlementLayoutSC.siteNear(seed, x, z);
        return s != null && Math.hypot(s.x - x, s.z - z) < 200;
    }

    // ------------------------------------------------------------------ 10. the ruins (package X, §8, ПУ-1..ПУ-6)

    private void ruins() {
        if (!com.sc.ruin.RuinSC.generates()) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the ruins - they don't generate");
            return;
        }
        int g = SubstrateTerrainSC.GRID;
        // a Dead Node on land nobody has seen yet (Ф5: never a Breakdown Epicentre's crossing - the arena replaces its Node, ПУ-8)
        int[] node = null;
        for (int k = 12; k < 60 && node == null; k++) {
            if (fresh(k * 16 - 2, -k * 16 - 2, k * 16 + 2, -k * 16 + 2) && !com.sc.arena.ArenaLayoutSC.isArena(seed, k, -k)) {
                node = new int[]{k, -k};
            }
        }
        if (node == null) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the Dead Node - no fresh crossing found");
        } else {
            int cx = node[0] * 16, cz = node[1] * 16;
            int[] box = {(node[0] * g - RuinLayoutSC.NODE_HALF) >> 4, (node[1] * g - RuinLayoutSC.NODE_HALF) >> 4, (node[0] * g + RuinLayoutSC.NODE_HALF) >> 4,
                    (node[1] * g + RuinLayoutSC.NODE_HALF) >> 4};
            int outside = generate(box);
            int[] got = scan(box, 0, 255);
            print(got[0] > 0 && got[1] == 0 && got[2] >= 1 && outside == 0, "the Dead Node (ПУ-1..ПУ-3) at the crossing " + node[0] + ", " + node[1] + " (chunk " + cx
                    + ", " + cz + "): " + got[0] + " ruin blocks, Minecraft stone " + got[1] + " (0), " + got[2] + " chest(s) with loot (1-3), " + outside
                    + " chunk(s) past the ring loaded (0 - no cascade)");
            // the same ground explored now: a start made on it is empty (ПУ-3)
            StructureStart empty = startAt(new MapGenDeadNodeSC(), cx, cz);
            int[] fresher = null;
            for (int k = 61; k < 120 && fresher == null; k++) {
                if (fresh(k * 16 - 2, -k * 16 - 2, k * 16 + 2, -k * 16 + 2) && !com.sc.arena.ArenaLayoutSC.isArena(seed, k, -k)) {
                    fresher = new int[]{k * 16, -k * 16};
                }
            }
            StructureStart full = fresher == null ? null : startAt(new MapGenDeadNodeSC(), fresher[0], fresher[1]);
            boolean emptyOk = empty != null && empty.getComponents().isEmpty() && (full == null || !full.getComponents().isEmpty());
            print(emptyOk, "the explored ground (ПУ-3): a Dead Node's start made where its chunks exist has " + (empty == null ? "?" : empty.getComponents().size())
                    + " pieces (0); on fresh ground " + (full == null ? "not checked" : full.getComponents().size() + " (> 0)"));
        }
        // a lithograph on fresh ground: the hall, its chests and residents
        RuinLayoutSC.LithoSite site = null;
        for (int k = 14; k < 70 && site == null; k++) {
            for (int j = 0; j < 4 && site == null; j++) {
                RuinLayoutSC.LithoSite s = RuinLayoutSC.lithoSite(seed, k + j, -k);
                if (s != null && fresh((s.x >> 4) - 3, (s.z >> 4) - 3, (s.x >> 4) + 3, (s.z >> 4) + 3)) {
                    site = s;
                }
            }
        }
        if (site == null) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the lithograph - no fresh lithograph site found (package X's lithoSite)");
            return;
        }
        int half = RuinLayoutSC.HALL / 2 + 2;
        int[] box = {(site.x - half) >> 4, (site.z - half) >> 4, (site.x + half) >> 4, (site.z + half) >> 4};
        int outside = generate(box);
        int[] got = scan(box, site.floorY - 1, site.floorY + RuinLayoutSC.HALL_H + 1);
        int[] all = scan(box, 0, 255);
        AxisAlignedBB hall = AxisAlignedBB.getBoundingBox(site.x - half, site.floorY - 1, site.z - half, site.x + half, site.floorY + RuinLayoutSC.HALL_H + 2,
                site.z + half);
        int mimics = w.getEntitiesWithinAABB(EntityMimicSC.class, hall).size(), drones = w.getEntitiesWithinAABB(EntitySentryDroneSC.class, hall).size();
        print(got[0] > 0 && all[1] == 0 && got[2] >= 2 && mimics >= 2 && drones >= 2 && outside == 0, "the lithograph (ПУ-4..ПУ-6) of cell " + site.cellX + ", "
                + site.cellZ + " (hall at " + site.x + ", " + site.floorY + ", " + site.z + "): " + got[0] + " ruin blocks in the hall, Minecraft stone " + all[1]
                + " (0), " + got[2] + " chest(s) with loot (2-4), residents - " + mimics + " mimics (2-4), " + drones + " sentry drones (2-3), " + outside
                + " chunk(s) past the ring loaded (0)");
    }

    /** Loads the chunks of `box` and a ring round them in one go, counting every chunk loaded meanwhile; @return how many lay past the ring */
    private int generate(int[] box) {
        WorldTestSettlementSC.ChunkWatch watch = new WorldTestSettlementSC.ChunkWatch();
        watch.start(w);
        MinecraftForge.EVENT_BUS.register(watch);
        try {
            for (int cx = box[0] - 1; cx <= box[2] + 1; cx++) {
                for (int cz = box[1] - 1; cz <= box[3] + 1; cz++) {
                    w.getChunkFromChunkCoords(cx, cz);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
        }
        int outside = 0;
        for (Long k : watch.loaded) {
            int x = (int) (k.longValue() >> 32), z = (int) k.longValue();
            if (x < box[0] - 1 || x > box[2] + 1 || z < box[1] - 1 || z > box[3] + 1) {
                outside++;
            }
        }
        return outside;
    }

    /** In the chunks of `box`, Y y0..y1: {ruin blocks, Minecraft stone (all heights), chests holding something}. */
    private int[] scan(int[] box, int y0, int y1) {
        int[] out = new int[3];
        for (int cx = box[0]; cx <= box[2]; cx++) {
            for (int cz = box[1]; cz <= box[3]; cz++) {
                Chunk ch = w.getChunkFromChunkCoords(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 256; y++) {
                            Block b = ch.getBlock(x, y, z);
                            out[1] += b == Blocks.stone ? 1 : 0;
                            if (y >= y0 && y <= y1 && b == RuinBlocksSC.ruin) {
                                out[0]++;
                            }
                        }
                    }
                }
                for (Object o : ch.chunkTileEntityMap.values()) {
                    if (o instanceof TileEntityChest && ((TileEntity) o).yCoord >= y0 && ((TileEntity) o).yCoord <= y1 && holds((IInventory) o)) {
                        out[2]++;
                    }
                }
            }
        }
        return out;
    }

    private static boolean holds(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i) != null) {
                return true;
            }
        }
        return false;
    }

    /** No chunk of [x0..x1] x [z0..z1] exists yet (on disk or loaded). */
    private boolean fresh(int x0, int z0, int x1, int z1) {
        IChunkLoader l = w.theChunkProviderServer.currentChunkLoader;
        for (int cx = x0 - 1; cx <= x1 + 1; cx++) {
            for (int cz = z0 - 1; cz <= z1 + 1; cz++) {
                if (w.theChunkProviderServer.chunkExists(cx, cz) || l instanceof AnvilChunkLoader && ((AnvilChunkLoader) l).chunkExists(w, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A structure start of `gen` at chunk (cx, cz), as the world would make it now (MapGenStructure.getStructureStart); null if it can't be asked. */
    private StructureStart startAt(MapGenStructure gen, int cx, int cz) {
        try {
            ReflectionHelper.setPrivateValue(MapGenBase.class, gen, w, "worldObj", "field_75039_c");
            Random r = new Random(seed);
            long a = r.nextLong(), b = r.nextLong();
            r.setSeed((long) cx * a ^ (long) cz * b ^ seed);
            ReflectionHelper.setPrivateValue(MapGenBase.class, gen, r, "rand", "field_75038_b");
            Method m = ReflectionHelper.findMethod(MapGenStructure.class, gen, new String[]{"getStructureStart", "func_75049_b"}, int.class, int.class);
            return (StructureStart) m.invoke(gen, Integer.valueOf(cx), Integer.valueOf(cz));
        } catch (Throwable t) {
            System.out.println("[SC-WORLDTEST] INFO combat: a structure start couldn't be asked for (" + t + ")");
            return null;
        }
    }

    // ------------------------------------------------------------------ 12. the integrator's additions: what the packages' own behaviour does

    /**
     * Added at the integration of Ф3 (the packages' TESTS lists): the Hollow Shell's reboot on a Pulse (ПБ-21), the Repair
     * Drone's repair and its falling apart without its Shell (§4.4), the swarm's cap in an area (§4.6), and the Executioner's
     * «Рассечение» (§4.5) - its 6 «Пробой» through a full Exo set with the shield on, broken off by a Point during the wind-up.
     */
    private void packagesBehaviour() {
        final FakePlayer shooter = fake("[SCTestGaps]", ax - 30, Y, az - 30);
        // ПБ-21: a Pulse that suppressed a piece - the Shell reboots for REBOOT_TICKS; a Point doesn't
        final EntityHollowShellSC pulsed = new EntityHollowShellSC(w), pointed = new EntityHollowShellSC(w);
        pulsed.setPosition(ax - 30.5, Y, az - 33.5);
        pointed.setPosition(ax - 32.5, Y, az - 33.5);
        dress(pulsed, ArmorSuit.EXO, true, 200F);
        dress(pointed, ArmorSuit.EXO, true, 200F);
        EmpSC.HitResult hit = pulsed.empHit(shooter, EmpMode.PULSE, false, new int[]{0, 1, 2, 3});
        pointed.empHit(shooter, EmpMode.POINT, false, new int[]{1});
        final boolean rebooted = hit != null && hit.anySuppressed() && pulsed.rebooting() && !pointed.rebooting();
        steps.add(new Step(EntityHollowShellSC.REBOOT_TICKS + 2, "the Shell's reboot") {
            @Override
            void run() {
                boolean over = !pulsed.rebooting();
                print(rebooted && over, "the Hollow Shell's reboot (ПБ-21): a Pulse that suppressed it - rebooting " + rebooted + " (a Point - not), over "
                        + EntityHollowShellSC.REBOOT_TICKS + " ticks later " + over);
            }
        });

        // §4.4: the drone bound to its Shell feeds its reserve; the Shell gone, it falls apart ORPHAN_TICKS later
        int dx = ax - 45, dz = az - 40;
        force(dx >> 4, dz >> 4, 1);                                         // ticking past the area's own tickets
        floor(dx, Y - 1, dz, 2);
        clearNatives(dx, dz, 20);
        final EntityHollowShellSC fed = spawned(new EntityHollowShellSC(w), dx + 0.5, Y, dz + 0.5);
        dress(fed, ArmorSuit.EXO, true, 200F);
        fed.mobArmor().reserve = 50F;
        final EntityRepairDroneSC drone = new EntityRepairDroneSC(w);
        drone.setLocationAndAngles(dx + 0.5, Y + EntityRepairDroneSC.HOVER_Y, dz + 1.9, 0F, 0F);
        w.spawnEntityInWorld(drone);
        made.add(drone);
        drone.bind(fed);
        final int fedAt = 20, killAt = fedAt + 70;
        steps.add(new Step(fedAt + 60, "the Repair Drone") {
            @Override
            void run() {
                float got = fed.mobArmor().reserve - 50F;
                print(got >= EntityRepairDroneSC.REPAIR_UNITS - 0.01F && drone.isEntityAlive(), "the Repair Drone (§4.4): bound to a Hollow Shell, it fed "
                        + n(got) + " units in 4 s (+" + n(EntityRepairDroneSC.REPAIR_UNITS) + " each " + EntityRepairDroneSC.REPAIR_EVERY + " ticks)");
            }
        });
        steps.add(new Step(killAt, "the Repair Drone") {
            @Override
            void run() {
                fed.setDead();
            }
        });
        steps.add(new Step(killAt + EntityRepairDroneSC.ORPHAN_TICKS / 2, "the Repair Drone") {
            @Override
            void run() {
                droneHalfway = drone.isEntityAlive();
            }
        });
        steps.add(new Step(killAt + EntityRepairDroneSC.ORPHAN_TICKS + 40, "the Repair Drone") {
            @Override
            void run() {
                boolean gone = !drone.isEntityAlive();
                print(droneHalfway && gone, "the Repair Drone (§4.4): its Shell dead - still there " + (EntityRepairDroneSC.ORPHAN_TICKS / 2) + " ticks on "
                        + droneHalfway + ", fallen apart " + EntityRepairDroneSC.ORPHAN_TICKS + " ticks on " + gone);
            }
        });

        // §4.6: never more than SWARM_CAP particles in an area, whatever is asked for
        int sx = ax - 45, sz = az - 10;
        force(sx >> 4, sz >> 4, 1);
        int sy = ground(sx, sz) + 2;
        Random rand = new Random(33L);
        int asked = 0, got = 0;
        for (int i = 0; i < 3; i++) {
            asked += EntityNaniteSC.SWARM_MAX;
            got += EntityNaniteSC.spawnSwarm(w, sx + 0.5, sy, sz + 0.5, EntityNaniteSC.SWARM_MAX, rand);
        }
        int inArea = EntityNaniteSC.inArea(w, sx + 0.5, sz + 0.5);
        for (Object o : w.getEntitiesWithinAABB(EntityNaniteSC.class, AxisAlignedBB.getBoundingBox(sx - 40, 0, sz - 40, sx + 40, 256, sz + 40))) {
            if (!made.contains(o)) {
                made.add((Entity) o);
            }
        }
        print(got > 0 && got <= EntityNaniteSC.SWARM_CAP && inArea <= EntityNaniteSC.SWARM_CAP, "the swarm's cap (§4.6): " + asked + " particles asked for in"
                + " three swarms at one spot - " + got + " made, " + inArea + " in its area (" + EntityNaniteSC.SWARM_CAP + " at most)");

        // §4.5: «Рассечение» - 6 «Пробой» through a full Exo set with the shield on; a Point during the wind-up breaks it off
        cleave(ax - 30, az - 46, false);
        cleave(ax - 30, az - 58, true);
        aimedByAi(ax - 15, az - 46);
    }

    // ------------------------------------------------------------------ 12. the wardens' suit (2026-10-18, ПГ-15..ПГ-20)

    /** A stray warden of `rank` dressed by its spawn egg's path (EntityWardenSC.equip), never spawned, never ticked. */
    private EntityWardenSC warden(NpcKindSC rank, int x, int z) {
        EntityWardenSC g = (EntityWardenSC) SettlementSC.newMember(w, rank);
        g.setLocationAndAngles(x + 0.5, Y, z + 0.5, 180F, 0F);
        g.onSpawnWithEgg(null);
        made.add(g);
        return g;
    }

    /**
     * ПГ-15..ПГ-20 on stray wardens (no settlement - nobody to provoke; WorldTestSettlementSC checks that with the dome's own): a
     * Sentry survives 8 armour-piercing blows of a Quantum blade (before: 40 HP and its plating pierced - dead after 2), each taking
     * 4 off its health and 16 off its reserve; a Captain's shield takes 8 absolute blows of an Exo blade whole, «Пробой» passes it; a
     * Pulse at a Sentry - not refused any more - suppresses all four pieces, heats the suit by the player's rule and lets the same blow
     * take 8 (iron's 60%); a FakePlayer's Siphon (EmpGunLogicSC, 3 s) drains a Sentry's reserve and delivers nothing (vented).
     */
    private void wardenSuit() {
        int x = ax + 50, z = az;
        EntityHollowShellSC hitter = new EntityHollowShellSC(w);
        hitter.setPosition(x + 0.5, Y, z - 6.5);
        made.add(hitter);
        float blade = BladeType.QUANTUM.onDamage;
        DamageSource pierce = new net.minecraft.util.EntityDamageSource("mob", hitter).setDamageBypassesArmor();
        // a Sentry and the piercing blade
        EntityWardenSC sentry = warden(NpcKindSC.SENTRY, x + 3, z - 3);
        float r0 = sentry.mobArmor().reserve, h0 = sentry.getHealth();
        int landed = 0;
        for (int i = 0; i < 8; i++) {
            sentry.hurtResistantTime = 0;
            landed += sentry.attackEntityFrom(pierce, blade) ? 1 : 0;
        }
        float hp = sentry.getHealth(), lost = r0 - sentry.mobArmor().reserve;
        float share = MobArmorMathSC.absorbShare(sentry.mobArmor().points());
        boolean survives = landed == 8 && sentry.isEntityAlive() && Math.abs(hp - (h0 - 8 * blade * (1F - share))) < 0.6F
                && Math.abs(lost - 8 * blade * share) < 0.6F;
        print(survives, "the wardens' suit (ПГ-15, ПГ-16): a Sentry (" + n(h0) + " HP, reserve " + n(r0) + ") took 8 armour-piercing Quantum blade"
                + " blows of " + n(blade) + " (before ПГ-16 dead after 2) - alive " + sentry.isEntityAlive() + ", HP " + n(hp) + " (want "
                + n(h0 - 8 * blade * (1F - share)) + "), the reserve " + n(lost) + " less (want " + n(8 * blade * share) + ")");
        // a Captain's shield and the Exo blade's absolute damage, «Пробой» through it
        EntityWardenSC captain = warden(NpcKindSC.CAPTAIN, x - 3, z - 3);
        DamageSource absolute = new net.minecraft.util.EntityDamageSource("mob", hitter).setDamageBypassesArmor().setDamageIsAbsolute();
        float c0 = captain.mobArmor().reserve, ch = captain.getHealth();
        for (int i = 0; i < 8; i++) {
            captain.hurtResistantTime = 0;
            captain.attackEntityFrom(absolute, BladeType.EXO.onDamage);
        }
        float took = c0 - captain.mobArmor().reserve;
        boolean shield = captain.getHealth() == ch && Math.abs(took - 8 * BladeType.EXO.onDamage) < 0.6F;
        captain.hurtResistantTime = 0;
        captain.attackEntityFrom(BreakdownDamageSC.of(hitter), EntityWardenSC.HEAVY_BLOW);
        boolean through = Math.abs(ch - EntityWardenSC.HEAVY_BLOW - captain.getHealth()) < 0.01F;
        print(shield && through, "the wardens' suit (ПГ-15): a Captain's shield took 8 absolute Exo blade blows of " + BladeType.EXO.onDamage + " whole - HP "
                + n(captain.getHealth()) + " of " + n(ch) + ", the reserve " + n(took) + " less " + shield + "; " + EntityWardenSC.HEAVY_BLOW
                + " of Breakdown passed it into the health " + through);
        if (!com.sc.util.ConfigSC.empEnabled || !SubstrateConfigSC.empMobTargets) {
            System.out.println("[SC-WORLDTEST] SKIP combat: PE-1 on a warden - weapons.pe1.enabled or dimension.empMobTargets is off");
            return;
        }
        // a Pulse at a Sentry: allowed, all four pieces suppressed, the heat, the blow on iron's points
        EntityWardenSC pulsed = warden(NpcKindSC.SENTRY, x + 6, z - 3);
        FakePlayer pe = fake("[SCTestWardenPulse]", x + 6, Y, z - 8);
        int refused = com.sc.emp.EmpMobSC.refusal(pulsed, EmpMode.PULSE);
        EmpSC.HitResult r = pulsed.empHit(pe, EmpMode.PULSE, false, new int[]{0, 1, 2, 3});
        int sup = 0;
        for (int i = 0; i < 4; i++) {
            sup += pulsed.mobArmor().suppressed(i) ? 1 : 0;
        }
        ArmorSuit cs = MobArmorSC.pieceSuit(pulsed, 1);
        int wantHeat = EmpMathSC.heat(cs.heatCapacity, EmpMode.PULSE, false, false, false, 0, ConfigSC.empHeatMultiplier);
        float ph = pulsed.getHealth();
        pulsed.hurtResistantTime = 0;
        pulsed.attackEntityFrom(pierce, blade);
        float pTook = ph - pulsed.getHealth(), want = blade * (1F - MobArmorMathSC.absorbShare(WardenSuitSC.setPoints(ArmorSuit.NANO, true)));
        boolean pulseOk = refused == EmpNetSC.OK && r.anyArmour && sup == 4 && pulsed.suit().heat == wantHeat && Math.abs(pTook - want) < 0.01F;
        print(pulseOk, "the wardens' suit (ПГ-20): a Pulse at a Sentry - refusal " + refused + " (0 = OK), " + sup + " of 4 pieces suppressed, heat +"
                + pulsed.suit().heat + " (want " + wantHeat + "); the same blade blow then took " + n(pTook) + " HP (want " + n(want) + ", iron's 60%)");
        if (!SubstrateConfigSC.siphonMobs) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the Siphon on a warden - dimension.siphonMobs is off");
            return;
        }
        // the Siphon at a Sentry: the reserve drains, nothing is delivered (the gun only paid the start, the shooter's empty suit still empty)
        final EntityWardenSC drained = warden(NpcKindSC.SENTRY, x, z + 4);
        list(drained);
        final FakePlayer sh = fake("[SCTestWardenSiphon]", x, Y, z);
        wear(sh, ArmorSuit.QUANTUM, 0, false);
        final ItemStack[] gun = {new ItemStack(ModItems.empGun)};
        ItemEmpGunSC.setCharge(gun[0], ItemEmpGunSC.capacityOf(gun[0]));
        ItemEmpGunSC.setMode(gun[0], EmpMode.SIPHON);
        ItemEmpGunSC.setHeat(gun[0], 0, w.getTotalWorldTime());
        sh.inventory.currentItem = 0;
        sh.inventory.mainInventory[0] = gun[0];
        final float before = drained.mobArmor().reserve;
        final int gunBefore = ItemEmpGunSC.chargeOf(gun[0]);
        final int span = 60;
        steps.add(new Step(2, "the Siphon on a warden") {
            @Override
            void run() {
                ItemStack held = com.sc.emp.EmpGunLogicSC.rightClick(gun[0], w, sh);
                if (held != null && held != gun[0]) {
                    gun[0] = held;
                    sh.inventory.mainInventory[0] = held;
                }
            }
        });
        for (int i = 1; i <= span; i++) {
            final int tick = i;
            steps.add(new Step(2 + i, "the Siphon on a warden") {
                @Override
                void run() {
                    com.sc.emp.EmpGunLogicSC.usingTick(gun[0], sh, tick);
                    com.sc.emp.EmpGunLogicSC.playerTick(sh);
                }
            });
        }
        steps.add(new Step(span + 4, "the Siphon on a warden") {
            @Override
            void run() {
                boolean running = sh.isUsingItem();
                com.sc.emp.EmpGunLogicSC.stoppedUsing(gun[0], w, sh, span);
                sh.clearItemInUse();
                float drawn = before - drained.mobArmor().reserve;
                int gunAfter = ItemEmpGunSC.chargeOf(gun[0]);
                long suit = 0;
                for (int t = 0; t < 4; t++) {
                    ItemStack st = ArmorLogicSC.piece(sh, t);
                    suit += st == null ? 0 : ItemArmorSC.chargeOf(st);
                }
                boolean ok = running && drawn > 50F && gunAfter == gunBefore - EmpMathSC.EU_SIPHON_START && suit == 0L;
                print(ok, "the wardens' suit (ПГ-20): a FakePlayer's Siphon at a Sentry for " + span + " ticks - running " + running + ", its reserve "
                        + n(drawn) + " units less (" + (long) (drawn * ArmorSuit.NANO.euPerDamage) + " EU), delivered: the gun " + (gunAfter - gunBefore)
                        + " EU (want -" + EmpMathSC.EU_SIPHON_START + ", the start only), the shooter's suit " + suit + " EU (want 0) - all vented");
                unlist(drained);
            }
        });
    }

    /**
     * §3.2 through the AI (package A's EntityAIEmpShooterSC, package M's wiring): a carrier loaded from NBT gets its ПЭ-1 task
     * back, charges a Point at its attack target (the telegraph ACT_EMP_POINT) and fires it - the chestplate suppressed.
     */
    private void aimedByAi(int x, int z) {
        if (!EmpMobGunSC.allowed()) {
            System.out.println("[SC-WORLDTEST] SKIP combat: the carrier's ПЭ-1 task - the mob's ПЭ-1 is off");
            return;
        }
        force(x >> 4, z >> 4, 1);
        floor(x, Y - 1, z, 2);
        clearNatives(x, z, 20);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        carrier(x + 0.5, z + 0.5).writeToNBT(tag);
        final EntityJammerSC jam = new EntityJammerSC(w);
        jam.readFromNBT(tag);                                               // EntityJammerSC.readEntityFromNBT puts the task back
        spawned(jam, x + 0.5, Y, z + 0.5);
        final FakePlayer target = fake("[SCTestAimed]", x, Y, z + 10);
        wear(target, ArmorSuit.NANO, 100, false);
        final boolean[] cue = new boolean[1];
        // its look for a shot (once in SIGHT_CHECK) and the Point's charge-up, with room to spare
        final int span = com.sc.mob.ai.EntityAIEmpShooterSC.SIGHT_CHECK + EmpMobGunSC.chargeTicks(EmpMode.POINT, false) + 20;
        for (int i = 1; i <= span; i++) {
            final int k = i;
            steps.add(new Step(30 + i, "the carrier's ПЭ-1 task") {
                @Override
                void run() {
                    jam.setAttackTarget(target);
                    cue[0] |= jam.mobAction() == com.sc.mob.IMobActionSC.ACT_EMP_POINT;
                    if (k == span) {
                        boolean hit = EmpSC.isSuppressed(target, 1);
                        print(cue[0] && hit, "the carrier's ПЭ-1 task (EntityAIEmpShooterSC, ПБ-17): a Jammer carrier loaded from NBT - the Point's"
                                + " telegraph seen " + cue[0] + ", then the FakePlayer's Nano chestplate suppressed " + hit + " within " + span + " ticks");
                        EmpSC.clearSuppression(target);
                    }
                }
            });
        }
    }

    private boolean droneHalfway;

    /** One Executioner on a glass floor, its target a test player 2.5 blocks off in full Exo (shield on); `point` - a Point at the wind-up. */
    private void cleave(int x, int z, final boolean point) {
        force(x >> 4, z >> 4, 1);
        floor(x, Y - 1, z, 3);
        clearNatives(x, z, 16);
        final EntityExecutionerSC ex = spawned(new EntityExecutionerSC(w), x + 0.5, Y, z + 0.5);
        dress(ex, ArmorSuit.EXO, true, 200F);
        ex.setCurrentItemOrArmor(0, null);                                  // the cleave alone (no blade blow can reach 2.5 blocks anyway)
        final HurtablePlayer p = hurtable(point ? "[SCTestCleaveP]" : "[SCTestCleave]", x, Y, z + 2);
        p.setLocationAndAngles(x + 0.5, Y, z + 3.0, 180F, 0F);
        wear(p, ArmorSuit.EXO, 100, true);
        final float h0 = p.getMaxHealth();
        final FakePlayer shooter = fake("[SCTestGaps]", x - 6, Y, z);
        final boolean[] wound = new boolean[1], broke = new boolean[1];
        final int span = 3 + EntityExecutionerSC.WINDUP_TICKS + 20;
        steps.add(new Step(30, "the Executioner") {
            @Override
            void run() {
                p.setHealth(h0);
                p.hurtResistantTime = 0;
                list(p);                                                    // only now: findable by its targeting and the cone's search
                ex.setAttackTarget(p);
            }
        });
        for (int i = 1; i <= span; i++) {
            final int k = i;
            steps.add(new Step(30 + i, "the Executioner") {
                @Override
                void run() {
                    p.hurtResistantTime = 0;
                    ex.setAttackTarget(p);
                    if (ex.windingUp() && !wound[0]) {
                        wound[0] = true;
                        if (point) {
                            ex.empHit(shooter, EmpMode.POINT, false, new int[]{1});
                            broke[0] = !ex.windingUp();
                        }
                    }
                    if (k == span) {
                        float lost = h0 - p.getHealth(), want = point ? 0F : onPlayer(w, BreakdownDamageSC.of(ex), EntityExecutionerSC.CLEAVE_DAMAGE);
                        boolean ok = wound[0] && Math.abs(lost - want) < 0.01F && (!point || broke[0]);
                        print(ok, "the Executioner's «Рассечение» (§4.5, ПБ-15): the wind-up seen " + wound[0] + (point ? "; a Point during it - broken off "
                                + broke[0] + ", the player in full Exo lost " + n(lost) + " HP (0)" : "; then " + n(lost) + " HP off a player in full Exo with"
                                + " the shield on (" + n(want) + " - " + EntityExecutionerSC.CLEAVE_DAMAGE + " at the difficulty " + w.difficultySetting + ")"));
                        p.setHealth(p.getMaxHealth());
                    }
                }
            });
        }
    }

    /** A glass floor (2r + 1 square) at y round (x, z), put back at the end. */
    private void floor(int x, int y, int z, int r) {
        w.getChunkFromBlockCoords(x, z);
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                place(x + i, y, z + j, Blocks.glass);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " combat: " + what);
    }

    private static String n(float v) {
        return String.valueOf(Math.round(v * 100F) / 100F);
    }

    /** Public static number `field` of class `cls` (another package's), or `plan`. */
    static float num(String cls, String field, float plan) {
        try {
            Object v = Class.forName(cls).getField(field).get(null);
            return v instanceof Number ? ((Number) v).floatValue() : plan;
        } catch (Throwable t) {
            return plan;
        }
    }

    /** Forces the chunks within `r` of chunk (cx, cz) for the test's time. */
    private void force(int cx, int cz, int r) {
        ForgeChunkManager.Ticket t = null;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (t == null || t.getChunkList().size() >= t.getChunkListDepth()) {
                    t = ForgeChunkManager.requestTicket(SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
                    if (t == null) {
                        return;
                    }
                    t.getModData().setString("Kind", "worldtest");          // ChunkLoaderSC releases it on a later load
                    tickets.add(t);
                }
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(x, z));
            }
        }
    }

    /** A clear line from `from`'s eyes to `to`'s middle (the blocks only - what EmpShotSC.inSight asks of a mob shooter). */
    private boolean sight(EntityLivingBase from, Entity to) {
        Vec3 a = Vec3.createVectorHelper(from.posX, from.posY + from.getEyeHeight(), from.posZ),
                b = Vec3.createVectorHelper(to.posX, to.posY + to.height / 2, to.posZ);
        return w.rayTraceBlocks(a, b) == null;
    }

    /** The top solid block's Y + 1 at (x, z) (its chunk loaded here). */
    private int ground(int x, int z) {
        w.getChunkFromBlockCoords(x, z);
        return w.getTopSolidOrLiquidBlock(x, z);
    }

    private void place(int x, int y, int z, Block b) {
        if (w.isAirBlock(x, y, z)) {
            w.setBlock(x, y, z, b, 0, 2);
            placed.add(new int[]{x, y, z});
        }
    }

    /** A cached FakePlayer of the test at (x, y, z) facing +Z, its ПЭ-1 state clean. */
    private FakePlayer fake(String name, int x, int y, int z) {
        FakePlayer p = FakePlayerFactory.get(w, new GameProfile(UUID.nameUUIDFromBytes(("sc-combat-" + name).getBytes()), name));
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        resetEmp(p);
        if (!fakes.contains(p)) {
            fakes.add(p);
        }
        return p;
    }

    /**
     * What `amount` of `src` comes to on a player after vanilla's difficulty scaling (EntityPlayer.attackEntityFrom: a mob's
     * blow - isDifficultyScaled - is 0 on Peaceful, half + 1 on Easy, x1.5 on Hard); the test server's own difficulty decides.
     */
    static float onPlayer(net.minecraft.world.World w, DamageSource src, float amount) {
        if (!src.isDifficultyScaled()) {
            return amount;
        }
        EnumDifficulty d = w.difficultySetting;
        return d == EnumDifficulty.PEACEFUL ? 0F : d == EnumDifficulty.EASY ? amount / 2F + 1F : d == EnumDifficulty.HARD ? amount * 3F / 2F : amount;
    }

    /** A test player that really takes damage (FakePlayer refuses it; the respawn protection cleared). Never spawned. */
    private HurtablePlayer hurtable(String name, int x, int y, int z) {
        HurtablePlayer p = new HurtablePlayer(w, new GameProfile(UUID.nameUUIDFromBytes(("sc-combat-" + name).getBytes()), name));
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, p, 0, "field_147101_bU");
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        resetEmp(p);
        fakes.add(p);
        return p;
    }

    /** As WorldTestEmpSC: FakePlayers are cached across runs - a clean ПЭ-1 state. */
    private static void resetEmp(EntityPlayer p) {
        EmpStateSC s = EmpSC.state(p);
        for (int i = 0; i < 4; i++) {
            s.until[i] = 0;
            s.immuneUntil[i] = 0;
        }
        s.boilMul = 0;
        s.boilUntil = 0;
        s.streamUntil = 0;
        s.drainBy = 0;
        s.lastHeatHit = 0;
        s.aimedUntil = 0;
        s.aimedBy = 0;
        s.rebootReadyAt = 0;
        EmpSC.save(p, s);
    }

    /** A full suit at `pct` charge, every tank full; `shield` - the chestplate's shield and explosion proofing on (Exo). */
    static void wear(EntityPlayer p, ArmorSuit suit, int pct, boolean shield) {
        for (int t = 0; t < 4; t++) {
            ItemStack s = new ItemStack(ModItems.ARMOR.get(suit)[t]);
            ItemArmorSC.setCharge(s, (int) ((long) ItemArmorSC.capacityOf(s) * pct / 100));
            for (ArmorGasSC.Gas g : ArmorGasSC.Gas.values()) {
                ArmorGasSC.setAmount(s, g, ArmorGasSC.capacity(s, g));
            }
            if (t == 1 && shield) {
                ItemArmorSC.setEnabled(s, ArmorFeature.SHIELD, true);
                ItemArmorSC.setEnabled(s, ArmorFeature.EXPLOSION_PROOF, true);
            }
            p.inventory.armorInventory[3 - t] = s;
        }
    }

    /** A mob dressed by hand (the plan's suit, 4/4), helium or not, a reserve of `reserve` units - the test's own, whatever M's onSpawnWithEgg does. */
    static void dress(EntityArmoredMobSC mob, ArmorSuit suit, boolean helium, float reserve) {
        for (int t = 0; t < 4; t++) {
            mob.setCurrentItemOrArmor(4 - t, new ItemStack(ModItems.ARMOR.get(suit)[t]));
        }
        MobArmorSC a = mob.mobArmor();
        a.helium = helium;
        a.emergency = false;
        a.spawnReserve(reserve, reserve);
    }

    /** WorldServer's id -> entity map: a creature never spawned goes in by hand where a ПЭ-1 hitscan must find it. */
    private static IntHashMap idMap(WorldServer w) {
        return (IntHashMap) ReflectionHelper.getPrivateValue(WorldServer.class, w, "entityIdMap", "field_73066_T");
    }

    /** Findable by a hitscan and by id, never ticked (not in the world's list) - as WorldTestEmpSC's target. */
    private void list(Entity e) {
        Chunk c = w.getChunkFromBlockCoords((int) Math.floor(e.posX), (int) Math.floor(e.posZ));
        c.addEntity(e);
        idMap(w).addKey(e.getEntityId(), e);
        if (!made.contains(e)) {
            made.add(e);
        }
    }

    private void unlist(Entity e) {
        if (w == null || e == null) {
            return;
        }
        Chunk c = w.getChunkFromBlockCoords((int) Math.floor(e.posX), (int) Math.floor(e.posZ));
        c.removeEntity(e);
        if (idMap(w).lookup(e.getEntityId()) == e && !w.loadedEntityList.contains(e)) {
            idMap(w).removeObject(e.getEntityId());
        }
    }

    /** Forge's FakePlayer is invulnerable; this one takes the blows (its respawn protection cleared by the test). */
    static final class HurtablePlayer extends FakePlayer {
        HurtablePlayer(WorldServer w, GameProfile profile) {
            super(w, profile);
        }

        @Override
        public boolean isEntityInvulnerable() {
            return false;
        }
    }
}
