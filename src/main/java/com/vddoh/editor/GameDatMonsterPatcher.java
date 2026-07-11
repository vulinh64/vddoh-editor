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
      writeMonsterCoreStats(
          data, o.tailOffset(), patch.strength(), patch.spirit(), patch.vitality(), patch.speed());
      data[o.effectOffset()] = EditorSupport.checkedByte(patch.effectId(), "monster effect id");
      summary.monsterHeader++;
      summary.monsterCoreStats++;
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
      int tailOffset = n;
      int effectOffset = tailOffset + 12;
      n += 13;
      offsets[monsterId] =
          MonsterOffsets.builder()
              .fixedOffset(fixedOffset)
              .tailOffset(tailOffset)
              .effectOffset(effectOffset)
              .build();
    }
    return offsets;
  }

  private static void writeMonsterCoreStats(
      byte[] data, int tailOffset, int strength, int spirit, int vitality, int speed) {
    strength = EditorSupport.checked7Bit(strength, "monster STR-like");
    spirit = EditorSupport.checked7Bit(spirit, "monster SPI-like");
    vitality = EditorSupport.checked7Bit(vitality, "monster VIT-like");
    speed = EditorSupport.checked7Bit(speed, "monster SPD-like");

    data[tailOffset + 4] =
        EditorSupport.checkedByte(
            (strength << 1) | (u8(data[tailOffset + 4]) & 0x01), "monster core stat byte 4");
    data[tailOffset + 4] =
        EditorSupport.checkedByte(
            (u8(data[tailOffset + 4]) & 0xfe) | ((spirit >> 6) & 0x01), "monster core stat byte 4");
    data[tailOffset + 5] =
        EditorSupport.checkedByte(
            ((spirit & 0x3f) << 2) | (u8(data[tailOffset + 5]) & 0x03), "monster core stat byte 5");
    data[tailOffset + 5] =
        EditorSupport.checkedByte(
            (u8(data[tailOffset + 5]) & 0xfc) | ((vitality >> 5) & 0x03),
            "monster core stat byte 5");
    data[tailOffset + 6] =
        EditorSupport.checkedByte(
            ((vitality & 0x1f) << 3) | (u8(data[tailOffset + 6]) & 0x07),
            "monster core stat byte 6");
    data[tailOffset + 6] =
        EditorSupport.checkedByte(
            (u8(data[tailOffset + 6]) & 0xf8) | ((speed >> 4) & 0x07), "monster core stat byte 6");
    data[tailOffset + 7] =
        EditorSupport.checkedByte(
            ((speed & 0x0f) << 4) | (u8(data[tailOffset + 7]) & 0x0f), "monster core stat byte 7");
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
