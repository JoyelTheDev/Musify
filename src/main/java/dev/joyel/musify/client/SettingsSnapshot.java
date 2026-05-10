package dev.joyel.musify.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SettingsSnapshot {
   public final HUDSettings.HUDPosition position;
   public final boolean lyricsEnabled;
   public final int lyricsMaxLines;
   public final float hudOpacity;
   public final float hudScale;
   public final boolean showAlbumArt;
   public final boolean showProgressBar;
   public final boolean compactMode;
   public final boolean autoHideInMenus;
   public final int accentColorIndex;
   public final boolean textShadow;
   public final boolean showArtist;
   public final boolean showTimeStamps;
   public final float animationSpeed;
   public final boolean smoothProgress;
   public final HUDSettings.ProgressBarStyle progressBarStyle;
   public final float fontSize;
   public final float albumArtSize;
   public final HUDSettings.AlbumArtStyle albumArtStyle;
   public final int glowColor;
   public final float lyricsFontSize;
   public final float lyricsBackgroundOpacity;
   public final boolean lyricsKaraokeMode;
   public final HUDSettings.LyricsPosition lyricsPosition;
   public final int customAccentColor;
   public final boolean usingCustomAccentColor;

   public SettingsSnapshot(HUDSettings settings) {
      this.position = settings.getPosition();
      this.lyricsEnabled = settings.isLyricsEnabled();
      this.lyricsMaxLines = settings.getLyricsMaxLines();
      this.hudOpacity = settings.getHudOpacity();
      this.hudScale = settings.getHudScale();
      this.showAlbumArt = settings.isShowAlbumArt();
      this.showProgressBar = settings.isShowProgressBar();
      this.compactMode = settings.isCompactMode();
      this.autoHideInMenus = settings.isAutoHideInMenus();
      this.accentColorIndex = settings.getAccentColorIndex();
      this.textShadow = settings.isTextShadow();
      this.showArtist = settings.isShowArtist();
      this.showTimeStamps = settings.isShowTimeStamps();
      this.animationSpeed = settings.getAnimationSpeed();
      this.smoothProgress = settings.isSmoothProgress();
      this.progressBarStyle = settings.getProgressBarStyle();
      this.fontSize = settings.getFontSize();
      this.albumArtSize = settings.getAlbumArtSize();
      this.albumArtStyle = settings.getAlbumArtStyle();
      this.glowColor = settings.getGlowColor();
      this.lyricsFontSize = settings.getLyricsFontSize();
      this.lyricsBackgroundOpacity = settings.getLyricsBackgroundOpacity();
      this.lyricsKaraokeMode = settings.isLyricsKaraokeMode();
      this.lyricsPosition = settings.getLyricsPosition();
      this.customAccentColor = settings.getCustomAccentColor();
      this.usingCustomAccentColor = settings.isUsingCustomAccentColor();
   }

   public void applyTo(HUDSettings settings) {
      settings.setPosition(this.position);
      settings.setLyricsEnabled(this.lyricsEnabled);
      settings.setLyricsMaxLines(this.lyricsMaxLines);
      settings.setHudOpacity(this.hudOpacity);
      settings.setHudScale(this.hudScale);
      settings.setShowAlbumArt(this.showAlbumArt);
      settings.setShowProgressBar(this.showProgressBar);
      settings.setCompactMode(this.compactMode);
      settings.setAutoHideInMenus(this.autoHideInMenus);
      settings.setAccentColorIndex(this.accentColorIndex);
      settings.setTextShadow(this.textShadow);
      settings.setShowArtist(this.showArtist);
      settings.setShowTimeStamps(this.showTimeStamps);
      settings.setAnimationSpeed(this.animationSpeed);
      settings.setSmoothProgress(this.smoothProgress);
      settings.setProgressBarStyle(this.progressBarStyle);
      settings.setFontSize(this.fontSize);
      settings.setAlbumArtSize(this.albumArtSize);
      settings.setAlbumArtStyle(this.albumArtStyle);
      settings.setGlowColor(this.glowColor);
      settings.setLyricsFontSize(this.lyricsFontSize);
      settings.setLyricsBackgroundOpacity(this.lyricsBackgroundOpacity);
      settings.setLyricsKaraokeMode(this.lyricsKaraokeMode);
      settings.setLyricsPosition(this.lyricsPosition);
      if (this.usingCustomAccentColor) {
         settings.setCustomAccentColor(this.customAccentColor);
      } else {
         settings.setAccentColorIndex(this.accentColorIndex);
      }

   }
}
