package com.vddoh.editor.service;

import static com.vddoh.editor.service.EditorPatchService.HERO_CLASS_ENTRY;
import static com.vddoh.editor.utils.EditorSupport.editorUserPath;
import static com.vddoh.editor.utils.EditorSupport.readJarEntry;
import static com.vddoh.editor.utils.EditorSupport.readZipEntry;

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
  private static final String EXPECTED_MIDLET_NAME = "Vampires Dawn: Deceit of Heretics";
  private static final String EXPECTED_MIDLET_1 = "VampiresDawn,/s.png,VD";

  public static EditorWorkspace load(Path selectedJar) throws IOException {
    Path inputJar = selectedJar.toAbsolutePath().normalize();
    validateVddohManifest(inputJar);
    String baseName = baseName(inputJar);
    Path workDir = editorUserPath("temp").resolve(baseName);
    Path gameDat = workDir.resolve(GAME_DAT_PATH);
    Path itemDat = workDir.resolve(ITEM_DAT_PATH);
    Path outputJar =
        EditorPatchService.nextAvailableOutputJar(
            editorUserPath("dist").resolve(baseName + "-patched-0001.jar"));
    ExtractedDataFiles extracted = extractDataFilesFromJar(inputJar, gameDat, itemDat);
    byte[] heroClass = readJarEntry(inputJar, HERO_CLASS_ENTRY);
    ResistanceOverflowClassPatcher.State patchState =
        ResistanceOverflowClassPatcher.state(heroClass);
    EquipmentBonusClassPatcher.State equipmentBonusState =
        EquipmentBonusClassPatcher.state(heroClass);
    GameData data = GameData.loadFromOriginalClasses(inputJar);
    List<SkillLevelSnapshot> skillLevels =
        data.skillLevels.stream().map(EditorSnapshots::skillLevel).toList();
    List<TalentSnapshot> talents = data.talents.stream().map(EditorSnapshots::talent).toList();
    List<HeroSnapshot> heroes = data.heroes.stream().map(EditorSnapshots::hero).toList();
    List<ItemSnapshot> items = data.items.stream().map(EditorSnapshots::item).toList();
    List<MonsterSnapshot> monsters = data.monsters.stream().map(EditorSnapshots::monster).toList();
    List<StatusSnapshot> statuses = data.statuses.stream().map(EditorSnapshots::status).toList();
    log.info(
        "Loaded JavaFX workspace from {} with skills={}, talents={}, heroes={}, items={}, monsters={}, statuses={}, resistance state {}, equipment bonus state {}",
        inputJar,
        skillLevels.size(),
        talents.size(),
        heroes.size(),
        items.size(),
        monsters.size(),
        statuses.size(),
        patchState,
        equipmentBonusState);
    return EditorWorkspace.builder()
        .inputJar(inputJar)
        .gameDat(gameDat)
        .itemDat(itemDat)
        .outputJar(outputJar)
        .gameDatEntryName(extracted.gameDatEntryName())
        .itemDatEntryName(extracted.itemDatEntryName())
        .resistanceOverflowState(patchState.name())
        .equipmentBonusState(equipmentBonusState.name())
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

  private record ExtractedDataFiles(String gameDatEntryName, String itemDatEntryName) {}
}
