# JavaFX Migration Implementation Plan

## Purpose

This document is the coding plan for introducing JavaFX incrementally into the
VDDOH Data Editor. It is written for Agent 4 to start implementation without
guessing at boundaries.

The migration must preserve the current Swing editor and conservative patching
pipeline while adding a parallel JavaFX application path. The first JavaFX slice
should prove the application shell and Items workflow before any Swing removal.

## Current Architecture Summary

### Entry Point And UI

- `src/main/java/com/vddoh/editor/VddohDataEditor.java`
  - Current main entry point.
  - Starts Swing with `SwingUtilities.invokeLater(...)`.
  - `pom.xml` property `main.class` points here, so the shaded executable JAR
    currently launches Swing by default.

- `src/main/java/com/vddoh/editor/EditorFrame.java`
  - Swing coordinator and main application frame.
  - Owns file path controls, load/build/reset/view-output actions, tab creation,
    search/filter wiring, and resistance overflow checkbox state.
  - Current tabs: Skills, Talents, Heroes, Items, Monsters, Statuses.
  - Current Items tab already splits Equipment, Consumables, and Other with
    Equipment slot filters and linked-skill double-click behavior.

### Swing Table Models

Current table models are Swing-specific and should not be reused by JavaFX:

- `SkillLevelTableModel`
- `SkillEffectTableModel`
- `TalentTableModel`
- `HeroTableModel`
- `ItemTableModel`
- `ItemEffectTableModel`
- `MonsterTableModel`
- `StatusTableModel`
- `SimpleNamedTableModel`

These classes can remain for Swing. JavaFX should use separate view-models over
the same row data and patch request pipeline.

### Data Loading

- `GameData`
  - Loads original game classes through reflection.
  - Produces framework-neutral row lists:
    - `skillLevels`
    - `talents`
    - `heroes`
    - `items`
    - `monsters`
    - `statuses`
  - Decodes item effects, linked skills, monster previews, and other known
    fields.

- `EditorSupport`
  - Shared helper class for JAR/file IO, Java ME classpath setup, reflection
    helpers, decoding, validation, and UI-neutral support methods.
  - Some methods are still Swing-facing, such as `showError(...)`; JavaFX should
    not deepen that coupling.

### Load Flow Boundary

The current complete load flow is coordinated by `EditorFrame`, not by one
fully reusable loader service. `EditorFrame` owns the selected input JAR path,
extracts `game.dat` and `item.dat` into the editor temp directory, tracks the
original JAR entry names, calls `GameData.loadFromOriginalClasses(...)`, updates
all table models, and applies resistance overflow patch-state detection to the
checkbox.

JavaFX must load the same way Swing loads:

1. User selects an input JAR.
2. The editor extracts `game.dat` and `item.dat` from that JAR into the same
   temp layout used by Swing.
3. The editor calls `GameData.loadFromOriginalClasses(inputJar)`.
4. If JavaFX shows resistance overflow state, it must use the same
   `ResistanceOverflowClassPatcher` detection behavior as Swing.

Agent 4 may extract a small UI-neutral loader only if it preserves Swing
behavior exactly. A good extraction would return a small workspace/result object
containing the input JAR, extracted data paths, original entry names, loaded
`GameData`, and resistance patch state. If this extraction starts touching large
parts of `EditorFrame`, stop and duplicate the minimal JavaFX load path for
Phase 1 instead.

### Rows, DTOs, Offsets, And Patch Requests

Framework-neutral data and patch classes live under `com.vddoh.editor`:

- Rows: `SkillLevelRow`, `SkillEffectRow`, `TalentRow`, `HeroRow`, `ItemRow`,
  `ItemEffectRow`, `MonsterRow`, `StatusRow`, `NamedRow`
- Patch DTOs: `SkillPatch`, `TalentPatch`, `HeroPatch`, `ItemPatch`,
  `MonsterPatch`, `StatusPatch`
- Offsets/carriers: `LevelOffsets`, `TalentOffsets`, `HeroOffsets`,
  `ItemOffsets`, `MonsterOffsets`, `StatusOffsets`, `StatCurve`,
  `TalentSection`, `TalentSections`
- `PatchSummary` and helper summaries

The project direction prefers JDK 25 syntax and Lombok-backed records with
`@Builder` and `@With` where useful. JavaFX property objects should stay in
JavaFX-only view-model classes and must not replace these framework-neutral
records/DTOs.

### Patchers

Patchers are conservative and should remain the only write path:

- `GameDatSkillPatcher`
- `GameDatTalentPatcher`
- `GameDatHeroPatcher`
- `ItemDatPatcher`
- `GameDatMonsterPatcher`
- `GameDatStatusPatcher`
- `ResistanceOverflowClassPatcher`

JavaFX must generate the same patch DTOs as Swing and feed them to the same
patchers. Do not add UI-side byte writing.

### Packaging

Current Maven packaging:

- `pom.xml` targets JDK 25 through `maven.compiler.release=25`.
- Maven Shade builds `target/vddoh-data-editor-1.0.0.jar`.
- The manifest points at `${main.class}`, currently `com.vddoh.editor.VddohDataEditor`.
- Maven Antrun unpacks minimal Java ME API jars from `me-lib` into the output.
- `run-vddoh-editor.cmd` runs the shaded JAR with `java -jar`.
- `build-with-jdk.cmd` delegates to the Maven wrapper.

The current executable JAR workflow must keep launching Swing until JavaFX
packaging is proven.

## Dependency And Build Plan

### OpenJFX Dependencies

JavaFX is not bundled with JDK 25. Add OpenJFX dependencies deliberately.

Verify the intended `org.openjfx` version exists in Maven Central before locking
it into `pom.xml`. The exact version below is a candidate, not a substitute for
checking availability during implementation.

```xml
<javafx.version>25.0.1</javafx.version>
```

Prefer the smallest dependency set first:

```xml
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-controls</artifactId>
  <version>${javafx.version}</version>
</dependency>
```

`javafx-controls` should pull the common UI stack needed for Phase 1. Add
explicit `javafx-graphics` only if the build or launcher proves it is needed.

Do not add `javafx-web` in Phase 1. The proposal explicitly means
webapp-like desktop UX, not embedded browser/HTML UI.

Do not change the Shade manifest away from the Swing main class. If plain
OpenJFX dependencies disturb the Swing packaged JAR, move JavaFX dependencies
and plugin configuration behind a Maven profile before continuing.

### Maven Plugins

Short-term development path:

- Add `org.openjfx:javafx-maven-plugin`.
- Configure a JavaFX main class such as
  `com.vddoh.editor.fx.VddohDataEditorFx`.
- Keep Shade manifest pointing at Swing `VddohDataEditor`.

Example target:

```cmd
mvnw.cmd -q javafx:run
```

or:

```cmd
mvnw.cmd -q -Djavafx.mainClass=com.vddoh.editor.fx.VddohDataEditorFx javafx:run
```

If plugin configuration becomes awkward on JDK 25, add a simple script first:

```text
run-vddoh-editor-fx.cmd
```

That script can call Maven's JavaFX plugin during development. Do not replace
`run-vddoh-editor.cmd`.

`run-vddoh-editor-fx.cmd` must not call:

```cmd
java -jar target\vddoh-data-editor-1.0.0.jar
```

That JAR remains the Swing launcher. The JavaFX script should use the Maven
JavaFX plugin or a clearly module-path-aware `java` command that launches
`com.vddoh.editor.fx.VddohDataEditorFx`.

### Packaging Caveats

OpenJFX artifacts include native platform pieces. A plain shaded JAR is not a
safe long-term distribution answer for JavaFX.

Phase 1 packaging rule:

- Preserve the current Swing shaded executable JAR.
- Add a development-only JavaFX run path.
- Do not claim JavaFX is self-contained until verified on a clean machine.

Medium-term packaging:

- Add a Maven profile for JavaFX runtime images.
- Prefer `jlink` or `jpackage` for true self-contained JavaFX distribution.
- Keep the Java ME API unpack behavior from the current Maven build.

## Proposed New Files

Use a separate JavaFX package tree. Keep current `com.vddoh.editor` classes in
place.

### Application Shell

```text
src/main/java/com/vddoh/editor/fx/VddohDataEditorFx.java
src/main/java/com/vddoh/editor/fx/FxEditorApplication.java
src/main/java/com/vddoh/editor/fx/FxEditorState.java
src/main/java/com/vddoh/editor/fx/FxNavigation.java
```

Suggested responsibilities:

- `VddohDataEditorFx`
  - JavaFX main class wrapper.
  - Calls `Application.launch(FxEditorApplication.class, args)`.

- `FxEditorApplication`
  - Extends `javafx.application.Application`.
  - Creates primary `Stage`, root layout, scene, stylesheet, and initial state.

- `FxEditorState`
  - Holds selected input JAR, extracted `game.dat`/`item.dat` paths, output JAR,
    loaded `GameData`, resistance patch state, dirty state, and status messages.
  - JavaFX properties live here or in smaller view-models, not in core row DTOs.
  - Keep this class minimal. Do not let it become a junk drawer for section
    state. Items filters, selected item, item observable rows, and item detail
    state belong in Items-specific view-model classes.

- `FxNavigation`
  - Owns current section, search context, and relationship jumps.
  - Phase 1 should support `ITEMS` and a pending `SKILLS` navigation request.

### Shared JavaFX UI Components

```text
src/main/java/com/vddoh/editor/fx/ui/FxCommandBar.java
src/main/java/com/vddoh/editor/fx/ui/FxStatusBar.java
src/main/java/com/vddoh/editor/fx/ui/FxSectionHost.java
src/main/java/com/vddoh/editor/fx/ui/FxDialogs.java
src/main/java/com/vddoh/editor/fx/ui/FxValueEditors.java
```

Suggested responsibilities:

- Command bar: input JAR chooser, load, output path, build, view output.
- Status bar: current load/build messages and dirty-state counts.
- Section host: navigation rail/tab strip and main content switching.
- Dialogs: JavaFX error dialogs and confirmation dialogs.
- Value editors: reusable `Spinner`, `TextField`, and validation helpers.

### Items Slice

```text
src/main/java/com/vddoh/editor/fx/items/FxItemsView.java
src/main/java/com/vddoh/editor/fx/items/FxItemViewModel.java
src/main/java/com/vddoh/editor/fx/items/FxItemEffectViewModel.java
src/main/java/com/vddoh/editor/fx/items/FxItemFilters.java
src/main/java/com/vddoh/editor/fx/items/FxItemDetailPane.java
```

Suggested responsibilities:

- `FxItemsView`
  - Owns master table/list, search, category filters, slot/mode filters, and
    selection.

- `FxItemViewModel`
  - Wraps one `ItemRow` for JavaFX display.
  - Exposes JavaFX properties only for UI state.
  - Phase 1 is read-only; do not generate `ItemPatch` here yet.

- `FxItemEffectViewModel`
  - Wraps `ItemEffectRow`.
  - Identifies linked-skill rows and exposes a `jumpToSkill` action.

- `FxItemFilters`
  - Encodes Equipment, Consumables, Other, equipment slots, and consumable modes.

- `FxItemDetailPane`
  - Uses `TitledPane` or `Accordion` groups:
    - Basic
    - Equipment
    - Consumable
    - Linked Skill
    - Raw Diagnostics

### Build/Patch Service

Phase 1 should not implement JavaFX build or item patch generation unless the
user explicitly approves expanding scope. Keep Items read-only first.

For Phase 2, if a shared build service is extracted, use names such as:

```text
src/main/java/com/vddoh/editor/EditorWorkspace.java
src/main/java/com/vddoh/editor/PatchedJarBuilder.java
```

These would be framework-neutral and shared by Swing/JavaFX, but only extract
them if doing so reduces duplication and preserves Swing behavior. Avoid a large
`EditorFrame` rewrite.

### Resources

```text
src/main/resources/com/vddoh/editor/fx/editor.css
src/main/resources/com/vddoh/editor/fx/icons/
```

Use CSS for spacing, badges, validation states, and compact tool styling.
Do not add decorative assets in Phase 1.

### Scripts

```text
run-vddoh-editor-fx.cmd
```

Short-term script should run the JavaFX development launcher. Keep
`run-vddoh-editor.cmd` as the Swing launcher.

## Phase 1 Implementation Scope

Phase 1 is a narrow proof slice.

Must include:

- JavaFX app starts beside Swing.
- Swing remains the default `java -jar target/vddoh-data-editor-1.0.0.jar`
  launcher.
- JavaFX command bar can select an input JAR and load data through `GameData`.
- JavaFX Items view displays loaded item rows.
- Items can be filtered as:
  - Equipment
  - Consumables
  - Other
- Equipment supports slot filters:
  - Weapon
  - Head Armor
  - Necklace
  - Ring
  - Main Armor
  - Boot
  - Rune/Modifier
- Consumables support mode filters:
  - Anytime/direct, category `5`
  - Combat-only skill-backed, category `9`
- Item detail pane shows:
  - Safe fields and max ranges where already known, displayed read-only.
  - Decoded effects.
  - Linked skill row/action for category `9` and `10` skill-backed items.
  - Raw diagnostics such as field labels already present in `ItemEffectRow`.
- Linked skill action:
  - Payload must include source item ID, source item full name as search text,
    linked skill ID, and skill level/variant.
  - If Skills JavaFX view exists, navigate there and set search to the source
    item full name.
  - If Skills view does not exist in Phase 1, store pending navigation in
    `FxNavigation` and show a status message such as
    `Skills view pending: search "Troll Elixir" for linked skill 52 level 1`.

Nice to include if low-risk:

- Output JAR path field.
- Resistance overflow state display as read-only.

Should not include:

- Item editing or patch generation unless the user explicitly approves expanding
  Phase 1.
- `FxItemPatchAdapter`; move it to Phase 2.
- Build Patched JAR from JavaFX.
- Swing removal.
- JavaFX replacement for every tab.
- New unsafe editable fields.
- JavaFX `WebView`.
- Large data-layer rewrites.

## Data/UI Boundary Rules

1. Do not reuse Swing `TableModel` classes in JavaFX.
2. JavaFX view-models may wrap existing row records/classes.
3. JavaFX `Property` and `ObservableList` types must stay in
   `com.vddoh.editor.fx...`.
4. Core row, offset, and patch DTO classes must remain framework-neutral.
5. Patch generation must flow through existing patch DTOs and patcher classes.
6. If a shared service is extracted from `EditorFrame`, keep it UI-neutral and
   small.
7. Do not decode original game classes in UI components. Use `GameData`.

## Acceptance Criteria

Phase 1 is accepted when all of the following are true:

- `mvnw.cmd -q -DskipTests package` succeeds.
- `run-vddoh-editor.cmd` still starts the Swing editor.
- JavaFX can be started through the new development launcher or Maven plugin.
- JavaFX can load `jar/vddoh.jar` or a user-selected vanilla JAR.
- JavaFX Items view shows Equipment, Consumables, and Other counts.
- Equipment slot filtering works for the known slots.
- Consumable mode filtering separates category `5` and category `9`.
- Selecting Troll Elixir or Might Potion shows it as a combat-only consumable.
- A linked-skill action exists and carries source item ID, source item full name,
  linked skill ID, and skill level/variant.
- Decoded item effect rows are visible in the detail pane.
- Items safe fields and max ranges are displayed read-only.
- `java -jar target\vddoh-data-editor-1.0.0.jar` still starts Swing after adding
  JavaFX dependencies.
- Swing Items behavior is not regressed.
- The resistance overflow checkbox/state in Swing is unchanged.

## Verification Commands

Build:

```cmd
mvnw.cmd -q -DskipTests package
```

Run Swing:

```cmd
run-vddoh-editor.cmd
```

Direct shaded-JAR non-regression check:

```cmd
java -jar target\vddoh-data-editor-1.0.0.jar
```

Run JavaFX:

```cmd
run-vddoh-editor-fx.cmd
```

or, if Agent 4 configures the JavaFX Maven plugin:

```cmd
mvnw.cmd -q javafx:run
```

Manual verification:

```text
1. Load jar/vddoh.jar.
2. Open Items.
3. Check Equipment, Consumables, Other filters.
4. Check Equipment slot filters.
5. Check Consumable mode filters.
6. Select Troll Elixir.
7. Confirm it appears as combat-only skill-backed.
8. Trigger linked-skill action.
9. Confirm navigation payload includes item ID, item full name, linked skill ID,
   and skill level/variant.
10. Run Swing editor and confirm current behavior still works.
11. Run the shaded JAR directly and confirm it still starts Swing.
```

## Risks

- JavaFX native dependencies may complicate executable packaging.
- Shading JavaFX dependencies may not produce a portable JavaFX app.
- Introducing JavaFX properties into core data classes would pollute the clean
  patching model.
- Reusing Swing `TableModel` classes would make the migration brittle.
- Extracting build/load logic from `EditorFrame` could accidentally alter Swing
  behavior.
- JavaFX editable table semantics differ from Swing and need careful validation
  before enabling writes.
- Phase creep could turn the slice into a full rewrite.

## Rollback Strategy

- Keep Swing as the manifest main class.
- Keep `run-vddoh-editor.cmd` unchanged unless a bug requires a targeted fix.
- Add JavaFX code in new packages so it can be removed without touching patchers.
- If JavaFX dependency or launcher work breaks packaging, revert only the JavaFX
  dependency/profile/script changes and leave Swing code intact.
- If a shared service extraction regresses Swing, stop and keep duplicated
  JavaFX load/build code until a safer extraction can be planned.

## Recommended Task Order For Agent 4

Use small commits or at least small working steps.

1. Add JavaFX dependencies and a development run path.
   - Verify the chosen OpenJFX version exists before editing `pom.xml`.
   - Add the smallest OpenJFX dependency set first, likely `javafx-controls`.
   - Add JavaFX Maven plugin or `run-vddoh-editor-fx.cmd`.
   - Keep Swing as the shaded JAR main class.
   - Build with `mvnw.cmd -q -DskipTests package`.
   - Run `java -jar target\vddoh-data-editor-1.0.0.jar` and confirm Swing still
     starts.

2. Add JavaFX shell.
   - Create `VddohDataEditorFx`, `FxEditorApplication`, `FxEditorState`,
     `FxNavigation`.
   - Show an empty shell with command bar, nav area, workspace, status bar.
   - Add `editor.css`.
   - Verify JavaFX starts.

3. Wire load flow.
   - Add input JAR chooser.
   - Match Swing's selected-JAR temp extraction and `GameData.loadFromOriginalClasses(...)`
     flow.
   - Show resistance patch state only if using the same detector as Swing.
   - Store loaded data in `FxEditorState`.
   - Show counts in status bar.
   - Keep errors in JavaFX dialogs.

4. Build Items read-only master view.
   - Create `FxItemsView`, filters, and item view-model wrappers.
   - Display Equipment, Consumables, Other.
   - Add slot and mode filters.
   - Verify Troll Elixir and Might Potion appear under combat-only consumables.

5. Add Items detail pane.
   - Create collapsible Basic, Equipment, Consumable, Linked Skill, Raw
     Diagnostics sections.
   - Show decoded `ItemEffectRow` values.
   - Show safe known ranges in labels/tooltips, read-only in Phase 1.

6. Add linked-skill action.
   - For linked skill effects, provide a button/hyperlink action.
   - Store a Skills navigation request with source item ID, source item full
     name as search text, linked skill ID, and skill level/variant.
   - If no Skills view exists, show the pending action in the status bar.

7. Keep Phase 1 read-only.
   - Do not add `FxItemPatchAdapter`.
   - Do not enable JavaFX item editing or Build Patched JAR unless explicitly
     approved by the user.
   - Document item editing/build as Phase 2.

8. Regression check Swing.
   - Run build.
   - Run Swing.
   - Load vanilla JAR.
   - Confirm Items and resistance overflow state still behave.

9. Update docs.
   - Update `PROGRESS.md` with JavaFX Phase 1 status.
   - If implementation discovers new data behavior, update
     `VDDOH-STATS-MECHANISM.md` or `docs/REVERSE_ENGINEERING_INDEX.md`.

## Suggested Commit Boundaries

If committing, keep each change easy to review:

1. `Add JavaFX dependencies and launcher`
2. `Add JavaFX application shell`
3. `Add JavaFX load flow`
4. `Add JavaFX items filters and master view`
5. `Add JavaFX item detail panes and linked skill action`
6. `Document JavaFX phase status`

Do not combine dependency, shell, item UI, and docs into one large commit unless
the user explicitly asks for a single commit.
