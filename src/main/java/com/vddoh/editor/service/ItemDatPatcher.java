package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.checkedByte;
import static com.vddoh.editor.utils.EditorSupport.u16;
import static com.vddoh.editor.utils.EditorSupport.u8;
import static com.vddoh.editor.utils.EditorSupport.writeU16;

import com.vddoh.editor.data.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemDatPatcher {

  static PatchSummary patch(byte[] data, List<ItemPatch> patches) {
    log.info("Applying {} item patches", patches.size());
    PatchSummary summary = new PatchSummary();
    ItemOffsets[] offsets = parseItemOffsets(data);
    for (ItemPatch patch : patches) {
      applyPatch(data, offsets, patch, summary);
    }
    log.info("Item patch summary: {}", summary);
    return summary;
  }

  private static void applyPatch(
      byte[] data, ItemOffsets[] offsets, ItemPatch patch, PatchSummary summary) {
    if (patch.itemId() < 0 || patch.itemId() >= offsets.length) {
      summary.skipped++;
      return;
    }
    ItemOffsets o = offsets[patch.itemId()];
    writePrice(data, o, patch, summary);
    writeIcon(data, o, patch, summary);
    writeHpRestore(data, o, patch, summary);
    writeResourceRestore(data, o, patch, summary);
  }

  private static void writePrice(
      byte[] data, ItemOffsets o, ItemPatch patch, PatchSummary summary) {
    if (o.getPriceOffset() >= 0) {
      writeU16(data, o.getPriceOffset(), patch.price());
      summary.price++;
    } else {
      summary.skipped++;
    }
  }

  private static void writeIcon(byte[] data, ItemOffsets o, ItemPatch patch, PatchSummary summary) {
    if (o.getIconOffset() >= 0) {
      data[o.getIconOffset()] =
          (byte) ((data[o.getIconOffset()] & 0x80) | (checkedByte(patch.icon(), "icon") & 0x7f));
      summary.icon++;
    } else {
      summary.skipped++;
    }
  }

  private static void writeHpRestore(
      byte[] data, ItemOffsets o, ItemPatch patch, PatchSummary summary) {
    if (o.getHpRestoreOffset() >= 0) {
      writeU16(data, o.getHpRestoreOffset(), patch.hpRestore());
      summary.hp++;
    } else {
      summary.skipped++;
    }
  }

  private static void writeResourceRestore(
      byte[] data, ItemOffsets o, ItemPatch patch, PatchSummary summary) {
    if (o.getResourceRestoreOffset() >= 0) {
      writeU16(data, o.getResourceRestoreOffset(), patch.resourceRestore());
      summary.resource++;
    } else {
      summary.skipped++;
    }
  }

  private static ItemOffsets[] parseItemOffsets(byte[] data) {
    int n = 0;
    ItemOffsets[] offsets = new ItemOffsets[u8(data[n])];
    for (int itemId = 0; itemId < offsets.length; itemId++) {
      ParsedItem item = parseItem(data, n);
      offsets[itemId] = item.offsets();
      n = item.nextOffset();
    }
    return offsets;
  }

  private static ParsedItem parseItem(byte[] data, int n) {
    ItemOffsets offsets = new ItemOffsets();
    int rawType = u8(data[++n]);
    int category = (rawType >> 4) & 0x0f;
    int nameLen = u8(data[++n]);
    n += 1 + nameLen;
    n = parsePriceAndIcon(n, category, offsets);
    n = skipAllowedClasses(data, n, category);
    n = skipEquipmentFields(data, n, category);
    n = skipCategoryTail(data, n, category, offsets);
    return new ParsedItem(offsets, n);
  }

  private static int parsePriceAndIcon(int n, int category, ItemOffsets offsets) {
    if (category == 12 || category == 8) {
      return n;
    }
    offsets.setPriceOffset(n);
    offsets.setIconOffset(n + 2);
    return n + 2;
  }

  private static int skipAllowedClasses(byte[] data, int n, int category) {
    if (category == 0 || category == 12 || category == 8) {
      return n;
    }
    if ((data[n] & 0x80) != 0) {
      int len = u8(data[++n]);
      return n + 1 + len;
    }
    return n + 1;
  }

  private static int skipEquipmentFields(byte[] data, int n, int category) {
    if (category <= 0 || category >= 8) {
      return n;
    }
    int flags = u8(data[n++]);
    n += (flags & 0x10) != 0 ? 2 : 0;
    n += (flags & 8) != 0 ? 2 : 0;
    n += (flags & 4) != 0 ? 2 : 0;
    n += (flags & 2) != 0 ? 2 : 0;
    n += (flags & 1) != 0 ? 1 : 0;
    n = skipIntEffectArray(data, n, flags, category);
    n = skipShortEffectArray(data, n, flags, 0x40);
    return skipShortEffectArray(data, n, flags, 0x20);
  }

  private static int skipIntEffectArray(byte[] data, int n, int flags, int category) {
    if ((flags & 0x80) == 0) {
      return n;
    }
    int len = u8(data[n++]);
    n += len * 3;
    if (category == 7) {
      len = u8(data[n++]);
      n += len * 3;
    }
    return n;
  }

  private static int skipShortEffectArray(byte[] data, int n, int flags, int flag) {
    if ((flags & flag) == 0) {
      return n;
    }
    int len = u8(data[n++]);
    return n + len * 2;
  }

  private static int skipCategoryTail(byte[] data, int n, int category, ItemOffsets offsets) {
    return switch (category) {
      case 1, 2, 4 -> n + 1;
      case 3 -> skipWeaponTail(data, n);
      case 5 -> parseConsumableTail(data, n, offsets);
      case 8 -> skipTextTail(data, n);
      case 9, 10 -> n + 1;
      case 12 -> skipQuestTail(data, n);
      default -> n;
    };
  }

  private static int skipWeaponTail(byte[] data, int n) {
    int flags = u8(data[n + 2]);
    return n + (((flags & 0x0f) > 1) ? 5 : 4);
  }

  private static int parseConsumableTail(byte[] data, int n, ItemOffsets offsets) {
    int flags = u8(data[n]);
    if ((flags & 4) != 0) {
      offsets.setHpRestoreOffset(++n);
      n++;
    }
    if ((flags & 2) != 0) {
      offsets.setResourceRestoreOffset(++n);
      n++;
    }
    n += (flags & 1) != 0 ? 1 : 0;
    return (flags & 8) == 0 ? n + 1 : n;
  }

  private static int skipTextTail(byte[] data, int n) {
    return n + 2 + u16(data, n);
  }

  private static int skipQuestTail(byte[] data, int n) {
    int len = u8(data[n]);
    n += 1 + len * 2;
    n += 2;
    return data[n] > 0 ? n + 2 : n;
  }

  private record ParsedItem(ItemOffsets offsets, int nextOffset) {}
}
