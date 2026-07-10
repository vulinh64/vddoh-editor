package com.vddoh.editor;

import java.io.Serial;
import java.io.Serializable;

final class SkillEffectRow implements Serializable {

  @Serial private static final long serialVersionUID = 8045045248927587306L;

  public static final String REMOVE_STATUS_LABEL = "Remove Status";

  final String type;
  final int index;
  final int targetId;
  final String target;
  final int originalValue;
  final boolean editable;
  final String notes;
  int value;

  SkillEffectRow(
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

  boolean isStatus() {
    return "Inflict Status".equals(type) || REMOVE_STATUS_LABEL.equals(type);
  }

  int displayValue() {
    return REMOVE_STATUS_LABEL.equals(type) ? Math.abs(value) : value;
  }

  void setDisplayValue(int value) {
    this.value = REMOVE_STATUS_LABEL.equals(type) ? -Math.abs(value) : value;
  }

  int encodedValue() {
    return value;
  }

  boolean changed() {
    return editable && value != originalValue;
  }

  void reset() {
    value = originalValue;
  }
}
