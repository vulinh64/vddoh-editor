package com.vddoh.editor.data;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemEdit(
    int itemId,
    int price,
    int icon,
    int hpRestore,
    int resourceRestore,
    List<ItemEffectEdit> effectEdits) {

  public ItemEdit {
    effectEdits = effectEdits == null ? Collections.emptyList() : List.copyOf(effectEdits);
  }
}
