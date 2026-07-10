package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class HeroTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = 7116353497659695323L;

  private final String[] columns = {
    "ID",
    "Hero",
    "Base HP",
    "Base Resource",
    "Base Attack",
    "Base Defense",
    "Base Move",
    "Base Regen",
    "Strength Start",
    "Strength Lv99 Target",
    "Strength Growth Curve",
    "Spirit Start",
    "Spirit Lv99 Target",
    "Spirit Growth Curve",
    "Vitality Start",
    "Vitality Lv99 Target",
    "Vitality Growth Curve",
    "Speed Start",
    "Speed Lv99 Target",
    "Speed Growth Curve",
    "Level Cap",
    "STR @ Cap",
    "SPI @ Cap",
    "VIT @ Cap",
    "SPD @ Cap",
    "Base Crit %",
    "Base Crit Dmg %",
    "Base Evasion %",
    "Notes"
  };
  private List<HeroRow> rows = new ArrayList<>();

  void setRows(List<HeroRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  void resetEdits() {
    for (HeroRow row : rows) {
      row.reset();
    }
    fireTableDataChanged();
  }

  List<HeroPatch> changedPatches() {
    List<HeroPatch> patches = new ArrayList<>();
    for (HeroRow row : rows) {
      if (row.changed()) {
        patches.add(
            HeroPatch.builder()
                .heroId(row.id)
                .strength(row.strength)
                .spirit(row.spirit)
                .vitality(row.vitality)
                .speed(row.speed)
                .levelCap(row.levelCap)
                .baseCritChance(row.baseCritChance)
                .baseCritDamage(row.baseCritDamage)
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
    if (column == 2) {
      return "Estimated level-1 max HP from starting Strength/Vitality before equipment and status effects.";
    }
    if (column == 3) {
      return "Estimated level-1 max Blood/Soul from starting Spirit/Vitality before equipment and status effects.";
    }
    if (column == 4) {
      return "Estimated level-1 attack from starting Strength before equipment.";
    }
    if (column == 5) {
      return "Estimated level-1 defense from starting Speed/Strength before equipment.";
    }
    if (column == 6) {
      return "Estimated movement range from starting Speed.";
    }
    if (column >= 8 && column <= 19) {
      int part = (column - 8) % 3;
      if (part == 0) {
        return "Level-1 value. This is the visible base stat before level growth and equipment.";
      }
      if (part == 1) {
        return "Growth target used by the formula at level 99, not the level-30 cap.";
      }
      return "Growth curve. 0 back-loads growth, 100 is roughly linear, higher values front-load growth before level cap.";
    }
    if (column == 20) {
      return "Maximum hero level. Vanilla heroes cap at 30.";
    }
    if (column >= 21 && column <= 24) {
      return "Read-only preview of the grown stat at Level Cap using the game's integer formula.";
    }
    if (column == 25) {
      return "Base physical critical hit chance. Final chance = this value + Find Weaknesses bonus.";
    }
    if (column == 26) {
      return "Base critical damage bonus. Final bonus = this value + Deadly Might bonus, capped by bytecode at 250.";
    }
    if (column == 27) {
      return "Read-only bytecode constant. Final evasion = 5 + Reflexes bonus; per-hero data does not store this.";
    }
    return null;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return (column >= 8 && column <= 20) || column == 25 || column == 26;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    HeroRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.id;
      case 1 -> row.name;
      case 2 -> row.baseHp();
      case 3 -> row.baseResource();
      case 4 -> row.baseAttack();
      case 5 -> row.baseDefense();
      case 6 -> row.baseMove();
      case 7 -> HeroRow.BASE_HP_REGEN;
      case 8 -> row.strength.start;
      case 9 -> row.strength.target;
      case 10 -> row.strength.curve;
      case 11 -> row.spirit.start;
      case 12 -> row.spirit.target;
      case 13 -> row.spirit.curve;
      case 14 -> row.vitality.start;
      case 15 -> row.vitality.target;
      case 16 -> row.vitality.curve;
      case 17 -> row.speed.start;
      case 18 -> row.speed.target;
      case 19 -> row.speed.curve;
      case 20 -> row.levelCap;
      case 21 -> row.strengthAtCap();
      case 22 -> row.spiritAtCap();
      case 23 -> row.vitalityAtCap();
      case 24 -> row.speedAtCap();
      case 25 -> row.baseCritChance;
      case 26 -> row.baseCritDamage;
      case 27 -> HeroRow.BASE_EVASION;
      case 28 -> row.notes;
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    HeroRow row = rows.get(rowIndex);
    int parsed = Integer.parseInt(String.valueOf(value).trim());
    if (columnIndex >= 8 && columnIndex <= 19) {
      StatCurve stat = row.stat((columnIndex - 8) / 3);
      switch ((columnIndex - 8) % 3) {
        case 0:
          stat.start = parsed;
          break;
        case 1:
          stat.target = parsed;
          break;
        case 2:
          stat.curve = parsed;
          break;
      }
    } else if (columnIndex == 20) {
      row.levelCap = parsed;
    } else if (columnIndex == 25) {
      row.baseCritChance = parsed;
    } else if (columnIndex == 26) {
      row.baseCritDamage = parsed;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}
