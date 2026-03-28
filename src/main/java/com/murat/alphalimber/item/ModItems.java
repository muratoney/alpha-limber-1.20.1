package com.murat.alphalimber.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Item ARM_SPAWNER = Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("limber", "arm_spawner"),
            new ArmSpawnerItem(new Item.Properties())
    );

    public static final Item TARGET_POINTER = Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("limber", "target_pointer"),
            new TargetPointerItem(new Item.Properties())
    );

    public static final Item STEP_CONTROLLER = Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("limber", "step_controller"),
            new StepControllerItem(new Item.Properties())
    );

    public static final Item SPIDER_SPAWNER = Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("limber", "spider_spawner"),
            new SpiderSpawnerItem(new Item.Properties())
    );

    public static final Item SPIDER_TARGET_POINTER = Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("limber", "spider_target_pointer"),
            new SpiderTargetPointerItem(new Item.Properties())
    );

    public static void register() {

    }
}
