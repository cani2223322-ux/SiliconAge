package com.sc.lore;

import java.util.List;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §6.1, rule ПЛ-1): «Журнал Литографа №N» - meta = N - 1 (JournalSC). A right click on
 * the client opens its pages (SCMod.proxy.openJournal -> lore.client.JournalClientSC: the handbook's screen on an article of its own
 * built from the lang - not one of the handbook's chapters, so the journals are read only by finding them); nothing on the server.
 * Stack 1; uncommon, №12 epic. The name «Журнал Литографа №%s» (item.siliconage.journalSC.name with the number), the tooltip - its
 * first line (sc.journal.<n>.title) and .date, Shift - where such journals are found, Ctrl - «ПКМ - читать».
 * <p>
 * Fixed (the coordinator's): the class, the metas, the registry name "journalSC", the client route. J's: the icon (one cover,
 * №12's own - textures/items/journal.png, journal12.png), the tooltip's lines.
 * <p>
 * The tooltip: the journal's own title (italic grey; a lang without it - sc.tooltip.journal) and its date (dark grey); Shift -
 * sc.tooltip.journal.details and a line where this number is found (sc.tooltip.journal.where with sc.tooltip.journal.src.<the first
 * source of JournalSC.POOLS holding it>: node, litho, vault - the Vaults' chests and the Sentinel -, wreck, signal, warden; left out
 * while the lang lacks them); Ctrl - sc.tooltip.journal.howto.
 */
public class ItemJournalSC extends Item {

    /** The sources' lang words (sc.tooltip.journal.src.<word>), by JournalSC.SRC_*. */
    public static final String[] SRC_KEYS = {"node", "litho", "vault", "sentinel", "wreck", "signal", "warden"};

    @SideOnly(Side.CLIENT)
    private IIcon last;

    public ItemJournalSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".journalSC");
        setTextureName(Reference.ASSETS + ":journal");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Lang.tr("item.siliconage.journalSC.name", JournalSC.numberOf(stack.getItemDamage()));
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return JournalSC.numberOf(stack.getItemDamage()) == JournalSC.COUNT ? EnumRarity.epic : EnumRarity.uncommon;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            SCMod.proxy.openJournal(JournalSC.numberOf(stack.getItemDamage()));
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(Reference.ASSETS + ":journal");
        last = register.registerIcon(Reference.ASSETS + ":journal12");
    }

    /** №1..№11 - the Lithographers' worn logbook; №12 - its own cover. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return JournalSC.numberOf(meta) == JournalSC.COUNT && last != null ? last : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int n = 1; n <= JournalSC.COUNT; n++) {
            list.add(new ItemStack(item, 1, JournalSC.metaOf(n)));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int n = JournalSC.numberOf(stack.getItemDamage());
        String title = "sc.journal." + n + ".title", date = "sc.journal." + n + ".date";
        TooltipSC.wrap(list, Lang.tr(StatCollector.canTranslate(title) ? title : "sc.tooltip.journal"), "§7§o");
        if (StatCollector.canTranslate(date)) {
            list.add("§8" + Lang.tr(date));
        }
        String details = Lang.tr("sc.tooltip.journal.details"), src = "sc.tooltip.journal.src." + sourceKey(n);
        if (StatCollector.canTranslate("sc.tooltip.journal.where") && StatCollector.canTranslate(src)) {
            details += "\n" + Lang.tr("sc.tooltip.journal.where", Lang.tr(src));
        }
        TooltipSC.more(list, details, Lang.tr("sc.tooltip.journal.howto"));
    }

    /** The lang word of the first source whose pool holds journal №`number` (JournalSC.POOLS). */
    public static String sourceKey(int number) {
        for (int s = 0; s < JournalSC.POOLS.length && s < SRC_KEYS.length; s++) {
            for (int k : JournalSC.POOLS[s]) {
                if (k == number) {
                    return SRC_KEYS[s];
                }
            }
        }
        return SRC_KEYS[JournalSC.SRC_NODE];
    }
}
