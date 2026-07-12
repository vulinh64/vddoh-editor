package com.vddoh.editor.view.monsters;

import com.vddoh.editor.data.MonsterEdit;
import com.vddoh.editor.data.MonsterSnapshot;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public record FxMonsterViewModel(
    MonsterSnapshot monster,
    IntegerProperty experience,
    IntegerProperty filar,
    IntegerProperty deathValue,
    IntegerProperty effectId,
    IntegerProperty strength,
    IntegerProperty spirit,
    IntegerProperty vitality,
    IntegerProperty speed,
    ObservableList<FxMonsterArrayEntryViewModel> arrayEntries) {

  public FxMonsterViewModel(MonsterSnapshot monster) {
    this(
        monster,
        new SimpleIntegerProperty(monster.experience()),
        new SimpleIntegerProperty(monster.filar()),
        new SimpleIntegerProperty(monster.deathValue()),
        new SimpleIntegerProperty(monster.effectId()),
        new SimpleIntegerProperty(monster.strength()),
        new SimpleIntegerProperty(monster.spirit()),
        new SimpleIntegerProperty(monster.vitality()),
        new SimpleIntegerProperty(monster.speed()),
        FXCollections.observableArrayList(
            monster.arrayEntries().stream().map(FxMonsterArrayEntryViewModel::new).toList()));
  }

  public int id() {
    return monster.id();
  }

  public String name() {
    return monster.name();
  }

  public IntegerProperty experienceProperty() {
    return experience;
  }

  public IntegerProperty filarProperty() {
    return filar;
  }

  public IntegerProperty deathValueProperty() {
    return deathValue;
  }

  public IntegerProperty effectIdProperty() {
    return effectId;
  }

  public IntegerProperty strengthProperty() {
    return strength;
  }

  public IntegerProperty spiritProperty() {
    return spirit;
  }

  public IntegerProperty vitalityProperty() {
    return vitality;
  }

  public IntegerProperty speedProperty() {
    return speed;
  }

  public int baseHp() {
    return (vitality.get() * 70 + strength.get() * 30) * 12 / 100;
  }

  public int baseResource() {
    return (spirit.get() * 70 + vitality.get() * 30) * 12 / 100;
  }

  public int baseAttack() {
    return Math.max(0, strength.get() * 5 - 9);
  }

  public int baseDefense() {
    return Math.max(0, speed.get() * 3 + strength.get() - 18);
  }

  public int baseMove() {
    return 2 + speed.get() / 5;
  }

  public int hitChance() {
    return monster.hitChance();
  }

  public int critOrDamage() {
    return monster.critOrDamage();
  }

  public int evadeOrGuard() {
    return monster.evadeOrGuard();
  }

  public int packedChance() {
    return monster.packedChance();
  }

  public int actionCount() {
    return monster.actionCount();
  }

  public int effectCount() {
    return monster.effectCount();
  }

  public int dropCount() {
    return monster.dropCount();
  }

  public String notes() {
    return monster.notes();
  }

  public ObservableList<FxMonsterArrayEntryViewModel> arrayEntryRows() {
    return arrayEntries;
  }

  public boolean changed() {
    return experience.get() != monster.experience()
        || filar.get() != monster.filar()
        || deathValue.get() != monster.deathValue()
        || effectId.get() != monster.effectId()
        || strength.get() != monster.strength()
        || spirit.get() != monster.spirit()
        || vitality.get() != monster.vitality()
        || speed.get() != monster.speed()
        || arrayEntries.stream().anyMatch(FxMonsterArrayEntryViewModel::changed);
  }

  public void reset() {
    experience.set(monster.experience());
    filar.set(monster.filar());
    deathValue.set(monster.deathValue());
    effectId.set(monster.effectId());
    strength.set(monster.strength());
    spirit.set(monster.spirit());
    vitality.set(monster.vitality());
    speed.set(monster.speed());
    arrayEntries.forEach(FxMonsterArrayEntryViewModel::reset);
  }

  public MonsterEdit toEdit() {
    return MonsterEdit.builder()
        .monsterId(id())
        .experience(checked(experience.get(), 4095, "EXP"))
        .filar(checked(filar.get(), 4095, "Filar"))
        .deathValue(checked(deathValue.get(), 127, "Soul Restore"))
        .effectId(checked(effectId.get(), 255, "Effect ID"))
        .strength(checked(strength.get(), 127, "STR-like"))
        .spirit(checked(spirit.get(), 127, "SPI-like"))
        .vitality(checked(vitality.get(), 127, "VIT-like"))
        .speed(checked(speed.get(), 127, "SPD-like"))
        .arrayEdits(
            arrayEntries.stream()
                .filter(FxMonsterArrayEntryViewModel::changed)
                .map(FxMonsterArrayEntryViewModel::toEdit)
                .toList())
        .build();
  }

  public boolean matches(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase();
    return normalized.isEmpty()
        || ("%d %s %s".formatted(id(), name(), notes())).toLowerCase().contains(normalized);
  }

  private static int checked(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
    return value;
  }
}
