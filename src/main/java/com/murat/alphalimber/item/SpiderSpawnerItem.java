package com.murat.alphalimber.item;

import com.murat.alphalimber.entity.ModEntities;
import com.murat.alphalimber.entity.SpiderEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SpiderSpawnerItem extends Item {
    public SpiderSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Vec3 spawnPos = context.getClickedPos().getCenter().add(0, 0.5, 0);

        if (!world.isClientSide()) {
            SpiderEntity spider = new SpiderEntity(ModEntities.SPIDER, world);
            spider.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            spider.initializeLegs(spawnPos);
            world.addFreshEntity(spider);
        }
        return InteractionResult.SUCCESS;
    }
}
