package com.vddoh.editor.fx.statuses;

import static com.vddoh.editor.fx.ui.FxTableColumns.editableIntColumn;
import static com.vddoh.editor.fx.ui.FxTableColumns.intColumn;
import static com.vddoh.editor.fx.ui.FxTableColumns.textColumn;

import com.vddoh.editor.BuildResult;
import com.vddoh.editor.EditorPatchService;
import com.vddoh.editor.EditorWorkspace;
import com.vddoh.editor.fx.FxEditorState;
import com.vddoh.editor.fx.ui.FxDialogs;
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

public final class FxStatusesView extends BorderPane {

  private final FxEditorState state;
  private final ObservableList<FxStatusViewModel> statuses = FXCollections.observableArrayList();
  private final FilteredList<FxStatusViewModel> filtered = new FilteredList<>(statuses);
  private final TextField search = new TextField();

  public FxStatusesView(FxEditorState state) {
    this.state = state;
    getStyleClass().add("statuses-view");
    TableView<FxStatusViewModel> table = table();
    table.setItems(filtered);
    table.setEditable(true);
    setTop(filters());
    setCenter(table);
    search.textProperty().addListener((_, _, _) -> refilter());
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace));
    state.statusEditsSupplier(this::changedEdits);
    refilter();
  }

  private HBox filters() {
    search.setPromptText("Search statuses");
    Button build = new Button("Build Status Patch");
    build.setOnAction(_ -> buildPatch(build));
    Button reset = new Button("Reset Status Edits");
    reset.setOnAction(
        _ -> {
          statuses.forEach(FxStatusViewModel::reset);
          state.status("Reset JavaFX status edits.");
        });
    HBox controls = new HBox(8, new Label("Search"), search, build, reset);
    controls.getStyleClass().add("filter-row");
    controls.setPadding(new Insets(8));
    HBox.setHgrow(search, Priority.ALWAYS);
    return controls;
  }

  private void load(EditorWorkspace workspace) {
    statuses.setAll(
        workspace == null
            ? List.of()
            : workspace.statuses().stream().map(FxStatusViewModel::new).toList());
    refilter();
  }

  private void refilter() {
    filtered.setPredicate(status -> status.matches(search.getText()));
  }

  private void buildPatch(Button build) {
    var edits = changedEdits();
    if (edits.isEmpty()) {
      state.status("No JavaFX status edits to patch.");
      return;
    }
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildGameDataPatch(
                state.workspace(), null, null, null, edits);
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building status patch with %d edited statuses...".formatted(edits.size()));
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
          state.status("Status patch failed: " + error.getMessage());
          FxDialogs.showError("Unable to build status patch", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-status-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private List<com.vddoh.editor.StatusEdit> changedEdits() {
    return statuses.stream()
        .filter(FxStatusViewModel::changed)
        .map(FxStatusViewModel::toEdit)
        .toList();
  }

  private static TableView<FxStatusViewModel> table() {
    TableView<FxStatusViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .addAll(
            intColumn("ID", FxStatusViewModel::id, 60),
            textColumn("Status", FxStatusViewModel::name, 180),
            editableIntColumn("Duration", FxStatusViewModel::durationProperty, 90),
            editableIntColumn("Expire Chance", FxStatusViewModel::expireChanceProperty, 110),
            editableIntColumn("Icon", FxStatusViewModel::iconProperty, 70),
            textColumn("Notes", FxStatusViewModel::notes, 260));
    return table;
  }
}
