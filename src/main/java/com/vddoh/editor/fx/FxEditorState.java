package com.vddoh.editor.fx;

import com.vddoh.editor.EditorWorkspace;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class FxEditorState {

  private final ObjectProperty<EditorWorkspace> workspace = new SimpleObjectProperty<>();
  private final StringProperty status = new SimpleStringProperty("Choose a VDDOH JAR to begin.");
  private final BooleanProperty patchResistanceOverflow = new SimpleBooleanProperty(false);

  public ObjectProperty<EditorWorkspace> workspaceProperty() {
    return workspace;
  }

  public EditorWorkspace workspace() {
    return workspace.get();
  }

  public void workspace(EditorWorkspace workspace) {
    this.workspace.set(workspace);
  }

  public StringProperty statusProperty() {
    return status;
  }

  public void status(String message) {
    status.set(message);
  }

  public BooleanProperty patchResistanceOverflowProperty() {
    return patchResistanceOverflow;
  }

  public boolean patchResistanceOverflow() {
    return patchResistanceOverflow.get();
  }

  public void patchResistanceOverflow(boolean patchResistanceOverflow) {
    this.patchResistanceOverflow.set(patchResistanceOverflow);
  }
}
