package com.sc.debug;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.BreakdownDamageSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.mob.TrophyTableSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * PACKAGE D (docs/plan-substrate-tech-f3.md §11.3): Ф3's settlements on a real server (-Dsc.worldtest=true), after
 * WorldTestCombatSC: the Ф3 roster of a new settlement (the Power Engineer, the Armourer, the Salvager, the Captain, the
 * Commandant at their posts, the workshops' showcase machines), a Ф2 record upgraded (slots appended, the old ones
 * untouched), the new deals and services by TradeServerSC.execute (a FakePlayer: the charge service, the Armourer's
 * restoring, the Salvager's purchases, the limits), the restoring at a station (TrophyRestoreSC: the price, the confirming
 * click, the station's EU, a Singular trophy refused at a plain one), the Captain's heavy blow («Пробой»). Output
 * "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO armourer: ...".
 * <ol>
 * <li>a new settlement (a City, else a Settlement with a Captain) generated on fresh land: its record of version 2 with the
 * roster of §6.2, the Power Engineer's and the Salvager's showcase machines (the second block); once the core has put
 * the roster in the world - the Captain at the guard square, the Commandant on the plaza, the three new Tuners;</li>
 * <li>the same record as a Ф2 world would have it (its Ф2 slots, version 1) - RosterSC.upgrade appends the Ф3 slots at the
 * points of ПЖ-10 (the square, a pad, the gate, the plaza), the old slots and their creatures' UUIDs untouched;</li>
 * <li>TradeServerSC.execute for a FakePlayer: the Power Engineer charges the Nano chestplate in its hand to full (its
 * other NBT kept), the Armourer makes the Nano trophy in its hand the real piece with 0 charge (the chips and controllers
 * taken), the Salvager buys Shell Firmware up to its limit and then refuses;</li>
 * <li>one's own station (TrophyRestoreSC.onStationClick) with 10 000 EU: the first click only names the price, the second
 * gives the real piece and takes the EU and the components; a Singular trophy refused at a plain station, at the Singular
 * station (a Clot) level 1; a FakePlayer (a machine) never restores;</li>
 * <li>the Captain's heavy blow - 5 «Пробой» through a full Exo set with the shield on (exoStops lets it pass), his ordinary
 * blade blow stopped.</li>
 * </ol>
 * Built on the core alone (what packages S / T add is asked for by name). Every ticket released, every block it placed
 * removed, the players' state put back; the test players are never spawned.
 */
public final class WorldTestArmourerSC {

    /** After WorldTestCombatSC (START 3600, done in ~700 ticks). */
    static final int START = 4500;
    /** CONFIRM: the ticks to the second click - past TrophyRestoreSC.REPEAT_TICKS (a held button's stream never confirms), within CONFIRM_TICKS. */
    private static final int ROSTER_WAIT = 600, CONFIRM = TrophyRestoreSC.REPEAT_TICKS + 4;
    private static final GameProfile TRADER = new GameProfile(UUID.fromString("5c0e7a1e-a77a-4000-8000-00000000f003"), "[SCTestArmourer]");

    private int ticks, phase, phaseAt;
    private boolean done, started;
    private WorldServer w;
    private long seed;
    private SettlementRegistrySC reg;
    private SettlementRegistrySC.Settlement rec;
    private SettlementLayoutSC.Site site;
    private SettlementLayoutSC.Plan plan;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final List<int[]> placed = new ArrayList<int[]>();
    /** The station test: the players, the stations, what the second click is checked against. */
    private StationPlayer owner;
    private int sx, sy, sz, plainBefore;
    private boolean refusedAtPlain;

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
        if (!SubstrateSC.active() || !SettlementSC.generates()) {
            System.out.println("[SC-WORLDTEST] SKIP armourer: " + (SubstrateSC.active() ? "dimension.villages is off" : "the dimension is off ("
                    + SubstrateSC.offReason() + ")"));
            done = true;
            return;
        }
        w = MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId());
        reg = w == null ? null : SettlementRegistrySC.get(w);
        if (w == null || reg == null) {
            print(false, "the world / the settlements' registry not there");
            done = true;
            return;
        }
        seed = w.getSeed();
        if (generate()) {
            upgrade();
            force();
        }
        stationsFirst();
        captainsBlow();
        phase = 1;
        phaseAt = ticks;
    }

    private void step(int t) {
        if (phase == 1 && t >= CONFIRM) {
            stationsSecond();
            phase = 2;
            phaseAt = ticks;
        } else if (phase == 2 && t >= CONFIRM) {
            stationsThird();
            phase = 3;
            phaseAt = ticks;
        } else if (phase == 3 && rec != null && (t % 20 == 0 && rosterUp() || t >= ROSTER_WAIT)) {
            posts(t);
            trade();
            finish();
        } else if (phase == 3 && rec == null) {
            finish();
        }
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
        if (w != null) {
            for (int[] p : placed) {
                w.setBlockToAir(p[0], p[1], p[2]);
            }
            clear(FakePlayerFactory.get(w, TRADER));
        }
        placed.clear();
        if (owner != null) {
            clear(owner);
        }
        w = null;
        System.out.println("[SC-WORLDTEST] DONE armourer");
    }

    // ------------------------------------------------------------------ 1. a new settlement of version 2

    /** @return false if no settlement could be generated (the posts and the trade need one) */
    private boolean generate() {
        site = null;
        for (int pass = 0; pass < 2 && site == null; pass++) {
            for (int rx = -40; rx > -70 && site == null; rx--) {
                for (int rz = 20; rz < 50 && site == null; rz++) {
                    SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, rx, rz);
                    boolean want = s != null && (pass == 0 ? s.size == SettlementSizeSC.CITY : s.size == SettlementSizeSC.SETTLEMENT && s.size.captain(s.seed));
                    if (want && reg.atCentre(w.provider.dimensionId, s.x, s.z) == null && fresh(SettlementLayoutSC.plan(seed, s))) {
                        site = s;
                    }
                }
            }
        }
        if (site == null) {
            System.out.println("[SC-WORLDTEST] SKIP armourer: no fresh land for a City or a Settlement with a Captain near regions -40..-70, 20..50");
            return false;
        }
        plan = SettlementLayoutSC.plan(seed, site);
        int[] fc = SettlementLayoutSC.footprintChunks(plan);
        for (int cx = fc[0] - 1; cx <= fc[2] + 1; cx++) {
            for (int cz = fc[1] - 1; cz <= fc[3] + 1; cz++) {
                w.getChunkFromChunkCoords(cx, cz);
            }
        }
        rec = reg.atCentre(w.provider.dimensionId, site.x, site.z);
        if (rec == null) {
            print(false, "a new settlement (§6.2): the " + site.size + " at " + site.x + ", " + site.z + " made no record");
            return false;
        }
        List<NpcKindSC> kinds = new ArrayList<NpcKindSC>();
        for (SettlementRegistrySC.Slot sl : rec.slots) {
            kinds.add(sl.kindOf());
        }
        WorldTestSettlementSC.withoutF6Dispatcher(rec, kinds);        // Ф6: the dock's Dispatcher (appended at its core's first second)
        String why = SelfTestCombatSC.rosterV2Problem(site.size, site.seed, kinds);
        boolean roster = why == null && kinds.equals(SettlementLayoutSC.roster(site)) && rec.version == SettlementRegistrySC.VERSION && rec.version == 2
                && !rec.extra.getCompoundTag("S").hasKey("F3From") && kinds.contains(NpcKindSC.CAPTAIN)
                && kinds.contains(NpcKindSC.COMMANDANT) == (site.size == SettlementSizeSC.CITY);
        // the showcase machines of the Power Engineer's workshop (with its roof) and the Salvager's warehouse
        int[] count = new int[BlockShowcase2SC.NAMES.length];
        for (int cx = fc[0]; cx <= fc[2]; cx++) {
            for (int cz = fc[1]; cz <= fc[3]; cz++) {
                Chunk ch = w.getChunkFromChunkCoords(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = Math.max(0, plan.floorY - 4); y < Math.min(256, plan.floorY + 24); y++) {
                            if (ch.getBlock(x, y, z) == SettlementBlocksSC.showcase2) {
                                count[Math.min(count.length - 1, ch.getBlockMetadata(x, y, z))]++;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder machines = new StringBuilder();
        boolean all = true;
        for (int m = 0; m < count.length; m++) {
            machines.append(' ').append(BlockShowcase2SC.NAMES[m]).append(' ').append(count[m]);
            all &= count[m] > 0 || m >= BlockShowcase2SC.GRAVITY_COIL;     // Ф6's metas 7..9 stand in the embassies only
        }
        print(roster && all, "a new settlement (package S, §6.2, §6.3): the " + site.size + " at " + site.x + ", " + site.z + " - its record of version "
                + rec.version + ", the roster of §6.2 " + (why == null ? "ok" : why) + " (" + kinds.size() + " slots, the Captain "
                + kinds.contains(NpcKindSC.CAPTAIN) + ", the Commandant " + kinds.contains(NpcKindSC.COMMANDANT) + ") " + roster + "; the workshops'"
                + " machines of the second block:" + machines + " " + all);
        return true;
    }

    /** No chunk of the plan's footprint exists yet (on disk or loaded). */
    private boolean fresh(SettlementLayoutSC.Plan p) {
        int[] fc = SettlementLayoutSC.footprintChunks(p);
        IChunkLoader l = w.theChunkProviderServer.currentChunkLoader;
        for (int cx = fc[0] - 1; cx <= fc[2] + 1; cx++) {
            for (int cz = fc[1] - 1; cz <= fc[3] + 1; cz++) {
                if (w.theChunkProviderServer.chunkExists(cx, cz) || l instanceof AnvilChunkLoader && ((AnvilChunkLoader) l).chunkExists(w, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The dome and a ring round it forced: the core ticks and puts the roster in the world. */
    private void force() {
        ForgeChunkManager.Ticket t = null;
        int r = rec.radius + 16;
        for (int cx = rec.x - r >> 4; cx <= rec.x + r >> 4; cx++) {
            for (int cz = rec.z - r >> 4; cz <= rec.z + r >> 4; cz++) {
                if (t == null || t.getChunkList().size() >= t.getChunkListDepth()) {
                    t = ForgeChunkManager.requestTicket(SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
                    if (t == null) {
                        return;
                    }
                    t.getModData().setString("Kind", "worldtest");
                    tickets.add(t);
                }
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(cx, cz));
            }
        }
    }

    private boolean rosterUp() {
        int alive = 0;
        for (SettlementRegistrySC.Slot sl : rec.slots) {
            alive += sl.state == SettlementRegistrySC.ALIVE ? 1 : 0;
        }
        return alive == rec.slots.size();
    }

    /** 1b: the chiefs at their posts (ПГ-11, ПГ-12), the three new Tuners in the world. */
    private void posts(int waited) {
        int[] square = rec.points.get(SettlementRegistrySC.P_SQUARE), plaza = rec.points.get(SettlementRegistrySC.P_PLAZA);
        StringBuilder bad = new StringBuilder(), seen = new StringBuilder();
        boolean captain = false, commandant = site.size != SettlementSizeSC.CITY;
        int engineers = 0, armourers = 0, salvagers = 0;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityWardenSC && ((EntityWardenSC) o).settlementId() == rec.id) {
                EntityWardenSC g = (EntityWardenSC) o;
                if (g.kind() == NpcKindSC.CAPTAIN) {
                    double d = square == null ? 999 : Math.hypot(g.posX - square[0], g.posZ - square[2]);
                    captain = d <= 16;
                    seen.append(" Captain ").append((int) d).append(" from the square");
                } else if (g.kind() == NpcKindSC.COMMANDANT) {
                    double d = plaza == null ? 999 : Math.hypot(g.posX - plaza[0], g.posZ - plaza[2]);
                    commandant = d <= 10;
                    seen.append(" Commandant ").append((int) d).append(" from the plaza");
                }
            } else if (o instanceof EntityTunerSC && ((EntityTunerSC) o).settlementId() == rec.id) {
                NpcKindSC k = ((EntityTunerSC) o).kind();
                engineers += k == NpcKindSC.POWER_ENGINEER ? 1 : 0;
                armourers += k == NpcKindSC.ARMOURER ? 1 : 0;
                salvagers += k == NpcKindSC.SALVAGER ? 1 : 0;
            }
        }
        if (!captain) {
            bad.append(" no Captain at the square");
        }
        if (!commandant) {
            bad.append(" no Commandant on the plaza");
        }
        boolean tuners = engineers > 0 && armourers > 0 && salvagers > 0;
        print(bad.length() == 0 && tuners, "the posts (ПЖ-8, ПГ-11, ПГ-12) " + waited + " ticks after the generation:" + seen + "; Power Engineers " + engineers
                + ", Armourers " + armourers + ", Salvagers " + salvagers + " in the world" + (bad.length() == 0 ? "" : " - NOT:" + bad));
    }

    // ------------------------------------------------------------------ 2. a Ф2 record upgraded (ПЖ-10)

    private void upgrade() {
        Method up;
        try {
            up = Class.forName("com.sc.settle.RosterSC").getMethod("upgrade", SettlementRegistrySC.Settlement.class);
        } catch (Exception e) {
            print(false, "a Ф2 record upgraded (ПЖ-10): RosterSC.upgrade(Settlement) is missing (package S)");
            return;
        }
        SettlementRegistrySC.Settlement old = new SettlementRegistrySC.Settlement();
        old.version = 1;
        old.id = rec.id;
        old.dim = rec.dim;
        old.size = rec.size;
        old.radius = rec.radius;
        old.seed = rec.seed;
        old.x = rec.x;
        old.y = rec.y;
        old.z = rec.z;
        old.floorY = rec.floorY;
        old.hasDock = rec.hasDock;
        old.points.putAll(rec.points);
        int i = 0;
        for (SettlementRegistrySC.Slot s : rec.slots) {
            if (s.kind <= NpcKindSC.SENIOR_WARDEN.ordinal()) {         // the Ф2 roster: what a version 1 record has
                SettlementRegistrySC.Slot c = new SettlementRegistrySC.Slot();
                c.kind = s.kind;
                c.nameElement = s.nameElement;
                c.nameNumber = s.nameNumber;
                c.postX = s.postX;
                c.postY = s.postY;
                c.postZ = s.postZ;
                c.homeX = s.homeX;
                c.homeY = s.homeY;
                c.homeZ = s.homeZ;
                c.state = SettlementRegistrySC.ALIVE;
                UUID u = UUID.nameUUIDFromBytes(("sc-armourer-" + i++).getBytes());
                c.uuidMost = u.getMostSignificantBits();
                c.uuidLeast = u.getLeastSignificantBits();
                old.slots.add(c);
            }
        }
        List<String> before = slots(old);
        int added;
        try {
            added = ((Number) up.invoke(null, old)).intValue();
        } catch (Exception e) {
            print(false, "a Ф2 record upgraded (ПЖ-10): RosterSC.upgrade threw " + (e.getCause() != null ? e.getCause() : e));
            return;
        }
        List<String> after = slots(old);
        boolean kept = after.size() == before.size() + added && after.subList(0, before.size()).equals(before);
        StringBuilder where = new StringBuilder();
        boolean posts = true;
        for (int k = before.size(); k < old.slots.size(); k++) {
            SettlementRegistrySC.Slot s = old.slots.get(k);
            NpcKindSC kind = s.kindOf();
            String point = kind == NpcKindSC.ARMOURER || kind == NpcKindSC.CAPTAIN ? SettlementRegistrySC.P_SQUARE : kind == NpcKindSC.POWER_ENGINEER
                    ? SettlementRegistrySC.P_PAD + "0" : kind == NpcKindSC.SALVAGER ? SettlementRegistrySC.P_GATE : SettlementRegistrySC.P_PLAZA;
            int[] p = old.points.get(point);
            double d = p == null ? 999 : Math.hypot(s.postX - p[0], s.postZ - p[2]);
            posts &= d <= 8 && s.state == SettlementRegistrySC.NEVER;
            where.append(' ').append(kind).append(" ").append((int) d).append(" from ").append(point);
        }
        boolean version = old.version == 2 && old.extra.getCompoundTag("S").getInteger("F3From") == 1;
        int expect = (rec.sizeKind() == SettlementSizeSC.OUTPOST ? 1 : 3) + (rec.sizeKind().captain(rec.seed) ? 1 : 0) + (rec.sizeKind().commandant ? 1 : 0);
        print(kept && posts && version && added == expect, "a Ф2 record upgraded (RosterSC.upgrade, ПЖ-10): this settlement's " + before.size() + " Ф2 slots"
                + " at version 1 - " + added + " appended (" + expect + "):" + where + " (within 8 of the point, NEVER yet) " + posts + "; the old slots and their"
                + " UUIDs untouched " + kept + "; version 2, F3From 1 " + version);
    }

    private static List<String> slots(SettlementRegistrySC.Settlement s) {
        List<String> out = new ArrayList<String>();
        for (SettlementRegistrySC.Slot sl : s.slots) {
            out.add(sl.kind + "@" + sl.postX + "," + sl.postY + "," + sl.postZ + "#" + sl.state + "," + sl.uuidMost + "," + sl.uuidLeast);
        }
        return out;
    }

    // ------------------------------------------------------------------ 3. the services and deals (package T, §6.7, §6.8)

    private void trade() {
        int outCharge = staticInt(TradeOfferSC.class, "OUT_CHARGE", 3), outRestore = staticInt(TradeOfferSC.class, "OUT_RESTORE", 4);
        EntityTunerSC engineer = tuner(NpcKindSC.POWER_ENGINEER), armourer = tuner(NpcKindSC.ARMOURER), salvager = tuner(NpcKindSC.SALVAGER);
        FakePlayer p = FakePlayerFactory.get(w, TRADER);
        // the charge: the Power Engineer, a Nano piece in the hand, «Знакомый»
        TradeOfferSC charge = null, restore = null, firmware = null;
        for (TradeOfferSC o : TradeTableSC.all()) {
            Object suit = member(o.in != null && o.in.length > 0 ? o.in[0] : null, "suit");
            if (o.outKind == outCharge && suit == ArmorSuit.NANO) {
                charge = o;
            }
            if (o.outKind == outRestore && o.in != null && o.in.length > 0 && contains(metas(o.in[0]), TrophyTableSC.armorMeta(ArmorSuit.NANO, 1))) {
                restore = o;
            }
            if (o.kind == NpcKindSC.SALVAGER && o.sell() && o.in.length == 1 && o.in[0] != null && o.in[0].display() != null && MobItemsSC.combatLoot != null
                    && o.in[0].display().getItem() == MobItemsSC.combatLoot && o.in[0].display().getItemDamage() == ItemCombatLootSC.LootKind.SHELL_FIRMWARE.ordinal()) {
                firmware = o;
            }
        }
        if (engineer == null || charge == null) {
            print(false, "the charge service (ПО-10, ПО-13): " + (engineer == null ? "no Power Engineer of the settlement in the world" : "no OUT_CHARGE deal for Nano"));
        } else {
            prepare(p, engineer, ReputationSC.ACQUAINTANCE_FROM);
            ItemStack chest = new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[1]);
            ItemArmorSC.setCharge(chest, 0);
            if (!chest.hasTagCompound()) {
                chest.setTagCompound(new NBTTagCompound());
            }
            chest.getTagCompound().setString("scTestMark", "kept");
            p.inventory.mainInventory[0] = chest;
            int chips = pay(p, charge);
            int code = TradeServerSC.execute(p, engineer, charge.key, 1);
            ItemStack now = p.inventory.mainInventory[0];
            boolean full = now != null && now.getItem() == chest.getItem() && ItemArmorSC.chargeOf(now) == ItemArmorSC.capacityOf(now)
                    && now.hasTagCompound() && "kept".equals(now.getTagCompound().getString("scTestMark"));
            boolean paid = chipsIn(p) == chips - side(charge, 1);
            print(full && paid, "the charge service (ПО-10, ПО-13): " + charge.key + " at the Power Engineer - the Nano chestplate in the hand charged to "
                    + (now == null ? "-" : ItemArmorSC.chargeOf(now) + "/" + ItemArmorSC.capacityOf(now)) + ", its other NBT kept " + full + "; the chips paid "
                    + paid + " (code " + code + ")");
            chargeLimit(p, engineer, charge);
        }
        if (armourer == null || restore == null) {
            print(false, "the restoring service (ПТ-11, ПТ-12): " + (armourer == null ? "no Armourer of the settlement in the world" : "no OUT_RESTORE deal for a Nano piece"));
        } else {
            prepare(p, armourer, ReputationSC.ACQUAINTANCE_FROM);
            p.inventory.mainInventory[0] = MobItemsSC.trophyStack(TrophyTableSC.armorMeta(ArmorSuit.NANO, 1));
            int[] before = pay2(p, restore);
            int code = TradeServerSC.execute(p, armourer, restore.key, 1);
            ItemStack now = p.inventory.mainInventory[0];
            boolean real = now != null && now.getItem() == ModItems.ARMOR.get(ArmorSuit.NANO)[1] && ItemArmorSC.chargeOf(now) == 0;
            int[] after = sideCounts(p, restore);
            boolean paid = true;
            for (int i = 1; i < restore.in.length; i++) {
                paid &= before[i] - after[i] == side(restore, i);
            }
            print(real && paid, "the restoring service (ПТ-11, ПТ-12): " + restore.key + " at the Armourer - the Nano trophy in the hand now " + (now == null ? "nothing"
                    : now.getDisplayName() + ", charge " + ItemArmorSC.chargeOf(now)) + " " + real + "; the chips and components taken " + paid + " (code " + code + ")");
        }
        if (salvager == null || firmware == null) {
            print(false, "the Salvager (ПО-12): " + (salvager == null ? "no Salvager of the settlement in the world" : "no Shell Firmware deal"));
        } else {
            prepare(p, salvager, 0);
            int level = ReputationSC.level(0), each = TradeRulesSC.playerSide(firmware.in[0].count, level, rec.seed, firmware.key);
            give(p, firmware.in[0].display(), each * (firmware.limit + 1));
            boolean ok = true;
            StringBuilder codes = new StringBuilder();
            for (int n = 0; n < firmware.limit; n++) {
                int[] b = sideCounts(p, firmware);
                int c0 = chipsIn(p);
                codes.append(TradeServerSC.execute(p, salvager, firmware.key, 1)).append(' ');
                int[] a = sideCounts(p, firmware);
                ok &= b[0] - a[0] == each && chipsIn(p) - c0 == firmware.out.count;
            }
            int[] b = sideCounts(p, firmware);
            int over = TradeServerSC.execute(p, salvager, firmware.key, 1);
            boolean limit = java.util.Arrays.equals(b, sideCounts(p, firmware));
            print(ok && limit, "the Salvager (ПО-12): " + firmware.key + " " + firmware.limit + " times - " + each + " Shell Firmware taken and " + firmware.out.count
                    + " chips given each time " + ok + " (codes " + codes.toString().trim() + "); once more refused, nothing taken " + limit + " (code " + over + ")");
        }
        clear(p);
    }

    /**
     * Added at the integration (package T's TESTS): with nothing to charge - R_NO_ITEM, nothing paid, nothing counted; the
     * three charges share one limit (TradeTableSC.PE_CHARGE_COUNTER) - after the Nano one, Quantum pieces fill it and an Exo
     * one is refused (R_LIMIT), its chips and charge untouched.
     */
    private void chargeLimit(FakePlayer p, EntityTunerSC engineer, TradeOfferSC nano) {
        TradeOfferSC quantum = TradeTableSC.byKey(TradeTableSC.PE_CHARGE_QUANTUM), exo = TradeTableSC.byKey(TradeTableSC.PE_CHARGE_EXO);
        if (quantum == null || exo == null) {
            print(false, "the charge services' shared limit (ПО-10): no " + TradeTableSC.PE_CHARGE_QUANTUM + " / " + TradeTableSC.PE_CHARGE_EXO + " deal");
            return;
        }
        clear(p);
        int chips0 = pay(p, nano);
        int none = TradeServerSC.execute(p, engineer, nano.key, 1);
        boolean noItem = none == TradeServerSC.R_NO_ITEM && chipsIn(p) == chips0;
        StringBuilder codes = new StringBuilder();
        boolean filled = true;
        for (int n = 1; n < nano.limit; n++) {                  // the Nano charge made one already
            clear(p);
            ItemStack q = new ItemStack(ModItems.ARMOR.get(ArmorSuit.QUANTUM)[1]);
            ItemArmorSC.setCharge(q, 0);
            p.inventory.mainInventory[0] = q;
            pay(p, quantum);
            int c = TradeServerSC.execute(p, engineer, quantum.key, 1);
            codes.append(c).append(' ');
            filled &= c == TradeServerSC.OK;
        }
        clear(p);
        ItemStack e = new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[1]);
        ItemArmorSC.setCharge(e, 0);
        p.inventory.mainInventory[0] = e;
        int chips1 = pay(p, exo);
        int over = TradeServerSC.execute(p, engineer, exo.key, 1);
        ItemStack after = p.inventory.mainInventory[0];
        boolean refused = over == TradeServerSC.R_LIMIT && chipsIn(p) == chips1 && after != null && ItemArmorSC.chargeOf(after) == 0;
        print(noItem && filled && refused, "the charge services (ПО-10, ПО-13): nothing to charge - refused (" + none + ", " + TradeServerSC.R_NO_ITEM
                + " wanted), nothing paid " + noItem + "; one limit for the three - " + (nano.limit - 1) + " Quantum charges after the Nano one (codes "
                + codes.toString().trim() + ") " + filled + ", then the Exo one refused (" + over + "), nothing taken " + refused);
        clear(p);
    }

    /** The player at the Tuner, a clean inventory, «Доверие» `rep`, nothing traded today. */
    private void prepare(EntityPlayer p, EntityTunerSC t, int rep) {
        clear(p);
        long today = SettlementSC.day(w);
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        pr.reputation = rep;
        pr.rollDay(today);
        pr.chips = pr.repTrades = 0;
        SettlementRegistrySC.LocalRec lr = rec.local(p.getUniqueID());
        lr.enemyUntil = 0L;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        p.setLocationAndAngles(t.posX, t.posY, t.posZ, 0F, 0F);
        p.inventory.currentItem = 0;
    }

    /** The player's side `i` of `o` at his level (the settlement's spread; TradeRulesSC.playerCounts - a purchase's goods, a matcher unscaled). */
    private int side(TradeOfferSC o, int i) {
        int level = ReputationSC.level(reg.player(FakePlayerFactory.get(w, TRADER).getUniqueID()).reputation);
        return TradeRulesSC.playerCounts(o, level, TradeRulesSC.seedPct(rec.seed, o.key))[i];
    }

    /** The service's paid sides (1..) into the inventory; @return the chips the player holds then */
    private int pay(EntityPlayer p, TradeOfferSC o) {
        pay2(p, o);
        return chipsIn(p);
    }

    private int[] pay2(EntityPlayer p, TradeOfferSC o) {
        for (int i = 1; i < o.in.length; i++) {
            give(p, o.in[i].display(), side(o, i));
        }
        return sideCounts(p, o);
    }

    private static int[] sideCounts(EntityPlayer p, TradeOfferSC o) {
        int[] n = new int[o.in.length];
        for (ItemStack s : p.inventory.mainInventory) {
            for (int i = 0; s != null && i < o.in.length; i++) {
                if (o.in[i] != null && o.in[i].matches(s)) {
                    n[i] += s.stackSize;
                }
            }
        }
        return n;
    }

    private static int chipsIn(EntityPlayer p) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            n += s != null && SettlementItemsSC.creditChip != null && s.getItem() == SettlementItemsSC.creditChip ? s.stackSize : 0;
        }
        return n;
    }

    private EntityTunerSC tuner(NpcKindSC kind) {
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityTunerSC && ((EntityTunerSC) o).settlementId() == rec.id && ((EntityTunerSC) o).kind() == kind && !((Entity) o).isDead) {
                return (EntityTunerSC) o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 4. one's own station (ПТ-11)

    /** The first clicks: a plain station with 12 000 EU and the Singular one, a real player (not a FakePlayer: a machine never restores). */
    private void stationsFirst() {
        sx = rec != null ? rec.x + rec.radius + SettlementSC.BELT + 20 : 0;
        sz = rec != null ? rec.z : 0;
        sy = 200;
        if (rec == null) {
            sx = (int) Math.floor(w.getSpawnPoint().posX) - 500;
            sz = (int) Math.floor(w.getSpawnPoint().posZ) - 500;
        }
        w.getChunkFromBlockCoords(sx, sz);
        place(sx, sy, sz, ModBlocks.armorStation);
        place(sx + 3, sy, sz, ModBlocks.singularStation);
        TileEntity te = w.getTileEntity(sx, sy, sz), ts = w.getTileEntity(sx + 3, sy, sz);
        if (!(te instanceof TileEntityArmorStationSC) || !(ts instanceof TileEntityArmorStationSC)) {
            print(false, "the station (ПТ-11): no station tile at " + sx + ", " + sy + ", " + sz);
            return;
        }
        ((TileEntityArmorStationSC) te).setEnergyStoredClient(12000);
        ((TileEntityArmorStationSC) ts).setEnergyStoredClient(1200000);
        owner = new StationPlayer(w, new GameProfile(UUID.fromString("5c0e7a1e-a77a-4000-8000-00000000f004"), "[SCTestStation]"));
        owner.setLocationAndAngles(sx + 0.5, sy + 1, sz + 1.5, 180F, 0F);
        holdAt(owner, TrophyTableSC.armorMeta(ArmorSuit.NANO, 1));
        give(owner, com.sc.init.ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.CONTROLLER), 4);
        boolean handled = TrophyRestoreSC.onStationClick(w, sx, sy, sz, owner);
        boolean untouched = isTrophy(owner.inventory.mainInventory[0]) && ((TileEntityArmorStationSC) te).getEnergyStored() == 12000;
        print(handled && untouched, "the station, the first click (ПТ-11): handled (the screen doesn't open) " + handled + ", only the price said - the trophy"
                + " still in the hand, the EU untouched " + untouched + (owner.said.isEmpty() ? "" : " («" + owner.said.get(owner.said.size() - 1) + "»)"));
    }

    /** The second click, within CONFIRM_TICKS: the plain station restores; then the first clicks of the Singular trophy and of a FakePlayer. */
    private void stationsSecond() {
        if (owner == null) {
            return;
        }
        TileEntity te = w.getTileEntity(sx, sy, sz), ts = w.getTileEntity(sx + 3, sy, sz);
        if (!(te instanceof TileEntityArmorStationSC) || !(ts instanceof TileEntityArmorStationSC)) {
            return;
        }
        TileEntityArmorStationSC plain = (TileEntityArmorStationSC) te, singular = (TileEntityArmorStationSC) ts;
        int controllers0 = count(owner, com.sc.init.ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.CONTROLLER));
        TrophyRestoreSC.onStationClick(w, sx, sy, sz, owner);
        ItemStack got = owner.inventory.mainInventory[0];
        int eu = plain.getEnergyStored(), controllers = count(owner, com.sc.init.ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.CONTROLLER));
        boolean real = got != null && got.getItem() == ModItems.ARMOR.get(ArmorSuit.NANO)[1] && ItemArmorSC.chargeOf(got) == 0;
        boolean paid = eu == 12000 - TrophyRestoreSC.STATION_EU && controllers < controllers0;
        print(real && paid, "the station, the second click (ПТ-11, ПТ-12): the trophy now " + (got == null ? "nothing" : got.getDisplayName()) + " with 0 charge "
                + real + "; the station's EU " + eu + " (12000 - " + TrophyRestoreSC.STATION_EU + "), Controllers " + controllers0 + " -> " + controllers + " " + paid);

        // a Singular trophy at the plain station: refused at once, nothing taken
        holdAt(owner, TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 1));
        give(owner, new ItemStack(ModItems.singularClot), 1);
        plainBefore = plain.getEnergyStored();
        TrophyRestoreSC.onStationClick(w, sx, sy, sz, owner);
        refusedAtPlain = isTrophy(owner.inventory.mainInventory[0]) && plain.getEnergyStored() == plainBefore;
        // the Singular station's first click (the price), a FakePlayer's first click at the plain one
        owner.setLocationAndAngles(sx + 3.5, sy + 1, sz + 1.5, 180F, 0F);
        TrophyRestoreSC.onStationClick(w, sx + 3, sy, sz, owner);
        FakePlayer machine = FakePlayerFactory.get(w, TRADER);
        clear(machine);
        machine.setLocationAndAngles(sx + 0.5, sy + 1, sz + 1.5, 180F, 0F);
        holdAt(machine, TrophyTableSC.armorMeta(ArmorSuit.NANO, 0));
        give(machine, com.sc.init.ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.CONTROLLER), 4);
        plain.setEnergyStoredClient(12000);
        TrophyRestoreSC.onStationClick(w, sx, sy, sz, machine);
    }

    /** The second clicks of the Singular trophy (the Singular station: level 1) and of the FakePlayer (never). */
    private void stationsThird() {
        if (owner == null) {
            return;
        }
        TileEntity te = w.getTileEntity(sx, sy, sz), ts = w.getTileEntity(sx + 3, sy, sz);
        if (!(te instanceof TileEntityArmorStationSC) || !(ts instanceof TileEntityArmorStationSC)) {
            return;
        }
        TileEntityArmorStationSC plain = (TileEntityArmorStationSC) te, singular = (TileEntityArmorStationSC) ts;
        TrophyRestoreSC.onStationClick(w, sx + 3, sy, sz, owner);
        boolean refused = refusedAtPlain;
        ItemStack s = owner.inventory.mainInventory[0];
        boolean level1 = s != null && s.getItem() == ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1] && SingularLevel.levelOf(s) == 1
                && count(owner, new ItemStack(ModItems.singularClot)) == 0 && singular.getEnergyStored() == 1200000 - TrophyRestoreSC.SINGULAR_STATION_EU;
        // a FakePlayer: a machine never restores (ПТ-11)
        FakePlayer machine = FakePlayerFactory.get(w, TRADER);
        TrophyRestoreSC.onStationClick(w, sx, sy, sz, machine);
        boolean never = isTrophy(machine.inventory.mainInventory[0]) && plain.getEnergyStored() == 12000;
        clear(machine);
        print(refused && level1 && never, "the station (ПТ-11): a Singular trophy at the plain station refused, nothing taken " + refused + "; at the Singular"
                + " station - " + (s == null ? "nothing" : s.getDisplayName() + " of level " + SingularLevel.levelOf(s)) + ", the Clot and "
                + TrophyRestoreSC.SINGULAR_STATION_EU + " EU taken " + level1 + "; a FakePlayer's two clicks restore nothing " + never);
    }

    private void holdAt(EntityPlayer p, int trophyMeta) {
        p.inventory.currentItem = 0;
        p.inventory.mainInventory[0] = MobItemsSC.trophyStack(trophyMeta);
    }

    private static boolean isTrophy(ItemStack s) {
        return s != null && MobItemsSC.trophy != null && s.getItem() == MobItemsSC.trophy;
    }

    // ------------------------------------------------------------------ 5. the Captain's heavy blow (ПГ-11, ПБ-16)

    private void captainsBlow() {
        EntityWardenSC captain = null;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityWardenSC && ((EntityWardenSC) o).kind() == NpcKindSC.CAPTAIN) {
                captain = (EntityWardenSC) o;
                break;
            }
        }
        if (captain == null) {
            captain = (EntityWardenSC) SettlementSC.newMember(w, NpcKindSC.CAPTAIN);
            captain.setPosition(sx + 0.5, 200, sz + 6.5);
        }
        WorldTestCombatSC.HurtablePlayer p = new WorldTestCombatSC.HurtablePlayer(w, new GameProfile(UUID.fromString("5c0e7a1e-a77a-4000-8000-00000000f005"),
                "[SCTestCaptain]"));
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, p, 0, "field_147101_bU");
        p.setLocationAndAngles(captain.posX + 1.5, captain.posY, captain.posZ, 0F, 0F);
        WorldTestCombatSC.wear(p, ArmorSuit.EXO, 100, true);
        float blow = WorldTestCombatSC.num("com.sc.settle.npc.EntityWardenSC", "HEAVY_BLOW", 5F);
        p.setHealth(p.getMaxHealth());
        p.hurtResistantTime = 0;
        float h0 = p.getHealth();
        p.attackEntityFrom(BreakdownDamageSC.of(captain), blow);
        float lost = h0 - p.getHealth(), want = WorldTestCombatSC.onPlayer(w, BreakdownDamageSC.of(captain), blow);   // after the difficulty's scaling
        p.hurtResistantTime = 0;
        float h1 = p.getHealth();
        p.attackEntityFrom(DamageSource.causeMobDamage(captain), WorldTestCombatSC.num("com.sc.settle.npc.EntityWardenSC", "CAPTAIN_BLADE", 10F));
        float stopped = h1 - p.getHealth();
        print(Math.abs(lost - want) < 0.01F && stopped < 0.01F, "the Captain's heavy blow (ПГ-11, ПБ-16): " + n(blow) + " «Пробой» from a Captain through a full"
                + " Exo set with the shield on - " + n(lost) + " HP came off (" + n(want) + " - " + n(blow) + " at the difficulty " + w.difficultySetting
                + "); his ordinary blade blow - " + n(stopped) + " (0, the shield)");
    }

    // ------------------------------------------------------------------ helpers

    private void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " armourer: " + what);
    }

    private static String n(float v) {
        return String.valueOf(Math.round(v * 100F) / 100F);
    }

    private void place(int x, int y, int z, Block b) {
        if (w.isAirBlock(x, y, z)) {
            w.setBlock(x, y, z, b, 0, 2);
            placed.add(new int[]{x, y, z});
        }
    }

    private static void give(EntityPlayer p, ItemStack s, int count) {
        if (s == null) {
            return;
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 1; i < inv.length && count > 0; i++) {         // slot 0 is the hand's
            if (inv[i] == null) {
                ItemStack c = s.copy();
                c.stackSize = Math.min(count, c.getMaxStackSize());
                inv[i] = c;
                count -= c.stackSize;
            }
        }
    }

    private static int count(EntityPlayer p, ItemStack like) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            n += s != null && like != null && s.getItem() == like.getItem() && s.getItemDamage() == like.getItemDamage() ? s.stackSize : 0;
        }
        return n;
    }

    private static void clear(EntityPlayer p) {
        if (p != null) {
            for (int i = 0; i < p.inventory.mainInventory.length; i++) {
                p.inventory.mainInventory[i] = null;
            }
            for (int i = 0; i < 4; i++) {
                p.inventory.armorInventory[i] = null;
            }
        }
    }

    private static int staticInt(Class<?> c, String name, int otherwise) {
        try {
            return c.getField(name).getInt(null);
        } catch (Throwable t) {
            return otherwise;
        }
    }

    /** A public field or no-argument method of `o` by name (package T's matchers), null none. */
    private static Object member(Object o, String name) {
        if (o == null) {
            return null;
        }
        try {
            return o.getClass().getField(name).get(o);
        } catch (Throwable t) {
            // a method then
        }
        try {
            return o.getClass().getMethod(name).invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int[] metas(TradeOfferSC.Ingredient i) {
        Object m = member(i, "metas");
        return m instanceof int[] ? (int[]) m : new int[0];
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /**
     * A real player for the station (TrophyRestoreSC never serves a FakePlayer - a machine): an EntityPlayerMP that is never
     * spawned, has no connection - so what would go to its screen is kept here (the price, the refusal).
     */
    static final class StationPlayer extends EntityPlayerMP {
        final List<String> said = new ArrayList<String>();

        StationPlayer(WorldServer w, GameProfile profile) {
            super(MinecraftServer.getServer(), w, profile, new ItemInWorldManager(w));
        }

        @Override
        public void addChatComponentMessage(IChatComponent c) {
            said.add(c.getUnformattedText());
        }

        @Override
        public void addChatMessage(IChatComponent c) {
            said.add(c.getUnformattedText());
        }

        @Override
        public void sendContainerToPlayer(net.minecraft.inventory.Container c) {
        }

        @Override
        public void onUpdate() {
        }
    }
}
