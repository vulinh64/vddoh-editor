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

import static com.vddoh.editor.EditorSupport.*;

final class GameData {
    final List<SkillLevelRow> skillLevels = new ArrayList<>();
    final List<TalentRow> talents = new ArrayList<>();
    final List<HeroRow> heroes = new ArrayList<>();
    final List<ItemRow> items = new ArrayList<>();
    final List<NamedRow> monsters = new ArrayList<>();
    final List<StatusRow> statuses = new ArrayList<>();

    static GameData loadFromOriginalClasses(Path inputJar) throws Exception {
        try (URLClassLoader loader = selectedJarClassLoader(inputJar)) {
            Class<?> vd = Class.forName("VD", true, loader);
            Class<?> game = Class.forName("j", true, loader);
            Class<?> statusClass = Class.forName("a", true, loader);
            Class<?> skillClass = Class.forName("f", true, loader);
            Class<?> itemClass = Class.forName("k", true, loader);
            Class<?> monsterClass = Class.forName("b", true, loader);
            Class<?> heroClass = Class.forName("g", true, loader);
            Class<?> talentClass = Class.forName("l", true, loader);

            vd.getDeclaredConstructor().newInstance();
            setFirstStaticBoolean(vd, true);
            game.getMethod("a", Boolean.TYPE).invoke(null, false);
            Method decode = game.getMethod("a", byte[].class);

            GameData data = new GameData();
            Object[] statuses = staticArray(statusClass, statusClass, 0);
            Object[] skills = staticArray(skillClass, skillClass, 0);
            byte[][] damageGroups = staticByte2d(itemClass, 0);
            String[] skillNames = decodedNames(skills, decode);
            for (int i = 0; i < skills.length; i++) appendSkillRows(data.skillLevels, i, skills[i], statuses, damageGroups, decode);
            appendItemRows(data.items, staticArray(itemClass, itemClass, 0), statuses, decode);
            appendStatusRows(data.statuses, statuses, decode);
            appendNamed(data.monsters, staticArray(monsterClass, monsterClass, 0), 1, decode, "monsters");
            appendHeroRows(data.heroes, largerStaticArray(heroClass, heroClass), decode);
            appendTalentRows(data.talents, staticArray(talentClass, talentClass, 1), true, skillNames, decode);
            appendTalentRows(data.talents, staticArray(talentClass, talentClass, 0), false, skillNames, decode);
            return data;
        }
    }



    private static void appendTalentRows(List<TalentRow> rows, Object[] talents, boolean group, String[] skillNames, Method decode) throws Exception {
        for (int i = 0; talents != null && i < talents.length; i++) {
            Object talent = talents[i];
            int levelByte = u8(raw(talent, 1));
            int maxLevel = levelByte & 0x0f;
            int currentLevel = (levelByte >> 4) & 0x0f;
            int amount = u8(raw(talent, 2));
            int globalBonus = u8(raw(talent, 3));
            int skillUnlock = u8(raw(talent, 4));
            int statusBonus = u8(raw(talent, 5));
            int resistanceBonus = u8(raw(talent, 6));
            int heroBonus = u8(raw(talent, 7));
            rows.add(new TalentRow(group, i, decodeName(talent, 0, decode), maxLevel, currentLevel, amount, globalBonus, skillUnlock, skillNameForTalentLink(skillUnlock, skillNames), statusBonus, resistanceBonus, heroBonus));
        }
    }
    private static void appendHeroRows(List<HeroRow> rows, Object[] heroes, Method decode) throws Exception {
        for (int i = 0; heroes != null && i < heroes.length; i++) {
            Object hero = heroes[i];
            int baseCrit = intValue(raw(hero, 32));
            rows.add(new HeroRow(
                    i,
                    decodeName(hero, 0, decode),
                    StatCurve.fromPacked(intValue(raw(hero, 15))),
                    StatCurve.fromPacked(intValue(raw(hero, 16))),
                    StatCurve.fromPacked(intValue(raw(hero, 17))),
                    StatCurve.fromPacked(intValue(raw(hero, 18))),
                    intValue(raw(hero, 24)) & 0xff,
                    (baseCrit >> 8) & 0xff,
                    baseCrit & 0xff,
                    "game.dat: inferred core stats and packed base crit"));
        }
    }
    private static void appendItemRows(List<ItemRow> rows, Object[] items, Object[] statuses, Method decode) throws Exception {
        String[] statusNames = decodedNames(statuses, decode);
        for (int i = 0; items != null && i < items.length; i++) {
            Object item = items[i];
            int rawType = u8(raw(item, 0));
            int category = (rawType >> 4) & 0x0f;
            int subtype = rawType & 0x0f;
            int price = intValue(raw(item, 4));
            int icon = u8(raw(item, 3));
            int hpRestore = intValue(raw(item, 21));
            int resourceRestore = intValue(raw(item, 22));
            byte[] allowed = byteArray(raw(item, 7));
            int packedAttackDefense = shortValue(raw(item, 10));
            int hpBonus = (packedAttackDefense >> 8) & 0xff;
            int resourceBonus = packedAttackDefense & 0xff;
            int weaponReach = category == 3 ? u8(raw(item, 18)) & 0x0f : 0;
            int weaponMode = category == 3 ? (u8(raw(item, 18)) >> 5) & 7 : 0;
            List<ItemEffectRow> effects = decodeItemEffects(item, category, statusNames);
            rows.add(new ItemRow(
                    i,
                    decodeName(item, 0, decode),
                    rawType,
                    category,
                    subtype,
                    slotLabel(category, subtype),
                    allowedClasses(allowed),
                    price,
                    icon,
                    hpRestore,
                    resourceRestore,
                    hpBonus,
                    resourceBonus,
                    weaponReach,
                    weaponMode,
                    effects,
                    itemNotes(category, subtype, weaponReach, weaponMode)));
        }
    }
    private static void appendStatusRows(List<StatusRow> rows, Object[] statuses, Method decode) throws Exception {
        for (int i = 0; statuses != null && i < statuses.length; i++) {
            Object status = statuses[i];
            rows.add(new StatusRow(
                    i,
                    decodeName(status, 0, decode),
                    u8(raw(status, 3)),
                    signedChance(u8(raw(status, 5))),
                    u8(raw(status, 14)),
                    "game.dat"));
        }
    }
    private static void appendSkillRows(List<SkillLevelRow> rows, int skillId, Object skill, Object[] statuses, byte[][] damageGroups, Method decode) throws Exception {
        String name = decodeName(skill, 0, decode);
        Object[] levels = objectArray(raw(skill, 6));
        int[] baseDamage = intArray(raw(skill, 0));
        short[] baseStatuses = shortArray(raw(skill, 1));
        for (int level = 0; level < levels.length; level++) {
            Object h = levels[level];
            short[] levelDamage = nullableShortArray(raw(h, 7));
            byte[] levelChances = byteArray(raw(h, 8));
            int packedShapeRange = u8(raw(h, 9));
            int packedArea = u8(raw(h, 10));
            List<SkillEffectRow> effects = new ArrayList<>();
            for (int i = 0; i < baseDamage.length; i++) {
                int kind = (baseDamage[i] >> 16) & 0xff;
                int value = levelDamage == null ? baseDamage[i] & 0xffff : levelDamage[i] & 0xffff;
                effects.add(new SkillEffectRow("Damage", i, kind, kind >= 0 && kind < damageGroups.length ? decodeBytes(damageGroups[kind], decode) : statName(kind), value, level == 0 || levelDamage != null, levelDamage == null && level > 0 ? "inherited from level 1" : "own value"));
            }
            for (int i = 0; i < baseStatuses.length; i++) {
                int statusId = (baseStatuses[i] >> 8) & 0xff;
                int chance = signedChance(levelChances == null ? baseStatuses[i] & 0xff : levelChances[i] & 0xff);
                effects.add(new SkillEffectRow(chance < 0 ? "Remove Status" : "Inflict Status", i, statusId, statusId >= 0 && statusId < statuses.length ? decodeName(statuses[statusId], 0, decode) : "Status " + statusId, chance, level == 0 || levelChances != null, levelChances == null && level > 0 ? "inherited from level 1" : "own chance"));
            }
            rows.add(new SkillLevelRow(
                    skillId,
                    name,
                    level,
                    u8(raw(h, 3)),
                    (packedShapeRange >> 4) & 7,
                    ((packedArea >> 4) & 0x0f) + 1,
                    (packedArea & 0x0f) + 1,
                    packedShapeRange & 0x0f,
                    (packedShapeRange & 0x80) != 0,
                    effects));
        }
    }    }
