package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.ArmEntity;
import com.murat.alphalimber.entity.ModEntities;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TargetPointerItem extends Item {
    public TargetPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        Level world = useOnContext.getLevel();
        Vec3 hitPos = useOnContext.getClickLocation();

        if (!world.isClientSide()){
            AABB searchArea = new AABB(hitPos.x - 10, hitPos.y - 10, hitPos.z - 10,
                    hitPos.x + 10, hitPos.y + 10, hitPos.z + 10);

            List<ArmEntity> arms = world.getEntitiesOfClass(ArmEntity.class, searchArea);
            if (arms.isEmpty()) return InteractionResult.PASS;


            ArmEntity closest = arms.get(0);
            for(int i = 1; i < arms.size(); i++){
                if (arms.get(i).position().distanceTo(hitPos) < closest.position().distanceTo(hitPos)){
                    closest = arms.get(i);
                }
            }
            closest.setTarget(hitPos);
        }
        return InteractionResult.SUCCESS;
    }
}
