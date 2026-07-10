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

final class SkillLevelTableModel extends AbstractTableModel {
    private final String[] columns = {"Skill ID", "Skill", "Level", "Cost", "Shape ID", "Area X", "Area Y", "Range", "Effects", "Notes"};
    private List<SkillLevelRow> rows = new ArrayList<>();

    void setRows(List<SkillLevelRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    void resetEdits() { for (SkillLevelRow row : rows) row.reset(); fireTableDataChanged(); }
    List<SkillEffectRow> effectRows(int rowIndex) { return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).effects : new ArrayList<>(); }
    boolean matchesSearch(int rowIndex, String query) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return false;
        SkillLevelRow row = rows.get(rowIndex);
        StringBuilder text = new StringBuilder();
        text.append(row.skillId).append(' ').append(row.skillName).append(' ').append(row.levelIndex + 1).append(' ').append(row.cost).append(' ').append(row.notes);
        for (SkillEffectRow effect : row.effects) text.append(' ').append(effect.type).append(' ').append(effect.target).append(' ').append(effect.value).append(' ').append(effect.notes);
        return text.toString().toLowerCase().contains(query);
    }
    List<SkillPatch> changedPatches() {
        List<SkillPatch> patches = new ArrayList<>();
        for (SkillLevelRow row : rows) if (row.changed()) patches.add(new SkillPatch(row.skillId, row.levelIndex, row.cost, row.effects));
        return patches;
    }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int column) { return columns[column]; }
    public boolean isCellEditable(int row, int column) { return column == 3; }
    public Object getValueAt(int rowIndex, int columnIndex) {
        SkillLevelRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.skillId;
            case 1: return row.skillName;
            case 2: return row.levelIndex + 1;
            case 3: return row.cost;
            case 4: return row.areaShape;
            case 5: return row.areaWidth;
            case 6: return row.areaHeight;
            case 7: return row.range;
            case 8: return row.effects.size();
            case 9: return row.notes;
            default: return "";
        }
    }
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        SkillLevelRow row = rows.get(rowIndex);
        int parsed = Integer.parseInt(String.valueOf(value).trim());
        if (columnIndex == 3) row.cost = parsed;
        fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
