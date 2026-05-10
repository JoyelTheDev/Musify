package dev.joyel.musify.client;

import dev.joyel.musify.Musify;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public class ConfigManager {
   private static final Gson GSON = new Gson();
   private static final String FILE_NAME = "musify.json";
   private static ConfigManager INSTANCE;
   private final Path path = FabricLoader.getInstance().getConfigDir().resolve("musify.json");
   private String accessToken;
   private String refreshToken;
   private long expiresAt;
   private String hudPosition = "TOP_LEFT";
   private boolean lyricsEnabled = true;
   private int lyricsMaxLines = 2;
   private float hudOpacity = 0.95F;
   private float hudScale = 1.0F;
   private boolean showAlbumArt = true;
   private boolean showProgressBar = true;
   private boolean compactMode = false;
   private boolean autoHideInMenus = true;
   private int accentColorIndex = 0;
   private int customAccentColor = -14829228;
   private boolean usingCustomAccentColor = false;
   private String progressBarStyle = "NORMAL";
   private float fontSize = 1.0F;
   private float albumArtSize = 1.0F;
   private String albumArtStyle = "SQUARE";
   private boolean textShadow = true;
   private boolean showArtist = true;
   private boolean showTimeStamps = true;
   private float animationSpeed = 1.0F;
   private boolean smoothProgress = true;
   private int glowColor = -14829228;
   private float lyricsFontSize = 1.0F;
   private float lyricsBackgroundOpacity = 0.9F;
   private boolean lyricsKaraokeMode = false;
   private String lyricsPosition = "BELOW_HUD";
   private String spotifyClientId = "";
   private String spotifyClientSecret = "";

   public static synchronized ConfigManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ConfigManager();
      }

      return INSTANCE;
   }

   private ConfigManager() {
   }

   public synchronized void load() {
      try {
         if (!Files.exists(this.path, new LinkOption[0])) {
            return;
         }

         String raw = Files.readString(this.path);
         JsonObject json = (JsonObject)GSON.fromJson(raw, JsonObject.class);
         if (json.has("access_token")) {
            this.accessToken = json.get("access_token").getAsString();
         }

         if (json.has("refresh_token")) {
            this.refreshToken = json.get("refresh_token").getAsString();
         }

         if (json.has("expires_at")) {
            this.expiresAt = json.get("expires_at").getAsLong();
         }

         if (json.has("hud_position")) {
            this.hudPosition = json.get("hud_position").getAsString();
         }

         if (json.has("lyrics_enabled")) {
            this.lyricsEnabled = json.get("lyrics_enabled").getAsBoolean();
         }

         if (json.has("lyrics_max_lines")) {
            this.lyricsMaxLines = json.get("lyrics_max_lines").getAsInt();
         }

         if (json.has("hud_opacity")) {
            this.hudOpacity = json.get("hud_opacity").getAsFloat();
         }

         if (json.has("hud_scale")) {
            this.hudScale = json.get("hud_scale").getAsFloat();
         }

         if (json.has("show_album_art")) {
            this.showAlbumArt = json.get("show_album_art").getAsBoolean();
         }

         if (json.has("show_progress_bar")) {
            this.showProgressBar = json.get("show_progress_bar").getAsBoolean();
         }

         if (json.has("compact_mode")) {
            this.compactMode = json.get("compact_mode").getAsBoolean();
         }

         if (json.has("auto_hide_in_menus")) {
            this.autoHideInMenus = json.get("auto_hide_in_menus").getAsBoolean();
         }

         if (json.has("accent_color_index")) {
            this.accentColorIndex = json.get("accent_color_index").getAsInt();
         }

         if (json.has("custom_accent_color")) {
            this.customAccentColor = json.get("custom_accent_color").getAsInt();
         }

         if (json.has("using_custom_accent_color")) {
            this.usingCustomAccentColor = json.get("using_custom_accent_color").getAsBoolean();
         }

         if (json.has("progress_bar_style")) {
            this.progressBarStyle = json.get("progress_bar_style").getAsString();
         }

         if (json.has("font_size")) {
            this.fontSize = json.get("font_size").getAsFloat();
         }

         if (json.has("album_art_size")) {
            this.albumArtSize = json.get("album_art_size").getAsFloat();
         }

         if (json.has("album_art_style")) {
            this.albumArtStyle = json.get("album_art_style").getAsString();
         }

         if (json.has("text_shadow")) {
            this.textShadow = json.get("text_shadow").getAsBoolean();
         }

         if (json.has("show_artist")) {
            this.showArtist = json.get("show_artist").getAsBoolean();
         }

         if (json.has("show_timestamps")) {
            this.showTimeStamps = json.get("show_timestamps").getAsBoolean();
         }

         if (json.has("animation_speed")) {
            this.animationSpeed = json.get("animation_speed").getAsFloat();
         }

         if (json.has("smooth_progress")) {
            this.smoothProgress = json.get("smooth_progress").getAsBoolean();
         }

         if (json.has("glow_color")) {
            this.glowColor = json.get("glow_color").getAsInt();
         }

         if (json.has("lyrics_font_size")) {
            this.lyricsFontSize = json.get("lyrics_font_size").getAsFloat();
         }

         if (json.has("lyrics_background_opacity")) {
            this.lyricsBackgroundOpacity = json.get("lyrics_background_opacity").getAsFloat();
         }

         if (json.has("lyrics_karaoke_mode")) {
            this.lyricsKaraokeMode = json.get("lyrics_karaoke_mode").getAsBoolean();
         }

         if (json.has("lyrics_position")) {
            this.lyricsPosition = json.get("lyrics_position").getAsString();
         }

         if (json.has("spotify_client_id")) {
            this.spotifyClientId = json.get("spotify_client_id").getAsString();
         }

         if (json.has("spotify_client_secret")) {
            this.spotifyClientSecret = json.get("spotify_client_secret").getAsString();
         }

         this.applyLoadedSettings();
      } catch (IOException e) {
         Musify.LOGGER.warn("Failed to load config", e);
      }

   }

   private void applyLoadedSettings() {
      HUDSettings settings = HUDSettings.getInstance();

      try {
         settings.setPosition(HUDSettings.HUDPosition.valueOf(this.hudPosition));
      } catch (IllegalArgumentException var6) {
         settings.setPosition(HUDSettings.HUDPosition.TOP_LEFT);
      }

      settings.setLyricsEnabled(this.lyricsEnabled);
      settings.setLyricsMaxLines(this.lyricsMaxLines);
      settings.setHudOpacity(this.hudOpacity);
      settings.setHudScale(this.hudScale);
      settings.setShowAlbumArt(this.showAlbumArt);
      settings.setShowProgressBar(this.showProgressBar);
      settings.setCompactMode(this.compactMode);
      settings.setAutoHideInMenus(this.autoHideInMenus);
      if (this.usingCustomAccentColor) {
         settings.setCustomAccentColor(this.customAccentColor);
      } else {
         settings.setAccentColorIndex(this.accentColorIndex);
      }

      try {
         settings.setProgressBarStyle(HUDSettings.ProgressBarStyle.valueOf(this.progressBarStyle));
      } catch (IllegalArgumentException var5) {
         settings.setProgressBarStyle(HUDSettings.ProgressBarStyle.NORMAL);
      }

      settings.setFontSize(this.fontSize);
      settings.setAlbumArtSize(this.albumArtSize);

      try {
         settings.setAlbumArtStyle(HUDSettings.AlbumArtStyle.valueOf(this.albumArtStyle));
      } catch (IllegalArgumentException var4) {
         settings.setAlbumArtStyle(HUDSettings.AlbumArtStyle.SQUARE);
      }

      settings.setTextShadow(this.textShadow);
      settings.setShowArtist(this.showArtist);
      settings.setShowTimeStamps(this.showTimeStamps);
      settings.setAnimationSpeed(this.animationSpeed);
      settings.setSmoothProgress(this.smoothProgress);
      settings.setGlowColor(this.glowColor);
      settings.setLyricsFontSize(this.lyricsFontSize);
      settings.setLyricsBackgroundOpacity(this.lyricsBackgroundOpacity);
      settings.setLyricsKaraokeMode(this.lyricsKaraokeMode);

      try {
         settings.setLyricsPosition(HUDSettings.LyricsPosition.valueOf(this.lyricsPosition));
      } catch (IllegalArgumentException var3) {
         settings.setLyricsPosition(HUDSettings.LyricsPosition.BELOW_HUD);
      }

   }

   public synchronized void saveTokens(String accessToken, String refreshToken, int expiresInSeconds) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.expiresAt = Instant.now().getEpochSecond() + (long)expiresInSeconds;
      this.save();
   }

   public synchronized void saveHUDSettings(HUDSettings settings) {
      this.hudPosition = settings.getPosition().name();
      this.lyricsEnabled = settings.isLyricsEnabled();
      this.lyricsMaxLines = settings.getLyricsMaxLines();
      this.hudOpacity = settings.getHudOpacity();
      this.hudScale = settings.getHudScale();
      this.showAlbumArt = settings.isShowAlbumArt();
      this.showProgressBar = settings.isShowProgressBar();
      this.compactMode = settings.isCompactMode();
      this.autoHideInMenus = settings.isAutoHideInMenus();
      this.accentColorIndex = settings.getAccentColorIndex();
      this.customAccentColor = settings.getCustomAccentColor();
      this.usingCustomAccentColor = settings.isUsingCustomAccentColor();
      this.progressBarStyle = settings.getProgressBarStyle().name();
      this.fontSize = settings.getFontSize();
      this.albumArtSize = settings.getAlbumArtSize();
      this.albumArtStyle = settings.getAlbumArtStyle().name();
      this.textShadow = settings.isTextShadow();
      this.showArtist = settings.isShowArtist();
      this.showTimeStamps = settings.isShowTimeStamps();
      this.animationSpeed = settings.getAnimationSpeed();
      this.smoothProgress = settings.isSmoothProgress();
      this.glowColor = settings.getGlowColor();
      this.lyricsFontSize = settings.getLyricsFontSize();
      this.lyricsBackgroundOpacity = settings.getLyricsBackgroundOpacity();
      this.lyricsKaraokeMode = settings.isLyricsKaraokeMode();
      this.lyricsPosition = settings.getLyricsPosition().name();
      this.save();
   }

   private synchronized void save() {
      try {
         JsonObject json = new JsonObject();
         if (this.accessToken != null) {
            json.addProperty("access_token", this.accessToken);
         }

         if (this.refreshToken != null) {
            json.addProperty("refresh_token", this.refreshToken);
         }

         json.addProperty("expires_at", this.expiresAt);
         json.addProperty("hud_position", this.hudPosition);
         json.addProperty("lyrics_enabled", this.lyricsEnabled);
         json.addProperty("lyrics_max_lines", this.lyricsMaxLines);
         json.addProperty("hud_opacity", this.hudOpacity);
         json.addProperty("hud_scale", this.hudScale);
         json.addProperty("show_album_art", this.showAlbumArt);
         json.addProperty("show_progress_bar", this.showProgressBar);
         json.addProperty("compact_mode", this.compactMode);
         json.addProperty("auto_hide_in_menus", this.autoHideInMenus);
         json.addProperty("accent_color_index", this.accentColorIndex);
         json.addProperty("custom_accent_color", this.customAccentColor);
         json.addProperty("using_custom_accent_color", this.usingCustomAccentColor);
         json.addProperty("progress_bar_style", this.progressBarStyle);
         json.addProperty("font_size", this.fontSize);
         json.addProperty("album_art_size", this.albumArtSize);
         json.addProperty("album_art_style", this.albumArtStyle);
         json.addProperty("text_shadow", this.textShadow);
         json.addProperty("show_artist", this.showArtist);
         json.addProperty("show_timestamps", this.showTimeStamps);
         json.addProperty("animation_speed", this.animationSpeed);
         json.addProperty("smooth_progress", this.smoothProgress);
         json.addProperty("glow_color", this.glowColor);
         json.addProperty("lyrics_font_size", this.lyricsFontSize);
         json.addProperty("lyrics_background_opacity", this.lyricsBackgroundOpacity);
         json.addProperty("lyrics_karaoke_mode", this.lyricsKaraokeMode);
         json.addProperty("lyrics_position", this.lyricsPosition);
         if (this.spotifyClientId != null && !this.spotifyClientId.isEmpty()) {
            json.addProperty("spotify_client_id", this.spotifyClientId);
         }

         if (this.spotifyClientSecret != null && !this.spotifyClientSecret.isEmpty()) {
            json.addProperty("spotify_client_secret", this.spotifyClientSecret);
         }

         Files.createDirectories(this.path.getParent());
         Files.writeString(this.path, GSON.toJson(json));
      } catch (IOException e) {
         Musify.LOGGER.warn("Failed to save config", e);
      }

   }

   public synchronized Optional<String> getAccessToken() {
      return Optional.ofNullable(this.accessToken);
   }

   public synchronized Optional<String> getRefreshToken() {
      return Optional.ofNullable(this.refreshToken);
   }

   public synchronized long getExpiresAt() {
      return this.expiresAt;
   }

   public synchronized void logout() {
      this.accessToken = null;
      this.refreshToken = null;
      this.expiresAt = 0L;
      this.save();
   }

   public synchronized boolean isLoggedIn() {
      if (this.accessToken != null && !this.accessToken.isEmpty()) {
         return this.expiresAt > Instant.now().getEpochSecond();
      } else {
         return false;
      }
   }

   public synchronized boolean isTokenExpired() {
      if (this.accessToken != null && !this.accessToken.isEmpty()) {
         return this.expiresAt <= Instant.now().getEpochSecond();
      } else {
         return false;
      }
   }

   public synchronized String getSpotifyClientId() {
      return this.spotifyClientId != null ? this.spotifyClientId : "";
   }

   public synchronized String getSpotifyClientSecret() {
      return this.spotifyClientSecret != null ? this.spotifyClientSecret : "";
   }

   public synchronized void setSpotifyCredentials(String clientId, String clientSecret) {
      this.spotifyClientId = clientId != null ? clientId : "";
      this.spotifyClientSecret = clientSecret != null ? clientSecret : "";
      this.save();
   }

   public synchronized boolean hasCustomSpotifyCredentials() {
      return this.spotifyClientId != null && !this.spotifyClientId.isEmpty() && this.spotifyClientSecret != null && !this.spotifyClientSecret.isEmpty();
   }
}
