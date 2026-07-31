package com.vddoh.editor.view.items;

import com.vddoh.editor.data.ChangeColumnName;
import com.vddoh.editor.data.EditorTabName;
import com.vddoh.editor.data.ItemSnapshot;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.FxNavigation;
import com.vddoh.editor.view.ui.FxSpinnerSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
import javafx.util.StringConverter;

public final class FxItemDetailPane extends ScrollPane {

  public static final String READ_ONLY_HINT = "read-only";
  public static final String WEAPON_SIDE = "Weapon";
  private static final List<Integer> DAMAGE_KINDS = List.of(0, 1, 2, 3, 4, 5);
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

  private GridPane basicGrid(FxItemViewModel viewModel) {
    ItemSnapshot item = viewModel.item();
    GridPane grid = grid();
    addRow(grid, 0, "ID", item.id(), READ_ONLY_HINT);
    addRow(grid, 1, "Name", item.name(), READ_ONLY_HINT);
    addRow(grid, 2, "Category", item.slotLabel(), "category=%d".formatted(item.category()));
    addRow(grid, 3, "Allowed", item.allowedClasses(), READ_ONLY_HINT);
    addEditableRow(
        grid,
        4,
        "Price (0..65535)",
        ChangeColumnName.PRICE,
        viewModel,
        viewModel.priceProperty(),
        0xffff);
    return grid;
  }

  private GridPane equipmentGrid(FxItemViewModel viewModel) {
    ItemSnapshot item = viewModel.item();
    GridPane grid = grid();
    addRow(grid, 0, "Slot", item.slotLabel(), item.category() == 7 ? "rune/modifier" : "");
    int row = 1;
    if (item.category() == 3) {
      addRow(grid, row++, "Weapon Reach", item.weaponReach(), "weapons only");
      Optional<FxItemEffectViewModel> weaponDamage =
          viewModel.effects().stream().filter(FxItemEffectViewModel::canEditEffectKind).findFirst();
      if (weaponDamage.isPresent()) {
        FxItemEffectViewModel effect = weaponDamage.orElseThrow();
        addWeaponDamageTypeRow(grid, row++, viewModel, effect);
        addEffectValueRow(grid, row++, viewModel, effect, "Weapon damage", 0xffff, "0..65535");
      }
    }
    if (item.category() == 2) {
      Optional<FxItemEffectViewModel> armorAbsorption =
          viewModel.effects().stream().filter(FxItemEffectViewModel::isArmorPhysicalEffect).findFirst();
      if (armorAbsorption.isPresent()) {
        addEffectValueRow(
            grid,
            row++,
            viewModel,
            armorAbsorption.orElseThrow(),
            "Physical absorption",
            0xff,
            "0..255");
      }
    }
    if (item.category() == 3 || (item.category() == 2 && item.subtype() == 1)) {
      addEditableRow(
          grid,
          row,
          "Rune Slots (0..4)",
          ChangeColumnName.RUNE_SLOTS,
          viewModel,
          viewModel.runeSlotsProperty(),
          4);
    }
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
    box.getChildren().addAll(summary, linkedSkillAction(viewModel, effect));
    return box;
  }

  private Button linkedSkillAction(FxItemViewModel viewModel, FxItemEffectViewModel effect) {
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
    return action;
  }

  private TableView<FxItemEffectViewModel> effectsTable(FxItemViewModel viewModel) {
    return effectsTable(viewModel, List.copyOf(viewModel.effects()));
  }

  private VBox runeEffectsBox(FxItemViewModel viewModel) {
    List<FxItemEffectViewModel> effects = List.copyOf(viewModel.effects());
    Set<String> commonKeys = runeCommonKeys(effects);
    VBox box = new VBox(8);
    box.getChildren()
        .addAll(
            pane("Common Effect", effectsTable(viewModel, commonRuneEffects(effects, commonKeys))),
            pane(
                "Weapon Effect",
                effectsTable(viewModel, runeSideEffects(effects, WEAPON_SIDE, commonKeys))),
            pane(
                "Armor Effect",
                effectsTable(viewModel, runeSideEffects(effects, "Armor", commonKeys))));
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

  private TableView<FxItemEffectViewModel> effectsTable(
      FxItemViewModel viewModel, List<FxItemEffectViewModel> effects) {
    TableView<FxItemEffectViewModel> table =
        new TableView<>(FXCollections.observableArrayList(effects));
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.setPrefHeight(220);
    table.setEditable(true);
    table
        .getColumns()
        .setAll(
            List.of(
                column(EffectColumn.SIDE),
                column(EffectColumn.TYPE),
                column(EffectColumn.TARGET),
                editableValueColumn(viewModel),
                column(EffectColumn.RANGE),
                column(EffectColumn.EDITABLE),
                column(EffectColumn.EXTRA),
                column(EffectColumn.RAW)));
    return table;
  }

  private void addWeaponDamageTypeRow(
      GridPane grid, int row, FxItemViewModel item, FxItemEffectViewModel effect) {
    Label label = new Label("Weapon elemental (damage type)");
    label.getStyleClass().add("field-label");
    ComboBox<Integer> selector = new ComboBox<>(FXCollections.observableArrayList(DAMAGE_KINDS));
    selector.setConverter(damageKindConverter());
    selector.setValue(effect.effectKind());
    selector.valueProperty().addListener(
        (_, oldValue, value) -> {
          if (value == null || value.equals(oldValue)) {
            return;
          }
          effect.effectKindProperty().set(value);
          state.recordChange(
              EditorTabName.ITEMS,
              item.id(),
              "%s / %s".formatted(item.name(), effect.raw()),
              ChangeColumnName.DAMAGE_TYPE,
              oldValue,
              value);
        });
    Label note = new Label("existing weapon damage entry");
    note.getStyleClass().add("field-note");
    grid.add(label, 0, row);
    grid.add(selector, 1, row);
    grid.add(note, 2, row);
  }

  private void addEffectValueRow(
      GridPane grid,
      int row,
      FxItemViewModel item,
      FxItemEffectViewModel effect,
      String labelText,
      int maximum,
      String rangeHint) {
    Label label = new Label(labelText);
    label.getStyleClass().add("field-label");
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setEditable(true);
    spinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maximum, effect.valueProperty().get()));
    FxSpinnerSupport.commitOnEnter(spinner);
    boolean[] syncing = {false};
    spinner
        .getValueFactory()
        .valueProperty()
        .addListener(
            (_, oldValue, value) -> {
              if (syncing[0]) {
                return;
              }
              effect.valueProperty().set(value);
              state.recordChange(
                  EditorTabName.ITEMS,
                  item.id(),
                  "%s / %s".formatted(item.name(), effect.raw()),
                  ChangeColumnName.DAMAGE,
                  oldValue,
                  value);
            });
    FxSpinnerSupport.syncFromProperty(spinner, effect.valueProperty(), syncing);
    Label note = new Label(rangeHint);
    note.getStyleClass().add("field-note");
    grid.add(label, 0, row);
    grid.add(spinner, 1, row);
    grid.add(note, 2, row);
  }

  private static StringConverter<Integer> damageKindConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(Integer kind) {
        if (kind == null) {
          return "";
        }
        return switch (kind) {
          case 0 -> "Physical";
          case 1 -> "Fire";
          case 2 -> "Ice";
          case 3 -> "Light";
          case 4 -> "Shadow";
          case 5 -> "Blood Drain";
          default -> "";
        };
      }

      @Override
      public Integer fromString(String text) {
        return DAMAGE_KINDS.stream()
            .filter(kind -> toString(kind).equals(text))
            .findFirst()
            .orElse(0);
      }
    };
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

  private void addEditableRow(
      GridPane grid,
      int row,
      String label,
      ChangeColumnName columnName,
      FxItemViewModel viewModel,
      IntegerProperty property,
      int max) {
    Label name = new Label(label);
    name.getStyleClass().add("field-label");
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setEditable(true);
    spinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, property.get()));
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
              state.recordChange(
                  EditorTabName.ITEMS,
                  viewModel.id(),
                  viewModel.name(),
                  columnName,
                  oldValue,
                  value);
            });
    FxSpinnerSupport.syncFromProperty(spinner, property, updatingFromProperty);
    Label note = new Label("safe edit");
    note.getStyleClass().add("field-note");
    grid.add(name, 0, row);
    grid.add(spinner, 1, row);
    grid.add(note, 2, row);
    GridPane.setHgrow(spinner, Priority.ALWAYS);
  }

  private static TableColumn<FxItemEffectViewModel, String> column(EffectColumn effectColumn) {
    TableColumn<FxItemEffectViewModel, String> column = new TableColumn<>(effectColumn.title());
    column.setCellValueFactory(
        cell -> {
          FxItemEffectViewModel effect = cell.getValue();
          if (effectColumn.refreshesForEffectKind()) {
            return Bindings.createStringBinding(
                () -> effectColumn.valueOf(effect), effect.effectKindProperty());
          }
          return stringValue(effectColumn.valueOf(effect));
        });
    return column;
  }

  private enum EffectColumn {
    SIDE("Side", FxItemEffectViewModel::side, false),
    TYPE("Type", FxItemEffectViewModel::type, true),
    TARGET("Target", FxItemEffectViewModel::target, true),
    RANGE("Range", FxItemEffectViewModel::range, false),
    EDITABLE("Editable", FxItemEffectViewModel::editable, false),
    EXTRA("Extra", FxItemEffectViewModel::extra, false),
    RAW("Raw", FxItemEffectViewModel::raw, false);

    private final String title;
    private final Function<FxItemEffectViewModel, String> value;
    private final boolean refreshesForEffectKind;

    EffectColumn(
        String title,
        Function<FxItemEffectViewModel, String> value,
        boolean refreshesForEffectKind) {
      this.title = title;
      this.value = value;
      this.refreshesForEffectKind = refreshesForEffectKind;
    }

    String title() {
      return title;
    }

    String valueOf(FxItemEffectViewModel effect) {
      return value.apply(effect);
    }

    boolean refreshesForEffectKind() {
      return refreshesForEffectKind;
    }
  }

  private TableColumn<FxItemEffectViewModel, Integer> editableValueColumn(
      FxItemViewModel viewModel) {
    TableColumn<FxItemEffectViewModel, Integer> column = new TableColumn<>("Value (row range)");
    column.setCellValueFactory(cell -> cell.getValue().valueProperty().asObject());
    column.setCellFactory(_ -> readOnlyAwareIntegerCell());
    column.setOnEditCommit(
        event -> {
          FxItemEffectViewModel effect = event.getRowValue();
          effect.valueProperty().set(event.getNewValue());
          state.recordChange(
              EditorTabName.ITEMS,
              viewModel.id(),
              "%s / %s %s %s [%s]"
                  .formatted(
                      viewModel.name(),
                      effect.side(),
                      effect.type(),
                      effect.target(),
                      effect.raw()),
              ChangeColumnName.DECODED_EFFECT_VALUE,
              event.getOldValue(),
              event.getNewValue());
        });
    column.setPrefWidth(90);
    return column;
  }

  private static TableCell<FxItemEffectViewModel, Integer> readOnlyAwareIntegerCell() {
    return new TextFieldTableCell<>(new IntegerStringConverter()) {
      @Override
      public void startEdit() {
        if (getTableRow() == null
            || !(getTableRow().getItem() instanceof FxItemEffectViewModel viewModel)
            || !viewModel.canEditValueInTable()) {
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
