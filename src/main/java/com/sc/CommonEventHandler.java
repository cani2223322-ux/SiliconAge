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
    /** "Overheat: 70%" in chat (with a sound), at most every 30 s, again once it's cooled under 60%. */
    private static final int HEAT_WARN_CHAT_PCT = 70;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world != null && !event.world.isRemote) {
            EnergyNetSC.instance().serverTick(event.world);
            takeLightning(event.world);
        }
    }

    /**
     * A bolt that fell into a field with its rain shield up: the shield takes it (LIGHTNING_COST EU) -
     * the fire it lit round itself goes out, and it's gone before it can light more.
     */
    private static void takeLightning(net.minecraft.world.World world) {
        for (Object o : world.weatherEffects) {
            if (!(o instanceof net.minecraft.entity.effect.EntityLightningBolt)) {
                continue;
            }
            net.minecraft.entity.Entity bolt = (net.minecraft.entity.Entity) o;
            if (bolt.isDead || bolt.getEntityData().getBoolean("SCShieldSeen")) {
                continue;
            }
            bolt.getEntityData().setBoolean("SCShieldSeen", true);
            com.sc.tileentity.TileEntityFieldGeneratorSC f =
                    com.sc.tileentity.TileEntityFieldGeneratorSC.rainShieldAt(world, bolt.posX, bolt.posY, bolt.posZ);
            if (f == null || !f.pay(com.sc.tileentity.TileEntityFieldGeneratorSC.LIGHTNING_COST)) {
                continue;
            }
            int bx = net.minecraft.util.MathHelper.floor_double(bolt.posX), by = net.minecraft.util.MathHelper.floor_double(bolt.posY),
                    bz = net.minecraft.util.MathHelper.floor_double(bolt.posZ);
            for (int dx = -1; dx <= 1; dx++) {                     // where a bolt lights its fire, no further
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (world.getBlock(bx + dx, by + dy, bz + dz) == net.minecraft.init.Blocks.fire) {
                            world.setBlockToAir(bx + dx, by + dy, bz + dz);
                        }
                    }
                }
            }
            bolt.setDead();
            com.sc.util.SoundsSC.play(world, bolt.posX, bolt.posY, bolt.posZ, "field.zap", 3F, 0.9F + world.rand.nextFloat() * 0.2F);
        }
    }

    /**
     * A portal resets the client's abilities: the Exo flight has to be sent again. The searchlight's
     * light left in the old world goes out (there is no event before the move; clearLight finds the
     * old world by the dimension stored with the light).
     */
    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        com.sc.item.ArmorLogicSC.clearLight(event.player);
        com.sc.item.ArmorLogicSC.resendFlight(event.player);
    }

    /** Logging out: the searchlight's light goes out with the player. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            com.sc.item.ArmorLogicSC.clearLight(event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null) {
            return;
        }
        com.sc.item.ArmorLogicSC.tick(event.player);          // both sides: the client moves the player
        com.sc.item.BladeLogicSC.tick(event.player);          // server: the blade's deflect while blocking
        com.sc.radiation.LeadSuitSC.tick(event.player);       // both sides: the lead suit's weight
        if (event.player.worldObj.isRemote || event.player.ticksExisted % HEAT_INTERVAL_TICKS != 0) {
            return;
        }
        com.sc.radiation.RadiationSC.perSecond(event.player); // before the suit's second: the shield's heat counts in it
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
        // the Nether, lava, a desert sun; snow and water cool - also the heat already stored (excess below is clamped at 0)
        heatGen += environmentHeat(player);
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
        // the suit sheds its own little (passive); what's left the chestplate's helium loop takes
        // away (docs/plan-armor-gases.md: hybrid - the radiators of the other pieces help it)
        int excess = Math.max(0, heat + heatGen - suit.heatDissipation);
        ItemStack[] worn = com.sc.util.ArmorGasSC.wornSet(player);
        excess -= com.sc.util.ArmorGasSC.heliumCool(worn, excess, com.sc.item.ArmorLogicSC.fusionRunning(player));
        com.sc.util.ArmorGasSC.heliumBoilOff(worn, com.sc.item.ArmorLogicSC.fusionRunning(player));
        heliumWarning(player, worn, heat + heatGen - suit.heatDissipation > 0);
        heat = Math.min(suit.heatCapacity, excess);
        int pct = heat * 100 / suit.heatCapacity;
        if (pct >= HEAT_WARN_CHAT_PCT) {
            com.sc.item.ArmorLogicSC.warn(player, "sc.gas.warn.heat", 600);
        } else if (pct < HEAT_WARN_CHAT_PCT - 10) {
            com.sc.item.ArmorLogicSC.rearm(player, "sc.gas.warn.heat");
        }
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

    /**
     * Heat from round the wearer, a second: the Nether +3, lava or fire within 2 blocks (or burning) +5,
     * a hot biome (desert, savanna, mesa) under an open daytime sky +1; a cold biome -1, in water -2,
     * high up (y > 150) -1. Checked once a second, 5x3x5 blocks.
     */
    static int environmentHeat(EntityPlayer p) {
        net.minecraft.world.World w = p.worldObj;
        int heat = 0;
        if (w.provider.isHellWorld) {
            heat += 3;
        }
        int x = net.minecraft.util.MathHelper.floor_double(p.posX), y = net.minecraft.util.MathHelper.floor_double(p.posY),
                z = net.minecraft.util.MathHelper.floor_double(p.posZ);
        boolean hot = p.isBurning();
        for (int dx = -2; dx <= 2 && !hot; dx++) {
            for (int dz = -2; dz <= 2 && !hot; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    net.minecraft.block.material.Material m = w.getBlock(x + dx, y + dy, z + dz).getMaterial();
                    if (m == net.minecraft.block.material.Material.lava || m == net.minecraft.block.material.Material.fire) {
                        hot = true;
                        break;
                    }
                }
            }
        }
        if (hot) {
            heat += 5;
        }
        net.minecraft.world.biome.BiomeGenBase biome = w.getBiomeGenForCoords(x, z);
        float temp = biome == null ? 0.8F : biome.getFloatTemperature(x, y, z);
        if (temp >= 1.2F && w.isDaytime() && w.canBlockSeeTheSky(x, y + 1, z)) {
            heat += 1;
        } else if (temp < 0.15F) {
            heat -= 1;
        }
        if (p.isInWater()) {
            heat -= 2;
        }
        if (y > 150) {
            heat -= 1;
        }
        return heat;
    }

    /** "Helium running low" while the loop has to work and under a tenth is left; again after a refill. */
    private static void heliumWarning(EntityPlayer player, ItemStack[] worn, boolean cooling) {
        int cap = com.sc.util.ArmorGasSC.capacityOf(worn, com.sc.util.ArmorGasSC.Gas.HELIUM);
        int left = com.sc.util.ArmorGasSC.amountOf(worn, com.sc.util.ArmorGasSC.Gas.HELIUM);
        if (cap <= 0) {
            return;
        }
        if (left * 5 > cap) {
            com.sc.item.ArmorLogicSC.rearm(player, "sc.gas.warn.helium");
        } else if (cooling && left * 10 <= cap) {
            com.sc.item.ArmorLogicSC.warn(player, "sc.gas.warn.helium", 20 * 60 * 5);
        }
    }

    /**
     * Old worlds, first login after the life-support update: the worn pieces get a start of helium
     * and oxygen (ArmorGasSC.STARTER_PCT), once per player (persisted flag) and once per piece. The
     * flag is only set at a login with some of the mod's armour on - a player who came in without it
     * gets the start at a later login wearing it.
     */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer p = event.player;
        if (p == null || p.worldObj.isRemote) {
            return;
        }
        NBTTagCompound entityData = p.getEntityData();
        NBTTagCompound data = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        ItemStack[] worn = com.sc.util.ArmorGasSC.wornSet(p);
        if (!com.sc.util.ArmorGasSC.anyPiece(worn)) {
            return;                                 // nothing of the mod on: try again next login
        }
        if (!data.getBoolean("scGasStart")) {
            data.setBoolean("scGasStart", true);
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
            if (com.sc.util.ArmorGasSC.giveStarter(worn) > 0) {
                p.inventoryContainer.detectAndSendChanges();
                p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.gas.starter", com.sc.util.ArmorGasSC.STARTER_PCT));
            }
        }
        // the strict rules (Quantum / Exo run every function on a gas): a quarter of hydrogen once, and the news
        boolean strict = false;
        for (ItemStack s : worn) {
            strict |= s != null && com.sc.item.ArmorLogicSC.strict(((ItemArmorSC) s.getItem()).getSuit());
        }
        // only with a Quantum / Exo piece on (a Nano-only login must not use the flag up: no hydrogen, no news)
        if (strict && !data.getBoolean("scGasStart2")) {
            data.setBoolean("scGasStart2", true);
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
            if (com.sc.util.ArmorGasSC.giveHydrogenStarter(worn) > 0) {
                p.inventoryContainer.detectAndSendChanges();
            }
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.gas.starter2"));
        }
    }

    private static int chipTier(NBTTagCompound chips, ChipType type) {
        return Math.max(1, Math.min(3, chips.getInteger(type.name())));
    }

    private static void applyChip(EntityPlayer player, ChipType type, int tier) {
        int duration = HEAT_INTERVAL_TICKS + 5;
        if (type.isGasChip()) {
            return;                         // life-support chips act through the gases (ArmorLogicSC / ArmorGasSC)
        }
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
