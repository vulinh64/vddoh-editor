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

final class GameDatHeroPatcher {
    static PatchSummary patch(byte[] data, List<HeroPatch> patches) {
        PatchSummary summary = new PatchSummary();
        HeroOffsets[] offsets = parseHeroOffsets(data);
        for (HeroPatch patch : patches) {
            if (patch.heroId < 0 || patch.heroId >= offsets.length) { summary.skipped++; continue; }
            HeroOffsets o = offsets[patch.heroId];
            writeHeroStats(data, o.statOffset, patch.strength.packed(), patch.spirit.packed(), patch.vitality.packed(), patch.speed.packed());
            summary.heroStats++;
            writeHeroSeeds(data, o.seedOffset, patch.levelCap, patch.baseCritChance, patch.baseCritDamage);
            summary.heroSeeds++;
        }
        return summary;
    }

    private static HeroOffsets[] parseHeroOffsets(byte[] data) {
        int n = heroStartOffset(data);
        HeroOffsets[] offsets = new HeroOffsets[u8(data[n++])];
        for (int heroId = 0; heroId < offsets.length; heroId++) {
            HeroOffsets o = new HeroOffsets();
            int nameLen = data[n] & 0x7f;
            n += 1 + nameLen;
            o.statOffset = n;
            n += 11;
            n += 3;
            n++;
            n++;
            o.seedOffset = n;
            n += 3;
            n += 3;
            for (int slot = 0; slot < 10; slot++) {
                int equipped = equipmentFlag(data, o.seedOffset + 3, slot);
                if (equipped > 0) n++;
            }
            int len = u8(data[n++]);
            n += len * 2;
            len = u8(data[n++]);
            n += len * 2;
            len = u8(data[n++]);
            n += len;
            offsets[heroId] = o;
        }
        return offsets;
    }
}
