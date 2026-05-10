package dev.joyel.musify.client.util;

import dev.joyel.musify.Musify;
import dev.joyel.musify.client.ExecutorManager;
import dev.joyel.musify.client.HUDSettings;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.DynamicTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.TextureManager;

@Environment(EnvType.CLIENT)
public class AlbumArtCache {
   private static AlbumArtCache INSTANCE;
   private static final int MAX_MEMORY_CACHE_SIZE = 20;
   private static final int MAX_DISK_CACHE_SIZE = 100;
   private static final long MAX_DISK_CACHE_BYTES = 52428800L;
   private static final String CACHE_DIR_NAME = "album_art_cache";
   private final LinkedHashMap<String, CacheEntry> memoryCache = new LinkedHashMap<String, CacheEntry>(20, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
         if (this.size() > 20) {
            AlbumArtCache.this.scheduleTextureCleanup(eldest.getValue());
            return true;
         } else {
            return false;
         }
      }
   };
   private final Map<String, Boolean> loadingArt = new ConcurrentHashMap();
   private final ReentrantLock cacheLock = new ReentrantLock();
   private final Path diskCachePath = FabricLoader.getInstance().getGameDir().resolve("musify").resolve("album_art_cache");
   private final AtomicInteger textureCounter = new AtomicInteger(0);
   private final HttpClient httpClient;

   public static synchronized AlbumArtCache getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AlbumArtCache();
      }

      return INSTANCE;
   }

   private AlbumArtCache() {
      try {
         Files.createDirectories(this.diskCachePath);
      } catch (IOException e) {
         Musify.LOGGER.warn("Failed to create album art cache directory", e);
      }

      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
      ExecutorManager.getInstance().submit(this::cleanupDiskCache);
   }

   public CacheEntry get(String url) {
      if (url != null && !url.isEmpty()) {
         this.cacheLock.lock();

         CacheEntry var3;
         try {
            CacheEntry entry = this.memoryCache.get(url);
            if (entry == null) {
               return null;
            }

            entry.touch();
            var3 = entry;
         } finally {
            this.cacheLock.unlock();
         }

         return var3;
      } else {
         return null;
      }
   }

   public boolean isLoading(String url) {
      return this.loadingArt.containsKey(url);
   }

   public boolean isCached(String url) {
      if (url != null && !url.isEmpty()) {
         this.cacheLock.lock();

         boolean var2;
         try {
            if (!this.memoryCache.containsKey(url)) {
               return this.getDiskCacheFile(url).exists();
            }

            var2 = true;
         } finally {
            this.cacheLock.unlock();
         }

         return var2;
      } else {
         return false;
      }
   }

   public boolean loadAsync(String url, MinecraftClient mc) {
      if (url != null && !url.isEmpty()) {
         if (this.get(url) != null) {
            return false;
         } else if (this.loadingArt.putIfAbsent(url, true) != null) {
            return false;
         } else {
            ExecutorManager.getInstance().submit(() -> {
               try {
                  this.loadAlbumArtInternal(url, mc);
               } finally {
                  this.loadingArt.remove(url);
               }
            });
            return true;
         }
      } else {
         return false;
      }
   }

   private void loadAlbumArtInternal(String url, MinecraftClient mc) {
      try {
         BufferedImage img = null;
         int dominantColor = -14829228;
         File diskFile = this.getDiskCacheFile(url);
         File metaFile = this.getDiskMetaFile(url);
         if (diskFile.exists() && metaFile.exists()) {
            try {
               img = ImageIO.read(diskFile);
               DiskCacheMetadata meta = this.loadMetadata(metaFile);
               if (meta != null) {
                  dominantColor = meta.dominantColor;
               }

               Musify.LOGGER.debug("Loaded album art from disk cache: {}", url);
            } catch (Exception e) {
               Musify.LOGGER.debug("Failed to load from disk cache, fetching from network", e);
               img = null;
            }
         }

         if (img == null) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15L)).GET().build();
            HttpResponse<InputStream> resp = this.httpClient.send(req, BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
               Musify.LOGGER.debug("Failed to fetch album art, status: {}", resp.statusCode());
               return;
            }

            try (InputStream is = resp.body()) {
               img = ImageIO.read(is);
            }

            if (img != null) {
               dominantColor = this.extractDominantColor(img);
               this.saveToDiskCache(url, img, dominantColor);
            }
         }

         if (img == null) {
            return;
         }

         mc.execute(() -> HUDSettings.getInstance().setAlbumDynamicColor(dominantColor));
         
         ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
         ImageIO.write(img, "PNG", pngOut);
         
         try (NativeImage nativeImg = NativeImage.read(new ByteArrayInputStream(pngOut.toByteArray()))) {
            int w = nativeImg.getWidth();
            int h = nativeImg.getHeight();
            mc.execute(() -> {
               try {
                  String name = "album_" + this.textureCounter.incrementAndGet();
                  DynamicTexture tex = new DynamicTexture(nativeImg);
                  Identifier loc = Identifier.of("musify", name);
                  mc.getTextureManager().registerTexture(loc, tex);
                  CacheEntry entry = new CacheEntry(loc, new int[]{w, h}, dominantColor, url);
                  this.cacheLock.lock();

                  try {
                     this.memoryCache.put(url, entry);
                  } finally {
                     this.cacheLock.unlock();
                  }
               } catch (Exception e) {
                  Musify.LOGGER.debug("Failed to register texture", e);
               }
            });
         }
      } catch (Exception e) {
         Musify.LOGGER.debug("Failed to load album art: {}", e.getMessage());
      }
   }

   private File getDiskCacheFile(String url) {
      String hash = this.hashUrl(url);
      return this.diskCachePath.resolve(hash + ".png").toFile();
   }

   private File getDiskMetaFile(String url) {
      String hash = this.hashUrl(url);
      return this.diskCachePath.resolve(hash + ".meta").toFile();
   }

   private String hashUrl(String url) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] hash = md.digest(url.getBytes());
         StringBuilder hex = new StringBuilder();

         for(int i = 0; i < 16; ++i) {
            hex.append(String.format("%02x", hash[i]));
         }

         return hex.toString();
      } catch (Exception var6) {
         return Integer.toHexString(url.hashCode());
      }
   }

   private void saveToDiskCache(String url, BufferedImage img, int dominantColor) {
      try {
         File imgFile = this.getDiskCacheFile(url);
         File metaFile = this.getDiskMetaFile(url);
         ImageIO.write(img, "PNG", imgFile);
         DiskCacheMetadata meta = new DiskCacheMetadata();
         meta.url = url;
         meta.dominantColor = dominantColor;
         meta.width = img.getWidth();
         meta.height = img.getHeight();
         meta.createdAt = System.currentTimeMillis();
         meta.fileSize = imgFile.length();
         this.saveMetadata(metaFile, meta);
         Musify.LOGGER.debug("Saved album art to disk cache: {}", url);
      } catch (Exception e) {
         Musify.LOGGER.debug("Failed to save to disk cache", e);
      }
   }

   private DiskCacheMetadata loadMetadata(File file) {
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         DiskCacheMetadata meta = new DiskCacheMetadata();
         String line;
         while((line = reader.readLine()) != null) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
               switch (parts[0]) {
                  case "url":
                     meta.url = parts[1];
                     break;
                  case "color":
                     meta.dominantColor = Integer.parseInt(parts[1]);
                     break;
                  case "width":
                     meta.width = Integer.parseInt(parts[1]);
                     break;
                  case "height":
                     meta.height = Integer.parseInt(parts[1]);
                     break;
                  case "created":
                     meta.createdAt = Long.parseLong(parts[1]);
                     break;
                  case "size":
                     meta.fileSize = Long.parseLong(parts[1]);
               }
            }
         }
         return meta;
      } catch (Exception var10) {
         return null;
      }
   }

   private void saveMetadata(File file, DiskCacheMetadata meta) {
      try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
         writer.println("url=" + meta.url);
         writer.println("color=" + meta.dominantColor);
         writer.println("width=" + meta.width);
         writer.println("height=" + meta.height);
         writer.println("created=" + meta.createdAt);
         writer.println("size=" + meta.fileSize);
      } catch (Exception e) {
         Musify.LOGGER.debug("Failed to save metadata", e);
      }
   }

   private void cleanupDiskCache() {
      try {
         File[] files = this.diskCachePath.toFile().listFiles((dir, name) -> name.endsWith(".png"));
         if (files == null || files.length <= 100) {
            return;
         }

         Arrays.sort(files, Comparator.comparingLong(File::lastModified));
         long totalSize = 0L;
         List<File> toDelete = new ArrayList();

         for(File file : files) {
            totalSize += file.length();
         }

         for(File file : files) {
            File meta = new File(file.getAbsolutePath().replace(".png", ".meta"));
            totalSize -= file.length();
            toDelete.add(file);
            if (meta.exists()) {
               toDelete.add(meta);
            }
         }

         for(File file : toDelete) {
            if (!file.delete()) {
               Musify.LOGGER.debug("Failed to delete cache file: {}", file);
            }
         }

         if (!toDelete.isEmpty()) {
            Musify.LOGGER.debug("Cleaned up {} files from disk cache", toDelete.size());
         }
      } catch (Exception e) {
         Musify.LOGGER.debug("Disk cache cleanup failed", e);
      }
   }

   private void scheduleTextureCleanup(CacheEntry entry) {
      if (entry != null && entry.textureId != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null) {
            mc.execute(() -> {
               try {
                  mc.getTextureManager().destroyTexture(entry.textureId);
               } catch (Exception e) {
                  Musify.LOGGER.debug("Failed to release texture", e);
               }
            });
         }
      }
   }

   public void clearAll(boolean includeDisk) {
      this.cacheLock.lock();

      try {
         for(CacheEntry entry : this.memoryCache.values()) {
            this.scheduleTextureCleanup(entry);
         }

         this.memoryCache.clear();
      } finally {
         this.cacheLock.unlock();
      }

      this.loadingArt.clear();
      if (includeDisk) {
         ExecutorManager.getInstance().submit(() -> {
            try {
               File[] files = this.diskCachePath.toFile().listFiles();
               if (files != null) {
                  for(File file : files) {
                     file.delete();
                  }
               }

               Musify.LOGGER.info("Cleared disk cache");
            } catch (Exception e) {
               Musify.LOGGER.debug("Failed to clear disk cache", e);
            }
         });
      }
   }

   public int getMemoryCacheSize() {
      this.cacheLock.lock();

      int var1;
      try {
         var1 = this.memoryCache.size();
      } finally {
         this.cacheLock.unlock();
      }

      return var1;
   }

   public String getStats() {
      int memoryCacheSize = this.getMemoryCacheSize();
      int diskCacheSize = 0;
      long diskBytes = 0L;

      try {
         File[] files = this.diskCachePath.toFile().listFiles((dir, name) -> name.endsWith(".png"));
         if (files != null) {
            diskCacheSize = files.length;

            for(File f : files) {
               diskBytes += f.length();
            }
         }
      } catch (Exception var10) {
      }

      return String.format("Memory: %d/%d, Disk: %d/%d (%.1fMB)", memoryCacheSize, 20, diskCacheSize, 100, (double)diskBytes / (double)1048576.0F);
   }

   private int extractDominantColor(BufferedImage img) {
      int width = img.getWidth();
      int height = img.getHeight();
      Map<Integer, double[]> colorBuckets = new HashMap();
      int sampleStep = Math.max(1, Math.min(width, height) / 80);

      for(int y = 0; y < height; y += sampleStep) {
         for(int x = 0; x < width; x += sampleStep) {
            int rgb = img.getRGB(x, y);
            int r = rgb >> 16 & 255;
            int g = rgb >> 8 & 255;
            int b = rgb & 255;
            float[] hsv = this.rgbToHsv(r, g, b);
            double pixelWeight = 0.5 + (double)hsv[1] * 0.5;
            int hBucket = (int)(hsv[0] / 20.0F) % 18;
            int sBucket = Math.min(3, (int)(hsv[1] * 4.0F));
            int vBucket = Math.min(3, (int)(hsv[2] * 4.0F));
            int quantized = hBucket << 8 | sBucket << 4 | vBucket;
            float finalSat = hsv[1];
            float finalBright = hsv[2];
            colorBuckets.compute(quantized, (k, v) -> {
               if (v == null) {
                  return new double[]{pixelWeight, (double)r * pixelWeight, (double)g * pixelWeight, (double)b * pixelWeight, (double)finalSat * pixelWeight, (double)finalBright * pixelWeight};
               } else {
                  v[0] += pixelWeight;
                  v[1] += (double)r * pixelWeight;
                  v[2] += (double)g * pixelWeight;
                  v[3] += (double)b * pixelWeight;
                  v[4] += (double)finalSat * pixelWeight;
                  v[5] += (double)finalBright * pixelWeight;
                  return v;
               }
            });
         }
      }

      if (colorBuckets.isEmpty()) {
         return -14829228;
      } else {
         List<double[]> candidates = new ArrayList(colorBuckets.values());
         candidates.sort((a, bx) -> Double.compare(bx[0], a[0]));
         double[] bestBucket = null;
         double bestScore = -1.0;

         for(int i = 0; i < Math.min(10, candidates.size()); ++i) {
            double[] bucket = candidates.get(i);
            double avgBright = bucket[5] / bucket[0];
            if (avgBright >= 0.25) {
               double score = bucket[0] / (1.0 + (double)i * 0.3) * (0.7 + bucket[4] / bucket[0] * 0.3);
               if (score > bestScore) {
                  bestScore = score;
                  bestBucket = bucket;
               }
            }
         }

         if (bestBucket == null) {
            for(double[] bucket : candidates) {
               if (bestBucket == null || bucket[5] / bucket[0] > bestBucket[5] / bestBucket[0]) {
                  bestBucket = bucket;
               }
            }
         }

         if (bestBucket == null) {
            return -14829228;
         } else {
            int r = Math.max(0, Math.min(255, (int)(bestBucket[1] / bestBucket[0])));
            int g = Math.max(0, Math.min(255, (int)(bestBucket[2] / bestBucket[0])));
            int b = Math.max(0, Math.min(255, (int)(bestBucket[3] / bestBucket[0])));
            float[] hsv = this.rgbToHsv(r, g, b);
            if (hsv[1] >= 0.15F && hsv[2] < 0.6F) {
               hsv[1] = Math.min(1.0F, hsv[1] * (1.0F + (1.0F - hsv[2] / 0.6F) * 0.5F));
            }

            if (hsv[2] < 0.25F) {
               hsv[2] = 0.25F + hsv[2] * 0.5F;
            }

            int[] adjusted = this.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            return 0xFF000000 | adjusted[0] << 16 | adjusted[1] << 8 | adjusted[2];
         }
      }
   }

   private float[] rgbToHsv(int r, int g, int b) {
      float rf = (float)r / 255.0F;
      float gf = (float)g / 255.0F;
      float bf = (float)b / 255.0F;
      float max = Math.max(rf, Math.max(gf, bf));
      float min = Math.min(rf, Math.min(gf, bf));
      float delta = max - min;
      float h = 0.0F;
      float s = max == 0.0F ? 0.0F : delta / max;
      if (delta != 0.0F) {
         if (max == rf) {
            h = (gf - bf) / delta % 6.0F;
         } else if (max == gf) {
            h = (bf - rf) / delta + 2.0F;
         } else {
            h = (rf - gf) / delta + 4.0F;
         }

         h *= 60.0F;
         if (h < 0.0F) {
            h += 360.0F;
         }
      }

      return new float[]{h, s, max};
   }

   private int[] hsvToRgb(float h, float s, float v) {
      float c = v * s;
      float x = c * (1.0F - Math.abs(h / 60.0F % 2.0F - 1.0F));
      float m = v - c;
      float rf;
      float gf;
      float bf;
      if (h < 60.0F) {
         rf = c;
         gf = x;
         bf = 0.0F;
      } else if (h < 120.0F) {
         rf = x;
         gf = c;
         bf = 0.0F;
      } else if (h < 180.0F) {
         rf = 0.0F;
         gf = c;
         bf = x;
      } else if (h < 240.0F) {
         rf = 0.0F;
         gf = x;
         bf = c;
      } else if (h < 300.0F) {
         rf = x;
         gf = 0.0F;
         bf = c;
      } else {
         rf = c;
         gf = 0.0F;
         bf = x;
      }

      return new int[]{Math.round((rf + m) * 255.0F), Math.round((gf + m) * 255.0F), Math.round((bf + m) * 255.0F)};
   }

   @Environment(EnvType.CLIENT)
   public static class CacheEntry {
      public final Identifier textureId;
      public final int[] dimensions;
      public final int dominantColor;
      public final long createdAt;
      public final String url;
      private volatile long lastAccessTime;

      public CacheEntry(Identifier textureId, int[] dimensions, int dominantColor, String url) {
         this.textureId = textureId;
         this.dimensions = dimensions;
         this.dominantColor = dominantColor;
         this.url = url;
         this.createdAt = System.currentTimeMillis();
         this.lastAccessTime = this.createdAt;
      }

      public void touch() {
         this.lastAccessTime = System.currentTimeMillis();
      }

      public long getLastAccessTime() {
         return this.lastAccessTime;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class DiskCacheMetadata {
      String url;
      int dominantColor;
      int width;
      int height;
      long createdAt;
      long fileSize;
   }
}