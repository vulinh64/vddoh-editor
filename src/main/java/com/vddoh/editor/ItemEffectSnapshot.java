package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemEffectSnapshot(
    String side, String type, String target, String value, String extra, String raw) {}
