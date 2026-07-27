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
import java.util.Objects;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class FxCommandBar extends HBox {

  private final Stage owner;
  private final FxEditorState state;
  private final TextField inputJar = new TextField();
  private final TextField outputJar = new TextField();
  private static final String DEFAULT_OUTPUT_FILE = "vddoh-edited.jar";
  private static final int CLASS_PATCH_OPTION_COUNT = 7;
  private final CheckBox patchResistanceOverflow = new CheckBox("Patch resistance overflow");
  private final CheckBox patchEquipmentBonus = new CheckBox("Patch equipment bonus overwrite");
  private final CheckBox patchPhysicalDamageCap = new CheckBox("Patch physical damage cap");
  private final CheckBox patchHighValueDisplay = new CheckBox("Patch high-value display");
  private final CheckBox patchHighValueGraphicDisplay =
      new CheckBox("Patch high-value graphic display");
  private final CheckBox patchVictoryReward = new CheckBox("Patch victory EXP reward");
  private final CheckBox patchMonsterRewardParser = new CheckBox("Patch monster EXP/Filar parser");
  private final Button patchJar = new Button();
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
    patchJar.setOnAction(_ -> showClassPatchDialog());
    patchJar.setDisable(true);
    updatePatchJarButton();
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
    patchPhysicalDamageCap
        .selectedProperty()
        .bindBidirectional(state.patchPhysicalDamageCapProperty());
    patchPhysicalDamageCap.setDisable(true);
    patchHighValueDisplay
        .selectedProperty()
        .bindBidirectional(state.patchHighValueDisplayProperty());
    patchHighValueDisplay.setDisable(true);
    patchHighValueGraphicDisplay
        .selectedProperty()
        .bindBidirectional(state.patchHighValueGraphicDisplayProperty());
    patchHighValueGraphicDisplay.setDisable(true);
    patchVictoryReward.selectedProperty().bindBidirectional(state.patchVictoryRewardProperty());
    patchVictoryReward.setDisable(true);
    patchMonsterRewardParser
        .selectedProperty()
        .bindBidirectional(state.patchMonsterRewardParserProperty());
    patchMonsterRewardParser.setDisable(true);
    patchResistanceOverflow.setOnAction(_ -> updatePatchJarButton());
    patchEquipmentBonus.setOnAction(_ -> updatePatchJarButton());
    patchPhysicalDamageCap.setOnAction(_ -> updatePatchJarButton());
    patchHighValueDisplay.setOnAction(_ -> updatePatchJarButton());
    patchHighValueGraphicDisplay.setOnAction(_ -> updatePatchJarButton());
    patchVictoryReward.setOnAction(_ -> updatePatchJarButton());
    patchMonsterRewardParser.setOnAction(_ -> updatePatchJarButton());

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
            buildDataOnly,
            patchJar,
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
          updatePhysicalDamageCapControl(workspace);
          updateHighValueDisplayControl(workspace);
          updateHighValueGraphicDisplayControl(workspace);
          updateVictoryRewardControl(workspace);
          updateMonsterRewardParserControl(workspace);
          patchJar.setDisable(false);
          updatePatchJarButton();
          state.status(
              "Loaded %d items from %s. Resistance patch state: %s. Equipment bonus patch state: %s. Physical damage cap patch state: %s. High-value display patch state: %s. High-value graphic display patch state: %s. Victory reward patch state: %s. Monster reward parser patch state: %s"
                  .formatted(
                      workspace.items().size(),
                      workspace.inputJar().getFileName(),
                      workspace.resistanceOverflowState(),
                      workspace.equipmentBonusState(),
                      workspace.physicalDamageCapState(),
                      workspace.highValueDisplayState(),
                      workspace.highValueGraphicDisplayState(),
                      workspace.victoryRewardState(),
                      workspace.monsterRewardParserState()));
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
      case PATCHED -> {
        state.patchResistanceOverflow(true);
        patchResistanceOverflow.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchResistanceOverflow(false);
        patchResistanceOverflow.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchResistanceOverflow(false);
        patchResistanceOverflow.setDisable(true);
      }
    }
  }

  private void updateEquipmentBonusControl(EditorWorkspace workspace) {
    switch (workspace.equipmentBonusState()) {
      case PATCHED -> {
        state.patchEquipmentBonus(true);
        patchEquipmentBonus.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchEquipmentBonus(false);
        patchEquipmentBonus.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchEquipmentBonus(false);
        patchEquipmentBonus.setDisable(true);
      }
    }
  }

  private void updateVictoryRewardControl(EditorWorkspace workspace) {
    switch (workspace.victoryRewardState()) {
      case PATCHED -> {
        state.patchVictoryReward(true);
        patchVictoryReward.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchVictoryReward(false);
        patchVictoryReward.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchVictoryReward(false);
        patchVictoryReward.setDisable(true);
      }
    }
  }

  private void updatePhysicalDamageCapControl(EditorWorkspace workspace) {
    switch (workspace.physicalDamageCapState()) {
      case PATCHED -> {
        state.patchPhysicalDamageCap(true);
        patchPhysicalDamageCap.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchPhysicalDamageCap(false);
        patchPhysicalDamageCap.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchPhysicalDamageCap(false);
        patchPhysicalDamageCap.setDisable(true);
      }
    }
  }

  private void updateHighValueDisplayControl(EditorWorkspace workspace) {
    switch (workspace.highValueDisplayState()) {
      case PATCHED -> {
        state.patchHighValueDisplay(true);
        patchHighValueDisplay.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchHighValueDisplay(false);
        patchHighValueDisplay.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchHighValueDisplay(false);
        patchHighValueDisplay.setDisable(true);
      }
    }
  }

  private void updateHighValueGraphicDisplayControl(EditorWorkspace workspace) {
    switch (workspace.highValueGraphicDisplayState()) {
      case PATCHED -> {
        state.patchHighValueGraphicDisplay(true);
        patchHighValueGraphicDisplay.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchHighValueGraphicDisplay(false);
        patchHighValueGraphicDisplay.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchHighValueGraphicDisplay(false);
        patchHighValueGraphicDisplay.setDisable(true);
      }
    }
  }

  private void updateMonsterRewardParserControl(EditorWorkspace workspace) {
    switch (workspace.monsterRewardParserState()) {
      case PATCHED -> {
        state.patchMonsterRewardParser(true);
        patchMonsterRewardParser.setDisable(true);
      }
      case ORIGINAL -> {
        state.patchMonsterRewardParser(false);
        patchMonsterRewardParser.setDisable(false);
      }
      case UNKNOWN -> {
        state.patchMonsterRewardParser(false);
        patchMonsterRewardParser.setDisable(true);
      }
    }
  }

  private void updatePatchJarButton() {
    int selected =
        selectedOriginal(patchResistanceOverflow)
            + selectedOriginal(patchEquipmentBonus)
            + selectedOriginal(patchPhysicalDamageCap)
            + selectedOriginal(patchHighValueDisplay)
            + selectedOriginal(patchHighValueGraphicDisplay)
            + selectedOriginal(patchVictoryReward)
            + selectedOriginal(patchMonsterRewardParser);
    int alreadyPatched =
        alreadyPatched(patchResistanceOverflow)
            + alreadyPatched(patchEquipmentBonus)
            + alreadyPatched(patchPhysicalDamageCap)
            + alreadyPatched(patchHighValueDisplay)
            + alreadyPatched(patchHighValueGraphicDisplay)
            + alreadyPatched(patchVictoryReward)
            + alreadyPatched(patchMonsterRewardParser);
    patchJar.setText(
        "Patch JAR (%d/%d/%d)".formatted(selected, CLASS_PATCH_OPTION_COUNT, alreadyPatched));
    patchJar.setTooltip(new Tooltip(patchJarTooltip()));
  }

  private void showClassPatchDialog() {
    if (state.workspace() == null) {
      state.status("Load a VDDOH JAR before choosing class patches.");
      return;
    }

    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(owner);
    dialog.setTitle("Patch JAR");
    dialog.setHeaderText("Choose class patches to include");
    DialogPane pane = dialog.getDialogPane();
    pane.getStylesheets()
        .add(
            Objects.requireNonNull(FxCommandBar.class.getResource("/editor.css")).toExternalForm());
    pane.getStyleClass().add("patch-dialog");
    pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    ((Button) pane.lookupButton(ButtonType.OK)).setText("Patch JAR");

    CheckBox resistanceOption = dialogCheckBox(patchResistanceOverflow);
    CheckBox equipmentOption = dialogCheckBox(patchEquipmentBonus);
    CheckBox physicalDamageOption = dialogCheckBox(patchPhysicalDamageCap);
    CheckBox highValueDisplayOption = dialogCheckBox(patchHighValueDisplay);
    CheckBox highValueGraphicDisplayOption = dialogCheckBox(patchHighValueGraphicDisplay);
    CheckBox victoryOption = dialogCheckBox(patchVictoryReward);
    CheckBox monsterOption = dialogCheckBox(patchMonsterRewardParser);
    VBox options =
        new VBox(
            8,
            resistanceOption,
            equipmentOption,
            physicalDamageOption,
            highValueDisplayOption,
            highValueGraphicDisplayOption,
            victoryOption,
            monsterOption);
    options.setPadding(new Insets(8, 0, 0, 0));
    pane.setContent(options);

    boolean shouldBuild = dialog.showAndWait().filter(ButtonType.OK::equals).isPresent();
    resistanceOption
        .selectedProperty()
        .unbindBidirectional(patchResistanceOverflow.selectedProperty());
    equipmentOption.selectedProperty().unbindBidirectional(patchEquipmentBonus.selectedProperty());
    physicalDamageOption
        .selectedProperty()
        .unbindBidirectional(patchPhysicalDamageCap.selectedProperty());
    highValueDisplayOption
        .selectedProperty()
        .unbindBidirectional(patchHighValueDisplay.selectedProperty());
    highValueGraphicDisplayOption
        .selectedProperty()
        .unbindBidirectional(patchHighValueGraphicDisplay.selectedProperty());
    victoryOption.selectedProperty().unbindBidirectional(patchVictoryReward.selectedProperty());
    monsterOption
        .selectedProperty()
        .unbindBidirectional(patchMonsterRewardParser.selectedProperty());
    if (shouldBuild) {
      buildPatchedJar(patchJar);
    }
    updatePatchJarButton();
  }

  private CheckBox dialogCheckBox(CheckBox source) {
    CheckBox checkbox = new CheckBox(source.getText());
    checkbox.selectedProperty().bindBidirectional(source.selectedProperty());
    checkbox.disableProperty().bind(source.disableProperty());
    checkbox.setOnAction(_ -> updatePatchJarButton());
    return checkbox;
  }

  private static void markPatchApplied(CheckBox checkbox, boolean requested) {
    if (requested) {
      checkbox.setSelected(true);
      checkbox.setDisable(true);
    }
  }

  private static int selectedOriginal(CheckBox item) {
    return item.isSelected() && !item.isDisable() ? 1 : 0;
  }

  private static int alreadyPatched(CheckBox item) {
    return item.isSelected() && item.isDisable() ? 1 : 0;
  }

  private String patchJarTooltip() {
    EditorWorkspace workspace = state.workspace();
    if (workspace == null) {
      return "Load a VDDOH JAR to inspect available class patches.";
    }
    return """
        Selected / Total / Already patched
        Resistance overflow: %s
        Equipment bonus overwrite: %s
        Physical damage cap: %s
        High-value display: %s
        High-value graphic display: %s
        Victory EXP reward: %s
        Monster EXP/Filar parser: %s
        """
        .formatted(
            optionTooltip(patchResistanceOverflow),
            optionTooltip(patchEquipmentBonus),
            optionTooltip(patchPhysicalDamageCap),
            optionTooltip(patchHighValueDisplay),
            optionTooltip(patchHighValueGraphicDisplay),
            optionTooltip(patchVictoryReward),
            optionTooltip(patchMonsterRewardParser))
        .strip();
  }

  private static String optionTooltip(CheckBox item) {
    if (item.isSelected() && item.isDisable()) {
      return "already patched";
    }
    if (item.isDisable()) {
      return "unsupported layout";
    }
    return item.isSelected() ? "selected" : "available";
  }

  private void buildPatchedJar(Button build) {
    boolean resistanceOverflowPatchRequested =
        state.patchResistanceOverflow() && !patchResistanceOverflow.isDisable();
    boolean equipmentBonusPatchRequested =
        state.patchEquipmentBonus() && !patchEquipmentBonus.isDisable();
    boolean physicalDamageCapPatchRequested =
        state.patchPhysicalDamageCap() && !patchPhysicalDamageCap.isDisable();
    boolean highValueDisplayPatchRequested =
        state.patchHighValueDisplay() && !patchHighValueDisplay.isDisable();
    boolean highValueGraphicDisplayPatchRequested =
        state.patchHighValueGraphicDisplay() && !patchHighValueGraphicDisplay.isDisable();
    boolean victoryRewardPatchRequested =
        state.patchVictoryReward() && !patchVictoryReward.isDisable();
    boolean monsterRewardParserPatchRequested =
        state.patchMonsterRewardParser() && !patchMonsterRewardParser.isDisable();
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildFullPatch(
                basePatchBuildRequest()
                    .resistanceOverflowPatchRequested(resistanceOverflowPatchRequested)
                    .equipmentBonusPatchRequested(equipmentBonusPatchRequested)
                    .physicalDamageCapPatchRequested(physicalDamageCapPatchRequested)
                    .highValueDisplayPatchRequested(highValueDisplayPatchRequested)
                    .highValueGraphicDisplayPatchRequested(highValueGraphicDisplayPatchRequested)
                    .victoryRewardPatchRequested(victoryRewardPatchRequested)
                    .monsterRewardParserPatchRequested(monsterRewardParserPatchRequested)
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
          markPatchApplied(patchResistanceOverflow, resistanceOverflowPatchRequested);
          markPatchApplied(patchEquipmentBonus, equipmentBonusPatchRequested);
          markPatchApplied(patchPhysicalDamageCap, physicalDamageCapPatchRequested);
          markPatchApplied(patchHighValueDisplay, highValueDisplayPatchRequested);
          markPatchApplied(patchHighValueGraphicDisplay, highValueGraphicDisplayPatchRequested);
          markPatchApplied(patchVictoryReward, victoryRewardPatchRequested);
          markPatchApplied(patchMonsterRewardParser, monsterRewardParserPatchRequested);
          updatePatchJarButton();
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
                    .physicalDamageCapPatchRequested(false)
                    .highValueDisplayPatchRequested(false)
                    .highValueGraphicDisplayPatchRequested(false)
                    .victoryRewardPatchRequested(false)
                    .monsterRewardParserPatchRequested(false)
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
