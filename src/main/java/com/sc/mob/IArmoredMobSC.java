package com.sc.mob;

/**
 * A mob that wears the mod's armour for real (docs/plan-substrate-tech.md §4, rules ПБ-1..ПБ-9): the worn stacks
 * are only for the look (ItemArmorSC.getArmorModel works for any EntityLivingBase); the reserve, the shield and
 * the ПЭ-1 suppression live in the entity (MobArmorSC) and its DataWatcher - never in the stacks' NBT (every change
 * there would go to every watcher as S04PacketEntityEquipment with the whole NBT).
 * <p>
 * Both sides: on the server mobArmor() is the authoritative state; on a client its fields are empty and the
 * static MobArmorSC.client* readers take what the DataWatcher carries.
 */
public interface IArmoredMobSC {

    /** The mob's armour state. Never null. */
    MobArmorSC mobArmor();
}
