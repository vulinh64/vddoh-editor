# VDDOH Editor Progress

This project contains the current Swing data editor and reverse-engineering notes for `Vampires Dawn: Deceit of Heretics` (`vddoh.jar`).

## Current Project State

- [x] Moved the editor into a Maven-style project: `vddoh-editor/`.
- [x] Main source moved to `src/main/java/VddohDataEditor.java`.
- [x] Java ME `Font` stub moved to `src/main/java/javax/microedition/lcdui/Font.java`.
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
- [x] Editable Statuses table for safe status fields.
- [x] Search support across Skills, Heroes, Items, Monsters, and Statuses.
- [x] Build patched JAR by replacing modified entries.
- [x] Optional `Patch resistance overflow` checkbox for the confirmed resistance byte overflow bug.

## Latest Bytecode Patch State

The resistance overflow patch is implemented as a conservative raw byte-pattern patch in `g.class`.

- [x] On JAR load, editor detects whether `g.class` is vanilla, already patched, or unknown.
- [x] Already patched JAR: checkbox checked and disabled.
- [x] Vanilla known JAR: checkbox enabled and unchecked by default.
- [x] Unknown layout: checkbox disabled.
- [x] Build patched JAR incorporates `g.class` only when checkbox is checked and patch is applicable.

Future improvement: use JDK 25's Class-File API from JEP 484 for structural class editing instead of raw byte-pattern replacement.

## What We Are Leaving For Next Session

- [ ] Run a quick in-game test of `Patch resistance overflow` with Romus/Manok plus extra Bleed resistance.
- [ ] Confirm patched `g.class` detection by loading an already-patched output JAR back into the editor.
- [ ] Add a dedicated `Class Patches` tab if more bytecode patches are added.
- [ ] Consider replacing raw byte-pattern class patching with JDK 25 Class-File API.
- [ ] Expand Monster editing: HP, attack, defense, EXP, Filar, resistances, drops, AI/skills if safe.
- [ ] Expand Item editing for multi-effect rows rather than only top-level safe fields.
- [ ] Add explicit patch validation reports before writing output JAR.
- [ ] Add unit tests or small command-line verifier for patchers.
- [ ] Update docs when new field mappings become confirmed.

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
