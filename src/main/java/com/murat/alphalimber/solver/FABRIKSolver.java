package com.murat.alphalimber.solver;

import net.minecraft.world.phys.Vec3;


public class FABRIKSolver {
    // Forward: tip -> root
    public static Vec3[] forwardPass(Vec3[] joints, Vec3 target, double segLength){
        Vec3[] result = new Vec3[joints.length];
        result[joints.length - 1] = target;
        for (int i = joints.length - 2; i >= 0; i--){
            Vec3 direction = joints[i].subtract(result[i+1]).normalize();
            result[i] = result[i+1].add(direction.scale(segLength));
        }
        return result;
    }

    // Backward: root -> tip
    public static Vec3[] backwardPass(Vec3[] joints, Vec3 root, double segLength){
        Vec3[] result = new Vec3[joints.length];
        result[0] = root;
        for (int i = 1; i < joints.length; i++){
            Vec3 direction = joints[i].subtract(result[i-1]).normalize();
            result[i] = result[i-1].add(direction.scale(segLength));
        }
        return result;
    }

    // Check if solved
    public static boolean isSolved(Vec3[] joints, Vec3 target, Vec3 root, double tolerance) {
        boolean tipReached = joints[joints.length - 1].distanceTo(target) <= tolerance;
        boolean rootAnchored = joints[0].distanceTo(root) <= tolerance;
        return tipReached && rootAnchored;
    }

    public static Vec3[] solve(Vec3[] joints, Vec3 root, Vec3 target,
                               double segLength, int maxIterations, double tolerance) {
        double maxReach = (joints.length - 1) * segLength;
        if (root.distanceTo(target) >= maxReach) {
            return straightenToward(joints, root, target, segLength);
        }

        Vec3[] current = joints;
        for (int iter = 0; iter < maxIterations; iter++) {
            if (isSolved(current, target, root, tolerance)) break;
            current = forwardPass(current, target, segLength);
            current = backwardPass(current, root, segLength);
        }
        return current;
    }

    public static Vec3[] straightenToward(Vec3[] joints, Vec3 root, Vec3 target, double segLength){
        Vec3[] result = new Vec3[joints.length];
        Vec3 direction = target.subtract(root).normalize();
        result[0] = root;
        for (int i = 1; i < joints.length; i++) {
            result[i] = result[i - 1].add(direction.scale(segLength));
        }
        return result;
    }

    public static Vec3 solveOneJoint(Vec3 currentJoint, Vec3 anchorJoint, double segLength) {
        Vec3 direction = currentJoint.subtract(anchorJoint).normalize();
        return anchorJoint.add(direction.scale(segLength));
    }

}
