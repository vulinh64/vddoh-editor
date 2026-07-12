package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record HeroSnapshot(
    int id,
    String name,
    StatCurveSnapshot strength,
    StatCurveSnapshot spirit,
    StatCurveSnapshot vitality,
    StatCurveSnapshot speed,
    int levelCap,
    int baseCritChance,
    int baseCritDamage,
    int baseEvasion,
    int baseHp,
    int baseResource,
    int baseAttack,
    int baseDefense,
    int baseMove,
    int baseRegen,
    int strengthAtCap,
    int spiritAtCap,
    int vitalityAtCap,
    int speedAtCap,
    String notes) {}
