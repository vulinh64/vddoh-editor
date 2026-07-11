package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatCurveEdit(int start, int target, int curve) {}
