package com.vddoh.editor;

import java.util.List;

final class ItemRow {
  final int id;
  final String name;
  final int rawType;
  final int category;
  final int subtype;
  final String slotLabel;
  final String allowedClasses;
  final int originalPrice;
  final int originalIcon;
  final int originalHpRestore;
  final int originalResourceRestore;
  final int hpBonus;
  final int resourceBonus;
  final int weaponReach;
  final int weaponMode;
  final List<ItemEffectRow> effects;
  final String notes;
  int price;
  int icon;
  int hpRestore;
  int resourceRestore;

  ItemRow(
      int id,
      String name,
      int rawType,
      int category,
      int subtype,
      String slotLabel,
      String allowedClasses,
      int price,
      int icon,
      int hpRestore,
      int resourceRestore,
      int hpBonus,
      int resourceBonus,
      int weaponReach,
      int weaponMode,
      List<ItemEffectRow> effects,
      String notes) {
    this.id = id;
    this.name = name;
    this.rawType = rawType;
    this.category = category;
    this.subtype = subtype;
    this.slotLabel = slotLabel;
    this.allowedClasses = allowedClasses;
    this.price = this.originalPrice = price;
    this.icon = this.originalIcon = icon;
    this.hpRestore = this.originalHpRestore = hpRestore;
    this.resourceRestore = this.originalResourceRestore = resourceRestore;
    this.hpBonus = hpBonus;
    this.resourceBonus = resourceBonus;
    this.weaponReach = weaponReach;
    this.weaponMode = weaponMode;
    this.effects = effects;
    this.notes = notes;
  }

  boolean changed() {
    return price != originalPrice
        || icon != originalIcon
        || hpRestore != originalHpRestore
        || resourceRestore != originalResourceRestore;
  }

  void reset() {
    price = originalPrice;
    icon = originalIcon;
    hpRestore = originalHpRestore;
    resourceRestore = originalResourceRestore;
  }
}
