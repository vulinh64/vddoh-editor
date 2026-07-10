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

final class SkillLevelRow {
    final int skillId;
    final String skillName;
    final int levelIndex;
    final int originalCost;
    final int areaShape;
    final int areaWidth;
    final int areaHeight;
    final int range;
    final boolean relativeAreaGrowth;
    final List<SkillEffectRow> effects;
    final String notes;
    int cost;

    SkillLevelRow(int skillId, String skillName, int levelIndex, int cost, int areaShape, int areaWidth, int areaHeight, int range, boolean relativeAreaGrowth, List<SkillEffectRow> effects) {
        this.skillId = skillId;
        this.skillName = skillName;
        this.levelIndex = levelIndex;
        this.cost = this.originalCost = cost;
        this.areaShape = areaShape;
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;
        this.range = range;
        this.relativeAreaGrowth = relativeAreaGrowth;
        this.effects = effects;
        this.notes = (relativeAreaGrowth ? "relative area growth; " : "") + "existing effects only";
    }

    boolean changed() {
        if (cost != originalCost) return true;
        for (SkillEffectRow effect : effects) if (effect.changed()) return true;
        return false;
    }
    void reset() {
        cost = originalCost;
        for (SkillEffectRow effect : effects) effect.reset();
    }
}
