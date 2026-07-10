package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class SkillLevelTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = 775540822357827899L;

  private final String[] columns = {
    "Skill ID",
    "Skill",
    "Level",
    "Cost",
    "Shape ID",
    "Area X",
    "Area Y",
    "Range",
    "Effects",
    "Notes"
  };
  private List<SkillLevelRow> rows = new ArrayList<>();

  void setRows(List<SkillLevelRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  void resetEdits() {
    for (SkillLevelRow row : rows) {
      row.reset();
    }
    fireTableDataChanged();
  }

  List<SkillEffectRow> effectRows(int rowIndex) {
    return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).effects : new ArrayList<>();
  }

  boolean matchesSearch(int rowIndex, String query) {
    if (rowIndex < 0 || rowIndex >= rows.size()) {
      return false;
    }
    SkillLevelRow row = rows.get(rowIndex);
    StringBuilder text = new StringBuilder();
    text.append(row.skillId)
        .append(' ')
        .append(row.skillName)
        .append(' ')
        .append(row.levelIndex + 1)
        .append(' ')
        .append(row.cost)
        .append(' ')
        .append(row.notes);
    for (SkillEffectRow effect : row.effects) {
      text.append(' ')
          .append(effect.type)
          .append(' ')
          .append(effect.target)
          .append(' ')
          .append(effect.value)
          .append(' ')
          .append(effect.notes);
    }
    return text.toString().toLowerCase().contains(query);
  }

  List<SkillPatch> changedPatches() {
    List<SkillPatch> patches = new ArrayList<>();
    for (SkillLevelRow row : rows) {
      if (row.changed()) {
        patches.add(
            SkillPatch.builder()
                .skillId(row.skillId)
                .levelIndex(row.levelIndex)
                .cost(row.cost)
                .effects(row.effects)
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

  @Override
  public boolean isCellEditable(int row, int column) {
    return column == 3;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    SkillLevelRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.skillId;
      case 1 -> row.skillName;
      case 2 -> row.levelIndex + 1;
      case 3 -> row.cost;
      case 4 -> row.areaShape;
      case 5 -> row.areaWidth;
      case 6 -> row.areaHeight;
      case 7 -> row.range;
      case 8 -> row.effects.size();
      case 9 -> row.notes;
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    SkillLevelRow row = rows.get(rowIndex);
    int parsed = Integer.parseInt(String.valueOf(value).trim());
    if (columnIndex == 3) {
      row.cost = parsed;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}
