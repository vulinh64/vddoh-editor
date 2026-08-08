# m.dat Children of Apocalypse Shops

Status: targeted map-script record map. This document covers only the confirmed
Children of Apocalypse consumable-shop events; it is not a general `m.dat`
format specification.

## Shop Event

Each confirmed shop is a length-prefixed event:

```text
byte 0       event payload length, including opcode/header/item IDs
byte 1       0x10 (shop opcode)
byte 2..3    0x30 0x07 (shop header)
byte 4..end  one unsigned item ID per stock row
```

The original runtime constructs its shop array by skipping the three-byte
payload header and resolving every remaining byte through `k.a[itemId]`.

| Field | Range | Confidence | Writable | Notes |
|---|---:|---|---|---|
| event length | `4..255` | Confirmed | Yes | Rebuilt when stock is added or deleted. |
| opcode/header | `10 30 07` | Confirmed | No | Required exact shape; preserved by the patcher. |
| stock item ID | confirmed Children catalogue | Confirmed | Yes | The tab supports IDs `6,7,8,9,10,13,14,24,25,26,28`: Blood/Life, Troll, Might, and Soul consumables. |

`MdatShopService` applies edits in descending source-offset order, so an
add/delete that changes one event's length cannot invalidate the location of an
earlier edit. It refuses any non-matching event layout.

Location labels are conservative when multiple script copies share the same
stock: the UI uses a source `m.dat` offset in that case rather than guessing the
town. The Gadanis Might-potion, pre-Mysterious-Potions, Lord Craft, and
Mysterious-Caves stock shapes receive explicit labels.
