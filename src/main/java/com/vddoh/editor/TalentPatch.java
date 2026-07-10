package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record TalentPatch(
    boolean group,
    int talentId,
    int maxLevel,
    int amount,
    int globalBonus,
    int skillUnlock,
    int statusBonus,
    int resistanceBonus,
    int heroBonus) {}
