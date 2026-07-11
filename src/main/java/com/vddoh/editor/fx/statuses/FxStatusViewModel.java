package com.vddoh.editor.fx.statuses;

import com.vddoh.editor.StatusEdit;
import com.vddoh.editor.StatusSnapshot;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public record FxStatusViewModel(
    StatusSnapshot status,
    IntegerProperty duration,
    IntegerProperty expireChance,
    IntegerProperty icon) {

  public FxStatusViewModel(StatusSnapshot status) {
    this(
        status,
        new SimpleIntegerProperty(status.duration()),
        new SimpleIntegerProperty(status.expireChance()),
        new SimpleIntegerProperty(status.icon()));
  }

  public int id() {
    return status.id();
  }

  public String name() {
    return status.name();
  }

  public IntegerProperty durationProperty() {
    return duration;
  }

  public IntegerProperty expireChanceProperty() {
    return expireChance;
  }

  public IntegerProperty iconProperty() {
    return icon;
  }

  public String notes() {
    return status.notes();
  }

  public boolean changed() {
    return duration.get() != status.duration()
        || expireChance.get() != status.expireChance()
        || icon.get() != status.icon();
  }

  public void reset() {
    duration.set(status.duration());
    expireChance.set(status.expireChance());
    icon.set(status.icon());
  }

  public StatusEdit toEdit() {
    return StatusEdit.builder()
        .statusId(id())
        .duration(duration.get())
        .expireChance(expireChance.get())
        .icon(icon.get())
        .build();
  }

  public boolean matches(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase();
    return normalized.isEmpty()
        || ("%d %s %s".formatted(id(), name(), notes())).toLowerCase().contains(normalized);
  }
}
