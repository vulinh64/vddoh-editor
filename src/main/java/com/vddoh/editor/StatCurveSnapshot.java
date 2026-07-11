package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatCurveSnapshot(int start, int target, int curve) {

  public int valueAtLevel(int level) {
    return level * (target - start) * (level * (100 - curve) / 99 + curve) / 99 / 100 + start;
  }
}
