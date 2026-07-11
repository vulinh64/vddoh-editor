package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record SkillEffectSnapshot(
    String type,
    int index,
    int targetId,
    String target,
    int value,
    boolean editable,
    String notes) {}
