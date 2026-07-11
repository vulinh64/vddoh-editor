package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.editorUserPath;
import static com.vddoh.editor.EditorSupport.readJarEntry;
import static com.vddoh.editor.EditorSupport.readZipEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EditorLoadService {

  private static final String VDDOH_ROOT = "vddoh";
  private static final String GAME_DAT_PATH = "game.dat";
  private static final String ITEM_DAT_PATH = "item.dat";
  private static final String HERO_CLASS_ENTRY = "g.class";

  public static EditorWorkspace load(Path selectedJar) throws IOException {
    Path inputJar = selectedJar.toAbsolutePath().normalize();
    String baseName = baseName(inputJar);
    Path workDir = editorUserPath("temp").resolve(baseName);
    Path gameDat = workDir.resolve(GAME_DAT_PATH);
    Path itemDat = workDir.resolve(ITEM_DAT_PATH);
    Path outputJar = editorUserPath("dist").resolve(baseName + "-patched.jar");
    ExtractedDataFiles extracted = extractDataFilesFromJar(inputJar, gameDat, itemDat);
    ResistanceOverflowClassPatcher.State patchState =
        ResistanceOverflowClassPatcher.state(readJarEntry(inputJar, HERO_CLASS_ENTRY));
    GameData data = GameData.loadFromOriginalClasses(inputJar);
    List<SkillLevelSnapshot> skillLevels =
        data.skillLevels.stream().map(EditorLoadService::snapshot).toList();
    List<TalentSnapshot> talents = data.talents.stream().map(EditorLoadService::snapshot).toList();
    List<HeroSnapshot> heroes = data.heroes.stream().map(EditorLoadService::snapshot).toList();
    List<ItemSnapshot> items = data.items.stream().map(EditorLoadService::snapshot).toList();
    List<MonsterSnapshot> monsters =
        data.monsters.stream().map(EditorLoadService::snapshot).toList();
    List<StatusSnapshot> statuses =
        data.statuses.stream().map(EditorLoadService::snapshot).toList();
    log.info(
        "Loaded JavaFX workspace from {} with skills={}, talents={}, heroes={}, items={}, monsters={}, statuses={} and resistance state {}",
        inputJar,
        skillLevels.size(),
        talents.size(),
        heroes.size(),
        items.size(),
        monsters.size(),
        statuses.size(),
        patchState);
    return EditorWorkspace.builder()
        .inputJar(inputJar)
        .gameDat(gameDat)
        .itemDat(itemDat)
        .outputJar(outputJar)
        .gameDatEntryName(extracted.gameDatEntryName())
        .itemDatEntryName(extracted.itemDatEntryName())
        .resistanceOverflowState(patchState.name())
        .skillLevels(skillLevels)
        .talents(talents)
        .heroes(heroes)
        .items(items)
        .monsters(monsters)
        .statuses(statuses)
        .build();
  }

  private static String baseName(Path inputJar) {
    Path fileName = inputJar.getFileName();
    String baseName = fileName == null ? VDDOH_ROOT : fileName.toString();
    return baseName.toLowerCase().endsWith(".jar")
        ? baseName.substring(0, baseName.length() - 4)
        : baseName;
  }

  private static ExtractedDataFiles extractDataFilesFromJar(
      Path inputJar, Path gameDat, Path itemDat) throws IOException {
    log.info("Extracting JavaFX data files from {} into {} and {}", inputJar, gameDat, itemDat);
    Files.createDirectories(gameDat.toAbsolutePath().getParent());
    Files.createDirectories(itemDat.toAbsolutePath().getParent());
    String gameDatEntryName = null;
    String itemDatEntryName = null;
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          String name = entry.getName();
          String lower = name.toLowerCase();
          if (lower.equals(GAME_DAT_PATH) || lower.endsWith("/game.dat")) {
            Files.write(gameDat, readZipEntry(in));
            gameDatEntryName = name;
          } else if (lower.equals(ITEM_DAT_PATH) || lower.endsWith("/item.dat")) {
            Files.write(itemDat, readZipEntry(in));
            itemDatEntryName = name;
          }
        }
        in.closeEntry();
      }
    }
    if (gameDatEntryName == null || itemDatEntryName == null) {
      throw new IOException("Selected JAR must contain game.dat and item.dat.");
    }
    return new ExtractedDataFiles(gameDatEntryName, itemDatEntryName);
  }

  private static ItemSnapshot snapshot(ItemRow row) {
    return ItemSnapshot.builder()
        .id(row.id)
        .name(row.name)
        .rawType(row.rawType)
        .category(row.category)
        .subtype(row.subtype)
        .slotLabel(row.slotLabel)
        .allowedClasses(row.allowedClasses)
        .price(row.price)
        .icon(row.icon)
        .hpRestore(row.hpRestore)
        .resourceRestore(row.resourceRestore)
        .hpBonus(row.hpBonus)
        .resourceBonus(row.resourceBonus)
        .weaponReach(row.weaponReach)
        .weaponMode(row.weaponMode)
        .notes(row.notes)
        .effects(row.effects.stream().map(EditorLoadService::snapshot).toList())
        .build();
  }

  private static TalentSnapshot snapshot(TalentRow row) {
    return TalentSnapshot.builder()
        .group(row.group)
        .id(row.id)
        .name(row.name)
        .talentType(row.talentType())
        .currentLevel(row.currentLevel)
        .maxLevel(row.maxLevel)
        .amount(row.amount)
        .globalBonus(row.globalBonus)
        .skillUnlock(row.skillUnlock)
        .castableSkillId(row.castableSkillIdText())
        .unlockedSkillName(row.unlockedSkillName)
        .statusBonus(row.statusBonus)
        .resistanceBonus(row.resistanceBonus)
        .heroBonus(row.heroBonus)
        .effectName(row.effectName())
        .level1(row.levelValueText(1))
        .level2(row.levelValueText(2))
        .level3(row.levelValueText(3))
        .level4(row.levelValueText(4))
        .notes(row.notes)
        .build();
  }

  private static HeroSnapshot snapshot(HeroRow row) {
    return HeroSnapshot.builder()
        .id(row.id)
        .name(row.name)
        .strength(snapshot(row.strength))
        .spirit(snapshot(row.spirit))
        .vitality(snapshot(row.vitality))
        .speed(snapshot(row.speed))
        .levelCap(row.levelCap)
        .baseCritChance(row.baseCritChance)
        .baseCritDamage(row.baseCritDamage)
        .baseEvasion(HeroRow.BASE_EVASION)
        .baseHp(row.baseHp())
        .baseResource(row.baseResource())
        .baseAttack(row.baseAttack())
        .baseDefense(row.baseDefense())
        .baseMove(row.baseMove())
        .baseRegen(HeroRow.BASE_HP_REGEN)
        .strengthAtCap(row.strengthAtCap())
        .spiritAtCap(row.spiritAtCap())
        .vitalityAtCap(row.vitalityAtCap())
        .speedAtCap(row.speedAtCap())
        .notes(row.notes)
        .build();
  }

  private static StatCurveSnapshot snapshot(StatCurve curve) {
    return StatCurveSnapshot.builder()
        .start(curve.start)
        .target(curve.target)
        .curve(curve.curve)
        .build();
  }

  private static MonsterSnapshot snapshot(MonsterRow row) {
    return MonsterSnapshot.builder()
        .id(row.id())
        .name(row.name())
        .experience(row.experience())
        .filar(row.filar())
        .deathValue(row.deathValue())
        .effectId(row.effectId())
        .strength(row.strength())
        .spirit(row.spirit())
        .vitality(row.vitality())
        .speed(row.speed())
        .baseHp(row.baseHp())
        .baseResource(row.baseResource())
        .baseAttack(row.baseAttack())
        .baseDefense(row.baseDefense())
        .baseMove(row.baseMove())
        .hitChance(row.hitChance())
        .critOrDamage(row.critOrDamage())
        .evadeOrGuard(row.evadeOrGuard())
        .packedChance(row.packedChance())
        .packedTailA(row.packedTailA())
        .packedTailB(row.packedTailB())
        .actionCount(row.actionCount())
        .effectCount(row.effectCount())
        .dropCount(row.dropCount())
        .notes(row.notes())
        .build();
  }

  private static StatusSnapshot snapshot(StatusRow row) {
    return StatusSnapshot.builder()
        .id(row.id)
        .name(row.name)
        .duration(row.duration)
        .expireChance(row.expireChance)
        .icon(row.icon)
        .notes(row.notes)
        .build();
  }

  private static SkillLevelSnapshot snapshot(SkillLevelRow row) {
    return SkillLevelSnapshot.builder()
        .skillId(row.skillId)
        .skillName(row.skillName)
        .levelIndex(row.levelIndex)
        .cost(row.cost)
        .areaShape(row.areaShape)
        .areaWidth(row.areaWidth)
        .areaHeight(row.areaHeight)
        .range(row.range)
        .relativeAreaGrowth(row.relativeAreaGrowth)
        .notes(row.notes)
        .effects(row.effects.stream().map(EditorLoadService::snapshot).toList())
        .build();
  }

  private static SkillEffectSnapshot snapshot(SkillEffectRow row) {
    return SkillEffectSnapshot.builder()
        .type(row.type)
        .index(row.index)
        .targetId(row.targetId)
        .target(row.target)
        .value(row.displayValue())
        .editable(row.editable)
        .notes(row.notes)
        .build();
  }

  private static ItemEffectSnapshot snapshot(ItemEffectRow row) {
    return ItemEffectSnapshot.builder()
        .side(row.side())
        .type(row.type())
        .target(row.target())
        .value(row.value())
        .extra(row.extra())
        .raw(row.raw())
        .build();
  }

  private record ExtractedDataFiles(String gameDatEntryName, String itemDatEntryName) {}
}
