package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record HeroEdit(
    int heroId,
    StatCurveEdit strength,
    StatCurveEdit spirit,
    StatCurveEdit vitality,
    StatCurveEdit speed,
    int levelCap,
    int baseCritChance,
    int baseCritDamage) {}
