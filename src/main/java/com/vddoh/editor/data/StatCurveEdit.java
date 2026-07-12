package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatCurveEdit(int start, int target, int curve) {}
