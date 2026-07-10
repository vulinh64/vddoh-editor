package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.editorUserPath;
import static com.vddoh.editor.EditorSupport.joinLines;
import static com.vddoh.editor.EditorSupport.readJarEntry;
import static com.vddoh.editor.EditorSupport.readZipEntry;
import static com.vddoh.editor.EditorSupport.replaceJarEntries;
import static com.vddoh.editor.EditorSupport.showError;

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
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
final class EditorFrame extends JFrame {
  public static final String VDDOH_ROOT = "vddoh";
  public static final String GAME_DAT_PATH = "game.dat";
  public static final String ITEM_DAT_PATH = "item.dat";
  public static final String SEARCH_LABEL = "Search";
  public static final String CLEAR_LABEL = "Clear";
  public static final String CLASS_G = "g.class";
  private final JTextField jarPath = new JTextField("vddoh.jar", 32);
  private final JTextField gameDatPath =
      new JTextField(
          editorUserPath("temp").resolve(VDDOH_ROOT).resolve(GAME_DAT_PATH).toString(), 32);
  private final JTextField itemDatPath =
      new JTextField(
          editorUserPath("temp").resolve(VDDOH_ROOT).resolve(ITEM_DAT_PATH).toString(), 32);
  private final JTextField outputJarPath =
      new JTextField(editorUserPath("dist").resolve("vddoh-editor-patch.jar").toString(), 32);
  private String gameDatEntryName = GAME_DAT_PATH;
  private String itemDatEntryName = ITEM_DAT_PATH;
  private final SkillLevelTableModel skillsModel = new SkillLevelTableModel();
  private final SkillEffectTableModel skillEffectsModel = new SkillEffectTableModel();
  private final HeroTableModel heroesModel = new HeroTableModel();
  private final ItemTableModel itemsModel = new ItemTableModel();
  private final ItemEffectTableModel itemEffectsModel = new ItemEffectTableModel();
  private final TalentTableModel talentsModel = new TalentTableModel();
  private final MonsterTableModel monstersModel = new MonsterTableModel();
  private final StatusTableModel statusesModel = new StatusTableModel();
  private final JLabel status = new JLabel("Ready");
  private final JCheckBox patchResistanceOverflow = new JCheckBox("Patch resistance overflow");

  EditorFrame() {
    super("VDDOH Data Editor");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
    load.addActionListener(_ -> loadData());
    build.addActionListener(_ -> buildPatchedJar());
    reset.addActionListener(
        _ -> {
          skillsModel.resetEdits();
          talentsModel.resetEdits();
          heroesModel.resetEdits();
          itemsModel.resetEdits();
          monstersModel.resetEdits();
          statusesModel.resetEdits();
        });
    buttons.add(load);
    buttons.add(build);
    buttons.add(reset);
    patchResistanceOverflow.setToolTipText(
        "Patch g.class so overflowed resistance bytes clamp to 100 instead of 0.");
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
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      log.info("Startup input JAR selection was cancelled");
      return false;
    }
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
    levels
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e.getValueIsAdjusting()) {
                return;
              }
              int selected = levels.getSelectedRow();
              if (selected < 0) {
                skillEffectsModel.setRows(new ArrayList<>());
                return;
              }
              int modelRow = levels.convertRowIndexToModel(selected);
              skillEffectsModel.setRows(skillsModel.effectRows(modelRow));
            });

    JTextField search = new JTextField(24);
    JButton searchButton = new JButton(SEARCH_LABEL);
    JButton clearButton = new JButton(CLEAR_LABEL);
    Runnable applySearch =
        () -> {
          String query = search.getText().trim().toLowerCase();
          if (query.length() < 3) {
            sorter.setRowFilter(null);
          } else {
            sorter.setRowFilter(
                new RowFilter<>() {
                  @Override
                  public boolean include(
                      Entry<? extends SkillLevelTableModel, ? extends Integer> entry) {
                    return skillsModel.matchesSearch(entry.getIdentifier(), query);
                  }
                });
          }
          if (levels.getRowCount() > 0) {
            levels.setRowSelectionInterval(0, 0);
          } else {
            skillEffectsModel.setRows(new ArrayList<>());
          }
          status.setText(
              "Skills shown: " + levels.getRowCount() + " / " + skillsModel.getRowCount());
        };
    search.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent e) {
            applySearch.run();
          }
        });
    searchButton.addActionListener(_ -> applySearch.run());
    clearButton.addActionListener(
        _ -> {
          search.setText(StringUtils.EMPTY);
          applySearch.run();
        });

    JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    searchPanel.add(new JLabel(SEARCH_LABEL));
    searchPanel.add(search);
    searchPanel.add(searchButton);
    searchPanel.add(clearButton);

    JSplitPane split =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, new JScrollPane(levels), new JScrollPane(effects));
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
    items
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e.getValueIsAdjusting()) {
                return;
              }
              int selected = items.getSelectedRow();
              if (selected < 0) {
                itemEffectsModel.setRows(new ArrayList<>());
                return;
              }
              int modelRow = items.convertRowIndexToModel(selected);
              itemEffectsModel.setRows(itemsModel.effectRows(modelRow));
            });

    JTextField search = new JTextField(24);
    JButton searchButton = new JButton(SEARCH_LABEL);
    JButton clearButton = new JButton(CLEAR_LABEL);
    Runnable applySearch =
        () -> {
          String query = search.getText().trim().toLowerCase();
          if (query.length() < 3) {
            sorter.setRowFilter(null);
          } else {
            sorter.setRowFilter(
                new RowFilter<>() {
                  @Override
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
    search.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent e) {
            applySearch.run();
          }
        });
    searchButton.addActionListener(_ -> applySearch.run());
    clearButton.addActionListener(
        _ -> {
          search.setText(StringUtils.EMPTY);
          applySearch.run();
        });

    JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    searchPanel.add(new JLabel(SEARCH_LABEL));
    searchPanel.add(search);
    searchPanel.add(searchButton);
    searchPanel.add(clearButton);

    JSplitPane split =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(items), new JScrollPane(effects));
    split.setResizeWeight(0.64);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(searchPanel, BorderLayout.NORTH);
    panel.add(split, BorderLayout.CENTER);
    return panel;
  }

  private JPanel createSearchableTablePanel(AbstractTableModel model, String label) {
    JTable table =
        switch (model) {
          case HeroTableModel heroTableModel -> heroJTable(heroTableModel);
          case TalentTableModel talentTableModel -> talentJTable(talentTableModel);
          case MonsterTableModel monsterTableModel -> monsterJTable(monsterTableModel);
          case null -> throw new IllegalArgumentException("Unknown model");
          default -> new JTable(model);
        };

    configureScrollableTable(table);
    TableRowSorter<AbstractTableModel> sorter = new TableRowSorter<>(model);
    table.setRowSorter(sorter);

    JTextField search = new JTextField(24);
    JButton searchButton = new JButton(SEARCH_LABEL);
    JButton clearButton = new JButton(CLEAR_LABEL);
    Runnable applySearch =
        () -> {
          String query = search.getText().trim().toLowerCase();
          if (query.length() < 3) {
            sorter.setRowFilter(null);
          } else {
            sorter.setRowFilter(
                new RowFilter<>() {
                  @Override
                  public boolean include(
                      Entry<? extends AbstractTableModel, ? extends Integer> entry) {
                    return tableRowMatches(model, entry.getIdentifier(), query);
                  }
                });
          }
          if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
          }
          status.setText(
              "%s shown: %d / %d".formatted(label, table.getRowCount(), model.getRowCount()));
        };
    search.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent e) {
            applySearch.run();
          }
        });
    searchButton.addActionListener(_ -> applySearch.run());
    clearButton.addActionListener(
        _ -> {
          search.setText(StringUtils.EMPTY);
          applySearch.run();
        });

    JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    searchPanel.add(new JLabel(SEARCH_LABEL));
    searchPanel.add(search);
    searchPanel.add(searchButton);
    searchPanel.add(clearButton);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(searchPanel, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    return panel;
  }

  private static JTable monsterJTable(MonsterTableModel model) {
    return new JTable(model) {
      @Override
      public String getToolTipText(MouseEvent event) {
        int viewColumn = columnAtPoint(event.getPoint());
        if (viewColumn < 0) {
          return super.getToolTipText(event);
        }
        int modelColumn = convertColumnIndexToModel(viewColumn);
        return model.columnTooltip(modelColumn);
      }
    };
  }

  private static JTable talentJTable(TalentTableModel model) {
    return new JTable(model) {
      @Override
      public String getToolTipText(MouseEvent event) {
        int viewColumn = columnAtPoint(event.getPoint());
        if (viewColumn < 0) {
          return super.getToolTipText(event);
        }
        int modelColumn = convertColumnIndexToModel(viewColumn);
        return model.columnTooltip(modelColumn);
      }
    };
  }

  private static JTable heroJTable(HeroTableModel model) {
    return new JTable(model) {
      @Override
      public String getToolTipText(MouseEvent event) {
        int viewColumn = columnAtPoint(event.getPoint());
        if (viewColumn < 0) {
          return super.getToolTipText(event);
        }
        int modelColumn = convertColumnIndexToModel(viewColumn);
        return model.columnTooltip(modelColumn);
      }
    };
  }

  private void configureScrollableTable(JTable table) {
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setFillsViewportHeight(true);
  }

  private boolean tableRowMatches(AbstractTableModel model, int rowIndex, String query) {
    StringBuilder text = new StringBuilder();
    for (int column = 0; column < model.getColumnCount(); column++) {
      Object value = model.getValueAt(rowIndex, column);
      if (value != null) {
        text.append(value).append(' ');
      }
    }
    return text.toString().toLowerCase().contains(query);
  }

  private JPanel pathRow(String label, JTextField field, boolean alsoGameDat) {
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.add(new JLabel(label), BorderLayout.WEST);
    row.add(field, BorderLayout.CENTER);
    JButton browse = new JButton("...");
    browse.addActionListener(
        _ -> {
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
    log.info("Selected input JAR: {}", absoluteJar);
    jarPath.setText(absoluteJar.toString());
    Path fileName = absoluteJar.getFileName();
    String baseName = fileName == null ? VDDOH_ROOT : fileName.toString();
    if (baseName.toLowerCase().endsWith(".jar")) {
      baseName = baseName.substring(0, baseName.length() - 4);
    }
    Path workDir = editorUserPath("temp").resolve(baseName);
    gameDatPath.setText(workDir.resolve(GAME_DAT_PATH).toString());
    itemDatPath.setText(workDir.resolve(ITEM_DAT_PATH).toString());
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
    log.info("Extracting data files from {} into {} and {}", inputJar, gameDat, itemDat);
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
          if (lower.equals(GAME_DAT_PATH) || lower.endsWith("/game.dat")) {
            Files.write(gameDat, readZipEntry(in));
            gameDatEntryName = name;
            foundGame = true;
          } else if (lower.equals(ITEM_DAT_PATH) || lower.endsWith("/item.dat")) {
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
    log.info(
        "Extracted data files from {}: {} -> {}, {} -> {}",
        inputJar,
        gameDatEntryName,
        gameDat,
        itemDatEntryName,
        itemDat);
  }

  private void updateResistanceOverflowPatchState(Path inputJar) throws IOException {
    ResistanceOverflowClassPatcher.State state =
        ResistanceOverflowClassPatcher.state(readJarEntry(inputJar, CLASS_G));
    log.info("Resistance overflow patch state for {} is {}", inputJar, state);
    switch (state) {
      case ResistanceOverflowClassPatcher.State.PATCHED -> {
        patchResistanceOverflow.setSelected(true);
        patchResistanceOverflow.setEnabled(false);
        patchResistanceOverflow.setToolTipText(
            "This JAR already contains the resistance overflow bytecode patch.");
      }
      case ResistanceOverflowClassPatcher.State.ORIGINAL -> {
        patchResistanceOverflow.setSelected(false);
        patchResistanceOverflow.setEnabled(true);
        patchResistanceOverflow.setToolTipText(
            "Patch g.class so overflowed resistance bytes clamp to 100 instead of 0.");
      }
      default -> {
        patchResistanceOverflow.setSelected(false);
        patchResistanceOverflow.setEnabled(false);
        patchResistanceOverflow.setToolTipText(
            "Unsupported g.class layout; resistance overflow patch is unavailable for this JAR.");
      }
    }
  }

  private void loadData() {
    try {
      Path inputJar = Path.of(jarPath.getText());
      log.info("Loading editor data from {}", inputJar);
      extractDataFilesFromJar(inputJar);
      updateResistanceOverflowPatchState(inputJar);
      GameData data = GameData.loadFromOriginalClasses(inputJar);
      skillsModel.setRows(data.skillLevels);
      skillEffectsModel.setRows(
          data.skillLevels.isEmpty() ? new ArrayList<>() : data.skillLevels.getFirst().effects);
      talentsModel.setRows(data.talents);
      heroesModel.setRows(data.heroes);
      itemsModel.setRows(data.items);
      itemEffectsModel.setRows(
          data.items.isEmpty() ? new ArrayList<>() : data.items.getFirst().effects);
      monstersModel.setRows(data.monsters);
      statusesModel.setRows(data.statuses);
      status.setText(
          "Loaded " + data.skillLevels.size() + " skill levels, " + data.items.size() + " items");
      log.info(
          "Loaded data: skillLevels={}, talents={}, heroes={}, items={}, monsters={}, statuses={}",
          data.skillLevels.size(),
          data.talents.size(),
          data.heroes.size(),
          data.items.size(),
          data.monsters.size(),
          data.statuses.size());
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
      List<MonsterPatch> monsterPatches = monstersModel.changedPatches();
      List<StatusPatch> statusPatches = statusesModel.changedPatches();
      boolean classPatchRequested =
          patchResistanceOverflow.isSelected() && patchResistanceOverflow.isEnabled();
      log.info(
          "Preparing patched JAR: skills={}, talents={}, heroes={}, items={}, monsters={}, statuses={}, classPatchRequested={}",
          skillPatches.size(),
          talentPatches.size(),
          heroPatches.size(),
          itemPatches.size(),
          monsterPatches.size(),
          statusPatches.size(),
          classPatchRequested);
      if (skillPatches.isEmpty()
          && talentPatches.isEmpty()
          && heroPatches.isEmpty()
          && itemPatches.isEmpty()
          && monsterPatches.isEmpty()
          && statusPatches.isEmpty()
          && !classPatchRequested) {
        JOptionPane.showMessageDialog(this, "No edits to patch.");
        return;
      }
      Path gameDat = Path.of(gameDatPath.getText());
      Path itemDat = Path.of(itemDatPath.getText());
      Path inputJar = Path.of(jarPath.getText());
      Path outputJar = Path.of(outputJarPath.getText());
      Files.createDirectories(outputJar.toAbsolutePath().getParent());
      log.info("Building patched JAR from {} to {}", inputJar, outputJar);

      Map<String, byte[]> replacements = new LinkedHashMap<>();
      List<String> summaries = new ArrayList<>();
      if (!skillPatches.isEmpty()
          || !talentPatches.isEmpty()
          || !heroPatches.isEmpty()
          || !monsterPatches.isEmpty()
          || !statusPatches.isEmpty()) {
        byte[] data = Files.readAllBytes(gameDat);
        if (!skillPatches.isEmpty()) {
          summaries.add("skills: " + GameDatSkillPatcher.patch(data, skillPatches));
        }
        if (!talentPatches.isEmpty()) {
          summaries.add("talents: " + GameDatTalentPatcher.patch(data, talentPatches));
        }
        if (!heroPatches.isEmpty()) {
          summaries.add("heroes: " + GameDatHeroPatcher.patch(data, heroPatches));
        }
        if (!monsterPatches.isEmpty()) {
          summaries.add("monsters: " + GameDatMonsterPatcher.patch(data, monsterPatches));
        }
        if (!statusPatches.isEmpty()) {
          summaries.add("statuses: " + GameDatStatusPatcher.patch(data, statusPatches));
        }
        Files.write(outputJar.resolveSibling(GAME_DAT_PATH), data);
        replacements.put(gameDatEntryName, data);
      }
      if (!itemPatches.isEmpty()) {
        byte[] data = Files.readAllBytes(itemDat);
        summaries.add("items: " + ItemDatPatcher.patch(data, itemPatches));
        Files.write(outputJar.resolveSibling(ITEM_DAT_PATH), data);
        replacements.put(itemDatEntryName, data);
      }
      if (classPatchRequested) {
        byte[] heroClass = readJarEntry(inputJar, CLASS_G);
        PatchSummary classSummary = ResistanceOverflowClassPatcher.patch(heroClass);
        replacements.put(CLASS_G, heroClass);
        summaries.add(
            "class: resistanceOverflow="
                + classSummary.heroResistOverflow
                + ", skipped="
                + classSummary.skipped);
      }
      replaceJarEntries(inputJar, outputJar, replacements);
      String summary = joinLines(summaries);
      status.setText("Wrote " + outputJar + " (" + summary.replace('\n', ';') + ")");
      log.info(
          "Wrote patched JAR {} with replacements {} and summary {}",
          outputJar,
          replacements.keySet(),
          summary);
      JOptionPane.showMessageDialog(this, "Patched JAR written:\n" + outputJar + "\n\n" + summary);
    } catch (Exception ex) {
      showError(this, ex);
    }
  }
}
