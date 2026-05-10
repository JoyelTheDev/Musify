package aqys.Musify.client;

import java.util.Stack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class UndoRedoManager {
   private static final int MAX_HISTORY_SIZE = 50;
   private final Stack<SettingsSnapshot> undoStack = new Stack();
   private final Stack<SettingsSnapshot> redoStack = new Stack();
   private final HUDSettings settings;

   public UndoRedoManager(HUDSettings settings) {
      this.settings = settings;
   }

   public void pushState() {
      SettingsSnapshot snapshot = new SettingsSnapshot(this.settings);
      this.undoStack.push(snapshot);
      this.redoStack.clear();
      if (this.undoStack.size() > 50) {
         this.undoStack.remove(0);
      }

   }

   public boolean undo() {
      if (!this.canUndo()) {
         return false;
      } else {
         SettingsSnapshot currentState = new SettingsSnapshot(this.settings);
         this.redoStack.push(currentState);
         SettingsSnapshot previousState = (SettingsSnapshot)this.undoStack.pop();
         previousState.applyTo(this.settings);
         ConfigManager.getInstance().saveHUDSettings(this.settings);
         return true;
      }
   }

   public boolean redo() {
      if (!this.canRedo()) {
         return false;
      } else {
         SettingsSnapshot currentState = new SettingsSnapshot(this.settings);
         this.undoStack.push(currentState);
         SettingsSnapshot nextState = (SettingsSnapshot)this.redoStack.pop();
         nextState.applyTo(this.settings);
         ConfigManager.getInstance().saveHUDSettings(this.settings);
         return true;
      }
   }

   public boolean canUndo() {
      return !this.undoStack.isEmpty();
   }

   public boolean canRedo() {
      return !this.redoStack.isEmpty();
   }

   public void clear() {
      this.undoStack.clear();
      this.redoStack.clear();
   }

   public int getUndoCount() {
      return this.undoStack.size();
   }

   public int getRedoCount() {
      return this.redoStack.size();
   }
}
