package com.vddoh.editor;

import java.io.Serializable;
import lombok.Builder;
import lombok.With;

@Builder
@With
record MonsterRow(
    int id,
    String name,
    int originalExperience,
    int originalFilar,
    int originalDeathValue,
    int originalEffectId,
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
    String notes)
    implements Serializable {

  static MonsterRow of(
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
      int dropCount) {
    return MonsterRow.builder()
        .id(id)
        .name(name)
        .originalExperience(experience)
        .originalFilar(filar)
        .originalDeathValue(deathValue)
        .originalEffectId(effectId)
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
        .notes(
            "editable: EXP, Filar, Death Value, Effect ID; base stats are derived from core stat bytes")
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

  boolean changed() {
    return experience != originalExperience
        || filar != originalFilar
        || deathValue != originalDeathValue
        || effectId != originalEffectId;
  }

  MonsterRow reset() {
    return withExperience(originalExperience)
        .withFilar(originalFilar)
        .withDeathValue(originalDeathValue)
        .withEffectId(originalEffectId);
  }
}
