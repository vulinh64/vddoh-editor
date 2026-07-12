package com.vddoh.editor.data;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record PatchBuildRequest(
    EditorWorkspace workspace,
    List<SkillEdit> skillEdits,
    List<TalentEdit> talentEdits,
    List<HeroEdit> heroEdits,
    List<ItemEdit> itemEdits,
    List<MonsterEdit> monsterEdits,
    List<StatusEdit> statusEdits,
    boolean resistanceOverflowPatchRequested,
    boolean equipmentBonusPatchRequested) {

  public PatchBuildRequest {
    skillEdits = skillEdits == null ? Collections.emptyList() : List.copyOf(skillEdits);
    talentEdits = talentEdits == null ? Collections.emptyList() : List.copyOf(talentEdits);
    heroEdits = heroEdits == null ? Collections.emptyList() : List.copyOf(heroEdits);
    itemEdits = itemEdits == null ? Collections.emptyList() : List.copyOf(itemEdits);
    monsterEdits = monsterEdits == null ? Collections.emptyList() : List.copyOf(monsterEdits);
    statusEdits = statusEdits == null ? Collections.emptyList() : List.copyOf(statusEdits);
  }
}
