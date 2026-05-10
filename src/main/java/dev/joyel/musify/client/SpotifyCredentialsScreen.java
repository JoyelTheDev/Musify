package dev.joyel.musify.client;

import java.awt.Desktop;
import java.net.URI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class SpotifyCredentialsScreen extends Screen {
   private static final int CONTENT_WIDTH = 280;
   private static final int BUTTON_HEIGHT = 20;
   private static final int FIELD_HEIGHT = 20;
   private static final int ROW_SPACING = 24;
   private static final int ACCENT_COLOR = 0xFF1E3A8A;
   private static final int LABEL_COLOR = 0xFFAAAAAA;
   private static final int WARNING_COLOR = 0xFFAA0000;
   private final Screen parent;
   private TextFieldWidget clientIdField;
   private TextFieldWidget clientSecretField;

   public SpotifyCredentialsScreen(Screen parent) {
      super(Text.translatable("Spotify App Configuration"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      super.init();
      ConfigManager config = ConfigManager.getInstance();
      int centerX = this.width / 2;
      int currentY = this.height / 2 - 80;
      currentY += 30;
      
      this.clientIdField = new TextFieldWidget(this.textRenderer, centerX - 140, currentY, 280, 20, Text.translatable("Client ID"));
      this.clientIdField.setMaxLength(64);
      this.clientIdField.setText(config.getSpotifyClientId());
      this.clientIdField.setPlaceholder(Text.translatable("Enter your Spotify Client ID"));
      this.addDrawableChild(this.clientIdField);
      
      currentY += 39;
      this.clientSecretField = new TextFieldWidget(this.textRenderer, centerX - 140, currentY, 280, 20, Text.translatable("Client Secret"));
      this.clientSecretField.setMaxLength(64);
      this.clientSecretField.setText(config.getSpotifyClientSecret());
      this.clientSecretField.setPlaceholder(Text.translatable("Enter your Spotify Client Secret"));
      this.addDrawableChild(this.clientSecretField);
      
      currentY += 49;
      this.addDrawableChild(ButtonWidget.builder(Text.literal(Formatting.GREEN + "Save Credentials"), (button) -> {
         String clientId = this.clientIdField.getText().trim();
         String clientSecret = this.clientSecretField.getText().trim();
         config.setSpotifyCredentials(clientId, clientSecret);
         if (!clientId.isEmpty() && !clientSecret.isEmpty()) {
            config.logout();
         }
         MinecraftClient.getInstance().setScreen(this.parent);
      }).dimensions(centerX - 140, currentY, 137, BUTTON_HEIGHT)
      .tooltip(ButtonWidget.TooltipSupplier.create(Text.translatable("Save your Spotify app credentials")))
      .build());
      
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), (button) -> 
         MinecraftClient.getInstance().setScreen(this.parent))
         .dimensions(centerX + 3, currentY, 137, BUTTON_HEIGHT)
         .build());
      
      currentY += 29;
      this.addDrawableChild(ButtonWidget.builder(Text.literal(Formatting.BLUE + "Open Spotify Developer Dashboard"), (button) -> 
         this.openBrowser("https://developer.spotify.com/dashboard"))
         .dimensions(centerX - 140, currentY, CONTENT_WIDTH, BUTTON_HEIGHT)
         .tooltip(ButtonWidget.TooltipSupplier.create(Text.translatable("Create a new Spotify app to get your credentials")))
         .build());
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      int centerX = this.width / 2;
      int currentY = this.height / 2 - 80;
      
      context.drawCenteredTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.GREEN + Formatting.BOLD + "Spotify App Configuration"), 
         centerX, currentY - 25, ACCENT_COLOR);
      context.drawCenteredTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.GRAY + "To use Musify, you need your own Spotify app."), 
         centerX, currentY - 8, LABEL_COLOR);
      
      currentY += 18;
      context.drawTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.WHITE + "Client ID:"), 
         centerX - 140, currentY, 0xFFFFFFFF);
      
      currentY += 39;
      context.drawTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.WHITE + "Client Secret:"), 
         centerX - 140, currentY, 0xFFFFFFFF);
      
      int instructionY = this.height - 65;
      context.drawCenteredTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.YELLOW + "Setup Instructions:"), 
         centerX, instructionY, WARNING_COLOR);
      context.drawCenteredTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.GRAY + "1. Go to Spotify Developer Dashboard"), 
         centerX, instructionY + 12, LABEL_COLOR);
      context.drawCenteredTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.GRAY + "2. Create a new app with Redirect URI: " + Formatting.WHITE + "http://127.0.0.1:25566/callback"), 
         centerX, instructionY + 24, LABEL_COLOR);
      context.drawCenteredTextWithShadow(this.textRenderer, 
         Text.literal(Formatting.GRAY + "3. Copy your Client ID and Client Secret above"), 
         centerX, instructionY + 36, LABEL_COLOR);
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
      } catch (Exception e) {
         try {
            Desktop.getDesktop().browse(URI.create(url));
         } catch (Exception ex) {
            // Fallback failed, ignore
         }
      }
   }

   @Override
   public void close() {
      MinecraftClient.getInstance().setScreen(this.parent);
   }
}