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

final class TalentTableModel extends AbstractTableModel {
    private final String[] columns = {"Kind", "Talent Type", "ID", "Talent", "Gameplay Effect", "Lv1 Value", "Lv2 Value", "Lv3 Value", "Lv4 Value", "Amount / Level", "Max Level", "Max Value", "Castable Skill ID", "Castable Skill", "Hero Effect ID", "Global ID", "Unlock Ref", "Status ID", "Resist ID", "Notes"};
    private List<TalentRow> rows = new ArrayList<>();
    void setRows(List<TalentRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    void resetEdits() { for (TalentRow row : rows) row.reset(); fireTableDataChanged(); }
    List<TalentPatch> changedPatches() {
        List<TalentPatch> patches = new ArrayList<>();
        for (TalentRow row : rows) if (row.changed()) patches.add(new TalentPatch(row.group, row.id, row.maxLevel, row.amount, row.globalBonus, row.skillUnlock, row.statusBonus, row.resistanceBonus, row.heroBonus));
        return patches;
    }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int column) { return columns[column]; }
    String columnTooltip(int column) {
        if (column >= 5 && column <= 8) return "Read-only gameplay preview for learned talent level " + (column - 4) + ". Original data stores one amount per level, not four independent values.";
        if (column == 9) return "Stored amount added per learned level. Deadly Might uses this on top of the base 50% critical-damage bonus.";
        if (column == 10) return "Maximum learnable dots/levels for this talent. J2ME data can store 1 to 4 here.";
        if (column == 11) return "Read-only preview at Max Level using the same per-level amount formula.";
        if (column == 14) return "Hero passive effect id: 1 regen, 2 movement, 3 crit chance, 4 crit damage, 5 evasion/reflex.";
        if (column >= 15 && column <= 18) return "Existing optional binary link. Safe to edit when the original row already has a value; adding/removing links would change record length.";
        return null;
    }
    public boolean isCellEditable(int rowIndex, int column) {
        TalentRow row = rows.get(rowIndex);
        return column == 9 || column == 10 || column == 14 || (column == 15 && row.originalGlobalBonus > 0) || (column == 16 && row.originalSkillUnlock > 0) || (column == 17 && row.originalStatusBonus > 0) || (column == 18 && row.originalResistanceBonus > 0);
    }
    public Object getValueAt(int rowIndex, int columnIndex) {
        TalentRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.group ? "Group" : "Hero";
            case 1: return row.talentType();
            case 2: return row.id;
            case 3: return row.name;
            case 4: return row.effectName();
            case 5: return row.levelValueText(1);
            case 6: return row.levelValueText(2);
            case 7: return row.levelValueText(3);
            case 8: return row.levelValueText(4);
            case 9: return row.amount;
            case 10: return row.maxLevel;
            case 11: return row.levelValueText(row.maxLevel);
            case 12: return row.castableSkillIdText();
            case 13: return row.unlockedSkillName;
            case 14: return row.heroBonus;
            case 15: return row.globalBonus == 0 ? "" : row.globalBonus;
            case 16: return row.skillUnlock == 0 ? "" : row.skillUnlock;
            case 17: return row.statusBonus == 0 ? "" : row.statusBonus;
            case 18: return row.resistanceBonus == 0 ? "" : row.resistanceBonus;
            case 19: return row.notes;
            default: return "";
        }
    }
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        TalentRow row = rows.get(rowIndex);
        int parsed = Integer.parseInt(String.valueOf(value).trim());
        if (columnIndex == 9) row.amount = parsed;
        else if (columnIndex == 10) row.maxLevel = parsed;
        else if (columnIndex == 14) row.heroBonus = parsed;
        else if (columnIndex == 15) row.globalBonus = parsed;
        else if (columnIndex == 16) row.skillUnlock = parsed;
        else if (columnIndex == 17) row.statusBonus = parsed;
        else if (columnIndex == 18) row.resistanceBonus = parsed;
        fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
