package com.vddoh.editor.fx.ui;

import java.util.function.Function;
import java.util.function.ToIntFunction;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;

public final class FxTableColumns {

  private FxTableColumns() {}

  public static <T> TableColumn<T, Number> intColumn(
      String title, ToIntFunction<T> value, int width) {
    TableColumn<T, Number> column = new TableColumn<>(title);
    column.setCellValueFactory(
        cell -> new SimpleIntegerProperty(value.applyAsInt(cell.getValue())));
    column.setPrefWidth(width);
    return column;
  }

  public static <T> TableColumn<T, Integer> editableIntColumn(
      String title, Function<T, IntegerProperty> property, int width) {
    TableColumn<T, Integer> column = new TableColumn<>(title);
    column.setCellValueFactory(cell -> property.apply(cell.getValue()).asObject());
    column.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
    column.setOnEditCommit(event -> property.apply(event.getRowValue()).set(event.getNewValue()));
    column.setPrefWidth(width);
    return column;
  }

  public static <T> TableColumn<T, String> textColumn(
      String title, Function<T, ?> value, int width) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        cell -> new SimpleStringProperty(String.valueOf(value.apply(cell.getValue()))));
    column.setPrefWidth(width);
    return column;
  }
}
