package com.vddoh.editor.service;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record LevelOffsets(
    int costOffset, int damageOffset, int damageCount, int statusOffset, int statusCount) {}
