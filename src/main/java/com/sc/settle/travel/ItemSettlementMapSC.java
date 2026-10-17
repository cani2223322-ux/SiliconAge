package com.sc.settle.travel;

import java.util.List;

import com.sc.Reference;
import com.sc.dim.SubstrateSC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.util.TooltipSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f2.md §8.5, rule ПР-5): «Карта поселений» - the TravelRulesSC.MAP_DOCKS (3)
 * docks nearest to the settlement it was bought at, written into the stack when the Cartographer sells it (make).
 * Right-click: the docks in the chat (name, x z, distance) and into the player's known docks (the bridge lists them,
 * ПР-3). The tooltip lists them too. Stack 1. Bought from a Cartographer (3 КЧ, «Знакомый»).
 * <p>
 * The stack keeps what it showed at the purchase (the tooltip reads it on the client); a read-out takes the live record
 * when the registry still has it. A map from an ownerless Cartographer (a spawn egg's) shows the docks round «Первый
 * причал». The map is never used up; a held button reads it out once per TravelRulesSC.MAP_COOLDOWN.
 */
public class ItemSettlementMapSC extends Item {

    /** The stack's NBT: a list of {id, name: [element, number], x, y, z, dim} - x, y, z the dock's beacon. */
    public static final String NBT_DOCKS = "scDocks";

    public ItemSettlementMapSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".settlementMap");
        setTextureName(Reference.ASSETS + ":settlementMap");
    }

    /** Package T's Cartographer deal: a map of the docks nearest to settlement `from` (server). Never null after init. */
    public static ItemStack make(SettlementRegistrySC reg, SettlementRegistrySC.Settlement from) {
        ItemStack st = new ItemStack(SettlementItemsSC.settlementMap);
        SettlementRegistrySC.Settlement at = from;
        if (at == null && reg != null && SubstrateSC.active()) {
            at = reg.first(SubstrateSC.dimId());             // an ownerless Cartographer: round «Первый причал»
        }
        if (reg == null || at == null) {
            return st;
        }
        NBTTagList l = new NBTTagList();
        for (SettlementRegistrySC.Settlement s : reg.nearestDocks(at.dim, at.x + 0.5D, at.z + 0.5D, TravelRulesSC.MAP_DOCKS, -1)) {
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("id", s.id);
            e.setIntArray("name", new int[]{s.nameElement, s.nameNumber});
            e.setInteger("x", s.dockX);
            e.setInteger("y", s.dockY);
            e.setInteger("z", s.dockZ);
            e.setInteger("dim", s.dim);
            l.appendTag(e);
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setTag(NBT_DOCKS, l);
        st.setTagCompound(t);
        return st;
    }

    /** The docks a map holds (never null). */
    public static NBTTagList docks(ItemStack stack) {
        NBTTagCompound t = stack == null ? null : stack.getTagCompound();
        return t == null ? new NBTTagList() : t.getTagList(NBT_DOCKS, 10);
    }

    /** «Купол «Ниобий-4»» in this client's language (the tooltip, the bridge screens' list). Client only. */
    public static String settlementName(int element, int number) {
        int code = NpcNamesSC.code(element, number);
        int e = NpcNamesSC.element(code);
        String n = e < 0 ? Lang.tr("sc.settle.npcName.none")
                : Lang.tr("sc.settle.npcName", Lang.tr("sc.settle.element." + NpcNamesSC.ELEMENTS[e]), NpcNamesSC.number(code));
        return Lang.tr("sc.settle.name", n);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player instanceof FakePlayer || !TravelEventsSC.mayReadMap(player)) {
            return stack;
        }
        NBTTagList l = docks(stack);
        if (l.tagCount() == 0) {
            say(player, EnumChatFormatting.GRAY, new ChatComponentTranslation("sc.map.empty"));
            return stack;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(world);
        SettlementRegistrySC.PlayerRec rec = r == null ? null : r.player(player.getUniqueID());
        say(player, EnumChatFormatting.DARK_AQUA, new ChatComponentTranslation("sc.map.header", l.tagCount()));
        boolean learned = false;
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            int id = e.getInteger("id");
            SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
            int[] nm = e.getIntArray("name");
            int el = nm.length == 2 ? nm[0] : -1, no = nm.length == 2 ? nm[1] : 0;
            int x = e.getInteger("x"), z = e.getInteger("z"), dim = e.getInteger("dim");
            if (s != null && s.hasDock) {
                el = s.nameElement;                          // the live record (the map keeps what it was sold with)
                no = s.nameNumber;
                x = s.dockX;
                z = s.dockZ;
                dim = s.dim;
                if (rec != null && rec.knownDocks.add(Integer.valueOf(id))) {
                    learned = true;
                }
            }
            IChatComponent name = new ChatComponentTranslation("sc.settle.name", NpcNamesSC.name(NpcNamesSC.code(el, no)));
            if (player.dimension == dim) {
                double dx = player.posX - (x + 0.5D), dz = player.posZ - (z + 0.5D);
                say(player, EnumChatFormatting.AQUA, new ChatComponentTranslation("sc.map.line", name, x, z, (int) Math.round(Math.sqrt(dx * dx + dz * dz))));
            } else {
                say(player, EnumChatFormatting.AQUA, new ChatComponentTranslation("sc.map.lineFar", name, x, z));
            }
        }
        if (learned) {
            r.markDirty();
        }
        say(player, EnumChatFormatting.GRAY, new ChatComponentTranslation("sc.map.learned"));
        return stack;
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, ChatComponentTranslation c) {
        p.addChatComponentMessage(c.setChatStyle(new ChatStyle().setColor(color)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.settlementMap"));
        NBTTagList l = docks(stack);
        if (l.tagCount() == 0) {
            list.add("§8" + Lang.tr("sc.map.tip.empty"));
        }
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            int[] nm = e.getIntArray("name");
            list.add("§b" + Lang.tr("sc.map.tip.line", settlementName(nm.length == 2 ? nm[0] : -1, nm.length == 2 ? nm[1] : 0),
                    e.getInteger("x"), e.getInteger("z")));
        }
        TooltipSC.more(list, Lang.tr("sc.tooltip.settlementMap.details", TravelRulesSC.MAP_DOCKS), Lang.tr("sc.tooltip.settlementMap.howto"));
    }
}
