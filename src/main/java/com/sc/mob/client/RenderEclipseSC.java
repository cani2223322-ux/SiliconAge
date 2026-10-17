package com.sc.mob.client;

import com.sc.mob.EntityEclipseSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.4, rule ПС-9): «Затмение» - its black undersuit veined with crimson
 * (textures/entity/eclipse.png) under the Singular suit of the scheme C «Багровая», drawn SCALE larger. The heavy blow's
 * wind-up (ACT_WINDUP) rocks it back; its flight (EntityEclipseSC.fleeing) hunches it forward; the blink's cue (ACT_BLINK) makes
 * it flicker - not drawn every other pair of frames - while the entity's own particles stream off it.
 */
@SideOnly(Side.CLIENT)
public class RenderEclipseSC extends RenderSubstrateMobSC {

    public static final float SCALE = 1.1F, WINDUP_LEAN = -14F, FLEE_LEAN = 12F;

    public RenderEclipseSC() {
        super(headless(new ModelBiped()), 0.6F, EntityEclipseSC.NAME);
    }

    private static ModelBiped headless(ModelBiped m) {
        m.bipedHeadwear.showModel = false;
        return m;
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float partial) {
        if (e.deathTime <= 0 && e instanceof IMobActionSC && ((IMobActionSC) e).mobAction() == IMobActionSC.ACT_BLINK
                && (e.ticksExisted / 2) % 2 == 0) {
            return;                                          // the flicker before a blink
        }
        super.doRender(e, x, y, z, yaw, partial);
    }

    @Override
    protected float scale(EntityLivingBase e) {
        return SCALE;
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        if (action == IMobActionSC.ACT_WINDUP) {
            return WINDUP_LEAN;
        }
        return e instanceof EntityEclipseSC && ((EntityEclipseSC) e).fleeing() ? FLEE_LEAN : 0F;
    }
}
