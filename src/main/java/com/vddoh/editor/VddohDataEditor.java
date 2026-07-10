package com.vddoh.editor;

import javax.swing.SwingUtilities;

public final class VddohDataEditor {

  void main() {
    SwingUtilities.invokeLater(
        () -> {
          try {
            new EditorFrame().setVisible(true);
          } catch (Exception ex) {
            EditorSupport.showError(null, ex);
          }
        });
  }
}
