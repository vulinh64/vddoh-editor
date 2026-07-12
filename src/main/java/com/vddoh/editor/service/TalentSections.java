package com.vddoh.editor.service;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record TalentSections(List<TalentOffsets> group, List<TalentOffsets> hero) {

  public TalentSections {
    group = group == null ? Collections.emptyList() : group;
    hero = hero == null ? Collections.emptyList() : hero;
  }
}
