package com.vddoh.editor.fx.monsters;

import static com.vddoh.editor.fx.ui.FxTableColumns.editableIntColumn;
import static com.vddoh.editor.fx.ui.FxTableColumns.intColumn;
import static com.vddoh.editor.fx.ui.FxTableColumns.textColumn;

import com.vddoh.editor.BuildResult;
import com.vddoh.editor.EditorPatchService;
import com.vddoh.editor.EditorWorkspace;
import com.vddoh.editor.fx.FxEditorState;
import com.vddoh.editor.fx.ui.FxDialogs;
import com.vddoh.editor.fx.ui.FxStickyTableSplit;
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

public final class FxMonstersView extends BorderPane {

  private final FxEditorState state;
  private final ObservableList<FxMonsterViewModel> monsters = FXCollections.observableArrayList();
  private final FilteredList<FxMonsterViewModel> filtered = new FilteredList<>(monsters);
  private final TextField search = new TextField();

  public FxMonstersView(FxEditorState state) {
    this.state = state;
    getStyleClass().add("monsters-view");
    TableView<FxMonsterViewModel> sticky = stickyTable();
    TableView<FxMonsterViewModel> table = table();
    sticky.setItems(filtered);
    table.setItems(filtered);
    table.setEditable(true);
    setTop(filters());
    setCenter(FxStickyTableSplit.horizontal(sticky, table, 0.16));
    search.textProperty().addListener((_, _, _) -> refilter());
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace));
    state.monsterEditsSupplier(this::changedEdits);
    refilter();
  }

  private HBox filters() {
    search.setPromptText("Search monsters");
    Button build = new Button("Build Monster Patch");
    build.setOnAction(_ -> buildPatch(build));
    Button reset = new Button("Reset Monster Edits");
    reset.setOnAction(
        _ -> {
          monsters.forEach(FxMonsterViewModel::reset);
          state.status("Reset JavaFX monster edits.");
        });
    HBox controls = new HBox(8, new Label("Search"), search, build, reset);
    controls.getStyleClass().add("filter-row");
    controls.setPadding(new Insets(8));
    HBox.setHgrow(search, Priority.ALWAYS);
    return controls;
  }

  private void load(EditorWorkspace workspace) {
    monsters.setAll(
        workspace == null
            ? List.of()
            : workspace.monsters().stream().map(FxMonsterViewModel::new).toList());
    refilter();
  }

  private void refilter() {
    filtered.setPredicate(monster -> monster.matches(search.getText()));
  }

  private void buildPatch(Button build) {
    var edits = changedEdits();
    if (edits.isEmpty()) {
      state.status("No JavaFX monster edits to patch.");
      return;
    }
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildGameDataPatch(
                state.workspace(), null, null, edits, null);
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building monster patch with %d edited monsters...".formatted(edits.size()));
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
          state.status("Monster patch failed: " + error.getMessage());
          FxDialogs.showError("Unable to build monster patch", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-monster-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private List<com.vddoh.editor.MonsterEdit> changedEdits() {
    return monsters.stream()
        .filter(FxMonsterViewModel::changed)
        .map(FxMonsterViewModel::toEdit)
        .toList();
  }

  private static TableView<FxMonsterViewModel> table() {
    TableView<FxMonsterViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .addAll(
            editableIntColumn("EXP", FxMonsterViewModel::experienceProperty, 70),
            editableIntColumn("Filar", FxMonsterViewModel::filarProperty, 70),
            editableIntColumn("Death", FxMonsterViewModel::deathValueProperty, 70),
            editableIntColumn("Effect", FxMonsterViewModel::effectIdProperty, 70),
            editableIntColumn("STR", FxMonsterViewModel::strengthProperty, 62),
            editableIntColumn("SPI", FxMonsterViewModel::spiritProperty, 62),
            editableIntColumn("VIT", FxMonsterViewModel::vitalityProperty, 62),
            editableIntColumn("SPD", FxMonsterViewModel::speedProperty, 62),
            intColumn("HP", FxMonsterViewModel::baseHp, 72),
            intColumn("Res", FxMonsterViewModel::baseResource, 72),
            intColumn("Attack", FxMonsterViewModel::baseAttack, 72),
            intColumn("Defense", FxMonsterViewModel::baseDefense, 72),
            intColumn("Move", FxMonsterViewModel::baseMove, 62),
            intColumn("Hit", FxMonsterViewModel::hitChance, 62),
            intColumn("Crit/Dmg", FxMonsterViewModel::critOrDamage, 76),
            intColumn("Evade", FxMonsterViewModel::evadeOrGuard, 70),
            intColumn("Chance", FxMonsterViewModel::packedChance, 72),
            intColumn("Actions", FxMonsterViewModel::actionCount, 72),
            intColumn("Effects", FxMonsterViewModel::effectCount, 72),
            intColumn("Drops", FxMonsterViewModel::dropCount, 72),
            textColumn("Notes", FxMonsterViewModel::notes, 240));
    return table;
  }

  private static TableView<FxMonsterViewModel> stickyTable() {
    TableView<FxMonsterViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .addAll(
            intColumn("ID", FxMonsterViewModel::id, 56),
            textColumn("Monster", FxMonsterViewModel::name, 180));
    return table;
  }
}
