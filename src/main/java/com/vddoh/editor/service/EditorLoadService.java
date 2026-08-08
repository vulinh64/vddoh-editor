package com.vddoh.editor.service;

import static com.vddoh.editor.service.EditorPatchService.GAME_ENGINE_CLASS_ENTRY;
import static com.vddoh.editor.service.EditorPatchService.HERO_CLASS_ENTRY;
import static com.vddoh.editor.service.EditorPatchService.BATTLE_UNIT_CLASS_ENTRY;
import static com.vddoh.editor.utils.EditorSupport.editorUserPath;
import static com.vddoh.editor.utils.EditorSupport.readJarEntry;
import static com.vddoh.editor.utils.EditorSupport.readZipEntry;
import static com.vddoh.editor.utils.EditorSupport.u8;

import com.vddoh.editor.data.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
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
  private static final String M_DAT_PATH = "m.dat";
  private static final String EXPECTED_MIDLET_NAME = "Vampires Dawn: Deceit of Heretics";
  private static final String EXPECTED_MIDLET_1 = "VampiresDawn,/s.png,VD";

  public static EditorWorkspace load(Path selectedJar) throws IOException {
    Path inputJar = selectedJar.toAbsolutePath().normalize();
    validateVddohManifest(inputJar);
    String baseName = baseName(inputJar);
    Path workDir = editorUserPath("temp").resolve(baseName);
    Path gameDat = workDir.resolve(GAME_DAT_PATH);
    Path itemDat = workDir.resolve(ITEM_DAT_PATH);
    Path mDat = workDir.resolve(M_DAT_PATH);
    Path outputJar =
        EditorPatchService.nextAvailableOutputJar(
            editorUserPath("dist").resolve(baseName + "-patched-0001.jar"));
    ExtractedDataFiles extracted = extractDataFilesFromJar(inputJar, gameDat, itemDat, mDat);
    byte[] heroClass = readJarEntry(inputJar, HERO_CLASS_ENTRY);
    byte[] battleUnitClass = readJarEntry(inputJar, BATTLE_UNIT_CLASS_ENTRY);
    byte[] gameEngineClass = readJarEntry(inputJar, GAME_ENGINE_CLASS_ENTRY);
    ResistanceOverflowClassPatcher.State patchState =
        ResistanceOverflowClassPatcher.state(heroClass);
    EquipmentBonusClassPatcher.State equipmentBonusState =
        EquipmentBonusClassPatcher.state(heroClass);
    PhysicalDamageCapClassPatcher.State physicalDamageCapState =
        PhysicalDamageCapClassPatcher.state(heroClass, battleUnitClass);
    HighValueDisplayClassPatcher.State highValueDisplayState =
        HighValueDisplayClassPatcher.state(gameEngineClass);
    HighValueGraphicDisplayClassPatcher.State highValueGraphicDisplayState =
        HighValueGraphicDisplayClassPatcher.state(gameEngineClass);
    VictoryRewardClassPatcher.State victoryRewardState =
        VictoryRewardClassPatcher.state(gameEngineClass);
    MonsterRewardClassPatcher.State monsterRewardParserState =
        MonsterRewardClassPatcher.state(gameEngineClass);
    DiagonalBackAttackClassPatcher.State diagonalBackAttackState =
        DiagonalBackAttackClassPatcher.state(battleUnitClass);
    GameData data = GameData.loadFromOriginalClasses(inputJar);
    List<SkillLevelSnapshot> skillLevels =
        data.skillLevels.stream().map(EditorSnapshots::skillLevel).toList();
    List<TalentSnapshot> talents = data.talents.stream().map(EditorSnapshots::talent).toList();
    List<HeroSnapshot> heroes = data.heroes.stream().map(EditorSnapshots::hero).toList();
    List<ItemSnapshot> items = data.items.stream().map(EditorSnapshots::item).toList();
    List<ShopSnapshot> shops = MdatShopService.parse(Files.readAllBytes(mDat), items);
    List<MonsterSnapshot> monsters =
        applyRawMonsterHeaders(
            data.monsters.stream().map(EditorSnapshots::monster).toList(),
            Files.readAllBytes(gameDat));
    List<StatusSnapshot> statuses = data.statuses.stream().map(EditorSnapshots::status).toList();
    log.info(
        "Loaded JavaFX workspace from {} with skills={}, talents={}, heroes={}, items={}, shops={}, monsters={}, statuses={}, resistance state {}, equipment bonus state {}, physical damage cap state {}, high-value display state {}, high-value graphic display state {}, victory reward state {}, monster reward parser state {}, diagonal back-attack state {}",
        inputJar,
        skillLevels.size(),
        talents.size(),
        heroes.size(),
        items.size(),
        shops.size(),
        monsters.size(),
        statuses.size(),
        patchState,
        equipmentBonusState,
        physicalDamageCapState,
        highValueDisplayState,
        highValueGraphicDisplayState,
        victoryRewardState,
        monsterRewardParserState,
        diagonalBackAttackState);
    return EditorWorkspace.builder()
        .inputJar(inputJar)
        .gameDat(gameDat)
        .itemDat(itemDat)
        .mDat(mDat)
        .outputJar(outputJar)
        .gameDatEntryName(extracted.gameDatEntryName())
        .itemDatEntryName(extracted.itemDatEntryName())
        .mDatEntryName(extracted.mDatEntryName())
        .resistanceOverflowState(PatchState.from(patchState))
        .equipmentBonusState(PatchState.from(equipmentBonusState))
        .physicalDamageCapState(PatchState.from(physicalDamageCapState))
        .highValueDisplayState(PatchState.from(highValueDisplayState))
        .highValueGraphicDisplayState(PatchState.from(highValueGraphicDisplayState))
        .victoryRewardState(PatchState.from(victoryRewardState))
        .monsterRewardParserState(PatchState.from(monsterRewardParserState))
        .diagonalBackAttackState(PatchState.from(diagonalBackAttackState))
        .skillLevels(skillLevels)
        .talents(talents)
        .heroes(heroes)
        .items(items)
        .shops(shops)
        .monsters(monsters)
        .statuses(statuses)
        .build();
  }

  private static List<MonsterSnapshot> applyRawMonsterHeaders(
      List<MonsterSnapshot> monsters, byte[] gameData) {
    MonsterOffsets[] offsets = GameDatMonsterPatcher.parseMonsterOffsets(gameData);
    return monsters.stream()
        .map(monster -> applyRawMonsterHeader(monster, offsets, gameData))
        .toList();
  }

  private static MonsterSnapshot applyRawMonsterHeader(
      MonsterSnapshot monster, MonsterOffsets[] offsets, byte[] gameData) {
    if (monster.id() < 0 || monster.id() >= offsets.length) {
      return monster;
    }
    int offset = offsets[monster.id()].fixedOffset();
    int experience = (u8(gameData[offset]) << 4) | (u8(gameData[offset + 1]) >>> 4);
    int filar = ((u8(gameData[offset + 1]) & 0x0f) << 8) | u8(gameData[offset + 2]);
    int soulRestore = u8(gameData[offset + 3]);
    return monster.withExperience(experience).withFilar(filar).withDeathValue(soulRestore);
  }

  private static String baseName(Path inputJar) {
    Path fileName = inputJar.getFileName();
    String baseName = fileName == null ? VDDOH_ROOT : fileName.toString();
    return baseName.toLowerCase().endsWith(".jar")
        ? baseName.substring(0, baseName.length() - 4)
        : baseName;
  }

  private static void validateVddohManifest(Path inputJar) throws IOException {
    try (JarFile jar = new JarFile(inputJar.toFile())) {
      Manifest manifest = jar.getManifest();
      if (manifest == null) {
        throw new IOException(
            "Selected JAR is not Vampires Dawn: Deceit of Heretics: missing META-INF/MANIFEST.MF.");
      }
      Attributes attributes = manifest.getMainAttributes();
      String midletName = attributes.getValue("MIDlet-Name");
      String midlet1 = attributes.getValue("MIDlet-1");
      if (!EXPECTED_MIDLET_NAME.equals(midletName) || !EXPECTED_MIDLET_1.equals(midlet1)) {
        throw new IOException(
            "Selected JAR is not Vampires Dawn: Deceit of Heretics: expected MIDlet-Name='%s' and MIDlet-1='%s'."
                .formatted(EXPECTED_MIDLET_NAME, EXPECTED_MIDLET_1));
      }
      log.info("Validated VDDOH manifest for {}", inputJar);
    }
  }

  private static ExtractedDataFiles extractDataFilesFromJar(
      Path inputJar, Path gameDat, Path itemDat, Path mDat) throws IOException {
    log.info("Extracting JavaFX data files from {} into {}, {}, and {}", inputJar, gameDat, itemDat, mDat);
    Files.createDirectories(gameDat.toAbsolutePath().getParent());
    Files.createDirectories(itemDat.toAbsolutePath().getParent());
    Files.createDirectories(mDat.toAbsolutePath().getParent());
    String gameDatEntryName = null;
    String itemDatEntryName = null;
    String mDatEntryName = null;
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
          } else if (lower.equals(M_DAT_PATH) || lower.endsWith("/m.dat")) {
            Files.write(mDat, readZipEntry(in));
            mDatEntryName = name;
          }
        }
        in.closeEntry();
      }
    }
    if (gameDatEntryName == null || itemDatEntryName == null || mDatEntryName == null) {
      throw new IOException("Selected JAR must contain game.dat, item.dat, and m.dat.");
    }
    return new ExtractedDataFiles(gameDatEntryName, itemDatEntryName, mDatEntryName);
  }

  private record ExtractedDataFiles(String gameDatEntryName, String itemDatEntryName, String mDatEntryName) {}
}
