package com.sc.tileentity;

import java.util.Random;

import com.sc.block.BlockShowcaseSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.6, rule ПП-12): a showcase machine's light tile - the way its front
 * faces (set by the structure, sent to the client in the description packet: BlockShowcaseSC turns its icons by it)
 * and, on the client only, once in TICK ticks (staggered by position) the work's particles and now and then a quiet
 * sound: the workshop machines while their Tuners stand at them (world time 0..WORK_END, the Tuners' day), the guard
 * square's, the Clone Capsule's and the gate's always. Never an inventory, a recipe or energy; nothing on the server;
 * blockExists before a neighbour is read (the air over a machine).
 */
public class TileEntityShowcaseSC extends TileEntity {

    /** How often it does anything (ticks). */
    public static final int TICK = 20;
    /** The workshops work from dawn to here (the Tuners' day at their machines: package N's 0..11 500). */
    public static final int WORK_END = 11500;
    /** A quiet sound in 1 of this many effect ticks (a workshop of five machines isn't a din). */
    public static final int SOUND_ONE_IN = 4;

    /** ForgeDirection ordinal of the front (2..5), south by default. */
    private byte facing = 3;

    public int getFacing() {
        return facing;
    }

    /** The front's side (ForgeDirection 2..5); the structure sets it. */
    public void setFacing(int side) {
        if (side >= 2 && side <= 5 && side != facing) {
            facing = (byte) side;
            markDirty();
            if (worldObj != null && !worldObj.isRemote) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || !worldObj.isRemote
                || (worldObj.getTotalWorldTime() + (xCoord * 7 + yCoord * 3 + zCoord * 13 & 15)) % TICK != 0) {
            return;                                     // the server: nothing to do, ever
        }
        int meta = getBlockMetadata();
        if (meta < BlockShowcaseSC.ARMOUR_STATION && worldObj.getWorldTime() % 24000L >= WORK_END) {
            return;                                     // the workshop's Tuners are off for the evening
        }
        effects(meta, worldObj.rand);
    }

    /** The work's particles and sound by meta (client). */
    private void effects(int meta, Random r) {
        ForgeDirection f = ForgeDirection.getOrientation(facing);
        double cx = xCoord + 0.5D, cy = yCoord + 0.5D, cz = zCoord + 0.5D;
        double fx = cx + f.offsetX * 0.55D, fz = cz + f.offsetZ * 0.55D;     // just off the front face
        boolean airOver = worldObj.blockExists(xCoord, yCoord + 1, zCoord) && worldObj.isAirBlock(xCoord, yCoord + 1, zCoord);
        double top = yCoord + 1.05D;
        String sound = null;
        float volume = 0.2F, pitch = 1.0F;
        switch (meta) {
            case BlockShowcaseSC.CVD:
                front("witchMagic", 2, fx, cy, fz, f, r, 0D);
                sound = "random.fizz";
                volume = 0.05F;
                pitch = 1.6F;
                break;
            case BlockShowcaseSC.CZOCHRALSKI:
                front("flame", 1, fx, cy + 0.2D, fz, f, r, 0D);
                if (airOver) {
                    worldObj.spawnParticle("smoke", cx, top, cz, 0D, 0.03D, 0D);
                }
                break;
            case BlockShowcaseSC.STEPPER:
                for (int i = 0; i < 3; i++) {                                // a violet flicker of the exposure
                    worldObj.spawnParticle("reddust", fx + (r.nextDouble() - 0.5D) * Math.abs(f.offsetZ) * 0.7D, cy + (r.nextDouble() - 0.4D) * 0.6D,
                            fz + (r.nextDouble() - 0.5D) * Math.abs(f.offsetX) * 0.7D, 0.6D, 0.2D, 1.0D);
                }
                break;
            case BlockShowcaseSC.ETCH_BATH:
                if (airOver) {
                    over("splash", 3, cx, top, cz, r);
                }
                sound = "liquid.water";
                volume = 0.1F;
                break;
            case BlockShowcaseSC.ION_IMPLANTER:
                front("magicCrit", 3, fx, cy, fz, f, r, 0.05D);
                break;
            case BlockShowcaseSC.CRUSHER:
                front("smoke", 2, fx, cy - 0.2D, fz, f, r, 0D);
                sound = "step.gravel";
                volume = 0.3F;
                pitch = 0.6F;
                break;
            case BlockShowcaseSC.ORE_WASHER:
                front("splash", 4, fx, cy, fz, f, r, 0D);
                sound = "liquid.water";
                volume = 0.15F;
                break;
            case BlockShowcaseSC.BLAST_FURNACE:
                front("flame", 2, fx, cy - 0.1D, fz, f, r, 0D);
                if (airOver) {
                    worldObj.spawnParticle("largesmoke", cx, top, cz, 0D, 0.04D, 0D);
                }
                sound = "fire.fire";
                volume = 0.3F;
                pitch = 0.7F;
                break;
            case BlockShowcaseSC.AIR_SEPARATOR:
                front("cloud", 1, fx, cy, fz, f, r, 0D);
                sound = "random.fizz";
                volume = 0.08F;
                pitch = 0.5F;
                break;
            case BlockShowcaseSC.CENTRIFUGE:
                front("crit", 2, fx, cy, fz, f, r, 0.1D);
                break;
            case BlockShowcaseSC.GAS_TANK:
                if (airOver && r.nextInt(3) == 0) {
                    worldObj.spawnParticle("cloud", cx, top, cz, 0D, 0.02D, 0D);
                }
                break;
            case BlockShowcaseSC.FOCUSER:                                   // as the bridge's own focuser
                if (airOver) {
                    over("witchMagic", 2, cx, top, cz, r);
                    over("portal", 4, cx, top, cz, r);
                }
                break;
            case BlockShowcaseSC.ARMOUR_STATION:
                if (airOver) {
                    over("cloud", 1, cx, top, cz, r);
                }
                break;
            case BlockShowcaseSC.CHARGE_PAD:
                if (airOver) {
                    over("magicCrit", 2, cx, top, cz, r);
                }
                break;
            case BlockShowcaseSC.CLONE_CAPSULE:
                for (int i = 0; i < 2; i++) {                               // turquoise motes in the pod
                    worldObj.spawnParticle("mobSpell", fx, cy + (r.nextDouble() - 0.5D) * 0.6D, fz, 0.24D, 0.82D, 0.78D);
                }
                break;
            case BlockShowcaseSC.SHOWER:                                    // it sprays up, as the real one
                if (airOver) {
                    for (int i = 0; i < 6; i++) {
                        worldObj.spawnParticle("splash", xCoord + 0.1D + r.nextDouble() * 0.8D, top + r.nextDouble() * 1.6D,
                                zCoord + 0.1D + r.nextDouble() * 0.8D, 0D, 0.2D, 0D);
                    }
                }
                break;
            default:
                break;
        }
        if (sound != null && r.nextInt(SOUND_ONE_IN) == 0) {
            worldObj.playSound(cx, cy, cz, sound, volume, pitch * (0.9F + r.nextFloat() * 0.2F), false);
        }
    }

    /** `n` particles on the front face (spread across it), drifting out at `speed`. */
    private void front(String name, int n, double x, double y, double z, ForgeDirection f, Random r, double speed) {
        for (int i = 0; i < n; i++) {
            double across = (r.nextDouble() - 0.5D) * 0.7D;
            worldObj.spawnParticle(name, x + across * Math.abs(f.offsetZ), y + (r.nextDouble() - 0.5D) * 0.6D, z + across * Math.abs(f.offsetX),
                    f.offsetX * speed, 0.01D, f.offsetZ * speed);
        }
    }

    /** `n` particles just over the top. */
    private void over(String name, int n, double x, double y, double z, Random r) {
        for (int i = 0; i < n; i++) {
            worldObj.spawnParticle(name, x + (r.nextDouble() - 0.5D) * 0.6D, y, z + (r.nextDouble() - 0.5D) * 0.6D, 0D, 0.03D, 0D);
        }
    }

    // ------------------------------------------------------------------ NBT, the client's copy

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        int f = nbt.getByte("Facing");
        facing = (byte) (f >= 2 && f <= 5 ? f : 3);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("Facing", facing);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setByte("Facing", facing);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        int f = pkt.func_148857_g().getByte("Facing");
        if (f >= 2 && f <= 5 && f != facing) {
            facing = (byte) f;
            if (worldObj != null) {
                worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
            }
        }
    }
}
