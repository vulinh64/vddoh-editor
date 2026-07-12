package com.vddoh.editor.service;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterOffsets(
    int fixedOffset,
    int tailOffset,
    int effectOffset,
    int effectsOffset,
    int effectsCount,
    int resistAOffset,
    int resistACount,
    int resistBOffset,
    int resistBCount,
    int bytesDOffset,
    int bytesDCount,
    int dropsOffset,
    int dropsCount) {}
