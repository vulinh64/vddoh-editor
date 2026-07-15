package com.vddoh.editor.view.ui;

import com.vddoh.editor.data.ChangeColumnName;
import com.vddoh.editor.data.EditorTabName;
import com.vddoh.editor.view.FxEditorState;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.apache.commons.lang3.StringUtils;

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
    return editableIntColumn(title, null, property, width, null);
  }

  public static <T> TableColumn<T, Integer> editableIntColumn(
      String title,
      ChangeColumnName columnName,
      Function<T, IntegerProperty> property,
      int width,
      ChangeLogContext<T> changeLogContext) {
    return editableIntColumn(
        title, columnName, property, width, changeLogContext, _ -> true, IntegerEditBounds.none());
  }

  public static <T> TableColumn<T, Integer> editableIntColumn(
      String title,
      ChangeColumnName columnName,
      Function<T, IntegerProperty> property,
      int width,
      ChangeLogContext<T> changeLogContext,
      Predicate<T> editable) {
    return editableIntColumn(
        title, columnName, property, width, changeLogContext, editable, IntegerEditBounds.none());
  }

  public static <T> TableColumn<T, Integer> editableIntColumn(
      String title,
      ChangeColumnName columnName,
      Function<T, IntegerProperty> property,
      int width,
      ChangeLogContext<T> changeLogContext,
      IntegerEditBounds bounds) {
    return editableIntColumn(
        title, columnName, property, width, changeLogContext, _ -> true, bounds);
  }

  public static <T> TableColumn<T, Integer> editableIntColumn(
      String title,
      ChangeColumnName columnName,
      Function<T, IntegerProperty> property,
      int width,
      ChangeLogContext<T> changeLogContext,
      Predicate<T> editable,
      IntegerEditBounds bounds) {
    IntegerEditBounds effectiveBounds = bounds == null ? IntegerEditBounds.none() : bounds;
    TableColumn<T, Integer> column = new TableColumn<>(effectiveBounds.title(title));
    column.setCellValueFactory(cell -> property.apply(cell.getValue()).asObject());
    column.setCellFactory(
        _ ->
            editableIntegerCell(columnName, property, changeLogContext, editable, effectiveBounds));
    column.setPrefWidth(width);
    return column;
  }

  private static <T> TableCell<T, Integer> editableIntegerCell(
      ChangeColumnName columnName,
      Function<T, IntegerProperty> property,
      ChangeLogContext<T> changeLogContext,
      Predicate<T> editable,
      IntegerEditBounds bounds) {
    return new CommittingIntegerCell<>(columnName, property, changeLogContext, editable, bounds);
  }

  public static <T> TableColumn<T, String> textColumn(
      String title, Function<T, ?> value, int width) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        cell -> new SimpleStringProperty(String.valueOf(value.apply(cell.getValue()))));
    column.setPrefWidth(width);
    return column;
  }

  public record ChangeLogContext<T>(
      FxEditorState state,
      EditorTabName tabName,
      ToIntFunction<T> entryId,
      Function<T, String> entryName) {

    public void recordChange(T row, ChangeColumnName columnName, Object oldValue, Object newValue) {
      state.recordChange(
          tabName, entryId.applyAsInt(row), entryName.apply(row), columnName, oldValue, newValue);
    }

    public void status(String message) {
      state.status(message);
    }
  }

  public record IntegerEditBounds(Optional<Integer> min, Optional<Integer> max, String label) {

    public static IntegerEditBounds none() {
      return new IntegerEditBounds(Optional.empty(), Optional.empty(), null);
    }

    public static IntegerEditBounds of(int min, int max, String label) {
      return new IntegerEditBounds(Optional.of(min), Optional.of(max), label);
    }

    public String title(String title) {
      return min.isEmpty() && max.isEmpty() ? title : "%s (%s)".formatted(title, range());
    }

    public String range() {
      if (min.isPresent() && max.isPresent()) {
        return "%d..%d".formatted(min.get(), max.get());
      }
      return min.map(">=%d"::formatted)
          .orElseGet(() -> max.map("<=%d"::formatted).orElse(StringUtils.EMPTY));
    }

    public boolean valid(int value) {
      return min.map(lower -> value >= lower).orElse(true)
          && max.map(upper -> value <= upper).orElse(true);
    }

    public String message() {
      if (min.isPresent() && max.isPresent()) {
        return "%s must be %d..%d".formatted(displayLabel(), min.get(), max.get());
      }
      return min.map(integer -> "%s must be >= %d".formatted(displayLabel(), integer))
          .orElseGet(
              () ->
                  max.map(integer -> "%s must be <= %d".formatted(displayLabel(), integer))
                      .orElseGet(() -> "%s is out of range".formatted(displayLabel())));
    }

    private String displayLabel() {
      return label == null ? "Value" : label;
    }
  }

  private static final class CommittingIntegerCell<T> extends TableCell<T, Integer> {

    private final Predicate<T> editable;
    private final ChangeColumnName columnName;
    private final Function<T, IntegerProperty> property;
    private final ChangeLogContext<T> changeLogContext;
    private final IntegerEditBounds bounds;
    private TextField editor;

    CommittingIntegerCell(
        ChangeColumnName columnName,
        Function<T, IntegerProperty> property,
        ChangeLogContext<T> changeLogContext,
        Predicate<T> editable,
        IntegerEditBounds bounds) {
      this.columnName = columnName;
      this.property = property;
      this.changeLogContext = changeLogContext;
      this.editable = editable;
      this.bounds = bounds == null ? IntegerEditBounds.none() : bounds;
    }

    @Override
    public void startEdit() {
      T row = getTableRow() == null ? null : getTableRow().getItem();
      if (row == null || !editable.test(row)) {
        return;
      }
      super.startEdit();
      if (editor == null) {
        editor = new TextField();
        editor.setOnAction(_ -> commitEditorValue());
        editor.setOnKeyPressed(
            event -> {
              if (event.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
              }
            });
        editor
            .focusedProperty()
            .addListener(
                (_, _, focused) -> {
                  if (!focused && isEditing()) {
                    commitEditorValue();
                  }
                });
      }
      editor.setText(format(getItem()));
      setText(null);
      setGraphic(editor);
      setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      editor.requestFocus();
      editor.selectAll();
    }

    @Override
    public void cancelEdit() {
      super.cancelEdit();
      setText(format(getItem()));
      setGraphic(null);
      setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    @Override
    protected void updateItem(Integer item, boolean empty) {
      super.updateItem(item, empty);
      if (empty) {
        setText(null);
        setGraphic(null);
        return;
      }
      if (isEditing()) {
        editor.setText(format(item));
        setText(null);
        setGraphic(editor);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return;
      }
      setText(format(item));
      setGraphic(null);
      setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    private void commitEditorValue() {
      try {
        int oldValue = getItem() == null ? 0 : getItem();
        int newValue = Integer.parseInt(editor.getText().trim());
        T row = getTableRow() == null ? null : getTableRow().getItem();
        if (row != null && property != null && editable.test(row)) {
          if (!bounds.valid(newValue)) {
            if (changeLogContext != null) {
              changeLogContext.status(bounds.message());
            }
            cancelEdit();
            return;
          }
          property.apply(row).set(newValue);
          if (changeLogContext != null) {
            changeLogContext.recordChange(row, columnName, oldValue, newValue);
          }
        }
        commitEdit(newValue);
      } catch (NumberFormatException _) {
        cancelEdit();
      }
    }

    private static String format(Integer value) {
      return value == null ? org.apache.commons.lang3.StringUtils.EMPTY : String.valueOf(value);
    }
  }
}
