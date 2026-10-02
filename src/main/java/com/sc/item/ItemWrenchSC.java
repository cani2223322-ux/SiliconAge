package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.machine.UpgradeType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyStorageSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityMachineSC;
import com.sc.tileentity.TileEntityTankSC;
import com.sc.tileentity.TileEntityTransformerSC;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The mod's wrench, three tiers (IC2 / Thermal style):
 * - Wrench (steel): turns blocks - the mod's machines, generators, storages and transformers,
 *   and anything else that supports rotateBlock; sets cable / pipe sides like any wrench.
 * - Electric Wrench (LV, battery): + Dismantle mode - the block goes straight into the inventory
 *   with its charge, fuel and tanks (what breaking it keeps), nothing lost.
 * - Quantum Wrench (HV, battery): + dismantles from up to 8 blocks away, + Copy mode - settings
 *   from one block to another like IC2's memory card (a field generator's settings, a
 *   transformer's direction, a machine's upgrade set, filled from the player's inventory).
 * Sneak + right-click in the air switches the mode. The name has "wrench" in it, so every
 * wrench check of the mod's blocks (BlockConduitSC.isWrench) takes it too.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemWrenchSC extends Item implements ic2.api.item.ISpecialElectricItem {

    public enum Tier {
        BASIC("wrench", 0, 0, com.sc.energy.Tier.LV),
        ELECTRIC("wrenchElectric", 10000, 250, com.sc.energy.Tier.LV),
        QUANTUM("wrenchQuantum", 100000, 250, com.sc.energy.Tier.HV);

        public final String name;
        public final int maxCharge;
        /** EU a dismantle costs (double from afar); copying / pasting costs a fifth of it. */
        public final int dismantleCost;
        public final com.sc.energy.Tier chargeTier;

        Tier(String name, int maxCharge, int dismantleCost, com.sc.energy.Tier chargeTier) {
            this.name = name;
            this.maxCharge = maxCharge;
            this.dismantleCost = dismantleCost;
            this.chargeTier = chargeTier;
        }

        public int modes() {
            return ordinal() + 1;           // basic: rotate; electric: + dismantle; quantum: + copy
        }
    }

    public static final int MODE_ROTATE = 0, MODE_DISMANTLE = 1, MODE_COPY = 2;
    public static final int REMOTE_RANGE = 8;
    private static final String CHARGE = "ChargeSC", MODE = "WrenchMode", COPY = "WrenchCopy";

    public final Tier tier;
    private IIcon icon;
    private Object ic2Manager;

    public ItemWrenchSC(Tier tier) {
        this.tier = tier;
        setMaxStackSize(1);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + tier.name);
    }

    // ------------------------------------------------------------------ charge

    public static boolean isElectric(ItemStack s) {
        return s != null && s.getItem() instanceof ItemWrenchSC && ((ItemWrenchSC) s.getItem()).tier.maxCharge > 0;
    }

    public static Tier tierOf(ItemStack s) {
        return ((ItemWrenchSC) s.getItem()).tier;
    }

    public static int chargeOf(ItemStack s) {
        return s != null && s.hasTagCompound() ? s.getTagCompound().getInteger(CHARGE) : 0;
    }

    private static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        return s.getTagCompound();
    }

    /** Charges up to `max` EU. @return EU taken */
    public static int charge(ItemStack s, int max) {
        if (!isElectric(s) || max <= 0) {
            return 0;
        }
        int taken = Math.max(0, Math.min(tierOf(s).maxCharge - chargeOf(s), max));
        if (taken > 0) {
            tag(s).setInteger(CHARGE, chargeOf(s) + taken);
        }
        return taken;
    }

    /** Spends up to `amount` EU (IC2's manager). @return EU actually spent */
    public static int discharge(ItemStack s, int amount) {
        int spent = isElectric(s) ? Math.max(0, Math.min(chargeOf(s), amount)) : 0;
        if (spent > 0) {
            tag(s).setInteger(CHARGE, chargeOf(s) - spent);
        }
        return spent;
    }

    private static boolean spend(EntityPlayer p, ItemStack s, int eu) {
        if (p.capabilities.isCreativeMode || eu <= 0) {
            return true;
        }
        if (chargeOf(s) < eu) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.nocharge", eu));
            return false;
        }
        tag(s).setInteger(CHARGE, chargeOf(s) - eu);
        return true;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return tier.maxCharge > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return tier.maxCharge > 0 ? 1.0 - (double) chargeOf(stack) / tier.maxCharge : 0;
    }

    // ------------------------------------------------------------------ modes

    public static int modeOf(ItemStack s) {
        int m = s.hasTagCompound() ? s.getTagCompound().getInteger(MODE) : 0;
        return m >= 0 && m < tierOf(s).modes() ? m : 0;
    }

    /** Sneak + right-click in the air: next mode. Quantum in Dismantle mode: right-click in the air dismantles from afar. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote && tier.modes() > 1) {
                int next = (modeOf(stack) + 1) % tier.modes();
                tag(stack).setInteger(MODE, next);
                player.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.mode",
                        new ChatComponentTranslation("sc.wrench.mode." + next)));
            }
            return stack;
        }
        if (!world.isRemote && tier == Tier.QUANTUM && modeOf(stack) == MODE_DISMANTLE
                && player.getEntityData().getLong("scWrenchAt") != world.getTotalWorldTime()) {   // not the click just used on a block
            MovingObjectPosition hit = lookedAt(world, player, REMOTE_RANGE);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && dismantlable(world, hit.blockX, hit.blockY, hit.blockZ)) {
                dismantle(stack, player, world, hit.blockX, hit.blockY, hit.blockZ, 2 * tier.dismantleCost, hit.subHit);
            }
        }
        return stack;
    }

    /** The block the player looks at within `range` (the look vector worked out here: getLook is client-only in places). */
    private static MovingObjectPosition lookedAt(World world, EntityPlayer p, double range) {
        float yaw = p.rotationYaw * 0.017453292F, pitch = p.rotationPitch * 0.017453292F;
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        Vec3 from = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3 to = from.addVector(lx * range, ly * range, lz * range);
        return world.rayTraceBlocks(from, to);
    }

    /** Sneaking with the wrench still reaches the block (machines: sneak + wrench turns them to the clicked side). */
    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    /**
     * Server side, before the block is clicked: Dismantle and Copy modes, and turning the mod's
     * blocks whose click opens a screen (generators, storages). Client: always false, or the
     * click would never reach the server.
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                                  float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return false;
        }
        int mode = modeOf(stack);
        if (mode == MODE_DISMANTLE) {
            if (!dismantlable(world, x, y, z)) {
                return false;
            }
            dismantle(stack, player, world, x, y, z, tier.dismantleCost, -1);
            player.getEntityData().setLong("scWrenchAt", world.getTotalWorldTime());
            return true;
        }
        if (mode == MODE_COPY) {
            return player.isSneaking() ? copy(stack, player, world, x, y, z) : paste(stack, player, world, x, y, z);
        }
        TileEntity te = world.getTileEntity(x, y, z);
        ForgeDirection clicked = ForgeDirection.getOrientation(side);
        if (te instanceof TileEntityGeneratorSC) {
            TileEntityGeneratorSC g = (TileEntityGeneratorSC) te;
            g.setFacing(player.isSneaking() && clicked.offsetY == 0 ? clicked : g.getFacing().getRotation(ForgeDirection.UP));
            g.markDirty();
            world.markBlockForUpdate(x, y, z);
            return true;
        }
        if (te instanceof TileEntityEnergyStorageSC) {
            TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
            boolean pad = te instanceof com.sc.tileentity.TileEntityChargePadSC;
            ForgeDirection next = player.isSneaking() ? clicked
                    : s.getFacing().offsetY != 0 ? ForgeDirection.NORTH : s.getFacing().getRotation(ForgeDirection.UP);
            if (pad && next == ForgeDirection.UP) {
                next = ForgeDirection.NORTH;             // the pad is on top
            }
            s.setFacing(next);
            s.markDirty();
            world.markBlockForUpdate(x, y, z);
            return true;
        }
        return false;                                    // machines, transformers, conduits handle the wrench themselves
    }

    // ------------------------------------------------------------------ storage output faces (Output Splitter)

    /**
     * Sneak + left-click with the mod's wrench on an energy storage's face (any mode): that face
     * becomes an extra output, or an input again (TileEntityEnergyStorageSC.toggleExtraOutput) -
     * right-click is already taken (turning the front: plain = a quarter turn, sneak = to the face).
     * Registered on the Forge bus in SCMod.preInit.
     */
    public static final class StorageFaceClick {
        /**
         * Quiet ticks before the next click toggles: every click (holding the button clicks again
         * every few ticks) pushes the mark on, so a held button toggles once and never back.
         */
        private static final int DEBOUNCE = 8;

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
            if (event.action != net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
                    || event.world.isRemote || !isStorageFaceClick(event.entityPlayer, event.world, event.x, event.y, event.z)) {
                return;
            }
            event.setCanceled(true);                   // never starts breaking the storage
            EntityPlayer p = event.entityPlayer;
            World world = event.world;
            long now = world.getTotalWorldTime();
            long last = p.getEntityData().getLong("scWrenchFaceAt");
            p.getEntityData().setLong("scWrenchFaceAt", now);      // sliding: each event moves the mark
            if (now - last < DEBOUNCE && now >= last) {
                return;
            }
            if (com.sc.ShieldEventHandler.privateFor(world, p, event.x, event.y, event.z)) {
                return;                                // someone else's private field
            }
            TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) world.getTileEntity(event.x, event.y, event.z);
            ForgeDirection face = ForgeDirection.getOrientation(event.face);
            ChatComponentTranslation side = new ChatComponentTranslation("sc.side." + face.name().toLowerCase(java.util.Locale.ROOT));
            switch (s.toggleExtraOutput(face)) {
                case TileEntityEnergyStorageSC.OUT_ADDED:
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.storage.out.added", side,
                            s.extraOutputCount(), s.outputSplitters()));
                    break;
                case TileEntityEnergyStorageSC.OUT_REMOVED:
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.storage.out.removed", side));
                    break;
                case TileEntityEnergyStorageSC.OUT_MAIN:
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.storage.out.main"));
                    break;
                case TileEntityEnergyStorageSC.OUT_FULL:
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.storage.out.full", s.outputSplitters()));
                    break;
                default:
                    p.addChatComponentMessage(new ChatComponentTranslation(
                            TileEntityEnergyStorageSC.overdriveWorks() ? "sc.storage.out.nomodule" : "sc.storage.out.noiu"));
            }
            world.markBlockForUpdate(event.x, event.y, event.z);   // a creative click broke it on the client: put it back whole
        }
    }

    /** The mod's wrench, sneaking, on an energy storage (not a charge pad - it takes no splitter). */
    static boolean isStorageFaceClick(EntityPlayer p, World world, int x, int y, int z) {
        ItemStack held = p == null ? null : p.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemWrenchSC) || !p.isSneaking()) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityEnergyStorageSC && !(te instanceof com.sc.tileentity.TileEntityChargePadSC);
    }

    /** Sneak + left-click on a storage sets its outputs - in creative it must not break it (client and server). */
    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        return player != null && isStorageFaceClick(player, player.worldObj, x, y, z);
    }

    /** Rotate mode, a block that didn't take the click itself: its own rotation, if it has one (vanilla and other mods). */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (world.isRemote || modeOf(stack) != MODE_ROTATE) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        return block.rotateBlock(world, x, y, z, ForgeDirection.getOrientation(side));
    }

    // ------------------------------------------------------------------ dismantle

    /** The mod's blocks that keep what matters in their item when broken (or have nothing inside). */
    private static boolean dismantlable(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityMachineSC || te instanceof TileEntityGeneratorSC || te instanceof TileEntityEnergyStorageSC
                || te instanceof TileEntityTransformerSC || te instanceof TileEntityTankSC || te instanceof TileEntityFieldGeneratorSC
                || te instanceof com.sc.tileentity.TileEntityQuarrySC || te instanceof com.sc.tileentity.TileEntityWirelessSC
                || te instanceof com.sc.tileentity.TileEntityConduitBundleSC || te instanceof com.sc.tileentity.TileEntityShowerSC;
    }

    /**
     * Takes the block into the inventory: its drops (they carry charge, fuel and tank contents)
     * go to the player, the rest (a machine's slots, a field generator's upgrades) drops where it
     * stood, exactly as breaking it would. Protection (a field's private zone, other mods'
     * claims) is asked first, as for any block break.
     */
    private void dismantle(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int cost, int subHit) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if (!world.canMineBlock(player, x, y, z)) {
            return;                                       // spawn protection (the remote dismantle's click never met it)
        }
        if (player instanceof EntityPlayerMP && net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(world,
                ((EntityPlayerMP) player).theItemInWorldManager.getGameType(), (EntityPlayerMP) player, x, y, z).isCanceled()) {
            return;
        }
        if (com.sc.block.BlockGeneratorSC.holdsHole(world, x, y, z)) {
            return;                                       // a Singular Reactor with its hole: put it out first (Stop)
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof com.sc.tileentity.TileEntityConduitBundleSC) {
            dismantlePart(stack, player, world, x, y, z, (com.sc.tileentity.TileEntityConduitBundleSC) te, block,
                    Math.max(1, cost / 10), subHit);
            return;
        }
        if (!spend(player, stack, cost)) {
            return;
        }
        java.util.ArrayList<ItemStack> drops = block.getDrops(world, x, y, z, meta, 0);
        world.setBlockToAir(x, y, z);                     // breakBlock: slots / upgrades out, field unlinked
        for (ItemStack d : drops) {
            if (d != null && !player.inventory.addItemStackToInventory(d)) {
                player.dropPlayerItemWithRandomChoice(d, false);
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, block.stepSound.getBreakSound(), 1.0F, 1.2F);
    }

    /**
     * A cable / pipe / tube bundle: the part under the cursor (a tenth of the price) goes into the
     * inventory, as breaking it would drop it (a pipe's fluid is lost, a tube's filters fall out);
     * the block goes with its last part.
     */
    private void dismantlePart(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                               com.sc.tileentity.TileEntityConduitBundleSC te, Block block, int cost, int subHit) {
        if (subHit < 0 && block instanceof com.sc.block.BlockConduitSC) {
            MovingObjectPosition hit = ((com.sc.block.BlockConduitSC) block).partUnderCursor(world, x, y, z, player);
            subHit = hit != null ? hit.subHit : -1;
        }
        com.sc.conduit.ConduitKind[] kinds = com.sc.conduit.ConduitKind.values();
        com.sc.conduit.ConduitKind kind = subHit >= 0 && subHit / 8 < kinds.length ? kinds[subHit / 8] : null;
        if (kind == null || !te.has(kind)) {
            kind = null;
            for (com.sc.conduit.ConduitKind k : kinds) {
                if (te.has(k)) {
                    kind = k;
                    break;
                }
            }
        }
        if (kind == null || !spend(player, stack, cost)) {
            return;
        }
        ItemStack drop = te.removePart(kind);
        if (te.isEmpty()) {
            world.setBlockToAir(x, y, z);
        }
        if (drop != null && !player.capabilities.isCreativeMode && !player.inventory.addItemStackToInventory(drop)) {
            player.dropPlayerItemWithRandomChoice(drop, false);
        }
        player.inventoryContainer.detectAndSendChanges();
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, block.stepSound.getBreakSound(), 1.0F, 1.2F);
    }

    // ------------------------------------------------------------------ copy / paste (Quantum)

    private boolean copy(ItemStack stack, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        NBTTagCompound data = new NBTTagCompound();
        if (te instanceof TileEntityFieldGeneratorSC && ((TileEntityFieldGeneratorSC) te).isMaster()) {
            data.setString("Kind", "field");
            ((TileEntityFieldGeneratorSC) te).exportSettings(data);
        } else if (te instanceof TileEntityTransformerSC) {
            data.setString("Kind", "transformer");
            data.setBoolean("StepUp", ((TileEntityTransformerSC) te).isStepUp());
        } else if (te instanceof TileEntityMachineSC) {
            data.setString("Kind", "machine");
            TileEntityMachineSC m = (TileEntityMachineSC) te;
            for (UpgradeType type : UpgradeType.values()) {
                data.setInteger(type.name(), m.upgradeCount(type));
            }
        } else {
            return false;
        }
        if (!spend(player, stack, tier.dismantleCost / 5)) {
            return true;
        }
        tag(stack).setTag(COPY, data);
        player.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.copied",
                new ChatComponentTranslation("sc.wrench.kind." + data.getString("Kind"))));
        return true;
    }

    private boolean paste(ItemStack stack, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityFieldGeneratorSC || te instanceof TileEntityTransformerSC || te instanceof TileEntityMachineSC)) {
            return false;
        }
        NBTTagCompound data = stack.hasTagCompound() ? stack.getTagCompound().getCompoundTag(COPY) : null;
        if (data == null || !data.hasKey("Kind")) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.nothing"));
            return true;
        }
        String kind = data.getString("Kind");
        boolean fits = "field".equals(kind) && te instanceof TileEntityFieldGeneratorSC && ((TileEntityFieldGeneratorSC) te).isMaster()
                || "transformer".equals(kind) && te instanceof TileEntityTransformerSC
                || "machine".equals(kind) && te instanceof TileEntityMachineSC;
        if (!fits) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.mismatch",
                    new ChatComponentTranslation("sc.wrench.kind." + kind)));
            return true;
        }
        if (te instanceof TileEntityFieldGeneratorSC && !((TileEntityFieldGeneratorSC) te).allowed(player)) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.field.noaccess", ((TileEntityFieldGeneratorSC) te).getOwner()));
            return true;
        }
        if (!spend(player, stack, tier.dismantleCost / 5)) {
            return true;
        }
        if (te instanceof TileEntityFieldGeneratorSC) {
            ((TileEntityFieldGeneratorSC) te).importSettings(data);
        } else if (te instanceof TileEntityTransformerSC) {
            ((TileEntityTransformerSC) te).setStepUp(data.getBoolean("StepUp"));
            te.markDirty();
        } else {
            int missing = fillUpgrades((TileEntityMachineSC) te, player, data);
            if (missing > 0) {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.missing", missing));
            }
        }
        world.markBlockForUpdate(x, y, z);
        player.addChatComponentMessage(new ChatComponentTranslation("sc.wrench.pasted",
                new ChatComponentTranslation("sc.wrench.kind." + kind)));
        return true;
    }

    /**
     * Brings the machine's upgrades up to the copied set, out of the player's inventory (never
     * takes any out of the machine). @return how many upgrades the player didn't have / didn't fit
     */
    private static int fillUpgrades(TileEntityMachineSC m, EntityPlayer p, NBTTagCompound data) {
        int missing = 0;
        for (UpgradeType type : UpgradeType.values()) {
            int want = data.getInteger(type.name()) - m.upgradeCount(type);
            while (want > 0) {
                int slot = upgradeSlotFor(m, type);
                int from = inventorySlotWith(p, type);
                if (slot < 0 || from < 0) {
                    missing += want;
                    break;
                }
                ItemStack there = m.getStackInSlot(slot);
                if (there == null) {
                    ItemStack one = p.inventory.decrStackSize(from, 1);
                    m.setInventorySlotContents(slot, one);
                } else {
                    p.inventory.decrStackSize(from, 1);
                    there.stackSize++;
                    m.markDirty();
                }
                want--;
            }
        }
        p.inventoryContainer.detectAndSendChanges();
        return missing;
    }

    private static int upgradeSlotFor(TileEntityMachineSC m, UpgradeType type) {
        int empty = -1;
        for (int i = 0; i < TileEntityMachineSC.UPGRADE_SLOTS; i++) {
            int slot = TileEntityMachineSC.FIRST_UPGRADE_SLOT + i;
            ItemStack s = m.getStackInSlot(slot);
            if (s == null) {
                empty = empty < 0 ? slot : empty;
            } else if (s.getItem() instanceof ItemUpgradeSC && ItemUpgradeSC.typeOf(s) == type && s.stackSize < s.getMaxStackSize()) {
                return slot;
            }
        }
        return empty;
    }

    private static int inventorySlotWith(EntityPlayer p, UpgradeType type) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.getItem() instanceof ItemUpgradeSC && ItemUpgradeSC.typeOf(s) == type) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ look and text

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":" + tier.name);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    /** Like the blades, drills and suits: the state, then the functions on Shift and how to use it on Ctrl. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        if (tier.maxCharge > 0) {
            list.add(Lang.tr("sc.wrench.charge", chargeOf(stack), tier.maxCharge, tier.chargeTier.name()));
        }
        list.add(Lang.tr("sc.wrench.modeline", Lang.tr("sc.wrench.mode." + modeOf(stack))));
        NBTTagCompound data = stack.hasTagCompound() ? stack.getTagCompound().getCompoundTag(COPY) : null;
        if (data != null && data.hasKey("Kind")) {
            list.add("§b" + Lang.tr("sc.wrench.holding", Lang.tr("sc.wrench.kind." + data.getString("Kind"))));
        }
        switch (com.sc.util.TooltipSC.page()) {
            case 1: {
                String[] fns = {"rotate", "wires", "dismantle", "remote", "copy"};
                boolean[] has = {true, true, tier.modes() > 1, tier == Tier.QUANTUM, tier == Tier.QUANTUM};
                java.util.List<String> names = new java.util.ArrayList<String>();
                java.util.List<Boolean> on = new java.util.ArrayList<Boolean>();
                int n = 0;
                for (int i = 0; i < fns.length; i++) {
                    names.add(Lang.tr("sc.wrench.fn." + fns[i]));
                    on.add(has[i]);
                    n += has[i] ? 1 : 0;
                }
                list.add(Lang.tr("sc.tooltip.wrench.functions", n, fns.length));
                com.sc.util.TooltipSC.pairs(list, names, on);
                if (tier.maxCharge > 0) {
                    list.add("§7" + Lang.tr("sc.tooltip.wrench.stats", tier.dismantleCost, 2 * tier.dismantleCost, tier.chargeTier.name()));
                }
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            }
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.wrench.howto." + tier.name().toLowerCase(java.util.Locale.ROOT)), "§7");
                if (tier.maxCharge > 0) {
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.wrench.charging", tier.chargeTier.name()), "§7");
                }
                break;
            default:
                com.sc.util.TooltipSC.hintShift(list);
        }
    }

    /** Creative tab: an electric wrench empty and charged, like the drills. */
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
        if (tier.maxCharge > 0) {
            ItemStack full = new ItemStack(item);
            tag(full).setInteger(CHARGE, tier.maxCharge);
            list.add(full);
        }
    }

    // ------------------------------------------------------------------ IC2: charged by batboxes / batteries (electric tiers)

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

    /** 0 for the plain wrench: the manager (ArmorElectricManagerSC) takes no charge into it. */
    @Override
    public double getMaxCharge(ItemStack stack) {
        return tier.maxCharge;
    }

    @Override
    public int getTier(ItemStack stack) {
        return tier.chargeTier.toIc2Tier();
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return tier.maxCharge > 0 ? tier.chargeTier.getVoltage() : 0;
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public ic2.api.item.IElectricItemManager getManager(ItemStack stack) {
        if (ic2Manager == null) {
            ic2Manager = new ArmorElectricManagerSC();
        }
        return (ic2.api.item.IElectricItemManager) ic2Manager;
    }
}
