# VDDOH Editor Progress

This project contains the current Swing data editor and reverse-engineering notes for `Vampires Dawn: Deceit of Heretics` (`vddoh.jar`).

## Current Project State

- [x] Moved the editor into a Maven-style project: `vddoh-editor/`.
- [x] Main source moved to a Maven-style Java package under `src/main/java/com/vddoh/editor/`.
- [x] Removed the temporary Java ME `Font` source stub; real Java ME API classes now come from `me-lib`.
- [x] Added minimal `me-lib/` Java ME API jars for reflection-loading original game classes: `cldc11.jar`, `midp21.jar`, and `jsr135.jar`.
- [x] Removed runtime dependence on external installs. The built editor JAR now embeds the minimal Java ME API classes and can also use repository-local `me-lib/*.jar` during development.
- [x] Maven packaging now creates a self-contained executable JAR with application dependencies and minimal Java ME API classes bundled.
- [x] Added Maven wrapper files: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`.
- [x] Added `pom.xml` targeting JDK 25.
- [x] Added `run-vddoh-editor.cmd` to run `target/vddoh-data-editor-1.0.0.jar`.
- [x] Added `build-with-jdk.cmd` as a no-Maven fallback build.
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
- [x] Editable Monsters table v1: names read-only, confirmed EXP and Filar rewards editable, Death Value and Effect ID editable, HP/combat/drop-related fields visible as read-only reflected previews.
- [x] Editable Statuses table for safe status fields.
- [x] Search support across Skills, Heroes, Items, Monsters, and Statuses.
- [x] Build patched JAR by replacing modified entries.
- [x] Optional `Patch resistance overflow` checkbox for the confirmed resistance byte overflow bug.
- [x] Split the former monolithic `VddohDataEditor.java` into package-scoped classes for UI, table models, data rows, patch requests, patchers, offsets, and shared support helpers.
- [x] Ran a pre-Sonar static cleanup pass: removed stale generated imports, replaced wildcard static imports with specific imports, replaced `printStackTrace()` with logging, and removed the hard-coded local runtime path.
- [x] Added Lombok and Apache Commons Lang3, then started the JDK 25 refactor style pass with records, `@Builder`, and `@With` where the data shape is a good fit.
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
- [ ] Expand Monster editing beyond v1: decode/write packed HP, attack, defense, resistances, drops, and AI/skills once the monster tail and variable arrays are mapped safely.
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

Fallback build without Maven:

```cmd
cd D:\Games\JAR\JAR\vddoh-editor
build-with-jdk.cmd
```

Run:

```cmd
cd D:\Games\JAR\JAR\vddoh-editor
run-vddoh-editor.cmd
```
