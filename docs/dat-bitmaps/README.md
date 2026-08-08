# VDDOH DAT Bitmaps

This directory tracks the confirmed byte and bit layouts for VDDOH's packed data
files:

```text
game-dat-bitmap.md
item-dat-bitmap.md
m-dat-shops.md
```

These are living reverse-engineering documents. They should describe the data
format itself, while `docs/REVERSE_ENGINEERING_INDEX.md` remains the map of
classes, tools, and source files.

## Confidence

| Label | Meaning |
|---|---|
| `Confirmed` | Verified by decompiled/bytecode behavior and a patcher test or in-game check. |
| `Probable` | Supported by code inspection but not yet fully patch/test confirmed. |
| `Unknown` | Navigational or preserved bytes only. Do not edit. |

## Writable

| Label | Meaning |
|---|---|
| `Yes` | The editor may write this exact byte or bit range. |
| `Partial` | Only the listed subfield is safe to write; neighboring bits/bytes must be preserved. |
| `No` | Read-only until confirmed. |

## Update Rule

When a new field is confirmed, update the matching bitmap with:

- exact absolute offset, computed-relative offset, or parser-derived offset name;
- byte and bit layout;
- valid range;
- confidence;
- writable status;
- source code pointer or reverse-engineering note.

Do not mark a field writable unless the patcher writes only known offsets/bit
ranges and the behavior has been confirmed by test or in-game verification.
