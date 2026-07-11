package com.vddoh.editor.service;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterOffsets(int fixedOffset, int tailOffset, int effectOffset) {}
