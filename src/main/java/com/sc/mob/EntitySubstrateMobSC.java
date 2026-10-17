package com.sc.mob;

import net.minecraft.entity.monster.EntityMob;
import net.minecraft.world.World;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §4.1): the base of the Substrate's hostiles WITHOUT the mod's armour - «Шум и
 * одичавшая техника»: the nanite swarm's particles, the feral sentry drone, the mimic machine (package X), the Hollow Shell's
 * repair drones (package M). An EntityMob (IMob: a settlement's field pushes it out, Peaceful removes it) and a native
 * (ISubstrateNativeSC: the bridge never takes it, the soul vial is refused, the «born in Si-14» flag applies - ПТ-1).
 * <p>
 * Fixed here (the coordinator's): the vanilla equipment drop off (ПС-6, as EntityArmoredMobSC: nothing worn ever drops -
 * the faction loot is MobDropsSC's under ПТ-1, through IFactionLootSC); DataWatcher DW_ACTION (25, the telegraph -
 * IMobActionSC); the census (ICensusSC: a plain native; a class answers otherwise - a swarm's other particles and the
 * mimic don't count). 20..24 and 26..31 belong to the subclasses. Subclasses call super in what they override here.
 */
public abstract class EntitySubstrateMobSC extends EntityMob implements ISubstrateNativeSC, ICensusSC, IMobActionSC {

    /** DataWatcher (fixed): the telegraph (byte, IMobActionSC.ACT_*) - the same slot as EntityArmoredMobSC's. */
    public static final int DW_ACTION = EntityArmoredMobSC.DW_ACTION;

    protected EntitySubstrateMobSC(World world) {
        super(world);
        for (int i = 0; i < 5; i++) {
            setEquipmentDropChance(i, 0F);
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_ACTION, Byte.valueOf((byte) IMobActionSC.ACT_NONE));
    }

    /** ПС-6: never drops what it holds (Looting or not). */
    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    public int mobAction() {
        return dataWatcher.getWatchableObjectByte(DW_ACTION);
    }

    @Override
    public void setMobAction(int action) {
        if (mobAction() != action) {
            dataWatcher.updateObject(DW_ACTION, Byte.valueOf((byte) action));
        }
    }

    @Override
    public boolean censusCounts() {
        return true;
    }

    @Override
    public int censusTier() {
        return TIER_PLAIN;
    }

    @Override
    public boolean censusElite() {
        return false;
    }
}
