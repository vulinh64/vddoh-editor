package com.vddoh.editor.fx.items;

import com.vddoh.editor.ItemEdit;
import com.vddoh.editor.ItemSnapshot;
import java.util.List;
import java.util.Locale;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class FxItemViewModel {

  private final ItemSnapshot item;
  private final ObservableList<FxItemEffectViewModel> effects;
  private final IntegerProperty price;
  private final IntegerProperty icon;
  private final IntegerProperty hpRestore;
  private final IntegerProperty resourceRestore;

  public FxItemViewModel(ItemSnapshot item) {
    this.item = item;
    price = new SimpleIntegerProperty(item.price());
    icon = new SimpleIntegerProperty(item.icon());
    hpRestore = new SimpleIntegerProperty(item.hpRestore());
    resourceRestore = new SimpleIntegerProperty(item.resourceRestore());
    this.effects =
        FXCollections.observableArrayList(
            item.effects().stream().map(FxItemEffectViewModel::new).toList());
  }

  public ItemSnapshot item() {
    return item;
  }

  public ObservableList<FxItemEffectViewModel> effects() {
    return effects;
  }

  public int id() {
    return item.id();
  }

  public String name() {
    return item.name();
  }

  public String slotLabel() {
    return item.slotLabel();
  }

  public String allowedClasses() {
    return item.allowedClasses();
  }

  public int price() {
    return price.get();
  }

  public IntegerProperty priceProperty() {
    return price;
  }

  public int icon() {
    return icon.get();
  }

  public IntegerProperty iconProperty() {
    return icon;
  }

  public int hpRestore() {
    return hpRestore.get();
  }

  public IntegerProperty hpRestoreProperty() {
    return hpRestore;
  }

  public int resourceRestore() {
    return resourceRestore.get();
  }

  public IntegerProperty resourceRestoreProperty() {
    return resourceRestore;
  }

  public int hpBonus() {
    return item.hpBonus();
  }

  public int resourceBonus() {
    return item.resourceBonus();
  }

  public int weaponReach() {
    return item.weaponReach();
  }

  public int weaponMode() {
    return item.weaponMode();
  }

  public String statBonusSummary() {
    return summarize(
        item.effects().stream()
            .filter(effect -> "Packed Stat".equals(effect.type()))
            .map(effect -> "%s +%s".formatted(effect.target(), effect.value()))
            .toList());
  }

  public String effectSummary() {
    return summarize(
        item.effects().stream()
            .filter(effect -> !"Info".equals(effect.side()))
            .filter(effect -> !"Linked skill".equals(effect.type()))
            .map(effect -> "%s: %s %s".formatted(effect.type(), effect.target(), effect.value()))
            .toList());
  }

  public String notes() {
    return item.notes();
  }

  public boolean changed() {
    return price() != item.price()
        || icon() != item.icon()
        || hpRestore() != item.hpRestore()
        || resourceRestore() != item.resourceRestore();
  }

  public void resetEdits() {
    price.set(item.price());
    icon.set(item.icon());
    hpRestore.set(item.hpRestore());
    resourceRestore.set(item.resourceRestore());
  }

  public ItemEdit toEdit() {
    return ItemEdit.builder()
        .itemId(id())
        .price(checkedRange(price(), 0xffff, "price"))
        .icon(checkedRange(icon(), 0x7f, "icon"))
        .hpRestore(checkedRange(hpRestore(), 0xffff, "HP restore/effect"))
        .resourceRestore(checkedRange(resourceRestore(), 0xffff, "resource restore/effect"))
        .build();
  }

  public boolean matchesSearch(String query) {
    String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
    if (normalized.isEmpty()) {
      return true;
    }
    StringBuilder text = new StringBuilder();
    text.append(item.id())
        .append(' ')
        .append(item.name())
        .append(' ')
        .append(item.slotLabel())
        .append(' ')
        .append(item.allowedClasses())
        .append(' ')
        .append(item.notes());
    for (var effect : item.effects()) {
      text.append(' ')
          .append(effect.side())
          .append(' ')
          .append(effect.type())
          .append(' ')
          .append(effect.target())
          .append(' ')
          .append(effect.value())
          .append(' ')
          .append(effect.extra())
          .append(' ')
          .append(effect.raw());
    }
    return text.toString().toLowerCase(Locale.ROOT).contains(normalized);
  }

  private static String summarize(List<String> values) {
    List<String> nonBlank = values.stream().filter(value -> !value.isBlank()).toList();
    if (nonBlank.isEmpty()) {
      return "";
    }
    String joined = String.join("; ", nonBlank);
    return joined.length() <= 140 ? joined : joined.substring(0, 137) + "...";
  }

  private static int checkedRange(int value, int max, String label) {
    if (value < 0 || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, 0, max));
    }
    return value;
  }
}
