# VDDOH Stats Mechanism

This document records the current confirmed understanding of hero stats in
`Vampires Dawn: Deceit of Heretics`.

## Core Hero Stats

Each hero has four natural growth stats:

- Strength
- Spirit
- Vitality
- Speed

The previous working labels were `Power`, `Spirit`, `Vitality`, and `Agility`.
Screenshots and code behavior confirm that the better labels are:

```text
Power   -> Strength
Agility -> Speed
```

Speed affects the battle movement zone, meaning how many tiles/area the hero can
cover in battle.

## Growth Encoding

Each core stat is stored as three values:

```text
Start
Lv99 Target
Growth Curve
```

Meaning:

- `Start` is the low-level / level-1 value.
- `Lv99 Target` is the natural stat value at level 99, not the normal level cap.
- `Growth Curve` controls how quickly the stat approaches the level-99 target.

The target is not an absolute final stat cap for two reasons:

- The game normally caps heroes at level 30, so vanilla heroes never naturally
  reach their level-99 target.
- Equipment, talents, buffs, and statuses can still modify stats after natural
  growth is calculated.

All three natural stat fields are byte-sized in the packed hero data. In the
current patcher, `Start` and `Lv99 Target` are treated as 7-bit values
(`0..127`) because of the packing format; `Growth Curve` is a full byte
(`0..255`). `Level Cap` is also stored as a 7-bit value (`0..127`).

## Growth Formula

The decompiled code calculates a core stat with integer math:

```text
current =
level * (target - start)
* (level * (100 - curve) / 99 + curve)
/ 99 / 100
+ start
```

Readable form:

```text
growthRange = target - start
curveFactor = level * (100 - curve) / 99 + curve

current = start + level * growthRange * curveFactor / 99 / 100
```

All divisions are integer divisions, matching Java ME behavior.

Curve interpretation:

```text
curve = 0    -> very slow early growth; heavily back-loaded
curve = 100  -> roughly linear growth toward the level-99 target
curve > 100  -> stronger early growth, slower later
curve < 100  -> slower early growth, stronger later
```

Important examples discovered while testing:

```text
Start 15, Lv99 Target 110, Curve 0,   Level 30 -> 23
Start 15, Lv99 Target 110, Curve 100, Level 30 -> 43
Start 15, Lv99 Target 110, Curve 200, Level 30 -> 63
```

This explained the confusing Vince test: changing `Start` to `15` and `Target`
to `110` still produced level-30 Strength `23` when the curve was `0`.

Another confirmed test:

```text
Start 15, Lv99 Target 115, Curve 100, Level Cap 50

Strength @ 50 = 65
Spirit   @ 50 = 65
Vitality @ 50 = 65
Speed    @ 50 = 32  (with Speed Target 50)
```

The patched game showed the same raw stat screen values at level 50, confirming
that the level cap patch and the editor's `@ Cap` preview formula match runtime
behavior.

## Derived Base Stats

From the code and level-1 screenshots, these base formulas are confirmed:

```text
Health   = ((Vitality * 70 + Strength * 30) * 12 / 100) + flat HP bonus
Resource = ((Spirit   * 70 + Vitality * 30) * 12 / 100) + flat resource bonus
Attack   = Strength * 5 - 9 + attack bonus
Defense  = Speed * 3 + Strength - 18 + defense bonus
Move     = 2 + Speed / 5 + move bonus
Regen    = 1 + regen bonus
```

`Resource` is Blood for Lara and Vince, and Soul for Romus and Manok.

These formulas are safest for level-1/no-equipment baselines. Runtime values at
higher levels are recalculated from grown Strength/Spirit/Vitality/Speed and can
also include equipment and other bonuses. For example, the level-50 test with
high grown stats produced `780/780` HP/resource and large attack/defense values
in-game, while the editor currently previews only the four core grown stats.
Future editor columns should add `HP @ Cap`, `Resource @ Cap`, `Attack @ Cap`,
and `Defense @ Cap` once those higher-level derived formulas are fully traced.

## Critical Hits

Critical chance and critical damage are packed together in a short-like runtime
field.

All four heroes share the same confirmed base packed crit value:

```text
short_b = 1330 decimal = 0x0532

high byte 0x05 = 5  -> base critical chance = 5%
low byte  0x32 = 50 -> base critical damage bonus = 50%
```

Confirmed base values:

```text
Lara:  5% crit chance, +50% crit damage
Vince: 5% crit chance, +50% crit damage
Romus: 5% crit chance, +50% crit damage
Manok: 5% crit chance, +50% crit damage
```

Confirmed attack-side formula:

```text
critChance = baseCritChance + Find Weaknesses bonus
critDamageBonus = baseCritDamageBonus + Deadly Might bonus

if critChance > random(0..99):
  damage = damage + damage * critDamageBonus / 100
```

The damage flag `0x10000` is then set so the UI draws the critical-hit marker.

The confirmed hero talent mapping is:

```text
Find Weaknesses -> hero bonus id 3 -> +1% crit chance per talent level
Deadly Might    -> hero bonus id 4 -> +10% crit damage bonus per talent level
```

The base critical damage bonus appears to be `50%`. Therefore vanilla Deadly
Might previews as:

```text
Level 1: 60%
Level 2: 70%
Level 3: 80%
Level 4: 90%
```

Important wording:

```text
Critical damage bonus is bonus damage, not total damage multiplier.
```

So `90%` means a critical hit deals:

```text
damage + damage * 90 / 100
```

or roughly `190%` of normal damage after integer truncation.

## Miss And Evasion

Physical attacks have two separate failure states:

```text
MISSED = accuracy/hit check failed
EVADED = evasion/reflex check failed
```

The combat result flags are:

```text
0x4000 = MISSED
0x8000 = EVADED
```

Hero runtime hit/evasion appears to be packed in `var_short_f`:

```text
high byte = hit / connect chance
low byte  = evasion / reflex chance
```

The hero-side base evasion formula is:

```text
evasionChance = 5 + Reflexes bonus
```

Reflexes is the passive hero talent with hero bonus id `5`:

```text
Reflexes -> +2% evasion per talent level
```

So vanilla Reflexes gives:

```text
Level 1:  7% evasion
Level 2:  9% evasion
Level 3: 11% evasion
Level 4: 13% evasion
```

Facing controls which checks are allowed.

For a physical attack:

```text
Back attack:
  skips MISS check
  skips EVADE check
  result: always connects

Side attack:
  performs MISS check
  skips EVADE check
  result: can MISS, cannot be EVADED

Front attack:
  performs MISS check
  performs EVADE check
  result: can MISS, or can be EVADED if the hit roll succeeds first
```

This matches gameplay text: attacking from the side prevents the enemy from
evading, but the attack can still miss; attacking from the back always hits.

The order is important:

```text
if not back attack and targetHitChance < random(0..99):
  MISSED
  stop

if front attack and evasionChance > random(0..99):
  EVADED
  stop

apply damage
roll critical hit
```

Because the game uses comparisons against `random(0..99)`, many displayed
percent-like values should be treated as gameplay probabilities with possible
off-by-one quirks around `0` and `100`.

## Elemental Resistance And Damage

Elemental resistance is not the same as status immunity. For skills that deal
raw elemental damage, the game subtracts or mitigates against the incoming damage
value rather than turning `100` resistance into full immunity.

This applies to damage-bearing skill elements:

```text
Fire
Ice
Light
Shadow
Blood
```

Confirmed gameplay implication:

```text
Fireball level 4 base damage: 150
Target fire resistance:       100
Final fire damage:             50
```

So a target with `100` Fire resistance can still take damage from a `150` damage
Fireball. The resistance behaves like a flat damage reduction in this case, not
like a 100% percentage shield.

This does not mean status effects work the same way. Statuses such as Poison,
Bleeding, Blaze, Sleep, Blind, and similar debuffs use status application and
status resistance checks instead of elemental damage reduction. For example,
Blaze may be caused by a fire-based skill or environment, but once treated as a
status it belongs to the status-resistance system, not the Fire damage-reduction
formula.

This matters for editor wording:

```text
Fire/Ice/Light/Shadow/Blood drain damage -> damage reduction / protection value
Status effects                       -> chance/immunity-style status blocking
```

## Confirmed Level-1 Base Values

These values were confirmed from in-game screenshots with no relevant stat items
equipped.

```text
Lara:
  Strength 2
  Spirit   3
  Vitality 2
  Speed    6
  Health   24
  Blood    32
  Attack   1
  Defense  2
  Move     3
  Regen    1

Vince:
  Strength 3
  Spirit   1
  Vitality 3
  Speed    6
  Health   36
  Blood    19
  Attack   6
  Defense  3
  Move     3
  Regen    1

Romus:
  Strength 3
  Spirit   1
  Vitality 2
  Speed    6
  Health   27
  Soul     15
  Attack   6
  Defense  3
  Move     3
  Regen    1

Manok:
  Strength 3
  Spirit   2
  Vitality 2
  Speed    6
  Health   27
  Soul     24
  Attack   6
  Defense  3
  Move     3
  Regen    1
```

## Formula Checks

Lara:

```text
Health = ((2 * 70 + 2 * 30) * 12 / 100) = 24
Blood  = ((3 * 70 + 2 * 30) * 12 / 100) = 32
Attack = 2 * 5 - 9 = 1
Defense = 6 * 3 + 2 - 18 = 2
Move = 2 + 6 / 5 = 3
```

Vince:

```text
Health = ((3 * 70 + 3 * 30) * 12 / 100) = 36
Blood  = ((1 * 70 + 3 * 30) * 12 / 100) = 19
Attack = 3 * 5 - 9 = 6
Defense = 6 * 3 + 3 - 18 = 3
```

Romus:

```text
Health = ((2 * 70 + 3 * 30) * 12 / 100) = 27
Soul   = ((1 * 70 + 2 * 30) * 12 / 100) = 15
Attack = 3 * 5 - 9 = 6
Defense = 6 * 3 + 3 - 18 = 3
```

Manok:

```text
Health = ((2 * 70 + 3 * 30) * 12 / 100) = 27
Soul   = ((2 * 70 + 2 * 30) * 12 / 100) = 24
Attack = 3 * 5 - 9 = 6
Defense = 6 * 3 + 3 - 18 = 3
```

## Character Role Implications

The stats support the gameplay roles:

```text
Vince / Romus = physical-leaning melee fighters
Lara / Manok  = caster-leaning ranged fighters
```

More specifically:

- Vince is the clearest bruiser: high Strength, high Vitality, low Spirit.
- Romus is also physical-leaning, but less tanky than Vince.
- Lara is the clearest caster: highest Spirit, lower Strength.
- Manok is a hybrid/support caster: moderate Strength and higher Spirit than
  Vince/Romus, plus unique spell access.

## Equipment and Bonus Separation

Hero base data should be separated from equipment and item bonuses.

Example: Manok originally showed:

```text
Attack 17
Defense 7
```

After removing his items, he showed:

```text
Attack 6
Defense 3
```

So the extra values were equipment bonuses:

```text
Attack bonus  +11
Defense bonus +4
```

This confirms that base hero editing should focus on Strength, Spirit, Vitality,
Speed, and their growth curves. Equipment/item editing should expose attack,
defense, HP/resource, movement, regen, resistance, and on-hit modifiers.

## Item Effect Notes

There are at least two consumable paths:

```text
Category 5  anytime/direct consumable
Category 6  permanent non-battle consumable
Category 9  combat-only skill-backed consumable
```

Category `5` is the direct consumable path. These items use direct item fields
for their effects:

```text
short_g     HP effect applied during item use
short_h     resource effect applied during item use
short_c..f  packed stat/equipment-style bonuses, also used by some consumables
short_arr_a status gate/check array observed in the consumable use path
short_arr_b status apply/remove array observed in the consumable use path
byte_q      use visual/effect id
```

Category `6` includes permanent non-battle consumables such as Ankh of Life and
Ankh of Magic. In-game, these are not battle items; each use permanently raises
max HP or max resource by `5`.

Category `9` items, including Troll Elixir and Might Potion, are combat-only
consumables that dispatch through a linked skill. Their linked skill is stored
as:

```text
byte_o  zero-based skill id, used as f.a[byte_o & 255]
byte_p  skill level/variant passed to the selected skill
```

The editor now displays category-5 consumable fields as consumable effects,
places category-9 combat consumables in the Consumables item view, and displays
their skill links as linked skill rows. The item view also shows a read-only
preview of the linked skill's target shape/range and effects, because combat
consumable tooltips are mostly skill tooltips.

Confirmed example:

```text
Might Potion / Might potion
  item category = 9
  linked skill id = 33
  stored level/variant = 1
  reflected skill level = 0
  target area = 1x1
  range = 1
  effect = Strong 100%
```

The in-game tooltip's `1` square and double-ended-arrow line is target/range
metadata from the linked skill. It is not a direct `+1 Speed` consumable field.

Confirmed category-5 consumable example:

```text
Vampire Stone / Vampire stone
  short_g        -> HP effect 999
  short_h        -> Resource effect 999
  short_arr_b[0] -> cures Poison
```

In game, Vampire Stone fully restores Health, or at least applies `999` HP
recovery, restores `999` resource regardless of whether the user uses Blood or
Soul, and cures Poison. These values are edited in the JavaFX `Decoded Effects`
table, not in a separate consumable panel.

Equipment wording should be category-aware. For example, War Plate Mail decodes
as:

```text
byte_d          -> Attack bonus 10
int_arr_a[0]   -> Armor value 65
short_arr_b[0] -> Anti-bleeding 30
```

Confirmed in game: War Plate Mail's `Armor value 65` adds flat `+65` Defense to
the wielder. A level-50 Vince comparison showed:

```text
No armor:       Attack 351, Defense 195, Bleed resistance 0
War Plate Mail: Attack 361, Defense 260, Bleed resistance 30
```

Therefore `byte_d = 10` is at least a displayed `Attack bonus +10`. Bytecode
inspection of `g.b()V` shows this is not the same path as weapon damage:
weapons add attack through `int_arr_a` entries whose high byte is `0`, while
`byte_d` is copied into one of two scalar locals during equipment aggregation.
Those locals are assigned, not accumulated.

Follow-up equipment-stack test at Vince level 50:

```text
Sickle Blade only:
  Attack 446, Defense 196

Sickle Blade + War Plate Mail:
  Attack 456, Defense 261, Bleed resistance 30

Sickle Blade + War Plate Mail + Strong Helmet:
  Attack 446, Defense 291, Bleed resistance 30

Sickle Blade + War Plate Mail + Strong Helmet + Aaron's Shoes:
  Attack 446, Defense 311, Sleep resistance 80, Bleed resistance 30
```

This is a real vanilla aggregation quirk, not an editor decoding issue. In
`g.b()V`, normal packed stat slots `0..7` are accumulated with addition, but
slot `8` / `byte_d` overwrites a single local as equipment slots are scanned.
The displayed Attack formula then adds that one local:

```text
Attack = max(Strength * 5 - 9, 0) + overwritten non-weapon byte_d
```

For the weapon slot, `byte_d` feeds a different local that is added to displayed
Defense. Actual weapon damage such as Sickle Blade's `+90` comes from
weapon-side `int_arr_a` target `0`, so it still stacks normally.

This explains the screenshots: War Plate Mail contributes `byte_d = 10`, so
Attack rises by `10`; Strong Helmet later contributes `byte_d = 0` through the
same overwrite path, so the War Plate attack bonus disappears while Defense and
status resistance still stack normally.

### Equipment Bonus Aggregation Patch Option

The editor now has a separate `Patch equipment bonus overwrite` checkbox.

Behavior:

```text
Input JAR already patched:
  checkbox is checked and disabled

Input JAR has the known vanilla g.b()V equipment byte_d overwrite shape:
  checkbox is enabled and unchecked by default

Input JAR has an unknown g.class layout:
  checkbox is disabled
```

When enabled and checked during `Build Full Patched JAR`, the editor rewrites
`g.class` so the four known `byte_d` assignment sites accumulate instead:

```text
bonus = item.byte_d   -> bonus += item.byte_d
```

This is implemented separately from the resistance-overflow patch. Unlike the
resistance patch, this transform inserts bytecode and therefore uses JDK 25's
Class-File API to rebuild `g.b()V` safely. The detector confirms the original
shape before patching and confirms the patched shape afterward.

The same inspection corrected the editor's armor subtype labels:

```text
category 2 subtype 0 -> Head
category 2 subtype 1 -> Main Body Armor
category 2 subtype 4 -> Boot
```

The editor shows these as display labels only; the raw packed fields and patch
offsets remain unchanged.

The editor can edit existing fixed-width decoded item effect rows in place:

```text
Packed stat high/low bytes
Category-5 HP/resource/use-effect bytes
short_arr_a / short_arr_b low value bytes
```

For `short_arr_a` and `short_arr_b`, the high byte identifies the stat/status
target and is preserved. The editable value is the low byte only. The editor
does not add/remove item effect rows yet, and it does not edit `int_arr_a/b`
rows until those rows are split into separate target-id and value controls.

Category `7` runes need rune-specific display rules. `int_arr_a` is the
weapon-side effect and `int_arr_b` is the armor-side effect. For elemental runes
the high byte maps to element ids such as Fire, Ice, Light, and Shadow; the
same id on armor means the corresponding `Anti-*` value. Rune status chances use
`short_arr_b` raw low-byte values scaled by `1/5` for display. For example,
Shadow Rune III decodes as:

```text
Weapon: Shadow +12, Blind chance 4%
Armor:  Anti-shadow 30, Anti-blind
```

Status Rune II decodes as:

```text
Weapon: Strength +1, Spirit +1, Vitality +1, Speed +1, Sleep chance 4%
Armor:  Strength +1, Spirit +1, Vitality +1, Speed +1, Anti-sleep 20%
```

Blood Rune III decodes as:

```text
Weapon: Vitality +5, Regen +3, Bleeding chance 6%
Armor:  Vitality +5, Regen +3, Anti-bleeding 30%
```

Rune of Nyr decodes as:

```text
Weapon: Fire damage +3, Spirit/INT +1, Max HP +5, Weak chance 2%
Armor:  Anti-fire damage 8, Spirit/INT +1, Max HP +5, Anti-weak 30%
```

In the item detail UI, category-7 runes are split into `Common Effect`,
`Weapon Effect`, and `Armor Effect` sections. The original game also separates
rune effects into Weapon and Armor panels in the item screen. Shared packed
stats are shown once under common effects; weapon-only damage/status chance and
armor-only anti-element/status resistance rows stay in their respective
sections.

## Confirmed Resistances From Screenshots

From the stat screen:

```text
Lara:
  Confuse resistance 100

Vince:
  Confuse resistance 100

Romus:
  Blood resistance 100
  Poison resistance 100
  Bleed resistance 100
  Confuse resistance 100

Manok:
  Blood resistance 100
  Poison resistance 100
  Bleed resistance 100
  Confuse resistance 100
```

Important correction:

```text
The ?! icon is Confuse / Confusion resistance, not Fear resistance.
```

Romus and Manok are skeletons, so they have no blood to suck and are immune to
poison and bleeding.

## Resistance Overflow Bug

Hero status resistances are accumulated in a Java `byte[]` before they are
clamped to the display/gameplay range.

The relevant runtime pattern is:

```text
resistance[id] = (byte)(resistance[id] + bonus)

if resistance[id] < 0:
  resistance[id] = 0
else if resistance[id] > 100:
  resistance[id] = 100
```

Because the cast to `byte` happens before clamping, values above `127` overflow
into the negative byte range. The later clamp then treats the negative value as
bad and sets it to `0`.

Examples:

```text
100 + 15  = 115  -> valid byte -> clamped to 100
100 + 27  = 127  -> valid byte -> clamped to 100
100 + 28  = 128  -> byte overflow to -128 -> clamped to 0
100 + 100 = 200  -> byte overflow to -56  -> clamped to 0
```

This is a blatant gameplay bug. A hero with natural `100` resistance can become
vulnerable if equipment adds enough extra resistance of the same type.

Concrete example:

```text
Romus has 100 Bleeding resistance.
If equipment adds enough Bleeding resistance to push the temporary sum to 128+,
the byte overflows and the final displayed/runtime resistance becomes 0.
Romus can then bleed.
```

Practical editor rule:

```text
Never let a hero's combined status resistance exceed 127 before clamping.
If the hero already has 100 resistance, avoid adding any extra resistance of the
same type unless intentionally testing the overflow bug.
```


### Bytecode Patch Option

The editor now has a `Patch resistance overflow` checkbox.

Behavior:

```text
Input JAR already patched:
  checkbox is checked and disabled

Input JAR has the known vanilla bytecode pattern and semantic clamp shape:
  checkbox is enabled and unchecked by default

Input JAR has an unknown g.class layout:
  checkbox is disabled
```

When enabled and checked during `Build Full Patched JAR`, the editor rewrites `g.class`
inside the output JAR. In JavaFX, this is part of the combined patch build, so
class, `game.dat`, and `item.dat` changes are written together instead of through
separate output passes. The current implementation is a hybrid patcher for hero
class `g` / renamed `Hero`: it uses JDK 25's Class-File API to confirm exactly
one semantic match in `g.b()V`, then applies a byte-minimal raw replacement
against the confirmed byte pattern.

Original clamp behavior:

```text
if resistanceByte < 0:
  resistanceByte = 0
else if resistanceByte > 100:
  resistanceByte = 100
```

Patched behavior for overflowed negative bytes:

```text
if resistanceByte < 0:
  resistanceByte = 100
else if resistanceByte > 100:
  resistanceByte = 100
```

This means common overflows such as `100 + 100 -> byte -56` become `100` instead
of `0`. The patch is intentionally narrow: it only applies when exactly one known
vanilla byte pattern is found and Class-File API confirms the same resistance
clamp shape, and it refuses unknown class layouts.

Confirmed patched-game test:

```text
Romus with 130 Bleeding resistance displays as 100 in game.
```

The original Swing-built patched JAR confirmed the runtime behavior. JavaFX uses
the same `EditorPatchService` and `ResistanceOverflowClassPatcher`, so the FX
side is considered complete as long as it continues to call that shared path.

Future improvement: a full Class-File API transform prototype worked and
preserved the old J2ME class version `45.3`, but the hybrid detector plus raw
writer currently keeps the patch smaller and easier to audit. Prefer a full
Class-File API transform if more class patches are added.

Current bytecode-tooling recommendation:

```text
Default: JDK 25 Class-File API for structural class patches.
Fallback: ASM only if a concrete instruction rewrite is too awkward with the
          standard API.
Avoid for now: Byte Buddy, because the editor patches JAR entries offline rather
               than generating or instrumenting classes at runtime.
Possible but not preferred: BCEL.
```

## EXP Curve and Leveling Notes

All heroes share the same EXP threshold curve.

Confirmed first threshold:

```text
Level 1 -> 2 requires 41 EXP
```

The per-level increment follows:

```text
EXP needed from level N to N+1 = 26 * N + 15
```

Confirmed cumulative thresholds:

```text
Level  2:    41
Level  3:   108
Level  4:   201
Level  5:   320
Level 10:  1305
Level 20:  5225
Level 30: 11745
```

There is also a likely EXP award bug in the battle-result code: EXP is awarded
in chunks of 4, but when the remaining EXP is `1..3`, the code appears to award
the remaining Filar value instead of the remaining EXP value. This can make the
final small EXP remainder after a battle incorrect.

## Monster Editor Notes

The monster table now uses the original monster class (`b`) through reflection
to show names and scalar attributes. Names are read-only.

Current safe writable fields:

```text
EXP         0..4095
Filar       0..4095
Soul Restore 0..127
Effect ID   0..255
STR-like    0..127
SPI-like    0..127
VIT-like    0..127
SPD-like    0..127
```

`EXP`, `Filar`, and `Soul Restore` are the fixed packed scalar block immediately
after the monster name in `game.dat`. `EXP` and `Filar` are 12-bit values:

```text
byte0                  = EXP high 8 bits
byte1 high nibble      = EXP low 4 bits
byte1 low nibble       = Filar high 4 bits
byte2                  = Filar low 8 bits
byte3                  = Soul Restore
```

The editor reloads these three values from the raw `game.dat` bytes instead of
trusting reflected monster fields, because the reflected runtime values can
carry signed/unsigned artifacts after patching. Regression coverage confirms a
patched monster with `EXP=1200`, `Filar=1000`, and `Soul Restore=25` reloads
with those exact values.

Battle-result bytecode confirms the reward totals:

```text
resultExperience += monster.EXP
resultFilar      += monster.Filar
```

In-game test: four `Junger Gabolg` units configured to `EXP=120`,
`Filar=120`, and `Soul Restore=25` awarded `480 EXP` and `480 Filar`. This
confirms `Soul Restore` is separate from the displayed battle-result EXP/Filar
totals.

Confirmed gameplay meaning: `Soul Restore` is the amount of Soul restored to
Romus and Manok when the enemy is slain. Their resource is Soul, and it can be
replenished through enemy deaths. In-game testing confirmed the value increases
Romus's Soul both in battle and after battle. It is not yet confirmed whether
the same slain-enemy value is split or duplicated when Manok is also below full
Soul.

`Effect ID` is the last byte of the 13-byte monster tail and is reflected as
monster field ordinal `16`. It is used by monster death-side effect handling,
not by the battle-result EXP/Filar totals.

The four core monster stat bytes are 7-bit values packed across bytes `+4..+7`
of the 13-byte monster tail:

```text
STR-like = tail[4] bits 7..1
SPI-like = tail[4] bit 0 + tail[5] bits 7..2
VIT-like = tail[5] bits 1..0 + tail[6] bits 7..3
SPD-like = tail[6] bits 2..0 + tail[7] bits 7..4
```

The editor now writes only those bit ranges and preserves the neighboring packed
fields in the same bytes.

Confirmed in-game test:

```text
Ryan (1), STR/SPI/VIT/SPD-like = 1/1/1/1
Observed HP = 12
Formula HP = ((1 * 70 + 1 * 30) * 12 / 100) = 12
```

This confirms that at least the STR/VIT core-stat write path and HP preview
formula match runtime behavior.

The editor also shows monster stat previews:

```text
Base HP
Base Resource
Base Attack
Base Defense
Base Move
STR-like / SPI-like / VIT-like / SPD-like
Hit % / Crit-Dmg % / Evade-Guard %
Packed Chance
Packed Tail A / Packed Tail B
Actions / Effects / Drops counts
```

The visible monster HP is derived at runtime from the core stat bytes, not taken
directly from the packed `short e` / `short f` tail fields. For example,
`Schlange (3)` reflects STR-like `4` and VIT-like `4`, so:

```text
Base HP = ((VIT * 70 + STR * 30) * 12 / 100)
        = ((4 * 70 + 4 * 30) * 12 / 100)
        = 48
```

This matches the Rescue Meryo snake's observed 48 HP. Earlier editor wording
that displayed packed tail values such as `1356` as HP was misleading; those
values are now shown only as packed diagnostics. The 13-byte tail is still
packed enough that direct HP/combat writes should not be expanded until the bit
layout is confirmed against bytecode and patched JAR tests.

The editor now exposes existing fixed-width monster array entries in a detail
table:

```text
Effects              three-byte packed entries
Resistance/Status A  two-byte packed entries
Resistance/Status B  two-byte packed entries
Byte Array           one-byte packed entries
Drops                two-byte packed entries
```

These entries are edited in place only. The editor intentionally does not
add/remove entries yet because changing variable-length counts shifts every
following packed offset in `game.dat`. The two short arrays are still labeled
conservatively as `Resistance/Status A/B` until their exact gameplay semantics
are confirmed against bytecode and in-game tests.

## Editor Notes

The hero editor should expose editable natural growth fields:

```text
Strength Start / Lv99 Target / Growth Curve
Spirit Start / Lv99 Target / Growth Curve
Vitality Start / Lv99 Target / Growth Curve
Speed Start / Lv99 Target / Growth Curve
Level Cap
```

It should also show read-only derived base values:

```text
Base HP
Base Resource
Base Attack
Base Defense
Base Move
Base Regen
```

The current editor also exposes editable base critical hit fields:

```text
Base Crit %
Base Crit Dmg %
```

These are the two bytes of the packed hero crit field, previously mistaken for
HP/resource flat seeds. Base Evasion is shown as read-only because it is
hardcoded as `5 + Reflexes bonus`, not stored per hero in `game.dat`.

The current editor also shows read-only cap previews:

```text
STR @ Cap
SPI @ Cap
VIT @ Cap
SPD @ Cap
```

These preview the grown core stats at `Level Cap` using the exact integer growth
formula above. They are now the preferred way to tune hero stats instead of
trying to reason from `Lv99 Target` alone.

Variable-length hero arrays such as starting talents, starting equipment, and
bonus/resistance lists should be handled separately and conservatively.
