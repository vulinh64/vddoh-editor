package com.vddoh.editor;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
record TalentSections(List<TalentOffsets> group, List<TalentOffsets> hero) {

  public TalentSections {
    group = group == null ? Collections.emptyList() : group;
    hero = hero == null ? Collections.emptyList() : hero;
  }
}
