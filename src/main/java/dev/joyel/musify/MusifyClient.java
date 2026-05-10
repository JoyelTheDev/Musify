package dev.joyel.musify;

import dev.joyel.musify.client.CustomizationScreen;
import dev.joyel.musify.client.ExecutorManager;
import dev.joyel.musify.client.HUDRenderer;
import dev.joyel.musify.client.Keybinds;
import dev.joyel.musify.client.LyricsManager;
import dev.joyel.musify.client.SpotifyApiClient;
import dev.joyel.musify.client.util.AlbumArtCache;
import dev.joyel.musify.client.util.ErrorHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

@Environment(EnvType.CLIENT)
public class MusifyClient implements ClientModInitializer {
   public void onInitializeClient() {
      Keybinds.register();
      HudRenderCallback.EVENT.register((HudRenderCallback)(drawContext, tickDelta) -> HUDRenderer.onHudRender(drawContext, tickDelta));
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         if (Keybinds.isOpenCustomizationPressed()) {
            CustomizationScreen.open();
         }

         if (Keybinds.isPlayPausePressed()) {
            SpotifyApiClient.getInstance().playPauseAsync();
         }

         if (Keybinds.isNextTrackPressed()) {
            SpotifyApiClient.getInstance().nextTrackAsync();
         }

         if (Keybinds.isPrevTrackPressed()) {
            SpotifyApiClient.getInstance().previousTrackAsync();
         }

      });
      SpotifyApiClient.getInstance().setStatusMessageListener((message) -> HUDRenderer.setStatusMessage(message));
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(client) -> {
         Musify.LOGGER.info("Musify shutting down...");
         SpotifyApiClient.getInstance().shutdown();
         LyricsManager.getInstance().shutdown();
         AlbumArtCache.getInstance().clearAll(false);
         HUDRenderer.clearAlbumArtCache();
         ExecutorManager.getInstance().shutdown();
         ErrorHandler.getInstance().reset();
         Musify.LOGGER.info("Musify shutdown complete");
      });
      SpotifyApiClient.getInstance().initialize();
      Musify.LOGGER.info("Musify client initialized!");
   }
}
