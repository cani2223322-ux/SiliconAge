package com.sc.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.dim.SubstrateTerrainSC;
import com.sc.finale.FinaleItemsSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.util.SiliconMaterial;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §7.2, rule ПЛ-7; proposal §9 событие 5 «Там тайник (чипы, Крупица в пределах лимита,
 * Журнал), но рядом появляется патруль Оболочек»): the Lithographers' Signal's cache and its patrol - once a signal (SignalDataSC
 * cacheDone), when a player first comes within SignalRulesSC.CACHE_REACH of the living Node (SignalSC's tick; the world test calls make
 * with its FakePlayer).
 * <ul>
 * <li>the cache: a vanilla chest on the plaza's floor as near its middle as there is room (the middle itself holds the Node's dead
 * heart, or a Vault's hatch): the first column within SPOT_RADIUS whose lowest air in SPOT_UP over the floor stands on something solid,
 * else the middle's own column higher up (to MIDDLE_UP) - only ever into air, never next to another chest (it would join it): a
 * player's build there is never touched. Its contents (contents): Memory Chips CHIPS_MIN..MAX, Controllers CONTROLLERS_MIN..MAX, one
 * Sealed Crumb (the daily three are counted when it is opened - ItemRelicSC), a Journal by JournalSC.roll(SRC_SIGNAL) - №11;</li>
 * <li>the patrol: SignalRulesSC.patrolSize(n) Hollow Shells, n - the real players within COUNT_RADIUS of the crossing (at least one),
 * coming up the four canyons' mouths MOUTH_PAST blocks past the plaza's edge (SpawnDirectorSC.spawnAt - the world's own checks, an
 * elite's roll, «born in Si-14»), one patrol id, their home the plaza (EntityHollowShellSC.setPatrol), after the player who found it
 * (not in creative); none on Peaceful (they would vanish at once);</li>
 * <li>sc.signal.cache (no Shell came - sc.signal.cache.quiet) to the real players within COUNT_RADIUS of the crossing.</li>
 * </ul>
 * Server only.
 */
public final class SignalCacheSC {

    /** The chest's place: columns this far round the plaza's middle, their lowest air this far over the floor; the middle up to this. */
    public static final int SPOT_RADIUS = 3, SPOT_UP = 4, MIDDLE_UP = 12;
    /** The patrol: this far past the plaza's edge up each canyon; a spawn's column searched this far up; players counted within this. */
    public static final int MOUTH_PAST = 4, PATROL_UP = 6, COUNT_RADIUS = 48;

    /** The four canyon mouths round a crossing: east, west, south, north. */
    private static final int[] MDX = {1, -1, 0, 0}, MDZ = {0, 0, 1, -1};

    private SignalCacheSC() {
    }

    /** A cache made: the chest's place and the patrol that came (the world test removes them). */
    public static final class Result {
        public final int x, y, z;
        public final List<EntityLiving> patrol;

        public Result(int x, int y, int z, List<EntityLiving> patrol) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.patrol = patrol;
        }
    }

    /** The cache's contents (server; `rand` - the world's). */
    public static List<ItemStack> contents(Random rand) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        WreckLootSC.add(out, WreckLootSC.silicon(SiliconMaterial.MEMORY_CHIP,
                SignalRulesSC.CHIPS_MIN + rand.nextInt(SignalRulesSC.CHIPS_MAX - SignalRulesSC.CHIPS_MIN + 1)));
        WreckLootSC.add(out, WreckLootSC.silicon(SiliconMaterial.CONTROLLER,
                SignalRulesSC.CONTROLLERS_MIN + rand.nextInt(SignalRulesSC.CONTROLLERS_MAX - SignalRulesSC.CONTROLLERS_MIN + 1)));
        WreckLootSC.add(out, RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1));
        WreckLootSC.add(out, FinaleItemsSC.journal(JournalSC.roll(JournalSC.SRC_SIGNAL, rand)));
        return out;
    }

    /**
     * ПЛ-7: the cache and the patrol of world `w`'s signal for player `p` (who came near), once: marks the data's cacheDone. Null - the
     * signal is off, its cache made already, the plaza not loaded or no room for the chest (the next look tries again).
     */
    public static Result make(WorldServer w, SignalDataSC d, EntityPlayer p) {
        if (w == null || d == null || !d.on || d.cacheDone || !w.blockExists(d.x, d.y, d.z)) {
            return null;
        }
        int[] at = spot(w, d.x, d.y, d.z);
        if (at == null) {
            return null;
        }
        int facing = p == null ? 3 : facing(at[0], at[2], p.posX, p.posZ);
        w.setBlock(at[0], at[1], at[2], Blocks.chest, facing, 3);
        TileEntity te = w.getTileEntity(at[0], at[1], at[2]);
        if (te instanceof IInventory) {
            WreckLootSC.fill((IInventory) te, contents(w.rand), w.rand);
        }
        d.cacheDone = true;
        d.markDirty();
        List<EntityLiving> patrol = patrol(w, d, p);
        String key = patrol.isEmpty() ? "sc.signal.cache.quiet" : "sc.signal.cache";      // Peaceful / no room: nobody came
        for (Object o : w.playerEntities) {
            if (SignalSC.real(o) && near((EntityPlayer) o, d, COUNT_RADIUS)) {
                SignalSC.tell((EntityPlayer) o, EnumChatFormatting.LIGHT_PURPLE, key);
            }
        }
        return new Result(at[0], at[1], at[2], patrol);
    }

    /** The chest's place {x, y, z} round the plaza's middle (x, floor + 1 = y, z), or null. Reads only loaded blocks. */
    static int[] spot(WorldServer w, int mx, int y0, int mz) {
        for (int r = 0; r <= SPOT_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;                                // the square ring at r only
                    }
                    int[] at = lowestAir(w, mx + dx, y0, mz + dz, y0 + SPOT_UP);
                    if (at != null) {
                        return at;
                    }
                }
            }
        }
        return lowestAir(w, mx, y0 + SPOT_UP + 1, mz, y0 + MIDDLE_UP);
    }

    /** The lowest air block of column (x, z) in y0..y1 standing on something solid and beside no chest; null - none (or not loaded). */
    private static int[] lowestAir(WorldServer w, int x, int y0, int z, int y1) {
        for (int y = Math.max(1, y0); y <= Math.min(254, y1); y++) {
            if (!w.blockExists(x, y, z)) {
                return null;
            }
            if (!w.isAirBlock(x, y, z)) {
                continue;
            }
            Material m = w.getBlock(x, y - 1, z).getMaterial();
            if (!m.isSolid() || m.isLiquid() || chestBeside(w, x, y, z)) {
                return null;                                     // the column's first air: it stands there or nowhere
            }
            return new int[]{x, y, z};
        }
        return null;
    }

    private static boolean chestBeside(WorldServer w, int x, int y, int z) {
        return isChest(w, x + 1, y, z) || isChest(w, x - 1, y, z) || isChest(w, x, y, z + 1) || isChest(w, x, y, z - 1);
    }

    /** A chest at (x, y, z) - read only where loaded (the plaza's middle is a chunk's corner: never load a neighbour). */
    private static boolean isChest(WorldServer w, int x, int y, int z) {
        if (!w.blockExists(x, y, z)) {
            return false;
        }
        Block b = w.getBlock(x, y, z);
        return b == Blocks.chest || b == Blocks.trapped_chest;
    }

    /** The chest's facing (2 north, 3 south, 4 west, 5 east) towards (px, pz). */
    private static int facing(int x, int z, double px, double pz) {
        double dx = px - (x + 0.5D), dz = pz - (z + 0.5D);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? 5 : 4;
        }
        return dz > 0 ? 3 : 2;
    }

    /** ПЛ-7: the patrol up the canyon mouths (none on Peaceful). */
    static List<EntityLiving> patrol(WorldServer w, SignalDataSC d, EntityPlayer finder) {
        List<EntityLiving> out = new ArrayList<EntityLiving>();
        if (w.difficultySetting == EnumDifficulty.PEACEFUL) {
            return out;
        }
        int players = 0;
        for (Object o : w.playerEntities) {
            if (SignalSC.real(o) && near((EntityPlayer) o, d, COUNT_RADIUS)) {
                players++;
            }
        }
        int n = SignalRulesSC.patrolSize(Math.max(1, players));
        long seed = w.getSeed();
        int half = Math.max(SubstrateTerrainSC.canyonHalfWidth(seed, d.i), SubstrateTerrainSC.canyonHalfWidth(~seed, d.j)) + SubstrateTerrainSC.CROSSING_EXTRA;
        long id = w.rand.nextLong() | 1L;                        // never 0 (0 - no patrol)
        int first = w.rand.nextInt(MDX.length);
        boolean chase = finder instanceof EntityPlayerMP && !(finder instanceof FakePlayer) && !finder.capabilities.isCreativeMode;
        for (int k = 0; k < n; k++) {
            EntityLiving e = null;
            for (int t = 0; t < MDX.length && e == null; t++) {
                int m = (first + k + t) % MDX.length, off = half + MOUTH_PAST + k / MDX.length * 2;
                e = shell(w, d, d.x + MDX[m] * off, d.z + MDZ[m] * off, id);
            }
            if (e != null) {
                if (chase) {
                    e.setAttackTarget(finder);
                }
                out.add(e);
            }
        }
        return out;
    }

    /** One Shell of the patrol standing in the canyon at column (x, z) (its floor, or a little over it), or null. */
    private static EntityLiving shell(WorldServer w, SignalDataSC d, int x, int z, long id) {
        int floor = SubstrateTerrainSC.canyonFloorY(w.getSeed(), x, z);
        int from = (floor >= 0 ? floor : d.y - 1) + 1;
        for (int y = from; y <= from + PATROL_UP; y++) {
            if (!w.blockExists(x, y, z)) {
                return null;
            }
            Material m = w.getBlock(x, y - 1, z).getMaterial();
            if (w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z) && m.isSolid() && !m.isLiquid()) {
                EntityLiving e = SpawnDirectorSC.spawnAt(w, MobEntitiesSC.ID_HOLLOW_SHELL, x + 0.5D, y, z + 0.5D, true, false);
                if (e instanceof EntityHollowShellSC) {
                    ((EntityHollowShellSC) e).setPatrol(id, d.x, d.y, d.z);   // its home - the plaza
                }
                return e;
            }
        }
        return null;
    }

    /** `p` stands within `r` of the crossing's beam base (3D). */
    static boolean near(EntityPlayer p, SignalDataSC d, double r) {
        return p.getDistanceSq(d.x + 0.5D, d.y, d.z + 0.5D) <= r * r;
    }
}
