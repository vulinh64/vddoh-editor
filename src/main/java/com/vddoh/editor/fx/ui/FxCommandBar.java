package com.vddoh.editor.fx.ui;

import com.vddoh.editor.BuildResult;
import com.vddoh.editor.EditorLoadService;
import com.vddoh.editor.EditorPatchService;
import com.vddoh.editor.EditorWorkspace;
import com.vddoh.editor.PatchBuildRequest;
import com.vddoh.editor.fx.FxEditorState;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    Button build = new Button("Build Patched JAR");
    build.setOnAction(_ -> buildPatchedJar(build));
    Button view = new Button("View");
    view.setOnAction(_ -> viewOutputJar());
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
            build,
            view);
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

  private void buildPatchedJar(Button build) {
    boolean classPatchRequested =
        state.patchResistanceOverflow() && !patchResistanceOverflow.isDisable();
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildFullPatch(
                PatchBuildRequest.builder()
                    .workspace(state.workspace())
                    .skillEdits(state.skillEdits())
                    .talentEdits(state.talentEdits())
                    .heroEdits(state.heroEdits())
                    .itemEdits(state.itemEdits())
                    .monsterEdits(state.monsterEdits())
                    .statusEdits(state.statusEdits())
                    .classPatchRequested(classPatchRequested)
                    .build());
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building patched JAR...");
    task.setOnSucceeded(
        _ -> {
          BuildResult result = task.getValue();
          state.status("Wrote %s (%s)".formatted(result.outputJar(), result.summary()));
          build.disableProperty().unbind();
          build.setDisable(false);
          if (classPatchRequested) {
            patchResistanceOverflow.setSelected(true);
            patchResistanceOverflow.setDisable(true);
          }
        });
    task.setOnFailed(
        _ -> {
          Throwable error = task.getException();
          state.status("Patch build failed: " + error.getMessage());
          FxDialogs.showError("Unable to build patched JAR", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-full-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private void viewOutputJar() {
    try {
      EditorWorkspace workspace = state.workspace();
      if (workspace == null) {
        throw new IllegalStateException("Load a VDDOH JAR before opening the output location.");
      }
      revealInFileManager(workspace.outputJar().toAbsolutePath().normalize());
    } catch (Exception ex) {
      state.status("Unable to open output location: " + ex.getMessage());
      FxDialogs.showError("Unable to open output location", ex);
    }
  }

  private static void revealInFileManager(Path target) throws IOException {
    Path parent = target.getParent();
    if (parent == null) {
      parent = Path.of(".").toAbsolutePath().normalize();
    }
    Files.createDirectories(parent);
    String osName = System.getProperty("os.name", "").toLowerCase();
    if (osName.contains("win")) {
      startFileManager(
          "explorer.exe", Files.exists(target) ? "/select," + target : parent.toString());
      return;
    }
    if (osName.contains("mac")) {
      if (Files.exists(target)) {
        startFileManager("open", "-R", target.toString());
      } else {
        startFileManager("open", parent.toString());
      }
      return;
    }
    try {
      startFileManager("xdg-open", parent.toString());
    } catch (IOException ex) {
      openWithDesktop(parent, ex);
    }
  }

  private static void startFileManager(String... command) throws IOException {
    new ProcessBuilder(command).start();
  }

  private static void openWithDesktop(Path directory, IOException originalFailure)
      throws IOException {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      Desktop.getDesktop().open(directory.toFile());
      return;
    }
    throw originalFailure;
  }
}
