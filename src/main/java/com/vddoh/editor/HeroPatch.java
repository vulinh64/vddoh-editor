package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record HeroPatch(
    int heroId,
    StatCurve strength,
    StatCurve spirit,
    StatCurve vitality,
    StatCurve speed,
    int levelCap,
    int baseCritChance,
    int baseCritDamage) {}
