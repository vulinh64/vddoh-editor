package com.vddoh.editor.view.heroes;

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
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class FxHeroesView extends BorderPane {

  private final FxEditorState state;
  private final ObservableList<FxHeroViewModel> heroes = FXCollections.observableArrayList();
  private final FilteredList<FxHeroViewModel> filtered = new FilteredList<>(heroes);
  private final TextField search = new TextField();

  public FxHeroesView(FxEditorState state) {
    this.state = state;
    getStyleClass().add("heroes-view");
    TableView<FxHeroViewModel> sticky = stickyTable();
    TableView<FxHeroViewModel> table = table();
    sticky.setItems(filtered);
    table.setItems(filtered);
    table.setEditable(true);
    setTop(filters());
    setCenter(FxStickyTableSplit.horizontal(sticky, table, 0.14));
    search.textProperty().addListener((_, _, _) -> refilter());
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace));
    state.heroEditsSupplier(this::changedEdits);
    refilter();
  }

  private HBox filters() {
    search.setPromptText("Search heroes");
    Button build = new Button("Build Hero Patch");
    build.setOnAction(_ -> buildPatch(build));
    Button reset = new Button("Reset Hero Edits");
    reset.setOnAction(
        _ -> {
          heroes.forEach(FxHeroViewModel::reset);
          state.status("Reset JavaFX hero edits.");
        });
    HBox controls = new HBox(8, new Label("Search"), search, build, reset);
    controls.getStyleClass().add("filter-row");
    controls.setPadding(new Insets(8));
    HBox.setHgrow(search, Priority.ALWAYS);
    return controls;
  }

  private void load(EditorWorkspace workspace) {
    heroes.setAll(
        workspace == null
            ? List.of()
            : workspace.heroes().stream().map(FxHeroViewModel::new).toList());
    refilter();
  }

  private void refilter() {
    filtered.setPredicate(hero -> hero.matches(search.getText()));
  }

  private void buildPatch(Button build) {
    var edits = changedEdits();
    if (edits.isEmpty()) {
      state.status("No JavaFX hero edits to patch.");
      return;
    }
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildGameDataPatch(
                state.buildWorkspace(), null, edits, null, null);
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building hero patch with %d edited heroes...".formatted(edits.size()));
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
          state.status("Hero patch failed: " + error.getMessage());
          FxDialogs.showError("Unable to build hero patch", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-hero-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private List<com.vddoh.editor.data.HeroEdit> changedEdits() {
    return heroes.stream().filter(FxHeroViewModel::changed).map(FxHeroViewModel::toEdit).toList();
  }

  private TableView<FxHeroViewModel> table() {
    TableView<FxHeroViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                editableIntColumn(
                    "Level Cap",
                    ChangeColumnName.LEVEL_CAP,
                    FxHeroViewModel::levelCapProperty,
                    110,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "Level Cap")),
                editableIntColumn(
                    "STR Start",
                    ChangeColumnName.STR_START,
                    hero -> hero.strength().start(),
                    110,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "STR Start")),
                editableIntColumn(
                    "STR Target",
                    ChangeColumnName.STR_TARGET,
                    hero -> hero.strength().target(),
                    116,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "STR Target")),
                editableIntColumn(
                    "STR Curve",
                    ChangeColumnName.STR_CURVE,
                    hero -> hero.strength().shape(),
                    112,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 255, "STR Curve")),
                editableIntColumn(
                    "SPI Start",
                    ChangeColumnName.SPI_START,
                    hero -> hero.spirit().start(),
                    110,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "SPI Start")),
                editableIntColumn(
                    "SPI Target",
                    ChangeColumnName.SPI_TARGET,
                    hero -> hero.spirit().target(),
                    116,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "SPI Target")),
                editableIntColumn(
                    "SPI Curve",
                    ChangeColumnName.SPI_CURVE,
                    hero -> hero.spirit().shape(),
                    112,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 255, "SPI Curve")),
                editableIntColumn(
                    "VIT Start",
                    ChangeColumnName.VIT_START,
                    hero -> hero.vitality().start(),
                    110,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "VIT Start")),
                editableIntColumn(
                    "VIT Target",
                    ChangeColumnName.VIT_TARGET,
                    hero -> hero.vitality().target(),
                    116,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "VIT Target")),
                editableIntColumn(
                    "VIT Curve",
                    ChangeColumnName.VIT_CURVE,
                    hero -> hero.vitality().shape(),
                    112,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 255, "VIT Curve")),
                editableIntColumn(
                    "SPD Start",
                    ChangeColumnName.SPD_START,
                    hero -> hero.speed().start(),
                    110,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "SPD Start")),
                editableIntColumn(
                    "SPD Target",
                    ChangeColumnName.SPD_TARGET,
                    hero -> hero.speed().target(),
                    116,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 127, "SPD Target")),
                editableIntColumn(
                    "SPD Curve",
                    ChangeColumnName.SPD_CURVE,
                    hero -> hero.speed().shape(),
                    112,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 255, "SPD Curve")),
                intColumn("STR @ Cap", FxHeroViewModel::strengthAtCap, 84),
                intColumn("SPI @ Cap", FxHeroViewModel::spiritAtCap, 84),
                intColumn("VIT @ Cap", FxHeroViewModel::vitalityAtCap, 84),
                intColumn("SPD @ Cap", FxHeroViewModel::speedAtCap, 84),
                intColumn("Base HP", FxHeroViewModel::baseHp, 76),
                intColumn("Base Res", FxHeroViewModel::baseResource, 76),
                intColumn("Attack", FxHeroViewModel::baseAttack, 76),
                intColumn("Defense", FxHeroViewModel::baseDefense, 76),
                intColumn("Move", FxHeroViewModel::baseMove, 62),
                intColumn("Regen", FxHeroViewModel::baseRegen, 62),
                editableIntColumn(
                    "Crit %",
                    ChangeColumnName.CRIT_CHANCE,
                    FxHeroViewModel::baseCritChanceProperty,
                    100,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 255, "Crit %")),
                editableIntColumn(
                    "Crit Dmg",
                    ChangeColumnName.CRIT_DAMAGE,
                    FxHeroViewModel::baseCritDamageProperty,
                    110,
                    changeLogContext(),
                    IntegerEditBounds.of(0, 255, "Crit Dmg")),
                intColumn("Evasion", FxHeroViewModel::baseEvasion, 72),
                textColumn("Notes", FxHeroViewModel::notes, 240)));
    return table;
  }

  private static TableView<FxHeroViewModel> stickyTable() {
    TableView<FxHeroViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                intColumn("ID", FxHeroViewModel::id, 56),
                textColumn("Hero", FxHeroViewModel::name, 150)));
    return table;
  }

  private ChangeLogContext<FxHeroViewModel> changeLogContext() {
    return new ChangeLogContext<>(
        state, EditorTabName.HEROES, FxHeroViewModel::id, FxHeroViewModel::name);
  }
}
