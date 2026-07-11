package com.vddoh.editor.fx.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;

public final class FxStickyTableSplit {

  private static final String SCROLL_SYNCED = "vddoh.stickyScrollSynced";

  private FxStickyTableSplit() {}

  public static <T> SplitPane horizontal(
      TableView<T> sticky, TableView<T> scrollable, double divider) {
    sticky.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    scrollable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    sticky
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((_, _, item) -> selectIfDifferent(scrollable, item));
    scrollable
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((_, _, item) -> selectIfDifferent(sticky, item));
    Platform.runLater(() -> syncVerticalScrollbars(sticky, scrollable));

    SplitPane split = new SplitPane(sticky, scrollable);
    split.setDividerPositions(divider);
    return split;
  }

  private static <T> void selectIfDifferent(TableView<T> table, T item) {
    if (table.getSelectionModel().getSelectedItem() != item) {
      table.getSelectionModel().select(item);
    }
  }

  private static void syncVerticalScrollbars(TableView<?> left, TableView<?> right) {
    ScrollBar leftBar = verticalScrollBar(left);
    ScrollBar rightBar = verticalScrollBar(right);
    if (leftBar != null
        && rightBar != null
        && !Boolean.TRUE.equals(left.getProperties().get(SCROLL_SYNCED))) {
      leftBar.valueProperty().bindBidirectional(rightBar.valueProperty());
      left.getProperties().put(SCROLL_SYNCED, true);
      right.getProperties().put(SCROLL_SYNCED, true);
    }
  }

  private static ScrollBar verticalScrollBar(TableView<?> table) {
    for (Node node : table.lookupAll(".scroll-bar")) {
      if (node instanceof ScrollBar scrollBar
          && scrollBar.getOrientation() == Orientation.VERTICAL) {
        return scrollBar;
      }
    }
    return null;
  }
}
