package com.vddoh.editor.view.items;

import com.vddoh.editor.data.ItemEffectEdit;
import com.vddoh.editor.data.ItemEffectSnapshot;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxItemEffectViewModel(
    ItemEffectSnapshot effect, IntegerProperty numericValue, IntegerProperty effectKindValue) {

  private static final Pattern SKILL_ID = Pattern.compile("skill id=(\\d+)");

  public FxItemEffectViewModel(ItemEffectSnapshot effect) {
    this(
        effect,
        new SimpleIntegerProperty(effect.numericValue()),
        new SimpleIntegerProperty(effect.effectKind()));
  }

  public String side() {
    return effect.side();
  }

  public String type() {
    if (canEditEffectKind()) {
      return switch (effectKind()) {
        case 1, 2, 3, 4 -> "Elemental Damage";
        case 5 -> "Blood Drain (blooded targets only)";
        default -> "Flat stat/damage";
      };
    }
    return effect.type();
  }

  public String target() {
    if (canEditEffectKind()) {
      return switch (effectKind()) {
        case 1 -> "Fire damage";
        case 2 -> "Ice damage";
        case 3 -> "Light damage";
        case 4 -> "Shadow damage";
        case 5 -> "Blood drain (no recovery from bloodless targets)";
        default -> "Physical";
      };
    }
    return effect.target();
  }

  public String value() {
    return effect.editable() ? String.valueOf(numericValue.get()) : effect.value();
  }

  public IntegerProperty valueProperty() {
    return numericValue;
  }

  public String editable() {
    if (canEditEffectKind() || isArmorPhysicalEffect()) {
      return "Equipment panel";
    }
    return effect.editable() ? "Yes" : "No";
  }

  public String range() {
    return effect.editable() ? "0.." + effect.max() : "";
  }

  public boolean canEditValue() {
    return effect.editable();
  }

  public boolean canEditValueInTable() {
    return canEditValue() && !canEditEffectKind() && !isArmorPhysicalEffect();
  }

  public boolean isArmorPhysicalEffect() {
    return "Armor value".equals(effect.type())
        && "Physical".equals(effect.target())
        && effect.raw().startsWith("int_arr_a[");
  }

  public int effectKind() {
    return effectKindValue.get();
  }

  public boolean canEditEffectKind() {
    return effect.effectKindEditable();
  }

  public IntegerProperty effectKindProperty() {
    return effectKindValue;
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
    return effect.editable()
        && (numericValue.get() != effect.numericValue()
            || (canEditEffectKind() && effectKind() != effect.effectKind()));
  }

  public void reset() {
    numericValue.set(effect.numericValue());
    effectKindValue.set(effect.effectKind());
  }

  public ItemEffectEdit toEdit() {
    int value = checked(numericValue.get(), effect.max(), effect.raw());
    Integer changedKind =
        canEditEffectKind() && effectKind() != effect.effectKind() ? effectKind() : null;
    return ItemEffectEdit.builder().raw(effect.raw()).value(value).effectKind(changedKind).build();
  }

  private static int checked(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
    return value;
  }
}
