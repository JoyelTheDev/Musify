package dev.joyel.musify.client;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class ColorPickerScreen extends Screen {
   private static final int SWATCH_SIZE = 32;
   private static final int SWATCH_SPACING = 8;
   private static final int PRESET_COLUMNS = 5;
   private final Screen parent;
   private final Consumer<Integer> onColorSelected;
   private final Consumer<Integer> onPresetSelected;
   private final int initialColor;
   private int selectedColor;
   private Integer selectedPresetIndex = null;
   private TextFieldWidget hexInput;
   private int hoveredSwatch = -1;
   private boolean initialized = false;

   public ColorPickerScreen(Screen parent, int initialColor, Consumer<Integer> onColorSelected, Consumer<Integer> onPresetSelected) {
      super(Text.translatable("Color Picker"));
      this.parent = parent;
      this.onColorSelected = onColorSelected;
      this.onPresetSelected = onPresetSelected;
      this.initialColor = initialColor;
      this.selectedColor = initialColor;
      HUDSettings settings = HUDSettings.getInstance();
      if (!settings.isUsingCustomAccentColor()) {
         this.selectedPresetIndex = settings.getAccentColorIndex();
      }
   }

   @Override
   protected void init() {
      super.init();
      this.initialized = false;
      int centerX = this.width / 2;
      int contentY = Math.max(30, this.height / 2 - 120);
      int gridWidth = 192;
      int swatchStartX = centerX - gridWidth / 2;
      int swatchStartY = contentY + 35;

      for (int i = 0; i < HUDSettings.ACCENT_COLORS.length; ++i) {
         int row = i / PRESET_COLUMNS;
         int col = i % PRESET_COLUMNS;
         int x = swatchStartX + col * (SWATCH_SIZE + SWATCH_SPACING);
         int y = swatchStartY + row * (SWATCH_SIZE + SWATCH_SPACING);
         final int index = i;
         ButtonWidget button = ButtonWidget.builder(Text.literal(""), (buttonWidget) -> {
            this.selectedColor = this.getPresetColor(index);
            this.selectedPresetIndex = index;
            this.hexInput.setText(this.colorToHex(this.selectedColor));
            HUDSettings.getInstance().setPreviewColor(this.selectedColor);
         }).dimensions(x, y, SWATCH_SIZE, SWATCH_SIZE).build();
         button.setTooltip(Tooltip.of(Text.translatable(HUDSettings.ACCENT_COLOR_NAMES[index])));
         this.addDrawableChild(button);
      }

      int rowCount = (HUDSettings.ACCENT_COLORS.length + PRESET_COLUMNS - 1) / PRESET_COLUMNS;
      int previewY = swatchStartY + rowCount * (SWATCH_SIZE + SWATCH_SPACING) + 25;
      int hexInputY = previewY + 80;
      this.hexInput = new TextFieldWidget(this.textRenderer, centerX - 60, hexInputY, 120, 20, Text.translatable("Hex Color"));
      this.hexInput.setMaxLength(7);
      this.hexInput.setText(this.colorToHex(this.selectedColor));
      this.hexInput.setChangedListener(this::onHexInputChanged);
      this.addDrawableChild(this.hexInput);
      this.initialized = true;
      
      this.addDrawableChild(ButtonWidget.builder(Text.literal(Formatting.GREEN + "Confirm"), (button) -> {
         HUDSettings.getInstance().clearPreviewColor();
         if (this.selectedPresetIndex != null) {
            this.onPresetSelected.accept(this.selectedPresetIndex);
         } else {
            this.onColorSelected.accept(this.selectedColor);
         }
         MinecraftClient.getInstance().setScreen(new CustomizationScreen());
      }).dimensions(centerX - 100, hexInputY + 30, 95, 20).build());
      
      this.addDrawableChild(ButtonWidget.builder(Text.literal(Formatting.RED + "Cancel"), (button) -> {
         HUDSettings.getInstance().clearPreviewColor();
         MinecraftClient.getInstance().setScreen(new CustomizationScreen());
      }).dimensions(centerX + 5, hexInputY + 30, 95, 20).build());
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
      super.render(context, mouseX, mouseY, partialTick);
      int centerX = this.width / 2;
      int contentY = Math.max(30, this.height / 2 - 120);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, contentY, 0xFFFFFFFF);
      int gridWidth = 192;
      int swatchStartX = centerX - gridWidth / 2;
      int swatchStartY = contentY + 35;
      context.drawCenteredTextWithShadow(this.textRenderer, Formatting.GRAY + "Preset Colors", centerX, contentY + 20, 0xFFAAAAAA);
      this.hoveredSwatch = -1;

      for (int i = 0; i < HUDSettings.ACCENT_COLORS.length; ++i) {
         int row = i / PRESET_COLUMNS;
         int col = i % PRESET_COLUMNS;
         int x = swatchStartX + col * (SWATCH_SIZE + SWATCH_SPACING);
         int y = swatchStartY + row * (SWATCH_SIZE + SWATCH_SPACING);
         boolean isHovered = mouseX >= x && mouseX <= x + SWATCH_SIZE && mouseY >= y && mouseY <= y + SWATCH_SIZE;
         boolean isSelected = this.selectedPresetIndex != null && this.selectedPresetIndex == i;
         if (isHovered) {
            this.hoveredSwatch = i;
         }

         this.drawColorSwatch(context, x, y, SWATCH_SIZE, this.getPresetColor(i), isSelected, isHovered);
      }

      int rowCount = (HUDSettings.ACCENT_COLORS.length + PRESET_COLUMNS - 1) / PRESET_COLUMNS;
      int previewY = swatchStartY + rowCount * (SWATCH_SIZE + SWATCH_SPACING) + 25;
      context.drawCenteredTextWithShadow(this.textRenderer, Formatting.GRAY + "Current Color", centerX, previewY, 0xFFAAAAAA);
      this.drawColorSwatch(context, centerX - 20, previewY + 15, 40, this.selectedColor, true, false);
      context.drawCenteredTextWithShadow(this.textRenderer, Formatting.GRAY + "Hex Code (#RRGGBB)", centerX, previewY + 68, 0xFFAAAAAA);
   }

   private void drawColorSwatch(DrawContext context, int x, int y, int size, int color, boolean selected, boolean hovered) {
      int borderColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFAAAAAA : 0xFF555555);
      int borderWidth = selected ? 2 : 1;
      context.fill(x - borderWidth, y - borderWidth, x + size + borderWidth, y + size + borderWidth, borderColor);
      context.fill(x, y, x + size, y + size, color);
      if ((color & 0xFF000000) != 0xFF000000) {
         this.drawCheckerboard(context, x, y, size);
      }
   }

   private void drawCheckerboard(DrawContext context, int x, int y, int size) {
      int checkSize = 4;

      for (int cy = 0; cy < size; cy += checkSize) {
         for (int cx = 0; cx < size; cx += checkSize) {
            boolean isDark = ((cx / checkSize) + (cy / checkSize)) % 2 == 0;
            int color = isDark ? 0xFF808080 : 0xFFC0C0C0;
            context.fill(x + cx, y + cy, x + Math.min(cx + checkSize, size), y + Math.min(cy + checkSize, size), color);
         }
      }
   }

   private void onHexInputChanged(String hex) {
      if (this.initialized) {
         try {
            if (hex.startsWith("#")) {
               hex = hex.substring(1);
            }

            if (hex.length() == 6) {
               int rgb = Integer.parseInt(hex, 16);
               this.selectedColor = 0xFF000000 | rgb;
               this.selectedPresetIndex = null;
               HUDSettings.getInstance().setPreviewColor(this.selectedColor);
            }
         } catch (NumberFormatException var3) {
            // Invalid hex format, ignore
         }
      }
   }

   private String colorToHex(int color) {
      return String.format("#%06X", color & 0xFFFFFF);
   }

   private int getPresetColor(int index) {
      return index == 0 ? HUDSettings.getInstance().getAlbumDynamicColor() : HUDSettings.ACCENT_COLORS[index];
   }

   @Override
   public void close() {
      HUDSettings.getInstance().clearPreviewColor();
      MinecraftClient.getInstance().setScreen(new CustomizationScreen());
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }
}
