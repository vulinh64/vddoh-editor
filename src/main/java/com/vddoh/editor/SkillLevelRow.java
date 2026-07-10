package com.vddoh.editor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

final class SkillLevelRow implements Serializable {

  @Serial private static final long serialVersionUID = 8556150641063866587L;

  final int skillId;
  final String skillName;
  final int levelIndex;
  final int originalCost;
  final int areaShape;
  final int areaWidth;
  final int areaHeight;
  final int range;
  final boolean relativeAreaGrowth;
  final List<SkillEffectRow> effects;
  final String notes;
  int cost;

  SkillLevelRow(
      int skillId,
      String skillName,
      int levelIndex,
      int cost,
      int areaShape,
      int areaWidth,
      int areaHeight,
      int range,
      boolean relativeAreaGrowth,
      List<SkillEffectRow> effects) {
    this.skillId = skillId;
    this.skillName = skillName;
    this.levelIndex = levelIndex;
    this.cost = originalCost = cost;
    this.areaShape = areaShape;
    this.areaWidth = areaWidth;
    this.areaHeight = areaHeight;
    this.range = range;
    this.relativeAreaGrowth = relativeAreaGrowth;
    this.effects = effects;
    notes =
        "%sexisting effects only"
            .formatted(relativeAreaGrowth ? "relative area growth; " : StringUtils.EMPTY);
  }

  boolean changed() {
    if (cost != originalCost) {
      return true;
    }
    for (SkillEffectRow effect : effects) {
      if (effect.changed()) {
        return true;
      }
    }
    return false;
  }

  void reset() {
    cost = originalCost;
    for (SkillEffectRow effect : effects) {
      effect.reset();
    }
  }
}
