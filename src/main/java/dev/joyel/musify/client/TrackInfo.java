package dev.joyel.musify.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TrackInfo {
   private final String spotifyId;
   private final String title;
   private final String artist;
   private final long progressMs;
   private final long durationMs;
   private final boolean playing;
   private final String albumArtUrl;

   public TrackInfo(String spotifyId, String title, String artist, long progressMs, long durationMs, boolean playing, String albumArtUrl) {
      this.spotifyId = spotifyId == null ? "" : spotifyId;
      this.title = title == null ? "" : title;
      this.artist = artist == null ? "" : artist;
      this.progressMs = progressMs;
      this.durationMs = durationMs;
      this.playing = playing;
      this.albumArtUrl = albumArtUrl;
   }

   public String getSpotifyId() {
      return this.spotifyId;
   }

   public String getTitle() {
      return this.title;
   }

   public String getArtist() {
      return this.artist;
   }

   public long getProgressMs() {
      return this.progressMs;
   }

   public long getDurationMs() {
      return this.durationMs;
   }

   public boolean isPlaying() {
      return this.playing;
   }

   public String getAlbumArtUrl() {
      return this.albumArtUrl;
   }

   public String getFormattedProgress() {
      return formatTime(this.progressMs);
   }

   public String getFormattedDuration() {
      return formatTime(this.durationMs);
   }

   private static String formatTime(long ms) {
      long totalSeconds = ms / 1000L;
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      return String.format("%d:%02d", minutes, seconds);
   }
}
