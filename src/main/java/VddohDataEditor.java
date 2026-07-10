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

public final class VddohDataEditor {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    new EditorFrame().setVisible(true);
                } catch (Exception ex) {
                    showError(null, ex);
                }
            }
        });
    }

    private static final class EditorFrame extends JFrame {
        private final JTextField jarPath = new JTextField("vddoh.jar", 32);
        private final JTextField gameDatPath = new JTextField(editorUserPath("temp").resolve("vddoh").resolve("game.dat").toString(), 32);
        private final JTextField itemDatPath = new JTextField(editorUserPath("temp").resolve("vddoh").resolve("item.dat").toString(), 32);
        private final JTextField outputJarPath = new JTextField(editorUserPath("dist").resolve("vddoh-editor-patch.jar").toString(), 32);
        private String gameDatEntryName = "game.dat";
        private String itemDatEntryName = "item.dat";
        private final SkillLevelTableModel skillsModel = new SkillLevelTableModel();
        private final SkillEffectTableModel skillEffectsModel = new SkillEffectTableModel();
        private final HeroTableModel heroesModel = new HeroTableModel();
        private final ItemTableModel itemsModel = new ItemTableModel();
        private final ItemEffectTableModel itemEffectsModel = new ItemEffectTableModel();
        private final TalentTableModel talentsModel = new TalentTableModel();
        private final SimpleNamedTableModel monstersModel = new SimpleNamedTableModel("Monster");
        private final StatusTableModel statusesModel = new StatusTableModel();
        private final JLabel status = new JLabel("Ready");
        private final JCheckBox patchResistanceOverflow = new JCheckBox("Patch resistance overflow");

        EditorFrame() throws Exception {
            super("VDDOH Data Editor");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1100, 720);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());

            JPanel top = new JPanel(new GridLayout(4, 1));
            top.add(pathRow("Input JAR", jarPath, true));
            top.add(pathRow("Input game.dat", gameDatPath, false));
            top.add(pathRow("Input item.dat", itemDatPath, false));
            top.add(pathRow("Output JAR", outputJarPath, false));
            add(top, BorderLayout.NORTH);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Skills", createSkillsPanel());
            tabs.addTab("Talents", createSearchableTablePanel(talentsModel, "Talents"));
            tabs.addTab("Heroes", createSearchableTablePanel(heroesModel, "Heroes"));
            tabs.addTab("Items", createItemsPanel());
            tabs.addTab("Monsters", createSearchableTablePanel(monstersModel, "Monsters"));
            tabs.addTab("Statuses", createSearchableTablePanel(statusesModel, "Statuses"));
            add(tabs, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton load = new JButton("Load Data");
            JButton build = new JButton("Build Patched JAR");
            JButton reset = new JButton("Reset Edits");
            load.addActionListener(e -> loadData());
            build.addActionListener(e -> buildPatchedJar());
            reset.addActionListener(e -> {
                skillsModel.resetEdits();
                talentsModel.resetEdits();
                heroesModel.resetEdits();
                itemsModel.resetEdits();
                statusesModel.resetEdits();
            });
            buttons.add(load);
            buttons.add(build);
            buttons.add(reset);
            patchResistanceOverflow.setToolTipText("Patch g.class so overflowed resistance bytes clamp to 100 instead of 0.");
            buttons.add(patchResistanceOverflow);
            bottom.add(buttons, BorderLayout.WEST);
            bottom.add(status, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);

            if (chooseInputJarAtStartup()) {
                loadData();
            } else {
                status.setText("Choose an input JAR, then click Load Data.");
            }
        }

        private boolean chooseInputJarAtStartup() {
            JFileChooser chooser = new JFileChooser(".");
            chooser.setDialogTitle("Choose VDDOH input JAR");
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            setInputJar(Path.of(chooser.getSelectedFile().getPath()));
            return true;
        }

        private JPanel createSkillsPanel() {
            JTable levels = new JTable(skillsModel);
            configureScrollableTable(levels);
            levels.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            TableRowSorter<SkillLevelTableModel> sorter = new TableRowSorter<>(skillsModel);
            levels.setRowSorter(sorter);

            JTable effects = new JTable(skillEffectsModel);
            configureScrollableTable(effects);
            levels.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int selected = levels.getSelectedRow();
                if (selected < 0) {
                    skillEffectsModel.setRows(new ArrayList<>());
                    return;
                }
                int modelRow = levels.convertRowIndexToModel(selected);
                skillEffectsModel.setRows(skillsModel.effectRows(modelRow));
            });

            JTextField search = new JTextField(24);
            JButton searchButton = new JButton("Search");
            JButton clearButton = new JButton("Clear");
            Runnable applySearch = () -> {
                String query = search.getText().trim().toLowerCase();
                if (query.length() < 3) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(new RowFilter<SkillLevelTableModel, Integer>() {
                        public boolean include(Entry<? extends SkillLevelTableModel, ? extends Integer> entry) {
                            return skillsModel.matchesSearch(entry.getIdentifier(), query);
                        }
                    });
                }
                if (levels.getRowCount() > 0) levels.setRowSelectionInterval(0, 0);
                else skillEffectsModel.setRows(new ArrayList<>());
                status.setText("Skills shown: " + levels.getRowCount() + " / " + skillsModel.getRowCount());
            };
            search.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { applySearch.run(); } });
            searchButton.addActionListener(e -> applySearch.run());
            clearButton.addActionListener(e -> { search.setText(""); applySearch.run(); });

            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.add(new JLabel("Search"));
            searchPanel.add(search);
            searchPanel.add(searchButton);
            searchPanel.add(clearButton);

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(levels), new JScrollPane(effects));
            split.setResizeWeight(0.58);
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(searchPanel, BorderLayout.NORTH);
            panel.add(split, BorderLayout.CENTER);
            return panel;
        }
        private JPanel createItemsPanel() {
            JTable items = new JTable(itemsModel);
            configureScrollableTable(items);
            items.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            TableRowSorter<ItemTableModel> sorter = new TableRowSorter<>(itemsModel);
            items.setRowSorter(sorter);

            JTable effects = new JTable(itemEffectsModel);
            configureScrollableTable(effects);
            items.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int selected = items.getSelectedRow();
                if (selected < 0) {
                    itemEffectsModel.setRows(new ArrayList<>());
                    return;
                }
                int modelRow = items.convertRowIndexToModel(selected);
                itemEffectsModel.setRows(itemsModel.effectRows(modelRow));
            });

            JTextField search = new JTextField(24);
            JButton searchButton = new JButton("Search");
            JButton clearButton = new JButton("Clear");
            Runnable applySearch = () -> {
                String query = search.getText().trim().toLowerCase();
                if (query.length() < 3) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(new RowFilter<ItemTableModel, Integer>() {
                        public boolean include(Entry<? extends ItemTableModel, ? extends Integer> entry) {
                            return itemsModel.matchesSearch(entry.getIdentifier(), query);
                        }
                    });
                }
                if (items.getRowCount() > 0) {
                    items.setRowSelectionInterval(0, 0);
                } else {
                    itemEffectsModel.setRows(new ArrayList<>());
                }
                status.setText("Items shown: " + items.getRowCount() + " / " + itemsModel.getRowCount());
            };
            search.addKeyListener(new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    applySearch.run();
                }
            });
            searchButton.addActionListener(e -> applySearch.run());
            clearButton.addActionListener(e -> {
                search.setText("");
                applySearch.run();
            });

            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.add(new JLabel("Search"));
            searchPanel.add(search);
            searchPanel.add(searchButton);
            searchPanel.add(clearButton);

            JSplitPane split = new JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    new JScrollPane(items),
                    new JScrollPane(effects));
            split.setResizeWeight(0.64);
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(searchPanel, BorderLayout.NORTH);
            panel.add(split, BorderLayout.CENTER);
            return panel;
        }
        private JPanel createSearchableTablePanel(AbstractTableModel model, String label) {
            JTable table = model instanceof HeroTableModel
                    ? new JTable(model) {
                        public String getToolTipText(MouseEvent event) {
                            int viewColumn = columnAtPoint(event.getPoint());
                            if (viewColumn < 0) return super.getToolTipText(event);
                            int modelColumn = convertColumnIndexToModel(viewColumn);
                            return ((HeroTableModel) getModel()).columnTooltip(modelColumn);
                        }
                    }
                    : model instanceof TalentTableModel
                    ? new JTable(model) {
                        public String getToolTipText(MouseEvent event) {
                            int viewColumn = columnAtPoint(event.getPoint());
                            if (viewColumn < 0) return super.getToolTipText(event);
                            int modelColumn = convertColumnIndexToModel(viewColumn);
                            return ((TalentTableModel) getModel()).columnTooltip(modelColumn);
                        }
                    }
                    : new JTable(model);
            configureScrollableTable(table);
            TableRowSorter<AbstractTableModel> sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);

            JTextField search = new JTextField(24);
            JButton searchButton = new JButton("Search");
            JButton clearButton = new JButton("Clear");
            Runnable applySearch = () -> {
                String query = search.getText().trim().toLowerCase();
                if (query.length() < 3) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(new RowFilter<AbstractTableModel, Integer>() {
                        public boolean include(Entry<? extends AbstractTableModel, ? extends Integer> entry) {
                            return tableRowMatches(model, entry.getIdentifier(), query);
                        }
                    });
                }
                if (table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
                status.setText(label + " shown: " + table.getRowCount() + " / " + model.getRowCount());
            };
            search.addKeyListener(new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    applySearch.run();
                }
            });
            searchButton.addActionListener(e -> applySearch.run());
            clearButton.addActionListener(e -> {
                search.setText("");
                applySearch.run();
            });

            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.add(new JLabel("Search"));
            searchPanel.add(search);
            searchPanel.add(searchButton);
            searchPanel.add(clearButton);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(searchPanel, BorderLayout.NORTH);
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            return panel;
        }

        private void configureScrollableTable(JTable table) {
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setFillsViewportHeight(true);
        }

        private boolean tableRowMatches(AbstractTableModel model, int rowIndex, String query) {
            StringBuilder text = new StringBuilder();
            for (int column = 0; column < model.getColumnCount(); column++) {
                Object value = model.getValueAt(rowIndex, column);
                if (value != null) text.append(value).append(' ');
            }
            return text.toString().toLowerCase().contains(query);
        }

        private JPanel pathRow(String label, JTextField field, boolean alsoGameDat) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.add(new JLabel(label), BorderLayout.WEST);
            row.add(field, BorderLayout.CENTER);
            JButton browse = new JButton("...");
            browse.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser(".");
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    if (alsoGameDat) {
                        setInputJar(Path.of(chooser.getSelectedFile().getPath()));
                    } else {
                        field.setText(chooser.getSelectedFile().getPath());
                    }
                }
            });
            row.add(browse, BorderLayout.EAST);
            return row;
        }

        private void setInputJar(Path inputJar) {
            Path absoluteJar = inputJar.toAbsolutePath().normalize();
            jarPath.setText(absoluteJar.toString());
            Path fileName = absoluteJar.getFileName();
            String baseName = fileName == null ? "vddoh" : fileName.toString();
            if (baseName.toLowerCase().endsWith(".jar")) baseName = baseName.substring(0, baseName.length() - 4);
            Path workDir = editorUserPath("temp").resolve(baseName);
            gameDatPath.setText(workDir.resolve("game.dat").toString());
            itemDatPath.setText(workDir.resolve("item.dat").toString());
            Path output = editorUserPath("dist").resolve(baseName + "-patched.jar");
            outputJarPath.setText(output.toString());
            try {
                extractDataFilesFromJar(absoluteJar);
                updateResistanceOverflowPatchState(absoluteJar);
            } catch (Exception ex) {
                showError(this, ex);
            }
        }

        private void extractDataFilesFromJar(Path inputJar) throws IOException {
            Path gameDat = Path.of(gameDatPath.getText());
            Path itemDat = Path.of(itemDatPath.getText());
            Files.createDirectories(gameDat.toAbsolutePath().getParent());
            Files.createDirectories(itemDat.toAbsolutePath().getParent());
            boolean foundGame = false;
            boolean foundItem = false;
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        String lower = name.toLowerCase();
                        if (lower.equals("game.dat") || lower.endsWith("/game.dat")) {
                            Files.write(gameDat, readZipEntry(in));
                            gameDatEntryName = name;
                            foundGame = true;
                        } else if (lower.equals("item.dat") || lower.endsWith("/item.dat")) {
                            Files.write(itemDat, readZipEntry(in));
                            itemDatEntryName = name;
                            foundItem = true;
                        }
                    }
                    in.closeEntry();
                }
            }
            if (!foundGame || !foundItem) {
                throw new IOException("Selected JAR must contain game.dat and item.dat.");
            }
        }

        private void updateResistanceOverflowPatchState(Path inputJar) throws IOException {
            ResistanceOverflowClassPatcher.State state = ResistanceOverflowClassPatcher.state(readJarEntry(inputJar, "g.class"));
            if (state == ResistanceOverflowClassPatcher.State.PATCHED) {
                patchResistanceOverflow.setSelected(true);
                patchResistanceOverflow.setEnabled(false);
                patchResistanceOverflow.setToolTipText("This JAR already contains the resistance overflow bytecode patch.");
            } else if (state == ResistanceOverflowClassPatcher.State.ORIGINAL) {
                patchResistanceOverflow.setSelected(false);
                patchResistanceOverflow.setEnabled(true);
                patchResistanceOverflow.setToolTipText("Patch g.class so overflowed resistance bytes clamp to 100 instead of 0.");
            } else {
                patchResistanceOverflow.setSelected(false);
                patchResistanceOverflow.setEnabled(false);
                patchResistanceOverflow.setToolTipText("Unsupported g.class layout; resistance overflow patch is unavailable for this JAR.");
            }
        }
        private void loadData() {
            try {
                Path inputJar = Path.of(jarPath.getText());
                extractDataFilesFromJar(inputJar);
                updateResistanceOverflowPatchState(inputJar);
                GameData data = GameData.loadFromOriginalClasses(inputJar);
                skillsModel.setRows(data.skillLevels);
                skillEffectsModel.setRows(data.skillLevels.isEmpty() ? new ArrayList<>() : data.skillLevels.get(0).effects);
                talentsModel.setRows(data.talents);
                heroesModel.setRows(data.heroes);
                itemsModel.setRows(data.items);
                itemEffectsModel.setRows(data.items.isEmpty() ? new ArrayList<>() : data.items.get(0).effects);
                monstersModel.setRows(data.monsters);
                statusesModel.setRows(data.statuses);
                status.setText("Loaded " + data.skillLevels.size() + " skill levels, " + data.items.size() + " items");
            } catch (Exception ex) {
                showError(this, ex);
            }
        }

        private void buildPatchedJar() {
            try {
                List<SkillPatch> skillPatches = skillsModel.changedPatches();
                List<TalentPatch> talentPatches = talentsModel.changedPatches();
                List<HeroPatch> heroPatches = heroesModel.changedPatches();
                List<ItemPatch> itemPatches = itemsModel.changedPatches();
                List<StatusPatch> statusPatches = statusesModel.changedPatches();
                boolean classPatchRequested = patchResistanceOverflow.isSelected() && patchResistanceOverflow.isEnabled();
                if (skillPatches.isEmpty() && talentPatches.isEmpty() && heroPatches.isEmpty() && itemPatches.isEmpty() && statusPatches.isEmpty() && !classPatchRequested) {
                    JOptionPane.showMessageDialog(this, "No edits to patch.");
                    return;
                }
                Path gameDat = Path.of(gameDatPath.getText());
                Path itemDat = Path.of(itemDatPath.getText());
                Path inputJar = Path.of(jarPath.getText());
                Path outputJar = Path.of(outputJarPath.getText());
                Files.createDirectories(outputJar.toAbsolutePath().getParent());

                Map<String, byte[]> replacements = new LinkedHashMap<>();
                List<String> summaries = new ArrayList<>();
                if (!skillPatches.isEmpty() || !talentPatches.isEmpty() || !heroPatches.isEmpty() || !statusPatches.isEmpty()) {
                    byte[] data = Files.readAllBytes(gameDat);
                    if (!skillPatches.isEmpty()) summaries.add("skills: " + GameDatSkillPatcher.patch(data, skillPatches));
                    if (!talentPatches.isEmpty()) summaries.add("talents: " + GameDatTalentPatcher.patch(data, talentPatches));
                    if (!heroPatches.isEmpty()) summaries.add("heroes: " + GameDatHeroPatcher.patch(data, heroPatches));
                    if (!statusPatches.isEmpty()) summaries.add("statuses: " + GameDatStatusPatcher.patch(data, statusPatches));
                    Files.write(outputJar.resolveSibling("game.dat"), data);
                    replacements.put(gameDatEntryName, data);
                }
                if (!itemPatches.isEmpty()) {
                    byte[] data = Files.readAllBytes(itemDat);
                    summaries.add("items: " + ItemDatPatcher.patch(data, itemPatches));
                    Files.write(outputJar.resolveSibling("item.dat"), data);
                    replacements.put(itemDatEntryName, data);
                }
                if (classPatchRequested) {
                    byte[] heroClass = readJarEntry(inputJar, "g.class");
                    PatchSummary classSummary = ResistanceOverflowClassPatcher.patch(heroClass);
                    replacements.put("g.class", heroClass);
                    summaries.add("class: resistanceOverflow=" + classSummary.heroResistOverflow + ", skipped=" + classSummary.skipped);
                }
                replaceJarEntries(inputJar, outputJar, replacements);
                String summary = joinLines(summaries);
                status.setText("Wrote " + outputJar + " (" + summary.replace('\n', ';') + ")");
                JOptionPane.showMessageDialog(this, "Patched JAR written:\n" + outputJar + "\n\n" + summary);
            } catch (Exception ex) {
                showError(this, ex);
            }
        }
    }

    private static Path editorUserPath(String child) {
        return Path.of(System.getProperty("user.home"), ".vddoh-editor", child);
    }

    private static final class SkillLevelTableModel extends AbstractTableModel {
        private final String[] columns = {"Skill ID", "Skill", "Level", "Cost", "Shape ID", "Area X", "Area Y", "Range", "Effects", "Notes"};
        private List<SkillLevelRow> rows = new ArrayList<>();

        void setRows(List<SkillLevelRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        void resetEdits() { for (SkillLevelRow row : rows) row.reset(); fireTableDataChanged(); }
        List<SkillEffectRow> effectRows(int rowIndex) { return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).effects : new ArrayList<>(); }
        boolean matchesSearch(int rowIndex, String query) {
            if (rowIndex < 0 || rowIndex >= rows.size()) return false;
            SkillLevelRow row = rows.get(rowIndex);
            StringBuilder text = new StringBuilder();
            text.append(row.skillId).append(' ').append(row.skillName).append(' ').append(row.levelIndex + 1).append(' ').append(row.cost).append(' ').append(row.notes);
            for (SkillEffectRow effect : row.effects) text.append(' ').append(effect.type).append(' ').append(effect.target).append(' ').append(effect.value).append(' ').append(effect.notes);
            return text.toString().toLowerCase().contains(query);
        }
        List<SkillPatch> changedPatches() {
            List<SkillPatch> patches = new ArrayList<>();
            for (SkillLevelRow row : rows) if (row.changed()) patches.add(new SkillPatch(row.skillId, row.levelIndex, row.cost, row.effects));
            return patches;
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public boolean isCellEditable(int row, int column) { return column == 3; }
        public Object getValueAt(int rowIndex, int columnIndex) {
            SkillLevelRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.skillId;
                case 1: return row.skillName;
                case 2: return row.levelIndex + 1;
                case 3: return row.cost;
                case 4: return row.areaShape;
                case 5: return row.areaWidth;
                case 6: return row.areaHeight;
                case 7: return row.range;
                case 8: return row.effects.size();
                case 9: return row.notes;
                default: return "";
            }
        }
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            SkillLevelRow row = rows.get(rowIndex);
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (columnIndex == 3) row.cost = parsed;
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }

    private static final class SkillEffectTableModel extends AbstractTableModel {
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
    private static final class TalentTableModel extends AbstractTableModel {
        private final String[] columns = {"Kind", "Talent Type", "ID", "Talent", "Gameplay Effect", "Lv1 Value", "Lv2 Value", "Lv3 Value", "Lv4 Value", "Amount / Level", "Max Level", "Max Value", "Castable Skill ID", "Castable Skill", "Hero Effect ID", "Global ID", "Unlock Ref", "Status ID", "Resist ID", "Notes"};
        private List<TalentRow> rows = new ArrayList<>();
        void setRows(List<TalentRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        void resetEdits() { for (TalentRow row : rows) row.reset(); fireTableDataChanged(); }
        List<TalentPatch> changedPatches() {
            List<TalentPatch> patches = new ArrayList<>();
            for (TalentRow row : rows) if (row.changed()) patches.add(new TalentPatch(row.group, row.id, row.maxLevel, row.amount, row.globalBonus, row.skillUnlock, row.statusBonus, row.resistanceBonus, row.heroBonus));
            return patches;
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        String columnTooltip(int column) {
            if (column >= 5 && column <= 8) return "Read-only gameplay preview for learned talent level " + (column - 4) + ". Original data stores one amount per level, not four independent values.";
            if (column == 9) return "Stored amount added per learned level. Deadly Might uses this on top of the base 50% critical-damage bonus.";
            if (column == 10) return "Maximum learnable dots/levels for this talent. J2ME data can store 1 to 4 here.";
            if (column == 11) return "Read-only preview at Max Level using the same per-level amount formula.";
            if (column == 14) return "Hero passive effect id: 1 regen, 2 movement, 3 crit chance, 4 crit damage, 5 evasion/reflex.";
            if (column >= 15 && column <= 18) return "Existing optional binary link. Safe to edit when the original row already has a value; adding/removing links would change record length.";
            return null;
        }
        public boolean isCellEditable(int rowIndex, int column) {
            TalentRow row = rows.get(rowIndex);
            return column == 9 || column == 10 || column == 14 || (column == 15 && row.originalGlobalBonus > 0) || (column == 16 && row.originalSkillUnlock > 0) || (column == 17 && row.originalStatusBonus > 0) || (column == 18 && row.originalResistanceBonus > 0);
        }
        public Object getValueAt(int rowIndex, int columnIndex) {
            TalentRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.group ? "Group" : "Hero";
                case 1: return row.talentType();
                case 2: return row.id;
                case 3: return row.name;
                case 4: return row.effectName();
                case 5: return row.levelValueText(1);
                case 6: return row.levelValueText(2);
                case 7: return row.levelValueText(3);
                case 8: return row.levelValueText(4);
                case 9: return row.amount;
                case 10: return row.maxLevel;
                case 11: return row.levelValueText(row.maxLevel);
                case 12: return row.castableSkillIdText();
                case 13: return row.unlockedSkillName;
                case 14: return row.heroBonus;
                case 15: return row.globalBonus == 0 ? "" : row.globalBonus;
                case 16: return row.skillUnlock == 0 ? "" : row.skillUnlock;
                case 17: return row.statusBonus == 0 ? "" : row.statusBonus;
                case 18: return row.resistanceBonus == 0 ? "" : row.resistanceBonus;
                case 19: return row.notes;
                default: return "";
            }
        }
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            TalentRow row = rows.get(rowIndex);
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (columnIndex == 9) row.amount = parsed;
            else if (columnIndex == 10) row.maxLevel = parsed;
            else if (columnIndex == 14) row.heroBonus = parsed;
            else if (columnIndex == 15) row.globalBonus = parsed;
            else if (columnIndex == 16) row.skillUnlock = parsed;
            else if (columnIndex == 17) row.statusBonus = parsed;
            else if (columnIndex == 18) row.resistanceBonus = parsed;
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }
    private static final class HeroTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Hero", "Base HP", "Base Resource", "Base Attack", "Base Defense", "Base Move", "Base Regen", "Strength Start", "Strength Lv99 Target", "Strength Growth Curve", "Spirit Start", "Spirit Lv99 Target", "Spirit Growth Curve", "Vitality Start", "Vitality Lv99 Target", "Vitality Growth Curve", "Speed Start", "Speed Lv99 Target", "Speed Growth Curve", "Level Cap", "STR @ Cap", "SPI @ Cap", "VIT @ Cap", "SPD @ Cap", "Base Crit %", "Base Crit Dmg %", "Base Evasion %", "Notes"};
        private List<HeroRow> rows = new ArrayList<>();
        void setRows(List<HeroRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        void resetEdits() { for (HeroRow row : rows) row.reset(); fireTableDataChanged(); }
        List<HeroPatch> changedPatches() {
            List<HeroPatch> patches = new ArrayList<>();
            for (HeroRow row : rows) if (row.changed()) patches.add(new HeroPatch(row.id, row.strength, row.spirit, row.vitality, row.speed, row.levelCap, row.baseCritChance, row.baseCritDamage));
            return patches;
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        String columnTooltip(int column) {
            if (column == 2) return "Estimated level-1 max HP from starting Strength/Vitality before equipment and status effects.";
            if (column == 3) return "Estimated level-1 max Blood/Soul from starting Spirit/Vitality before equipment and status effects.";
            if (column == 4) return "Estimated level-1 attack from starting Strength before equipment.";
            if (column == 5) return "Estimated level-1 defense from starting Speed/Strength before equipment.";
            if (column == 6) return "Estimated movement range from starting Speed.";
            if (column >= 8 && column <= 19) {
                int part = (column - 8) % 3;
                if (part == 0) return "Level-1 value. This is the visible base stat before level growth and equipment.";
                if (part == 1) return "Growth target used by the formula at level 99, not the level-30 cap.";
                return "Growth curve. 0 back-loads growth, 100 is roughly linear, higher values front-load growth before level cap.";
            }
            if (column == 20) return "Maximum hero level. Vanilla heroes cap at 30.";
            if (column >= 21 && column <= 24) return "Read-only preview of the grown stat at Level Cap using the game's integer formula.";
            if (column == 25) return "Base physical critical hit chance. Final chance = this value + Find Weaknesses bonus.";
            if (column == 26) return "Base critical damage bonus. Final bonus = this value + Deadly Might bonus, capped by bytecode at 250.";
            if (column == 27) return "Read-only bytecode constant. Final evasion = 5 + Reflexes bonus; per-hero data does not store this.";
            return null;
        }
        public boolean isCellEditable(int row, int column) { return (column >= 8 && column <= 20) || column == 25 || column == 26; }
        public Object getValueAt(int rowIndex, int columnIndex) {
            HeroRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.id;
                case 1: return row.name;
                case 2: return row.baseHp();
                case 3: return row.baseResource();
                case 4: return row.baseAttack();
                case 5: return row.baseDefense();
                case 6: return row.baseMove();
                case 7: return row.baseRegen();
                case 8: return row.strength.start;
                case 9: return row.strength.target;
                case 10: return row.strength.curve;
                case 11: return row.spirit.start;
                case 12: return row.spirit.target;
                case 13: return row.spirit.curve;
                case 14: return row.vitality.start;
                case 15: return row.vitality.target;
                case 16: return row.vitality.curve;
                case 17: return row.speed.start;
                case 18: return row.speed.target;
                case 19: return row.speed.curve;
                case 20: return row.levelCap;
                case 21: return row.strengthAtCap();
                case 22: return row.spiritAtCap();
                case 23: return row.vitalityAtCap();
                case 24: return row.speedAtCap();
                case 25: return row.baseCritChance;
                case 26: return row.baseCritDamage;
                case 27: return row.baseEvasion();
                case 28: return row.notes;
                default: return "";
            }
        }
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            HeroRow row = rows.get(rowIndex);
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (columnIndex >= 8 && columnIndex <= 19) {
                StatCurve stat = row.stat((columnIndex - 8) / 3);
                switch ((columnIndex - 8) % 3) {
                    case 0: stat.start = parsed; break;
                    case 1: stat.target = parsed; break;
                    case 2: stat.curve = parsed; break;
                }
            } else if (columnIndex == 20) row.levelCap = parsed;
            else if (columnIndex == 25) row.baseCritChance = parsed;
            else if (columnIndex == 26) row.baseCritDamage = parsed;
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }
    private static final class ItemTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Item", "Slot", "Allowed", "Price", "Icon", "HP Restore", "Resource Restore", "HP Bonus", "Resource Bonus", "Reach", "Notes"};
        private List<ItemRow> rows = new ArrayList<>();
        void setRows(List<ItemRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        void resetEdits() { for (ItemRow row : rows) row.reset(); fireTableDataChanged(); }
        List<ItemEffectRow> effectRows(int rowIndex) { return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).effects : new ArrayList<>(); }
        boolean matchesSearch(int rowIndex, String query) {
            if (rowIndex < 0 || rowIndex >= rows.size()) return false;
            ItemRow row = rows.get(rowIndex);
            StringBuilder text = new StringBuilder();
            text.append(row.id).append(' ')
                    .append(row.name).append(' ')
                    .append(row.slotLabel).append(' ')
                    .append(row.allowedClasses).append(' ')
                    .append(row.price).append(' ')
                    .append(row.icon).append(' ')
                    .append(row.hpRestore).append(' ')
                    .append(row.resourceRestore).append(' ')
                    .append(row.hpBonus).append(' ')
                    .append(row.resourceBonus).append(' ')
                    .append(row.weaponReach).append(' ')
                    .append(row.notes);
            for (ItemEffectRow effect : row.effects) {
                text.append(' ')
                        .append(effect.side).append(' ')
                        .append(effect.type).append(' ')
                        .append(effect.target).append(' ')
                        .append(effect.value).append(' ')
                        .append(effect.extra).append(' ')
                        .append(effect.raw);
            }
            return text.toString().toLowerCase().contains(query);
        }
        List<ItemPatch> changedPatches() {
            List<ItemPatch> patches = new ArrayList<>();
            for (ItemRow row : rows) if (row.changed()) patches.add(new ItemPatch(row.id, row.price, row.icon, row.hpRestore, row.resourceRestore));
            return patches;
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public boolean isCellEditable(int row, int column) { return column == 4 || column == 5 || column == 6 || column == 7; }
        public Object getValueAt(int rowIndex, int columnIndex) {
            ItemRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.id;
                case 1: return row.name;
                case 2: return row.slotLabel;
                case 3: return row.allowedClasses;
                case 4: return row.price;
                case 5: return row.icon;
                case 6: return row.hpRestore;
                case 7: return row.resourceRestore;
                case 8: return row.hpBonus;
                case 9: return row.resourceBonus;
                case 10: return row.weaponReach;
                case 11: return row.notes;
                default: return "";
            }
        }
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ItemRow row = rows.get(rowIndex);
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (columnIndex == 4) row.price = parsed;
            else if (columnIndex == 5) row.icon = parsed;
            else if (columnIndex == 6) row.hpRestore = parsed;
            else if (columnIndex == 7) row.resourceRestore = parsed;
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }

    private static final class ItemEffectTableModel extends AbstractTableModel {
        private final String[] columns = {"Effect Side", "Effect Type", "Target", "Value", "Chance/Extra", "Raw"};
        private List<ItemEffectRow> rows = new ArrayList<>();
        void setRows(List<ItemEffectRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public Object getValueAt(int rowIndex, int columnIndex) {
            ItemEffectRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.side;
                case 1: return row.type;
                case 2: return row.target;
                case 3: return row.value;
                case 4: return row.extra;
                case 5: return row.raw;
                default: return "";
            }
        }
    }

    private static final class StatusTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Status", "Duration", "Expire %", "Icon", "Notes"};
        private List<StatusRow> rows = new ArrayList<>();
        void setRows(List<StatusRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        void resetEdits() { for (StatusRow row : rows) row.reset(); fireTableDataChanged(); }
        List<StatusPatch> changedPatches() {
            List<StatusPatch> patches = new ArrayList<>();
            for (StatusRow row : rows) if (row.changed()) patches.add(new StatusPatch(row.id, row.duration, row.expireChance, row.icon));
            return patches;
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public boolean isCellEditable(int row, int column) { return column == 2 || column == 3 || column == 4; }
        public Object getValueAt(int rowIndex, int columnIndex) {
            StatusRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.id;
                case 1: return row.name;
                case 2: return row.duration;
                case 3: return row.expireChance;
                case 4: return row.icon;
                case 5: return row.notes;
                default: return "";
            }
        }
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            StatusRow row = rows.get(rowIndex);
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (columnIndex == 2) row.duration = parsed;
            else if (columnIndex == 3) row.expireChance = parsed;
            else if (columnIndex == 4) row.icon = parsed;
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }

    private static final class SimpleNamedTableModel extends AbstractTableModel {
        private final String kind;
        private List<NamedRow> rows = new ArrayList<>();
        SimpleNamedTableModel(String kind) { this.kind = kind; }
        void setRows(List<NamedRow> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return 3; }
        public String getColumnName(int column) { return column == 0 ? "ID" : column == 1 ? kind : "Notes"; }
        public Object getValueAt(int rowIndex, int columnIndex) {
            NamedRow row = rows.get(rowIndex);
            return columnIndex == 0 ? row.id : columnIndex == 1 ? row.name : row.notes;
        }
    }

    private static final class GameData {
        final List<SkillLevelRow> skillLevels = new ArrayList<>();
        final List<TalentRow> talents = new ArrayList<>();
        final List<HeroRow> heroes = new ArrayList<>();
        final List<ItemRow> items = new ArrayList<>();
        final List<NamedRow> monsters = new ArrayList<>();
        final List<StatusRow> statuses = new ArrayList<>();

        static GameData loadFromOriginalClasses(Path inputJar) throws Exception {
            try (URLClassLoader loader = selectedJarClassLoader(inputJar)) {
                Class<?> vd = Class.forName("VD", true, loader);
                Class<?> game = Class.forName("j", true, loader);
                Class<?> statusClass = Class.forName("a", true, loader);
                Class<?> skillClass = Class.forName("f", true, loader);
                Class<?> itemClass = Class.forName("k", true, loader);
                Class<?> monsterClass = Class.forName("b", true, loader);
                Class<?> heroClass = Class.forName("g", true, loader);
                Class<?> talentClass = Class.forName("l", true, loader);

                vd.getDeclaredConstructor().newInstance();
                setFirstStaticBoolean(vd, true);
                game.getMethod("a", Boolean.TYPE).invoke(null, false);
                Method decode = game.getMethod("a", byte[].class);

                GameData data = new GameData();
                Object[] statuses = staticArray(statusClass, statusClass, 0);
                Object[] skills = staticArray(skillClass, skillClass, 0);
                byte[][] damageGroups = staticByte2d(itemClass, 0);
                String[] skillNames = decodedNames(skills, decode);
                for (int i = 0; i < skills.length; i++) appendSkillRows(data.skillLevels, i, skills[i], statuses, damageGroups, decode);
                appendItemRows(data.items, staticArray(itemClass, itemClass, 0), statuses, decode);
                appendStatusRows(data.statuses, statuses, decode);
                appendNamed(data.monsters, staticArray(monsterClass, monsterClass, 0), 1, decode, "monsters");
                appendHeroRows(data.heroes, largerStaticArray(heroClass, heroClass), decode);
                appendTalentRows(data.talents, staticArray(talentClass, talentClass, 1), true, skillNames, decode);
                appendTalentRows(data.talents, staticArray(talentClass, talentClass, 0), false, skillNames, decode);
                return data;
            }
        }



        private static void appendTalentRows(List<TalentRow> rows, Object[] talents, boolean group, String[] skillNames, Method decode) throws Exception {
            for (int i = 0; talents != null && i < talents.length; i++) {
                Object talent = talents[i];
                int levelByte = u8(raw(talent, 1));
                int maxLevel = levelByte & 0x0f;
                int currentLevel = (levelByte >> 4) & 0x0f;
                int amount = u8(raw(talent, 2));
                int globalBonus = u8(raw(talent, 3));
                int skillUnlock = u8(raw(talent, 4));
                int statusBonus = u8(raw(talent, 5));
                int resistanceBonus = u8(raw(talent, 6));
                int heroBonus = u8(raw(talent, 7));
                rows.add(new TalentRow(group, i, decodeName(talent, 0, decode), maxLevel, currentLevel, amount, globalBonus, skillUnlock, VddohDataEditor.skillNameForTalentLink(skillUnlock, skillNames), statusBonus, resistanceBonus, heroBonus));
            }
        }
        private static void appendHeroRows(List<HeroRow> rows, Object[] heroes, Method decode) throws Exception {
            for (int i = 0; heroes != null && i < heroes.length; i++) {
                Object hero = heroes[i];
                int baseCrit = intValue(raw(hero, 32));
                rows.add(new HeroRow(
                        i,
                        decodeName(hero, 0, decode),
                        StatCurve.fromPacked(intValue(raw(hero, 15))),
                        StatCurve.fromPacked(intValue(raw(hero, 16))),
                        StatCurve.fromPacked(intValue(raw(hero, 17))),
                        StatCurve.fromPacked(intValue(raw(hero, 18))),
                        intValue(raw(hero, 24)) & 0xff,
                        (baseCrit >> 8) & 0xff,
                        baseCrit & 0xff,
                        "game.dat: inferred core stats and packed base crit"));
            }
        }
        private static void appendItemRows(List<ItemRow> rows, Object[] items, Object[] statuses, Method decode) throws Exception {
            String[] statusNames = decodedNames(statuses, decode);
            for (int i = 0; items != null && i < items.length; i++) {
                Object item = items[i];
                int rawType = u8(raw(item, 0));
                int category = (rawType >> 4) & 0x0f;
                int subtype = rawType & 0x0f;
                int price = intValue(raw(item, 4));
                int icon = u8(raw(item, 3));
                int hpRestore = intValue(raw(item, 21));
                int resourceRestore = intValue(raw(item, 22));
                byte[] allowed = byteArray(raw(item, 7));
                int packedAttackDefense = shortValue(raw(item, 10));
                int hpBonus = (packedAttackDefense >> 8) & 0xff;
                int resourceBonus = packedAttackDefense & 0xff;
                int weaponReach = category == 3 ? u8(raw(item, 18)) & 0x0f : 0;
                int weaponMode = category == 3 ? (u8(raw(item, 18)) >> 5) & 7 : 0;
                List<ItemEffectRow> effects = decodeItemEffects(item, category, statusNames);
                rows.add(new ItemRow(
                        i,
                        decodeName(item, 0, decode),
                        rawType,
                        category,
                        subtype,
                        slotLabel(category, subtype),
                        allowedClasses(allowed),
                        price,
                        icon,
                        hpRestore,
                        resourceRestore,
                        hpBonus,
                        resourceBonus,
                        weaponReach,
                        weaponMode,
                        effects,
                        itemNotes(category, subtype, weaponReach, weaponMode)));
            }
        }
        private static void appendStatusRows(List<StatusRow> rows, Object[] statuses, Method decode) throws Exception {
            for (int i = 0; statuses != null && i < statuses.length; i++) {
                Object status = statuses[i];
                rows.add(new StatusRow(
                        i,
                        decodeName(status, 0, decode),
                        u8(raw(status, 3)),
                        signedChance(u8(raw(status, 5))),
                        u8(raw(status, 14)),
                        "game.dat"));
            }
        }
        private static void appendSkillRows(List<SkillLevelRow> rows, int skillId, Object skill, Object[] statuses, byte[][] damageGroups, Method decode) throws Exception {
            String name = decodeName(skill, 0, decode);
            Object[] levels = objectArray(raw(skill, 6));
            int[] baseDamage = intArray(raw(skill, 0));
            short[] baseStatuses = shortArray(raw(skill, 1));
            for (int level = 0; level < levels.length; level++) {
                Object h = levels[level];
                short[] levelDamage = nullableShortArray(raw(h, 7));
                byte[] levelChances = byteArray(raw(h, 8));
                int packedShapeRange = u8(raw(h, 9));
                int packedArea = u8(raw(h, 10));
                List<SkillEffectRow> effects = new ArrayList<>();
                for (int i = 0; i < baseDamage.length; i++) {
                    int kind = (baseDamage[i] >> 16) & 0xff;
                    int value = levelDamage == null ? baseDamage[i] & 0xffff : levelDamage[i] & 0xffff;
                    effects.add(new SkillEffectRow("Damage", i, kind, kind >= 0 && kind < damageGroups.length ? decodeBytes(damageGroups[kind], decode) : statName(kind), value, level == 0 || levelDamage != null, levelDamage == null && level > 0 ? "inherited from level 1" : "own value"));
                }
                for (int i = 0; i < baseStatuses.length; i++) {
                    int statusId = (baseStatuses[i] >> 8) & 0xff;
                    int chance = signedChance(levelChances == null ? baseStatuses[i] & 0xff : levelChances[i] & 0xff);
                    effects.add(new SkillEffectRow(chance < 0 ? "Remove Status" : "Inflict Status", i, statusId, statusId >= 0 && statusId < statuses.length ? decodeName(statuses[statusId], 0, decode) : "Status " + statusId, chance, level == 0 || levelChances != null, levelChances == null && level > 0 ? "inherited from level 1" : "own chance"));
                }
                rows.add(new SkillLevelRow(
                        skillId,
                        name,
                        level,
                        u8(raw(h, 3)),
                        (packedShapeRange >> 4) & 7,
                        ((packedArea >> 4) & 0x0f) + 1,
                        (packedArea & 0x0f) + 1,
                        packedShapeRange & 0x0f,
                        (packedShapeRange & 0x80) != 0,
                        effects));
            }
        }    }

    private static final class SkillLevelRow {
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

    private static final class SkillEffectRow {
        final String type;
        final int index;
        final int targetId;
        final String target;
        final int originalValue;
        final boolean editable;
        final String notes;
        int value;
        SkillEffectRow(String type, int index, int targetId, String target, int value, boolean editable, String notes) {
            this.type = type;
            this.index = index;
            this.targetId = targetId;
            this.target = target;
            this.value = this.originalValue = value;
            this.editable = editable;
            this.notes = notes;
        }
        boolean isStatus() { return "Inflict Status".equals(type) || "Remove Status".equals(type); }
        int displayValue() { return "Remove Status".equals(type) ? Math.abs(value) : value; }
        void setDisplayValue(int value) { this.value = "Remove Status".equals(type) ? -Math.abs(value) : value; }
        int encodedValue() { return value; }
        boolean changed() { return editable && value != originalValue; }
        void reset() { value = originalValue; }
    }
    private static final class StatCurve {
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

    private static final class HeroRow {
        final int id;
        final String name;
        final StatCurve strength;
        final StatCurve spirit;
        final StatCurve vitality;
        final StatCurve speed;
        final int originalLevelCap;
        final int originalBaseCritChance;
        final int originalBaseCritDamage;
        final String notes;
        int levelCap;
        int baseCritChance;
        int baseCritDamage;
        HeroRow(int id, String name, StatCurve strength, StatCurve spirit, StatCurve vitality, StatCurve speed, int levelCap, int baseCritChance, int baseCritDamage, String notes) {
            this.id = id;
            this.name = name;
            this.strength = strength;
            this.spirit = spirit;
            this.vitality = vitality;
            this.speed = speed;
            this.levelCap = this.originalLevelCap = levelCap;
            this.baseCritChance = this.originalBaseCritChance = baseCritChance;
            this.baseCritDamage = this.originalBaseCritDamage = baseCritDamage;
            this.notes = notes;
        }
        StatCurve stat(int index) {
            switch (index) {
                case 0: return strength;
                case 1: return spirit;
                case 2: return vitality;
                case 3: return speed;
                default: throw new IllegalArgumentException("Unknown hero stat " + index);
            }
        }
        int baseHp() { return (vitality.start * 70 + strength.start * 30) * 12 / 100; }
        int baseResource() { return (spirit.start * 70 + vitality.start * 30) * 12 / 100; }
        int baseAttack() { return Math.max(0, strength.start * 5 - 9); }
        int baseDefense() { return Math.max(0, speed.start * 3 + strength.start - 18); }
        int baseMove() { return 2 + speed.start / 5; }
        int baseRegen() { return 1; }
        int baseEvasion() { return 5; }
        int previewLevel() { return Math.max(1, levelCap); }
        int strengthAtCap() { return strength.valueAtLevel(previewLevel()); }
        int spiritAtCap() { return spirit.valueAtLevel(previewLevel()); }
        int vitalityAtCap() { return vitality.valueAtLevel(previewLevel()); }
        int speedAtCap() { return speed.valueAtLevel(previewLevel()); }
        boolean changed() { return strength.changed() || spirit.changed() || vitality.changed() || speed.changed() || levelCap != originalLevelCap || baseCritChance != originalBaseCritChance || baseCritDamage != originalBaseCritDamage; }
        void reset() {
            strength.reset();
            spirit.reset();
            vitality.reset();
            speed.reset();
            levelCap = originalLevelCap;
            baseCritChance = originalBaseCritChance;
            baseCritDamage = originalBaseCritDamage;
        }
    }
    private static final class TalentRow {
        final boolean group;
        final int id;
        final String name;
        final int currentLevel;
        final int originalMaxLevel;
        final int originalAmount;
        final int originalGlobalBonus;
        final int originalSkillUnlock;
        final int originalStatusBonus;
        final int originalResistanceBonus;
        final int originalHeroBonus;
        final String unlockedSkillName;
        final String notes;
        int maxLevel;
        int amount;
        int globalBonus;
        int skillUnlock;
        int statusBonus;
        int resistanceBonus;
        int heroBonus;
        TalentRow(boolean group, int id, String name, int maxLevel, int currentLevel, int amount, int globalBonus, int skillUnlock, String unlockedSkillName, int statusBonus, int resistanceBonus, int heroBonus) {
            this.group = group;
            this.id = id;
            this.name = name;
            this.maxLevel = this.originalMaxLevel = maxLevel;
            this.currentLevel = currentLevel;
            this.amount = this.originalAmount = amount;
            this.globalBonus = this.originalGlobalBonus = globalBonus;
            this.skillUnlock = this.originalSkillUnlock = skillUnlock;
            this.unlockedSkillName = unlockedSkillName;
            this.statusBonus = this.originalStatusBonus = statusBonus;
            this.resistanceBonus = this.originalResistanceBonus = resistanceBonus;
            this.heroBonus = this.originalHeroBonus = heroBonus;
            this.notes = talentNotes(heroBonus, skillUnlock, unlockedSkillName, statusBonus, resistanceBonus, globalBonus, currentLevel);
        }
        String talentType() {
            if (group) return "Group Talent";
            if (skillUnlock > 0) return "Hero Spell Unlock";
            if (heroBonus > 0) return "Passive Hero Bonus";
            if (resistanceBonus > 0) return "Resistance Bonus";
            if (statusBonus > 0) return "Status Bonus";
            if (globalBonus > 0) return "Global Bonus";
            return "Unused/Unknown";
        }
        String castableSkillIdText() { return skillUnlock > 0 ? String.valueOf(skillUnlock - 1) : ""; }
        String effectName() {
            if (skillUnlock > 0) return "Unlock castable skill";
            if (heroBonus > 0) return heroBonusName(heroBonus);
            if (statusBonus > 0) return "Status bonus " + statusBonus;
            if (resistanceBonus > 0) return resistanceTalentName(name, resistanceBonus);
            if (globalBonus > 0) return globalTalentName(globalBonus);
            return "Unknown";
        }
        String levelValueText(int level) {
            if (level < 1 || level > maxLevel) return "";
            if (skillUnlock > 0) return "";
            if (heroBonus > 0 || statusBonus > 0 || resistanceBonus > 0 || globalBonus > 0) return String.valueOf(levelValue(level));
            return "";
        }
        int levelValue(int level) {
            return passiveDisplayBase() + amount * level;
        }
        int passiveDisplayBase() {
            return heroBonus == 4 ? 50 : 0;
        }
        boolean changed() {
            return maxLevel != originalMaxLevel || amount != originalAmount || globalBonus != originalGlobalBonus || skillUnlock != originalSkillUnlock || statusBonus != originalStatusBonus || resistanceBonus != originalResistanceBonus || heroBonus != originalHeroBonus;
        }
        void reset() {
            maxLevel = originalMaxLevel;
            amount = originalAmount;
            globalBonus = originalGlobalBonus;
            skillUnlock = originalSkillUnlock;
            statusBonus = originalStatusBonus;
            resistanceBonus = originalResistanceBonus;
            heroBonus = originalHeroBonus;
        }
    }    private static final class NamedRow {
        final int id;
        final String name;
        final String notes;
        NamedRow(int id, String name, String notes) {
            this.id = id;
            this.name = name;
            this.notes = notes;
        }
    }

    private static final class ItemRow {
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

    private static final class ItemEffectRow {
        final String side;
        final String type;
        final String target;
        final String value;
        final String extra;
        final String raw;
        ItemEffectRow(String side, String type, String target, String value, String extra, String raw) {
            this.side = side;
            this.type = type;
            this.target = target;
            this.value = value;
            this.extra = extra;
            this.raw = raw;
        }
    }
    private static final class StatusRow {
        final int id;
        final String name;
        final int originalDuration;
        final int originalExpireChance;
        final int originalIcon;
        final String notes;
        int duration;
        int expireChance;
        int icon;
        StatusRow(int id, String name, int duration, int expireChance, int icon, String notes) {
            this.id = id;
            this.name = name;
            this.duration = this.originalDuration = duration;
            this.expireChance = this.originalExpireChance = expireChance;
            this.icon = this.originalIcon = icon;
            this.notes = notes;
        }
        boolean changed() { return duration != originalDuration || expireChance != originalExpireChance || icon != originalIcon; }
        void reset() {
            duration = originalDuration;
            expireChance = originalExpireChance;
            icon = originalIcon;
        }
    }

    private static final class SkillPatch {
        final int skillId;
        final int levelIndex;
        final int cost;
        final List<SkillEffectRow> effects;
        SkillPatch(int skillId, int levelIndex, int cost, List<SkillEffectRow> effects) {
            this.skillId = skillId;
            this.levelIndex = levelIndex;
            this.cost = cost;
            this.effects = effects;
        }
    }
    private static final class TalentPatch {
        final boolean group;
        final int talentId;
        final int maxLevel;
        final int amount;
        final int globalBonus;
        final int skillUnlock;
        final int statusBonus;
        final int resistanceBonus;
        final int heroBonus;
        TalentPatch(boolean group, int talentId, int maxLevel, int amount, int globalBonus, int skillUnlock, int statusBonus, int resistanceBonus, int heroBonus) {
            this.group = group;
            this.talentId = talentId;
            this.maxLevel = maxLevel;
            this.amount = amount;
            this.globalBonus = globalBonus;
            this.skillUnlock = skillUnlock;
            this.statusBonus = statusBonus;
            this.resistanceBonus = resistanceBonus;
            this.heroBonus = heroBonus;
        }
    }    private static final class HeroPatch {
        final int heroId;
        final StatCurve strength;
        final StatCurve spirit;
        final StatCurve vitality;
        final StatCurve speed;
        final int levelCap;
        final int baseCritChance;
        final int baseCritDamage;
        HeroPatch(int heroId, StatCurve strength, StatCurve spirit, StatCurve vitality, StatCurve speed, int levelCap, int baseCritChance, int baseCritDamage) {
            this.heroId = heroId;
            this.strength = strength;
            this.spirit = spirit;
            this.vitality = vitality;
            this.speed = speed;
            this.levelCap = levelCap;
            this.baseCritChance = baseCritChance;
            this.baseCritDamage = baseCritDamage;
        }
    }
    private static final class ItemPatch {
        final int itemId;
        final int price;
        final int icon;
        final int hpRestore;
        final int resourceRestore;
        ItemPatch(int itemId, int price, int icon, int hpRestore, int resourceRestore) {
            this.itemId = itemId;
            this.price = price;
            this.icon = icon;
            this.hpRestore = hpRestore;
            this.resourceRestore = resourceRestore;
        }
    }

    private static final class StatusPatch {
        final int statusId;
        final int duration;
        final int expireChance;
        final int icon;
        StatusPatch(int statusId, int duration, int expireChance, int icon) {
            this.statusId = statusId;
            this.duration = duration;
            this.expireChance = expireChance;
            this.icon = icon;
        }
    }

    private static final class PatchSummary {
        int cost;
        int damage;
        int status;
        int price;
        int icon;
        int hp;
        int resource;
        int duration;
        int expire;
        int heroStats;
        int heroSeeds;
        int heroResistOverflow;
        int talentAmount;
        int skipped;
        public String toString() {
            List<String> parts = new ArrayList<>();
            if (cost != 0) parts.add("cost=" + cost);
            if (damage != 0) parts.add("damage=" + damage);
            if (status != 0) parts.add("status=" + status);
            if (price != 0) parts.add("price=" + price);
            if (icon != 0) parts.add("icon=" + icon);
            if (hp != 0) parts.add("hp=" + hp);
            if (resource != 0) parts.add("resource=" + resource);
            if (duration != 0) parts.add("duration=" + duration);
            if (expire != 0) parts.add("expire=" + expire);
            if (heroStats != 0) parts.add("heroStats=" + heroStats);
            if (heroSeeds != 0) parts.add("heroCrit=" + heroSeeds);
            if (heroResistOverflow != 0) parts.add("heroResistOverflow=" + heroResistOverflow);
            if (talentAmount != 0) parts.add("talentAmount=" + talentAmount);
            parts.add("skipped=" + skipped);
            return joinParts(parts);
        }
    }

    private static final class ResistanceOverflowClassPatcher {
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
    private static final class GameDatSkillPatcher {
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

    private static final class GameDatTalentPatcher {
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
    }    private static final class GameDatHeroPatcher {
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
    private static final class ItemDatPatcher {
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

    private static final class GameDatStatusPatcher {
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

    private static final class TalentOffsets {
        int metaOffset = -1;
        int amountOffset = -1;
        int globalOffset = -1;
        int skillOffset = -1;
        int statusOffset = -1;
        int resistanceOffset = -1;
    }
    private static final class TalentSection {
        final TalentOffsets[] offsets;
        final int nextOffset;
        TalentSection(TalentOffsets[] offsets, int nextOffset) {
            this.offsets = offsets;
            this.nextOffset = nextOffset;
        }
    }

    private static final class TalentSections {
        final TalentOffsets[] group;
        final TalentOffsets[] hero;
        TalentSections(TalentOffsets[] group, TalentOffsets[] hero) {
            this.group = group;
            this.hero = hero;
        }
    }
    private static final class HeroOffsets {
        int statOffset;
        int seedOffset;
    }

    private static final class ItemOffsets {
        int priceOffset = -1;
        int iconOffset = -1;
        int hpRestoreOffset = -1;
        int resourceRestoreOffset = -1;
    }

    private static final class StatusOffsets {
        int durationOffset = -1;
        int expireOffset = -1;
        int iconOffset = -1;
    }
    private static final class LevelOffsets {
        int costOffset;
        int damageOffset;
        int damageCount;
        int statusOffset;
        int statusCount;
        LevelOffsets(int costOffset, int damageOffset, int damageCount, int statusOffset, int statusCount) {
            this.costOffset = costOffset;
            this.damageOffset = damageOffset;
            this.damageCount = damageCount;
            this.statusOffset = statusOffset;
            this.statusCount = statusCount;
        }
    }

    private static void appendNamed(List<NamedRow> out, Object[] values, int nameOrdinal, Method decode, String notes) throws Exception {
        for (int i = 0; values != null && i < values.length; i++) {
            out.add(new NamedRow(i, decodeName(values[i], nameOrdinal, decode), notes));
        }
    }

    private static int skipDamageGroups(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int i = 0; i < count; i++) n += 1 + (data[n] & 0x7f);
        return n;
    }

    private static int skipStatuses(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int i = 0; i < count; i++) {
            int nameLen = data[n] & 0x1f;
            n += 1 + nameLen;
            boolean specialFlag = (data[n] & 0x80) != 0;
            n++;
            if (i > 0) {
                int flags = u8(data[n++]);
                if ((flags & 0x80) != 0) n++;
                if ((flags & 0x40) != 0) n += 2;
                if ((flags & 0x20) != 0) n++;
                if ((flags & 0x10) != 0) n++;
                if ((flags & 8) != 0) n++;
                if ((flags & 4) != 0) n++;
                if ((flags & 2) != 0) n++;
                if ((flags & 1) != 0) n++;
                n++;
            }
            if (i <= 0) continue;
            n++;
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
        return n;
    }


    private static int heroStartOffset(byte[] data) {
        int n = 13 + u16(data, 11) * 5;
        n = skipDamageGroups(data, n);
        n = skipStatuses(data, n);
        n = skipSkills(data, n);
        return skipMonsters(data, n);
    }

    private static int skipSkills(byte[] data, int n) {
        int skillCount = u8(data[n++]);
        for (int skillId = 0; skillId < skillCount; skillId++) {
            int nameLen = data[n] & 0x1f;
            n += 1 + nameLen;
            int header = u8(data[n++]);
            int levelCount = ((header >> 6) & 3) + 1;
            int inheritedFlags = header & 7;
            n++;
            int packedUsability = u8(data[n++]);
            inheritedFlags = inheritedFlags | ((packedUsability & 1) << 3);
            if ((packedUsability & 8) != 0) n++;
            n++;
            if ((inheritedFlags & 1) != 0) n += 2;
            int damageCount = u8(data[n++]);
            n += damageCount * 3;
            int statusCount = 0;
            if ((inheritedFlags & 8) != 0) {
                statusCount = u8(data[n++]);
                n += statusCount * 2;
            }
            n += 2;
            if ((inheritedFlags & 4) != 0) n += 2;
            if ((inheritedFlags & 2) != 0) n += 2;
            n++;
            for (int level = 1; level < levelCount; level++) {
                int flags = u8(data[n++]);
                int flags2 = u8(data[n++]);
                if ((flags & 8) != 0) n++;
                if ((flags & 0x80) != 0 && (flags2 & 0x80) != 0) n++;
                if ((flags & 0x40) != 0) n++;
                if ((flags & 0x20) != 0 && (flags2 & 8) != 0) n += 2;
                if ((flags & 0x10) != 0) n++;
                if ((flags2 & 0x10) != 0 || (flags & 4) != 0) n++;
                if ((flags & 4) != 0) n++;
                if ((flags2 & 4) != 0) n++;
                else if ((flags & 2) != 0) n += damageCount * 2;
                if ((flags2 & 2) != 0) n++;
                else if ((flags & 1) != 0) n += statusCount;
                n += 2;
            }
        }
        return n;
    }

    private static int skipHeroes(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int heroId = 0; heroId < count; heroId++) {
            int nameLen = data[n] & 0x7f;
            n += 1 + nameLen;
            n += 11;
            n += 3;
            n++;
            n++;
            int seedOffset = n;
            n += 3;
            n += 3;
            for (int slot = 0; slot < 10; slot++) {
                int equipped = equipmentFlag(data, seedOffset + 3, slot);
                if (equipped > 0) n++;
            }
            int len = u8(data[n++]);
            n += len * 2;
            len = u8(data[n++]);
            n += len * 2;
            len = u8(data[n++]);
            n += len;
        }
        return n;
    }
    private static int skipMonsters(byte[] data, int n) {
        int count = u8(data[n++]);
        for (int i = 0; i < count; i++) {
            int nameLen = u8(data[n]);
            n += 1 + nameLen;
            n += 4;
            int flags = u8(data[n++]);
            int len = u8(data[n++]);
            for (int j = 0; j < len; j++) {
                n += 2;
                if ((data[n - 1] & 1) != 0) n++;
            }
            len = u8(data[n++]);
            n += len * 3;
            if ((flags & 8) != 0) { len = u8(data[n++]); n += len * 2; }
            if ((flags & 4) != 0) { len = u8(data[n++]); n += len * 2; }
            if ((flags & 2) != 0) { len = u8(data[n++]); n += len; }
            len = u8(data[n++]);
            n += len * 2;
            n += 13;
        }
        return n;
    }

    private static int equipmentFlag(byte[] data, int offset, int slot) {
        switch (slot) {
            case 0: return (data[offset] & 8) != 0 ? ((data[offset] & 4) != 0 ? 1 : 0) : -1;
            case 1: return (data[offset] & 2) != 0 ? ((data[offset] & 1) != 0 ? 1 : 0) : -1;
            case 2: return (data[offset + 1] & 0x80) != 0 ? ((data[offset + 1] & 0x40) != 0 ? 1 : 0) : -1;
            case 3: return (data[offset + 1] & 0x20) != 0 ? ((data[offset + 1] & 0x10) != 0 ? 1 : 0) : -1;
            case 4: return (data[offset + 1] & 8) != 0 ? ((data[offset + 1] & 4) != 0 ? 1 : 0) : -1;
            case 5: return (data[offset + 1] & 2) != 0 ? ((data[offset + 1] & 1) != 0 ? 1 : 0) : -1;
            case 6: return (data[offset + 2] & 0x80) != 0 ? ((data[offset + 2] & 0x40) != 0 ? 1 : 0) : -1;
            case 7: return (data[offset + 2] & 0x20) != 0 ? ((data[offset + 2] & 0x10) != 0 ? 1 : 0) : -1;
            case 8: return (data[offset + 2] & 8) != 0 ? ((data[offset + 2] & 4) != 0 ? 1 : 0) : -1;
            case 9: return (data[offset + 2] & 2) != 0 ? ((data[offset + 2] & 1) != 0 ? 1 : 0) : -1;
            default: return -1;
        }
    }

    private static void writeHeroStats(byte[] data, int offset, int power, int spirit, int vitality, int agility) {
        data[offset] = checkedByte((power >> 16) & 0xff, "power curve");
        data[offset + 1] = checkedByte((spirit >> 16) & 0xff, "spirit curve");
        data[offset + 2] = checkedByte((vitality >> 16) & 0xff, "vitality curve");
        data[offset + 3] = checkedByte((agility >> 16) & 0xff, "agility curve");
        int p = power & 0x7fff;
        int s = spirit & 0x7fff;
        int v = vitality & 0x7fff;
        int a = agility & 0x7fff;
        data[offset + 4] = checkedByte(((p >> 7) & 0xfe) | ((p >> 6) & 1), "packed hero stat");
        data[offset + 5] = checkedByte(((p & 0x3f) << 2) | ((s >> 13) & 3), "packed hero stat");
        data[offset + 6] = checkedByte(((s >> 5) & 0xf8) | ((s >> 4) & 7), "packed hero stat");
        data[offset + 7] = checkedByte(((s & 0x0f) << 4) | ((v >> 11) & 0x0f), "packed hero stat");
        data[offset + 8] = checkedByte(((v >> 3) & 0xe0) | ((v >> 2) & 0x1f), "packed hero stat");
        data[offset + 9] = checkedByte(((v & 3) << 6) | ((a >> 9) & 0x3f), "packed hero stat");
        data[offset + 10] = checkedByte(((a >> 1) & 0x80) | (a & 0x7f), "packed hero stat");
    }

    private static void writeHeroSeeds(byte[] data, int offset, int levelCap, int baseCritChance, int baseCritDamage) {
        int shortA = checked7Bit(levelCap, "level cap");
        int shortB = ((checkedByte(baseCritChance, "base crit chance") & 0xff) << 8) | (checkedByte(baseCritDamage, "base crit damage") & 0xff);
        data[offset] = checkedByte(((shortA >> 7) & 0xfe) | ((shortA >> 6) & 1), "packed hero header");
        data[offset + 1] = checkedByte(((shortA & 0x3f) << 2) | ((shortB >> 13) & 3), "packed hero header");
        data[offset + 2] = checkedByte(((shortB >> 5) & 0xf8) | ((shortB >> 4) & 7), "packed hero header");
        data[offset + 3] = (byte)((data[offset + 3] & 0x0f) | ((shortB & 0x0f) << 4));
    }
    private static String[] decodedNames(Object[] values, Method decode) throws Exception {
        if (values == null) return new String[0];
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = decodeName(values[i], 0, decode);
        return names;
    }

    private static List<ItemEffectRow> decodeItemEffects(Object item, int category, String[] statusNames) throws Exception {
        List<ItemEffectRow> rows = new ArrayList<>();
        int hpRestore = intValue(raw(item, 21));
        int resourceRestore = intValue(raw(item, 22));
        if (hpRestore > 0) rows.add(new ItemEffectRow("Consumable", "Restore HP", "HP", String.valueOf(hpRestore), "", "short_g"));
        if (resourceRestore > 0) rows.add(new ItemEffectRow("Consumable", "Restore Resource", "Blood/Soul", String.valueOf(resourceRestore), "", "short_h"));

        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 0, shortValue(raw(item, 8)), "short_c");
        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 2, shortValue(raw(item, 9)), "short_d");
        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 4, shortValue(raw(item, 10)), "short_e");
        appendPackedStat(rows, category == 7 ? "Rune/Equipment" : "Equipment", "Packed Stat", 6, shortValue(raw(item, 11)), "short_f");
        int misc = u8(raw(item, 12));
        if (misc != 0) rows.add(new ItemEffectRow("Equipment", "Packed Stat", statName(8), String.valueOf(misc), "", "byte_d"));

        appendIntEffects(rows, category == 7 ? "Weapon effect" : "Equipment/Weapon", intArray(raw(item, 13)), "int_arr_a");
        appendIntEffects(rows, "Armor effect", intArray(raw(item, 14)), "int_arr_b");
        appendShortEffects(rows, "Protection", shortArray(raw(item, 15)), statusNames, "short_arr_a");
        appendShortEffects(rows, category == 5 ? "Consumable" : "On hit / item use", shortArray(raw(item, 16)), statusNames, "short_arr_b");

        if (category == 3) {
            int weaponReach = u8(raw(item, 18)) & 0x0f;
            int weaponMode = (u8(raw(item, 18)) >> 5) & 7;
            rows.add(new ItemEffectRow("Weapon", "Reach", "Tiles", String.valueOf(weaponReach), "mode=" + weaponMode, "byte_f"));
            rows.add(new ItemEffectRow("Weapon", "Animation", "Projectile/impact", "q=" + intValue(raw(item, 32)), "r=" + intValue(raw(item, 33)), "q/r"));
        }
        if (rows.isEmpty()) rows.add(new ItemEffectRow("Info", "No decoded effects", "", "", "", ""));
        return rows;
    }

    private static void appendPackedStat(List<ItemEffectRow> rows, String side, String type, int baseStat, int packed, String rawName) {
        int high = (packed >> 8) & 0xff;
        int low = packed & 0xff;
        if (high != 0) rows.add(new ItemEffectRow(side, type, statName(baseStat), String.valueOf(high), "", rawName + ":hi"));
        if (low != 0) rows.add(new ItemEffectRow(side, type, statName(baseStat + 1), String.valueOf(low), "", rawName + ":lo"));
    }

    private static void appendIntEffects(List<ItemEffectRow> rows, String side, int[] values, String rawName) {
        for (int i = 0; values != null && i < values.length; i++) {
            int packed = values[i];
            int kind = (packed >> 16) & 0xff;
            int value = packed & 0xffff;
            rows.add(new ItemEffectRow(side, effectKind(kind), statName(kind), String.valueOf(value), "", rawName + "[" + i + "]=" + packed));
        }
    }

    private static void appendShortEffects(List<ItemEffectRow> rows, String side, short[] values, String[] statusNames, String rawName) {
        for (int i = 0; values != null && i < values.length; i++) {
            int packed = values[i] & 0xffff;
            int id = (packed >> 8) & 0xff;
            int value = packed & 0xff;
            rows.add(new ItemEffectRow(side, "Status", statusLabel(id, statusNames), String.valueOf(value), "%/value", rawName + "[" + i + "]=" + packed));
        }
    }

    private static String slotLabel(int category, int subtype) {
        switch (category) {
            case 1: return subtype == 0 ? "Ring" : "Neck/Accessory";
            case 2:
                if (subtype == 0) return "Body Armor";
                if (subtype == 1) return "Head";
                if (subtype == 4) return "Feet";
                return "Armor subtype " + subtype;
            case 3: return "Weapon";
            case 4: return "Equipment subtype " + subtype;
            case 5: return "Consumable";
            case 7: return "Rune/Modifier";
            case 8: return "Text/Special";
            case 9: return "Skill item";
            case 10: return "Special item";
            case 12: return "Quest/Special";
            default: return "Category " + category;
        }
    }

    private static String allowedClasses(byte[] allowed) {
        if (allowed == null || allowed.length == 0) return "Any";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < allowed.length; i++) parts.add(heroClassName(allowed[i] & 0xff));
        return joinParts(parts);
    }

    private static String heroClassName(int id) {
        switch (id) {
            case 0: return "Lara";
            case 1: return "Vince";
            case 2: return "Romus";
            case 3: return "Manok";
            default: return "Class " + id;
        }
    }

    private static String itemNotes(int category, int subtype, int reach, int mode) {
        if (category == 3) return "weapon: reach=" + reach + ", mode=" + mode;
        if (category == 7) return "rune/modifier: weapon effect + armor effect";
        return "type=" + category + ", subtype=" + subtype;
    }

    private static String effectKind(int id) {
        if (id == 0) return "Flat stat/damage";
        if (id >= 9 && id <= 13) return "Element/resistance";
        if (id >= 14) return "Status/resistance";
        return "Modifier";
    }

    private static String skillNameForTalentLink(int skillUnlock, String[] skillNames) {
        int skillId = skillUnlock - 1;
        if (skillId < 0) return "";
        if (skillId >= skillNames.length) return "Skill " + skillId;
        return skillNames[skillId];
    }
    private static String globalTalentName(int id) {
        switch (id) {
            case 1: return "Blood sucking / NPC resource gain";
            case 2: return "Stealing tier";
            case 3: return "Sharp senses";
            default: return "Global bonus " + id;
        }
    }

    private static String resistanceTalentName(String talentName, int id) {
        String normalized = talentName == null ? "" : talentName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mental")) return "Anti-sleep";
        if (normalized.contains("poison")) return "Anti-poison";
        if (normalized.contains("magic eyes")) return "Anti-blind";
        if (normalized.contains("hard bones")) return "Anti-blaze";
        return "Resistance bonus " + id;
    }

    private static String heroBonusName(int id) {
        switch (id) {
            case 1: return "HP regen per turn";
            case 2: return "Movement/zone bonus";
            case 3: return "Critical chance %";
            case 4: return "Critical damage bonus %";
            case 5: return "Reflex/evasion";
            default: return "Hero bonus " + id;
        }
    }

    private static String talentNotes(int heroBonus, int skillUnlock, String unlockedSkillName, int statusBonus, int resistanceBonus, int globalBonus, int currentLevel) {
        String prefix = currentLevel > 0 ? "current=" + currentLevel + "; " : "";
        if (skillUnlock > 0) return prefix + "hero talent unlocks castable skill " + (skillUnlock - 1) + (unlockedSkillName.isEmpty() ? "" : " (" + unlockedSkillName + ")") + ".";
        if (heroBonus == 3) return prefix + "Find Weaknesses-like: adds amount percent critical chance per learned level.";
        if (heroBonus == 4) return prefix + "Deadly Might-like: adds amount percent critical damage bonus per learned level.";
        if (heroBonus > 0) return prefix + "hero-wide bonus id " + heroBonus + "; amount applies per learned level.";
        if (statusBonus > 0) return prefix + "status bonus id " + statusBonus + ".";
        if (resistanceBonus > 0) return prefix + "resistance bonus id " + resistanceBonus + ".";
        if (globalBonus > 0) return prefix + "global party bonus id " + globalBonus + ".";
        return prefix + "amount applies per learned level; exact effect not named yet.";
    }
    private static String statName(int id) {
        String[] names = {"Strength/Power", "Spirit", "Vitality", "Speed", "Max HP", "Max Resource", "Move", "Regen", "Weapon Attack / Armor Defense", "Fire", "Frost", "Light", "Shadow", "Blood", "Status", "Poison", "Sleep", "Bleed", "Blind", "Silence", "Enfeeble", "Frenzy", "Confuse", "Shackle", "Blaze", "Cold", "Fear"};
        return id >= 0 && id < names.length ? names[id] : "Stat " + id;
    }

    private static String statusLabel(int id, String[] statusNames) {
        return id >= 0 && id < statusNames.length ? statusNames[id] : "Status " + id;
    }

    private static URLClassLoader selectedJarClassLoader(Path inputJar) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(inputJar.toAbsolutePath().normalize().toUri().toURL());
        urls.add(VddohDataEditor.class.getProtectionDomain().getCodeSource().getLocation());
        addIfExists(urls, Path.of("lib", "kemulator", "cldc11.jar"));
        addIfExists(urls, Path.of("lib", "kemulator", "midp21.jar"));
        addIfExists(urls, Path.of("lib", "kemulator", "jsr135.jar"));
        addIfExists(urls, Path.of("lib", "kemulator", "nokiaui.jar"));
        return new URLClassLoader(urls.toArray(new URL[0]), null);
    }

    private static void addIfExists(List<URL> urls, Path path) throws IOException {
        if (Files.exists(path)) urls.add(path.toAbsolutePath().normalize().toUri().toURL());
    }

    private static byte[] readZipEntry(ZipInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static byte[] readJarEntry(Path inputJar, String entryName) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) return readZipEntry(in);
                in.closeEntry();
            }
        }
        throw new IOException("JAR does not contain " + entryName);
    }
    private static void replaceJarEntries(Path inputJar, Path outputJar, Map<String, byte[]> replacements) throws IOException {
        Set<String> seen = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputJar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                byte[] replacement = replacements.get(entry.getName());
                if (replacement != null) {
                    out.write(replacement);
                    seen.add(entry.getName());
                } else {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                }
                out.closeEntry();
                in.closeEntry();
            }
            for (Map.Entry<String, byte[]> replacement : replacements.entrySet()) {
                if (seen.contains(replacement.getKey())) continue;
                out.putNextEntry(new ZipEntry(replacement.getKey()));
                out.write(replacement.getValue());
                out.closeEntry();
            }
        }
    }

    private static Object raw(Object value, int ordinal) throws Exception {
        int i = 0;
        for (Field field : value.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (i++ == ordinal) return field.get(value);
        }
        return null;
    }

    private static Object[] staticArray(Class<?> owner, Class<?> component, int ordinal) throws Exception {
        int seen = 0;
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!type.isArray() || type.getComponentType() != component) continue;
            if (seen++ == ordinal) return (Object[]) field.get(null);
        }
        return new Object[0];
    }

    private static Object[] largerStaticArray(Class<?> owner, Class<?> component) throws Exception {
        Object[] best = new Object[0];
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!type.isArray() || type.getComponentType() != component) continue;
            Object[] value = (Object[]) field.get(null);
            if (value != null && value.length > best.length) best = value;
        }
        return best;
    }

    private static byte[][] staticByte2d(Class<?> owner, int ordinal) throws Exception {
        int seen = 0;
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!type.isArray() || !type.getComponentType().isArray() || type.getComponentType().getComponentType() != Byte.TYPE) continue;
            if (seen++ == ordinal) return (byte[][]) field.get(null);
        }
        return new byte[0][];
    }

    private static void setFirstStaticBoolean(Class<?> owner, boolean value) throws Exception {
        for (Field field : owner.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Boolean.TYPE) {
                field.setBoolean(null, value);
                return;
            }
        }
    }

    private static String decodeName(Object value, int byteArrayOrdinal, Method decode) throws Exception {
        int seen = 0;
        for (Field field : value.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getType().isArray() || field.getType().getComponentType() != Byte.TYPE) continue;
            if (seen++ == byteArrayOrdinal) return decodeBytes((byte[]) field.get(value), decode);
        }
        return "";
    }

    private static String decodeBytes(byte[] encoded, Method decode) throws Exception {
        return encoded == null ? "" : (String) decode.invoke(null, (Object) encoded);
    }

    private static Object[] objectArray(Object value) { return value == null ? new Object[0] : (Object[]) value; }
    private static int[] intArray(Object value) { return value instanceof int[] ? (int[]) value : new int[0]; }
    private static short[] shortArray(Object value) { return value instanceof short[] ? (short[]) value : new short[0]; }
    private static short[] nullableShortArray(Object value) { return value instanceof short[] ? (short[]) value : null; }
    private static byte[] byteArray(Object value) { return value instanceof byte[] ? (byte[]) value : null; }
    private static int u8(Object value) { return value instanceof Byte ? ((Byte) value).byteValue() & 0xff : 0; }
    private static int intValue(Object value) { return value instanceof Number ? ((Number) value).intValue() & 0xffff : 0; }
    private static int shortValue(Object value) { return value instanceof Number ? ((Number) value).intValue() & 0xffff : 0; }
    private static int u8(byte value) { return value & 0xff; }
    private static int u16(byte[] data, int offset) { return (u8(data[offset]) << 8) | u8(data[offset + 1]); }
    private static int signedChance(int raw) { return (raw & 0x80) != 0 ? -((-raw) & 0x7f) : raw & 0x7f; }
    private static void writeU16(byte[] data, int offset, int value) {
        if (value < 0 || value > 0xffff) throw new IllegalArgumentException("damage must be 0..65535");
        data[offset] = (byte)((value >>> 8) & 0xff);
        data[offset + 1] = (byte)(value & 0xff);
    }
    private static int checked7Bit(int value, String label) {
        if (value < 0 || value > 127) throw new IllegalArgumentException(label + " must be 0..127");
        return value;
    }
    private static int checkedTalentMaxLevel(int value) {
        if (value < 1 || value > 4) throw new IllegalArgumentException("talent max level must be 1..4");
        return value;
    }

    private static byte checkedTalentLink(int value, String label) {
        if (value < 1 || value > 256) throw new IllegalArgumentException(label + " must be 1..256 for an existing talent link");
        return (byte)((value - 1) & 0xff);
    }
    private static int checked4Bit(int value, String label) {
        if (value < 0 || value > 15) throw new IllegalArgumentException(label + " must be 0..15");
        return value;
    }
    private static byte checkedByte(int value, String label) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(label + " must be 0..255");
        return (byte)value;
    }
    private static byte encodeSignedChance(int chance) {
        if (chance < -127 || chance > 127) throw new IllegalArgumentException("status chance must be -127..127");
        return chance < 0 ? (byte)(-(-chance & 0x7f)) : (byte)(chance & 0x7f);
    }

    private static String joinLines(List<String> values) { return String.join("\n", values); }
    private static String joinParts(List<String> values) { return String.join(", ", values); }

    private static void showError(java.awt.Component parent, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(parent, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}





































