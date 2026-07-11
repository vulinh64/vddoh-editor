# VDDOH Editor Progress

This project contains the current Swing data editor and reverse-engineering notes for `Vampires Dawn: Deceit of Heretics` (`vddoh.jar`).

## Current Project State

- [x] Moved the editor into a Maven-style project: `vddoh-editor/`.
- [x] Main source moved to a Maven-style Java package under `src/main/java/com/vddoh/editor/`.
- [x] Kept a minimal desktop-safe Java ME `Font` shim because the bundled MIDP API returns `null` from font factory methods and breaks original game class initialization.
- [x] Added minimal `me-lib/` Java ME API jars for reflection-loading original game classes: `cldc11.jar`, `midp21.jar`, and `jsr135.jar`.
- [x] Removed runtime dependence on external installs. The built editor JAR now embeds the minimal Java ME API classes and can also use repository-local `me-lib/*.jar` during development.
- [x] Maven packaging now creates a self-contained executable JAR with application dependencies and minimal Java ME API classes bundled.
- [x] Added Maven wrapper files: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`.
- [x] Added `pom.xml` targeting JDK 25.
- [x] Added `run-vddoh-editor.cmd` to run `target/vddoh-data-editor-1.0.0.jar`.
- [x] Reduced Windows launchers to two explicit build-and-run scripts: `build-and-run-vddoh-editor-swing.cmd` for the legacy Swing shaded JAR and `build-and-run-vddoh-editor-fx.cmd` for the JavaFX Maven launcher.
- [x] Added an IntelliJ Maven run configuration `VDDOH JavaFX (Maven)` because plain IntelliJ Application runs can miss the OpenJFX module/runtime path and fail with "JavaFX runtime components are missing".
- [x] Added JavaFX resistance-overflow patch control. FX now mirrors Swing's ORIGINAL/PATCHED/UNKNOWN state behavior and can build the `g.class` patch through `EditorPatchService`.
- [x] Updated `build-with-jdk.cmd` to delegate to the Maven wrapper so dependency resolution, annotation processing, resources, Java ME API unpacking, and shading stay in one build path.
- [x] Moved and updated `VDDOH-STATS-MECHANISM.md`.
- [x] Confirmed `build-with-jdk.cmd` builds the target JAR.
- [x] Confirmed `mvnw.cmd -q -DskipTests package` builds the target JAR on JDK 25.

## Editor Features Implemented

- [x] Choose input JAR at startup instead of assuming a fixed path.
- [x] Extract `game.dat` and `item.dat` to `%USERPROFILE%/.vddoh-editor/temp/<jar-name>/`.
- [x] Default patched output goes to `%USERPROFILE%/.vddoh-editor/dist/`.
- [x] Editable Skills table with costs, damage values, and status chances where safe.
- [x] Editable Talents table for group talents, passive hero talents, and spell unlock links.
- [x] Editable Heroes table for natural stat growth, level cap, base crit chance, and base crit damage.
- [x] Read-only Hero previews for base derived stats and core stats at level cap.
- [x] Editable Items table for safe top-level fields and decoded effect previews.
- [x] Fixed Items/Skills search so the lower effect-detail table refreshes even when filtering keeps view row `0` selected.
- [x] Improved Item effect previews: category-5 consumables now show packed stat boosts, status-use arrays, and use effect IDs as consumable effects; category-9/10 skill items now show their linked skill from `byte_o/byte_p`.
- [x] Split the Items tab into filtered Equipment, Consumables, and Other views. Equipment can be further filtered by Weapon, Head Armor, Necklace, Ring, Main Armor, Boot, and Rune/Modifier. Consumables now include both anytime consumables and combat-only skill-backed consumables.
- [x] Added double-click navigation from an item `Linked skill` effect row to the Skills tab, using the selected item's full name as the skill search text.
- [x] Started JavaFX migration Phase 1 on a parallel launcher. Swing remains the default executable JAR; JavaFX currently provides a read-only Items browser with Equipment, Consumables, Other, equipment slot filtering, consumable mode filtering, decoded effect details, and pending linked-skill navigation status.
- [x] Updated item grouping to Equipment, Runes, Consumable, Battle-only Consumable, and Special. Equipment now means only Head, Neck, Ring, Main Body Armor, Main Weapon, and Boot. Category 6 permanent-use items such as Ankh of Life/Magic are Consumable, not Special.
- [x] Started JavaFX migration Phase 2 with conservative item editing: JavaFX can edit item Price, Icon, HP Restore/Effect, and Resource Restore/Effect, then build an item-only patched JAR through the shared `EditorPatchService` and existing `ItemDatPatcher`.
- [x] Added a read-only JavaFX Skills tab backed by immutable skill snapshots. Linked-skill item actions now switch to Skills and set the search text to the item full name, selecting the linked skill level when the filtered row is visible.
- [x] Completed the first full JavaFX browsing surface: Skills, Talents, Heroes, Items, Monsters, and Statuses now load from immutable snapshot records. Swing remains available while JavaFX write parity is expanded through the shared patch service.
- [x] The Maven shaded JAR still uses Swing as its manifest main class because JavaFX native/runtime-image packaging is a separate distribution step; use the explicit build-and-run scripts for UI selection.
- [x] Added JavaFX edit/build parity for confirmed safe fields: skill costs/effect values, talent scalar/link fields, hero growth/level/crit fields, item safe top-level fields, monster reward/core-stat fields, and status duration/chance/icon fields. JavaFX edit carriers use records with `@Builder`/`@With`; UI view-models are records with JavaFX properties only at the table edge.
- [x] Editable Monsters table v1: names read-only; confirmed EXP, Filar, Death Value, Effect ID, and STR/SPI/VIT/SPD-like core stat bytes editable. HP/combat previews recalculate from the core bytes; drops/actions/effects remain read-only.
- [x] Editable Statuses table for safe status fields.
- [x] Search support across Skills, Heroes, Items, Monsters, and Statuses.
- [x] Build patched JAR by replacing modified entries.
- [x] Added an output JAR `View` button that opens the patched-output location; Windows selects the file in Explorer when it exists.
- [x] Optional `Patch resistance overflow` checkbox for the confirmed resistance byte overflow bug.
- [x] Split the former monolithic `VddohDataEditor.java` into package-scoped classes for UI, table models, data rows, patch requests, patchers, offsets, and shared support helpers.
- [x] Ran a pre-Sonar static cleanup pass: removed stale generated imports, replaced wildcard static imports with specific imports, replaced `printStackTrace()` with logging, and removed the hard-coded local runtime path.
- [x] Added Lombok and Apache Commons Lang3, then started the JDK 25 refactor style pass with records, `@Builder`, and `@With` where the data shape is a good fit.
- [x] Added SLF4J 2.0.18 and Logback 1.5.37 for editor diagnostics. Default logging is `INFO` so load/build/patch breadcrumbs are visible; run with `-Dvddoh.log.level=WARN` to quiet it down.
- [x] Converted patch DTOs and several offset/read-model carriers to Lombok-backed records. Record construction should prefer builders or builder-backed factories over positional canonical constructor calls.
- [x] Converted `MonsterRow` to the first immutable editable-row prototype: Swing edits replace the row with `with...` copies instead of mutating fields directly.

## Latest Bytecode Patch State

The resistance overflow patch is implemented as a hybrid `g.class` patcher.
It uses JDK 25's Class-File API to semantically confirm the known vanilla
`g.b()V` resistance clamp shape, then applies the already-confirmed raw
byte-pattern replacement so the output changes only the intended bytes.

- [x] On JAR load, editor detects whether `g.class` is vanilla, already patched, or unknown.
- [x] Already patched JAR: checkbox checked and disabled.
- [x] Vanilla known JAR: checkbox enabled and unchecked by default.
- [x] Unknown layout: checkbox disabled.
- [x] Build patched JAR incorporates `g.class` only when checkbox is checked and patch is applicable.
- [x] Vanilla detection now requires both the exact byte pattern and one semantic Class-File API match in `g.b()V`.

Future improvement: replace the raw write step with a full Class-File API transform
if more class patches are added. A prototype transform preserved `g.class` length
and version `45.3`, but the hybrid approach is simpler and keeps this patch byte-minimal.

Bytecode tooling direction:

- Prefer JDK 25 Class-File API for future structural patches.
- Keep ASM as a possible fallback if instruction-window transforms become too awkward with the standard API.
- Avoid Byte Buddy for the current JAR patching use case; it is better suited to runtime generation/instrumentation than conservative offline edits of old J2ME classes.
- BCEL is viable for class-file manipulation, but there is no current reason to prefer it over Class-File API or ASM.

## What We Are Leaving For Next Session

- [ ] Run a quick in-game test of `Patch resistance overflow` with Romus/Manok plus extra Bleed resistance.
- [ ] Confirm patched `g.class` detection by loading an already-patched output JAR back into the editor.
- [ ] Add a dedicated `Class Patches` tab if more bytecode patches are added.
- [x] Consider replacing raw byte-pattern class patching with JDK 25 Class-File API; decided on hybrid semantic detector plus byte-minimal raw writer for now.
- [ ] Continue in-game testing monster STR/SPI/VIT/SPD-like edits for resource/attack/defense/move preview matches. HP is confirmed with Ryan (1): setting all four core stats to `1` produced `12 HP`.
- [ ] Expand Monster editing beyond v1: decode/write resistances, drops, and AI/skills once the variable arrays are mapped safely.
- [ ] Expand Item editing for multi-effect rows rather than only top-level safe fields.
- [ ] Add explicit patch validation reports before writing output JAR.
- [ ] Add unit tests or small command-line verifier for patchers.
- [ ] Update docs when new field mappings become confirmed.
- [ ] Continue the record/Lombok refactor carefully: immutable editable rows should use `@With` and table-model row replacement; mutable parser accumulators may stay mutable until a clean builder/with flow is obvious.

## Current Refactor Rules

- Use JDK 25 language features where they simplify the code, while keeping the editor easy to audit.
- Prefer records with Lombok `@Builder` and `@With` for immutable data carriers.
- For record initialization, prefer builders or builder-backed factories over calling canonical constructors directly.
- For record components that are collections (`List`, `Set`, etc.), normalize null inputs to the matching empty collection in the compact constructor unless null has a documented meaning.
- Do not force table row classes into records unless the table model is updated to replace row instances on edit. `MonsterRow` is the current working pattern.

## Source Layout

The editor now lives in package `com.vddoh.editor`.

Key entry points:

```text
src/main/java/com/vddoh/editor/VddohDataEditor.java              Main class only
src/main/java/com/vddoh/editor/EditorFrame.java                  Swing UI coordinator
src/main/java/com/vddoh/editor/EditorSupport.java                Shared parsing, JAR, reflection, decode, and validation helpers
src/main/java/com/vddoh/editor/*TableModel.java                  Swing table models
src/main/java/com/vddoh/editor/*Row.java                         Editable/read-only row data
src/main/java/com/vddoh/editor/*Patch.java                       Patch request DTOs
src/main/java/com/vddoh/editor/*Patcher.java                     game.dat/item.dat/class patchers
src/main/java/com/vddoh/editor/*Offsets.java                     Packed-data offset holders
me-lib/*.jar                                                     Minimal Java ME/API jars unpacked into the built editor JAR
```

## Build And Run

Preferred build:

```cmd
cd D:\Games\JAR\JAR\vddoh-editor
mvnw.cmd -q -DskipTests package
```

Compatibility build wrapper:

```cmd
cd D:\Games\JAR\JAR\vddoh-editor
build-with-jdk.cmd
```

Run:

```cmd
cd D:\Games\JAR\JAR\vddoh-editor
run-vddoh-editor.cmd
```
