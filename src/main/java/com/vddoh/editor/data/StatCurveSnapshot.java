package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatCurveSnapshot(int start, int target, int curve) {}
