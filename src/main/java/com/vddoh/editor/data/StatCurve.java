package com.vddoh.editor.data;

import static com.vddoh.editor.utils.EditorSupport.checked7Bit;
import static com.vddoh.editor.utils.EditorSupport.checkedByte;

public final class StatCurve {
  public int start;
  public int target;
  public int curve;

  public StatCurve(int start, int target, int curve) {
    this.start = start;
    this.target = target;
    this.curve = curve;
  }

  public static StatCurve fromPacked(int packed) {
    return new StatCurve(packed & 0xff, (packed >> 8) & 0xff, (packed >> 16) & 0xff);
  }

  public int packed() {
    return (checkedByte(curve, "stat curve") << 16)
        | (checked7Bit(target, "stat target") << 8)
        | checked7Bit(start, "stat start");
  }

  public int valueAtLevel(int level) {
    return level * (target - start) * (level * (100 - curve) / 99 + curve) / 99 / 100 + start;
  }
}
