package com.vddoh.editor.data;

import java.io.Serial;
import java.io.Serializable;

public final class HeroRow implements Serializable {

  @Serial private static final long serialVersionUID = 4585202215126762132L;

  public static final int BASE_EVASION = 5;
  public static final int BASE_HP_REGEN = 1;

  final int id;
  final String name;
  final StatCurve strength;
  final StatCurve spirit;
  final StatCurve vitality;
  final StatCurve speed;
  final String notes;
  int levelCap;
  int baseCritChance;
  int baseCritDamage;

  HeroRow(
      int id,
      String name,
      StatCurve strength,
      StatCurve spirit,
      StatCurve vitality,
      StatCurve speed,
      int levelCap,
      int baseCritChance,
      int baseCritDamage,
      String notes) {
    this.id = id;
    this.name = name;
    this.strength = strength;
    this.spirit = spirit;
    this.vitality = vitality;
    this.speed = speed;
    this.levelCap = levelCap;
    this.baseCritChance = baseCritChance;
    this.baseCritDamage = baseCritDamage;
    this.notes = notes;
  }

  int baseHp() {
    return (vitality.start * 70 + strength.start * 30) * 12 / 100;
  }

  int baseResource() {
    return (spirit.start * 70 + vitality.start * 30) * 12 / 100;
  }

  int baseAttack() {
    return Math.max(0, strength.start * 5 - 9);
  }

  int baseDefense() {
    return Math.max(0, speed.start * 3 + strength.start - 18);
  }

  int baseMove() {
    return 2 + speed.start / 5;
  }

  int previewLevel() {
    return Math.max(1, levelCap);
  }

  int strengthAtCap() {
    return strength.valueAtLevel(previewLevel());
  }

  int spiritAtCap() {
    return spirit.valueAtLevel(previewLevel());
  }

  int vitalityAtCap() {
    return vitality.valueAtLevel(previewLevel());
  }

  int speedAtCap() {
    return speed.valueAtLevel(previewLevel());
  }
}
