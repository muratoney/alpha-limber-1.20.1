package com.murat;

import com.murat.alphalimber.entity.ModEntities;
import com.murat.alphalimber.model.ArmSegmentModel;
import com.murat.alphalimber.renderer.ArmEntityRenderer;
import com.murat.alphalimber.renderer.SpiderEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class LimberClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.ARM, ArmEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.SPIDER, SpiderEntityRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ArmEntityRenderer.ARM_SEGMENT_LAYER, ArmSegmentModel::getTexturedModelData);
	}
}