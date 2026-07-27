package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.equipmentFlag;
import static com.vddoh.editor.utils.EditorSupport.heroStartOffset;
import static com.vddoh.editor.utils.EditorSupport.u8;
import static com.vddoh.editor.utils.EditorSupport.writeHeroSeeds;
import static com.vddoh.editor.utils.EditorSupport.writeHeroStats;

import com.vddoh.editor.data.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameDatHeroPatcher {

  public static PatchSummary patch(byte[] data, List<HeroPatch> patches) {
    log.info("Applying {} hero patches", patches.size());
    PatchSummary summary = new PatchSummary();
    HeroOffsets[] offsets = parseHeroOffsets(data);
    for (HeroPatch patch : patches) {
      if (patch.heroId() < 0 || patch.heroId() >= offsets.length) {
        summary.incrementSkipped();
        continue;
      }
      HeroOffsets o = offsets[patch.heroId()];
      writeHeroStats(
          data,
          o.statOffset(),
          patch.strength().packed(),
          patch.spirit().packed(),
          patch.vitality().packed(),
          patch.speed().packed());
      summary.incrementHeroStats();
      writeHeroSeeds(
          data, o.seedOffset(), patch.levelCap(), patch.baseCritChance(), patch.baseCritDamage());
      summary.incrementHeroSeeds();
    }
    log.info("Hero patch summary: {}", summary);
    return summary;
  }

  private static HeroOffsets[] parseHeroOffsets(byte[] data) {
    int n = heroStartOffset(data);
    HeroOffsets[] offsets = new HeroOffsets[u8(data[n++])];
    for (int heroId = 0; heroId < offsets.length; heroId++) {
      int nameLen = data[n] & 0x7f;
      n += 1 + nameLen;
      int statOffset = n;
      int seedOffset = seedOffset(statOffset);
      n = skipFixedHeroFields(data, statOffset);
      n = getN(data, n);
      offsets[heroId] = HeroOffsets.builder().statOffset(statOffset).seedOffset(seedOffset).build();
    }
    return offsets;
  }

  public static int skipFixedHeroFields(byte[] data, int statOffset) {
    int seedOffset = seedOffset(statOffset);
    int n = seedOffset + 6;
    for (int slot = 0; slot < 10; slot++) {
      int equipped = equipmentFlag(data, seedOffset + 3, slot);
      if (equipped > 0) {
        n++;
      }
    }
    return n;
  }

  private static int seedOffset(int statOffset) {
    return statOffset + 11 + 3 + 1 + 1;
  }

  public static int getN(byte[] data, int n) {
    int len = u8(data[n++]);
    n += len * 2;
    len = u8(data[n++]);
    n += len * 2;
    len = u8(data[n++]);
    n += len;
    return n;
  }
}
