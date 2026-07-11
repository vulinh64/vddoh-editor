package com.vddoh.editor.service;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record TalentSection(List<TalentOffsets> offsets, int nextOffset) {

  public TalentSection {
    offsets = offsets == null ? Collections.emptyList() : offsets;
  }
}
