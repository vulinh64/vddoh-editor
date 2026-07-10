package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

final class SimpleNamedTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = -4956286318180790973L;

  private final String kind;
  private List<NamedRow> rows = new ArrayList<>();

  SimpleNamedTableModel(String kind) {
    this.kind = kind;
  }

  void setRows(List<NamedRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public String getColumnName(int column) {
    if (column == 0) {
      return "ID";
    }

    return column == 1 ? kind : "Notes";
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    NamedRow row = rows.get(rowIndex);
    return columnIndex == 0 ? row.id() : columnIndex == 1 ? row.name() : row.notes();
  }
}
