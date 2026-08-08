package com.vddoh.editor.view.shops;

import com.vddoh.editor.data.BuildResult;
import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.data.ItemSnapshot;
import com.vddoh.editor.data.ShopEdit;
import com.vddoh.editor.data.ShopSnapshot;
import com.vddoh.editor.service.EditorPatchService;
import com.vddoh.editor.service.MdatShopService;
import com.vddoh.editor.view.FxEditorState;
import com.vddoh.editor.view.ui.FxDialogs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Editor for the confirmed consumable-shop event payloads in {@code m.dat}. */
public final class FxChildrenShopsView extends BorderPane {
  private final FxEditorState state;
  private final ComboBox<ShopSnapshot> shops = new ComboBox<>();
  private final ComboBox<ItemSnapshot> availableItems = new ComboBox<>();
  private final ObservableList<Integer> stock = FXCollections.observableArrayList();
  private final ListView<Integer> stockList = new ListView<>(stock);
  private final Map<Integer, List<Integer>> editedStock = new HashMap<>();
  private final Map<Integer, ItemSnapshot> itemsById = new HashMap<>();

  public FxChildrenShopsView(FxEditorState state) {
    this.state = state;
    getStyleClass().add("shops-view");
    shops.setConverter(shopConverter());
    availableItems.setConverter(itemConverter());
    stockList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    stockList.setCellFactory(_ -> itemCell());
    shops.valueProperty().addListener((_, _, shop) -> selectShop(shop));
    state.workspaceProperty().addListener((_, _, workspace) -> load(workspace));
    setTop(controls());
    setCenter(stockList);
  }

  private VBox controls() {
    Button add = new Button("Add Selected Item");
    add.setOnAction(_ -> addSelectedItem());
    Button replace = new Button("Replace Selected Item");
    replace.setOnAction(_ -> replaceSelectedItem());
    Button remove = new Button("Delete Selected Item(s)");
    remove.setOnAction(_ -> removeSelectedItems());
    Button reset = new Button("Reset Shop");
    reset.setOnAction(_ -> resetShop());
    Button build = new Button("Build Shop Patch");
    build.setOnAction(_ -> buildPatch(build));
    HBox shopRow = new HBox(8, new Label("Shop"), shops, reset, build);
    HBox itemRow =
        new HBox(8, new Label("Add Children-shop item"), availableItems, add, replace, remove);
    shopRow.getStyleClass().add("filter-row");
    itemRow.getStyleClass().add("filter-row");
    HBox.setHgrow(shops, Priority.ALWAYS);
    HBox.setHgrow(availableItems, Priority.ALWAYS);
    VBox controls = new VBox(8, shopRow, itemRow);
    controls.setPadding(new Insets(8));
    return controls;
  }

  private void load(EditorWorkspace workspace) {
    editedStock.clear();
    itemsById.clear();
    shops.setItems(FXCollections.observableArrayList(workspace == null ? List.of() : workspace.shops()));
    List<ItemSnapshot> available =
        workspace == null
            ? List.of()
            : workspace.items().stream().filter(item -> MdatShopService.isChildrenShopItem(item.id())).toList();
    if (workspace != null) workspace.items().forEach(item -> itemsById.put(item.id(), item));
    availableItems.setItems(FXCollections.observableArrayList(available));
    shops.getSelectionModel().selectFirst();
  }

  private void selectShop(ShopSnapshot shop) {
    stock.clear();
    if (shop != null) stock.setAll(editedStock.getOrDefault(shop.id(), shop.itemIds()));
  }

  private void addSelectedItem() {
    ItemSnapshot item = availableItems.getValue();
    if (item == null || shops.getValue() == null) return;
    stock.add(item.id());
    remember();
  }

  private void removeSelectedItems() {
    if (shops.getValue() == null) return;
    stock.removeAll(new ArrayList<>(stockList.getSelectionModel().getSelectedItems()));
    remember();
  }

  private void replaceSelectedItem() {
    ItemSnapshot item = availableItems.getValue();
    Integer selected = stockList.getSelectionModel().getSelectedItem();
    if (item == null || selected == null) {
      state.status("Select one stocked item and one replacement item.");
      return;
    }
    int index = stockList.getSelectionModel().getSelectedIndex();
    stock.set(index, item.id());
    remember();
  }

  private void resetShop() {
    ShopSnapshot shop = shops.getValue();
    if (shop == null) return;
    editedStock.remove(shop.id());
    stock.setAll(shop.itemIds());
    state.status("Reset " + shop.name() + ".");
  }

  private void remember() {
    ShopSnapshot shop = shops.getValue();
    if (shop != null) editedStock.put(shop.id(), List.copyOf(stock));
  }

  private void buildPatch(Button build) {
    List<ShopEdit> edits = changedEdits();
    if (edits.isEmpty()) {
      state.status("No Children of Apocalypse shop edits to patch.");
      return;
    }
    Task<BuildResult> task = new Task<>() {
      @Override protected BuildResult call() throws Exception {
        return EditorPatchService.buildShopPatch(state.buildWorkspace(), edits);
      }
    };
    build.disableProperty().bind(task.runningProperty());
    task.setOnSucceeded(_ -> {
      BuildResult result = task.getValue();
      state.status("Wrote %s (%s)".formatted(result.outputJar(), result.summary()));
      build.disableProperty().unbind(); build.setDisable(false);
    });
    task.setOnFailed(_ -> {
      Throwable error = task.getException();
      state.status("Shop patch failed: " + error.getMessage());
      FxDialogs.showError("Unable to build shop patch", error);
      build.disableProperty().unbind(); build.setDisable(false);
    });
    Thread thread = new Thread(task, "vddoh-fx-shop-patch");
    thread.setDaemon(true); thread.start();
  }

  private List<ShopEdit> changedEdits() {
    return shops.getItems().stream()
        .filter(shop -> editedStock.containsKey(shop.id()) && !editedStock.get(shop.id()).equals(shop.itemIds()))
        .map(shop -> ShopEdit.builder().shopId(shop.id()).eventOffset(shop.eventOffset()).itemIds(editedStock.get(shop.id())).build())
        .toList();
  }

  private ListCell<Integer> itemCell() {
    return new ListCell<>() {
      @Override protected void updateItem(Integer itemId, boolean empty) {
        super.updateItem(itemId, empty);
        ItemSnapshot item = itemId == null ? null : itemsById.get(itemId);
        setText(empty ? null : item == null ? "Unknown item " + itemId : "%d — %s".formatted(itemId, item.name()));
      }
    };
  }

  private static StringConverter<ShopSnapshot> shopConverter() {
    return new StringConverter<>() {
      @Override public String toString(ShopSnapshot shop) { return shop == null ? "" : shop.name(); }
      @Override public ShopSnapshot fromString(String ignored) { return null; }
    };
  }

  private static StringConverter<ItemSnapshot> itemConverter() {
    return new StringConverter<>() {
      @Override public String toString(ItemSnapshot item) { return item == null ? "" : "%d — %s".formatted(item.id(), item.name()); }
      @Override public ItemSnapshot fromString(String ignored) { return null; }
    };
  }
}
