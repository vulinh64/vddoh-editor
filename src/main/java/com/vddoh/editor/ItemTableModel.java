package com.vddoh.editor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang3.StringUtils;

final class ItemTableModel extends AbstractTableModel {

  @Serial private static final long serialVersionUID = 2000494902667515748L;
  public static final char SPACE_CHAR = ' ';

  private final String[] columns = {
    "ID",
    "Item",
    "Slot",
    "Allowed",
    "Price",
    "Icon",
    "HP Restore",
    "Resource Restore",
    "HP Bonus",
    "Resource Bonus",
    "Reach",
    "Notes"
  };

  private List<ItemRow> rows = new ArrayList<>();

  void setRows(List<ItemRow> rows) {
    this.rows = new ArrayList<>(rows);
    fireTableDataChanged();
  }

  void resetEdits() {
    for (ItemRow row : rows) {
      row.reset();
    }
    fireTableDataChanged();
  }

  List<ItemEffectRow> effectRows(int rowIndex) {
    return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).effects : new ArrayList<>();
  }

  boolean matchesSearch(int rowIndex, String query) {
    if (rowIndex < 0 || rowIndex >= rows.size()) {
      return false;
    }
    ItemRow row = rows.get(rowIndex);
    StringBuilder text = new StringBuilder();
    text.append(row.id)
        .append(SPACE_CHAR)
        .append(row.name)
        .append(SPACE_CHAR)
        .append(row.slotLabel)
        .append(SPACE_CHAR)
        .append(row.allowedClasses)
        .append(SPACE_CHAR)
        .append(row.price)
        .append(SPACE_CHAR)
        .append(row.icon)
        .append(SPACE_CHAR)
        .append(row.hpRestore)
        .append(SPACE_CHAR)
        .append(row.resourceRestore)
        .append(SPACE_CHAR)
        .append(row.hpBonus)
        .append(SPACE_CHAR)
        .append(row.resourceBonus)
        .append(SPACE_CHAR)
        .append(row.weaponReach)
        .append(SPACE_CHAR)
        .append(row.notes);
    for (ItemEffectRow effect : row.effects) {
      text.append(SPACE_CHAR)
          .append(effect.side())
          .append(SPACE_CHAR)
          .append(effect.type())
          .append(SPACE_CHAR)
          .append(effect.target())
          .append(SPACE_CHAR)
          .append(effect.value())
          .append(SPACE_CHAR)
          .append(effect.extra())
          .append(SPACE_CHAR)
          .append(effect.raw());
    }
    return text.toString().toLowerCase().contains(query);
  }

  List<ItemPatch> changedPatches() {
    List<ItemPatch> patches = new ArrayList<>();
    for (ItemRow row : rows) {
      if (row.changed()) {
        patches.add(
            ItemPatch.builder()
                .itemId(row.id)
                .price(row.price)
                .icon(row.icon)
                .hpRestore(row.hpRestore)
                .resourceRestore(row.resourceRestore)
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
    return column == 4 || column == 5 || column == 6 || column == 7;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    ItemRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> row.id;
      case 1 -> row.name;
      case 2 -> row.slotLabel;
      case 3 -> row.allowedClasses;
      case 4 -> row.price;
      case 5 -> row.icon;
      case 6 -> row.hpRestore;
      case 7 -> row.resourceRestore;
      case 8 -> row.hpBonus;
      case 9 -> row.resourceBonus;
      case 10 -> row.weaponReach;
      case 11 -> row.notes;
      default -> StringUtils.EMPTY;
    };
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    ItemRow row = rows.get(rowIndex);
    int parsed = Integer.parseInt(String.valueOf(value).trim());
    if (columnIndex == 4) {
      row.price = parsed;
    } else if (columnIndex == 5) {
      row.icon = parsed;
    } else if (columnIndex == 6) {
      row.hpRestore = parsed;
    } else if (columnIndex == 7) {
      row.resourceRestore = parsed;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}
