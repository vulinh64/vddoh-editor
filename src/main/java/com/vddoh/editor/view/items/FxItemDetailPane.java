package com.vddoh.editor.view.items;

import com.vddoh.editor.data.ItemSnapshot;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.FxNavigation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;

public final class FxItemDetailPane extends ScrollPane {

  public static final String READ_ONLY_HINT = "read-only";
  public static final String WEAPON_SIDE = "Weapon";
  private final FxEditorState state;
  private final FxNavigation navigation;
  private final ObjectProperty<FxItemViewModel> selectedItem = new SimpleObjectProperty<>();
  private final VBox content = new VBox(8);

  public FxItemDetailPane(FxEditorState state, FxNavigation navigation) {
    this.state = state;
    this.navigation = navigation;
    getStyleClass().add("detail-scroll");
    content.getStyleClass().add("detail-pane");
    content.setPadding(new Insets(10));
    setFitToWidth(true);
    setContent(content);
    selectedItem.addListener((_, _, item) -> render(item));
    render(null);
  }

  public void setSelectedItem(FxItemViewModel item) {
    selectedItem.set(item);
  }

  private void render(FxItemViewModel viewModel) {
    content.getChildren().clear();
    if (viewModel == null) {
      Label empty = new Label("Select an item to inspect its decoded fields.");
      empty.getStyleClass().add("muted");
      content.getChildren().add(empty);
      return;
    }
    ItemSnapshot item = viewModel.item();
    List<Node> panes = new ArrayList<>();
    panes.add(new Label("%d - %s".formatted(item.id(), item.name())));
    panes.add(pane("Basic", basicGrid(viewModel)));
    if (hasEquipmentDetails(item)) {
      panes.add(pane("Equipment", equipmentGrid(viewModel)));
    }
    if (item.category() == 9 || item.category() == 10) {
      panes.add(pane("Linked Skill", linkedSkillBox(viewModel)));
    }
    panes.add(
        pane(
            item.category() == 7 ? "Rune Effects" : "Decoded Effects",
            item.category() == 7 ? runeEffectsBox(viewModel) : effectsTable(viewModel)));
    panes.add(pane("Raw Diagnostics", rawGrid(item)));
    content.getChildren().addAll(panes);
  }

  private static boolean hasEquipmentDetails(ItemSnapshot item) {
    return switch (item.category()) {
      case 1, 2, 3, 4, 7 -> true;
      default -> false;
    };
  }

  private static TitledPane pane(String title, javafx.scene.Node content) {
    TitledPane pane = new TitledPane(title, content);
    pane.setExpanded(!"Raw Diagnostics".equals(title));
    return pane;
  }

  private static GridPane basicGrid(FxItemViewModel viewModel) {
    ItemSnapshot item = viewModel.item();
    GridPane grid = grid();
    addRow(grid, 0, "ID", item.id(), READ_ONLY_HINT);
    addRow(grid, 1, "Name", item.name(), READ_ONLY_HINT);
    addRow(grid, 2, "Category", item.slotLabel(), "category=%d".formatted(item.category()));
    addRow(grid, 3, "Subtype", item.subtype(), "raw type=%d".formatted(item.rawType()));
    addRow(grid, 4, "Allowed", item.allowedClasses(), READ_ONLY_HINT);
    addEditableRow(grid, 5, "Price", viewModel.priceProperty(), 0xffff);
    addEditableRow(grid, 6, "Icon", viewModel.iconProperty(), 0x7f);
    return grid;
  }

  private static GridPane equipmentGrid(FxItemViewModel viewModel) {
    ItemSnapshot item = viewModel.item();
    GridPane grid = grid();
    addRow(grid, 0, "Slot", item.slotLabel(), item.category() == 7 ? "rune/modifier" : "");
    addRow(grid, 1, "HP Bonus", item.hpBonus(), "read-only preview");
    addRow(grid, 2, "Resource Bonus", item.resourceBonus(), "read-only preview");
    addRow(grid, 3, "Weapon Reach", item.weaponReach(), "weapons only");
    addRow(grid, 4, "Weapon Mode", item.weaponMode(), "weapons only");
    addRow(grid, 5, "Stat Bonuses", viewModel.statBonusSummary(), "decoded packed stats");
    addRow(grid, 6, "Damage / Effects", viewModel.effectSummary(), "decoded effect rows");
    return grid;
  }

  private VBox linkedSkillBox(FxItemViewModel viewModel) {
    VBox box = new VBox(8);
    Optional<FxItemEffectViewModel> linked =
        viewModel.effects().stream().filter(FxItemEffectViewModel::linkedSkill).findFirst();
    if (linked.isEmpty()) {
      Label none = new Label("No linked skill row decoded for this item.");
      none.getStyleClass().add("muted");
      box.getChildren().add(none);
      return box;
    }
    FxItemEffectViewModel effect = linked.orElseThrow();
    Label summary =
        new Label(
            "%s, skill id %d, level/variant %d"
                .formatted(effect.target(), effect.linkedSkillId(), effect.skillLevel()));
    Button action = new Button("Send To Skills Search");
    action.setOnAction(
        _ -> {
          navigation.requestSkillNavigation(
              new FxNavigation.PendingSkillNavigation(
                  viewModel.id(), viewModel.name(), effect.linkedSkillId(), effect.skillLevel()));
          state.status(
              "Opening Skills with search \"%s\" for linked skill %d level %d from item %d."
                  .formatted(
                      viewModel.name(),
                      effect.linkedSkillId(),
                      effect.skillLevel(),
                      viewModel.id()));
        });
    box.getChildren().addAll(summary, action);
    return box;
  }

  private static TableView<FxItemEffectViewModel> effectsTable(FxItemViewModel viewModel) {
    return effectsTable(List.copyOf(viewModel.effects()));
  }

  private static VBox runeEffectsBox(FxItemViewModel viewModel) {
    List<FxItemEffectViewModel> effects = List.copyOf(viewModel.effects());
    Set<String> commonKeys = runeCommonKeys(effects);
    VBox box = new VBox(8);
    box.getChildren()
        .addAll(
            pane("Common Effect", effectsTable(commonRuneEffects(effects, commonKeys))),
            pane("Weapon Effect", effectsTable(runeSideEffects(effects, WEAPON_SIDE, commonKeys))),
            pane("Armor Effect", effectsTable(runeSideEffects(effects, "Armor", commonKeys))));
    return box;
  }

  private static List<FxItemEffectViewModel> commonRuneEffects(
      List<FxItemEffectViewModel> effects, Set<String> commonKeys) {
    Set<String> seen = new HashSet<>();
    return effects.stream()
        .filter(effect -> commonKeys.contains(commonKey(effect)))
        .filter(effect -> WEAPON_SIDE.equals(effect.side()))
        .filter(effect -> seen.add(commonKey(effect)))
        .toList();
  }

  private static List<FxItemEffectViewModel> runeSideEffects(
      List<FxItemEffectViewModel> effects, String side, Set<String> commonKeys) {
    return effects.stream()
        .filter(effect -> side.equals(effect.side()))
        .filter(effect -> !commonKeys.contains(commonKey(effect)))
        .toList();
  }

  private static Set<String> runeCommonKeys(List<FxItemEffectViewModel> effects) {
    Set<String> weaponKeys = new HashSet<>();
    Set<String> armorKeys = new HashSet<>();
    for (FxItemEffectViewModel effect : effects) {
      String key = commonKey(effect);
      if (WEAPON_SIDE.equals(effect.side())) {
        weaponKeys.add(key);
      } else if ("Armor".equals(effect.side())) {
        armorKeys.add(key);
      }
    }
    weaponKeys.retainAll(armorKeys);
    return weaponKeys;
  }

  private static String commonKey(FxItemEffectViewModel effect) {
    return "%s|%s|%s|%s".formatted(effect.type(), effect.target(), effect.value(), effect.raw());
  }

  private static TableView<FxItemEffectViewModel> effectsTable(
      List<FxItemEffectViewModel> effects) {
    TableView<FxItemEffectViewModel> table =
        new TableView<>(FXCollections.observableArrayList(effects));
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.setPrefHeight(220);
    table.setEditable(true);
    table
        .getColumns()
        .setAll(
            List.of(
                column("Side", "side"),
                column("Type", "type"),
                column("Target", "target"),
                editableValueColumn(),
                column("Editable", "editable"),
                column("Extra", "extra"),
                column("Raw", "raw")));
    return table;
  }

  private static GridPane rawGrid(ItemSnapshot item) {
    GridPane grid = grid();
    addRow(grid, 0, "rawType", item.rawType(), "high nibble category, low nibble subtype");
    addRow(grid, 1, "category", item.category(), "");
    addRow(grid, 2, "subtype", item.subtype(), "");
    addRow(grid, 3, "notes", item.notes(), "");
    return grid;
  }

  private static GridPane grid() {
    GridPane grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(6);
    return grid;
  }

  private static void addRow(GridPane grid, int row, String label, Object value, String hint) {
    Label name = new Label(label);
    name.getStyleClass().add("field-label");
    Label val = new Label(String.valueOf(value));
    val.getStyleClass().add("field-value");
    Label note = new Label(hint == null ? "" : hint);
    note.getStyleClass().add("field-note");
    grid.add(name, 0, row);
    grid.add(val, 1, row);
    grid.add(note, 2, row);
    GridPane.setHgrow(val, Priority.ALWAYS);
  }

  private static void addEditableRow(
      GridPane grid, int row, String label, IntegerProperty property, int max) {
    Label name = new Label(label);
    name.getStyleClass().add("field-label");
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setEditable(true);
    spinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, property.get()));
    spinner.getValueFactory().valueProperty().addListener((_, _, value) -> property.set(value));
    property.addListener((_, _, value) -> spinner.getValueFactory().setValue(value.intValue()));
    Label note = new Label("%s, range %d..%d".formatted("safe edit", 0, max));
    note.getStyleClass().add("field-note");
    grid.add(name, 0, row);
    grid.add(spinner, 1, row);
    grid.add(note, 2, row);
    GridPane.setHgrow(spinner, Priority.ALWAYS);
  }

  private static TableColumn<FxItemEffectViewModel, String> column(String title, String property) {
    TableColumn<FxItemEffectViewModel, String> column = new TableColumn<>(title);
    Function<FxItemEffectViewModel, String> value =
        switch (property) {
          case "side" -> FxItemEffectViewModel::side;
          case "type" -> FxItemEffectViewModel::type;
          case "target" -> FxItemEffectViewModel::target;
          case "value" -> FxItemEffectViewModel::value;
          case "editable" -> FxItemEffectViewModel::editable;
          case "extra" -> FxItemEffectViewModel::extra;
          case "raw" -> FxItemEffectViewModel::raw;
          default -> _ -> "";
        };
    column.setCellValueFactory(cell -> stringValue(value.apply(cell.getValue())));
    return column;
  }

  private static TableColumn<FxItemEffectViewModel, Integer> editableValueColumn() {
    TableColumn<FxItemEffectViewModel, Integer> column = new TableColumn<>("Value");
    column.setCellValueFactory(cell -> cell.getValue().valueProperty().asObject());
    column.setCellFactory(_ -> readOnlyAwareIntegerCell());
    column.setOnEditCommit(event -> event.getRowValue().valueProperty().set(event.getNewValue()));
    column.setPrefWidth(90);
    return column;
  }

  private static TableCell<FxItemEffectViewModel, Integer> readOnlyAwareIntegerCell() {
    return new TextFieldTableCell<>(new IntegerStringConverter()) {
      @Override
      public void startEdit() {
        if (getTableRow() == null
            || !(getTableRow().getItem() instanceof FxItemEffectViewModel viewModel)
            || !viewModel.canEditValue()) {
          return;
        }
        super.startEdit();
      }
    };
  }

  private static ObservableValue<String> stringValue(String value) {
    return new SimpleStringProperty(value);
  }
}
