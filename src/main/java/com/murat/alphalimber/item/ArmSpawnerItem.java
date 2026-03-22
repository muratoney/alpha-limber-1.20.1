package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.ArmEntity;
import com.murat.alphalimber.entity.ModEntities;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ArmSpawnerItem extends Item {
    public ArmSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        Level world = useOnContext.getLevel();
        Vec3 block_top = useOnContext.getClickedPos().getCenter().add(0, 0.5, 0);

        if (!world.isClientSide()){
            ArmEntity arm = new ArmEntity(ModEntities.ARM, world);
            arm.setPos(block_top.x, block_top.y, block_top.z);
            arm.initializeJoints(block_top);
            world.addFreshEntity(arm);
        }
        return InteractionResult.SUCCESS;
    }
}
