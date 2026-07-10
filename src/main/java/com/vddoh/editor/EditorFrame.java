package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.*;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

final class EditorFrame extends JFrame {
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
