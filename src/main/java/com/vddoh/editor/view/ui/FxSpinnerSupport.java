package com.vddoh.editor.view.ui;

import javafx.beans.property.IntegerProperty;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.KeyCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FxSpinnerSupport {

  public static void commitOnEnter(Spinner<Integer> spinner) {
    spinner
        .getEditor()
        .setOnKeyPressed(
            event -> {
              if (event.getCode() != KeyCode.ENTER) {
                return;
              }
              SpinnerValueFactory<Integer> valueFactory = spinner.getValueFactory();
              valueFactory.setValue(
                  valueFactory.getConverter().fromString(spinner.getEditor().getText()));
              event.consume();
            });
  }

  public static void syncFromProperty(
      Spinner<Integer> spinner, IntegerProperty property, boolean[] updatingFromProperty) {
    property.addListener(
        (_, _, value) -> {
          if (spinner.getValueFactory().getValue().equals(value.intValue())) {
            return;
          }
          updatingFromProperty[0] = true;
          spinner.getValueFactory().setValue(value.intValue());
          updatingFromProperty[0] = false;
        });
  }
}
