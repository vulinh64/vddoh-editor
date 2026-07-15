package com.vddoh.editor.service;

import static com.vddoh.editor.service.EditorPatchService.GAME_ENGINE_CLASS_ENTRY;
import static com.vddoh.editor.service.EditorPatchService.HERO_CLASS_ENTRY;
import static com.vddoh.editor.utils.EditorSupport.readJarEntry;

import com.vddoh.editor.data.EditorWorkspace;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClassPatchService {

  static void addClassPatches(
      EditorWorkspace workspace,
      ClassPatchSelection selection,
      Map<String, byte[]> replacements,
      List<String> summaries)
      throws IOException {
    if (!selection.anyRequested()) {
      return;
    }

    ClassPatchContext context = new ClassPatchContext(workspace);
    List<String> patchSummaries = new ArrayList<>();
    patchHeroClass(selection, context, patchSummaries);
    patchGameEngineClass(selection, context, patchSummaries);
    summaries.add("class patches: " + String.join(", ", patchSummaries));
    context.addReplacements(replacements);
  }

  private static void patchHeroClass(
      ClassPatchSelection selection, ClassPatchContext context, List<String> summaries)
      throws IOException {
    if (selection.resistanceOverflow()) {
      summaries.add(
          "resistance overflow: " + ResistanceOverflowClassPatcher.patch(context.heroClass()));
    }
    if (selection.equipmentBonus()) {
      EquipmentBonusClassPatcher.Result result =
          EquipmentBonusClassPatcher.patch(context.heroClass());
      context.heroClass(result.data());
      summaries.add("equipment bonus: " + result.summary());
    }
    if (selection.physicalDamageCap()) {
      PhysicalDamageCapClassPatcher.Result result =
          PhysicalDamageCapClassPatcher.patch(context.heroClass());
      context.heroClass(result.data());
      summaries.add("physical damage cap: " + result.summary());
    }
  }

  private static void patchGameEngineClass(
      ClassPatchSelection selection, ClassPatchContext context, List<String> summaries)
      throws IOException {
    if (selection.monsterRewardParser()) {
      MonsterRewardClassPatcher.Result result =
          MonsterRewardClassPatcher.patch(context.gameEngineClass());
      context.gameEngineClass(result.data());
      summaries.add("monster reward parser: " + result.summary());
    }
    if (selection.highValueDisplay()) {
      HighValueDisplayClassPatcher.Result result =
          HighValueDisplayClassPatcher.patch(context.gameEngineClass());
      context.gameEngineClass(result.data());
      summaries.add("high-value display: " + result.summary());
    }
    if (selection.highValueGraphicDisplay()) {
      HighValueGraphicDisplayClassPatcher.Result result =
          HighValueGraphicDisplayClassPatcher.patch(context.gameEngineClass());
      context.gameEngineClass(result.data());
      summaries.add("high-value graphic display: " + result.summary());
    }
    if (selection.victoryReward()) {
      summaries.add(
          "victory reward: " + VictoryRewardClassPatcher.patch(context.gameEngineClass()));
    }
  }

  private static final class ClassPatchContext {
    private final EditorWorkspace workspace;
    private byte[] heroClass;
    private byte[] gameEngineClass;

    private ClassPatchContext(EditorWorkspace workspace) {
      this.workspace = workspace;
    }

    private byte[] heroClass() throws IOException {
      if (heroClass == null) {
        heroClass = readJarEntry(workspace.inputJar(), HERO_CLASS_ENTRY);
      }
      return heroClass;
    }

    private void heroClass(byte[] heroClass) {
      this.heroClass = heroClass;
    }

    private byte[] gameEngineClass() throws IOException {
      if (gameEngineClass == null) {
        gameEngineClass = readJarEntry(workspace.inputJar(), GAME_ENGINE_CLASS_ENTRY);
      }
      return gameEngineClass;
    }

    private void gameEngineClass(byte[] gameEngineClass) {
      this.gameEngineClass = gameEngineClass;
    }

    private void addReplacements(Map<String, byte[]> replacements) {
      if (heroClass != null) {
        replacements.put(HERO_CLASS_ENTRY, heroClass);
      }
      if (gameEngineClass != null) {
        replacements.put(GAME_ENGINE_CLASS_ENTRY, gameEngineClass);
      }
    }
  }
}
