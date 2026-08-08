package com.vddoh.editor.data;

import java.util.List;

public final class ItemRow {
  final int id;
  final String name;
  final int rawType;
  final int category;
  final int subtype;
  final String slotLabel;
  final String allowedClasses;
  final int hpBonus;
  final int resourceBonus;
  final int weaponReach;
  final int runeSlots;
  final String questInstruction;
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
      int runeSlots,
      String questInstruction,
      List<ItemEffectRow> effects,
      String notes) {
    this.id = id;
    this.name = name;
    this.rawType = rawType;
    this.category = category;
    this.subtype = subtype;
    this.slotLabel = slotLabel;
    this.allowedClasses = allowedClasses;
    this.price = price;
    this.icon = icon;
    this.hpRestore = hpRestore;
    this.resourceRestore = resourceRestore;
    this.hpBonus = hpBonus;
    this.resourceBonus = resourceBonus;
    this.weaponReach = weaponReach;
    this.runeSlots = runeSlots;
    this.questInstruction = questInstruction;
    this.effects = effects;
    this.notes = notes;
  }
}
