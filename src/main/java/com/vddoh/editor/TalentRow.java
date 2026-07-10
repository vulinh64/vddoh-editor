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

final class TalentRow {
    final boolean group;
    final int id;
    final String name;
    final int currentLevel;
    final int originalMaxLevel;
    final int originalAmount;
    final int originalGlobalBonus;
    final int originalSkillUnlock;
    final int originalStatusBonus;
    final int originalResistanceBonus;
    final int originalHeroBonus;
    final String unlockedSkillName;
    final String notes;
    int maxLevel;
    int amount;
    int globalBonus;
    int skillUnlock;
    int statusBonus;
    int resistanceBonus;
    int heroBonus;
    TalentRow(boolean group, int id, String name, int maxLevel, int currentLevel, int amount, int globalBonus, int skillUnlock, String unlockedSkillName, int statusBonus, int resistanceBonus, int heroBonus) {
        this.group = group;
        this.id = id;
        this.name = name;
        this.maxLevel = this.originalMaxLevel = maxLevel;
        this.currentLevel = currentLevel;
        this.amount = this.originalAmount = amount;
        this.globalBonus = this.originalGlobalBonus = globalBonus;
        this.skillUnlock = this.originalSkillUnlock = skillUnlock;
        this.unlockedSkillName = unlockedSkillName;
        this.statusBonus = this.originalStatusBonus = statusBonus;
        this.resistanceBonus = this.originalResistanceBonus = resistanceBonus;
        this.heroBonus = this.originalHeroBonus = heroBonus;
        this.notes = talentNotes(heroBonus, skillUnlock, unlockedSkillName, statusBonus, resistanceBonus, globalBonus, currentLevel);
    }
    String talentType() {
        if (group) return "Group Talent";
        if (skillUnlock > 0) return "Hero Spell Unlock";
        if (heroBonus > 0) return "Passive Hero Bonus";
        if (resistanceBonus > 0) return "Resistance Bonus";
        if (statusBonus > 0) return "Status Bonus";
        if (globalBonus > 0) return "Global Bonus";
        return "Unused/Unknown";
    }
    String castableSkillIdText() { return skillUnlock > 0 ? String.valueOf(skillUnlock - 1) : ""; }
    String effectName() {
        if (skillUnlock > 0) return "Unlock castable skill";
        if (heroBonus > 0) return heroBonusName(heroBonus);
        if (statusBonus > 0) return "Status bonus " + statusBonus;
        if (resistanceBonus > 0) return resistanceTalentName(name, resistanceBonus);
        if (globalBonus > 0) return globalTalentName(globalBonus);
        return "Unknown";
    }
    String levelValueText(int level) {
        if (level < 1 || level > maxLevel) return "";
        if (skillUnlock > 0) return "";
        if (heroBonus > 0 || statusBonus > 0 || resistanceBonus > 0 || globalBonus > 0) return String.valueOf(levelValue(level));
        return "";
    }
    int levelValue(int level) {
        return passiveDisplayBase() + amount * level;
    }
    int passiveDisplayBase() {
        return heroBonus == 4 ? 50 : 0;
    }
    boolean changed() {
        return maxLevel != originalMaxLevel || amount != originalAmount || globalBonus != originalGlobalBonus || skillUnlock != originalSkillUnlock || statusBonus != originalStatusBonus || resistanceBonus != originalResistanceBonus || heroBonus != originalHeroBonus;
    }
    void reset() {
        maxLevel = originalMaxLevel;
        amount = originalAmount;
        globalBonus = originalGlobalBonus;
        skillUnlock = originalSkillUnlock;
        statusBonus = originalStatusBonus;
        resistanceBonus = originalResistanceBonus;
        heroBonus = originalHeroBonus;
    }
}
