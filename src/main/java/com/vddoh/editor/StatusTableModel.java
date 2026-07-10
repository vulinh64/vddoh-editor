package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class StatusTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = -6488964994045241088L;

  private final String[] columns = {"ID", "Status", "Duration", "Expire %", "Icon", "Notes"};

  private List<StatusRow> rows = new ArrayList<>();

  void setRows(List<StatusRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  void resetEdits() {
    for (StatusRow row : rows) {
      row.reset();
    }
    fireTableDataChanged();
  }

  List<StatusPatch> changedPatches() {
    List<StatusPatch> patches = new ArrayList<>();
    for (StatusRow row : rows) {
      if (row.changed()) {
        patches.add(
            StatusPatch.builder()
                .statusId(row.id)
                .duration(row.duration)
                .expireChance(row.expireChance)
                .icon(row.icon)
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
    return column == 2 || column == 3 || column == 4;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    StatusRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.id;
      case 1 -> row.name;
      case 2 -> row.duration;
      case 3 -> row.expireChance;
      case 4 -> row.icon;
      case 5 -> row.notes;
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    StatusRow row = rows.get(rowIndex);
    int parsed = Integer.parseInt(String.valueOf(value).trim());
    if (columnIndex == 2) {
      row.duration = parsed;
    } else if (columnIndex == 3) {
      row.expireChance = parsed;
    } else if (columnIndex == 4) {
      row.icon = parsed;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}
