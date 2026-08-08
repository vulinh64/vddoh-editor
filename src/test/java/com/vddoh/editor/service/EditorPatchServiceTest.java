package com.vddoh.editor.service;

import static com.vddoh.editor.utils.EditorSupport.readJarEntry;
import static com.vddoh.editor.utils.EditorSupport.replaceJarEntries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vddoh.editor.data.BuildResult;
import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.data.HeroEdit;
import com.vddoh.editor.data.HeroSnapshot;
import com.vddoh.editor.data.ItemEdit;
import com.vddoh.editor.data.ItemEffectEdit;
import com.vddoh.editor.data.ItemEffectSnapshot;
import com.vddoh.editor.data.ItemSnapshot;
import com.vddoh.editor.data.MonsterEdit;
import com.vddoh.editor.data.MonsterSnapshot;
import com.vddoh.editor.data.PatchBuildRequest;
import com.vddoh.editor.data.PatchState;
import com.vddoh.editor.data.ShopEdit;
import com.vddoh.editor.data.StatCurveEdit;
import com.vddoh.editor.data.StatCurveSnapshot;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorPatchServiceTest {

  private static final String VINCE = "Vince";
  private static final byte[] RESISTANCE_ORIGINAL_PREFIX =
      new byte[] {0x2a, (byte) 0xb4, 0x00, 0x46, (byte) 0xb2, 0x00, 0x15, 0x33};

  @TempDir private Path tempDir;

  private String originalUserHome;

  @BeforeEach
  void useIsolatedEditorHome() {
    originalUserHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.resolve("home").toString());
  }

  @AfterEach
  void restoreUserHome() throws IOException {
    System.setProperty("user.home", originalUserHome);
    cleanupCreatedFiles();
  }

  @Test
  void vanillaFixtureMatchesKnownBaseline() throws Exception {
    EditorWorkspace workspace = workspace("baseline.jar");
    byte[] heroClass = readJarEntry(workspace.inputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    byte[] gameEngineClass =
        readJarEntry(workspace.inputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    HeroSnapshot vince = hero(workspace);

    assertWorkspaceCounts(workspace);
    assertWorkspacePatchStates(workspace);
    assertClassPatchStates(heroClass, gameEngineClass);
    assertVinceBaseline(vince);
  }

  @Test
  void diagonalBackAttackPatchPromotesOnlyRearHalfPlanes() throws Exception {
    byte[] battleUnitClass = readJarEntry(originalJar(), EditorPatchService.BATTLE_UNIT_CLASS_ENTRY);

    assertEquals(
        DiagonalBackAttackClassPatcher.State.ORIGINAL,
        DiagonalBackAttackClassPatcher.state(battleUnitClass));
    DiagonalBackAttackClassPatcher.Result result = DiagonalBackAttackClassPatcher.patch(battleUnitClass);
    assertEquals(
        DiagonalBackAttackClassPatcher.State.PATCHED,
        DiagonalBackAttackClassPatcher.state(result.data()));
    assertTrue(DiagonalBackAttackClassPatcher.isBehind(1, 100, 100, 84, 116));
    assertTrue(DiagonalBackAttackClassPatcher.isBehind(4, 100, 100, 116, 84));
    assertTrue(DiagonalBackAttackClassPatcher.isBehind(2, 100, 100, 116, 68));
    assertTrue(DiagonalBackAttackClassPatcher.isBehind(8, 100, 100, 84, 100));
    assertFalse(DiagonalBackAttackClassPatcher.isBehind(1, 100, 100, 116, 84));
    assertFalse(DiagonalBackAttackClassPatcher.isBehind(4, 100, 100, 84, 116));
  }

  private static void assertWorkspaceCounts(EditorWorkspace workspace) {
    assertEquals(138, workspace.skillLevels().size());
    assertEquals(38, workspace.talents().size());
    assertEquals(4, workspace.heroes().size());
    assertEquals(234, workspace.items().size());
    assertEquals(65, workspace.monsters().size());
    assertEquals(42, workspace.statuses().size());
  }

  private static void assertWorkspacePatchStates(EditorWorkspace workspace) {
    assertEquals(PatchState.ORIGINAL, workspace.resistanceOverflowState());
    assertEquals(PatchState.ORIGINAL, workspace.equipmentBonusState());
    assertEquals(PatchState.ORIGINAL, workspace.physicalDamageCapState());
    assertEquals(PatchState.ORIGINAL, workspace.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, workspace.highValueGraphicDisplayState());
    assertEquals(PatchState.ORIGINAL, workspace.victoryRewardState());
    assertEquals(PatchState.ORIGINAL, workspace.monsterRewardParserState());
    assertEquals(PatchState.ORIGINAL, workspace.diagonalBackAttackState());
  }

  private static void assertClassPatchStates(byte[] heroClass, byte[] gameEngineClass) {
    assertEquals(
        ResistanceOverflowClassPatcher.State.ORIGINAL,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.ORIGINAL, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.ORIGINAL,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertEquals(
        HighValueDisplayClassPatcher.State.ORIGINAL,
        HighValueDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        HighValueGraphicDisplayClassPatcher.State.ORIGINAL,
        HighValueGraphicDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        VictoryRewardClassPatcher.State.ORIGINAL, VictoryRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        MonsterRewardClassPatcher.State.ORIGINAL, MonsterRewardClassPatcher.state(gameEngineClass));
  }

  private static void assertVinceBaseline(HeroSnapshot vince) {
    assertEquals(1, vince.id());
    assertEquals(3, vince.strength().start());
    assertEquals(3, vince.vitality().start());
    assertEquals(36, vince.baseHp());
    assertEquals(6, vince.baseAttack());
    assertEquals(3, vince.baseDefense());
    assertEquals(3, vince.baseMove());
  }

  @Test
  void appliesResistanceOverflowPatch() throws Exception {
    EditorWorkspace workspace = workspace("resistance-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .resistanceOverflowPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.PATCHED,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.ORIGINAL, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.ORIGINAL,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertTrue(result.summary().contains("resistance overflow"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.PATCHED, reloaded.resistanceOverflowState());
    assertEquals(PatchState.ORIGINAL, reloaded.equipmentBonusState());
    assertEquals(PatchState.ORIGINAL, reloaded.physicalDamageCapState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueGraphicDisplayState());
  }

  @Test
  void appliesEquipmentBonusOverwritePatch() throws Exception {
    EditorWorkspace workspace = workspace("equipment-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .equipmentBonusPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.ORIGINAL,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.PATCHED, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.ORIGINAL,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertTrue(result.summary().contains("equipment bonus"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.ORIGINAL, reloaded.resistanceOverflowState());
    assertEquals(PatchState.PATCHED, reloaded.equipmentBonusState());
    assertEquals(PatchState.ORIGINAL, reloaded.physicalDamageCapState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueGraphicDisplayState());
  }

  @Test
  void appliesPhysicalDamageCapPatch() throws Exception {
    EditorWorkspace workspace = workspace("physical-damage-cap-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .physicalDamageCapPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.ORIGINAL,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.ORIGINAL, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.PATCHED,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertTrue(result.summary().contains("physical damage cap"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.ORIGINAL, reloaded.resistanceOverflowState());
    assertEquals(PatchState.ORIGINAL, reloaded.equipmentBonusState());
    assertEquals(PatchState.PATCHED, reloaded.physicalDamageCapState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueGraphicDisplayState());
  }

  @Test
  void physicalDamageCapUsesRawDamageBandBeforeDisplayMask() {
    assertEquals(998, PhysicalDamageCapClassPatcher.capPhysicalDamageResult(998));
    assertEquals(999, PhysicalDamageCapClassPatcher.capPhysicalDamageResult(999));
    assertEquals(999, PhysicalDamageCapClassPatcher.capPhysicalDamageResult(1000));
    assertEquals(999, PhysicalDamageCapClassPatcher.capPhysicalDamageResult(1039));
    assertEquals(999, PhysicalDamageCapClassPatcher.capPhysicalDamageResult(4101));
    assertEquals(0x10000 | 999, PhysicalDamageCapClassPatcher.capPhysicalDamageResult(0x10000 | 4101));
  }

  @Test
  void appliesHighValueDisplayPatch() throws Exception {
    EditorWorkspace workspace = workspace("high-value-display-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .highValueDisplayPatchRequested(true)
                .build());

    byte[] gameEngineClass =
        readJarEntry(result.outputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    assertEquals(
        VictoryRewardClassPatcher.State.ORIGINAL, VictoryRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        MonsterRewardClassPatcher.State.ORIGINAL, MonsterRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        HighValueDisplayClassPatcher.State.PATCHED,
        HighValueDisplayClassPatcher.state(gameEngineClass));
    assertTrue(result.summary().contains("high-value display"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.ORIGINAL, reloaded.victoryRewardState());
    assertEquals(PatchState.ORIGINAL, reloaded.monsterRewardParserState());
    assertEquals(PatchState.PATCHED, reloaded.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueGraphicDisplayState());
  }

  @Test
  void appliesHighValueGraphicDisplayPatch() throws Exception {
    EditorWorkspace workspace = workspace("high-value-graphic-display-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .highValueGraphicDisplayPatchRequested(true)
                .build());

    byte[] gameEngineClass =
        readJarEntry(result.outputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    assertEquals(
        VictoryRewardClassPatcher.State.ORIGINAL, VictoryRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        MonsterRewardClassPatcher.State.ORIGINAL, MonsterRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        HighValueDisplayClassPatcher.State.ORIGINAL,
        HighValueDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        HighValueGraphicDisplayClassPatcher.State.PATCHED,
        HighValueGraphicDisplayClassPatcher.state(gameEngineClass));
    assertTrue(result.summary().contains("high-value graphic display"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.ORIGINAL, reloaded.victoryRewardState());
    assertEquals(PatchState.ORIGINAL, reloaded.monsterRewardParserState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueDisplayState());
    assertEquals(PatchState.PATCHED, reloaded.highValueGraphicDisplayState());
  }

  @Test
  void appliesVictoryRewardPatch() throws Exception {
    EditorWorkspace workspace = workspace("victory-reward-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .victoryRewardPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    byte[] gameEngineClass =
        readJarEntry(result.outputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.ORIGINAL,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.ORIGINAL, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.ORIGINAL,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertEquals(
        VictoryRewardClassPatcher.State.PATCHED, VictoryRewardClassPatcher.state(gameEngineClass));
    assertTrue(result.summary().contains("victory reward"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.ORIGINAL, reloaded.resistanceOverflowState());
    assertEquals(PatchState.ORIGINAL, reloaded.equipmentBonusState());
    assertEquals(PatchState.ORIGINAL, reloaded.physicalDamageCapState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueGraphicDisplayState());
    assertEquals(PatchState.PATCHED, reloaded.victoryRewardState());
    assertEquals(PatchState.ORIGINAL, reloaded.monsterRewardParserState());
  }

  @Test
  void appliesMonsterRewardParserPatch() throws Exception {
    EditorWorkspace workspace = workspace("monster-reward-parser-only.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .monsterRewardParserPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    byte[] gameEngineClass =
        readJarEntry(result.outputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.ORIGINAL,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.ORIGINAL, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.ORIGINAL,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertEquals(
        VictoryRewardClassPatcher.State.ORIGINAL, VictoryRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        MonsterRewardClassPatcher.State.PATCHED, MonsterRewardClassPatcher.state(gameEngineClass));
    assertTrue(result.summary().contains("monster reward parser"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.ORIGINAL, reloaded.resistanceOverflowState());
    assertEquals(PatchState.ORIGINAL, reloaded.equipmentBonusState());
    assertEquals(PatchState.ORIGINAL, reloaded.physicalDamageCapState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.highValueGraphicDisplayState());
    assertEquals(PatchState.ORIGINAL, reloaded.victoryRewardState());
    assertEquals(PatchState.PATCHED, reloaded.monsterRewardParserState());
  }

  @Test
  void appliesBothClassPatches() throws Exception {
    EditorWorkspace workspace = workspace("both-class-patches.jar");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .resistanceOverflowPatchRequested(true)
                .equipmentBonusPatchRequested(true)
                .physicalDamageCapPatchRequested(true)
                .highValueDisplayPatchRequested(true)
                .highValueGraphicDisplayPatchRequested(true)
                .victoryRewardPatchRequested(true)
                .monsterRewardParserPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    byte[] gameEngineClass =
        readJarEntry(result.outputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.PATCHED,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.PATCHED, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.PATCHED,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertEquals(
        HighValueDisplayClassPatcher.State.PATCHED,
        HighValueDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        HighValueGraphicDisplayClassPatcher.State.PATCHED,
        HighValueGraphicDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        VictoryRewardClassPatcher.State.PATCHED, VictoryRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        MonsterRewardClassPatcher.State.PATCHED, MonsterRewardClassPatcher.state(gameEngineClass));
    assertTrue(result.summary().contains("resistance overflow"));
    assertTrue(result.summary().contains("equipment bonus"));
    assertTrue(result.summary().contains("physical damage cap"));
    assertTrue(result.summary().contains("high-value display"));
    assertTrue(result.summary().contains("high-value graphic display"));
    assertTrue(result.summary().contains("victory reward"));
    assertTrue(result.summary().contains("monster reward parser"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals(PatchState.PATCHED, reloaded.resistanceOverflowState());
    assertEquals(PatchState.PATCHED, reloaded.equipmentBonusState());
    assertEquals(PatchState.PATCHED, reloaded.physicalDamageCapState());
    assertEquals(PatchState.PATCHED, reloaded.highValueDisplayState());
    assertEquals(PatchState.PATCHED, reloaded.highValueGraphicDisplayState());
    assertEquals(PatchState.PATCHED, reloaded.victoryRewardState());
    assertEquals(PatchState.PATCHED, reloaded.monsterRewardParserState());
  }

  @Test
  void classPatchesAreIdempotentOnAlreadyPatchedJar() throws Exception {
    BuildResult first =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace("idempotent-first.jar"))
                .resistanceOverflowPatchRequested(true)
                .equipmentBonusPatchRequested(true)
                .physicalDamageCapPatchRequested(true)
                .highValueDisplayPatchRequested(true)
                .highValueGraphicDisplayPatchRequested(true)
                .victoryRewardPatchRequested(true)
                .monsterRewardParserPatchRequested(true)
                .build());
    EditorWorkspace patchedWorkspace =
        EditorLoadService.load(first.outputJar())
            .withOutputJar(tempDir.resolve("idempotent-second.jar"));

    BuildResult second =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(patchedWorkspace)
                .resistanceOverflowPatchRequested(true)
                .equipmentBonusPatchRequested(true)
                .physicalDamageCapPatchRequested(true)
                .highValueDisplayPatchRequested(true)
                .highValueGraphicDisplayPatchRequested(true)
                .victoryRewardPatchRequested(true)
                .monsterRewardParserPatchRequested(true)
                .build());

    byte[] heroClass = readJarEntry(second.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    byte[] gameEngineClass =
        readJarEntry(second.outputJar(), EditorPatchService.GAME_ENGINE_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.PATCHED,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.PATCHED, EquipmentBonusClassPatcher.state(heroClass));
    assertEquals(
        PhysicalDamageCapClassPatcher.State.PATCHED,
        PhysicalDamageCapClassPatcher.state(heroClass));
    assertEquals(
        HighValueDisplayClassPatcher.State.PATCHED,
        HighValueDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        HighValueGraphicDisplayClassPatcher.State.PATCHED,
        HighValueGraphicDisplayClassPatcher.state(gameEngineClass));
    assertEquals(
        VictoryRewardClassPatcher.State.PATCHED, VictoryRewardClassPatcher.state(gameEngineClass));
    assertEquals(
        MonsterRewardClassPatcher.State.PATCHED, MonsterRewardClassPatcher.state(gameEngineClass));
    assertTrue(second.summary().contains("skipped=1"));
  }

  @Test
  void refusesUnknownResistancePatchLayoutWithoutWritingOutputJar() throws Exception {
    Path corruptedJar = tempDir.resolve("corrupted-layout.jar");
    byte[] heroClass = readJarEntry(originalJar(), EditorPatchService.HERO_CLASS_ENTRY);
    heroClass[indexOf(heroClass) + RESISTANCE_ORIGINAL_PREFIX.length] = 0x00;
    replaceJarEntries(
        originalJar(), corruptedJar, Map.of(EditorPatchService.HERO_CLASS_ENTRY, heroClass));
    EditorWorkspace workspace =
        EditorWorkspace.builder()
            .inputJar(corruptedJar)
            .outputJar(tempDir.resolve("should-not-exist.jar"))
            .build();

    assertEquals(
        ResistanceOverflowClassPatcher.State.UNKNOWN,
        ResistanceOverflowClassPatcher.state(
            readJarEntry(corruptedJar, EditorPatchService.HERO_CLASS_ENTRY)));
    assertThrows(
        Exception.class,
        () ->
            EditorPatchService.buildFullPatch(
                PatchBuildRequest.builder()
                    .workspace(workspace)
                    .resistanceOverflowPatchRequested(true)
                    .build()));
    assertFalse(Files.exists(workspace.outputJar()));
  }

  @Test
  void outputJarUsesExplicitPathThenNumberedSuffixesOnlyWhenNeeded() throws Exception {
    Path edited = tempDir.resolve("vddoh-edited.jar");

    assertEquals(
        edited.toAbsolutePath().normalize(), EditorPatchService.nextAvailableOutputJar(edited));
    Files.write(edited, new byte[] {1});
    assertEquals(
        tempDir.resolve("vddoh-edited-0001.jar").toAbsolutePath().normalize(),
        EditorPatchService.nextAvailableOutputJar(edited));
    Files.write(tempDir.resolve("vddoh-edited-0001.jar"), new byte[] {1});
    assertEquals(
        tempDir.resolve("vddoh-edited-0002.jar").toAbsolutePath().normalize(),
        EditorPatchService.nextAvailableOutputJar(edited));
  }

  @Test
  void knownItemsDecodeToConfirmedEffects() throws Exception {
    EditorWorkspace workspace = workspace("items.jar");

    assertEquals(1, item(workspace, "Sickle blade").runeSlots());
    assertEquals(2, item(workspace, "Foryn-crossbow").runeSlots());
    assertEquals(2, item(workspace, "Syr-Spear").runeSlots());
    assertEquals(1, item(workspace, "Bronze armor").runeSlots());
    assertEquals(2, item(workspace, "War plate mail").runeSlots());
    assertEffect(
        item(workspace, "Sickle blade"), "Equipment", "Packed Stat", "Strength/Power", "1");
    assertEffect(
        item(workspace, "Sickle blade"),
        "Equipment/Weapon",
        "Flat stat/damage",
        "Physical",
        "90");
    assertEffect(
        item(workspace, "War plate mail"), "Equipment", "Packed Stat", "Attack bonus", "10");
    assertEffect(
        item(workspace, "War plate mail"), "Equipment/Weapon", "Armor value", "Physical", "65");
    assertEffect(
        item(workspace, "War plate mail"),
        "Protection",
        "Status resistance",
        "Anti-bleeding",
        "30");
    assertEffect(
        item(workspace, "Strong helmet"), "Equipment/Weapon", "Armor value", "Physical", "30");
    assertEffect(item(workspace, "Aaron's shoes"), "Equipment", "Packed Stat", "Move", "1");
    assertEffect(
        item(workspace, "Aaron's shoes"), "Equipment/Weapon", "Armor value", "Physical", "20");
    assertEffect(
        item(workspace, "Aaron's shoes"), "Protection", "Status resistance", "Anti-sleep", "20");
    ItemSnapshot vampireStone = item(workspace, "Vampire stone");
    assertEquals(999, vampireStone.hpRestore());
    assertEquals(999, vampireStone.resourceRestore());
    assertEffect(vampireStone, "Consumable", "HP effect", "HP", "999");
    assertEffect(vampireStone, "Consumable", "Resource effect", "Blood/Soul", "999");
    assertEffect(vampireStone, "Consumable status effect", "Status", "Poison", "100");
  }

  @Test
  void textSpecialItemsExposeTheirReadOnlyQuestInstruction() throws Exception {
    ItemSnapshot finalQuest = item(workspace("quest-text.jar"), "This is the end (30)");

    assertEquals("Go into the church east of Lammar and kill Ayrene.", finalQuest.questInstruction());
  }

  @Test
  void childrenShopStockCanAddAndDeleteConfirmedItems() throws Exception {
    EditorWorkspace workspace = workspace("children-shop.jar");
    var lordCraft =
        workspace.shops().stream()
            .filter(shop -> shop.name().equals("Lord Craft shop"))
            .findFirst()
            .orElseThrow();
    List<Integer> updatedStock = List.of(6, 7, 24, 25, 10, 26);

    byte[] patched =
        MdatShopService.patch(
            Files.readAllBytes(workspace.mDat()),
            List.of(
                ShopEdit.builder()
                    .shopId(lordCraft.id())
                    .eventOffset(lordCraft.eventOffset())
                    .itemIds(updatedStock)
                    .build()));

    assertTrue(
        MdatShopService.parse(patched, workspace.items()).stream()
            .anyMatch(shop -> shop.itemIds().equals(updatedStock)));
  }

  @Test
  void itemPatchWritesConsumableRestoreEffectsWithoutOffsetDrift() throws Exception {
    EditorWorkspace workspace = workspace("vampire-stone-item-edit.jar");
    ItemSnapshot vampireStone = item(workspace, "Vampire stone");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .itemEdits(
                    List.of(
                        ItemEdit.builder()
                            .itemId(vampireStone.id())
                            .price(vampireStone.price())
                            .icon(vampireStone.icon())
                            .hpRestore(777)
                            .resourceRestore(888)
                            .effectEdits(
                                List.of(
                                    ItemEffectEdit.builder().raw("short_g").value(777).build(),
                                    ItemEffectEdit.builder().raw("short_h").value(888).build()))
                            .build()))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    ItemSnapshot patchedVampireStone = item(patched, "Vampire stone");
    assertEquals(777, patchedVampireStone.hpRestore());
    assertEquals(888, patchedVampireStone.resourceRestore());
    assertEffect(patchedVampireStone, "Consumable", "HP effect", "HP", "777");
    assertEffect(patchedVampireStone, "Consumable", "Resource effect", "Blood/Soul", "888");
  }

  @Test
  void itemPatchWritesRuneSlotsWithoutChangingWeaponReach() throws Exception {
    EditorWorkspace workspace = workspace("rune-slots.jar");
    ItemSnapshot sickle = item(workspace, "Sickle blade");
    ItemSnapshot warPlate = item(workspace, "War plate mail");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .itemEdits(
                    List.of(itemEdit(sickle, 4), itemEdit(warPlate, 3)))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    ItemSnapshot patchedSickle = item(patched, "Sickle blade");
    ItemSnapshot patchedWarPlate = item(patched, "War plate mail");
    assertEquals(4, patchedSickle.runeSlots());
    assertEquals(sickle.weaponReach(), patchedSickle.weaponReach());
    assertEquals(3, patchedWarPlate.runeSlots());
  }

  @Test
  void itemPatchWritesPhysicalAndElementalWeaponDamage() throws Exception {
    EditorWorkspace workspace = workspace("weapon-damage.jar");
    ItemSnapshot sickle = item(workspace, "Sickle blade");
    ItemSnapshot icyLance = item(workspace, "Icy lance");
    ItemSnapshot staffOfLight = item(workspace, "Staff of light");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .itemEdits(
                    List.of(
                        weaponDamageEdit(sickle, 321),
                        weaponDamageEdit(icyLance, 222),
                        weaponDamageEdit(staffOfLight, 123)))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    assertEffect(item(patched, "Sickle blade"), "Equipment/Weapon", "Flat stat/damage", "Physical", "321");
    assertEffect(item(patched, "Icy lance"), "Equipment/Weapon", "Elemental Damage", "Ice damage", "222");
    assertEffect(item(patched, "Staff of light"), "Equipment/Weapon", "Elemental Damage", "Fire damage", "123");
  }

  @Test
  void itemPatchCanChangeExistingWeaponDamageType() throws Exception {
    EditorWorkspace workspace = workspace("weapon-damage-type.jar");
    ItemSnapshot sickle = item(workspace, "Sickle blade");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .itemEdits(List.of(weaponDamageEdit(sickle, 150, 2)))
                .build());

    assertEffect(
        item(EditorLoadService.load(result.outputJar()), "Sickle blade"),
        "Equipment/Weapon",
        "Elemental Damage",
        "Ice damage",
        "150");
  }

  @Test
  void itemPatchWritesArmorPhysicalAbsorption() throws Exception {
    EditorWorkspace workspace = workspace("armor-absorption.jar");
    ItemSnapshot warPlate = item(workspace, "War plate mail");

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .itemEdits(List.of(armorAbsorptionEdit(warPlate)))
                .build());

    assertEffect(
        item(EditorLoadService.load(result.outputJar()), "War plate mail"),
        "Equipment/Weapon",
        "Armor value",
        "Physical",
        "200");
  }

  @Test
  void changingVinceStrengthStartTo15UpdatesExpectedHpPreview() throws Exception {
    EditorWorkspace workspace = workspace("vince-str-15.jar");
    HeroSnapshot vince = hero(workspace);

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .heroEdits(List.of(vinceWithStrengthStart(vince)))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    HeroSnapshot patchedVince = hero(patched);
    int expectedHp = (patchedVince.vitality().start() * 70 + 15 * 30) * 12 / 100;
    assertEquals(15, patchedVince.strength().start());
    assertEquals(79, expectedHp);
    assertEquals(expectedHp, patchedVince.baseHp());
  }

  @Test
  void heroStatCurveShapeRoundTripsThroughGameDatPatch() throws Exception {
    EditorWorkspace workspace = workspace("vince-str-curve.jar");
    HeroSnapshot vince = hero(workspace);

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .heroEdits(
                    List.of(
                        HeroEdit.builder()
                            .heroId(vince.id())
                            .strength(curve(vince.strength()).withCurve(37))
                            .spirit(curve(vince.spirit()))
                            .vitality(curve(vince.vitality()))
                            .speed(curve(vince.speed()))
                            .levelCap(vince.levelCap())
                            .baseCritChance(vince.baseCritChance())
                            .baseCritDamage(vince.baseCritDamage())
                            .build()))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    assertEquals(37, hero(patched).strength().curve());
  }

  @Test
  void monsterExpFilarSoulRestoreRoundTripsFromRawGameDatHeader() throws Exception {
    EditorWorkspace workspace = workspace("monster-filar.jar");
    MonsterSnapshot monster = workspace.monsters().getFirst();

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .monsterEdits(
                    List.of(
                        MonsterEdit.builder()
                            .monsterId(monster.id())
                            .experience(1200)
                            .filar(1000)
                            .deathValue(25)
                            .effectId(monster.effectId())
                            .strength(monster.strength())
                            .spirit(monster.spirit())
                            .vitality(monster.vitality())
                            .speed(monster.speed())
                            .arrayEdits(List.of())
                            .build()))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    MonsterSnapshot patchedMonster = patched.monsters().get(monster.id());
    assertEquals(1200, patchedMonster.experience());
    assertEquals(1000, patchedMonster.filar());
    assertEquals(25, patchedMonster.deathValue());
  }

  private EditorWorkspace workspace(String outputFileName) throws Exception {
    return EditorLoadService.load(originalJar()).withOutputJar(tempDir.resolve(outputFileName));
  }

  private static HeroSnapshot hero(EditorWorkspace workspace) {
    return workspace.heroes().stream()
        .filter(hero -> EditorPatchServiceTest.VINCE.equals(hero.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing hero " + EditorPatchServiceTest.VINCE));
  }

  private static ItemSnapshot item(EditorWorkspace workspace, String name) {
    return workspace.items().stream()
        .filter(item -> name.equals(item.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing item " + name));
  }

  private static ItemEdit itemEdit(ItemSnapshot item, int runeSlots) {
    return ItemEdit.builder()
        .itemId(item.id())
        .price(item.price())
        .icon(item.icon())
        .hpRestore(item.hpRestore())
        .resourceRestore(item.resourceRestore())
        .runeSlots(runeSlots)
        .effectEdits(List.of())
        .build();
  }

  private static ItemEdit weaponDamageEdit(ItemSnapshot item, int damage) {
    return weaponDamageEdit(item, damage, null);
  }

  private static ItemEdit weaponDamageEdit(ItemSnapshot item, int damage, Integer effectKind) {
    return ItemEdit.builder()
        .itemId(item.id())
        .price(item.price())
        .icon(item.icon())
        .hpRestore(item.hpRestore())
        .resourceRestore(item.resourceRestore())
        .effectEdits(
            List.of(
                ItemEffectEdit.builder()
                    .raw("int_arr_a[0]")
                    .value(damage)
                    .effectKind(effectKind)
                    .build()))
        .build();
  }

  private static ItemEdit armorAbsorptionEdit(ItemSnapshot item) {
    return ItemEdit.builder()
        .itemId(item.id())
        .price(item.price())
        .icon(item.icon())
        .hpRestore(item.hpRestore())
        .resourceRestore(item.resourceRestore())
        .effectEdits(List.of(ItemEffectEdit.builder().raw("int_arr_a[0]").value(200).build()))
        .build();
  }

  private static void assertEffect(
      ItemSnapshot item, String side, String type, String target, String value) {
    boolean found =
        item.effects().stream().anyMatch(effect -> matches(effect, side, type, target, value));
    assertTrue(
        found,
        () ->
            "Missing effect %s/%s/%s=%s on %s; effects=%s"
                .formatted(side, type, target, value, item.name(), item.effects()));
  }

  private static boolean matches(
      ItemEffectSnapshot effect, String side, String type, String target, String value) {
    return side.equals(effect.side())
        && type.equals(effect.type())
        && target.equals(effect.target())
        && value.equals(effect.value());
  }

  private static HeroEdit vinceWithStrengthStart(HeroSnapshot vince) {
    return HeroEdit.builder()
        .heroId(vince.id())
        .strength(curve(vince.strength()).withStart(15))
        .spirit(curve(vince.spirit()))
        .vitality(curve(vince.vitality()))
        .speed(curve(vince.speed()))
        .levelCap(vince.levelCap())
        .baseCritChance(vince.baseCritChance())
        .baseCritDamage(vince.baseCritDamage())
        .build();
  }

  private static StatCurveEdit curve(StatCurveSnapshot snapshot) {
    return StatCurveEdit.builder()
        .start(snapshot.start())
        .target(snapshot.target())
        .curve(snapshot.curve())
        .build();
  }

  private static Path originalJar() throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(EditorPatchServiceTest.class.getResource("/vddoh.jar")).toURI());
  }

  private static int indexOf(byte[] data) {
    for (int i = 0;
        i <= data.length - EditorPatchServiceTest.RESISTANCE_ORIGINAL_PREFIX.length;
        i++) {
      boolean matches = true;
      for (int j = 0; j < EditorPatchServiceTest.RESISTANCE_ORIGINAL_PREFIX.length; j++) {
        if (data[i + j] != EditorPatchServiceTest.RESISTANCE_ORIGINAL_PREFIX[j]) {
          matches = false;
          break;
        }
      }
      if (matches) {
        return i;
      }
    }
    throw new AssertionError("Pattern not found");
  }

  private void cleanupCreatedFiles() throws IOException {
    if (Files.notExists(tempDir)) {
      return;
    }
    try (var paths = Files.walk(tempDir)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (!path.equals(tempDir)) {
          Files.deleteIfExists(path);
        }
      }
    }
  }
}
