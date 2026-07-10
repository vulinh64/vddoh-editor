package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.globalTalentName;
import static com.vddoh.editor.EditorSupport.heroBonusName;
import static com.vddoh.editor.EditorSupport.resistanceTalentName;
import static com.vddoh.editor.EditorSupport.talentNotes;

import java.io.Serial;
import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;

final class TalentRow implements Serializable {

  @Serial private static final long serialVersionUID = 8856941351648922688L;

  final boolean group;
  final int id;
  final String name;
  final int currentLevel;
  final int originalMaxLevel;
  final int originalAmount;
  final int originalGlobalBonus;
  final int originalSkillUnlock;
  final int originalStatusBonus;
  final int originalResistanceBonus;
  final int originalHeroBonus;
  final String unlockedSkillName;
  final String notes;
  int maxLevel;
  int amount;
  int globalBonus;
  int skillUnlock;
  int statusBonus;
  int resistanceBonus;
  int heroBonus;

  TalentRow(
      boolean group,
      int id,
      String name,
      int maxLevel,
      int currentLevel,
      int amount,
      int globalBonus,
      int skillUnlock,
      String unlockedSkillName,
      int statusBonus,
      int resistanceBonus,
      int heroBonus) {
    this.group = group;
    this.id = id;
    this.name = name;
    this.maxLevel = originalMaxLevel = maxLevel;
    this.currentLevel = currentLevel;
    this.amount = originalAmount = amount;
    this.globalBonus = originalGlobalBonus = globalBonus;
    this.skillUnlock = originalSkillUnlock = skillUnlock;
    this.unlockedSkillName = unlockedSkillName;
    this.statusBonus = originalStatusBonus = statusBonus;
    this.resistanceBonus = originalResistanceBonus = resistanceBonus;
    this.heroBonus = originalHeroBonus = heroBonus;
    notes =
        talentNotes(
            heroBonus,
            skillUnlock,
            unlockedSkillName,
            statusBonus,
            resistanceBonus,
            globalBonus,
            currentLevel);
  }

  String talentType() {
    if (group) {
      return "Group Talent";
    }
    if (skillUnlock > 0) {
      return "Hero Spell Unlock";
    }
    if (heroBonus > 0) {
      return "Passive Hero Bonus";
    }
    if (resistanceBonus > 0) {
      return "Resistance Bonus";
    }
    if (statusBonus > 0) {
      return "Status Bonus";
    }
    if (globalBonus > 0) {
      return "Global Bonus";
    }
    return "Unused/Unknown";
  }

  String castableSkillIdText() {
    return skillUnlock > 0 ? String.valueOf(skillUnlock - 1) : StringUtils.EMPTY;
  }

  String effectName() {
    if (skillUnlock > 0) {
      return "Unlock castable skill";
    }
    if (heroBonus > 0) {
      return heroBonusName(heroBonus);
    }
    if (statusBonus > 0) {
      return "Status bonus " + statusBonus;
    }
    if (resistanceBonus > 0) {
      return resistanceTalentName(name, resistanceBonus);
    }
    if (globalBonus > 0) {
      return globalTalentName(globalBonus);
    }
    return "Unknown";
  }

  String levelValueText(int level) {
    if (level < 1 || level > maxLevel || skillUnlock > 0) {
      return StringUtils.EMPTY;
    }
    return heroBonus > 0 || statusBonus > 0 || resistanceBonus > 0 || globalBonus > 0
        ? String.valueOf(levelValue(level))
        : StringUtils.EMPTY;
  }

  int levelValue(int level) {
    return passiveDisplayBase() + amount * level;
  }

  int passiveDisplayBase() {
    return heroBonus == 4 ? 50 : 0;
  }

  boolean changed() {
    return maxLevel != originalMaxLevel
        || amount != originalAmount
        || globalBonus != originalGlobalBonus
        || skillUnlock != originalSkillUnlock
        || statusBonus != originalStatusBonus
        || resistanceBonus != originalResistanceBonus
        || heroBonus != originalHeroBonus;
  }

  void reset() {
    maxLevel = originalMaxLevel;
    amount = originalAmount;
    globalBonus = originalGlobalBonus;
    skillUnlock = originalSkillUnlock;
    statusBonus = originalStatusBonus;
    resistanceBonus = originalResistanceBonus;
    heroBonus = originalHeroBonus;
  }
}
