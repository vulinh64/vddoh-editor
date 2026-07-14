package com.vddoh.editor.view.talents;

import com.vddoh.editor.data.TalentEdit;
import com.vddoh.editor.data.TalentSnapshot;
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

  public boolean globalBonusEditable() {
    return talent.globalBonusEditable();
  }

  public IntegerProperty skillUnlockProperty() {
    return skillUnlock;
  }

  public boolean skillUnlockEditable() {
    return talent.skillUnlockEditable();
  }

  public IntegerProperty statusBonusProperty() {
    return statusBonus;
  }

  public boolean statusBonusEditable() {
    return talent.statusBonusEditable();
  }

  public IntegerProperty resistanceBonusProperty() {
    return resistanceBonus;
  }

  public boolean resistanceBonusEditable() {
    return talent.resistanceBonusEditable();
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
        .maxLevel(checked(maxLevel.get()))
        .amount(checked(amount.get(), "Amount"))
        .globalBonus(checkedOptionalLink(globalBonus.get(), globalBonusEditable(), "Global Bonus"))
        .skillUnlock(checkedOptionalLink(skillUnlock.get(), skillUnlockEditable(), "Skill Unlock"))
        .statusBonus(checkedOptionalLink(statusBonus.get(), statusBonusEditable(), "Status Bonus"))
        .resistanceBonus(
            checkedOptionalLink(
                resistanceBonus.get(), resistanceBonusEditable(), "Resistance Bonus"))
        .heroBonus(checked(heroBonus.get(), "Hero Bonus"))
        .build();
  }

  public boolean matches(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase();
    return normalized.isEmpty()
        || ("%d %s %s %s %s".formatted(id(), name(), talentType(), effectName(), notes()))
            .toLowerCase()
            .contains(normalized);
  }

  private static int checked(int value, String label) {
    if (value < 0 || value > 15) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, 15));
    }
    return value;
  }

  private static int checked(int value) {
    if (value < 1 || value > 4) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted("Max Level", 1, 4));
    }
    return value;
  }

  private static int checkedOptionalLink(int value, boolean editable, String label) {
    if (!editable) {
      return 0;
    }
    if (value < 1 || value > 256) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 1, 256));
    }
    return value;
  }
}
