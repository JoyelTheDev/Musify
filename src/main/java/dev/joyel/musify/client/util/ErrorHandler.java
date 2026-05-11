package dev.joyel.musify.client.util;

import dev.joyel.musify.Musify;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ErrorHandler {
   private static ErrorHandler INSTANCE;
   private final AtomicBoolean offlineMode = new AtomicBoolean(false);
   private final AtomicLong lastOnlineCheck = new AtomicLong(0L);
   private final AtomicLong lastSuccessfulRequest = new AtomicLong(System.currentTimeMillis());
   private static final long OFFLINE_CHECK_INTERVAL_MS = 30000L;
   private static final long OFFLINE_THRESHOLD_MS = 60000L;
   private static final int MAX_RETRIES = 3;
   private static final long BASE_DELAY_MS = 1000L;
   private static final long MAX_DELAY_MS = 30000L;
   private static final double BACKOFF_MULTIPLIER = 2.0;
   private static final double JITTER_FACTOR = 0.1;
   private final Map<String, EndpointErrorState> endpointErrors = new ConcurrentHashMap<>();
   private Consumer<ConnectionStatus> statusListener;

   public static synchronized ErrorHandler getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ErrorHandler();
      }
      return INSTANCE;
   }

   private ErrorHandler() {
   }

   public ErrorCategory categorizeException(Exception e) {
      if (e instanceof UnknownHostException || e instanceof ConnectException) {
         return ErrorCategory.NETWORK_OFFLINE;
      }
      if (e instanceof SocketTimeoutException || e instanceof HttpTimeoutException) {
         return ErrorCategory.NETWORK_TIMEOUT;
      }
      if (e instanceof IOException) {
         String msg = e.getMessage();
         return msg != null && msg.toLowerCase().contains("timeout") ? ErrorCategory.NETWORK_TIMEOUT : ErrorCategory.NETWORK_OFFLINE;
      }
      return ErrorCategory.UNKNOWN;
   }

   public ErrorCategory categorizeHttpCode(int statusCode, String responseBody) {
      switch (statusCode) {
         case 401:
            return ErrorCategory.AUTH_EXPIRED;
         case 403:
            return responseBody != null && responseBody.contains("PREMIUM_REQUIRED") ? ErrorCategory.PREMIUM_REQUIRED : ErrorCategory.AUTH_INVALID;
         case 404:
            return responseBody != null && responseBody.contains("NO_ACTIVE_DEVICE") ? ErrorCategory.NO_ACTIVE_DEVICE : ErrorCategory.NOT_FOUND;
         case 429:
            return ErrorCategory.RATE_LIMITED;
         case 500:
         case 502:
         case 503:
         case 504:
            return ErrorCategory.SERVER_ERROR;
         default:
            return statusCode >= 400 ? ErrorCategory.UNKNOWN : null;
      }
   }

   public String getUserMessage(ErrorCategory category) {
      return category != null ? category.getUserMessage() : "An unexpected error occurred.";
   }

   public String getUserMessage(Exception e) {
      return this.getUserMessage(this.categorizeException(e));
   }

   public boolean isOffline() {
      return this.offlineMode.get();
   }

   public ConnectionStatus getConnectionStatus() {
      if (this.offlineMode.get()) {
         long timeSinceCheck = System.currentTimeMillis() - this.lastOnlineCheck.get();
         return timeSinceCheck < OFFLINE_CHECK_INTERVAL_MS ? ConnectionStatus.RECONNECTING : ConnectionStatus.OFFLINE;
      }
      return ConnectionStatus.ONLINE;
   }

   public void recordSuccess(String endpoint) {
      this.lastSuccessfulRequest.set(System.currentTimeMillis());
      if (this.offlineMode.compareAndSet(true, false)) {
         Musify.LOGGER.info("Connection restored - back online!");
         this.notifyStatusChange(ConnectionStatus.ONLINE);
      }
      EndpointErrorState state = this.endpointErrors.get(endpoint);
      if (state != null) {
         state.recordSuccess();
      }
   }

   public void recordFailure(String endpoint, ErrorCategory category) {
      EndpointErrorState state = this.endpointErrors.computeIfAbsent(endpoint, k -> new EndpointErrorState());
      state.recordFailure(category);
      if (category == ErrorCategory.NETWORK_OFFLINE || category == ErrorCategory.NETWORK_TIMEOUT) {
         long timeSinceSuccess = System.currentTimeMillis() - this.lastSuccessfulRequest.get();
         if (timeSinceSuccess > OFFLINE_THRESHOLD_MS && this.offlineMode.compareAndSet(false, true)) {
            Musify.LOGGER.warn("Entering offline mode - no successful requests for {}ms", timeSinceSuccess);
            this.notifyStatusChange(ConnectionStatus.OFFLINE);
         }
      }
   }

   public boolean isCircuitOpen(String endpoint) {
      EndpointErrorState state = this.endpointErrors.get(endpoint);
      return state != null && state.isCircuitOpen();
   }

   public long calculateBackoffDelay(int attemptNumber) {
      if (attemptNumber <= 0) {
         return 0L;
      }
      long delay = (long)(BASE_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attemptNumber - 1));
      delay = Math.min(delay, MAX_DELAY_MS);
      double jitter = (Math.random() * 2.0 - 1.0) * JITTER_FACTOR * delay;
      delay = (long)(delay + jitter);
      return Math.max(0L, delay);
   }

   public <T> Result<T> executeWithRetry(String operationName, Supplier<Result<T>> operation) {
      return this.executeWithRetry(operationName, operation, MAX_RETRIES);
   }

   public <T> Result<T> executeWithRetry(String operationName, Supplier<Result<T>> operation, int maxRetries) {
      if (this.isCircuitOpen(operationName)) {
         Musify.LOGGER.debug("Circuit open for {}, skipping request", operationName);
         EndpointErrorState state = this.endpointErrors.get(operationName);
         return Result.failure(state != null ? state.lastError : ErrorCategory.SERVER_ERROR, 
            "Too many recent failures. Please wait a moment.");
      }

      Result<T> lastResult = null;
      for (int attempt = 1; attempt <= maxRetries; ++attempt) {
         try {
            lastResult = operation.get();
            if (lastResult.isSuccess()) {
               this.recordSuccess(operationName);
               return lastResult;
            }

            ErrorCategory error = lastResult.getError();
            if (!this.isRetryable(error)) {
               this.recordFailure(operationName, error);
               return lastResult;
            }

            if (attempt < maxRetries) {
               long delay = this.calculateBackoffDelay(attempt);
               Musify.LOGGER.debug("{} failed (attempt {}/{}), retrying in {}ms: {}", 
                  operationName, attempt, maxRetries, delay, error);
               if (delay > 0L) {
                  Thread.sleep(delay);
               }
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(ErrorCategory.UNKNOWN, "Operation interrupted");
         } catch (Exception e) {
            ErrorCategory category = this.categorizeException(e);
            lastResult = Result.failure(category, this.getUserMessage(category));
            if (!this.isRetryable(category) || attempt >= maxRetries) {
               this.recordFailure(operationName, category);
               Musify.LOGGER.debug("{} failed: {}", operationName, e.getMessage());
               return lastResult;
            }
            try {
               long delay = this.calculateBackoffDelay(attempt);
               Thread.sleep(delay);
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               return Result.failure(ErrorCategory.UNKNOWN, "Operation interrupted");
            }
         }
      }

      if (lastResult != null) {
         this.recordFailure(operationName, lastResult.getError());
      }
      return lastResult != null ? lastResult : Result.failure(ErrorCategory.UNKNOWN);
   }

   public boolean isRetryable(ErrorCategory category) {
      if (category == null) {
         return false;
      }
      switch (category) {
         case NETWORK_TIMEOUT:
         case RATE_LIMITED:
         case SERVER_ERROR:
            return true;
         case NETWORK_OFFLINE:
            return !this.offlineMode.get();
         default:
            return false;
      }
   }

   public void setStatusListener(Consumer<ConnectionStatus> listener) {
      this.statusListener = listener;
   }

   private void notifyStatusChange(ConnectionStatus status) {
      if (this.statusListener != null) {
         try {
            this.statusListener.accept(status);
         } catch (Exception e) {
            Musify.LOGGER.debug("Status listener error", e);
         }
      }
   }

   public void tryReconnect() {
      if (this.offlineMode.get()) {
         long now = System.currentTimeMillis();
         if (now - this.lastOnlineCheck.get() >= OFFLINE_CHECK_INTERVAL_MS) {
            this.lastOnlineCheck.set(now);
            this.notifyStatusChange(ConnectionStatus.RECONNECTING);
         }
      }
   }

   public void resetEndpoint(String endpoint) {
      this.endpointErrors.remove(endpoint);
   }

   public void reset() {
      this.endpointErrors.clear();
      this.offlineMode.set(false);
      this.lastSuccessfulRequest.set(System.currentTimeMillis());
   }

   public long parseRetryAfter(String retryAfterHeader, long defaultMs) {
      if (retryAfterHeader != null && !retryAfterHeader.isEmpty()) {
         try {
            int seconds = Integer.parseInt(retryAfterHeader);
            return (long)seconds * 1000L;
         } catch (NumberFormatException e) {
            try {
               DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
               ZonedDateTime retryTime = ZonedDateTime.parse(retryAfterHeader, formatter);
               long delayMs = retryTime.toInstant().toEpochMilli() - System.currentTimeMillis();
               return Math.max(0L, delayMs);
            } catch (Exception ex) {
               return defaultMs;
            }
         }
      }
      return defaultMs;
   }

   public void logError(String operation, ErrorCategory category, Exception e) {
      String message = String.format("%s failed: %s", operation, category.getUserMessage());
      switch (category) {
         case NETWORK_OFFLINE:
         case NETWORK_TIMEOUT:
            Musify.LOGGER.debug(message);
            break;
         case RATE_LIMITED:
            Musify.LOGGER.info("Rate limited: {}", operation);
            break;
         case AUTH_EXPIRED:
         case AUTH_INVALID:
            Musify.LOGGER.warn(message);
            break;
         case SERVER_ERROR:
            Musify.LOGGER.debug("{} - {}", message, e != null ? e.getMessage() : "");
            break;
         default:
            Musify.LOGGER.debug(message, e);
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum ErrorCategory {
      NETWORK_OFFLINE("No internet connection. Musify is in offline mode."),
      NETWORK_TIMEOUT("Connection timed out. Will retry automatically."),
      RATE_LIMITED("Too many requests. Slowing down..."),
      AUTH_EXPIRED("Session expired. Please re-authenticate with Spotify."),
      AUTH_INVALID("Invalid credentials. Please reconnect to Spotify."),
      SERVER_ERROR("Spotify is having issues. Will retry shortly."),
      NOT_FOUND("Resource not found."),
      NO_ACTIVE_DEVICE("No active Spotify device. Open Spotify and play something."),
      PREMIUM_REQUIRED("This feature requires Spotify Premium."),
      UNKNOWN("An unexpected error occurred.");

      private final String userMessage;

      private ErrorCategory(String userMessage) {
         this.userMessage = userMessage;
      }

      public String getUserMessage() {
         return this.userMessage;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum ConnectionStatus {
      ONLINE,
      OFFLINE,
      RECONNECTING,
      RATE_LIMITED;
   }

   @Environment(EnvType.CLIENT)
   private static class EndpointErrorState {
      final AtomicInteger consecutiveFailures = new AtomicInteger(0);
      final AtomicLong lastFailure = new AtomicLong(0L);
      final AtomicLong circuitOpenUntil = new AtomicLong(0L);
      volatile ErrorCategory lastError = null;

      void recordSuccess() {
         this.consecutiveFailures.set(0);
         this.circuitOpenUntil.set(0L);
         this.lastError = null;
      }

      void recordFailure(ErrorCategory category) {
         int failures = this.consecutiveFailures.incrementAndGet();
         this.lastFailure.set(System.currentTimeMillis());
         this.lastError = category;
         if (failures >= 5) {
            long cooldown = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * (long)Math.pow(BACKOFF_MULTIPLIER, failures - 5));
            this.circuitOpenUntil.set(System.currentTimeMillis() + cooldown);
         }
      }

      boolean isCircuitOpen() {
         return System.currentTimeMillis() < this.circuitOpenUntil.get();
      }
   }

   @Environment(EnvType.CLIENT)
   public static class Result<T> {
      private final T value;
      private final ErrorCategory error;
      private final String errorMessage;
      private final boolean success;

      private Result(T value, ErrorCategory error, String errorMessage, boolean success) {
         this.value = value;
         this.error = error;
         this.errorMessage = errorMessage;
         this.success = success;
      }

      public static <T> Result<T> success(T value) {
         return new Result<>(value, null, null, true);
      }

      public static <T> Result<T> failure(ErrorCategory error, String message) {
         return new Result<>(null, error, message, false);
      }

      public static <T> Result<T> failure(ErrorCategory error) {
         return new Result<>(null, error, error.getUserMessage(), false);
      }

      public boolean isSuccess() {
         return this.success;
      }

      public boolean isFailure() {
         return !this.success;
      }

      public T getValue() {
         return this.value;
      }

      public T getOrDefault(T defaultValue) {
         return this.success ? this.value : defaultValue;
      }

      public ErrorCategory getError() {
         return this.error;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }
   }
}
