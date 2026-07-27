package com.vddoh.editor.view.skills;

import com.vddoh.editor.data.ChangeColumnName;
import com.vddoh.editor.data.EditorTabName;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.ui.FxSpinnerSupport;
import java.util.List;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;

public final class FxSkillDetailPane extends ScrollPane {

  private static final String READ_ONLY_HINT = "read-only";
  private static final String FIELD_LABEL = "field-label";
  private final FxEditorState state;
  private final ObjectProperty<FxSkillViewModel> selectedSkill = new SimpleObjectProperty<>();
  private final VBox content = new VBox(8);

  public FxSkillDetailPane(FxEditorState state) {
    this.state = state;
    getStyleClass().add("detail-scroll");
    content.getStyleClass().add("detail-pane");
    content.setPadding(new Insets(10));
    setFitToWidth(true);
    setContent(content);
    selectedSkill.addListener((_, _, skill) -> render(skill));
    render(null);
  }

  public void setSelectedSkill(FxSkillViewModel skill) {
    selectedSkill.set(skill);
  }

  private void render(FxSkillViewModel viewModel) {
    content.getChildren().clear();
    if (viewModel == null) {
      Label empty = new Label("Select a skill to inspect its decoded fields.");
      empty.getStyleClass().add("muted");
      content.getChildren().add(empty);
      return;
    }
    content
        .getChildren()
        .addAll(
            new Label(
                "%d - %s L%d"
                    .formatted(viewModel.skillId(), viewModel.skillName(), viewModel.level())),
            pane("Basic", basicGrid(viewModel)),
            pane("Decoded Effects", effectsGrid(viewModel)),
            pane("Raw Diagnostics", rawGrid(viewModel)));
  }

  private GridPane basicGrid(FxSkillViewModel viewModel) {
    GridPane grid = grid();
    addRow(grid, 0, "ID", viewModel.skillId(), READ_ONLY_HINT);
    addRow(grid, 1, "Skill", viewModel.skillName(), READ_ONLY_HINT);
    addRow(grid, 2, "Level", viewModel.level(), READ_ONLY_HINT);
    addEditableRow(grid, viewModel, viewModel.costProperty());
    addRow(grid, 4, "Shape", viewModel.areaShape(), READ_ONLY_HINT);
    addRow(grid, 5, "Area", viewModel.area(), READ_ONLY_HINT);
    addRow(grid, 6, "Range", viewModel.range(), READ_ONLY_HINT);
    addRow(grid, 7, "Relative", viewModel.relativeAreaGrowth(), READ_ONLY_HINT);
    addRow(grid, 8, "Notes", viewModel.notes(), READ_ONLY_HINT);
    return grid;
  }

  private static GridPane rawGrid(FxSkillViewModel viewModel) {
    GridPane grid = grid();
    addRow(grid, 0, "levelIndex", viewModel.skill().levelIndex(), "zero-based level");
    addRow(grid, 1, "effectCount", viewModel.effects().size(), READ_ONLY_HINT);
    return grid;
  }

  private static TitledPane pane(String title, Node content) {
    TitledPane pane = new TitledPane(title, content);
    pane.setExpanded(!"Raw Diagnostics".equals(title));
    return pane;
  }

  private static GridPane grid() {
    GridPane grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(6);
    return grid;
  }

  private static void addRow(GridPane grid, int row, String label, Object value, String hint) {
    Label name = new Label(label);
    name.getStyleClass().add(FIELD_LABEL);
    Label val = new Label(String.valueOf(value));
    val.getStyleClass().add("field-value");
    Label note = new Label(hint == null ? StringUtils.EMPTY : hint);
    note.getStyleClass().add("field-note");
    grid.add(name, 0, row);
    grid.add(val, 1, row);
    grid.add(note, 2, row);
    GridPane.setHgrow(val, Priority.ALWAYS);
  }

  private void addEditableRow(GridPane grid, FxSkillViewModel viewModel, IntegerProperty property) {
    Label name = new Label("Cost (0..255)");
    name.getStyleClass().add(FIELD_LABEL);
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setEditable(true);
    spinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 255, property.get()));
    FxSpinnerSupport.commitOnEnter(spinner);
    boolean[] updatingFromProperty = {false};
    spinner
        .getValueFactory()
        .valueProperty()
        .addListener(
            (_, oldValue, value) -> {
              if (updatingFromProperty[0]) {
                return;
              }
              property.set(value);
              recordSkillChange(viewModel, oldValue, value);
            });
    FxSpinnerSupport.syncFromProperty(spinner, property, updatingFromProperty);
    Label note = new Label("safe edit");
    note.getStyleClass().add("field-note");
    grid.add(name, 0, 3);
    grid.add(spinner, 1, 3);
    grid.add(note, 2, 3);
    GridPane.setHgrow(spinner, Priority.ALWAYS);
  }

  private GridPane effectsGrid(FxSkillViewModel viewModel) {
    GridPane grid = grid();
    addHeader(grid);
    List<FxSkillEffectViewModel> effects = FXCollections.observableArrayList(viewModel.effects());
    for (int i = 0; i < effects.size(); i++) {
      addEffectRow(grid, i + 1, viewModel, effects.get(i));
    }
    return grid;
  }

  private static void addHeader(GridPane grid) {
    addHeaderCell(grid, 0, "Type");
    addHeaderCell(grid, 1, "Index");
    addHeaderCell(grid, 2, "Target ID");
    addHeaderCell(grid, 3, "Target");
    addHeaderCell(grid, 4, "Value (row range)");
    addHeaderCell(grid, 5, "Range");
    addHeaderCell(grid, 6, "Editable");
    addHeaderCell(grid, 7, "Notes");
  }

  private static void addHeaderCell(GridPane grid, int column, String text) {
    Label label = new Label(text);
    label.getStyleClass().add(FIELD_LABEL);
    grid.add(label, column, 0);
  }

  private void addEffectRow(
      GridPane grid, int row, FxSkillViewModel skill, FxSkillEffectViewModel effect) {
    grid.add(valueLabel(effect.type()), 0, row);
    grid.add(valueLabel(effect.index()), 1, row);
    grid.add(valueLabel(effect.targetId()), 2, row);
    grid.add(valueLabel(effect.target()), 3, row);
    grid.add(effectValueNode(skill, effect), 4, row);
    grid.add(valueLabel(effect.range()), 5, row);
    grid.add(valueLabel(effect.editable()), 6, row);
    grid.add(valueLabel(effect.notes()), 7, row);
  }

  private Node effectValueNode(FxSkillViewModel skill, FxSkillEffectViewModel effect) {
    if (!effect.canEditValue()) {
      return valueLabel(effect.valueProperty().get());
    }
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setEditable(true);
    spinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            0, effect.maxValue(), effect.valueProperty().get()));
    FxSpinnerSupport.commitOnEnter(spinner);
    boolean[] updatingFromProperty = {false};
    spinner
        .getValueFactory()
        .valueProperty()
        .addListener(
            (_, oldValue, value) -> {
              if (updatingFromProperty[0]) {
                return;
              }
              effect.valueProperty().set(value);
              recordSkillEffectChange(skill, effect, oldValue, value);
            });
    effect
        .valueProperty()
        .addListener(
            (_, _, value) -> {
              if (spinner.getValueFactory().getValue().equals(value.intValue())) {
                return;
              }
              updatingFromProperty[0] = true;
              spinner.getValueFactory().setValue(value.intValue());
              updatingFromProperty[0] = false;
            });
    return spinner;
  }

  private static Label valueLabel(Object value) {
    Label label = new Label(String.valueOf(value));
    label.getStyleClass().add("field-value");
    return label;
  }

  private void recordSkillChange(FxSkillViewModel viewModel, Object oldValue, Object newValue) {
    state.recordChange(
        EditorTabName.SKILLS,
        viewModel.skillId(),
        "%s L%d".formatted(viewModel.skillName(), viewModel.level()),
        ChangeColumnName.COST,
        oldValue,
        newValue);
  }

  private void recordSkillEffectChange(
      FxSkillViewModel skill, FxSkillEffectViewModel effect, Object oldValue, Object newValue) {
    state.recordChange(
        EditorTabName.SKILLS,
        skill.skillId(),
        "%s L%d / %s %s #%d"
            .formatted(
                skill.skillName(), skill.level(), effect.type(), effect.target(), effect.index()),
        ChangeColumnName.VALUE,
        oldValue,
        newValue);
  }
}
