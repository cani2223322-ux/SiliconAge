package com.sc.finale;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.SingularScheme;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE P (docs/plan-substrate-tech-f5.md §9.1, rule ПТ-25; proposal §9 «25% — Схема окраски «Литограф»», §7 «Старейшина | 3
 * Крупицы + 32 КЧ | Схема окраски «Наладчик»»): «Схема окраски» - meta FinaleRulesSC.PAINT_LITHOGRAPHER «Литограф» (the Breakdown
 * Warden's 25%), PAINT_TUNER «Наладчик» (the Elder's, «Почётный»). A right click unlocks its scheme (SingularScheme.L / M) for this
 * player for good (FinaleRulesSC.unlock - his persisted NBT, by UUID) and is spent; already unlocked - a line, kept. Only cosmetics:
 * the Singular station then offers the scheme in its ◄ ► (SingularStationMath.cycle with the player's mask - P wires the station).
 * <p>
 * Fixed (the coordinator's): the class, the metas and names (FinaleRulesSC.PAINT_*), the registry name "paintScheme". P's: the
 * icon per meta (textures/items/paintScheme_<name>.png - a swatch of the scheme's base and accent), the sound and the chat lines
 * (sc.paint.unlocked / sc.paint.known), the tooltip (sc.tooltip.paint.<name> + .details + .howto). Stack 1; uncommon / rare.
 * <p>
 * The use (package P): the server only, never a FakePlayer (a mod's machine has no persisted NBT of a player to keep it in) - the
 * client's click does nothing of its own. A new scheme: the line «открыта» with its name, the item spent (not in creative), a
 * chime; one already open: the line «уже открыта», the item kept (it can still be traded or given to a LAN friend - his mask is
 * his own). Nothing else changes: the pieces keep their schemes until the station's ◄ ► is pressed.
 */
public class ItemPaintSchemeSC extends Item {

    /** An icon per meta (paintScheme_<name>). */
    private IIcon[] icons;

    public ItemPaintSchemeSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".paintScheme");
        setTextureName(Reference.ASSETS + ":paintScheme_lithographer");
    }

    public static String nameOf(int meta) {
        return FinaleRulesSC.PAINT_NAMES[meta >= 0 && meta < FinaleRulesSC.PAINT_NAMES.length ? meta : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + nameOf(stack.getItemDamage());
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return stack.getItemDamage() == FinaleRulesSC.PAINT_LITHOGRAPHER ? EnumRarity.epic : EnumRarity.rare;
    }

    /** ПТ-25: the scheme unlocked for this player for good (server), the item spent; already open - said, kept. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player == null || player instanceof FakePlayer) {
            return stack;
        }
        SingularScheme scheme = FinaleRulesSC.schemeOf(stack.getItemDamage());
        if (scheme == null) {
            return stack;
        }
        if (FinaleRulesSC.unlock(player, scheme)) {
            if (!player.capabilities.isCreativeMode) {
                stack.stackSize--;
            }
            world.playSoundAtEntity(player, "random.levelup", 0.5F, 1.6F);
            tell(player, "sc.paint.unlocked", EnumChatFormatting.LIGHT_PURPLE, new ChatComponentTranslation(scheme.langKey()));
        } else {
            tell(player, "sc.paint.known", EnumChatFormatting.GRAY, new ChatComponentTranslation(scheme.langKey()));
        }
        return stack;
    }

    /** A chat line (the client translates it - the scheme's name too: its own language). */
    private static void tell(EntityPlayer p, String key, EnumChatFormatting colour, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    // ------------------------------------------------------------------ client

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[FinaleRulesSC.PAINT_NAMES.length];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":paintScheme_" + FinaleRulesSC.PAINT_NAMES[m]);
        }
        itemIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? itemIcon : icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < FinaleRulesSC.PAINT_NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String k = "sc.tooltip.paint." + nameOf(stack.getItemDamage());
        TooltipSC.wrap(list, Lang.tr(k), "§7");
        // .howto: the Warden's chance for «Литограф» (WardenRulesSC.PAINT_CHANCE, %); «Наладчик»'s text takes no number
        TooltipSC.more(list, Lang.tr(k + ".details"), Lang.tr(k + ".howto", Math.round(com.sc.mob.WardenRulesSC.PAINT_CHANCE * 100F)));
    }
}
