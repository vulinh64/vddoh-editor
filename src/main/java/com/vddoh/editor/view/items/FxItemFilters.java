package com.vddoh.editor.view.items;

import com.vddoh.editor.data.ItemSnapshot;

public final class FxItemFilters {

  public static final String ALL_SLOTS = "All Slots";

  private FxItemFilters() {}

  public static boolean notEquipment(ItemSnapshot item) {
    int category = item.category();

    return category != 1 && category != 2 && category != 3;
  }

  public static boolean runes(ItemSnapshot item) {
    return item.category() == 7;
  }

  public static boolean consumable(ItemSnapshot item) {
    return item.category() == 5 || item.category() == 6;
  }

  public static boolean battleOnlyConsumable(ItemSnapshot item) {
    return item.category() == 9;
  }

  public static boolean special(ItemSnapshot item) {
    return notEquipment(item) && !runes(item) && !consumable(item) && !battleOnlyConsumable(item);
  }
}
