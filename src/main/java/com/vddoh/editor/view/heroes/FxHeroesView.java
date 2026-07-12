package com.vddoh.editor.view.heroes;

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

  private static TableView<FxHeroViewModel> table() {
    TableView<FxHeroViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                editableIntColumn("Level Cap", FxHeroViewModel::levelCapProperty, 80),
                editableIntColumn("STR Start", hero -> hero.strength().start(), 82),
                editableIntColumn("STR Target", hero -> hero.strength().target(), 82),
                editableIntColumn("STR Curve", hero -> hero.strength().shape(), 82),
                editableIntColumn("SPI Start", hero -> hero.spirit().start(), 82),
                editableIntColumn("SPI Target", hero -> hero.spirit().target(), 82),
                editableIntColumn("SPI Curve", hero -> hero.spirit().shape(), 82),
                editableIntColumn("VIT Start", hero -> hero.vitality().start(), 82),
                editableIntColumn("VIT Target", hero -> hero.vitality().target(), 82),
                editableIntColumn("VIT Curve", hero -> hero.vitality().shape(), 82),
                editableIntColumn("SPD Start", hero -> hero.speed().start(), 82),
                editableIntColumn("SPD Target", hero -> hero.speed().target(), 82),
                editableIntColumn("SPD Curve", hero -> hero.speed().shape(), 82),
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
                editableIntColumn("Crit %", FxHeroViewModel::baseCritChanceProperty, 66),
                editableIntColumn("Crit Dmg", FxHeroViewModel::baseCritDamageProperty, 76),
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
}
