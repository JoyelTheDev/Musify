package dev.joyel.musify.client;

import dev.joyel.musify.Musify;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class LyricsManager {
   private static final Gson GSON = new Gson();
   private static LyricsManager INSTANCE;
   private static final int MAX_REQUESTS_PER_MINUTE = 30;
   private static final long RATE_LIMIT_WINDOW_MS = 60000L;
   private static final int MAX_CACHE_SIZE = 50;
   private static final int PREFETCH_QUEUE_SIZE = 3;
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private final ConcurrentHashMap<String, List<LyricLine>> lyricsCache = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, Boolean> loadingLyrics = new ConcurrentHashMap<>();
   private final Queue<Long> requestTimestamps = new LinkedList<>();
   private final Object rateLimitLock = new Object();
   private final Queue<String> prefetchQueue = new ConcurrentLinkedQueue<>();
   private final ConcurrentHashMap<String, String> prefetchTokens = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<BatchRequest> batchQueue = new ConcurrentLinkedQueue<>();
   private volatile ScheduledFuture<?> batchProcessor;
   private volatile String currentTrackId = "";
   private volatile List<LyricLine> currentLyrics = new ArrayList<>();
   private volatile int currentLineIndex = -1;

   public static synchronized LyricsManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new LyricsManager();
      }
      return INSTANCE;
   }

   private LyricsManager() {
      this.startBatchProcessor();
   }

   private void startBatchProcessor() {
      ExecutorManager executor = ExecutorManager.getInstance();
      this.batchProcessor = executor.scheduleAtFixedRate(this::processBatchQueue, 100L, 100L, TimeUnit.MILLISECONDS);
   }

   private void processBatchQueue() {
      if (!this.batchQueue.isEmpty()) {
         if (this.canMakeRequest()) {
            BatchRequest request = this.batchQueue.poll();
            if (request != null) {
               if (this.lyricsCache.containsKey(request.trackId)) {
                  request.future.complete(this.lyricsCache.get(request.trackId));
               } else if (this.loadingLyrics.containsKey(request.trackId)) {
                  this.batchQueue.offer(request);
               } else {
                  this.recordRequest();
                  this.loadingLyrics.put(request.trackId, true);
                  ExecutorManager.getInstance().submit(() -> {
                     try {
                        List<LyricLine> lyrics = this.fetchLyrics(request.trackId, request.accessToken);
                        if (!lyrics.isEmpty()) {
                           this.addToCache(request.trackId, lyrics);
                           if (request.trackId.equals(this.currentTrackId)) {
                              this.currentLyrics = lyrics;
                           }
                        }
                        request.future.complete(lyrics);
                     } catch (Exception e) {
                        Musify.LOGGER.debug("Failed to fetch lyrics: {}", e.getMessage());
                        request.future.complete(new ArrayList<>());
                     } finally {
                        this.loadingLyrics.remove(request.trackId);
                     }
                  });
               }
            }
         }
      }
   }

   private boolean canMakeRequest() {
      synchronized(this.rateLimitLock) {
         long now = System.currentTimeMillis();
         while (!this.requestTimestamps.isEmpty() && now - this.requestTimestamps.peek() > RATE_LIMIT_WINDOW_MS) {
            this.requestTimestamps.poll();
         }
         return this.requestTimestamps.size() < MAX_REQUESTS_PER_MINUTE;
      }
   }

   private void recordRequest() {
      synchronized(this.rateLimitLock) {
         this.requestTimestamps.offer(System.currentTimeMillis());
      }
   }

   private void addToCache(String trackId, List<LyricLine> lyrics) {
      while (this.lyricsCache.size() >= MAX_CACHE_SIZE) {
         String oldestKey = this.lyricsCache.keySet().iterator().next();
         this.lyricsCache.remove(oldestKey);
      }
      this.lyricsCache.put(trackId, lyrics);
   }

   public void prefetchLyrics(List<String> upcomingTrackIds, String accessToken) {
      if (upcomingTrackIds != null && !upcomingTrackIds.isEmpty()) {
         int prefetched = 0;
         for (String trackId : upcomingTrackIds) {
            if (prefetched >= PREFETCH_QUEUE_SIZE) {
               break;
            }
            if (trackId != null && !trackId.isEmpty() && 
                !this.lyricsCache.containsKey(trackId) && 
                !this.loadingLyrics.containsKey(trackId) && 
                !this.prefetchQueue.contains(trackId)) {
               this.prefetchQueue.offer(trackId);
               this.prefetchTokens.put(trackId, accessToken);
               prefetched++;
            }
         }
         this.processPrefetchQueue();
      }
   }

   private void processPrefetchQueue() {
      String trackId = this.prefetchQueue.poll();
      if (trackId != null) {
         String token = this.prefetchTokens.remove(trackId);
         if (token != null && !token.isEmpty()) {
            if (!this.canMakeRequest()) {
               this.prefetchQueue.offer(trackId);
               this.prefetchTokens.put(trackId, token);
            } else {
               BatchRequest request = new BatchRequest(trackId, token);
               this.batchQueue.offer(request);
            }
         }
      }
   }

   public Optional<LyricLine> getCurrentLine() {
      return this.currentLineIndex >= 0 && this.currentLineIndex < this.currentLyrics.size() 
         ? Optional.of(this.currentLyrics.get(this.currentLineIndex)) 
         : Optional.empty();
   }

   public Optional<LyricLine> getNextLine() {
      int nextIndex = this.currentLineIndex + 1;
      return nextIndex >= 0 && nextIndex < this.currentLyrics.size() 
         ? Optional.of(this.currentLyrics.get(nextIndex)) 
         : Optional.empty();
   }

   public List<LyricLine> getUpcomingLines(int count) {
      List<LyricLine> lines = new ArrayList<>();
      int startIndex = Math.max(0, this.currentLineIndex);
      for (int i = 0; i < count && startIndex + i < this.currentLyrics.size(); i++) {
         lines.add(this.currentLyrics.get(startIndex + i));
      }
      return lines;
   }

   public int getCurrentLineIndex() {
      return this.currentLineIndex;
   }

   public void updateProgress(long progressMs) {
      if (this.currentLyrics.isEmpty()) {
         this.currentLineIndex = -1;
      } else {
         int newIndex = -1;
         for (int i = 0; i < this.currentLyrics.size() && progressMs >= this.currentLyrics.get(i).getStartTimeMs(); i++) {
            newIndex = i;
         }
         this.currentLineIndex = newIndex;
      }
   }

   public void loadLyricsForTrack(String trackId, String accessToken) {
      if (trackId != null && !trackId.isEmpty()) {
         if (!trackId.equals(this.currentTrackId) || this.currentLyrics.isEmpty()) {
            if (this.lyricsCache.containsKey(trackId)) {
               this.currentLyrics = this.lyricsCache.get(trackId);
               this.currentTrackId = trackId;
               this.currentLineIndex = -1;
            } else if (!this.loadingLyrics.containsKey(trackId)) {
               this.currentTrackId = trackId;
               this.currentLyrics = new ArrayList<>();
               this.currentLineIndex = -1;
               BatchRequest request = new BatchRequest(trackId, accessToken);
               this.batchQueue.offer(request);
               request.future.thenAccept((lyrics) -> {
                  if (trackId.equals(this.currentTrackId) && !lyrics.isEmpty()) {
                     this.currentLyrics = lyrics;
                  }
               });
            }
         }
      } else {
         this.currentLyrics = new ArrayList<>();
         this.currentTrackId = "";
         this.currentLineIndex = -1;
      }
   }

   private List<LyricLine> fetchLyrics(String trackId, String accessToken) {
      List<LyricLine> lyrics = new ArrayList<>();
      if (trackId != null && !trackId.isEmpty()) {
         try {
            String url = "https://spotify-lyrics-api-pi.vercel.app/?trackid=" + trackId + "&format=lrc";
            HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create(url))
               .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
               .GET()
               .build();
            HttpResponse<String> response = this.httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
               lyrics = this.parseSpotifyLyricsApiResponse(response.body());
            }
         } catch (Exception e) {
            Musify.LOGGER.debug("Lyrics fetch error: {}", e.getMessage());
         }
      }
      return lyrics;
   }

   private List<LyricLine> parseSpotifyLyricsApiResponse(String json) {
      List<LyricLine> lyrics = new ArrayList<>();
      try {
         JsonObject root = GSON.fromJson(json, JsonObject.class);
         if (root.has("error") && root.get("error").getAsBoolean()) {
            return lyrics;
         }
         if (root.has("lines") && root.get("lines").isJsonArray()) {
            JsonArray lines = root.getAsJsonArray("lines");
            for (int i = 0; i < lines.size(); i++) {
               JsonObject line = lines.get(i).getAsJsonObject();
               String words = line.has("words") ? line.get("words").getAsString() : "";
               long startMs = 0L;
               if (line.has("startTimeMs")) {
                  try {
                     startMs = Long.parseLong(line.get("startTimeMs").getAsString());
                  } catch (NumberFormatException e) {
                     startMs = line.get("startTimeMs").getAsLong();
                  }
               } else if (line.has("timeTag")) {
                  startMs = this.parseLrcTimeTag(line.get("timeTag").getAsString());
               }
               
               long endMs = startMs + 5000L;
               if (i + 1 < lines.size()) {
                  JsonObject nextLine = lines.get(i + 1).getAsJsonObject();
                  if (nextLine.has("startTimeMs")) {
                     try {
                        endMs = Long.parseLong(nextLine.get("startTimeMs").getAsString());
                     } catch (NumberFormatException e) {
                        endMs = nextLine.get("startTimeMs").getAsLong();
                     }
                  }
               }
               
               if (!words.isEmpty() && !words.equals("♪") && !words.equals("...")) {
                  lyrics.add(new LyricLine(words, startMs, endMs));
               }
            }
         }
      } catch (Exception e) {
         Musify.LOGGER.debug("Lyrics parse error: {}", e.getMessage());
      }
      return lyrics;
   }

   private long parseLrcTimeTag(String timeTag) {
      try {
         String clean = timeTag.replace("[", "").replace("]", "");
         String[] parts = clean.split(":");
         if (parts.length == 2) {
            int minutes = Integer.parseInt(parts[0]);
            float seconds = Float.parseFloat(parts[1]);
            return (long)(minutes * 60000L + seconds * 1000.0F);
         }
      } catch (Exception e) {
         // Ignore parse errors
      }
      return 0L;
   }

   public boolean hasLyrics() {
      return !this.currentLyrics.isEmpty();
   }

   public boolean isLoading() {
      return !this.currentTrackId.isEmpty() && this.loadingLyrics.containsKey(this.currentTrackId);
   }

   public void clearCache() {
      this.lyricsCache.clear();
      this.currentLyrics = new ArrayList<>();
      this.currentTrackId = "";
      this.currentLineIndex = -1;
      this.prefetchQueue.clear();
      this.prefetchTokens.clear();
      this.batchQueue.clear();
   }

   public String getCurrentTrackId() {
      return this.currentTrackId;
   }

   public String getCacheStats() {
      return String.format("Cache: %d/%d, Loading: %d, Queue: %d, Prefetch: %d", 
         this.lyricsCache.size(), MAX_CACHE_SIZE, 
         this.loadingLyrics.size(), 
         this.batchQueue.size(), 
         this.prefetchQueue.size());
   }

   public void shutdown() {
      if (this.batchProcessor != null) {
         this.batchProcessor.cancel(false);
      }
      this.clearCache();
   }

   @Environment(EnvType.CLIENT)
   private static class BatchRequest {
      final String trackId;
      final String accessToken;
      final CompletableFuture<List<LyricLine>> future;
      final long timestamp;

      BatchRequest(String trackId, String accessToken) {
         this.trackId = trackId;
         this.accessToken = accessToken;
         this.future = new CompletableFuture<>();
         this.timestamp = System.currentTimeMillis();
      }
   }

   @Environment(EnvType.CLIENT)
   public static class LyricLine {
      private final String text;
      private final long startTimeMs;
      private final long endTimeMs;

      public LyricLine(String text, long startTimeMs, long endTimeMs) {
         this.text = text;
         this.startTimeMs = startTimeMs;
         this.endTimeMs = endTimeMs;
      }

      public String getText() {
         return this.text;
      }

      public long getStartTimeMs() {
         return this.startTimeMs;
      }

      public long getEndTimeMs() {
         return this.endTimeMs;
      }
   }
}