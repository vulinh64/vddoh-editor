package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.checked4Bit;
import static com.vddoh.editor.EditorSupport.checkedTalentLink;
import static com.vddoh.editor.EditorSupport.checkedTalentMaxLevel;
import static com.vddoh.editor.EditorSupport.heroStartOffset;
import static com.vddoh.editor.EditorSupport.skipHeroes;
import static com.vddoh.editor.EditorSupport.u8;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GameDatTalentPatcher {

  static PatchSummary patch(byte[] data, List<TalentPatch> patches) {
    log.info("Applying {} talent patches", patches.size());
    PatchSummary summary = new PatchSummary();
    TalentSections sections = parseTalentSections(data);
    for (TalentPatch patch : patches) {
      List<TalentOffsets> offsets = patch.group() ? sections.group() : sections.hero();
      if (patch.talentId() < 0 || patch.talentId() >= offsets.size()) {
        summary.skipped++;
        continue;
      }
      TalentOffsets o = offsets.get(patch.talentId());
      if (o.metaOffset < 0 || o.amountOffset < 0) {
        summary.skipped++;
        continue;
      }
      int maxLevel = checkedTalentMaxLevel(patch.maxLevel());
      int heroBonus = checked4Bit(patch.heroBonus(), "hero effect id");
      int amount = checked4Bit(patch.amount(), "talent amount");
      data[o.metaOffset] = (byte) (((maxLevel - 1) << 4) | heroBonus);
      data[o.amountOffset] = (byte) ((amount << 4) | (data[o.amountOffset] & 0x0f));
      if (o.globalOffset >= 0)
        data[o.globalOffset] = checkedTalentLink(patch.globalBonus(), "global id");
      if (o.skillOffset >= 0)
        data[o.skillOffset] = checkedTalentLink(patch.skillUnlock(), "skill id");
      if (o.statusOffset >= 0)
        data[o.statusOffset] = checkedTalentLink(patch.statusBonus(), "status id");
      if (o.resistanceOffset >= 0)
        data[o.resistanceOffset] = checkedTalentLink(patch.resistanceBonus(), "resist id");
      summary.talentAmount++;
    }
    log.info("Talent patch summary: {}", summary);
    return summary;
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
      o.metaOffset = n;
      n++;
      o.amountOffset = n;
      int flags = u8(data[n++]) & 0x0f;
      if ((flags & 8) != 0) o.globalOffset = n++;
      if ((flags & 4) != 0) o.skillOffset = n++;
      if ((flags & 2) != 0) o.statusOffset = n++;
      if ((flags & 1) != 0) o.resistanceOffset = n++;
      offsets.add(o);
    }
    return TalentSection.builder().offsets(offsets).nextOffset(n).build();
  }
}
