package com.vddoh.editor.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EditorSnapshots {

  public static ItemSnapshot item(ItemRow row) {
    return ItemSnapshot.builder()
        .id(row.id)
        .name(row.name)
        .rawType(row.rawType)
        .category(row.category)
        .subtype(row.subtype)
        .slotLabel(row.slotLabel)
        .allowedClasses(row.allowedClasses)
        .price(row.price)
        .icon(row.icon)
        .hpRestore(row.hpRestore)
        .resourceRestore(row.resourceRestore)
        .hpBonus(row.hpBonus)
        .resourceBonus(row.resourceBonus)
        .weaponReach(row.weaponReach)
        .weaponMode(row.weaponMode)
        .notes(row.notes)
        .effects(row.effects.stream().map(EditorSnapshots::itemEffect).toList())
        .build();
  }

  public static TalentSnapshot talent(TalentRow row) {
    return TalentSnapshot.builder()
        .group(row.group)
        .id(row.id)
        .name(row.name)
        .talentType(row.talentType())
        .currentLevel(row.currentLevel)
        .maxLevel(row.maxLevel)
        .amount(row.amount)
        .globalBonus(row.globalBonus)
        .skillUnlock(row.skillUnlock)
        .castableSkillId(row.castableSkillIdText())
        .unlockedSkillName(row.unlockedSkillName)
        .statusBonus(row.statusBonus)
        .resistanceBonus(row.resistanceBonus)
        .heroBonus(row.heroBonus)
        .effectName(row.effectName())
        .level1(row.levelValueText(1))
        .level2(row.levelValueText(2))
        .level3(row.levelValueText(3))
        .level4(row.levelValueText(4))
        .notes(row.notes)
        .build();
  }

  public static HeroSnapshot hero(HeroRow row) {
    return HeroSnapshot.builder()
        .id(row.id)
        .name(row.name)
        .strength(statCurve(row.strength))
        .spirit(statCurve(row.spirit))
        .vitality(statCurve(row.vitality))
        .speed(statCurve(row.speed))
        .levelCap(row.levelCap)
        .baseCritChance(row.baseCritChance)
        .baseCritDamage(row.baseCritDamage)
        .baseEvasion(HeroRow.BASE_EVASION)
        .baseHp(row.baseHp())
        .baseResource(row.baseResource())
        .baseAttack(row.baseAttack())
        .baseDefense(row.baseDefense())
        .baseMove(row.baseMove())
        .baseRegen(HeroRow.BASE_HP_REGEN)
        .strengthAtCap(row.strengthAtCap())
        .spiritAtCap(row.spiritAtCap())
        .vitalityAtCap(row.vitalityAtCap())
        .speedAtCap(row.speedAtCap())
        .notes(row.notes)
        .build();
  }

  public static MonsterSnapshot monster(MonsterRow row) {
    return MonsterSnapshot.builder()
        .id(row.id())
        .name(row.name())
        .experience(row.experience())
        .filar(row.filar())
        .deathValue(row.deathValue())
        .effectId(row.effectId())
        .strength(row.strength())
        .spirit(row.spirit())
        .vitality(row.vitality())
        .speed(row.speed())
        .baseHp(row.baseHp())
        .baseResource(row.baseResource())
        .baseAttack(row.baseAttack())
        .baseDefense(row.baseDefense())
        .baseMove(row.baseMove())
        .hitChance(row.hitChance())
        .critOrDamage(row.critOrDamage())
        .evadeOrGuard(row.evadeOrGuard())
        .packedChance(row.packedChance())
        .packedTailA(row.packedTailA())
        .packedTailB(row.packedTailB())
        .actionCount(row.actionCount())
        .effectCount(row.effectCount())
        .dropCount(row.dropCount())
        .arrayEntries(row.arrayEntries())
        .notes(row.notes())
        .build();
  }

  public static StatusSnapshot status(StatusRow row) {
    return StatusSnapshot.builder()
        .id(row.id)
        .name(row.name)
        .duration(row.duration)
        .expireChance(row.expireChance)
        .icon(row.icon)
        .notes(row.notes)
        .build();
  }

  public static SkillLevelSnapshot skillLevel(SkillLevelRow row) {
    return SkillLevelSnapshot.builder()
        .skillId(row.skillId)
        .skillName(row.skillName)
        .levelIndex(row.levelIndex)
        .cost(row.cost)
        .areaShape(row.areaShape)
        .areaWidth(row.areaWidth)
        .areaHeight(row.areaHeight)
        .range(row.range)
        .relativeAreaGrowth(row.relativeAreaGrowth)
        .notes(row.notes)
        .effects(row.effects.stream().map(EditorSnapshots::skillEffect).toList())
        .build();
  }

  private static StatCurveSnapshot statCurve(StatCurve curve) {
    return StatCurveSnapshot.builder()
        .start(curve.start)
        .target(curve.target)
        .curve(curve.curve)
        .build();
  }

  private static SkillEffectSnapshot skillEffect(SkillEffectRow row) {
    return SkillEffectSnapshot.builder()
        .type(row.type)
        .index(row.index)
        .targetId(row.targetId)
        .target(row.target)
        .value(row.displayValue())
        .editable(row.editable)
        .notes(row.notes)
        .build();
  }

  private static ItemEffectSnapshot itemEffect(ItemEffectRow row) {
    int numericValue = numericValue(row.value());
    int max = itemEffectMax(row.raw());
    boolean editable = max >= 0 && numericValue >= 0;
    return ItemEffectSnapshot.builder()
        .side(row.side())
        .type(row.type())
        .target(row.target())
        .value(row.value())
        .extra(row.extra())
        .raw(row.raw())
        .editable(editable)
        .numericValue(numericValue)
        .max(Math.max(max, 0))
        .build();
  }

  private static int numericValue(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException _) {
      return -1;
    }
  }

  private static int itemEffectMax(String raw) {
    if (raw == null) {
      return -1;
    }
    if (raw.endsWith(":hi") || raw.endsWith(":lo") || raw.equals("byte_q")) {
      return 0xff;
    }
    if (raw.equals("short_g") || raw.equals("short_h")) {
      return 0xffff;
    }
    if (raw.startsWith("short_arr_a[") || raw.startsWith("short_arr_b[")) {
      return 0xff;
    }
    return -1;
  }
}
