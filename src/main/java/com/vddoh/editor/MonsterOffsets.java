package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record MonsterOffsets(int fixedOffset, int tailOffset, int effectOffset) {}
