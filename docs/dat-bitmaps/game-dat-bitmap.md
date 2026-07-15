# game.dat Bitmap

Status: living document. Only confirmed editor write surfaces are listed as
writable. Offsets are parser-derived because `game.dat` is a packed,
variable-length stream.

## Global Layout

| Region | Offset | Count | Confidence | Writable | Source |
|---|---:|---:|---|---|---|
| Damage groups / prelude | `13 + u16(data, 11) * 5`, then `skipDamageGroups` | dynamic | Probable | No | `GameDatSkillPatcher.skillTableOffset`, `GameDatStatusPatcher.parseStatusOffsets` |
| Statuses | after damage groups | reflected count byte | Confirmed | Partial | `GameDatStatusPatcher` |
| Skills | after statuses | reflected count byte | Confirmed | Partial | `GameDatSkillPatcher` |
| Monsters | `EditorSupport.monsterStartOffset(data)` | count byte | Confirmed | Partial | `GameDatMonsterPatcher` |
| Heroes | `EditorSupport.heroStartOffset(data)` | count byte | Confirmed | Partial | `GameDatHeroPatcher` |
| Group talents | after heroes | count byte | Confirmed | Partial | `GameDatTalentPatcher` |
| Hero talents | after group talents | count byte | Confirmed | Partial | `GameDatTalentPatcher` |

## Skills

Base offset: computed by `GameDatSkillPatcher.skillTableOffset(data)`.

### Skill Record

| Relative layout | Field | Range | Confidence | Writable | Notes |
|---|---|---:|---|---|---|
| `+0` low 5 bits | Name length | `0..31` | Confirmed | No | Used only to skip the name. |
| after name | Header | bitfield | Confirmed | No | High two bits encode level count as `((header >> 6) & 3) + 1`; low bits feed inherited flags. |
| parsed base level | Level 1 cost | `0..255` | Confirmed | Yes | `LevelOffsets.costOffset`, written by `writeLevelCost`. |
| parsed base level damage array | Damage row value bytes | `0..65535` | Confirmed | Yes | Level 1 damage rows are 3 bytes each; byte 0 target/type is preserved, bytes 1..2 are written. |
| parsed base level status array | Status chance value byte | signed chance | Confirmed | Yes | Level 1 status rows are 2 bytes each; byte 0 status id is preserved, byte 1 is written via `encodeSignedChance`. |
| parsed override level | Override cost byte | `0..255` | Confirmed | Yes | Present only when override flags expose a cost. |
| parsed override level damage array | Damage row value bytes | `0..65535` | Confirmed | Yes | Override damage rows store only the 2-byte value. |
| parsed override level status array | Status chance value byte | signed chance | Confirmed | Yes | Override status rows store only the value byte. |

### Skill Damage Row

```text
Level 1:
  byte 0      target/type id, preserved
  bytes 1..2 unsigned damage/value, writable 0..65535

Override levels:
  bytes 0..1 unsigned damage/value, writable 0..65535
```

Gameplay note: the data value can be unsigned-short sized, but battle result
display/processing uses the low 10 bits in confirmed paths (`damage & 1023`).
Values above `1023` should be treated as advanced/experimental until the
specific skill path is tested.

### Skill Status Row

```text
Level 1:
  byte 0 status id, preserved
  byte 1 signed chance/value, writable through encodeSignedChance

Override levels:
  byte 0 signed chance/value, writable through encodeSignedChance
```

## Statuses

Base offset: after damage groups, before skills. Source:
`GameDatStatusPatcher.parseStatusOffsets`.

| Parser-derived offset | Field | Range | Confidence | Writable | Notes |
|---|---|---:|---|---|---|
| `StatusOffsets.durationOffset` | Duration | `0..255` | Confirmed | Yes | Present when status flags include `0x40`; patcher writes first byte and preserves the following byte. |
| `StatusOffsets.expireOffset` | Expire chance | signed chance | Confirmed | Yes | Present when flags include `0x20`; written via `encodeSignedChance`. |
| `StatusOffsets.iconOffset` | Icon | `0..255` | Confirmed | Yes | One byte. |
| packed positive/negative modifier tail | Status modifiers | dynamic | Probable | No | Parser skips it through `getPacked`; editor does not write it. |

## Monsters

Base offset: `EditorSupport.monsterStartOffset(data)`. Each monster starts with a
counted name, then fixed header bytes, variable arrays, and a 13-byte tail.

### Fixed Header

Parser-derived base: `MonsterOffsets.fixedOffset`.

| Relative byte/bit | Field | Range | Confidence | Writable | Source |
|---|---|---:|---|---|---|
| `+0` | EXP high 8 bits | `0..4095` total | Confirmed | Partial | `EditorSupport.writeMonsterHeader` |
| `+1` bits 7..4 | EXP low 4 bits | `0..4095` total | Confirmed | Partial | `EditorLoadService.applyRawMonsterHeader` |
| `+1` bits 3..0 | Filar high 4 bits | `0..4095` total | Confirmed | Partial | `EditorSupport.writeMonsterHeader` |
| `+2` | Filar low 8 bits | `0..4095` total | Confirmed | Partial | `EditorLoadService.applyRawMonsterHeader` |
| `+3` | Soul restore on kill | `0..127` editor range | Confirmed | Yes | In-game Romus soul restore test. |

Runtime caveat: these offsets are confirmed correct. Vanilla `j.f(int)` parses
the EXP high byte at `+0` and the Filar low byte at `+2` with signed `baload`
semantics before combining the packed 12-bit values. Values where either byte
is `>= 128` can therefore mis-award in-game unless the optional
`MonsterRewardClassPatcher` is applied. The editor reads and writes the raw
bytes unsigned.

### Variable Arrays

Parser-derived bases: `MonsterOffsets.effectsOffset`, `resistAOffset`,
`resistBOffset`, `bytesDOffset`, `dropsOffset`.

| Array | Entry width | Value range | Confidence | Writable | Notes |
|---|---:|---:|---|---|---|
| `effects[index]` | 3 bytes | `0..0x00ffffff` | Confirmed | Yes | Existing entries only. Count is not edited. |
| `resistA[index]` | 2 bytes | `0..65535` | Confirmed | Yes | Existing entries only; semantics still conservative. |
| `resistB[index]` | 2 bytes | `0..65535` | Confirmed | Yes | Existing entries only; semantics still conservative. |
| `bytesD[index]` | 1 byte | `0..255` | Confirmed | Yes | Existing entries only. |
| `drops[index]` | 2 bytes | `0..65535` | Confirmed | Yes | Existing entries only. |

Do not add/remove entries yet; counts and following offsets are variable-length.

### 13-Byte Tail

Parser-derived base: `MonsterOffsets.tailOffset`.

| Relative byte/bit | Field | Range | Confidence | Writable | Notes |
|---|---|---:|---|---|---|
| `+4` bits 7..1 | STR-like | `0..127` | Confirmed | Partial | `writeMonsterCoreStats`; preserves bit 0. |
| `+4` bit 0 + `+5` bits 7..2 | SPI-like | `0..127` | Confirmed | Partial | Preserves `+5` bits 1..0. |
| `+5` bits 1..0 + `+6` bits 7..3 | VIT-like | `0..127` | Confirmed | Partial | Preserves `+6` bits 2..0. |
| `+6` bits 2..0 + `+7` bits 7..4 | SPD-like | `0..127` | Confirmed | Partial | Preserves `+7` bits 3..0. |
| `+12` | Effect ID | `0..255` | Confirmed | Yes | Death-side/effect byte, not EXP/Filar reward. |
| other tail bits | Packed combat diagnostics | unknown | Unknown | No | Shown read-only only. |

## Heroes

Base offset: `EditorSupport.heroStartOffset(data)`. Source:
`GameDatHeroPatcher.parseHeroOffsets`.

### Natural Stat Curves

Parser-derived base: `HeroOffsets.statOffset`.

```text
bytes +0..+3   curve bytes for STR/SPI/VIT/SPD
bytes +4..+10  packed start/target values for all four stats
```

| Relative byte/bit | Field | Range | Confidence | Writable | Source |
|---|---|---:|---|---|---|
| `+0` | STR curve | `0..255` | Confirmed | Yes | `EditorSupport.writeHeroStats` |
| `+1` | SPI curve | `0..255` | Confirmed | Yes | `EditorSupport.writeHeroStats` |
| `+2` | VIT curve | `0..255` | Confirmed | Yes | `EditorSupport.writeHeroStats` |
| `+3` | SPD curve | `0..255` | Confirmed | Yes | `EditorSupport.writeHeroStats` |
| packed across `+4..+10` | STR seed and target | `0..127` each | Confirmed | Partial | Packed by `StatCurve.packed()` and `writeHeroStats`. |
| packed across `+4..+10` | SPI seed and target | `0..127` each | Confirmed | Partial | Packed by `StatCurve.packed()` and `writeHeroStats`. |
| packed across `+4..+10` | VIT seed and target | `0..127` each | Confirmed | Partial | Packed by `StatCurve.packed()` and `writeHeroStats`. |
| packed across `+4..+10` | SPD seed and target | `0..127` each | Confirmed | Partial | Packed by `StatCurve.packed()` and `writeHeroStats`. |

The editor label should treat `start` as the packed seed, not necessarily the
displayed level-1 value. The runtime stat screen uses `valueAtLevel(1)`.

### Hero Seed/Header Fields

Parser-derived base: `HeroOffsets.seedOffset`.

| Relative byte/bit | Field | Range | Confidence | Writable | Source |
|---|---|---:|---|---|---|
| packed across `+0..+1` | Level cap | `0..127` | Confirmed | Partial | `EditorSupport.writeHeroSeeds` |
| packed across `+1..+3` high nibble | Base crit chance and crit damage | `0..255` each | Confirmed | Partial | Writes crit chance high byte and crit damage low byte; preserves `+3` low nibble. |
| `seedOffset + 3` and following optional bytes | Starting equipment flags/items | dynamic | Probable | No | Parser uses `equipmentFlag`; editor does not write. |
| following variable arrays | talents/equipment/bonus lists | dynamic | Probable | No | Skipped by `GameDatHeroPatcher.getN`. |

## Talents

Base offset: after heroes. There are two counted sections: group talents, then
hero talents. Source: `GameDatTalentPatcher.parseTalentSections`.

| Parser-derived offset | Field | Range | Confidence | Writable | Notes |
|---|---|---:|---|---|---|
| `TalentOffsets.metaOffset` high nibble | Max level minus one | stored `0..3`, editor `1..4` | Confirmed | Partial | Low nibble is hero effect id. |
| `TalentOffsets.metaOffset` low nibble | Hero effect id | `0..15` | Confirmed | Partial | Same byte as max level. |
| `TalentOffsets.amountOffset` high nibble | Amount | `0..15` | Confirmed | Partial | Low nibble contains link flags and is preserved. |
| `TalentOffsets.globalOffset` | Global bonus id | `1..256` display, optional | Confirmed | Yes | Written only when flag exists. |
| `TalentOffsets.skillOffset` | Skill unlock id | `1..256` display, optional | Confirmed | Yes | Written only when flag exists. |
| `TalentOffsets.statusOffset` | Status bonus id | `1..256` display, optional | Confirmed | Yes | Written only when flag exists. |
| `TalentOffsets.resistanceOffset` | Resistance bonus id | `1..256` display, optional | Confirmed | Yes | Written only when flag exists. |

Talent optional links use `checkedTalentLink`, preserving the existing section
shape. The editor does not add missing link fields.
