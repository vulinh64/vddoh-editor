package com.vddoh.editor;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record SkillLevelSnapshot(
    int skillId,
    String skillName,
    int levelIndex,
    int cost,
    int areaShape,
    int areaWidth,
    int areaHeight,
    int range,
    boolean relativeAreaGrowth,
    String notes,
    List<SkillEffectSnapshot> effects) {

  public SkillLevelSnapshot {
    effects = effects == null ? Collections.emptyList() : List.copyOf(effects);
  }
}
