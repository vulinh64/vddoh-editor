# Reverse Engineering Index

Central entry point for future VDDOH reverse-engineering. Read this before opening large decompiled files.

## Main Local Artifacts

```text
../vddoh.jar                                Original game JAR
../decompiled/renamed-classes/             Renamed/decompiled runtime classes
../decompiled/data-json-reflect/            JSON reflection dumps from original classes
../decompiled/data-tools/                   Older data extraction tools and notes
src/main/java/com/vddoh/editor/             Current editor and patcher implementation
me-lib/                                     Minimal Java ME/API jars unpacked into the built editor JAR
VDDOH-STATS-MECHANISM.md                    Confirmed mechanics
PROGRESS.md                                 Current status and next checklist
```

## Key Runtime Classes

Use `javap -classpath ../vddoh.jar -c -p <class>` for bytecode and `rg -n` for text search in renamed classes.

| Obfuscated | Renamed file | Why it matters |
|---|---|---|
| `g` | `decompiled/renamed-classes/Hero.java` | Hero stats, level growth, crit, hit/evasion, equipment aggregation, resistance overflow bug. |
| `f` | `decompiled/renamed-classes/Monster.java` | Monster stats, damage intake, skill/status resistance checks, EXP/Filar paths. |
| `h` | `decompiled/renamed-classes/SkillLevelData.java` | Per-level skill cost, damage, area/range animation references. |
| `i` | `decompiled/renamed-classes/Skill.java` | Skill definitions, level selection, tooltip drawing, status/damage arrays. |
| `k` | `decompiled/renamed-classes/Item.java` | Item/equipment fields, rune/offensive/defensive effects, tooltip display. |
| `l` | `decompiled/renamed-classes/Talent.java` | Group talents, passive hero bonuses, spell unlocks. |
| `a` | `decompiled/renamed-classes/StatusEffect.java` | Status definitions, durations, modifiers, UI icons. |
| `j` | `decompiled/renamed-classes/GameEngine.java` | Parser for packed game data, save/load, battle and UI flow. Very large; search narrowly. |

## Stable Mechanics Pointers

Do not re-derive these from scratch unless behavior changes:

- Hero growth formula: see `VDDOH-STATS-MECHANISM.md`.
- Base crit: hero packed field `short_b = 0x0532`, high byte chance, low byte damage bonus.
- Base evasion: bytecode constant `5 + Reflexes bonus`; not per-hero data.
- Resistance overflow: Hero resistance byte array clamps negative overflow to 0 in vanilla.
- Group talents write to `GameEngine.var_short_arr_a` slots, but that array is also general script/save state.

## Useful Search Patterns

```powershell
rg -n "var_short_b|var_short_c|var_byte_j|var_byte_k|var_byte_l" ..\decompiled\renamed-classes\Hero.java
rg -n "var_byte_arr_b|bastore|ifge|bipush        100" ..\decompiled\renamed-classes ..\decompiled\data-json-reflect
rg -n "byte_g_7|Find Weaknesses|Deadly might|Reflexes" ..\decompiled\data-json-reflect\talents_hero.json
rg -n "short_b_32|short_f_36" ..\decompiled\data-json-reflect\heroes.json
```

For exact bytecode of the current class patch target:

```cmd
javap -classpath ..\vddoh.jar -c -p g
```

Look in `public final void b();` around the resistance array clamp:

```text
baload
ifge ...
... iconst_0
bastore
goto ...
... bipush 100
bastore
```

The editor patches the `iconst_0; bastore; goto` block into
`bipush 100; bastore; nop; nop` using an exact byte pattern. Before applying the
raw write, `ResistanceOverflowClassPatcher` also uses JDK 25's Class-File API to
confirm exactly one semantic match in `g.b()V`:

```text
aload_0
getfield same byte[] resistance field
getstatic same resistance index field
baload
ifge ...
aload_0
getfield same byte[] resistance field
getstatic same resistance index field
iconst_0
bastore
goto ...
```

This hybrid check keeps the actual patch byte-minimal while refusing unexpected
class layouts.

## Data Files And JSON Dumps

Prefer these files before opening huge decompiled classes:

```text
../decompiled/data-json-reflect/heroes.json
../decompiled/data-json-reflect/talents_hero.json
../decompiled/data-json-reflect/talents_group.json
../decompiled/data-json-reflect/items.json
../decompiled/data-json-reflect/skills*.json
../decompiled/data-json-reflect/monsters*.json
../decompiled/data-json-reflect/statuses*.json
```

If a JSON file is large, search within it instead of reading all of it.

## Editor Patchers

In `src/main/java/com/vddoh/editor/`:

| Class | Purpose |
|---|---|
| `VddohDataEditor.java` | Main class only. |
| `EditorFrame.java` | Swing UI coordinator and build/load actions. |
| `EditorSupport.java` | Shared JAR, reflection, Java ME classpath, decode, binary, and validation helpers. |
| `EditorPatchService.java` | Shared JavaFX patch build service. `buildFullPatch(PatchBuildRequest)` writes game.dat, item.dat, and optional `g.class` replacements in one cumulative JAR operation. |
| `GameDatSkillPatcher.java` | Writes safe skill cost/damage/status changes. |
| `GameDatTalentPatcher.java` | Writes safe talent amount/link changes. |
| `GameDatHeroPatcher.java` | Writes hero stat curves, level cap, base crit bytes. |
| `GameDatMonsterPatcher.java` | Writes conservative monster v1 fields: EXP, Filar, Death Value, tail Effect ID, and packed STR/SPI/VIT/SPD-like core stat bytes. |
| `ItemDatPatcher.java` | Writes safe item top-level fields. |
| `GameDatStatusPatcher.java` | Writes safe status fields. |
| `ResistanceOverflowClassPatcher.java` | Optional `g.class` bytecode patch for resistance overflow; uses Class-File API semantic detection plus byte-minimal raw replacement. |

## Future Direction

The user uses JDK 25. For future bytecode patches, prefer JDK 25 Class-File API (JEP 484) when possible:

https://openjdk.org/jeps/484

Use raw byte-pattern replacement only for narrow, already-confirmed patches with
semantic detection and refusal on unknown layouts. The current resistance
overflow patch follows this hybrid approach.

Bytecode tooling preference:

```text
1. JDK Class-File API
   Default choice for structural offline class patches. It is standard in the
   JDK 25 toolchain and avoids dependency/version skew.

2. ASM
   Keep as a fallback if the Class-File API becomes too verbose for a specific
   instruction-window rewrite. Do not add it until a concrete patch needs it.

3. Byte Buddy
   Not a good fit for current VDDOH patching. It shines at runtime generation,
   proxies, agents, and instrumentation, while this editor performs conservative
   offline edits to old J2ME class files.

4. BCEL
   Possible, but currently no advantage over Class-File API or ASM.
```

For class patches, prefer this shape:

```text
parse class -> structurally match one known method/instruction shape
            -> transform only the intended instruction block
            -> preserve old class version when possible
            -> verify patched semantic shape
            -> refuse unknown layouts
```

## Java Refactor Direction

The current source uses JDK 25 plus Lombok and Apache Commons Lang3.
Diagnostics use Lombok `@Slf4j`, SLF4J 2.0.18, and Logback 1.5.37.
The default runtime level is `INFO` so load/build/patch breadcrumbs are visible
during reverse-engineering sessions. Launch with `-Dvddoh.log.level=WARN` when
you want quieter output.

Guidelines for new editor data classes:

- Prefer records with Lombok `@Builder` and `@With` for immutable DTOs, offsets,
  and read-model rows.
- Prefer builder calls or builder-backed static factories over positional record
  canonical constructors.
- If a record component is a collection, normalize null input to an empty
  collection in the compact constructor unless null has a specific documented
  meaning.
- Editable Swing table rows can remain mutable until the table model is updated
  to replace row instances. `MonsterRow` is the current immutable editable-row
  prototype using `@With`.

For Java ME/API classes, keep `me-lib` minimal:

```text
cldc11.jar
midp21.jar
jsr135.jar
```

The Maven build unpacks those API classes into the executable editor JAR, so
`target/vddoh-data-editor-1.0.0.jar` can run away from the repository without a
neighboring `me-lib` directory. During development, `EditorSupport` also adds
local `me-lib/*.jar` if the directory exists. Do not add runtime fallback logic
to external installations; the project should remain self-contained.

Exception: keep the local `javax.microedition.lcdui.Font` shim. The `Font.class`
inside `midp21.jar` returns `null` from `getFont(...)`/`getDefaultFont()`, which
breaks the original `j` class static initializer during reflection loading.
The Maven unpack excludes only `javax/microedition/lcdui/Font.class` from
`midp21.jar` so the local shim wins while the rest of MIDP still comes from
`me-lib`.
