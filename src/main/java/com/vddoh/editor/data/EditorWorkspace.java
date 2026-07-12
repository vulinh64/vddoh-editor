package com.vddoh.editor.data;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record EditorWorkspace(
    Path inputJar,
    Path gameDat,
    Path itemDat,
    Path outputJar,
    String gameDatEntryName,
    String itemDatEntryName,
    String resistanceOverflowState,
    String equipmentBonusState,
    List<SkillLevelSnapshot> skillLevels,
    List<TalentSnapshot> talents,
    List<HeroSnapshot> heroes,
    List<ItemSnapshot> items,
    List<MonsterSnapshot> monsters,
    List<StatusSnapshot> statuses) {

  public EditorWorkspace {
    skillLevels = skillLevels == null ? Collections.emptyList() : List.copyOf(skillLevels);
    talents = talents == null ? Collections.emptyList() : List.copyOf(talents);
    heroes = heroes == null ? Collections.emptyList() : List.copyOf(heroes);
    items = items == null ? Collections.emptyList() : List.copyOf(items);
    monsters = monsters == null ? Collections.emptyList() : List.copyOf(monsters);
    statuses = statuses == null ? Collections.emptyList() : List.copyOf(statuses);
  }
}
