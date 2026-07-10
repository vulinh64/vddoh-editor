package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.checked7Bit;
import static com.vddoh.editor.EditorSupport.checkedByte;

final class StatCurve {
  final int originalStart;
  final int originalTarget;
  final int originalCurve;
  int start;
  int target;
  int curve;

  StatCurve(int start, int target, int curve) {
    this.start = this.originalStart = start;
    this.target = this.originalTarget = target;
    this.curve = this.originalCurve = curve;
  }

  static StatCurve fromPacked(int packed) {
    return new StatCurve(packed & 0xff, (packed >> 8) & 0xff, (packed >> 16) & 0xff);
  }

  int packed() {
    return (checkedByte(curve, "stat curve") << 16)
        | (checked7Bit(target, "stat target") << 8)
        | checked7Bit(start, "stat start");
  }

  int valueAtLevel(int level) {
    return level * (target - start) * (level * (100 - curve) / 99 + curve) / 99 / 100 + start;
  }

  boolean changed() {
    return start != originalStart || target != originalTarget || curve != originalCurve;
  }

  void reset() {
    start = originalStart;
    target = originalTarget;
    curve = originalCurve;
  }
}
