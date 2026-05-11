package dev.joyel.musify.client;

import dev.joyel.musify.Musify;
import dev.joyel.musify.client.util.AlbumArtCache;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;

@Environment(EnvType.CLIENT)
public class HUDRenderer {
   private static float slideOffset = 0.0F;
   private static float fadeAlpha = 0.0F;
   private static String lastTrackId = "";
   private static float trackChangeAnim = 1.0F;
   private static float animatedProgress = 0.0F;
   private static float lyricsSlideAnim = 0.0F;
   private static int lastLyricLineIndex = -1;
   private static final AlbumArtCache albumArtCache = AlbumArtCache.getInstance();
   private static long lastUpdateTime = 0L;
   private static long interpolatedProgressMs = 0L;
   private static String statusMessage = null;
   private static long statusMessageExpiry = 0L;
   private static final long STATUS_MESSAGE_DURATION_MS = 5000L;
   private static final int BASE_HUD_WIDTH = 300;
   private static final int BASE_HUD_HEIGHT = 66;
   private static final int BASE_COMPACT_HEIGHT = 46;
   private static final int BASE_MARGIN = 8;
   private static final int BASE_ICON_SIZE = 50;
   private static final int BASE_COMPACT_ICON_SIZE = 36;
   private static final int LYRICS_LINE_HEIGHT = 14;
   private static final int BG_MAIN = 0xF0F0F0F0;
   private static final int BG_ACCENT = 0xFF1A2A3A;
   private static final int BG_LYRICS = 0xCC000000;
   private static final int BORDER_TOP = 0x21000000;
   private static final int TEXT_WHITE = 0xFFFFFFFF;
   private static final int TEXT_GRAY = 0xFFA0A0A0;
   private static final int TEXT_DIM = 0xFF606060;
   private static final int TEXT_LYRIC_CURRENT = 0xFFFFFFFF;
   private static final int TEXT_LYRIC_NEXT = 0xFF808080;
   private static final int PROGRESS_BG = 0xFF2A2A2A;
   private static CachedLayout cachedLayout = null;
   private static int lastScreenWidth = 0;
   private static int lastScreenHeight = 0;
   private static long lastSettingsHash = 0L;

   private static long computeSettingsHash(HUDSettings settings) {
      long hash = 17L;
      hash = hash * 31L + (long)Float.floatToIntBits(settings.getHudScale());
      hash = hash * 31L + (long)(settings.isCompactMode() ? 1 : 0);
      hash = hash * 31L + (long)(settings.isShowAlbumArt() ? 1 : 0);
      hash = hash * 31L + (long)Float.floatToIntBits(settings.getAlbumArtSize());
      hash = hash * 31L + (long)settings.getPosition().ordinal();
      hash = hash * 31L + (long)(settings.isLyricsEnabled() ? 1 : 0);
      hash = hash * 31L + (long)settings.getLyricsMaxLines();
      hash = hash * 31L + (long)settings.getLyricsPosition().ordinal();
      hash = hash * 31L + (long)Float.floatToIntBits(settings.getLyricsFontSize());
      hash = hash * 31L + (long)(settings.isShowProgressBar() ? 1 : 0);
      hash = hash * 31L + (long)settings.getProgressBarStyle().ordinal();
      hash = hash * 31L + (long)(settings.isShowArtist() ? 1 : 0);
      hash = hash * 31L + (long)(settings.isShowTimeStamps() ? 1 : 0);
      return hash;
   }

   private static CachedLayout getLayout(HUDSettings settings, int screenWidth, int screenHeight) {
      long currentHash = computeSettingsHash(settings);
      if (cachedLayout == null || screenWidth != lastScreenWidth || screenHeight != lastScreenHeight || currentHash != lastSettingsHash) {
         cachedLayout = new CachedLayout(settings, screenWidth, screenHeight);
         lastScreenWidth = screenWidth;
         lastScreenHeight = screenHeight;
         lastSettingsHash = currentHash;
      }
      return cachedLayout;
   }

   public static void invalidateLayoutCache() {
      cachedLayout = null;
      lastSettingsHash = 0L;
   }

   public static void onHudRender(Object drawContextObj, Object tickDeltaObj) {
      try {
         if (!(drawContextObj instanceof DrawContext)) {
            return;
         }

         DrawContext graphics = (DrawContext) drawContextObj;
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player == null) {
            return;
         }

         HUDSettings settings = HUDSettings.getInstance();
         if (settings.isAutoHideInMenus() && mc.currentScreen != null) {
            return;
         }

         TrackInfo track = SpotifyApiClient.getInstance().getCurrentTrack();
         LyricsManager lyrics = LyricsManager.getInstance();
         int accentColor = settings.getAccentColor();
         int screenWidth = mc.getWindow().getScaledWidth();
         int screenHeight = mc.getWindow().getScaledHeight();
         CachedLayout layout = getLayout(settings, screenWidth, screenHeight);
         float dt = tickDeltaObj instanceof Float ? Math.min((Float) tickDeltaObj, 0.1F) : 0.016F;
         
         if (statusMessage != null && System.currentTimeMillis() > statusMessageExpiry) {
            statusMessage = null;
         }

         boolean hasTrack = track != null;
         boolean hasStatusMessage = statusMessage != null;
         boolean isLoggedIn = ConfigManager.getInstance().isLoggedIn();
         boolean showConnectionPrompt = !hasTrack && !hasStatusMessage && !isLoggedIn;
         boolean showNoPlaybackMessage = !hasTrack && !hasStatusMessage && isLoggedIn;
         boolean shouldShowHUD = hasTrack || hasStatusMessage || showConnectionPrompt || showNoPlaybackMessage;
         
         float targetSlide = shouldShowHUD ? 1.0F : 0.0F;
         float targetAlpha = shouldShowHUD ? 1.0F : 0.0F;
         slideOffset += (targetSlide - slideOffset) * dt * 8.0F;
         fadeAlpha += (targetAlpha - fadeAlpha) * dt * 10.0F;
         
         if (track != null) {
            String trackId = track.getTitle() + "|" + track.getArtist();
            if (!trackId.equals(lastTrackId)) {
               if (!lastTrackId.isEmpty()) {
                  trackChangeAnim = 0.0F;
               }
               lastTrackId = trackId;
               if (!track.getSpotifyId().isEmpty()) {
                  String accessToken = ConfigManager.getInstance().getAccessToken().orElse("");
                  lyrics.loadLyricsForTrack(track.getSpotifyId(), accessToken);
               }
               if (track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
                  AlbumArtCache.CacheEntry cached = albumArtCache.get(track.getAlbumArtUrl());
                  if (cached != null) {
                     settings.setAlbumDynamicColor(cached.dominantColor);
                  } else {
                     albumArtCache.loadAsync(track.getAlbumArtUrl(), mc);
                  }
               }
            }

            if (settings.isLyricsEnabled() && track.isPlaying()) {
               long currentTime = System.currentTimeMillis();
               if (lastUpdateTime == 0L || Math.abs(track.getProgressMs() - interpolatedProgressMs) > 2000L) {
                  interpolatedProgressMs = track.getProgressMs();
                  lastUpdateTime = currentTime;
               }
               long displayProgress = Math.min(interpolatedProgressMs + (currentTime - lastUpdateTime), track.getDurationMs());
               lyrics.updateProgress(displayProgress);
            }
         }

         if (trackChangeAnim < 1.0F) {
            trackChangeAnim = Math.min(1.0F, trackChangeAnim + dt * 5.0F * settings.getAnimationSpeed());
         }

         if (slideOffset < 0.01F) {
            return;
         }

         int x = layout.baseX;
         int y = layout.baseY;
         switch (settings.getPosition()) {
            case TOP_RIGHT:
               x = layout.baseX + (int)((1.0F - slideOffset) * (float)(layout.hudWidth + layout.margin * 2));
               break;
            case BOTTOM_LEFT:
               x = layout.baseX + (int)((slideOffset - 1.0F) * (float)(layout.hudWidth + layout.margin * 2));
               break;
            case BOTTOM_RIGHT:
               x = layout.baseX + (int)((1.0F - slideOffset) * (float)(layout.hudWidth + layout.margin * 2));
               break;
            default:
               x = layout.baseX + (int)((slideOffset - 1.0F) * (float)(layout.hudWidth + layout.margin * 2));
         }

         float bgAlpha = Math.min(1.0F, fadeAlpha) * settings.getHudOpacity();
         float alpha = Math.min(1.0F, fadeAlpha);
         if (bgAlpha < 0.01F) {
            return;
         }

         graphics.fill(x, y, x + layout.hudWidth, y + layout.hudHeight, applyAlpha(BG_MAIN, bgAlpha));
         graphics.fill(x, y, x + layout.hudWidth, y + 1, applyAlpha(BORDER_TOP, bgAlpha));
         graphics.fill(x, y, x + 3, y + layout.hudHeight, applyAlpha(accentColor, bgAlpha));
         
         int artX = x + layout.artX;
         int artY = y + (layout.hudHeight - layout.iconSize) / 2;
         if (settings.isShowAlbumArt()) {
            drawAlbumArt(graphics, settings, track, mc, artX, artY, layout.iconSize, bgAlpha, alpha);
         }

         int textX = x + layout.textX;
         if (track != null) {
            float textAlpha = alpha * easeOutCubic(trackChangeAnim);
            boolean useShadow = settings.isTextShadow();
            String title = truncate(mc, track.getTitle(), layout.textMaxW);
            graphics.drawText(mc.textRenderer, title, textX, y + layout.titleY, applyAlpha(TEXT_WHITE, textAlpha), useShadow);
            
            if (track.isPlaying() && !layout.compact) {
               int barsX = x + layout.hudWidth - (int)(20.0F * layout.scale);
               int barsY = y + (int)(8.0F * layout.scale);
               drawPlayingBars(graphics, barsX, barsY, alpha, accentColor);
            }

            if (settings.isShowArtist()) {
               String artist = truncate(mc, track.getArtist(), layout.textMaxW - (int)(10.0F * layout.scale));
               if (track.isPlaying()) {
                  graphics.drawText(mc.textRenderer, artist, textX, y + layout.artistY, applyAlpha(TEXT_GRAY, textAlpha), useShadow);
               } else {
                  graphics.drawText(mc.textRenderer, "❚❚ " + artist, textX, y + layout.artistY, applyAlpha(TEXT_DIM, textAlpha), useShadow);
               }
            }

            if (settings.isShowProgressBar()) {
               drawProgressBar(graphics, settings, track, mc, textX, x, y, layout, alpha, bgAlpha, accentColor, dt);
            }

            if (settings.isLyricsEnabled()) {
               drawLyrics(graphics, settings, lyrics, mc, x, y, layout, screenWidth, screenHeight, alpha, bgAlpha, accentColor, dt);
            }
         } else {
            boolean useShadow = settings.isTextShadow();
            if (hasStatusMessage) {
               String msg = truncate(mc, statusMessage, layout.textMaxW);
               int msgY = y + layout.hudHeight / 2 - 4;
               graphics.drawText(mc.textRenderer, msg, textX, msgY, applyAlpha(TEXT_GRAY, alpha), useShadow);
            } else if (showConnectionPrompt) {
               String title = "Musify";
               String msg = "Press K to connect Spotify";
               int titleY = layout.compact ? y + (int)(6.0F * layout.scale) : y + (int)(10.0F * layout.scale);
               int msgY = layout.compact ? y + (int)(20.0F * layout.scale) : y + (int)(26.0F * layout.scale);
               graphics.drawText(mc.textRenderer, title, textX, titleY, applyAlpha(TEXT_WHITE, alpha), useShadow);
               graphics.drawText(mc.textRenderer, msg, textX, msgY, applyAlpha(TEXT_DIM, alpha), useShadow);
            } else if (showNoPlaybackMessage) {
               String title = "Musify";
               String msg = "No active playback";
               int titleY = layout.compact ? y + (int)(6.0F * layout.scale) : y + (int)(10.0F * layout.scale);
               int msgY = layout.compact ? y + (int)(20.0F * layout.scale) : y + (int)(26.0F * layout.scale);
               graphics.drawText(mc.textRenderer, title, textX, titleY, applyAlpha(TEXT_WHITE, alpha), useShadow);
               graphics.drawText(mc.textRenderer, msg, textX, msgY, applyAlpha(TEXT_DIM, alpha), useShadow);
            }
         }
      } catch (Throwable t) {
         Musify.LOGGER.debug("HUD render error", t);
      }
   }

   private static void drawAlbumArt(DrawContext graphics, HUDSettings settings, TrackInfo track, MinecraftClient mc, int artX, int artY, int iconSize, float bgAlpha, float alpha) {
      HUDSettings.AlbumArtStyle artStyle = settings.getAlbumArtStyle();
      if (artStyle == HUDSettings.AlbumArtStyle.CIRCULAR) {
         drawCircle(graphics, artX + iconSize / 2, artY + iconSize / 2, iconSize / 2, applyAlpha(BG_ACCENT, bgAlpha));
      } else if (artStyle == HUDSettings.AlbumArtStyle.ROUNDED) {
         drawRoundedRect(graphics, artX, artY, iconSize, iconSize, 6, applyAlpha(BG_ACCENT, bgAlpha));
      } else {
         graphics.fill(artX, artY, artX + iconSize, artY + iconSize, applyAlpha(BG_ACCENT, bgAlpha));
      }

      if (track != null && track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
         String artUrl = track.getAlbumArtUrl();
         AlbumArtCache.CacheEntry cached = albumArtCache.get(artUrl);
         if (cached != null) {
            int[] size = cached.dimensions;
            float artScale = 0.95F + 0.05F * easeOutCubic(trackChangeAnim);
            int drawSize = (int)((float)iconSize * artScale);
            int offset = (iconSize - drawSize) / 2;
            graphics.drawTexture(cached.textureId, 
                                 artX + offset, 
                                 artY + offset, 
                                 0, 
                                 0, 
                                 drawSize, 
                                 drawSize, 
                                 size[0], 
                                 size[1]);
            
            if (artStyle == HUDSettings.AlbumArtStyle.ROUNDED) {
               drawRoundedCornerMask(graphics, artX + offset, artY + offset, drawSize, drawSize, 6, applyAlpha(BG_MAIN, bgAlpha));
            } else if (artStyle == HUDSettings.AlbumArtStyle.CIRCULAR) {
               drawCircularMask(graphics, artX + offset + drawSize / 2, artY + offset + drawSize / 2, drawSize / 2, applyAlpha(BG_MAIN, bgAlpha));
            }
         } else if (!albumArtCache.isLoading(artUrl)) {
            albumArtCache.loadAsync(artUrl, mc);
         }
      }
   }

   private static void drawProgressBar(DrawContext graphics, HUDSettings settings, TrackInfo track, MinecraftClient mc, int textX, int x, int y, CachedLayout layout, float alpha, float bgAlpha, int accentColor, float dt) {
      long currentTime = System.currentTimeMillis();
      long displayProgress;
      if (track.isPlaying()) {
         if (lastUpdateTime == 0L || Math.abs(track.getProgressMs() - interpolatedProgressMs) > 2000L) {
            interpolatedProgressMs = track.getProgressMs();
            lastUpdateTime = currentTime;
         }
         displayProgress = Math.min(interpolatedProgressMs + (currentTime - lastUpdateTime), track.getDurationMs());
      } else {
         displayProgress = track.getProgressMs();
         interpolatedProgressMs = displayProgress;
         lastUpdateTime = currentTime;
      }

      if (settings.isShowTimeStamps()) {
         String timeStr = formatTime(displayProgress) + " / " + track.getFormattedDuration();
         int timeW = mc.textRenderer.getWidth(timeStr);
         graphics.drawText(mc.textRenderer, timeStr, x + layout.hudWidth - timeW - (int)(10.0F * layout.scale), y + layout.timeY, applyAlpha(TEXT_DIM, alpha * easeOutCubic(trackChangeAnim)), settings.isTextShadow());
      }

      int barY = y + layout.barY;
      int barW = layout.barW;
      int barH = layout.barH;
      HUDSettings.ProgressBarStyle barStyle = settings.getProgressBarStyle();
      if (barStyle == HUDSettings.ProgressBarStyle.ROUNDED) {
         drawRoundedRect(graphics, textX, barY, barW, barH, barH / 2, applyAlpha(PROGRESS_BG, bgAlpha));
      } else {
         graphics.fill(textX, barY, textX + barW, barY + barH, applyAlpha(PROGRESS_BG, bgAlpha));
      }

      float targetProg = (float) displayProgress / (float) Math.max(1L, track.getDurationMs());
      animatedProgress = settings.isSmoothProgress() ? animatedProgress + (targetProg - animatedProgress) * dt * 12.0F * settings.getAnimationSpeed() : targetProg;
      int progW = (int)((float)barW * animatedProgress);
      if (progW > 0) {
         if (barStyle == HUDSettings.ProgressBarStyle.GRADIENT) {
            drawGradientRect(graphics, textX, barY, progW, barH, applyAlpha(accentColor, alpha), applyAlpha(brightenColor(accentColor, 0.3F), alpha));
         } else if (barStyle == HUDSettings.ProgressBarStyle.ROUNDED) {
            drawRoundedRect(graphics, textX, barY, progW, barH, barH / 2, applyAlpha(accentColor, alpha));
         } else {
            graphics.fill(textX, barY, textX + progW, barY + barH, applyAlpha(accentColor, alpha));
         }

         if (progW > 2 && barStyle != HUDSettings.ProgressBarStyle.THIN) {
            int dotSize = (int)(5.0F * layout.scale);
            int dotX = textX + progW - dotSize / 2;
            int dotY = barY - (dotSize - barH) / 2;
            if (barStyle == HUDSettings.ProgressBarStyle.ROUNDED) {
               drawCircle(graphics, dotX + dotSize / 2, dotY + dotSize / 2, dotSize / 2, applyAlpha(accentColor, alpha));
            } else {
               graphics.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, applyAlpha(accentColor, alpha));
            }
         }
      }
   }

   private static void drawLyrics(DrawContext graphics, HUDSettings settings, LyricsManager lyrics, MinecraftClient mc, int x, int y, CachedLayout layout, int screenWidth, int screenHeight, float alpha, float bgAlpha, int accentColor, float dt) {
      int lyricsY = y + layout.lyricsY;
      int lyricsX = x + layout.lyricsX;
      if (settings.getLyricsPosition() == HUDSettings.LyricsPosition.SEPARATE) {
         lyricsY = screenHeight - layout.lyricsHeight - (int)(20.0F * layout.scale);
         lyricsX = (screenWidth - layout.lyricsW) / 2;
      }

      float lyricsBgAlpha = bgAlpha * settings.getLyricsBackgroundOpacity();
      graphics.fill(lyricsX, lyricsY, lyricsX + layout.lyricsW, lyricsY + layout.lyricsHeight, applyAlpha(BG_LYRICS, lyricsBgAlpha));
      graphics.fill(lyricsX, lyricsY, lyricsX + 2, lyricsY + layout.lyricsHeight, applyAlpha(accentColor, lyricsBgAlpha * 0.7F));
      
      List<LyricsManager.LyricLine> upcomingLines = lyrics.getUpcomingLines(settings.getLyricsMaxLines());
      int currentLineIndex = lyrics.getCurrentLineIndex();
      if (currentLineIndex != lastLyricLineIndex) {
         lyricsSlideAnim = 0.0F;
         lastLyricLineIndex = currentLineIndex;
      }

      lyricsSlideAnim = Math.min(1.0F, lyricsSlideAnim + dt * 2.0F);
      float slideEase = easeOutCubic(lyricsSlideAnim);
      int slideOffsetY = (int)((1.0F - slideEase) * (float)layout.lineHeight);
      int lineY = lyricsY + layout.lyricsPadding + slideOffsetY;
      boolean karaokeMode = settings.isLyricsKaraokeMode();

      for (int i = 0; i < upcomingLines.size(); ++i) {
         LyricsManager.LyricLine line = upcomingLines.get(i);
         int color = i == 0 ? (karaokeMode ? accentColor : TEXT_WHITE) : TEXT_LYRIC_NEXT;
         float lineAlpha = i == 0 ? alpha * slideEase : alpha * 0.6F;
         if (lineY >= lyricsY && lineY < lyricsY + layout.lyricsHeight - 2) {
            String displayText = mc.textRenderer.getWidth(line.getText()) > layout.maxLyricWidth ? truncate(mc, line.getText(), layout.maxLyricWidth) : line.getText();
            graphics.drawText(mc.textRenderer, displayText, lyricsX + layout.lyricsPadding, lineY, applyAlpha(color, lineAlpha), i == 0 || karaokeMode);
         }
         lineY += layout.lineHeight;
      }

      if (upcomingLines.isEmpty()) {
         String text = lyrics.isLoading() ? "♪ Loading lyrics..." : "♪ No lyrics available";
         float textAlpha = lyrics.isLoading() ? alpha : alpha * 0.5F;
         graphics.drawText(mc.textRenderer, text, lyricsX + layout.lyricsPadding, lyricsY + layout.lyricsPadding, applyAlpha(TEXT_DIM, textAlpha), false);
      }
   }

   private static void drawPlayingBars(DrawContext graphics, int x, int y, float alpha, int color) {
      long time = System.currentTimeMillis();
      for (int i = 0; i < 3; ++i) {
         double phase = (double) time / 200.0 + (double) i * 0.8;
         int height = 3 + (int)(Math.abs(Math.sin(phase)) * 5.0);
         graphics.fill(x + i * 4, y + (8 - height), x + i * 4 + 2, y + 8, applyAlpha(color, alpha));
      }
   }

   private static int applyAlpha(int color, float alpha) {
      int a = (int)((float)(color >> 24 & 255) * alpha);
      return a << 24 | color & 0xFFFFFF;
   }

   private static float easeOutCubic(float t) {
      return 1.0F - (float) Math.pow(1.0F - t, 3.0);
   }

   private static int brightenColor(int color, float amount) {
      int a = color >> 24 & 255;
      int r = Math.min(255, (int)((float)(color >> 16 & 255) * (1.0F + amount)));
      int g = Math.min(255, (int)((float)(color >> 8 & 255) * (1.0F + amount)));
      int b = Math.min(255, (int)((float)(color & 255) * (1.0F + amount)));
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static void drawRoundedRect(DrawContext graphics, int x, int y, int width, int height, int radius, int color) {
      graphics.fill(x + radius, y, x + width - radius, y + height, color);
      graphics.fill(x, y + radius, x + width, y + height - radius, color);
      graphics.fill(x, y, x + radius, y + radius, color);
      graphics.fill(x + width - radius, y, x + width, y + radius, color);
      graphics.fill(x, y + height - radius, x + radius, y + height, color);
      graphics.fill(x + width - radius, y + height - radius, x + width, y + height, color);
   }

   private static void drawCircle(DrawContext graphics, int centerX, int centerY, int radius, int color) {
      for (int dy = -radius; dy <= radius; ++dy) {
         int dx = (int) Math.sqrt(radius * radius - dy * dy);
         graphics.fill(centerX - dx, centerY + dy, centerX + dx, centerY + dy + 1, color);
      }
   }

   private static void drawRoundedCornerMask(DrawContext graphics, int x, int y, int width, int height, int radius, int color) {
      for (int dy = 0; dy < radius; ++dy) {
         int dx = radius - (int) Math.sqrt(radius * radius - (radius - dy) * (radius - dy));
         if (dx > 0) {
            graphics.fill(x, y + dy, x + dx, y + dy + 1, color);
            graphics.fill(x + width - dx, y + dy, x + width, y + dy + 1, color);
            graphics.fill(x, y + height - radius + dy, x + dx, y + height - radius + dy + 1, color);
            graphics.fill(x + width - dx, y + height - radius + dy, x + width, y + height - radius + dy + 1, color);
         }
      }
   }

   private static void drawCircularMask(DrawContext graphics, int centerX, int centerY, int radius, int color) {
      int boxSize = radius * 2;
      int boxX = centerX - radius;
      int boxY = centerY - radius;
      for (int dy = 0; dy < boxSize; ++dy) {
         int relY = dy - radius;
         int dx = (int) Math.sqrt(Math.max(0, radius * radius - relY * relY));
         if (dx < radius) {
            graphics.fill(boxX, boxY + dy, boxX + radius - dx, boxY + dy + 1, color);
            graphics.fill(boxX + radius + dx, boxY + dy, boxX + boxSize, boxY + dy + 1, color);
         }
      }
   }

   private static void drawGradientRect(DrawContext graphics, int x, int y, int width, int height, int colorLeft, int colorRight) {
      int steps = Math.min(width, 20);
      int stepWidth = Math.max(1, width / steps);
      for (int i = 0; i < steps; ++i) {
         float t = (float) i / (float)(steps - 1);
         int color = lerpColor(colorLeft, colorRight, t);
         int startX = x + i * stepWidth;
         int endX = i == steps - 1 ? x + width : startX + stepWidth;
         graphics.fill(startX, y, endX, y + height, color);
      }
   }

   private static int lerpColor(int color1, int color2, float t) {
      int a = (int)((float)(color1 >> 24 & 255) + (float)((color2 >> 24 & 255) - (color1 >> 24 & 255)) * t);
      int r = (int)((float)(color1 >> 16 & 255) + (float)((color2 >> 16 & 255) - (color1 >> 16 & 255)) * t);
      int g = (int)((float)(color1 >> 8 & 255) + (float)((color2 >> 8 & 255) - (color1 >> 8 & 255)) * t);
      int b = (int)((float)(color1 & 255) + (float)((color2 & 255) - (color1 & 255)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static String truncate(MinecraftClient mc, String text, int maxWidth) {
      if (text != null && !text.isEmpty()) {
         if (maxWidth <= 0) {
            return text.length() > 10 ? text.substring(0, 10) + "…" : text;
         } else if (mc.textRenderer.getWidth(text) <= maxWidth) {
            return text;
         } else {
            while (text.length() > 1 && mc.textRenderer.getWidth(text + "…") > maxWidth) {
               text = text.substring(0, text.length() - 1);
            }
            return text.isEmpty() ? "…" : text + "…";
         }
      } else {
         return "";
      }
   }

   private static String formatTime(long ms) {
      long sec = ms / 1000L;
      return String.format("%d:%02d", sec / 60L, sec % 60L);
   }

   private static void loadAlbumArt(String url, MinecraftClient mc) {
      albumArtCache.loadAsync(url, mc);
   }

   public static void clearAlbumArtCache(boolean includeDisk) {
      albumArtCache.clearAll(includeDisk);
   }

   public static void clearAlbumArtCache() {
      albumArtCache.clearAll(false);
   }

   public static int getCachedAlbumArtCount() {
      return albumArtCache.getMemoryCacheSize();
   }

   public static String getCacheStats() {
      return albumArtCache.getStats();
   }

   public static void setStatusMessage(String message) {
      statusMessage = message;
      statusMessageExpiry = message != null ? System.currentTimeMillis() + STATUS_MESSAGE_DURATION_MS : 0L;
   }

   @Environment(EnvType.CLIENT)
   private static class CachedLayout {
      final float scale;
      final boolean compact;
      final int hudWidth;
      final int hudHeight;
      final int margin;
      final int iconSize;
      final int lyricsHeight;
      final int totalHeight;
      final int baseX;
      final int baseY;
      final int artX;
      final int textX;
      final int textMaxW;
      final int barH;
      final int barY;
      final int barW;
      final int titleY;
      final int artistY;
      final int timeY;
      final int lyricsY;
      final int lyricsX;
      final int lyricsW;
      final int lyricsPadding;
      final int lineHeight;
      final int maxLyricWidth;

      CachedLayout(HUDSettings settings, int screenWidth, int screenHeight) {
         this.scale = settings.getHudScale();
         this.compact = settings.isCompactMode();
         this.hudWidth = (int)(BASE_HUD_WIDTH * this.scale);
         this.hudHeight = (int)((float)(this.compact ? BASE_COMPACT_HEIGHT : BASE_HUD_HEIGHT) * this.scale);
         this.margin = (int)(BASE_MARGIN * this.scale);
         int baseIconSize = (int)((float)(this.compact ? BASE_COMPACT_ICON_SIZE : BASE_ICON_SIZE) * this.scale);
         float artSizeMultiplier = Math.max(0.5F, Math.min(1.5F, settings.getAlbumArtSize()));
         this.iconSize = Math.min((int)((float)baseIconSize * artSizeMultiplier), (int)((float)this.hudWidth * 0.6F));
         this.lyricsHeight = settings.isLyricsEnabled() ? (int)((float)(settings.getLyricsMaxLines() * LYRICS_LINE_HEIGHT + 10) * this.scale) : 0;
         this.totalHeight = this.hudHeight + this.lyricsHeight;
         this.baseX = settings.getMarginX(screenWidth, this.hudWidth);
         this.baseY = settings.getMarginY(screenHeight, this.totalHeight);
         this.artX = (int)(10.0F * this.scale);
         this.textX = settings.isShowAlbumArt() ? this.artX + this.iconSize + (int)(10.0F * this.scale) : (int)(10.0F * this.scale);
         this.textMaxW = Math.max(50, settings.isShowAlbumArt() ? this.hudWidth - this.iconSize - (int)(30.0F * this.scale) : this.hudWidth - (int)(20.0F * this.scale));
         
         int barHeight;
         switch (settings.getProgressBarStyle()) {
            case THIN:
               barHeight = (int)(2.0F * this.scale);
               break;
            case THICK:
               barHeight = (int)(5.0F * this.scale);
               break;
            case ROUNDED:
            case GRADIENT:
               barHeight = (int)(4.0F * this.scale);
               break;
            default:
               barHeight = (int)(3.0F * this.scale);
         }
         this.barH = barHeight;
         this.barY = this.hudHeight - (int)(14.0F * this.scale);
         this.barW = this.hudWidth - (settings.isShowAlbumArt() ? this.iconSize : 0) - (int)(30.0F * this.scale);
         this.titleY = this.compact ? (int)(6.0F * this.scale) : (int)(12.0F * this.scale);
         this.artistY = this.compact ? (int)(20.0F * this.scale) : (int)(25.0F * this.scale);
         this.timeY = this.compact ? (int)(8.0F * this.scale) : (int)(25.0F * this.scale);
         
         int lyricsYOffset;
         switch (settings.getLyricsPosition()) {
            case ABOVE_HUD:
               lyricsYOffset = -(this.lyricsHeight + (int)(2.0F * this.scale));
               break;
            case SEPARATE:
               lyricsYOffset = screenHeight - this.lyricsHeight - (int)(20.0F * this.scale) - this.baseY;
               break;
            default:
               lyricsYOffset = this.hudHeight + (int)(2.0F * this.scale);
         }
         this.lyricsY = lyricsYOffset;
         this.lyricsW = this.hudWidth;
         this.lyricsX = settings.getLyricsPosition() == HUDSettings.LyricsPosition.SEPARATE ? (screenWidth - this.lyricsW) / 2 - this.baseX : 0;
         this.lyricsPadding = (int)(6.0F * this.scale);
         this.lineHeight = (int)(LYRICS_LINE_HEIGHT * this.scale * settings.getLyricsFontSize());
         this.maxLyricWidth = this.lyricsW - (int)(16.0F * this.scale);
      }
   }
}
