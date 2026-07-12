package com.vddoh.editor.data;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterSnapshot(
    int id,
    String name,
    int experience,
    int filar,
    int deathValue,
    int effectId,
    int strength,
    int spirit,
    int vitality,
    int speed,
    int baseHp,
    int baseResource,
    int baseAttack,
    int baseDefense,
    int baseMove,
    int hitChance,
    int critOrDamage,
    int evadeOrGuard,
    int packedChance,
    int packedTailA,
    int packedTailB,
    int actionCount,
    int effectCount,
    int dropCount,
    List<MonsterArrayEntrySnapshot> arrayEntries,
    String notes) {

  public MonsterSnapshot {
    arrayEntries = arrayEntries == null ? Collections.emptyList() : List.copyOf(arrayEntries);
  }
}
