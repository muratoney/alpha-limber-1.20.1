package com.murat.alphalimber.entity;

import com.murat.alphalimber.solver.BlockConstraintHandler;
import com.murat.alphalimber.solver.FABRIKSolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SpiderEntity extends Entity {

    // --- Constants ---

    private static final double SEGMENT_LENGTH = 1.0;
    private static final int NUM_SEGMENTS = 3;
    private static final int NUM_LEGS = 4;
    private static final double WALK_SPEED = 4.0 / 20.0;         // max speed (blocks/tick)
    private static final double WALK_ACCELERATION = WALK_SPEED / 7.0; // accel per tick
    private static final double ROOT_Y_OFFSET = 1.3;              // root above body center
    private static final double BODY_HEIGHT_CORRECTION = 0.08;    // body height lerp speed
    private static final double REST_RADIUS = 1.5;                // horizontal foot distance
    private static final double STEP_THRESHOLD_MOVING = 0.9;      // dist before foot steps while moving
    private static final double STEP_THRESHOLD_STATIONARY = 0.25; // dist before foot steps while stationary
    private static final double STRAIGHTEN_HEIGHT = 1.25;         // upward bias before FABRIK
    private static final double SCAN_ABOVE = 2.0;                 // raycast starts this far above body
    private static final double SCAN_BELOW = 2.0;                 // raycast ends this far below body

    // --- Synched Data ---

    private static final EntityDataAccessor<CompoundTag> LEG_DATA =
            SynchedEntityData.defineId(SpiderEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // --- State ---

    private Vec3[][] legs;
    private Vec3[][] previousLegs;
    private Vec3[] legTargets;
    private Vec3[] idealRestPositions;
    private Vec3[] clientLegTargets;
    private Vec3[] clientIdealRests;
    private boolean clientAtRest = true;
    private boolean[] legStepping;
    private Vec3 bodyTarget;
    private Vec3 velocity = Vec3.ZERO;

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
        idealRestPositions = new Vec3[NUM_LEGS];
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

        // Snap entity Y to correct height immediately so updateBodyHeight has nothing to correct
        double avgLegY = 0;
        for (Vec3 t : legTargets) avgLegY += t.y;
        avgLegY /= NUM_LEGS;
        setPos(center.x, avgLegY, center.z);
        Vec3 snappedRoot = new Vec3(center.x, avgLegY + ROOT_Y_OFFSET, center.z);
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            legs[leg][0] = snappedRoot;
        }

        previousLegs = copyLegs(legs);
        syncLegs();
    }

    // --- Network Sync ---

    public void syncLegs() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("atRest", bodyTarget == null);
        tag.putInt("numLegs", legs.length);
        for (int leg = 0; leg < legs.length; leg++) {
            tag.putInt("leg_" + leg + "_count", legs[leg].length);
            for (int j = 0; j < legs[leg].length; j++) {
                tag.putDouble("l" + leg + "j" + j + "x", legs[leg][j].x);
                tag.putDouble("l" + leg + "j" + j + "y", legs[leg][j].y);
                tag.putDouble("l" + leg + "j" + j + "z", legs[leg][j].z);
            }
            tag.putDouble("t" + leg + "x", legTargets[leg].x);
            tag.putDouble("t" + leg + "y", legTargets[leg].y);
            tag.putDouble("t" + leg + "z", legTargets[leg].z);
            if (idealRestPositions[leg] != null) {
                tag.putDouble("r" + leg + "x", idealRestPositions[leg].x);
                tag.putDouble("r" + leg + "y", idealRestPositions[leg].y);
                tag.putDouble("r" + leg + "z", idealRestPositions[leg].z);
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
            updateBodyHeight();
            checkAndStepLegs();
            serverStep();
            return;
        }

        CompoundTag tag = this.entityData.get(LEG_DATA);
        if (!tag.contains("numLegs")) return;

        int numLegs = tag.getInt("numLegs");
        Vec3[][] newLegs = new Vec3[numLegs][];
        Vec3[] newTargets = new Vec3[numLegs];
        Vec3[] newRests = new Vec3[numLegs];
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
            newTargets[leg] = new Vec3(
                    tag.getDouble("t" + leg + "x"),
                    tag.getDouble("t" + leg + "y"),
                    tag.getDouble("t" + leg + "z")
            );
            if (tag.contains("r" + leg + "x")) {
                newRests[leg] = new Vec3(
                        tag.getDouble("r" + leg + "x"),
                        tag.getDouble("r" + leg + "y"),
                        tag.getDouble("r" + leg + "z")
                );
            }
        }

        previousLegs = legs;
        legs = newLegs;
        clientLegTargets = newTargets;
        clientIdealRests = newRests;
        clientAtRest = tag.getBoolean("atRest");
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

        // Always run FABRIK — when out of reach it naturally extends as far as possible
        for (int i = 0; i < 10; i++) {
            legs[leg] = FABRIKSolver.forwardPass(legs[leg], target, SEGMENT_LENGTH);
            legs[leg] = FABRIKSolver.backwardPass(legs[leg], root, SEGMENT_LENGTH);
            if (legs[leg][NUM_SEGMENTS].distanceTo(target) < 0.01) break;
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
        Vec3 toTarget = new Vec3(bodyTarget.x - pos.x, 0, bodyTarget.z - pos.z);
        double dist = toTarget.length();

        if (dist < 0.4) {
            bodyTarget = null;
            velocity = Vec3.ZERO;
            return;
        }

        // Decelerate when within stopping distance
        double decelerateDist = (WALK_SPEED * WALK_SPEED) / (2.0 * WALK_ACCELERATION);
        Vec3 targetVel = dist > decelerateDist
                ? toTarget.normalize().scale(WALK_SPEED)
                : Vec3.ZERO;

        // Accelerate toward target velocity by fixed step
        velocity = lerpVec(velocity, targetVel, WALK_ACCELERATION);

        Vec3 newPos = pos.add(velocity);
        setPos(newPos.x, newPos.y, newPos.z);
        updateLegRoots(position());
    }

    private void updateBodyHeight() {
        if (legTargets == null) return;
        // Only average grounded legs — stepping legs have a new target that hasn't landed yet
        double avgLegY = 0;
        int groundedCount = 0;
        for (int i = 0; i < NUM_LEGS; i++) {
            if (!legStepping[i]) {
                avgLegY += legTargets[i].y;
                groundedCount++;
            }
        }
        if (groundedCount == 0) return;
        avgLegY /= groundedCount;
        double targetY = avgLegY; // entity Y sits at ground level; roots are ROOT_Y_OFFSET above
        double newY = getY() + (targetY - getY()) * BODY_HEIGHT_CORRECTION;
        setPos(getX(), newY, getZ());
        updateLegRoots(position());
    }

    private void updateLegRoots(Vec3 center) {
        Vec3 sharedRoot = new Vec3(center.x, center.y + ROOT_Y_OFFSET, center.z);
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            legs[leg][0] = sharedRoot;
        }
    }

    private static Vec3 lerpVec(Vec3 current, Vec3 target, double step) {
        Vec3 diff = target.subtract(current);
        double len = diff.length();
        return len <= step ? target : current.add(diff.normalize().scale(step));
    }

    // --- Stepping ---

    private void checkAndStepLegs() {
        Vec3 center = position();
        double threshold = bodyTarget == null ? STEP_THRESHOLD_STATIONARY : STEP_THRESHOLD_MOVING;
        for (int leg = 0; leg < NUM_LEGS; leg++) {
            double angle = (2 * Math.PI * leg) / NUM_LEGS;
            Vec3 idealRest = restPosition(center, angle);
            idealRestPositions[leg] = idealRest;
            int prev = (leg + NUM_LEGS - 1) % NUM_LEGS;
            int next = (leg + 1) % NUM_LEGS;
            boolean adjacentStepping = legStepping[prev] || legStepping[next];
            boolean outsideSquare = Math.abs(legTargets[leg].x - idealRest.x) > threshold
                    || Math.abs(legTargets[leg].z - idealRest.z) > threshold;
            if (!adjacentStepping && outsideSquare) {
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
        Vec3 ground = scanGround(footX, footZ, center.y);
        if (ground != null) return new Vec3(footX, ground.y, footZ);
        // Fallback to heightmap if raycast misses
        int groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(footX), (int) Math.floor(footZ));
        return new Vec3(footX, groundY, footZ);
    }

    private Vec3 scanGround(double x, double z, double preferredY) {
        // Use the direct hit first — it correctly finds raised blocks
        Vec3 main = rayCastGround(x, z);
        if (main != null) return main;

        // Fallback: 3×3 grid for when the foot lands on a block edge or gap
        double margin = 2.0 / 16.0;
        double nx = Math.floor(x) - margin;
        double px = Math.ceil(x) + margin;
        double nz = Math.floor(z) - margin;
        double pz = Math.ceil(z) + margin;

        Vec3[] candidates = {
            rayCastGround(nx, nz), rayCastGround(nx, z),  rayCastGround(nx, pz),
            rayCastGround(x,  nz),                        rayCastGround(x,  pz),
            rayCastGround(px, nz), rayCastGround(px, z),  rayCastGround(px, pz),
        };

        Vec3 preferred = new Vec3(x, preferredY, z);
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (Vec3 c : candidates) {
            if (c == null) continue;
            double d = c.distanceToSqr(preferred);
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private Vec3 rayCastGround(double x, double z) {
        Vec3 from = new Vec3(x, getY() + SCAN_ABOVE, z);
        Vec3 to   = new Vec3(x, getY() - SCAN_BELOW, z);
        BlockHitResult hit = level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return null;
        return hit.getLocation();
    }

    // --- Serialization ---

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int numLegs = tag.getInt("numLegs");
        if (numLegs == 0) return;

        legs = new Vec3[numLegs][];
        legTargets = new Vec3[numLegs];
        idealRestPositions = new Vec3[numLegs];
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
    public Vec3[] getClientLegTargets() { return clientLegTargets; }
    public Vec3[] getClientIdealRests() { return clientIdealRests; }
    public double getSegmentLength() { return SEGMENT_LENGTH; }
    public int getNumSegments() { return NUM_SEGMENTS; }
    public double getRestRadius() { return REST_RADIUS; }
    public double getStepThresholdMoving() { return STEP_THRESHOLD_MOVING; }
    public double getStepThresholdStationary() { return STEP_THRESHOLD_STATIONARY; }
    public boolean isClientAtRest() { return clientAtRest; }

    /** Previous-tick positions for interpolation in the renderer. */
    public double getPrevX() { return xo; }
    public double getPrevY() { return yo; }
    public double getPrevZ() { return zo; }
}
