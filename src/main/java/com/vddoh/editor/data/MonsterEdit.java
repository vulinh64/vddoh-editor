package com.vddoh.editor.data;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterEdit(
    int monsterId,
    int experience,
    int filar,
    int deathValue,
    int effectId,
    int strength,
    int spirit,
    int vitality,
    int speed,
    List<MonsterArrayEntryEdit> arrayEdits) {

  public MonsterEdit {
    arrayEdits = arrayEdits == null ? Collections.emptyList() : List.copyOf(arrayEdits);
  }
}
