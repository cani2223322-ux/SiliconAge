package com.sc.mob;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7, rule ПК-9): the Substrate mobs' spawn eggs - ours, because the mobs are
 * registered with registerModEntity only (the global id pool that EnderIO, GraviSuite, TF and others share is never
 * touched, so vanilla's egg can't make them). Meta = the pool id of MobEntitiesSC; vanilla's two egg icons, tinted.
 * A mob from an egg is never «born in Si-14» (BornInSubstrateSC): it gives experience only - no trophies, no faction
 * drops (rule ПТ-1). For the creative mode and map makers.
 */
public class ItemSpawnEggSC extends Item {

    private IIcon spots;

    public ItemSpawnEggSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".spawnEgg");
    }

    /** «Яйцо призыва: Обходчик» - both sides (vanilla's egg does the same with StatCollector). */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = MobEntitiesSC.nameOf(stack.getItemDamage());
        String mob = name == null ? "?" : StatCollector.translateToLocal("entity." + Reference.MODID + "." + name + ".name");
        return StatCollector.translateToLocalFormatted(getUnlocalizedName() + ".name", mob).trim();
    }

    /** As vanilla's egg: on the face clicked (half a block up on a fence), the anvil's name kept. Creative: not used up. */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (world.isRemote) {
            return true;
        }
        String name = MobEntitiesSC.nameOf(stack.getItemDamage());
        if (name == null || !player.canPlayerEdit(x, y, z, side, stack)) {
            return false;
        }
        Block b = world.getBlock(x, y, z);
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];
        double up = side == 1 && b != null && b.getRenderType() == 11 ? 0.5D : 0D;
        Entity e = EntityList.createEntityByName(Reference.MODID + "." + name, world);
        if (!(e instanceof EntityLiving)) {
            return false;
        }
        EntityLiving mob = (EntityLiving) e;
        mob.setLocationAndAngles(x + 0.5D, y + up, z + 0.5D, world.rand.nextFloat() * 360F, 0F);
        mob.rotationYawHead = mob.rotationYaw;
        mob.renderYawOffset = mob.rotationYaw;
        mob.onSpawnWithEgg((IEntityLivingData) null);          // equipment and reserve - and no «born» flag
        world.spawnEntityInWorld(mob);
        mob.playLivingSound();
        if (stack.hasDisplayName()) {
            mob.setCustomNameTag(stack.getDisplayName());
        }
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }
        return true;
    }

    // ------------------------------------------------------------------ look: vanilla's egg in our colours

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon("spawn_egg");
        spots = register.registerIcon("spawn_egg_overlay");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        return pass > 0 ? spots : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        int id = stack.getItemDamage();
        if (id < 0 || id >= MobEntitiesSC.EGG_COLORS.length) {
            return 0xFFFFFF;
        }
        return MobEntitiesSC.EGG_COLORS[id][pass > 0 ? 1 : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int id = 0; id < MobEntitiesSC.NAMES.length; id++) {
            list.add(new ItemStack(item, 1, id));
        }
    }

    /** The mob it calls; Shift - why it gives no trophies; Ctrl - how to use it. */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.spawnEgg"), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.spawnEgg.details"), Lang.tr("sc.tooltip.spawnEgg.howto"));
    }
}
