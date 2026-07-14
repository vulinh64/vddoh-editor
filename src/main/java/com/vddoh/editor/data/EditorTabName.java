package com.vddoh.editor.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EditorTabName {
  SKILLS("Skills"),
  TALENTS("Talents"),
  HEROES("Heroes"),
  ITEMS("Items"),
  MONSTERS("Monsters"),
  STATUSES("Statuses");

  private final String label;
}
