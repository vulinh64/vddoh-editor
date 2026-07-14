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
    HeroSnapshot vince = hero(workspace, VINCE);

    assertEquals(138, workspace.skillLevels().size());
    assertEquals(38, workspace.talents().size());
    assertEquals(4, workspace.heroes().size());
    assertEquals(234, workspace.items().size());
    assertEquals(65, workspace.monsters().size());
    assertEquals(42, workspace.statuses().size());
    assertEquals("ORIGINAL", workspace.resistanceOverflowState());
    assertEquals("ORIGINAL", workspace.equipmentBonusState());
    assertEquals(
        ResistanceOverflowClassPatcher.State.ORIGINAL,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.ORIGINAL, EquipmentBonusClassPatcher.state(heroClass));
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
    assertTrue(result.summary().contains("resistance overflow"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals("PATCHED", reloaded.resistanceOverflowState());
    assertEquals("ORIGINAL", reloaded.equipmentBonusState());
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
    assertTrue(result.summary().contains("equipment bonus"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals("ORIGINAL", reloaded.resistanceOverflowState());
    assertEquals("PATCHED", reloaded.equipmentBonusState());
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
                .build());

    byte[] heroClass = readJarEntry(result.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.PATCHED,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.PATCHED, EquipmentBonusClassPatcher.state(heroClass));
    assertTrue(result.summary().contains("resistance overflow"));
    assertTrue(result.summary().contains("equipment bonus"));
    EditorWorkspace reloaded = EditorLoadService.load(result.outputJar());
    assertEquals("PATCHED", reloaded.resistanceOverflowState());
    assertEquals("PATCHED", reloaded.equipmentBonusState());
  }

  @Test
  void classPatchesAreIdempotentOnAlreadyPatchedJar() throws Exception {
    BuildResult first =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace("idempotent-first.jar"))
                .resistanceOverflowPatchRequested(true)
                .equipmentBonusPatchRequested(true)
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
                .build());

    byte[] heroClass = readJarEntry(second.outputJar(), EditorPatchService.HERO_CLASS_ENTRY);
    assertEquals(
        ResistanceOverflowClassPatcher.State.PATCHED,
        ResistanceOverflowClassPatcher.state(heroClass));
    assertEquals(
        EquipmentBonusClassPatcher.State.PATCHED, EquipmentBonusClassPatcher.state(heroClass));
    assertTrue(second.summary().contains("skipped=1"));
  }

  @Test
  void refusesUnknownResistancePatchLayoutWithoutWritingOutputJar() throws Exception {
    Path corruptedJar = tempDir.resolve("corrupted-layout.jar");
    byte[] heroClass = readJarEntry(originalJar(), EditorPatchService.HERO_CLASS_ENTRY);
    heroClass[indexOf(heroClass, RESISTANCE_ORIGINAL_PREFIX) + RESISTANCE_ORIGINAL_PREFIX.length] =
        0x00;
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

    assertEffect(
        item(workspace, "Sickle blade"), "Equipment", "Packed Stat", "Strength/Power", "1");
    assertEffect(
        item(workspace, "Sickle blade"),
        "Equipment/Weapon",
        "Flat stat/damage",
        "Strength/Power",
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
  void changingVinceStrengthStartTo15UpdatesExpectedHpPreview() throws Exception {
    EditorWorkspace workspace = workspace("vince-str-15.jar");
    HeroSnapshot vince = hero(workspace, VINCE);

    BuildResult result =
        EditorPatchService.buildFullPatch(
            PatchBuildRequest.builder()
                .workspace(workspace)
                .heroEdits(List.of(vinceWithStrengthStart(vince)))
                .build());

    EditorWorkspace patched = EditorLoadService.load(result.outputJar());
    HeroSnapshot patchedVince = hero(patched, VINCE);
    int expectedHp = (patchedVince.vitality().start() * 70 + 15 * 30) * 12 / 100;
    assertEquals(15, patchedVince.strength().start());
    assertEquals(79, expectedHp);
    assertEquals(expectedHp, patchedVince.baseHp());
  }

  @Test
  void heroStatCurveShapeRoundTripsThroughGameDatPatch() throws Exception {
    EditorWorkspace workspace = workspace("vince-str-curve.jar");
    HeroSnapshot vince = hero(workspace, VINCE);

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
    assertEquals(37, hero(patched, VINCE).strength().curve());
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

  private static HeroSnapshot hero(EditorWorkspace workspace, String name) {
    return workspace.heroes().stream()
        .filter(hero -> name.equals(hero.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing hero " + name));
  }

  private static ItemSnapshot item(EditorWorkspace workspace, String name) {
    return workspace.items().stream()
        .filter(item -> name.equals(item.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing item " + name));
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

  private static int indexOf(byte[] data, byte[] pattern) {
    for (int i = 0; i <= data.length - pattern.length; i++) {
      boolean matches = true;
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) {
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
