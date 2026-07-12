package com.vddoh.editor.data;

import java.io.Serial;
import java.io.Serializable;

public final class SkillEffectRow implements Serializable {

  @Serial private static final long serialVersionUID = 8045045248927587306L;

  public static final String REMOVE_STATUS_LABEL = "Remove Status";

  public final String type;
  public final int index;
  public final int targetId;
  public final String target;
  public final int originalValue;
  public final boolean editable;
  public final String notes;
  public int value;

  public SkillEffectRow(
      String type,
      int index,
      int targetId,
      String target,
      int value,
      boolean editable,
      String notes) {
    this.type = type;
    this.index = index;
    this.targetId = targetId;
    this.target = target;
    this.value = originalValue = value;
    this.editable = editable;
    this.notes = notes;
  }

  public boolean isStatus() {
    return "Inflict Status".equals(type) || REMOVE_STATUS_LABEL.equals(type);
  }

  public int displayValue() {
    return REMOVE_STATUS_LABEL.equals(type) ? Math.abs(value) : value;
  }

  public void setDisplayValue(int value) {
    this.value = REMOVE_STATUS_LABEL.equals(type) ? -Math.abs(value) : value;
  }

  public int encodedValue() {
    return value;
  }

  public boolean changed() {
    return editable && value != originalValue;
  }
}
