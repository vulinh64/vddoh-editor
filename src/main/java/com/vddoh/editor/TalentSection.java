package com.vddoh.editor;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
record TalentSection(List<TalentOffsets> offsets, int nextOffset) {

  public TalentSection {
    offsets = offsets == null ? Collections.emptyList() : offsets;
  }
}
