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

public class SpiderEntity extends Entity {

    // --- Constants ---

    private static final double SEGMENT_LENGTH = 1.0;
    private static final int NUM_SEGMENTS = 3;
    private static final int NUM_LEGS = 8;
    private static final double SOLVE_TOLERANCE = 0.1;
    private static final double LEG_ROOT_RADIUS = 0.8;
    private static final double MOVE_SPEED = 2.0 / 20.0; // 2 blocks per second

    // --- Synched Data ---

    private static final EntityDataAccessor<CompoundTag> LEG_DATA =
            SynchedEntityData.defineId(SpiderEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // --- State ---

    private Vec3[][] legs;
    private Vec3[][] previousLegs;
    private Vec3[] legTargets;
    private boolean[] isForwardPass;
    private Vec3 bodyTarget;

    // --- Constructor ---

    public SpiderEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LEG_DATA, new CompoundTag());
    }

    // --- Initialization ---

    public void initializeLegs(Vec3 center) {
        legs = new Vec3[NUM_LEGS][NUM_SEGMENTS + 1];
        legTargets = new Vec3[NUM_LEGS];
        isForwardPass = new boolean[NUM_LEGS];

        for (int leg = 0; leg < NUM_LEGS; leg++) {
            double angle = (2 * Math.PI * leg) / NUM_LEGS;
            double rootX = center.x + LEG_ROOT_RADIUS * Math.sin(angle);
            double rootZ = center.z + LEG_ROOT_RADIUS * Math.cos(angle);
            Vec3 root = new Vec3(rootX, center.y, rootZ);

            Vec3 direction = new Vec3(Math.sin(angle), -1.0 / 3.0, Math.cos(angle)).normalize();
            legs[leg][0] = root;
            for (int j = 1; j <= NUM_SEGMENTS; j++) {
                legs[leg][j] = legs[leg][j - 1].add(direction.scale(SEGMENT_LENGTH));
            }
            legTargets[leg] = legs[leg][NUM_SEGMENTS];
            isForwardPass[leg] = true;
        }

        previousLegs = copyLegs(legs);
        syncLegs();
    }

    // --- Network Sync ---

    public void syncLegs() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("numLegs", legs.length);
        for (int leg = 0; leg < legs.length; leg++) {
            tag.putInt("leg_" + leg + "_count", legs[leg].length);
            for (int j = 0; j < legs[leg].length; j++) {
                tag.putDouble("l" + leg + "j" + j + "x", legs[leg][j].x);
                tag.putDouble("l" + leg + "j" + j + "y", legs[leg][j].y);
                tag.putDouble("l" + leg + "j" + j + "z", legs[leg][j].z);
            }
        }
        this.entityData.set(LEG_DATA, tag);
    }

    // --- Tick ---

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (legs == null) {
                initializeLegs(position());
            }
            if (bodyTarget != null) {
                moveTowardTarget();
            }
            serverStep();
            return;
        }

        CompoundTag tag = this.entityData.get(LEG_DATA);
        if (!tag.contains("numLegs")) return;

        int numLegs = tag.getInt("numLegs");
        Vec3[][] newLegs = new Vec3[numLegs][];
        for (int leg = 0; leg < numLegs; leg++) {
            int count = tag.getInt("leg_" + leg + "_count");
            newLegs[leg] = new Vec3[count];
            for (int j = 0; j < count; j++) {
                newLegs[leg][j] = new Vec3(
                        tag.getDouble("l" + leg + "j" + j + "x"),
                        tag.getDouble("l" + leg + "j" + j + "y"),
                        tag.getDouble("l" + leg + "j" + j + "z")
                );
            }
        }

        previousLegs = legs;
        legs = newLegs;
        if (previousLegs == null) {
            previousLegs = copyLegs(legs);
        }
    }

    // --- Solving ---

    public void serverStep() {
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            solveLeg(leg);
        }
        syncLegs();
    }

    private void solveLeg(int leg) {
        Vec3 root = legs[leg][0];
        Vec3 target = legTargets[leg];

        if (FABRIKSolver.isSolved(legs[leg], target, root, SOLVE_TOLERANCE)) return;

        double maxReach = NUM_SEGMENTS * SEGMENT_LENGTH;
        if (root.distanceTo(target) >= maxReach) {
            legs[leg] = FABRIKSolver.straightenToward(legs[leg], root, target, SEGMENT_LENGTH);
        } else {
            if (isForwardPass[leg]) {
                legs[leg] = FABRIKSolver.forwardPass(legs[leg], target, SEGMENT_LENGTH);
            } else {
                legs[leg] = FABRIKSolver.backwardPass(legs[leg], root, SEGMENT_LENGTH);
            }
            isForwardPass[leg] = !isForwardPass[leg];
        }
        BlockConstraintHandler.applyConstraints(legs[leg], level());
    }

    // --- Movement ---

    public void setBodyTarget(Vec3 target) {
        this.bodyTarget = target;
    }

    private void moveTowardTarget() {
        Vec3 pos = position();
        Vec3 delta = bodyTarget.subtract(pos);
        double dist = delta.length();

        if (dist <= MOVE_SPEED) {
            setPos(bodyTarget.x, bodyTarget.y, bodyTarget.z);
            updateLegRoots(bodyTarget);
            bodyTarget = null;
        } else {
            Vec3 newPos = pos.add(delta.normalize().scale(MOVE_SPEED));
            setPos(newPos.x, newPos.y, newPos.z);
            updateLegRoots(newPos);
        }
    }

    private void updateLegRoots(Vec3 center) {
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            double angle = (2 * Math.PI * leg) / NUM_LEGS;
            double rootX = center.x + LEG_ROOT_RADIUS * Math.sin(angle);
            double rootZ = center.z + LEG_ROOT_RADIUS * Math.cos(angle);
            legs[leg][0] = new Vec3(rootX, center.y, rootZ);
            // legTargets stay fixed in world space — feet stay planted
        }
    }

    // --- Serialization ---

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int numLegs = tag.getInt("numLegs");
        if (numLegs == 0) return;

        legs = new Vec3[numLegs][];
        legTargets = new Vec3[numLegs];
        isForwardPass = new boolean[numLegs];

        for (int leg = 0; leg < numLegs; leg++) {
            int count = tag.getInt("leg_" + leg + "_count");
            legs[leg] = new Vec3[count];
            for (int j = 0; j < count; j++) {
                legs[leg][j] = new Vec3(
                        tag.getDouble("l" + leg + "j" + j + "x"),
                        tag.getDouble("l" + leg + "j" + j + "y"),
                        tag.getDouble("l" + leg + "j" + j + "z")
                );
            }
            legTargets[leg] = new Vec3(
                    tag.getDouble("target_" + leg + "_x"),
                    tag.getDouble("target_" + leg + "_y"),
                    tag.getDouble("target_" + leg + "_z")
            );
            isForwardPass[leg] = tag.getBoolean("forward_" + leg);
        }

        if (tag.contains("bodyTargetX")) {
            bodyTarget = new Vec3(
                    tag.getDouble("bodyTargetX"),
                    tag.getDouble("bodyTargetY"),
                    tag.getDouble("bodyTargetZ")
            );
        }

        syncLegs();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (legs == null) return;
        tag.putInt("numLegs", legs.length);
        for (int leg = 0; leg < legs.length; leg++) {
            tag.putInt("leg_" + leg + "_count", legs[leg].length);
            for (int j = 0; j < legs[leg].length; j++) {
                tag.putDouble("l" + leg + "j" + j + "x", legs[leg][j].x);
                tag.putDouble("l" + leg + "j" + j + "y", legs[leg][j].y);
                tag.putDouble("l" + leg + "j" + j + "z", legs[leg][j].z);
            }
            tag.putDouble("target_" + leg + "_x", legTargets[leg].x);
            tag.putDouble("target_" + leg + "_y", legTargets[leg].y);
            tag.putDouble("target_" + leg + "_z", legTargets[leg].z);
            tag.putBoolean("forward_" + leg, isForwardPass[leg]);
        }
        if (bodyTarget != null) {
            tag.putDouble("bodyTargetX", bodyTarget.x);
            tag.putDouble("bodyTargetY", bodyTarget.y);
            tag.putDouble("bodyTargetZ", bodyTarget.z);
        }
    }

    // --- Helpers ---

    private Vec3[][] copyLegs(Vec3[][] src) {
        Vec3[][] copy = new Vec3[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i].clone();
        }
        return copy;
    }

    // --- Accessors ---

    public Vec3[][] getLegs() { return legs; }
    public Vec3[][] getPreviousLegs() { return previousLegs; }
    public double getSegmentLength() { return SEGMENT_LENGTH; }
    public int getNumSegments() { return NUM_SEGMENTS; }
}
