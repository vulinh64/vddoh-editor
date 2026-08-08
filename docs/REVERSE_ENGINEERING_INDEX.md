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
docs/dat-bitmaps/                           Confirmed byte/bit maps for game.dat and item.dat
docs/DECOMPILED_METHOD_LEDGER.md            Confirmed and suspected decompiled method roles
VDDOH-STATS-MECHANISM.md                    Confirmed mechanics
PROGRESS.md                                 Current status and next checklist
```

## Key Runtime Classes

Use `javap -classpath ../vddoh.jar -c -p <class>` for bytecode and `rg -n` for text search in renamed classes.

| Obfuscated | Renamed file                                     | Why it matters                                                                                      |
|------------|--------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `g`        | `decompiled/renamed-classes/Hero.java`           | Hero stats, level growth, crit, hit/evasion, equipment aggregation, resistance overflow bug.        |
| `b`        | `decompiled/renamed-classes/BattleUnit.java`     | Monster/battle-unit rows parsed from `game.dat`; names, packed EXP/Filar/Soul Restore, core stats.  |
| `f`        | `decompiled/renamed-classes/Monster.java`        | Skill/action definition used by battle units; old "Monster" label is misleading for reward offsets. |
| `h`        | `decompiled/renamed-classes/SkillLevelData.java` | Per-level skill cost, damage, area/range animation references.                                      |
| `i`        | `decompiled/renamed-classes/Skill.java`          | Skill definitions, level selection, tooltip drawing, status/damage arrays.                          |
| `k`        | `decompiled/renamed-classes/Item.java`           | Item/equipment fields, rune/offensive/defensive effects, tooltip display.                           |
| `l`        | `decompiled/renamed-classes/Talent.java`         | Group talents, passive hero bonuses, spell unlocks.                                                 |
| `a`        | `decompiled/renamed-classes/StatusEffect.java`   | Status definitions, durations, modifiers, UI icons.                                                 |
| `j`        | `decompiled/renamed-classes/GameEngine.java`     | Parser for packed game data, save/load, battle and UI flow. Very large; search narrowly.            |

## Stable Mechanics Pointers

Do not re-derive these from scratch unless behavior changes:

- Hero growth formula: see `VDDOH-STATS-MECHANISM.md`.
- Base crit: hero packed field `short_b = 0x0532`, high byte chance, low byte damage bonus.
- Base evasion: bytecode constant `5 + Reflexes bonus`; not per-hero data.
- Resistance overflow: Hero resistance byte array clamps negative overflow to 0 in vanilla.
- Group talents write to `GameEngine.var_short_arr_a` slots, but that array is also general script/save state.
- Equipment rune slots: runtime `Item.k.g`; see `docs/dat-bitmaps/item-dat-bitmap.md` for the armor-tail and weapon-tail bit layouts.

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

## DAT Bitmaps

Use `docs/dat-bitmaps/` for stable byte/bit mappings:

```text
docs/dat-bitmaps/README.md
docs/dat-bitmaps/game-dat-bitmap.md
docs/dat-bitmaps/item-dat-bitmap.md
```

Keep these focused on data-file layout, computed offsets, writable bit ranges,
confidence, and source pointers. When a new `game.dat` or `item.dat` field is
confirmed, update the relevant bitmap instead of burying the offset only in a
general mechanics note.

## Decompiled Method Ledger

Use `docs/DECOMPILED_METHOD_LEDGER.md` for confirmed and suspected method roles
in obfuscated runtime classes. Keep the ledger focused on methods we have
actually touched or investigated; do not paste whole decompiled classes into it.

## Editor Patchers

In `src/main/java/com/vddoh/editor/`:

| Class                                         | Purpose                                                                                                                                                                                   |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `VddohDataEditor.java`                        | Application launcher.                                                                                                                                                                     |
| `view/FxEditorApplication.java`               | JavaFX UI coordinator and main tab layout.                                                                                                                                                |
| `view/**`                                     | JavaFX views and view-models.                                                                                                                                                             |
| `data/**`                                     | DTOs, rows, snapshots, patch requests, summaries, and read models.                                                                                                                        |
| `service/EditorLoadService.java`              | Loads selected JAR workspaces and applies raw `game.dat` monster reward headers so EXP/Filar/Soul Restore reload with packed 12-bit values instead of reflected signed artifacts.         |
| `service/EditorPatchService.java`             | Patch build service. `buildFullPatch(PatchBuildRequest)` writes game.dat, item.dat, and optional `g.class`/`j.class` replacements in one cumulative JAR operation.                        |
| `service/GameDatSkillPatcher.java`            | Writes safe skill cost/damage/status changes.                                                                                                                                             |
| `service/GameDatTalentPatcher.java`           | Writes safe talent amount/link changes.                                                                                                                                                   |
| `service/GameDatHeroPatcher.java`             | Writes hero stat curves, level cap, base crit bytes.                                                                                                                                      |
| `service/GameDatMonsterPatcher.java`          | Writes conservative monster fields: EXP, Filar, Soul Restore, tail Effect ID, packed STR/SPI/VIT/SPD-like core stat bytes, and existing fixed-width effect/resistance/drop array entries. |
| `service/MdatShopService.java`                | Parses and rewrites only the confirmed `m.dat` Children of Apocalypse shop events: length, `0x10 0x30 0x07` header, then stock item IDs. |
| `service/ItemDatPatcher.java`                 | Writes safe item top-level fields and existing fixed-width decoded effect bytes.                                                                                                          |
| `service/GameDatStatusPatcher.java`           | Writes safe status fields.                                                                                                                                                                |
| `service/ResistanceOverflowClassPatcher.java` | Optional `g.class` bytecode patch for resistance overflow; uses Class-File API semantic detection plus byte-minimal raw replacement.                                                      |
| `service/EquipmentBonusClassPatcher.java`     | Optional `g.class` bytecode patch for the equipment `byte_d` overwrite quirk; uses Class-File API to transform four assignment sites in `g.b()V` into accumulation sites.                 |
| `service/PhysicalDamageCapClassPatcher.java`  | Legacy `g.class` patcher; disproven for outgoing hero basic damage and must not be used for that fix. The confirmed target is `b.a(int heroIndex)`, which stores the final hero basic/rune/critical result in `b.i` before its low-10-bit mask. |
| `service/DiagonalBackAttackClassPatcher.java` | Optional guarded `b.class` patch for hero basic attacks. It preserves the vanilla direct-back check and promotes only the target-facing rear half-plane, including rear diagonals, to Back. |
| `service/HighValueDisplayClassPatcher.java`   | Optional `j.class` bytecode patch for high 3-digit text displays; changes `j.a(value, 1000)` so values over `999` render as `999+` instead of low-digit wrap.                              |
| `service/HighValueGraphicDisplayClassPatcher.java` | Optional `j.class` bytecode patch for party/menu HP/resource sprite digits; clamps only the digit operands to `999` after bar math so graphic values do not wrap.                      |
| `service/VictoryRewardClassPatcher.java`      | Optional `j.class` bytecode patch for the victory EXP remainder bug; changes the final `1..3` EXP award branch to use the pending EXP remainder instead of the pending Filar remainder.   |
| `service/MonsterRewardClassPatcher.java`      | Optional `j.class` bytecode patch for monster reward parsing; masks the EXP high byte and Filar low byte with `0xff` in `j.f(int)`.                                                       |
| `utils/EditorSupport.java`                    | Shared JAR, reflection, Java ME classpath, decode, binary, and validation helpers.                                                                                                        |

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
- Mutable parser accumulators may stay mutable until a clean immutable flow is
  obvious. Immutable editable rows should use `@With`; `MonsterRow` is the
  current immutable row prototype.

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
