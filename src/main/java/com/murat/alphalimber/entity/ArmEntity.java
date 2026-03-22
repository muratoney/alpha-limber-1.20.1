package com.murat.alphalimber.entity;

import com.murat.alphalimber.solver.FABRIKSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ArmEntity extends Entity {
    private Vec3[] joints;
    private Vec3 target;
    private boolean isForwardPass;
    private final double segLength = 1.0;
    private final int numSegments = 3;
    private int lastPassCount = 0;
    private Vec3[] previousJoints;
    private Vec3[] renderJoints;
    private int currentJointIndex = -1;
    private boolean inForwardPass = true;
    private static final EntityDataAccessor<CompoundTag> JOINT_DATA =
            SynchedEntityData.defineId(ArmEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> TARGET_X =
            SynchedEntityData.defineId(ArmEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y =
            SynchedEntityData.defineId(ArmEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z =
            SynchedEntityData.defineId(ArmEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PASS_COUNT =
            SynchedEntityData.defineId(ArmEntity.class, EntityDataSerializers.INT);


    public ArmEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_X, 0f);
        this.entityData.define(TARGET_Y, 0f);
        this.entityData.define(TARGET_Z, 0f);
        this.entityData.define(PASS_COUNT, 0);
        this.entityData.define(JOINT_DATA, new CompoundTag());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        isForwardPass = tag.getBoolean("isForwardPass");

        target = new Vec3(
                tag.getDouble("targetX"),
                tag.getDouble("targetY"),
                tag.getDouble("targetZ")
        );
        setTarget(target);

        int jointCount = tag.getInt("jointCount");
        joints = new Vec3[jointCount];
        for (int i = 0; i < jointCount; i++) {
            joints[i] = new Vec3(
                    tag.getDouble("joint_" + i + "_x"),
                    tag.getDouble("joint_" + i + "_y"),
                    tag.getDouble("joint_" + i + "_z")
            );
        }
        syncJoints();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("numSegments", numSegments);
        tag.putDouble("segLength", segLength);
        tag.putBoolean("isForwardPass", isForwardPass);

        // Save target
        tag.putDouble("targetX", target.x);
        tag.putDouble("targetY", target.y);
        tag.putDouble("targetZ", target.z);

        // Save joints array
        tag.putInt("jointCount", joints.length);
        for (int i = 0; i < joints.length; i++) {
            tag.putDouble("joint_" + i + "_x", joints[i].x);
            tag.putDouble("joint_" + i + "_y", joints[i].y);
            tag.putDouble("joint_" + i + "_z", joints[i].z);
        }
    }

    public void initializeJoints(Vec3 root){
        this.joints = new Vec3[numSegments + 1];
        this.joints[0] = root;
        for(int i = 1; i < numSegments + 1; i++){
            this.joints[i] = root.add(0, i * segLength, 0);
        }

        setTarget(joints[joints.length - 1]);
        this.isForwardPass = true;
        syncJoints();
    }

    public void syncJoints() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", joints.length);
        for (int i = 0; i < joints.length; i++) {
            tag.putDouble("j" + i + "x", joints[i].x);
            tag.putDouble("j" + i + "y", joints[i].y);
            tag.putDouble("j" + i + "z", joints[i].z);
        }
        this.entityData.set(JOINT_DATA, tag);
    }

    public void performStep() {
        if (FABRIKSolver.isSolved(joints, target, position(), 0.1)) return;

        double maxReach = numSegments * segLength;
        double distanceToTarget = position().distanceTo(target);

        if (distanceToTarget >= maxReach) {
            joints = FABRIKSolver.straightenToward(joints, position(), target, segLength);
        } else {
            if (isForwardPass) {
                joints = FABRIKSolver.forwardPass(joints, target, segLength);
            } else {
                joints = FABRIKSolver.backwardPass(joints, position(), segLength);
            }
            isForwardPass = !isForwardPass;
        }
        applyBlockConstraints();
    }

    public void stepOneJoint() {
        if (FABRIKSolver.isSolved(joints, target, position(), 0.1)) return;

        double maxReach = numSegments * segLength;
        if (position().distanceTo(target) >= maxReach) {
            joints = FABRIKSolver.straightenToward(joints, position(), target, segLength);
            syncJoints();
            return;
        }

        if (currentJointIndex == -1) {
            if (inForwardPass) {
                joints[joints.length - 1] = target;
                currentJointIndex = joints.length - 2;
            } else {
                joints[0] = position();
                currentJointIndex = 1;
            }
        } else {
            if (inForwardPass) {
                joints[currentJointIndex] = FABRIKSolver.solveOneJoint(
                        joints[currentJointIndex], joints[currentJointIndex + 1], segLength);
                currentJointIndex--;
                if (currentJointIndex < 0) {
                    inForwardPass = false;
                    currentJointIndex = -1;
                }
            } else {
                joints[currentJointIndex] = FABRIKSolver.solveOneJoint(
                        joints[currentJointIndex], joints[currentJointIndex - 1], segLength);
                currentJointIndex++;
                if (currentJointIndex >= joints.length) {
                    inForwardPass = true;
                    currentJointIndex = -1;
                }
            }
        }

        applyBlockConstraints();
        syncJoints();
    }

    public void serverStep() {
        performStep();
        this.entityData.set(PASS_COUNT, this.entityData.get(PASS_COUNT) + 1);
        syncJoints();
    }

    private void applyBlockConstraints() {
        for (int i = 1; i < joints.length; i++) {
            BlockPos blockPos = BlockPos.containing(joints[i]);
            if (level().getBlockState(blockPos).isSolidRender(level(), blockPos)) {
                // Find nearest face to push the joint out
                double x = joints[i].x;
                double y = joints[i].y;
                double z = joints[i].z;

                // Distance to each face of the block
                double distMinX = x - blockPos.getX();
                double distMaxX = (blockPos.getX() + 1) - x;
                double distMinY = y - blockPos.getY();
                double distMaxY = (blockPos.getY() + 1) - y;
                double distMinZ = z - blockPos.getZ();
                double distMaxZ = (blockPos.getZ() + 1) - z;

                // Find the smallest distance — that's the nearest face
                double min = Math.min(Math.min(Math.min(distMinX, distMaxX), Math.min(distMinY, distMaxY)), Math.min(distMinZ, distMaxZ));

                if (min == distMinX) {
                    joints[i] = new Vec3(blockPos.getX() - 0.01, y, z);
                } else if (min == distMaxX) {
                    joints[i] = new Vec3(blockPos.getX() + 1.01, y, z);
                } else if (min == distMinY) {
                    joints[i] = new Vec3(x, blockPos.getY() - 0.01, z);
                } else if (min == distMaxY) {
                    joints[i] = new Vec3(x, blockPos.getY() + 1.01, z);
                } else if (min == distMinZ) {
                    joints[i] = new Vec3(x, y, blockPos.getZ() - 0.01);
                } else {
                    joints[i] = new Vec3(x, y, blockPos.getZ() + 1.01);
                }
            }
        }
    }




    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            this.target = new Vec3(
                    this.entityData.get(TARGET_X),
                    this.entityData.get(TARGET_Y),
                    this.entityData.get(TARGET_Z)
            );

            CompoundTag jointTag = this.entityData.get(JOINT_DATA);
            if (jointTag.contains("count")) {
                int count = jointTag.getInt("count");
                Vec3[] newJoints = new Vec3[count];
                for (int i = 0; i < count; i++) {
                    newJoints[i] = new Vec3(
                            jointTag.getDouble("j" + i + "x"),
                            jointTag.getDouble("j" + i + "y"),
                            jointTag.getDouble("j" + i + "z")
                    );
                }

                // Shift: current becomes previous, new becomes current
                previousJoints = joints;
                joints = newJoints;
            }

            // First tick — no previous data yet
            if (previousJoints == null && joints != null) {
                previousJoints = joints;
            }
        }
    }

    public void setTarget(Vec3 newTarget) {
        this.target = newTarget;
        this.entityData.set(TARGET_X, (float) newTarget.x);
        this.entityData.set(TARGET_Y, (float) newTarget.y);
        this.entityData.set(TARGET_Z, (float) newTarget.z);
    }

    public Vec3[] getJoints() {
        return joints;
    }

    public double getSegLength() {
        return segLength;
    }

    public int getNumSegments() {
        return numSegments;
    }

    public Vec3[] getPreviousJoints() {
        return previousJoints;
    }
}
