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

final class ItemDatPatcher {
    static PatchSummary patch(byte[] data, List<ItemPatch> patches) {
        PatchSummary summary = new PatchSummary();
        ItemOffsets[] offsets = parseItemOffsets(data);
        for (ItemPatch patch : patches) {
            if (patch.itemId < 0 || patch.itemId >= offsets.length) { summary.skipped++; continue; }
            ItemOffsets o = offsets[patch.itemId];
            if (o.priceOffset >= 0) { writeU16(data, o.priceOffset, patch.price); summary.price++; } else summary.skipped++;
            if (o.iconOffset >= 0) { data[o.iconOffset] = (byte)((data[o.iconOffset] & 0x80) | (checkedByte(patch.icon, "icon") & 0x7f)); summary.icon++; } else summary.skipped++;
            if (o.hpRestoreOffset >= 0) { writeU16(data, o.hpRestoreOffset, patch.hpRestore); summary.hp++; } else summary.skipped++;
            if (o.resourceRestoreOffset >= 0) { writeU16(data, o.resourceRestoreOffset, patch.resourceRestore); summary.resource++; } else summary.skipped++;
        }
        return summary;
    }

    private static ItemOffsets[] parseItemOffsets(byte[] data) {
        int n = 0;
        ItemOffsets[] offsets = new ItemOffsets[u8(data[n])];
        for (int itemId = 0; itemId < offsets.length; itemId++) {
            ItemOffsets o = new ItemOffsets();
            int rawType = u8(data[++n]);
            int category = (rawType >> 4) & 0x0f;
            int nameLen = u8(data[++n]);
            n += 1 + nameLen;
            if (category != 12 && category != 8) {
                o.priceOffset = n;
                n += 2;
                o.iconOffset = n;
            }
            if (category != 0 && category != 12 && category != 8) {
                if ((data[n] & 0x80) != 0) {
                    int len = u8(data[++n]);
                    n += 1 + len;
                } else {
                    ++n;
                }
            }
            if (category > 0 && category < 8) {
                int flags = u8(data[n]);
                ++n;
                if ((flags & 0x10) != 0) n += 2;
                if ((flags & 8) != 0) n += 2;
                if ((flags & 4) != 0) n += 2;
                if ((flags & 2) != 0) n += 2;
                if ((flags & 1) != 0) n++;
                if ((flags & 0x80) != 0) {
                    int len = u8(data[n++]);
                    n += len * 3;
                    if (category == 7) {
                        len = u8(data[n++]);
                        n += len * 3;
                    }
                }
                if ((flags & 0x40) != 0) {
                    int len = u8(data[n++]);
                    n += len * 2;
                }
                if ((flags & 0x20) != 0) {
                    int len = u8(data[n++]);
                    n += len * 2;
                }
            }
            if (category == 1 || category == 2 || category == 4) {
                ++n;
            } else if (category == 3) {
                ++n;
                ++n;
                int flags = u8(data[++n]);
                ++n;
                if ((flags & 0x0f) > 1) ++n;
            } else if (category == 5) {
                int flags = u8(data[n]);
                if ((flags & 4) != 0) {
                    o.hpRestoreOffset = ++n;
                    ++n;
                }
                if ((flags & 2) != 0) {
                    o.resourceRestoreOffset = ++n;
                    ++n;
                }
                if ((flags & 1) != 0) ++n;
                if ((flags & 8) == 0) ++n;
            } else if (category == 8) {
                int textLen = u16(data, n);
                n += 2 + textLen;
            } else if (category == 9 || category == 10) {
                ++n;
            } else if (category == 12) {
                int len = u8(data[n]);
                n += 1 + len * 2;
                n += 2;
                if (data[n] > 0) n += 2;
            }
            offsets[itemId] = o;
        }
        return offsets;
    }
}
