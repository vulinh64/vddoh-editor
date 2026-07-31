package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemEffectEdit(String raw, int value, Integer effectKind) {}
