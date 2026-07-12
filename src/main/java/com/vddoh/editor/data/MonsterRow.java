package com.vddoh.editor.data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterRow(
    int id,
    String name,
    int experience,
    int filar,
    int deathValue,
    int effectId,
    int packedTailA,
    int packedTailB,
    int strength,
    int spirit,
    int vitality,
    int speed,
    int hitChance,
    int critOrDamage,
    int evadeOrGuard,
    int packedChance,
    int actionCount,
    int effectCount,
    int dropCount,
    List<MonsterArrayEntrySnapshot> arrayEntries,
    String notes)
    implements Serializable {

  public MonsterRow {
    arrayEntries = arrayEntries == null ? Collections.emptyList() : List.copyOf(arrayEntries);
  }

  public static MonsterRow of(
      int id,
      String name,
      int experience,
      int filar,
      int deathValue,
      int effectId,
      int packedTailA,
      int packedTailB,
      int strength,
      int spirit,
      int vitality,
      int speed,
      int hitChance,
      int critOrDamage,
      int evadeOrGuard,
      int packedChance,
      int actionCount,
      int effectCount,
      int dropCount,
      List<MonsterArrayEntrySnapshot> arrayEntries) {
    return MonsterRow.builder()
        .id(id)
        .name(name)
        .experience(experience)
        .filar(filar)
        .deathValue(deathValue)
        .effectId(effectId)
        .packedTailA(packedTailA)
        .packedTailB(packedTailB)
        .strength(strength)
        .spirit(spirit)
        .vitality(vitality)
        .speed(speed)
        .hitChance(hitChance)
        .critOrDamage(critOrDamage)
        .evadeOrGuard(evadeOrGuard)
        .packedChance(packedChance)
        .actionCount(actionCount)
        .effectCount(effectCount)
        .dropCount(dropCount)
        .arrayEntries(arrayEntries)
        .notes(
            "editable: EXP, Filar, Soul Restore, Effect ID, STR/SPI/VIT/SPD, existing effects/resistances/drops")
        .build();
  }

  int baseHp() {
    return (vitality * 70 + strength * 30) * 12 / 100;
  }

  int baseResource() {
    return (spirit * 70 + vitality * 30) * 12 / 100;
  }

  int baseAttack() {
    return Math.max(0, strength * 5 - 9);
  }

  int baseDefense() {
    return Math.max(0, speed * 3 + strength - 18);
  }

  int baseMove() {
    return 2 + speed / 5;
  }
}
