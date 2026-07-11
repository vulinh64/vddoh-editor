package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record MonsterPatch(
    int monsterId,
    int experience,
    int filar,
    int deathValue,
    int effectId,
    int strength,
    int spirit,
    int vitality,
    int speed) {}
