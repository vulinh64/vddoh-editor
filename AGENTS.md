# AGENTS.md

You are continuing the VDDOH editor/reverse-engineering project.

## First Things To Read

Read these files first, in this order:

1. `PROGRESS.md` - current implementation state and next checklist.
2. `docs/REVERSE_ENGINEERING_INDEX.md` - compact map of useful decompiled classes and data files.
3. `VDDOH-STATS-MECHANISM.md` - confirmed mechanics and field interpretations.
4. `src/main/java/com/vddoh/editor/VddohDataEditor.java` and related `com.vddoh.editor` classes only after reading the targeted notes above.

Do not start by dumping entire decompiled classes into context. They are large and wasteful.

## Build Commands

Preferred:

```cmd
mvnw.cmd -q -DskipTests package
```

Compatibility wrapper that runs the Maven build:

```cmd
build-with-jdk.cmd
```

Run:

```cmd
run-vddoh-editor.cmd
```

## JDK And Bytecode Editing

The user uses JDK 25. For future class/bytecode patches, prefer the standard Class-File API from JEP 484 when possible:

https://openjdk.org/jeps/484

Current bytecode patching for the resistance overflow bug is a raw byte-pattern replacement in `ResistanceOverflowClassPatcher`. Treat it as a safe, narrow first implementation. If expanding class patches, consider replacing or supplementing it with Class-File API based structural transforms.

## Efficient Reverse-Engineering Method

Use targeted searches and summaries instead of reading huge files:

- Search first with `rg -n "pattern" decompiled\\renamed-classes`.
- Use `javap -classpath vddoh.jar -c -p <class>` for exact bytecode snippets.
- Read small slices with PowerShell line ranges only after finding line numbers.
- Prefer existing JSON exports in `decompiled/data-json-reflect/` and `decompiled/data-semantic/` when available.
- Update `docs/REVERSE_ENGINEERING_INDEX.md` whenever a class/field mapping becomes stable.

## Safety Rules

- Do not revert user changes.
- Keep `game.dat` and `item.dat` patching conservative; only write known offsets.
- For class patches, refuse unknown bytecode layouts rather than guessing.
- Preserve original JARs; write patched output to `%USERPROFILE%/.vddoh-editor/dist/` by default.
- Rebuild after Java changes and report whether `mvnw.cmd` or `build-with-jdk.cmd` was used.

## PowerShell Tips And Tricks

This session hit several PowerShell-specific hiccups. Use these rules to avoid repeating them:

- Prefer `rg -n` for searching, then read small slices with `Get-Content` and explicit line ranges.
- When a command fails with Windows sandbox helper errors, rerun the same important read/build command with `sandbox_permissions: require_escalated`.
- Avoid large chained commands with many embedded Java string literals. PowerShell quoting around `"` is easy to break.
- For source edits containing Java quotes, prefer one of these approaches:
  - `apply_patch` if it works in the current sandbox.
  - Small line-based PowerShell edits using `Get-Content` as a list of lines.
  - Here-strings (`@' ... '@`) for larger literal text blocks.
- Do not use Java-style escaped quotes (`\"`) inside normal PowerShell strings unless you really mean to write a backslash into the file.
- Watch for accidentally writing literal `` `r`n`` text into `.java` files. Verify with:

```powershell
Select-String -Path src\main\java\com\vddoh\editor\*.java -Pattern '`r`n' -SimpleMatch
```

- In PowerShell wildcard matching, patterns like `*byte[]*` can fail because `[]` has wildcard meaning. Use `.Contains(...)` for literal substring checks.
- Avoid giant `.Replace(...)` batches. If one replacement misses because whitespace or line endings differ, the source may become half-updated.
- After any edit to Java source, immediately run one of:

```cmd
build-with-jdk.cmd
mvnw.cmd -q -DskipTests package
```

- Maven wrapper works in this project. Global `mvn` may not be installed, so use `mvnw.cmd`.
- Maven on JDK 25 may print `sun.misc.Unsafe` deprecation warnings from Maven/Guice. These warnings are not from the editor code.
- When inspecting bytecode, use `javap` narrowly:

```cmd
javap -classpath ..\vddoh.jar -c -p g
```

Then search for a nearby method/field pattern rather than dumping the whole class into the conversation.
