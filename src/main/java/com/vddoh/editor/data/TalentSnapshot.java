package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record TalentSnapshot(
    boolean group,
    int id,
    String name,
    String talentType,
    int currentLevel,
    int maxLevel,
    int amount,
    int globalBonus,
    boolean globalBonusEditable,
    int skillUnlock,
    boolean skillUnlockEditable,
    String castableSkillId,
    String unlockedSkillName,
    int statusBonus,
    boolean statusBonusEditable,
    int resistanceBonus,
    boolean resistanceBonusEditable,
    int heroBonus,
    String effectName,
    String level1,
    String level2,
    String level3,
    String level4,
    String notes) {}
