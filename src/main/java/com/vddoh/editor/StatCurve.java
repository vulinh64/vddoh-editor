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

final class StatCurve {
    final int originalStart;
    final int originalTarget;
    final int originalCurve;
    int start;
    int target;
    int curve;
    StatCurve(int start, int target, int curve) {
        this.start = this.originalStart = start;
        this.target = this.originalTarget = target;
        this.curve = this.originalCurve = curve;
    }
    static StatCurve fromPacked(int packed) { return new StatCurve(packed & 0xff, (packed >> 8) & 0xff, (packed >> 16) & 0xff); }
    int packed() { return (checkedByte(curve, "stat curve") << 16) | (checked7Bit(target, "stat target") << 8) | checked7Bit(start, "stat start"); }
    int valueAtLevel(int level) {
        return level * (target - start) * (level * (100 - curve) / 99 + curve) / 99 / 100 + start;
    }
    boolean changed() { return start != originalStart || target != originalTarget || curve != originalCurve; }
    void reset() { start = originalStart; target = originalTarget; curve = originalCurve; }
}
