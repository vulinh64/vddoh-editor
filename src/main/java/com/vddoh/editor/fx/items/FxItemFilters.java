package com.vddoh.editor.fx.items;

import com.vddoh.editor.ItemSnapshot;

public final class FxItemFilters {

  public static final String ALL_SLOTS = "All Slots";

  private FxItemFilters() {}

  public static boolean equipment(ItemSnapshot item) {
    return switch (item.category()) {
      case 1, 2, 3 -> true;
      default -> false;
    };
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
    return !equipment(item) && !runes(item) && !consumable(item) && !battleOnlyConsumable(item);
  }
}
