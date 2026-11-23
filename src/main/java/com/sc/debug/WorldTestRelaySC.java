package com.sc.debug;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.arena.ArenaLayoutSC;
import com.sc.block.BlockRelaySC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.init.IdeaBlocksSC;
import com.sc.mob.MobCombatSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.RelayPostSC;
import com.sc.settle.RelayRulesSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.tileentity.TileEntityRelaySC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

/**
 * ПД-7 (ИГ-2 А of the audit 2026-11-04; docs/plan-ideas-2026-10-26.md, the Substrate's region k = 7: X 27168, Z 20000), -Dsc.worldtest=true:
 * the Relay on a cleared Dead Node's plaza, on the server (RelayPostSC's test hooks placeNow / finishNow, a StationPlayer - a real
 * EntityPlayerMP, never spawned). Its chunks held by a ticket; the plaza's natives killed first. Off the plaza - refused («place»); on it -
 * the defence begins and its first wave comes (the tile's wave 0, raiders marked RAIDER_NBT); «a restart mid-defence»: the tile written
 * to NBT and read into a new one, the defence taken out of memory (as a stopped server loses it - by reflection, RelayPostSC.DEFENCES),
 * then a right click (RelayPostSC.resume) - it goes on from the kept wave (the next one, never the first again) and the time held; a tile
 * marked lost (ПМ-2) - a resume begins afresh. finishNow - the post: a RELAY record with a dock, the core in the Relay's place, the mast
 * over it, the Node claimed; a second Relay in the region - refused («region»); destroy (what a lost raid on the post does) - the record
 * gone, the core ruined. SKIP while the Substrate or dimension.relayPosts is off, or no free crossing in the region. Every block the test
 * and the post changed in the box round the plaza is put back, the raiders and the post's creatures removed, the ticket released.
 */
public final class WorldTestRelaySC {

    static final int START = WorldTestContractsSC.START + 120;
    private static final int X = 20000 + 7 * 1024, Z = 20000, BOX = 26, BELOW = 3, ABOVE = 12;
    private static final GameProfile ME = new GameProfile(UUID.nameUUIDFromBytes("sc-pd7-defender".getBytes()), "[SCTestRelay]"),
            OTHER = new GameProfile(UUID.nameUUIDFromBytes("sc-pd7-other".getBytes()), "[SCTestRelay2]");

    private int ticks, at = -1, phase;
    private boolean done;
    private WorldServer w;
    private SettlementRegistrySC reg;
    private WorldTestArmourerSC.StationPlayer p;
    private int i, j, x, y, z;
    private Block[] snapB;
    private int[] snapM;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private int savedWave = -2;
    private long savedHeld;
    private SettlementRegistrySC.Settlement post;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        if (ticks < START || at >= 0 && ticks < at) {
            return;
        }
        try {
            step();
        } catch (Throwable t) {
            print(false, "exception in phase " + phase + ": " + t);
            t.printStackTrace();
            finish();
        }
    }

    private void wait(int t) {
        at = ticks + t;
        phase++;
    }

    private void step() {
        switch (phase) {
            case 0:
                if (start()) {
                    wait(70);                               // three of the defence's steps (STEP_TICKS 20): the first wave in
                } else {
                    finish();
                }
                break;
            case 1:
                firstWave();
                restart();
                wait(50);
                break;
            case 2:
                resumed();
                lostAfresh();
                wait(50);
                break;
            case 3:
                afresh();
                built();
                finish();
                break;
            default:
                finish();
        }
    }

    // ------------------------------------------------------------------ the phases

    private boolean start() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP relay: the dimension is off (" + SubstrateSC.offReason() + ")");
            return false;
        }
        if (!SubstrateConfigSC.relayPosts || IdeaBlocksSC.relay == null) {
            System.out.println("[SC-WORLDTEST] SKIP relay: the rule dimension.relayPosts is off (or no Relay block)");
            return false;
        }
        w = MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId());
        reg = w == null ? null : SettlementRegistrySC.get(w);
        if (w == null || reg == null) {
            print(false, "the Substrate's world or the registry not there");
            return false;
        }
        p = new WorldTestArmourerSC.StationPlayer(w, ME);
        int g = SubstrateTerrainSC.GRID, i0 = X / g, j0 = Z / g;
        String why = "";
        for (int di = 0; di < 4 && x == 0; di++) {
            for (int dj = 0; dj < 4 && x == 0; dj++) {
                int ci = (i0 & ~3) + di, cj = (j0 & ~3) + dj, cx = ci * g, cz = cj * g;
                int cy = SubstrateTerrainSC.nodeFloorY(w.getSeed(), ci, cj) + 1;
                p.setLocationAndAngles(cx + 0.5D, cy, cz + 0.5D, 0F, 0F);
                String r = RelayPostSC.refusal(w, p, cx, cy, cz);
                if (r == null && second(ci, cj) != null) {
                    i = ci;
                    j = cj;
                    x = cx;
                    y = cy;
                    z = cz;
                } else {
                    why += " " + ci + "," + cj + ":" + (r == null ? "no second crossing" : r);
                }
            }
        }
        if (x == 0) {
            System.out.println("[SC-WORLDTEST] SKIP relay: no free crossing in the region round " + X + " " + Z + " -" + why);
            return false;
        }
        force(x >> 4, z >> 4, 3);
        // the chunks just made may hold a settlement of their own (the seed's): the rules asked again
        String late = RelayPostSC.refusal(w, p, x, y, z);
        if (late != null) {
            System.out.println("[SC-WORLDTEST] SKIP relay: the crossing " + i + ", " + j + " (" + x + " " + y + " " + z + ") refused once its chunks were"
                    + " made - " + late + " (a settlement of this seed nearby)");
            x = 0;
            return false;
        }
        snapshot();
        clearNatives(48);
        // off the plaza: refused
        String off = RelayPostSC.placeNow(w, p, x + RelayRulesSC.PLAZA + 5, y, z);
        // on it: the defence
        w.setBlock(x, y, z, IdeaBlocksSC.relay, BlockRelaySC.RELAY, 3);
        String on = RelayPostSC.placeNow(w, p, x, y, z);
        boolean ok = "sc.relay.refuse.place".equals(off) && on == null && RelayPostSC.defending(x, y, z);
        print(ok, "ПД-7 the Relay at the crossing " + i + ", " + j + " (" + x + " " + y + " " + z + "): " + (RelayRulesSC.PLAZA + 5) + " off the plaza - "
                + off + " (sc.relay.refuse.place), on the cleared plaza - " + (on == null ? "the defence began" : on) + ", defending "
                + RelayPostSC.defending(x, y, z));
        return on == null;
    }

    private void firstWave() {
        TileEntityRelaySC t = tile();
        int raiders = raiders(false);
        boolean peaceful = w.difficultySetting == net.minecraft.world.EnumDifficulty.PEACEFUL;
        boolean ok = t != null && t.wave() == 0 && t.held() > 0L && (raiders > 0 || peaceful) && RelayPostSC.defending(x, y, z);
        print(ok, "ПД-7 the first wave (" + RelayRulesSC.WAVES + " in all): the tile's wave " + (t == null ? "-" : t.wave()) + " (0), held " + (t == null ? "-"
                : String.valueOf(t.held())) + " ticks, " + raiders + " raiders marked within 64" + (peaceful ? " (Peaceful: none may come)" : "")
                + ", defending " + RelayPostSC.defending(x, y, z));
        savedWave = t == null ? -2 : t.wave();
        savedHeld = t == null ? 0L : t.held();
    }

    /** «The server stopped»: the tile through its NBT into a new one, the defence out of memory, its raiders gone; then a right click. */
    private void restart() {
        raiders(true);                                      // they'd keep at the Relay's HP while nobody defends it
        reload();
        boolean out = forgetDefence() && !RelayPostSC.defending(x, y, z);
        boolean resumed = RelayPostSC.resume(w, p, x, y, z) && RelayPostSC.defending(x, y, z);
        TileEntityRelaySC t = tile();
        System.out.println("[SC-WORLDTEST] INFO relay: «a restart mid-defence» - the tile read back (wave " + (t == null ? "-" : t.wave()) + ", held "
                + (t == null ? "-" : String.valueOf(t.held())) + ", lost " + (t != null && t.lost()) + "), the defence out of memory " + out
                + ", resumed by a right click " + resumed);
    }

    private void resumed() {
        TileEntityRelaySC t = tile();
        int raiders = raiders(true);
        boolean ok = t != null && savedWave == 0 && t.wave() == savedWave + 1 && t.held() >= savedHeld && RelayPostSC.defending(x, y, z);
        print(ok, "ПД-7 after «a restart mid-defence»: it went on from wave " + savedWave + " - now wave " + (t == null ? "-" : t.wave()) + " ("
                + (savedWave + 1) + ": the first never comes again), held " + (t == null ? "-" : String.valueOf(t.held())) + " >= " + savedHeld
                + " (the time kept), " + raiders + " new raiders, defending " + RelayPostSC.defending(x, y, z));
    }

    /** ПМ-2: a tile marked lost - the next resume begins afresh (wave 0 again, the time from nothing). */
    private void lostAfresh() {
        forgetDefence();
        TileEntityRelaySC t = tile();
        if (t != null) {
            t.markLost();
        }
        reload();
        RelayPostSC.resume(w, p, x, y, z);
    }

    private void afresh() {
        TileEntityRelaySC t = tile();
        raiders(true);
        boolean ok = t != null && !t.lost() && t.wave() == 0 && t.held() < savedHeld + 50L && RelayPostSC.defending(x, y, z);
        print(ok, "ПД-7 a lost defence's tile (ПМ-2, «lost» through its NBT) resumed: afresh - wave " + (t == null ? "-" : t.wave()) + " (0), held "
                + (t == null ? "-" : String.valueOf(t.held())) + ", lost " + (t != null && t.lost()) + " (false), defending " + RelayPostSC.defending(x, y, z));
    }

    private void built() {
        post = RelayPostSC.finishNow(w, x, y, z);
        boolean record = post != null && SettlementSC.isRelay(post) && post.hasDock && reg.byId(post.id) == post;
        boolean blocks = w.getBlock(x, y, z) == SettlementBlocksSC.core && w.getBlock(x, y + 1, z) == IdeaBlocksSC.relay
                && w.getBlockMetadata(x, y + 1, z) == BlockRelaySC.MAST;
        boolean claimed = RelayPostSC.claimed(w, i, j) && !RelayPostSC.defending(x, y, z);
        // a second Relay in the region: refused
        int[] two = second(i, j);
        WorldTestArmourerSC.StationPlayer q = new WorldTestArmourerSC.StationPlayer(w, OTHER);
        String again = "-";
        if (two != null) {
            q.setLocationAndAngles(two[0] + 0.5D, two[1], two[2] + 0.5D, 0F, 0F);
            again = RelayPostSC.placeNow(w, q, two[0], two[1], two[2]);
            if (again == null) {
                forgetDefenceAt(two[0], two[1], two[2]);
            }
        }
        boolean region = "sc.relay.refuse.region".equals(again);
        print(record && blocks && claimed && region, "ПД-7 held (finishNow): the record " + (post == null ? "none" : "#" + post.id + " RELAY " + SettlementSC.isRelay(post)
                + ", dock " + post.hasDock + " at " + post.dockX + " " + post.dockY + " " + post.dockZ) + " " + record + "; the core in the Relay's place and the mast"
                + " over it " + blocks + (blocks ? "" : " [at " + x + " " + y + " " + z + ": " + w.getBlock(x, y, z).getUnlocalizedName() + ", over it "
                + w.getBlock(x, y + 1, z).getUnlocalizedName() + ":" + w.getBlockMetadata(x, y + 1, z) + "]") + "; the Node claimed, no defence left " + claimed + "; a second Relay in the region (" + (two == null ? "-" : two[0] + " "
                + two[2]) + ") - " + again + " (sc.relay.refuse.region)");
        if (post != null) {
            int id = post.id;
            RelayPostSC.destroy(w, post);                    // what a lost raid on the post does (RaidRunSC)
            boolean gone = reg.byId(id) == null && w.getBlock(x, y, z) != SettlementBlocksSC.core && RelayPostSC.gone(reg, id) && !RelayPostSC.claimed(w, i, j);
            print(gone, "ПД-7 a lost raid on the post (RelayPostSC.destroy): the record #" + id + " gone, the core ruined, its id GONE, the Node free " + gone);
        }
    }

    private void finish() {
        done = true;
        try {
            if (w != null) {
                forgetDefenceAt(x, y, z);
                raiders(true);
                for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
                    if (o instanceof com.sc.settle.ISettlementMemberSC && o instanceof Entity && post != null
                            && ((com.sc.settle.ISettlementMemberSC) o).settlementId() == post.id) {
                        ((Entity) o).setDead();
                    }
                }
                restore();
            }
        } finally {
            for (ForgeChunkManager.Ticket t : tickets) {
                ForgeChunkManager.releaseTicket(t);
            }
            tickets.clear();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Another crossing of (ci, cj)'s region (RelayRulesSC.regionKey) the rules would take but for the region: {x, y, z}, or null. */
    private int[] second(int ci, int cj) {
        long seed = w.getSeed(), key = RelayRulesSC.regionKey(ci, cj);
        int g = SubstrateTerrainSC.GRID;
        for (int k = 1; k < 4; k++) {
            int ti = ci ^ k;
            if (RelayRulesSC.regionKey(ti, cj) != key || VaultLayoutSC.hasVault(seed, ti, cj) || ArenaLayoutSC.isArena(seed, ti, cj)) {
                continue;
            }
            return new int[]{ti * g, SubstrateTerrainSC.nodeFloorY(seed, ti, cj) + 1, cj * g};
        }
        return null;
    }

    private TileEntityRelaySC tile() {
        TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof TileEntityRelaySC ? (TileEntityRelaySC) te : null;
    }

    /** The Relay's tile written to NBT and read into a new one in its place (a chunk saved and loaded again). */
    private void reload() {
        TileEntityRelaySC t = tile();
        if (t == null) {
            return;
        }
        NBTTagCompound nbt = new NBTTagCompound();
        t.writeToNBT(nbt);
        TileEntityRelaySC n = new TileEntityRelaySC();
        n.readFromNBT(nbt);
        w.setTileEntity(x, y, z, n);
    }

    /** The defence at the Relay (x, y, z) out of RelayPostSC's memory, as a stopped server loses it. @return it was there */
    private boolean forgetDefence() {
        return forgetDefenceAt(x, y, z);
    }

    @SuppressWarnings("unchecked")
    private static boolean forgetDefenceAt(int bx, int by, int bz) {
        try {
            Field f = RelayPostSC.class.getDeclaredField("DEFENCES");
            f.setAccessible(true);
            Map<Long, Object> m = (Map<Long, Object>) f.get(null);
            synchronized (m) {
                for (Iterator<Object> it = m.values().iterator(); it.hasNext(); ) {
                    Object d = it.next();
                    if (intOf(d, "x") == bx && intOf(d, "y") == by && intOf(d, "z") == bz) {
                        it.remove();
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[SC-WORLDTEST] INFO relay: RelayPostSC.DEFENCES not reachable - " + e);
        }
        return false;
    }

    private static int intOf(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(o);
    }

    /** The live raiders of this crossing's defence (RAIDER_NBT) within 64; `kill` - removed. */
    private int raiders(boolean kill) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        int n = 0;
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            Entity e = (Entity) o;
            if (e.isDead || !e.getEntityData().hasKey(RelayPostSC.RAIDER_NBT) || e.getEntityData().getLong(RelayPostSC.RAIDER_NBT) != key
                    || e.getDistanceSq(x + 0.5D, y, z + 0.5D) > 64D * 64D) {
                continue;
            }
            n++;
            if (kill) {
                e.setDead();
            }
        }
        return n;
    }

    /** «A cleared Node»: the hostile natives within `r` of the plaza removed. */
    private void clearNatives(int r) {
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            Entity e = (Entity) o;
            if (!e.isDead && MobCombatSC.hostileNative(e) && e.getDistanceSq(x + 0.5D, y, z + 0.5D) <= (double) r * r) {
                e.setDead();
            }
        }
    }

    private int idx(int dx, int dy, int dz) {
        int s = 2 * BOX + 1;
        return ((dy + BELOW) * s + (dx + BOX)) * s + (dz + BOX);
    }

    private void snapshot() {
        int s = 2 * BOX + 1, n = s * s * (BELOW + ABOVE + 1);
        snapB = new Block[n];
        snapM = new int[n];
        for (int dy = -BELOW; dy <= ABOVE; dy++) {
            for (int dx = -BOX; dx <= BOX; dx++) {
                for (int dz = -BOX; dz <= BOX; dz++) {
                    int k = idx(dx, dy, dz);
                    snapB[k] = w.getBlock(x + dx, y + dy, z + dz);
                    snapM[k] = w.getBlockMetadata(x + dx, y + dy, z + dz);
                }
            }
        }
    }

    /** Every block of the box that differs from the snapshot put back (the core's, the dock's, the lamps', the ruin's). */
    private void restore() {
        if (snapB == null) {
            return;
        }
        int changed = 0;
        for (int dy = ABOVE; dy >= -BELOW; dy--) {
            for (int dx = -BOX; dx <= BOX; dx++) {
                for (int dz = -BOX; dz <= BOX; dz++) {
                    int k = idx(dx, dy, dz), bx = x + dx, by = y + dy, bz = z + dz;
                    if (w.getBlock(bx, by, bz) != snapB[k] || w.getBlockMetadata(bx, by, bz) != snapM[k]) {
                        w.removeTileEntity(bx, by, bz);
                        w.setBlock(bx, by, bz, snapB[k], snapM[k], 2);
                        changed++;
                    }
                }
            }
        }
        System.out.println("[SC-WORLDTEST] INFO relay: " + changed + " blocks round the plaza " + x + " " + y + " " + z + " put back as they were");
    }

    private void force(int cx, int cz, int r) {
        ForgeChunkManager.Ticket t = null;
        for (int a = cx - r; a <= cx + r; a++) {
            for (int b = cz - r; b <= cz + r; b++) {
                if (t == null || t.getChunkList().size() >= t.getChunkListDepth()) {
                    t = ForgeChunkManager.requestTicket(SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
                    if (t == null) {
                        return;
                    }
                    t.getModData().setString("Kind", "worldtest");
                    tickets.add(t);
                }
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(a, b));
                w.theChunkProviderServer.loadChunk(a, b);
            }
        }
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " relay: " + what);
    }
}
