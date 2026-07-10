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

final class ItemTableModel extends AbstractTableModel {
    private final String[] columns = {"ID", "Item", "Slot", "Allowed", "Price", "Icon", "HP Restore", "Resource Restore", "HP Bonus", "Resource Bonus", "Reach", "Notes"};
    private List<ItemRow> rows = new ArrayList<>();
    void setRows(List<ItemRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    void resetEdits() { for (ItemRow row : rows) row.reset(); fireTableDataChanged(); }
    List<ItemEffectRow> effectRows(int rowIndex) { return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).effects : new ArrayList<>(); }
    boolean matchesSearch(int rowIndex, String query) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return false;
        ItemRow row = rows.get(rowIndex);
        StringBuilder text = new StringBuilder();
        text.append(row.id).append(' ')
                .append(row.name).append(' ')
                .append(row.slotLabel).append(' ')
                .append(row.allowedClasses).append(' ')
                .append(row.price).append(' ')
                .append(row.icon).append(' ')
                .append(row.hpRestore).append(' ')
                .append(row.resourceRestore).append(' ')
                .append(row.hpBonus).append(' ')
                .append(row.resourceBonus).append(' ')
                .append(row.weaponReach).append(' ')
                .append(row.notes);
        for (ItemEffectRow effect : row.effects) {
            text.append(' ')
                    .append(effect.side).append(' ')
                    .append(effect.type).append(' ')
                    .append(effect.target).append(' ')
                    .append(effect.value).append(' ')
                    .append(effect.extra).append(' ')
                    .append(effect.raw);
        }
        return text.toString().toLowerCase().contains(query);
    }
    List<ItemPatch> changedPatches() {
        List<ItemPatch> patches = new ArrayList<>();
        for (ItemRow row : rows) if (row.changed()) patches.add(new ItemPatch(row.id, row.price, row.icon, row.hpRestore, row.resourceRestore));
        return patches;
    }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int column) { return columns[column]; }
    public boolean isCellEditable(int row, int column) { return column == 4 || column == 5 || column == 6 || column == 7; }
    public Object getValueAt(int rowIndex, int columnIndex) {
        ItemRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.id;
            case 1: return row.name;
            case 2: return row.slotLabel;
            case 3: return row.allowedClasses;
            case 4: return row.price;
            case 5: return row.icon;
            case 6: return row.hpRestore;
            case 7: return row.resourceRestore;
            case 8: return row.hpBonus;
            case 9: return row.resourceBonus;
            case 10: return row.weaponReach;
            case 11: return row.notes;
            default: return "";
        }
    }
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        ItemRow row = rows.get(rowIndex);
        int parsed = Integer.parseInt(String.valueOf(value).trim());
        if (columnIndex == 4) row.price = parsed;
        else if (columnIndex == 5) row.icon = parsed;
        else if (columnIndex == 6) row.hpRestore = parsed;
        else if (columnIndex == 7) row.resourceRestore = parsed;
        fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
