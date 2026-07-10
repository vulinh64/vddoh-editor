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

final class GameDatSkillPatcher {
    static PatchSummary patch(byte[] data, List<SkillPatch> patches) {
        PatchSummary summary = new PatchSummary();
        int n = 13 + u16(data, 11) * 5;
        n = skipDamageGroups(data, n);
        n = skipStatuses(data, n);
        int skillCount = u8(data[n++]);
        for (int skillId = 0; skillId < skillCount; skillId++) {
            int nameLen = data[n] & 0x1f;
            n += 1 + nameLen;
            int header = u8(data[n++]);
            int levelCount = ((header >> 6) & 3) + 1;
            int modeD = (header >> 4) & 3;
            int inheritedFlags = header & 7;

            int baseCostOffset = n++;
            int packedUsabilityOffset = n++;
            inheritedFlags = inheritedFlags | ((data[packedUsabilityOffset] & 1) << 3);
            if ((data[packedUsabilityOffset] & 8) != 0) n++;
            n++;
            if ((inheritedFlags & 1) != 0) n += 2;
            int damageCount = u8(data[n++]);
            int baseDamageOffset = n;
            n += damageCount * 3;
            int statusCount = 0;
            int baseStatusOffset = -1;
            if ((inheritedFlags & 8) != 0) {
                statusCount = u8(data[n++]);
                baseStatusOffset = n;
                n += statusCount * 2;
            }
            n += 2;
            if ((inheritedFlags & 4) != 0) n += 2;
            if ((inheritedFlags & 2) != 0) n += 2;
            n++;

            List<LevelOffsets> offsets = new ArrayList<>();
            offsets.add(new LevelOffsets(baseCostOffset, baseDamageOffset, damageCount, baseStatusOffset, statusCount));
            for (int level = 1; level < levelCount; level++) {
                LevelOffsets o = new LevelOffsets(-1, -1, damageCount, -1, statusCount);
                int flags = u8(data[n++]);
                int flags2 = u8(data[n++]);
                if ((flags & 8) != 0) n++;
                if ((flags & 0x80) != 0 && (flags2 & 0x80) != 0) n++;
                if ((flags & 0x40) != 0) n++;
                if ((flags & 0x20) != 0 && (flags2 & 8) != 0) n += 2;
                if ((flags & 0x10) != 0) o.costOffset = n++;
                if ((flags2 & 0x10) != 0 || (flags & 4) != 0) n++;
                if ((flags & 4) != 0) n++;
                if ((flags2 & 4) != 0) n++;
                else if ((flags & 2) != 0) {
                    o.damageOffset = n;
                    n += damageCount * 2;
                }
                if ((flags2 & 2) != 0) n++;
                else if ((flags & 1) != 0) {
                    o.statusOffset = n;
                    n += statusCount;
                }
                n += 2;
                offsets.add(o);
            }

            for (SkillPatch patch : patches) {
                if (patch.skillId != skillId || patch.levelIndex < 0 || patch.levelIndex >= offsets.size()) continue;
                LevelOffsets o = offsets.get(patch.levelIndex);
                if (o.costOffset >= 0) {
                    data[o.costOffset] = checkedByte(patch.cost, "cost");
                    summary.cost++;
                } else summary.skipped++;
                for (SkillEffectRow effect : patch.effects) {
                    if (!effect.changed()) continue;
                    if ("Damage".equals(effect.type)) {
                        if (o.damageOffset >= 0 && effect.index >= 0 && effect.index < o.damageCount) {
                            int offset = patch.levelIndex == 0 ? o.damageOffset + effect.index * 3 + 1 : o.damageOffset + effect.index * 2;
                            writeU16(data, offset, effect.value);
                            summary.damage++;
                        } else summary.skipped++;
                    } else if (effect.isStatus()) {
                        if (o.statusOffset >= 0 && effect.index >= 0 && effect.index < o.statusCount) {
                            int offset = patch.levelIndex == 0 ? o.statusOffset + effect.index * 2 + 1 : o.statusOffset + effect.index;
                            data[offset] = encodeSignedChance(effect.encodedValue());
                            summary.status++;
                        } else summary.skipped++;
                    }
                }
            }
        }
        return summary;
    }
}
