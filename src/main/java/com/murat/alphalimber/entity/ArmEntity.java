package com.murat.alphalimber.entity;

import com.murat.alphalimber.solver.BlockConstraintHandler;
import com.murat.alphalimber.solver.FABRIKSolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ArmEntity extends Entity {

    // --- Constants ---

    private static final double SEGMENT_LENGTH = 1.0;
    private static final int NUM_SEGMENTS = 3;
    private static final double SOLVE_TOLERANCE = 0.1;

    // --- Synched Data Accessors ---

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

    // --- State ---

    private Vec3[] joints;
    private Vec3[] previousJoints;
    private Vec3 target;
    private boolean isForwardPass = true;
    private int currentJointIndex = -1;

    // --- Constructor & Data Definition ---

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

    // --- Initialization ---

    public void initializeJoints(Vec3 root) {
        this.joints = new Vec3[NUM_SEGMENTS + 1];
        this.joints[0] = root;
        for (int i = 1; i <= NUM_SEGMENTS; i++) {
            this.joints[i] = root.add(0, i * SEGMENT_LENGTH, 0);
        }
        setTarget(joints[joints.length - 1]);
        this.isForwardPass = true;
        syncJoints();
    }

    // --- Serialization ---

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
        tag.putInt("numSegments", NUM_SEGMENTS);
        tag.putDouble("segLength", SEGMENT_LENGTH);
        tag.putBoolean("isForwardPass", isForwardPass);

        tag.putDouble("targetX", target.x);
        tag.putDouble("targetY", target.y);
        tag.putDouble("targetZ", target.z);

        tag.putInt("jointCount", joints.length);
        for (int i = 0; i < joints.length; i++) {
            tag.putDouble("joint_" + i + "_x", joints[i].x);
            tag.putDouble("joint_" + i + "_y", joints[i].y);
            tag.putDouble("joint_" + i + "_z", joints[i].z);
        }
    }

    // --- Network Sync ---

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

    public void setTarget(Vec3 newTarget) {
        this.target = newTarget;
        this.entityData.set(TARGET_X, (float) newTarget.x);
        this.entityData.set(TARGET_Y, (float) newTarget.y);
        this.entityData.set(TARGET_Z, (float) newTarget.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) return;

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
            previousJoints = joints;
            joints = newJoints;
        }

        if (previousJoints == null && joints != null) {
            previousJoints = joints;
        }
    }

    // --- Solving ---

    public void performStep() {
        if (FABRIKSolver.isSolved(joints, target, position(), SOLVE_TOLERANCE)) return;

        double maxReach = NUM_SEGMENTS * SEGMENT_LENGTH;
        if (position().distanceTo(target) >= maxReach) {
            joints = FABRIKSolver.straightenToward(joints, position(), target, SEGMENT_LENGTH);
        } else {
            if (isForwardPass) {
                joints = FABRIKSolver.forwardPass(joints, target, SEGMENT_LENGTH);
            } else {
                joints = FABRIKSolver.backwardPass(joints, position(), SEGMENT_LENGTH);
            }
            isForwardPass = !isForwardPass;
        }
        BlockConstraintHandler.applyConstraints(joints, level());
    }

    public void stepOneJoint() {
        if (FABRIKSolver.isSolved(joints, target, position(), SOLVE_TOLERANCE)) return;

        double maxReach = NUM_SEGMENTS * SEGMENT_LENGTH;
        if (position().distanceTo(target) >= maxReach) {
            joints = FABRIKSolver.straightenToward(joints, position(), target, SEGMENT_LENGTH);
            syncJoints();
            return;
        }

        if (currentJointIndex == -1) {
            if (isForwardPass) {
                joints[joints.length - 1] = target;
                currentJointIndex = joints.length - 2;
            } else {
                joints[0] = position();
                currentJointIndex = 1;
            }
        } else {
            if (isForwardPass) {
                joints[currentJointIndex] = FABRIKSolver.solveOneJoint(
                        joints[currentJointIndex], joints[currentJointIndex + 1], SEGMENT_LENGTH);
                currentJointIndex--;
                if (currentJointIndex < 0) {
                    isForwardPass = false;
                    currentJointIndex = -1;
                }
            } else {
                joints[currentJointIndex] = FABRIKSolver.solveOneJoint(
                        joints[currentJointIndex], joints[currentJointIndex - 1], SEGMENT_LENGTH);
                currentJointIndex++;
                if (currentJointIndex >= joints.length) {
                    isForwardPass = true;
                    currentJointIndex = -1;
                }
            }
        }

        BlockConstraintHandler.applyConstraints(joints, level());
        syncJoints();
    }

    public void serverStep() {
        performStep();
        this.entityData.set(PASS_COUNT, this.entityData.get(PASS_COUNT) + 1);
        syncJoints();
    }

    // --- Accessors ---

    public Vec3[] getJoints() {
        return joints;
    }

    public Vec3[] getPreviousJoints() {
        return previousJoints;
    }

    public double getSegLength() {
        return SEGMENT_LENGTH;
    }

    public int getNumSegments() {
        return NUM_SEGMENTS;
    }
}
