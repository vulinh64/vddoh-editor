package com.vddoh.editor;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemSnapshot(
    int id,
    String name,
    int rawType,
    int category,
    int subtype,
    String slotLabel,
    String allowedClasses,
    int price,
    int icon,
    int hpRestore,
    int resourceRestore,
    int hpBonus,
    int resourceBonus,
    int weaponReach,
    int weaponMode,
    String notes,
    List<ItemEffectSnapshot> effects) {

  public ItemSnapshot {
    effects = effects == null ? Collections.emptyList() : List.copyOf(effects);
  }
}
