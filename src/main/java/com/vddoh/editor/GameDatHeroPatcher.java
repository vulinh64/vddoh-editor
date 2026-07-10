package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.equipmentFlag;
import static com.vddoh.editor.EditorSupport.heroStartOffset;
import static com.vddoh.editor.EditorSupport.u8;
import static com.vddoh.editor.EditorSupport.writeHeroSeeds;
import static com.vddoh.editor.EditorSupport.writeHeroStats;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GameDatHeroPatcher {

  static PatchSummary patch(byte[] data, List<HeroPatch> patches) {
    log.info("Applying {} hero patches", patches.size());
    PatchSummary summary = new PatchSummary();
    HeroOffsets[] offsets = parseHeroOffsets(data);
    for (HeroPatch patch : patches) {
      if (patch.heroId() < 0 || patch.heroId() >= offsets.length) {
        summary.skipped++;
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
      summary.heroStats++;
      writeHeroSeeds(
          data, o.seedOffset(), patch.levelCap(), patch.baseCritChance(), patch.baseCritDamage());
      summary.heroSeeds++;
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
      n += 11;
      n += 3;
      n++;
      n++;
      int seedOffset = n;
      n += 3;
      n += 3;
      for (int slot = 0; slot < 10; slot++) {
        int equipped = equipmentFlag(data, seedOffset + 3, slot);
        if (equipped > 0) {
          n++;
        }
      }
      n = getN(data, n);
      offsets[heroId] = HeroOffsets.builder().statOffset(statOffset).seedOffset(seedOffset).build();
    }
    return offsets;
  }

  static int getN(byte[] data, int n) {
    int len = u8(data[n++]);
    n += len * 2;
    len = u8(data[n++]);
    n += len * 2;
    len = u8(data[n++]);
    n += len;
    return n;
  }
}
