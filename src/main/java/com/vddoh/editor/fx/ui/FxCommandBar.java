package com.vddoh.editor.fx.ui;

import com.vddoh.editor.BuildResult;
import com.vddoh.editor.EditorLoadService;
import com.vddoh.editor.EditorPatchService;
import com.vddoh.editor.EditorWorkspace;
import com.vddoh.editor.fx.FxEditorState;
import java.io.File;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class FxCommandBar extends HBox {

  private final Stage owner;
  private final FxEditorState state;
  private final TextField inputJar = new TextField("jar/vddoh.jar");
  private final TextField outputJar = new TextField();
  private final CheckBox patchResistanceOverflow = new CheckBox("Patch resistance overflow");

  public FxCommandBar(Stage owner, FxEditorState state) {
    this.owner = owner;
    this.state = state;
    getStyleClass().add("command-bar");
    setPadding(new Insets(8));
    setSpacing(8);
    outputJar.setEditable(false);

    Button browse = new Button("...");
    browse.setOnAction(_ -> chooseInputJar());
    Button load = new Button("Load");
    load.setDefaultButton(true);
    load.setOnAction(_ -> loadSelectedJar(load));
    Button buildClassPatch = new Button("Build Class Patch");
    buildClassPatch.setOnAction(_ -> buildResistanceOverflowPatch(buildClassPatch));
    patchResistanceOverflow
        .selectedProperty()
        .bindBidirectional(state.patchResistanceOverflowProperty());
    patchResistanceOverflow.setDisable(true);

    HBox.setHgrow(inputJar, Priority.ALWAYS);
    HBox.setHgrow(outputJar, Priority.ALWAYS);
    getChildren()
        .addAll(
            new Label("Input JAR"),
            inputJar,
            browse,
            load,
            new Label("Output preview"),
            outputJar,
            patchResistanceOverflow,
            buildClassPatch);
  }

  private void chooseInputJar() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose VDDOH JAR");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));
    File chosen = chooser.showOpenDialog(owner);
    if (chosen != null) {
      inputJar.setText(chosen.toPath().toString());
    }
  }

  private void loadSelectedJar(Button load) {
    Path selected = Path.of(inputJar.getText().trim());
    Task<EditorWorkspace> task =
        new Task<>() {
          @Override
          protected EditorWorkspace call() throws Exception {
            return EditorLoadService.load(selected);
          }
        };
    load.disableProperty().bind(task.runningProperty());
    state.status("Loading " + selected + "...");
    task.setOnSucceeded(
        _ -> {
          EditorWorkspace workspace = task.getValue();
          state.workspace(workspace);
          outputJar.setText(workspace.outputJar().toString());
          updateResistanceOverflowControl(workspace);
          state.status(
              "Loaded %d items from %s. Resistance patch state: %s"
                  .formatted(
                      workspace.items().size(),
                      workspace.inputJar().getFileName(),
                      workspace.resistanceOverflowState()));
          load.disableProperty().unbind();
          load.setDisable(false);
        });
    task.setOnFailed(
        _ -> {
          Throwable error = task.getException();
          state.status("Load failed: " + error.getMessage());
          FxDialogs.showError("Unable to load VDDOH JAR", error);
          load.disableProperty().unbind();
          load.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-load");
    thread.setDaemon(true);
    thread.start();
  }

  private void updateResistanceOverflowControl(EditorWorkspace workspace) {
    switch (workspace.resistanceOverflowState()) {
      case "PATCHED" -> {
        state.patchResistanceOverflow(true);
        patchResistanceOverflow.setDisable(true);
        patchResistanceOverflow.setTooltip(
            new javafx.scene.control.Tooltip("Input JAR is already patched."));
      }
      case "ORIGINAL" -> {
        state.patchResistanceOverflow(false);
        patchResistanceOverflow.setDisable(false);
        patchResistanceOverflow.setTooltip(
            new javafx.scene.control.Tooltip("Enable to patch g.class resistance overflow."));
      }
      default -> {
        state.patchResistanceOverflow(false);
        patchResistanceOverflow.setDisable(true);
        patchResistanceOverflow.setTooltip(
            new javafx.scene.control.Tooltip("Unsupported g.class layout; patch disabled."));
      }
    }
  }

  private void buildResistanceOverflowPatch(Button build) {
    if (!state.patchResistanceOverflow() || patchResistanceOverflow.isDisable()) {
      state.status("Resistance overflow patch is not enabled for this input JAR.");
      return;
    }
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildResistanceOverflowPatch(state.workspace());
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building resistance overflow class patch...");
    task.setOnSucceeded(
        _ -> {
          BuildResult result = task.getValue();
          state.status("Wrote %s (%s)".formatted(result.outputJar(), result.summary()));
          build.disableProperty().unbind();
          build.setDisable(false);
          patchResistanceOverflow.setSelected(true);
          patchResistanceOverflow.setDisable(true);
        });
    task.setOnFailed(
        _ -> {
          Throwable error = task.getException();
          state.status("Resistance overflow patch failed: " + error.getMessage());
          FxDialogs.showError("Unable to build resistance overflow patch", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-class-patch");
    thread.setDaemon(true);
    thread.start();
  }
}
