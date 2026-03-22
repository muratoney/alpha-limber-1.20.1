package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.ArmEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class StepControllerItem  extends Item {
    public StepControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (!world.isClientSide()){
            Vec3 block_center = player.blockPosition().getCenter();
            AABB searchArea = new AABB(block_center.x - 10, block_center.y - 10, block_center.z - 10,
                    block_center.x + 10, block_center.y + 10, block_center.z + 10);

            List<ArmEntity> arms = world.getEntitiesOfClass(ArmEntity.class, searchArea);
            if (arms.isEmpty()) return InteractionResultHolder.pass(player.getItemInHand(hand));


            ArmEntity closest = arms.get(0);
            for(int i = 1; i < arms.size(); i++){
                if (arms.get(i).position().distanceTo(block_center) < closest.position().distanceTo(block_center)){
                    closest = arms.get(i);
                }
            }
            closest.serverStep();
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
