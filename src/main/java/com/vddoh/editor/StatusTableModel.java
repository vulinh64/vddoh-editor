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

final class StatusTableModel extends AbstractTableModel {
    private final String[] columns = {"ID", "Status", "Duration", "Expire %", "Icon", "Notes"};
    private List<StatusRow> rows = new ArrayList<>();
    void setRows(List<StatusRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    void resetEdits() { for (StatusRow row : rows) row.reset(); fireTableDataChanged(); }
    List<StatusPatch> changedPatches() {
        List<StatusPatch> patches = new ArrayList<>();
        for (StatusRow row : rows) if (row.changed()) patches.add(new StatusPatch(row.id, row.duration, row.expireChance, row.icon));
        return patches;
    }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int column) { return columns[column]; }
    public boolean isCellEditable(int row, int column) { return column == 2 || column == 3 || column == 4; }
    public Object getValueAt(int rowIndex, int columnIndex) {
        StatusRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.id;
            case 1: return row.name;
            case 2: return row.duration;
            case 3: return row.expireChance;
            case 4: return row.icon;
            case 5: return row.notes;
            default: return "";
        }
    }
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        StatusRow row = rows.get(rowIndex);
        int parsed = Integer.parseInt(String.valueOf(value).trim());
        if (columnIndex == 2) row.duration = parsed;
        else if (columnIndex == 3) row.expireChance = parsed;
        else if (columnIndex == 4) row.icon = parsed;
        fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
