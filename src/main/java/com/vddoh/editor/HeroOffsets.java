package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record HeroOffsets(int statOffset, int seedOffset) {}
