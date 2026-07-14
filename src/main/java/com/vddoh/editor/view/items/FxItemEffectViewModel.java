package com.vddoh.editor.view.items;

import com.vddoh.editor.data.ItemEffectEdit;
import com.vddoh.editor.data.ItemEffectSnapshot;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxItemEffectViewModel(ItemEffectSnapshot effect, IntegerProperty numericValue) {

  private static final Pattern SKILL_ID = Pattern.compile("skill id=(\\d+)");

  public FxItemEffectViewModel(ItemEffectSnapshot effect) {
    this(effect, new SimpleIntegerProperty(effect.numericValue()));
  }

  public String side() {
    return effect.side();
  }

  public String type() {
    return effect.type();
  }

  public String target() {
    return effect.target();
  }

  public String value() {
    return effect.editable() ? String.valueOf(numericValue.get()) : effect.value();
  }

  public IntegerProperty valueProperty() {
    return numericValue;
  }

  public String editable() {
    return effect.editable() ? "Yes" : "No";
  }

  public String range() {
    return effect.editable() ? "0.." + effect.max() : "";
  }

  public boolean canEditValue() {
    return effect.editable();
  }

  public String extra() {
    return effect.extra();
  }

  public String raw() {
    return effect.raw();
  }

  public boolean linkedSkill() {
    return "Linked skill".equals(effect.type());
  }

  public int linkedSkillId() {
    Matcher matcher = SKILL_ID.matcher(effect.extra() == null ? "" : effect.extra());
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
  }

  public int skillLevel() {
    try {
      return Integer.parseInt(value());
    } catch (NumberFormatException _) {
      return -1;
    }
  }

  public boolean changed() {
    return effect.editable() && numericValue.get() != effect.numericValue();
  }

  public void reset() {
    numericValue.set(effect.numericValue());
  }

  public ItemEffectEdit toEdit() {
    int value = checked(numericValue.get(), effect.max(), effect.raw());
    return ItemEffectEdit.builder().raw(effect.raw()).value(value).build();
  }

  private static int checked(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
    return value;
  }
}
