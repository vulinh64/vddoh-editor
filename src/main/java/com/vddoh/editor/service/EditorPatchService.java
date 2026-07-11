package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.readJarEntry;
import static com.vddoh.editor.utils.EditorSupport.replaceJarEntries;

import com.vddoh.editor.data.*;
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
  public static final String GAME_DAT_FILE = "game.dat";
  private static final String ITEM_DAT_FILE = "item.dat";

  public static BuildResult buildFullPatch(PatchBuildRequest request) throws IOException {
    PatchBuildPlan plan = PatchBuildPlan.from(request);
    EditorWorkspace workspace = plan.workspace();
    if (!plan.hasWork()) {
      throw new IllegalArgumentException("No edits or class patch selected.");
    }

    log.info(
        "Building JavaFX full patch with skills={}, talents={}, heroes={}, items={}, monsters={}, statuses={}, classPatchRequested={}",
        plan.skillPatches().size(),
        plan.talentPatches().size(),
        plan.heroPatches().size(),
        plan.itemPatches().size(),
        plan.monsterPatches().size(),
        plan.statusPatches().size(),
        plan.classPatchRequested());

    Files.createDirectories(workspace.outputJar().toAbsolutePath().getParent());
    Map<String, byte[]> replacements = new LinkedHashMap<>();
    List<String> summaries = new ArrayList<>();

    addGameDataPatch(plan, replacements, summaries);
    addItemDataPatch(plan, replacements, summaries);
    addClassPatch(plan, replacements, summaries);

    replaceJarEntries(workspace.inputJar(), workspace.outputJar(), replacements);
    String summary = String.join("; ", summaries);
    log.info(
        "Wrote JavaFX full patch {} with replacements {} and summary {}",
        workspace.outputJar(),
        replacements.keySet(),
        summary);
    return BuildResult.builder().outputJar(workspace.outputJar()).summary(summary).build();
  }

  private static void addGameDataPatch(
      PatchBuildPlan plan, Map<String, byte[]> replacements, List<String> summaries)
      throws IOException {
    if (!plan.hasGameDataPatches()) {
      return;
    }
    EditorWorkspace workspace = plan.workspace();
    byte[] gameData = Files.readAllBytes(workspace.gameDat());
    appendSummary(summaries, "skills", GameDatSkillPatcher.patch(gameData, plan.skillPatches()));
    appendSummary(summaries, "talents", GameDatTalentPatcher.patch(gameData, plan.talentPatches()));
    appendSummary(summaries, "heroes", GameDatHeroPatcher.patch(gameData, plan.heroPatches()));
    appendSummary(
        summaries, "monsters", GameDatMonsterPatcher.patch(gameData, plan.monsterPatches()));
    appendSummary(
        summaries, "statuses", GameDatStatusPatcher.patch(gameData, plan.statusPatches()));
    writeDebugDataFile(workspace, GAME_DAT_FILE, gameData);
    replacements.put(workspace.gameDatEntryName(), gameData);
  }

  private static void addItemDataPatch(
      PatchBuildPlan plan, Map<String, byte[]> replacements, List<String> summaries)
      throws IOException {
    if (plan.itemPatches().isEmpty()) {
      return;
    }
    EditorWorkspace workspace = plan.workspace();
    byte[] itemData = Files.readAllBytes(workspace.itemDat());
    summaries.add("items: " + ItemDatPatcher.patch(itemData, plan.itemPatches()));
    writeDebugDataFile(workspace, ITEM_DAT_FILE, itemData);
    replacements.put(workspace.itemDatEntryName(), itemData);
  }

  private static void addClassPatch(
      PatchBuildPlan plan, Map<String, byte[]> replacements, List<String> summaries)
      throws IOException {
    if (!plan.classPatchRequested()) {
      return;
    }
    byte[] heroClass = readJarEntry(plan.workspace().inputJar(), HERO_CLASS_ENTRY);
    summaries.add("class patches: " + ResistanceOverflowClassPatcher.patch(heroClass));
    replacements.put(HERO_CLASS_ENTRY, heroClass);
  }

  private static void appendSummary(List<String> summaries, String label, PatchSummary summary) {
    if (!summary.onlySkipped()) {
      summaries.add(label + ": " + summary);
    }
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
    replaceSingleEntry(workspace, workspace.gameDatEntryName(), GAME_DAT_FILE, gameData);
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
    replaceSingleEntry(workspace, workspace.gameDatEntryName(), GAME_DAT_FILE, gameData);
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

    List<ItemPatch> patches = edits.stream().map(EditorPatchService::itemPatch).toList();

    log.info("Building JavaFX item-only patch with {} edits", patches.size());
    byte[] itemData = Files.readAllBytes(workspace.itemDat());
    PatchSummary summary = ItemDatPatcher.patch(itemData, patches);
    replaceSingleEntry(workspace, workspace.itemDatEntryName(), ITEM_DAT_FILE, itemData);
    return BuildResult.builder()
        .outputJar(workspace.outputJar())
        .summary("items: " + summary)
        .build();
  }

  private static void replaceSingleEntry(
      EditorWorkspace workspace, String entryName, String debugFileName, byte[] data)
      throws IOException {
    Files.createDirectories(workspace.outputJar().toAbsolutePath().getParent());
    writeDebugDataFile(workspace, debugFileName, data);
    Map<String, byte[]> replacements = new LinkedHashMap<>();
    replacements.put(entryName, data);
    replaceJarEntries(workspace.inputJar(), workspace.outputJar(), replacements);
  }

  private static void writeDebugDataFile(
      EditorWorkspace workspace, String debugFileName, byte[] data) throws IOException {
    Files.write(workspace.outputJar().resolveSibling(debugFileName), data);
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

  private static ItemPatch itemPatch(ItemEdit edit) {
    return ItemPatch.builder()
        .itemId(edit.itemId())
        .price(edit.price())
        .icon(edit.icon())
        .hpRestore(edit.hpRestore())
        .resourceRestore(edit.resourceRestore())
        .build();
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

  private record PatchBuildPlan(
      EditorWorkspace workspace,
      List<SkillPatch> skillPatches,
      List<TalentPatch> talentPatches,
      List<HeroPatch> heroPatches,
      List<ItemPatch> itemPatches,
      List<MonsterPatch> monsterPatches,
      List<StatusPatch> statusPatches,
      boolean classPatchRequested) {

    static PatchBuildPlan from(PatchBuildRequest request) {
      if (request == null || request.workspace() == null) {
        throw new IllegalArgumentException("Load a VDDOH JAR before building a patch.");
      }
      return new PatchBuildPlan(
          request.workspace(),
          request.skillEdits().stream().map(EditorPatchService::patch).toList(),
          request.talentEdits().stream().map(EditorPatchService::patch).toList(),
          request.heroEdits().stream().map(EditorPatchService::patch).toList(),
          request.itemEdits().stream().map(EditorPatchService::itemPatch).toList(),
          request.monsterEdits().stream().map(EditorPatchService::patch).toList(),
          request.statusEdits().stream().map(EditorPatchService::patch).toList(),
          request.classPatchRequested());
    }

    boolean hasGameDataPatches() {
      return !(skillPatches.isEmpty()
          && talentPatches.isEmpty()
          && heroPatches.isEmpty()
          && monsterPatches.isEmpty()
          && statusPatches.isEmpty());
    }

    boolean hasWork() {
      return hasGameDataPatches() || !itemPatches.isEmpty() || classPatchRequested;
    }
  }
}
