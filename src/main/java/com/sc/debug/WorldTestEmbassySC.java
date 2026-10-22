package com.sc.debug;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.block.BlockDockSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.embassy.EmbassyBlocksSC;
import com.sc.embassy.EmbassyItemsSC;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyRepairSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.embassy.EmbassySC;
import com.sc.embassy.EmbassySiteSC;
import com.sc.embassy.EmbassyTicketSC;
import com.sc.embassy.FlightSC;
import com.sc.embassy.MapGenEmbassySC;
import com.sc.embassy.StructureEmbassyStartSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.RosterSC;
import com.sc.settle.SettleNetSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.ai.NpcLinkSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * PACKAGE D (docs/plan-substrate-tech-f6.md §10.2): Ф6's world test on a real server (-Dsc.worldtest=true, after WorldTestFinaleSC is
 * finished). Output "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO embassy: ...". The registration always; the rest SKIP when the embassies
 * don't generate (SettlementSC.embassiesGenerate) or the Overworld doesn't fit (EmbassySiteSC.worldFits).
 * <ol>
 * <li>registration: embassyRingSC and its TE, embassyFinder and its shapeless recipe, the structure names SCOwEmbassy, SCOwEmbPart,
 * SCOwEmbGround and package S's pieces;</li>
 * <li>the guaranteed embassy: its decision in the registry («E»), its distance to the spawn in the ring, its land fitting, siteAtChunk
 * of its chunk answering it;</li>
 * <li>the generation without a cascade: the guaranteed one's chunks made centre first (an arrival by teleport into a future embassy)
 * - the start made with the first chunk, not empty; then every chunk of its footprint and a ring round it - not one chunk loaded past
 * them (ChunkEvent.Load), every footprint chunk populated, the start's pieces there;</li>
 * <li>the city: the record (EMBASSY, dock zone 13, 30..34 slots, the points ring / vokzal / office); the floor on floorY in 64 columns
 * of the level disc, the blend band without a step back; not one log or plank in the cylinder, no leaves outside the greenhouse; the
 * core and its TE with the record's id, the ring's TE with it too, the Vokzal's beacon the record's dock, the Vokzal outside the dome;</li>
 * <li>old land: a grid embassy one chunk of whose footprint is made with embassy.embassies off - then its start is empty, no record;</li>
 * <li>the protection (governs(0)): protecting() inside, explosions in the cylinder and the Vokzal's zone leave the blocks, CheckSpawn of
 * a zombie in the cylinder DENY, a player's field next to the dome refused with «[Поселение-N]», a bridge end refused inside, not at
 * the beacon;</li>
 * <li>the roster: the core's seconds (settlementTickNow) - the settlers and guards made, the Ambassador the ELDER, two Dispatchers at the
 * office, their home round their post (OUTPOST_HOME); an embassy's settler carried into the Substrate - exiled, a Substrate one in the
 * Overworld too;</li>
 * <li>the Dispatcher at a Substrate dock: «Первый причал» gets his slot at the end (F6Disp) at its core's second, an Outpost none;</li>
 * <li>the flights (package R's helpers by name - FlightServerSC.execute, FlightSC.age / boardingZone / look / watchGuarantee; the
 * players test ones, never spawned): the level, «Враг», the day's third ride; INTRO at «Знакомый» - the soft admission's warning,
 * then confirmed: the price, the talon, the introductory flight noted, the window's countdown; the phases by ticks, the vortex the
 * boarding zone, the cooling refuses, a talon of an earlier day back; the round trip (its return half 3 days, the fourth - R_NO_BACK)
 * with BACK and HOME at the First Dock's Dispatcher; the rescue (a fresh mark yes, two days on no, a second time no); the guarantee and
 * its -30. A real move between worlds of a living player - only in game;</li>
 * <li>the trade (package T): at the embassy's Cartographer the Capsule yes, the Ruin Finder no; the Technologist's wafers yes, GaAs no;
 * the Quartermaster's bread four times, the fifth R_LIMIT; the ceiling: 590 + a supply contract -> 599; «Депеша» taken at the
 * Ambassador, handed in at a Substrate Elder - +30 over 599;</li>
 * <li>the Direction Finder: in the Overworld the guaranteed one's line; in the Substrate «no signal»;</li>
 * <li>the «Достройка»: a wall block taken off past the events - not put back with a player near (no force), put back by repair(force),
 * the core's TE not made twice;</li>
 * <li>the bans: no vanilla tree, lake or dungeon in the area;</li>
 * <li>the regression of the earlier world tests - their own lines in the log.</li>
 * </ol>
 * Built on the core alone: the packages' own helpers are asked for by name - a missing one fails its own lines. Every ticket released,
 * the blocks set back, the test's creatures never spawned. What the world generated stays (the test world's embassy, the empty start).
 */
public final class WorldTestEmbassySC {

    /** Never before this; and after WorldTestFinaleSC is finished. */
    static final int START = WorldTestFinaleSC.START + 600;
    /** Done (WorldTestEmbassyFlySC flies after it). */
    static volatile boolean finished;
    /** Waits this long for WorldTestFinaleSC, then begins anyway (INFO). */
    private static final int WAIT_MAX = 72000;
    private static final int TIMEOUT = 4000;
    /** The schedule (ticks from the start). */
    private static final int T_ROSTER = 20, T_TRADE = 40, T_FLIGHTS = 60, T_FINDER = 80, T_REPAIR = 100, T_END = 120;
    private static final Object NONE = new Object();
    private static final String SERVER = "com.sc.embassy.FlightServerSC", RUN = "com.sc.embassy.FlightRunSC";
    /** Package T's «Депеша домой» (ContractTableSC's key, docs/plan-substrate-tech-f6.md §2.4). */
    private static final String DISPATCH = "embDispatch";

    private int ticks, startAt, next;
    private boolean done, started;
    private WorldServer w, sub;
    private long seed;
    private SettlementRegistrySC reg;
    private EmbassySiteSC.Site site;
    private EmbassyLayoutSC.Plan plan;
    private SettlementRegistrySC.Settlement rec;
    private TileEntitySettlementCoreSC core;
    private final List<Step> steps = new ArrayList<Step>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final Set<String> missing = new TreeSet<String>();

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
        if (ticks < START || !WorldTestFinaleSC.finished && ticks < START + WAIT_MAX) {
            return;
        }
        try {
            if (!started) {
                started = true;
                startAt = ticks;
                if (!WorldTestFinaleSC.finished) {
                    System.out.println("[SC-WORLDTEST] INFO embassy: WorldTestFinaleSC never finished - beginning anyway");
                }
                start();
            }
            while (!done && next < steps.size() && ticks - startAt >= steps.get(next).at) {
                Step s = steps.get(next++);
                try {
                    s.run();
                } catch (Throwable t) {
                    print(false, s.name + " - exception " + t);
                    t.printStackTrace();
                }
            }
            if (!done && (next >= steps.size() || ticks - startAt > TIMEOUT)) {
                if (next < steps.size()) {
                    print(false, "timed out before " + steps.get(next).name);
                }
                finish();
            }
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
            finish();
        }
    }

    // ------------------------------------------------------------------ the schedule

    private void start() {
        w = MinecraftServer.getServer().worldServerForDimension(0);
        sub = SubstrateSC.active() ? MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId()) : null;
        try {
            registration();
        } catch (Throwable t) {
            print(false, "registration - exception " + t);
        }
        if (w == null || !SettlementSC.embassiesGenerate() || !EmbassySiteSC.worldFits(w)) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the embassies don't generate here (embassies " + SubstrateConfigSC.embassies + ", the dimension "
                    + (SubstrateSC.active() ? "on" : "off") + ", the Overworld fits " + (w != null && EmbassySiteSC.worldFits(w)) + ")");
            finish();
            return;
        }
        seed = w.getSeed();
        reg = SettlementRegistrySC.get(w);
        step(0, "guaranteed", new Runnable() {
            public void run() {
                guaranteed();
            }
        });
        step(0, "generation", new Runnable() {
            public void run() {
                generation();
            }
        });
        step(0, "city", new Runnable() {
            public void run() {
                city();
            }
        });
        step(0, "city pieces", new Runnable() {
            public void run() {
                cityPieces();
            }
        });
        step(0, "old land", new Runnable() {
            public void run() {
                oldLand();
            }
        });
        step(0, "protection", new Runnable() {
            public void run() {
                protection();
            }
        });
        step(0, "bans", new Runnable() {
            public void run() {
                bans();
            }
        });
        step(T_ROSTER, "roster", new Runnable() {
            public void run() {
                roster();
            }
        });
        step(T_ROSTER, "substrate dispatcher", new Runnable() {
            public void run() {
                substrateDispatcher();
            }
        });
        step(T_TRADE, "trade", new Runnable() {
            public void run() {
                trade();
            }
        });
        step(T_FLIGHTS, "flights", new Runnable() {
            public void run() {
                flights();
            }
        });
        step(T_FINDER, "finder", new Runnable() {
            public void run() {
                finder();
            }
        });
        step(T_REPAIR, "repair", new Runnable() {
            public void run() {
                repair();
            }
        });
        step(T_END, "regression", new Runnable() {
            public void run() {
                System.out.println("[SC-WORLDTEST] INFO embassy: the regression of Ф1..Ф5 (WorldTestEmpSC, WorldTestSubstrateSC, WorldTestSettlementSC,"
                        + " WorldTestCombatSC, WorldTestArmourerSC, WorldTestHazardSC, WorldTestWildSC, WorldTestFinaleSC) - their own lines above"
                        + (missing.isEmpty() ? "" : "; the packages' helpers asked for and not found: " + missing));
            }
        });
    }

    private void step(int at, final String name, final Runnable r) {
        steps.add(new Step(at, name) {
            @Override
            void run() {
                r.run();
            }
        });
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        for (ForgeChunkManager.Ticket t : tickets) {
            ForgeChunkManager.releaseTicket(t);
        }
        tickets.clear();
        w = null;
        sub = null;
        if (WorldTestEmbassyFlySC.registered) {
            System.out.println("[SC-WORLDTEST] INFO embassy: the fly-through comes next (WorldTestEmbassyFlySC); this test's end line follows its own");
        } else {
            System.out.println("[SC-WORLDTEST] DONE embassy");
        }
        finished = true;
    }

    // ------------------------------------------------------------------ 1. registration (§2.2, ПК-27)

    @SuppressWarnings("unchecked")
    private void registration() {
        StringBuilder bad = new StringBuilder();
        Object ring = Block.blockRegistry.getObject("SiliconAge:embassyRingSC");
        if (ring == null || ring == Blocks.air || ring != EmbassyBlocksSC.ring) {
            bad.append(" block embassyRingSC");
        }
        Map<String, Class<?>> tiles = ReflectionHelper.getPrivateValue(TileEntity.class, null, "field_145855_i", "nameToClassMap");
        if (tiles.get("SiliconAge.embassyRing") != TileEntityEmbassyRingSC.class) {
            bad.append(" tile SiliconAge.embassyRing");
        }
        Object finder = Item.itemRegistry.getObject("SiliconAge:embassyFinder");
        if (finder == null || finder != EmbassyItemsSC.finder) {
            bad.append(" item embassyFinder");
        }
        // the recipe: a compass, two Transistors, a Silicon Wafer - shapeless, vanilla's and this mod's only
        boolean recipe = false;
        ItemStack transistor = ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR), wafer = ModItems.siliconMaterial.stackOf(SiliconMaterial.SI_WAFER);
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            ItemStack out = r.getRecipeOutput();
            if (out == null || out.getItem() != EmbassyItemsSC.finder || !(r instanceof ShapelessRecipes)) {
                continue;
            }
            int compass = 0, tr = 0, wf = 0, other = 0;
            for (Object in : ((ShapelessRecipes) r).recipeItems) {
                ItemStack s = (ItemStack) in;
                if (s.getItem() == Items.compass) {
                    compass++;
                } else if (s.getItem() == transistor.getItem() && s.getItemDamage() == transistor.getItemDamage()) {
                    tr++;
                } else if (s.getItem() == wafer.getItem() && s.getItemDamage() == wafer.getItemDamage()) {
                    wf++;
                } else {
                    other++;
                }
            }
            recipe |= compass == 1 && tr == 2 && wf == 1 && other == 0;
        }
        if (!recipe) {
            bad.append(" the finder's recipe");
        }
        // the structure's names: every String of MapGenStructureIO's maps (their names differ between the dev and the reobf names)
        Set<String> names = new TreeSet<String>();
        for (Field f : MapGenStructureIO.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                f.setAccessible(true);
                for (Map.Entry<Object, Object> e : ((Map<Object, Object>) f.get(null)).entrySet()) {
                    for (Object o : new Object[]{e.getKey(), e.getValue()}) {
                        if (o instanceof String && ((String) o).startsWith("SCOw")) {
                            names.add((String) o);
                        }
                    }
                }
            } catch (Exception ex) {
                bad.append(" MapGenStructureIO ").append(f.getName());
            }
        }
        for (String n : new String[]{EmbassySC.START_NAME, EmbassySC.PIECE_PREFIX + "Part", EmbassySC.PIECE_PREFIX + "Ground"}) {
            if (!names.contains(n)) {
                bad.append(" structure ").append(n);
            }
        }
        int pieces = names.size() - 3;
        print(bad.length() == 0 && pieces > 0, "registration (§2.2, ПК-27): embassyRingSC and its TE, embassyFinder and its shapeless recipe (a compass, two"
                + " Transistors, a Silicon Wafer), the structure names " + names + " - package S's pieces " + pieces + " (> 0)" + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ 2. the guaranteed embassy (ПЕ-2)

    private void guaranteed() {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        EmbassySiteSC.Site g = EmbassySiteSC.guaranteed(w);
        NBTTagCompound e = reg.extra.getCompoundTag(EmbassySiteSC.EXTRA);
        if (g == null) {
            print(false, "guaranteed (ПЕ-2): none - the registry says " + (e.getBoolean("none") ? "«nowhere» (grid " + e.getInteger("grid") + ", "
                    + e.getInteger("min") + ".." + e.getInteger("max") + ")" : "nothing") + "; the rest of the test on the nearest grid embassy");
            for (EmbassySiteSC.Site s : EmbassySiteSC.near(w, sx, sz, 8000)) {
                if (reg.atCentre(0, s.x, s.z) != null || !EmbassySiteSC.explored(w, s)) {
                    site = s;
                    break;
                }
            }
            return;
        }
        site = g;
        double dist = Math.sqrt(g.distSq(sx, sz));
        boolean ring = dist >= SubstrateConfigSC.embassyRingMin - 1 && dist <= SubstrateConfigSC.embassyRingMax + 1
                || dist >= EmbassySiteSC.FALLBACK_MIN - 1 && dist <= EmbassySiteSC.FALLBACK_MAX + 1;
        boolean written = e.hasKey("x") && e.getInteger("x") == g.x && e.getInteger("z") == g.z && e.getInteger("r") == g.radius && e.getLong("s") == g.seed;
        // a decision stands: one chosen by older land rules (no "v", or another) is not judged by today's (the review's LAND_RULES 3)
        boolean today = e.getInteger("v") == EmbassySiteSC.LAND_RULES;
        String land = today ? EmbassySiteSC.landProblem(w, g.x, g.z) : null;
        EmbassySiteSC.Site at = EmbassySiteSC.siteAtChunk(w, g.chunkX(), g.chunkZ());
        boolean chunk = at != null && at.x == g.x && at.z == g.z && at.guaranteed;
        print(written && ring && land == null && chunk, "guaranteed (ПЕ-2): at " + g + ", " + Math.round(dist) + " from the spawn " + sx + " " + sz + " (the ring "
                + SubstrateConfigSC.embassyRingMin + ".." + SubstrateConfigSC.embassyRingMax + ", or " + EmbassySiteSC.FALLBACK_MIN + ".." + EmbassySiteSC.FALLBACK_MAX
                + ") " + ring + "; written into the registry («E») " + written + " (on the grid " + e.getBoolean("g") + "); its land "
                + (!today ? "not judged (chosen by the land rules " + e.getInteger("v") + ", today's " + EmbassySiteSC.LAND_RULES + ")" : land == null ? "fits" : land)
                + "; siteAtChunk of its chunk - it " + chunk);
    }

    // ------------------------------------------------------------------ 3. the generation without a cascade (ПЕ-4, ПЕ-6, ПЕ-7)

    private void generation() {
        if (site == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the generation - no embassy site near the spawn");
            return;
        }
        plan = EmbassyLayoutSC.plan(seed, site, site.floorY);
        int[] box = EmbassyLayoutSC.footprintChunks(plan);
        SettlementRegistrySC.Settlement before = reg.atCentre(0, site.x, site.z);
        boolean fresh = before == null && !EmbassySiteSC.explored(w, site);
        if (before == null && !fresh) {
            print(false, "generation (ПЕ-4): the embassy at " + site + " has no record and its land is explored already - its start is an empty one");
            return;
        }
        int cx = site.chunkX(), cz = site.chunkZ();
        Watch watch = new Watch(w, box);
        MinecraftForge.EVENT_BUS.register(watch);
        StructureEmbassyStartSC first;
        try {
            w.getChunkFromChunkCoords(cx, cz);                         // the centre first: an arrival by teleport into a future embassy
            MapGenEmbassySC gen = EmbassySC.mapGen(w);
            first = gen == null ? null : gen.startAt(w, cx, cz);
            for (int x = box[0] - 1; x <= box[2] + 1; x++) {
                for (int z = box[1] - 1; z <= box[3] + 1; z++) {
                    w.getChunkFromChunkCoords(x, z);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
        }
        int outside = watch.ours;                                     // past the asked chunks by this mod's code (another mod's: listed)
        int unpopulated = 0;
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                unpopulated += w.getChunkFromChunkCoords(x, z).isTerrainPopulated ? 0 : 1;
            }
        }
        force(box);
        rec = reg.atCentre(0, site.x, site.z);
        if (rec != null && (rec.floorY != plan.floorY || rec.radius != plan.radius)) {
            plan = EmbassyLayoutSC.planOf(rec, seed);                  // the record's own numbers
        }
        boolean started = first != null && first.isSizeableStructure() && rec != null && first.settlementId() == rec.id;
        int pieces = first == null ? 0 : first.getComponents().size();
        print(started && pieces > 1 && unpopulated == 0 && (!fresh || outside == 0), "generation (ПЕ-4, ПЕ-6, ПЕ-7) at " + site + ", " + (fresh ? "fresh land"
                : "built before") + ": the start made with the centre's chunk, not empty, the record's " + started + " (" + pieces + " pieces); "
                + ((box[2] - box[0] + 3) * (box[3] - box[1] + 3)) + " chunks asked, " + watch.loaded.size() + " loaded, " + watch.ours + " past them by this"
                + " mod's code" + (fresh ? " (0 - no cascade)" : " (built before: not measured)") + ", " + watch.foreign + " by another mod's or vanilla's"
                + " generator (not ours - listed)" + "; footprint chunks not populated " + unpopulated + " (0)"
                + (watch.past.length() == 0 ? "" : "; loaded past them:" + watch.past));
    }

    /**
     * ChunkEvent.Load counted (WorldTestSettlementSC.ChunkWatch's way). A chunk past the asked box + 1 is a cascade: this mod's when a
     * frame of its code (not the test's own) asked for it, else another mod's or vanilla's generator - each listed with the frames that
     * asked (the first ones past the chunk loading itself).
     */
    public static final class Watch {
        private final World world;
        private final int[] box;
        final Set<Long> loaded = new HashSet<Long>();
        final StringBuilder past = new StringBuilder();
        int ours, foreign;

        Watch(World world, int[] box) {
            this.world = world;
            this.box = box;
        }

        @SubscribeEvent
        public void onLoad(ChunkEvent.Load e) {
            if (e.world != world) {
                return;
            }
            Chunk c = e.getChunk();
            loaded.add(Long.valueOf((long) c.xPosition << 32 ^ c.zPosition & 0xFFFFFFFFL));
            if (c.xPosition >= box[0] - 1 && c.xPosition <= box[2] + 1 && c.zPosition >= box[1] - 1 && c.zPosition <= box[3] + 1) {
                return;
            }
            boolean mine = false;
            StringBuilder frames = new StringBuilder();
            int n = 0;
            for (StackTraceElement f : new Throwable().getStackTrace()) {
                String k = f.getClassName();
                if (k.startsWith("java.") || k.startsWith("sun.") || k.contains("EventBus") || k.contains("ASMEventHandler")
                        || k.startsWith("cpw.mods.fml.common.eventhandler") || k.startsWith("com.sc.debug.") || k.startsWith("net.minecraft.world.chunk.")
                        || k.startsWith("net.minecraft.world.gen.ChunkProviderServer") || k.startsWith("net.minecraftforge.common.chunkio")) {
                    continue;
                }
                mine |= k.startsWith("com.sc.");
                if (n++ < 10) {
                    frames.append(' ').append(k.substring(k.lastIndexOf('.') + 1)).append('.').append(f.getMethodName());
                }
            }
            if (mine) {
                ours++;
            } else {
                foreign++;
            }
            if (past.length() < 2500) {
                past.append(" [").append(c.xPosition).append(',').append(c.zPosition).append(mine ? " OURS:" : ":").append(frames).append(']');
            }
        }
    }

    // ------------------------------------------------------------------ 4. the city (ПЕ-5..ПЕ-8, ПЖ-13)

    private void city() {
        if (rec == null || plan == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the city - no embassy generated");
            return;
        }
        boolean record = SettlementSC.isEmbassy(rec) && rec.dim == 0 && rec.dockZone == EmbassyLayoutSC.DOCK_ZONE && rec.hasDock && rec.slots.size() >= 30
                && rec.slots.size() <= 34 && rec.point(EmbassyLayoutSC.P_RING) != null && rec.point(EmbassyLayoutSC.P_VOKZAL) != null
                && rec.point(EmbassyLayoutSC.P_OFFICE) != null;
        // the floor: 64 columns of the level disc - solid at floorY, nothing lower on top
        Random r = new Random(seed ^ 0xF6F100L);
        int floorOk = 0;
        for (int i = 0; i < 64; i++) {
            double a = r.nextDouble() * 2D * Math.PI, d = Math.sqrt(r.nextDouble()) * (plan.levelRadius - 1);
            int x = plan.coreX + (int) Math.round(Math.cos(a) * d), z = plan.coreZ + (int) Math.round(Math.sin(a) * d);
            Block b = w.getBlock(x, plan.floorY, z);
            floorOk += b.getMaterial().isSolid() && w.getHeightValue(x, z) - 1 >= plan.floorY ? 1 : 0;
        }
        // the blend band: along 16 rays from the level disc's edge out, no step back by more than 2
        int against = 0;
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8D;
            int[] tops = new int[EmbassyLayoutSC.BLEND + 2];
            for (int d = 0; d < tops.length; d++) {
                int x = plan.coreX + (int) Math.round(Math.cos(a) * (plan.levelRadius + d)), z = plan.coreZ + (int) Math.round(Math.sin(a) * (plan.levelRadius + d));
                tops[d] = w.getHeightValue(x, z) - 1;
            }
            int sign = Integer.signum(tops[tops.length - 1] - tops[0]);
            for (int d = 1; d < tops.length; d++) {
                if (sign != 0 && (tops[d] - tops[d - 1]) * sign < -2 || sign == 0 && Math.abs(tops[d] - tops[d - 1]) > 2) {
                    against++;
                    break;
                }
            }
        }
        // no log or plank in the cylinder, leaves only in the greenhouse
        int[] gh = null;
        for (SettlementLayoutSC.Lot l : plan.lots) {
            if (l.kind == EmbassyLayoutSC.LOT_GREENHOUSE) {
                gh = new int[]{l.x0, l.z0, l.x1, l.z1};
            }
        }
        int wood = 0, leaves = 0;
        long r2 = (long) rec.radius * rec.radius;
        for (int x = rec.x - rec.radius; x <= rec.x + rec.radius; x++) {
            for (int z = rec.z - rec.radius; z <= rec.z + rec.radius; z++) {
                if ((long) (x - rec.x) * (x - rec.x) + (long) (z - rec.z) * (z - rec.z) > r2) {
                    continue;
                }
                Chunk ch = w.getChunkFromBlockCoords(x, z);
                boolean inGreenhouse = gh != null && x >= gh[0] && x <= gh[2] && z >= gh[1] && z <= gh[3];
                for (int y = rec.floorY - SettlementSC.FOUNDATION; y <= Math.min(255, rec.floorY + rec.radius); y++) {
                    Block b = ch.getBlock(x & 15, y, z & 15);
                    if (b == Blocks.log || b == Blocks.log2 || b == Blocks.planks || b == Blocks.wooden_slab || b == Blocks.double_wooden_slab) {
                        wood++;
                    } else if ((b == Blocks.leaves || b == Blocks.leaves2) && !inGreenhouse) {
                        leaves++;
                    }
                }
            }
        }
        // the core and its TE, the ring's TE, the Vokzal's beacon
        TileEntity te = w.getTileEntity(rec.x, rec.y, rec.z);
        boolean coreOk = w.getBlock(rec.x, rec.y, rec.z) == SettlementBlocksSC.core && te instanceof TileEntitySettlementCoreSC
                && ((TileEntitySettlementCoreSC) te).settlementId() == rec.id;
        core = te instanceof TileEntitySettlementCoreSC ? (TileEntitySettlementCoreSC) te : null;
        TileEntityEmbassyRingSC ring = ring();
        boolean ringOk = ring != null && ring.embassyId() == rec.id;
        boolean beacon = false;
        for (int y = rec.dockY - 1; y <= rec.dockY + 1; y++) {
            beacon |= w.getBlock(rec.dockX, y, rec.dockZ) == SettlementBlocksSC.dock && w.getBlockMetadata(rec.dockX, y, rec.dockZ) == BlockDockSC.BEACON;
        }
        boolean outside = rec.distSq(rec.dockX + 0.5D, rec.dockZ + 0.5D) > (double) rec.radius * rec.radius && rec.dockX == plan.dockX && rec.dockZ == plan.dockZ;
        print(record && floorOk == 64 && against == 0 && wood == 0 && leaves == 0 && coreOk && ringOk && beacon && outside, "city (ПЕ-5..ПЕ-8, ПЖ-13): the"
                + " record (EMBASSY, zone 13, " + rec.slots.size() + " slots of 30..34, ring / vokzal / office) " + record + "; the floor at Y " + plan.floorY + " in "
                + floorOk + " of 64 columns of the level disc; the blend band steps back on " + against + " of 16 rays (0); logs / planks in the cylinder "
                + wood + ", leaves outside the greenhouse " + leaves + " (0 / 0); the core and its TE of record " + rec.id + " " + coreOk + ", the ring's TE "
                + ringOk + ", the beacon on the record's dock " + beacon + ", the Vokzal outside the dome " + outside);
    }

    /**
     * The integrator's (package S's numbers, its mock-world check): the Great Ring's plane holds a Gravity Coil in every cell of
     * EmbassyQuartersSC.ringCoil, the Vokzal its cryo tanks and the Dispatchers' two desks; every post of the record has room to stand
     * (feet and head free, ground under them); the beam's list for a player at the core has this ring (EmbassyBeamSC.beams).
     */
    private void cityPieces() {
        if (rec == null || plan == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the city's pieces - no embassy generated");
            return;
        }
        Block show = SettlementBlocksSC.showcase2;
        int a0 = plan.radius + EmbassyLayoutSC.VOKZAL_OUT + EmbassyLayoutSC.RING_BACK, rr = EmbassyLayoutSC.RING_R, mid = EmbassyLayoutSC.RING_DY;
        int coils = 0, want = 0;
        for (int p = -rr - 1; p <= rr + 1; p++) {
            for (int q = -rr - 1; q <= rr + 1; q++) {
                want += com.sc.embassy.EmbassyQuartersSC.ringCoil(p, q) ? 1 : 0;
                int x = plan.x(a0, p), y = plan.floorY + mid + q, z = plan.z(a0, p);
                coils += w.getBlock(x, y, z) == show && w.getBlockMetadata(x, y, z) == com.sc.block.BlockShowcase2SC.GRAVITY_COIL ? 1 : 0;
            }
        }
        int tanks = 0, desks = 0, beacon = plan.radius + EmbassyLayoutSC.VOKZAL_OUT, h = EmbassyLayoutSC.VOKZAL_HALF;
        for (int a = beacon - h; a <= beacon + h; a++) {
            for (int p = -h; p <= h; p++) {
                int x = plan.x(a, p), z = plan.z(a, p);
                for (int y = plan.floorY; y <= plan.floorY + 8; y++) {
                    if (w.getBlock(x, y, z) == show) {
                        int m = w.getBlockMetadata(x, y, z);
                        tanks += m == com.sc.block.BlockShowcase2SC.CRYO_TANK ? 1 : 0;
                        desks += m == com.sc.block.BlockShowcase2SC.DISPATCH_CONSOLE ? 1 : 0;
                    }
                }
            }
        }
        int cramped = 0;
        StringBuilder bad = new StringBuilder();
        for (SettlementRegistrySC.Slot sl : rec.slots) {
            Block feet = w.getBlock(sl.postX, sl.postY, sl.postZ), head = w.getBlock(sl.postX, sl.postY + 1, sl.postZ);
            Block under = w.getBlock(sl.postX, sl.postY - 1, sl.postZ);
            if (feet.getMaterial().blocksMovement() || head.getMaterial().blocksMovement() || under.isAir(w, sl.postX, sl.postY - 1, sl.postZ)) {
                cramped++;
                if (bad.length() < 300) {
                    bad.append(' ').append(sl.kind).append('@').append(sl.postX).append(',').append(sl.postY).append(',').append(sl.postZ);
                }
            }
        }
        boolean beam = false;
        int[] ring = rec.point(EmbassyLayoutSC.P_RING);
        for (int[] b : com.sc.embassy.EmbassyBeamSC.beams(w, rec.x + 0.5D, rec.z + 0.5D)) {
            beam |= ring != null && b[0] == ring[0] && b[1] == ring[1] && b[2] == ring[2];
        }
        print(want > 0 && coils == want && tanks > 0 && desks == 2 && cramped == 0 && beam, "city's pieces (ПЕ-8, ПЯ-3, ПЕ-12): the Great Ring's coils "
                + coils + " of " + want + "; the Vokzal's cryo tanks " + tanks + " (> 0), the Dispatchers' desks " + desks + " (2); posts with no room to stand "
                + cramped + " of " + rec.slots.size() + " (0)" + bad + "; the beam's list at the core has this ring " + beam);
    }

    /** The Great Ring's core under the ring's middle (a few blocks up or down), or null. */
    private TileEntityEmbassyRingSC ring() {
        for (int y = plan.floorY - 4; y <= plan.ringY + 1; y++) {
            TileEntity te = w.getTileEntity(plan.ringX, y, plan.ringZ);
            if (te instanceof TileEntityEmbassyRingSC) {
                return (TileEntityEmbassyRingSC) te;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 5. old land (ПЕ-4)

    /** How far from the spawn the old land's test looks for an unmade grid embassy (the review's land rules: about one rolled region in seven). */
    private static final int OLD_LAND_REACH = 20000;

    private void oldLand() {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        EmbassySiteSC.Site pick = null;
        for (EmbassySiteSC.Site s : EmbassySiteSC.near(w, sx, sz, OLD_LAND_REACH)) {     // each run uses one up (nearest first)
            if (!s.guaranteed && (site == null || s.x != site.x || s.z != site.z) && reg.atCentre(0, s.x, s.z) == null && !EmbassySiteSC.explored(w, s)
                    && EmbassySC.mapGen(w).startAt(w, s.chunkX(), s.chunkZ()) == null) {
                pick = s;
                break;
            }
        }
        if (pick == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: old land - no grid embassy on unexplored land within " + OLD_LAND_REACH + " of the spawn");
            return;
        }
        EmbassyLayoutSC.Plan p = EmbassyLayoutSC.plan(seed, pick, pick.floorY);
        int[] fc = EmbassyLayoutSC.footprintChunks(p);
        boolean was = SubstrateConfigSC.embassies;
        try {
            SubstrateConfigSC.embassies = false;                       // a world of 0.2.x: one chunk of its land made before any start
            w.getChunkFromChunkCoords(fc[0], fc[1]);
        } finally {
            SubstrateConfigSC.embassies = was;
        }
        w.getChunkFromChunkCoords(pick.chunkX(), pick.chunkZ());       // now its start is made - on explored ground
        StructureEmbassyStartSC st = EmbassySC.mapGen(w).startAt(w, pick.chunkX(), pick.chunkZ());
        boolean noRecord = reg.atCentre(0, pick.x, pick.z) == null;
        boolean empty = st != null && !st.isSizeableStructure() && st.getComponents().isEmpty();
        print(empty && noRecord, "old land (ПЕ-4): the grid embassy at " + pick + " - chunk " + fc[0] + " " + fc[1] + " of its land made with embassy.embassies"
                + " off, then its centre: the start " + (st == null ? "none" : st.getComponents().size() + " pieces") + " (an empty one) " + empty + ", no record " + noRecord);
    }

    // ------------------------------------------------------------------ 6. the protection (ПЕ-13, ПП-14)

    private void protection() {
        if (rec == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the protection - no embassy generated");
            return;
        }
        int[] sq = rec.point(SettlementRegistrySC.P_SQUARE);
        int x = sq == null ? rec.x + 6 : sq[0], z = sq == null ? rec.z : sq[2], y = rec.floorY;
        SettlementRegistrySC.Settlement prot = SettlementSC.protecting(w, x + 0.5D, y + 1, z + 0.5D);
        boolean governs = SettlementSC.governs(w) && prot != null && prot.id == rec.id;
        int before = solidAround(x, y + 2, z);
        w.newExplosion(null, x + 0.5D, y + 2.5D, z + 0.5D, 3F, false, true);
        int after = solidAround(x, y + 2, z);
        // the Vokzal's zone: by the beacon, outside the dome
        int vx = rec.dockX + 3, vz = rec.dockZ + 3, vy = rec.dockY;
        boolean inZone = rec.inDockZone(vx + 0.5D, vy + 2, vz + 0.5D);
        int vBefore = solidAround(vx, vy + 1, vz);
        w.newExplosion(null, vx + 0.5D, vy + 1.5D, vz + 0.5D, 3F, false, true);
        int vAfter = solidAround(vx, vy + 1, vz);
        // a zombie may not spawn in the cylinder
        EntityZombie zombie = new EntityZombie(w);
        zombie.setLocationAndAngles(x + 2.5D, y + 1, z + 2.5D, 0F, 0F);
        LivingSpawnEvent.CheckSpawn spawn = new LivingSpawnEvent.CheckSpawn(zombie, w, x + 2.5F, y + 1, z + 2.5F);
        MinecraftForge.EVENT_BUS.post(spawn);
        boolean denied = spawn.getResult() == Event.Result.DENY;
        // a player's Field Generator just outside the dome, away from the Vokzal
        int ax = rec.x - (rec.dockX - rec.x > 0 ? 1 : rec.dockX - rec.x < 0 ? -1 : 0) * (rec.radius + 4);
        int az = rec.z - (rec.dockZ - rec.z > 0 ? 1 : rec.dockZ - rec.z < 0 ? -1 : 0) * (rec.radius + 4);
        if (ax == rec.x && az == rec.z) {
            ax = rec.x + rec.radius + 4;
        }
        int ay = rec.floorY + 1;
        Block was = w.getBlock(ax, ay, az);
        int wasMeta = w.getBlockMetadata(ax, ay, az);
        String refused = null;
        w.setBlock(ax, ay, az, ModBlocks.fieldGeneratorSC, 0, 2);
        TileEntity te = w.getTileEntity(ax, ay, az);
        if (te instanceof TileEntityFieldGeneratorSC) {
            ((TileEntityFieldGeneratorSC) te).setPlacer("SCTestFieldOwner");
            refused = ((TileEntityFieldGeneratorSC) te).placedNearForeign();
        }
        w.setBlock(ax, ay, az, was, wasMeta, 2);
        boolean field = rec.owner().equals(refused);
        boolean bridge = SettlementSC.refusesBridgeEnd(w, rec.x, rec.floorY + 2, rec.z) && !SettlementSC.refusesBridgeEnd(w, rec.dockX, rec.dockY + 1, rec.dockZ);
        print(governs && before == after && inZone && vBefore == vAfter && denied && field && bridge, "protection (ПЕ-13, ПП-14): the Overworld governed, the"
                + " square protected by its embassy " + governs + "; an explosion over the square - solid blocks round it " + before + " -> " + after + ", in the"
                + " Vokzal's zone (" + inZone + ") " + vBefore + " -> " + vAfter + "; a zombie's CheckSpawn in the cylinder DENY " + denied + "; a Field"
                + " Generator next to the dome refused, owner \"" + refused + "\" " + field + "; a bridge end refused inside, not at the beacon " + bridge);
    }

    /** Column (x, z) is on one of the plan's lots (but the wall's: its box is the whole ring). */
    private static boolean inLot(List<SettlementLayoutSC.Lot> lots, int x, int z) {
        for (SettlementLayoutSC.Lot l : lots) {
            if (l.kind != EmbassyLayoutSC.LOT_WALL && x >= l.x0 && x <= l.x1 && z >= l.z0 && z <= l.z1) {
                return true;
            }
        }
        return false;
    }

    private int solidAround(int x, int y, int z) {
        int n = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    n += w.getBlock(x + dx, y + dy, z + dz).getMaterial().isSolid() ? 1 : 0;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 13. the bans (ПЕ-6)

    private void bans() {
        if (rec == null || plan == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the bans - no embassy generated");
            return;
        }
        int[] gh = null;
        for (SettlementLayoutSC.Lot l : plan.lots) {
            if (l.kind == EmbassyLayoutSC.LOT_GREENHOUSE) {
                gh = new int[]{l.x0, l.z0, l.x1, l.z1};
            }
        }
        int area = EmbassySiteSC.area(plan.radius), trees = 0, spawners = 0, water = 0, mineshafts = 0;
        StringBuilder where = new StringBuilder();
        List<SettlementLayoutSC.Lot> lots = plan.lots;
        long a2 = (long) area * area, l2 = (long) plan.levelRadius * plan.levelRadius;
        for (int x = plan.coreX - area; x <= plan.coreX + area; x++) {
            for (int z = plan.coreZ - area; z <= plan.coreZ + area; z++) {
                long d2 = (long) (x - plan.coreX) * (x - plan.coreX) + (long) (z - plan.coreZ) * (z - plan.coreZ);
                if (d2 > a2) {
                    continue;
                }
                Chunk ch = w.getChunkFromBlockCoords(x, z);
                boolean inGreenhouse = gh != null && x >= gh[0] && x <= gh[2] && z >= gh[1] && z <= gh[3];
                for (int y = 1; y < 256; y++) {
                    Block b = ch.getBlock(x & 15, y, z & 15);
                    if ((b == Blocks.log || b == Blocks.log2 || b == Blocks.leaves || b == Blocks.leaves2) && !inGreenhouse) {
                        trees++;
                    } else if (b == Blocks.mob_spawner) {
                        TileEntity te = ch.getTileEntityUnsafe(x & 15, y, z & 15);
                        String mob = te instanceof TileEntityMobSpawner ? ((TileEntityMobSpawner) te).func_145881_a().getEntityNameToSpawn() : "?";
                        if ("CaveSpider".equals(mob) && y < plan.floorY - SettlementSC.FOUNDATION) {
                            mineshafts++;                                   // a mineshaft's, deep under the city: not a ban of ПЕ-6
                        } else {
                            spawners++;
                            where.append(' ').append(mob).append('@').append(x).append(',').append(y).append(',').append(z);
                        }
                    } else if (d2 <= l2 && y >= plan.floorY - SettlementSC.FOUNDATION && (b == Blocks.water || b == Blocks.flowing_water || b == Blocks.lava
                            || b == Blocks.flowing_lava) && !inLot(lots, x, z)) {
                        water++;
                    }
                }
            }
        }
        print(trees == 0 && spawners == 0 && water == 0, "bans (ПЕ-6): in the area (radius " + area + ") vanilla trees' logs / leaves " + trees + ", dungeon"
                + " spawners " + spawners + where + ", water / lava on the level ground " + water + " (0 / 0 / 0); mineshafts' spawners deep under it "
                + mineshafts + " (not banned)");
    }

    // ------------------------------------------------------------------ 7. the roster (ПЖ-11..ПЖ-14, ПГ-21, ПП-14)

    private void roster() {
        if (rec == null || core == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the roster - no embassy core");
            return;
        }
        for (int i = 0; i < 16; i++) {
            core.settlementTickNow();                                   // the core's seconds now
        }
        int tuners = 0, guards = 0, alive = 0;
        EntityTunerSC ambassador = null;
        List<EntityTunerSC> dispatchers = new ArrayList<EntityTunerSC>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityTunerSC && ((EntityTunerSC) o).settlementId() == rec.id && !((Entity) o).isDead) {
                EntityTunerSC t = (EntityTunerSC) o;
                tuners++;
                if (t.kind() == NpcKindSC.ELDER) {
                    ambassador = t;
                } else if (t.kind() == NpcKindSC.DISPATCHER) {
                    dispatchers.add(t);
                }
            } else if (o instanceof EntityWardenSC && ((EntityWardenSC) o).settlementId() == rec.id && !((Entity) o).isDead) {
                guards++;
            }
        }
        for (SettlementRegistrySC.Slot sl : rec.slots) {
            alive += sl.state == SettlementRegistrySC.ALIVE ? 1 : 0;
        }
        StringBuilder disp = new StringBuilder();
        for (EntityTunerSC t : dispatchers) {
            SettlementRegistrySC.Slot sl = t.slot() >= 0 && t.slot() < rec.slots.size() ? rec.slots.get(t.slot()) : null;
            ChunkCoordinates home = t.getHomePosition();
            boolean office = sl != null && Math.hypot(sl.postX - rec.dockX, sl.postZ - rec.dockZ) <= EmbassyLayoutSC.DOCK_ZONE;
            boolean atHome = sl != null && t.hasHome() && home.posX == sl.postX && home.posZ == sl.postZ && Math.round(t.func_110174_bM()) == NpcLinkSC.OUTPOST_HOME;
            if (!office || !atHome) {
                disp.append(" [slot ").append(t.slot()).append(": office ").append(office).append(", home round the post ").append(atHome).append(']');
            }
        }
        // exiled: an embassy's settler carried into the Substrate; a Substrate one in the Overworld
        String exile = "skipped - no Substrate";
        if (sub != null) {
            EntityTunerSC away = new EntityTunerSC(sub);
            away.bindTo(rec, rec.slotsOf(NpcKindSC.TECHNOLOGIST).isEmpty() ? 0 : rec.slotsOf(NpcKindSC.TECHNOLOGIST).get(0));
            boolean a = NpcLinkSC.exiled(away) && away.isDead;
            SettlementRegistrySC.Settlement first = reg.first(SubstrateSC.dimId());
            boolean b = true;
            if (first != null && !first.slots.isEmpty()) {
                EntityTunerSC stray = new EntityTunerSC(w);
                stray.bindTo(first, 0);
                b = NpcLinkSC.exiled(stray) && stray.isDead;
            }
            EntityTunerSC home = new EntityTunerSC(w);
            home.bindTo(rec, 0);
            boolean c = !NpcLinkSC.exiled(home) && !home.isDead;
            exile = (a && b && c ? "ok" : "BAD") + " (into the Substrate " + a + ", a Substrate settler here " + b + ", at home stays " + c + ")";
        }
        boolean ok = tuners >= 18 && guards >= 1 && ambassador != null && dispatchers.size() == 2 && disp.length() == 0 && !exile.startsWith("BAD");
        print(ok, "roster (ПЖ-11..ПЖ-14, ПГ-21): after 16 of the core's seconds " + tuners + " settlers and " + guards + " guards of record " + rec.id
                + " (slots alive " + alive + " of " + rec.slots.size() + "), the Ambassador " + (ambassador != null) + ", the Dispatchers " + dispatchers.size()
                + " (2) at the office, their home round the post " + (disp.length() == 0 ? "ok" : "-" + disp) + "; exiled " + exile);
    }

    // ------------------------------------------------------------------ 8. the Dispatcher at a Substrate dock (ПЖ-11, ПР-8)

    private void substrateDispatcher() {
        if (sub == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the Substrate's Dispatcher - the dimension is off");
            return;
        }
        SettlementRegistrySC.Settlement first = reg.first(SubstrateSC.dimId());
        if (first == null || !first.hasDock) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the Substrate's Dispatcher - no «Первый причал» with a dock in the registry");
            return;
        }
        List<String> before = slotsText(first);
        boolean had = !first.slotsOf(NpcKindSC.DISPATCHER).isEmpty();
        if (!had) {
            for (int cx = (first.x >> 4) - 1; cx <= (first.x >> 4) + 1; cx++) {
                for (int cz = (first.z >> 4) - 1; cz <= (first.z >> 4) + 1; cz++) {
                    sub.getChunkFromChunkCoords(cx, cz);           // its window populated: the core stands
                }
            }
            TileEntity te = sub.getTileEntity(first.x, first.y, first.z);
            if (te instanceof TileEntitySettlementCoreSC) {
                ((TileEntitySettlementCoreSC) te).settlementTickNow();
            }
        }
        List<Integer> d = first.slotsOf(NpcKindSC.DISPATCHER);
        boolean one = d.size() == 1 && first.extra.getCompoundTag(RosterSC.EXTRA_S).getBoolean(RosterSC.F6_DISPATCHER);
        boolean atEnd = had || d.size() == 1 && d.get(0).intValue() == before.size() && slotsText(first).subList(0, before.size()).equals(before);
        int outposts = 0;
        StringBuilder bad = new StringBuilder();
        for (SettlementRegistrySC.Settlement s : reg.all()) {
            if (s.dim != SubstrateSC.dimId()) {
                continue;
            }
            int n = s.slotsOf(NpcKindSC.DISPATCHER).size();
            if (s.size == com.sc.settle.SettlementSizeSC.OUTPOST.ordinal()) {
                outposts++;
                if (n > 0 || RosterSC.dispatcherDue(s, SubstrateSC.dimId())) {
                    bad.append(" outpost ").append(s.id);
                }
            } else if (n > 1) {
                bad.append(" twice ").append(s.id);
            }
        }
        print(one && atEnd && bad.length() == 0, "the Substrate's Dispatcher (ПЖ-11): «Первый причал» (record " + first.id + ") " + (had ? "had him already"
                : "got him at its core's second") + " - one, F6Disp " + one + ", appended at the end with the old slots the same " + atEnd + "; " + outposts
                + " Outposts without one, no record with two" + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ 10. the trade (package T, ПО-23..ПО-27, ПД-7)

    private void trade() {
        if (rec == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the trade - no embassy");
            return;
        }
        EntityTunerSC cart = tuner(NpcKindSC.CARTOGRAPHER), tech = tuner(NpcKindSC.TECHNOLOGIST), qm = tuner(NpcKindSC.QUARTERMASTER), amb = tuner(NpcKindSC.ELDER);
        WorldTestArmourerSC.StationPlayer p = station(w, "trade", rec.x, rec.floorY + 1, rec.z);
        long today = SettlementSC.day(w);
        // the shop: the Capsule yes, the Ruin Finder no; the Technologist: the wafers yes, GaAs no
        String shop = "skipped - no Cartographer / Technologist";
        if (cart != null && tech != null) {
            dealer(p, ReputationSC.HONOURED_FROM);
            int capsule = deal(p, cart, TradeTableSC.CART_CAPSULE), finder = deal(p, cart, TradeTableSC.CART_FINDER);
            int wafers = deal(p, tech, TradeTableSC.TECH_WAFERS), gaas = deal(p, tech, TradeTableSC.TECH_GAAS);
            shop = (capsule == TradeServerSC.OK && finder == TradeServerSC.R_NOT_HERE && wafers == TradeServerSC.OK && gaas == TradeServerSC.R_NOT_HERE ? "ok" : "BAD")
                    + " (the Capsule " + capsule + ", the Ruin Finder " + finder + ", wafers " + wafers + ", GaAs " + gaas + ")";
        }
        // the Quartermaster: four bread deals, the fifth R_LIMIT
        String bread = "skipped - no Quartermaster";
        if (qm != null) {
            dealer(p, ReputationSC.ACQUAINTANCE_FROM);
            int[] codes = new int[5];
            for (int i = 0; i < 5; i++) {
                codes[i] = deal(p, qm, "qmBread");                    // package T's key
            }
            boolean four = true;
            for (int i = 0; i < 4; i++) {
                four &= codes[i] == TradeServerSC.OK;
            }
            bread = (four && codes[4] == TradeServerSC.R_LIMIT ? "ok" : "BAD") + " (" + java.util.Arrays.toString(codes) + ")";
        }
        // the ceiling: 590 + a supply contract -> 599; «Депеша» over it at a Substrate Elder
        String ceiling = "skipped - no Ambassador", dispatch = "skipped";
        if (amb != null) {
            WorldTestArmourerSC.StationPlayer c = null;
            String supply = null;
            boolean withDispatch = false;
            for (int i = 0; i < 80 && !withDispatch; i++) {         // a board with a supply contract and the dispatch, else a supply one
                WorldTestArmourerSC.StationPlayer cand = station(w, "contracts-" + i, rec.x, rec.floorY + 1, rec.z);
                List<String> board = embassyBoard(cand.getUniqueID(), today);
                String sup = null;
                for (String k : board) {
                    if (k.startsWith("embSupply")) {
                        sup = k;
                    }
                }
                if (sup != null && (c == null || board.contains(DISPATCH))) {
                    c = cand;
                    supply = sup;
                    withDispatch = board.contains(DISPATCH);
                }
            }
            if (c == null) {
                ceiling = "BAD (no supply contract on 60 players' boards today)";
            } else {
                SettlementRegistrySC.PlayerRec pr = dealer(c, 590);
                pr.contracts.clear();
                pr.contractsTaken = 0;
                int take = TradeServerSC.contract(c, amb, SettleNetSC.CONTRACT_TAKE, supply);
                ContractTableSC.Contract k = ContractTableSC.byKey(supply);
                ItemStack goods = k == null ? null : k.item != null ? k.item.copy() : firstOre(k.what);
                for (int left = k == null ? 0 : k.target; goods != null && left > 0; ) {
                    ItemStack g = goods.copy();
                    g.stackSize = Math.min(left, g.getMaxStackSize());
                    left -= g.stackSize;
                    c.inventory.addItemStackToInventory(g);
                }
                int turn = TradeServerSC.contract(c, amb, SettleNetSC.CONTRACT_TURN_IN, supply);
                ceiling = (take == TradeServerSC.OK && turn == TradeServerSC.OK && pr.reputation == ReputationSC.EMBASSY_CEILING ? "ok" : "BAD") + " (" + supply
                        + " taken " + take + ", handed in " + turn + ", Trust 590 -> " + pr.reputation + ")";
                clear(c);
                SettlementRegistrySC.Settlement first = sub == null ? null : reg.first(SubstrateSC.dimId());
                List<Integer> elders = first == null ? new ArrayList<Integer>() : first.slotsOf(NpcKindSC.ELDER);
                if (!withDispatch) {
                    dispatch = "skipped - «Депеша» not on that board today";
                } else if (elders.isEmpty()) {
                    dispatch = "skipped - no Substrate Elder";
                } else {
                    pr.contractsTaken = 0;
                    int t2 = TradeServerSC.contract(c, amb, SettleNetSC.CONTRACT_TAKE, DISPATCH);
                    EntityTunerSC elder = new EntityTunerSC(sub);
                    elder.bindTo(first, elders.get(0).intValue());
                    elder.setLocationAndAngles(first.x + 1.5D, first.floorY + 1, first.z + 0.5D, 0F, 0F);
                    WorldTestArmourerSC.StationPlayer there = station(sub, "contracts-dispatch", first.x, first.floorY + 1, first.z);
                    there = sameProfile(c, there);
                    int beforeRep = pr.reputation;
                    int done = TradeServerSC.contract(there, elder, SettleNetSC.CONTRACT_TURN_IN, DISPATCH);
                    dispatch = (t2 == TradeServerSC.OK && done == TradeServerSC.OK && pr.reputation == beforeRep + 30 && pr.reputation > ReputationSC.EMBASSY_CEILING
                            ? "ok" : "BAD") + " (taken " + t2 + ", handed in at " + SettlementSC.displayName(first).getUnformattedText() + " " + done + ", Trust "
                            + beforeRep + " -> " + pr.reputation + ")";
                }
            }
        }
        clear(p);
        boolean ok = !shop.startsWith("BAD") && !bread.startsWith("BAD") && !ceiling.startsWith("BAD") && !dispatch.startsWith("BAD");
        print(ok, "trade (package T, ПО-23..ПО-27, ПД-7) at " + SettlementSC.displayName(rec).getUnformattedText() + ": the shop " + shop + "; the Quartermaster's"
                + " bread x5 " + bread + "; the ceiling " + ceiling + "; «Депеша домой» " + dispatch);
    }

    /** The Ambassador's board (package T's ContractTableSC.embassyBoard by name), empty without it. */
    @SuppressWarnings("unchecked")
    private List<String> embassyBoard(UUID player, long day) {
        Object o = call(ContractTableSC.class, "embassyBoard", player, Long.valueOf(day));
        return o instanceof List ? (List<String>) o : new ArrayList<String>();
    }

    private static ItemStack firstOre(String ore) {
        List<ItemStack> l = net.minecraftforge.oredict.OreDictionary.getOres(ore);
        if (l.isEmpty()) {
            return null;
        }
        ItemStack s = l.get(0).copy();
        if (s.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) {
            s.setItemDamage(0);
        }
        return s;
    }

    /** One deal `key` at `t` with its price put in the inventory first (WorldTestFinaleSC's way). @return the code */
    private int deal(WorldTestArmourerSC.StationPlayer p, EntityTunerSC t, String key) {
        com.sc.settle.trade.TradeOfferSC o = TradeTableSC.byKey(key);
        if (o == null) {
            return -1;
        }
        SettlementRegistrySC.Settlement s = reg.byId(t.settlementId());
        int level = ReputationSC.level(reg.player(p.getUniqueID()).reputation);
        int[] counts = TradeRulesSC.playerCounts(o, level, TradeRulesSC.seedPct(s.seed, o.key));
        for (int i = 0; i < o.in.length && i < counts.length; i++) {
            ItemStack d = o.in[i] == null ? null : o.in[i].display();
            for (int left = counts[i]; d != null && left > 0; ) {
                ItemStack c = d.copy();
                c.stackSize = Math.min(left, c.getMaxStackSize());
                left -= c.stackSize;
                p.inventory.addItemStackToInventory(c);
            }
        }
        p.inventory.currentItem = 0;
        int code = TradeServerSC.execute(p, t, key, 1);
        clear(p);
        return code;
    }

    /** The test player with `reputation`, no enmity, the day's counters fresh (here and at the embassy). @return his record */
    private SettlementRegistrySC.PlayerRec dealer(WorldTestArmourerSC.StationPlayer p, int reputation) {
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        long today = SettlementSC.day(w);
        pr.reputation = reputation;
        pr.rollDay(today - 1);
        pr.rollDay(today);
        SettlementRegistrySC.LocalRec lr = rec.local(p.getUniqueID());
        lr.enemyUntil = 0L;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        clear(p);
        return pr;
    }

    /** A loaded live Tuner of `kind` of the embassy, or null. */
    private EntityTunerSC tuner(NpcKindSC kind) {
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityTunerSC && ((EntityTunerSC) o).kind() == kind && ((EntityTunerSC) o).settlementId() == rec.id && !((Entity) o).isDead) {
                return (EntityTunerSC) o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 9. the flights (package R, ПЯ-3..ПЯ-16)

    private void flights() {
        if (rec == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the flights - no embassy");
            return;
        }
        Class<?> server = cls(SERVER), run = cls(RUN);
        if (server == null || run == null || !Boolean.TRUE.equals(call(run, "channel"))) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the flights - " + (server == null ? "no FlightServerSC" : "the channel is closed (bridge.enabled,"
                    + " crossDimension, the dimension)"));
            return;
        }
        EntityTunerSC disp = tuner(NpcKindSC.DISPATCHER);
        if (disp == null) {
            disp = new EntityTunerSC(w);
            List<Integer> d = rec.slotsOf(NpcKindSC.DISPATCHER);
            if (!d.isEmpty()) {
                disp.bindTo(rec, d.get(0).intValue());
            }
            disp.setLocationAndAngles(rec.dockX + 0.5D, rec.dockY + 1, rec.dockZ + 1.5D, 0F, 0F);
        }
        long now = w.getTotalWorldTime(), today = SettlementSC.day(w), period = TradeRulesSC.period(today, 1);
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(10000L));        // a window of a former run: over
        StringBuilder bad = new StringBuilder();
        WorldTestArmourerSC.StationPlayer a = station(w, "flight-a", rec.dockX, rec.dockY + 1, rec.dockZ);
        SettlementRegistrySC.PlayerRec ar = passenger(a, 0);
        // the level: FIRST needs «Партнёр»
        int level = code(execute(a, disp, EmbassyRulesSC.RIDE_FIRST, 0, false, true, null));
        // «Враг» of the embassy
        passenger(a, ReputationSC.ACQUAINTANCE_FROM);
        rec.local(a.getUniqueID()).enemyUntil = now + ReputationSC.ENEMY_TICKS;
        int enemy = code(execute(a, disp, EmbassyRulesSC.RIDE_INTRO, 0, false, true, null));
        rec.local(a.getUniqueID()).enemyUntil = 0L;
        // INTRO at «Знакомый»: the soft admission warns (no armour, no capsule), then the same ride confirmed
        chips(a, 64);
        int[] warn = execute(a, disp, EmbassyRulesSC.RIDE_INTRO, 0, false, false, null);
        int[] intro = execute(a, disp, EmbassyRulesSC.RIDE_INTRO, 0, false, true, null);
        int price = EmbassyRulesSC.price(EmbassyRulesSC.RIDE_INTRO, ReputationSC.ACQUAINTANCE, rec.seed);
        EmbassyTicketSC.Talon k = EmbassyTicketSC.talon(ar);
        boolean introOk = code(warn) == EmbassyRulesSC.R_CONFIRM && code(intro) == EmbassyRulesSC.OK && intro[1] == price && held(a) == 64 - price
                && k != null && k.ride == EmbassyRulesSC.RIDE_INTRO && k.settlement == rec.id && EmbassyTicketSC.introUsed(ar);
        int countdown = FlightSC.phase(rec.id);
        // the window by ticks: the vortex - the boarding zone; the cooling refuses a newcomer; then idle
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(EmbassyRulesSC.COUNTDOWN_TICKS));
        int vortex = FlightSC.phase(rec.id);
        Object zone = call(FlightSC.class, "boardingZone", rec, w, Double.valueOf(plan.ringX + 0.5D), Double.valueOf(plan.floorY + 1D), Double.valueOf(plan.ringZ + 0.5D));
        Object above = call(FlightSC.class, "boardingZone", rec, w, Double.valueOf(plan.ringX + 0.5D), Double.valueOf(plan.floorY + 9D), Double.valueOf(plan.ringZ + 0.5D));
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(EmbassyRulesSC.VORTEX_TICKS));
        int cool = FlightSC.phase(rec.id);
        WorldTestArmourerSC.StationPlayer b = station(w, "flight-b", rec.dockX + 1, rec.dockY + 1, rec.dockZ);
        passenger(b, ReputationSC.ACQUAINTANCE_FROM);
        chips(b, 64);
        int busy = code(execute(b, disp, EmbassyRulesSC.RIDE_INTRO, 0, false, true, null));
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(EmbassyRulesSC.COOL_TICKS));
        int idle = FlightSC.phase(rec.id);
        boolean window = countdown == EmbassyRulesSC.PHASE_COUNTDOWN && vortex == EmbassyRulesSC.PHASE_VORTEX && Boolean.TRUE.equals(zone)
                && Boolean.FALSE.equals(above) && cool == EmbassyRulesSC.PHASE_COOL && busy == EmbassyRulesSC.R_BUSY && idle == EmbassyRulesSC.PHASE_IDLE;
        // a talon of an earlier day goes back at the player's look (its chips, the introductory flight open again)
        if (k != null) {
            EmbassyTicketSC.setTalon(ar, new EmbassyTicketSC.Talon(k.ride, k.target, k.settlement, k.chips, today - 1, ""));
        }
        int beforeRefund = held(a);
        call(FlightSC.class, "look", a);
        boolean refunded = EmbassyTicketSC.talon(ar) == null && held(a) == beforeRefund + price && !EmbassyTicketSC.introUsed(ar);
        // the day's third ride
        passenger(a, ReputationSC.PARTNER_FROM);
        ar.addGlobalCount(EmbassyRulesSC.COUNT_RIDES, period, 2);
        int limit = code(execute(a, disp, EmbassyRulesSC.RIDE_FIRST, 0, false, true, null));
        // the round trip: FIRST + its return half (3 days)
        passenger(a, ReputationSC.PARTNER_FROM);
        chips(a, 64);
        execute(a, disp, EmbassyRulesSC.RIDE_FIRST, 0, true, false, null);
        int[] round = execute(a, disp, EmbassyRulesSC.RIDE_FIRST, 0, true, true, null);
        int roundPrice = EmbassyRulesSC.roundTripPrice(EmbassyRulesSC.RIDE_FIRST, ReputationSC.PARTNER, rec.seed);
        long until = EmbassyTicketSC.backUntil(ar);
        boolean roundOk = code(round) == EmbassyRulesSC.OK && round[1] == roundPrice && EmbassyTicketSC.backEmbassy(ar) == rec.id
                && Math.abs(until - (now + EmbassyRulesSC.BACK_TICKS)) <= 200;
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(10000L));
        // back in the Substrate: BACK (the return half), its fourth day R_NO_BACK, HOME to the embassy flown out of
        String backs = "skipped - no Substrate dock with a Dispatcher";
        SettlementRegistrySC.Settlement first = sub == null ? null : reg.first(SubstrateSC.dimId());
        List<Integer> fd = first == null ? new ArrayList<Integer>() : first.slotsOf(NpcKindSC.DISPATCHER);
        if (!fd.isEmpty()) {
            EntityTunerSC dock = new EntityTunerSC(sub);
            dock.bindTo(first, fd.get(0).intValue());
            dock.setLocationAndAngles(first.dockX + 0.5D, first.dockY + 1, first.dockZ + 1.5D, 0F, 0F);
            WorldTestArmourerSC.StationPlayer s = sameProfile(a, station(sub, "flight-a", first.dockX, first.dockY + 1, first.dockZ));
            EmbassyTicketSC.setTalon(ar, null);
            ar.globalCounts = new NBTTagCompound();                    // a new day's flights (FIRST counted one)
            int back = code(execute(s, dock, EmbassyRulesSC.RIDE_BACK, 0, false, true, null));
            EmbassyTicketSC.setTalon(ar, null);
            ar.globalCounts = new NBTTagCompound();
            call(FlightSC.class, "age", Integer.valueOf(first.id), Long.valueOf(10000L));
            EmbassyTicketSC.setBack(ar, rec.id, sub.getTotalWorldTime() - 1);         // the fourth day: run out
            int noBack = code(execute(s, dock, EmbassyRulesSC.RIDE_BACK, 0, false, true, null));
            passenger(a, ReputationSC.ACQUAINTANCE_FROM);
            EmbassyTicketSC.setFrom(ar, rec.id);
            chips(s, 64);
            int[] home = execute(s, dock, EmbassyRulesSC.RIDE_HOME, 0, false, true, null);
            EmbassyTicketSC.Talon hk = EmbassyTicketSC.talon(ar);
            boolean homeOk = code(home) == EmbassyRulesSC.OK && hk != null && hk.target == rec.id;
            call(FlightSC.class, "age", Integer.valueOf(first.id), Long.valueOf(10000L));
            backs = (back == EmbassyRulesSC.OK && noBack == EmbassyRulesSC.R_NO_BACK && homeOk ? "ok" : "BAD") + " (BACK " + back + ", its fourth day "
                    + noBack + ", HOME " + code(home) + " to record " + (hk == null ? "?" : String.valueOf(hk.target)) + ")";
            clear(s);
        }
        // the rescue: a fresh mark yes, the same death again no, two days on no
        String rescue = "skipped - no Substrate";
        if (sub != null) {
            passenger(a, ReputationSC.ACQUAINTANCE_FROM);
            chips(a, 64);
            ar.hasDeath = true;
            ar.deathDim = SubstrateSC.dimId();
            ar.deathX = 100;
            ar.deathY = 80;
            ar.deathZ = 100;
            ar.deathAt = now - 100L;
            execute(a, disp, EmbassyRulesSC.RIDE_RESCUE, 0, false, false, null);
            int fresh = code(execute(a, disp, EmbassyRulesSC.RIDE_RESCUE, 0, false, true, null));
            boolean noted = EmbassyTicketSC.rescuedAt(ar) == ar.deathAt || EmbassyTicketSC.talon(ar) != null;
            EmbassyTicketSC.setTalon(ar, null);
            EmbassyTicketSC.setRescued(ar, ar.deathAt);
            call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(10000L));
            int again = code(execute(a, disp, EmbassyRulesSC.RIDE_RESCUE, 0, false, true, null));
            ar.deathAt = now - EmbassyRulesSC.RESCUE_TICKS - 1L;
            int late = code(execute(a, disp, EmbassyRulesSC.RIDE_RESCUE, 0, false, true, null));
            ar.hasDeath = false;
            rescue = (fresh == EmbassyRulesSC.OK && noted && again == EmbassyRulesSC.R_NO_MARK && late == EmbassyRulesSC.R_NO_MARK ? "ok" : "BAD") + " (fresh "
                    + fresh + ", again " + again + ", two days on " + late + ")";
        }
        // the guarantee: a «Партнёр» pays a «Чужак»'s ride; the friend becomes the embassy's «Враг» - the guarantor -30, once
        WorldTestArmourerSC.StationPlayer g = station(w, "flight-guarantor", rec.dockX, rec.dockY + 1, rec.dockZ + 1);
        WorldTestArmourerSC.StationPlayer f = station(w, "flight-friend", rec.dockX, rec.dockY + 1, rec.dockZ - 1);
        SettlementRegistrySC.PlayerRec gr = passenger(g, ReputationSC.PARTNER_FROM), fr = passenger(f, 0);
        EmbassyTicketSC.of(gr).removeTag(EmbassyTicketSC.GUARANTEE);
        chips(g, 64);
        execute(g, disp, EmbassyRulesSC.RIDE_FIRST, 0, false, false, f);
        int[] gu = execute(g, disp, EmbassyRulesSC.RIDE_FIRST, 0, false, true, f);
        EmbassyTicketSC.Talon fk = EmbassyTicketSC.talon(fr);
        boolean given = code(gu) == EmbassyRulesSC.OK && gu[1] == EmbassyRulesSC.guaranteePrice(EmbassyRulesSC.RIDE_FIRST, ReputationSC.PARTNER, rec.seed)
                && fk != null && g.getUniqueID().toString().equals(fk.payer) && EmbassyTicketSC.guarantee(gr) != null;
        rec.local(f.getUniqueID()).enemyUntil = w.getTotalWorldTime() + ReputationSC.ENEMY_TICKS;
        int repBefore = gr.reputation;
        Object penalty = call(FlightSC.class, "watchGuarantee", reg, gr, g);
        Object twice = call(FlightSC.class, "watchGuarantee", reg, gr, g);
        boolean guaranteed = given && Boolean.TRUE.equals(penalty) && Boolean.FALSE.equals(twice) && gr.reputation == repBefore + EmbassyRulesSC.GUARANTEE_PENALTY;
        rec.local(f.getUniqueID()).enemyUntil = 0L;
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(10000L));
        // the review's (ПЯ-13, ПЯ-6): the friend refuses the guarantor's talon - his day's ride and the guarantor's day's guarantee taken
        // back, the watch gone, the chips the guarantor's (owed: a test player is not on the server's list - paid at his look); a talon's
        // refund waits while its passenger lies dead and comes after the respawn
        long period2 = TradeRulesSC.period(SettlementSC.day(w), 1);
        gr = passenger(g, ReputationSC.PARTNER_FROM);
        fr = passenger(f, 0);
        EmbassyTicketSC.of(gr).removeTag(EmbassyTicketSC.GUARANTEE);
        chips(g, 64);
        int gHeld = held(g);
        execute(g, disp, EmbassyRulesSC.RIDE_FIRST, 0, false, false, f);
        int[] gu2 = execute(g, disp, EmbassyRulesSC.RIDE_FIRST, 0, false, true, f);
        int rides1 = fr.globalCount(EmbassyRulesSC.COUNT_RIDES, period2), guar1 = gr.globalCount(EmbassyRulesSC.COUNT_GUARANTEE, period2);
        NBTTagCompound dd = new NBTTagCompound();
        dd.setBoolean("decline", true);
        Object dr = call(cls(SERVER), "execute", f, disp, dd, null);
        int declined = dr instanceof int[] ? ((int[]) dr)[0] : -1;
        boolean undone = EmbassyTicketSC.talon(fr) == null && fr.globalCount(EmbassyRulesSC.COUNT_RIDES, period2) == rides1 - 1
                && gr.globalCount(EmbassyRulesSC.COUNT_GUARANTEE, period2) == guar1 - 1 && EmbassyTicketSC.guarantee(gr) == null;
        call(FlightSC.class, "look", g);
        boolean repaid = held(g) == gHeld;
        EmbassyTicketSC.setTalon(fr, new EmbassyTicketSC.Talon(EmbassyRulesSC.RIDE_FIRST, 0, rec.id, 5, SettlementSC.day(w) - 1, ""));
        int fHeld = held(f);
        f.setHealth(0F);
        call(FlightSC.class, "look", f);
        boolean waited = EmbassyTicketSC.talon(fr) != null && held(f) == fHeld;
        f.setHealth(f.getMaxHealth());
        call(FlightSC.class, "look", f);
        boolean after = EmbassyTicketSC.talon(fr) == null && held(f) == fHeld + 5;
        call(FlightSC.class, "age", Integer.valueOf(rec.id), Long.valueOf(10000L));
        for (WorldTestArmourerSC.StationPlayer x : new WorldTestArmourerSC.StationPlayer[]{a, b, g, f}) {
            SettlementRegistrySC.PlayerRec xr = reg.player(x.getUniqueID());
            EmbassyTicketSC.setTalon(xr, null);
            EmbassyTicketSC.clearBack(xr);
            clear(x);
        }
        reg.markDirty();
        boolean ok = level == EmbassyRulesSC.R_LEVEL && enemy == EmbassyRulesSC.R_ENEMY && introOk && window && refunded && limit == EmbassyRulesSC.R_LIMIT
                && roundOk && !backs.startsWith("BAD") && !rescue.startsWith("BAD") && guaranteed;
        if (!ok) {
            bad.append(" (said: ").append(a.said).append(')');
        }
        print(ok, "flights (package R, ПЯ-3..ПЯ-16) at " + SettlementSC.displayName(rec).getUnformattedText() + ": FIRST at «Чужак» " + level + " (R_LEVEL), INTRO as"
                + " the embassy's «Враг» " + enemy + " (R_ENEMY); INTRO at «Знакомый» - warned " + code(warn) + " (R_CONFIRM), confirmed " + code(intro) + " for "
                + intro[1] + " (" + price + "), the talon and the introductory flight noted " + introOk + "; the window: countdown / vortex / cool / idle "
                + countdown + " / " + vortex + " / " + cool + " / " + idle + ", the vortex the boarding zone " + zone + " (9 up " + above + "), a newcomer in"
                + " the cooling " + busy + " (R_BUSY) " + window + "; a talon of yesterday back at the look " + refunded + "; the third ride " + limit
                + " (R_LIMIT); the round trip " + code(round) + " for " + round[1] + " (" + roundPrice + "), its half good 3 days " + roundOk + "; at the First"
                + " Dock " + backs + "; the rescue " + rescue + "; the guarantee " + code(gu) + ", the friend's «Враг» - the guarantor " + repBefore + " -> "
                + gr.reputation + " once " + guaranteed + bad);
        print(code(gu2) == EmbassyRulesSC.OK && declined == EmbassyRulesSC.OK && undone && repaid && waited && after, "flights (the review's, ПЯ-13, ПЯ-6): a"
                + " guarantee " + code(gu2) + " refused by the friend " + declined + " - his day's ride " + rides1 + " and the guarantor's guarantee " + guar1
                + " taken back, the talon and the watch gone " + undone + ", the chips the guarantor's again at his look " + repaid + "; a stale talon of a"
                + " dead passenger waits " + waited + ", refunded after the respawn " + after);
    }

    /** FlightServerSC.execute(p, t, {ride, target, round, confirm}, friend) by name: {code, chips, ...}; {-1, 0} if it isn't there. */
    private int[] execute(WorldTestArmourerSC.StationPlayer p, EntityTunerSC t, int ride, int target, boolean round, boolean confirm, WorldTestArmourerSC.StationPlayer friend) {
        NBTTagCompound d = new NBTTagCompound();
        d.setInteger("ride", ride);
        d.setInteger("target", target);
        d.setBoolean("round", round);
        d.setBoolean("confirm", confirm);
        Object r = call(cls(SERVER), "execute", p, t, d, friend);
        return r instanceof int[] && ((int[]) r).length >= 2 ? (int[]) r : new int[]{-1, 0};
    }

    private static int code(int[] r) {
        return r == null || r.length == 0 ? -1 : r[0];
    }

    /** A flight test player fresh: `reputation`, no ride counted today, no flight record, no enmity at the embassy, nothing held. */
    private SettlementRegistrySC.PlayerRec passenger(WorldTestArmourerSC.StationPlayer p, int reputation) {
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        pr.reputation = reputation;
        pr.globalCounts = new NBTTagCompound();
        pr.extra.removeTag(EmbassyTicketSC.EXTRA);
        pr.hasDeath = false;
        rec.local(p.getUniqueID()).enemyUntil = 0L;
        clear(p);
        return pr;
    }

    private static void chips(WorldTestArmourerSC.StationPlayer p, int n) {
        ItemStack c = SettlementItemsSC.chips(n);
        if (c != null) {
            p.inventory.addItemStackToInventory(c);
        }
    }

    private static int held(WorldTestArmourerSC.StationPlayer p) {
        ItemStack want = SettlementItemsSC.chips(1);
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            n += s != null && want != null && s.getItem() == want.getItem() ? s.stackSize : 0;
        }
        return n;
    }

    // ------------------------------------------------------------------ 11. the Direction Finder (package T, ПЕ-11)

    private void finder() {
        if (site == null || EmbassyItemsSC.finder == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the finder - no site or no item");
            return;
        }
        WorldTestArmourerSC.StationPlayer here = station(w, "finder-here", site.x + 40, (plan == null ? 70 : plan.floorY) + 1, site.z + 30);
        ItemStack stack = EmbassyItemsSC.finder(1);
        EmbassyItemsSC.finder.onItemRightClick(stack, w, here);
        Object heard = call(cls("com.sc.embassy.ItemEmbassyFinderSC"), "heard", w, Integer.valueOf(site.x + 40), Integer.valueOf(site.z + 30));
        Object line = heard == null || heard == NONE ? null : call(cls("com.sc.embassy.ItemEmbassyFinderSC"), "reading", heard, Integer.valueOf(site.x + 40),
                Integer.valueOf(site.z + 30));
        String want = line instanceof net.minecraft.util.IChatComponent ? ((net.minecraft.util.IChatComponent) line).getUnformattedText() : null;
        boolean overworld = !here.said.isEmpty() && want != null && here.said.get(here.said.size() - 1).equals(want);
        String substrate = "skipped - no Substrate";
        if (sub != null) {
            WorldTestArmourerSC.StationPlayer there = station(sub, "finder-there", 0, 80, 0);
            EmbassyItemsSC.finder.onItemRightClick(EmbassyItemsSC.finder(1), sub, there);
            String no = net.minecraft.util.StatCollector.translateToLocal("sc.finder.embassy.offworld");
            substrate = (!there.said.isEmpty() && there.said.get(0).equals(no) ? "ok" : "BAD") + " (said " + there.said + ")";
        }
        print(overworld && !substrate.startsWith("BAD"), "finder (ПЕ-11): 50 blocks from the embassy at " + site + " it says " + here.said + " - its reading "
                + overworld + "; in the Substrate " + substrate);
    }

    // ------------------------------------------------------------------ 12. the «Достройка» (package G, ПЕ-9)

    private void repair() {
        if (rec == null || plan == null) {
            System.out.println("[SC-WORLDTEST] SKIP embassy: the «Достройка» - no embassy");
            return;
        }
        // a block of the wall's ring (its inner course, between the main gate and a tower)
        int ring = plan.radius - EmbassyLayoutSC.WALL_IN - 1, x = 0, y = -1, z = 0;
        for (int deg = 15; deg <= 35 && y < 0; deg++) {
            int a = (int) Math.round(ring * Math.cos(Math.toRadians(deg))), p = (int) Math.round(ring * Math.sin(Math.toRadians(deg)));
            for (int dy = 2; dy <= EmbassyLayoutSC.WALL_H - 1 && y < 0; dy++) {
                int bx = plan.x(a, p), bz = plan.z(a, p);
                if (!w.isAirBlock(bx, plan.floorY + dy, bz) && w.getTileEntity(bx, plan.floorY + dy, bz) == null) {
                    x = bx;
                    y = plan.floorY + dy;
                    z = bz;
                }
            }
        }
        if (y < 0) {
            print(false, "«Достройка» (ПЕ-9): no wall block found " + (plan.radius - EmbassyLayoutSC.WALL_IN - 1) + " from the core");
            return;
        }
        Block block = w.getBlock(x, y, z);
        int meta = w.getBlockMetadata(x, y, z);
        TileEntity coreBefore = w.getTileEntity(rec.x, rec.y, rec.z);
        w.setBlockToAir(x, y, z);                                       // past the events: a mod's wand, a Spatial IO cell
        // a player near: the window is left alone without force
        WorldTestArmourerSC.StationPlayer near = station(w, "repair-near", x, y, z);
        @SuppressWarnings("unchecked")
        List<Object> players = w.playerEntities;
        players.add(near);
        Object soft;
        try {
            soft = Integer.valueOf(EmbassyRepairSC.repair(w, rec, false));
        } finally {
            players.remove(near);
        }
        boolean untouched = w.isAirBlock(x, y, z);
        Object forced = Integer.valueOf(EmbassyRepairSC.repair(w, rec, true));
        boolean back = w.getBlock(x, y, z) == block && w.getBlockMetadata(x, y, z) == meta;
        if (!back) {
            w.setBlock(x, y, z, block, meta, 2);                        // put back by hand: the test leaves the city whole
        }
        TileEntity coreAfter = w.getTileEntity(rec.x, rec.y, rec.z);
        boolean sameCore = coreBefore != null && coreBefore == coreAfter && !coreAfter.isInvalid();
        print(untouched && back && sameCore, "«Достройка» (ПЕ-9): a wall block " + block.getUnlocalizedName() + " at " + x + " " + y + " " + z + " taken off past"
                + " the events; with a player near and no force (" + soft + " windows) still off " + untouched + "; repair(force) (" + forced + " windows) put it"
                + " back " + back + "; the core's TE the same one " + sameCore);

        // the integrator's: a second pass over the whole intact city sets no block - the ground's «Достройка» and the pieces agree
        // (a piece leaving air under the floor or growth over it would be filled / cleared and built again at every pass)
        Changes changes = new Changes();
        w.addWorldAccess(changes);
        Object again;
        try {
            again = Integer.valueOf(EmbassyRepairSC.repair(w, rec, true));
        } finally {
            w.removeWorldAccess(changes);
        }
        StringBuilder first = new StringBuilder();
        for (int[] c : changes.first) {
            first.append(' ').append(c[0]).append(',').append(c[1]).append(',').append(c[2]).append('=')
                    .append(Block.blockRegistry.getNameForObject(w.getBlock(c[0], c[1], c[2]))).append(':').append(w.getBlockMetadata(c[0], c[1], c[2]));
        }
        NBTTagCompound g = rec.extra.getCompoundTag(EmbassyRepairSC.EXTRA);
        boolean written = g.hasKey(EmbassyRepairSC.K_PASS) && g.getLong(EmbassyRepairSC.K_PASS) == w.getTotalWorldTime() && g.getInteger(EmbassyRepairSC.K_NEXT) == 0;
        print(changes.n == 0 && written, "«Достройка» (ПЕ-9) of the intact city: a second repair(force) (" + again + " windows) set " + changes.n + " blocks (0)"
                + (first.length() == 0 ? "" : " -" + first) + "; the pass written in the record's extra \"G\" (w 0, p now) " + written);

        // the review's (§12 п.7): after a pass no block of the city yields anything - its Copper Traces, their nodes and its quartz are
        // the trim's look-alikes (ComponentEmbassySC.trim); a city built by an older version has them put over by the pass above
        int valuables = 0, trimmed = 0;
        String where = "";
        int[] fc = EmbassyLayoutSC.footprintChunks(plan);
        for (int cx = fc[0]; cx <= fc[2]; cx++) {
            for (int cz = fc[1]; cz <= fc[3]; cz++) {
                net.minecraft.world.chunk.Chunk ch = w.getChunkFromChunkCoords(cx, cz);
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int by = Math.max(1, plan.floorY - 8); by < Math.min(256, plan.floorY + 48); by++) {
                            Block b = ch.getBlock(bx, by, bz);
                            if (b == Blocks.quartz_block || com.sc.dim.SubstrateBlocksSC.isTrace(b, ch.getBlockMetadata(bx, by, bz))) {
                                if (valuables++ == 0) {
                                    where = " - first at " + (cx * 16 + bx) + " " + by + " " + (cz * 16 + bz);
                                }
                            } else if (b == SettlementBlocksSC.decor && ch.getBlockMetadata(bx, by, bz) >= com.sc.block.BlockSettlementDecorSC.TRACE) {
                                trimmed++;
                            }
                        }
                    }
                }
            }
        }
        print(valuables == 0 && trimmed > 0, "«Достройка» (§12 п.7): no Copper Trace, node or quartz block in the city - " + valuables + " (0)" + where
                + "; their trim look-alikes " + trimmed);

        // the review's: the core itself gone past the events (an AE2 meteor's crater) - no core, no second of its own: the Overworld's
        // tick runs its Heart's window (EmbassyRepairSC.heal), which puts the core back and sets it up; a core standing - nothing
        boolean standing = !EmbassyRepairSC.heal(w, rec, true);
        w.setBlockToAir(rec.x, rec.y, rec.z);
        boolean gone = w.getBlock(rec.x, rec.y, rec.z) != SettlementBlocksSC.core;
        boolean healed = EmbassyRepairSC.heal(w, rec, true);
        TileEntity coreNow = w.getTileEntity(rec.x, rec.y, rec.z);
        boolean coreBack = w.getBlock(rec.x, rec.y, rec.z) == SettlementBlocksSC.core && coreNow instanceof TileEntitySettlementCoreSC
                && ((TileEntitySettlementCoreSC) coreNow).settlementId() == rec.id;
        if (coreNow instanceof TileEntitySettlementCoreSC) {
            core = (TileEntitySettlementCoreSC) coreNow;
        }
        print(standing && gone && healed && coreBack, "«Достройка» (ПЕ-9) of a lost core: heal with the core standing does nothing " + standing
                + "; the core at " + rec.x + " " + rec.y + " " + rec.z + " set to air past the events " + gone + ", heal ran its Heart's window " + healed
                + " - the core back with its tile of record " + rec.id + " " + coreBack);
    }

    /** The blocks a pass sets (World.setBlock with flag 2 tells its accesses; the same block and meta again tells nothing). */
    private static final class Changes implements IWorldAccess {
        int n;
        final List<int[]> first = new ArrayList<int[]>();

        public void markBlockForUpdate(int x, int y, int z) {
            if (n++ < 8) {
                first.add(new int[]{x, y, z});
            }
        }

        public void markBlockForRenderUpdate(int x, int y, int z) {
        }

        public void markBlockRangeForRenderUpdate(int x0, int y0, int z0, int x1, int y1, int z1) {
        }

        public void playSound(String sound, double x, double y, double z, float volume, float pitch) {
        }

        public void playSoundToNearExcept(EntityPlayer p, String sound, double x, double y, double z, float volume, float pitch) {
        }

        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
        }

        public void onEntityCreate(Entity e) {
        }

        public void onEntityDestroy(Entity e) {
        }

        public void playRecord(String record, int x, int y, int z) {
        }

        public void broadcastSound(int id, int x, int y, int z, int data) {
        }

        public void playAuxSFX(EntityPlayer p, int id, int x, int y, int z, int data) {
        }

        public void destroyBlockPartially(int id, int x, int y, int z, int progress) {
        }

        public void onStaticEntitiesChanged() {
        }
    }

    // ------------------------------------------------------------------ helpers

    private void force(int[] box) {
        ForgeChunkManager.Ticket t = null;
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
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

    private static List<String> slotsText(SettlementRegistrySC.Settlement s) {
        List<String> out = new ArrayList<String>();
        for (SettlementRegistrySC.Slot sl : s.slots) {
            out.add(sl.kind + "/" + sl.nameCode() + "/" + sl.postX + "," + sl.postY + "," + sl.postZ);
        }
        return out;
    }

    /** A real (not Fake) test player that never ticks and isn't in the server's list; its chat kept (WorldTestArmourerSC's). */
    private static WorldTestArmourerSC.StationPlayer station(WorldServer world, String name, double x, double y, double z) {
        WorldTestArmourerSC.StationPlayer p = new WorldTestArmourerSC.StationPlayer(world, profile(name));
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        return p;
    }

    /** `other` made again with `p`'s profile (the same player in another world). */
    private static WorldTestArmourerSC.StationPlayer sameProfile(WorldTestArmourerSC.StationPlayer p, WorldTestArmourerSC.StationPlayer other) {
        WorldTestArmourerSC.StationPlayer q = new WorldTestArmourerSC.StationPlayer((WorldServer) other.worldObj, p.getGameProfile());
        q.setLocationAndAngles(other.posX, other.posY, other.posZ, 0F, 0F);
        return q;
    }

    private static void clear(WorldTestArmourerSC.StationPlayer p) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            p.inventory.mainInventory[i] = null;
        }
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("sc-embassy-" + name).getBytes()), "[SCTestEmbassy]");
    }

    // ------------------------------------------------------------------ the packages' helpers by name (built on the core alone)

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name, true, WorldTestEmbassySC.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Public method `name` of `target` (an instance, or a Class for a static one) taking these arguments, invoked; NONE if there is none. */
    private Object call(Object target, String name, Object... args) {
        Class<?> c = target instanceof Class ? (Class<?>) target : target == null ? null : target.getClass();
        if (c == null) {
            missing.add("?." + name);
            return NONE;
        }
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.length || !fits(m.getParameterTypes(), args)) {
                continue;
            }
            try {
                return m.invoke(target instanceof Class ? null : target, args);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(c.getSimpleName() + "." + name + ": " + e.getCause(), e.getCause());
            } catch (Exception e) {
                missing.add(c.getSimpleName() + "." + name + " (" + e + ")");
                return NONE;
            }
        }
        missing.add(c.getSimpleName() + "." + name);
        return NONE;
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            Object a = args[i];
            if (a == null) {
                if (t.isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> want = !t.isPrimitive() ? t : t == int.class ? Integer.class : t == long.class ? Long.class : t == boolean.class ? Boolean.class
                    : t == float.class ? Float.class : t == double.class ? Double.class : t == byte.class ? Byte.class : t == short.class ? Short.class
                    : Character.class;
            if (!want.isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " embassy: " + what);
    }
}
