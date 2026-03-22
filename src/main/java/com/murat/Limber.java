package com.murat;

import com.murat.alphalimber.entity.ModEntities;
import com.murat.alphalimber.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Limber implements ModInitializer {
	public static final String MOD_ID = "limber";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();

	}
}