# JavaFX Migration Proposal

## Purpose

The current VDDOH Data Editor has outgrown a basic Swing tab-and-table UI.
The editor now supports multiple editing domains, linked navigation, generated
patch output, reverse-engineered previews, and optional bytecode patching. JavaFX
is a strong next UI step because it can keep the existing data and patching core
while giving the editor a modern desktop workflow with webapp-like interaction
patterns: CSS styling, master-detail routing, breadcrumbs, validation, richer
controls, and collapsible panels.

This proposal focuses on product and UX direction. It does not require replacing
the reverse-engineering, parser, or patcher code at the same time.

`Webapp-like` means the editor should borrow the interaction quality of a good
web application while remaining a local desktop application. This proposal is
not recommending a browser-based app or HTML UI. JavaFX `WebView` should only be
considered later if a specific feature truly needs embedded HTML content.

## Current Editor Context

The Swing editor currently provides:

- Top-level tabs for Skills, Talents, Heroes, Items, Monsters, and Statuses.
- Input and output JAR controls, including a `View` button for generated output.
- A `Patch resistance overflow` checkbox that is enabled, checked, or disabled
  based on the detected `g.class` patch state.
- Search support for major tables.
- Item subviews for Equipment, Consumables, and Other.
- Equipment filtering by Weapon, Head Armor, Necklace, Ring, Main Armor, Boot,
  and Rune/Modifier.
- Item effect details, including category-5 anytime consumables and category-9
  combat-only skill-backed consumables.
- Double-click navigation from an item `Linked skill` effect row to the Skills
  tab with the selected item name applied as the skill search text.
- Editable monster fields for confirmed safe values such as EXP, Filar, Death
  Value, Effect ID, and core STR/SPI/VIT/SPD-like bytes.
- Read-only preview columns where reverse engineering is not yet safe enough for
  writing, especially monster drops, actions, effects, and packed diagnostics.

The main UX issue is not lack of functionality. It is density. Many features are
visible only as table columns or secondary rows, and the user has to mentally
connect related fields across tabs and detail tables. JavaFX should make those
relationships first-class.

## Product Goals

1. Preserve the current editor's safety model.
   Known writable fields remain editable. Unknown or risky packed data remains
   read-only until confirmed.

2. Make relationships visible.
   Items should reveal linked skills, status effects, stat boosts, equipment
   slots, restrictions, and consumable behavior without forcing the user to scan
   raw columns.

3. Improve editing confidence.
   Validation, max values, warnings, and patch summaries should be visible near
   the edited field, not only implied by table constraints.

4. Support reverse-engineering work.
   The UI should expose raw field names, byte offsets, and confidence levels in
   optional advanced panes without overwhelming normal editing.

5. Keep the data layer reusable.
   JavaFX should wrap existing row, patch request, patcher, and loader classes
   through view models instead of rewriting the whole editor at once.

## Non-Goals

- Do not rewrite `GameData`, patchers, the bytecode patcher, the Java ME shim,
  or packed-data decoders during the initial migration.
- Do not expand unsafe field editing just because JavaFX can display the data
  more clearly.
- Do not remove Swing until the JavaFX editor reaches feature parity for current
  load, edit, build, and patch workflows.

## Why JavaFX

JavaFX gives us practical UI features that are awkward in Swing:

- `SplitPane`, `Accordion`, `TitledPane`, and nested layouts for expandable
  editors.
- Rich `TableView` and `TreeTableView` cells with icons, badges, validation
  states, and inline editors.
- CSS-based styling for a cleaner visual system.
- Better master-detail flows: a list or table on the left, detailed editor on
  the right.
- Better keyboard, focus, and selection handling for dense editing workflows.
- Charts, badges, pills, and structured panes for previews without hand-drawing
  Swing renderers.
- Easier future additions such as dark mode, responsive resizing, and richer
  dialogs.

The editor can become closer to a local webapp while remaining a desktop tool.

## Proposed UX Model

### Application Shell

Use a three-part layout:

- Top command bar: input JAR, output JAR, load, build, view output, and patch
  status.
- Left navigation rail or tab strip: Skills, Talents, Heroes, Items, Monsters,
  Statuses, and future Class Patches.
- Main workspace: each section owns a search/filter toolbar, master list, and
  detail panel.

The resistance overflow control should move from a bottom checkbox into a
dedicated build/patch summary area. It can still be shown prominently because it
changes the generated JAR, but JavaFX can present its state more clearly:

- `Vanilla: patch available`
- `Already patched: enabled in source JAR`
- `Unknown layout: patch unavailable`

### Master-Detail Editing

Each major data domain should use a master-detail pattern:

- Master: searchable/filterable table or list.
- Detail: selected row editor grouped into sections.
- Preview: derived values and decoded effect summaries.
- Advanced: raw fields, offsets, and reverse-engineering diagnostics.

This is especially valuable for Items and Monsters, where a single row now
contains many unrelated concepts.

### Collapsible And Expandable Panels

Use `TitledPane` or `Accordion` for grouped detail sections:

- Identity and category.
- Safe editable fields.
- Derived previews.
- Effects and linked records.
- Raw diagnostics.
- Patch impact summary.

Panels should remember expanded/collapsed state during the session so repeated
editing feels stable.

## Section-Specific UX Improvements

### Items

Items are the strongest candidate for the first JavaFX migration slice.

Current item data already distinguishes:

- Equipment.
- Anytime/direct consumables.
- Combat-only skill-backed consumables.
- Other items.

JavaFX should make that split more explicit:

- A left-side item category filter with counts.
- Equipment slot filter chips for Weapon, Head Armor, Necklace, Ring, Main
  Armor, Boot, and Rune/Modifier.
- A consumable mode filter for Anytime and Combat-only.
- An item detail pane with collapsible groups:
  - Basic: name, ID, price, icon, category, subtype.
  - Equipment: allowed heroes, slot, attack/defense, HP/resource bonuses,
    stat boosts, resistances, on-hit effects.
  - Consumable: use timing, HP/resource effects, status apply/remove behavior,
    use visual/effect ID.
  - Linked Skill: skill name, skill id, level/variant, and a button to jump to
    the Skills view with search pre-filled.
  - Raw: original field names such as `short_g`, `byte_o`, `byte_p`, and
    offsets where available.

Linked skill navigation should become a normal button or hyperlink-style control
instead of relying only on double-clicking a table row.

### Monsters

The monster editor is moving from inspection toward safe editing. JavaFX should
help separate confirmed fields from unknown packed data:

- Master list with search, quest/context hints when known, EXP/Filar preview,
  and safety badges.
- Detail sections:
  - Rewards: EXP, Filar, Death Value.
  - Core Stats: STR/SPI/VIT/SPD-like bytes with max values and derived previews.
  - Combat Preview: HP, resource, attack, defense, move, hit, crit, evade/guard.
  - Effects: effect ID and known decoded meaning when confirmed.
  - Read-only Unknowns: drops, actions, effect arrays, packed tail diagnostics.

The detail panel should show why a field is read-only: `Variable-length format
not yet mapped`, `Packed neighboring bits`, or `Runtime behavior not confirmed`.
That turns editor limitations into useful reverse-engineering notes.

### Skills

Skills should gain a stronger detail view:

- Master list with search and element/status filters.
- Per-level expandable panels for cost, damage, chance, area/range, animation,
  and status arrays.
- Inline warnings when edited values exceed confirmed ranges.
- Backlinks from Items that dispatch through linked skills.

When navigation comes from an item, the Skills view should preserve that context,
for example: `Filtered by item: Troll Elixir`.

### Talents

Talent editing should benefit from grouped sections:

- Group talents.
- Passive hero talents.
- Spell unlock links.
- Related hero, skill, or stat effect previews.

Potential future improvement: show talent trees or grouped cards if the data
supports stable relationships.

### Heroes

Heroes already have natural stat growth, level cap, crit values, and previews.
JavaFX can make this easier to tune:

- Sliders/spinners for Start, Lv99 Target, Growth Curve, and Level Cap.
- Compact preview cards for HP, resource, attack, defense, move, and regen.
- Small line charts for growth curves from level 1 to cap.
- Clear separation between natural stats and equipment/talent/status bonuses.

### Statuses

Statuses can remain table-first initially, but JavaFX detail panes can clarify:

- Display name and icon.
- Duration/modifier values.
- Known status resistance relationships.
- Referencing skills/items when those links are available.

## Filtering And Navigation

JavaFX should turn the current search boxes into richer navigation:

- Global quick search across Skills, Talents, Heroes, Items, Monsters, and
  Statuses.
- Section-local filters with chips, dropdowns, and saved last-used filters.
- Breadcrumbs when navigating by relationship, such as Item -> Linked Skill.
- Back/forward navigation within the editor session.
- Counts on filtered views, such as `Consumables 12 / 42`.
- Keyboard shortcuts for search focus, build, reset edits, and jump-to-result.

The goal is to make the editor feel like a domain browser, not only a set of
independent tables.

## Validation And Safety UX

JavaFX should make patch safety visible before build:

- Inline validation messages beside edited fields.
- Value range hints, such as `0..127`, `0..255`, or `0..4095`.
- Warnings for suspicious gameplay states, such as resistance overflow risk.
- Dirty-state badges per section.
- A build preview dialog that lists changed records and generated entries.
- Clear disabled states for fields that are known but intentionally read-only.
- Confirmation only for risky operations, not every normal edit.

The editor should distinguish:

- Editable and confirmed.
- Preview-only and confirmed.
- Raw diagnostic.
- Unknown or unsafe to edit.

## Styling Direction

Use CSS to give the app a restrained, tool-focused visual design:

- Compact spacing and readable tables.
- Muted section backgrounds.
- Clear badges for editable, read-only, changed, linked, and risky fields.
- Consistent iconography for navigation, warnings, reset, view output, and build.
- Optional dark theme later, but not required for the first migration.

This should feel like a modern local developer tool, not a marketing page.

## Migration Strategy

### Phase 1: JavaFX Shell And Item Slice

Create a JavaFX entry point beside the Swing app. Keep Swing runnable until the
JavaFX app reaches feature parity.

First JavaFX target:

- Load original JAR.
- Display Items with Equipment, Consumables, and Other filters.
- Show item detail panes and linked skill navigation.
- Reuse existing `GameData`, item rows, and patching classes.

This phase validates the UI architecture without risking the whole editor.

Acceptance criteria:

- JavaFX app starts beside the Swing app.
- JavaFX can load the same vanilla or patched JAR path used by the Swing editor.
- Items display as Equipment, Consumables, and Other.
- Equipment supports slot filters.
- Consumables support mode filters for anytime/direct and combat-only items.
- Item details show safe fields, decoded effects, and raw diagnostics.
- Linked skill action navigates to the Skills view, or records the intended
  navigation state if Skills has not been migrated yet.
- Swing app still builds and runs.

### Phase 2: Build Controls And Patch Summary

Add:

- Output JAR selector.
- Build Patched JAR.
- View output.
- Reset edits.
- Resistance overflow patch state panel.
- Patch summary before writing.

This makes JavaFX useful as a real editor, not only a browser.

### Phase 3: Monsters And Skills

Migrate:

- Monster master-detail editor.
- Skill master-detail editor.
- Item-to-skill navigation with retained context.

These areas benefit most from expandable panels and linked navigation.

### Phase 4: Heroes, Talents, Statuses

Migrate the remaining domains:

- Hero stat growth editor with previews.
- Talent grouping and skill unlock links.
- Status editor and reverse-link references.

### Phase 5: Swing Retirement

Remove Swing UI only after JavaFX supports:

- JAR load.
- All current tables.
- All current safe edits.
- Patch generation.
- Resistance overflow detection and patching.
- Output viewing.
- Search and navigation workflows.

## Technical Boundaries

The JavaFX migration should not rewrite core logic unnecessarily.

Keep:

- `GameData` loading and reflection logic.
- Existing patcher classes.
- Existing row and patch DTOs where they are still useful.
- Java ME shim and Maven self-contained packaging behavior.
- Conservative bytecode patch refusal behavior.

Add:

- JavaFX application entry point.
- View models/adapters for JavaFX properties.
- UI-specific controllers and components.
- CSS resources.
- Navigation state service for cross-section jumps.

Avoid:

- Re-decoding game data in the UI layer.
- Making unsafe fields editable because the UI can display them nicely.
- Removing Swing before feature parity.

## Data/UI Boundary

- Existing row records, DTOs, patch requests, and patchers should remain
  framework-neutral.
- JavaFX `Property` classes should live only in JavaFX view-models and adapters.
- Swing `TableModel` classes should not be reused as JavaFX models. JavaFX views
  should receive their own view-model layer over the shared data.
- Patch requests should still be generated through the existing conservative
  patch pipeline, so JavaFX changes do not bypass validation or known-offset
  writing rules.

## Packaging Considerations

JavaFX is not bundled with the JDK, so packaging needs deliberate handling.

Short term:

- Add OpenJFX Maven dependencies.
- Keep Maven builds reproducible on JDK 25.
- Maven can run JavaFX in development, but a plain shaded JAR may not be enough
  for JavaFX native modules.
- Continue producing a runnable developer launcher path.
- Keep the current Swing executable JAR workflow available until JavaFX
  packaging is proven.

Medium term:

- Consider Maven profiles for Windows/Linux packaging.
- Prefer `jlink` or `jpackage` for a true self-contained runtime image.
- Keep the current self-contained Java ME API behavior.

Long term:

- Produce platform-specific app bundles if the editor becomes a regular tool
  rather than a development artifact.

The first migration should not block on perfect packaging, but it must not
accidentally regress the current executable-JAR workflow.

## Validation And Testing Checklist

Use this checklist while the JavaFX migration is in progress:

- Run `mvnw.cmd -q -DskipTests package`.
- Run the Swing editor.
- Run the JavaFX editor.
- Load vanilla `vddoh.jar`.
- Verify category-5 anytime/direct consumables.
- Verify category-9 combat-only skill-backed consumables.
- Verify linked skill navigation.
- Build a patched JAR.
- Reload the patched JAR.
- Confirm the resistance overflow patch state is unchanged and regression-free.

## Risks

- JavaFX dependencies may complicate the current self-contained JAR.
- Table editing behavior differs from Swing and needs careful validation.
- A full rewrite could stall reverse-engineering work if attempted in one pass.
- JavaFX property wrappers can leak UI concerns into data records if adapters are
  not kept separate.
- Some workflows rely on current Swing table model behavior and must be tested
  before Swing is removed.

## Recommendation

Proceed with an incremental JavaFX migration.

Start with a JavaFX shell and the Items workflow because it already has the most
webapp-like behavior: category filters, linked skills, detail effects, equipment
slots, consumable modes, and raw diagnostics. Keep the Swing editor available
throughout the migration. Once Items prove the architecture, move build controls,
Monsters, and Skills next.

This path gives the editor a modern UX without risking the confirmed patching
and reverse-engineering work that already exists.
