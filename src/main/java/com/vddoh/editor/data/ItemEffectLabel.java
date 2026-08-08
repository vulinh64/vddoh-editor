package com.vddoh.editor.data;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ItemEffectLabel {
  ATTACK_BONUS("Attack bonus"),
  ARMOR_VALUE("Armor value"),
  ANTI_STATUS("Anti-%s");

  private final String displayName;

  public static String packedStatTarget(int category, int statId, String rawName, String fallback) {
    if (category == 2 && statId == 8 && "byte_d".equals(rawName)) {
      return ATTACK_BONUS.getDisplayName();
    }
    return fallback;
  }

  public static String packedStatExtra(int category, int statId, String rawName, String fallback) {
    if (category == 2 && statId == 8 && "byte_d".equals(rawName)) {
      return "armor-side attack bonus; stacking behavior is suspicious";
    }
    return fallback;
  }

  public static String intEffectType(int category, int kind, String fallback) {
    if (category == 2 && kind == 0) {
      return ARMOR_VALUE.getDisplayName();
    }
    if (category == 3 && kind >= 1 && kind <= 4) {
      return "Elemental Damage";
    }
    if (category == 3 && kind == 5) {
      return "Blood Drain (blooded targets only)";
    }
    return fallback;
  }

  public static String intEffectTarget(int category, int kind, String fallback) {
    if (category == 2 && kind == 0) {
      return "Physical";
    }
    if (category == 3 && kind == 0) {
      return "Physical";
    }
    return DamageEffectKind.itemEffectName(kind, fallback);
  }

  public static String statusTarget(int category, String statusName) {
    if (category == 2) {
      return ANTI_STATUS.getDisplayName().formatted(statusName.toLowerCase(Locale.ROOT));
    }
    return statusName;
  }
}
