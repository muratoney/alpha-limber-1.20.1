package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.ArmEntity;
import com.murat.alphalimber.entity.ArmHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StepControllerItem extends Item {

    private static final double SEARCH_RADIUS = 10.0;

    public StepControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (!world.isClientSide()) {
            Vec3 center = player.blockPosition().getCenter();
            ArmEntity closest = ArmHelper.findClosestArm(world, center, SEARCH_RADIUS);
            if (closest == null) return InteractionResultHolder.pass(player.getItemInHand(hand));
            closest.serverStep();
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
