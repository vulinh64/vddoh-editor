package com.vddoh.editor;

import java.io.Serial;
import java.io.Serializable;

final class StatusRow implements Serializable {

  @Serial private static final long serialVersionUID = 7619741879065234600L;

  final int id;
  final String name;
  final int originalDuration;
  final int originalExpireChance;
  final int originalIcon;
  final String notes;
  int duration;
  int expireChance;
  int icon;

  StatusRow(int id, String name, int duration, int expireChance, int icon, String notes) {
    this.id = id;
    this.name = name;
    this.duration = originalDuration = duration;
    this.expireChance = originalExpireChance = expireChance;
    this.icon = originalIcon = icon;
    this.notes = notes;
  }

  boolean changed() {
    return duration != originalDuration
        || expireChance != originalExpireChance
        || icon != originalIcon;
  }

  void reset() {
    duration = originalDuration;
    expireChance = originalExpireChance;
    icon = originalIcon;
  }
}
