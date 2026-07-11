package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record HeroPatch(
    int heroId,
    StatCurve strength,
    StatCurve spirit,
    StatCurve vitality,
    StatCurve speed,
    int levelCap,
    int baseCritChance,
    int baseCritDamage) {}
