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

final class SkillEffectTableModel extends AbstractTableModel {
    private final String[] columns = {"Type", "Index", "Target ID", "Target", "Value / Chance", "Own Level Data", "Notes"};
    private List<SkillEffectRow> rows = new ArrayList<>();
    void setRows(List<SkillEffectRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int column) { return columns[column]; }
    public boolean isCellEditable(int row, int column) { return column == 4 && rows.get(row).editable; }
    public Object getValueAt(int rowIndex, int columnIndex) {
        SkillEffectRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.type;
            case 1: return row.index;
            case 2: return row.targetId;
            case 3: return row.target;
            case 4: return row.displayValue();
            case 5: return row.editable ? "yes" : "inherited";
            case 6: return row.notes;
            default: return "";
        }
    }
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex != 4 || !rows.get(rowIndex).editable) return;
        rows.get(rowIndex).setDisplayValue(Integer.parseInt(String.valueOf(value).trim()));
        fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
