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

final class ItemRow {
    final int id;
    final String name;
    final int rawType;
    final int category;
    final int subtype;
    final String slotLabel;
    final String allowedClasses;
    final int originalPrice;
    final int originalIcon;
    final int originalHpRestore;
    final int originalResourceRestore;
    final int hpBonus;
    final int resourceBonus;
    final int weaponReach;
    final int weaponMode;
    final List<ItemEffectRow> effects;
    final String notes;
    int price;
    int icon;
    int hpRestore;
    int resourceRestore;
    ItemRow(int id, String name, int rawType, int category, int subtype, String slotLabel, String allowedClasses,
            int price, int icon, int hpRestore, int resourceRestore, int hpBonus, int resourceBonus,
            int weaponReach, int weaponMode, List<ItemEffectRow> effects, String notes) {
        this.id = id;
        this.name = name;
        this.rawType = rawType;
        this.category = category;
        this.subtype = subtype;
        this.slotLabel = slotLabel;
        this.allowedClasses = allowedClasses;
        this.price = this.originalPrice = price;
        this.icon = this.originalIcon = icon;
        this.hpRestore = this.originalHpRestore = hpRestore;
        this.resourceRestore = this.originalResourceRestore = resourceRestore;
        this.hpBonus = hpBonus;
        this.resourceBonus = resourceBonus;
        this.weaponReach = weaponReach;
        this.weaponMode = weaponMode;
        this.effects = effects;
        this.notes = notes;
    }
    boolean changed() { return price != originalPrice || icon != originalIcon || hpRestore != originalHpRestore || resourceRestore != originalResourceRestore; }
    void reset() {
        price = originalPrice;
        icon = originalIcon;
        hpRestore = originalHpRestore;
        resourceRestore = originalResourceRestore;
    }
}
