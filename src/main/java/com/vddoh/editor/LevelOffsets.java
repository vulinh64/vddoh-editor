package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record LevelOffsets(
    int costOffset, int damageOffset, int damageCount, int statusOffset, int statusCount) {}
