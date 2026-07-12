package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.checkedByte;
import static com.vddoh.editor.utils.EditorSupport.encodeSignedChance;
import static com.vddoh.editor.utils.EditorSupport.skipDamageGroups;
import static com.vddoh.editor.utils.EditorSupport.skipStatuses;
import static com.vddoh.editor.utils.EditorSupport.u16;
import static com.vddoh.editor.utils.EditorSupport.u8;
import static com.vddoh.editor.utils.EditorSupport.writeU16;

import com.vddoh.editor.data.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameDatSkillPatcher {

  public static PatchSummary patch(byte[] data, List<SkillPatch> patches) {
    PatchSummary summary = new PatchSummary();
    int cursor = skillTableOffset(data);
    int skillCount = u8(data[cursor++]);
    for (int skillId = 0; skillId < skillCount; skillId++) {
      ParsedSkillLayout skillLayout = parseSkillLayout(data, cursor);
      cursor = skillLayout.nextOffset();
      applyPatchesForSkill(data, patches, skillId, skillLayout.levelOffsets(), summary);
    }
    return summary;
  }

  private static int skillTableOffset(byte[] data) {
    int cursor = 13 + u16(data, 11) * 5;
    cursor = skipDamageGroups(data, cursor);
    return skipStatuses(data, cursor);
  }

  private static ParsedSkillLayout parseSkillLayout(byte[] data, int offset) {
    int cursor = offset;
    int nameLength = data[cursor] & 0x1f;
    cursor += 1 + nameLength;
    int header = u8(data[cursor++]);
    int levelCount = ((header >> 6) & 3) + 1;

    ParsedBaseLevelLayout baseLevel = parseBaseLevelLayout(data, cursor, header & 7);
    cursor = baseLevel.nextOffset();

    List<LevelOffsets> offsets = new ArrayList<>(levelCount);
    offsets.add(baseLevel.offsets());
    for (int level = 1; level < levelCount; level++) {
      ParsedLevelOverrideLayout overrideLevel =
          parseOverrideLevelLayout(
              data, cursor, baseLevel.damageEffectCount(), baseLevel.statusEffectCount());
      offsets.add(overrideLevel.offsets());
      cursor = overrideLevel.nextOffset();
    }
    return ParsedSkillLayout.builder().levelOffsets(offsets).nextOffset(cursor).build();
  }

  private static ParsedBaseLevelLayout parseBaseLevelLayout(
      byte[] data, int offset, int inheritedFlags) {
    int cursor = offset;
    int baseCostOffset = cursor++;
    int packedUsabilityOffset = cursor++;
    int effectiveFlags = inheritedFlags | ((data[packedUsabilityOffset] & 1) << 3);
    if ((data[packedUsabilityOffset] & 8) != 0) {
      cursor++;
    }
    cursor++;
    if ((effectiveFlags & 1) != 0) {
      cursor += 2;
    }

    int damageEffectCount = u8(data[cursor++]);
    int baseDamageOffset = cursor;
    cursor += damageEffectCount * 3;

    int statusEffectCount = 0;
    int baseStatusOffset = -1;
    if ((effectiveFlags & 8) != 0) {
      statusEffectCount = u8(data[cursor++]);
      baseStatusOffset = cursor;
      cursor += statusEffectCount * 2;
    }
    cursor = skipBaseLevelTail(cursor, effectiveFlags);

    LevelOffsets offsets =
        LevelOffsets.builder()
            .costOffset(baseCostOffset)
            .damageOffset(baseDamageOffset)
            .damageCount(damageEffectCount)
            .statusOffset(baseStatusOffset)
            .statusCount(statusEffectCount)
            .build();
    return ParsedBaseLevelLayout.builder()
        .offsets(offsets)
        .nextOffset(cursor)
        .damageEffectCount(damageEffectCount)
        .statusEffectCount(statusEffectCount)
        .build();
  }

  private static int skipBaseLevelTail(int offset, int inheritedFlags) {
    int cursor = offset + 2;
    if ((inheritedFlags & 4) != 0) {
      cursor += 2;
    }
    if ((inheritedFlags & 2) != 0) {
      cursor += 2;
    }
    return cursor + 1;
  }

  private static ParsedLevelOverrideLayout parseOverrideLevelLayout(
      byte[] data, int offset, int damageEffectCount, int statusEffectCount) {
    int cursor = offset;
    LevelOffsets offsets =
        LevelOffsets.builder()
            .costOffset(-1)
            .damageOffset(-1)
            .damageCount(damageEffectCount)
            .statusOffset(-1)
            .statusCount(statusEffectCount)
            .build();
    int overrideFlags = u8(data[cursor++]);
    int reuseFlags = u8(data[cursor++]);

    cursor = skipOverrideLevelPrefix(cursor, overrideFlags, reuseFlags);
    if ((overrideFlags & 0x10) != 0) {
      offsets = offsets.withCostOffset(cursor++);
    }
    cursor = skipOverrideLevelVisualFields(cursor, overrideFlags, reuseFlags);

    if ((reuseFlags & 4) != 0) {
      cursor++;
    } else if ((overrideFlags & 2) != 0) {
      offsets = offsets.withDamageOffset(cursor);
      cursor += damageEffectCount * 2;
    }

    if ((reuseFlags & 2) != 0) {
      cursor++;
    } else if ((overrideFlags & 1) != 0) {
      offsets = offsets.withStatusOffset(cursor);
      cursor += statusEffectCount;
    }

    return ParsedLevelOverrideLayout.builder().offsets(offsets).nextOffset(cursor + 2).build();
  }

  public static int skipOverrideLevelPrefix(int offset, int overrideFlags, int reuseFlags) {
    int cursor = offset;
    if ((overrideFlags & 8) != 0) {
      cursor++;
    }
    if ((overrideFlags & 0x80) != 0 && (reuseFlags & 0x80) != 0) {
      cursor++;
    }
    if ((overrideFlags & 0x40) != 0) {
      cursor++;
    }
    if ((overrideFlags & 0x20) != 0 && (reuseFlags & 8) != 0) {
      cursor += 2;
    }
    return cursor;
  }

  private static int skipOverrideLevelVisualFields(int offset, int overrideFlags, int reuseFlags) {
    int cursor = offset;
    if ((reuseFlags & 0x10) != 0 || (overrideFlags & 4) != 0) {
      cursor++;
    }
    if ((overrideFlags & 4) != 0) {
      cursor++;
    }
    return cursor;
  }

  private static void applyPatchesForSkill(
      byte[] data,
      List<SkillPatch> patches,
      int skillId,
      List<LevelOffsets> offsets,
      PatchSummary summary) {
    for (SkillPatch patch : patches) {
      if (!patchTargetsParsedLevel(patch, skillId, offsets)) {
        continue;
      }
      applyLevelPatch(data, patch, offsets.get(patch.levelIndex()), summary);
    }
  }

  private static boolean patchTargetsParsedLevel(
      SkillPatch patch, int skillId, List<LevelOffsets> offsets) {
    return patch.skillId() == skillId
        && patch.levelIndex() >= 0
        && patch.levelIndex() < offsets.size();
  }

  private static void applyLevelPatch(
      byte[] data, SkillPatch patch, LevelOffsets offsets, PatchSummary summary) {
    writeLevelCost(data, patch, offsets, summary);
    for (SkillEffectRow effect : patch.effects()) {
      if (effect.changed()) {
        writeLevelEffect(data, patch.levelIndex(), offsets, effect, summary);
      }
    }
  }

  private static void writeLevelCost(
      byte[] data, SkillPatch patch, LevelOffsets offsets, PatchSummary summary) {
    if (offsets.costOffset() >= 0) {
      data[offsets.costOffset()] = checkedByte(patch.cost(), "cost");
      summary.incrementCost();
    } else {
      summary.incrementSkipped();
    }
  }

  private static void writeLevelEffect(
      byte[] data,
      int levelIndex,
      LevelOffsets offsets,
      SkillEffectRow effect,
      PatchSummary summary) {
    if ("Damage".equals(effect.type)) {
      writeDamageEffect(data, levelIndex, offsets, effect, summary);
    } else if (effect.isStatus()) {
      writeStatusEffect(data, levelIndex, offsets, effect, summary);
    }
  }

  private static void writeDamageEffect(
      byte[] data,
      int levelIndex,
      LevelOffsets offsets,
      SkillEffectRow effect,
      PatchSummary summary) {
    if (offsets.damageOffset() < 0 || effect.index < 0 || effect.index >= offsets.damageCount()) {
      summary.incrementSkipped();
      return;
    }
    int offset =
        levelIndex == 0
            ? offsets.damageOffset() + effect.index * 3 + 1
            : offsets.damageOffset() + effect.index * 2;
    writeU16(data, offset, effect.value);
    summary.incrementDamage();
  }

  private static void writeStatusEffect(
      byte[] data,
      int levelIndex,
      LevelOffsets offsets,
      SkillEffectRow effect,
      PatchSummary summary) {
    if (offsets.statusOffset() < 0 || effect.index < 0 || effect.index >= offsets.statusCount()) {
      summary.incrementSkipped();
      return;
    }
    int offset =
        levelIndex == 0
            ? offsets.statusOffset() + effect.index * 2 + 1
            : offsets.statusOffset() + effect.index;
    data[offset] = encodeSignedChance(effect.encodedValue());
    summary.incrementStatus();
  }

  @Builder
  @With
  private record ParsedSkillLayout(List<LevelOffsets> levelOffsets, int nextOffset) {

    public ParsedSkillLayout {
      levelOffsets = levelOffsets == null ? Collections.emptyList() : levelOffsets;
    }
  }

  @Builder
  @With
  private record ParsedBaseLevelLayout(
      LevelOffsets offsets, int nextOffset, int damageEffectCount, int statusEffectCount) {}

  @Builder
  @With
  private record ParsedLevelOverrideLayout(LevelOffsets offsets, int nextOffset) {}
}
