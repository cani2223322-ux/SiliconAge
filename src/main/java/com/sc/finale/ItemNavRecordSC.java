package com.sc.finale;

import java.util.List;

import com.sc.Reference;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSignalSC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE P (docs/plan-substrate-tech-f5.md §10.3, rule ПВ-13; proposal §4 «опция requireSignal: мир виден только игроку с
 * «Навигационной записью Si-14» (крафт: Половинка запутанного кристалла + Чип памяти + Контроллер)»): «Навигационная запись Si-14».
 * A right click (server): SubstrateSignalSC.grant - the Substrate shows in this player's bridge lists from now on (whatever the
 * rule says later); spent; already known - a line, kept. With requireSignal off it still records (a later switch-on keeps him in)
 * and says the world is open to all anyway. The recipe is the core's (FinaleRecipesSC: shapeless, the half's NBT ignored).
 * <p>
 * Fixed (the coordinator's): the class, the registry name "navRecord", the stack (16). P's: the use's body and its lines
 * (sc.navrecord.learned / .known / .open), the icon (textures/items/navRecord.png), the tooltip (sc.tooltip.navRecord + .details +
 * .howto; the details say whether requireSignal is on on this server - the rule is synced).
 * <p>
 * The use (package P): the server only, never a FakePlayer (SubstrateSignalSC.grant refuses one anyway). A new record: «записано»,
 * one spent (not in creative), a chime; already known: «уже есть», kept. The rule off on this server: the line «мир и так открыт
 * всем» follows either way. Nothing is sent: the bridge's lists are filtered by the server per viewer (the next screen update).
 */
public class ItemNavRecordSC extends Item {

    public ItemNavRecordSC() {
        setMaxStackSize(16);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".navRecord");
        setTextureName(Reference.ASSETS + ":navRecord");
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.uncommon;
    }

    /** ПВ-13: SubstrateSignalSC.grant (server, never a FakePlayer) - a new one spent, a known one kept; said either way. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player == null || player instanceof FakePlayer) {
            return stack;
        }
        if (SubstrateSignalSC.grant(player)) {
            if (!player.capabilities.isCreativeMode) {
                stack.stackSize--;
            }
            world.playSoundAtEntity(player, "random.orb", 0.5F, 0.7F);
            tell(player, "sc.navrecord.learned", EnumChatFormatting.AQUA);
        } else {
            tell(player, "sc.navrecord.known", EnumChatFormatting.GRAY);
        }
        if (!SubstrateConfigSC.requireSignal) {
            tell(player, "sc.navrecord.open", EnumChatFormatting.GRAY);   // recorded all the same: a later switch-on keeps him in
        }
        return stack;
    }

    private static void tell(EntityPlayer p, String key, EnumChatFormatting colour) {
        ChatComponentTranslation m = new ChatComponentTranslation(key);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.navRecord"), "§7");
        TooltipSC.more(list, Lang.tr(SubstrateConfigSC.requireSignal ? "sc.tooltip.navRecord.details.on" : "sc.tooltip.navRecord.details.off"),
                Lang.tr("sc.tooltip.navRecord.howto"));
    }
}
