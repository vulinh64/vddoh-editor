# Reverse Engineering Index

Central entry point for future VDDOH reverse-engineering. Read this before opening large decompiled files.

## Main Local Artifacts

```text
../vddoh.jar                                Original game JAR
../decompiled/renamed-classes/             Renamed/decompiled runtime classes
../decompiled/data-json-reflect/            JSON reflection dumps from original classes
../decompiled/data-tools/                   Older data extraction tools and notes
src/main/java/VddohDataEditor.java          Current editor and patcher implementation
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

The editor currently patches the `iconst_0; bastore; goto` block into `bipush 100; bastore; nop; nop` using an exact byte pattern.

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

In `VddohDataEditor.java`:

| Class | Purpose |
|---|---|
| `GameDatSkillPatcher` | Writes safe skill cost/damage/status changes. |
| `GameDatTalentPatcher` | Writes safe talent amount/link changes. |
| `GameDatHeroPatcher` | Writes hero stat curves, level cap, base crit bytes. |
| `ItemDatPatcher` | Writes safe item top-level fields. |
| `GameDatStatusPatcher` | Writes safe status fields. |
| `ResistanceOverflowClassPatcher` | Optional `g.class` bytecode patch for resistance overflow. |

## Future Direction

The user uses JDK 25. For future bytecode patches, prefer JDK 25 Class-File API (JEP 484) when possible:

https://openjdk.org/jeps/484

Use raw byte-pattern replacement only for narrow, already-confirmed patches with exact detection and refusal on unknown layouts.
