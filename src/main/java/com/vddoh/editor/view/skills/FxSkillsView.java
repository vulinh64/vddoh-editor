package com.vddoh.editor.view.skills;

import static com.vddoh.editor.view.ui.FxTableColumns.editableIntColumn;
import static com.vddoh.editor.view.ui.FxTableColumns.intColumn;
import static com.vddoh.editor.view.ui.FxTableColumns.textColumn;

import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.FxNavigation;
import com.vddoh.editor.view.ui.FxStickyTableSplit;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class FxSkillsView extends BorderPane {

  private final FxEditorState state;
  private final ObservableList<FxSkillViewModel> skills = FXCollections.observableArrayList();
  private final FilteredList<FxSkillViewModel> filtered = new FilteredList<>(skills);
  private final TextField search = new TextField();
  private final TableView<FxSkillViewModel> stickyTable = stickySkillTable();
  private final TableView<FxSkillViewModel> table = skillTable();
  private final TableView<FxSkillEffectViewModel> effects = effectTable();

  public FxSkillsView(FxEditorState state, FxNavigation navigation) {
    this.state = state;
    getStyleClass().add("skills-view");
    stickyTable.setItems(filtered);
    table.setItems(filtered);
    table.setEditable(true);
    effects.setEditable(true);
    setTop(filters());
    javafx.scene.control.SplitPane skillSplit =
        FxStickyTableSplit.horizontal(stickyTable, table, 0.24);
    javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(skillSplit, effects);
    split.setDividerPositions(0.62);
    setCenter(split);
    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((_, _, skill) -> effects.setItems(skill == null ? null : skill.effects()));
    search.textProperty().addListener((_, _, _) -> refilter());
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace));
    navigation
        .pendingSkillNavigationProperty()
        .addListener((_, _, request) -> applyNavigation(state, request));
    state.skillEditsSupplier(this::changedEdits);
    refilter();
  }

  private HBox filters() {
    search.setPromptText("Search skills");
    Button reset = new Button("Reset Skill Edits");
    reset.setOnAction(
        _ -> {
          skills.forEach(FxSkillViewModel::reset);
          state.status("Reset JavaFX skill edits.");
        });
    HBox controls = new HBox(8, new Label("Search"), search, reset);
    controls.getStyleClass().add("filter-row");
    controls.setPadding(new Insets(8));
    HBox.setHgrow(search, Priority.ALWAYS);
    return controls;
  }

  private void load(EditorWorkspace workspace) {
    skills.setAll(
        workspace == null
            ? List.of()
            : workspace.skillLevels().stream().map(FxSkillViewModel::new).toList());
    refilter();
    if (!filtered.isEmpty()) {
      table.getSelectionModel().selectFirst();
    }
  }

  private void applyNavigation(FxEditorState state, FxNavigation.PendingSkillNavigation request) {
    if (request == null) {
      return;
    }
    search.setText(request.sourceItemName());
    refilter();
    filtered.stream()
        .filter(skill -> skill.skillId() == request.linkedSkillId())
        .filter(
            skill ->
                skill.level() == request.skillLevel() || skill.level() - 1 == request.skillLevel())
        .findFirst()
        .ifPresentOrElse(
            skill -> {
              table.getSelectionModel().select(skill);
              stickyTable.getSelectionModel().select(skill);
              table.scrollTo(skill);
              stickyTable.scrollTo(skill);
              state.status(
                  "Skills search set to \"%s\"; selected skill %d level %d from item %d."
                      .formatted(
                          request.sourceItemName(),
                          request.linkedSkillId(),
                          request.skillLevel(),
                          request.sourceItemId()));
            },
            () ->
                state.status(
                    "Skills search set to \"%s\" for linked skill %d level %d from item %d."
                        .formatted(
                            request.sourceItemName(),
                            request.linkedSkillId(),
                            request.skillLevel(),
                            request.sourceItemId())));
  }

  private void refilter() {
    filtered.setPredicate(skill -> skill.matchesSearch(search.getText()));
  }

  private List<com.vddoh.editor.data.SkillEdit> changedEdits() {
    return skills.stream().filter(FxSkillViewModel::changed).map(FxSkillViewModel::toEdit).toList();
  }

  private static TableView<FxSkillViewModel> skillTable() {
    TableView<FxSkillViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                editableIntColumn("Cost", FxSkillViewModel::costProperty, 72),
                intColumn("Shape", FxSkillViewModel::areaShape, 72),
                textColumn("Area", FxSkillViewModel::area, 82),
                intColumn("Range", FxSkillViewModel::range, 72),
                textColumn("Relative", FxSkillViewModel::relativeAreaGrowth, 90),
                textColumn("Notes", FxSkillViewModel::notes, 220)));
    return table;
  }

  private static TableView<FxSkillViewModel> stickySkillTable() {
    TableView<FxSkillViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                intColumn("ID", FxSkillViewModel::skillId, 62),
                textColumn("Skill", FxSkillViewModel::skillName, 190),
                intColumn("Level", FxSkillViewModel::level, 72)));
    return table;
  }

  private static TableView<FxSkillEffectViewModel> effectTable() {
    TableView<FxSkillEffectViewModel> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .setAll(
            List.of(
                textColumn("Type", FxSkillEffectViewModel::type, 130),
                intColumn("Index", FxSkillEffectViewModel::index, 62),
                intColumn("Target ID", FxSkillEffectViewModel::targetId, 82),
                textColumn("Target", FxSkillEffectViewModel::target, 150),
                editableIntColumn("Value", FxSkillEffectViewModel::valueProperty, 72),
                textColumn("Editable", FxSkillEffectViewModel::editable, 80),
                textColumn("Notes", FxSkillEffectViewModel::notes, 220)));
    return table;
  }
}
