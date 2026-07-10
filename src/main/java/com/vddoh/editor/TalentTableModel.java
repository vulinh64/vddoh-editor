package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class TalentTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = 4608104735853933982L;

  private final String[] columns = {
    "Kind",
    "Talent Type",
    "ID",
    "Talent",
    "Gameplay Effect",
    "Lv1 Value",
    "Lv2 Value",
    "Lv3 Value",
    "Lv4 Value",
    "Amount / Level",
    "Max Level",
    "Max Value",
    "Castable Skill ID",
    "Castable Skill",
    "Hero Effect ID",
    "Global ID",
    "Unlock Ref",
    "Status ID",
    "Resist ID",
    "Notes"
  };
  private List<TalentRow> rows = new ArrayList<>();

  void setRows(List<TalentRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  void resetEdits() {
    for (TalentRow row : rows) {
      row.reset();
    }
    fireTableDataChanged();
  }

  List<TalentPatch> changedPatches() {
    List<TalentPatch> patches = new ArrayList<>();
    for (TalentRow row : rows) {
      if (row.changed()) {
        patches.add(
            TalentPatch.builder()
                .group(row.group)
                .talentId(row.id)
                .maxLevel(row.maxLevel)
                .amount(row.amount)
                .globalBonus(row.globalBonus)
                .skillUnlock(row.skillUnlock)
                .statusBonus(row.statusBonus)
                .resistanceBonus(row.resistanceBonus)
                .heroBonus(row.heroBonus)
                .build());
      }
    }
    return patches;
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public int getColumnCount() {
    return columns.length;
  }

  @Override
  public String getColumnName(int column) {
    return columns[column];
  }

  String columnTooltip(int column) {
    if (column >= 5 && column <= 8) {
      return "Read-only gameplay preview for learned talent level "
          + (column - 4)
          + ". Original data stores one amount per level, not four independent values.";
    }
    if (column == 9) {
      return "Stored amount added per learned level. Deadly Might uses this on top of the base 50% critical-damage bonus.";
    }
    if (column == 10) {
      return "Maximum learnable dots/levels for this talent. J2ME data can store 1 to 4 here.";
    }
    if (column == 11) {
      return "Read-only preview at Max Level using the same per-level amount formula.";
    }
    if (column == 14) {
      return "Hero passive effect id: 1 regen, 2 movement, 3 crit chance, 4 crit damage, 5 evasion/reflex.";
    }
    if (column >= 15 && column <= 18) {
      return "Existing optional binary link. Safe to edit when the original row already has a value; adding/removing links would change record length.";
    }
    return null;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int column) {
    TalentRow row = rows.get(rowIndex);
    return column == 9
        || column == 10
        || column == 14
        || (column == 15 && row.originalGlobalBonus > 0)
        || (column == 16 && row.originalSkillUnlock > 0)
        || (column == 17 && row.originalStatusBonus > 0)
        || (column == 18 && row.originalResistanceBonus > 0);
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    TalentRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.group ? "Group" : "Hero";
      case 1 -> row.talentType();
      case 2 -> row.id;
      case 3 -> row.name;
      case 4 -> row.effectName();
      case 5 -> row.levelValueText(1);
      case 6 -> row.levelValueText(2);
      case 7 -> row.levelValueText(3);
      case 8 -> row.levelValueText(4);
      case 9 -> row.amount;
      case 10 -> row.maxLevel;
      case 11 -> row.levelValueText(row.maxLevel);
      case 12 -> row.castableSkillIdText();
      case 13 -> row.unlockedSkillName;
      case 14 -> row.heroBonus;
      case 15 -> row.globalBonus == 0 ? StringUtils.EMPTY : row.globalBonus;
      case 16 -> row.skillUnlock == 0 ? StringUtils.EMPTY : row.skillUnlock;
      case 17 -> row.statusBonus == 0 ? StringUtils.EMPTY : row.statusBonus;
      case 18 -> row.resistanceBonus == 0 ? StringUtils.EMPTY : row.resistanceBonus;
      case 19 -> row.notes;
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    TalentRow row = rows.get(rowIndex);
    int parsed = Integer.parseInt(String.valueOf(value).trim());
    if (columnIndex == 9) {
      row.amount = parsed;
    } else if (columnIndex == 10) {
      row.maxLevel = parsed;
    } else if (columnIndex == 14) {
      row.heroBonus = parsed;
    } else if (columnIndex == 15) {
      row.globalBonus = parsed;
    } else if (columnIndex == 16) {
      row.skillUnlock = parsed;
    } else if (columnIndex == 17) {
      row.statusBonus = parsed;
    } else if (columnIndex == 18) {
      row.resistanceBonus = parsed;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}
