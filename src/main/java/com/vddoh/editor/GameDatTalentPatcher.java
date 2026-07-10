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

final class GameDatTalentPatcher {
    static PatchSummary patch(byte[] data, List<TalentPatch> patches) {
        PatchSummary summary = new PatchSummary();
        TalentSections sections = parseTalentSections(data);
        for (TalentPatch patch : patches) {
            TalentOffsets[] offsets = patch.group ? sections.group : sections.hero;
            if (patch.talentId < 0 || patch.talentId >= offsets.length) { summary.skipped++; continue; }
            TalentOffsets o = offsets[patch.talentId];
            if (o.metaOffset < 0 || o.amountOffset < 0) { summary.skipped++; continue; }
            int maxLevel = checkedTalentMaxLevel(patch.maxLevel);
            int heroBonus = checked4Bit(patch.heroBonus, "hero effect id");
            int amount = checked4Bit(patch.amount, "talent amount");
            data[o.metaOffset] = (byte)(((maxLevel - 1) << 4) | heroBonus);
            data[o.amountOffset] = (byte)((amount << 4) | (data[o.amountOffset] & 0x0f));
            if (o.globalOffset >= 0) data[o.globalOffset] = checkedTalentLink(patch.globalBonus, "global id");
            if (o.skillOffset >= 0) data[o.skillOffset] = checkedTalentLink(patch.skillUnlock, "skill id");
            if (o.statusOffset >= 0) data[o.statusOffset] = checkedTalentLink(patch.statusBonus, "status id");
            if (o.resistanceOffset >= 0) data[o.resistanceOffset] = checkedTalentLink(patch.resistanceBonus, "resist id");
            summary.talentAmount++;
        }
        return summary;
    }

    private static TalentSections parseTalentSections(byte[] data) {
        int n = skipHeroes(data, heroStartOffset(data));
        TalentSection group = parseTalentSection(data, n);
        TalentSection hero = parseTalentSection(data, group.nextOffset);
        return new TalentSections(group.offsets, hero.offsets);
    }

    private static TalentSection parseTalentSection(byte[] data, int n) {
        TalentOffsets[] offsets = new TalentOffsets[u8(data[n++])];
        for (int i = 0; i < offsets.length; i++) {
            TalentOffsets o = new TalentOffsets();
            int nameLen = u8(data[n++]);
            n += nameLen;
            o.metaOffset = n;
            n++;
            o.amountOffset = n;
            int flags = u8(data[n++]) & 0x0f;
            if ((flags & 8) != 0) o.globalOffset = n++;
            if ((flags & 4) != 0) o.skillOffset = n++;
            if ((flags & 2) != 0) o.statusOffset = n++;
            if ((flags & 1) != 0) o.resistanceOffset = n++;
            offsets[i] = o;
        }
        return new TalentSection(offsets, n);
    }
}
