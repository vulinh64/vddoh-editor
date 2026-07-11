package com.vddoh.editor.fx.talents;

import com.vddoh.editor.TalentEdit;
import com.vddoh.editor.TalentSnapshot;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxTalentViewModel(
    TalentSnapshot talent,
    IntegerProperty amount,
    IntegerProperty maxLevel,
    IntegerProperty heroBonus,
    IntegerProperty globalBonus,
    IntegerProperty skillUnlock,
    IntegerProperty statusBonus,
    IntegerProperty resistanceBonus) {

  public FxTalentViewModel(TalentSnapshot talent) {
    this(
        talent,
        new SimpleIntegerProperty(talent.amount()),
        new SimpleIntegerProperty(talent.maxLevel()),
        new SimpleIntegerProperty(talent.heroBonus()),
        new SimpleIntegerProperty(talent.globalBonus()),
        new SimpleIntegerProperty(talent.skillUnlock()),
        new SimpleIntegerProperty(talent.statusBonus()),
        new SimpleIntegerProperty(talent.resistanceBonus()));
  }

  public int id() {
    return talent.id();
  }

  public String scope() {
    return talent.group() ? "Group" : "Hero";
  }

  public String name() {
    return talent.name();
  }

  public String talentType() {
    return talent.talentType();
  }

  public int currentLevel() {
    return talent.currentLevel();
  }

  public IntegerProperty amountProperty() {
    return amount;
  }

  public IntegerProperty maxLevelProperty() {
    return maxLevel;
  }

  public IntegerProperty heroBonusProperty() {
    return heroBonus;
  }

  public IntegerProperty globalBonusProperty() {
    return globalBonus;
  }

  public IntegerProperty skillUnlockProperty() {
    return skillUnlock;
  }

  public IntegerProperty statusBonusProperty() {
    return statusBonus;
  }

  public IntegerProperty resistanceBonusProperty() {
    return resistanceBonus;
  }

  public String effectName() {
    return talent.effectName();
  }

  public String unlockedSkillName() {
    return talent.unlockedSkillName();
  }

  public String levelValue(int level) {
    if (level < 1 || level > maxLevel.get() || skillUnlock.get() > 0) {
      return "";
    }
    if (heroBonus.get() > 0
        || statusBonus.get() > 0
        || resistanceBonus.get() > 0
        || globalBonus.get() > 0) {
      int base = heroBonus.get() == 4 ? 50 : 0;
      return String.valueOf(base + amount.get() * level);
    }
    return "";
  }

  public String notes() {
    return talent.notes();
  }

  public boolean changed() {
    return amount.get() != talent.amount()
        || maxLevel.get() != talent.maxLevel()
        || heroBonus.get() != talent.heroBonus()
        || globalBonus.get() != talent.globalBonus()
        || skillUnlock.get() != talent.skillUnlock()
        || statusBonus.get() != talent.statusBonus()
        || resistanceBonus.get() != talent.resistanceBonus();
  }

  public void reset() {
    amount.set(talent.amount());
    maxLevel.set(talent.maxLevel());
    heroBonus.set(talent.heroBonus());
    globalBonus.set(talent.globalBonus());
    skillUnlock.set(talent.skillUnlock());
    statusBonus.set(talent.statusBonus());
    resistanceBonus.set(talent.resistanceBonus());
  }

  public TalentEdit toEdit() {
    return TalentEdit.builder()
        .group(talent.group())
        .talentId(id())
        .maxLevel(checked(maxLevel.get(), 15, "Max Level"))
        .amount(checked(amount.get(), 255, "Amount"))
        .globalBonus(checked(globalBonus.get(), 255, "Global Bonus"))
        .skillUnlock(checked(skillUnlock.get(), 255, "Skill Unlock"))
        .statusBonus(checked(statusBonus.get(), 255, "Status Bonus"))
        .resistanceBonus(checked(resistanceBonus.get(), 255, "Resistance Bonus"))
        .heroBonus(checked(heroBonus.get(), 255, "Hero Bonus"))
        .build();
  }

  public boolean matches(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase();
    return normalized.isEmpty()
        || ("%d %s %s %s %s".formatted(id(), name(), talentType(), effectName(), notes()))
            .toLowerCase()
            .contains(normalized);
  }

  private static int checked(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
    return value;
  }
}
