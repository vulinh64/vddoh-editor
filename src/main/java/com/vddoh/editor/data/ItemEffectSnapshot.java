package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemEffectSnapshot(
    String side,
    String type,
    String target,
    String value,
    String extra,
    String raw,
    boolean editable,
    int numericValue,
    int max,
    int effectKind,
    boolean effectKindEditable) {}
