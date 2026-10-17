package com.sc.mob.client;

import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4, rules ПС-9, ПБ-21): the Hollow Shell - an empty Exo suit: its skin
 * (textures/entity/hollowShell.png) is all but transparent, only the dark inner ribs and joint cables where the pieces part,
 * so through the gaps there is nothing inside. Rebooting (IMobActionSC.ACT_REBOOT) it slumps forward, the head hanging (the
 * server pitches it down); the sparks are the entity's own (client side).
 */
@SideOnly(Side.CLIENT)
public class RenderHollowShellSC extends RenderSubstrateMobSC {

    /** Degrees forward while it reboots, swaying a little. */
    public static final float REBOOT_LEAN = 14F;

    public RenderHollowShellSC() {
        super(new ModelBiped(), 0.5F, EntityHollowShellSC.NAME);
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        if (action != IMobActionSC.ACT_REBOOT) {
            return 0F;
        }
        return REBOOT_LEAN + 2F * (float) Math.sin((e.ticksExisted + partial) * 0.9F);
    }
}
