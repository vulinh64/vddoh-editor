package com.vddoh.editor.data;

import java.io.Serializable;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemEffectRow(
    String side,
    String type,
    String target,
    String value,
    String extra,
    String raw,
    int effectKind,
    boolean effectKindEditable)
    implements Serializable {

  public static ItemEffectRow of(
      String side, String type, String target, String value, String extra, String raw) {
    return ItemEffectRow.builder()
        .side(side)
        .type(type)
        .target(target)
        .value(value)
        .extra(extra)
        .raw(raw)
        .effectKind(-1)
        .effectKindEditable(false)
        .build();
  }

  public static ItemEffectRow weaponDamage(
      String side, String type, String target, String value, String raw, int effectKind) {
    return ItemEffectRow.builder()
        .side(side)
        .type(type)
        .target(target)
        .value(value)
        .extra("damage type editable")
        .raw(raw)
        .effectKind(effectKind)
        .effectKindEditable(true)
        .build();
  }
}
