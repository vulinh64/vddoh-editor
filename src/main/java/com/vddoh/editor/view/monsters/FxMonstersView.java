package com.vddoh.editor.view.monsters;

import static com.vddoh.editor.view.ui.FxTableColumns.editableIntColumn;
import static com.vddoh.editor.view.ui.FxTableColumns.intColumn;
import static com.vddoh.editor.view.ui.FxTableColumns.textColumn;

import com.vddoh.editor.data.BuildResult;
import com.vddoh.editor.data.ChangeColumnName;
import com.vddoh.editor.data.EditorTabName;
import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.service.EditorPatchService;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.ui.FxDialogs;
import com.vddoh.editor.view.ui.FxStickyTableSplit;
import com.vddoh.editor.view.ui.FxTableColumns.ChangeLogContext;
import com.vddoh.editor.view.ui.FxTableColumns.IntegerEditBounds;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
    TableView<FxMonsterArrayEntryViewModel> details = detailTable(table);
    sticky.setItems(filtered);
    table.setItems(filtered);
    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (_, _, monster) ->
                details.setItems(
                    monster == null
                        ? FXCollections.observableArrayList()
                        : monster.arrayEntryRows()));
    table.setEditable(true);
    details.setEditable(true);
    setTop(filters());
    SplitPane main = FxStickyTableSplit.horizontal(sticky, table, 0.16);
    VBox body = new VBox(8, main, details);
    VBox.setVgrow(main, Priority.ALWAYS);
    details.setPrefHeight(190);
    setCenter(body);
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
                state.buildWorkspace(), null, null, edits, null);
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

  private List<com.vddoh.editor.data.MonsterEdit> changedEdits() {
    return monsters.stream()
        .filter(FxMonsterViewModel::changed)
        .map(FxMonsterViewModel::toEdit)
        .toList();
  }

  private TableView<FxMonsterViewModel> table() {
    TableView<FxMonsterViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                editableIntColumn(
                    "EXP",
                    ChangeColumnName.EXP,
                    FxMonsterViewModel::experienceProperty,
                    100,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 4095, "EXP")),
                editableIntColumn(
                    "Filar",
                    ChangeColumnName.FILAR,
                    FxMonsterViewModel::filarProperty,
                    100,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 4095, "Filar")),
                editableIntColumn(
                    "Soul Restore",
                    ChangeColumnName.SOUL_RESTORE,
                    FxMonsterViewModel::deathValueProperty,
                    140,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 127, "Soul Restore")),
                editableIntColumn(
                    "Effect",
                    ChangeColumnName.EFFECT,
                    FxMonsterViewModel::effectIdProperty,
                    100,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 255, "Effect")),
                editableIntColumn(
                    "STR",
                    ChangeColumnName.STR,
                    FxMonsterViewModel::strengthProperty,
                    92,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 127, "STR")),
                editableIntColumn(
                    "SPI",
                    ChangeColumnName.SPI,
                    FxMonsterViewModel::spiritProperty,
                    92,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 127, "SPI")),
                editableIntColumn(
                    "VIT",
                    ChangeColumnName.VIT,
                    FxMonsterViewModel::vitalityProperty,
                    92,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 127, "VIT")),
                editableIntColumn(
                    "SPD",
                    ChangeColumnName.SPD,
                    FxMonsterViewModel::speedProperty,
                    92,
                    monsterChangeLogContext(),
                    IntegerEditBounds.of(0, 127, "SPD")),
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
                textColumn("Notes", FxMonsterViewModel::notes, 240)));
    return table;
  }

  private static TableView<FxMonsterViewModel> stickyTable() {
    TableView<FxMonsterViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                intColumn("ID", FxMonsterViewModel::id, 56),
                textColumn("Monster", FxMonsterViewModel::name, 180)));
    return table;
  }

  private TableView<FxMonsterArrayEntryViewModel> detailTable(
      TableView<FxMonsterViewModel> monsterTable) {
    TableView<FxMonsterArrayEntryViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                textColumn("Side", FxMonsterArrayEntryViewModel::side, 140),
                textColumn("Type", FxMonsterArrayEntryViewModel::type, 110),
                intColumn("Index", FxMonsterArrayEntryViewModel::index, 62),
                textColumn("Target", FxMonsterArrayEntryViewModel::target, 120),
                editableIntColumn(
                    "Value (row range)",
                    ChangeColumnName.DETAIL_VALUE,
                    FxMonsterArrayEntryViewModel::valueProperty,
                    120,
                    monsterArrayChangeLogContext(monsterTable)),
                textColumn("Range", FxMonsterArrayEntryViewModel::range, 88),
                textColumn("Editable", FxMonsterArrayEntryViewModel::editable, 78),
                textColumn("Raw", FxMonsterArrayEntryViewModel::raw, 110)));
    return table;
  }

  private ChangeLogContext<FxMonsterViewModel> monsterChangeLogContext() {
    return new ChangeLogContext<>(
        state, EditorTabName.MONSTERS, FxMonsterViewModel::id, FxMonsterViewModel::name);
  }

  private ChangeLogContext<FxMonsterArrayEntryViewModel> monsterArrayChangeLogContext(
      TableView<FxMonsterViewModel> monsterTable) {
    return new ChangeLogContext<>(
        state,
        EditorTabName.MONSTERS,
        _ -> selectedMonster(monsterTable) == null ? -1 : selectedMonster(monsterTable).id(),
        entry ->
            selectedMonster(monsterTable) == null
                ? "%s %s #%d".formatted(entry.type(), entry.target(), entry.index())
                : "%s / %s %s #%d"
                    .formatted(
                        selectedMonster(monsterTable).name(),
                        entry.type(),
                        entry.target(),
                        entry.index()));
  }

  private static FxMonsterViewModel selectedMonster(TableView<FxMonsterViewModel> table) {
    return table.getSelectionModel().getSelectedItem();
  }
}
