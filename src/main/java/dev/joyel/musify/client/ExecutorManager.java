package dev.joyel.musify.client;

import dev.joyel.musify.Musify;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ExecutorManager {
   private static ExecutorManager INSTANCE;
   private final ExecutorService workerPool;
   private final ScheduledExecutorService scheduledPool;
   private final ExecutorService rateLimitedPool;
   private volatile boolean isShutdown = false;

   public static synchronized ExecutorManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ExecutorManager();
      }

      return INSTANCE;
   }

   private ExecutorManager() {
      this.workerPool = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue(100), new DaemonThreadFactory("Musify-Worker"), new ThreadPoolExecutor.CallerRunsPolicy());
      this.scheduledPool = Executors.newScheduledThreadPool(2, new DaemonThreadFactory("Musify-Scheduler"));
      this.rateLimitedPool = Executors.newSingleThreadExecutor(new DaemonThreadFactory("Musify-RateLimited"));
   }

   public CompletableFuture<Void> submit(Runnable task) {
      return this.isShutdown ? CompletableFuture.completedFuture((Object)null) : CompletableFuture.runAsync(task, this.workerPool);
   }

   public <T> CompletableFuture<T> submit(Callable<T> task) {
      return this.isShutdown ? CompletableFuture.completedFuture((Object)null) : CompletableFuture.supplyAsync(() -> {
         try {
            return task.call();
         } catch (Exception e) {
            throw new CompletionException(e);
         }
      }, this.workerPool);
   }

   public CompletableFuture<Void> submitRateLimited(Runnable task) {
      return this.isShutdown ? CompletableFuture.completedFuture((Object)null) : CompletableFuture.runAsync(task, this.rateLimitedPool);
   }

   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
      return this.isShutdown ? null : this.scheduledPool.scheduleAtFixedRate(() -> {
         try {
            task.run();
         } catch (Exception e) {
            Musify.LOGGER.debug("Scheduled task error", e);
         }

      }, initialDelay, period, unit);
   }

   public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
      return this.isShutdown ? null : this.scheduledPool.schedule(() -> {
         try {
            task.run();
         } catch (Exception e) {
            Musify.LOGGER.debug("Scheduled task error", e);
         }

      }, delay, unit);
   }

   public Executor getWorkerExecutor() {
      return this.workerPool;
   }

   public ScheduledExecutorService getScheduledExecutor() {
      return this.scheduledPool;
   }

   public void shutdown() {
      if (!this.isShutdown) {
         this.isShutdown = true;
         Musify.LOGGER.info("Shutting down Musify thread pools...");
         this.shutdownExecutor(this.workerPool, "Worker");
         this.shutdownExecutor(this.scheduledPool, "Scheduler");
         this.shutdownExecutor(this.rateLimitedPool, "RateLimited");
         Musify.LOGGER.info("Musify thread pools shutdown complete");
      }
   }

   private void shutdownExecutor(ExecutorService executor, String name) {
      executor.shutdown();

      try {
         if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
            Musify.LOGGER.debug("{} pool did not terminate gracefully, forcing shutdown", name);
            executor.shutdownNow();
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
               Musify.LOGGER.warn("{} pool did not terminate", name);
            }
         }
      } catch (InterruptedException var4) {
         executor.shutdownNow();
         Thread.currentThread().interrupt();
      }

   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   @Environment(EnvType.CLIENT)
   private static class DaemonThreadFactory implements ThreadFactory {
      private final String namePrefix;
      private final AtomicInteger threadNumber = new AtomicInteger(1);

      DaemonThreadFactory(String namePrefix) {
         this.namePrefix = namePrefix;
      }

      public Thread newThread(Runnable r) {
         String var10003 = this.namePrefix;
         Thread t = new Thread(r, var10003 + "-" + this.threadNumber.getAndIncrement());
         t.setDaemon(true);
         t.setPriority(4);
         return t;
      }
   }
}
