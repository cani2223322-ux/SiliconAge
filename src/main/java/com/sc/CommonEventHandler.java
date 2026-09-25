package com.sc;

import com.sc.energy.EnergyNetSC;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/** Drives EnergyNetSC's fallback relay (§9.4, a no-op under IC2) and the armor Heat system (§16). */
public class CommonEventHandler {

    // TODO(design doc §16): "heatGen = 5 x tier per tick" would hit the 100-heat cap in a few
    // seconds if taken literally - applied once a second (every 20 ticks) and scaled down; see
    // tickArmorHeat for the numbers.
    private static final int HEAT_INTERVAL_TICKS = 20;
    private static final int HEAT_CAPACITY_WARN_PCT = 80;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world != null && !event.world.isRemote) {
            EnergyNetSC.instance().serverTick(event.world);
        }
    }

    /** A portal resets the client's abilities: the Exo flight has to be sent again. */
    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        com.sc.item.ArmorLogicSC.resendFlight(event.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null) {
            return;
        }
        com.sc.item.ArmorLogicSC.tick(event.player);          // both sides: the client moves the player
        com.sc.item.BladeLogicSC.tick(event.player);          // server: the blade's deflect while blocking
        if (event.player.worldObj.isRemote || event.player.ticksExisted % HEAT_INTERVAL_TICKS != 0) {
            return;
        }
        tickArmorHeat(event.player, com.sc.item.ArmorLogicSC.perSecond(event.player));
    }

    /**
     * §16 Heat, once a second. Installed chips give their effect and heat the suit by their
     * tier; the suit sheds ArmorSuit.heatDissipation. Above 80% the wearer is slowed; at 100%
     * the chips shut off ("часть брони временно отключается") - no effects, no heat - until the
     * suit has cooled to half. Before this, chips had no effect at all and nothing ever cooled a
     * suit that had one installed, so any chip meant permanent Slowness + Weakness.
     *
     * TODO(design doc names chip categories only, not their effects): Sensor = Night Vision,
     * Power = Strength, Defense = Resistance, Mobility = Speed, Utility = Haste; level = tier.
     * Heat per second = sum of tiers (the doc's "5 x tier" per tick, scaled like the rest).
     * Chips run on the chestplate's charge (ArmorSuit.CHIP_EU_PER_TIER_SECOND x tier each second);
     * with too little EU left they switch off - no effects, no heat.
     */
    private void tickArmorHeat(EntityPlayer player, int functionHeat) {
        ItemStack chest = findChestplate(player);
        if (chest == null) {
            return;
        }
        ArmorSuit suit = ((ItemArmorSC) chest.getItem()).getSuit();
        NBTTagCompound chips = ItemArmorSC.chipsTag(chest);
        NBTTagCompound root = chest.getTagCompound();
        int heat = root.getInteger("HeatSC");
        boolean shutDown = root.getBoolean("ChipsOffSC");

        int heatGen = shutDown ? 0 : functionHeat;       // the suit's own functions heat it too
        if (!shutDown) {
            int cost = 0;
            for (ChipType type : ChipType.values()) {
                if (chips.hasKey(type.name())) {
                    cost += chipTier(chips, type) * ArmorSuit.CHIP_EU_PER_TIER_SECOND;
                }
            }
            cost = (int) Math.ceil(cost * com.sc.item.ArmorLogicSC.regenMul(player));   // regeneration: x3
            if (cost > 0 && ItemArmorSC.chargeOf(chest) >= cost) {
                ItemArmorSC.discharge(chest, cost);
                for (ChipType type : ChipType.values()) {
                    if (chips.hasKey(type.name())) {
                        int tier = chipTier(chips, type);
                        heatGen += tier;
                        applyChip(player, type, tier);
                    }
                }
            }
        }
        heat = Math.max(0, Math.min(suit.heatCapacity, heat + heatGen - suit.heatDissipation));
        int pct = heat * 100 / suit.heatCapacity;
        if (pct >= 100) {
            shutDown = true;
        } else if (pct <= 50) {
            shutDown = false;
        }
        root.setInteger("HeatSC", heat);
        root.setBoolean("ChipsOffSC", shutDown);

        if (shutDown) {
            player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.getId(), HEAT_INTERVAL_TICKS + 5, 1));
            player.addPotionEffect(new PotionEffect(Potion.weakness.getId(), HEAT_INTERVAL_TICKS + 5, 0));
        } else if (pct >= HEAT_CAPACITY_WARN_PCT) {
            player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.getId(), HEAT_INTERVAL_TICKS + 5, 0));
        }
    }

    private static int chipTier(NBTTagCompound chips, ChipType type) {
        return Math.max(1, Math.min(3, chips.getInteger(type.name())));
    }

    private static void applyChip(EntityPlayer player, ChipType type, int tier) {
        int duration = HEAT_INTERVAL_TICKS + 5;
        switch (type) {
            case SENSOR:
                // Night vision flickers in its last 10 s, so it's kept topped up well above that.
                player.addPotionEffect(new PotionEffect(Potion.nightVision.getId(), 260, 0, true));
                break;
            case POWER:
                player.addPotionEffect(new PotionEffect(Potion.damageBoost.getId(), duration, tier - 1, true));
                break;
            case DEFENSE:
                player.addPotionEffect(new PotionEffect(Potion.resistance.getId(), duration, tier - 1, true));
                break;
            case MOBILITY:
                player.addPotionEffect(new PotionEffect(Potion.moveSpeed.getId(), duration, tier - 1, true));
                break;
            default:
                player.addPotionEffect(new PotionEffect(Potion.digSpeed.getId(), duration, tier - 1, true));
        }
    }

    // §1 of 02_guide_book.md: "даётся игроку автоматически при первом крафте/подборе любого
    // предмета мода... once-флаг в NBT игрока".

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        giveManualOnce(event.player, event.crafting);
    }

    @SubscribeEvent
    public void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        giveManualOnce(event.player, event.pickedUp.getEntityItem());
    }

    private void giveManualOnce(EntityPlayer player, ItemStack triggerStack) {
        if (player.worldObj.isRemote || triggerStack == null || !isModItem(triggerStack)) {
            return;
        }
        // The persisted sub-tag: plain getEntityData() isn't copied to the respawned player, so
        // the flag was lost on every death and the next pickup handed out another manual.
        NBTTagCompound entityData = player.getEntityData();
        NBTTagCompound data = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (data.getBoolean("scManualGiven")) {
            return;
        }
        data.setBoolean("scManualGiven", true);
        entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
        if (!player.inventory.hasItem(ModItems.manual)) {
            ItemStack manual = new ItemStack(ModItems.manual);
            if (!player.inventory.addItemStackToInventory(manual)) {
                player.dropPlayerItemWithRandomChoice(manual, false);   // full inventory: at their feet, not lost
            }
        }
    }

    private static boolean isModItem(ItemStack stack) {
        if (stack.getItem() == null) {
            return false;
        }
        String name = (String) net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null && name.startsWith(com.sc.Reference.MODID + ":");
    }

    private static ItemStack findChestplate(EntityPlayer player) {
        for (ItemStack armor : player.inventory.armorInventory) {
            if (armor != null && armor.getItem() instanceof ItemArmorSC && ((ItemArmorSC) armor.getItem()).armorType == 1) {
                return armor;
            }
        }
        return null;
    }
}
