package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.monsterStartOffset;
import static com.vddoh.editor.utils.EditorSupport.u8;
import static com.vddoh.editor.utils.EditorSupport.writeMonsterHeader;

import com.vddoh.editor.data.*;
import com.vddoh.editor.utils.EditorSupport;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameDatMonsterPatcher {

  public static PatchSummary patch(byte[] data, List<MonsterPatch> patches) {
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
    int checkedStrength = EditorSupport.checked7Bit(strength, "monster STR-like");
    int checkedSpirit = EditorSupport.checked7Bit(spirit, "monster SPI-like");
    int checkedVitality = EditorSupport.checked7Bit(vitality, "monster VIT-like");
    int checkedSpeed = EditorSupport.checked7Bit(speed, "monster SPD-like");

    data[tailOffset + 4] =
        EditorSupport.checkedByte(
            (checkedStrength << 1) | (u8(data[tailOffset + 4]) & 0x01), "monster core stat byte 4");
    data[tailOffset + 4] =
        EditorSupport.checkedByte(
            (u8(data[tailOffset + 4]) & 0xfe) | ((checkedSpirit >> 6) & 0x01),
            "monster core stat byte 4");
    data[tailOffset + 5] =
        EditorSupport.checkedByte(
            ((checkedSpirit & 0x3f) << 2) | (u8(data[tailOffset + 5]) & 0x03),
            "monster core stat byte 5");
    data[tailOffset + 5] =
        EditorSupport.checkedByte(
            (u8(data[tailOffset + 5]) & 0xfc) | ((checkedVitality >> 5) & 0x03),
            "monster core stat byte 5");
    data[tailOffset + 6] =
        EditorSupport.checkedByte(
            ((checkedVitality & 0x1f) << 3) | (u8(data[tailOffset + 6]) & 0x07),
            "monster core stat byte 6");
    data[tailOffset + 6] =
        EditorSupport.checkedByte(
            (u8(data[tailOffset + 6]) & 0xf8) | ((checkedSpeed >> 4) & 0x07),
            "monster core stat byte 6");
    data[tailOffset + 7] =
        EditorSupport.checkedByte(
            ((checkedSpeed & 0x0f) << 4) | (u8(data[tailOffset + 7]) & 0x0f),
            "monster core stat byte 7");
  }

  public static int getN(byte[] data, int n) {
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
