package com.vddoh.editor.fx.heroes;

import com.vddoh.editor.HeroEdit;
import com.vddoh.editor.HeroSnapshot;
import com.vddoh.editor.StatCurveEdit;
import com.vddoh.editor.StatCurveSnapshot;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxHeroViewModel(
    HeroSnapshot hero,
    FxStatCurveViewModel strength,
    FxStatCurveViewModel spirit,
    FxStatCurveViewModel vitality,
    FxStatCurveViewModel speed,
    IntegerProperty levelCap,
    IntegerProperty baseCritChance,
    IntegerProperty baseCritDamage) {

  public FxHeroViewModel(HeroSnapshot hero) {
    this(
        hero,
        new FxStatCurveViewModel(hero.strength()),
        new FxStatCurveViewModel(hero.spirit()),
        new FxStatCurveViewModel(hero.vitality()),
        new FxStatCurveViewModel(hero.speed()),
        new SimpleIntegerProperty(hero.levelCap()),
        new SimpleIntegerProperty(hero.baseCritChance()),
        new SimpleIntegerProperty(hero.baseCritDamage()));
  }

  public int id() {
    return hero.id();
  }

  public String name() {
    return hero.name();
  }

  public IntegerProperty levelCapProperty() {
    return levelCap;
  }

  public IntegerProperty baseCritChanceProperty() {
    return baseCritChance;
  }

  public IntegerProperty baseCritDamageProperty() {
    return baseCritDamage;
  }

  public int baseHp() {
    return (vitality.start().get() * 70 + strength.start().get() * 30) * 12 / 100;
  }

  public int baseResource() {
    return (spirit.start().get() * 70 + vitality.start().get() * 30) * 12 / 100;
  }

  public int baseAttack() {
    return Math.max(0, strength.start().get() * 5 - 9);
  }

  public int baseDefense() {
    return Math.max(0, speed.start().get() * 3 + strength.start().get() - 18);
  }

  public int baseMove() {
    return 2 + speed.start().get() / 5;
  }

  public int baseRegen() {
    return hero.baseRegen();
  }

  public int baseEvasion() {
    return hero.baseEvasion();
  }

  public int strengthAtCap() {
    return strength.valueAtLevel(levelCap.get());
  }

  public int spiritAtCap() {
    return spirit.valueAtLevel(levelCap.get());
  }

  public int vitalityAtCap() {
    return vitality.valueAtLevel(levelCap.get());
  }

  public int speedAtCap() {
    return speed.valueAtLevel(levelCap.get());
  }

  public String notes() {
    return hero.notes();
  }

  public boolean changed() {
    return strength.changed()
        || spirit.changed()
        || vitality.changed()
        || speed.changed()
        || levelCap.get() != hero.levelCap()
        || baseCritChance.get() != hero.baseCritChance()
        || baseCritDamage.get() != hero.baseCritDamage();
  }

  public void reset() {
    strength.reset();
    spirit.reset();
    vitality.reset();
    speed.reset();
    levelCap.set(hero.levelCap());
    baseCritChance.set(hero.baseCritChance());
    baseCritDamage.set(hero.baseCritDamage());
  }

  public HeroEdit toEdit() {
    return HeroEdit.builder()
        .heroId(id())
        .strength(strength.toEdit())
        .spirit(spirit.toEdit())
        .vitality(vitality.toEdit())
        .speed(speed.toEdit())
        .levelCap(checked(levelCap.get(), 127, "Level Cap"))
        .baseCritChance(checked(baseCritChance.get(), 255, "Base Crit %"))
        .baseCritDamage(checked(baseCritDamage.get(), 255, "Base Crit Dmg %"))
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

  public record FxStatCurveViewModel(
      StatCurveSnapshot curve,
      IntegerProperty start,
      IntegerProperty target,
      IntegerProperty shape) {

    public FxStatCurveViewModel(StatCurveSnapshot curve) {
      this(
          curve,
          new SimpleIntegerProperty(curve.start()),
          new SimpleIntegerProperty(curve.target()),
          new SimpleIntegerProperty(curve.curve()));
    }

    public boolean changed() {
      return start.get() != curve.start()
          || target.get() != curve.target()
          || shape.get() != curve.curve();
    }

    public void reset() {
      start.set(curve.start());
      target.set(curve.target());
      shape.set(curve.curve());
    }

    public int valueAtLevel(int level) {
      return level
              * (target.get() - start.get())
              * (level * (100 - shape.get()) / 99 + shape.get())
              / 99
              / 100
          + start.get();
    }

    public StatCurveEdit toEdit() {
      return StatCurveEdit.builder()
          .start(checked(start.get(), 127, "Stat Start"))
          .target(checked(target.get(), 127, "Stat Target"))
          .curve(checked(shape.get(), 255, "Stat Growth Curve"))
          .build();
    }
  }
}
