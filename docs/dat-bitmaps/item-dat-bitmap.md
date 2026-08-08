# item.dat Bitmap

Status: living document. `item.dat` is a counted sequence of packed,
variable-length item records. The editor currently writes only top-level fields
and existing fixed-width decoded effect rows.

## Global Layout

| Offset | Field | Confidence | Writable | Source |
|---:|---|---|---|---|
| `0` | Item count | Confirmed | No | `ItemDatPatcher.parseItemOffsets` |
| dynamic | Item records | Confirmed | Partial | `ItemDatPatcher.parseItem` |

## Item Record Header

Parser-derived base: the cursor passed to `ItemDatPatcher.parseItem`.

| Relative byte/bit | Field | Confidence | Writable | Notes |
|---|---|---|---|---|
| `+1` high nibble | Category | Confirmed | No | Used to select record shape. |
| `+2` | Name length | Confirmed | No | Name bytes immediately follow. |
| after name | Price | Confirmed | Yes | Absent for categories `8` and `12`; written as unsigned 16-bit. |
| after price, low 7 bits | Icon | Confirmed | Partial | Absent for categories `8` and `12`; high bit is preserved. |
| allowed-classes block | Allowed hero/class data | Probable | No | Skipped by `skipAllowedClasses`; not edited. |

## Price And Icon

Source: `ItemDatPatcher.parsePriceAndIcon`.

| Parser-derived offset | Field | Range | Confidence | Writable |
|---|---|---:|---|---|
| `ItemOffsets.priceOffset` | Price | `0..65535` | Confirmed | Yes |
| `ItemOffsets.iconOffset` bits 6..0 | Icon | `0..127` | Confirmed | Partial |
| `ItemOffsets.iconOffset` bit 7 | Preserved flag | unknown | Unknown | No |

## Equipment/Effect Field Flags

Equipment-like categories `1..7` have an effect flag byte parsed by
`ItemDatPatcher.parseEquipmentFields`.

| Flag | Field group | Entry width | Confidence | Writable | Notes |
|---:|---|---:|---|---|---|
| `0x10` | `short_c` packed stat | 2 bytes | Confirmed | Partial | Editor exposes high and low bytes separately. |
| `0x08` | `short_d` packed stat | 2 bytes | Confirmed | Partial | Editor exposes high and low bytes separately. |
| `0x04` | `short_e` packed stat | 2 bytes | Confirmed | Partial | Editor exposes high and low bytes separately. |
| `0x02` | `short_f` packed stat | 2 bytes | Confirmed | Partial | Editor exposes high and low bytes separately. |
| `0x01` | `byte_d` scalar bonus | 1 byte | Confirmed | Yes | Equipment bonus overwrite quirk is patched in `g.class`, not here. |
| `0x80` | `int_arr_a`, and rune `int_arr_b` | 3-byte entries | Confirmed for weapon damage and armor value | Partial | Category-3 weapon entries write byte 0 as the selected damage kind (`0..5`) and bytes 1..2 as the 16-bit damage value. Category-2 armor entries preserve byte 0 and edit bytes 1..2 through the `0..255` Physical absorption control. Rune arrays remain read-only. |
| `0x40` | `short_arr_a` | 2-byte entries | Confirmed | Partial | Existing entries only; high byte target/status preserved, low byte value written. |
| `0x20` | `short_arr_b` | 2-byte entries | Confirmed | Partial | Existing entries only; high byte target/status preserved, low byte value written. |

## Packed Stat Shorts

Parser-derived keys: `short_c:hi`, `short_c:lo`, `short_d:hi`,
`short_d:lo`, `short_e:hi`, `short_e:lo`, `short_f:hi`, `short_f:lo`.

```text
byte 0  high packed id/value byte, writable as a byte through the explicit row
byte 1  low packed value byte, writable as a byte through the explicit row
```

| Key pattern | Range | Confidence | Writable | Notes |
|---|---:|---|---|---|
| `short_*:hi` | `0..255` | Confirmed | Yes | Existing field only. |
| `short_*:lo` | `0..255` | Confirmed | Yes | Existing field only. |

The editor preserves the existence of the field and does not add or remove flag
groups.

## Short Effect Arrays

Parser-derived keys: `short_arr_a[index]`, `short_arr_b[index]`.

```text
byte 0  target/status id, preserved
byte 1  value byte, writable 0..255
```

| Key pattern | Range | Confidence | Writable | Notes |
|---|---:|---|---|---|
| `short_arr_a[index]` | `0..255` value byte | Confirmed | Partial | Existing entries only. |
| `short_arr_b[index]` | `0..255` value byte | Confirmed | Partial | Existing entries only. |

## Category 5 Consumable Tail

Source: `ItemDatPatcher.parseConsumableTail`.

| Flag | Parser-derived offset/key | Field | Range | Confidence | Writable | Notes |
|---:|---|---|---:|---|---|---|
| `0x04` | `ItemOffsets.hpRestoreOffset`, `short_g` | HP effect/restore | `0..65535` | Confirmed | Yes | Written as full unsigned 16-bit. |
| `0x02` | `ItemOffsets.resourceRestoreOffset`, `short_h` | Resource effect/restore | `0..65535` | Confirmed | Yes | Blood/Soul restore depending on user. |
| `0x01` | unnamed byte | preserved category-5 byte | unknown | Unknown | No | Parser skips it. |
| no `0x08` | `byte_q` | Use visual/effect id | `0..255` | Confirmed | Yes | Only present when bit `0x08` is clear. |

Confirmed example: Vampire Stone uses `short_g = 999` and `short_h = 999`.

## Category-Specific Tails

| Category | Parser behavior | Confidence | Writable | Notes |
|---:|---|---|---|---|
| `1`, `2`, `4` | skip 1 byte | Probable | No | Tail semantics not yet exposed. |
| `3` | `skipWeaponTail` | Probable | No | Uses local flags; weapon tail not written. |
| `5` | `parseConsumableTail` | Confirmed | Partial | See category-5 table. |
| `8` | `skipTextTail` | Confirmed | No | A 16-bit big-endian text length followed by encoded quest-instruction bytes. The editor decodes and shows this second item byte-array read-only; it never writes it. |
| `9`, `10` | skip 1 byte | Confirmed | No | Linked skill id/level are currently read-only item fields. |
| `12` | `skipQuestTail` | Probable | No | Quest item tail not written. |

## Equipment Rune Slots

The original parser stores the rune-slot count in runtime item field `k.g`.
The equipment menu uses that field as its loop bound when drawing rune positions.

| Category | Packed location | Byte/bit layout | Range | Confidence | Writable |
|---:|---|---|---:|---|---|
| `2`, subtype `1` main armor | Category-tail parser position `+1` | bits `3..0` = rune-slot count; bits `7..4` preserved | editor `0..4` | Confirmed | Yes, low nibble only |
| `3` weapon | Weapon-tail parser position `+3` (`byte_f`) | bits `7..5` = rune-slot count; bits `4` and `3..0` are separate weapon data | editor `0..4` | Confirmed | Yes, bits `7..5` only |

Confirmed fixture examples: Bronze armor has `1`, War plate mail has `2`, Sickle blade has
`1`, Foryn-crossbow has `2`, and Syr spear has `2`. The editor displays this value as
editable from `0..4` and preserves all neighboring packed bits.

Rune attachment applies only to weapons and category-2 subtype-1 main armor. The same tail
byte is present for other equipment categories, but it is preserved as unknown data and is not
displayed or interpreted as a rune count.

## Current Write Policy

The editor may write:

- top-level `Price`;
- top-level `Icon` low 7 bits;
- existing packed stat high/low bytes;
- existing category-3 weapon `int_arr_a` damage values;
- existing category-2 armor `int_arr_a` Physical absorption values;
- existing `short_arr_a` / `short_arr_b` low value bytes;
- category-5 `short_g` and `short_h` full 16-bit values;
- category-5 `byte_q` when present.

The editor must not:

- add or remove item effect groups;
- change array counts;
- edit `int_arr_b` rune/armor arrays or non-weapon `int_arr_a` rows;
- change category, name, allowed-class blocks, or unknown category tails.
