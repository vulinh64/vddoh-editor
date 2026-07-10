package com.vddoh.editor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.RowFilter;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

final class EditorSupport {



    static Path editorUserPath(String child) {
        return Path.of(System.getProperty("user.home"), ".vddoh-editor", child);
    }
























































    static void appendNamed(List<NamedRow> out, Object[] values, int nameOrdinal, Method decode, String notes) throws Exception {
        for (int i = 0; values != null && i < values.length; i++) {
            out.add(new NamedRow(i, decodeName(values[i], nameOrdinal, decode), notes));
        }
    }

    static int skipDamageGroups(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int i = 0; i < count; i++) n += 1 + (data[n] & 0x7f);
        return n;
    }

    static int skipStatuses(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int i = 0; i < count; i++) {
            int nameLen = data[n] & 0x1f;
            n += 1 + nameLen;
            boolean specialFlag = (data[n] & 0x80) != 0;
            n++;
            if (i > 0) {
                int flags = u8(data[n++]);
                if ((flags & 0x80) != 0) n++;
                if ((flags & 0x40) != 0) n += 2;
                if ((flags & 0x20) != 0) n++;
                if ((flags & 0x10) != 0) n++;
                if ((flags & 8) != 0) n++;
                if ((flags & 4) != 0) n++;
                if ((flags & 2) != 0) n++;
                if ((flags & 1) != 0) n++;
                n++;
            }
            if (i <= 0) continue;
            n++;
            int packed = u8(data[n++]);
            if ((packed & 0x80) != 0) {
                int len = u8(data[n++]);
                n += len * 2;
            }
            int nFlags = ((packed >> 5) & 3) | (((packed >> 4) & 1) << 7);
            if ((nFlags & 3) != 0) {
                n++;
                if (specialFlag) n += 2;
            }
            int pFlags = ((packed >> 2) & 3) | (((packed >> 1) & 1) << 7);
            if ((pFlags & 3) != 0) {
                n++;
                if (specialFlag) n += 2;
            }
        }
        return n;
    }


    static int heroStartOffset(byte[] data) {
        int n = 13 + u16(data, 11) * 5;
        n = skipDamageGroups(data, n);
        n = skipStatuses(data, n);
        n = skipSkills(data, n);
        return skipMonsters(data, n);
    }

    static int skipSkills(byte[] data, int n) {
        int skillCount = u8(data[n++]);
        for (int skillId = 0; skillId < skillCount; skillId++) {
            int nameLen = data[n] & 0x1f;
            n += 1 + nameLen;
            int header = u8(data[n++]);
            int levelCount = ((header >> 6) & 3) + 1;
            int inheritedFlags = header & 7;
            n++;
            int packedUsability = u8(data[n++]);
            inheritedFlags = inheritedFlags | ((packedUsability & 1) << 3);
            if ((packedUsability & 8) != 0) n++;
            n++;
            if ((inheritedFlags & 1) != 0) n += 2;
            int damageCount = u8(data[n++]);
            n += damageCount * 3;
            int statusCount = 0;
            if ((inheritedFlags & 8) != 0) {
                statusCount = u8(data[n++]);
                n += statusCount * 2;
            }
            n += 2;
            if ((inheritedFlags & 4) != 0) n += 2;
            if ((inheritedFlags & 2) != 0) n += 2;
            n++;
            for (int level = 1; level < levelCount; level++) {
                int flags = u8(data[n++]);
                int flags2 = u8(data[n++]);
                if ((flags & 8) != 0) n++;
                if ((flags & 0x80) != 0 && (flags2 & 0x80) != 0) n++;
                if ((flags & 0x40) != 0) n++;
                if ((flags & 0x20) != 0 && (flags2 & 8) != 0) n += 2;
                if ((flags & 0x10) != 0) n++;
                if ((flags2 & 0x10) != 0 || (flags & 4) != 0) n++;
                if ((flags & 4) != 0) n++;
                if ((flags2 & 4) != 0) n++;
                else if ((flags & 2) != 0) n += damageCount * 2;
                if ((flags2 & 2) != 0) n++;
                else if ((flags & 1) != 0) n += statusCount;
                n += 2;
            }
        }
        return n;
    }

    static int skipHeroes(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int heroId = 0; heroId < count; heroId++) {
            int nameLen = data[n] & 0x7f;
            n += 1 + nameLen;
            n += 11;
            n += 3;
            n++;
            n++;
            int seedOffset = n;
            n += 3;
            n += 3;
            for (int slot = 0; slot < 10; slot++) {
                int equipped = equipmentFlag(data, seedOffset + 3, slot);
                if (equipped > 0) n++;
            }
            int len = u8(data[n++]);
            n += len * 2;
            len = u8(data[n++]);
            n += len * 2;
            len = u8(data[n++]);
            n += len;
        }
        return n;
    }
    static int skipMonsters(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int i = 0; i < count; i++) {
            int nameLen = u8(data[n]);
            n += 1 + nameLen;
            n += 4;
            int flags = u8(data[n++]);
            int len = u8(data[n++]);
            for (int j = 0; j < len; j++) {
                n += 2;
                if ((data[n - 1] & 1) != 0) n++;
            }
            len = u8(data[n++]);
            n += len * 3;
            if ((flags & 8) != 0) { len = u8(data[n++]); n += len * 2; }
            if ((flags & 4) != 0) { len = u8(data[n++]); n += len * 2; }
            if ((flags & 2) != 0) { len = u8(data[n++]); n += len; }
            len = u8(data[n++]);
            n += len * 2;
            n += 13;
        }
        return n;
    }

    static int equipmentFlag(byte[] data, int offset, int slot) {
        switch (slot) {
            case 0: return (data[offset] & 8) != 0 ? ((data[offset] & 4) != 0 ? 1 : 0) : -1;
            case 1: return (data[offset] & 2) != 0 ? ((data[offset] & 1) != 0 ? 1 : 0) : -1;
            case 2: return (data[offset + 1] & 0x80) != 0 ? ((data[offset + 1] & 0x40) != 0 ? 1 : 0) : -1;
            case 3: return (data[offset + 1] & 0x20) != 0 ? ((data[offset + 1] & 0x10) != 0 ? 1 : 0) : -1;
            case 4: return (data[offset + 1] & 8) != 0 ? ((data[offset + 1] & 4) != 0 ? 1 : 0) : -1;
            case 5: return (data[offset + 1] & 2) != 0 ? ((data[offset + 1] & 1) != 0 ? 1 : 0) : -1;
            case 6: return (data[offset + 2] & 0x80) != 0 ? ((data[offset + 2] & 0x40) != 0 ? 1 : 0) : -1;
            case 7: return (data[offset + 2] & 0x20) != 0 ? ((data[offset + 2] & 0x10) != 0 ? 1 : 0) : -1;
            case 8: return (data[offset + 2] & 8) != 0 ? ((data[offset + 2] & 4) != 0 ? 1 : 0) : -1;
            case 9: return (data[offset + 2] & 2) != 0 ? ((data[offset + 2] & 1) != 0 ? 1 : 0) : -1;
            default: return -1;
        }
    }

    static void writeHeroStats(byte[] data, int offset, int power, int spirit, int vitality, int agility) {
        data[offset] = checkedByte((power >> 16) & 0xff, "power curve");
        data[offset + 1] = checkedByte((spirit >> 16) & 0xff, "spirit curve");
        data[offset + 2] = checkedByte((vitality >> 16) & 0xff, "vitality curve");
        data[offset + 3] = checkedByte((agility >> 16) & 0xff, "agility curve");
        int p = power & 0x7fff;
        int s = spirit & 0x7fff;
        int v = vitality & 0x7fff;
        int a = agility & 0x7fff;
        data[offset + 4] = checkedByte(((p >> 7) & 0xfe) | ((p >> 6) & 1), "packed hero stat");
        data[offset + 5] = checkedByte(((p & 0x3f) << 2) | ((s >> 13) & 3), "packed hero stat");
        data[offset + 6] = checkedByte(((s >> 5) & 0xf8) | ((s >> 4) & 7), "packed hero stat");
        data[offset + 7] = checkedByte(((s & 0x0f) << 4) | ((v >> 11) & 0x0f), "packed hero stat");
        data[offset + 8] = checkedByte(((v >> 3) & 0xe0) | ((v >> 2) & 0x1f), "packed hero stat");
        data[offset + 9] = checkedByte(((v & 3) << 6) | ((a >> 9) & 0x3f), "packed hero stat");
        data[offset + 10] = checkedByte(((a >> 1) & 0x80) | (a & 0x7f), "packed hero stat");
    }

    static void writeHeroSeeds(byte[] data, int offset, int levelCap, int baseCritChance, int baseCritDamage) {
        int shortA = checked7Bit(levelCap, "level cap");
        int shortB = ((checkedByte(baseCritChance, "base crit chance") & 0xff) << 8) | (checkedByte(baseCritDamage, "base crit damage") & 0xff);
        data[offset] = checkedByte(((shortA >> 7) & 0xfe) | ((shortA >> 6) & 1), "packed hero header");
        data[offset + 1] = checkedByte(((shortA & 0x3f) << 2) | ((shortB >> 13) & 3), "packed hero header");
        data[offset + 2] = checkedByte(((shortB >> 5) & 0xf8) | ((shortB >> 4) & 7), "packed hero header");
        data[offset + 3] = (byte)((data[offset + 3] & 0x0f) | ((shortB & 0x0f) << 4));
    }
    static String[] decodedNames(Object[] values, Method decode) throws Exception {
        if (values == null) return new String[0];
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = decodeName(values[i], 0, decode);
        return names;
    }

    static List<ItemEffectRow> decodeItemEffects(Object item, int category, String[] statusNames) throws Exception {
        List<ItemEffectRow> rows = new ArrayList<>();
        int hpRestore = intValue(raw(item, 21));
        int resourceRestore = intValue(raw(item, 22));
        if (hpRestore > 0) rows.add(new ItemEffectRow("Consumable", "Restore HP", "HP", String.valueOf(hpRestore), "", "short_g"));
        if (resourceRestore > 0) rows.add(new ItemEffectRow("Consumable", "Restore Resource", "Blood/Soul", String.valueOf(resourceRestore), "", "short_h"));

        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 0, shortValue(raw(item, 8)), "short_c");
        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 2, shortValue(raw(item, 9)), "short_d");
        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 4, shortValue(raw(item, 10)), "short_e");
        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 6, shortValue(raw(item, 11)), "short_f");
        int misc = u8(raw(item, 12));
        if (misc != 0) rows.add(new ItemEffectRow("Equipment", "Packed Stat", statName(8), String.valueOf(misc), "", "byte_d"));

        appendIntEffects(rows, category == 7 ? "Weapon effect" : "Equipment/Weapon", intArray(raw(item, 13)), "int_arr_a");
        appendIntEffects(rows, "Armor effect", intArray(raw(item, 14)), "int_arr_b");
        appendShortEffects(rows, "Protection", shortArray(raw(item, 15)), statusNames, "short_arr_a");
        appendShortEffects(rows, category == 5 ? "Consumable" : "On hit / item use", shortArray(raw(item, 16)), statusNames, "short_arr_b");

        if (category == 3) {
            int weaponReach = u8(raw(item, 18)) & 0x0f;
            int weaponMode = (u8(raw(item, 18)) >> 5) & 7;
            rows.add(new ItemEffectRow("Weapon", "Reach", "Tiles", String.valueOf(weaponReach), "mode=" + weaponMode, "byte_f"));
            rows.add(new ItemEffectRow("Weapon", "Animation", "Projectile/impact", "q=" + intValue(raw(item, 32)), "r=" + intValue(raw(item, 33)), "q/r"));
        }
        if (rows.isEmpty()) rows.add(new ItemEffectRow("Info", "No decoded effects", "", "", "", ""));
        return rows;
    }

    static void appendPackedStat(List<ItemEffectRow> rows, String side, String type, int baseStat, int packed, String rawName) {
        int high = (packed >> 8) & 0xff;
        int low = packed & 0xff;
        if (high != 0) rows.add(new ItemEffectRow(side, type, statName(baseStat), String.valueOf(high), "", rawName + ":hi"));
        if (low != 0) rows.add(new ItemEffectRow(side, type, statName(baseStat + 1), String.valueOf(low), "", rawName + ":lo"));
    }

    static void appendIntEffects(List<ItemEffectRow> rows, String side, int[] values, String rawName) {
        for (int i = 0; values != null && i < values.length; i++) {
            int packed = values[i];
            int kind = (packed >> 16) & 0xff;
            int value = packed & 0xffff;
            rows.add(new ItemEffectRow(side, effectKind(kind), statName(kind), String.valueOf(value), "", rawName + "[" + i + "]=" + packed));
        }
    }

    static void appendShortEffects(List<ItemEffectRow> rows, String side, short[] values, String[] statusNames, String rawName) {
        for (int i = 0; values != null && i < values.length; i++) {
            int packed = values[i] & 0xffff;
            int id = (packed >> 8) & 0xff;
            int value = packed & 0xff;
            rows.add(new ItemEffectRow(side, "Status", statusLabel(id, statusNames), String.valueOf(value), "%/value", rawName + "[" + i + "]=" + packed));
        }
    }

    static String slotLabel(int category, int subtype) {
        switch (category) {
            case 1: return subtype == 0 ? "Ring" : "Neck/Accessory";
            case 2:
                if (subtype == 0) return "Body Armor";
                if (subtype == 1) return "Head";
                if (subtype == 4) return "Feet";
                return "Armor subtype " + subtype;
            case 3: return "Weapon";
            case 4: return "Equipment subtype " + subtype;
            case 5: return "Consumable";
            case 7: return "Rune/Modifier";
            case 8: return "Text/Special";
            case 9: return "Skill item";
            case 10: return "Special item";
            case 12: return "Quest/Special";
            default: return "Category " + category;
        }
    }

    static String allowedClasses(byte[] allowed) {
        if (allowed == null || allowed.length == 0) return "Any";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < allowed.length; i++) parts.add(heroClassName(allowed[i] & 0xff));
        return joinParts(parts);
    }

    static String heroClassName(int id) {
        switch (id) {
            case 0: return "Lara";
            case 1: return "Vince";
            case 2: return "Romus";
            case 3: return "Manok";
            default: return "Class " + id;
        }
    }

    static String itemNotes(int category, int subtype, int reach, int mode) {
        if (category == 3) return "weapon: reach=" + reach + ", mode=" + mode;
        if (category == 7) return "rune/modifier: weapon effect + armor effect";
        return "type=" + category + ", subtype=" + subtype;
    }

    static String effectKind(int id) {
        if (id == 0) return "Flat stat/damage";
        if (id >= 9 && id <= 13) return "Element/resistance";
        if (id >= 14) return "Status/resistance";
        return "Modifier";
    }

    static String skillNameForTalentLink(int skillUnlock, String[] skillNames) {
        int skillId = skillUnlock - 1;
        if (skillId < 0) return "";
        if (skillId >= skillNames.length) return "Skill " + skillId;
        return skillNames[skillId];
    }
    static String globalTalentName(int id) {
        switch (id) {
            case 1: return "Blood sucking / NPC resource gain";
            case 2: return "Stealing tier";
            case 3: return "Sharp senses";
            default: return "Global bonus " + id;
        }
    }

    static String resistanceTalentName(String talentName, int id) {
        String normalized = talentName == null ? "" : talentName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mental")) return "Anti-sleep";
        if (normalized.contains("poison")) return "Anti-poison";
        if (normalized.contains("magic eyes")) return "Anti-blind";
        if (normalized.contains("hard bones")) return "Anti-blaze";
        return "Resistance bonus " + id;
    }

    static String heroBonusName(int id) {
        switch (id) {
            case 1: return "HP regen per turn";
            case 2: return "Movement/zone bonus";
            case 3: return "Critical chance %";
            case 4: return "Critical damage bonus %";
            case 5: return "Reflex/evasion";
            default: return "Hero bonus " + id;
        }
    }

    static String talentNotes(int heroBonus, int skillUnlock, String unlockedSkillName, int statusBonus, int resistanceBonus, int globalBonus, int currentLevel) {
        String prefix = currentLevel > 0 ? "current=" + currentLevel + "; " : "";
        if (skillUnlock > 0) return prefix + "hero talent unlocks castable skill " + (skillUnlock - 1) + (unlockedSkillName.isEmpty() ? "" : " (" + unlockedSkillName + ")") + ".";
        if (heroBonus == 3) return prefix + "Find Weaknesses-like: adds amount percent critical chance per learned level.";
        if (heroBonus == 4) return prefix + "Deadly Might-like: adds amount percent critical damage bonus per learned level.";
        if (heroBonus > 0) return prefix + "hero-wide bonus id " + heroBonus + "; amount applies per learned level.";
        if (statusBonus > 0) return prefix + "status bonus id " + statusBonus + ".";
        if (resistanceBonus > 0) return prefix + "resistance bonus id " + resistanceBonus + ".";
        if (globalBonus > 0) return prefix + "global party bonus id " + globalBonus + ".";
        return prefix + "amount applies per learned level; exact effect not named yet.";
    }
    static String statName(int id) {
        String[] names = {"Strength/Power", "Spirit", "Vitality", "Speed", "Max HP", "Max Resource", "Move", "Regen", "Weapon Attack / Armor Defense", "Fire", "Frost", "Light", "Shadow", "Blood", "Status", "Poison", "Sleep", "Bleed", "Blind", "Silence", "Enfeeble", "Frenzy", "Confuse", "Shackle", "Blaze", "Cold", "Fear"};
        return id >= 0 && id < names.length ? names[id] : "Stat " + id;
    }

    static String statusLabel(int id, String[] statusNames) {
        return id >= 0 && id < statusNames.length ? statusNames[id] : "Status " + id;
    }

    static URLClassLoader selectedJarClassLoader(Path inputJar) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(inputJar.toAbsolutePath().normalize().toUri().toURL());
        urls.add(EditorSupport.class.getProtectionDomain().getCodeSource().getLocation());
        addKEmulatorLibraries(urls);
        return new URLClassLoader(urls.toArray(new URL[0]), null);
    }

    static void addKEmulatorLibraries(List<URL> urls) throws IOException {
        addIfExists(urls, Path.of("me-lib", "cldc11.jar"));
        addIfExists(urls, Path.of("me-lib", "midp21.jar"));
        addIfExists(urls, Path.of("me-lib", "jsr135.jar"));
        addIfExists(urls, Path.of("me-lib", "nokiaui.jar"));

        addIfExists(urls, Path.of("lib", "kemulator", "cldc11.jar"));
        addIfExists(urls, Path.of("lib", "kemulator", "midp21.jar"));
        addIfExists(urls, Path.of("lib", "kemulator", "jsr135.jar"));
        addIfExists(urls, Path.of("lib", "kemulator", "nokiaui.jar"));

        String configuredHome = System.getProperty("kemulator.home");
        if (configuredHome == null || configuredHome.isBlank()) configuredHome = System.getenv("KEMULATOR_HOME");
        if (configuredHome != null && !configuredHome.isBlank()) addKEmulatorHome(urls, Path.of(configuredHome));

        addKEmulatorHome(urls, Path.of("D:", "Games", "JAR", "KEmulator2"));
    }

    static void addKEmulatorHome(List<URL> urls, Path home) throws IOException {
        addIfExists(urls, home.resolve("uei").resolve("cldc11.jar"));
        addIfExists(urls, home.resolve("uei").resolve("midp21.jar"));
        addIfExists(urls, home.resolve("uei").resolve("jsr135.jar"));
        addIfExists(urls, home.resolve("uei").resolve("nokiaui.jar"));
        addIfExists(urls, home.resolve("KEmulator.jar"));
    }

    static void addIfExists(List<URL> urls, Path path) throws IOException {
        if (Files.exists(path)) urls.add(path.toAbsolutePath().normalize().toUri().toURL());
    }

    static byte[] readZipEntry(ZipInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    static byte[] readJarEntry(Path inputJar, String entryName) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) return readZipEntry(in);
                in.closeEntry();
            }
        }
        throw new IOException("JAR does not contain " + entryName);
    }
    static void replaceJarEntries(Path inputJar, Path outputJar, Map<String, byte[]> replacements) throws IOException {
        Set<String> seen = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputJar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                byte[] replacement = replacements.get(entry.getName());
                if (replacement != null) {
                    out.write(replacement);
                    seen.add(entry.getName());
                } else {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                }
                out.closeEntry();
                in.closeEntry();
            }
            for (Map.Entry<String, byte[]> replacement : replacements.entrySet()) {
                if (seen.contains(replacement.getKey())) continue;
                out.putNextEntry(new ZipEntry(replacement.getKey()));
                out.write(replacement.getValue());
                out.closeEntry();
            }
        }
    }

    static Object raw(Object value, int ordinal) throws Exception {
        int i = 0;
        for (Field field : value.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (i++ == ordinal) return field.get(value);
        }
        return null;
    }

    static Object[] staticArray(Class<?> owner, Class<?> component, int ordinal) throws Exception {
        int seen = 0;
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!type.isArray() || type.getComponentType() != component) continue;
            if (seen++ == ordinal) return (Object[]) field.get(null);
        }
        return new Object[0];
    }

    static Object[] largerStaticArray(Class<?> owner, Class<?> component) throws Exception {
        Object[] best = new Object[0];
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!type.isArray() || type.getComponentType() != component) continue;
            Object[] value = (Object[]) field.get(null);
            if (value != null && value.length > best.length) best = value;
        }
        return best;
    }

    static byte[][] staticByte2d(Class<?> owner, int ordinal) throws Exception {
        int seen = 0;
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!type.isArray() || !type.getComponentType().isArray() || type.getComponentType().getComponentType() != Byte.TYPE) continue;
            if (seen++ == ordinal) return (byte[][]) field.get(null);
        }
        return new byte[0][];
    }

    static void setFirstStaticBoolean(Class<?> owner, boolean value) throws Exception {
        for (Field field : owner.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Boolean.TYPE) {
                field.setBoolean(null, value);
                return;
            }
        }
    }

    static String decodeName(Object value, int byteArrayOrdinal, Method decode) throws Exception {
        int seen = 0;
        for (Field field : value.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getType().isArray() || field.getType().getComponentType() != Byte.TYPE) continue;
            if (seen++ == byteArrayOrdinal) return decodeBytes((byte[]) field.get(value), decode);
        }
        return "";
    }

    static String decodeBytes(byte[] encoded, Method decode) throws Exception {
        return encoded == null ? "" : (String) decode.invoke(null, (Object) encoded);
    }

    static Object[] objectArray(Object value) { return value == null ? new Object[0] : (Object[]) value; }
    static int[] intArray(Object value) { return value instanceof int[] ? (int[]) value : new int[0]; }
    static short[] shortArray(Object value) { return value instanceof short[] ? (short[]) value : new short[0]; }
    static short[] nullableShortArray(Object value) { return value instanceof short[] ? (short[]) value : null; }
    static byte[] byteArray(Object value) { return value instanceof byte[] ? (byte[]) value : null; }
    static int u8(Object value) { return value instanceof Byte ? ((Byte) value).byteValue() & 0xff : 0; }
    static int intValue(Object value) { return value instanceof Number ? ((Number) value).intValue() & 0xffff : 0; }
    static int shortValue(Object value) { return value instanceof Number ? ((Number) value).intValue() & 0xffff : 0; }
    static int u8(byte value) { return value & 0xff; }
    static int u16(byte[] data, int offset) { return (u8(data[offset]) << 8) | u8(data[offset + 1]); }
    static int signedChance(int raw) { return (raw & 0x80) != 0 ? -((-raw) & 0x7f) : raw & 0x7f; }
    static void writeU16(byte[] data, int offset, int value) {
        if (value < 0 || value > 0xffff) throw new IllegalArgumentException("damage must be 0..65535");
        data[offset] = (byte)((value >>> 8) & 0xff);
        data[offset + 1] = (byte)(value & 0xff);
    }
    static int checked7Bit(int value, String label) {
        if (value < 0 || value > 127) throw new IllegalArgumentException(label + " must be 0..127");
        return value;
    }
    static int checkedTalentMaxLevel(int value) {
        if (value < 1 || value > 4) throw new IllegalArgumentException("talent max level must be 1..4");
        return value;
    }

    static byte checkedTalentLink(int value, String label) {
        if (value < 1 || value > 256) throw new IllegalArgumentException(label + " must be 1..256 for an existing talent link");
        return (byte)((value - 1) & 0xff);
    }
    static int checked4Bit(int value, String label) {
        if (value < 0 || value > 15) throw new IllegalArgumentException(label + " must be 0..15");
        return value;
    }
    static byte checkedByte(int value, String label) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(label + " must be 0..255");
        return (byte)value;
    }
    static byte encodeSignedChance(int chance) {
        if (chance < -127 || chance > 127) throw new IllegalArgumentException("status chance must be -127..127");
        return chance < 0 ? (byte)(-(-chance & 0x7f)) : (byte)(chance & 0x7f);
    }

    static String joinLines(List<String> values) { return String.join("\n", values); }
    static String joinParts(List<String> values) { return String.join(", ", values); }

    static void showError(java.awt.Component parent, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(parent, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
