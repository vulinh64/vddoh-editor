package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.monsterStartOffset;
import static com.vddoh.editor.utils.EditorSupport.u8;
import static com.vddoh.editor.utils.EditorSupport.writeMonsterHeader;

import com.vddoh.editor.data.*;
import com.vddoh.editor.utils.EditorSupport;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameDatMonsterPatcher {

  private static final Pattern ARRAY_KEY = Pattern.compile("([a-zA-Z]+)\\[(\\d+)]");

  public static PatchSummary patch(byte[] data, List<MonsterPatch> patches) {
    log.info("Applying {} monster patches", patches.size());
    PatchSummary summary = new PatchSummary();
    MonsterOffsets[] offsets = parseMonsterOffsets(data);
    for (MonsterPatch patch : patches) {
      if (patch.monsterId() < 0 || patch.monsterId() >= offsets.length) {
        summary.incrementSkipped();
        continue;
      }
      MonsterOffsets o = offsets[patch.monsterId()];
      writeMonsterHeader(
          data, o.fixedOffset(), patch.experience(), patch.filar(), patch.deathValue());
      writeMonsterCoreStats(
          data, o.tailOffset(), patch.strength(), patch.spirit(), patch.vitality(), patch.speed());
      data[o.effectOffset()] = EditorSupport.checkedByte(patch.effectId(), "monster effect id");
      writeArrayEntries(data, o, patch, summary);
      summary.incrementMonsterHeader();
      summary.incrementMonsterCoreStats();
      summary.incrementMonsterEffect();
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
      ParsedMonsterArrays arrays = parseArrays(data, n);
      n = arrays.nextOffset();
      int tailOffset = n;
      int effectOffset = tailOffset + 12;
      n += 13;
      offsets[monsterId] =
          MonsterOffsets.builder()
              .fixedOffset(fixedOffset)
              .tailOffset(tailOffset)
              .effectOffset(effectOffset)
              .effectsOffset(arrays.effectsOffset())
              .effectsCount(arrays.effectsCount())
              .resistAOffset(arrays.resistAOffset())
              .resistACount(arrays.resistACount())
              .resistBOffset(arrays.resistBOffset())
              .resistBCount(arrays.resistBCount())
              .bytesDOffset(arrays.bytesDOffset())
              .bytesDCount(arrays.bytesDCount())
              .dropsOffset(arrays.dropsOffset())
              .dropsCount(arrays.dropsCount())
              .build();
    }
    return offsets;
  }

  private static ParsedMonsterArrays parseArrays(byte[] data, int n) {
    n += 4;
    int flags = u8(data[n++]);
    int len = u8(data[n++]);
    for (int j = 0; j < len; j++) {
      n += 2;
      if ((data[n - 1] & 1) != 0) {
        n++;
      }
    }
    int effectsOffset = n + 1;
    int effectsCount = u8(data[n++]);
    n += effectsCount * 3;
    int resistAOffset = -1;
    int resistACount = 0;
    if ((flags & 8) != 0) {
      resistAOffset = n + 1;
      resistACount = u8(data[n++]);
      n += resistACount * 2;
    }
    int resistBOffset = -1;
    int resistBCount = 0;
    if ((flags & 4) != 0) {
      resistBOffset = n + 1;
      resistBCount = u8(data[n++]);
      n += resistBCount * 2;
    }
    int bytesDOffset = -1;
    int bytesDCount = 0;
    if ((flags & 2) != 0) {
      bytesDOffset = n + 1;
      bytesDCount = u8(data[n++]);
      n += bytesDCount;
    }
    int dropsOffset = n + 1;
    int dropsCount = u8(data[n++]);
    n += dropsCount * 2;
    return ParsedMonsterArrays.builder()
        .effectsOffset(effectsOffset)
        .effectsCount(effectsCount)
        .resistAOffset(resistAOffset)
        .resistACount(resistACount)
        .resistBOffset(resistBOffset)
        .resistBCount(resistBCount)
        .bytesDOffset(bytesDOffset)
        .bytesDCount(bytesDCount)
        .dropsOffset(dropsOffset)
        .dropsCount(dropsCount)
        .nextOffset(n)
        .build();
  }

  private static void writeArrayEntries(
      byte[] data, MonsterOffsets offsets, MonsterPatch patch, PatchSummary summary) {
    for (MonsterArrayEntryEdit edit : patch.arrayEdits()) {
      if (writeArrayEntry(data, offsets, edit)) {
        summary.incrementMonsterEffect();
      } else {
        summary.incrementSkipped();
      }
    }
  }

  private static boolean writeArrayEntry(
      byte[] data, MonsterOffsets offsets, MonsterArrayEntryEdit edit) {
    Matcher matcher = ARRAY_KEY.matcher(edit.raw());
    if (!matcher.matches()) {
      return false;
    }
    String key = matcher.group(1);
    int index = Integer.parseInt(matcher.group(2));
    return switch (key) {
      case "effects" ->
          writeThreeByteEntry(
              data, offsets.effectsOffset(), offsets.effectsCount(), index, edit.value());
      case "resistA" ->
          writeShortEntry(
              data, offsets.resistAOffset(), offsets.resistACount(), index, edit.value());
      case "resistB" ->
          writeShortEntry(
              data, offsets.resistBOffset(), offsets.resistBCount(), index, edit.value());
      case "bytesD" ->
          writeByteEntry(data, offsets.bytesDOffset(), offsets.bytesDCount(), index, edit.value());
      case "drops" ->
          writeShortEntry(data, offsets.dropsOffset(), offsets.dropsCount(), index, edit.value());
      default -> false;
    };
  }

  private static boolean writeThreeByteEntry(
      byte[] data, int offset, int count, int index, int value) {
    if (offset < 0 || index < 0 || index >= count) {
      return false;
    }
    int checked = EditorSupport.checkedRange(value, 0, 0x00ffffff, "monster packed effect");
    int n = offset + index * 3;
    data[n] = EditorSupport.checkedByte((checked >>> 16) & 0xff, "monster packed effect high");
    data[n + 1] = EditorSupport.checkedByte((checked >>> 8) & 0xff, "monster packed effect mid");
    data[n + 2] = EditorSupport.checkedByte(checked & 0xff, "monster packed effect low");
    return true;
  }

  private static boolean writeShortEntry(byte[] data, int offset, int count, int index, int value) {
    if (offset < 0 || index < 0 || index >= count) {
      return false;
    }
    int checked = EditorSupport.checkedRange(value, 0, 0xffff, "monster packed short");
    data[offset + index * 2] =
        EditorSupport.checkedByte((checked >>> 8) & 0xff, "monster packed short high");
    data[offset + index * 2 + 1] =
        EditorSupport.checkedByte(checked & 0xff, "monster packed short low");
    return true;
  }

  private static boolean writeByteEntry(byte[] data, int offset, int count, int index, int value) {
    if (offset < 0 || index < 0 || index >= count) {
      return false;
    }
    data[offset + index] = EditorSupport.checkedByte(value, "monster byte-array value");
    return true;
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

  @Builder
  private record ParsedMonsterArrays(
      int effectsOffset,
      int effectsCount,
      int resistAOffset,
      int resistACount,
      int resistBOffset,
      int resistBCount,
      int bytesDOffset,
      int bytesDCount,
      int dropsOffset,
      int dropsCount,
      int nextOffset) {}
}
