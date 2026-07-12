package com.vddoh.editor.data;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record SkillEdit(int skillId, int levelIndex, int cost, List<SkillEffectEdit> effects) {

  public SkillEdit {
    effects = effects == null ? Collections.emptyList() : List.copyOf(effects);
  }
}
