package com.vddoh.editor.view.ui;

import com.vddoh.editor.data.BuildResult;
import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.data.PatchBuildRequest;
import com.vddoh.editor.service.EditorLoadService;
import com.vddoh.editor.service.EditorPatchService;
import com.vddoh.editor.view.FxEditorState;
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
  private final TextField inputJar = new TextField();
  private final TextField outputJar = new TextField();
  private static final String DEFAULT_OUTPUT_FILE = "vddoh-edited.jar";
  private final CheckBox patchResistanceOverflow = new CheckBox("Patch resistance overflow");
  private final CheckBox patchEquipmentBonus = new CheckBox("Patch equipment bonus overwrite");
  private final Button load = new Button("Load");
  private Path latestOutputJar;

  public FxCommandBar(Stage owner, FxEditorState state) {
    this.owner = owner;
    this.state = state;
    getStyleClass().add("command-bar");
    setPadding(new Insets(8));
    setSpacing(8);
    inputJar.setPromptText("Choose original VDDOH JAR...");
    outputJar.setEditable(false);
    outputJar.setOnMouseClicked(_ -> chooseOutputJar());
    outputJar.setFocusTraversable(false);

    Button browse = new Button("...");
    browse.setOnAction(_ -> chooseInputJar());
    load.setOnAction(_ -> loadSelectedJar(load));
    Button buildDataOnly = new Button("Build Data-Only JAR");
    buildDataOnly.setOnAction(_ -> buildDataOnlyJar(buildDataOnly));
    Button build = new Button("Build Full Patched JAR");
    build.setOnAction(_ -> buildPatchedJar(build));
    Button view = new Button("View");
    view.setOnAction(_ -> viewOutputJar());
    Button changeLog = new Button("Change Log");
    changeLog.setOnAction(_ -> FxChangeLogDialog.show(owner, state));
    patchResistanceOverflow
        .selectedProperty()
        .bindBidirectional(state.patchResistanceOverflowProperty());
    patchResistanceOverflow.setDisable(true);
    patchEquipmentBonus.selectedProperty().bindBidirectional(state.patchEquipmentBonusProperty());
    patchEquipmentBonus.setDisable(true);

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
            patchEquipmentBonus,
            buildDataOnly,
            build,
            view,
            changeLog);
  }

  public void loadInitialInputJar(Path selectedJar) {
    inputJar.setText(selectedJar.toString());
    loadSelectedJar(load);
  }

  private void chooseInputJar() {
    File chosen = chooseInputJarFile();
    if (chosen != null) {
      inputJar.setText(chosen.toPath().toString());
    }
  }

  private File chooseInputJarFile() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose VDDOH JAR");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));
    return chooser.showOpenDialog(owner);
  }

  private void chooseOutputJar() {
    EditorWorkspace workspace = state.workspace();
    if (workspace == null) {
      state.status("Load a VDDOH JAR before choosing an output location.");
      return;
    }
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose Patched JAR Output");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));
    Path initial = latestOutputJar == null ? workspace.outputJar() : latestOutputJar;
    Path initialDirectory = initial.toAbsolutePath().normalize().getParent();
    if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
      chooser.setInitialDirectory(initialDirectory.toFile());
    }
    chooser.setInitialFileName(DEFAULT_OUTPUT_FILE);
    File chosen = chooser.showSaveDialog(owner);
    if (chosen != null) {
      Path selected = ensureJarExtension(chosen.toPath()).toAbsolutePath().normalize();
      state.outputJar(selected);
      setLatestOutputJar(selected);
      state.status("Output JAR set to " + selected);
    }
  }

  private void loadSelectedJar(Button load) {
    String input = inputJar.getText().trim();
    if (input.isEmpty()) {
      File chosen = chooseInputJarFile();
      if (chosen == null) {
        state.status("Choose a VDDOH JAR to begin.");
        return;
      }
      input = chosen.toPath().toString();
      inputJar.setText(input);
    }
    Path selected = Path.of(input);
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
          state.clearChangeLog();
          setLatestOutputJar(workspace.outputJar());
          updateResistanceOverflowControl(workspace);
          updateEquipmentBonusControl(workspace);
          state.status(
              "Loaded %d items from %s. Resistance patch state: %s. Equipment bonus patch state: %s"
                  .formatted(
                      workspace.items().size(),
                      workspace.inputJar().getFileName(),
                      workspace.resistanceOverflowState(),
                      workspace.equipmentBonusState()));
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

  private void updateEquipmentBonusControl(EditorWorkspace workspace) {
    switch (workspace.equipmentBonusState()) {
      case "PATCHED" -> {
        state.patchEquipmentBonus(true);
        patchEquipmentBonus.setDisable(true);
        patchEquipmentBonus.setTooltip(
            new javafx.scene.control.Tooltip("Input JAR already accumulates equipment bonuses."));
      }
      case "ORIGINAL" -> {
        state.patchEquipmentBonus(false);
        patchEquipmentBonus.setDisable(false);
        patchEquipmentBonus.setTooltip(
            new javafx.scene.control.Tooltip(
                "Enable to patch g.class equipment byte_d overwrite into accumulation."));
      }
      default -> {
        state.patchEquipmentBonus(false);
        patchEquipmentBonus.setDisable(true);
        patchEquipmentBonus.setTooltip(
            new javafx.scene.control.Tooltip("Unsupported g.class layout; patch disabled."));
      }
    }
  }

  private void buildPatchedJar(Button build) {
    boolean resistanceOverflowPatchRequested =
        state.patchResistanceOverflow() && !patchResistanceOverflow.isDisable();
    boolean equipmentBonusPatchRequested =
        state.patchEquipmentBonus() && !patchEquipmentBonus.isDisable();
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildFullPatch(
                basePatchBuildRequest()
                    .resistanceOverflowPatchRequested(resistanceOverflowPatchRequested)
                    .equipmentBonusPatchRequested(equipmentBonusPatchRequested)
                    .build());
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building full patched JAR...");
    task.setOnSucceeded(
        _ -> {
          BuildResult result = task.getValue();
          setLatestOutputJar(result.outputJar());
          state.status("Wrote %s (%s)".formatted(result.outputJar(), result.summary()));
          build.disableProperty().unbind();
          build.setDisable(false);
          if (resistanceOverflowPatchRequested) {
            patchResistanceOverflow.setSelected(true);
            patchResistanceOverflow.setDisable(true);
          }
          if (equipmentBonusPatchRequested) {
            patchEquipmentBonus.setSelected(true);
            patchEquipmentBonus.setDisable(true);
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

  private void buildDataOnlyJar(Button build) {
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildFullPatch(
                basePatchBuildRequest()
                    .resistanceOverflowPatchRequested(false)
                    .equipmentBonusPatchRequested(false)
                    .build());
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building data-only JAR...");
    task.setOnSucceeded(
        _ -> {
          BuildResult result = task.getValue();
          setLatestOutputJar(result.outputJar());
          state.status("Wrote %s (%s)".formatted(result.outputJar(), result.summary()));
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    task.setOnFailed(
        _ -> {
          Throwable error = task.getException();
          state.status("Data-only build failed: " + error.getMessage());
          FxDialogs.showError("Unable to build data-only JAR", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-data-only-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private PatchBuildRequest.PatchBuildRequestBuilder basePatchBuildRequest() {
    return PatchBuildRequest.builder()
        .workspace(state.buildWorkspace())
        .skillEdits(state.skillEdits())
        .talentEdits(state.talentEdits())
        .heroEdits(state.heroEdits())
        .itemEdits(state.itemEdits())
        .monsterEdits(state.monsterEdits())
        .statusEdits(state.statusEdits());
  }

  private void viewOutputJar() {
    try {
      EditorWorkspace workspace = state.workspace();
      if (workspace == null) {
        throw new IllegalStateException("Load a VDDOH JAR before opening the output location.");
      }
      revealInFileManager(latestOutputJar.toAbsolutePath().normalize());
    } catch (Exception ex) {
      state.status("Unable to open output location: " + ex.getMessage());
      FxDialogs.showError("Unable to open output location", ex);
    }
  }

  private void setLatestOutputJar(Path path) {
    latestOutputJar = path;
    state.outputJar(path);
    outputJar.setText(path.toString());
  }

  private static Path ensureJarExtension(Path path) {
    String text = path.toString();
    return text.toLowerCase().endsWith(".jar") ? path : Path.of(text + ".jar");
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
