package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.checked4Bit;
import static com.vddoh.editor.utils.EditorSupport.checkedTalentLink;
import static com.vddoh.editor.utils.EditorSupport.checkedTalentMaxLevel;
import static com.vddoh.editor.utils.EditorSupport.heroStartOffset;
import static com.vddoh.editor.utils.EditorSupport.skipHeroes;
import static com.vddoh.editor.utils.EditorSupport.u8;

import com.vddoh.editor.data.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameDatTalentPatcher {

  static PatchSummary patch(byte[] data, List<TalentPatch> patches) {
    log.info("Applying {} talent patches", patches.size());
    PatchSummary summary = new PatchSummary();
    TalentSections sections = parseTalentSections(data);
    for (TalentPatch patch : patches) {
      applyPatch(data, sections, patch, summary);
    }
    log.info("Talent patch summary: {}", summary);
    return summary;
  }

  private static void applyPatch(
      byte[] data, TalentSections sections, TalentPatch patch, PatchSummary summary) {
    List<TalentOffsets> offsets = patch.group() ? sections.group() : sections.hero();
    if (invalidTalentId(patch, offsets)) {
      summary.incrementSkipped();
      return;
    }
    TalentOffsets o = offsets.get(patch.talentId());
    if (o.getMetaOffset() < 0 || o.getAmountOffset() < 0) {
      summary.incrementSkipped();
      return;
    }
    writePatch(data, o, patch);
    summary.incrementTalentAmount();
  }

  private static boolean invalidTalentId(TalentPatch patch, List<TalentOffsets> offsets) {
    return patch.talentId() < 0 || patch.talentId() >= offsets.size();
  }

  private static void writePatch(byte[] data, TalentOffsets o, TalentPatch patch) {
    int maxLevel = checkedTalentMaxLevel(patch.maxLevel());
    int heroBonus = checked4Bit(patch.heroBonus(), "hero effect id");
    int amount = checked4Bit(patch.amount(), "talent amount");
    data[o.getMetaOffset()] = (byte) (((maxLevel - 1) << 4) | heroBonus);
    data[o.getAmountOffset()] = (byte) ((amount << 4) | (data[o.getAmountOffset()] & 0x0f));
    writeOptionalLink(data, o.getGlobalOffset(), patch.globalBonus(), "global id");
    writeOptionalLink(data, o.getSkillOffset(), patch.skillUnlock(), "skill id");
    writeOptionalLink(data, o.getStatusOffset(), patch.statusBonus(), "status id");
    writeOptionalLink(data, o.getResistanceOffset(), patch.resistanceBonus(), "resist id");
  }

  private static void writeOptionalLink(byte[] data, int offset, int value, String label) {
    if (offset >= 0) {
      data[offset] = checkedTalentLink(value, label);
    }
  }

  private static TalentSections parseTalentSections(byte[] data) {
    int n = skipHeroes(data, heroStartOffset(data));
    TalentSection group = parseTalentSection(data, n);
    TalentSection hero = parseTalentSection(data, group.nextOffset());
    return TalentSections.builder().group(group.offsets()).hero(hero.offsets()).build();
  }

  private static TalentSection parseTalentSection(byte[] data, int n) {
    int count = u8(data[n++]);
    List<TalentOffsets> offsets = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      TalentOffsets o = new TalentOffsets();
      int nameLen = u8(data[n++]);
      n += nameLen;
      o.setMetaOffset(n);
      n++;
      o.setAmountOffset(n);
      int flags = u8(data[n++]) & 0x0f;
      if ((flags & 8) != 0) o.setGlobalOffset(n++);
      if ((flags & 4) != 0) o.setSkillOffset(n++);
      if ((flags & 2) != 0) o.setStatusOffset(n++);
      if ((flags & 1) != 0) o.setResistanceOffset(n++);
      offsets.add(o);
    }
    return TalentSection.builder().offsets(offsets).nextOffset(n).build();
  }
}
