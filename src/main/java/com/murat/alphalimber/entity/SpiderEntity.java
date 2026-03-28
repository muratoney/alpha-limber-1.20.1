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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class SpiderEntity extends Entity {

    // --- Constants ---

    private static final double SEGMENT_LENGTH = 1.0;
    private static final int NUM_SEGMENTS = 3;
    private static final int NUM_LEGS = 4;
    private static final double MOVE_SPEED = 4.0 / 20.0;   // 4 blocks/sec
    private static final double ROOT_Y_OFFSET = 1.3;        // root above body center
    private static final double REST_RADIUS = 1.5;          // horizontal foot distance
    private static final double STEP_THRESHOLD = 1.5;       // dist before foot steps
    private static final double STRAIGHTEN_HEIGHT = 1.25;   // upward bias before FABRIK

    // --- Synched Data ---

    private static final EntityDataAccessor<CompoundTag> LEG_DATA =
            SynchedEntityData.defineId(SpiderEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // --- State ---

    private Vec3[][] legs;
    private Vec3[][] previousLegs;
    private Vec3[] legTargets;
    private boolean[] legStepping;
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
        legStepping = new boolean[NUM_LEGS];

        Vec3 sharedRoot = new Vec3(center.x, center.y + ROOT_Y_OFFSET, center.z);

        for (int leg = 0; leg < NUM_LEGS; leg++) {
            double angle = (2 * Math.PI * leg) / NUM_LEGS;

            Vec3 restPos = restPosition(center, angle);
            legTargets[leg] = restPos;

            Vec3 direction = restPos.subtract(sharedRoot).normalize();
            legs[leg][0] = sharedRoot;
            for (int j = 1; j <= NUM_SEGMENTS; j++) {
                legs[leg][j] = legs[leg][j - 1].add(direction.scale(SEGMENT_LENGTH));
            }
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
            checkAndStepLegs();
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
        double maxReach = NUM_SEGMENTS * SEGMENT_LENGTH;

        if (root.distanceTo(target) < maxReach) {
            for (int i = 0; i < 10; i++) {
                legs[leg] = FABRIKSolver.forwardPass(legs[leg], target, SEGMENT_LENGTH);
                legs[leg] = FABRIKSolver.backwardPass(legs[leg], root, SEGMENT_LENGTH);
                if (legs[leg][NUM_SEGMENTS].distanceTo(target) < 0.01) break;
            }
        }
        if (legs[leg][NUM_SEGMENTS].distanceTo(target) < 0.05) {
            legStepping[leg] = false;
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
        Vec3 sharedRoot = new Vec3(center.x, center.y + ROOT_Y_OFFSET, center.z);
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            legs[leg][0] = sharedRoot;
        }
    }

    // --- Stepping ---

    private void checkAndStepLegs() {
        Vec3 center = position();
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            double angle = (2 * Math.PI * leg) / NUM_LEGS;
            Vec3 idealRest = restPosition(center, angle);
            int prev = (leg + NUM_LEGS - 1) % NUM_LEGS;
            int next = (leg + 1) % NUM_LEGS;
            boolean adjacentStepping = legStepping[prev] || legStepping[next];
            if (!adjacentStepping && idealRest.distanceTo(legTargets[leg]) > STEP_THRESHOLD) {
                legStepping[leg] = true;
                legTargets[leg] = idealRest;
                Vec3 root = legs[leg][0];
                Vec3 rawDir = idealRest.subtract(root).normalize();
                Vec3 biasedDir = new Vec3(rawDir.x, rawDir.y + STRAIGHTEN_HEIGHT, rawDir.z).normalize();
                for (int j = 0; j < legs[leg].length; j++) {
                    legs[leg][j] = root.add(biasedDir.scale(j * SEGMENT_LENGTH));
                }
                double maxReach = NUM_SEGMENTS * SEGMENT_LENGTH;
                if (root.distanceTo(idealRest) < maxReach) {
                    for (int i = 0; i < 20; i++) {
                        legs[leg] = FABRIKSolver.forwardPass(legs[leg], idealRest, SEGMENT_LENGTH);
                        legs[leg] = FABRIKSolver.backwardPass(legs[leg], root, SEGMENT_LENGTH);
                        if (legs[leg][NUM_SEGMENTS].distanceTo(idealRest) < 0.01) break;
                    }
                }
            }
        }
    }

    private Vec3 restPosition(Vec3 center, double angle) {
        double footX = center.x + REST_RADIUS * Math.sin(angle);
        double footZ = center.z + REST_RADIUS * Math.cos(angle);
        int groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(footX), (int) Math.floor(footZ));
        return new Vec3(footX, groundY, footZ);
    }

    // --- Serialization ---

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int numLegs = tag.getInt("numLegs");
        if (numLegs == 0) return;

        legs = new Vec3[numLegs][];
        legTargets = new Vec3[numLegs];
        legStepping = new boolean[numLegs];

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
