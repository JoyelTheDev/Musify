package dev.joyel.musify.client;

import dev.joyel.musify.Musify;
import dev.joyel.musify.client.util.ErrorHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SpotifyApiClient {
   private static final Gson GSON = new Gson();
   private static SpotifyApiClient INSTANCE;
   private static final String DEFAULT_CLIENT_ID = "8a6dcd6b12314c2db17ece771ca340fc";
   private static final String DEFAULT_CLIENT_SECRET = "05ed7db9d622431099a5d2555b1bb359";
   private static final String REDIRECT_URI = "http://127.0.0.1:25566/callback";
   private static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
   private static final String ENDPOINT_PLAYBACK = "spotify/playback";
   private static final String ENDPOINT_PLAY_PAUSE = "spotify/play-pause";
   private static final String ENDPOINT_NEXT = "spotify/next";
   private static final String ENDPOINT_PREV = "spotify/prev";
   private static final String ENDPOINT_TOKEN = "spotify/token";
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private final ConfigManager config = ConfigManager.getInstance();
   private final ErrorHandler errorHandler = ErrorHandler.getInstance();
   private volatile TrackInfo currentTrack;
   private volatile TrackInfo lastKnownTrack;
   private volatile HttpServer callbackServer;
   private ScheduledFuture<?> tokenRefreshTask;
   private ScheduledFuture<?> playbackPollTask;
   private ScheduledFuture<?> reconnectTask;
   private volatile String lastErrorMessage = null;
   private volatile ErrorHandler.ErrorCategory lastErrorCategory = null;
   private final AtomicInteger consecutivePollingFailures = new AtomicInteger(0);
   private static final int MAX_POLLING_FAILURES_BEFORE_SLOWDOWN = 5;
   private volatile long currentPollInterval = 5L;
   private Consumer<String> statusMessageListener;
   private Consumer<Boolean> connectionStatusListener;

   public static synchronized SpotifyApiClient getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new SpotifyApiClient();
      }

      return INSTANCE;
   }

   private SpotifyApiClient() {
      this.errorHandler.setStatusListener((status) -> {
         switch (status) {
            case OFFLINE -> this.notifyStatusMessage("Offline - music controls unavailable");
            case RECONNECTING -> this.notifyStatusMessage("Reconnecting to Spotify...");
            case ONLINE -> this.notifyStatusMessage((String)null);
            case RATE_LIMITED -> this.notifyStatusMessage("Rate limited - slowing down requests");
         }

      });
   }

   private String getClientId() {
      String customId = this.config.getSpotifyClientId();
      return customId != null && !customId.isEmpty() ? customId : "8a6dcd6b12314c2db17ece771ca340fc";
   }

   private String getClientSecret() {
      String customSecret = this.config.getSpotifyClientSecret();
      return customSecret != null && !customSecret.isEmpty() ? customSecret : "05ed7db9d622431099a5d2555b1bb359";
   }

   public void initialize() {
      this.config.load();
      ExecutorManager executor = ExecutorManager.getInstance();
      this.tokenRefreshTask = executor.scheduleAtFixedRate(() -> {
         try {
            if (this.config.getRefreshToken().isPresent()) {
               long expiresAt = this.config.getExpiresAt();
               long now = Instant.now().getEpochSecond();
               if (expiresAt - now < 60L) {
                  this.refreshAccessTokenAsync();
               }
            }
         } catch (Exception e) {
            Musify.LOGGER.debug("Token refresh check failed", e);
         }

      }, 10L, 30L, TimeUnit.SECONDS);
      this.playbackPollTask = executor.scheduleAtFixedRate(() -> {
         try {
            this.pollPlaybackWithRetry();
         } catch (Exception e) {
            this.handlePollError(e);
         }

      }, 0L, 3L, TimeUnit.SECONDS);
      this.reconnectTask = executor.scheduleAtFixedRate(() -> {
         if (this.errorHandler.isOffline()) {
            this.errorHandler.tryReconnect();
            if (this.ensureAuthorized()) {
               this.pollPlaybackWithRetry();
            }
         }

      }, 30L, 30L, TimeUnit.SECONDS);
   }

   public void shutdown() {
      if (this.tokenRefreshTask != null) {
         this.tokenRefreshTask.cancel(false);
      }

      if (this.playbackPollTask != null) {
         this.playbackPollTask.cancel(false);
      }

      if (this.reconnectTask != null) {
         this.reconnectTask.cancel(false);
      }

      if (this.callbackServer != null) {
         try {
            this.callbackServer.stop(0);
         } catch (Exception var2) {
         }

         this.callbackServer = null;
      }

   }

   public TrackInfo getCurrentTrack() {
      return this.errorHandler.isOffline() && this.currentTrack == null && this.lastKnownTrack != null ? new TrackInfo(this.lastKnownTrack.getSpotifyId(), this.lastKnownTrack.getTitle(), this.lastKnownTrack.getArtist(), this.lastKnownTrack.getProgressMs(), this.lastKnownTrack.getDurationMs(), false, this.lastKnownTrack.getAlbumArtUrl()) : this.currentTrack;
   }

   public void clearCurrentTrack() {
      this.currentTrack = null;
   }

   public String getLastErrorMessage() {
      return this.lastErrorMessage;
   }

   public boolean isOffline() {
      return this.errorHandler.isOffline();
   }

   public void setStatusMessageListener(Consumer<String> listener) {
      this.statusMessageListener = listener;
   }

   public void setConnectionStatusListener(Consumer<Boolean> listener) {
      this.connectionStatusListener = listener;
   }

   private void notifyStatusMessage(String message) {
      this.lastErrorMessage = message;
      if (this.statusMessageListener != null) {
         try {
            this.statusMessageListener.accept(message);
         } catch (Exception e) {
            Musify.LOGGER.debug("Status listener error", e);
         }
      }

   }

   private void notifyConnectionStatus(boolean connected) {
      if (this.connectionStatusListener != null) {
         try {
            this.connectionStatusListener.accept(connected);
         } catch (Exception e) {
            Musify.LOGGER.debug("Connection listener error", e);
         }
      }

   }

   public void playPauseAsync() {
      ExecutorManager.getInstance().submit((Runnable)(() -> {
         if (this.errorHandler.isOffline()) {
            this.notifyStatusMessage("Offline - cannot control playback");
         } else {
            ErrorHandler.Result<Void> result = this.errorHandler.<Void>executeWithRetry("spotify/play-pause", () -> {
               try {
                  if (!this.ensureAuthorized()) {
                     return ErrorHandler.Result.failure(ErrorHandler.ErrorCategory.AUTH_EXPIRED);
                  } else {
                     TrackInfo track = this.getCurrentTrack();
                     String endpoint = track != null && track.isPlaying() ? "https://api.spotify.com/v1/me/player/pause" : "https://api.spotify.com/v1/me/player/play";
                     HttpRequest.Builder var10000 = HttpRequest.newBuilder().uri(URI.create(endpoint));
                     Optional var10002 = this.config.getAccessToken();
                     HttpRequest req = var10000.header("Authorization", "Bearer " + (String)var10002.orElse("")).PUT(BodyPublishers.noBody()).timeout(Duration.ofSeconds(10L)).build();
                     HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString());
                     ErrorHandler.ErrorCategory error = this.errorHandler.categorizeHttpCode(resp.statusCode(), (String)resp.body());
                     return error != null ? ErrorHandler.Result.failure(error) : ErrorHandler.Result.success((Object)null);
                  }
               } catch (Exception e) {
                  return ErrorHandler.Result.failure(this.errorHandler.categorizeException(e));
               }
            });
            if (result.isSuccess()) {
               try {
                  Thread.sleep(300L);
               } catch (InterruptedException var3) {
               }

               this.pollPlaybackWithRetry();
               this.notifyStatusMessage((String)null);
            } else {
               this.notifyStatusMessage(result.getErrorMessage());
            }

         }
      }));
   }

   public void nextTrackAsync() {
      ExecutorManager.getInstance().submit((Runnable)(() -> {
         if (this.errorHandler.isOffline()) {
            this.notifyStatusMessage("Offline - cannot skip track");
         } else {
            ErrorHandler.Result<Void> result = this.errorHandler.<Void>executeWithRetry("spotify/next", () -> {
               try {
                  if (!this.ensureAuthorized()) {
                     return ErrorHandler.Result.failure(ErrorHandler.ErrorCategory.AUTH_EXPIRED);
                  } else {
                     HttpRequest.Builder var10000 = HttpRequest.newBuilder().uri(URI.create("https://api.spotify.com/v1/me/player/next"));
                     Optional var10002 = this.config.getAccessToken();
                     HttpRequest req = var10000.header("Authorization", "Bearer " + (String)var10002.orElse("")).POST(BodyPublishers.noBody()).timeout(Duration.ofSeconds(10L)).build();
                     HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString());
                     ErrorHandler.ErrorCategory error = this.errorHandler.categorizeHttpCode(resp.statusCode(), (String)resp.body());
                     return error != null ? ErrorHandler.Result.failure(error) : ErrorHandler.Result.success((Object)null);
                  }
               } catch (Exception e) {
                  return ErrorHandler.Result.failure(this.errorHandler.categorizeException(e));
               }
            });
            if (result.isFailure()) {
               this.notifyStatusMessage(result.getErrorMessage());
            } else {
               try {
                  Thread.sleep(500L);
               } catch (InterruptedException var3) {
               }

               this.pollPlaybackWithRetry();
               this.notifyStatusMessage((String)null);
            }

         }
      }));
   }

   public void previousTrackAsync() {
      ExecutorManager.getInstance().submit((Runnable)(() -> {
         if (this.errorHandler.isOffline()) {
            this.notifyStatusMessage("Offline - cannot go to previous track");
         } else {
            ErrorHandler.Result<Void> result = this.errorHandler.<Void>executeWithRetry("spotify/prev", () -> {
               try {
                  if (!this.ensureAuthorized()) {
                     return ErrorHandler.Result.failure(ErrorHandler.ErrorCategory.AUTH_EXPIRED);
                  } else {
                     HttpRequest.Builder var10000 = HttpRequest.newBuilder().uri(URI.create("https://api.spotify.com/v1/me/player/previous"));
                     Optional var10002 = this.config.getAccessToken();
                     HttpRequest req = var10000.header("Authorization", "Bearer " + (String)var10002.orElse("")).POST(BodyPublishers.noBody()).timeout(Duration.ofSeconds(10L)).build();
                     HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString());
                     ErrorHandler.ErrorCategory error = this.errorHandler.categorizeHttpCode(resp.statusCode(), (String)resp.body());
                     return error != null ? ErrorHandler.Result.failure(error) : ErrorHandler.Result.success((Object)null);
                  }
               } catch (Exception e) {
                  return ErrorHandler.Result.failure(this.errorHandler.categorizeException(e));
               }
            });
            if (result.isFailure()) {
               this.notifyStatusMessage(result.getErrorMessage());
            } else {
               try {
                  Thread.sleep(500L);
               } catch (InterruptedException var3) {
               }

               this.pollPlaybackWithRetry();
               this.notifyStatusMessage((String)null);
            }

         }
      }));
   }

   private void handlePollError(Exception e) {
      int failures = this.consecutivePollingFailures.incrementAndGet();
      ErrorHandler.ErrorCategory category = this.errorHandler.categorizeException(e);
      this.errorHandler.recordFailure("spotify/playback", category);
      if (failures >= 5) {
         this.currentPollInterval = Math.min(30L, this.currentPollInterval * 2L);
         Musify.LOGGER.debug("Slowing poll interval to {}s after {} failures", this.currentPollInterval, failures);
      }

   }

   private void pollPlaybackWithRetry() {
      if (!this.errorHandler.isCircuitOpen("spotify/playback")) {
         ErrorHandler.Result<TrackInfo> result = this.errorHandler.<TrackInfo>executeWithRetry("spotify/playback", () -> {
            try {
               return this.pollPlaybackInternal();
            } catch (Exception e) {
               return ErrorHandler.Result.failure(this.errorHandler.categorizeException(e));
            }
         }, 2);
         if (result.isSuccess()) {
            this.consecutivePollingFailures.set(0);
            this.currentPollInterval = 5L;
            this.currentTrack = result.getValue();
            if (this.currentTrack != null) {
               this.lastKnownTrack = this.currentTrack;
            }

            this.notifyConnectionStatus(true);
         } else {
            this.consecutivePollingFailures.incrementAndGet();
            this.lastErrorCategory = result.getError();
            if (result.getError() != ErrorHandler.ErrorCategory.NETWORK_TIMEOUT) {
               Musify.LOGGER.debug("Poll failed: {}", result.getErrorMessage());
            }
         }

      }
   }

   private ErrorHandler.Result<TrackInfo> pollPlaybackInternal() throws IOException, InterruptedException {
      if (!this.ensureAuthorized()) {
         return ErrorHandler.Result.<TrackInfo>failure(ErrorHandler.ErrorCategory.AUTH_EXPIRED);
      } else {
         HttpRequest.Builder var10000 = HttpRequest.newBuilder().uri(URI.create("https://api.spotify.com/v1/me/player/currently-playing"));
         Optional var10002 = this.config.getAccessToken();
         HttpRequest req = var10000.header("Authorization", "Bearer " + (String)var10002.orElse("")).timeout(Duration.ofSeconds(10L)).GET().build();
         HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString());
         if (resp.statusCode() == 200) {
            JsonObject body = (JsonObject)GSON.fromJson((String)resp.body(), JsonObject.class);
            TrackInfo track = this.parseTrackFromJson(body);
            return ErrorHandler.Result.<TrackInfo>success(track);
         } else if (resp.statusCode() == 204) {
            if (this.currentTrack != null) {
               TrackInfo pausedTrack = new TrackInfo(this.currentTrack.getSpotifyId(), this.currentTrack.getTitle(), this.currentTrack.getArtist(), this.currentTrack.getProgressMs(), this.currentTrack.getDurationMs(), false, this.currentTrack.getAlbumArtUrl());
               return ErrorHandler.Result.<TrackInfo>success(pausedTrack);
            } else {
               return ErrorHandler.Result.<TrackInfo>success((Object)null);
            }
         } else if (resp.statusCode() == 401) {
            this.refreshAccessTokenAsync();
            return ErrorHandler.Result.<TrackInfo>failure(ErrorHandler.ErrorCategory.AUTH_EXPIRED);
         } else if (resp.statusCode() == 429) {
            String retryAfter = (String)resp.headers().firstValue("Retry-After").orElse((Object)null);
            long waitMs = this.errorHandler.parseRetryAfter(retryAfter, 5000L);
            Musify.LOGGER.debug("Rate limited, waiting {}ms", waitMs);
            return ErrorHandler.Result.<TrackInfo>failure(ErrorHandler.ErrorCategory.RATE_LIMITED, "Rate limited. Waiting " + waitMs / 1000L + "s...");
         } else {
            ErrorHandler.ErrorCategory error = this.errorHandler.categorizeHttpCode(resp.statusCode(), (String)resp.body());
            return ErrorHandler.Result.<TrackInfo>failure(error != null ? error : ErrorHandler.ErrorCategory.UNKNOWN);
         }
      }
   }

   private TrackInfo parseTrackFromJson(JsonObject body) {
      String spotifyId = "";
      String title = "";
      String artist = "";
      String albumArtUrl = "";
      long progress = 0L;
      long duration = 1L;
      boolean playing = false;
      if (body.has("item") && !body.get("item").isJsonNull()) {
         JsonObject item = body.getAsJsonObject("item");
         if (item.has("id") && !item.get("id").isJsonNull()) {
            spotifyId = item.get("id").getAsString();
         }

         title = item.has("name") ? item.get("name").getAsString() : "";
         if (item.has("artists") && item.get("artists").isJsonArray() && item.getAsJsonArray("artists").size() > 0) {
            artist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
         }

         if (item.has("duration_ms")) {
            duration = item.get("duration_ms").getAsLong();
         }

         if (item.has("album") && !item.get("album").isJsonNull()) {
            JsonObject album = item.getAsJsonObject("album");
            if (album.has("images") && album.get("images").isJsonArray()) {
               JsonArray images = album.getAsJsonArray("images");
               if (images.size() >= 2) {
                  albumArtUrl = images.get(1).getAsJsonObject().get("url").getAsString();
               } else if (images.size() > 0) {
                  albumArtUrl = images.get(0).getAsJsonObject().get("url").getAsString();
               }
            }
         }
      }

      if (body.has("progress_ms")) {
         progress = body.get("progress_ms").getAsLong();
      }

      if (body.has("is_playing")) {
         playing = body.get("is_playing").getAsBoolean();
      }

      return new TrackInfo(spotifyId, title, artist, progress, duration, playing, albumArtUrl);
   }

   public synchronized void startAuthorization() {
      try {
         if (this.callbackServer != null) {
            try {
               this.callbackServer.stop(0);
            } catch (Exception var2) {
            }

            this.callbackServer = null;
         }

         InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 25566);
         this.callbackServer = HttpServer.create(addr, 0);
         this.callbackServer.createContext("/callback", new OAuthCallbackHandler());
         this.callbackServer.setExecutor((command) -> {
            Thread t = new Thread(command, "Musify-OAuth-Callback");
            t.setDaemon(true);
            t.start();
         });
         this.callbackServer.start();
      } catch (IOException e) {
         Musify.LOGGER.warn("Failed to start OAuth callback server", e);
      }

      String var10000 = urlEncode(this.getClientId());
      String url = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + var10000 + "&scope=" + urlEncode("user-read-playback-state user-modify-playback-state user-read-currently-playing") + "&redirect_uri=" + urlEncode("http://127.0.0.1:25566/callback");
      this.openBrowser(url);
   }

   private void openBrowser(String url) {
      try {
         String os = System.getProperty("os.name").toLowerCase();
         if (os.contains("win")) {
            Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
         } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(new String[]{"open", url});
         } else {
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
         }
      } catch (Exception var5) {
         try {
            Desktop.getDesktop().browse(URI.create(url));
         } catch (Exception var4) {
            Musify.LOGGER.info("Please open this URL in your browser: {}", url);
         }
      }

   }

   private void exchangeCodeForTokens(String code) {
      try {
         String auth = Base64.getEncoder().encodeToString((this.getClientId() + ":" + this.getClientSecret()).getBytes(StandardCharsets.UTF_8));
         String var10000 = urlEncode(code);
         String body = "grant_type=authorization_code&code=" + var10000 + "&redirect_uri=" + urlEncode("http://127.0.0.1:25566/callback");
         HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://accounts.spotify.com/api/token")).header("Authorization", "Basic " + auth).header("Content-Type", "application/x-www-form-urlencoded").POST(BodyPublishers.ofString(body)).build();
         HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString());
         if (resp.statusCode() == 200) {
            JsonObject json = (JsonObject)GSON.fromJson((String)resp.body(), JsonObject.class);
            String access = json.has("access_token") ? json.get("access_token").getAsString() : null;
            int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
            String refresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
            if (access != null && refresh != null) {
               this.config.saveTokens(access, refresh, expiresIn);
               Musify.LOGGER.info("Spotify authentication successful!");
            }
         }
      } catch (Exception e) {
         Musify.LOGGER.warn("Token exchange failed", e);
      } finally {
         if (this.callbackServer != null) {
            try {
               this.callbackServer.stop(0);
            } catch (Exception var17) {
            }

            this.callbackServer = null;
         }

      }

   }

   public void refreshAccessTokenAsync() {
      ExecutorManager.getInstance().submit((Runnable)(() -> {
         try {
            Optional<String> refreshOpt = this.config.getRefreshToken();
            if (refreshOpt.isEmpty()) {
               return;
            }

            String auth = Base64.getEncoder().encodeToString((this.getClientId() + ":" + this.getClientSecret()).getBytes(StandardCharsets.UTF_8));
            String body = "grant_type=refresh_token&refresh_token=" + urlEncode((String)refreshOpt.get());
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://accounts.spotify.com/api/token")).header("Authorization", "Basic " + auth).header("Content-Type", "application/x-www-form-urlencoded").POST(BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
               JsonObject json = (JsonObject)GSON.fromJson((String)resp.body(), JsonObject.class);
               String access = json.has("access_token") ? json.get("access_token").getAsString() : null;
               int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
               String newRefresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : (String)refreshOpt.get();
               if (access != null) {
                  this.config.saveTokens(access, newRefresh, expiresIn);
               }
            }
         } catch (Exception e) {
            Musify.LOGGER.debug("Token refresh failed", e);
         }

      }));
   }

   private boolean ensureAuthorized() {
      if (this.config.getAccessToken().isPresent()) {
         return true;
      } else if (this.config.getRefreshToken().isPresent()) {
         this.refreshAccessTokenAsync();
         return false;
      } else {
         return false;
      }
   }

   private static String urlEncode(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

   @Environment(EnvType.CLIENT)
   private class OAuthCallbackHandler implements HttpHandler {
      public void handle(HttpExchange exchange) throws IOException {
         String query = exchange.getRequestURI().getQuery();
         String responseHtml = "<html><head><style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#1a1a1a;color:#fff}h2{color:#1DB954}</style></head><body><h2>✓ Spotify connected! You can close this window.</h2></body></html>";
         if (query != null && query.contains("code=")) {
            String code = null;

            for(String part : query.split("&")) {
               if (part.startsWith("code=")) {
                  code = part.substring("code=".length());
                  break;
               }
            }

            ExecutorManager.getInstance().submit((Runnable)(() -> SpotifyApiClient.this.exchangeCodeForTokens(code)));
         } else {
            responseHtml = "<html><head><style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#1a1a1a;color:#fff}h2{color:#e74c3c}</style></head><body><h2>✗ Authorization failed or cancelled.</h2></body></html>";
         }

         byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
         exchange.sendResponseHeaders(200, (long)bytes.length);
         OutputStream os = exchange.getResponseBody();

         try {
            os.write(bytes);
         } catch (Throwable var10) {
            if (os != null) {
               try {
                  os.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (os != null) {
            os.close();
         }

      }
   }
}
