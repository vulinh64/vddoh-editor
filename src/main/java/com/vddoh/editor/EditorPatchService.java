package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.readJarEntry;
import static com.vddoh.editor.EditorSupport.replaceJarEntries;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EditorPatchService {

  private static final String HERO_CLASS_ENTRY = "g.class";

  public static BuildResult buildResistanceOverflowPatch(EditorWorkspace workspace)
      throws IOException {
    if (workspace == null) {
      throw new IllegalArgumentException("Load a VDDOH JAR before building a patch.");
    }
    byte[] heroClass = readJarEntry(workspace.inputJar(), HERO_CLASS_ENTRY);
    PatchSummary summary = ResistanceOverflowClassPatcher.patch(heroClass);
    Files.createDirectories(workspace.outputJar().toAbsolutePath().getParent());

    Map<String, byte[]> replacements = new LinkedHashMap<>();
    replacements.put(HERO_CLASS_ENTRY, heroClass);
    replaceJarEntries(workspace.inputJar(), workspace.outputJar(), replacements);
    return BuildResult.builder()
        .outputJar(workspace.outputJar())
        .summary("class patches: " + summary)
        .build();
  }

  public static BuildResult buildGameDataPatch(
      EditorWorkspace workspace,
      List<TalentEdit> talentEdits,
      List<HeroEdit> heroEdits,
      List<MonsterEdit> monsterEdits,
      List<StatusEdit> statusEdits)
      throws IOException {
    if (workspace == null) {
      throw new IllegalArgumentException("Load a VDDOH JAR before building a patch.");
    }
    List<TalentPatch> talentPatches =
        nullToEmpty(talentEdits).stream().map(EditorPatchService::patch).toList();
    List<HeroPatch> heroPatches =
        nullToEmpty(heroEdits).stream().map(EditorPatchService::patch).toList();
    List<MonsterPatch> monsterPatches =
        nullToEmpty(monsterEdits).stream().map(EditorPatchService::patch).toList();
    List<StatusPatch> statusPatches =
        nullToEmpty(statusEdits).stream().map(EditorPatchService::patch).toList();
    if (talentPatches.isEmpty()
        && heroPatches.isEmpty()
        && monsterPatches.isEmpty()
        && statusPatches.isEmpty()) {
      throw new IllegalArgumentException("No game.dat edits to patch.");
    }

    log.info(
        "Building JavaFX game.dat patch with talents={}, heroes={}, monsters={}, statuses={}",
        talentPatches.size(),
        heroPatches.size(),
        monsterPatches.size(),
        statusPatches.size());
    byte[] gameData = Files.readAllBytes(workspace.gameDat());
    List<String> summaries = new ArrayList<>();
    if (!talentPatches.isEmpty()) {
      summaries.add("talents: " + GameDatTalentPatcher.patch(gameData, talentPatches));
    }
    if (!heroPatches.isEmpty()) {
      summaries.add("heroes: " + GameDatHeroPatcher.patch(gameData, heroPatches));
    }
    if (!monsterPatches.isEmpty()) {
      summaries.add("monsters: " + GameDatMonsterPatcher.patch(gameData, monsterPatches));
    }
    if (!statusPatches.isEmpty()) {
      summaries.add("statuses: " + GameDatStatusPatcher.patch(gameData, statusPatches));
    }
    Files.createDirectories(workspace.outputJar().toAbsolutePath().getParent());
    Files.write(workspace.outputJar().resolveSibling("game.dat"), gameData);

    Map<String, byte[]> replacements = new LinkedHashMap<>();
    replacements.put(workspace.gameDatEntryName(), gameData);
    replaceJarEntries(workspace.inputJar(), workspace.outputJar(), replacements);
    return BuildResult.builder()
        .outputJar(workspace.outputJar())
        .summary(String.join("; ", summaries))
        .build();
  }

  public static BuildResult buildSkillPatch(EditorWorkspace workspace, List<SkillEdit> edits)
      throws IOException {
    if (workspace == null) {
      throw new IllegalArgumentException("Load a VDDOH JAR before building a patch.");
    }
    if (edits == null || edits.isEmpty()) {
      throw new IllegalArgumentException("No skill edits to patch.");
    }
    List<SkillPatch> skillPatches = edits.stream().map(EditorPatchService::patch).toList();
    log.info("Building JavaFX skill patch with {} edited skill levels", skillPatches.size());
    byte[] gameData = Files.readAllBytes(workspace.gameDat());
    PatchSummary summary = GameDatSkillPatcher.patch(gameData, skillPatches);
    Files.createDirectories(workspace.outputJar().toAbsolutePath().getParent());
    Files.write(workspace.outputJar().resolveSibling("game.dat"), gameData);

    Map<String, byte[]> replacements = new LinkedHashMap<>();
    replacements.put(workspace.gameDatEntryName(), gameData);
    replaceJarEntries(workspace.inputJar(), workspace.outputJar(), replacements);
    return BuildResult.builder()
        .outputJar(workspace.outputJar())
        .summary("skills: " + summary)
        .build();
  }

  public static BuildResult buildItemPatch(EditorWorkspace workspace, List<ItemEdit> edits)
      throws IOException {
    if (workspace == null) {
      throw new IllegalArgumentException("Load a VDDOH JAR before building a patch.");
    }
    if (edits == null || edits.isEmpty()) {
      throw new IllegalArgumentException("No item edits to patch.");
    }

    List<ItemPatch> patches =
        edits.stream()
            .map(
                edit ->
                    ItemPatch.builder()
                        .itemId(edit.itemId())
                        .price(edit.price())
                        .icon(edit.icon())
                        .hpRestore(edit.hpRestore())
                        .resourceRestore(edit.resourceRestore())
                        .build())
            .toList();

    log.info("Building JavaFX item-only patch with {} edits", patches.size());
    byte[] itemData = Files.readAllBytes(workspace.itemDat());
    PatchSummary summary = ItemDatPatcher.patch(itemData, patches);
    Files.createDirectories(workspace.outputJar().toAbsolutePath().getParent());
    Files.write(workspace.outputJar().resolveSibling("item.dat"), itemData);

    Map<String, byte[]> replacements = new LinkedHashMap<>();
    replacements.put(workspace.itemDatEntryName(), itemData);
    replaceJarEntries(workspace.inputJar(), workspace.outputJar(), replacements);
    return BuildResult.builder()
        .outputJar(workspace.outputJar())
        .summary("items: " + summary)
        .build();
  }

  private static TalentPatch patch(TalentEdit edit) {
    return TalentPatch.builder()
        .group(edit.group())
        .talentId(edit.talentId())
        .maxLevel(edit.maxLevel())
        .amount(edit.amount())
        .globalBonus(edit.globalBonus())
        .skillUnlock(edit.skillUnlock())
        .statusBonus(edit.statusBonus())
        .resistanceBonus(edit.resistanceBonus())
        .heroBonus(edit.heroBonus())
        .build();
  }

  private static SkillPatch patch(SkillEdit edit) {
    return SkillPatch.builder()
        .skillId(edit.skillId())
        .levelIndex(edit.levelIndex())
        .cost(edit.cost())
        .effects(edit.effects().stream().map(EditorPatchService::row).toList())
        .build();
  }

  private static SkillEffectRow row(SkillEffectEdit edit) {
    int originalEncodedValue =
        SkillEffectRow.REMOVE_STATUS_LABEL.equals(edit.type())
            ? -Math.abs(edit.originalValue())
            : edit.originalValue();
    SkillEffectRow row =
        new SkillEffectRow(
            edit.type(),
            edit.index(),
            edit.targetId(),
            edit.target(),
            originalEncodedValue,
            edit.editable(),
            edit.notes());
    int displayValue =
        SkillEffectRow.REMOVE_STATUS_LABEL.equals(edit.type())
            ? Math.abs(edit.value())
            : edit.value();
    row.setDisplayValue(displayValue);
    return row;
  }

  private static HeroPatch patch(HeroEdit edit) {
    return HeroPatch.builder()
        .heroId(edit.heroId())
        .strength(curve(edit.strength()))
        .spirit(curve(edit.spirit()))
        .vitality(curve(edit.vitality()))
        .speed(curve(edit.speed()))
        .levelCap(edit.levelCap())
        .baseCritChance(edit.baseCritChance())
        .baseCritDamage(edit.baseCritDamage())
        .build();
  }

  private static MonsterPatch patch(MonsterEdit edit) {
    return MonsterPatch.builder()
        .monsterId(edit.monsterId())
        .experience(edit.experience())
        .filar(edit.filar())
        .deathValue(edit.deathValue())
        .effectId(edit.effectId())
        .strength(edit.strength())
        .spirit(edit.spirit())
        .vitality(edit.vitality())
        .speed(edit.speed())
        .build();
  }

  private static StatusPatch patch(StatusEdit edit) {
    return StatusPatch.builder()
        .statusId(edit.statusId())
        .duration(edit.duration())
        .expireChance(edit.expireChance())
        .icon(edit.icon())
        .build();
  }

  private static StatCurve curve(StatCurveEdit edit) {
    return new StatCurve(edit.start(), edit.target(), edit.curve());
  }

  private static <T> List<T> nullToEmpty(List<T> values) {
    return values == null ? List.of() : values;
  }
}
