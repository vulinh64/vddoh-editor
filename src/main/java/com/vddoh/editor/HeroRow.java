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

final class HeroRow {
    final int id;
    final String name;
    final StatCurve strength;
    final StatCurve spirit;
    final StatCurve vitality;
    final StatCurve speed;
    final int originalLevelCap;
    final int originalBaseCritChance;
    final int originalBaseCritDamage;
    final String notes;
    int levelCap;
    int baseCritChance;
    int baseCritDamage;
    HeroRow(int id, String name, StatCurve strength, StatCurve spirit, StatCurve vitality, StatCurve speed, int levelCap, int baseCritChance, int baseCritDamage, String notes) {
        this.id = id;
        this.name = name;
        this.strength = strength;
        this.spirit = spirit;
        this.vitality = vitality;
        this.speed = speed;
        this.levelCap = this.originalLevelCap = levelCap;
        this.baseCritChance = this.originalBaseCritChance = baseCritChance;
        this.baseCritDamage = this.originalBaseCritDamage = baseCritDamage;
        this.notes = notes;
    }
    StatCurve stat(int index) {
        switch (index) {
            case 0: return strength;
            case 1: return spirit;
            case 2: return vitality;
            case 3: return speed;
            default: throw new IllegalArgumentException("Unknown hero stat " + index);
        }
    }
    int baseHp() { return (vitality.start * 70 + strength.start * 30) * 12 / 100; }
    int baseResource() { return (spirit.start * 70 + vitality.start * 30) * 12 / 100; }
    int baseAttack() { return Math.max(0, strength.start * 5 - 9); }
    int baseDefense() { return Math.max(0, speed.start * 3 + strength.start - 18); }
    int baseMove() { return 2 + speed.start / 5; }
    int baseRegen() { return 1; }
    int baseEvasion() { return 5; }
    int previewLevel() { return Math.max(1, levelCap); }
    int strengthAtCap() { return strength.valueAtLevel(previewLevel()); }
    int spiritAtCap() { return spirit.valueAtLevel(previewLevel()); }
    int vitalityAtCap() { return vitality.valueAtLevel(previewLevel()); }
    int speedAtCap() { return speed.valueAtLevel(previewLevel()); }
    boolean changed() { return strength.changed() || spirit.changed() || vitality.changed() || speed.changed() || levelCap != originalLevelCap || baseCritChance != originalBaseCritChance || baseCritDamage != originalBaseCritDamage; }
    void reset() {
        strength.reset();
        spirit.reset();
        vitality.reset();
        speed.reset();
        levelCap = originalLevelCap;
        baseCritChance = originalBaseCritChance;
        baseCritDamage = originalBaseCritDamage;
    }
}
