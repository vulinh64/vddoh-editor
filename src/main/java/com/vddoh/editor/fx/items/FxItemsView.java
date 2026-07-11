package com.vddoh.editor.fx.items;

import com.vddoh.editor.BuildResult;
import com.vddoh.editor.EditorPatchService;
import com.vddoh.editor.EditorWorkspace;
import com.vddoh.editor.ItemSnapshot;
import com.vddoh.editor.fx.FxEditorState;
import com.vddoh.editor.fx.FxNavigation;
import com.vddoh.editor.fx.ui.FxDialogs;
import com.vddoh.editor.fx.ui.FxStickyTableSplit;
import java.util.List;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class FxItemsView extends BorderPane {

  private static final List<String> EQUIPMENT_SLOTS =
      List.of(
          FxItemFilters.ALL_SLOTS,
          "Head",
          "Neck",
          "Ring",
          "Main Body Armor",
          "Main Weapon",
          "Boot");
  public static final String RESOURCE_BONUS_PROPS = "resourceBonus";
  public static final String HP_BONUS_PROPS = "hpBonus";
  public static final String PRICE_PROPS = "price";

  private final ObservableList<FxItemViewModel> items = FXCollections.observableArrayList();
  private final FilteredList<FxItemViewModel> filtered = new FilteredList<>(items);
  private final TextField search = new TextField();
  private final ComboBox<String> slotFilter =
      new ComboBox<>(FXCollections.observableArrayList(EQUIPMENT_SLOTS));
  private final RadioButton equipment = new RadioButton("Equipment");
  private final RadioButton runes = new RadioButton("Runes");
  private final RadioButton consumable = new RadioButton("Consumable");
  private final RadioButton battleOnlyConsumable = new RadioButton("Battle-only Consumable");
  private final RadioButton special = new RadioButton("Special");
  private final FxEditorState state;

  public FxItemsView(FxEditorState state, FxNavigation navigation) {
    this.state = state;
    getStyleClass().add("items-view");
    TableView<FxItemViewModel> sticky = stickyTable();
    TableView<FxItemViewModel> table = table();
    FxItemDetailPane detail = new FxItemDetailPane(state, navigation);
    sticky.setItems(filtered);
    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((_, _, item) -> detail.setSelectedItem(item));
    setTop(filters());
    javafx.scene.control.SplitPane itemSplit = FxStickyTableSplit.horizontal(sticky, table, 0.18);
    javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(itemSplit, detail);
    split.setDividerPositions(0.58);
    setCenter(split);
    slotFilter.getSelectionModel().select(FxItemFilters.ALL_SLOTS);
    ToggleGroup group = new ToggleGroup();
    equipment.setToggleGroup(group);
    runes.setToggleGroup(group);
    consumable.setToggleGroup(group);
    battleOnlyConsumable.setToggleGroup(group);
    special.setToggleGroup(group);
    equipment.setSelected(true);
    search.textProperty().addListener((_, _, _) -> refilter());
    slotFilter.valueProperty().addListener((_, _, _) -> refilter());
    group
        .selectedToggleProperty()
        .addListener(
            (_, _, _) -> {
              updateSlotFilterVisibility();
              refilter();
            });
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace, table));
    state.itemEditsSupplier(this::changedEdits);
    updateSlotFilterVisibility();
    refilter();
  }

  private VBox filters() {
    search.setPromptText("Search items and decoded effects");
    HBox category = new HBox(8, equipment, runes, consumable, battleOnlyConsumable, special);
    category.getStyleClass().add("filter-row");
    Button build = new Button("Build Item Patch");
    build.setOnAction(_ -> buildItemPatch(build));
    Button reset = new Button("Reset Item Edits");
    reset.setOnAction(
        _ -> {
          items.forEach(FxItemViewModel::resetEdits);
          state.status("Reset JavaFX item edits.");
        });
    HBox controls =
        new HBox(8, new Label("Search"), search, new Label("Slot"), slotFilter, build, reset);
    controls.getStyleClass().add("filter-row");
    HBox.setHgrow(search, Priority.ALWAYS);
    VBox box = new VBox(6, category, controls);
    box.setPadding(new Insets(8));
    return box;
  }

  private void updateSlotFilterVisibility() {
    boolean equipmentSelected = equipment.isSelected();
    slotFilter.setVisible(equipmentSelected);
    slotFilter.setManaged(equipmentSelected);
  }

  private TableView<FxItemViewModel> table() {
    TableView<FxItemViewModel> table = new TableView<>(filtered);
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table
        .getColumns()
        .addAll(
            stringColumn("Slot", "slotLabel", 140),
            stringColumn("Allowed", "allowedClasses", 115),
            intColumn("Price", PRICE_PROPS, 80),
            intColumn("Icon", "icon", 72),
            intColumn("HP+", HP_BONUS_PROPS, 72),
            intColumn("Res+", RESOURCE_BONUS_PROPS, 72),
            stringColumn("Bonuses", "statBonusSummary", 210),
            stringColumn("Decoded Effects", "effectSummary", 260),
            stringColumn("Notes", "notes", 220));
    return table;
  }

  private TableView<FxItemViewModel> stickyTable() {
    TableView<FxItemViewModel> table = new TableView<>(filtered);
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.getColumns().addAll(intColumn("ID", "id", 62), stringColumn("Item", "name", 190));
    return table;
  }

  private void buildItemPatch(Button build) {
    List<com.vddoh.editor.ItemEdit> edits = changedEdits();
    if (edits.isEmpty()) {
      state.status("No JavaFX item edits to patch.");
      return;
    }
    Task<BuildResult> task =
        new Task<>() {
          @Override
          protected BuildResult call() throws Exception {
            return EditorPatchService.buildItemPatch(state.workspace(), edits);
          }
        };
    build.disableProperty().bind(task.runningProperty());
    state.status("Building item patch with %d edited items...".formatted(edits.size()));
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
          state.status("Item patch failed: " + error.getMessage());
          FxDialogs.showError("Unable to build item patch", error);
          build.disableProperty().unbind();
          build.setDisable(false);
        });
    Thread thread = new Thread(task, "vddoh-fx-item-patch");
    thread.setDaemon(true);
    thread.start();
  }

  private List<com.vddoh.editor.ItemEdit> changedEdits() {
    return items.stream().filter(FxItemViewModel::changed).map(FxItemViewModel::toEdit).toList();
  }

  private void load(EditorWorkspace workspace, TableView<FxItemViewModel> table) {
    items.setAll(
        workspace == null
            ? List.of()
            : workspace.items().stream().map(FxItemViewModel::new).toList());
    refilter();
    if (!filtered.isEmpty()) {
      table.getSelectionModel().selectFirst();
    }
  }

  private void refilter() {
    filtered.setPredicate(this::matches);
  }

  private boolean matches(FxItemViewModel viewModel) {
    ItemSnapshot item = viewModel.item();
    boolean matchesSearch = viewModel.matchesSearch(search.getText());
    if (equipment.isSelected()) {
      if (!FxItemFilters.equipment(item)) {
        return false;
      }
      String selectedSlot = slotFilter.getValue();
      if (!FxItemFilters.ALL_SLOTS.equals(selectedSlot) && !selectedSlot.equals(item.slotLabel())) {
        return false;
      }
      return matchesSearch;
    }
    if (runes.isSelected()) {
      return FxItemFilters.runes(item) && matchesSearch;
    }
    if (consumable.isSelected()) {
      return FxItemFilters.consumable(item) && matchesSearch;
    }
    if (battleOnlyConsumable.isSelected()) {
      return FxItemFilters.battleOnlyConsumable(item) && matchesSearch;
    }
    return FxItemFilters.special(item) && matchesSearch;
  }

  private static TableColumn<FxItemViewModel, Number> intColumn(
      String title, String property, int width) {
    TableColumn<FxItemViewModel, Number> column = new TableColumn<>(title);
    column.setCellValueFactory(cell -> integerValue(cell.getValue(), property));
    column.setPrefWidth(width);
    return column;
  }

  private static TableColumn<FxItemViewModel, String> stringColumn(
      String title, String property, int width) {
    TableColumn<FxItemViewModel, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        cell -> new SimpleStringProperty(String.valueOf(propertyValue(cell.getValue(), property))));
    column.setPrefWidth(width);
    return column;
  }

  private static Object propertyValue(FxItemViewModel item, String property) {
    return switch (property) {
      case "id" -> item.id();
      case "name" -> item.name();
      case "slotLabel" -> item.slotLabel();
      case "allowedClasses" -> item.allowedClasses();
      case PRICE_PROPS -> item.price();
      case "icon" -> item.icon();
      case HP_BONUS_PROPS -> item.hpBonus();
      case RESOURCE_BONUS_PROPS -> item.resourceBonus();
      case "statBonusSummary" -> item.statBonusSummary();
      case "effectSummary" -> item.effectSummary();
      case "notes" -> item.notes();
      default -> "";
    };
  }

  private static ObservableValue<Number> integerValue(FxItemViewModel item, String property) {
    return switch (property) {
      case "id" -> new SimpleIntegerProperty(item.id());
      case PRICE_PROPS -> item.priceProperty();
      case "icon" -> item.iconProperty();
      case HP_BONUS_PROPS -> new SimpleIntegerProperty(item.hpBonus());
      case RESOURCE_BONUS_PROPS -> new SimpleIntegerProperty(item.resourceBonus());
      default -> new SimpleIntegerProperty(0);
    };
  }
}
