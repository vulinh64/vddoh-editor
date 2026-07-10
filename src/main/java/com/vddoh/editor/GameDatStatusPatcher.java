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

final class GameDatStatusPatcher {
    static PatchSummary patch(byte[] data, List<StatusPatch> patches) {
        PatchSummary summary = new PatchSummary();
        StatusOffsets[] offsets = parseStatusOffsets(data);
        for (StatusPatch patch : patches) {
            if (patch.statusId < 0 || patch.statusId >= offsets.length) { summary.skipped++; continue; }
            StatusOffsets o = offsets[patch.statusId];
            if (o.durationOffset >= 0) { data[o.durationOffset] = checkedByte(patch.duration, "duration"); summary.duration++; } else summary.skipped++;
            if (o.expireOffset >= 0) { data[o.expireOffset] = encodeSignedChance(patch.expireChance); summary.expire++; } else summary.skipped++;
            if (o.iconOffset >= 0) { data[o.iconOffset] = checkedByte(patch.icon, "icon"); summary.icon++; } else summary.skipped++;
        }
        return summary;
    }

    private static StatusOffsets[] parseStatusOffsets(byte[] data) {
        int n = 13 + u16(data, 11) * 5;
        n = skipDamageGroups(data, n);
        int count = u8(data[n++]);
        StatusOffsets[] offsets = new StatusOffsets[count];
        for (int statusId = 0; statusId < count; statusId++) {
            StatusOffsets o = new StatusOffsets();
            int nameLen = data[n] & 0x1f;
            n += 1 + nameLen;
            boolean specialFlag = (data[n] & 0x80) != 0;
            n++;
            if (statusId > 0) {
                int flags = u8(data[n++]);
                if ((flags & 0x80) != 0) n++;
                if ((flags & 0x40) != 0) {
                    o.durationOffset = n;
                    n += 2;
                }
                if ((flags & 0x20) != 0) {
                    o.expireOffset = n;
                    n++;
                }
                if ((flags & 0x10) != 0) n++;
                if ((flags & 8) != 0) n++;
                if ((flags & 4) != 0) n++;
                if ((flags & 2) != 0) n++;
                if ((flags & 1) != 0) n++;
                n++;
                o.iconOffset = n++;
                int packed = u8(data[n++]);
                if ((packed & 0x80) != 0) {
                    int len = u8(data[n++]);
                    n += len * 2;
                }
                int nFlags = ((packed >> 5) & 3) | (((packed >> 4) & 1) << 7);
                if ((nFlags & 3) != 0) {
                    n++;
                    if (specialFlag) n += 2;
                }
                int pFlags = ((packed >> 2) & 3) | (((packed >> 1) & 1) << 7);
                if ((pFlags & 3) != 0) {
                    n++;
                    if (specialFlag) n += 2;
                }
            }
            offsets[statusId] = o;
        }
        return offsets;
    }
}
