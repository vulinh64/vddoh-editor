package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class ItemEffectTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = 2829805618046611092L;

  private final String[] columns = {
    "Effect Side", "Effect Type", "Target", "Value", "Chance/Extra", "Raw"
  };

  private List<ItemEffectRow> rows = new ArrayList<>();

  void setRows(List<ItemEffectRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  ItemEffectRow row(int rowIndex) {
    return rows.get(rowIndex);
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
  public Object getValueAt(int rowIndex, int columnIndex) {
    ItemEffectRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.side();
      case 1 -> row.type();
      case 2 -> row.target();
      case 3 -> row.value();
      case 4 -> row.extra();
      case 5 -> row.raw();
      default -> StringUtils.EMPTY;
    };
  }
}
