package dev.joyel.musify.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class CustomizationScreen extends Screen {
   private static final int CONTENT_WIDTH = 240;
   private static final int BUTTON_HEIGHT = 20;
   private static final int SMALL_BUTTON_WIDTH = 115;
   private static final int ROW_SPACING = 24;
   private static final int SECTION_SPACING = 12;
   private static final int ACCENT_COLOR = 0xFF1E3A8A;
   private static final int SECTION_HEADER_COLOR = 0xFF1E3A8A;
   private static final int LABEL_COLOR = 0xFFAAAAAA;
   private final HUDSettings settings = HUDSettings.getInstance();
   private final UndoRedoManager undoRedoManager;
   private boolean wasLoggedIn;
   private int tickCounter = 0;
   private ButtonWidget undoButton;
   private ButtonWidget redoButton;

   public CustomizationScreen() {
      super(Text.translatable("screen.musify.customization"));
      this.undoRedoManager = new UndoRedoManager(this.settings);
      ConfigManager config = ConfigManager.getInstance();
      this.wasLoggedIn = config.isLoggedIn() && !config.isTokenExpired();
   }

   @Override
   protected void init() {
      super.init();
      float guiScale = Math.min(1.0F, (float)this.height / 480.0F);
      int scaledContentWidth = (int)(240.0F * guiScale);
      int scaledButtonHeight = (int)(20.0F * guiScale);
      int scaledSmallButtonWidth = (int)(115.0F * guiScale);
      int scaledRowSpacing = (int)(24.0F * guiScale);
      int scaledSectionSpacing = (int)(12.0F * guiScale);
      int centerX = this.width / 2;
      int leftCol = centerX - scaledContentWidth / 2;
      int rightCol = centerX + scaledContentWidth / 2 - scaledSmallButtonWidth;
      ConfigManager config = ConfigManager.getInstance();
      boolean isLoggedIn = config.isLoggedIn();
      boolean isExpired = config.isTokenExpired();
      if (isLoggedIn && !isExpired) {
         this.initSettingsUI(leftCol, rightCol, centerX, scaledContentWidth, scaledButtonHeight, scaledSmallButtonWidth, scaledRowSpacing, scaledSectionSpacing, guiScale);
      } else {
         this.initLoginUI(centerX, scaledContentWidth, scaledButtonHeight, scaledRowSpacing);
      }
   }

   private void initLoginUI(int centerX, int scaledContentWidth, int scaledButtonHeight, int scaledRowSpacing) {
      int currentY = this.height / 2 - 50;
      ConfigManager config = ConfigManager.getInstance();
      boolean hasCredentials = config.hasCustomSpotifyCredentials();
      ButtonWidget configButton = ButtonWidget.builder(Text.literal("§e⚙ Configure Spotify App" + (hasCredentials ? " §a✓" : "")), (button) -> 
         MinecraftClient.getInstance().setScreen(new SpotifyCredentialsScreen(this)))
         .dimensions(centerX - scaledContentWidth / 2, currentY, scaledContentWidth, scaledButtonHeight)
         .build();
      configButton.setTooltip(Tooltip.of(Text.translatable("Set up your own Spotify app credentials (required for most users)")));
      this.addDrawableChild(configButton);
      
      currentY += scaledRowSpacing;
      ButtonWidget connectButton = ButtonWidget.builder(Text.literal("§a▶ Connect with Spotify"), (button) -> {
         SpotifyApiClient.getInstance().startAuthorization();
         button.setMessage(Text.literal("§eOpening browser..."));
      }).dimensions(centerX - scaledContentWidth / 2, currentY, scaledContentWidth, scaledButtonHeight).build();
      connectButton.setTooltip(Tooltip.of(Text.translatable("Connect your Spotify account")));
      this.addDrawableChild(connectButton);
      
      currentY += scaledRowSpacing + 20;
      this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), (button) -> this.close())
         .dimensions(centerX - scaledContentWidth / 2, currentY, scaledContentWidth, scaledButtonHeight)
         .build());
   }

   private void initSettingsUI(int leftCol, int rightCol, int centerX, int scaledContentWidth, int scaledButtonHeight, int scaledSmallButtonWidth, int scaledRowSpacing, int scaledSectionSpacing, float guiScale) {
      int currentY = (int)(52.0F * guiScale);
      currentY += (int)(14.0F * guiScale);
      
      ButtonWidget positionButton = ButtonWidget.builder(Text.literal("Position: §f" + this.settings.getPosition().getDisplayName()), (button) -> {
         this.pushUndo();
         this.settings.cyclePosition();
         button.setMessage(Text.literal("Position: §f" + this.settings.getPosition().getDisplayName()));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledContentWidth, scaledButtonHeight).build();
      positionButton.setTooltip(Tooltip.of(Text.literal("§7Click to cycle HUD position")));
      this.addDrawableChild(positionButton);
      
      currentY += scaledRowSpacing;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Album Art: " + this.toggleText(this.settings.isShowAlbumArt())), (button) -> {
         this.pushUndo();
         this.settings.toggleAlbumArt();
         button.setMessage(Text.literal("Album Art: " + this.toggleText(this.settings.isShowAlbumArt())));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Artist: " + this.toggleText(this.settings.isShowArtist())), (button) -> {
         this.pushUndo();
         this.settings.toggleShowArtist();
         button.setMessage(Text.literal("Artist: " + this.toggleText(this.settings.isShowArtist())));
         this.saveSettings();
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Progress: " + this.toggleText(this.settings.isShowProgressBar())), (button) -> {
         this.pushUndo();
         this.settings.toggleProgressBar();
         button.setMessage(Text.literal("Progress: " + this.toggleText(this.settings.isShowProgressBar())));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Time: " + this.toggleText(this.settings.isShowTimeStamps())), (button) -> {
         this.pushUndo();
         this.settings.toggleShowTimeStamps();
         button.setMessage(Text.literal("Time: " + this.toggleText(this.settings.isShowTimeStamps())));
         this.saveSettings();
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Compact: " + this.toggleText(this.settings.isCompactMode())), (button) -> {
         this.pushUndo();
         this.settings.toggleCompactMode();
         button.setMessage(Text.literal("Compact: " + this.toggleText(this.settings.isCompactMode())));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto-Hide: " + this.toggleText(this.settings.isAutoHideInMenus())), (button) -> {
         this.pushUndo();
         this.settings.toggleAutoHideInMenus();
         button.setMessage(Text.literal("Auto-Hide: " + this.toggleText(this.settings.isAutoHideInMenus())));
         this.saveSettings();
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing + scaledSectionSpacing;
      currentY += (int)(14.0F * guiScale);
      this.addDrawableChild(new OpacitySlider(leftCol, currentY, scaledContentWidth, scaledButtonHeight, this.settings.getHudOpacity(), guiScale));
      currentY += scaledRowSpacing;
      this.addDrawableChild(new ScaleSlider(leftCol, currentY, scaledContentWidth, scaledButtonHeight, this.settings.getHudScale(), guiScale));
      currentY += scaledRowSpacing;
      
      ButtonWidget accentButton = ButtonWidget.builder(Text.literal("Accent Menu"), (button) -> 
         MinecraftClient.getInstance().setScreen(new ColorPickerScreen(this, this.settings.getAccentColor(), 
            (color) -> {
               this.pushUndo();
               this.settings.setCustomAccentColor(color);
               this.saveSettings();
            }, 
            (presetIndex) -> {
               this.pushUndo();
               this.settings.setAccentColorIndex(presetIndex);
               this.saveSettings();
            })))
         .dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build();
      accentButton.setTooltip(Tooltip.of(Text.literal("§7Click to pick a color")));
      this.addDrawableChild(accentButton);
      
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Shadow: " + this.toggleText(this.settings.isTextShadow())), (button) -> {
         this.pushUndo();
         this.settings.toggleTextShadow();
         button.setMessage(Text.literal("Shadow: " + this.toggleText(this.settings.isTextShadow())));
         this.saveSettings();
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Bar: §f" + this.settings.getProgressBarStyle().getDisplayName()), (button) -> {
         this.pushUndo();
         this.settings.cycleProgressBarStyle();
         button.setMessage(Text.literal("Bar: §f" + this.settings.getProgressBarStyle().getDisplayName()));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Smooth: " + this.toggleText(this.settings.isSmoothProgress())), (button) -> {
         this.pushUndo();
         this.settings.toggleSmoothProgress();
         button.setMessage(Text.literal("Smooth: " + this.toggleText(this.settings.isSmoothProgress())));
         this.saveSettings();
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing + scaledSectionSpacing;
      currentY += (int)(14.0F * guiScale);
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Lyrics: " + this.toggleText(this.settings.isLyricsEnabled())), (button) -> {
         this.pushUndo();
         this.settings.toggleLyrics();
         button.setMessage(Text.literal("Lyrics: " + this.toggleText(this.settings.isLyricsEnabled())));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      this.addDrawableChild(new LyricsLinesSlider(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight, this.settings.getLyricsMaxLines(), guiScale));
      currentY += scaledRowSpacing;
      this.addDrawableChild(new LyricsFontSizeSlider(leftCol, currentY, scaledContentWidth, scaledButtonHeight, this.settings.getLyricsFontSize(), guiScale));
      currentY += scaledRowSpacing;
      this.addDrawableChild(new LyricsOpacitySlider(leftCol, currentY, scaledContentWidth, scaledButtonHeight, this.settings.getLyricsBackgroundOpacity(), guiScale));
      currentY += scaledRowSpacing;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Position: §f" + this.settings.getLyricsPosition().getDisplayName()), (button) -> {
         this.pushUndo();
         this.settings.cycleLyricsPosition();
         button.setMessage(Text.literal("Position: §f" + this.settings.getLyricsPosition().getDisplayName()));
         this.saveSettings();
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Match Color: " + this.toggleText(this.settings.isLyricsKaraokeMode())), (button) -> {
         this.pushUndo();
         this.settings.toggleLyricsKaraokeMode();
         button.setMessage(Text.literal("Match Color: " + this.toggleText(this.settings.isLyricsKaraokeMode())));
         this.saveSettings();
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing + scaledSectionSpacing + (int)(4.0F * guiScale);
      this.addDrawableChild(ButtonWidget.builder(Text.literal("§cDisconnect Spotify"), (button) -> {
         ConfigManager.getInstance().logout();
         SpotifyApiClient.getInstance().clearCurrentTrack();
         MinecraftClient.getInstance().setScreen(new CustomizationScreen());
      }).dimensions(leftCol, currentY, scaledContentWidth, scaledButtonHeight).build());
      currentY += scaledRowSpacing;
      this.undoButton = ButtonWidget.builder(Text.literal("↶ Undo"), (button) -> {
         if (this.undoRedoManager.undo()) {
            this.clearAndInit();
         }
      }).dimensions(leftCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build();
      this.undoButton.active = this.undoRedoManager.canUndo();
      this.addDrawableChild(this.undoButton);
      this.redoButton = ButtonWidget.builder(Text.literal("↷ Redo"), (button) -> {
         if (this.undoRedoManager.redo()) {
            this.clearAndInit();
         }
      }).dimensions(rightCol, currentY, scaledSmallButtonWidth, scaledButtonHeight).build();
      this.redoButton.active = this.undoRedoManager.canRedo();
      this.addDrawableChild(this.redoButton);
      currentY += scaledRowSpacing;
      this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), (button) -> this.close())
         .dimensions(leftCol, currentY, scaledContentWidth, scaledButtonHeight)
         .build());
   }

   private String toggleText(boolean enabled) {
      return enabled ? "§aON" : "§7OFF";
   }

   private void pushUndo() {
      this.undoRedoManager.pushState();
      this.updateUndoRedoButtons();
   }

   private void updateUndoRedoButtons() {
      if (this.undoButton != null) {
         this.undoButton.active = this.undoRedoManager.canUndo();
      }
      if (this.redoButton != null) {
         this.redoButton.active = this.undoRedoManager.canRedo();
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
      super.render(context, mouseX, mouseY, partialTick);
      float guiScale = Math.min(1.0F, (float)this.height / 480.0F);
      int scaledContentWidth = (int)(240.0F * guiScale);
      int scaledRowSpacing = (int)(24.0F * guiScale);
      int scaledSectionSpacing = (int)(12.0F * guiScale);
      int centerX = this.width / 2;
      int leftCol = centerX - scaledContentWidth / 2;
      ConfigManager config = ConfigManager.getInstance();
      boolean isLoggedIn = config.isLoggedIn() && !config.isTokenExpired();
      context.fill(centerX - (int)(60.0F * guiScale), (int)(14.0F * guiScale), centerX + (int)(60.0F * guiScale), (int)(16.0F * guiScale), 0xFF1E3A8A);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, (int)(20.0F * guiScale), -1);
      if (!isLoggedIn) {
         String message = config.isTokenExpired() ? "§eYour session has expired" : "§7Connect to Spotify to use Musify";
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message), centerX, this.height / 2 - (int)(50.0F * guiScale), 0xFFFFFF);
      } else {
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Customize your HUD"), centerX, (int)(34.0F * guiScale), 0xFFAAAAAA);
         int currentY = (int)(52.0F * guiScale);
         context.drawTextWithShadow(this.textRenderer, Text.literal("§a§l▸ §r§aDisplay"), leftCol, currentY, 0xFF1E3A8A);
         currentY += (int)(14.0F * guiScale) + scaledRowSpacing * 4 + scaledSectionSpacing;
         context.drawTextWithShadow(this.textRenderer, Text.literal("§a§l▸ §r§aAppearance"), leftCol, currentY, 0xFF1E3A8A);
         currentY += (int)(14.0F * guiScale) + scaledRowSpacing * 4 + scaledSectionSpacing;
         context.drawTextWithShadow(this.textRenderer, Text.literal("§a§l▸ §r§aLyrics"), leftCol, currentY, 0xFF1E3A8A);
      }
   }

   @Override
   public void close() {
      this.saveSettings();
      super.close();
   }

   private void saveSettings() {
      ConfigManager.getInstance().saveHUDSettings(this.settings);
   }

   @Override
   public void tick() {
      super.tick();
      ++this.tickCounter;
      if (this.tickCounter % 20 == 0) {
         ConfigManager config = ConfigManager.getInstance();
         boolean isNowLoggedIn = config.isLoggedIn() && !config.isTokenExpired();
         if (isNowLoggedIn != this.wasLoggedIn) {
            this.wasLoggedIn = isNowLoggedIn;
            this.clearAndInit();
         }
      }
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   public static void open() {
      MinecraftClient.getInstance().setScreen(new CustomizationScreen());
   }

   @Environment(EnvType.CLIENT)
   private class OpacitySlider extends SliderWidget {
      public OpacitySlider(int x, int y, int w, int h, float val, float guiScale) {
         super(x, y, w, h, Text.literal(""), (MathHelper.clamp(val, 0.3f, 1.0F) - 0.3f) / 0.7f);
         this.updateMessage();
      }

      @Override
      protected void updateMessage() {
         double value = 0.3 + this.value * 0.7;
         this.setMessage(Text.literal("Opacity: §f" + Math.round(value * 100.0) + "%"));
      }

      @Override
      protected void applyValue() {
         CustomizationScreen.this.settings.setHudOpacity((float)(0.3 + this.value * 0.7));
         CustomizationScreen.this.saveSettings();
      }
   }

   @Environment(EnvType.CLIENT)
   private class ScaleSlider extends SliderWidget {
      public ScaleSlider(int x, int y, int w, int h, float val, float guiScale) {
         super(x, y, w, h, Text.literal(""), (MathHelper.clamp(val, 0.5F, 2.0F) - 0.5f) / 1.5f);
         this.updateMessage();
      }

      @Override
      protected void updateMessage() {
         double value = 0.5 + this.value * 1.5;
         this.setMessage(Text.literal("Scale: §f" + Math.round(value * 100.0) + "%"));
      }

      @Override
      protected void applyValue() {
         CustomizationScreen.this.settings.setHudScale((float)(0.5 + this.value * 1.5));
         CustomizationScreen.this.saveSettings();
      }
   }

   @Environment(EnvType.CLIENT)
   private class LyricsLinesSlider extends SliderWidget {
      public LyricsLinesSlider(int x, int y, int w, int h, int val, float guiScale) {
         super(x, y, w, h, Text.literal(""), (double)(MathHelper.clamp(val, 1, 4) - 1) / 3.0);
         this.updateMessage();
      }

      @Override
      protected void updateMessage() {
         this.setMessage(Text.literal("Lines: §f" + (1 + (int)Math.round(this.value * 3.0))));
      }

      @Override
      protected void applyValue() {
         CustomizationScreen.this.settings.setLyricsMaxLines(1 + (int)Math.round(this.value * 3.0));
         CustomizationScreen.this.saveSettings();
      }
   }

   @Environment(EnvType.CLIENT)
   private class LyricsFontSizeSlider extends SliderWidget {
      public LyricsFontSizeSlider(int x, int y, int w, int h, float val, float guiScale) {
         super(x, y, w, h, Text.literal(""), (MathHelper.clamp(val, 0.7F, 1.5F) - 0.7f) / 0.8f);
         this.updateMessage();
      }

      @Override
      protected void updateMessage() {
         double value = 0.7 + this.value * 0.8;
         this.setMessage(Text.literal("Lyrics Spacing: §f" + Math.round(value * 100.0) + "%"));
      }

      @Override
      protected void applyValue() {
         CustomizationScreen.this.settings.setLyricsFontSize((float)(0.7 + this.value * 0.8));
         CustomizationScreen.this.saveSettings();
      }
   }

   @Environment(EnvType.CLIENT)
   private class LyricsOpacitySlider extends SliderWidget {
      public LyricsOpacitySlider(int x, int y, int w, int h, float val, float guiScale) {
         super(x, y, w, h, Text.literal(""), (MathHelper.clamp(val, 0.3F, 1.0F) - 0.3f) / 0.7f);
         this.updateMessage();
      }

      @Override
      protected void updateMessage() {
         double value = 0.3 + this.value * 0.7;
         this.setMessage(Text.literal("Lyrics BG: §f" + Math.round(value * 100.0) + "%"));
      }

      @Override
      protected void applyValue() {
         CustomizationScreen.this.settings.setLyricsBackgroundOpacity((float)(0.3 + this.value * 0.7));
         CustomizationScreen.this.saveSettings();
      }
   }
}
