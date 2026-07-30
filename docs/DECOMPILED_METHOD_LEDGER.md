# Decompiled Method Ledger

Status: living document. This records method roles we have discovered or
strongly suspect in the decompiled VDDOH runtime classes. It is intentionally
not a full decompiler dump.

Use this together with:

```text
docs/REVERSE_ENGINEERING_INDEX.md
docs/dat-bitmaps/
VDDOH-STATS-MECHANISM.md
```

## Confidence Labels

| Label       | Meaning                                                                           |
|-------------|-----------------------------------------------------------------------------------|
| `Confirmed` | Verified by bytecode/decompiled code and either editor tests or in-game behavior. |
| `Probable`  | Strongly supported by bytecode/decompiled code, but not yet confirmed in-game.    |
| `Suspected` | Working hypothesis. Useful for search/navigation, not safe for patching.          |
| `Unknown`   | Signature is known, but the role is not mapped yet.                               |

## Class Map

| Obfuscated | Working name     | Primary domain                                                               |
|------------|------------------|------------------------------------------------------------------------------|
| `a`        | `StatusEffect`   | Status definitions, modifiers, icons.                                        |
| `b`        | `BattleUnit`     | Monster/battle-unit rows, reward values, packed core stats.                  |
| `f`        | `MonsterAction`  | Skill/action definition used by battle units; old Monster label is misleading. |
| `g`        | `Hero`           | Hero stats, equipment aggregation, battle result state, HP/resource updates. |
| `h`        | `SkillLevelData` | Per-level skill payload.                                                     |
| `i`        | `Skill`          | Skill definitions, targeting, tooltip/execution flow.                        |
| `j`        | `GameEngine`     | Packed data parsing, UI formatting, battle/save flow.                        |
| `k`        | `Item`           | Item/equipment fields, tooltip display, rune/effect application.             |
| `l`        | `Talent`         | Group and hero talents, passive bonuses, spell unlocks.                      |

## `j` / GameEngine

`j` is very large. Do not read it whole. Search narrowly or use `javap`.

| Method                                                                                              | Role                                                                                                                                                                                                | Confidence                                                                                       | Evidence / notes                                                                                                                                                           |
|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `public static java.lang.String a(int value, int widthBase)`                                        | Fixed-width decimal formatter that emits the low digits of `value` for a width implied by `widthBase`. Vanilla `j.a(value, 1000)` gives a 3-digit modulo display: `1000 -> "000"`, `1234 -> "234"`. | Confirmed                                                                                        | `javap -c -p j`; stat screen uses `sipush 1000` and measures `"999"`. `HighValueDisplayClassPatcher` changes only the `widthBase == 1000 && value > 999` case to `"999+"`. |
| `private static void a(Graphics, int, int, boolean, int, boolean, boolean, boolean, int, int)`      | HP/resource bar helper. It draws bar fills from a packed `current                                                                                                                                   | max << 16` value and, when its display flag is enabled, draws red sprite digits for current/max. | Confirmed                                                                                                                                                                  | `javap -c -p j`; `HighValueGraphicDisplayClassPatcher` clamps only the helper's sprite digit operands after bar math. |
| `public static void a(Graphics, Image, int, int, int, int, int, int, int)`                          | Sprite/tile draw helper used by stat screens, item tooltips, and battle result number rendering.                                                                                                    | Confirmed                                                                                        | Many call sites in `f`, `g`, `k`; parameters are image plus source/destination geometry.                                                                                   |
| `public static java.lang.String a(byte[])`                                                          | Decode game byte-string/name bytes into Java `String`.                                                                                                                                              | Confirmed                                                                                        | Used by item/name rendering and editor reflection support through matching runtime names.                                                                                  |
| `public static byte[] a(String, int)`                                                               | Encode or load byte-string data for text/save paths.                                                                                                                                                | Probable                                                                                         | Signature and nearby string methods; not used by editor patchers.                                                                                                          |
| `private static byte[] a(int)`                                                                      | Resource/data blob loader for packed assets or records.                                                                                                                                             | Suspected                                                                                        | Signature cluster around image loading and resource methods.                                                                                                               |
| `private static Image a(int)`                                                                       | Image resource loader/cache helper.                                                                                                                                                                 | Probable                                                                                         | Signature and image field usage.                                                                                                                                           |
| `private static int f(int offset)`                                                                  | Parse `game.dat` monster/battle-unit rows into `b.a:[Lb;`, including packed EXP/Filar/Soul Restore reward header.                                                                                   | Confirmed                                                                                        | `javap -c -p j`; editor offset parser and `MonsterRewardClassPatcher` target.                                                                                              |
| `private static int g(int offset)`                                                                  | Parse packed hero rows from `game.dat` into `g.b:[Lg;` and reset active party `g.a:[Lg;` to an empty array. Does not reset party Filar `g.q`.                                                       | Confirmed                                                                                        | `javap -c -p j`; method starts with `putstatic g.b`, `putstatic g.a`, then constructs `new g(1, heroIndex)`.                                                               |
| `public static void d(int slot)`                                                                    | Load save slot `VDBLOCK<slot>` from RMS, including active party heroes, inventory/state vectors, and party Filar `g.q`.                                                                             | Confirmed                                                                                        | `javap -c -p j`; reads `DataInputStream.readInt()` then `putstatic g.q:I`.                                                                                                 |
| `public static void e(int slot)`                                                                    | Write save slot `VDBLOCK<slot>` to RMS, including active party heroes, inventory/state vectors, and party Filar `g.q`.                                                                              | Confirmed                                                                                        | `javap -c -p j`; writes `getstatic g.q:I` through `DataOutputStream.writeInt(I)`.                                                                                          |
| `private void y()`                                                                                  | Script/event command dispatcher. Opcode `17` adds/subtracts party Filar based on event bytes.                                                                                                       | Confirmed                                                                                        | `javap -c -p j`; opcode `17` branch updates `g.q` from bytes `e[1]..e[3]`.                                                                                                 |
| `private static int a(l talent, int level)`                                                         | Talent value calculation or lookup helper.                                                                                                                                                          | Probable                                                                                         | Method takes `Talent` plus level; `VDDOH-STATS-MECHANISM.md` confirms talent amount-per-level semantics.                                                                   |
| `private static int a(short[] values, int index)` / `private static int a(int[] values, int index)` | Packed array lookup/sum helper.                                                                                                                                                                     | Suspected                                                                                        | Seen near parser/math helpers; not safe for writes.                                                                                                                        |
| `public static void a(i skill)`                                                                     | Enter or execute selected skill flow.                                                                                                                                                               | Probable                                                                                         | Signature takes `Skill`; called from battle/menu flow.                                                                                                                     |
| `public static Enumeration a(int, int, int, int, int, boolean)`                                     | Build/enumerate map or battle targets in an area/range.                                                                                                                                             | Suspected                                                                                        | Returns `Enumeration`; related overloads accept coordinates and booleans.                                                                                                  |
| `private static boolean a(int, int, g)`                                                             | Coordinate/hero interaction check.                                                                                                                                                                  | Suspected                                                                                        | Battle/map helper shape; no confirmed gameplay edit depends on it.                                                                                                         |
| `public static byte a()` / `public static byte b()`                                                 | Menu/state byte accessors.                                                                                                                                                                          | Unknown                                                                                          | Duplicate obfuscated names; no stable role yet.                                                                                                                            |
| `public static boolean a()` / `public static boolean b()`                                           | Game/UI state predicates.                                                                                                                                                                           | Unknown                                                                                          | Duplicate obfuscated names; not used by editor.                                                                                                                            |

### GameEngine Victory Reward Fields

| Field                    | Role                                                                | Confidence | Evidence / notes                                                                                                                      |
|--------------------------|---------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `private static short q` | Original total EXP for the victory progress display denominator.    | Confirmed  | `ai()` totals slain battle-unit rewards, then copies `q` into pending EXP `r`; victory renderer uses `r * 100 / q`.                   |
| `private static short r` | Pending EXP remaining to award during the victory fill animation.   | Confirmed  | The reward tick gives every hero `4` EXP chunks while `r > 3`, then decrements `r`; patched final branch now gives the `r` remainder. |
| `private static short s` | Pending Filar remaining to award during the victory fill animation. | Confirmed  | The reward tick adds chunks/remainder to party Filar `g.q`; renderer labels this field as `Filar`.                                    |

### Victory EXP Remainder Bug

`j.class` has a vanilla victory-result bug in the final small EXP remainder
branch. When pending EXP `r` is `1..3`, the code calls each hero's EXP award
method with pending Filar `s` instead of pending EXP `r`.

`VictoryRewardClassPatcher` fixes the single confirmed site:

```text
g.a[hero].a(s, false)  ->  g.a[hero].a(r, false)
```

The patcher requires one exact raw byte pattern and one semantic Class-File API
match before writing, then detects the patched `j.class` independently on reload.

### Monster Reward Parser Bug

The monster reward header offsets are confirmed in `j.f(int)`:

```text
+0                 = EXP high 8 bits
+1 high nibble     = EXP low 4 bits
+1 low nibble      = Filar high 4 bits
+2                 = Filar low 8 bits
+3                 = Soul Restore
```

The vanilla parser masks the shared nibble byte before extracting EXP low bits
and Filar high bits, but it does not mask the EXP high byte or Filar low byte.
Those two `baload` results sign-extend when the byte is `>= 128`, corrupting
larger rewards. `MonsterRewardClassPatcher` inserts `sipush 255; iand` at the
two confirmed sites in `j.f(int)`.

### New Game Filar Inheritance Good Bug

This is a confirmed vanilla bug and is intentionally not patched.

Party Filar lives in static hero field `g.q`. Class initialization sets it to
`0` only once in `g.<clinit>`. Loading a save later writes the saved value into
that same static field through `j.d(int)`:

```text
DataInputStream.readInt() -> putstatic g.q:I
```

Starting a new game rebuilds hero runtime rows through `j.g(int)`, but that
method only recreates `g.b:[Lg;` and clears active party `g.a:[Lg;` to an empty
array before constructing the hero definitions. It does not write `g.q`, so
money loaded from a previous save remains alive inside the same JVM/session.

The visible early-game `+25 Filar` then comes from script opcode `17` in
`j.y()`. The opcode decodes the amount as:

```text
((e[1] & 63) << 16) | ((e[2] & 255) << 8) | (e[3] & 255)
```

If `e[1] & 0x80` is set, the amount is added to `g.q`; otherwise it is
subtracted and clamped at zero. The packed script data contains two confirmed
`+25 Filar` commands in `m.dat`:

```text
0x000FA6: 11 80 00 19  -> opcode 17, add 25
0x027CFE: 11 C0 00 19  -> opcode 17, add 25, also sets the command flag bit 0x40
```

The resulting exploit chain is: load a save with high Filar, start a new game
without restarting the app, `j.g(int)` rebuilds heroes without clearing `g.q`,
then the early script command adds `25` more Filar when control is handed to
Vince and party.

### GameEngine Parser Responsibilities

The editor has confirmed that `j` parses these packed data groups into runtime
arrays:

| Runtime data                 | Confidence | Notes                                                                     |
|------------------------------|------------|---------------------------------------------------------------------------|
| `a[]` status definitions     | Confirmed  | Mirrored by `GameDatStatusPatcher.parseStatusOffsets`.                    |
| `i[]` skill definitions      | Confirmed  | Mirrored by `GameDatSkillPatcher`.                                        |
| `b[]` monsters/battle units  | Confirmed  | Monster EXP/Filar/Soul Restore raw reload corrected reflection artifacts. |
| `g[]` heroes                 | Confirmed  | Hero stat curves/crit fields patch and reload.                            |
| `l[]` group and hero talents | Confirmed  | Talent amount/link patching.                                              |
| `k[]` items                  | Confirmed  | Item decode and patch rows.                                               |

## `g` / Hero

`g` contains many overloads named `a`, `b`, etc. The rows below cover the methods
we have actually assigned.

| Method                                                                                | Role                                                                                                                                                                     | Confidence                                                 | Evidence / notes                                                                                                                              |
|---------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `public g(byte, byte)`                                                                | Hero constructor from packed hero id/type indices.                                                                                                                       | Confirmed                                                  | Reflection loading creates hero rows; field initialization includes battle result field `v = 0`.                                              |
| `public final void b()`                                                               | Recompute derived hero combat stats from natural stats, equipment, talents, and statuses. Contains the resistance overflow clamp and equipment `byte_d` overwrite sites. | Confirmed                                                  | Both `ResistanceOverflowClassPatcher` and `EquipmentBonusClassPatcher` target `g.b()V`; in-game equipment/resistance tests verified behavior. |
| `private int a(int level)`                                                            | Natural stat growth formula for a packed stat curve at a level.                                                                                                          | Confirmed                                                  | Formula matches editor `StatCurve.valueAtLevel` and level-cap in-game tests.                                                                  |
| `public final void a(int damage, int percent, boolean resourceSide, boolean healing)` | Apply HP/resource damage or recovery. Stores battle result flags for visible feedback and updates packed current/max HP/resource fields.                                  | Confirmed                                                  | `javap -c -p g`; HP/resource math uses incoming `damage` directly, then masks stored HP/resource with `0xffff`. Caller paths may pass a pre-masked value. |
| `public final void a(int damage, int percent, boolean resourceSide)`                  | Related HP/resource recovery path, likely non-healing or status/tick variant.                                                                                            | Probable                                                   | Bytecode uses incoming `damage` directly in `iload_1 * iload_2 / 100` and masks only the stored current/max fields.                           |
| `public final void a(f action, boolean, b target, boolean, boolean)`                  | Resolve a hero action against a battle-unit target, including physical damage build-up, crit flagging, enemy damage application, and status follow-up.                    | Confirmed                                                  | `PhysicalDamageCapClassPatcher` targets the enemy-side `target.b(v & 1023, ...)` damage call in this method.                                  |
| `public final void a(b)`                                                              | Resolve a battle-unit physical action against this hero, including miss/evasion, physical damage build-up, crit flagging, and hero HP application.                         | Confirmed                                                  | `javap -c -p g`; this path calls `this.a(v & 1023, 100, false, false)`, so actual hero Health damage receives the low-10-bit value.           |
| `public final boolean a(int, boolean)`                                                | Check/modify a hero state with a flag, likely status or equipment/talent gating.                                                                                         | Unknown                                                    | Signature known; not mapped safely.                                                                                                           |
| `public final boolean a(int)`                                                         | Predicate for a numeric hero state, possibly status/resistance/talent lookup.                                                                                            | Unknown                                                    | Multiple overloads; do not use for patch assumptions.                                                                                         |
| `public final int a()`                                                                | Accessor for a derived hero value.                                                                                                                                       | Unknown                                                    | Duplicate obfuscated name; no stable assignment.                                                                                              |
| `public final boolean f()`                                                            | Battle/state predicate.                                                                                                                                                  | Unknown                                                    | Signature known only.                                                                                                                         |
| `public static void a(short[])`                                                       | Static state/script array load or update.                                                                                                                                | Probable                                                   | Group talents write into `GameEngine` short-array slots; exact method not used by editor.                                                     |
| `public static void a(k item, int slot)`                                              | Apply/evaluate item in a hero/equipment slot.                                                                                                                            | Probable                                                   | Takes `Item` and slot; related to equipment aggregation.                                                                                      |
| `private static void b(k item, int slot)`                                             | Equipment aggregation helper for item slot.                                                                                                                              | Probable                                                   | Bytecode patchers inspect item `byte_d` handling in `g.b()V`; this helper is in same cluster.                                                 |
| `public final void a(Graphics)` / `b(Graphics)` / `c(Graphics)` / `d(Graphics)`       | Hero/stat/battle rendering methods.                                                                                                                                      | Probable                                                   | Graphics signatures; stat screen evidence from in-game screenshots.                                                                           |

### Hero Battle Result Field

`g.v` is a packed battle result field:

```text
low 10 bits  numeric result shown/used by confirmed damage paths: v & 1023
0x1000       hit/damage marker used by renderer
0x2000       evaded flag
0x4000       missed flag
0x10000      critical-hit marker
bits 17..23  skill/effect visual id in renderer
```

The low-10-bit behavior is a mask, not a clamp. Values over `1023` wrap in
display and in at least one confirmed HP application path.

## `f` / Monster

| Method                                                                | Role                                                           | Confidence | Evidence / notes                                                                          |
|-----------------------------------------------------------------------|----------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------|
| `public f()`                                                          | Monster constructor populated by packed data parser.           | Confirmed  | Reflection rows and raw `game.dat` header reload.                                         |
| `public final h a()`                                                  | Return selected/current skill level data.                      | Probable   | Used by skill-backed monster actions; `h` is per-level skill data.                        |
| `public final h b()`                                                  | Return alternate/current skill level data for selected action. | Probable   | Hero action bytecode calls `f.b()` to inspect skill effect fields.                        |
| `public final boolean a(byte statusOrType)`                           | Status/type resistance or applicability check.                 | Suspected  | Byte argument and monster status context; not in-game confirmed.                          |
| `public final boolean a(int)`                                         | Generic monster predicate/value check.                         | Unknown    | Duplicate obfuscated role.                                                                |
| `public final boolean a(g hero)`                                      | Monster can target or interact with a hero.                    | Probable   | Signature takes `Hero`; battle flow.                                                      |
| `public final boolean a(int, int)`                                    | Coordinate/range check.                                        | Suspected  | Two int parameters in monster movement/target cluster.                                    |
| `public final void a(Graphics, int, int, int, int, boolean, boolean)` | Draw monster in battle/map with state flags.                   | Probable   | Graphics signature.                                                                       |
| `private void a(Graphics, int, int)`                                  | Draw helper.                                                   | Probable   | Graphics-only helper.                                                                     |
| `public final void a(Graphics, int, int, int, int, int)`              | Draw monster detail/stat block.                                | Probable   | Stat screen and monster/item UI use fixed decimal formatter.                              |
| `public final void a(int)`                                            | Set or apply monster state/effect.                             | Unknown    | Signature known only.                                                                     |
| `private int d(int)`                                                  | Core stat or packed stat decode helper.                        | Probable   | Related public stat accessors call one-arg helpers.                                       |
| `public final int a(int)`                                             | Indexed monster stat/effect accessor.                          | Probable   | Editor reflects monster scalar/core stats.                                                |
| `public final int b(int)`                                             | Indexed monster stat/effect accessor.                          | Probable   | Same accessor cluster.                                                                    |
| `public final int c(int)`                                             | Indexed monster stat/effect accessor.                          | Probable   | Same accessor cluster; screenshots confirmed HP formula from core stats.                  |
| `public final int a()`                                                | Derived HP accessor.                                           | Probable   | Monster HP preview formula confirmed with Ryan and Schlange.                              |
| `public final int b()`                                                | Derived resource accessor.                                     | Probable   | Editor preview mirrors hero-like formula; not all monster resource behavior is confirmed. |
| `public final int c()`                                                | Derived attack accessor.                                       | Probable   | Editor preview mirrors hero-like formula.                                                 |
| `public final int d()`                                                | Derived defense/move accessor.                                 | Probable   | Editor preview mirrors hero-like formula.                                                 |
| `public final byte a()` / `public final byte b()`                     | Small packed monster property accessors.                       | Unknown    | Signature known only.                                                                     |

### Monster Unconfirmed Areas

| Topic                                                              | Status                                                                                           |
|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Monster `effects` int-array protection entries                     | Confirmed for spell/element reduction. High byte is kind, bit `0x8000` marks protection, low 15 bits are the flat reduction. Ayrene has `0x038064`, meaning Light protection `100`, explaining why a 100-damage Light attack appeared to do no damage. |
| Monster status resistance short-array currently keyed as `drops`    | Confirmed runtime status-resistance check in `b.b(int)`: high byte is status id, low byte is block chance. `100` blocks every `Random.nextInt(100)` roll. The editor raw key remains `drops` for now to avoid offset churn. |
| `resistA` / `resistB` exact semantics                              | Existing entries are writable as packed shorts, but exact gameplay meaning remains conservative; `resistA` is read by a high-byte-id lookup helper used near monster action selection/weighting. |
| `bytesD` exact semantics                                           | Existing byte entries are writable; byte values are copied into runtime status list setup through `StatusEffect.a(int, short[])`, but exact spawn/seed semantics are not fully named. |
| Soul Restore split/duplication when both Romus and Manok need Soul | In-game test confirmed restoration for Romus; multi-skeleton distribution is not confirmed.      |

## `h` / SkillLevelData

| Method              | Role                                                      | Confidence | Evidence / notes                                                                            |
|---------------------|-----------------------------------------------------------|------------|---------------------------------------------------------------------------------------------|
| `public h(f owner)` | Build per-level skill data from a monster or skill owner. | Probable   | Constructor takes `Monster`; fields match cost, target/range, damage arrays, status arrays. |

`h` has no additional public methods in the current signature inventory. Its
fields are decoded by reflection into skill snapshots and mirrored by
`GameDatSkillPatcher`.

## `i` / Skill

| Method                                         | Role                                                | Confidence | Evidence / notes                                                  |
|------------------------------------------------|-----------------------------------------------------|------------|-------------------------------------------------------------------|
| `public i()`                                   | Skill constructor populated by packed data parser.  | Confirmed  | Reflected `SkillLevelSnapshot` rows.                              |
| `public final void a(int, int)`                | Set target origin or prepare targeting coordinates. | Suspected  | Coordinate pair signature in skill flow.                          |
| `public final boolean a()`                     | Skill availability/castability predicate.           | Probable   | Public predicate in skill flow.                                   |
| `public final void a()`                        | Execute or advance skill action.                    | Probable   | Public no-arg action method in battle flow.                       |
| `private void c()`                             | Internal skill setup/cleanup.                       | Unknown    | Signature known only.                                             |
| `public final void a(Graphics)`                | Draw skill tooltip or targeting overlay.            | Probable   | Graphics signature; item linked-skill tooltip behavior confirmed. |
| `public final boolean b()`                     | Skill state predicate.                              | Unknown    | Signature known only.                                             |
| `private void d()` / `public final void b()`   | Internal/public skill flow steps.                   | Unknown    | Obfuscated duplicates.                                            |
| `private boolean d()` / `e()` / `f()` / `g()`  | Targeting, range, or usability checks.              | Suspected  | Boolean cluster in skill execution.                               |
| `private static void b(int, int)`              | Static coordinate helper.                           | Unknown    | Signature known only.                                             |
| `private boolean a(int, int)`                  | Coordinate/range predicate.                         | Suspected  | Used with targeting grid.                                         |
| `private static boolean a(int, int, int, int)` | Rectangle/area overlap or line/range predicate.     | Suspected  | Four coordinate parameters.                                       |
| `public static synchronized boolean c()`       | Global skill/battle update gate.                    | Unknown    | Signature known only.                                             |
| `private void a(byte)`                         | Select skill level/variant or status byte.          | Probable   | Category-9 item links pass a skill level/variant byte.            |

### Skill Notes

| Topic                              | Status                                                                                                  |
|------------------------------------|---------------------------------------------------------------------------------------------------------|
| Damage data width                  | Confirmed unsigned 16-bit in `game.dat` patcher; battle result uses low 10 bits in confirmed hero path. |
| Status data width                  | Confirmed one signed chance/value byte per editable row.                                                |
| Category-9 item linked skill level | Confirmed enough for UI navigation; exact variant semantics still need more in-game tests.              |

## `k` / Item

| Method                                                                             | Role                                                                   | Confidence | Evidence / notes                                                                         |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------|
| `public k()`                                                                       | Item constructor populated by packed `item.dat`.                       | Confirmed  | Reflection rows and item patch tests.                                                    |
| `private void a(Graphics, int, int)`                                               | Draw item name/icon helper.                                            | Probable   | Graphics helper.                                                                         |
| `public final void a(Graphics, int, int, boolean, boolean, int, boolean, boolean)` | Draw item tooltip/detail panel with category-specific effect sections. | Confirmed  | Tooltip behavior matched category-5, category-7, and category-9 observations.            |
| `public final int a(int statId)`                                                   | Return packed stat/effect value by stat id.                            | Probable   | Equipment aggregation bytecode calls item stat lookups; editor decodes packed stat rows. |
| `public final boolean a()`                                                         | Item category/use predicate.                                           | Unknown    | Signature known only.                                                                    |
| `public final boolean b()`                                                         | Item category/use predicate.                                           | Unknown    | Signature known only.                                                                    |
| `private void a(Graphics, int, int, int, int)`                                     | Tooltip subsection renderer.                                           | Probable   | Graphics helper.                                                                         |
| `public final int b(int)`                                                          | Return another indexed item effect/value.                              | Probable   | Used near tooltip/effect rendering.                                                      |
| `private void b(Graphics, int, int, int, int)`                                     | Tooltip subsection renderer.                                           | Probable   | Graphics helper.                                                                         |
| `private void b(Graphics, int, int, int, int, int)`                                | Tooltip row renderer with value.                                       | Probable   | Graphics helper.                                                                         |
| `private void a(Graphics, int, int, int, int, int, int)`                           | Tooltip row renderer with icon/value geometry.                         | Probable   | Graphics helper.                                                                         |
| `private void c(Graphics, int, int, int, int)`                                     | Tooltip subsection renderer.                                           | Probable   | Graphics helper.                                                                         |
| `public final void a(Graphics, int, int, int, int, int)`                           | Public draw method for item list/detail contexts.                      | Probable   | Graphics signature.                                                                      |
| `public final boolean a(g hero)`                                                   | Whether hero can use/equip item.                                       | Probable   | Takes `Hero`; allowed-class/equipment behavior.                                          |
| `public final int a(g hero, int statId)`                                           | Item effect value for a hero/stat context.                             | Probable   | Equipment aggregation and tooltip contexts.                                              |
| `public final boolean c()`                                                         | Use/equip predicate.                                                   | Unknown    | Signature known only.                                                                    |
| `public final byte a()` / `public final byte b()`                                  | Category/subtype accessors.                                            | Probable   | Editor category/subtype labels.                                                          |
| `public final boolean a(int)`                                                      | Predicate by hero id, class id, category, or status id.                | Unknown    | Signature known only.                                                                    |

### Item Confirmed/Suspected Areas

| Topic                            | Status                                                                                                   |
|----------------------------------|----------------------------------------------------------------------------------------------------------|
| Category 5 `short_g` / `short_h` | Confirmed HP/resource effect values; Vampire Stone tested.                                               |
| Category 6 permanent consumables | Confirmed in-game as non-battle max HP/resource +5 items. Exact method path not fully named.             |
| Category 7 rune split            | Confirmed tooltip split into common/weapon/armor sections; patcher does not write 3-byte int arrays yet. |
| Category 9/10 linked skills      | Confirmed linked skill preview/navigation; direct item effect values should be edited on Skills tab.     |
| `byte_d` equipment overwrite     | Confirmed vanilla quirk; optional `g.class` patch makes it accumulate.                                   |

## `l` / Talent

| Method                                                              | Role                                                  | Confidence | Evidence / notes                                                                             |
|---------------------------------------------------------------------|-------------------------------------------------------|------------|----------------------------------------------------------------------------------------------|
| `public l()`                                                        | Talent constructor populated by packed `game.dat`.    | Confirmed  | Reflected group/hero talent rows.                                                            |
| `public final boolean a(boolean group, int level, boolean preview)` | Check/apply talent effect or availability at a level. | Probable   | Signature includes group flag and level; editor confirms amount-per-level display from data. |
| `public final void a(Graphics, int, int, int, int, int)`            | Draw talent tooltip/detail row.                       | Probable   | Graphics signature.                                                                          |

### Talent Confirmed Areas

| Topic                                         | Status                                                                        |
|-----------------------------------------------|-------------------------------------------------------------------------------|
| Max level                                     | Confirmed stored as high nibble plus one, editor range `1..4`.                |
| Amount                                        | Confirmed high nibble `0..15`; applied per learned level.                     |
| Hero effect ids                               | Confirmed for Find Weaknesses, Deadly Might, Reflexes.                        |
| Optional global/skill/status/resistance links | Confirmed writable only when the packed flag already includes the link field. |
| Group talent exact script-state side effects  | Suspected beyond known slot writes; do not expand writes without tests.       |

## `a` / StatusEffect

| Method                                    | Role                                                | Confidence | Evidence / notes                                                      |
|-------------------------------------------|-----------------------------------------------------|------------|-----------------------------------------------------------------------|
| `public a()`                              | Status constructor populated by packed `game.dat`.  | Confirmed  | Reflected status rows and status patch tests.                         |
| `public final void a(Graphics, int, int)` | Draw status icon/name/effect detail.                | Probable   | Graphics signature.                                                   |
| `public final void b(Graphics, int, int)` | Draw alternate status tooltip/detail.               | Probable   | Graphics signature.                                                   |
| `public static short[] a(int, short[])`   | Apply or add a status modifier to a short array.    | Probable   | Static status-array helper; exact modifier semantics not fully named. |
| `public static short[] a(short[])`        | Normalize/filter status modifier array.             | Suspected  | Static array helper.                                                  |
| `public static short[] b(int, short[])`   | Remove or query a status modifier in a short array. | Probable   | Static status-array helper.                                           |

### Status Confirmed Areas

| Topic                              | Status                                               |
|------------------------------------|------------------------------------------------------|
| Duration                           | Confirmed writable byte when status flags expose it. |
| Expire chance                      | Confirmed writable signed chance byte.               |
| Icon                               | Confirmed writable byte.                             |
| Packed positive/negative modifiers | Parsed/skipped but not yet safely writable.          |

## `h`, `i`, `f`, `g` Battle Damage Notes

Confirmed battle path fragments:

```text
skill/item data damage value can be 0..65535
hero result field g.v is used by hero-side action/result paths
battle-unit result field b.i is used by outgoing hero basic attacks
damage/result display uses a low-10-bit mask, not a clamp
outgoing hero basic damage uses b.i & 1023 before application and popup render
enemy-to-hero physical damage uses g.v & 1023 before applying hero Health damage
HP/resource storage itself is masked as 16-bit current/max fields
```

### Physical Damage Wrap Bug

`b.a(int heroIndex)` is the confirmed outgoing hero basic-attack resolver. It
adds weapon/rune components, applies `g.d` (hero Attack) against battle-unit
defense, applies the hero critical bonus, and stores the final packed result in
`b.i`. Both the enemy-damage handoff and popup use `b.i & 1023`. This is a real
gameplay bug, not only display truncation. For example:

```text
1039 & 1023 = 15
1040 & 1023 = 16
```

Bit 16 of `b.i` is set after the critical arithmetic as its critical marker.
The direct distribution-JAR fix consequently caps the unflagged value once
after component/physical arithmetic and again after the critical addition,
before that marker is set:

```text
if (b.i > 999) {
  b.i = 999;
}
```

The first cap prevents an extreme pre-critical sum from narrowing incorrectly;
the second guarantees that a high critical cannot reach the later
`b.i & 1023` wrap.

The old `PhysicalDamageCapClassPatcher` targets `g.class` and is disproven for
this purpose. It must be replaced with a guarded `b.class` patcher before the
editor control is used again.

The same low-10-bit handoff exists on the opposite physical direction:
`g.a(b)` builds incoming battle-unit damage in `v`, applies the critical bonus,
then calls the hero HP/resource updater as:

```text
this.a(v & 1023, 100, false, false)
```

This specific call uses `percent = 100`, so the truncated value is applied to
Health rather than resource. The HP/resource updater methods themselves do not
apply the `1023` mask. They use their incoming `damage` argument directly and
mask only the packed current/max storage fields to `0xffff`. Therefore
truncation is caller-driven: physical battle paths that pass `v & 1023` apply
truncated real damage, while skill/recovery paths that pass raw `v` use the
larger value.

### High 3-Digit Display Wrap

The shared formatter `j.a(int value, int widthBase)` emits fixed low digits.
For `widthBase == 1000`, vanilla output is a 3-digit wrap:

```text
999  -> "999"
1000 -> "000"
1007 -> "007"
1234 -> "234"
```

This affects text-rendered stat/menu values such as high HP/resource displays,
but it does not change the underlying packed 16-bit HP/resource storage.

`HighValueDisplayClassPatcher` patches only this formatter case:

```text
if (widthBase == 1000 && value > 999) {
  return "999+";
}
```

Values `0..999` keep the vanilla fixed-width output. Sprite/graphic battle
damage digits do not use a plus glyph; those remain handled by the physical
damage cap patch and saturate at `999`.

The party overview screen does not use the shared text formatter for the red
bar numbers. It first calls the HP/resource bar helper without its internal
digit drawing, then draws six inline red sprite digits directly from active
party hero fields:

```text
g.a[slot].l & 65535 -> current HP digits
g.a[slot].m & 65535 -> current resource digits
```

Those inline digits used the same low-three-digit math, so `1001` displayed as
`001`. `HighValueGraphicDisplayClassPatcher` is a separate patch from the text
formatter patch. It caps only the operands used by sprite digit math:

```text
Math.min(displayValue, 999)
```

It covers the shared bar helper and the six confirmed party-overview inline
digit sites. The actual packed HP/resource values and bar-fill calculations are
left unchanged.

## Unmapped Method Inventory

The obfuscated classes contain many duplicate overloads named `a`, `b`, `c`,
etc. A signature appearing in `javap -p` is not enough to assign a safe role.
When mapping a new method:

1. Start with `javap -classpath src\test\resources\vddoh.jar -c -p <class>`.
2. Search for nearby field ids or constants already documented here.
3. Confirm against a focused in-game test or an editor regression when the
   method affects editable data.
4. Update this ledger and the relevant `docs/dat-bitmaps/` file if a data field
   mapping changes.
