package com.murat.alphalimber.entity;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ArmHelper {

    private ArmHelper() {}

    public static ArmEntity findClosestArm(Level world, Vec3 center, double radius) {
        AABB searchArea = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        List<ArmEntity> arms = world.getEntitiesOfClass(ArmEntity.class, searchArea);
        if (arms.isEmpty()) return null;

        ArmEntity closest = arms.get(0);
        for (int i = 1; i < arms.size(); i++) {
            if (arms.get(i).position().distanceTo(center) < closest.position().distanceTo(center)) {
                closest = arms.get(i);
            }
        }
        return closest;
    }
}
