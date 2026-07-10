package com.vddoh.editor;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
record SkillPatch(int skillId, int levelIndex, int cost, List<SkillEffectRow> effects) {

  public SkillPatch {
    effects = effects == null ? Collections.emptyList() : effects;
  }
}
