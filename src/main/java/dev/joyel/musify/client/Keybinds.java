package dev.joyel.musify.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class Keybinds {
   private static KeyBinding OPEN_CUSTOMIZATION_KB;
   private static KeyBinding PLAY_PAUSE_KB;
   private static KeyBinding NEXT_TRACK_KB;
   private static KeyBinding PREV_TRACK_KB;

   private Keybinds() {
   }

   public static void register() {
      KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("musify", "controls"));
      OPEN_CUSTOMIZATION_KB = KeyBindingHelper.registerKeyBinding(new KeyBinding(
         "key.musify.open_customization", 
         InputUtil.Type.KEYSYM, 
         GLFW.GLFW_KEY_K, 
         category
      ));
      PLAY_PAUSE_KB = KeyBindingHelper.registerKeyBinding(new KeyBinding(
         "key.musify.play_pause", 
         InputUtil.Type.KEYSYM, 
         GLFW.GLFW_KEY_P, 
         category
      ));
      NEXT_TRACK_KB = KeyBindingHelper.registerKeyBinding(new KeyBinding(
         "key.musify.next_track", 
         InputUtil.Type.KEYSYM, 
         GLFW.GLFW_KEY_N, 
         category
      ));
      PREV_TRACK_KB = KeyBindingHelper.registerKeyBinding(new KeyBinding(
         "key.musify.prev_track", 
         InputUtil.Type.KEYSYM, 
         GLFW.GLFW_KEY_B, 
         category
      ));
   }

   public static boolean isOpenCustomizationPressed() {
      return OPEN_CUSTOMIZATION_KB != null && OPEN_CUSTOMIZATION_KB.isPressed();
   }

   public static boolean isPlayPausePressed() {
      return PLAY_PAUSE_KB != null && PLAY_PAUSE_KB.isPressed();
   }

   public static boolean isNextTrackPressed() {
      return NEXT_TRACK_KB != null && NEXT_TRACK_KB.isPressed();
   }

   public static boolean isPrevTrackPressed() {
      return PREV_TRACK_KB != null && PREV_TRACK_KB.isPressed();
   }
}