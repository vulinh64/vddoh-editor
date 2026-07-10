package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.monsterStartOffset;
import static com.vddoh.editor.EditorSupport.u8;
import static com.vddoh.editor.EditorSupport.writeMonsterHeader;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GameDatMonsterPatcher {

  static PatchSummary patch(byte[] data, List<MonsterPatch> patches) {
    log.info("Applying {} monster patches", patches.size());
    PatchSummary summary = new PatchSummary();
    MonsterOffsets[] offsets = parseMonsterOffsets(data);
    for (MonsterPatch patch : patches) {
      if (patch.monsterId() < 0 || patch.monsterId() >= offsets.length) {
        summary.skipped++;
        continue;
      }
      MonsterOffsets o = offsets[patch.monsterId()];
      writeMonsterHeader(
          data, o.fixedOffset(), patch.experience(), patch.filar(), patch.deathValue());
      data[o.effectOffset()] = EditorSupport.checkedByte(patch.effectId(), "monster effect id");
      summary.monsterHeader++;
      summary.monsterEffect++;
    }
    log.info("Monster patch summary: {}", summary);
    return summary;
  }

  private static MonsterOffsets[] parseMonsterOffsets(byte[] data) {
    int n = monsterStartOffset(data);
    MonsterOffsets[] offsets = new MonsterOffsets[u8(data[n++])];
    for (int monsterId = 0; monsterId < offsets.length; monsterId++) {
      int nameLen = u8(data[n]);
      n += 1 + nameLen;
      int fixedOffset = n;
      n = getN(data, n);
      int effectOffset = n + 12;
      n += 13;
      offsets[monsterId] =
          MonsterOffsets.builder().fixedOffset(fixedOffset).effectOffset(effectOffset).build();
    }
    return offsets;
  }

  static int getN(byte[] data, int n) {
    n += 4;
    int flags = u8(data[n++]);
    int len = u8(data[n++]);
    for (int j = 0; j < len; j++) {
      n += 2;
      if ((data[n - 1] & 1) != 0) {
        n++;
      }
    }
    len = u8(data[n++]);
    n += len * 3;
    if ((flags & 8) != 0) {
      len = u8(data[n++]);
      n += len * 2;
    }
    if ((flags & 4) != 0) {
      len = u8(data[n++]);
      n += len * 2;
    }
    if ((flags & 2) != 0) {
      len = u8(data[n++]);
      n += len;
    }
    len = u8(data[n++]);
    n += len * 2;
    return n;
  }
}
