package com.murat.alphalimber.solver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BlockConstraintHandler {

    private static final double PUSH_OFFSET = 0.01;

    private BlockConstraintHandler() {}

    public static void applyConstraints(Vec3[] joints, Level level) {
        for (int i = 1; i < joints.length; i++) {
            BlockPos blockPos = BlockPos.containing(joints[i]);
            if (level.getBlockState(blockPos).isSolidRender(level, blockPos)) {
                joints[i] = pushOutOfBlock(joints[i], blockPos);
            }
        }
    }

    private static Vec3 pushOutOfBlock(Vec3 point, BlockPos blockPos) {
        double x = point.x, y = point.y, z = point.z;
        int bx = blockPos.getX(), by = blockPos.getY(), bz = blockPos.getZ();

        double[] distances = {
            x - bx,       // 0: minX face
            (bx + 1) - x, // 1: maxX face
            y - by,        // 2: minY face
            (by + 1) - y,  // 3: maxY face
            z - bz,        // 4: minZ face
            (bz + 1) - z   // 5: maxZ face
        };

        int minIdx = 0;
        for (int i = 1; i < distances.length; i++) {
            if (distances[i] < distances[minIdx]) minIdx = i;
        }

        return switch (minIdx) {
            case 0 -> new Vec3(bx - PUSH_OFFSET, y, z);
            case 1 -> new Vec3(bx + 1 + PUSH_OFFSET, y, z);
            case 2 -> new Vec3(x, by - PUSH_OFFSET, z);
            case 3 -> new Vec3(x, by + 1 + PUSH_OFFSET, z);
            case 4 -> new Vec3(x, y, bz - PUSH_OFFSET);
            default -> new Vec3(x, y, bz + 1 + PUSH_OFFSET);
        };
    }
}
