package com.murat.alphalimber.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities{
    public static final EntityType<ArmEntity> ARM =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    new ResourceLocation("limber", "arm"),
                    FabricEntityTypeBuilder.create(MobCategory.MISC, ArmEntity::new)
                            .dimensions(EntityDimensions.fixed(0.5f,0.5f))
                            .build()
            );

    public static final EntityType<SpiderEntity> SPIDER =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    new ResourceLocation("limber", "spider"),
                    FabricEntityTypeBuilder.create(MobCategory.MISC, SpiderEntity::new)
                            .dimensions(EntityDimensions.fixed(1.5f, 2.5f))
                            .build()
            );

    public static void register() {

    }
}
