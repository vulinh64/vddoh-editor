package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record TalentPatch(
    boolean group,
    int talentId,
    int maxLevel,
    int amount,
    int globalBonus,
    int skillUnlock,
    int statusBonus,
    int resistanceBonus,
    int heroBonus) {}
