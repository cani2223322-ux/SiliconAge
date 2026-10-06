package com.sc.item;

import java.util.List;
import java.util.Locale;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.ChipType;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * §6/§16: armor chips, tiers I-III x 5 types (Sensor/Power/Defense/Mobility/Utility) = 15
 * metadata variants (well under the 16-value block cap - this is an Item, no cap either way).
 * Right-clicking while wearing a chestplate (armorType==1, see ItemArmorSC) installs the chip
 * into that piece's NBT chip list (§16's slot-conflict rule: at most one chip per type) -
 * ArmorTickHandler reads that NBT each tick to apply heatGen (§16: `5 x tier`).
 */
public class ItemArmorChipSC extends Item {

    private static final int TIER_COUNT = 3;

    private IIcon[] icons;

    public ItemArmorChipSC() {
        setHasSubtypes(true);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".armorChip");
    }

    public static ChipType typeAt(int meta) {
        ChipType[] types = ChipType.values();
        int index = meta / TIER_COUNT;
        return types[index >= 0 && index < types.length ? index : 0];
    }

    public static int tierAt(int meta) {
        return (meta % TIER_COUNT) + 1;
    }

    public static int metaFor(ChipType type, int tier) {
        return type.ordinal() * TIER_COUNT + (tier - 1);
    }

    public ItemStack stackOf(ChipType type, int tier) {
        return new ItemStack(this, 1, metaFor(type, tier));
    }

    @Override
    public void registerIcons(IIconRegister register) {
        ChipType[] types = ChipType.values();
        icons = new IIcon[types.length];
        for (ChipType type : types) {
            icons[type.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + type.textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons != null ? icons[typeAt(meta).ordinal()] : null;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        ChipType type = typeAt(stack.getItemDamage());
        return super.getUnlocalizedName() + "." + type.name().toLowerCase(Locale.ROOT);
    }

    /** Tiers used to be invisible - same name and icon for I, II and III. */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int tier = tierAt(stack.getItemDamage());
        list.add(com.sc.manual.Lang.tr("sc.tooltip.chip.tier", tierLabel(tier)));
        list.add(com.sc.manual.Lang.tr("sc.tooltip.chip.heat", tier));
        ChipType type = typeAt(stack.getItemDamage());
        if (type.isGasChip()) {
            list.add("§7" + com.sc.manual.Lang.tr("sc.tooltip.chip." + type.name().toLowerCase(Locale.ROOT), gasChipValue(type, tier)));
        }
    }

    /** What a life-support chip of that tier gives (the number its tooltip shows). */
    public static int gasChipValue(ChipType type, int tier) {
        int i = Math.max(1, Math.min(TIER_COUNT, tier)) - 1;
        switch (type) {
            case CRYO_LOOP: return com.sc.util.ArmorGasSC.CRYO_LOOP_PCT[i];
            case CRYO_TANK: return com.sc.util.ArmorGasSC.CRYO_TANK_PCT[i];
            case OXYGEN_REGEN: return com.sc.util.ArmorGasSC.OXYGEN_REGEN_BASE + com.sc.util.ArmorGasSC.OXYGEN_REGEN_PER_TIER * (i + 1);
            case RECUPERATOR: return com.sc.util.ArmorGasSC.RECUPERATOR_PCT[i];
            default: return 0;
        }
    }

    private static String tierLabel(int tier) {
        return tier == 1 ? "I" : tier == 2 ? "II" : "III";
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (ChipType type : ChipType.values()) {
            for (int tier = 1; tier <= TIER_COUNT; tier++) {
                list.add(new ItemStack(item, 1, metaFor(type, tier)));
            }
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            return stack;
        }
        ItemStack chest = findChestplate(player);
        if (chest == null) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.chip.nochest"));
            return stack;
        }
        ChipType type = typeAt(stack.getItemDamage());
        int tier = tierAt(stack.getItemDamage());
        if (!worksIn(type, ((ItemArmorSC) chest.getItem()).getSuit())) {
            // БР-5: a chip this suit can't run is refused - it used to go in and sit there doing nothing
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.chip.unsupported",
                    new ChatComponentTranslation(getUnlocalizedName(stack) + ".name"), chest.getDisplayName()));
            return stack;
        }
        net.minecraft.nbt.NBTTagCompound chips = ItemArmorSC.chipsTag(chest);
        // §16: one chip per type. Installing another of the same type swaps it in and hands the
        // old one back - a plain refusal meant an installed chip could never be upgraded or removed.
        ItemStack returned = null;
        if (chips.hasKey(type.name())) {
            int installed = chips.getInteger(type.name());
            if (installed == tier) {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.chip.same"));
                return stack;
            }
            returned = stackOf(type, Math.max(1, Math.min(TIER_COUNT, installed)));
        }
        chips.setInteger(type.name(), tier);
        com.sc.util.ArmorGasSC.applyCapacityBonus(com.sc.util.ArmorGasSC.wornSet(player));   // Cryo Tank: the tanks grow / shrink now (the gas kept, ArmorGasSC.applyCapacityBonus)
        stack.stackSize--;
        player.addChatComponentMessage(new ChatComponentTranslation(returned == null ? "sc.chat.chip.installed" : "sc.chat.chip.swapped",
                new ChatComponentTranslation(getUnlocalizedName(stack) + ".name"), tierLabel(tier)));
        if (returned != null && !player.inventory.addItemStackToInventory(returned)) {
            player.dropPlayerItemWithRandomChoice(returned, false);
        }
        return stack;                                          // an emptied stack (size 0) is removed by vanilla; null crashed tryUseItem
    }

    /**
     * БР-5: can this chip work in a chestplate of that suit? The Oxygen Regenerator only runs in an
     * Exo-class suit (ArmorLogicSC: ArmorSuit.exoClass); the rest work in all of them - every
     * chestplate has a helium loop and gas tanks (ArmorGasSC.CAP).
     */
    public static boolean worksIn(ChipType type, com.sc.util.ArmorSuit suit) {
        if (type == null || suit == null) {
            return false;
        }
        return type != ChipType.OXYGEN_REGEN || com.sc.util.ArmorSuit.exoClass(suit);
    }

    /** Whether the chestplate has any chip installed (read-only: safe on the client). */
    public static boolean hasChips(ItemStack chest) {
        return chest != null && chest.hasTagCompound() && !chest.getTagCompound().getCompoundTag("ChipsSC").hasNoTags();
    }

    /**
     * Server, from the armour screen's "remove chips" button: every chip installed in the worn
     * chestplate goes back to the inventory (at the player's feet if it's full). @return how many
     */
    public static int removeAll(EntityPlayer player) {
        ItemStack chest = findChestplate(player);
        if (!hasChips(chest) || com.sc.init.ModItems.armorChip == null) {
            return 0;
        }
        net.minecraft.nbt.NBTTagCompound chips = ItemArmorSC.chipsTag(chest);
        int removed = 0;
        for (ChipType type : ChipType.values()) {
            if (!chips.hasKey(type.name())) {
                continue;
            }
            ItemStack chip = com.sc.init.ModItems.armorChip.stackOf(type, Math.max(1, Math.min(TIER_COUNT, chips.getInteger(type.name()))));
            if (!player.inventory.addItemStackToInventory(chip)) {
                player.dropPlayerItemWithRandomChoice(chip, false);
            }
            removed++;
        }
        chest.getTagCompound().removeTag("ChipsSC");
        com.sc.util.ArmorGasSC.applyCapacityBonus(com.sc.util.ArmorGasSC.wornSet(player));   // no Cryo Tank: the tanks shrink, the extra stays hidden till the chip is back
        player.inventoryContainer.detectAndSendChanges();
        player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.chip.removed", removed));
        return removed;
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
