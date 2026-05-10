package dev.joyel.musify;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Musify implements ModInitializer {
   public static final String MOD_ID = "musify";
   public static final String MOD_NAME = "Musify";
   public static final Logger LOGGER = LoggerFactory.getLogger("musify");

   public void onInitialize() {
      LOGGER.info("{} initialized successfully!", "Musify");
   }
}
