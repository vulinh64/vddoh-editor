package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class SkillEffectTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = -5084740770107716249L;

  private final String[] columns = {
    "Type", "Index", "Target ID", "Target", "Value / Chance", "Own Level Data", "Notes"
  };

  private List<SkillEffectRow> rows = new ArrayList<>();

  void setRows(List<SkillEffectRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
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
    return column == 4 && rows.get(row).editable;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    SkillEffectRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.type;
      case 1 -> row.index;
      case 2 -> row.targetId;
      case 3 -> row.target;
      case 4 -> row.displayValue();
      case 5 -> row.editable ? "yes" : "inherited";
      case 6 -> row.notes;
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    if (columnIndex != 4 || !rows.get(rowIndex).editable) {
      return;
    }
    rows.get(rowIndex).setDisplayValue(Integer.parseInt(String.valueOf(value).trim()));
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}
