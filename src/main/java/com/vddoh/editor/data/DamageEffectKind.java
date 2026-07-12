package com.vddoh.editor.data;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DamageEffectKind {
  PHYSICAL("physischer schaden", "Physical Damage"),
  FIRE("feuer", "Fire damage"),
  ICE("eis", "Ice damage"),
  LIGHT("licht", "Light damage"),
  SHADOW("schatten", "Shadow damage"),
  BLOOD_DRAIN("blutsaugen", "Blood drain");

  private final String sourceName;
  private final String displayName;

  public static String displayName(String decodedName) {
    for (DamageEffectKind kind : values()) {
      if (kind.matches(decodedName)) {
        return kind.getDisplayName();
      }
    }
    return decodedName;
  }

  public static String displayName(String decodedName, String skillName) {
    if (PHYSICAL.matches(decodedName)) {
      return physicalDisplayName(skillName);
    }
    return displayName(decodedName);
  }

  public static String elementName(int id) {
    return switch (id) {
      case 1 -> FIRE.getDisplayName();
      case 2 -> ICE.getDisplayName();
      case 3 -> LIGHT.getDisplayName();
      case 4 -> SHADOW.getDisplayName();
      case 5 -> BLOOD_DRAIN.getDisplayName();
      default -> null;
    };
  }

  private boolean matches(String decodedName) {
    return normalize(decodedName).equals(sourceName);
  }

  public static String itemEffectName(int kind, String fallbackName) {
    String elementName = elementName(kind);
    return elementName == null ? fallbackName : elementName;
  }

  private static String physicalDisplayName(String skillName) {
    String normalized = normalize(skillName);
    if (normalized.equals("heal")
        || normalized.equals("heal aura")
        || normalized.equals("renew")
        || normalized.equals("healing flask")
        || normalized.equals("healing flash")) {
      return "HP Recovery";
    }
    if (normalized.equals("healing haze")) {
      return PHYSICAL.getDisplayName();
    }
    if (normalized.contains("heal") || normalized.contains("renew")) {
      return "Physical HP";
    }
    return PHYSICAL.getDisplayName();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
  }
}
