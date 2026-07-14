package com.vddoh.editor.view.ui;

import com.vddoh.editor.data.ChangeLogEntry;
import com.vddoh.editor.view.FxEditorState;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.SortedList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class FxChangeLogDialog {

  private FxChangeLogDialog() {}

  public static void show(Stage owner, FxEditorState state) {
    Stage dialog = new Stage();
    dialog.initOwner(owner);
    dialog.initModality(Modality.NONE);
    dialog.setTitle("Change Log");

    TableView<ChangeLogEntry> table = table(state);
    Button clear = new Button("Clear");
    clear.setOnAction(_ -> state.clearChangeLog());
    Button close = new Button("Close");
    close.setOnAction(_ -> dialog.close());
    HBox actions = new HBox(8, clear, close);
    actions.getStyleClass().add("filter-row");

    BorderPane root = new BorderPane(table);
    root.getStyleClass().add("app-root");
    root.setBottom(actions);
    dialog.setScene(new Scene(root, 980, 520));
    dialog.show();
  }

  private static TableView<ChangeLogEntry> table(FxEditorState state) {
    TableView<ChangeLogEntry> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.setItems(new SortedList<>(state.changeLog()));
    table
        .getColumns()
        .setAll(
            java.util.List.of(
                column("Timestamp", ChangeLogEntry::formattedTimestamp, 150),
                column("Tab", entry -> entry.tabName().getLabel(), 90),
                column("ID", entry -> String.valueOf(entry.entryId()), 70),
                column("Entry", ChangeLogEntry::entryName, 260),
                column("Column", entry -> entry.columnName().getLabel(), 160),
                column("Old", ChangeLogEntry::oldValue, 120),
                column("New", ChangeLogEntry::newValue, 120)));
    return table;
  }

  private static TableColumn<ChangeLogEntry, String> column(
      String title, java.util.function.Function<ChangeLogEntry, String> value, int width) {
    TableColumn<ChangeLogEntry, String> column = new TableColumn<>(title);
    column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
    column.setPrefWidth(width);
    return column;
  }
}
