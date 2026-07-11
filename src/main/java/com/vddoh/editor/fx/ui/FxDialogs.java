package com.vddoh.editor.fx.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

public final class FxDialogs {

  private FxDialogs() {}

  public static void showError(String title, Throwable error) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(error.getMessage());
    StringWriter out = new StringWriter();
    error.printStackTrace(new PrintWriter(out));
    TextArea details = new TextArea(out.toString());
    details.setEditable(false);
    details.setWrapText(false);
    alert.getDialogPane().setExpandableContent(details);
    alert.showAndWait();
  }
}
