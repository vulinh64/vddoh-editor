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

final class HeroTableModel extends AbstractTableModel {
    private final String[] columns = {"ID", "Hero", "Base HP", "Base Resource", "Base Attack", "Base Defense", "Base Move", "Base Regen", "Strength Start", "Strength Lv99 Target", "Strength Growth Curve", "Spirit Start", "Spirit Lv99 Target", "Spirit Growth Curve", "Vitality Start", "Vitality Lv99 Target", "Vitality Growth Curve", "Speed Start", "Speed Lv99 Target", "Speed Growth Curve", "Level Cap", "STR @ Cap", "SPI @ Cap", "VIT @ Cap", "SPD @ Cap", "Base Crit %", "Base Crit Dmg %", "Base Evasion %", "Notes"};
    private List<HeroRow> rows = new ArrayList<>();
    void setRows(List<HeroRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    void resetEdits() { for (HeroRow row : rows) row.reset(); fireTableDataChanged(); }
    List<HeroPatch> changedPatches() {
        List<HeroPatch> patches = new ArrayList<>();
        for (HeroRow row : rows) if (row.changed()) patches.add(new HeroPatch(row.id, row.strength, row.spirit, row.vitality, row.speed, row.levelCap, row.baseCritChance, row.baseCritDamage));
        return patches;
    }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int column) { return columns[column]; }
    String columnTooltip(int column) {
        if (column == 2) return "Estimated level-1 max HP from starting Strength/Vitality before equipment and status effects.";
        if (column == 3) return "Estimated level-1 max Blood/Soul from starting Spirit/Vitality before equipment and status effects.";
        if (column == 4) return "Estimated level-1 attack from starting Strength before equipment.";
        if (column == 5) return "Estimated level-1 defense from starting Speed/Strength before equipment.";
        if (column == 6) return "Estimated movement range from starting Speed.";
        if (column >= 8 && column <= 19) {
            int part = (column - 8) % 3;
            if (part == 0) return "Level-1 value. This is the visible base stat before level growth and equipment.";
            if (part == 1) return "Growth target used by the formula at level 99, not the level-30 cap.";
            return "Growth curve. 0 back-loads growth, 100 is roughly linear, higher values front-load growth before level cap.";
        }
        if (column == 20) return "Maximum hero level. Vanilla heroes cap at 30.";
        if (column >= 21 && column <= 24) return "Read-only preview of the grown stat at Level Cap using the game's integer formula.";
        if (column == 25) return "Base physical critical hit chance. Final chance = this value + Find Weaknesses bonus.";
        if (column == 26) return "Base critical damage bonus. Final bonus = this value + Deadly Might bonus, capped by bytecode at 250.";
        if (column == 27) return "Read-only bytecode constant. Final evasion = 5 + Reflexes bonus; per-hero data does not store this.";
        return null;
    }
    public boolean isCellEditable(int row, int column) { return (column >= 8 && column <= 20) || column == 25 || column == 26; }
    public Object getValueAt(int rowIndex, int columnIndex) {
        HeroRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.id;
            case 1: return row.name;
            case 2: return row.baseHp();
            case 3: return row.baseResource();
            case 4: return row.baseAttack();
            case 5: return row.baseDefense();
            case 6: return row.baseMove();
            case 7: return row.baseRegen();
            case 8: return row.strength.start;
            case 9: return row.strength.target;
            case 10: return row.strength.curve;
            case 11: return row.spirit.start;
            case 12: return row.spirit.target;
            case 13: return row.spirit.curve;
            case 14: return row.vitality.start;
            case 15: return row.vitality.target;
            case 16: return row.vitality.curve;
            case 17: return row.speed.start;
            case 18: return row.speed.target;
            case 19: return row.speed.curve;
            case 20: return row.levelCap;
            case 21: return row.strengthAtCap();
            case 22: return row.spiritAtCap();
            case 23: return row.vitalityAtCap();
            case 24: return row.speedAtCap();
            case 25: return row.baseCritChance;
            case 26: return row.baseCritDamage;
            case 27: return row.baseEvasion();
            case 28: return row.notes;
            default: return "";
        }
    }
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        HeroRow row = rows.get(rowIndex);
        int parsed = Integer.parseInt(String.valueOf(value).trim());
        if (columnIndex >= 8 && columnIndex <= 19) {
            StatCurve stat = row.stat((columnIndex - 8) / 3);
            switch ((columnIndex - 8) % 3) {
                case 0: stat.start = parsed; break;
                case 1: stat.target = parsed; break;
                case 2: stat.curve = parsed; break;
            }
        } else if (columnIndex == 20) row.levelCap = parsed;
        else if (columnIndex == 25) row.baseCritChance = parsed;
        else if (columnIndex == 26) row.baseCritDamage = parsed;
        fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
