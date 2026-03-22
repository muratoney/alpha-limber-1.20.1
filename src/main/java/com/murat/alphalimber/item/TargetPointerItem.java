package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.ArmEntity;
import com.murat.alphalimber.entity.ArmHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TargetPointerItem extends Item {

    private static final double SEARCH_RADIUS = 10.0;

    public TargetPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        Level world = useOnContext.getLevel();
        Vec3 hitPos = useOnContext.getClickLocation();

        if (!world.isClientSide()) {
            ArmEntity closest = ArmHelper.findClosestArm(world, hitPos, SEARCH_RADIUS);
            if (closest == null) return InteractionResult.PASS;
            closest.setTarget(hitPos);
        }
        return InteractionResult.SUCCESS;
    }
}
