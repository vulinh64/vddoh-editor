package com.vddoh.editor.service;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class ItemOffsets {
  private int priceOffset = -1;
  private int iconOffset = -1;
  private int hpRestoreOffset = -1;
  private int resourceRestoreOffset = -1;
  private final Map<String, EffectOffset> effectOffsets = new HashMap<>();

  public void putEffectOffset(String key, int offset, int width, int byteIndex) {
    effectOffsets.put(key, new EffectOffset(offset, width, byteIndex));
  }

  public EffectOffset effectOffset(String key) {
    return effectOffsets.get(key);
  }

  public record EffectOffset(int offset, int width, int byteIndex) {}
}
