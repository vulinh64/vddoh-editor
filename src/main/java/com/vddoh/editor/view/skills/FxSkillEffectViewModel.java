package com.vddoh.editor.view.skills;

import com.vddoh.editor.data.SkillEffectEdit;
import com.vddoh.editor.data.SkillEffectSnapshot;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxSkillEffectViewModel(SkillEffectSnapshot effect, IntegerProperty value) {

  public FxSkillEffectViewModel(SkillEffectSnapshot effect) {
    this(effect, new SimpleIntegerProperty(effect.value()));
  }

  public String type() {
    return effect.type();
  }

  public int index() {
    return effect.index();
  }

  public int targetId() {
    return effect.targetId();
  }

  public String target() {
    return effect.target();
  }

  public IntegerProperty valueProperty() {
    return value;
  }

  public String editable() {
    return effect.editable() ? "Yes" : "No";
  }

  public String notes() {
    return effect.notes();
  }

  public boolean changed() {
    return effect.editable() && value.get() != effect.value();
  }

  public void reset() {
    value.set(effect.value());
  }

  public SkillEffectEdit toEdit() {
    return SkillEffectEdit.builder()
        .type(type())
        .index(index())
        .targetId(targetId())
        .target(target())
        .originalValue(effect.value())
        .value(value.get())
        .editable(effect.editable())
        .notes(notes())
        .build();
  }
}
