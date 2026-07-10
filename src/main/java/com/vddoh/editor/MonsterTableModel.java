package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class MonsterTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = 7784918097378125598L;

  private final String[] columns = {
    "ID",
    "Monster",
    "EXP (0-4095)",
    "Filar (0-4095)",
    "Death Value (0-127)",
    "Effect ID (0-255)",
    "Base HP",
    "Base Resource",
    "Base Attack",
    "Base Defense",
    "Base Move",
    "STR-like",
    "SPI-like",
    "VIT-like",
    "SPD-like",
    "Hit %",
    "Crit/Dmg %",
    "Evade/Guard %",
    "Packed Chance",
    "Packed Tail A",
    "Packed Tail B",
    "Actions",
    "Effects",
    "Drops",
    "Notes"
  };
  private List<MonsterRow> rows = new ArrayList<>();

  void setRows(List<MonsterRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  void resetEdits() {
    rows.replaceAll(MonsterRow::reset);
    fireTableDataChanged();
  }

  List<MonsterPatch> changedPatches() {
    List<MonsterPatch> patches = new ArrayList<>();
    for (MonsterRow row : rows) {
      if (row.changed()) {
        patches.add(
            MonsterPatch.builder()
                .monsterId(row.id())
                .experience(row.experience())
                .filar(row.filar())
                .deathValue(row.deathValue())
                .effectId(row.effectId())
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
      return "Editable 12-bit battle result EXP pool. Valid range: 0..4095. Vanilla has a known 1..3 EXP remainder award bug.";
    }
    if (column == 3) {
      return "Editable 12-bit Filar/gold reward. Valid range: 0..4095.";
    }
    if (column == 4) {
      return "Editable signed-byte death-side value used by monster death handling. Safe range: 0..127 until negative values are tested.";
    }
    if (column == 5) {
      return "Editable direct tail byte used as an effect id. Valid byte range: 0..255.";
    }
    if (column >= 6 && column <= 10) {
      return "Read-only derived runtime preview calculated from the reflected monster core stat bytes.";
    }
    if (column >= 11 && column <= 19) {
      return "Read-only reflected monster attribute. Several values come from a packed tail that needs more decoding before safe writes.";
    }
    if (column >= 20 && column <= 22) {
      return "Read-only decoded array length for monster behavior, effects, or drops.";
    }
    return null;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return column >= 2 && column <= 5;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    MonsterRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.id();
      case 1 -> row.name();
      case 2 -> row.experience();
      case 3 -> row.filar();
      case 4 -> row.deathValue();
      case 5 -> row.effectId();
      case 6 -> row.baseHp();
      case 7 -> row.baseResource();
      case 8 -> row.baseAttack();
      case 9 -> row.baseDefense();
      case 10 -> row.baseMove();
      case 11 -> row.strength();
      case 12 -> row.spirit();
      case 13 -> row.vitality();
      case 14 -> row.speed();
      case 15 -> row.hitChance();
      case 16 -> row.critOrDamage();
      case 17 -> row.evadeOrGuard();
      case 18 -> row.packedChance();
      case 19 -> row.packedTailA();
      case 20 -> row.packedTailB();
      case 21 -> row.actionCount();
      case 22 -> row.effectCount();
      case 23 -> row.dropCount();
      case 24 -> row.notes();
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    MonsterRow row = rows.get(rowIndex);
    int parsed = Integer.parseInt(String.valueOf(value).trim());
    switch (columnIndex) {
      case 2:
        checkedRange(parsed, 4095, "EXP");
        rows.set(rowIndex, row.withExperience(parsed));
        break;
      case 3:
        checkedRange(parsed, 4095, "Filar");
        rows.set(rowIndex, row.withFilar(parsed));
        break;
      case 4:
        checkedRange(parsed, 127, "Death Value");
        rows.set(rowIndex, row.withDeathValue(parsed));
        break;
      case 5:
        checkedRange(parsed, 255, "Effect ID");
        rows.set(rowIndex, row.withEffectId(parsed));
        break;
      default:
        break;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }

  private static void checkedRange(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
  }
}
