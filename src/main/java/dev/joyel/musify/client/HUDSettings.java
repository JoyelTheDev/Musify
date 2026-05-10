package dev.joyel.musify.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class HUDSettings {
   private static HUDSettings INSTANCE;
   public static final int[] ACCENT_COLORS = new int[]{0xFF1E3A8A, 0xFF1DB954, 0xFF3B82F6, 0xFFEF4444, 0xFFFBBF24, 0xFF8B5CF6, 0xFFEC4899, 0xFF06B6D4, 0xFFF97316, 0xFFFFFFFF};
   public static final String[] ACCENT_COLOR_NAMES = new String[]{"Album", "Spotify Green", "Blue", "Red", "Yellow", "Purple", "Pink", "Turquoise", "Orange", "White"};
   private int customAccentColor = 0xFF1E3A8A;
   private boolean usingCustomAccentColor = false;
   private Integer previewColor = null;
   private HUDPosition position;
   private boolean lyricsEnabled;
   private int lyricsMaxLines;
   private float hudOpacity;
   private float hudScale;
   private boolean showAlbumArt;
   private boolean showProgressBar;
   private boolean compactMode;
   private boolean autoHideInMenus;
   private int accentColorIndex;
   private boolean textShadow;
   private boolean showArtist;
   private boolean showTimeStamps;
   private float animationSpeed;
   private boolean smoothProgress;
   private ProgressBarStyle progressBarStyle;
   private float fontSize;
   private float albumArtSize;
   private AlbumArtStyle albumArtStyle;
   private int glowColor;
   private float lyricsFontSize;
   private float lyricsBackgroundOpacity;
   private boolean lyricsKaraokeMode;
   private LyricsPosition lyricsPosition;
   private int albumDynamicColor;

   public static synchronized HUDSettings getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new HUDSettings();
      }
      return INSTANCE;
   }

   private HUDSettings() {
      this.position = HUDPosition.TOP_LEFT;
      this.lyricsEnabled = true;
      this.lyricsMaxLines = 2;
      this.hudOpacity = 0.95F;
      this.hudScale = 1.0F;
      this.showAlbumArt = true;
      this.showProgressBar = true;
      this.compactMode = false;
      this.autoHideInMenus = true;
      this.accentColorIndex = 0;
      this.textShadow = true;
      this.showArtist = true;
      this.showTimeStamps = true;
      this.animationSpeed = 1.0F;
      this.smoothProgress = true;
      this.progressBarStyle = ProgressBarStyle.NORMAL;
      this.fontSize = 1.0F;
      this.albumArtSize = 1.0F;
      this.albumArtStyle = AlbumArtStyle.SQUARE;
      this.glowColor = 0xFF1E3A8A;
      this.lyricsFontSize = 1.0F;
      this.lyricsBackgroundOpacity = 0.9F;
      this.lyricsKaraokeMode = false;
      this.lyricsPosition = LyricsPosition.BELOW_HUD;
      this.albumDynamicColor = 0xFF1E3A8A;
   }

   public HUDPosition getPosition() {
      return this.position;
   }

   public void setPosition(HUDPosition position) {
      this.position = position;
   }

   public void cyclePosition() {
      HUDPosition[] values = HUDPosition.values();
      this.position = values[(this.position.ordinal() + 1) % values.length];
   }

   public boolean isLyricsEnabled() {
      return this.lyricsEnabled;
   }

   public void setLyricsEnabled(boolean lyricsEnabled) {
      this.lyricsEnabled = lyricsEnabled;
   }

   public void toggleLyrics() {
      this.lyricsEnabled = !this.lyricsEnabled;
   }

   public int getLyricsMaxLines() {
      return this.lyricsMaxLines;
   }

   public void setLyricsMaxLines(int lyricsMaxLines) {
      this.lyricsMaxLines = Math.max(1, Math.min(4, lyricsMaxLines));
   }

   public float getHudOpacity() {
      return this.hudOpacity;
   }

   public void setHudOpacity(float hudOpacity) {
      this.hudOpacity = Math.max(0.3F, Math.min(1.0F, hudOpacity));
   }

   public float getHudScale() {
      return this.hudScale;
   }

   public void setHudScale(float hudScale) {
      this.hudScale = Math.max(0.5F, Math.min(2.0F, hudScale));
   }

   public boolean isShowAlbumArt() {
      return this.showAlbumArt;
   }

   public void setShowAlbumArt(boolean showAlbumArt) {
      this.showAlbumArt = showAlbumArt;
   }

   public void toggleAlbumArt() {
      this.showAlbumArt = !this.showAlbumArt;
   }

   public boolean isShowProgressBar() {
      return this.showProgressBar;
   }

   public void setShowProgressBar(boolean showProgressBar) {
      this.showProgressBar = showProgressBar;
   }

   public void toggleProgressBar() {
      this.showProgressBar = !this.showProgressBar;
   }

   public boolean isCompactMode() {
      return this.compactMode;
   }

   public void setCompactMode(boolean compactMode) {
      this.compactMode = compactMode;
   }

   public void toggleCompactMode() {
      this.compactMode = !this.compactMode;
   }

   public boolean isAutoHideInMenus() {
      return this.autoHideInMenus;
   }

   public void setAutoHideInMenus(boolean autoHide) {
      this.autoHideInMenus = autoHide;
   }

   public void toggleAutoHideInMenus() {
      this.autoHideInMenus = !this.autoHideInMenus;
   }

   public boolean isTextShadow() {
      return this.textShadow;
   }

   public void setTextShadow(boolean textShadow) {
      this.textShadow = textShadow;
   }

   public void toggleTextShadow() {
      this.textShadow = !this.textShadow;
   }

   public boolean isShowArtist() {
      return this.showArtist;
   }

   public void setShowArtist(boolean showArtist) {
      this.showArtist = showArtist;
   }

   public void toggleShowArtist() {
      this.showArtist = !this.showArtist;
   }

   public boolean isShowTimeStamps() {
      return this.showTimeStamps;
   }

   public void setShowTimeStamps(boolean showTimeStamps) {
      this.showTimeStamps = showTimeStamps;
   }

   public void toggleShowTimeStamps() {
      this.showTimeStamps = !this.showTimeStamps;
   }

   public float getAnimationSpeed() {
      return this.animationSpeed;
   }

   public void setAnimationSpeed(float speed) {
      this.animationSpeed = Math.max(0.5F, Math.min(2.0F, speed));
   }

   public boolean isSmoothProgress() {
      return this.smoothProgress;
   }

   public void setSmoothProgress(boolean smoothProgress) {
      this.smoothProgress = smoothProgress;
   }

   public void toggleSmoothProgress() {
      this.smoothProgress = !this.smoothProgress;
   }

   public ProgressBarStyle getProgressBarStyle() {
      return this.progressBarStyle;
   }

   public void setProgressBarStyle(ProgressBarStyle style) {
      this.progressBarStyle = style;
   }

   public void cycleProgressBarStyle() {
      ProgressBarStyle[] values = ProgressBarStyle.values();
      this.progressBarStyle = values[(this.progressBarStyle.ordinal() + 1) % values.length];
   }

   public float getFontSize() {
      return this.fontSize;
   }

   public void setFontSize(float fontSize) {
      this.fontSize = Math.max(0.7F, Math.min(1.5F, fontSize));
   }

   public float getAlbumArtSize() {
      return this.albumArtSize;
   }

   public void setAlbumArtSize(float albumArtSize) {
      this.albumArtSize = Math.max(0.5F, Math.min(1.5F, albumArtSize));
   }

   public AlbumArtStyle getAlbumArtStyle() {
      return this.albumArtStyle;
   }

   public void setAlbumArtStyle(AlbumArtStyle style) {
      this.albumArtStyle = style;
   }

   public void cycleAlbumArtStyle() {
      AlbumArtStyle[] values = AlbumArtStyle.values();
      this.albumArtStyle = values[(this.albumArtStyle.ordinal() + 1) % values.length];
   }

   public int getGlowColor() {
      return this.glowColor;
   }

   public void setGlowColor(int glowColor) {
      this.glowColor = glowColor;
   }

   public float getLyricsFontSize() {
      return this.lyricsFontSize;
   }

   public void setLyricsFontSize(float lyricsFontSize) {
      this.lyricsFontSize = Math.max(0.7F, Math.min(1.5F, lyricsFontSize));
   }

   public float getLyricsBackgroundOpacity() {
      return this.lyricsBackgroundOpacity;
   }

   public void setLyricsBackgroundOpacity(float opacity) {
      this.lyricsBackgroundOpacity = Math.max(0.3F, Math.min(1.0F, opacity));
   }

   public boolean isLyricsKaraokeMode() {
      return this.lyricsKaraokeMode;
   }

   public void setLyricsKaraokeMode(boolean karaokeMode) {
      this.lyricsKaraokeMode = karaokeMode;
   }

   public void toggleLyricsKaraokeMode() {
      this.lyricsKaraokeMode = !this.lyricsKaraokeMode;
   }

   public LyricsPosition getLyricsPosition() {
      return this.lyricsPosition;
   }

   public void setLyricsPosition(LyricsPosition position) {
      this.lyricsPosition = position;
   }

   public void cycleLyricsPosition() {
      LyricsPosition[] values = LyricsPosition.values();
      this.lyricsPosition = values[(this.lyricsPosition.ordinal() + 1) % values.length];
   }

   public int getAccentColorIndex() {
      return this.accentColorIndex;
   }

   public void setAccentColorIndex(int index) {
      this.accentColorIndex = Math.max(0, Math.min(ACCENT_COLORS.length - 1, index));
      this.usingCustomAccentColor = false;
   }

   public void cycleAccentColor() {
      this.accentColorIndex = (this.accentColorIndex + 1) % ACCENT_COLORS.length;
      this.usingCustomAccentColor = false;
   }

   public int getAccentColor() {
      if (this.previewColor != null) {
         return this.previewColor;
      } else if (this.usingCustomAccentColor) {
         return this.customAccentColor;
      } else {
         return this.accentColorIndex == 0 ? this.albumDynamicColor : ACCENT_COLORS[this.accentColorIndex];
      }
   }

   public void setPreviewColor(Integer color) {
      this.previewColor = color;
   }

   public void clearPreviewColor() {
      this.previewColor = null;
   }

   public String getAccentColorName() {
      return this.usingCustomAccentColor ? "Custom" : ACCENT_COLOR_NAMES[this.accentColorIndex];
   }

   public void setCustomAccentColor(int color) {
      this.customAccentColor = color;
      this.usingCustomAccentColor = true;
   }

   public int getCustomAccentColor() {
      return this.customAccentColor;
   }

   public boolean isUsingCustomAccentColor() {
      return this.usingCustomAccentColor;
   }

   public void setAlbumDynamicColor(int color) {
      this.albumDynamicColor = color;
   }

   public int getAlbumDynamicColor() {
      return this.albumDynamicColor;
   }

   public boolean isAlbumColorMode() {
      return this.accentColorIndex == 0;
   }

   public int getMarginX(int screenWidth, int hudWidth) {
      int margin = 8;
      switch (this.position) {
         case TOP_LEFT:
         case BOTTOM_LEFT:
            return margin;
         case TOP_RIGHT:
         case BOTTOM_RIGHT:
            return screenWidth - hudWidth - margin;
         default:
            return margin;
      }
   }

   public int getMarginY(int screenHeight, int hudHeight) {
      int margin = 8;
      switch (this.position) {
         case TOP_LEFT:
         case TOP_RIGHT:
            return margin;
         case BOTTOM_LEFT:
         case BOTTOM_RIGHT:
            return screenHeight - hudHeight - margin;
         default:
            return margin;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum HUDPosition {
      TOP_LEFT("Top Left"),
      TOP_RIGHT("Top Right"),
      BOTTOM_LEFT("Bottom Left"),
      BOTTOM_RIGHT("Bottom Right");

      private final String displayName;

      private HUDPosition(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public HUDPosition next() {
         HUDPosition[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum ProgressBarStyle {
      THIN("Thin"),
      NORMAL("Normal"),
      THICK("Thick"),
      ROUNDED("Rounded"),
      GRADIENT("Gradient");

      private final String displayName;

      private ProgressBarStyle(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public ProgressBarStyle next() {
         ProgressBarStyle[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum AlbumArtStyle {
      SQUARE("Square"),
      ROUNDED("Rounded"),
      CIRCULAR("Circular");

      private final String displayName;

      private AlbumArtStyle(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public AlbumArtStyle next() {
         AlbumArtStyle[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum LyricsPosition {
      BELOW_HUD("Below HUD"),
      ABOVE_HUD("Above HUD"),
      SEPARATE("Separate");

      private final String displayName;

      private LyricsPosition(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public LyricsPosition next() {
         LyricsPosition[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }
}