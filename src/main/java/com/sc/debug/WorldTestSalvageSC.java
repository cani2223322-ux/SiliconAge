package com.sc.debug;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.init.ModBlocks;
import com.sc.item.SalvageSC;
import com.sc.mob.MobItemsSC;
import com.sc.tileentity.TileEntityArmorStationSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * ОМ-6 (docs/plan-ideas-2026-10-26.md, -Dsc.worldtest=true; the Overworld, X = 4000 + 100 x 14, Y 200, Z 0): an Armour Service Station
 * with EU in its buffer, a Quantum chestplate trophy in its salvage slot (8): salvage(a test player) - the parts in the player's
 * inventory (as SalvageSC.result says), the slot empty, SalvageSC.STATION_EU off the buffer; an empty buffer - refused, the thing kept.
 * Removed afterwards (the player's things cleared).
 */
public class WorldTestSalvageSC {

    private static final int X = 5400, Y = 200, Z = 0;
    private static final GameProfile TESTER = new GameProfile(UUID.fromString("5c0e6a1e-0000-4000-8000-0000000000e6"), "[SC-Salvage]");
    private int ticks;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        WorldServer w = DimensionManager.getWorld(0);
        if (w == null || ModBlocks.armorStation == null || MobItemsSC.trophy == null) {
            return;
        }
        if (ticks == 60) {
            w.setBlock(X, Y, Z, ModBlocks.armorStation, 0, 3);
            w.setBlock(X + 3, Y, Z, ModBlocks.armorStation, 0, 3);
        }
        if (ticks == 80) {
            TileEntityArmorStationSC st = (TileEntityArmorStationSC) w.getTileEntity(X, Y, Z);
            TileEntityArmorStationSC dry = (TileEntityArmorStationSC) w.getTileEntity(X + 3, Y, Z);
            FakePlayer p = FakePlayerFactory.get(w, TESTER);
            p.inventory.clearInventory(null, -1);
            ItemStack trophy = new ItemStack(MobItemsSC.trophy, 1, 5);                // a Quantum chestplate trophy
            int expect = 0;
            for (ItemStack s : SalvageSC.result(trophy)) {
                expect += s.stackSize;
            }
            st.setEnergyStoredClient(st.getMaxEnergyStored());
            int before = st.getEnergyStored();
            st.setInventorySlotContents(TileEntityArmorStationSC.SALVAGE_SLOT, trophy.copy());
            boolean done = st.salvage(p);
            int got = 0;
            for (ItemStack s : p.inventory.mainInventory) {
                got += s == null ? 0 : s.stackSize;
            }
            boolean ok = done && expect > 0 && got == expect && st.getStackInSlot(TileEntityArmorStationSC.SALVAGE_SLOT) == null
                    && before - st.getEnergyStored() == SalvageSC.STATION_EU;
            dry.setEnergyStoredClient(0);
            dry.setInventorySlotContents(TileEntityArmorStationSC.SALVAGE_SLOT, trophy.copy());
            boolean refused = !dry.salvage(p) && dry.getStackInSlot(TileEntityArmorStationSC.SALVAGE_SLOT) != null;
            System.out.println("[SC-WORLDTEST] " + (ok && refused ? "PASS" : "FAIL") + " ОМ-6 salvage at the station: done " + done + ", parts "
                    + got + " of " + expect + ", EU off " + (before - st.getEnergyStored()) + ", the slot emptied "
                    + (st.getStackInSlot(TileEntityArmorStationSC.SALVAGE_SLOT) == null) + "; no EU - refused, kept " + refused);
            p.inventory.clearInventory(null, -1);
            dry.setInventorySlotContents(TileEntityArmorStationSC.SALVAGE_SLOT, null);
            w.setBlockToAir(X, Y, Z);
            w.setBlockToAir(X + 3, Y, Z);
        }
    }
}
