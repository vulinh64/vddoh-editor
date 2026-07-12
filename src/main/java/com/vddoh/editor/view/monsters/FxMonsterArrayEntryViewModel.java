package com.vddoh.editor.view.monsters;

import com.vddoh.editor.data.MonsterArrayEntryEdit;
import com.vddoh.editor.data.MonsterArrayEntrySnapshot;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxMonsterArrayEntryViewModel(MonsterArrayEntrySnapshot entry, IntegerProperty value) {

  public FxMonsterArrayEntryViewModel(MonsterArrayEntrySnapshot entry) {
    this(entry, new SimpleIntegerProperty(entry.value()));
  }

  public String side() {
    return entry.side();
  }

  public String type() {
    return entry.type();
  }

  public int index() {
    return entry.index();
  }

  public String target() {
    return entry.target();
  }

  public IntegerProperty valueProperty() {
    return value;
  }

  public String editable() {
    return entry.editable() ? "Yes" : "No";
  }

  public String raw() {
    return entry.raw();
  }

  public boolean changed() {
    return entry.editable() && value.get() != entry.value();
  }

  public void reset() {
    value.set(entry.value());
  }

  public MonsterArrayEntryEdit toEdit() {
    int checked = checked(value.get(), entry.max(), entry.raw());
    return MonsterArrayEntryEdit.builder().raw(entry.raw()).value(checked).build();
  }

  private static int checked(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
    return value;
  }
}
