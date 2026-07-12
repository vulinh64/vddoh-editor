package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record SkillEffectEdit(
    String type,
    int index,
    int targetId,
    String target,
    int originalValue,
    int value,
    boolean editable,
    String notes) {}
