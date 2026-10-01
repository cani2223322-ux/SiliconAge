package com.sc.radiation;

import java.util.UUID;

import com.sc.item.ItemLeadSuitSC;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The lead suit's weight: each piece worn takes 15% of the walking speed (the full suit 60%), with
 * the full suit there's no running, the jump is lower and the wearer sinks in water; from three
 * pieces digging is slower; it tires (hunger) faster. Its protection - ConfigSC.leadSuitPartProtection
 * % of the radiation a piece (22 by default, at most 25) - is in RadiationSC.
 */
public final class LeadSuitSC {

    public static final UUID SPEED_ID = UUID.fromString("7a4e2f10-3c5b-4d8e-9f21-6b0c1d2e3f45");
    /** Speed taken a piece; the jump kept with the full suit; digging speed from three pieces; hunger a second a piece. */
    public static final double SPEED_PER_PART = 0.15;
    public static final float FULL_JUMP = 0.6F, HEAVY_DIG = 0.75F, EXHAUSTION_PER_PART = 0.025F;
    public static final int FULL = 4, HEAVY = 3;

    private LeadSuitSC() {
    }

    /** Lead suit pieces worn (0-4). */
    public static int parts(EntityPlayer p) {
        int n = 0;
        for (ItemStack s : p.inventory.armorInventory) {
            if (s != null && s.getItem() instanceof ItemLeadSuitSC) {
                n++;
            }
        }
        return n;
    }

    /** Every tick, both sides: speed (server - the client gets the attribute), no running, sinking. */
    public static void tick(EntityPlayer p) {
        int n = parts(p);
        if (!p.worldObj.isRemote) {
            IAttributeInstance speed = p.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
            AttributeModifier old = speed.getModifier(SPEED_ID);
            double want = -SPEED_PER_PART * n;
            if (old == null ? n > 0 : old.getAmount() != want) {
                if (old != null) {
                    speed.removeModifier(old);
                }
                if (n > 0) {
                    speed.applyModifier(new AttributeModifier(SPEED_ID, "Lead suit", want, 2));
                }
            }
            if (n > 0 && p.ticksExisted % 20 == 0 && !p.capabilities.isCreativeMode) {
                p.addExhaustion(EXHAUSTION_PER_PART * n);
            }
        }
        if (n >= FULL && !p.capabilities.isFlying) {
            if (p.isSprinting()) {
                p.setSprinting(false);
            }
            if (p.isInWater() && p.motionY > -0.2) {
                p.motionY -= 0.03;                          // too heavy to swim up
            }
        }
    }

    /** The jump with the suit on (LivingJumpEvent). */
    public static void jump(EntityPlayer p) {
        if (parts(p) >= FULL) {
            p.motionY *= FULL_JUMP;
        }
    }

    /** Digging speed with the suit on (BreakSpeed). */
    public static float digSpeed(EntityPlayer p, float speed) {
        return parts(p) >= HEAVY ? speed * HEAVY_DIG : speed;
    }
}
