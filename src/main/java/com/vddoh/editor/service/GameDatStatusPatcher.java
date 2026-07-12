package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.checkedByte;
import static com.vddoh.editor.utils.EditorSupport.encodeSignedChance;
import static com.vddoh.editor.utils.EditorSupport.skipDamageGroups;
import static com.vddoh.editor.utils.EditorSupport.u16;
import static com.vddoh.editor.utils.EditorSupport.u8;

import com.vddoh.editor.data.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameDatStatusPatcher {

  public static PatchSummary patch(byte[] data, List<StatusPatch> patches) {
    log.info("Applying {} status patches", patches.size());
    PatchSummary summary = new PatchSummary();
    StatusOffsets[] offsets = parseStatusOffsets(data);
    for (StatusPatch patch : patches) {
      if (patch.statusId() < 0 || patch.statusId() >= offsets.length) {
        summary.incrementSkipped();
        continue;
      }
      StatusOffsets o = offsets[patch.statusId()];
      if (o.getDurationOffset() >= 0) {
        data[o.getDurationOffset()] = checkedByte(patch.duration(), "duration");
        summary.incrementDuration();
      } else {
        summary.incrementSkipped();
      }
      if (o.getExpireOffset() >= 0) {
        data[o.getExpireOffset()] = encodeSignedChance(patch.expireChance());
        summary.incrementExpire();
      } else {
        summary.incrementSkipped();
      }
      if (o.getIconOffset() >= 0) {
        data[o.getIconOffset()] = checkedByte(patch.icon(), "icon");
        summary.incrementIcon();
      } else {
        summary.incrementSkipped();
      }
    }
    log.info("Status patch summary: {}", summary);
    return summary;
  }

  private static StatusOffsets[] parseStatusOffsets(byte[] data) {
    int n = 13 + u16(data, 11) * 5;
    n = skipDamageGroups(data, n);
    int count = u8(data[n++]);
    StatusOffsets[] offsets = new StatusOffsets[count];
    for (int statusId = 0; statusId < count; statusId++) {
      StatusOffsets o = new StatusOffsets();
      int nameLen = data[n] & 0x1f;
      n += 1 + nameLen;
      boolean specialFlag = (data[n] & 0x80) != 0;
      n++;
      if (statusId > 0) {
        int flags = u8(data[n++]);
        if ((flags & 0x80) != 0) {
          n++;
        }
        if ((flags & 0x40) != 0) {
          o.setDurationOffset(n);
          n += 2;
        }
        if ((flags & 0x20) != 0) {
          o.setExpireOffset(n);
          n++;
        }
        n = getN(n, flags);
        o.setIconOffset(n++);
        n = getPacked(data, n, specialFlag);
      }
      offsets[statusId] = o;
    }
    return offsets;
  }

  public static int getPacked(byte[] data, int n, boolean specialFlag) {
    int packed = u8(data[n++]);
    if ((packed & 0x80) != 0) {
      int len = u8(data[n++]);
      n += len * 2;
    }
    int nFlags = ((packed >> 5) & 3) | (((packed >> 4) & 1) << 7);
    if ((nFlags & 3) != 0) {
      n++;
      if (specialFlag) {
        n += 2;
      }
    }
    int pFlags = ((packed >> 2) & 3) | (((packed >> 1) & 1) << 7);
    if ((pFlags & 3) != 0) {
      n++;
      if (specialFlag) {
        n += 2;
      }
    }
    return n;
  }

  public static int getN(int n, int flags) {
    if ((flags & 0x10) != 0) {
      n++;
    }
    if ((flags & 8) != 0) {
      n++;
    }
    if ((flags & 4) != 0) {
      n++;
    }
    if ((flags & 2) != 0) {
      n++;
    }
    if ((flags & 1) != 0) {
      n++;
    }
    n++;
    return n;
  }
}
