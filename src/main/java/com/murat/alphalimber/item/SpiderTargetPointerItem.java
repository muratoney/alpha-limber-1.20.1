package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.SpiderEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SpiderTargetPointerItem extends Item {

    private static final double SEARCH_RADIUS = 20.0;

    public SpiderTargetPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Vec3 destination = context.getClickLocation();

        if (!world.isClientSide()) {
            SpiderEntity spider = findClosestSpider(world, context.getPlayer().position(), SEARCH_RADIUS);
            if (spider == null) return InteractionResult.PASS;
            spider.setBodyTarget(destination);
        }
        return InteractionResult.SUCCESS;
    }

    private static SpiderEntity findClosestSpider(Level world, Vec3 center, double radius) {
        AABB searchArea = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        List<SpiderEntity> spiders = world.getEntitiesOfClass(SpiderEntity.class, searchArea);
        if (spiders.isEmpty()) return null;

        SpiderEntity closest = spiders.get(0);
        for (int i = 1; i < spiders.size(); i++) {
            if (spiders.get(i).position().distanceTo(center) < closest.position().distanceTo(center)) {
                closest = spiders.get(i);
            }
        }
        return closest;
    }
}
