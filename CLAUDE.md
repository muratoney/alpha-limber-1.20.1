# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Fabric mod for Minecraft 1.20.1 that implements procedural arm rigging using the FABRIK (Forward And Backward Reaching Inverse Kinematics) algorithm. The mod adds entities with procedurally-animated limbs that can reach toward targets while respecting block constraints.

## Build & Development Commands

### Build the mod
```bash
./gradlew build
```
The built JAR will be in `build/libs/`

### Run Minecraft client with the mod
```bash
./gradlew runClient
```

### Run Minecraft server with the mod
```bash
./gradlew runServer
```

### Generate sources for Minecraft classes (useful for development)
```bash
./gradlew genSources
```

### Clean build artifacts
```bash
./gradlew clean
```

## Project Structure

This mod uses Fabric's **split source sets** pattern:
- `src/main/java` - Common code (runs on both server and client)
- `src/client/java` - Client-only code (rendering, models, client initialization)
- `src/main/resources` - Mod metadata, assets, data
- `src/client/resources` - Client-only resources (client mixins)

### Key Packages

- **`com.murat`** - Root package containing mod initializers
  - `Limber.java` - Main server-side entrypoint (registers entities and items)
  - `LimberClient.java` - Client-side entrypoint (registers renderers)

- **`com.murat.alphalimber.entity`** - Entity definitions
  - `ArmEntity.java` - Core IK arm entity with joint system and network sync
  - `ModEntities.java` - Entity type registration
  - `ArmHelper.java` - Utility functions for arm entities

- **`com.murat.alphalimber.solver`** - FABRIK IK algorithm implementation
  - `FABRIKSolver.java` - Core IK solver (forward/backward passes)
  - `BlockConstraintHandler.java` - Applies world collision constraints to joint positions

- **`com.murat.alphalimber.item`** - Custom items
  - `ArmSpawnerItem.java` - Spawns arm entities
  - `TargetPointerItem.java` - Sets IK target positions
  - `StepControllerItem.java` - Manual stepping through IK algorithm
  - `ModItems.java` - Item registration

- **`com.murat.alphalimber.renderer`** (client-only) - Entity rendering
  - `ArmEntityRenderer.java` - Renders arm entity with segments

- **`com.murat.alphalimber.model`** (client-only) - Entity models
  - `ArmSegmentModel.java` - 3D model for arm segments

## Core Architecture

### FABRIK IK System

The mod implements a two-phase inverse kinematics solver:

1. **Forward Pass** (tip → root): Positions the end effector at the target, then works backward constraining each joint
2. **Backward Pass** (root → tip): Anchors the root position, then works forward constraining joints
3. **Constraint Application**: After each pass, `BlockConstraintHandler` ensures joints don't intersect blocks

### Entity Network Synchronization

`ArmEntity` uses Minecraft's `EntityData` system to sync state between server and client:
- Joint positions are serialized to `CompoundTag` and synced via `JOINT_DATA` data accessor
- Target position synced via separate `TARGET_X/Y/Z` accessors
- Client receives updates in `tick()` and stores previous positions for interpolation

### Step-by-Step Solving

The mod supports both automatic solving (`performStep()` runs full passes) and manual stepping (`stepOneJoint()` solves one joint at a time) for debugging and visualization.

## Important Implementation Notes

### Entity Registration Pattern
Entities must be registered in both:
1. `ModEntities` static fields (creates entity types)
2. `Limber.onInitialize()` for attributes (via `FabricDefaultAttributeRegistry`)
3. `LimberClient.onInitializeClient()` for renderers and model layers

### Client-Only Code
Always check execution side before running IK solver or accessing renderer code:
```java
if (!this.level().isClientSide()) return; // Server-side only
if (this.level().isClientSide()) return;  // Client-side only
```

### Resource Locations
All registrations use the namespace `"limber"`:
```java
new ResourceLocation("limber", "entity_name")
```

### Mixins
The mod declares mixin configs in `fabric.mod.json` but currently has no mixin implementations. Mixin files exist but are empty placeholders.

## Dependencies

- **Minecraft**: 1.20.1
- **Fabric Loader**: 0.18.4+
- **Fabric API**: 0.92.7+1.20.1
- **Java**: 17+

Check `gradle.properties` for version management.

## Mod Metadata

Defined in `src/main/resources/fabric.mod.json`:
- Mod ID: `limber`
- Main entrypoint: `com.murat.Limber`
- Client entrypoint: `com.murat.LimberClient`
- License: CC0-1.0
