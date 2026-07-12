package com.vddoh.editor.view.talents;

import static com.vddoh.editor.view.ui.FxTableColumns.editableIntColumn;
import static com.vddoh.editor.view.ui.FxTableColumns.intColumn;
import static com.vddoh.editor.view.ui.FxTableColumns.textColumn;

import com.vddoh.editor.data.BuildResult;
import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.service.EditorPatchService;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.ui.FxDialogs;
import com.vddoh.editor.view.ui.FxStickyTableSplit;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class FxTalentsView extends BorderPane {

  private final FxEditorState state;
  private final ObservableList<FxTalentViewModel> talents = FXCollections.observableArrayList();
  private final FilteredList<FxTalentViewModel> filtered = new FilteredList<>(talents);
  private final TextField search = new TextField();

  public FxTalentsView(FxEditorState state) {
    this.state = state;
    getStyleClass().add("talents-view");
    TableView<FxTalentViewModel> sticky = stickyTable();
    TableView<FxTalentViewModel> table = table();
    sticky.setItems(filtered);
    table.setItems(filtered);
    table.setEditable(true);
    setTop(filters());
    setCenter(FxStickyTableSplit.horizontal(sticky, table, 0.16));
    search.textProperty().addListener((_, _, _) -> refilter());
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace));
    state.talentEditsSupplier(this::changedEdits);
    refilter();
  }

  private HBox filters() {
    search.setPromptText("Search talents");
    Button build = new Button("Build Talent Patch");
    build.setOnAction(_ -> buildPatch(build));
    Button reset = new Button("Reset Talent Edits");
    reset.setOnAction(
        _ -> {
          talents.forEach(FxTalentViewModel::reset);
          state.status("Reset JavaFX talent edits.");
        });
    HBox controls = new HBox(8, new Label("Search"), search, build, reset);
    controls.getStyleClass().add("filter-row");
    controls.setPadding(new Insets(8));
    HBox.setHgrow(search, Priority.ALWAYS);
    return controls;
  }

  private void load(EditorWorkspace workspace) {
    talents.setAll(
        workspace == null
            ? List.of()
            : workspace.talents().stream().map(FxTalentViewModel::new).toList());
    refilter();
  }

  private void refilter() {
    filtered.setPredicate(talent -> talent.matches(search.getText()));
  }

  private void buildPatch(Button build) {
    var edits = changedEdits();
    if (edits.isEmpty()) {
      state.status("No JavaFX talent edits to patch.");
      return;
    }
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildGameDataPatch(
                state.buildWorkspace(), edits, null, null, null);
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building talent patch with %d edited talents...".formatted(edits.size()));
    task.setOnSucceeded(
        _ -> {
          BuildResult result = task.getValue();
          state.status("Wrote %s (%s)".formatted(result.outputJar(), result.summary()));
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    task.setOnFailed(
        _ -> {
          Throwable error = task.getException();
          state.status("Talent patch failed: " + error.getMessage());
          FxDialogs.showError("Unable to build talent patch", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-talent-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private List<com.vddoh.editor.data.TalentEdit> changedEdits() {
    return talents.stream()
        .filter(FxTalentViewModel::changed)
        .map(FxTalentViewModel::toEdit)
        .toList();
  }

  private static TableView<FxTalentViewModel> table() {
    TableView<FxTalentViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                textColumn("Scope", FxTalentViewModel::scope, 75),
                textColumn("Type", FxTalentViewModel::talentType, 145),
                intColumn("Current", FxTalentViewModel::currentLevel, 78),
                editableIntColumn("Max", FxTalentViewModel::maxLevelProperty, 62),
                editableIntColumn("Amount", FxTalentViewModel::amountProperty, 72),
                textColumn("Effect", FxTalentViewModel::effectName, 180),
                editableIntColumn("Hero Effect", FxTalentViewModel::heroBonusProperty, 90),
                editableIntColumn("Global ID", FxTalentViewModel::globalBonusProperty, 82),
                editableIntColumn("Unlock Ref", FxTalentViewModel::skillUnlockProperty, 82),
                editableIntColumn("Status ID", FxTalentViewModel::statusBonusProperty, 82),
                editableIntColumn("Resist ID", FxTalentViewModel::resistanceBonusProperty, 82),
                textColumn("Skill", FxTalentViewModel::unlockedSkillName, 160),
                textColumn("L1", talent -> talent.levelValue(1), 58),
                textColumn("L2", talent -> talent.levelValue(2), 58),
                textColumn("L3", talent -> talent.levelValue(3), 58),
                textColumn("L4", talent -> talent.levelValue(4), 58),
                textColumn("Notes", FxTalentViewModel::notes, 260)));
    return table;
  }

  private static TableView<FxTalentViewModel> stickyTable() {
    TableView<FxTalentViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                intColumn("ID", FxTalentViewModel::id, 56),
                textColumn("Talent", FxTalentViewModel::name, 190)));
    return table;
  }
}
