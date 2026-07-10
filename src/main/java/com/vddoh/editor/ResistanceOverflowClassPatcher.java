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

final class ResistanceOverflowClassPatcher {
    enum State { ORIGINAL, PATCHED, UNKNOWN }

    private static final byte[] ORIGINAL = new byte[] {
            0x2a, (byte)0xb4, 0x00, 0x46, (byte)0xb2, 0x00, 0x15, 0x33,
            (byte)0x9c, 0x00, 0x0f, 0x2a, (byte)0xb4, 0x00, 0x46, (byte)0xb2,
            0x00, 0x15, 0x03, 0x54, (byte)0xa7, 0x00, 0x1a
    };
    private static final byte[] PATCHED = new byte[] {
            0x2a, (byte)0xb4, 0x00, 0x46, (byte)0xb2, 0x00, 0x15, 0x33,
            (byte)0x9c, 0x00, 0x0f, 0x2a, (byte)0xb4, 0x00, 0x46, (byte)0xb2,
            0x00, 0x15, 0x10, 0x64, 0x54, 0x00, 0x00
    };

    static State state(byte[] data) {
        int original = countPattern(data, ORIGINAL);
        int patched = countPattern(data, PATCHED);
        if (patched == 1 && original == 0) return State.PATCHED;
        if (original == 1 && patched == 0) return State.ORIGINAL;
        return State.UNKNOWN;
    }

    static PatchSummary patch(byte[] data) {
        PatchSummary summary = new PatchSummary();
        State state = state(data);
        if (state == State.PATCHED) {
            summary.skipped++;
            return summary;
        }
        if (state != State.ORIGINAL) throw new IllegalArgumentException("Unsupported g.class layout for resistance overflow patch");
        int offset = indexOf(data, ORIGINAL);
        System.arraycopy(PATCHED, 0, data, offset, PATCHED.length);
        summary.heroResistOverflow++;
        return summary;
    }

    private static int countPattern(byte[] data, byte[] pattern) {
        int count = 0;
        for (int i = 0; i <= data.length - pattern.length; i++) if (matches(data, pattern, i)) count++;
        return count;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i <= data.length - pattern.length; i++) if (matches(data, pattern, i)) return i;
        return -1;
    }

    private static boolean matches(byte[] data, byte[] pattern, int offset) {
        for (int i = 0; i < pattern.length; i++) if (data[offset + i] != pattern[i]) return false;
        return true;
    }
}
