package com.sc.emp;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModCreativeTab;

import cpw.mods.fml.common.Optional;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * ПЭ-1 «Подавитель электроники» / ПЭ-1М (meta 1, the Upgrade Station HV's upgrade) - docs/plan-pe1.md.
 * Registry name "empGun"; unlocalized item.siliconage.empGun / item.siliconage.empGunM.
 *
 * NBT (all on the stack's root):
 *  "ChargeSC"   int    EU (as every weapon of the mod), 0..capacityOf
 *  "EmpMode"    byte   the current EmpMode ordinal
 *  "EmpOff"     byte   bits of EmpMode switched off in the K menu (the wheel / keys skip them; never all three)
 *  "EmpMods"    int    bits of installed EmpModule
 *  "HeatSC"     int    the gun's heat 0..EmpMathSC.GUN_HEAT_MAX; "OverheatSC" boolean - Point / Pulse locked until below GUN_HEAT_RECOVER
 *  "EmpHeatAt"  long   world tick of the last heating (cooling waits GUN_COOL_DELAY)
 *  "EmpPulseAt" long   world tick of the last Pulse (PULSE_COOLDOWN)
 *  "EmpZone"    byte   Point target: EmpMathSC.ZONE_AUTO / HEAD / BODY / LEGS
 *  "EmpRecv"    int    receiver order, base-4 digits r0 + 4 r1 + 16 r2 (EmpMathSC.RECV_*); 0 / invalid = default 0,1,2
 *  "EmpRecvOff" byte   bits of receivers switched off
 *  "EmpLimit"   byte   EmpMathSC.LIMIT_VENT 0 = no limit (overflow vented), 1..100 = until my armour holds N%,
 *                     LIMIT_FULL 101 = until the receiver is full
 *  "EmpWarn"    boolean the overheat warning 2 s before (default on: stored inverted as "EmpNoWarn")
 * The upgrade flag is the meta (1 = ПЭ-1М); the Upgrade Station carries the whole tag over (package A).
 *
 * Behaviour (firing, charge-up, the stream, the gun's heat) is package A's EmpGunLogicSC; the tooltip package F's
 * EmpTooltipSC; the 3D model package D's RenderEmpGunSC (the icon is only the fallback / particle texture).
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemEmpGunSC extends Item implements ic2.api.item.ISpecialElectricItem {

    public static final String CHARGE = "ChargeSC", MODE = "EmpMode", OFF = "EmpOff", MODS = "EmpMods", HEAT = "HeatSC",
            OVERHEAT = "OverheatSC", HEAT_AT = "EmpHeatAt", PULSE_AT = "EmpPulseAt", ZONE = "EmpZone", RECV = "EmpRecv",
            RECV_OFF = "EmpRecvOff", LIMIT = "EmpLimit", NO_WARN = "EmpNoWarn";
    public static final int META_BASIC = 0, META_UPGRADED = 1;

    private IIcon[] icons = new IIcon[2];
    private Object ic2Manager;

    public ItemEmpGunSC() {
        setMaxStackSize(1);
        setMaxDamage(0);
        setHasSubtypes(true);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".empGun");
    }

    public static boolean isGun(ItemStack s) {
        return s != null && s.getItem() instanceof ItemEmpGunSC;
    }

    /** The ПЭ-1 in the player's hand, or null. */
    public static ItemStack held(EntityPlayer p) {
        ItemStack s = p == null ? null : p.getCurrentEquippedItem();
        return isGun(s) ? s : null;
    }

    public static boolean upgraded(ItemStack s) {
        return isGun(s) && s.getItemDamage() == META_UPGRADED;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + (upgraded(stack) ? "M" : "");
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icons[0] = register.registerIcon(Reference.ASSETS + ":empGun");
        icons[1] = register.registerIcon(Reference.ASSETS + ":empGunM");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icons[damage == META_UPGRADED ? 1 : 0];
    }

    // ------------------------------------------------------------------ NBT

    private static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        return s.getTagCompound();
    }

    private static NBTTagCompound peek(ItemStack s) {
        return isGun(s) && s.hasTagCompound() ? s.getTagCompound() : null;
    }

    public static boolean hasModule(ItemStack s, EmpModule m) {
        NBTTagCompound t = peek(s);
        return t != null && m != null && (t.getInteger(MODS) & m.bit()) != 0;
    }

    public static void setModule(ItemStack s, EmpModule m, boolean on) {
        if (!isGun(s) || m == null) {
            return;
        }
        int bits = tag(s).getInteger(MODS);
        tag(s).setInteger(MODS, on ? bits | m.bit() : bits & ~m.bit());
        if (!on && m == EmpModule.CAPACITY) {
            setCharge(s, chargeOf(s));                  // clamps to the smaller buffer
        }
    }

    public static int chargeOf(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null ? 0 : Math.max(0, t.getInteger(CHARGE));
    }

    public static int capacityOf(ItemStack s) {
        return isGun(s) ? EmpMathSC.capacity(hasModule(s, EmpModule.CAPACITY)) : 0;
    }

    public static void setCharge(ItemStack s, int eu) {
        if (isGun(s)) {
            tag(s).setInteger(CHARGE, Math.max(0, Math.min(capacityOf(s), eu)));
        }
    }

    /** Charges up to `max` EU. @return EU taken */
    public static int charge(ItemStack s, int max) {
        int taken = Math.max(0, Math.min(capacityOf(s) - chargeOf(s), max));
        if (taken > 0) {
            setCharge(s, chargeOf(s) + taken);
        }
        return taken;
    }

    /** Spends up to `eu`. @return EU spent */
    public static int discharge(ItemStack s, int eu) {
        int spent = Math.max(0, Math.min(chargeOf(s), eu));
        if (spent > 0) {
            setCharge(s, chargeOf(s) - spent);
        }
        return spent;
    }

    public static EmpMode modeOf(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null ? EmpMode.POINT : EmpMode.of(t.getByte(MODE));
    }

    public static void setMode(ItemStack s, EmpMode m) {
        if (isGun(s) && m != null) {
            tag(s).setByte(MODE, (byte) m.ordinal());
        }
    }

    public static boolean modeEnabled(ItemStack s, EmpMode m) {
        NBTTagCompound t = peek(s);
        return t == null || (t.getByte(OFF) & (1 << m.ordinal())) == 0;
    }

    /** Switches a mode on / off; the last enabled mode can't be switched off. @return whether it changed */
    public static boolean setModeEnabled(ItemStack s, EmpMode m, boolean on) {
        if (!isGun(s) || m == null || modeEnabled(s, m) == on) {
            return false;
        }
        int bits = tag(s).getByte(OFF) & 0xFF;
        int next = on ? bits & ~(1 << m.ordinal()) : bits | (1 << m.ordinal());
        if ((next & 7) == 7) {
            return false;
        }
        tag(s).setByte(OFF, (byte) next);
        if (!on && modeOf(s) == m) {
            setMode(s, step(s, m, 1));
        }
        return true;
    }

    /** The next enabled mode `delta` (+1 / -1) steps from `from` (itself when it's the only one). */
    public static EmpMode step(ItemStack s, EmpMode from, int delta) {
        EmpMode[] v = EmpMode.values();
        int i = from.ordinal();
        for (int n = 0; n < v.length; n++) {
            i = ((i + (delta < 0 ? -1 : 1)) % v.length + v.length) % v.length;
            if (modeEnabled(s, v[i])) {
                return v[i];
            }
        }
        return from;
    }

    public static int heatOf(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null ? 0 : t.getInteger(HEAT);
    }

    public static boolean overheated(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t != null && t.getBoolean(OVERHEAT);
    }

    /** Sets the heat (0..GUN_HEAT_MAX): reaching the max locks the gun, below GUN_HEAT_RECOVER unlocks it. */
    public static void setHeat(ItemStack s, int heat, long now) {
        if (!isGun(s)) {
            return;
        }
        NBTTagCompound t = tag(s);
        int h = Math.max(0, Math.min(EmpMathSC.GUN_HEAT_MAX, heat));
        if (h > t.getInteger(HEAT)) {
            t.setLong(HEAT_AT, now);
        }
        t.setInteger(HEAT, h);
        if (h >= EmpMathSC.GUN_HEAT_MAX) {
            t.setBoolean(OVERHEAT, true);
        } else if (h < EmpMathSC.GUN_HEAT_RECOVER) {
            t.removeTag(OVERHEAT);
        }
    }

    /** The end of a stream (EmpSiphonSC.flush): its heat, whether it ended overheated (throttled), the heating time now. */
    public static void setStreamHeat(ItemStack s, int heat, boolean overheated, long now) {
        if (!isGun(s)) {
            return;
        }
        NBTTagCompound t = tag(s);
        int h = Math.max(0, Math.min(EmpMathSC.GUN_HEAT_MAX, heat));
        t.setInteger(HEAT, h);
        t.setLong(HEAT_AT, now);
        if (overheated || h >= EmpMathSC.GUN_HEAT_MAX) {
            t.setBoolean(OVERHEAT, true);
        } else {
            t.removeTag(OVERHEAT);
        }
    }

    public static long lastHeatAt(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null ? 0L : t.getLong(HEAT_AT);
    }

    public static long lastPulseAt(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null ? 0L : t.getLong(PULSE_AT);
    }

    public static void setLastPulseAt(ItemStack s, long tick) {
        if (isGun(s)) {
            tag(s).setLong(PULSE_AT, tick);
        }
    }

    /** Pulse cooldown ticks left at world tick `now`. */
    public static int pulseCooldown(ItemStack s, long now) {
        long at = lastPulseAt(s);
        long left = at <= 0 || now < at ? 0 : at + EmpMathSC.PULSE_COOLDOWN - now;
        return (int) Math.max(0, left);
    }

    public static int zoneOf(ItemStack s) {
        NBTTagCompound t = peek(s);
        int z = t == null ? 0 : t.getByte(ZONE);
        return z >= 0 && z < EmpMathSC.ZONE_COUNT ? z : EmpMathSC.ZONE_AUTO;
    }

    public static void setZone(ItemStack s, int zone) {
        if (isGun(s) && zone >= 0 && zone < EmpMathSC.ZONE_COUNT) {
            tag(s).setByte(ZONE, (byte) zone);
        }
    }

    /** The receivers in order (a permutation of RECV_BATTERY, RECV_ARMOR, RECV_GUN). */
    public static int[] receiverOrder(ItemStack s) {
        NBTTagCompound t = peek(s);
        int packed = t == null ? 0 : t.getInteger(RECV);
        int[] o = unpackOrder(packed);
        return o != null ? o : new int[]{EmpMathSC.RECV_BATTERY, EmpMathSC.RECV_ARMOR, EmpMathSC.RECV_GUN};
    }

    /** Pure: base-4 digits -> a permutation of 0..2, or null when it isn't one. */
    public static int[] unpackOrder(int packed) {
        int[] o = {packed & 3, (packed >> 2) & 3, (packed >> 4) & 3};
        int seen = 0;
        for (int r : o) {
            if (r >= EmpMathSC.RECV_COUNT) {
                return null;
            }
            seen |= 1 << r;
        }
        return seen == 7 ? o : null;
    }

    public static int packOrder(int[] o) {
        return (o[0] & 3) | ((o[1] & 3) << 2) | ((o[2] & 3) << 4);
    }

    public static void setReceiverOrder(ItemStack s, int packed) {
        if (isGun(s) && unpackOrder(packed) != null) {
            tag(s).setInteger(RECV, packed);
        }
    }

    public static boolean receiverEnabled(ItemStack s, int recv) {
        NBTTagCompound t = peek(s);
        return t == null || (t.getByte(RECV_OFF) & (1 << recv)) == 0;
    }

    public static void setReceiverEnabled(ItemStack s, int recv, boolean on) {
        if (isGun(s) && recv >= 0 && recv < EmpMathSC.RECV_COUNT) {
            int bits = tag(s).getByte(RECV_OFF);
            tag(s).setByte(RECV_OFF, (byte) (on ? bits & ~(1 << recv) : bits | (1 << recv)));
        }
    }

    /** EmpMathSC.LIMIT_VENT (0, no limit - overflow vented), 1..100 = until my armour holds N%, LIMIT_FULL = until the receiver is full. */
    public static int limitOf(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null ? EmpMathSC.LIMIT_VENT : Math.max(EmpMathSC.LIMIT_VENT, Math.min(EmpMathSC.LIMIT_FULL, t.getByte(LIMIT)));
    }

    public static void setLimit(ItemStack s, int pct) {
        if (isGun(s)) {
            tag(s).setByte(LIMIT, (byte) Math.max(EmpMathSC.LIMIT_VENT, Math.min(EmpMathSC.LIMIT_FULL, pct)));
        }
    }

    public static boolean warnOn(ItemStack s) {
        NBTTagCompound t = peek(s);
        return t == null || !t.getBoolean(NO_WARN);
    }

    public static void setWarn(ItemStack s, boolean on) {
        if (isGun(s)) {
            if (on) {
                tag(s).removeTag(NO_WARN);
            } else {
                tag(s).setBoolean(NO_WARN, true);
            }
        }
    }

    // ------------------------------------------------------------------ the item

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - Math.min(1.0, (double) chargeOf(stack) / Math.max(1, capacityOf(stack)));
    }

    /** Creative tab: an empty and a full ПЭ-1 and ПЭ-1М. */
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int meta = META_BASIC; meta <= META_UPGRADED; meta++) {
            list.add(new ItemStack(item, 1, meta));
            ItemStack full = new ItemStack(item, 1, meta);
            setCharge(full, capacityOf(full));
            list.add(full);
        }
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        EmpTooltipSC.gun(stack, player, list);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;                                   // held: the charge-up / the stream (package A ends it)
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.none;
    }

    /** Sneak + right-click on a machine / generator: charge from its buffer (as the other weapons). */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyBase)) {
            return false;
        }
        if (!world.isRemote && ((TileEntityEnergyBase) te).canItemCharge(player)) {
            int moved = ((TileEntityEnergyBase) te).extractForItemCharging(capacityOf(stack) - chargeOf(stack));
            setCharge(stack, chargeOf(stack) + moved);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.charged", moved, chargeOf(stack), capacityOf(stack)));
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        return EmpGunLogicSC.rightClick(stack, world, player);
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
        EmpGunLogicSC.usingTick(stack, player, getMaxItemUseDuration(stack) - count);
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int itemInUseCount) {
        EmpGunLogicSC.stoppedUsing(stack, world, player, getMaxItemUseDuration(stack) - itemInUseCount);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        EmpGunLogicSC.inventoryTick(stack, world, entity, held);
    }

    // ------------------------------------------------------------------ IC2

    @Override
    public boolean canProvideEnergy(ItemStack stack) {
        return false;
    }

    @Override
    public Item getChargedItem(ItemStack stack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack stack) {
        return this;
    }

    @Override
    public double getMaxCharge(ItemStack stack) {
        return capacityOf(stack);
    }

    @Override
    public int getTier(ItemStack stack) {
        return EmpMathSC.CHARGE_TIER.toIc2Tier();
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return EmpMathSC.CHARGE_TIER.getVoltage();
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public ic2.api.item.IElectricItemManager getManager(ItemStack stack) {
        if (ic2Manager == null) {
            ic2Manager = new com.sc.item.ArmorElectricManagerSC();
        }
        return (ic2.api.item.IElectricItemManager) ic2Manager;
    }
}
