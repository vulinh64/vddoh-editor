package com.vddoh.editor.data;

import java.io.Serial;
import java.io.Serializable;

public final class StatusRow implements Serializable {

  @Serial private static final long serialVersionUID = 7619741879065234600L;

  final int id;
  final String name;
  final String notes;
  int duration;
  int expireChance;
  int icon;

  StatusRow(int id, String name, int duration, int expireChance, int icon, String notes) {
    this.id = id;
    this.name = name;
    this.duration = duration;
    this.expireChance = expireChance;
    this.icon = icon;
    this.notes = notes;
  }
}
